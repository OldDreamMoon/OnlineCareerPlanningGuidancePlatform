import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import {
  Alert,
  Button,
  Empty,
  Form,
  InputNumber,
  Select,
  Skeleton,
  Typography,
  message,
} from "antd";
import {
  AlertTriangle,
  ArrowRight,
  Bot,
  BrainCircuit,
  Flag,
  Network,
  RefreshCcw,
  Route,
  Settings2,
  ShieldAlert,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  Workflow,
  type LucideIcon,
} from "lucide-react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { AdminAnimatedNumber, canAnimateAdminValue } from "../components/admin/AdminAnimatedNumber";
import {
  AdminDetailPlaceholder,
  AdminMetricCard,
  AdminPageFrame,
  AdminPageHeader,
  AdminSettingSwitch,
  AdminSurfaceCard,
  joinAdminClassNames,
} from "../components/admin/AdminOpsPrimitives";
import { useLatestRequest } from "../hooks/useLatestRequest";
import { useAdminStaleCache } from "../hooks/useAdminStaleCache";
import { adminAiChannelCatalog } from "../lib/adminAiChannels";
import { ApiClientError, apiRequest, isAbortError } from "../lib/apiClient";
import {
  featureFlagMetaMap,
  gatewayTaskTypeLabelMap,
  getLabel,
} from "../lib/adminLabels";
import { formatCount, formatDateTime } from "../lib/formatters";

const { Paragraph, Text } = Typography;

const runtimeTabs = ["home", "basic", "ai-channels"] as const;
type RuntimeTabKey = typeof runtimeTabs[number];
type RuntimeBasicSectionKey = "business" | "operations" | "degrade";
type TimeValue = number | string | null;

type FeatureFlagItem = {
  key: string;
  displayName: string;
  description: string;
  valueType: "BOOLEAN" | "ENUM";
  allowedValues: string[];
  currentValue: string;
  defaultValue: string;
  overridden: boolean;
  updatedBy: number | null;
  updatedAt: TimeValue;
};

type RuntimeSettingsPayload = {
  debugModeEnabled: boolean;
  aiRequestLogEnabled: boolean;
  defaultDebugModeEnabled: boolean;
  defaultAiRequestLogEnabled: boolean;
  defaultReasoningEffort: string | null;
  defaultThinkingBudget: number | null;
  defaultThinkingLevel: string | null;
  updatedAt: TimeValue;
};

type ModerationPoliciesResponse = {
  aiInputEnabled: boolean;
  aiOutputEnabled: boolean;
  communityStrictReviewEnabled: boolean;
  autoHideReportThreshold: number;
};

type RouteDetailItem = {
  id: number;
  sceneRoutePolicyId: number | null;
  routePolicyCode?: string | null;
  routePolicyUserTier?: string | null;
  strategyType?: string | null;
  routeCode: string;
  taskType: string;
  sceneCode: string | null;
  providerConfigId: number;
  providerCode: string;
  providerType: string;
  providerDisplayName: string;
  modelName: string;
  priorityNo: number;
  executionMode: string;
  enabled: boolean;
  temperature: string;
  systemPrompt: string | null;
  promptTemplateName: string | null;
  promptTemplateVersionNo: number | null;
  extraConfigJson: string | null;
  createdAt: TimeValue;
  updatedAt: TimeValue;
};

type RoutePolicyDetailItem = {
  id: number;
  policyCode: string;
  taskType: string;
  sceneCode: string;
  userTier: string;
  strategyType: string;
  enabled: boolean;
  reasoningEffort: string | null;
  thinkingBudget: number | null;
  thinkingLevel: string | null;
  notes: string | null;
  updatedAt: TimeValue;
};

type PromptTemplateDetailItem = {
  id: number;
  taskType: string;
  templateName: string;
  versionNo: number;
  status: string;
  templateFormat: string;
  content: string;
  description: string | null;
  variablesJson: string | null;
  bundleJson: string | null;
  createdAt: TimeValue;
  updatedAt: TimeValue;
};

type PromptPreviewPayload = {
  taskType: string;
  templateFormat: string;
  renderedContent: string;
  renderedBundleJson: string | null;
  placeholderVariables: string[];
  missingVariables: string[];
  resolvedVariablesJson: string | null;
};

type AiChannelOverviewPayload = {
  totalProviders: number;
  enabledProviders: number;
  healthyProviders: number;
  degradedProviders: number;
  downProviders: number;
  idleProviders: number;
  disabledProviders: number;
  totalRoutes: number;
  enabledRoutes: number;
  sceneBoundRoutes: number;
  syncBlockingRoutes: number;
  streamRoutes: number;
  asyncRoutes: number;
  realtimeRoutes: number;
  totalPromptTemplates: number;
  activePromptTemplates: number;
  draftPromptTemplates: number;
  inactivePromptTemplates: number;
};

type ConsoleSnapshotPayload = {
  generatedAt: TimeValue;
  featureFlags: FeatureFlagItem[];
  runtimeSettings: RuntimeSettingsPayload;
  moderationPolicies: ModerationPoliciesResponse;
  aiChannels: AiChannelOverviewPayload;
};

type RuntimeConsoleCachePayload = {
  generatedAt: TimeValue;
  flags: FeatureFlagItem[];
  runtimeSettings: RuntimeSettingsPayload | null;
  policies: ModerationPoliciesResponse | null;
  aiChannels: AiChannelOverviewPayload | null;
  routeDetails: RouteDetailItem[];
  routePolicies: RoutePolicyDetailItem[];
  templateDetails: PromptTemplateDetailItem[];
};

type RuntimeSceneControlRow = {
  sceneKey: string;
  channelCode: string;
  displayName: string;
  ownerDomain: string;
  frontEntry: string;
  registered: boolean;
  taskType: string;
  primaryRoute: RouteDetailItem | null;
  primaryPolicy: RoutePolicyDetailItem | null;
  policies: RoutePolicyDetailItem[];
  routeCount: number;
  differentiated: boolean;
  thinkingConfiguredCount: number;
  thinkingSummary: string;
  activeTemplate: PromptTemplateDetailItem | null;
  templateVersionCount: number;
};

type RuntimeFormValues = {
  debugModeEnabled: boolean;
  aiRequestLogEnabled: boolean;
  defaultReasoningEffort?: string | null;
  defaultThinkingBudget?: number | null;
  defaultThinkingLevel?: string | null;
};

type ModerationFormValues = {
  aiInputEnabled: boolean;
  aiOutputEnabled: boolean;
  communityStrictReviewEnabled: boolean;
  autoHideReportThreshold: number;
};

const enumLabelMap: Record<string, string> = {
  MOCK: "模拟支付",
  SANDBOX: "支付宝沙箱",
  OFF: "关闭画像总结",
  TEMPLATE: "模板总结",
  LLM: "LLM 润色",
  true: "已开启",
  false: "已关闭",
};

const reasoningEffortOptions = [
  { value: "OFF", label: "关闭" },
  { value: "LOW", label: "低" },
  { value: "MEDIUM", label: "中" },
  { value: "HIGH", label: "高" },
  { value: "DYNAMIC", label: "动态" },
];

const thinkingLevelOptions = [
  { value: "minimal", label: "minimal" },
  { value: "low", label: "low" },
  { value: "standard", label: "standard" },
  { value: "medium", label: "medium" },
  { value: "high", label: "high" },
];

const legacyTabMap: Record<string, RuntimeTabKey> = {
  flags: "basic",
  runtime: "basic",
  moderation: "basic",
  business: "basic",
  operations: "basic",
  degrade: "basic",
};

function normalizeSceneCode(value: string | null | undefined) {
  return value?.trim().toUpperCase() || "";
}

function buildSceneMetricKey(taskType: string, sceneCode: string | null | undefined) {
  return `${taskType}::${normalizeSceneCode(sceneCode)}`;
}

function sortRouteDetails(left: RouteDetailItem, right: RouteDetailItem) {
  if (left.enabled !== right.enabled) {
    return left.enabled ? -1 : 1;
  }
  return left.priorityNo - right.priorityNo;
}

function normalizeTab(tab: string | null): RuntimeTabKey {
  if (tab === "home" || tab === "overview") {
    return "home";
  }
  const resolved = tab ? legacyTabMap[tab] ?? tab : null;
  if (resolved && runtimeTabs.includes(resolved as RuntimeTabKey)) {
    return resolved as RuntimeTabKey;
  }
  return "home";
}

function normalizeBasicSection(tab: string | null): RuntimeBasicSectionKey {
  if (tab === "operations" || tab === "runtime") {
    return "operations";
  }
  if (tab === "degrade" || tab === "moderation") {
    return "degrade";
  }
  return "business";
}

function normalizeBooleanValue(value: string | number | boolean | null | undefined): boolean | null {
  if (typeof value === "boolean") {
    return value;
  }
  if (value === "true") {
    return true;
  }
  if (value === "false") {
    return false;
  }
  return null;
}

function getRuntimeReturnModeLabel(mode: string) {
  switch (mode) {
    case "SYNC_BLOCKING":
      return "非流式返回";
    case "STREAM_SSE":
      return "流式返回";
    case "REALTIME_SESSION":
      return "实时会话";
    case "ASYNC_JOB":
      return "后台任务";
    default:
      return mode || "未识别";
  }
}

function getReasoningEffortLabel(value?: string | null) {
  if (!value) {
    return "";
  }
  switch (value) {
    case "OFF":
      return "关闭";
    case "LOW":
      return "低";
    case "MEDIUM":
      return "中";
    case "HIGH":
      return "高";
    case "DYNAMIC":
      return "动态";
    default:
      return value;
  }
}

function formatThinkingSummary(
  reasoningEffort?: string | null,
  thinkingBudget?: number | null,
  thinkingLevel?: string | null,
) {
  const parts = [reasoningEffort ? `档位 ${getReasoningEffortLabel(reasoningEffort)}` : null];
  if (typeof thinkingBudget === "number") {
    parts.push(`预算 ${formatCount(thinkingBudget)}`);
  }
  if (thinkingLevel) {
    parts.push(`原生层级 ${thinkingLevel}`);
  }
  return parts.filter(Boolean).join(" / ");
}

function getSceneDomainVisual(ownerDomain: string): {
  icon: LucideIcon;
  haloClassName: string;
  panelClassName: string;
  iconWrapClassName: string;
  badgeClassName: string;
  compactTone: "emerald" | "indigo" | "sky" | "violet";
} {
  switch (ownerDomain) {
    case "学生 AI":
      return {
        icon: Sparkles,
        haloClassName: "bg-[radial-gradient(circle_at_top_left,rgba(34,211,238,0.14),transparent_56%),radial-gradient(circle_at_top_right,rgba(59,130,246,0.12),transparent_54%)]",
        panelClassName: "border-cyan-100/80 bg-[linear-gradient(135deg,rgba(236,254,255,0.92),rgba(255,255,255,0.98))]",
        iconWrapClassName: "bg-cyan-100 text-cyan-700",
        badgeClassName: "border border-cyan-200 bg-cyan-50 text-cyan-700",
        compactTone: "sky",
      };
    case "社区":
      return {
        icon: Network,
        haloClassName: "bg-[radial-gradient(circle_at_top_left,rgba(167,139,250,0.16),transparent_56%),radial-gradient(circle_at_bottom_right,rgba(129,140,248,0.10),transparent_54%)]",
        panelClassName: "border-violet-100/80 bg-[linear-gradient(135deg,rgba(245,243,255,0.92),rgba(255,255,255,0.98))]",
        iconWrapClassName: "bg-violet-100 text-violet-700",
        badgeClassName: "border border-violet-200 bg-violet-50 text-violet-700",
        compactTone: "violet",
      };
    case "导师广场":
      return {
        icon: Flag,
        haloClassName: "bg-[radial-gradient(circle_at_top_left,rgba(16,185,129,0.14),transparent_56%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.10),transparent_54%)]",
        panelClassName: "border-emerald-100/80 bg-[linear-gradient(135deg,rgba(236,253,245,0.92),rgba(255,255,255,0.98))]",
        iconWrapClassName: "bg-emerald-100 text-emerald-700",
        badgeClassName: "border border-emerald-200 bg-emerald-50 text-emerald-700",
        compactTone: "emerald",
      };
    default:
      return {
        icon: BrainCircuit,
        haloClassName: "bg-[radial-gradient(circle_at_top_left,rgba(99,102,241,0.14),transparent_56%),radial-gradient(circle_at_bottom_right,rgba(56,189,248,0.10),transparent_54%)]",
        panelClassName: "border-indigo-100/80 bg-[linear-gradient(135deg,rgba(238,242,255,0.92),rgba(255,255,255,0.98))]",
        iconWrapClassName: "bg-indigo-100 text-indigo-700",
        badgeClassName: "border border-indigo-200 bg-indigo-50 text-indigo-700",
        compactTone: "indigo",
      };
  }
}

function SettingBadge({ label, tone }: { label: string; tone: "emerald" | "rose" | "indigo" | "amber" | "slate" }) {
  const toneClassName = {
    emerald: "bg-emerald-100 text-emerald-700",
    rose: "bg-rose-100 text-rose-700",
    indigo: "bg-indigo-100 text-indigo-700",
    amber: "bg-amber-100 text-amber-700",
    slate: "bg-slate-100 text-slate-700",
  }[tone];

  return (
    <span className={joinAdminClassNames("admin-typography-chip inline-flex items-center rounded-full px-3 py-1", toneClassName)}>
      {label}
    </span>
  );
}

function SettingCompareCard({
  label,
  value,
  kind,
}: {
  label: string;
  value: string | number | boolean;
  kind: "current" | "default";
}) {
  const booleanValue = normalizeBooleanValue(value);
  return (
    <div className={joinAdminClassNames(
      "rounded-2xl border px-4 py-4",
      kind === "current" ? "border-indigo-100 bg-indigo-50/80" : "border-slate-200 bg-slate-50",
    )}>
      <div className="admin-typography-data-label text-slate-400">{label}</div>
      <div className="mt-3">
        {booleanValue !== null ? (
          <SettingBadge label={booleanValue ? "已开启" : "已关闭"} tone={booleanValue ? "emerald" : "rose"} />
        ) : typeof value === "number" ? (
          <div className="admin-typography-card-value font-['Manrope'] text-slate-950">{value}</div>
        ) : (
          <SettingBadge label={enumLabelMap[String(value)] ?? String(value)} tone={kind === "current" ? "indigo" : "slate"} />
        )}
      </div>
    </div>
  );
}

function DisabledStripeMask() {
  return (
    <div
      aria-hidden
      className="pointer-events-none absolute inset-0 rounded-[24px] bg-[linear-gradient(180deg,rgba(239,68,68,0.04),rgba(239,68,68,0.08))] after:absolute after:inset-0 after:rounded-[24px] after:bg-[repeating-linear-gradient(-45deg,rgba(239,68,68,0.18)_0px,rgba(239,68,68,0.18)_12px,rgba(255,255,255,0)_12px,rgba(255,255,255,0)_24px)]"
    />
  );
}

function RuntimeVisualMetricCard({
  icon: Icon,
  label,
  value,
  note,
  tone,
}: {
  icon: LucideIcon;
  label: string;
  value: string | number;
  note: string;
  tone: "indigo" | "emerald" | "amber" | "fuchsia";
}) {
  const toneMap = {
    indigo: {
      wrapper: "border-slate-200 bg-white",
      icon: "bg-indigo-50 text-indigo-600",
      label: "text-slate-500",
      value: "text-slate-950",
      note: "text-slate-500",
    },
    emerald: {
      wrapper: "border-slate-200 bg-white",
      icon: "bg-emerald-50 text-emerald-600",
      label: "text-slate-500",
      value: "text-slate-950",
      note: "text-slate-500",
    },
    amber: {
      wrapper: "border-slate-200 bg-white",
      icon: "bg-amber-50 text-amber-600",
      label: "text-slate-500",
      value: "text-slate-950",
      note: "text-slate-500",
    },
    fuchsia: {
      wrapper: "border-slate-200 bg-white",
      icon: "bg-fuchsia-50 text-fuchsia-600",
      label: "text-slate-500",
      value: "text-slate-950",
      note: "text-slate-500",
    },
  }[tone];
  const renderedValue = canAnimateAdminValue(value) ? <AdminAnimatedNumber value={value} /> : value;

  return (
    <div className={joinAdminClassNames("rounded-[28px] border px-5 py-5 shadow-none", toneMap.wrapper)}>
      <div className="flex items-start gap-4">
        <div className={joinAdminClassNames("flex h-11 w-11 items-center justify-center rounded-2xl", toneMap.icon)}>
          <Icon size={20} />
        </div>
        <div className="min-w-0 flex-1">
          <div className={joinAdminClassNames("admin-typography-card-title", toneMap.label)}>{label}</div>
          <div className={joinAdminClassNames("admin-typography-card-value mt-3 font-['Manrope']", toneMap.value)}>{renderedValue}</div>
          <div className={joinAdminClassNames("admin-typography-card-note mt-2", toneMap.note)}>{note}</div>
        </div>
      </div>
    </div>
  );
}

function RuntimeCompactInfoCard({
  icon: Icon,
  label,
  value,
  note,
  tone = "slate",
  className,
  valueClassName,
  noteClassName,
}: {
  icon: LucideIcon;
  label: ReactNode;
  value: ReactNode;
  note?: ReactNode;
  tone?: "slate" | "emerald" | "amber" | "indigo" | "sky" | "violet";
  className?: string;
  valueClassName?: string;
  noteClassName?: string;
}) {
  const toneMap = {
    slate: {
      wrapper: "border-slate-200 bg-slate-50/85",
      icon: "bg-white text-slate-500",
    },
    emerald: {
      wrapper: "border-emerald-100 bg-emerald-50/70",
      icon: "bg-white text-emerald-600",
    },
    amber: {
      wrapper: "border-amber-100 bg-amber-50/70",
      icon: "bg-white text-amber-600",
    },
    indigo: {
      wrapper: "border-indigo-100 bg-indigo-50/70",
      icon: "bg-white text-indigo-600",
    },
    sky: {
      wrapper: "border-cyan-100 bg-cyan-50/70",
      icon: "bg-white text-cyan-600",
    },
    violet: {
      wrapper: "border-violet-100 bg-violet-50/70",
      icon: "bg-white text-violet-600",
    },
  }[tone];
  const renderedValue = canAnimateAdminValue(value) ? <AdminAnimatedNumber value={value} /> : value;

  return (
    <div className={joinAdminClassNames("h-full rounded-[22px] border px-3 py-3", toneMap.wrapper, className)}>
      <div className="flex items-start gap-3">
        <span className={joinAdminClassNames("flex h-9 w-9 shrink-0 items-center justify-center rounded-2xl shadow-[0_6px_18px_rgba(15,23,42,0.05)]", toneMap.icon)}>
          <Icon size={16} />
        </span>
        <div className="min-w-0 flex-1">
          <div className="admin-typography-data-label text-slate-400">{label}</div>
          <div className={joinAdminClassNames("admin-typography-mini-value mt-1 text-slate-900 break-words [overflow-wrap:anywhere]", valueClassName)}>{renderedValue}</div>
          {note ? <div className={joinAdminClassNames("admin-typography-caption mt-1 text-slate-500 break-words [overflow-wrap:anywhere]", noteClassName)}>{note}</div> : null}
        </div>
      </div>
    </div>
  );
}

export default function AdminRuntimePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const createConsoleRequest = useLatestRequest();
  const rawTab = searchParams.get("tab");
  const consoleCache = useAdminStaleCache<RuntimeConsoleCachePayload>("admin-runtime:console");

  const [loading, setLoading] = useState(() => !consoleCache.hasCache);
  const [error, setError] = useState<string | null>(null);
  const [generatedAt, setGeneratedAt] = useState<TimeValue>(() => consoleCache.cached?.generatedAt ?? null);
  const activeTab = useMemo(() => normalizeTab(rawTab), [rawTab]);

  const [flags, setFlags] = useState<FeatureFlagItem[]>(() => consoleCache.cached?.flags ?? []);
  const [savingKey, setSavingKey] = useState<string | null>(null);

  const [runtimeSettings, setRuntimeSettings] = useState<RuntimeSettingsPayload | null>(() => consoleCache.cached?.runtimeSettings ?? null);
  const [runtimeSaving, setRuntimeSaving] = useState(false);

  const [policies, setPolicies] = useState<ModerationPoliciesResponse | null>(() => consoleCache.cached?.policies ?? null);
  const [moderationSaving, setModerationSaving] = useState(false);

  const [aiChannels, setAiChannels] = useState<AiChannelOverviewPayload | null>(() => consoleCache.cached?.aiChannels ?? null);
  const [routeDetails, setRouteDetails] = useState<RouteDetailItem[]>(() => consoleCache.cached?.routeDetails ?? []);
  const [routeDetailsError, setRouteDetailsError] = useState<string | null>(null);
  const [routeActionLoadingId, setRouteActionLoadingId] = useState<number | null>(null);
  const [routePolicies, setRoutePolicies] = useState<RoutePolicyDetailItem[]>(() => consoleCache.cached?.routePolicies ?? []);
  const [routePoliciesError, setRoutePoliciesError] = useState<string | null>(null);

  const [templateDetails, setTemplateDetails] = useState<PromptTemplateDetailItem[]>(() => consoleCache.cached?.templateDetails ?? []);
  const [templateDetailsError, setTemplateDetailsError] = useState<string | null>(null);

  const [runtimeForm] = Form.useForm<RuntimeFormValues>();
  const [moderationForm] = Form.useForm<ModerationFormValues>();

  const watchedDebugModeEnabled = Form.useWatch("debugModeEnabled", runtimeForm);
  const watchedAiRequestLogEnabled = Form.useWatch("aiRequestLogEnabled", runtimeForm);
  const watchedDefaultReasoningEffort = Form.useWatch("defaultReasoningEffort", runtimeForm);
  const watchedDefaultThinkingBudget = Form.useWatch("defaultThinkingBudget", runtimeForm);
  const watchedDefaultThinkingLevel = Form.useWatch("defaultThinkingLevel", runtimeForm);
  const watchedAiInputEnabled = Form.useWatch("aiInputEnabled", moderationForm);
  const watchedAiOutputEnabled = Form.useWatch("aiOutputEnabled", moderationForm);
  const watchedCommunityStrictReviewEnabled = Form.useWatch("communityStrictReviewEnabled", moderationForm);
  const watchedAutoHideReportThreshold = Form.useWatch("autoHideReportThreshold", moderationForm);

  const loadConsole = useCallback(async (showLoading = true) => {
    const request = createConsoleRequest();
    if (showLoading) {
      setLoading(true);
    }
    setError(null);

    try {
      // 主快照是必需数据，AI 路由/策略/模板作为附加面板独立容错。
      const [snapshotResult, routeDetailsResult, routePoliciesResult, templateDetailsResult] = await Promise.allSettled([
        apiRequest<ConsoleSnapshotPayload>("/admin/system/console-snapshot", { signal: request.signal }),
        apiRequest<{ records: RouteDetailItem[] }>("/admin/ai/routes", { signal: request.signal }),
        apiRequest<{ records: RoutePolicyDetailItem[] }>("/admin/ai/route-policies", { signal: request.signal }),
        apiRequest<{ records: PromptTemplateDetailItem[] }>("/admin/ai/prompt-templates", { signal: request.signal }),
      ]);

      if (!request.isCurrent()) {
        return;
      }

      if (snapshotResult.status !== "fulfilled") {
        throw snapshotResult.reason;
      }

      const snapshot = snapshotResult.value;
      setGeneratedAt(snapshot.generatedAt);
      setFlags(snapshot.featureFlags ?? []);
      setRuntimeSettings(snapshot.runtimeSettings);
      setPolicies(snapshot.moderationPolicies);
      setAiChannels(snapshot.aiChannels);
      runtimeForm.setFieldsValue(snapshot.runtimeSettings);
      moderationForm.setFieldsValue(snapshot.moderationPolicies);
      const nextRouteDetails = routeDetailsResult.status === "fulfilled" ? routeDetailsResult.value.records ?? [] : [];
      const nextRoutePolicies = routePoliciesResult.status === "fulfilled" ? routePoliciesResult.value.records ?? [] : [];
      const nextTemplateDetails = templateDetailsResult.status === "fulfilled" ? templateDetailsResult.value.records ?? [] : [];

      if (routeDetailsResult.status === "fulfilled") {
        setRouteDetails(nextRouteDetails);
        setRouteDetailsError(null);
      } else {
        const apiError = routeDetailsResult.reason as ApiClientError;
        setRouteDetails([]);
        setRouteDetailsError(apiError.message || "AI 路由详情加载失败");
      }

      if (routePoliciesResult.status === "fulfilled") {
        setRoutePolicies(nextRoutePolicies);
        setRoutePoliciesError(null);
      } else {
        const apiError = routePoliciesResult.reason as ApiClientError;
        setRoutePolicies([]);
        setRoutePoliciesError(apiError.message || "AI 场景策略加载失败");
      }

      if (templateDetailsResult.status === "fulfilled") {
        setTemplateDetails(nextTemplateDetails);
        setTemplateDetailsError(null);
      } else {
        const apiError = templateDetailsResult.reason as ApiClientError;
        setTemplateDetails([]);
        setTemplateDetailsError(apiError.message || "AI 模板详情加载失败");
      }
      consoleCache.write({
        generatedAt: snapshot.generatedAt,
        flags: snapshot.featureFlags ?? [],
        runtimeSettings: snapshot.runtimeSettings,
        policies: snapshot.moderationPolicies,
        aiChannels: snapshot.aiChannels,
        routeDetails: nextRouteDetails,
        routePolicies: nextRoutePolicies,
        templateDetails: nextTemplateDetails,
      });
    } catch (loadError) {
      if (isAbortError(loadError) || !request.isCurrent()) {
        return;
      }
      const apiError = loadError as ApiClientError;
      setError(apiError.message || "运行配置加载失败");
    } finally {
      if (request.isCurrent()) {
        setLoading(false);
      }
    }
  }, [consoleCache, createConsoleRequest, moderationForm, runtimeForm]);

  useEffect(() => {
    if (!consoleCache.cached) {
      return;
    }
    // stale cache 先恢复整页表单和卡片，再由 loadConsole 写入最新值。
    setGeneratedAt(consoleCache.cached.generatedAt);
    setFlags(consoleCache.cached.flags ?? []);
    setRuntimeSettings(consoleCache.cached.runtimeSettings);
    setPolicies(consoleCache.cached.policies);
    setAiChannels(consoleCache.cached.aiChannels);
    setRouteDetails(consoleCache.cached.routeDetails ?? []);
    setRoutePolicies(consoleCache.cached.routePolicies ?? []);
    setTemplateDetails(consoleCache.cached.templateDetails ?? []);
    if (consoleCache.cached.runtimeSettings) {
      runtimeForm.setFieldsValue(consoleCache.cached.runtimeSettings);
    }
    if (consoleCache.cached.policies) {
      moderationForm.setFieldsValue(consoleCache.cached.policies);
    }
  }, [consoleCache.cached, moderationForm, runtimeForm]);

  useEffect(() => {
    void loadConsole(!consoleCache.hasCache);
  }, [consoleCache.hasCache, loadConsole]);

  const sortedFlags = useMemo(
    () => [...flags].sort((left, right) => left.displayName.localeCompare(right.displayName, "zh-CN")),
    [flags],
  );
  const safeRouteDetails = Array.isArray(routeDetails) ? routeDetails : [];
  const safeRoutePolicies = Array.isArray(routePolicies) ? routePolicies : [];
  const safeTemplateDetails = Array.isArray(templateDetails) ? templateDetails : [];

  const overriddenFlagCount = useMemo(() => sortedFlags.filter((flag) => flag.overridden).length, [sortedFlags]);
  const highRiskFlagCount = useMemo(
    () => sortedFlags.filter((flag) => (featureFlagMetaMap[flag.key]?.risk ?? "MEDIUM") === "HIGH").length,
    [sortedFlags],
  );
  const paymentMode = sortedFlags.find((flag) => flag.key === "payment.mode")?.currentValue ?? "MOCK";
  const currentDebugModeEnabled = watchedDebugModeEnabled ?? runtimeSettings?.debugModeEnabled ?? false;
  const currentAiRequestLogEnabled = watchedAiRequestLogEnabled ?? runtimeSettings?.aiRequestLogEnabled ?? false;
  const persistedDefaultThinkingSummary = formatThinkingSummary(
    runtimeSettings?.defaultReasoningEffort ?? null,
    runtimeSettings?.defaultThinkingBudget ?? null,
    runtimeSettings?.defaultThinkingLevel ?? null,
  ) || "沿用代码内置推荐";
  const currentAiInputEnabled = watchedAiInputEnabled ?? policies?.aiInputEnabled ?? false;
  const currentAiOutputEnabled = watchedAiOutputEnabled ?? policies?.aiOutputEnabled ?? false;
  const currentCommunityStrictReviewEnabled = watchedCommunityStrictReviewEnabled ?? policies?.communityStrictReviewEnabled ?? false;
  const enabledObservabilityCount = Number(currentDebugModeEnabled) + Number(currentAiRequestLogEnabled);
  const watchedThreshold = watchedAutoHideReportThreshold ?? policies?.autoHideReportThreshold ?? 1;
  const relaxedGuardrails = policies
    ? [
        currentAiInputEnabled ? null : "AI 输入审查",
        currentAiOutputEnabled ? null : "AI 输出审查",
        currentCommunityStrictReviewEnabled ? null : "社区严格审查",
      ].filter((item): item is string => Boolean(item))
    : [];
  const enabledGuardrailCount = policies ? 3 - relaxedGuardrails.length : 0;

  const sceneControlRows = useMemo<RuntimeSceneControlRow[]>(() => {
    // 以固定场景目录为主轴，把路由、策略和模板版本挂回同一行展示。
    const catalogMap = new Map(adminAiChannelCatalog.map((item) => [buildSceneMetricKey(item.taskType, item.sceneCode), item]));
    const routeMap = new Map<string, RouteDetailItem[]>();
    const policyMap = new Map<string, RoutePolicyDetailItem[]>();
    safeRouteDetails.forEach((route) => {
      if (!normalizeSceneCode(route.sceneCode)) {
        return;
      }
      const routeKey = buildSceneMetricKey(route.taskType, route.sceneCode);
      const current = routeMap.get(routeKey) ?? [];
      current.push(route);
      routeMap.set(routeKey, current);
    });
    safeRoutePolicies.forEach((policy) => {
      const policyKey = buildSceneMetricKey(policy.taskType, policy.sceneCode);
      const current = policyMap.get(policyKey) ?? [];
      current.push(policy);
      policyMap.set(policyKey, current);
    });
    const templateMap = new Map<string, PromptTemplateDetailItem[]>();
    safeTemplateDetails.forEach((template) => {
      const templateKey = `${template.taskType}::${template.templateName}`;
      const current = templateMap.get(templateKey) ?? [];
      current.push(template);
      templateMap.set(templateKey, current);
    });

    const sceneKeys = new Set<string>();
    adminAiChannelCatalog.forEach((item) => {
      sceneKeys.add(buildSceneMetricKey(item.taskType, item.sceneCode));
    });
    safeRouteDetails.forEach((route) => {
      if (normalizeSceneCode(route.sceneCode)) {
        sceneKeys.add(buildSceneMetricKey(route.taskType, route.sceneCode));
      }
    });

    return Array.from(sceneKeys)
      .map((sceneKey) => {
        const catalogItem = catalogMap.get(sceneKey);
        const [taskType, rawSceneCode] = sceneKey.split("::");
        const matchedRoutes = [...(routeMap.get(sceneKey) ?? [])].sort(sortRouteDetails);
        const matchedPolicies = [...(policyMap.get(sceneKey) ?? [])].sort((left, right) => {
          const tierOrder = { ALL: 0, FREE: 1, PREMIUM: 2 };
          return (tierOrder[left.userTier as keyof typeof tierOrder] ?? 9) - (tierOrder[right.userTier as keyof typeof tierOrder] ?? 9);
        });
        const primaryRoute = matchedRoutes[0] ?? null;
        const primaryPolicy = matchedPolicies.find((policy) => policy.userTier === "ALL") ?? matchedPolicies[0] ?? null;
        const matchedTemplates = primaryRoute?.promptTemplateName
          ? [...(templateMap.get(`${taskType}::${primaryRoute.promptTemplateName}`) ?? [])].sort((left, right) => right.versionNo - left.versionNo)
          : [];
        const activeTemplate = matchedTemplates.find((template) => template.status === "ACTIVE") ?? null;
        const thinkingConfiguredCount = matchedPolicies.filter((policy) => Boolean(
          policy.reasoningEffort || typeof policy.thinkingBudget === "number" || policy.thinkingLevel,
        )).length;
        // thinking 配置在策略层展示，便于答辩说明不同场景可独立控成本和推理强度。
        const thinkingSummary = primaryPolicy
          ? formatThinkingSummary(primaryPolicy.reasoningEffort, primaryPolicy.thinkingBudget, primaryPolicy.thinkingLevel) || "沿用系统默认"
          : "未设置策略";

        return {
          sceneKey,
          channelCode: catalogItem?.channelCode ?? sceneKey.toLowerCase().replace(/::/g, "."),
          displayName: (catalogItem?.displayName ?? rawSceneCode) || taskType,
          ownerDomain: catalogItem?.ownerDomain ?? "待补充分组",
          frontEntry: catalogItem?.frontEntry ?? "待补充",
          registered: Boolean(catalogItem),
          taskType,
          primaryRoute,
          primaryPolicy,
          policies: matchedPolicies,
          routeCount: matchedRoutes.length,
          differentiated: matchedPolicies.some((policy) => policy.userTier === "FREE" || policy.userTier === "PREMIUM"),
          thinkingConfiguredCount,
          thinkingSummary,
          activeTemplate,
          templateVersionCount: matchedTemplates.length,
        };
      })
      .sort((left, right) => {
        const leftScore = Number(Boolean(left.primaryRoute?.enabled)) * 4 + Number(Boolean(left.primaryRoute)) * 2 + Number(left.registered);
        const rightScore = Number(Boolean(right.primaryRoute?.enabled)) * 4 + Number(Boolean(right.primaryRoute)) * 2 + Number(right.registered);
        return rightScore - leftScore || left.ownerDomain.localeCompare(right.ownerDomain, "zh-CN") || left.displayName.localeCompare(right.displayName, "zh-CN");
      });
  }, [safeRouteDetails, safeRoutePolicies, safeTemplateDetails]);

  const sceneControlSummary = useMemo(() => {
    const totalSceneCount = sceneControlRows.length;
    const enabledPrimaryCount = sceneControlRows.filter((item) => item.primaryRoute?.enabled).length;
    const disabledPrimaryCount = sceneControlRows.filter((item) => item.primaryRoute && !item.primaryRoute.enabled).length;
    const missingPrimaryCount = sceneControlRows.filter((item) => !item.primaryRoute).length;
    const pendingTemplateCount = sceneControlRows.filter((item) => item.primaryRoute?.promptTemplateName && !item.activeTemplate).length;
    const unregisteredCount = sceneControlRows.filter((item) => !item.registered).length;
    const thinkingConfiguredSceneCount = sceneControlRows.filter((item) => item.thinkingConfiguredCount > 0).length;
    return {
      totalSceneCount,
      enabledPrimaryCount,
      disabledPrimaryCount,
      missingPrimaryCount,
      pendingTemplateCount,
      unregisteredCount,
      thinkingConfiguredSceneCount,
    };
  }, [sceneControlRows]);

  const runtimeSummary = useMemo(() => ({
    generatedAt,
    totalFlags: sortedFlags.length,
    overriddenFlagCount,
    enabledObservabilityCount,
    threshold: watchedThreshold,
    enabledProviders: aiChannels?.enabledProviders ?? 0,
    totalProviders: aiChannels?.totalProviders ?? 0,
    enabledRoutes: aiChannels?.enabledRoutes ?? safeRouteDetails.filter((item) => item.enabled).length,
    totalRoutes: aiChannels?.totalRoutes ?? safeRouteDetails.length,
    activePromptTemplates: aiChannels?.activePromptTemplates ?? safeTemplateDetails.filter((item) => item.status === "ACTIVE").length,
    totalPromptTemplates: aiChannels?.totalPromptTemplates ?? safeTemplateDetails.length,
  }), [
    aiChannels,
    enabledObservabilityCount,
    generatedAt,
    overriddenFlagCount,
    safeRouteDetails,
    safeTemplateDetails,
    sortedFlags.length,
    watchedThreshold,
  ]);

  const workspaceMeta = useMemo(() => {
    if (activeTab === "basic") {
      return {
        sectionLabel: "BASIC CONTROLS",
        title: "基础策略",
        description: "统一维护业务开关、运行记录与风险保护等高频策略；更细的 AI 接入设置继续前往 AI 网关处理。",
        tone: "indigo" as const,
      };
    }
    if (activeTab === "ai-channels") {
      return {
        sectionLabel: "AI CHANNELS",
        title: "AI 运行态",
        description: "这里主要确认场景是否在提供服务、当前方案是否开启，以及内容方案是否已启用；调用量、成功率和成本统一前往 AI 应用运营查看。",
        tone: "emerald" as const,
      };
    }
    return {
      sectionLabel: "RUNTIME CONFIG",
      title: "运行配置",
      description: "先查看全局状态，再从左侧进入基础策略或 AI 运行态工作区完成调整。",
      tone: "indigo" as const,
    };
  }, [activeTab]);

  const handleUpdateFlag = useCallback(async (flag: FeatureFlagItem, nextValue: string) => {
    setSavingKey(flag.key);
    try {
      // feature flag 统一走键值接口，页面只替换当前 key 的返回结果。
      const updated = await apiRequest<FeatureFlagItem>("/admin/feature-flags", {
        method: "POST",
        body: JSON.stringify({ key: flag.key, value: nextValue }),
      });
      setFlags((current) => current.map((item) => (item.key === updated.key ? updated : item)));
      message.success(`已更新业务开关：${flag.displayName}`);
    } catch (updateError) {
      const apiError = updateError as ApiClientError;
      message.error(apiError.message || "更新业务开关失败");
    } finally {
      setSavingKey(null);
    }
  }, []);

  const handleSaveRuntime = useCallback(async (successMessage = "运行记录设置已保存") => {
    try {
      const values = await runtimeForm.validateFields();
      setRuntimeSaving(true);
      try {
        // 运行记录设置保存后立即回填表单，避免 number/null 转换造成显示漂移。
        const response = await apiRequest<RuntimeSettingsPayload>("/admin/ai/runtime-settings", {
          method: "PUT",
          body: JSON.stringify({
            debugModeEnabled: values.debugModeEnabled,
            aiRequestLogEnabled: values.aiRequestLogEnabled,
            defaultReasoningEffort: values.defaultReasoningEffort || null,
            defaultThinkingBudget: typeof values.defaultThinkingBudget === "number" ? values.defaultThinkingBudget : null,
            defaultThinkingLevel: values.defaultThinkingLevel || null,
          }),
        });
        setRuntimeSettings(response);
        runtimeForm.setFieldsValue(response);
        message.success(successMessage);
      } catch (saveError) {
        const apiError = saveError as ApiClientError;
        message.error(apiError.message || "保存运行记录设置失败");
      } finally {
        setRuntimeSaving(false);
      }
    } catch {
      return;
    }
  }, [runtimeForm]);

  const handleSaveModeration = useCallback(async (successMessage: string) => {
    try {
      const values = await moderationForm.validateFields();
      setModerationSaving(true);
      try {
        // 治理策略是全局开关，保存后会影响社区、AI 输入和 AI 输出三条链路。
        const response = await apiRequest<ModerationPoliciesResponse>("/admin/content/moderation/policies", {
          method: "PUT",
          body: JSON.stringify(values),
        });
        setPolicies(response);
        moderationForm.setFieldsValue(response);
        message.success(successMessage);
      } catch (saveError) {
        const apiError = saveError as ApiClientError;
        message.error(apiError.message || "保存治理参数失败");
      } finally {
        setModerationSaving(false);
      }
    } catch {
      return;
    }
  }, [moderationForm]);

  const handleToggleRouteEnabled = useCallback(async (routeId: number, nextEnabled: boolean) => {
    const route = safeRouteDetails.find((item) => item.id === routeId);
    if (!route) {
      message.error("当前承接方案信息尚未就绪，请刷新后重试");
      return;
    }

    setRouteActionLoadingId(routeId);
    try {
      // 运行配置页只切 enabled，其他路由字段按原值回传避免被后端置空。
      await apiRequest<RouteDetailItem>(`/admin/ai/routes/${routeId}`, {
        method: "PUT",
        body: JSON.stringify({
          routeCode: route.routeCode,
          taskType: route.taskType,
          sceneCode: route.sceneCode,
          providerConfigId: route.providerConfigId,
          modelName: route.modelName,
          priorityNo: route.priorityNo,
          executionMode: route.executionMode,
          enabled: nextEnabled,
          temperature: route.temperature,
          systemPrompt: route.systemPrompt,
          promptTemplateName: route.promptTemplateName,
          extraConfigJson: route.extraConfigJson,
        }),
      });
      message.success(`当前承接方案已${nextEnabled ? "开启" : "关闭"}`);
      await loadConsole();
    } catch (toggleError) {
      const apiError = toggleError as ApiClientError;
      message.error(apiError.message || "更新承接状态失败");
    } finally {
      setRouteActionLoadingId(null);
    }
  }, [loadConsole, safeRouteDetails]);

  useEffect(() => {
    if (activeTab !== "basic") {
      return;
    }
    if (!rawTab || !["business", "operations", "degrade", "flags", "runtime", "moderation"].includes(rawTab)) {
      return;
    }
    // 兼容旧 tab 参数，进入 basic 后滚到对应子区块。
    const sectionId = normalizeBasicSection(rawTab);
    const timer = window.setTimeout(() => {
      const element = document.getElementById(`runtime-basic-${sectionId}`);
      element?.scrollIntoView({ block: "start" });
    }, 0);
    return () => window.clearTimeout(timer);
  }, [activeTab, rawTab]);

  const renderHome = () => {
    const workspaceCards = [
      {
        key: "basic",
        title: "基础策略",
        description: "业务开关、运行记录和风险保护的统一入口。",
        tone: "indigo" as const,
        icon: Settings2,
        to: "/admin/runtime?tab=basic",
        facets: ["业务开关", "运行记录", "风险保护"],
        note: relaxedGuardrails.length > 0
          ? "当前存在已放宽的保护项，进入后优先确认风险边界。"
          : runtimeSummary.overriddenFlagCount > 0
            ? "当前有部分入口已单独调整，进入后优先确认是否仍需保持。"
            : "当前基础策略整体稳定，可按工作区继续查看细项配置。",
      },
      {
        key: "ai-channels",
        title: "AI 运行态",
        description: "集中查看场景是否在提供服务、当前方案是否开启，以及内容方案是否已启用。",
        tone: "emerald" as const,
        icon: BrainCircuit,
        to: "/admin/runtime?tab=ai-channels",
        facets: ["场景开关", "场景承接", "内容方案"],
        note: sceneControlSummary.missingPrimaryCount + sceneControlSummary.pendingTemplateCount > 0
          ? `当前有 ${formatCount(sceneControlSummary.missingPrimaryCount)} 个场景待补承接方案，${formatCount(sceneControlSummary.pendingTemplateCount)} 个场景待启用内容方案。`
          : "当前场景接入状态稳定，可继续按需进入工作区做精细调整。",
      },
    ] as const;

    const detailPanels = [
      {
        key: "basic-detail",
        title: "基础策略明细",
        tone: "indigo" as const,
        rows: [
          {
            label: "单独调整项",
            value: `${formatCount(runtimeSummary.overriddenFlagCount)} 项`,
            note: "当前使用单独设置的入口策略",
            icon: Settings2,
            iconClassName: "bg-indigo-100 text-indigo-600",
          },
          {
            label: "高风险开关",
            value: formatCount(highRiskFlagCount),
            note: "建议优先核对是否仍需保持",
            icon: AlertTriangle,
            iconClassName: "bg-rose-100 text-rose-600",
          },
          {
            label: "运行记录",
            value: `${runtimeSummary.enabledObservabilityCount}/2 开启`,
            note: "当前记录能力开启情况",
            icon: SlidersHorizontal,
            iconClassName: "bg-amber-100 text-amber-600",
          },
          {
            label: "自动隐藏条件",
            value: `${watchedThreshold} 次`,
            note: "达到后内容会先隐藏",
            icon: ShieldCheck,
            iconClassName: "bg-emerald-100 text-emerald-600",
          },
        ],
      },
      {
        key: "channel-detail",
        title: "AI 运行态明细",
        tone: "emerald" as const,
        rows: [
          {
            label: "已承接场景",
            value: formatCount(sceneControlSummary.enabledPrimaryCount),
            note: "当前方案已开启，可正常提供服务的场景",
            icon: Route,
            iconClassName: "bg-emerald-100 text-emerald-600",
          },
          {
            label: "待补承接方案",
            value: formatCount(sceneControlSummary.missingPrimaryCount),
            note: "场景已纳入管理，但尚未完成承接设置",
            icon: AlertTriangle,
            iconClassName: "bg-amber-100 text-amber-600",
          },
          {
            label: "已暂停承接",
            value: formatCount(sceneControlSummary.disabledPrimaryCount),
            note: "当前方案已停用，建议确认是否仍需恢复",
            icon: SlidersHorizontal,
            iconClassName: "bg-rose-100 text-rose-600",
          },
          {
            label: "内容方案待启用",
            value: formatCount(sceneControlSummary.pendingTemplateCount),
            note: "已选择内容方案，但当前还没有启用版本",
            icon: Sparkles,
            iconClassName: "bg-fuchsia-100 text-fuchsia-600",
          },
        ],
      },
    ] as const;

    const focusItems = [
      {
        key: "flags",
        title: "业务开关",
        tone: runtimeSummary.overriddenFlagCount > 0 ? "indigo" as const : "slate" as const,
        summary: runtimeSummary.overriddenFlagCount > 0
          ? "入口策略存在单独调整"
          : "当前保持默认策略",
        detail: runtimeSummary.overriddenFlagCount > 0
          ? "建议确认当前入口策略是否仍然需要维持单独设置。"
          : "可继续按需维护新增入口级开关。",
        to: "/admin/runtime?tab=basic",
      },
      {
        key: "channels",
        title: "AI 运行态",
        tone: sceneControlSummary.missingPrimaryCount + sceneControlSummary.disabledPrimaryCount + sceneControlSummary.pendingTemplateCount > 0 ? "amber" as const : "emerald" as const,
        summary: sceneControlSummary.missingPrimaryCount + sceneControlSummary.disabledPrimaryCount + sceneControlSummary.pendingTemplateCount > 0
          ? "存在待处理的接入项"
          : "当前接入状态稳定",
        detail: sceneControlSummary.missingPrimaryCount + sceneControlSummary.disabledPrimaryCount + sceneControlSummary.pendingTemplateCount > 0
          ? `待补承接方案 ${formatCount(sceneControlSummary.missingPrimaryCount)} 个，已暂停承接 ${formatCount(sceneControlSummary.disabledPrimaryCount)} 个，内容方案待启用 ${formatCount(sceneControlSummary.pendingTemplateCount)} 个。`
          : "业务表现统一去 AI 应用运营查看，这里只处理开关与承接。",
        to: "/admin/runtime?tab=ai-channels",
      },
      {
        key: "guardrails",
        title: "风险保护",
        tone: relaxedGuardrails.length > 0 ? "rose" as const : "emerald" as const,
        summary: relaxedGuardrails.length > 0 ? "当前存在放宽项" : "当前保护保持全开",
        detail: relaxedGuardrails.length > 0
          ? `已放宽 ${relaxedGuardrails.join("、")}，建议同步回看内容治理。`
          : "可继续按工作区查看细项配置。",
        to: "/admin/runtime?tab=basic",
      },
    ] as const;

    const linkedWorkspaces = [
      {
        key: "applications",
        title: "AI 应用运营",
        description: "查看场景调用量、成功率、成本、活跃度与权益策略，不与当前配置台重复。",
        to: "/admin/ai/applications",
      },
      {
        key: "gateway",
        title: "AI 网关",
        description: "继续处理承接方案、内容版本与服务商侧的更细设置。",
        to: "/admin/ai/gateway",
      },
      {
        key: "content",
        title: "内容治理",
        description: "回看举报、审核和词库状态，确认风险保护调整后的影响。",
        to: "/admin/content",
      },
    ] as const;

    return (
      <div className="space-y-6">
        <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-4">
          <AdminMetricCard
            icon={Flag}
            label="业务开关"
            value={loading ? "—" : formatCount(runtimeSummary.overriddenFlagCount)}
            note={`当前高风险开关 ${formatCount(highRiskFlagCount)} 项`}
            badge="FLAGS"
            tone="indigo"
          />
          <AdminMetricCard
            icon={SlidersHorizontal}
            label="运行记录"
            value={loading ? "—" : `${watchedThreshold} 次`}
            note={`${runtimeSummary.enabledObservabilityCount}/2 项记录能力开启`}
            badge="OPS"
            tone="amber"
          />
          <AdminMetricCard
            icon={BrainCircuit}
            label="AI 运行态"
            value={loading ? "—" : formatCount(sceneControlSummary.enabledPrimaryCount)}
            note={`${formatCount(sceneControlSummary.missingPrimaryCount)} 个场景待补承接方案 · ${formatCount(sceneControlSummary.pendingTemplateCount)} 个内容方案待启用`}
            badge="AI"
            tone="emerald"
          />
          <AdminMetricCard
            icon={ShieldAlert}
            label="风险保护"
            value={loading ? "—" : relaxedGuardrails.length > 0 ? `${relaxedGuardrails.length} 项` : "全开"}
            note={relaxedGuardrails.length > 0 ? `当前已放宽：${relaxedGuardrails.join("、")}` : "当前未放宽任何保护项"}
            badge="GUARD"
            tone="rose"
          />
        </div>

        <div className="grid gap-6 xl:grid-cols-[minmax(0,1.45fr)_360px] xl:items-start">
          <div className="space-y-6">
            <AdminSurfaceCard
              title={(
                <div className="flex items-center gap-3">
                  <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-indigo-100 text-indigo-600">
                    <Workflow size={20} />
                  </span>
                  <span>工作台</span>
                </div>
              )}
              description={`数据刷新于 ${formatDateTime(runtimeSummary.generatedAt, "待生成")}，从这里直接进入当前需要处理的工作区。`}
            >
              <div className="grid gap-4 xl:grid-cols-2">
                {workspaceCards.map((item) => {
                  const Icon = item.icon;
                  const toneClassName = item.tone === "indigo"
                    ? "border-slate-200 bg-[linear-gradient(180deg,rgba(99,102,241,0.06),rgba(255,255,255,0.98))] shadow-[0_10px_26px_rgba(15,23,42,0.05)]"
                    : "border-slate-200 bg-[linear-gradient(180deg,rgba(16,185,129,0.06),rgba(255,255,255,0.98))] shadow-[0_10px_26px_rgba(15,23,42,0.05)]";
                  const iconClassName = item.tone === "indigo"
                    ? "bg-indigo-100 text-indigo-600 ring-1 ring-indigo-200/70"
                    : "bg-emerald-100 text-emerald-600 ring-1 ring-emerald-200/70";
                  const noteClassName = item.tone === "indigo"
                    ? "border-indigo-100 bg-indigo-50/55"
                    : "border-emerald-100 bg-emerald-50/55";

                  return (
                    <div key={item.key} className={joinAdminClassNames("rounded-[28px] border px-5 py-5", toneClassName)}>
                      <div className="flex items-start justify-between gap-3">
                        <span className={joinAdminClassNames("flex h-11 w-11 items-center justify-center rounded-2xl", iconClassName)}>
                          <Icon size={18} />
                        </span>
                        <Button
                          className="!rounded-xl !border-slate-200 !bg-white"
                          onClick={() => navigate(item.to)}
                        >
                          进入工作区
                        </Button>
                      </div>
                    <div className="mt-4 font-['Manrope'] text-[1.45rem] font-black tracking-tight text-slate-950">{item.title}</div>
                    <div className="mt-2 text-sm leading-6 text-slate-500">{item.description}</div>
                    <div className="mt-4 flex flex-wrap gap-2">
                      {item.facets.map((facet) => (
                        <SettingBadge key={facet} label={facet} tone={item.tone} />
                      ))}
                    </div>
                    <div className={joinAdminClassNames("mt-4 rounded-2xl border px-4 py-4 text-sm leading-6 text-slate-600", noteClassName)}>
                      {item.note}
                    </div>
                  </div>
                );
              })}
              </div>
            </AdminSurfaceCard>

            <div className="grid gap-6 xl:grid-cols-2">
              {detailPanels.map((panel) => (
                <AdminSurfaceCard
                  key={panel.key}
                  title={panel.title}
                  description="下方保留更详细的状态明细，便于进入工作区前先完成一次判断。"
                >
                  <div className="grid gap-4 sm:grid-cols-2">
                    {panel.rows.map((row) => {
                      const Icon = row.icon;
                      const cardClassName = panel.tone === "indigo"
                        ? "border-slate-200 bg-[linear-gradient(180deg,rgba(99,102,241,0.04),rgba(255,255,255,0.98))] shadow-[0_8px_18px_rgba(15,23,42,0.04)]"
                        : "border-slate-200 bg-[linear-gradient(180deg,rgba(16,185,129,0.04),rgba(255,255,255,0.98))] shadow-[0_8px_18px_rgba(15,23,42,0.04)]";
                      const valueClassName = "text-slate-950";

                      return (
                        <div
                          key={row.label}
                          className={joinAdminClassNames(
                            "rounded-[22px] border px-4 py-4 shadow-none",
                            cardClassName,
                          )}
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              <div className="flex items-center gap-3">
                                <span className={joinAdminClassNames("flex h-10 w-10 items-center justify-center rounded-2xl ring-1 ring-black/5", row.iconClassName)}>
                                  <Icon size={17} />
                                </span>
                                <div className="text-[11px] font-bold text-slate-400">{row.label}</div>
                              </div>
                              <div className={joinAdminClassNames("mt-3 font-['Manrope'] text-[1.85rem] font-black tracking-[-0.05em]", valueClassName)}>
                                {row.value}
                              </div>
                            </div>
                          </div>
                          <div className="mt-3 text-sm leading-6 text-slate-500">{row.note}</div>
                        </div>
                      );
                    })}
                  </div>
                </AdminSurfaceCard>
              ))}
            </div>
          </div>

          <div className="space-y-6">
            <AdminSurfaceCard title="优先关注" description="直接显示当前更需要优先处理的配置方向。">
              <div className="space-y-3">
                {focusItems.map((item) => {
                  const toneClassName = item.tone === "indigo"
                    ? "border-indigo-100 bg-indigo-50/70"
                    : item.tone === "amber"
                      ? "border-amber-100 bg-amber-50/70"
                      : item.tone === "rose"
                        ? "border-rose-100 bg-rose-50/70"
                        : item.tone === "emerald"
                          ? "border-emerald-100 bg-emerald-50/70"
                          : "border-slate-200 bg-slate-50";
                  return (
                    <button
                      key={item.key}
                      type="button"
                      className={joinAdminClassNames(
                        "w-full rounded-[24px] border px-4 py-4 text-left transition hover:-translate-y-0.5 hover:shadow-[0_12px_24px_rgba(15,23,42,0.06)]",
                        toneClassName,
                      )}
                      onClick={() => navigate(item.to)}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <div className="text-sm font-semibold text-slate-900">{item.title}</div>
                          <div className="mt-2 text-sm font-semibold text-slate-700">{item.summary}</div>
                        </div>
                        <ArrowRight size={16} className="mt-1 text-slate-400" />
                      </div>
                      <div className="mt-2 text-sm leading-6 text-slate-500">{item.detail}</div>
                    </button>
                  );
                })}
              </div>
            </AdminSurfaceCard>

            <AdminSurfaceCard title="关联工作区" description="主页判断出问题方向后，可继续在相关页面完成联动核查。">
              <div className="space-y-3">
                {linkedWorkspaces.map((item) => (
                  <Button
                    key={item.key}
                    className="!flex !h-auto !w-full !items-start !justify-between !rounded-[22px] !border-slate-200 !px-4 !py-4 !text-left"
                    onClick={() => navigate(item.to)}
                  >
                    <span className="block min-w-0">
                      <span className="block text-sm font-semibold text-slate-900">{item.title}</span>
                      <span className="mt-2 block whitespace-normal text-sm leading-6 text-slate-500">{item.description}</span>
                    </span>
                    <ArrowRight size={16} className="mt-1 shrink-0 text-slate-400" />
                  </Button>
                ))}
              </div>
            </AdminSurfaceCard>
          </div>
        </div>
      </div>
    );
  };

  const renderBusiness = () => {
    if (loading && sortedFlags.length === 0) {
      return (
        <div className="space-y-4">
          <Skeleton active paragraph={{ rows: 6 }} />
          <Skeleton active paragraph={{ rows: 6 }} />
        </div>
      );
    }

    if (sortedFlags.length === 0) {
      return <AdminDetailPlaceholder description="暂无业务开关配置" />;
    }

    return (
      <div className="space-y-6">
        <AdminSurfaceCard>
          <div className="mb-5 flex flex-col gap-3 border-b border-slate-100 pb-5 sm:flex-row sm:items-start sm:justify-between">
            <div className="flex items-start gap-3">
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-indigo-100 text-indigo-600">
                <Flag size={20} />
              </span>
              <div>
                <Text strong className="text-base text-slate-900">业务开关</Text>
                <Paragraph className="!mb-0 !mt-1 !text-sm !leading-6 !text-slate-500">
                  集中管理支付、AI 和社区等日常高频开关，适合快速启停与临时收口。
                </Paragraph>
              </div>
            </div>
            <SettingBadge label={`${formatCount(runtimeSummary.totalFlags)} 项`} tone="indigo" />
          </div>

          <div className="grid gap-4 xl:grid-cols-2 2xl:grid-cols-3">
            {sortedFlags.map((flag) => {
              const meta = featureFlagMetaMap[flag.key];
              const Icon = meta?.icon ?? Settings2;
              const risk = meta?.risk ?? "MEDIUM";
              const riskTone = risk === "HIGH" ? "rose" : risk === "MEDIUM" ? "amber" : "emerald";
              const isDisabledCard = flag.valueType === "BOOLEAN" && flag.currentValue !== "true";
              const impactMeta = {
                "payment.enabled": {
                  label: "支付与售后",
                },
                "payment.mode": {
                  label: "支付与售后",
                },
                "voice.interview.enabled": {
                  label: "AI 面试",
                },
                "voice.stt.enabled": {
                  label: "AI 面试",
                },
                "voice.tts.enabled": {
                  label: "AI 面试",
                },
                "community.ai-draft.enabled": {
                  label: "内容治理",
                },
                "community.ai-first-reply.enabled": {
                  label: "内容治理",
                },
                "student.portrait.async-refresh.enabled": {
                  label: "画像与推荐",
                },
                "student.portrait.summary.mode": {
                  label: "画像与推荐",
                },
              }[flag.key] ?? {
                label: "运行配置",
              };

              return (
                <div
                  key={flag.key}
                  className={joinAdminClassNames(
                    "flex h-full flex-col overflow-hidden rounded-[28px] border bg-white px-5 py-5 shadow-[0_16px_32px_rgba(15,23,42,0.05)]",
                    meta?.borderClassName ?? "border-slate-200",
                  )}
                >
                  <div className="relative flex flex-1 flex-col">
                    <div className="flex items-start gap-4">
                      <span className={joinAdminClassNames(
                        "flex h-11 w-11 items-center justify-center rounded-2xl",
                        meta?.accentClassName ?? "bg-slate-100 text-slate-600",
                      )}>
                        <Icon size={18} />
                      </span>
                      <div className="min-w-0 flex-1">
                        <div className="text-base font-semibold text-slate-900">{flag.displayName}</div>
                        <Paragraph className="!mb-0 !mt-2 !text-sm !leading-6 !text-slate-500">
                          {flag.description || meta?.helper || "由平台统一维护。"}
                        </Paragraph>
                      </div>
                    </div>

                    <div className="mt-4 flex flex-wrap gap-2">
                      <SettingBadge label={impactMeta.label} tone="indigo" />
                      <SettingBadge label={risk === "HIGH" ? "高风险" : risk === "MEDIUM" ? "中风险" : "低风险"} tone={riskTone} />
                      {flag.overridden ? <SettingBadge label="已单独调整" tone="emerald" /> : <SettingBadge label="沿用默认设置" tone="slate" />}
                    </div>

                    <div className="mt-4 grid gap-3 sm:grid-cols-2">
                      <SettingCompareCard label="当前设置" value={flag.currentValue} kind="current" />
                      <SettingCompareCard label="默认设置" value={flag.defaultValue} kind="default" />
                    </div>

                    {isDisabledCard ? <DisabledStripeMask /> : null}
                  </div>

                  <div className="mt-auto flex items-center justify-between gap-3 pt-4">
                    <div className="text-xs leading-5 text-slate-400">
                      最近更新 {formatDateTime(flag.updatedAt, "尚未单独配置")}{flag.updatedBy ? ` · 管理员 #${flag.updatedBy}` : ""}
                    </div>
                    {flag.valueType === "BOOLEAN" ? (
                      <AdminSettingSwitch
                        checked={flag.currentValue === "true"}
                        loading={savingKey === flag.key}
                        onChange={(checked) => void handleUpdateFlag(flag, checked ? "true" : "false")}
                      />
                    ) : (
                      <Select
                        value={flag.currentValue}
                        loading={savingKey === flag.key}
                        className="!min-w-[148px]"
                        options={flag.allowedValues.map((value) => ({
                          value,
                          label: enumLabelMap[value] ?? value,
                        }))}
                        onChange={(value) => void handleUpdateFlag(flag, String(value))}
                      />
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </AdminSurfaceCard>
      </div>
    );
  };

  const renderOperations = () => {
    if (loading && (!runtimeSettings || !policies)) {
      return (
        <div className="space-y-4">
          <Skeleton active paragraph={{ rows: 7 }} />
          <Skeleton active paragraph={{ rows: 6 }} />
        </div>
      );
    }

    if (!runtimeSettings || !policies) {
      return <AdminDetailPlaceholder description="暂无运行记录设置" />;
    }

    const observabilityCards = [
      {
        key: "debugModeEnabled" as const,
        title: "详细记录",
        description: "记录处理过程中的关键步骤，便于异常时回看原因。",
        sceneLabel: "问题回看",
        borderClassName: "border-indigo-200",
        iconWrap: "bg-indigo-100 text-indigo-600",
        statusTone: currentDebugModeEnabled ? "emerald" as const : "slate" as const,
        statusLabel: currentDebugModeEnabled ? "当前开启" : "当前关闭",
        currentValue: currentDebugModeEnabled,
        defaultValue: runtimeSettings.defaultDebugModeEnabled,
        icon: BrainCircuit,
      },
      {
        key: "aiRequestLogEnabled" as const,
        title: "调用摘要记录",
        description: "记录调用过程中的关键信息，方便日常查看运行变化。",
        sceneLabel: "日常查看",
        borderClassName: "border-amber-200",
        iconWrap: "bg-amber-100 text-amber-600",
        statusTone: currentAiRequestLogEnabled ? "emerald" as const : "slate" as const,
        statusLabel: currentAiRequestLogEnabled ? "当前开启" : "当前关闭",
        currentValue: currentAiRequestLogEnabled,
        defaultValue: runtimeSettings.defaultAiRequestLogEnabled,
        icon: Workflow,
      },
    ];

    return (
      <div className="space-y-6">
        <AdminSurfaceCard>
          <div className="mb-5 flex flex-col gap-3 border-b border-slate-100 pb-5 sm:flex-row sm:items-start sm:justify-between">
            <div className="flex items-start gap-3">
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-amber-100 text-amber-600">
                <SlidersHorizontal size={20} />
              </span>
              <div>
                <Text strong className="text-base text-slate-900">运行记录</Text>
                <Paragraph className="!mb-0 !mt-1 !text-sm !leading-6 !text-slate-500">
                  统一维护问题回看与日常记录范围，兼顾定位效率和记录成本。
                </Paragraph>
              </div>
            </div>
            <SettingBadge label={`${runtimeSummary.enabledObservabilityCount}/2 已开启`} tone="amber" />
          </div>

          <Form layout="vertical" form={runtimeForm}>
            <div className="grid gap-4 xl:grid-cols-2">
              {observabilityCards.map((item) => {
                const Icon = item.icon;
                const isDisabledCard = !item.currentValue;
                return (
                  <div
                    key={item.key}
                    className={joinAdminClassNames(
                      "flex h-full flex-col overflow-hidden rounded-[28px] border bg-white px-5 py-5 shadow-[0_16px_32px_rgba(15,23,42,0.05)]",
                      item.borderClassName,
                    )}
                  >
                    <div className="relative flex flex-1 flex-col">
                      <div className="flex items-start gap-4">
                        <span className={joinAdminClassNames("flex h-11 w-11 items-center justify-center rounded-2xl", item.iconWrap)}>
                          <Icon size={18} />
                        </span>
                        <div className="min-w-0 flex-1">
                          <div className="text-base font-semibold text-slate-900">{item.title}</div>
                          <Paragraph className="!mb-0 !mt-2 !text-sm !leading-6 !text-slate-500">{item.description}</Paragraph>
                        </div>
                      </div>

                      <div className="mt-4 flex flex-wrap gap-2">
                        <SettingBadge label={item.sceneLabel} tone="amber" />
                        <SettingBadge label={item.statusLabel} tone={item.statusTone} />
                      </div>

                      <div className="mt-4 grid gap-3 sm:grid-cols-2">
                        <SettingCompareCard label="当前状态" value={item.currentValue} kind="current" />
                        <SettingCompareCard label="默认状态" value={item.defaultValue} kind="default" />
                      </div>

                      {isDisabledCard ? <DisabledStripeMask /> : null}
                    </div>

                    <div className="mt-auto flex items-center justify-between gap-3 pt-4">
                      <div className="text-xs leading-5 text-slate-400">
                        最近更新 {formatDateTime(runtimeSettings.updatedAt, "尚未单独调整")}
                      </div>
                      <Form.Item name={item.key} valuePropName="checked" className="!mb-0">
                        <AdminSettingSwitch />
                      </Form.Item>
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="mt-5 flex items-center justify-between gap-3">
              <div className="text-xs leading-5 text-slate-400">
                调整后会影响后续问题回看与日常记录范围。
              </div>
              <Button type="primary" className="!rounded-xl !border-none !bg-indigo-600" loading={runtimeSaving} onClick={() => void handleSaveRuntime()}>
                保存运行记录
              </Button>
            </div>
          </Form>
        </AdminSurfaceCard>
      </div>
    );
  };

  const renderBasic = () => (
    <div className="space-y-6 pt-4">
      <section id="runtime-basic-business" className="scroll-mt-24">
        {renderBusiness()}
      </section>

      <section id="runtime-basic-operations" className="scroll-mt-24">
        {renderOperations()}
      </section>

      <section id="runtime-basic-degrade" className="scroll-mt-24">
        {renderDegrade()}
      </section>
    </div>
  );

  const renderAiChannels = () => {
    if (loading && !aiChannels) {
      return (
        <div className="space-y-4 pt-4">
          <Skeleton active paragraph={{ rows: 8 }} />
          <Skeleton active paragraph={{ rows: 8 }} />
        </div>
      );
    }

    if (!aiChannels) {
      return <AdminDetailPlaceholder description="暂无 AI 运行态概览" />;
    }

    return (
        <div className="space-y-6 pt-4">
          {routeDetailsError ? <Alert type="warning" showIcon message="场景接入信息暂未完整同步" description={routeDetailsError} /> : null}
          {routePoliciesError ? <Alert type="warning" showIcon message="场景策略信息暂未完整同步" description={routePoliciesError} /> : null}
          {templateDetailsError ? <Alert type="warning" showIcon message="内容方案状态暂未完整同步" description={templateDetailsError} /> : null}

        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
          <RuntimeVisualMetricCard
            icon={BrainCircuit}
            label="已纳入场景"
            value={formatCount(sceneControlSummary.totalSceneCount)}
            note={`已纳入 ${formatCount(sceneControlSummary.totalSceneCount - sceneControlSummary.unregisteredCount)} 个标准场景。`}
            tone="indigo"
          />
          <RuntimeVisualMetricCard
            icon={Route}
            label="当前提供服务"
            value={formatCount(sceneControlSummary.enabledPrimaryCount)}
            note={`当前有 ${formatCount(sceneControlSummary.disabledPrimaryCount)} 个场景处于暂停承接状态。`}
            tone="emerald"
          />
          <RuntimeVisualMetricCard
            icon={AlertTriangle}
            label="待补承接方案"
            value={formatCount(sceneControlSummary.missingPrimaryCount)}
            note="尚未完成承接设置的场景需前往 AI 网关补齐。"
            tone="amber"
          />
          <RuntimeVisualMetricCard
            icon={Sparkles}
            label="内容方案待启用"
            value={formatCount(sceneControlSummary.pendingTemplateCount)}
            note="这里只确认内容方案是否可用；业务表现统一在 AI 应用运营查看。"
            tone="fuchsia"
          />
          <RuntimeVisualMetricCard
            icon={Bot}
            label="已设思考策略"
            value={formatCount(sceneControlSummary.thinkingConfiguredSceneCount)}
            note="这里只做只读观察，正式调整统一回 AI 网关场景方案。"
            tone="indigo"
          />
        </div>

        <AdminSurfaceCard
          className="border-slate-200 bg-[linear-gradient(180deg,rgba(255,255,255,0.98),rgba(248,250,252,0.96))]"
          bodyClassName="p-5 lg:p-6"
          title="全局默认思考量"
          description="这里维护系统级默认思考基线；推荐优先配置“思考强度”，只有在需要精细控成本或贴近模型原生参数时再补预算与层级。"
          extra={(
            <SettingBadge
              label={persistedDefaultThinkingSummary === "沿用代码内置推荐" ? "当前未单独覆盖" : "当前已设置全局默认"}
              tone={persistedDefaultThinkingSummary === "沿用代码内置推荐" ? "slate" : "indigo"}
            />
          )}
        >
          {!runtimeSettings ? (
            <AdminDetailPlaceholder description="当前没有可用的思考量默认配置" />
          ) : (
            <Form layout="vertical" form={runtimeForm}>
              <div className="grid gap-4 xl:grid-cols-[minmax(0,1.05fr)_minmax(0,1.35fr)]">
                <div className="rounded-[28px] border border-indigo-100 bg-[linear-gradient(135deg,rgba(238,242,255,0.92),rgba(255,255,255,0.98))] px-5 py-5 shadow-[0_14px_30px_rgba(99,102,241,0.06)]">
                  <div className="flex items-start gap-4">
                    <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-indigo-100 text-indigo-600">
                      <BrainCircuit size={18} />
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="text-base font-semibold text-slate-900">当前全局默认基线</div>
                      <Paragraph className="!mb-0 !mt-2 !text-sm !leading-6 !text-slate-500">
                        这组值只在场景方案、provider 和 route 都没有显式配置时生效；留空就继续沿用系统代码内置推荐。
                      </Paragraph>
                    </div>
                  </div>
                  <div className="mt-4 grid gap-3 sm:grid-cols-2">
                    <SettingCompareCard label="当前生效基线" value={persistedDefaultThinkingSummary} kind="current" />
                    <SettingCompareCard label="回退策略" value="代码内置推荐" kind="default" />
                  </div>
                  <div className="mt-4 rounded-2xl border border-indigo-100 bg-white/80 px-4 py-4 text-xs leading-6 text-slate-500">
                    常见用法：全站希望更省成本时，把推荐档位压到 `LOW`；想让默认质量更稳，可以设成 `MEDIUM / HIGH`。预算用于更细的成本控制，原生层级只在特定模型需要时再配置。
                  </div>
                </div>

                <div className="rounded-[28px] border border-slate-200 bg-white px-5 py-5 shadow-[0_12px_28px_rgba(15,23,42,0.04)]">
                  <div className="grid gap-4 md:grid-cols-3">
                    <Form.Item
                      label={<span className="text-[15px] font-semibold text-slate-800">默认思考强度（推荐配置）</span>}
                      name="defaultReasoningEffort"
                      className="!mb-0"
                    >
                      <Select
                        allowClear
                        options={reasoningEffortOptions}
                        placeholder="优先配置这一项"
                        className="[&_.ant-select-selector]:!h-12 [&_.ant-select-selector]:!rounded-2xl [&_.ant-select-selector]:!px-3 [&_.ant-select-selection-item]:!text-[15px] [&_.ant-select-selection-item]:!font-medium [&_.ant-select-selection-placeholder]:!text-[15px]"
                      />
                    </Form.Item>
                    <Form.Item
                      label={<span className="text-[15px] font-semibold text-slate-800">默认思考预算（精细控成本）</span>}
                      name="defaultThinkingBudget"
                      className="!mb-0"
                    >
                      <InputNumber
                        className="!flex !h-12 !w-full !rounded-2xl [&_.ant-input-number-input-wrap]:!h-full [&_.ant-input-number-input]:!h-full [&_.ant-input-number-input]:!px-3 [&_.ant-input-number-input]:!text-[15px] [&_.ant-input-number-input]:!font-medium"
                        min={0}
                        max={1000000}
                        placeholder="需要控成本时再填，例如 1024"
                      />
                    </Form.Item>
                    <Form.Item
                      label={<span className="text-[15px] font-semibold text-slate-800">默认思考层级（模型原生）</span>}
                      name="defaultThinkingLevel"
                      className="!mb-0"
                    >
                      <Select
                        allowClear
                        options={thinkingLevelOptions}
                        placeholder="仅在模型原生需要时填写"
                        className="[&_.ant-select-selector]:!h-12 [&_.ant-select-selector]:!rounded-2xl [&_.ant-select-selector]:!px-3 [&_.ant-select-selection-item]:!text-[15px] [&_.ant-select-selection-item]:!font-medium [&_.ant-select-selection-placeholder]:!text-[15px]"
                      />
                    </Form.Item>
                  </div>
                  <div className="mt-4 rounded-2xl bg-slate-50 px-4 py-4 text-sm leading-7 text-slate-500">
                    配置优先级：代码内置推荐 → 这里的全局默认 → provider 覆写 → 场景方案 → route 高级覆写。
                    最近更新 {formatDateTime(runtimeSettings.updatedAt, "尚未单独调整")}。
                  </div>
                  <div className="mt-4 flex items-center justify-between gap-3">
                    <div className="text-sm leading-6 text-slate-400">
                      清空三项后会恢复到代码内置推荐，不会影响已配置的场景方案。
                    </div>
                    <Button
                      type="primary"
                      className="!h-11 !rounded-xl !border-none !bg-indigo-600 !px-5 !text-[15px] !font-semibold"
                      loading={runtimeSaving}
                      onClick={() => void handleSaveRuntime("默认思考量已更新")}
                    >
                      保存默认思考量
                    </Button>
                  </div>
                </div>
              </div>
            </Form>
          )}
        </AdminSurfaceCard>

          <AdminSurfaceCard
          className="border-slate-200 bg-[linear-gradient(180deg,rgba(255,255,255,0.98),rgba(248,250,252,0.96))]"
          bodyClassName="p-5 lg:p-6"
          title="场景开关与接入状态"
          description="这里主要维护场景是否启用、当前方案是否开启、返回方式以及内容方案是否可用；调用量、成功率和成本统一前往 AI 应用运营查看。"
          extra={(
            <Button className="!rounded-xl !border-slate-200" onClick={() => navigate("/admin/ai/applications")}>
              查看 AI 应用运营
            </Button>
          )}
        >
          {sceneControlRows.length === 0 ? (
            <Empty description="当前暂无可展示的场景接入信息" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <div className="grid gap-4 lg:grid-cols-2 xl:grid-cols-3">
              {sceneControlRows.map((item) => {
                const templateStateLabel = item.primaryRoute?.promptTemplateName
                  ? item.activeTemplate
                    ? `已启用 v${item.activeTemplate.versionNo}`
                    : item.templateVersionCount > 0
                      ? "待启用"
                      : "未准备内容"
                  : "未配置内容";
                const primaryStatusLabel = !item.primaryRoute
                  ? "承接方案待补充"
                  : item.primaryRoute.enabled
                    ? "当前提供服务"
                    : "当前未开启";
                const isDisabledCard = Boolean(item.primaryRoute && !item.primaryRoute.enabled);
                const cardBorderClassName = !item.primaryRoute
                  ? "border-amber-200"
                  : item.primaryRoute.enabled
                    ? "border-emerald-200"
                    : "border-rose-200";
                const sceneStatusTone = !item.primaryRoute
                  ? "amber" as const
                  : item.primaryRoute.enabled
                    ? "emerald" as const
                    : "rose" as const;
                const sceneVisual = getSceneDomainVisual(item.ownerDomain);
                const HeaderIcon = sceneVisual.icon;

                return (
                  <div
                    key={item.sceneKey}
                    className={joinAdminClassNames(
                      "relative flex h-full flex-col overflow-hidden rounded-[28px] border bg-white px-4 py-4 shadow-[0_12px_28px_rgba(15,23,42,0.04)]",
                      cardBorderClassName,
                    )}
                  >
                    <div aria-hidden className={joinAdminClassNames("pointer-events-none absolute inset-x-0 top-0 h-28 opacity-90", sceneVisual.haloClassName)} />
                    <div className="relative flex flex-1 flex-col">
                      <div className={joinAdminClassNames("rounded-[24px] border px-4 py-4 shadow-[0_10px_24px_rgba(15,23,42,0.03)]", sceneVisual.panelClassName)}>
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-3">
                              <span className={joinAdminClassNames("flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl shadow-[0_8px_20px_rgba(255,255,255,0.45)]", sceneVisual.iconWrapClassName)}>
                                <HeaderIcon size={18} />
                              </span>
                              <div className="min-w-0 flex-1">
                                <div className="flex flex-wrap items-center gap-2">
                                  <Text strong className="text-base text-slate-900">{item.displayName}</Text>
                                  <span className={joinAdminClassNames("inline-flex items-center rounded-full px-3 py-1 text-[11px] font-bold", sceneVisual.badgeClassName)}>
                                    {item.ownerDomain}
                                  </span>
                                  {!item.registered ? <SettingBadge label="待补目录" tone="amber" /> : null}
                                  <SettingBadge label={primaryStatusLabel} tone={sceneStatusTone} />
                                </div>
                                <div className="mt-2 flex flex-wrap items-center gap-2 text-[11px] leading-5 text-slate-500">
                                  <span className="inline-flex rounded-full bg-white/80 px-2.5 py-1 font-semibold text-slate-600 shadow-[inset_0_0_0_1px_rgba(148,163,184,0.12)]">
                                    {item.frontEntry}
                                  </span>
                                  <span>{getLabel(item.taskType, gatewayTaskTypeLabelMap, item.taskType)}</span>
                                  <span className="font-mono text-slate-400">{item.channelCode}</span>
                                </div>
                              </div>
                            </div>
                          </div>

                          {item.primaryRoute ? (
                            <Form.Item className="!mb-0">
                              <AdminSettingSwitch
                                checked={item.primaryRoute.enabled}
                                loading={routeActionLoadingId === item.primaryRoute.id}
                                onChange={(checked) => void handleToggleRouteEnabled(item.primaryRoute!.id, checked)}
                              />
                            </Form.Item>
                          ) : null}
                        </div>
                      </div>

                      <div className="mt-4 grid gap-3 sm:grid-cols-[minmax(0,0.82fr)_minmax(0,1.18fr)]">
                        <RuntimeCompactInfoCard
                          icon={ArrowRight}
                          label="前台入口"
                          value={item.frontEntry}
                          note={item.channelCode}
                          tone={sceneVisual.compactTone}
                          valueClassName="font-['Manrope'] text-[1.05rem] font-extrabold tracking-tight"
                          noteClassName="font-mono"
                        />
                        <RuntimeCompactInfoCard
                          icon={Route}
                          label="当前承接方案"
                          value={item.primaryRoute ? item.primaryRoute.routeCode : "待补充"}
                          note={item.primaryRoute ? `${item.primaryRoute.providerDisplayName} / ${item.primaryRoute.modelName}` : "前往 AI 网关补齐承接设置"}
                          tone={item.primaryRoute ? sceneVisual.compactTone : "amber"}
                          valueClassName="text-[15px] font-extrabold leading-6"
                          noteClassName="text-[11px]"
                        />
                        <RuntimeCompactInfoCard
                          icon={Workflow}
                          label="返回方式"
                          value={item.primaryRoute ? getRuntimeReturnModeLabel(item.primaryRoute.executionMode) : "待配置"}
                          note={item.primaryRoute ? "按当前方案返回" : "完成接入后可自动显示"}
                          tone={item.primaryRoute ? "slate" : "amber"}
                        />
                        <RuntimeCompactInfoCard
                          icon={Sparkles}
                          label="内容方案"
                          value={templateStateLabel}
                          note={item.routeCount > 0 ? `备用方案 ${formatCount(item.routeCount)} 个` : "当前暂无备用方案"}
                          tone={item.activeTemplate ? "emerald" : "amber"}
                        />
                        <RuntimeCompactInfoCard
                          icon={Bot}
                          label="思考策略"
                          value={item.thinkingSummary}
                          note={item.primaryPolicy
                            ? `${item.primaryPolicy.userTier === "ALL" ? "默认策略" : item.primaryPolicy.userTier === "FREE" ? "普通策略" : "VIP 策略"} · ${item.differentiated ? "支持层级拆分" : "统一配置"}`
                            : "前往 AI 网关补齐场景策略"}
                          tone={item.thinkingConfiguredCount > 0 ? sceneVisual.compactTone : "slate"}
                          valueClassName="text-[15px] font-extrabold leading-6"
                          noteClassName="text-[11px]"
                        />
                      </div>

                      {isDisabledCard ? <DisabledStripeMask /> : null}
                    </div>

                    <div className="mt-auto flex flex-col gap-3 pt-4">
                      <div className="text-xs leading-6 text-slate-500 break-words [overflow-wrap:anywhere]">
                        {item.primaryRoute
                          ? `${primaryStatusLabel}，当前承接方案为 ${item.primaryRoute.routeCode}，${item.thinkingSummary === "未设置策略" ? "尚未补齐场景策略。" : `思考策略为 ${item.thinkingSummary}。`}`
                          : "当前还未完成承接设置，可前往 AI 网关补齐。"}
                      </div>
                      <Button className="!h-10 self-end !rounded-2xl !border-none !bg-indigo-600 !px-4 !text-white !shadow-[0_10px_24px_rgba(99,102,241,0.16)] hover:!bg-indigo-700" onClick={() => navigate("/admin/ai/gateway?tab=scenes")}>
                        去 AI 网关设置
                      </Button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </AdminSurfaceCard>
      </div>
    );
  };

  const renderDegrade = () => {
    if (loading && !policies) {
      return (
        <div className="space-y-4">
          <Skeleton active paragraph={{ rows: 6 }} />
          <Skeleton active paragraph={{ rows: 5 }} />
        </div>
      );
    }

    if (!policies) {
      return <AdminDetailPlaceholder description="暂无风险保护设置" />;
    }

    const guardrailCards = [
      {
        key: "aiInputEnabled" as const,
        title: "AI 输入审查",
        description: "拦截提示词注入和恶意指令，在模型执行前阻断高风险输入。",
        sceneLabel: "AI 入口",
        followLabel: "提交前先拦截",
        borderClassName: "border-cyan-200",
        iconWrap: "bg-cyan-100 text-cyan-600",
        icon: Bot,
        enabled: currentAiInputEnabled,
      },
      {
        key: "aiOutputEnabled" as const,
        title: "AI 输出审查",
        description: "对生成结果做实时内容治理，防止有害或违规内容直接回流前台。",
        sceneLabel: "AI 回流",
        followLabel: "结果回前台前校验",
        borderClassName: "border-rose-200",
        iconWrap: "bg-rose-100 text-rose-600",
        icon: Sparkles,
        enabled: currentAiOutputEnabled,
      },
      {
        key: "communityStrictReviewEnabled" as const,
        title: "社区严格审查",
        description: "提高公共频道治理强度，适合敏感期或内容波动期使用。",
        sceneLabel: "社区内容",
        followLabel: "高流量频道优先复核",
        borderClassName: "border-amber-200",
        iconWrap: "bg-amber-100 text-amber-600",
        icon: ShieldAlert,
        enabled: currentCommunityStrictReviewEnabled,
      },
    ];

    return (
      <div className="space-y-6">
        <AdminSurfaceCard>
          <div className="mb-5 flex flex-col gap-3 border-b border-slate-100 pb-5 sm:flex-row sm:items-start sm:justify-between">
            <div className="flex items-start gap-3">
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-rose-100 text-rose-600">
                <ShieldAlert size={20} />
              </span>
              <div>
                <Text strong className="text-base text-slate-900">风险保护</Text>
                <Paragraph className="!mb-0 !mt-1 !text-sm !leading-6 !text-slate-500">
                  统一维护 AI 输入、AI 输出、社区严格审查与举报处置节奏，避免关键风险设置分散在多个页面重复调整。
                </Paragraph>
              </div>
            </div>
            <SettingBadge label={`${enabledGuardrailCount}/3 已开启`} tone="rose" />
          </div>

          <Form layout="vertical" form={moderationForm}>
            <div className="grid gap-4 xl:grid-cols-2">
              {guardrailCards.map((item) => {
                const Icon = item.icon;
                const isDisabledCard = !item.enabled;
                return (
                  <div
                    key={item.key}
                    className={joinAdminClassNames(
                      "flex h-full flex-col overflow-hidden rounded-[28px] border bg-white px-5 py-5 shadow-[0_16px_32px_rgba(15,23,42,0.05)]",
                      item.borderClassName,
                    )}
                  >
                    <div className="relative flex flex-1 flex-col">
                      <div className="flex items-start gap-4">
                        <span className={joinAdminClassNames("flex h-11 w-11 items-center justify-center rounded-2xl", item.iconWrap)}>
                          <Icon size={18} />
                        </span>
                        <div className="min-w-0 flex-1">
                          <div className="text-base font-semibold text-slate-900">{item.title}</div>
                          <Paragraph className="!mb-0 !mt-2 !text-sm !leading-6 !text-slate-500">{item.description}</Paragraph>
                        </div>
                      </div>

                      <div className="mt-4 flex flex-wrap gap-2">
                        <SettingBadge label={item.enabled ? "保持开启" : "已放宽"} tone={item.enabled ? "emerald" : "rose"} />
                        <SettingBadge label={item.sceneLabel} tone="rose" />
                      </div>

                      <div className="mt-4 grid gap-3 sm:grid-cols-2">
                        <SettingCompareCard label="当前策略" value={item.enabled ? "保持开启" : "放宽"} kind="current" />
                        <SettingCompareCard label="重点关注" value={item.followLabel} kind="default" />
                      </div>

                      {isDisabledCard ? <DisabledStripeMask /> : null}
                    </div>

                    <div className="mt-auto flex items-center justify-between gap-3 pt-4">
                      <div className="text-xs leading-5 text-slate-400">
                        保存后立即生效，建议同步回看内容治理与 AI 运行态表现。
                      </div>
                      <Form.Item name={item.key} valuePropName="checked" className="!mb-0">
                        <AdminSettingSwitch />
                      </Form.Item>
                    </div>
                  </div>
                );
              })}
              <div className="flex h-full flex-col rounded-[28px] border border-emerald-200 bg-[linear-gradient(180deg,rgba(16,185,129,0.08),rgba(255,255,255,0.98))] px-5 py-5 shadow-[0_16px_32px_rgba(15,23,42,0.05)]">
                <div className="flex items-start gap-4">
                  <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-emerald-100 text-emerald-600">
                    <ShieldCheck size={18} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="text-base font-semibold text-slate-900">举报自动隐藏节奏</div>
                    <Paragraph className="!mb-0 !mt-2 !text-sm !leading-6 !text-slate-500">
                      当举报累计达到设定次数后，内容会先进入隐藏状态，平台可更快收口扩散风险。
                    </Paragraph>
                  </div>
                </div>

                <div className="mt-4 flex flex-wrap gap-2">
                  <SettingBadge label="内容治理" tone="emerald" />
                  <SettingBadge label={`${watchedThreshold} 次触发`} tone="amber" />
                </div>

                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                  <SettingCompareCard label="当前设置" value={`${watchedThreshold} 次`} kind="current" />
                  <SettingCompareCard label="联动状态" value={`${enabledGuardrailCount}/3 保护项已开启`} kind="default" />
                </div>

                <div className="mt-auto flex items-end justify-between gap-4 pt-4">
                  <div className="text-xs leading-5 text-slate-400">
                    达到阈值后，内容会先隐藏并进入优先处理队列。
                  </div>

                  <div className="flex w-full max-w-[148px] items-center justify-between gap-3">
                      <div className="shrink-0 whitespace-nowrap text-[11px] font-bold  leading-none tracking-[0.12em] text-slate-400">
                      自动隐藏条件
                      </div>
                    <Form.Item<ModerationFormValues>
                      name="autoHideReportThreshold"
                      className="!mb-0"
                      rules={[{ required: true, message: "请输入自动隐藏阈值" }]}
                    >
                      <InputNumber min={1} max={99} className="!w-[92px]" />
                    </Form.Item>
                  </div>
                </div>
              </div>
            </div>

            <div className="mt-5 flex items-center justify-between gap-3">
              <div className="text-xs leading-5 text-slate-400">
                已开启 {enabledGuardrailCount}/3 项保护策略；当前举报达到 {watchedThreshold} 次会自动隐藏{relaxedGuardrails.length > 0 ? `，已放宽：${relaxedGuardrails.join("、")}` : "。"}
              </div>
              <Button type="primary" className="!rounded-xl !border-none !bg-indigo-600" loading={moderationSaving} onClick={() => void handleSaveModeration("风险设置已保存")}>
                保存风险设置
              </Button>
            </div>
          </Form>
        </AdminSurfaceCard>
      </div>
    );
  };

  return (
    <AdminPageFrame>
      <AdminPageHeader
        sectionLabel={workspaceMeta.sectionLabel}
        title={workspaceMeta.title}
        tone={workspaceMeta.tone}
        description={workspaceMeta.description}
        actions={(
          <>
            {activeTab === "home" ? (
              <Button className="!h-11 !rounded-2xl !border-slate-200" onClick={() => navigate("/admin/ai/gateway")}>
                进入 AI 网关
              </Button>
            ) : null}
            <Button className="!h-11 !rounded-2xl !border-slate-200 !px-5 !shadow-none" onClick={() => void loadConsole()} disabled={loading}>
              <RefreshCcw size={16} className={loading ? "mr-2 animate-spin" : "mr-2"} />
              刷新数据
            </Button>
          </>
        )}
      />

      {error ? (
        <Alert type="error" showIcon className="rounded-[28px]" message="运行配置加载失败" description={error} />
      ) : null}

      {activeTab === "home"
        ? renderHome()
        : activeTab === "basic"
          ? renderBasic()
          : renderAiChannels()}
    </AdminPageFrame>
  );
}
