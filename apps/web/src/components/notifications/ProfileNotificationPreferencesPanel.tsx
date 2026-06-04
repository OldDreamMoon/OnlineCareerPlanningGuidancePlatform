import {
  AlertCircle,
  BellRing,
  Bot,
  Briefcase,
  Lock,
  Mail,
  Megaphone,
  MessageSquare,
  Monitor,
  ShieldCheck,
  Wifi,
  type LucideIcon,
} from "lucide-react";
import { useEffect, useState } from "react";
import { ApiClientError } from "../../lib/apiClient";
import {
  getNotificationCategoryLabel,
  listNotificationPreferences,
  updateNotificationPreference,
  type NotificationPreferenceItem,
} from "../../lib/notifications";
import { useNotificationCenter } from "./NotificationCenterProvider";

type PanelVariant = "student" | "mentor" | "enterprise";

type FeedbackTone = "success" | "warning" | "error";

type FeedbackState = {
  tone: FeedbackTone;
  message: string;
} | null;

type VariantStyle = {
  shell: string;
  header: string;
  iconWrap: string;
  titleAccent: string;
  primaryButton: string;
  summaryCard: string;
  categoryCard: string;
  categoryIconWrap: string;
  badge: string;
  customizedBadge: string;
  switchOn: string;
};

type ToggleChannelKey = "inboxEnabled" | "websocketEnabled" | "browserPopupEnabled" | "emailEnabled";

const CATEGORY_ICONS: Record<string, LucideIcon> = {
  AI_TASK: Bot,
  CONSULT: Briefcase,
  BOUNTY: Briefcase,
  CERTIFICATION: ShieldCheck,
  COMMUNITY: MessageSquare,
};

// 同一套偏好矩阵复用于学生、导师、企业资料页，只替换文案和视觉调性。
const CATEGORY_DESCRIPTIONS_BY_VARIANT: Record<PanelVariant, Record<string, string>> = {
  student: {
    AI_TASK: "简历优化、模拟面试和复盘进展会统一回到这里，适合保留网页内与桌面提醒。",
    CONSULT: "咨询订单的新回复、进度更新和售后处理会持续回流，方便你及时跟进。",
    BOUNTY: "悬赏任务的提交结果、审核进展和继续协作提醒都会沉淀在这里。",
    COMMUNITY: "帖子回复、互动反馈和社区治理结果会同步回来，更适合作为轻量提醒。",
  },
  mentor: {
    CONSULT: "学生咨询订单的创建、回复和售后处理会回到这里，方便你按节奏处理。",
    CERTIFICATION: "认证审核、补件提醒与结果通知会统一保留，避免错过关键节点。",
    COMMUNITY: "社区互动、内容反馈和治理结果会同步回来，适合按你的工作节奏选择提醒方式。",
  },
  enterprise: {
    BOUNTY: "悬赏任务的最新提交、审核回流和处理进展会集中沉淀，方便企业团队协同查看。",
    CERTIFICATION: "企业资料与认证流程的审核结果、补件提醒会统一保留，避免漏掉关键节点。",
  },
};

const VARIANT_STYLES: Record<PanelVariant, VariantStyle> = {
  student: {
    shell: "overflow-hidden rounded-[2rem] border border-slate-200/85 bg-[#fcfdff]/96 shadow-[0_22px_60px_rgba(100,116,139,0.16)] backdrop-blur-xl",
    header: "border-b border-slate-200/80 bg-[#eef2f7]/78",
    iconWrap: "bg-indigo-100 text-indigo-600",
    titleAccent: "text-indigo-600",
    primaryButton: "border-indigo-200 bg-indigo-100 text-indigo-700 hover:bg-indigo-200",
    summaryCard: "border-slate-200/80 bg-[linear-gradient(180deg,rgba(239,244,250,0.96),rgba(255,255,255,0.98))] shadow-[0_12px_28px_rgba(100,116,139,0.10)]",
    categoryCard: "border-slate-200/85 bg-white/96 shadow-[0_14px_32px_rgba(100,116,139,0.10)]",
    categoryIconWrap: "border-indigo-100 bg-indigo-50 text-indigo-600",
    badge: "border-indigo-100 bg-indigo-50 text-indigo-700",
    customizedBadge: "border-emerald-200 bg-emerald-50 text-emerald-700",
    switchOn: "border-indigo-300 bg-indigo-100",
  },
  mentor: {
    shell: "overflow-hidden rounded-[2rem] border border-white/70 bg-white/80 shadow-[0_15px_40px_rgba(15,23,42,0.04)] backdrop-blur-xl",
    header: "border-b border-slate-100 bg-slate-50/50",
    iconWrap: "bg-emerald-50 text-emerald-600",
    titleAccent: "text-emerald-600",
    primaryButton: "border-emerald-200 bg-emerald-50 text-emerald-700 hover:bg-emerald-100",
    summaryCard: "border-slate-100 bg-slate-50/70 shadow-sm",
    categoryCard: "border-slate-100 bg-white shadow-sm",
    categoryIconWrap: "border-emerald-100 bg-emerald-50 text-emerald-600",
    badge: "border-emerald-100 bg-emerald-50 text-emerald-700",
    customizedBadge: "border-teal-200 bg-teal-50 text-teal-700",
    switchOn: "border-emerald-300 bg-emerald-100",
  },
  enterprise: {
    shell: "overflow-hidden rounded-[2rem] border border-white/80 bg-white/85 shadow-[0_22px_60px_rgba(148,163,184,0.15)] backdrop-blur-xl",
    header: "border-b border-slate-100 bg-slate-50/55",
    iconWrap: "bg-blue-50 text-blue-600",
    titleAccent: "text-blue-600",
    primaryButton: "border-blue-200 bg-blue-50 text-blue-700 hover:bg-blue-100",
    summaryCard: "border-slate-100 bg-slate-50/70 shadow-sm",
    categoryCard: "border-slate-100 bg-white shadow-sm",
    categoryIconWrap: "border-blue-100 bg-blue-50 text-blue-600",
    badge: "border-blue-100 bg-blue-50 text-blue-700",
    customizedBadge: "border-emerald-200 bg-emerald-50 text-emerald-700",
    switchOn: "border-blue-300 bg-blue-100",
  },
};

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function toUserMessage(error: unknown, fallback: string) {
  if (error instanceof ApiClientError) {
    return error.message || fallback;
  }
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}

function mergePreferenceItem(
  currentItems: NotificationPreferenceItem[],
  nextItem: NotificationPreferenceItem,
) {
  const matched = currentItems.some((item) => item.category === nextItem.category);
  if (!matched) {
    return [...currentItems, nextItem];
  }
  return currentItems.map((item) => (item.category === nextItem.category ? nextItem : item));
}

function getCategoryIcon(category: string) {
  return CATEGORY_ICONS[category] ?? BellRing;
}

function getCategoryDescription(category: string, variant: PanelVariant) {
  return CATEGORY_DESCRIPTIONS_BY_VARIANT[variant]?.[category]
    ?? "当前分类的提醒会统一沉淀到站内收件箱。";
}

function getPermissionCardCopy(permission: string) {
  switch (permission) {
    case "granted":
      return {
        title: "桌面提醒已可用",
        description: "离开当前页面后，浏览器仍然可以在桌面上提醒你重要通知。",
        buttonLabel: null,
        toneClassName: "border-emerald-200 bg-emerald-50 text-emerald-800",
      };
    case "denied":
      return {
        title: "桌面提醒已被浏览器拦截",
        description: "如果你需要桌面提醒，请先在浏览器站点权限中重新开启通知授权。",
        buttonLabel: "重新申请提醒授权",
        toneClassName: "border-rose-200 bg-rose-50 text-rose-800",
      };
    case "unsupported":
      return {
        title: "当前环境不支持桌面提醒",
        description: "你仍然可以继续使用站内收件箱与网页内实时提醒，不会影响核心通知回流。",
        buttonLabel: null,
        toneClassName: "border-slate-200 bg-slate-100 text-slate-700",
      };
    default:
      return {
        title: "桌面提醒待开启",
        description: "授权后，你在处理其他页面时也能及时收到平台消息，不容易错过关键进展。",
        buttonLabel: "开启桌面提醒",
        toneClassName: "border-amber-200 bg-amber-50 text-amber-800",
      };
  }
}

function getFeedbackClassName(tone: FeedbackTone) {
  switch (tone) {
    case "success":
      return "border-emerald-200 bg-emerald-50 text-emerald-800";
    case "warning":
      return "border-amber-200 bg-amber-50 text-amber-800";
    case "error":
    default:
      return "border-rose-200 bg-rose-50 text-rose-800";
  }
}

const CHANNEL_COLUMNS: Array<{
  key: ToggleChannelKey;
  label: string;
  helper: string;
  icon: LucideIcon;
  locked?: boolean;
}> = [
  // 收件箱永远固定开启，其他渠道才允许用户按业务分类细调。
  {
    key: "inboxEnabled",
    label: "收件箱",
    helper: "固定留痕",
    icon: BellRing,
    locked: true,
  },
  {
    key: "websocketEnabled",
    label: "网页内",
    helper: "页内实时提醒",
    icon: Wifi,
  },
  {
    key: "browserPopupEnabled",
    label: "桌面",
    helper: "浏览器桌面提醒",
    icon: Monitor,
  },
  {
    key: "emailEnabled",
    label: "邮箱",
    helper: "离线兜底提醒",
    icon: Mail,
  },
] as const;

function MatrixToggle({
  checked,
  disabled,
  onToggle,
  switchOnClassName,
}: {
  checked: boolean;
  disabled?: boolean;
  onToggle?: () => void;
  switchOnClassName: string;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onToggle}
      className={joinClasses(
        "relative inline-flex h-6 w-10 rounded-full border border-transparent p-0.5 transition-colors disabled:cursor-not-allowed disabled:opacity-50",
        checked ? switchOnClassName : "bg-slate-300",
      )}
      role="switch"
      aria-checked={checked}
    >
      <span className="sr-only">切换通知渠道</span>
      <span
        className={joinClasses(
          "pointer-events-none inline-block h-5 w-5 rounded-full bg-white shadow-sm transition-transform",
          checked ? "translate-x-4" : "translate-x-0",
        )}
      />
    </button>
  );
}

export default function ProfileNotificationPreferencesPanel({
  variant,
  title = "通知提醒设置",
  description = "统一管理你当前角色可自定义的业务提醒方式。站内收件箱会固定留痕，邮箱提醒默认关闭，需要时再逐项开启即可。",
}: {
  variant: PanelVariant;
  title?: string;
  description?: string;
}) {
  const styles = VARIANT_STYLES[variant];
  const { browserPermission, requestBrowserPermission } = useNotificationCenter();
  const [preferences, setPreferences] = useState<NotificationPreferenceItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [savingCategory, setSavingCategory] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<FeedbackState>(null);
  const permissionCard = getPermissionCardCopy(browserPermission);

  useEffect(() => {
    if (!feedback) {
      return undefined;
    }

    const timer = window.setTimeout(() => setFeedback(null), 2600);
    return () => window.clearTimeout(timer);
  }, [feedback]);

  useEffect(() => {
    const loadPreferences = async () => {
      setLoading(true);
      try {
        const response = await listNotificationPreferences();
        setPreferences(response.records);
        setLoadError(null);
      } catch (error) {
        setLoadError(toUserMessage(error, "通知提醒设置加载失败，请稍后重试。"));
      } finally {
        setLoading(false);
      }
    };

    void loadPreferences();
  }, []);

  const emailEnabledCount = preferences.filter((item) => item.emailEnabled).length;
  const desktopEnabledCount = preferences.filter((item) => item.browserPopupEnabled).length;
  const websocketEnabledCount = preferences.filter((item) => item.websocketEnabled).length;

  async function handlePreferencePatch(
    category: string,
    patch: Partial<NotificationPreferenceItem>,
  ) {
    const targetItem = preferences.find((item) => item.category === category);
    if (!targetItem) {
      return;
    }

    const nextBrowserValue = patch.browserPopupEnabled;
    if (nextBrowserValue) {
      // 桌面提醒必须先拿浏览器授权，授权失败时不修改后端偏好。
      if (browserPermission === "denied") {
        setFeedback({
          tone: "warning",
          message: "浏览器当前已拦截桌面提醒，请先在站点权限里重新开启通知授权。",
        });
        return;
      }

      const permission = browserPermission === "granted" ? browserPermission : await requestBrowserPermission();
      if (permission !== "granted") {
        setFeedback({
          tone: "warning",
          message: "桌面提醒没有拿到浏览器授权，当前会继续保留站内收件箱和网页内提醒。",
        });
        return;
      }
    }

    const optimisticItem: NotificationPreferenceItem = {
      ...targetItem,
      ...patch,
    };
    const previousItems = preferences;
    setSavingCategory(category);
    // 偏好切换先乐观回显，服务端失败再回滚到 previousItems。
    setPreferences(mergePreferenceItem(previousItems, optimisticItem));

    try {
      const updated = await updateNotificationPreference({
        category,
        websocketEnabled: optimisticItem.websocketEnabled,
        browserPopupEnabled: optimisticItem.browserPopupEnabled,
        emailEnabled: optimisticItem.emailEnabled,
      });
      setPreferences((currentItems) => mergePreferenceItem(currentItems, updated));
      setFeedback({
        tone: "success",
        message: `${getNotificationCategoryLabel(category)}的提醒方式已更新。`,
      });
    } catch (error) {
      setPreferences(previousItems);
      setFeedback({
        tone: "error",
        message: toUserMessage(error, "通知提醒设置保存失败，请稍后重试。"),
      });
    } finally {
      setSavingCategory(null);
    }
  }

  return (
    <section className={styles.shell}>
      <div className={joinClasses("px-7 py-6", styles.header)}>
        <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <div className="flex items-center gap-3">
              <span className={joinClasses("flex h-11 w-11 items-center justify-center rounded-2xl", styles.iconWrap)}>
                <BellRing size={20} />
              </span>
              <div>
                <h2 className="text-xl font-semibold text-slate-900">{title}</h2>
                <p className="mt-1 text-sm leading-7 text-slate-500">{description}</p>
              </div>
            </div>
          </div>

          <div className="flex flex-wrap gap-2">
            <span className={joinClasses("inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold", styles.badge)}>
              <Mail size={14} />
              邮箱提醒默认关闭
            </span>
            <span className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-600">
              <Wifi size={14} />
              网页内提醒 {websocketEnabledCount} 项开启
            </span>
          </div>
        </div>
      </div>

      <div className="space-y-5 p-7">
        {feedback ? (
          <div className={joinClasses("rounded-[1.15rem] border px-4 py-3 text-sm", getFeedbackClassName(feedback.tone))}>
            {feedback.message}
          </div>
        ) : null}

        {loadError ? (
          <div className="rounded-[1.4rem] border border-rose-200 bg-rose-50 px-5 py-5 text-sm text-rose-800">
            <div className="flex items-center gap-2 font-semibold">
              <AlertCircle size={16} />
              {loadError}
            </div>
          </div>
        ) : null}

        <div className="grid gap-4 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
          <div className={joinClasses("rounded-[1.45rem] border p-5", styles.summaryCard)}>
            <div className="flex items-start justify-between gap-4">
              <div>
                <div className={joinClasses("text-[11px] font-bold", styles.titleAccent)}>
                  桌面提醒状态
                </div>
                <div className="mt-2 text-lg font-bold text-slate-900">{permissionCard.title}</div>
              </div>
              <span className={joinClasses("flex h-10 w-10 items-center justify-center rounded-2xl border", styles.categoryIconWrap)}>
                <Monitor size={18} />
              </span>
            </div>
            <p className="mt-3 text-sm leading-7 text-slate-600">{permissionCard.description}</p>
            {permissionCard.buttonLabel ? (
              <button
                type="button"
                onClick={() => void requestBrowserPermission()}
                className={joinClasses("mt-4 inline-flex items-center justify-center rounded-full border px-4 py-2 text-sm font-semibold transition-colors", styles.primaryButton)}
              >
                <Monitor size={15} className="mr-2" />
                {permissionCard.buttonLabel}
              </button>
            ) : null}
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            {[
              {
                label: "桌面提醒",
                value: `${desktopEnabledCount} 项`,
                helper: browserPermission === "granted" ? "已可随浏览器桌面提示" : "需要浏览器授权后生效",
                icon: Monitor,
                toneClassName: "border-sky-100 bg-sky-50 text-sky-700",
              },
              {
                label: "网页内提醒",
                value: `${websocketEnabledCount} 项`,
                helper: "保持网页打开时即时提醒",
                icon: Wifi,
                toneClassName: "border-emerald-100 bg-emerald-50 text-emerald-700",
              },
              {
                label: "邮箱提醒",
                value: `${emailEnabledCount} 项`,
                helper: emailEnabledCount > 0 ? "仅你主动开启的分类会发信" : "默认全部关闭，不主动打扰",
                icon: Mail,
                toneClassName: "border-amber-100 bg-amber-50 text-amber-700",
              },
            ].map((item) => {
              const Icon = item.icon;
              return (
                <div key={item.label} className={joinClasses("rounded-[1.45rem] border p-5", styles.summaryCard)}>
                  <div className="flex items-center justify-between gap-3">
                    <div className="text-[12px] font-bold text-slate-400">{item.label}</div>
                    <span className={joinClasses("flex h-9 w-9 items-center justify-center rounded-2xl border", item.toneClassName)}>
                      <Icon size={16} />
                    </span>
                  </div>
                  <div className="mt-3 text-[1.75rem] font-black text-slate-900">{item.value}</div>
                  <div className="mt-1 text-xs leading-6 text-slate-500">{item.helper}</div>
                </div>
              );
            })}
          </div>
        </div>

        <div className="rounded-[1.45rem] border border-amber-200/80 bg-[linear-gradient(135deg,rgba(255,251,235,0.98),rgba(255,255,255,0.98))] px-5 py-4 shadow-[0_10px_24px_rgba(245,158,11,0.08)]">
          <div className="flex items-start gap-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl border border-amber-200 bg-amber-50 text-amber-600">
              <Megaphone size={18} />
            </span>
            <div>
              <div className="text-sm font-bold text-slate-900">系统公告由平台统一保障送达</div>
              <p className="mt-1 text-sm leading-7 text-slate-600">
                维护提醒、平台公告这类强送达消息不需要单独设置，这里只保留你可以自定义的业务通知分类。
              </p>
            </div>
          </div>
        </div>

        <div className="rounded-[1.55rem] border border-slate-200/85 bg-white/96 shadow-[0_14px_32px_rgba(100,116,139,0.08)]">
          <div className="border-b border-slate-200/80 bg-slate-50/75 px-5 py-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div className={joinClasses("text-[11px] font-bold", styles.titleAccent)}>
                  通知渠道矩阵
                </div>
                <div className="mt-1 text-lg font-bold text-slate-900">按分类管理提醒方式</div>
              </div>
              <span className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-600">
                <Lock size={14} />
                收件箱固定保留全部通知
              </span>
            </div>
          </div>

          <div className="overflow-x-auto">
            <div className="min-w-[66rem]">
              <div className="grid grid-cols-[minmax(18rem,1.75fr)_repeat(4,minmax(0,0.72fr))] gap-4 border-b border-slate-200/80 px-5 py-4">
                <div className="flex items-center text-sm font-bold text-slate-700">通知分类</div>
                {CHANNEL_COLUMNS.map((column) => {
                  const Icon = column.icon;
                  return (
                    <div key={column.key} className="flex flex-col items-center justify-center gap-1 text-center">
                      <span className="flex h-9 w-9 items-center justify-center rounded-2xl border border-slate-200 bg-slate-50 text-slate-600">
                        <Icon size={16} />
                      </span>
                      <div className="text-sm font-bold text-slate-800">{column.label}</div>
                      <div className="text-[11px] leading-5 text-slate-400">{column.helper}</div>
                    </div>
                  );
                })}
              </div>

              {loading ? (
                <div className="divide-y divide-slate-100">
                  {Array.from({ length: 4 }).map((_, index) => (
                    <div
                      key={index}
                      className="grid grid-cols-[minmax(18rem,1.75fr)_repeat(4,minmax(0,0.72fr))] gap-4 px-5 py-5"
                    >
                      <div className="space-y-2">
                        <div className="h-5 w-40 animate-pulse rounded-full bg-slate-100" />
                        <div className="h-4 w-full animate-pulse rounded-full bg-slate-100" />
                      </div>
                      {Array.from({ length: 4 }).map((__, toggleIndex) => (
                        <div key={toggleIndex} className="flex flex-col items-center justify-center gap-2">
                          <div className="h-6 w-10 animate-pulse rounded-full bg-slate-100" />
                          <div className="h-4 w-12 animate-pulse rounded-full bg-slate-100" />
                        </div>
                      ))}
                    </div>
                  ))}
                </div>
              ) : (
                <div className="divide-y divide-slate-100">
                  {preferences.map((item) => {
                    const Icon = getCategoryIcon(String(item.category));
                    const disabled = savingCategory === item.category;
                    return (
                      <article
                        key={item.category}
                        className="grid grid-cols-[minmax(18rem,1.75fr)_repeat(4,minmax(0,0.72fr))] gap-4 px-5 py-5 transition-colors hover:bg-slate-50/65"
                      >
                        <div className="min-w-0 pr-3">
                          <div className="flex items-start gap-3">
                            <span className={joinClasses("mt-0.5 flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border", styles.categoryIconWrap)}>
                              <Icon size={18} />
                            </span>
                            <div className="min-w-0">
                              <div className="flex flex-wrap items-center gap-2">
                                <h3 className="text-base font-bold text-slate-900">{getNotificationCategoryLabel(String(item.category))}</h3>
                                <span className={joinClasses("rounded-full border px-2.5 py-1 text-[11px] font-semibold", item.customized ? styles.customizedBadge : styles.badge)}>
                                  {item.customized ? "已自定义" : "默认方案"}
                                </span>
                              </div>
                              <p className="mt-2 text-sm leading-6 text-slate-500">
                                {getCategoryDescription(String(item.category), variant)}
                              </p>
                            </div>
                          </div>
                        </div>

                        {CHANNEL_COLUMNS.map((column) => {
                          const checked = item[column.key];
                          const isLocked = column.locked ?? false;
                          return (
                            <div key={`${item.category}-${column.key}`} className="flex flex-col items-center justify-center gap-2 text-center">
                              {isLocked ? (
                                <div className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-slate-100 px-3 py-1.5 text-xs font-semibold text-slate-500">
                                  <Lock size={13} />
                                  固定
                                </div>
                              ) : (
                                <MatrixToggle
                                  checked={checked}
                                  disabled={disabled}
                                  onToggle={() => void handlePreferencePatch(String(item.category), {
                                    [column.key]: !checked,
                                  })}
                                  switchOnClassName={styles.switchOn}
                                />
                              )}
                              <div className={joinClasses(
                                "text-xs font-semibold",
                                checked ? "text-slate-700" : "text-slate-400",
                              )}>
                                {isLocked ? "始终开启" : checked ? "已开启" : "已关闭"}
                              </div>
                            </div>
                          );
                        })}
                      </article>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
