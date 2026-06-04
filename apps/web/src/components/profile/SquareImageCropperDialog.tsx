import { AnimatePresence, motion } from "framer-motion";
import { RefreshCw, Scissors, UploadCloud } from "lucide-react";
import { useEffect, useMemo, useRef, useState, type PointerEvent, type WheelEvent } from "react";
import { createPortal } from "react-dom";

const CROP_VIEWPORT_SIZE = 280;
const PREVIEW_VIEWPORT_SIZE = 128;
const EDITOR_STAGE_WIDTH = 560;
const EDITOR_STAGE_HEIGHT = 396;
const MIN_ZOOM = 0.35;
const MAX_ZOOM = 3;
const OUTPUT_SIZE = 640;
const INITIAL_ZOOM = 1.04;
const TRANSPARENT_SURFACE_CLASS_NAME = "bg-[linear-gradient(45deg,rgba(203,213,225,0.9)_25%,transparent_25%,transparent_75%,rgba(203,213,225,0.9)_75%,rgba(203,213,225,0.9)),linear-gradient(45deg,rgba(203,213,225,0.9)_25%,transparent_25%,transparent_75%,rgba(203,213,225,0.9)_75%,rgba(203,213,225,0.9))] bg-[length:20px_20px] bg-[position:0_0,10px_10px] bg-slate-100";

type CropState = {
  zoom: number;
  offsetX: number;
  offsetY: number;
  busy: boolean;
};

type BackgroundMode = "transparent" | "light" | "dark";

type DragState = {
  pointerId: number;
  startX: number;
  startY: number;
  originOffsetX: number;
  originOffsetY: number;
};

type SquareImageCropperDialogProps = {
  open: boolean;
  sourceDataUrl: string;
  sourceName: string;
  naturalWidth: number;
  naturalHeight: number;
  title: string;
  subjectName: string;
  previewTitle: string;
  confirmLabel: string;
  busyLabel: string;
  stageAlt: string;
  previewAlt: string;
  onCancel: () => void;
  onPickAnother?: () => void;
  onConfirm: (file: File) => Promise<void> | void;
};

function createCropState(): CropState {
  return {
    zoom: INITIAL_ZOOM,
    offsetX: 0,
    offsetY: 0,
    busy: false,
  };
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function getImageScale(naturalWidth: number, naturalHeight: number, zoom: number) {
  if (!naturalWidth || !naturalHeight) {
    return 1;
  }
  const cropCoverScale = CROP_VIEWPORT_SIZE / Math.min(naturalWidth, naturalHeight);
  return cropCoverScale * zoom;
}

function constrainOffsets(
  naturalWidth: number,
  naturalHeight: number,
  zoom: number,
  offsetX: number,
  offsetY: number,
) {
  const scale = getImageScale(naturalWidth, naturalHeight, zoom);
  const renderedWidth = naturalWidth * scale;
  const renderedHeight = naturalHeight * scale;
  const maxOffsetX = Math.max((renderedWidth - CROP_VIEWPORT_SIZE) / 2, 0);
  const maxOffsetY = Math.max((renderedHeight - CROP_VIEWPORT_SIZE) / 2, 0);

  return {
    offsetX: clamp(offsetX, -maxOffsetX, maxOffsetX),
    offsetY: clamp(offsetY, -maxOffsetY, maxOffsetY),
  };
}

function getPreviewImageStyle(
  naturalWidth: number,
  naturalHeight: number,
  zoom: number,
  offsetX: number,
  offsetY: number,
  previewSize: number,
) {
  const previewScale = getImageScale(naturalWidth, naturalHeight, zoom) * (previewSize / CROP_VIEWPORT_SIZE);
  return {
    width: naturalWidth * previewScale,
    height: naturalHeight * previewScale,
    transform: `translate(calc(-50% + ${offsetX * (previewSize / CROP_VIEWPORT_SIZE)}px), calc(-50% + ${offsetY * (previewSize / CROP_VIEWPORT_SIZE)}px))`,
  };
}

async function loadImageElement(sourceDataUrl: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("image load failed"));
    image.src = sourceDataUrl;
  });
}

async function canvasToBlob(canvas: HTMLCanvasElement) {
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (!blob) {
        reject(new Error("canvas export failed"));
        return;
      }
      resolve(blob);
    }, "image/png");
  });
}

async function buildCroppedSquareFile(options: {
  sourceDataUrl: string;
  sourceName: string;
  naturalWidth: number;
  naturalHeight: number;
  zoom: number;
  offsetX: number;
  offsetY: number;
  backgroundMode: BackgroundMode;
}) {
  const image = await loadImageElement(options.sourceDataUrl);
  const canvas = document.createElement("canvas");
  canvas.width = OUTPUT_SIZE;
  canvas.height = OUTPUT_SIZE;

  const context = canvas.getContext("2d");
  if (!context) {
    throw new Error("canvas context unavailable");
  }

  const scaleRatio = OUTPUT_SIZE / CROP_VIEWPORT_SIZE;
  const imageScale = getImageScale(options.naturalWidth, options.naturalHeight, options.zoom);
  const drawWidth = image.naturalWidth * imageScale * scaleRatio;
  const drawHeight = image.naturalHeight * imageScale * scaleRatio;
  const drawX = (OUTPUT_SIZE - drawWidth) / 2 + options.offsetX * scaleRatio;
  const drawY = (OUTPUT_SIZE - drawHeight) / 2 + options.offsetY * scaleRatio;

  if (options.backgroundMode === "dark") {
    context.fillStyle = "#0f172a";
    context.fillRect(0, 0, OUTPUT_SIZE, OUTPUT_SIZE);
  } else if (options.backgroundMode === "light") {
    context.fillStyle = "#ffffff";
    context.fillRect(0, 0, OUTPUT_SIZE, OUTPUT_SIZE);
  } else {
    context.clearRect(0, 0, OUTPUT_SIZE, OUTPUT_SIZE);
  }
  context.imageSmoothingEnabled = true;
  context.imageSmoothingQuality = "high";
  context.drawImage(
    image,
    drawX,
    drawY,
    drawWidth,
    drawHeight,
  );

  const blob = await canvasToBlob(canvas);
  const fileName = `${options.sourceName.replace(/\.(png|jpe?g)$/i, "") || "logo"}.png`;
  return new File([blob], fileName, { type: "image/png" });
}

export default function SquareImageCropperDialog({
  open,
  sourceDataUrl,
  sourceName,
  naturalWidth,
  naturalHeight,
  title,
  subjectName,
  previewTitle,
  confirmLabel,
  busyLabel,
  stageAlt,
  previewAlt,
  onCancel,
  onPickAnother,
  onConfirm,
}: SquareImageCropperDialogProps) {
  const [crop, setCrop] = useState<CropState>(createCropState());
  const [backgroundMode, setBackgroundMode] = useState<BackgroundMode>("transparent");
  const dragStateRef = useRef<DragState | null>(null);
  const previewImageStyle = useMemo(
    () => getPreviewImageStyle(
      naturalWidth,
      naturalHeight,
      crop.zoom,
      crop.offsetX,
      crop.offsetY,
      PREVIEW_VIEWPORT_SIZE,
    ),
    [crop.offsetX, crop.offsetY, crop.zoom, naturalHeight, naturalWidth],
  );

  useEffect(() => {
    if (open) {
      dragStateRef.current = null;
      setCrop(createCropState());
      setBackgroundMode("transparent");
    }
  }, [open, sourceDataUrl]);

  useEffect(() => {
    if (!open || typeof document === "undefined") {
      return undefined;
    }

    const originalOverflow = document.body.style.overflow;
    const originalPaddingRight = document.body.style.paddingRight;
    const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;

    document.body.style.overflow = "hidden";
    if (scrollbarWidth > 0) {
      document.body.style.paddingRight = `${scrollbarWidth}px`;
    }

    return () => {
      document.body.style.overflow = originalOverflow;
      document.body.style.paddingRight = originalPaddingRight;
    };
  }, [open]);

  useEffect(() => {
    if (!open) {
      return undefined;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Escape" || crop.busy) {
        return;
      }
      event.preventDefault();
      onCancel();
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [crop.busy, onCancel, open]);

  const handleZoomChange = (nextZoom: number) => {
    setCrop((current) => {
      const zoom = clamp(nextZoom, MIN_ZOOM, MAX_ZOOM);
      const constrainedOffsets = constrainOffsets(
        naturalWidth,
        naturalHeight,
        zoom,
        current.offsetX,
        current.offsetY,
      );
      return {
        ...current,
        zoom,
        ...constrainedOffsets,
      };
    });
  };

  const handleStageWheel = (event: WheelEvent<HTMLDivElement>) => {
    event.preventDefault();
    if (crop.busy) {
      return;
    }

    const zoomDelta = event.deltaY > 0 ? -0.08 : 0.08;
    handleZoomChange(crop.zoom + zoomDelta);
  };

  const handlePointerDown = (event: PointerEvent<HTMLDivElement>) => {
    if (crop.busy) {
      return;
    }

    dragStateRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      originOffsetX: crop.offsetX,
      originOffsetY: crop.offsetY,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const handlePointerMove = (event: PointerEvent<HTMLDivElement>) => {
    if (!dragStateRef.current || dragStateRef.current.pointerId !== event.pointerId) {
      return;
    }

    const deltaX = event.clientX - dragStateRef.current.startX;
    const deltaY = event.clientY - dragStateRef.current.startY;
    setCrop((current) => ({
      ...current,
      ...constrainOffsets(
        naturalWidth,
        naturalHeight,
        current.zoom,
        (dragStateRef.current?.originOffsetX ?? current.offsetX) + deltaX,
        (dragStateRef.current?.originOffsetY ?? current.offsetY) + deltaY,
      ),
    }));
  };

  const handlePointerRelease = (event: PointerEvent<HTMLDivElement>) => {
    if (dragStateRef.current?.pointerId === event.pointerId) {
      dragStateRef.current = null;
    }
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  };

  const handleConfirm = async () => {
    if (crop.busy) {
      return;
    }

    setCrop((current) => ({ ...current, busy: true }));
    try {
      const file = await buildCroppedSquareFile({
        sourceDataUrl,
        sourceName,
        naturalWidth,
        naturalHeight,
        zoom: crop.zoom,
        offsetX: crop.offsetX,
        offsetY: crop.offsetY,
        backgroundMode,
      });
      await onConfirm(file);
    } catch {
      setCrop((current) => ({ ...current, busy: false }));
    }
  };

  if (typeof document === "undefined") {
    return null;
  }

  const cropFrameInsetX = (EDITOR_STAGE_WIDTH - CROP_VIEWPORT_SIZE) / 2;
  const cropFrameInsetY = (EDITOR_STAGE_HEIGHT - CROP_VIEWPORT_SIZE) / 2;
  const backgroundSurfaceClassName = backgroundMode === "dark"
    ? "bg-slate-950"
    : backgroundMode === "light"
      ? "bg-white"
      : TRANSPARENT_SURFACE_CLASS_NAME;
  const backgroundButtonClassName = (mode: BackgroundMode) => (
    mode === backgroundMode
      ? "border-sky-300 bg-sky-50 text-sky-700"
      : "border-slate-200 bg-white text-slate-500 hover:border-slate-300 hover:text-slate-700"
  );

  return createPortal(
    <AnimatePresence>
      {open ? (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-[90] overflow-y-auto bg-[rgba(226,232,240,0.62)] px-4 py-6 backdrop-blur-md"
          onClick={(event) => {
            if (event.target === event.currentTarget && !crop.busy) {
              onCancel();
            }
          }}
        >
          <div className="flex min-h-full items-center justify-center">
            <motion.div
              role="dialog"
              aria-modal="true"
              aria-label={title}
              initial={{ opacity: 0, scale: 0.96, y: 18 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.98, y: 16 }}
              transition={{ type: "spring", bounce: 0.16, duration: 0.4 }}
              className="relative max-h-[calc(100vh-2rem)] w-full max-w-[60rem] overflow-y-auto rounded-[2.1rem] border border-white/85 bg-[linear-gradient(145deg,rgba(255,255,255,0.97),rgba(246,248,252,0.96)_40%,rgba(238,244,255,0.96))] p-5 text-slate-900 shadow-[0_28px_80px_rgba(148,163,184,0.26)] lg:p-6"
            >
              <div className="pointer-events-none absolute inset-x-0 top-0 h-32 bg-[radial-gradient(circle_at_top,rgba(59,130,246,0.18),transparent_62%)]" />

              <div className="relative z-10">
                <div className="flex flex-col gap-4 border-b border-slate-200/80 pb-4 lg:flex-row lg:items-center lg:justify-between">
                  <div className="flex items-center gap-3">
                    <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-sky-50 text-sky-600 shadow-sm">
                      <Scissors size={20} />
                    </div>
                    <h3 className="text-2xl font-semibold text-slate-900">{title}</h3>
                  </div>
                  <div className="flex flex-wrap items-center gap-3">
                    <button
                      type="button"
                      onClick={() => setCrop(createCropState())}
                      disabled={crop.busy}
                      className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition-colors hover:border-slate-300 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      还原位置
                    </button>
                    {onPickAnother ? (
                      <button
                        type="button"
                        onClick={onPickAnother}
                        disabled={crop.busy}
                        className="inline-flex items-center justify-center rounded-full border border-sky-200 bg-sky-50 px-4 py-2.5 text-sm font-semibold text-sky-700 transition-colors hover:border-sky-300 hover:bg-sky-100 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        <UploadCloud size={15} className="mr-2" />
                        重新选图
                      </button>
                    ) : null}
                  </div>
                </div>

                <div className="mt-5 grid gap-5 xl:grid-cols-[35rem_18rem] xl:justify-center">
                  <div
                    className="relative h-[396px] w-[560px] cursor-grab overflow-hidden rounded-[1.9rem] border border-slate-200/90 bg-[radial-gradient(circle_at_top,rgba(56,189,248,0.14),transparent_60%),linear-gradient(180deg,#ffffff_0%,#edf6ff_100%)] shadow-[0_22px_56px_rgba(148,163,184,0.2)] active:cursor-grabbing"
                    onWheel={handleStageWheel}
                    onPointerDown={handlePointerDown}
                    onPointerMove={handlePointerMove}
                    onPointerUp={handlePointerRelease}
                    onPointerCancel={handlePointerRelease}
                    onPointerLeave={handlePointerRelease}
                  >
                    <div
                      className={`pointer-events-none absolute rounded-[1.5rem] ${backgroundSurfaceClassName}`}
                      style={{
                        left: cropFrameInsetX,
                        top: cropFrameInsetY,
                        width: CROP_VIEWPORT_SIZE,
                        height: CROP_VIEWPORT_SIZE,
                      }}
                    />
                    <img
                      src={sourceDataUrl}
                      alt={stageAlt}
                      draggable={false}
                      className="pointer-events-none absolute left-1/2 top-1/2 max-w-none select-none"
                      style={{
                        width: naturalWidth * getImageScale(naturalWidth, naturalHeight, crop.zoom),
                        height: naturalHeight * getImageScale(naturalWidth, naturalHeight, crop.zoom),
                        transform: `translate(calc(-50% + ${crop.offsetX}px), calc(-50% + ${crop.offsetY}px))`,
                      }}
                    />
                    <div
                      className="pointer-events-none absolute rounded-[1.5rem]"
                      style={{
                        left: cropFrameInsetX,
                        top: cropFrameInsetY,
                        width: CROP_VIEWPORT_SIZE,
                        height: CROP_VIEWPORT_SIZE,
                        boxShadow: "0 0 0 9999px rgba(241, 245, 249, 0.82)",
                      }}
                    />
                    <div
                      className="pointer-events-none absolute rounded-[1.5rem] border border-sky-300 shadow-[0_0_0_1px_rgba(125,211,252,0.35)]"
                      style={{
                        left: cropFrameInsetX,
                        top: cropFrameInsetY,
                        width: CROP_VIEWPORT_SIZE,
                        height: CROP_VIEWPORT_SIZE,
                      }}
                    />
                    <div
                      className="pointer-events-none absolute w-px bg-sky-200/90"
                      style={{
                        left: EDITOR_STAGE_WIDTH / 2,
                        top: cropFrameInsetY,
                        height: CROP_VIEWPORT_SIZE,
                      }}
                    />
                    <div
                      className="pointer-events-none absolute h-px bg-sky-200/90"
                      style={{
                        left: cropFrameInsetX,
                        top: EDITOR_STAGE_HEIGHT / 2,
                        width: CROP_VIEWPORT_SIZE,
                      }}
                    />
                  </div>

                  <div className="w-[19rem]">
                    <div className="rounded-[1.7rem] border border-slate-200/80 bg-white/90 p-5 shadow-[0_16px_40px_rgba(148,163,184,0.14)]">
                      <div className="text-base font-semibold text-slate-900">{previewTitle}</div>
                      <div className="mt-4 flex flex-col gap-4">
                        <div className={`relative flex h-32 w-32 items-center justify-center overflow-hidden rounded-[2rem] border border-slate-200 shadow-inner ${backgroundSurfaceClassName}`}>
                          <img
                            src={sourceDataUrl}
                            alt={previewAlt}
                            draggable={false}
                            className="pointer-events-none absolute left-1/2 top-1/2 max-w-none select-none"
                            style={previewImageStyle}
                          />
                        </div>
                        <div className="min-w-0">
                          <div className="text-lg font-semibold text-slate-900">{subjectName}</div>
                          <div className="mt-3 inline-flex rounded-full border border-sky-200 bg-sky-50 px-3 py-1.5 text-sm font-semibold text-sky-700">
                            缩放 {Math.round(crop.zoom * 100)}%
                          </div>
                        </div>
                      </div>

                      <label className="mt-5 block">
                        <div className="mb-2 text-sm font-semibold text-slate-700">缩放比例</div>
                        <input
                          type="range"
                          min={MIN_ZOOM}
                          max={MAX_ZOOM}
                          step={0.01}
                          value={crop.zoom}
                          disabled={crop.busy}
                          onChange={(event) => handleZoomChange(Number(event.target.value))}
                          className="h-2 w-full cursor-pointer appearance-none rounded-full bg-slate-200 accent-sky-600 disabled:cursor-not-allowed"
                        />
                      </label>

                      <div className="mt-5">
                        <div className="mb-2 text-sm font-semibold text-slate-700">背景</div>
                        <div className="flex gap-2">
                          <button
                            type="button"
                            disabled={crop.busy}
                            onClick={() => setBackgroundMode("transparent")}
                            className={`inline-flex items-center justify-center rounded-full border px-3 py-2 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${backgroundButtonClassName("transparent")}`}
                          >
                            透明
                          </button>
                          <button
                            type="button"
                            disabled={crop.busy}
                            onClick={() => setBackgroundMode("light")}
                            className={`inline-flex items-center justify-center rounded-full border px-3 py-2 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${backgroundButtonClassName("light")}`}
                          >
                            白底
                          </button>
                          <button
                            type="button"
                            disabled={crop.busy}
                            onClick={() => setBackgroundMode("dark")}
                            className={`inline-flex items-center justify-center rounded-full border px-3 py-2 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${backgroundButtonClassName("dark")}`}
                          >
                            黑底
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <div className="mt-5 flex flex-col-reverse gap-3 border-t border-slate-200/80 pt-4 sm:flex-row sm:justify-end">
                  <button
                    type="button"
                    onClick={onCancel}
                    disabled={crop.busy}
                    className="inline-flex items-center justify-center rounded-2xl border border-slate-200 bg-white px-5 py-3 text-base font-semibold text-slate-700 transition-colors hover:border-slate-300 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 sm:min-w-[9rem]"
                  >
                    取消
                  </button>
                  <button
                    type="button"
                    onClick={() => void handleConfirm()}
                    disabled={crop.busy}
                    className="inline-flex items-center justify-center rounded-2xl bg-sky-600 px-6 py-3 text-base font-semibold text-white shadow-[0_16px_30px_rgba(14,165,233,0.24)] transition-transform hover:-translate-y-0.5 hover:bg-sky-500 disabled:cursor-not-allowed disabled:opacity-70 sm:min-w-[10rem]"
                  >
                    {crop.busy ? (
                      <>
                        <RefreshCw size={16} className="mr-2 animate-spin" />
                        {busyLabel}
                      </>
                    ) : (
                      confirmLabel
                    )}
                  </button>
                </div>
              </div>
            </motion.div>
          </div>
        </motion.div>
      ) : null}
    </AnimatePresence>,
    document.body,
  );
}
