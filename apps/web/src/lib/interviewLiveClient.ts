const AUDIO_GATE_THRESHOLD = 0.012;
const AUDIO_GATE_SILENCE_FRAMES = 12;
const AUDIO_GATE_PRESPEECH_FRAMES = 4;

type InterviewLivePayload = Record<string, unknown>;

export type InterviewLiveEvent = {
  type: string;
  payload: InterviewLivePayload;
};

export type InterviewLiveSetupPayload = {
  candidateName: string;
  targetRole: string;
  interviewType: string;
  interviewerStyle: string;
  focusTopics: string;
  resumeSummary: string;
  jobDescription: string;
};

type InterviewLiveClientOptions = {
  wsUrl: string;
  onEvent: (event: InterviewLiveEvent) => void;
};

type CaptureState = {
  context: AudioContext | null;
  stream: MediaStream | null;
  source: MediaStreamAudioSourceNode | null;
  processor: ScriptProcessorNode | null;
  sink: GainNode | null;
};

type PlaybackState = {
  context: AudioContext | null;
  cursor: number;
  sources: Set<AudioBufferSourceNode>;
};

function createClientEvent(type: string, payload: InterviewLivePayload): InterviewLiveEvent {
  return { type, payload };
}

function base64ToUint8Array(base64: string) {
  const binary = window.atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

function arrayBufferToBase64(buffer: ArrayBuffer) {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  const chunkSize = 0x8000;
  for (let offset = 0; offset < bytes.length; offset += chunkSize) {
    const chunk = bytes.subarray(offset, offset + chunkSize);
    binary += String.fromCharCode(...chunk);
  }
  return window.btoa(binary);
}

function downsampleBuffer(float32Buffer: Float32Array, inputRate: number, targetRate: number) {
  if (targetRate === inputRate) {
    return float32Buffer;
  }
  const ratio = inputRate / targetRate;
  const newLength = Math.round(float32Buffer.length / ratio);
  const result = new Float32Array(newLength);
  let offsetResult = 0;
  let offsetBuffer = 0;
  while (offsetResult < result.length) {
    const nextOffsetBuffer = Math.round((offsetResult + 1) * ratio);
    let accum = 0;
    let count = 0;
    for (let index = offsetBuffer; index < nextOffsetBuffer && index < float32Buffer.length; index += 1) {
      accum += float32Buffer[index];
      count += 1;
    }
    result[offsetResult] = count ? accum / count : 0;
    offsetResult += 1;
    offsetBuffer = nextOffsetBuffer;
  }
  return result;
}

function floatTo16BitPcm(float32Buffer: Float32Array) {
  const buffer = new ArrayBuffer(float32Buffer.length * 2);
  const view = new DataView(buffer);
  for (let index = 0; index < float32Buffer.length; index += 1) {
    const sample = Math.max(-1, Math.min(1, float32Buffer[index]));
    view.setInt16(index * 2, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true);
  }
  return buffer;
}

function calculateRms(float32Buffer: Float32Array) {
  let sumSquares = 0;
  for (let index = 0; index < float32Buffer.length; index += 1) {
    sumSquares += float32Buffer[index] * float32Buffer[index];
  }
  return Math.sqrt(sumSquares / float32Buffer.length);
}

export class InterviewLiveClient {
  private readonly wsUrl: string;
  private readonly onEvent: (event: InterviewLiveEvent) => void;

  private ws: WebSocket | null = null;
  private capture: CaptureState = {
    context: null,
    stream: null,
    source: null,
    processor: null,
    sink: null,
  };
  private playback: PlaybackState = {
    context: null,
    cursor: 0,
    sources: new Set(),
  };
  private autoMicPending = false;
  private microphoneEnabled = false;
  private userSpeechActive = false;
  private silenceFrameCount = 0;
  private preSpeechChunks: Array<{ data: string; mimeType: string }> = [];

  constructor(options: InterviewLiveClientOptions) {
    this.wsUrl = options.wsUrl;
    this.onEvent = options.onEvent;
  }

  isConnected() {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  isMicrophoneEnabled() {
    return this.microphoneEnabled;
  }

  async connect(setupPayload: InterviewLiveSetupPayload) {
    if (this.isConnected()) {
      this.emit("client.log", { message: "当前已经存在 Live 连接，忽略重复连接。" });
      return;
    }

    await this.ensureCapturePipeline();
    await this.ensurePlaybackContext();

    this.emit("client.connection", { status: "connecting", message: "正在连接 Live Python 服务..." });

    await new Promise<void>((resolve, reject) => {
      let opened = false;
      const socket = new WebSocket(this.wsUrl);
      this.ws = socket;
      this.autoMicPending = true;

      socket.addEventListener("open", () => {
        opened = true;
        this.emit("client.connection", { status: "connected", message: "浏览器已连到 Live Python 服务。" });
        this.emit("client.mic_status", { message: "麦克风待命，等待开场结束后自动开启。" });
        this.sendEnvelope("session.start", setupPayload);
        resolve();
      });

      socket.addEventListener("message", (event) => {
        try {
          const data = JSON.parse(event.data) as InterviewLiveEvent;
          this.handleIncomingEvent(data);
        } catch (error) {
          this.emit("client.log", {
            message: "后端消息解析失败。",
            detail: error instanceof Error ? error.message : String(error),
          });
        }
      });

      socket.addEventListener("close", () => {
        this.emit("client.connection", { status: "closed", message: "Live 连接已关闭。" });
        this.stopPlaybackQueue();
        this.teardownCapturePipeline();
        this.ws = null;
      });

      socket.addEventListener("error", () => {
        this.emit("client.connection", { status: "error", message: "Live WebSocket 连接异常。" });
        if (!opened) {
          reject(new Error("live websocket connection error"));
        }
      });
    });
  }

  async setMicrophoneEnabled(enabled: boolean) {
    if (enabled) {
      await this.ensureCapturePipeline();
      this.autoMicPending = false;
      this.resetAudioGate();
      this.microphoneEnabled = true;
      this.emit("client.mic_status", { message: "实时采集中" });
      return;
    }
    this.microphoneEnabled = false;
    this.resetAudioGate();
    this.emit("client.mic_status", { message: "已静音" });
    this.sendEnvelope("audio.stream_end", {});
  }

  finish() {
    this.sendEnvelope("session.finish", {});
  }

  sendText(text: string) {
    const normalizedText = text.trim();
    if (!normalizedText) {
      return;
    }
    this.sendEnvelope("user.text", { text: normalizedText });
  }

  close() {
    this.sendEnvelope("session.close", {});
    this.stopPlaybackQueue();
    this.teardownCapturePipeline();
    if (this.ws) {
      try {
        this.ws.close();
      } catch {
        // ignore close errors
      }
      this.ws = null;
    }
  }

  private emit(type: string, payload: InterviewLivePayload) {
    this.onEvent(createClientEvent(type, payload));
  }

  private sendEnvelope(type: string, payload: InterviewLivePayload) {
    if (!this.isConnected()) {
      this.emit("client.log", { message: "当前未连到 Live Python 服务，消息未发送。", type });
      return;
    }
    this.ws?.send(JSON.stringify({ type, payload }));
  }

  private resetAudioGate() {
    this.userSpeechActive = false;
    this.silenceFrameCount = 0;
    this.preSpeechChunks = [];
  }

  private async ensurePlaybackContext() {
    if (!this.playback.context) {
      this.playback.context = new AudioContext();
    }
    if (this.playback.context.state === "suspended") {
      await this.playback.context.resume();
    }
    return this.playback.context;
  }

  private async queueModelAudio(base64Audio: string) {
    if (!base64Audio) {
      return;
    }
    const audioContext = await this.ensurePlaybackContext();
    const rawBytes = base64ToUint8Array(base64Audio);
    const pcmView = new DataView(rawBytes.buffer, rawBytes.byteOffset, rawBytes.byteLength);
    const sampleCount = rawBytes.byteLength / 2;
    const channelBuffer = new Float32Array(sampleCount);
    for (let index = 0; index < sampleCount; index += 1) {
      channelBuffer[index] = pcmView.getInt16(index * 2, true) / 0x8000;
    }

    const audioBuffer = audioContext.createBuffer(1, channelBuffer.length, 24000);
    audioBuffer.copyToChannel(channelBuffer, 0);

    const source = audioContext.createBufferSource();
    source.buffer = audioBuffer;
    source.connect(audioContext.destination);

    const startAt = Math.max(audioContext.currentTime + 0.03, this.playback.cursor);
    source.start(startAt);
    this.playback.cursor = startAt + audioBuffer.duration;
    this.playback.sources.add(source);
    this.emit("client.playback_status", { message: "模型正在说话" });

    source.onended = () => {
      this.playback.sources.delete(source);
      if (this.playback.sources.size === 0) {
        this.emit("client.playback_status", { message: "空闲" });
      }
    };
  }

  private stopPlaybackQueue() {
    for (const source of this.playback.sources) {
      try {
        source.stop();
      } catch {
        // ignore stop errors
      }
    }
    this.playback.sources.clear();
    if (this.playback.context) {
      this.playback.cursor = this.playback.context.currentTime;
    } else {
      this.playback.cursor = 0;
    }
    this.emit("client.playback_status", { message: "已清空播放队列" });
  }

  private async ensureCapturePipeline() {
    if (this.capture.context && this.capture.stream) {
      if (this.capture.context.state === "suspended") {
        await this.capture.context.resume();
      }
      return;
    }

    const stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        noiseSuppression: true,
        echoCancellation: true,
        autoGainControl: true,
      },
    });

    const context = new AudioContext();
    const source = context.createMediaStreamSource(stream);
    const processor = context.createScriptProcessor(2048, 1, 1);
    const sink = context.createGain();
    sink.gain.value = 0;

    source.connect(processor);
    processor.connect(sink);
    sink.connect(context.destination);

    processor.onaudioprocess = (event) => {
      if (!this.microphoneEnabled || !this.isConnected() || !this.capture.context) {
        return;
      }

      const input = event.inputBuffer.getChannelData(0);
      const rms = calculateRms(input);
      const downsampled = downsampleBuffer(input, this.capture.context.sampleRate, 16000);
      const pcmBuffer = floatTo16BitPcm(downsampled);
      const payload = {
        data: arrayBufferToBase64(pcmBuffer),
        mimeType: "audio/pcm;rate=16000",
      };

      if (rms >= AUDIO_GATE_THRESHOLD) {
        if (!this.userSpeechActive) {
          this.userSpeechActive = true;
          for (const cachedChunk of this.preSpeechChunks) {
            this.sendEnvelope("audio.chunk", cachedChunk);
          }
          this.preSpeechChunks = [];
          this.emit("client.log", { message: "检测到说话，开始上传语音。" });
        }
        this.silenceFrameCount = 0;
        this.sendEnvelope("audio.chunk", payload);
        return;
      }

      if (!this.userSpeechActive) {
        this.preSpeechChunks.push(payload);
        if (this.preSpeechChunks.length > AUDIO_GATE_PRESPEECH_FRAMES) {
          this.preSpeechChunks.shift();
        }
        return;
      }

      this.silenceFrameCount += 1;
      this.sendEnvelope("audio.chunk", payload);
      if (this.silenceFrameCount >= AUDIO_GATE_SILENCE_FRAMES) {
        this.sendEnvelope("audio.stream_end", {});
        this.resetAudioGate();
        this.emit("client.log", { message: "检测到停顿，已提交本轮语音。" });
      }
    };

    this.capture = {
      context,
      stream,
      source,
      processor,
      sink,
    };
  }

  private teardownCapturePipeline() {
    this.autoMicPending = false;
    this.microphoneEnabled = false;
    this.resetAudioGate();
    this.capture.processor?.disconnect();
    if (this.capture.processor) {
      this.capture.processor.onaudioprocess = null;
    }
    this.capture.source?.disconnect();
    this.capture.sink?.disconnect();
    this.capture.stream?.getTracks().forEach((track) => track.stop());
    const closePromise = this.capture.context ? this.capture.context.close() : null;
    closePromise?.catch(() => {});
    this.capture = {
      context: null,
      stream: null,
      source: null,
      processor: null,
      sink: null,
    };
    this.emit("client.mic_status", { message: "未授权" });
  }

  private handleIncomingEvent(message: InterviewLiveEvent) {
    switch (message.type) {
      case "server.ready":
        if (!this.microphoneEnabled && this.autoMicPending) {
          this.emit("client.mic_status", { message: "麦克风待命，等待开场结束后自动开启。" });
        }
        this.emit(message.type, message.payload);
        break;
      case "turn.model.audio":
        void this.queueModelAudio(typeof message.payload.data === "string" ? message.payload.data : "");
        break;
      case "turn.model.interrupted":
        this.stopPlaybackQueue();
        this.emit(message.type, message.payload);
        break;
      case "turn.complete":
        this.emit(message.type, message.payload);
        if (this.autoMicPending && !this.microphoneEnabled) {
          this.autoMicPending = false;
          void this.setMicrophoneEnabled(true)
            .then(() => {
              this.emit("client.log", { message: "开场结束，已自动开启麦克风。" });
            })
            .catch((error) => {
              this.emit("client.log", {
                message: "自动开启麦克风失败。",
                detail: error instanceof Error ? error.message : String(error),
              });
              this.emit("client.mic_status", { message: "自动启麦失败，请手动重试。" });
            });
        }
        break;
      default:
        this.emit(message.type, message.payload);
        break;
    }
  }
}
