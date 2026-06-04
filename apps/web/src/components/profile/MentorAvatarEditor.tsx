import { AnimatePresence, motion } from "framer-motion";
import { Camera, RefreshCw, Scissors, ShieldCheck, UploadCloud } from "lucide-react";
import { useEffect, useRef, useState, type ChangeEvent, type PointerEvent, type WheelEvent } from "react";
import { createPortal } from "react-dom";
import { ApiClientError, apiRequest } from "../../lib/apiClient";
import {
  getCachedMentorAvatarDataUrl,
  loadMentorAvatarDataUrl,
  primeMentorAvatarCache,
} from "../../lib/mentorAvatar";
import { deriveAvatarLabel } from "../../lib/studentAvatar";

const AVATAR_SOURCE_LIMIT_BYTES = 6 * 1024 * 1024;
const TARGET_AVATAR_LIMIT_BYTES = 380 * 1024;
const CROP_VIEWPORT_SIZE = 320;
const PREVIEW_VIEWPORT_SIZE = 112;
const EDITOR_STAGE_WIDTH = 640;
const EDITOR_STAGE_HEIGHT = 440;
const MIN_ZOOM = 1;
const MAX_ZOOM = 2.8;
const OUTPUT_SIZE_CANDIDATES = [512, 448, 384];
const OUTPUT_QUALITY_CANDIDATES = [0.9, 0.84, 0.78, 0.72];

type ToastTone = "success" | "error" | "info";

export type MentorAvatarUploadResult = {
  uploaded: boolean;
  contentType: string | null;
  sizeBytes: number;
  updatedAt: number | string;
  avatarUrl: string;
};

type AvatarEditorState = {
  isOpen: boolean;
  sourceDataUrl: string;
  sourceName: string;
  naturalWidth: number;
  naturalHeight: number;
  zoom: number;
  offsetX: number;
  offsetY: number;
  busy: boolean;
};

type DragState = {
  pointerId: number;
  startX: number;
  startY: number;
  originOffsetX: number;
  originOffsetY: number;
};

function createAvatarEditorState(): AvatarEditorState {
  return {
    isOpen: false,
    sourceDataUrl: "",
    sourceName: "",
    naturalWidth: 0,
    naturalHeight: 0,
    zoom: 1,
    offsetX: 0,
    offsetY: 0,
    busy: false,
  };
}

function buildErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiClientError) {
    return error.message || fallback;
  }
  return fallback;
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function getAvatarScale(editor: AvatarEditorState) {
  if (!editor.naturalWidth || !editor.naturalHeight) {
    return 1;
  }
  const cropCoverScale = CROP_VIEWPORT_SIZE / Math.min(editor.naturalWidth, editor.naturalHeight);
  return cropCoverScale * editor.zoom;
}

function getAvatarPreviewStyle(editor: AvatarEditorState, previewSize: number) {
  const scaleRatio = previewSize / CROP_VIEWPORT_SIZE;
  const previewScale = getAvatarScale(editor) * scaleRatio;

  return {
    width: editor.naturalWidth * previewScale,
    height: editor.naturalHeight * previewScale,
    transform: `translate(calc(-50% + ${editor.offsetX * scaleRatio}px), calc(-50% + ${editor.offsetY * scaleRatio}px))`,
  };
}

function constrainOffsets(editor: AvatarEditorState, offsetX: number, offsetY: number) {
  const scale = getAvatarScale(editor);
  const renderedWidth = editor.naturalWidth * scale;
  const renderedHeight = editor.naturalHeight * scale;
  const maxOffsetX = Math.max((renderedWidth - CROP_VIEWPORT_SIZE) / 2, 0);
  const maxOffsetY = Math.max((renderedHeight - CROP_VIEWPORT_SIZE) / 2, 0);

  return {
    offsetX: clamp(offsetX, -maxOffsetX, maxOffsetX),
    offsetY: clamp(offsetY, -maxOffsetY, maxOffsetY),
  };
}

async function readFileAsDataUrl(file: Blob) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(typeof reader.result === "string" ? reader.result : "");
    reader.onerror = () => reject(new Error("file read failed"));
    reader.readAsDataURL(file);
  });
}

async function loadImageElement(sourceDataUrl: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("image load failed"));
    image.src = sourceDataUrl;
  });
}

async function canvasToBlob(canvas: HTMLCanvasElement, quality: number) {
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (!blob) {
        reject(new Error("canvas export failed"));
        return;
      }
      resolve(blob);
    }, "image/jpeg", quality);
  });
}

async function buildCroppedAvatarBlob(editor: AvatarEditorState) {
  const image = await loadImageElement(editor.sourceDataUrl);
  const scale = getAvatarScale(editor);
  const cropSideInSource = CROP_VIEWPORT_SIZE / scale;
  const sourceX = image.naturalWidth / 2 - (CROP_VIEWPORT_SIZE / 2 + editor.offsetX) / scale;
  const sourceY = image.naturalHeight / 2 - (CROP_VIEWPORT_SIZE / 2 + editor.offsetY) / scale;
  let lastBlob: Blob | null = null;

  for (const outputSize of OUTPUT_SIZE_CANDIDATES) {
    const canvas = document.createElement("canvas");
    canvas.width = outputSize;
    canvas.height = outputSize;
    const context = canvas.getContext("2d");
    if (!context) {
      throw new Error("canvas context unavailable");
    }

    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = "high";
    context.drawImage(
      image,
      sourceX,
      sourceY,
      cropSideInSource,
      cropSideInSource,
      0,
      0,
      outputSize,
      outputSize,
    );

    for (const quality of OUTPUT_QUALITY_CANDIDATES) {
      const blob = await canvasToBlob(canvas, quality);
      lastBlob = blob;
      if (blob.size <= TARGET_AVATAR_LIMIT_BYTES) {
        return blob;
      }
    }
  }

  if (!lastBlob) {
    throw new Error("avatar render failed");
  }

  return lastBlob;
}

function normalizeSelectedFile(file: File | null | undefined) {
  if (!file) {
    return null;
  }
  if (!["image/jpeg", "image/png"].includes(file.type)) {
    throw new Error("头像仅支持 JPG 或 PNG 图片。");
  }
  if (file.size > AVATAR_SOURCE_LIMIT_BYTES) {
    throw new Error("原图过大，请控制在 6 MB 以内。");
  }
  return file;
}

function getApprovalBadgeClassName(approvalStatus?: string | null) {
  if (approvalStatus === "APPROVED") {
    return "border-white bg-emerald-500 text-white";
  }
  if (approvalStatus === "REJECTED") {
    return "border-white bg-rose-500 text-white";
  }
  return "border-white bg-amber-500 text-white";
}

export default function MentorAvatarEditor({
  userId,
  heroName,
  avatarConfigured,
  avatarUrl,
  approvalStatus,
  onAvatarUploaded,
  onToast,
}: {
  userId: number;
  heroName: string;
  avatarConfigured: boolean;
  avatarUrl?: string | null;
  approvalStatus?: string | null;
  onAvatarUploaded: (payload: MentorAvatarUploadResult) => void;
  onToast: (message: string, tone?: ToastTone) => void;
}) {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const dragStateRef = useRef<DragState | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(() => (
    avatarConfigured ? getCachedMentorAvatarDataUrl({ userId, avatarUrl }) : null
  ));
  const [editor, setEditor] = useState<AvatarEditorState>(createAvatarEditorState());
  const avatarLabel = deriveAvatarLabel(heroName);
  const previewImageStyle = getAvatarPreviewStyle(editor, PREVIEW_VIEWPORT_SIZE);

  useEffect(() => {
    let active = true;

    if (!avatarConfigured || !avatarUrl) {
      setPreviewUrl(null);
      return () => {
        active = false;
      };
    }

    const cachedValue = getCachedMentorAvatarDataUrl({ userId, avatarUrl });
    if (cachedValue) {
      setPreviewUrl(cachedValue);
    }

    void loadMentorAvatarDataUrl({ userId, avatarUrl })
      .then((nextAvatarUrl) => {
        if (active) {
          setPreviewUrl(nextAvatarUrl);
        }
      })
      .catch(() => {
        if (active) {
          setPreviewUrl((current) => current ?? null);
        }
      });

    return () => {
      active = false;
    };
  }, [avatarConfigured, avatarUrl, userId]);

  useEffect(() => {
    if (!editor.isOpen || typeof document === "undefined") {
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
  }, [editor.isOpen]);

  useEffect(() => {
    if (!editor.isOpen) {
      return undefined;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Escape" || editor.busy) {
        return;
      }
      event.preventDefault();
      setEditor(createAvatarEditorState());
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [editor.busy, editor.isOpen]);

  const closeEditor = () => {
    if (editor.busy) {
      return;
    }
    dragStateRef.current = null;
    setEditor(createAvatarEditorState());
  };

  const openFilePicker = () => {
    if (editor.busy) {
      return;
    }
    fileInputRef.current?.click();
  };

  const handleFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    try {
      const file = normalizeSelectedFile(event.target.files?.[0]);
      event.target.value = "";
      if (!file) {
        return;
      }

      const sourceDataUrl = await readFileAsDataUrl(file);
      const image = await loadImageElement(sourceDataUrl);
      if (Math.min(image.naturalWidth, image.naturalHeight) < 120) {
        onToast("头像尺寸太小，请选择至少 120 x 120 的图片。", "error");
        return;
      }

      setEditor({
        isOpen: true,
        sourceDataUrl,
        sourceName: file.name,
        naturalWidth: image.naturalWidth,
        naturalHeight: image.naturalHeight,
        zoom: 1,
        offsetX: 0,
        offsetY: 0,
        busy: false,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : "头像读取失败，请重新选择图片。";
      onToast(message, "error");
    }
  };

  const handleZoomChange = (nextZoom: number) => {
    setEditor((current) => {
      const nextState = { ...current, zoom: clamp(nextZoom, MIN_ZOOM, MAX_ZOOM) };
      return {
        ...nextState,
        ...constrainOffsets(nextState, current.offsetX, current.offsetY),
      };
    });
  };

  const handleStageWheel = (event: WheelEvent<HTMLDivElement>) => {
    event.preventDefault();
    if (editor.busy) {
      return;
    }

    const zoomDelta = event.deltaY > 0 ? -0.08 : 0.08;
    handleZoomChange(editor.zoom + zoomDelta);
  };

  const handlePointerDown = (event: PointerEvent<HTMLDivElement>) => {
    if (editor.busy) {
      return;
    }

    dragStateRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      originOffsetX: editor.offsetX,
      originOffsetY: editor.offsetY,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const handlePointerMove = (event: PointerEvent<HTMLDivElement>) => {
    if (!dragStateRef.current || dragStateRef.current.pointerId !== event.pointerId) {
      return;
    }

    const deltaX = event.clientX - dragStateRef.current.startX;
    const deltaY = event.clientY - dragStateRef.current.startY;
    setEditor((current) => ({
      ...current,
      ...constrainOffsets(
        current,
        dragStateRef.current ? dragStateRef.current.originOffsetX + deltaX : current.offsetX,
        dragStateRef.current ? dragStateRef.current.originOffsetY + deltaY : current.offsetY,
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

  const handleAvatarUpload = async () => {
    if (!editor.sourceDataUrl) {
      return;
    }

    setEditor((current) => ({ ...current, busy: true }));
    try {
      const blob = await buildCroppedAvatarBlob(editor);
      const file = new File([blob], editor.sourceName.replace(/\.(png|jpe?g)$/i, "") + ".jpg", { type: "image/jpeg" });
      const payload = new FormData();
      payload.append("file", file);
      const response = await apiRequest<MentorAvatarUploadResult>("/mentor/profile/avatar", {
        method: "POST",
        body: payload,
      });
      const nextPreviewUrl = await primeMentorAvatarCache({
        userId,
        avatarUrl: response.avatarUrl,
        blob,
      });
      setPreviewUrl(nextPreviewUrl);
      onAvatarUploaded(response);
      setEditor(createAvatarEditorState());
      onToast(avatarConfigured ? "头像已经更新。" : "头像已经上传。");
    } catch (error) {
      setEditor((current) => ({ ...current, busy: false }));
      onToast(buildErrorMessage(error, "头像上传失败，请稍后再试。"), "error");
    }
  };

  const cropFrameInsetX = (EDITOR_STAGE_WIDTH - CROP_VIEWPORT_SIZE) / 2;
  const cropFrameInsetY = (EDITOR_STAGE_HEIGHT - CROP_VIEWPORT_SIZE) / 2;
  const editorOverlay = typeof document === "undefined"
    ? null
    : createPortal(
      <AnimatePresence>
        {editor.isOpen ? (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-[90] overflow-y-auto bg-[rgba(236,253,245,0.56)] px-4 py-6 backdrop-blur-md"
            onClick={(event) => {
              if (event.target === event.currentTarget) {
                closeEditor();
              }
            }}
          >
            <div
              className="flex min-h-full items-center justify-center"
              onClick={(event) => {
                if (event.target === event.currentTarget) {
                  closeEditor();
                }
              }}
            >
              <motion.div
                role="dialog"
                aria-modal="true"
                aria-label="导师头像编辑器"
                initial={{ opacity: 0, scale: 0.96, y: 20 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.97, y: 18 }}
                transition={{ type: "spring", bounce: 0.18, duration: 0.42 }}
                className="relative w-full max-w-[66rem] overflow-hidden rounded-[2.4rem] border border-white/80 bg-[linear-gradient(145deg,rgba(255,255,255,0.98),rgba(244,252,249,0.98)_38%,rgba(236,253,245,0.98))] p-5 text-slate-900 shadow-[0_32px_90px_rgba(15,118,110,0.18)] lg:p-6"
              >
                <div className="pointer-events-none absolute inset-x-0 top-0 h-40 bg-[radial-gradient(circle_at_top,rgba(16,185,129,0.16),transparent_62%)]" />
                <div className="pointer-events-none absolute -left-12 bottom-0 h-48 w-48 rounded-full bg-teal-300/18 blur-3xl" />

                <div className="relative z-10">
                  <div className="flex flex-col gap-4 border-b border-slate-200/80 pb-5 lg:flex-row lg:items-end lg:justify-between">
                    <div>
                      <div className="flex items-center gap-3">
                        <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-50 text-emerald-600 shadow-sm">
                          <Scissors size={22} />
                        </div>
                        <div>
                          <div className="text-sm font-bold text-slate-500">头像设置</div>
                          <h3 className="mt-1 text-3xl font-semibold text-slate-900 lg:text-[2.1rem]">调整导师头像</h3>
                        </div>
                      </div>
                      <p className="mt-4 max-w-2xl text-base leading-8 text-slate-600">
                        调整好位置后直接保存，新的头像会同步到资料页和学生侧预览展示。
                      </p>
                    </div>
                    <div className="flex flex-wrap items-center gap-3">
                      <button
                        type="button"
                        onClick={() => {
                          setEditor((current) => ({
                            ...current,
                            zoom: 1,
                            offsetX: 0,
                            offsetY: 0,
                          }));
                        }}
                        disabled={editor.busy}
                        className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-4 py-2.5 text-base font-semibold text-slate-700 transition-colors hover:border-slate-300 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        还原位置
                      </button>
                      <button
                        type="button"
                        onClick={openFilePicker}
                        disabled={editor.busy}
                        className="inline-flex items-center justify-center rounded-full border border-emerald-200 bg-emerald-50 px-4 py-2.5 text-base font-semibold text-emerald-700 transition-colors hover:border-emerald-300 hover:bg-emerald-100 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        <UploadCloud size={15} className="mr-2" />
                        重新选图
                      </button>
                    </div>
                  </div>

                  <div className="mt-6 grid gap-5 xl:justify-center xl:grid-cols-[40rem_20rem]">
                    <div
                      className="relative h-[440px] w-[640px] cursor-grab overflow-hidden rounded-[2rem] border border-slate-200/90 bg-[radial-gradient(circle_at_top,rgba(16,185,129,0.1),transparent_58%),linear-gradient(180deg,#fcfffe_0%,#ecfdf5_100%)] shadow-[0_24px_60px_rgba(148,163,184,0.18)] active:cursor-grabbing"
                      onWheel={handleStageWheel}
                      onPointerDown={handlePointerDown}
                      onPointerMove={handlePointerMove}
                      onPointerUp={handlePointerRelease}
                      onPointerCancel={handlePointerRelease}
                      onPointerLeave={handlePointerRelease}
                    >
                      <img
                        src={editor.sourceDataUrl}
                        alt="头像预览"
                        draggable={false}
                        className="pointer-events-none absolute left-1/2 top-1/2 max-w-none select-none"
                        style={{
                          width: editor.naturalWidth * getAvatarScale(editor),
                          height: editor.naturalHeight * getAvatarScale(editor),
                          transform: `translate(calc(-50% + ${editor.offsetX}px), calc(-50% + ${editor.offsetY}px))`,
                        }}
                      />
                      <div
                        className="pointer-events-none absolute rounded-[1.7rem]"
                        style={{
                          left: cropFrameInsetX,
                          top: cropFrameInsetY,
                          width: CROP_VIEWPORT_SIZE,
                          height: CROP_VIEWPORT_SIZE,
                          boxShadow: "0 0 0 9999px rgba(241, 245, 249, 0.82)",
                        }}
                      />
                      <div
                        className="pointer-events-none absolute rounded-[1.7rem] border border-emerald-300 shadow-[0_0_0_1px_rgba(110,231,183,0.3)]"
                        style={{
                          left: cropFrameInsetX,
                          top: cropFrameInsetY,
                          width: CROP_VIEWPORT_SIZE,
                          height: CROP_VIEWPORT_SIZE,
                        }}
                      />
                      <div
                        className="pointer-events-none absolute w-px bg-emerald-200/80"
                        style={{
                          left: EDITOR_STAGE_WIDTH / 2,
                          top: cropFrameInsetY,
                          height: CROP_VIEWPORT_SIZE,
                        }}
                      />
                      <div
                        className="pointer-events-none absolute h-px bg-emerald-200/80"
                        style={{
                          left: cropFrameInsetX,
                          top: EDITOR_STAGE_HEIGHT / 2,
                          width: CROP_VIEWPORT_SIZE,
                        }}
                      />
                    </div>

                    <div className="flex h-[440px] w-80 flex-col">
                      <div className="flex h-full flex-col rounded-[1.8rem] border border-slate-200/80 bg-white/92 p-5 shadow-[0_18px_45px_rgba(148,163,184,0.14)]">
                        <div className="text-base font-semibold text-slate-900">头像预览</div>
                        <div className="mt-4 flex items-center gap-4">
                          <div className="relative flex h-28 w-28 items-center justify-center overflow-hidden rounded-[2rem] bg-gradient-to-br from-emerald-100 via-teal-50 to-white text-3xl font-black text-slate-900 ring-4 ring-emerald-100 shadow-[0_18px_45px_rgba(16,185,129,0.14)]">
                            <img
                              src={editor.sourceDataUrl}
                              alt="头像效果预览"
                              draggable={false}
                              className="pointer-events-none absolute left-1/2 top-1/2 max-w-none select-none"
                              style={previewImageStyle}
                            />
                          </div>
                          <div className="min-w-0">
                            <div className="text-lg font-semibold text-slate-900">{heroName}</div>
                            <div className="mt-3 inline-flex rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1.5 text-sm font-semibold text-emerald-700">
                              缩放 {Math.round(editor.zoom * 100)}%
                            </div>
                          </div>
                        </div>

                        <div className="mt-6 inline-flex self-start rounded-full border border-slate-200 bg-slate-50 px-4 py-2 text-sm font-semibold text-slate-500">
                          拖动移动，滚轮缩放
                        </div>

                        <div className="mt-auto pt-4">
                          <div className="flex flex-col gap-3">
                            <button
                              type="button"
                              disabled={editor.busy}
                              onClick={closeEditor}
                              className="inline-flex w-full items-center justify-center rounded-2xl border border-slate-200 bg-white px-5 py-3.5 text-base font-semibold text-slate-700 transition-colors hover:border-slate-300 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                              取消
                            </button>
                            <button
                              type="button"
                              disabled={editor.busy}
                              onClick={() => void handleAvatarUpload()}
                              className="inline-flex w-full items-center justify-center rounded-2xl bg-emerald-600 px-6 py-3.5 text-base font-semibold text-white shadow-[0_18px_32px_rgba(16,185,129,0.22)] transition-transform hover:-translate-y-0.5 hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-70"
                            >
                              {editor.busy ? (
                                <>
                                  <RefreshCw size={16} className="mr-2 animate-spin" />
                                  正在保存
                                </>
                              ) : (
                                "保存头像"
                              )}
                            </button>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </motion.div>
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>,
      document.body,
    );

  return (
    <>
      <div className="flex w-[9.5rem] shrink-0 flex-col items-center gap-3 text-center">
        <div className="relative">
          <button
            type="button"
            onClick={openFilePicker}
            className="group relative flex h-32 w-32 items-center justify-center overflow-hidden rounded-[2.2rem] bg-gradient-to-br from-emerald-100 via-teal-50 to-white text-[2.2rem] font-black text-slate-900 shadow-[0_22px_55px_rgba(16,185,129,0.18)] transition-transform hover:-translate-y-1"
          >
            {previewUrl ? (
              <img src={previewUrl} alt={`${heroName} 的头像`} className="h-full w-full object-cover" />
            ) : (
              <span>{avatarLabel}</span>
            )}

            <div className="absolute inset-x-0 bottom-0 flex items-center justify-center gap-2 bg-slate-950/72 px-3 py-2 text-xs font-semibold text-white opacity-0 transition-opacity group-hover:opacity-100">
              <Camera size={14} />
              {avatarConfigured ? "修改头像" : "上传头像"}
            </div>
          </button>

          <div className={`absolute -right-2 -top-2 rounded-full border-4 p-1.5 shadow-sm ${getApprovalBadgeClassName(approvalStatus)}`}>
            <ShieldCheck size={18} />
          </div>
        </div>

        <div className="text-[11px] font-medium leading-5 text-slate-400">
          点击头像即可更新，支持 JPG / PNG
        </div>

        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png"
          className="hidden"
          onChange={(event) => void handleFileChange(event)}
        />
      </div>
      {editorOverlay}
    </>
  );
}
