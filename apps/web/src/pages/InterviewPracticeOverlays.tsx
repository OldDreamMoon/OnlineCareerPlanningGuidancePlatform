import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  ArrowRight,
  Clock3,
  X,
} from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { buildQuery } from "../lib/apiClient";
import { formatDateTime } from "../lib/formatters";

type TimeValue = number | string;

type InterviewHistoryItem = {
  sessionId: string | null;
  summary: string;
  createdAt: TimeValue | null;
};

type PrepareSetupOverlayProps = {
  kind: "prepare-setup";
  open: boolean;
  title: string;
  description: string;
  onClose: () => void;
  widthClassName?: string;
  children: ReactNode;
};

type PrivacyNoticeOverlayProps = {
  kind: "privacy-notice";
  open: boolean;
  displayName: string;
  onDismissOnce: () => void;
  onDismissToday: () => void;
};

type ContinuableInterviewOverlayProps = {
  kind: "continuable-interview";
  open: boolean;
  record: InterviewHistoryItem;
  onClose: () => void;
  onContinue: () => void;
};

type SessionCreatingOverlayProps = {
  kind: "session-creating";
  open: boolean;
  sessionTitle: string;
  interviewTypeLabel: string;
  interviewerStyleLabel: string;
  difficultyLabel: string;
  answerModeLabel: string;
  prepMaterialCount: number;
};

type SummaryGeneratingOverlayProps = {
  kind: "summary-generating";
  open: boolean;
  sessionTitle: string;
  interviewerStyleLabel: string;
  answerModeLabel: string;
  replyRoundUsed: number;
};

type InterviewPracticeOverlayProps =
  | PrepareSetupOverlayProps
  | PrivacyNoticeOverlayProps
  | ContinuableInterviewOverlayProps
  | SessionCreatingOverlayProps
  | SummaryGeneratingOverlayProps;

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function PrepareSetupModal(props: PrepareSetupOverlayProps) {
  const {
    open,
    title,
    description,
    onClose,
    widthClassName = "max-w-5xl",
    children,
  } = props;

  return (
    <AnimatePresence>
      {open ? (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.16, ease: "easeOut" }}
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/36 px-4 py-8 backdrop-blur-sm sm:px-6"
          onClick={onClose}
        >
          <motion.div
            initial={{ opacity: 0, scale: 0.98, y: 8 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.985, y: 6 }}
            transition={{ duration: 0.18, ease: "easeOut" }}
            className={joinClasses(
              "w-full overflow-hidden rounded-[2.25rem] border border-white/80 bg-white/95 shadow-[0_30px_120px_rgba(15,23,42,0.22)]",
              widthClassName,
            )}
            onClick={(event) => event.stopPropagation()}
          >
            <div className="flex flex-wrap items-start justify-between gap-4 border-b border-slate-100 px-6 py-5 lg:px-7">
              <div>
                <div className="text-[11px] tracking-[0.22em] text-slate-400">设置详情</div>
                <h3 className="mt-2 text-2xl font-semibold text-slate-900">{title}</h3>
                <p className="mt-2 max-w-3xl text-sm leading-7 text-slate-500">{description}</p>
              </div>
              <button
                type="button"
                onClick={onClose}
                className="inline-flex h-11 w-11 items-center justify-center rounded-2xl border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:border-slate-300 hover:text-slate-700"
              >
                <X size={18} />
              </button>
            </div>

            <div className="max-h-[78vh] overflow-y-auto px-6 py-6 lg:px-7">
              {children}
            </div>
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}

function PrivacyNoticeModal(props: PrivacyNoticeOverlayProps) {
  const { open, displayName, onDismissOnce, onDismissToday } = props;

  return (
    <AnimatePresence>
      {open ? (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-950/45 px-4 py-8 backdrop-blur-sm sm:px-6">
          <motion.div
            initial={{ opacity: 0, scale: 0.98, y: 10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.985, y: 8 }}
            transition={{ duration: 0.18, ease: "easeOut" }}
            className="w-full max-w-2xl overflow-hidden rounded-[2.25rem] border border-white/80 bg-white/96 shadow-[0_30px_120px_rgba(15,23,42,0.24)]"
          >
            <div className="border-b border-slate-100 px-6 py-5 lg:px-7">
              <div className="inline-flex items-center gap-2 rounded-full border border-amber-200 bg-amber-50 px-3.5 py-1.5 text-[13px] font-semibold text-amber-700">
                <AlertCircle size={15} />
                隐私提醒
              </div>
              <h3 className="mt-4 text-2xl font-semibold text-slate-900">开始前先确认一条隐私原则</h3>
              <p className="mt-3 text-sm leading-7 text-slate-600">
                当前页面会默认使用“<strong className="font-semibold text-slate-900">{displayName}</strong>”作为匿名称呼。为了保护你自己，建议在简历、自我介绍、岗位补充材料和自由回答里，尽量不要保留真实姓名、手机号、邮箱、住址、证件号等敏感信息。
              </p>
            </div>

            <div className="px-6 py-6 lg:px-7">
              <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                <button
                  type="button"
                  onClick={onDismissOnce}
                  className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-5 py-3 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-slate-300 hover:text-slate-800"
                >
                  本次关闭
                </button>
                <button
                  type="button"
                  onClick={onDismissToday}
                  className="inline-flex items-center justify-center rounded-full bg-gradient-to-r from-amber-500 to-orange-500 px-5 py-3 text-sm font-semibold text-white shadow-[0_16px_36px_rgba(245,158,11,0.22)] transition-transform hover:-translate-y-0.5"
                >
                  今日关闭
                </button>
              </div>
            </div>
          </motion.div>
        </div>
      ) : null}
    </AnimatePresence>
  );
}

function ContinuableInterviewPromptModal(props: ContinuableInterviewOverlayProps) {
  const { open, record, onClose, onContinue } = props;

  return (
    <AnimatePresence>
      {open ? (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.16, ease: "easeOut" }}
          className="fixed inset-0 z-[55] flex items-center justify-center bg-slate-950/40 px-4 py-8 backdrop-blur-sm sm:px-6"
        >
          <motion.div
            initial={{ opacity: 0, scale: 0.98, y: 10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.985, y: 8 }}
            transition={{ duration: 0.18, ease: "easeOut" }}
            className="w-full max-w-3xl overflow-hidden rounded-[2.3rem] border border-white/80 bg-white/96 shadow-[0_30px_120px_rgba(15,23,42,0.24)]"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="border-b border-slate-100 px-6 py-5 lg:px-7">
              <div>
                <div className="inline-flex items-center rounded-full border border-teal-200 bg-teal-50 px-3.5 py-1.5 text-[12px] font-semibold text-teal-700">
                  <Clock3 size={14} className="mr-2" />
                  检测到进行中的面试
                </div>
                <h3 className="mt-4 text-[28px] font-semibold text-slate-900">你上一次的会话还没有结束</h3>
                <p className="mt-3 text-[15px] leading-8 text-slate-600">
                  如果刚才只是误退出或中途离开，不需要重新开一轮。当前会话记录仍然保留，可以直接回到进行页继续作答。
                </p>
              </div>
            </div>

            <div className="px-6 py-6 lg:px-7">
              <div className="rounded-[1.6rem] border border-slate-200/90 bg-[linear-gradient(135deg,rgba(240,253,250,0.92),rgba(255,255,255,0.98))] p-5 shadow-sm">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <div className="text-[11px] text-slate-400">当前会话</div>
                    <div className="mt-2 text-[16px] font-semibold text-slate-900">{record.summary}</div>
                    <div className="mt-2 text-sm text-slate-500">创建时间：{formatDateTime(record.createdAt)}</div>
                  </div>
                  <div className="rounded-full border border-teal-200 bg-white px-3 py-1 text-xs font-semibold text-teal-700 shadow-sm">
                    进行中
                  </div>
                </div>
              </div>

              <div className="mt-6 flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:justify-end">
                <button
                  type="button"
                  onClick={onClose}
                  className="inline-flex items-center justify-center rounded-[1.2rem] border border-slate-200 bg-white px-5 py-3 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-slate-300 hover:text-slate-800"
                >
                  继续新建这一轮
                </button>
                <Link
                  to={`/ai/history${buildQuery({ type: "interview", sessionId: record.sessionId })}`}
                  className="inline-flex items-center justify-center rounded-[1.2rem] border border-slate-200 bg-white px-5 py-3 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-teal-200 hover:text-teal-700"
                >
                  去复盘中心查看记录
                </Link>
                <button
                  type="button"
                  onClick={onContinue}
                  className="inline-flex items-center justify-center rounded-[1.2rem] bg-teal-600 px-5 py-3 text-sm font-semibold text-white shadow-[0_16px_32px_rgba(13,148,136,0.18)] transition-transform hover:-translate-y-0.5 hover:bg-teal-700"
                >
                  返回进行中的面试
                  <ArrowRight size={15} className="ml-2" />
                </button>
              </div>
            </div>
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}

function InterviewSessionCreatingModal(props: SessionCreatingOverlayProps) {
  const {
    open,
    sessionTitle,
    interviewTypeLabel,
    interviewerStyleLabel,
    difficultyLabel,
    answerModeLabel,
    prepMaterialCount,
  } = props;
  const statusItems = [
    { label: "练习主题", value: sessionTitle || "模拟面试" },
    { label: "面试类型", value: interviewTypeLabel },
    { label: "面试官风格", value: interviewerStyleLabel },
    { label: "练习强度", value: difficultyLabel },
    { label: "作答方式", value: answerModeLabel },
    { label: "带入资料", value: prepMaterialCount > 0 ? `${prepMaterialCount} 项` : "基础语境" },
  ];

  return (
    <AnimatePresence>
      {open ? (
        <div className="fixed inset-0 z-[70] flex items-center justify-center px-4">
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute inset-0 bg-slate-900/28 backdrop-blur-sm"
          />

          <motion.div
            initial={{ opacity: 0, scale: 0.96, y: 18 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: 18 }}
            transition={{ type: "spring", bounce: 0.2, duration: 0.42 }}
            className="relative w-full max-w-xl overflow-hidden rounded-[2.2rem] border border-white/80 bg-white shadow-[0_30px_100px_rgba(15,23,42,0.20)]"
          >
            <div className="h-3 bg-gradient-to-r from-teal-500 via-sky-400 to-indigo-500" />

            <div className="p-8">
              <div className="flex flex-col items-center text-center">
                <motion.div
                  animate={{ rotate: 360 }}
                  transition={{ repeat: Number.POSITIVE_INFINITY, duration: 1.6, ease: "linear" }}
                  className="mb-6 flex h-16 w-16 items-center justify-center rounded-full border-4 border-indigo-100 border-t-indigo-600"
                />
                <div className="text-[11px] font-bold text-slate-400">正在准备</div>
                <h3 className="mt-3 text-[30px] font-semibold tracking-tight text-slate-900">正在创建这轮模拟面试</h3>
                <p className="mt-3 max-w-lg text-[15px] leading-8 text-slate-500">
                  系统正在整理本轮练习设置，并为你准备开场问题。稍等片刻，随后会直接进入正式问答。
                </p>
              </div>

              <div className="mt-7 grid gap-3 sm:grid-cols-2">
                {statusItems.map((item) => (
                  <div
                    key={item.label}
                    className="rounded-[1.4rem] border border-slate-200 bg-slate-50/85 px-4 py-3 text-left shadow-sm"
                  >
                    <div className="text-[11px] text-slate-400">{item.label}</div>
                    <div className="mt-1.5 text-[15px] font-semibold text-slate-900">{item.value}</div>
                  </div>
                ))}
              </div>
            </div>
          </motion.div>
        </div>
      ) : null}
    </AnimatePresence>
  );
}

function InterviewSummaryGeneratingModal(props: SummaryGeneratingOverlayProps) {
  const {
    open,
    sessionTitle,
    interviewerStyleLabel,
    answerModeLabel,
    replyRoundUsed,
  } = props;
  const statusItems = [
    { label: "练习主题", value: sessionTitle || "模拟面试" },
    { label: "面试风格", value: interviewerStyleLabel },
    { label: "作答方式", value: answerModeLabel },
    { label: "已完成轮次", value: `${replyRoundUsed} 轮` },
  ];

  return (
    <AnimatePresence>
      {open ? (
        <div className="fixed inset-0 z-[70] flex items-center justify-center px-4">
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute inset-0 bg-slate-900/28 backdrop-blur-sm"
          />

          <motion.div
            initial={{ opacity: 0, scale: 0.96, y: 18 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: 18 }}
            transition={{ type: "spring", bounce: 0.2, duration: 0.42 }}
            className="relative w-full max-w-xl overflow-hidden rounded-[2.2rem] border border-white/80 bg-white shadow-[0_30px_100px_rgba(15,23,42,0.20)]"
          >
            <div className="h-3 bg-gradient-to-r from-amber-400 via-teal-400 to-indigo-500" />

            <div className="p-8">
              <div className="flex flex-col items-center text-center">
                <motion.div
                  animate={{ rotate: 360 }}
                  transition={{ repeat: Number.POSITIVE_INFINITY, duration: 1.6, ease: "linear" }}
                  className="mb-6 flex h-16 w-16 items-center justify-center rounded-full border-4 border-indigo-100 border-t-indigo-600"
                />
                <div className="text-[11px] font-bold text-slate-400">正在整理</div>
                <h3 className="mt-3 text-[30px] font-semibold tracking-tight text-slate-900">正在生成本轮复盘</h3>
                <p className="mt-3 max-w-lg text-[15px] leading-8 text-slate-500">
                  系统正在整理这轮练习表现，并生成评分、亮点、待改进点和下一步建议。稍等片刻，完成后会自动进入报告页。
                </p>
              </div>

              <div className="mt-7 grid gap-3 sm:grid-cols-2">
                {statusItems.map((item) => (
                  <div
                    key={item.label}
                    className="rounded-[1.4rem] border border-slate-200 bg-slate-50/85 px-4 py-3 text-left shadow-sm"
                  >
                    <div className="text-[11px] text-slate-400">{item.label}</div>
                    <div className="mt-1.5 text-[15px] font-semibold text-slate-900">{item.value}</div>
                  </div>
                ))}
              </div>
            </div>
          </motion.div>
        </div>
      ) : null}
    </AnimatePresence>
  );
}

export default function InterviewPracticeOverlay(props: InterviewPracticeOverlayProps) {
  if (props.kind === "continuable-interview") {
    return <ContinuableInterviewPromptModal {...props} />;
  }

  if (props.kind === "session-creating") {
    return <InterviewSessionCreatingModal {...props} />;
  }

  if (props.kind === "summary-generating") {
    return <InterviewSummaryGeneratingModal {...props} />;
  }

  if (props.kind === "privacy-notice") {
    return <PrivacyNoticeModal {...props} />;
  }

  return <PrepareSetupModal {...props} />;
}
