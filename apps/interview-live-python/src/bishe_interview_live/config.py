from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import dotenv_values


def _as_bool(value: str | None, default: bool) -> bool:
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def _as_int(value: str | None, default: int) -> int:
    if value is None:
        return default
    try:
        return int(value.strip())
    except ValueError:
        return default


def _as_text(value: str | None, default: str) -> str:
    if value is None:
        return default
    stripped = value.strip()
    return stripped or default


def _find_repo_env_file() -> Path | None:
    candidate_dirs: list[Path] = []

    for path in (Path.cwd(), Path(__file__).resolve().parent):
        for directory in (path, *path.parents):
            if directory not in candidate_dirs:
                candidate_dirs.append(directory)

    for directory in candidate_dirs:
        dotenv_path = directory / ".env"
        if dotenv_path.exists():
            return dotenv_path

    return None


@dataclass(slots=True)
class Settings:
    gemini_api_key: str
    model: str
    voice_name: str
    host: str
    port: int
    ws_path: str
    health_path: str
    thinking_level: str
    thinking_budget: int
    vad_prefix_padding_ms: int
    vad_silence_duration_ms: int
    compression_trigger_tokens: int
    compression_target_tokens: int
    enable_session_resumption: bool
    log_level: str

    @classmethod
    def from_env(cls) -> "Settings":
        merged_file_values: dict[str, str] = {}
        dotenv_path = _find_repo_env_file()
        if dotenv_path is not None:
            for key, value in dotenv_values(dotenv_path).items():
                if value is not None:
                    merged_file_values[key] = value

        for key, value in merged_file_values.items():
            os.environ.setdefault(key, value)

        return cls(
            gemini_api_key=os.getenv("GEMINI_API_KEY", "").strip(),
            model=_as_text(os.getenv("INTERVIEW_LIVE_MODEL"), "gemini-3.1-flash-live-preview"),
            voice_name=_as_text(os.getenv("INTERVIEW_LIVE_VOICE"), "Kore"),
            host=_as_text(os.getenv("INTERVIEW_LIVE_HOST"), "127.0.0.1"),
            port=_as_int(os.getenv("INTERVIEW_LIVE_PORT"), 8765),
            ws_path=_as_text(os.getenv("INTERVIEW_LIVE_WS_PATH"), "/ai/interview/live/ws"),
            health_path=_as_text(os.getenv("INTERVIEW_LIVE_HEALTH_PATH"), "/ai/interview/live/healthz"),
            thinking_level=_as_text(os.getenv("INTERVIEW_LIVE_THINKING_LEVEL"), "minimal").lower(),
            thinking_budget=_as_int(os.getenv("INTERVIEW_LIVE_THINKING_BUDGET"), 0),
            vad_prefix_padding_ms=_as_int(os.getenv("INTERVIEW_LIVE_VAD_PREFIX_PADDING_MS"), 20),
            vad_silence_duration_ms=_as_int(os.getenv("INTERVIEW_LIVE_VAD_SILENCE_DURATION_MS"), 180),
            compression_trigger_tokens=_as_int(os.getenv("INTERVIEW_LIVE_COMPRESSION_TRIGGER_TOKENS"), 24000),
            compression_target_tokens=_as_int(os.getenv("INTERVIEW_LIVE_COMPRESSION_TARGET_TOKENS"), 12000),
            enable_session_resumption=_as_bool(os.getenv("INTERVIEW_LIVE_SESSION_RESUMPTION"), True),
            log_level=_as_text(os.getenv("INTERVIEW_LIVE_LOG_LEVEL"), "INFO").upper(),
        )
