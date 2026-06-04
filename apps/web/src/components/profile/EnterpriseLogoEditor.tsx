import { Camera, Loader2 } from "lucide-react";
import { useEffect, useRef, useState, type ChangeEvent } from "react";
import { ApiClientError, apiRequest } from "../../lib/apiClient";
import { buildEnterpriseLogoUrl } from "../../lib/enterpriseLogo";
import { formatDateTime } from "../../lib/formatters";
import EnterpriseIdentityLogo from "../avatar/EnterpriseIdentityLogo";
import SquareImageCropperDialog from "./SquareImageCropperDialog";

const LOGO_SOURCE_LIMIT_BYTES = 6 * 1024 * 1024;
const LOGO_MIN_EDGE = 120;
const LOGO_TRIM_ALPHA_THRESHOLD = 16;
const LOGO_TRIM_WHITE_THRESHOLD = 246;
const LOGO_TRIM_PADDING = 0;

type ToastTone = "success" | "error" | "info";

export type EnterpriseLogoUploadResult = {
  uploaded: boolean;
  logoConfigured: boolean;
  contentType: string | null;
  sizeBytes: number;
  updatedAt: number | string | null;
  logoUrl: string | null;
};

type LogoCropSource = {
  sourceDataUrl: string;
  sourceName: string;
  naturalWidth: number;
  naturalHeight: number;
};

function buildErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiClientError && error.message) {
    return error.message;
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}

function normalizeSelectedFile(file: File | null | undefined) {
  if (!file) {
    return null;
  }
  if (!["image/jpeg", "image/png"].includes(file.type)) {
    throw new Error("企业 Logo 仅支持 JPG 或 PNG 图片。");
  }
  if (file.size > LOGO_SOURCE_LIMIT_BYTES) {
    throw new Error("企业 Logo 原图过大，请控制在 6 MB 以内。");
  }
  return file;
}

async function readFileAsDataUrl(file: File) {
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

function isTrimBackgroundPixel(
  red: number,
  green: number,
  blue: number,
  alpha: number,
  trimWhiteBackground: boolean,
) {
  return alpha <= LOGO_TRIM_ALPHA_THRESHOLD
    || (trimWhiteBackground
      && red >= LOGO_TRIM_WHITE_THRESHOLD
      && green >= LOGO_TRIM_WHITE_THRESHOLD
      && blue >= LOGO_TRIM_WHITE_THRESHOLD);
}

async function trimLogoSourceDataUrl(
  sourceDataUrl: string,
  options?: { trimWhiteBackground?: boolean },
) {
  const image = await loadImageElement(sourceDataUrl);
  const canvas = document.createElement("canvas");
  canvas.width = image.naturalWidth;
  canvas.height = image.naturalHeight;
  const context = canvas.getContext("2d");
  const trimWhiteBackground = options?.trimWhiteBackground ?? false;

  if (!context) {
    return {
      sourceDataUrl,
      naturalWidth: image.naturalWidth,
      naturalHeight: image.naturalHeight,
    };
  }

  context.drawImage(image, 0, 0);
  const imageData = context.getImageData(0, 0, canvas.width, canvas.height);
  const { data, width, height } = imageData;

  const rowHasContent = (row: number) => {
    for (let column = 0; column < width; column += 1) {
      const pixelIndex = (row * width + column) * 4;
      if (!isTrimBackgroundPixel(
        data[pixelIndex] ?? 0,
        data[pixelIndex + 1] ?? 0,
        data[pixelIndex + 2] ?? 0,
        data[pixelIndex + 3] ?? 0,
        trimWhiteBackground,
      )) {
        return true;
      }
    }
    return false;
  };

  const columnHasContent = (column: number) => {
    for (let row = 0; row < height; row += 1) {
      const pixelIndex = (row * width + column) * 4;
      if (!isTrimBackgroundPixel(
        data[pixelIndex] ?? 0,
        data[pixelIndex + 1] ?? 0,
        data[pixelIndex + 2] ?? 0,
        data[pixelIndex + 3] ?? 0,
        trimWhiteBackground,
      )) {
        return true;
      }
    }
    return false;
  };

  let top = 0;
  while (top < height && !rowHasContent(top)) {
    top += 1;
  }

  let bottom = height - 1;
  while (bottom >= top && !rowHasContent(bottom)) {
    bottom -= 1;
  }

  let left = 0;
  while (left < width && !columnHasContent(left)) {
    left += 1;
  }

  let right = width - 1;
  while (right >= left && !columnHasContent(right)) {
    right -= 1;
  }

  if (top === 0 && left === 0 && right === width - 1 && bottom === height - 1) {
    return {
      sourceDataUrl,
      naturalWidth: image.naturalWidth,
      naturalHeight: image.naturalHeight,
    };
  }

  if (right < left || bottom < top) {
    return {
      sourceDataUrl,
      naturalWidth: image.naturalWidth,
      naturalHeight: image.naturalHeight,
    };
  }

  const trimmedLeft = Math.max(left - LOGO_TRIM_PADDING, 0);
  const trimmedTop = Math.max(top - LOGO_TRIM_PADDING, 0);
  const trimmedRight = Math.min(right + LOGO_TRIM_PADDING, width - 1);
  const trimmedBottom = Math.min(bottom + LOGO_TRIM_PADDING, height - 1);
  const trimmedWidth = trimmedRight - trimmedLeft + 1;
  const trimmedHeight = trimmedBottom - trimmedTop + 1;
  const trimmedCanvas = document.createElement("canvas");
  trimmedCanvas.width = trimmedWidth;
  trimmedCanvas.height = trimmedHeight;
  const trimmedContext = trimmedCanvas.getContext("2d");

  if (!trimmedContext) {
    return {
      sourceDataUrl,
      naturalWidth: image.naturalWidth,
      naturalHeight: image.naturalHeight,
    };
  }

  trimmedContext.drawImage(
    canvas,
    trimmedLeft,
    trimmedTop,
    trimmedWidth,
    trimmedHeight,
    0,
    0,
    trimmedWidth,
    trimmedHeight,
  );

  return {
    sourceDataUrl: trimmedCanvas.toDataURL("image/png"),
    naturalWidth: trimmedWidth,
    naturalHeight: trimmedHeight,
  };
}

export default function EnterpriseLogoEditor({
  companyName,
  logoUrl,
  logoConfigured,
  logoUpdatedAt,
  onLogoUploaded,
  onToast,
}: {
  companyName: string | null | undefined;
  logoUrl?: string | null;
  logoConfigured: boolean;
  logoUpdatedAt?: number | string | null;
  onLogoUploaded: (payload: EnterpriseLogoUploadResult) => void;
  onToast: (message: string, tone?: ToastTone) => void;
}) {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const optimisticPreviewUrlRef = useRef<string | null>(null);
  const normalizedLogoUrl = buildEnterpriseLogoUrl(logoUrl, logoUpdatedAt);
  const [previewUrl, setPreviewUrl] = useState<string | null>(normalizedLogoUrl);
  const [uploading, setUploading] = useState(false);
  const [cropSource, setCropSource] = useState<LogoCropSource | null>(null);

  useEffect(() => {
    setPreviewUrl(normalizedLogoUrl);
  }, [normalizedLogoUrl]);

  useEffect(() => () => {
    if (optimisticPreviewUrlRef.current) {
      URL.revokeObjectURL(optimisticPreviewUrlRef.current);
      optimisticPreviewUrlRef.current = null;
    }
  }, []);

  const releaseOptimisticPreview = () => {
    if (!optimisticPreviewUrlRef.current) {
      return;
    }
    URL.revokeObjectURL(optimisticPreviewUrlRef.current);
    optimisticPreviewUrlRef.current = null;
  };

  const handleFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const selectedFile = event.target.files?.[0] ?? null;
    event.target.value = "";

    if (!selectedFile) {
      return;
    }

    let file: File;
    try {
      file = normalizeSelectedFile(selectedFile) as File;
    } catch (error) {
      onToast(buildErrorMessage(error, "企业 Logo 文件校验失败。"), "error");
      return;
    }

    try {
      const originalSourceDataUrl = await readFileAsDataUrl(file);
      const trimmedSource = await trimLogoSourceDataUrl(originalSourceDataUrl, {
        trimWhiteBackground: file.type === "image/jpeg",
      });
      if (Math.min(trimmedSource.naturalWidth, trimmedSource.naturalHeight) < LOGO_MIN_EDGE) {
        onToast(`Logo 尺寸太小，请选择至少 ${LOGO_MIN_EDGE} x ${LOGO_MIN_EDGE} 的图片。`, "error");
        return;
      }
      setCropSource({
        sourceDataUrl: trimmedSource.sourceDataUrl,
        sourceName: file.name,
        naturalWidth: trimmedSource.naturalWidth,
        naturalHeight: trimmedSource.naturalHeight,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : "企业 Logo 读取失败，请重新选择图片。";
      onToast(message, "error");
    }
  };

  const openFilePicker = () => {
    if (uploading) {
      return;
    }
    fileInputRef.current?.click();
  };

  const handleCroppedUpload = async (file: File) => {
    releaseOptimisticPreview();
    const optimisticPreviewUrl = URL.createObjectURL(file);
    optimisticPreviewUrlRef.current = optimisticPreviewUrl;
    setPreviewUrl(optimisticPreviewUrl);
    setUploading(true);

    try {
      const payload = new FormData();
      payload.append("file", file);
      const response = await apiRequest<EnterpriseLogoUploadResult>("/profiles/enterprises/me/logo", {
        method: "POST",
        body: payload,
      });
      releaseOptimisticPreview();
      setPreviewUrl(buildEnterpriseLogoUrl(response.logoUrl, response.updatedAt));
      setCropSource(null);
      onLogoUploaded(response);
      onToast("企业 Logo 已更新。");
    } catch (error) {
      releaseOptimisticPreview();
      setPreviewUrl(normalizedLogoUrl);
      onToast(buildErrorMessage(error, "企业 Logo 上传失败，请稍后重试。"), "error");
      throw error;
    } finally {
      setUploading(false);
    }
  };

  return (
    <>
      <div className="rounded-[1.6rem] border border-slate-200/80 bg-slate-50/75 p-5">
        <div className="flex items-start justify-between gap-3">
          <div className="text-lg font-bold text-slate-900">企业 Logo</div>
          <span className={`inline-flex items-center rounded-full border px-2.5 py-1 text-[12px] font-semibold ${logoConfigured ? "border-emerald-200 bg-emerald-50 text-emerald-700" : "border-slate-200 bg-white text-slate-600"}`}>
            {logoConfigured ? "已配置" : "待上传"}
          </span>
        </div>

        <div className="mt-5 rounded-[1.4rem] border border-slate-200/80 bg-white/85 p-4">
          <div className="flex flex-wrap items-center gap-4 sm:flex-nowrap">
            <div className="shrink-0">
              <button
                type="button"
                onClick={openFilePicker}
                disabled={uploading}
                className="group relative block overflow-hidden rounded-[1.35rem] shadow-sm transition-transform hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-70"
              >
                <EnterpriseIdentityLogo
                  companyName={companyName}
                  logoUrl={previewUrl}
                  className="h-24 w-24 rounded-[1.35rem] border border-slate-200/80 bg-white"
                  imageClassName="p-0"
                  fallbackClassName="bg-gradient-to-br from-slate-200 to-slate-300 text-slate-700"
                  textClassName="text-2xl"
                />
                <div className="absolute inset-x-0 bottom-0 flex items-center justify-center gap-2 bg-slate-950/72 px-3 py-2 text-xs font-semibold text-white opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100">
                  {uploading ? <Loader2 size={14} className="animate-spin" /> : <Camera size={14} />}
                  {uploading ? "上传中..." : logoConfigured ? "更换 Logo" : "上传 Logo"}
                </div>
              </button>
            </div>

            <div className="min-w-0 flex-1">
              <div className="text-base font-semibold text-slate-900">{companyName?.trim() || "当前企业"}</div>
              <div className="mt-2 text-[12px] text-slate-400">
                {logoUpdatedAt ? `最近更新：${formatDateTime(logoUpdatedAt)}` : "尚未上传"}
              </div>
            </div>

            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png"
              className="hidden"
              onChange={(event) => {
                void handleFileChange(event);
              }}
            />
          </div>
        </div>
      </div>

      <SquareImageCropperDialog
        open={!!cropSource}
        sourceDataUrl={cropSource?.sourceDataUrl || ""}
        sourceName={cropSource?.sourceName || "logo"}
        naturalWidth={cropSource?.naturalWidth || 0}
        naturalHeight={cropSource?.naturalHeight || 0}
        title="调整 Logo"
        subjectName={companyName?.trim() || "当前企业"}
        previewTitle="预览"
        confirmLabel="保存 Logo"
        busyLabel="正在保存"
        stageAlt="Logo 裁剪预览"
        previewAlt="Logo 效果预览"
        onCancel={() => {
          if (!uploading) {
            setCropSource(null);
          }
        }}
        onPickAnother={openFilePicker}
        onConfirm={handleCroppedUpload}
      />
    </>
  );
}
