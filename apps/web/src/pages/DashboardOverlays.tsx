import { AnimatePresence, motion } from "framer-motion";
import {
  Award,
  Flame,
  RefreshCw,
  X,
  Zap,
} from "lucide-react";
import { formatCount } from "../lib/formatters";

type RewardTone = "amber" | "emerald" | "indigo";

type RewardState = {
  title: string;
  subtitle: string;
  points: number;
  tone: RewardTone;
  highlight: string;
};

type GrowthCheckinRewardItem = {
  rewardCode: string;
  title: string;
  description: string;
  streakDays: number;
  bonusPoints: number;
};

type CheckinProgressSummary = {
  headline: string;
  detail: string;
  progressValue: number;
  progressText: string;
  rewardLabel: string;
};

type CalendarDayCell =
  | {
      key: string;
      type: "placeholder";
    }
  | {
      key: string;
      type: "day";
      day: number;
      checkedIn: boolean;
      isToday: boolean;
      isFuture: boolean;
    };

type CelebrationOverlayProps = {
  kind: "celebration";
  reward: RewardState | null;
  onClose: () => void;
};

type CalendarOverlayProps = {
  kind: "calendar";
  open: boolean;
  hasOverview: boolean;
  progress: CheckinProgressSummary;
  calendarCells: CalendarDayCell[];
  rewardRules: GrowthCheckinRewardItem[];
  currentStreak: number;
  signedInToday: boolean;
  monthLabel: string;
  basePoints: number;
  submitting: boolean;
  onClose: () => void;
  onCheckin: () => void;
};

type DashboardOverlayProps = CelebrationOverlayProps | CalendarOverlayProps;

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function CelebrationModal(props: CelebrationOverlayProps) {
  const { reward, onClose } = props;
  const toneClassNames: Record<RewardTone, { halo: string; pill: string; button: string }> = {
    amber: {
      halo: "from-amber-200 via-orange-100 to-white",
      pill: "border-amber-200 bg-gradient-to-r from-amber-100 to-orange-100 text-amber-700",
      button: "bg-slate-900 text-white hover:bg-slate-800",
    },
    emerald: {
      halo: "from-emerald-200 via-teal-100 to-white",
      pill: "border-emerald-200 bg-gradient-to-r from-emerald-100 to-teal-100 text-emerald-700",
      button: "bg-slate-900 text-white hover:bg-slate-800",
    },
    indigo: {
      halo: "from-indigo-200 via-violet-100 to-white",
      pill: "border-indigo-200 bg-gradient-to-r from-indigo-100 to-violet-100 text-indigo-700",
      button: "bg-slate-900 text-white hover:bg-slate-800",
    },
  };

  return (
    <AnimatePresence>
      {reward ? (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/55 p-4 backdrop-blur-sm"
        >
          <motion.div
            initial={{ scale: 0.82, y: 42, opacity: 0 }}
            animate={{ scale: 1, y: 0, opacity: 1, transition: { type: "spring", bounce: 0.38, duration: 0.55 } }}
            exit={{ scale: 0.92, y: 16, opacity: 0 }}
            className="relative w-full max-w-md overflow-hidden rounded-[2.4rem] bg-white p-8 text-center shadow-[0_35px_100px_rgba(15,23,42,0.32)] md:p-10"
          >
            <div className={joinClasses("absolute inset-x-0 top-0 h-48 bg-gradient-to-b opacity-90", toneClassNames[reward.tone].halo)} />
            <button
              type="button"
              onClick={onClose}
              className="absolute right-5 top-5 z-20 inline-flex h-10 w-10 items-center justify-center rounded-full border border-white/70 bg-white/80 text-slate-500 shadow-sm transition-colors hover:text-slate-900"
            >
              <X size={18} />
            </button>

            <div className="relative z-10">
              <motion.div
                initial={{ scale: 0, rotate: -24 }}
                animate={{ scale: 1, rotate: 0 }}
                transition={{ type: "spring", delay: 0.14, stiffness: 240 }}
                className="mx-auto flex h-24 w-24 items-center justify-center rounded-[2rem] bg-white shadow-[0_18px_40px_rgba(15,23,42,0.10)]"
              >
                <Award size={38} className="text-amber-500" />
              </motion.div>

              <h2 className="mt-6 text-3xl font-black tracking-tight text-slate-900">{reward.title}</h2>
              <p className="mt-4 text-sm leading-7 text-slate-500">{reward.subtitle}</p>
              <div className="mt-4 text-sm font-semibold text-slate-700">{reward.highlight}</div>

              <div className={joinClasses("mt-8 inline-flex items-center gap-2 rounded-2xl border px-6 py-3 text-xl font-black shadow-sm", toneClassNames[reward.tone].pill)}>
                <Award size={22} />
                <span>+{formatCount(reward.points)} 积分</span>
              </div>

              <button
                type="button"
                onClick={onClose}
                className={joinClasses("mt-8 inline-flex w-full items-center justify-center rounded-2xl px-5 py-4 text-lg font-bold transition-all hover:-translate-y-0.5", toneClassNames[reward.tone].button)}
              >
                收下奖励
              </button>
            </div>
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}

function CheckinCalendarModal(props: CalendarOverlayProps) {
  const {
    open,
    hasOverview,
    progress,
    calendarCells,
    rewardRules,
    currentStreak,
    signedInToday,
    monthLabel,
    basePoints,
    submitting,
    onClose,
    onCheckin,
  } = props;

  return (
    <AnimatePresence>
      {open ? (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 backdrop-blur-sm"
        >
          <motion.div
            initial={{ scale: 0.92, y: 22, opacity: 0 }}
            animate={{ scale: 1, y: 0, opacity: 1, transition: { type: "spring", bounce: 0.28, duration: 0.5 } }}
            exit={{ scale: 0.94, y: 18, opacity: 0 }}
            className="relative flex max-h-[90vh] w-full max-w-lg flex-col overflow-hidden rounded-[2.2rem] bg-[#FAF9F6] shadow-[0_36px_100px_rgba(15,23,42,0.35)]"
          >
            <div className="absolute inset-x-0 top-0 h-40 bg-gradient-to-br from-indigo-500 via-violet-500 to-orange-400" />
            <div className="absolute right-[-3rem] top-[-2rem] h-36 w-36 rounded-full bg-white/20 blur-2xl" />

            <button
              type="button"
              onClick={onClose}
              className="absolute right-4 top-4 z-20 inline-flex h-9 w-9 items-center justify-center rounded-full bg-white/20 text-white backdrop-blur-md transition-colors hover:bg-white/35"
            >
              <X size={18} strokeWidth={3} />
            </button>

            <div className="relative z-10 px-8 pb-7 pt-8 text-center text-white">
              <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-[1.4rem] bg-white text-3xl shadow-lg">📅</div>
              <h2 className="mt-4 text-3xl font-black tracking-tight">本月成长足迹</h2>
              <p className="mt-2 text-sm font-medium text-white/85">{monthLabel} · 坚持是最稀缺的成长信号</p>
            </div>

            <div className="relative z-10 flex-1 overflow-y-auto rounded-t-[2rem] bg-white px-6 py-7 shadow-[0_-10px_20px_rgba(0,0,0,0.05)]">
              <div className="rounded-[1.7rem] border border-amber-100 bg-amber-50 p-4">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <div className="text-sm font-bold text-amber-800">连签冲刺挑战</div>
                    <div className="mt-1 text-sm leading-6 text-amber-700">{progress.headline}</div>
                  </div>
                  <div className="rounded-2xl border border-amber-200 bg-white px-3 py-2 text-right shadow-sm">
                    <div className="text-[11px] text-amber-500">进度</div>
                    <div className="mt-1 text-lg font-black text-amber-700">{progress.progressText}</div>
                  </div>
                </div>
                <div className="mt-4 h-2 overflow-hidden rounded-full bg-amber-200/60">
                  <div
                    className="h-full rounded-full bg-gradient-to-r from-amber-400 to-orange-500 transition-all"
                    style={{ width: `${Math.max(progress.progressValue * 100, 0)}%` }}
                  />
                </div>
                <div className="mt-3 text-xs font-semibold text-amber-600">{progress.rewardLabel}</div>
                <div className="mt-1 text-xs leading-6 text-amber-700/90">{progress.detail}</div>
              </div>

              <div className="mt-5">
                <div className="text-xs font-bold text-slate-400">连签奖励规则</div>
                <div className="mt-3 flex flex-wrap gap-2">
                  {rewardRules.length > 0 ? (
                    rewardRules.map((rule) => (
                      <div
                        key={rule.rewardCode}
                        className={joinClasses(
                          "rounded-full border px-3 py-2 text-xs font-semibold shadow-sm",
                          currentStreak >= rule.streakDays
                            ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                            : "border-slate-200 bg-slate-50 text-slate-600",
                        )}
                      >
                        连签 {rule.streakDays} 天 · +{formatCount(rule.bonusPoints)}
                      </div>
                    ))
                  ) : (
                    <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-4 text-sm leading-7 text-slate-500">
                      连签奖励规则还在同步中，当前可以先完成今日签到。
                    </div>
                  )}
                </div>
              </div>

              {hasOverview ? (
                <div className="mt-6">
                  <div className="grid grid-cols-7 gap-x-2 gap-y-3">
                    {["一", "二", "三", "四", "五", "六", "日"].map((label) => (
                      <div key={label} className="text-center text-xs font-bold text-slate-400">
                        {label}
                      </div>
                    ))}

                    {calendarCells.map((cell) => {
                      if (cell.type === "placeholder") {
                        return <div key={cell.key} className="aspect-square" />;
                      }

                      return (
                        <div key={cell.key} className="flex justify-center">
                          <div
                            className={joinClasses(
                              "relative flex aspect-square w-full items-center justify-center rounded-xl text-sm font-bold transition-all",
                              cell.checkedIn && "border border-emerald-200 bg-emerald-100 text-emerald-600",
                              !cell.checkedIn && cell.isToday && "z-10 scale-105 border-2 border-orange-400 bg-white text-orange-500 shadow-md",
                              !cell.checkedIn && !cell.isToday && !cell.isFuture && "border border-slate-200 bg-slate-100 text-slate-500",
                              !cell.checkedIn && cell.isFuture && "border border-slate-100 bg-slate-50 text-slate-300",
                            )}
                          >
                            {cell.checkedIn ? (
                              <Flame size={18} className="text-emerald-500" fill="currentColor" />
                            ) : (
                              <span className={cell.isToday && !signedInToday ? "animate-pulse" : ""}>{cell.day}</span>
                            )}

                            {cell.isToday && !signedInToday ? (
                              <span className="absolute -right-1 -top-1 flex h-3 w-3">
                                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-orange-400 opacity-75" />
                                <span className="relative inline-flex h-3 w-3 rounded-full border-2 border-white bg-orange-500" />
                              </span>
                            ) : null}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              ) : (
                <div className="mt-6 rounded-[1.6rem] border border-dashed border-slate-200 bg-slate-50 px-5 py-6 text-sm leading-7 text-slate-500">
                  签到总览暂时没有同步成功，日历面板已进入降级展示。你仍然可以直接完成今日签到，稍后重试会自动补上本月记录。
                </div>
              )}
            </div>

            <div className="border-t border-slate-100 bg-white p-6">
              <button
                type="button"
                onClick={onCheckin}
                disabled={signedInToday || submitting}
                className={joinClasses(
                  "inline-flex w-full items-center justify-center rounded-2xl px-5 py-4 text-lg font-black transition-all",
                  signedInToday
                    ? "cursor-not-allowed bg-slate-100 text-slate-400"
                    : "bg-gradient-to-r from-orange-400 to-rose-500 text-white shadow-[0_16px_35px_rgba(251,146,60,0.26)] hover:-translate-y-0.5",
                )}
              >
                {submitting ? (
                  <RefreshCw size={22} className="animate-spin" />
                ) : signedInToday ? (
                  <>今天已经完成签到</>
                ) : (
                  <>
                    <Zap className="mr-2" size={20} fill="currentColor" />
                    立即签到（+{formatCount(basePoints)} 积分起）
                  </>
                )}
              </button>
            </div>
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}

export default function DashboardOverlay(props: DashboardOverlayProps) {
  if (props.kind === "celebration") {
    return <CelebrationModal {...props} />;
  }

  return <CheckinCalendarModal {...props} />;
}
