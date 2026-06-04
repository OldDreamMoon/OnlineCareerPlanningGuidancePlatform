from __future__ import annotations

import asyncio
import base64
import json
import logging
from dataclasses import dataclass
from typing import Any

from fastapi import WebSocket, WebSocketDisconnect
from google import genai
from google.genai import types
from websockets.exceptions import ConnectionClosed

from .config import Settings
from .prompts import InterviewSetup, build_finish_prompt, build_opening_prompt, build_system_instruction

LOGGER = logging.getLogger(__name__)


@dataclass(slots=True)
class BrowserEnvelope:
    message_type: str
    payload: dict[str, Any]


class LiveInterviewBridge:
    def __init__(self, websocket: WebSocket, settings: Settings) -> None:
        self.websocket = websocket
        self.settings = settings
        self.client = genai.Client(api_key=settings.gemini_api_key) if settings.gemini_api_key else None
        self.closed = False
        self.live_session: Any | None = None
        self.live_ready = asyncio.Event()
        self.live_task: asyncio.Task[None] | None = None
        self.setup: InterviewSetup | None = None
        self.resumption_handle: str | None = None
        self.opening_sent = False
        self.reconnect_attempt = 0

    async def run(self) -> None:
        await self.websocket.accept()
        await self._emit("server.status", {"message": "Live Python 服务已连接，等待开始会话。"})
        try:
            await self._browser_loop()
        finally:
            await self._shutdown()

    async def _browser_loop(self) -> None:
        while not self.closed:
            try:
                raw_message = await self.websocket.receive_text()
            except WebSocketDisconnect:
                break
            try:
                envelope = self._parse_envelope(raw_message)
            except ValueError as exc:
                await self._emit("error", {"message": str(exc)})
                continue
            try:
                await self._dispatch_browser_event(envelope)
            except Exception as exc:  # pragma: no cover - 运行态保护
                LOGGER.exception("处理浏览器事件失败：%s", envelope.message_type)
                await self._emit(
                    "error",
                    {
                        "message": "处理浏览器输入时发生异常。",
                        "detail": str(exc),
                        "source": envelope.message_type,
                    },
                )

    def _parse_envelope(self, raw_message: str) -> BrowserEnvelope:
        try:
            data = json.loads(raw_message)
        except json.JSONDecodeError as exc:
            raise ValueError(f"浏览器消息不是合法 JSON：{exc}") from exc
        message_type = str(data.get("type") or "").strip()
        if not message_type:
            raise ValueError("浏览器消息缺少 type 字段。")
        payload = data.get("payload")
        if payload is None:
            payload = {}
        if not isinstance(payload, dict):
            raise ValueError("浏览器消息 payload 必须是对象。")
        return BrowserEnvelope(message_type=message_type, payload=payload)

    async def _dispatch_browser_event(self, envelope: BrowserEnvelope) -> None:
        if envelope.message_type == "session.start":
            await self._handle_session_start(envelope.payload)
            return
        if envelope.message_type == "session.finish":
            await self._send_text_to_live(build_finish_prompt())
            return
        if envelope.message_type == "session.close":
            self.closed = True
            return
        if envelope.message_type == "user.text":
            text = str(envelope.payload.get("text") or "").strip()
            if text:
                await self._send_text_to_live(text)
            return
        if envelope.message_type == "audio.chunk":
            await self._send_audio_chunk(envelope.payload)
            return
        if envelope.message_type == "audio.stream_end":
            await self._signal_audio_stream_end()
            return
        await self._emit("error", {"message": f"未识别的浏览器事件：{envelope.message_type}"})

    async def _handle_session_start(self, payload: dict[str, Any]) -> None:
        if self.setup is not None:
            await self._emit("server.status", {"message": "当前会话已经启动，忽略重复创建。"})
            return
        if self.client is None:
            await self._emit(
                "error",
                {
                    "message": "后端未检测到 GEMINI_API_KEY，无法创建 Gemini Live 会话。",
                    "hint": "请先在仓库根目录 .env 中配置 GEMINI_API_KEY。",
                },
            )
            return
        self.setup = InterviewSetup(
            candidate_name=str(payload.get("candidateName") or "").strip(),
            target_role=str(payload.get("targetRole") or "").strip(),
            interview_type=str(payload.get("interviewType") or "").strip(),
            interviewer_style=str(payload.get("interviewerStyle") or "").strip(),
            focus_topics=str(payload.get("focusTopics") or "").strip(),
            resume_summary=str(payload.get("resumeSummary") or "").strip(),
            job_description=str(payload.get("jobDescription") or "").strip(),
        )
        self.live_task = asyncio.create_task(self._live_loop())
        await self._emit("server.status", {"message": "正在连接 Gemini Live，会话即将开始。"})

    async def _live_loop(self) -> None:
        assert self.client is not None
        assert self.setup is not None
        while not self.closed:
            config = self._build_live_config()
            try:
                async with self.client.aio.live.connect(model=self.settings.model, config=config) as session:
                    self.live_session = session
                    self.live_ready.set()
                    self.reconnect_attempt = 0
                    await self._emit(
                        "server.ready",
                        {
                            "message": "Gemini Live 会话已建立。",
                            "model": self.settings.model,
                            "voice": self.settings.voice_name,
                        },
                    )
                    if not self.opening_sent:
                        self.opening_sent = True
                        await session.send_realtime_input(text=build_opening_prompt(self.setup))
                    while not self.closed:
                        received_message = False
                        async for message in session.receive():
                            received_message = True
                            await self._handle_live_message(message)
                        if self.closed:
                            break
                        if not received_message:
                            LOGGER.info("Gemini Live receive() 未再返回消息，视为远端已结束当前会话。")
                            break
            except asyncio.CancelledError:
                raise
            except Exception as exc:  # pragma: no cover - 运行态保护
                LOGGER.exception("Gemini Live 会话异常断开")
                await self._emit(
                    "error",
                    {
                        "message": "Gemini Live 会话异常断开。",
                        "detail": str(exc),
                        "reconnectable": bool(self.resumption_handle and self.settings.enable_session_resumption),
                    },
                )
            finally:
                self.live_session = None
                self.live_ready.clear()
            if self.closed:
                break
            if not self.settings.enable_session_resumption or not self.resumption_handle:
                break
            self.reconnect_attempt += 1
            await self._emit(
                "session.reconnecting",
                {
                    "message": "Gemini 连接即将重建，尝试用 session resumption 自动续接。",
                    "attempt": self.reconnect_attempt,
                },
            )
            await asyncio.sleep(0.35)
        await self._emit("server.status", {"message": "Gemini Live 会话已结束。"})

    def _build_live_config(self) -> dict[str, Any]:
        assert self.setup is not None
        config: dict[str, Any] = {
            "response_modalities": ["AUDIO"],
            "system_instruction": {"parts": [{"text": build_system_instruction(self.setup)}]},
            "speech_config": {
                "voice_config": {
                    "prebuilt_voice_config": {
                        "voice_name": self.settings.voice_name,
                    }
                }
            },
            "input_audio_transcription": {},
            "output_audio_transcription": {},
            "realtime_input_config": {
                "automatic_activity_detection": {
                    "disabled": False,
                    "start_of_speech_sensitivity": types.StartSensitivity.START_SENSITIVITY_LOW,
                    "end_of_speech_sensitivity": types.EndSensitivity.END_SENSITIVITY_LOW,
                    "prefix_padding_ms": self.settings.vad_prefix_padding_ms,
                    "silence_duration_ms": self.settings.vad_silence_duration_ms,
                }
            },
            "context_window_compression": {
                "trigger_tokens": self.settings.compression_trigger_tokens,
                "sliding_window": {"target_tokens": self.settings.compression_target_tokens},
            },
        }
        if self.settings.model.startswith("gemini-2.5-"):
            config["thinking_config"] = {"thinking_budget": self.settings.thinking_budget}
        else:
            config["thinking_config"] = {"thinking_level": self.settings.thinking_level}
        if self.settings.enable_session_resumption:
            config["session_resumption"] = {"handle": self.resumption_handle} if self.resumption_handle else {}
        return config

    async def _send_audio_chunk(self, payload: dict[str, Any]) -> None:
        audio_b64 = str(payload.get("data") or "").strip()
        mime_type = str(payload.get("mimeType") or "audio/pcm;rate=16000").strip()
        if not audio_b64:
            return
        try:
            audio_bytes = base64.b64decode(audio_b64)
        except Exception as exc:  # pragma: no cover - 浏览器非法输入保护
            await self._emit("error", {"message": "浏览器上传的音频块无法解码。", "detail": str(exc)})
            return
        await self._send_realtime_input(
            "audio.chunk",
            audio=types.Blob(data=audio_bytes, mime_type=mime_type),
        )

    async def _signal_audio_stream_end(self) -> None:
        await self._send_realtime_input("audio.stream_end", audio_stream_end=True)

    async def _send_text_to_live(self, text: str) -> None:
        if not text:
            return
        await self._send_realtime_input("user.text", text=text)

    async def _send_realtime_input(
        self,
        source: str,
        *,
        audio: types.Blob | None = None,
        audio_stream_end: bool | None = None,
        text: str | None = None,
    ) -> bool:
        if not await self._wait_live_ready():
            return False
        session = self.live_session
        if session is None:
            await self._emit("error", {"message": "Gemini Live 当前不可用，输入未发送。", "source": source})
            return False
        try:
            await session.send_realtime_input(
                audio=audio,
                audio_stream_end=audio_stream_end,
                text=text,
            )
            return True
        except ConnectionClosed as exc:  # pragma: no cover - 运行态保护
            self.live_ready.clear()
            self.live_session = None
            LOGGER.warning("Gemini Live 已关闭，丢弃来自 %s 的输入：%s", source, exc)
            await self._emit(
                "error",
                {
                    "message": "Gemini Live 会话已关闭，当前输入未送达。",
                    "detail": str(exc),
                    "source": source,
                },
            )
            return False
        except Exception as exc:  # pragma: no cover - 运行态保护
            LOGGER.exception("发送 Gemini Live 输入失败：%s", source)
            await self._emit(
                "error",
                {
                    "message": "发送到 Gemini Live 时发生异常。",
                    "detail": str(exc),
                    "source": source,
                },
            )
            return False

    async def _wait_live_ready(self) -> bool:
        try:
            await asyncio.wait_for(self.live_ready.wait(), timeout=8)
        except TimeoutError:
            await self._emit("error", {"message": "Gemini Live 尚未准备完成，请稍后再试。"})
            return False
        return True

    async def _handle_live_message(self, message: Any) -> None:
        if getattr(message, "go_away", None) is not None:
            await self._emit(
                "session.go_away",
                {"message": "Gemini 将主动切断当前连接，后端会尝试续接。", "timeLeft": str(message.go_away.time_left)},
            )
        if getattr(message, "session_resumption_update", None) is not None:
            update = message.session_resumption_update
            if getattr(update, "resumable", False) and getattr(update, "new_handle", None):
                self.resumption_handle = update.new_handle
                await self._emit(
                    "session.resumption",
                    {"message": "已收到新的 session resumption handle。", "resumable": True},
                )
        if getattr(message, "usage_metadata", None) is not None:
            usage = message.usage_metadata
            details = []
            for item in getattr(usage, "response_tokens_details", []) or []:
                modality = getattr(item, "modality", None)
                token_count = getattr(item, "token_count", None)
                if modality is not None and token_count is not None:
                    details.append({"modality": str(modality), "tokenCount": int(token_count)})
            await self._emit(
                "usage.update",
                {
                    "totalTokenCount": int(getattr(usage, "total_token_count", 0) or 0),
                    "responseTokenCount": int(getattr(usage, "response_token_count", 0) or 0),
                    "details": details,
                },
            )
        server_content = getattr(message, "server_content", None)
        if server_content is None:
            return
        if getattr(server_content, "interrupted", False):
            await self._emit("turn.model.interrupted", {"message": "模型输出已被新的语音活动打断。"})
        input_transcription = getattr(server_content, "input_transcription", None)
        if input_transcription is not None and getattr(input_transcription, "text", None):
            await self._emit("turn.user.transcript", {"text": input_transcription.text})
        output_transcription = getattr(server_content, "output_transcription", None)
        if output_transcription is not None and getattr(output_transcription, "text", None):
            await self._emit("turn.model.transcript", {"text": output_transcription.text})
        model_turn = getattr(server_content, "model_turn", None)
        if model_turn is not None:
            for part in getattr(model_turn, "parts", []) or []:
                inline_data = getattr(part, "inline_data", None)
                text = getattr(part, "text", None)
                if inline_data is not None and getattr(inline_data, "data", None):
                    data = inline_data.data
                    if isinstance(data, str):
                        encoded = data
                    else:
                        encoded = base64.b64encode(data).decode("ascii")
                    await self._emit(
                        "turn.model.audio",
                        {
                            "data": encoded,
                            "mimeType": getattr(inline_data, "mime_type", "audio/pcm;rate=24000"),
                        },
                    )
                if text:
                    await self._emit("turn.model.text", {"text": text})
        if getattr(server_content, "turn_complete", False):
            await self._emit("turn.complete", {"message": "当前一轮输出结束。"})

    async def _emit(self, event_type: str, payload: dict[str, Any]) -> None:
        if self.closed:
            return
        await self.websocket.send_text(json.dumps({"type": event_type, "payload": payload}, ensure_ascii=False))

    async def _shutdown(self) -> None:
        self.closed = True
        if self.live_task is not None:
            self.live_task.cancel()
            try:
                await self.live_task
            except asyncio.CancelledError:
                pass
