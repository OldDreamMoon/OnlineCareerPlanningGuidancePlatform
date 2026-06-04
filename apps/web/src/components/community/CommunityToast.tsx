import {
  AlertCircle,
  CheckCircle2,
  Info,
  X,
} from "lucide-react";
import { AnimatePresence, motion } from "framer-motion";
import { useEffect } from "react";
import { joinClasses } from "./communityUtils";

export type CommunityToastState = {
  id: number;
  tone: "success" | "error" | "info";
  text: string;
};

type CommunityToastProps = {
  toast: CommunityToastState | null;
  onClose?: (toastId: number) => void;
};

function getToastMeta(tone: CommunityToastState["tone"]) {
  switch (tone) {
    case "success":
      return {
        icon: CheckCircle2,
        title: "处理完成",
        shellClassName: "border-emerald-200/90",
        iconWrapClassName: "bg-emerald-100 text-emerald-700",
        titleClassName: "text-emerald-700",
      };
    case "error":
      return {
        icon: AlertCircle,
        title: "操作未完成",
        shellClassName: "border-rose-200/90",
        iconWrapClassName: "bg-rose-100 text-rose-700",
        titleClassName: "text-rose-700",
      };
    case "info":
    default:
      return {
        icon: Info,
        title: "最新提醒",
        shellClassName: "border-sky-200/90",
        iconWrapClassName: "bg-sky-100 text-sky-700",
        titleClassName: "text-sky-700",
      };
  }
}

export default function CommunityToast({ toast, onClose }: CommunityToastProps) {
  useEffect(() => {
    if (!toast || !onClose || typeof window === "undefined") {
      return undefined;
    }

    const timeoutId = window.setTimeout(() => {
      onClose(toast.id);
    }, 3200);

    return () => {
      window.clearTimeout(timeoutId);
    };
  }, [onClose, toast]);

  return (
    <div className="pointer-events-none fixed inset-x-0 top-[6.2rem] z-[80] flex justify-center px-4">
      <AnimatePresence>
        {toast ? (
          <motion.div
            key={toast.id}
            initial={{ opacity: 0, y: -12, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -10, scale: 0.985 }}
            transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
            className="pointer-events-auto w-full max-w-[34rem]"
          >
            {(() => {
              const meta = getToastMeta(toast.tone);
              const ToastIcon = meta.icon;

              return (
                <div
                  className={joinClasses(
                    "flex items-start gap-3 rounded-[1.45rem] border bg-white/96 px-4 py-3.5 shadow-[0_18px_40px_rgba(15,23,42,0.14)] backdrop-blur-xl",
                    meta.shellClassName,
                  )}
                >
                  <div
                    className={joinClasses(
                      "flex h-10 w-10 shrink-0 items-center justify-center rounded-full",
                      meta.iconWrapClassName,
                    )}
                  >
                    <ToastIcon size={18} />
                  </div>

                  <div className="min-w-0 flex-1">
                    <div className={joinClasses("text-sm font-bold tracking-[0.04em]", meta.titleClassName)}>
                      {meta.title}
                    </div>
                    <p className="mt-1 text-[15px] leading-6 text-slate-600">{toast.text}</p>
                  </div>

                  <button
                    type="button"
                    onClick={() => onClose?.(toast.id)}
                    className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-400 transition-colors hover:border-slate-300 hover:text-slate-700"
                    aria-label="关闭提醒"
                  >
                    <X size={16} />
                  </button>
                </div>
              );
            })()}
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}
