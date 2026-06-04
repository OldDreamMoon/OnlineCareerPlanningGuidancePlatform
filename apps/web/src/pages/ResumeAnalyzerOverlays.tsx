import { AnimatePresence, motion } from "framer-motion";
import {
  AlertTriangle,
  Award,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  History,
  RefreshCw,
  Trash2,
  X,
  Zap,
} from "lucide-react";
import type { ReactNode } from "react";
import { formatCount, formatRelativeTime as formatRelativeTimeByBrowserTimezone } from "../lib/formatters";

type TimeValue = number | string;

type StreamPreviewState = {
  traceId: string | null;
  message: string | null;
  summary: string | null;
  strengths: string[] | null;
  risks: string[] | null;
  suggestions: string[] | null;
};

type ResumeAnalyzeResponse = {
  strengths: string[];
  risks: string[];
};

type ResumeHistoryItem = {
  id: number;
  summary: string;
  pointsConsumed: number;
  createdAt: TimeValue;
};

type ResumeHistoryResponse = {
  records: ResumeHistoryItem[];
  total: number;
  page: number;
  size: number;
};

type HistoryOverlayProps = {
  kind: "history";
  isOpen: boolean;
  currentRecordId: number | null;
  historyData: ResumeHistoryResponse | null;
  loading: boolean;
  errorMessage: string | null;
  page: number;
  totalPages: number;
  deletingRecordId: number | null;
  onClose: () => void;
  onRefresh: () => void;
  onPageChange: (page: number) => void;
  onViewRecord: (recordId: number) => void;
  onDeleteRecord: (recordId: number) => void;
};

type SuccessOverlayProps = {
  kind: "success";
  open: boolean;
  isAnalyzing: boolean;
  streamPreview: StreamPreviewState | null;
  result: ResumeAnalyzeResponse | null;
  scoreLabel: string;
  scoreTextClassName: string;
  onClose: () => void;
  onGoToReview: () => void;
};

type DraftOverlayProps = {
  kind: "draft";
  open: boolean;
  savedAt: string | null;
  showPdfRestoreHint: boolean;
  onContinue: () => void;
  onClear: () => void;
};

type ResumeAnalyzerOverlayProps =
  | HistoryOverlayProps
  | SuccessOverlayProps
  | DraftOverlayProps;

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function Button({
  className = "",
  variant = "default",
  size = "default",
  children,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  className?: string;
  variant?: "default" | "secondary" | "outline" | "ghost" | "danger";
  size?: "default" | "sm" | "lg" | "icon";
}) {
  const baseStyle =
    "inline-flex items-center justify-center rounded-full text-sm font-semibold transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 disabled:pointer-events-none disabled:opacity-50 hover:-translate-y-0.5 active:scale-[0.98]";

  const variants = {
    default:
      "bg-gradient-to-r from-indigo-500 to-indigo-600 text-white hover:from-indigo-600 hover:to-indigo-700 shadow-[0_12px_24px_rgba(79,70,229,0.25)]",
    secondary: "bg-slate-100 text-slate-900 hover:bg-slate-200 shadow-sm",
    outline:
      "border border-slate-200 bg-white text-slate-600 hover:border-indigo-200 hover:text-indigo-600 shadow-sm",
    ghost:
      "text-slate-600 hover:bg-slate-100 hover:text-slate-900 shadow-none hover:shadow-none hover:translate-y-0 active:scale-100",
    danger: "bg-rose-50 text-rose-600 hover:bg-rose-100",
  };

  const sizes = {
    default: "h-11 px-5 py-2.5",
    sm: "h-9 px-4 text-xs",
    lg: "h-12 px-8 text-base",
    icon: "h-11 w-11",
  };

  return (
    <button
      {...props}
      className={joinClasses(baseStyle, variants[variant], sizes[size], className)}
    >
      {children}
    </button>
  );
}

function Badge({
  children,
  variant = "default",
  className = "",
}: {
  children: ReactNode;
  variant?: "default" | "success" | "warning" | "outline";
  className?: string;
}) {
  const variants = {
    default: "bg-indigo-50 text-indigo-700 border-indigo-200",
    success: "bg-emerald-50 text-emerald-700 border-emerald-200",
    warning: "bg-amber-50 text-amber-700 border-amber-200",
    outline: "border-slate-200 text-slate-600 bg-white",
  };

  return (
    <span
      className={joinClasses(
        "inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold shadow-sm transition-colors",
        variants[variant],
        className,
      )}
    >
      {children}
    </span>
  );
}

function formatRelativeTime(value: TimeValue | null) {
  return formatRelativeTimeByBrowserTimezone(value, "刚刚");
}

function getHistoryRecordTitle(record: ResumeHistoryItem) {
  const firstClause = record.summary.replace(/\s+/g, " ").split(/[。；;]/)[0].trim();
  return firstClause ? firstClause.slice(0, 20) : `诊断记录 #${record.id}`;
}

function HistoryDrawer(props: HistoryOverlayProps) {
  const {
    isOpen,
    currentRecordId,
    historyData,
    loading,
    errorMessage,
    page,
    totalPages,
    deletingRecordId,
    onClose,
    onRefresh,
    onPageChange,
    onViewRecord,
    onDeleteRecord,
  } = props;

  return (
    <AnimatePresence>
      {isOpen ? (
        <div className="fixed inset-0 z-[120] flex justify-start">
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute inset-0 bg-slate-900/20 backdrop-blur-sm"
            onClick={onClose}
          />
          <motion.div
            initial={{ x: "-100%" }}
            animate={{ x: 0 }}
            exit={{ x: "-100%" }}
            transition={{ type: "spring", bounce: 0, duration: 0.4 }}
            className="relative flex h-full w-full max-w-md flex-col border-r border-slate-200 bg-slate-50"
          >
            <div className="flex items-center justify-between border-b border-slate-100 bg-white px-6 py-5">
              <div>
                <h2 className="flex items-center gap-2 text-xl font-bold text-slate-900">
                  <History className="text-indigo-600" size={22} />
                  页内历史速览
                </h2>
                <p className="mt-1 text-xs text-slate-400">正式历史回看请优先使用 AI 复盘中心，这里保留为当前页面内的兼容速览入口</p>
              </div>
              <button
                type="button"
                onClick={onClose}
                className="rounded-full p-2 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
              >
                <X size={20} />
              </button>
            </div>

            <div className="flex items-center justify-between gap-3 border-b border-slate-100 bg-white px-4 py-3">
              <Button size="sm" variant="outline" onClick={onRefresh}>
                <RefreshCw size={14} className={joinClasses("mr-2", loading && "animate-spin")} />
                刷新
              </Button>
              <div className="text-xs font-medium text-slate-500">
                第 {formatCount(page)} / {formatCount(totalPages)} 页
              </div>
            </div>

            <div className="flex-1 space-y-3 overflow-y-auto p-4">
              {loading && !historyData ? (
                Array.from({ length: 4 }).map((_, index) => (
                  <div
                    key={index}
                    className="h-28 rounded-[1.25rem] border border-slate-200 bg-white/80"
                  />
                ))
              ) : errorMessage ? (
                <div className="rounded-[1.5rem] border border-rose-100 bg-rose-50 px-4 py-4 text-sm leading-7 text-rose-700">
                  {errorMessage}
                </div>
              ) : historyData?.records.length ? (
                historyData.records.map((item) => (
                  <div
                    key={item.id}
                    className={joinClasses(
                      "group rounded-xl border bg-white p-4 transition-all",
                      currentRecordId === item.id
                        ? "border-indigo-400 ring-1 ring-indigo-400/20 shadow-sm"
                        : "border-slate-200 hover:border-indigo-200 hover:shadow-sm",
                    )}
                  >
                    <div className="mb-2 flex items-start justify-between gap-3">
                      <h4 className="text-base font-bold text-slate-800">
                        {getHistoryRecordTitle(item)}
                      </h4>
                      <span className="rounded-md bg-slate-100 px-2 py-1 text-xs text-slate-400">
                        {formatRelativeTime(item.createdAt)}
                      </span>
                    </div>
                    <p className="mb-4 line-clamp-2 text-sm text-slate-600">
                      {item.summary || "该记录暂无摘要。"}
                    </p>

                    <div className="flex items-center justify-between border-t border-slate-100 pt-3">
                      <div className="flex items-center gap-2">
                        <Badge variant="outline" className="text-[10px] text-slate-500">
                          记录 #{item.id}
                        </Badge>
                        <span className="flex items-center text-[10px] text-slate-400">
                          <Zap size={10} className="mr-0.5" />
                          {item.pointsConsumed > 0 ? `${item.pointsConsumed} 积分` : "免费"}
                        </span>
                      </div>
                      <div className="flex items-center gap-2 opacity-100 transition-opacity md:opacity-0 group-hover:opacity-100">
                        <button
                          type="button"
                          onClick={() => onDeleteRecord(item.id)}
                          disabled={deletingRecordId === item.id}
                          className="rounded-md p-1.5 text-slate-400 transition-colors hover:bg-rose-50 hover:text-rose-500 disabled:opacity-40"
                        >
                          <Trash2 size={14} />
                        </button>
                        <Button
                          size="sm"
                          variant={currentRecordId === item.id ? "secondary" : "default"}
                          className="h-7 px-3 text-xs"
                          onClick={() => onViewRecord(item.id)}
                        >
                          {currentRecordId === item.id ? "当前查看中" : "查看详情"}
                        </Button>
                      </div>
                    </div>
                  </div>
                ))
              ) : (
                <div className="rounded-[1.5rem] border border-dashed border-slate-200 bg-white/70 px-4 py-5 text-sm leading-7 text-slate-500">
                  你还没有简历历史记录。先完成一轮分析，这里会自动写入历史并支持回看。
                </div>
              )}
            </div>

            <div className="border-t border-slate-100 bg-white px-4 py-4">
              <div className="mb-3 flex items-center justify-between gap-3">
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => onPageChange(Math.max(page - 1, 1))}
                  disabled={page <= 1}
                >
                  <ChevronLeft size={14} className="mr-1.5" />
                  上一页
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => onPageChange(Math.min(page + 1, totalPages))}
                  disabled={page >= totalPages}
                >
                  下一页
                  <ChevronRight size={14} className="ml-1.5" />
                </Button>
              </div>
              <p className="text-center text-xs text-slate-400">
                系统最多自动保存最近 30 天的诊断记录
              </p>
            </div>
          </motion.div>
        </div>
      ) : null}
    </AnimatePresence>
  );
}

function SuccessModal(props: SuccessOverlayProps) {
  const {
    open,
    isAnalyzing,
    streamPreview,
    result,
    scoreLabel,
    scoreTextClassName,
    onClose,
    onGoToReview,
  } = props;

  return (
    <AnimatePresence>
      {open ? (
        <div className="fixed inset-0 z-[100] flex items-center justify-center px-4">
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute inset-0 bg-slate-900/30 backdrop-blur-sm"
            onClick={() => {
              if (!isAnalyzing) {
                onClose();
              }
            }}
          />

          <motion.div
            initial={{ opacity: 0, scale: 0.95, y: 15 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 15 }}
            transition={{ type: "spring", bounce: 0.25, duration: 0.5 }}
            className="relative w-full max-w-md overflow-hidden rounded-3xl border border-slate-100 bg-white shadow-2xl"
          >
            <div className="h-3 bg-gradient-to-r from-indigo-500 via-teal-400 to-emerald-400" />

            {!isAnalyzing ? (
              <button
                type="button"
                onClick={onClose}
                className="absolute right-5 top-6 rounded-full bg-slate-50 p-1.5 text-slate-400 transition-colors hover:text-slate-600"
              >
                <X size={18} />
              </button>
            ) : null}

            <div className="p-8">
              <AnimatePresence mode="wait">
                {isAnalyzing ? (
                  <motion.div
                    key="analyzing"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    className="flex flex-col items-center justify-center py-6"
                  >
                    <motion.div
                      animate={{ rotate: 360 }}
                      transition={{ repeat: Number.POSITIVE_INFINITY, duration: 1.5, ease: "linear" }}
                      className="mb-6 h-16 w-16 rounded-full border-4 border-indigo-100 border-t-indigo-600"
                    />
                    <h3 className="mb-2 text-center text-2xl font-bold text-slate-900">
                      正在深度诊断...
                    </h3>
                    <p className="text-center text-sm text-slate-500">
                      {streamPreview?.message || "AI 正在逐字解析你的简历，并比对目标岗位要求"}
                    </p>
                    {streamPreview?.summary ? (
                      <div className="mt-5 rounded-2xl border border-slate-100 bg-slate-50/80 px-4 py-4 text-sm leading-7 text-slate-600">
                        <span className="mr-2 font-semibold text-indigo-600">阶段摘要</span>
                        {streamPreview.summary}
                      </div>
                    ) : null}
                  </motion.div>
                ) : (
                  <motion.div
                    key="success"
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.1 }}
                  >
                    <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-2xl bg-emerald-100 text-emerald-600 shadow-inner">
                      <CheckCircle2 size={32} />
                    </div>

                    <h3 className="mb-2 text-center text-2xl font-bold text-slate-900">
                      诊断分析完成
                    </h3>
                    <p className="mb-8 text-center text-sm text-slate-500">
                      已生成本次诊断摘要，可以继续查看详细结果
                    </p>

                    <div className="mb-8 flex items-center justify-around rounded-2xl border border-slate-100 bg-slate-50 p-5 shadow-sm">
                      <div className="flex-1 text-center">
                        <div className={joinClasses("mb-1 text-4xl font-black", scoreTextClassName)}>
                          {scoreLabel}
                        </div>
                        <div className="text-xs font-medium text-slate-500">推荐度</div>
                      </div>
                      <div className="h-12 w-px bg-slate-200" />
                      <div className="flex-1 text-center">
                        <div className="mb-1 flex items-baseline justify-center gap-1 text-2xl font-bold text-slate-700">
                          {formatCount(result?.strengths.length ?? 0)}
                          <span className="text-xs font-normal text-slate-400">处</span>
                        </div>
                        <div className="flex items-center justify-center text-xs font-medium text-slate-500">
                          <Award size={12} className="mr-1 text-emerald-500" />
                          亮点
                        </div>
                      </div>
                      <div className="h-12 w-px bg-slate-200" />
                      <div className="flex-1 text-center">
                        <div className="mb-1 flex items-baseline justify-center gap-1 text-2xl font-bold text-slate-700">
                          {formatCount(result?.risks.length ?? 0)}
                          <span className="text-xs font-normal text-slate-400">处</span>
                        </div>
                        <div className="flex items-center justify-center text-xs font-medium text-slate-500">
                          <AlertTriangle size={12} className="mr-1 text-amber-500" />
                          风险点
                        </div>
                      </div>
                    </div>

                    <Button
                      size="lg"
                      className="w-full text-base font-bold shadow-md shadow-indigo-200"
                      onClick={onGoToReview}
                    >
                      查看详细诊断结果
                      <ChevronRight size={18} className="ml-1 opacity-70" />
                    </Button>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          </motion.div>
        </div>
      ) : null}
    </AnimatePresence>
  );
}

function DraftRecoveryModal(props: DraftOverlayProps) {
  const {
    open,
    savedAt,
    showPdfRestoreHint,
    onContinue,
    onClear,
  } = props;

  return (
    <AnimatePresence>
      {open ? (
        <div className="fixed inset-0 z-[105] flex items-center justify-center px-4">
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute inset-0 bg-slate-900/30 backdrop-blur-sm"
          />

          <motion.div
            initial={{ opacity: 0, scale: 0.96, y: 18 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: 18 }}
            transition={{ type: "spring", bounce: 0.2, duration: 0.42 }}
            className="relative w-full max-w-lg overflow-hidden rounded-[2rem] border border-slate-100 bg-white shadow-[0_28px_80px_rgba(15,23,42,0.18)]"
          >
            <div className="h-2 bg-gradient-to-r from-indigo-500 via-sky-400 to-teal-400" />

            <div className="p-7 sm:p-8">
              <div className="flex items-start gap-4">
                <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-[1.4rem] border border-indigo-100 bg-indigo-50 text-indigo-600 shadow-sm">
                  <History size={24} strokeWidth={2.2} />
                </div>
                <div>
                  <div className="text-[11px] font-bold text-slate-400">
                    Draft Recovery
                  </div>
                  <h3 className="mt-2 text-2xl font-black tracking-tight text-slate-900">
                    检测到你上次未完成的简历草稿
                  </h3>
                  <p className="mt-3 text-sm leading-7 text-slate-600">
                    我们已经帮你保留了目标语境、岗位信息和简历输入内容，你可以直接继续编辑，也可以清空后重新开始。
                  </p>
                </div>
              </div>

              <div className="mt-6 rounded-[1.5rem] border border-slate-100 bg-slate-50/80 p-5">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge variant="outline" className="bg-white">
                    最近保存
                  </Badge>
                  <span className="text-sm font-semibold text-slate-700">
                    {savedAt ? formatRelativeTime(savedAt) : "刚刚"}
                  </span>
                </div>
                <div className="mt-3 text-sm leading-7 text-slate-600">
                  返回后会直接恢复到上次填写位置，分析结果与历史记录不会受到影响。
                  {showPdfRestoreHint ? " 其中 PDF 文件只恢复了文件名，真正开始分析前仍需要重新上传。" : ""}
                </div>
              </div>

              <div className="mt-7 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                <Button variant="outline" className="sm:min-w-[132px]" onClick={onClear}>
                  清空草稿
                </Button>
                <Button className="sm:min-w-[148px]" onClick={onContinue}>
                  继续编辑
                </Button>
              </div>
            </div>
          </motion.div>
        </div>
      ) : null}
    </AnimatePresence>
  );
}

export default function ResumeAnalyzerOverlay(props: ResumeAnalyzerOverlayProps) {
  if (props.kind === "history") {
    return <HistoryDrawer {...props} />;
  }

  if (props.kind === "success") {
    return <SuccessModal {...props} />;
  }

  return <DraftRecoveryModal {...props} />;
}
