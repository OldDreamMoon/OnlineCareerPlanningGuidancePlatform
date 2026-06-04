import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Button,
  Empty,
  Form,
  Input,
  InputNumber,
  Segmented,
  Select,
  Table,
  Typography,
  message,
  type TableColumnsType,
} from "antd";
import {
  Activity,
  AlertTriangle,
  ArrowRight,
  Bot,
  Clock3,
  Coins,
  Gauge,
  RefreshCcw,
  Sparkles,
  TrendingUp,
  Workflow,
  type LucideIcon,
} from "lucide-react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { AdminAnimatedNumber, canAnimateAdminValue } from "../components/admin/AdminAnimatedNumber";
import { AdminFilterBar, AdminPageFrame, AdminPageHeader, AdminSurfaceCard } from "../components/admin/AdminOpsPrimitives";
import { useLatestRequest } from "../hooks/useLatestRequest";
import { buildAdminStaleCacheKey, useAdminStaleCache } from "../hooks/useAdminStaleCache";
import { ApiClientError, apiRequest, buildQuery, isAbortError } from "../lib/apiClient";
import { gatewayTaskTypeLabelMap, getLabel } from "../lib/adminLabels";
import { formatCount, formatDateTime } from "../lib/formatters";

const { Paragraph, Text } = Typography;
const AdminAiApplicationsPolicyModal = lazy(() => import("../components/admin/overlays/AdminAiApplicationsPolicyModal"));

type TimeValue = number | string | null;
type ScopeFilterValue = "ALL" | "FOLLOW_UP" | "ACTIVE" | "DORMANT" | "PLANNED";
type WindowValue = 7 | 15 | 30;
type CardTone = "mint" | "teal" | "amber" | "rose";

type ApplicationOpsSummary = {
  totalChannels: number;
  readyChannels: number;
  activeChannels: number;
  followUpChannels: number;
  dormantChannels: number;
  plannedChannels: number;
  totalCalls: number;
  totalCost: string;
  overallSuccessRate: string;
  avgLatencyMs: number;
};

type ApplicationOpsSceneItem = {
  channelCode: string;
  displayName: string;
  ownerDomain: string;
  frontEntry: string;
  summary: string;
  taskType: string;
  sceneCode: string;
  calls: number;
  successCalls: number;
  successRate: string;
  avgLatencyMs: number;
  totalCost: string;
  lastCallAt: TimeValue;
  routeCount: number;
  primaryRouteCode: string;
  primaryProviderDisplayName: string;
  primaryModelName: string;
  usesBuiltinPrompt: boolean;
  hasActiveTemplate: boolean;
  activeTemplateName: string;
  activeTemplateVersionNo: number | null;
  status: string;
  statusLabel: string;
  riskType: string;
  riskLabel: string;
  followUp: boolean;
  active: boolean;
  ready: boolean;
  dormant: boolean;
  planned: boolean;
  chainSummary: string;
  nextActionLabel: string;
  nextActionTo: string;
};

type ApplicationOpsPayload = {
  days: number;
  summary: ApplicationOpsSummary;
  hotScenes: ApplicationOpsSceneItem[];
  costScenes: ApplicationOpsSceneItem[];
  riskScenes: ApplicationOpsSceneItem[];
  dormantScenes: ApplicationOpsSceneItem[];
  records: ApplicationOpsSceneItem[];
};

type ApplicationOpsCachePayload = {
  payload: ApplicationOpsPayload | null;
  quotaPolicies: QuotaPolicyItem[];
};

type QuotaPolicyItem = {
  id: number;
  tier: string;
  taskType: string;
  sceneCode: string | null;
  dailyFreeLimit: number;
  pointsPerCall: number;
  dailyMaxLimit: number;
  modelPreference: string;
  maxInputTokens: number;
  createdAt: TimeValue;
  updatedAt: TimeValue;
};

type QuotaPolicyEditorValues = {
  dailyFreeLimit: number;
  pointsPerCall: number;
  dailyMaxLimit: number;
  modelPreference?: string;
  maxInputTokens: number;
};

type QuotaStrategyScope = "SCENE_OVERRIDE" | "TASK_DIRECT" | "TASK_SHARED" | "INCLUDED_WITH_SESSION";

type SceneQuotaStrategy = {
  key: string;
  title: string;
  primaryScene: ApplicationOpsSceneItem;
  scenes: ApplicationOpsSceneItem[];
  sharedScenes: ApplicationOpsSceneItem[];
  freePolicy: QuotaPolicyItem | null;
  premiumPolicy: QuotaPolicyItem | null;
  scope: QuotaStrategyScope;
  scopeLabel: string;
  description: string;
  coverageNote: string;
  editable: boolean;
  referenceLabel?: string;
};

type StatusTone = "emerald" | "amber" | "rose" | "sky" | "slate" | "teal";
type SignalTone = "emerald" | "amber" | "rose" | "sky" | "violet" | "slate";
type AiApplicationsTab = "home" | "scenes" | "policies";
type PolicyFilterValue = "ALL" | "REVIEW" | "INDEPENDENT" | "SHARED" | "INCLUDED" | "MODEL";

function joinClassNames(...values: Array<string | false | null | undefined>) {
  return values.filter(Boolean).join(" ");
}

const cardToneMap: Record<CardTone, {
  iconWrap: string;
  accent: string;
}> = {
  mint: {
    iconWrap: "bg-emerald-100 text-emerald-700",
    accent: "bg-emerald-500/12",
  },
  teal: {
    iconWrap: "bg-cyan-100 text-cyan-700",
    accent: "bg-cyan-500/12",
  },
  amber: {
    iconWrap: "bg-amber-100 text-amber-700",
    accent: "bg-amber-500/12",
  },
  rose: {
    iconWrap: "bg-rose-100 text-rose-700",
    accent: "bg-rose-500/12",
  },
};

function formatCny(value?: string | number | null, digits = 4) {
  if (value === null || value === undefined || value === "") {
    return "¥0.0000";
  }
  const parsed = Number(value);
  if (Number.isNaN(parsed)) {
    return `¥${value}`;
  }
  return `¥${parsed.toFixed(digits)}`;
}

function formatQuotaLimit(limit: number, unit: string) {
  if (limit < 0) {
    return "不限";
  }
  return `${formatCount(limit)} ${unit}`;
}

function parseDecimal(value?: string | number | null) {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

function normalizeSceneCode(value?: string | null) {
  const normalized = value?.trim();
  return normalized ? normalized.toUpperCase() : "";
}

function normalizeAiApplicationsTab(value: string | null): AiApplicationsTab {
  if (value === "scenes" || value === "policies") {
    return value;
  }
  return "home";
}

function getStatusTone(status: string): StatusTone {
  switch (status) {
    case "ACTIVE":
      return "emerald";
    case "WATCH":
      return "amber";
    case "BLOCKED":
      return "rose";
    case "DORMANT":
      return "sky";
    case "GOVERN":
      return "teal";
    default:
      return "slate";
  }
}

function getStatusBadgeClass(status: string) {
  const tone = getStatusTone(status);
  if (tone === "emerald") {
    return "bg-emerald-50 text-emerald-700 ring-1 ring-emerald-100";
  }
  if (tone === "amber") {
    return "bg-amber-50 text-amber-700 ring-1 ring-amber-100";
  }
  if (tone === "rose") {
    return "bg-rose-50 text-rose-700 ring-1 ring-rose-100";
  }
  if (tone === "sky") {
    return "bg-sky-50 text-sky-700 ring-1 ring-sky-100";
  }
  if (tone === "teal") {
    return "bg-cyan-50 text-cyan-700 ring-1 ring-cyan-100";
  }
  return "bg-slate-50 text-slate-600 ring-1 ring-slate-200";
}

function getStatusDotClass(status: string) {
  const tone = getStatusTone(status);
  if (tone === "emerald") {
    return "bg-emerald-500";
  }
  if (tone === "amber") {
    return "bg-amber-500";
  }
  if (tone === "rose") {
    return "bg-rose-500";
  }
  if (tone === "sky") {
    return "bg-sky-500";
  }
  if (tone === "teal") {
    return "bg-cyan-500";
  }
  return "bg-slate-400";
}

function getSignalChipClass(tone: SignalTone) {
  if (tone === "emerald") {
    return "bg-emerald-50 text-emerald-700 ring-1 ring-emerald-100";
  }
  if (tone === "amber") {
    return "bg-amber-50 text-amber-700 ring-1 ring-amber-100";
  }
  if (tone === "rose") {
    return "bg-rose-50 text-rose-700 ring-1 ring-rose-100";
  }
  if (tone === "sky") {
    return "bg-sky-50 text-sky-700 ring-1 ring-sky-100";
  }
  if (tone === "violet") {
    return "bg-violet-50 text-violet-700 ring-1 ring-violet-100";
  }
  return "bg-slate-100 text-slate-600 ring-1 ring-slate-200";
}

function getRiskHintTone(record: ApplicationOpsSceneItem): SignalTone {
  if (record.status === "BLOCKED") {
    return "rose";
  }
  if (record.followUp) {
    return "amber";
  }
  if (record.dormant) {
    return "sky";
  }
  if (record.planned) {
    return "slate";
  }
  if (record.usesBuiltinPrompt) {
    return "violet";
  }
  return "slate";
}

function MetricCard({
  icon: Icon,
  label,
  value,
  note,
  tone,
}: {
  icon: LucideIcon;
  label: string;
  value: string;
  note: string;
  tone: CardTone;
}) {
  const palette = cardToneMap[tone];
  const renderedValue = canAnimateAdminValue(value) ? <AdminAnimatedNumber value={value} /> : value;

  return (
    <section className="relative overflow-hidden rounded-[26px] border border-white bg-white p-6 shadow-[0px_10px_40px_rgba(44,47,49,0.06)]">
      <div className={joinClassNames("absolute right-0 top-0 h-24 w-24 -translate-y-8 translate-x-8 rounded-full", palette.accent)} />
      <div className={joinClassNames("relative flex h-11 w-11 items-center justify-center rounded-2xl", palette.iconWrap)}>
        <Icon size={20} />
      </div>
      <div className="relative mt-5">
        <div className="admin-typography-card-title text-slate-700">{label}</div>
        <div className="admin-typography-card-value mt-2 font-['Manrope'] text-slate-950">{renderedValue}</div>
        <div className="admin-typography-card-note mt-2 text-slate-500">{note}</div>
      </div>
    </section>
  );
}

function FocusList({
  icon: Icon,
  title,
  description,
  items,
  emptyText,
  navigate,
  renderMeta,
}: {
  icon: LucideIcon;
  title: string;
  description: string;
  items: ApplicationOpsSceneItem[];
  emptyText: string;
  navigate: (to: string) => void;
  renderMeta: (item: ApplicationOpsSceneItem) => string;
}) {
  return (
    <section className="rounded-[24px] bg-slate-50 px-5 py-5">
      <div className="flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-white text-slate-700 shadow-[0_6px_18px_rgba(15,23,42,0.06)]">
          <Icon size={16} />
        </div>
        <div>
          <div className="admin-typography-subsection-title text-slate-900">{title}</div>
          <div className="admin-typography-caption text-slate-500">{description}</div>
        </div>
      </div>

      <div className="mt-4 space-y-3">
        {items.length > 0 ? items.map((item) => (
          <button
            key={`${title}-${item.channelCode}`}
            type="button"
            className="block w-full rounded-[18px] bg-white px-4 py-3 text-left shadow-[0_6px_18px_rgba(15,23,42,0.04)] transition-all hover:-translate-y-0.5 hover:bg-slate-50 hover:shadow-[0_10px_24px_rgba(15,23,42,0.08)]"
            onClick={() => navigate(item.nextActionTo)}
          >
            <div className="flex items-start gap-3">
              <div className={joinClassNames("mt-1 h-2.5 w-2.5 rounded-full", getStatusDotClass(item.status))} />
              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between gap-3">
                  <div className="truncate text-sm font-semibold text-slate-900">{item.displayName}</div>
                  <span className="admin-typography-chip shrink-0 text-cyan-700">{item.nextActionLabel}</span>
                </div>
                <div className="admin-typography-caption mt-1 truncate text-slate-500">
                  {renderMeta(item)}
                </div>
              </div>
            </div>
          </button>
        )) : (
          <div className="admin-typography-card-note rounded-[18px] bg-white px-4 py-5 text-slate-500">
            {emptyText}
          </div>
        )}
      </div>
    </section>
  );
}

function getPolicyScopeBadgeClass(scope: QuotaStrategyScope) {
  if (scope === "SCENE_OVERRIDE") {
    return "bg-violet-100 text-violet-700 ring-1 ring-violet-200";
  }
  if (scope === "TASK_DIRECT") {
    return "bg-indigo-100 text-indigo-700 ring-1 ring-indigo-200";
  }
  if (scope === "TASK_SHARED") {
    return "bg-amber-100 text-amber-700 ring-1 ring-amber-200";
  }
  return "bg-cyan-100 text-cyan-700 ring-1 ring-cyan-200";
}

function getPolicyCardPalette(scope: QuotaStrategyScope) {
  if (scope === "SCENE_OVERRIDE") {
    return {
      panel: "border-violet-100 bg-[linear-gradient(180deg,rgba(139,92,246,0.08),rgba(255,255,255,0.98))]",
      iconWrap: "bg-violet-100 text-violet-700",
      line: "from-violet-400 to-fuchsia-400",
      icon: Sparkles,
    };
  }
  if (scope === "TASK_DIRECT") {
    return {
      panel: "border-indigo-100 bg-[linear-gradient(180deg,rgba(99,102,241,0.08),rgba(255,255,255,0.98))]",
      iconWrap: "bg-indigo-100 text-indigo-700",
      line: "from-indigo-400 to-blue-400",
      icon: Coins,
    };
  }
  if (scope === "TASK_SHARED") {
    return {
      panel: "border-amber-100 bg-[linear-gradient(180deg,rgba(245,158,11,0.09),rgba(255,255,255,0.98))]",
      iconWrap: "bg-amber-100 text-amber-700",
      line: "from-amber-400 to-orange-400",
      icon: Workflow,
    };
  }
  return {
    panel: "border-cyan-100 bg-[linear-gradient(180deg,rgba(34,211,238,0.08),rgba(255,255,255,0.98))]",
    iconWrap: "bg-cyan-100 text-cyan-700",
    line: "from-cyan-400 to-sky-400",
    icon: Bot,
  };
}

function PolicyTierPanel({
  title,
  badge,
  badgeClassName,
  policy,
  editable,
  emptyText,
  onEdit,
}: {
  title: string;
  badge: string;
  badgeClassName: string;
  policy: QuotaPolicyItem | null;
  editable: boolean;
  emptyText: string;
  onEdit: () => void;
}) {
  return (
    <div className="rounded-[22px] border border-slate-200/70 bg-white p-4 shadow-[0_14px_32px_rgba(15,23,42,0.04)]">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <span className={joinClassNames("admin-typography-chip inline-flex rounded-full px-2.5 py-1", badgeClassName)}>
            {badge}
          </span>
          <span className="text-sm font-semibold text-slate-900">{title}</span>
        </div>
        {editable ? (
          <Button
            type="link"
            className="admin-typography-chip !h-auto !p-0 !font-bold"
            onClick={onEdit}
          >
            编辑
          </Button>
        ) : null}
      </div>

      {policy ? (
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <div className="rounded-2xl bg-slate-50 px-3 py-3">
            <div className="admin-typography-data-label text-slate-400">每日免费</div>
            <div className="admin-typography-mini-value mt-2 text-slate-900">{formatQuotaLimit(policy.dailyFreeLimit, "次")}</div>
          </div>
          <div className="rounded-2xl bg-slate-50 px-3 py-3">
            <div className="admin-typography-data-label text-slate-400">单次积分</div>
            <div className="admin-typography-mini-value mt-2 text-slate-900">{formatCount(policy.pointsPerCall)} 分</div>
          </div>
          <div className="rounded-2xl bg-slate-50 px-3 py-3">
            <div className="admin-typography-data-label text-slate-400">每日上限</div>
            <div className="admin-typography-mini-value mt-2 text-slate-900">{formatQuotaLimit(policy.dailyMaxLimit, "次")}</div>
          </div>
          <div className="rounded-2xl bg-slate-50 px-3 py-3">
            <div className="admin-typography-data-label text-slate-400">优先模型</div>
            <div className="admin-typography-mini-value mt-2 break-all text-slate-900">{policy.modelPreference?.trim() || "未指定"}</div>
          </div>
        </div>
      ) : (
        <div className="admin-typography-caption mt-4 rounded-2xl bg-slate-50 px-4 py-4 text-slate-400">
          {emptyText}
        </div>
      )}
    </div>
  );
}

function isIndependentPolicyScope(scope: QuotaStrategyScope) {
  return scope === "SCENE_OVERRIDE" || scope === "TASK_DIRECT";
}

function hasPreferredModel(strategy: SceneQuotaStrategy) {
  return Boolean(strategy.freePolicy?.modelPreference?.trim() || strategy.premiumPolicy?.modelPreference?.trim());
}

function shouldReviewPolicyFirst(strategy: SceneQuotaStrategy) {
  if (!strategy.editable) {
    return false;
  }
  if (!strategy.freePolicy || !strategy.premiumPolicy) {
    return true;
  }
  if (strategy.primaryScene.followUp || strategy.primaryScene.status !== "ACTIVE") {
    return true;
  }
  return strategy.scope === "TASK_SHARED";
}

function getPolicyScopeSortWeight(scope: QuotaStrategyScope) {
  if (scope === "TASK_SHARED") {
    return 0;
  }
  if (scope === "SCENE_OVERRIDE") {
    return 1;
  }
  if (scope === "TASK_DIRECT") {
    return 2;
  }
  return 3;
}

function getPolicyFilterButtonClass(active: boolean) {
  return active
    ? "bg-slate-900 text-white shadow-none"
    : "bg-white text-slate-600 shadow-[inset_0_0_0_1px_rgba(148,163,184,0.18)] hover:bg-slate-50";
}

export default function AdminAiApplicationsPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const createRequest = useLatestRequest();
  const [policyForm] = Form.useForm<QuotaPolicyEditorValues>();
  const activeTab = normalizeAiApplicationsTab(searchParams.get("tab"));

  const [windowDays, setWindowDays] = useState<WindowValue>(7);
  const [scopeFilter, setScopeFilter] = useState<ScopeFilterValue>("ALL");
  const [domainFilter, setDomainFilter] = useState<string>("ALL");
  const cache = useAdminStaleCache<ApplicationOpsCachePayload>(
    buildAdminStaleCacheKey("admin-ai-applications", {
      windowDays,
    }),
  );
  const [payload, setPayload] = useState<ApplicationOpsPayload | null>(() => cache.cached?.payload ?? null);
  const [quotaPolicies, setQuotaPolicies] = useState<QuotaPolicyItem[]>(() => cache.cached?.quotaPolicies ?? []);
  const [loading, setLoading] = useState(() => !cache.hasCache);
  const [error, setError] = useState<string | null>(null);
  const [policyFilter, setPolicyFilter] = useState<PolicyFilterValue>("ALL");
  const [policyEditorOpen, setPolicyEditorOpen] = useState(false);
  const [editingPolicy, setEditingPolicy] = useState<QuotaPolicyItem | null>(null);
  const [editingPolicyTargetLabel, setEditingPolicyTargetLabel] = useState<string>("");
  const [editingPolicySharedScenes, setEditingPolicySharedScenes] = useState<ApplicationOpsSceneItem[]>([]);
  const [policySaving, setPolicySaving] = useState(false);

  const loadData = useCallback(async (showLoading = true) => {
    const request = createRequest();
    if (showLoading) {
      setLoading(true);
    }
    setError(null);

    try {
      // AI 应用运营同时读取调用指标和权益策略，成本视角和额度视角需要一起看。
      const [response, quotaResponse] = await Promise.all([
        apiRequest<ApplicationOpsPayload>(
          `/admin/ai/application-ops${buildQuery({ days: windowDays })}`,
          { signal: request.signal },
        ),
        apiRequest<{ records: QuotaPolicyItem[] }>("/admin/ai/quota-policies", { signal: request.signal }),
      ]);

      if (!request.isCurrent()) {
        return;
      }

      setPayload(response);
      setQuotaPolicies(quotaResponse.records ?? []);
      cache.write({
        payload: response,
        quotaPolicies: quotaResponse.records ?? [],
      });
    } catch (fetchError) {
      if (isAbortError(fetchError) || !request.isCurrent()) {
        return;
      }
      const apiError = fetchError as ApiClientError;
      const nextMessage = apiError.message || "加载 AI 应用运营失败";
      setError(nextMessage);
      message.error(nextMessage);
    } finally {
      if (request.isCurrent()) {
        setLoading(false);
      }
    }
  }, [cache, createRequest, windowDays]);

  useEffect(() => {
    if (!cache.cached) {
      return;
    }
    setPayload(cache.cached.payload);
    setQuotaPolicies(cache.cached.quotaPolicies);
  }, [cache.cached]);

  useEffect(() => {
    void loadData(!cache.hasCache);
  }, [cache.hasCache, loadData]);

  const summary = payload?.summary ?? {
    totalChannels: 0,
    readyChannels: 0,
    activeChannels: 0,
    followUpChannels: 0,
    dormantChannels: 0,
    plannedChannels: 0,
    totalCalls: 0,
    totalCost: "0",
    overallSuccessRate: "0.0%",
    avgLatencyMs: 0,
  };

  const records = payload?.records ?? [];
  const hotSceneCodes = useMemo(() => new Set((payload?.hotScenes ?? []).map((item) => item.channelCode)), [payload?.hotScenes]);
  const costSceneCodes = useMemo(() => new Set((payload?.costScenes ?? []).map((item) => item.channelCode)), [payload?.costScenes]);
  const riskSceneCodes = useMemo(() => new Set((payload?.riskScenes ?? []).map((item) => item.channelCode)), [payload?.riskScenes]);
  const dormantSceneCodes = useMemo(() => new Set((payload?.dormantScenes ?? []).map((item) => item.channelCode)), [payload?.dormantScenes]);

  const domainOptions = useMemo(
    () => [
      { label: "全部业务域", value: "ALL" },
      ...Array.from(new Set(records.map((item) => item.ownerDomain)))
        .sort((left, right) => left.localeCompare(right, "zh-CN"))
        .map((domain) => ({
          label: `${domain} (${records.filter((item) => item.ownerDomain === domain).length})`,
          value: domain,
        })),
    ],
    [records],
  );

  const filteredRecords = useMemo(() => records.filter((record) => {
    // 顶部筛选只改变前端视图，不重新拉取指标窗口内的服务端数据。
    if (domainFilter !== "ALL" && record.ownerDomain !== domainFilter) {
      return false;
    }
    if (scopeFilter === "FOLLOW_UP") {
      return record.followUp;
    }
    if (scopeFilter === "ACTIVE") {
      return record.active;
    }
    if (scopeFilter === "DORMANT") {
      return record.dormant;
    }
    if (scopeFilter === "PLANNED") {
      return record.planned;
    }
    return true;
  }), [domainFilter, records, scopeFilter]);

  const policiesByTierAndTaskFallback = useMemo(() => new Map(
    quotaPolicies
      .filter((policy) => !normalizeSceneCode(policy.sceneCode))
      .map((policy) => [`${policy.tier}::${policy.taskType}`, policy] as const),
  ), [quotaPolicies]);

  const policiesByTierAndScene = useMemo(() => new Map(
    quotaPolicies
      .filter((policy) => normalizeSceneCode(policy.sceneCode))
      .map((policy) => [`${policy.tier}::${policy.taskType}::${normalizeSceneCode(policy.sceneCode)}`, policy] as const),
  ), [quotaPolicies]);

  const sceneQuotaStrategies = useMemo<SceneQuotaStrategy[]>(() => {
    // 权益策略按“场景独立、任务共享、随主功能”三类归组，降低后台维护成本。
    const strategies: SceneQuotaStrategy[] = [];
    const recordsBySceneCode = new Map(records.map((record) => [normalizeSceneCode(record.sceneCode), record] as const));
    const sceneOrder = new Map(records.map((record, index) => [normalizeSceneCode(record.sceneCode), index] as const));
    const includedConfigs = new Map([
      ["INTERVIEW_ANSWER_HELPER", {
        description: "作为面试过程中的辅助能力提供，不单独计费；权益统一跟随主功能结算。",
        referenceLabel: "跟随主功能规则",
      }],
      ["INTERVIEW_VOICE_TRANSCRIBE", {
        description: "作为语音面试的基础能力提供，不单独计费；相关稳定性请到网关和运行配置查看。",
        referenceLabel: "跟随主功能规则",
      }],
      ["INTERVIEW_SUMMARY", {
        description: "作为面试结束后的复盘能力提供，不单独计费；权益统一跟随主功能结算。",
        referenceLabel: "跟随主功能规则",
      }],
      ["STUDENT_PORTRAIT_SUMMARY", {
        description: "平台内部低频调用的画像表达层能力，不参与学生积分扣费；相关路由和模式请到 AI 网关与运行配置维护。",
        referenceLabel: "平台内低频调用",
      }],
    ]);
    const includedSceneCodes = new Set(Array.from(includedConfigs.keys()));
    const sceneOverrideSceneCodes = new Set(
      records
        .filter((scene) => (
          policiesByTierAndScene.has(`FREE::${scene.taskType}::${normalizeSceneCode(scene.sceneCode)}`)
          || policiesByTierAndScene.has(`PREMIUM::${scene.taskType}::${normalizeSceneCode(scene.sceneCode)}`)
        ))
        .map((scene) => normalizeSceneCode(scene.sceneCode)),
    );

    records
      .filter((scene) => sceneOverrideSceneCodes.has(normalizeSceneCode(scene.sceneCode)))
      .forEach((scene) => {
        const normalizedSceneCode = normalizeSceneCode(scene.sceneCode);
        strategies.push({
          key: `scene:${normalizedSceneCode}`,
          title: scene.displayName,
          primaryScene: scene,
          scenes: [scene],
          sharedScenes: [scene],
          freePolicy: policiesByTierAndScene.get(`FREE::${scene.taskType}::${normalizedSceneCode}`) ?? policiesByTierAndTaskFallback.get(`FREE::${scene.taskType}`) ?? null,
          premiumPolicy: policiesByTierAndScene.get(`PREMIUM::${scene.taskType}::${normalizedSceneCode}`) ?? policiesByTierAndTaskFallback.get(`PREMIUM::${scene.taskType}`) ?? null,
          scope: "SCENE_OVERRIDE",
          scopeLabel: "独立配置",
          description: "这一类场景已经拆出单独权益，可以分别维护免费次数、积分价格和内容上限。",
          coverageNote: "当前设置只影响这一项场景，不会联动其他功能。",
          editable: true,
        });
      });

    const interviewPackageScenes = records.filter((scene) => (
      scene.taskType === "INTERVIEW_TEXT"
      && !includedSceneCodes.has(normalizeSceneCode(scene.sceneCode))
      && !sceneOverrideSceneCodes.has(normalizeSceneCode(scene.sceneCode))
    ));
    if (interviewPackageScenes.length > 0) {
      const primaryScene = interviewPackageScenes.find((scene) => normalizeSceneCode(scene.sceneCode) === "INTERVIEW_REPLY") ?? interviewPackageScenes[0];
      strategies.push({
        key: "task:INTERVIEW_TEXT:package",
        title: "面试主会话包",
        primaryScene,
        scenes: interviewPackageScenes,
        sharedScenes: interviewPackageScenes,
        freePolicy: policiesByTierAndTaskFallback.get("FREE::INTERVIEW_TEXT") ?? null,
        premiumPolicy: policiesByTierAndTaskFallback.get("PREMIUM::INTERVIEW_TEXT") ?? null,
        scope: "TASK_SHARED",
        scopeLabel: "共享权益",
        description: "统一覆盖面试主流程中的核心问答，普通与 VIP 的免费次数和积分价格都在这里维护。",
        coverageNote: `适用场景：${interviewPackageScenes.map((scene) => scene.displayName).join("、")}。`,
        editable: true,
      });
    }

    const remainingTaskGroups = new Map<string, ApplicationOpsSceneItem[]>();
    records.forEach((scene) => {
      const normalizedSceneCode = normalizeSceneCode(scene.sceneCode);
      if (sceneOverrideSceneCodes.has(normalizedSceneCode) || includedSceneCodes.has(normalizedSceneCode)) {
        return;
      }
      if (scene.taskType === "INTERVIEW_TEXT") {
        return;
      }
      const current = remainingTaskGroups.get(scene.taskType) ?? [];
      current.push(scene);
      remainingTaskGroups.set(scene.taskType, current);
    });
    remainingTaskGroups.forEach((groupScenes, taskType) => {
      const primaryScene = groupScenes[0];
      const directStrategy = groupScenes.length === 1;
      strategies.push({
        key: `task:${taskType}`,
        title: directStrategy ? primaryScene.displayName : `${primaryScene.displayName} 等 ${groupScenes.length} 个场景`,
        primaryScene,
        scenes: groupScenes,
        sharedScenes: groupScenes,
        freePolicy: policiesByTierAndTaskFallback.get(`FREE::${taskType}`) ?? null,
        premiumPolicy: policiesByTierAndTaskFallback.get(`PREMIUM::${taskType}`) ?? null,
        scope: directStrategy ? "TASK_DIRECT" : "TASK_SHARED",
        scopeLabel: directStrategy ? "单独定价" : "共享权益",
        description: directStrategy
          ? "该功能单独维护权益规则，可直接按功能控制免费次数和积分价格。"
          : "这组功能共用一套权益规则，需要一起观察成本和放量效果。",
        coverageNote: directStrategy
          ? "当前设置只对应这一项业务场景。"
          : `适用场景：${groupScenes.map((scene) => scene.displayName).join("、")}。`,
        editable: true,
      });
    });

    includedConfigs.forEach((config, sceneCode) => {
      const scene = recordsBySceneCode.get(sceneCode);
      if (!scene) {
        return;
      }
      strategies.push({
        key: `included:${sceneCode}`,
        title: scene.displayName,
        primaryScene: scene,
        scenes: [scene],
        sharedScenes: [scene],
        freePolicy: policiesByTierAndTaskFallback.get("FREE::INTERVIEW_TEXT") ?? null,
        premiumPolicy: policiesByTierAndTaskFallback.get("PREMIUM::INTERVIEW_TEXT") ?? null,
        scope: "INCLUDED_WITH_SESSION",
        scopeLabel: "随主功能结算",
        description: config.description,
        coverageNote: "这里不单独设置价格，统一跟随“面试主会话包”的权益规则。",
        editable: false,
        referenceLabel: config.referenceLabel,
      });
    });

    return strategies.sort((left, right) => {
      const leftOrder = sceneOrder.get(normalizeSceneCode(left.primaryScene.sceneCode)) ?? Number.MAX_SAFE_INTEGER;
      const rightOrder = sceneOrder.get(normalizeSceneCode(right.primaryScene.sceneCode)) ?? Number.MAX_SAFE_INTEGER;
      return leftOrder - rightOrder;
    });
  }, [policiesByTierAndScene, policiesByTierAndTaskFallback, records]);

  const quotaSummary = useMemo(() => ({
    totalPolicies: quotaPolicies.length,
    editableStrategies: sceneQuotaStrategies.filter((item) => item.editable).length,
    reviewStrategies: sceneQuotaStrategies.filter((item) => shouldReviewPolicyFirst(item)).length,
    independentStrategies: sceneQuotaStrategies.filter((item) => isIndependentPolicyScope(item.scope)).length,
    sharedStrategies: sceneQuotaStrategies.filter((item) => item.scope === "TASK_SHARED").length,
    includedStrategies: sceneQuotaStrategies.filter((item) => item.scope === "INCLUDED_WITH_SESSION").length,
    preferredModelStrategies: sceneQuotaStrategies.filter((item) => hasPreferredModel(item)).length,
  }), [quotaPolicies.length, sceneQuotaStrategies]);

  const visibleSceneQuotaStrategies = useMemo(() => {
    // 需要优先确认的策略排最前面，避免成本异常或缺额配置被长列表淹没。
    const sourceOrder = new Map(sceneQuotaStrategies.map((strategy, index) => [strategy.key, index] as const));
    const ordered = [...sceneQuotaStrategies].sort((left, right) => {
      const leftNeedReview = shouldReviewPolicyFirst(left);
      const rightNeedReview = shouldReviewPolicyFirst(right);
      if (leftNeedReview !== rightNeedReview) {
        return leftNeedReview ? -1 : 1;
      }
      if (left.editable !== right.editable) {
        return left.editable ? -1 : 1;
      }
      const scopeDiff = getPolicyScopeSortWeight(left.scope) - getPolicyScopeSortWeight(right.scope);
      if (scopeDiff !== 0) {
        return scopeDiff;
      }
      if (left.sharedScenes.length !== right.sharedScenes.length) {
        return right.sharedScenes.length - left.sharedScenes.length;
      }
      if (left.primaryScene.followUp !== right.primaryScene.followUp) {
        return left.primaryScene.followUp ? -1 : 1;
      }
      return (sourceOrder.get(left.key) ?? Number.MAX_SAFE_INTEGER) - (sourceOrder.get(right.key) ?? Number.MAX_SAFE_INTEGER);
    });

    return ordered.filter((strategy) => {
      if (policyFilter === "REVIEW") {
        return shouldReviewPolicyFirst(strategy);
      }
      if (policyFilter === "INDEPENDENT") {
        return isIndependentPolicyScope(strategy.scope);
      }
      if (policyFilter === "SHARED") {
        return strategy.scope === "TASK_SHARED";
      }
      if (policyFilter === "INCLUDED") {
        return strategy.scope === "INCLUDED_WITH_SESSION";
      }
      if (policyFilter === "MODEL") {
        return hasPreferredModel(strategy);
      }
      return true;
    });
  }, [policyFilter, sceneQuotaStrategies]);

  const policyFilterOptions = useMemo(() => ([
    { value: "ALL" as const, label: "全部", count: sceneQuotaStrategies.length },
    { value: "REVIEW" as const, label: "优先确认", count: quotaSummary.reviewStrategies },
    { value: "INDEPENDENT" as const, label: "独立定价", count: quotaSummary.independentStrategies },
    { value: "SHARED" as const, label: "共享权益", count: quotaSummary.sharedStrategies },
    { value: "INCLUDED" as const, label: "随主功能", count: quotaSummary.includedStrategies },
    { value: "MODEL" as const, label: "已设优先模型", count: quotaSummary.preferredModelStrategies },
  ]), [
    quotaSummary.includedStrategies,
    quotaSummary.independentStrategies,
    quotaSummary.preferredModelStrategies,
    quotaSummary.reviewStrategies,
    quotaSummary.sharedStrategies,
    sceneQuotaStrategies.length,
  ]);

  const openPolicyEditor = useCallback((policy: QuotaPolicyItem | null, targetLabel: string, sharedScenes: ApplicationOpsSceneItem[]) => {
    if (!policy) {
      message.warning("当前策略尚未就绪，先补齐对应权益配置后再编辑。");
      return;
    }
    // 编辑弹层只拿当前策略 ID，相关共享场景作为只读说明展示。
    setEditingPolicyTargetLabel(targetLabel);
    setEditingPolicy(policy);
    setEditingPolicySharedScenes(sharedScenes);
    policyForm.setFieldsValue({
      dailyFreeLimit: policy.dailyFreeLimit,
      pointsPerCall: policy.pointsPerCall,
      dailyMaxLimit: policy.dailyMaxLimit,
      modelPreference: policy.modelPreference || "",
      maxInputTokens: policy.maxInputTokens,
    });
    setPolicyEditorOpen(true);
  }, [policyForm]);

  const handleSavePolicy = useCallback(async () => {
    if (!editingPolicy) {
      return;
    }
    try {
      const values = await policyForm.validateFields();
      setPolicySaving(true);
      // 策略保存后整页重拉，确保热点、成本和权益归组同时更新。
      await apiRequest<QuotaPolicyItem>(`/admin/ai/quota-policies/${editingPolicy.id}`, {
        method: "PUT",
        body: JSON.stringify({
          dailyFreeLimit: values.dailyFreeLimit,
          pointsPerCall: values.pointsPerCall,
          dailyMaxLimit: values.dailyMaxLimit,
          modelPreference: values.modelPreference?.trim() || null,
          maxInputTokens: values.maxInputTokens,
        }),
      });
      message.success("权益策略已更新");
      setPolicyEditorOpen(false);
      setEditingPolicyTargetLabel("");
      await loadData();
    } catch (saveError) {
      if (saveError instanceof Error && saveError.name === "Error") {
        const apiError = saveError as ApiClientError;
        message.error(apiError.message || "保存权益策略失败");
      }
    } finally {
      setPolicySaving(false);
    }
  }, [editingPolicy, loadData, policyForm]);

  const columns = useMemo<TableColumnsType<ApplicationOpsSceneItem>>(
    () => [
      {
        title: "场景",
        key: "scene",
        render: (_value, record) => (
          <div className="min-w-0">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-100 text-emerald-700">
                <Sparkles size={18} />
              </div>
              <div className="min-w-0">
                <Text strong className="block text-sm text-slate-900">{record.displayName}</Text>
                <Text type="secondary" className="admin-typography-caption block">{record.ownerDomain} · {record.frontEntry}</Text>
              </div>
            </div>
            <Paragraph className="admin-typography-caption !mb-0 !mt-2 !text-slate-500">
              {record.summary}
            </Paragraph>
            <Text type="secondary" className="admin-typography-chip block !mt-2">
              {getLabel(record.taskType, gatewayTaskTypeLabelMap, record.taskType)}
            </Text>
          </div>
        ),
      },
      {
        title: "关键信号",
        key: "signals",
        render: (_value, record) => {
          const signals = [];
          if (hotSceneCodes.has(record.channelCode)) {
            signals.push({ label: "热门", tone: "emerald" as const });
          }
          if (costSceneCodes.has(record.channelCode)) {
            signals.push({ label: "成本高", tone: "amber" as const });
          }
          if (riskSceneCodes.has(record.channelCode)) {
            signals.push({ label: "优先风险", tone: "rose" as const });
          }
          if (dormantSceneCodes.has(record.channelCode)) {
            signals.push({ label: "待激活", tone: "sky" as const });
          }
          if (record.usesBuiltinPrompt) {
            signals.push({ label: "内置提示词", tone: "violet" as const });
          }
          if (record.planned) {
            signals.push({ label: "规划中", tone: "slate" as const });
          }

          return (
            <div className="space-y-2">
              <div className="flex flex-wrap gap-2">
                {signals.length > 0 ? signals.map((signal) => (
                  <span
                    key={`${record.channelCode}-${signal.label}`}
                    className={joinClassNames("admin-typography-chip inline-flex rounded-full px-2.5 py-1 font-bold", getSignalChipClass(signal.tone))}
                  >
                    {signal.label}
                  </span>
                )) : (
                  <span className="admin-typography-chip inline-flex rounded-full bg-slate-100 px-2.5 py-1 font-bold text-slate-500 ring-1 ring-slate-200">
                    稳定运行
                  </span>
                )}
              </div>
              <span
                className={joinClassNames(
                  "admin-typography-chip inline-flex max-w-full rounded-full px-2.5 py-1 font-semibold",
                  getSignalChipClass(getRiskHintTone(record)),
                )}
              >
                {record.riskLabel}
              </span>
            </div>
          );
        },
      },
      {
        title: `近 ${payload?.days ?? windowDays} 日概览`,
        key: "volume",
        render: (_value, record) => (
          <div className="space-y-2">
            <div className="flex items-start gap-2">
              <span className="admin-typography-chip shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-slate-500">调用</span>
              <Text className="block text-sm leading-6 text-slate-900">{formatCount(record.calls)} 次</Text>
            </div>
            <div className="flex items-start gap-2">
              <span className="admin-typography-chip shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-slate-500">成本</span>
              <Text className="block text-sm leading-6 text-slate-900">{formatCny(record.totalCost, 2)}</Text>
            </div>
            <div className="flex items-start gap-2">
              <span className="admin-typography-chip shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-slate-500">最近</span>
              <Text className="block text-sm leading-6 text-slate-900">
                {record.lastCallAt ? formatDateTime(record.lastCallAt) : "暂无"}
              </Text>
            </div>
          </div>
        ),
      },
      {
        title: "质量表现",
        key: "quality",
        render: (_value, record) => (
          <div className="space-y-2">
            <div className="flex items-start gap-2">
              <span className="admin-typography-chip shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-slate-500">成功率</span>
              <Text className="block text-sm leading-6 text-slate-900">
                {record.calls > 0 ? record.successRate : "—"}
              </Text>
            </div>
            <div className="flex items-start gap-2">
              <span className="admin-typography-chip shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-slate-500">时延</span>
              <Text className="block text-sm leading-6 text-slate-900">
                {record.calls > 0 ? `${formatCount(record.avgLatencyMs)} ms` : "暂无"}
              </Text>
            </div>
          </div>
        ),
      },
      {
        title: "当前状态",
        key: "status",
        render: (_value, record) => (
          <div className="space-y-2">
            <span
              className={joinClassNames(
                "admin-typography-chip inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 font-bold tracking-[0.08em]",
                getStatusBadgeClass(record.status),
              )}
            >
              <span className={joinClassNames("h-1.5 w-1.5 rounded-full", getStatusDotClass(record.status))} />
              {record.statusLabel}
            </span>
            <div className="space-y-1.5">
              <div className="flex items-start gap-2">
                <span className="admin-typography-chip shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-slate-500">链路</span>
                <Text className="block text-sm leading-6 text-slate-900">
                  {record.primaryRouteCode || "等待建立投放链路"}
                </Text>
              </div>
              <div className="flex items-start gap-2">
                <span className="admin-typography-chip shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-slate-500">服务商</span>
                <Text className="block text-sm leading-6 text-slate-900">
                  {record.primaryRouteCode ? record.primaryProviderDisplayName : "待链路接入后显示"}
                </Text>
              </div>
              <div className="flex items-start gap-2">
                <span className="admin-typography-chip shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-slate-500">模型</span>
                <Text className="block text-sm leading-6 text-slate-900">
                  {record.primaryRouteCode ? record.primaryModelName : "待链路接入后显示"}
                </Text>
              </div>
              <div className="flex items-start gap-2">
                <span className="admin-typography-chip shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-slate-500">模板</span>
                <Text className="block text-sm leading-6 text-slate-900">
                  {record.hasActiveTemplate
                    ? `${record.activeTemplateName} v${record.activeTemplateVersionNo ?? "—"}`
                    : record.usesBuiltinPrompt
                      ? "当前走内置提示词"
                      : record.primaryRouteCode
                        ? "模板尚未就绪"
                        : "等待投放配置"}
                </Text>
              </div>
            </div>
          </div>
        ),
      },
      {
        title: "下一步",
        key: "actions",
        render: (_value, record) => (
          <Button
            type="link"
            className="!px-0 !text-xs !font-bold"
            onClick={() => navigate(record.nextActionTo)}
          >
            {record.nextActionLabel}
          </Button>
        ),
      },
    ],
    [costSceneCodes, dormantSceneCodes, hotSceneCodes, navigate, payload?.days, riskSceneCodes, windowDays],
  );

  const errorNotice = error ? (
    <Alert
      type="error"
      showIcon
      className="rounded-[28px]"
      message="加载 AI 应用运营失败"
      description={error}
    />
  ) : null;

  const scenesSection = (
    <section className="overflow-hidden rounded-[28px] border border-white bg-white shadow-[0px_10px_40px_rgba(44,47,49,0.06)]">
      <div className="flex flex-col gap-4 border-b border-slate-200 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <div className="text-xl font-bold text-slate-900">重点场景清单</div>
          <div className="mt-1 text-sm text-slate-500">
            统一用一张表看调用、成本、质量、经营信号和下一步动作，不再拆成重复榜单。
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Segmented
            value={scopeFilter}
            onChange={(value) => setScopeFilter(value as ScopeFilterValue)}
            options={[
              { label: `全部 ${summary.totalChannels}`, value: "ALL" },
              { label: `需跟进 ${summary.followUpChannels}`, value: "FOLLOW_UP" },
              { label: `经营中 ${summary.activeChannels}`, value: "ACTIVE" },
              { label: `静默 ${summary.dormantChannels}`, value: "DORMANT" },
              { label: `规划中 ${summary.plannedChannels}`, value: "PLANNED" },
            ]}
          />
          <Select
            className="min-w-[220px]"
            value={domainFilter}
            options={domainOptions}
            onChange={setDomainFilter}
          />
          <Button
            className="!h-10 !rounded-xl !border-slate-200 !px-4"
            onClick={() => void loadData()}
            disabled={loading}
          >
            <RefreshCcw size={15} className={loading ? "mr-2 animate-spin" : "mr-2"} />
            刷新数据
          </Button>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3 border-b border-slate-100 px-6 py-4">
        <div className="inline-flex items-center gap-2 rounded-full bg-slate-100 px-3 py-1.5 text-xs font-semibold text-slate-600">
          <Activity size={14} />
          总体成功率 {summary.overallSuccessRate}
        </div>
        <div className="inline-flex items-center gap-2 rounded-full bg-slate-100 px-3 py-1.5 text-xs font-semibold text-slate-600">
          <Gauge size={14} />
          平均时延 {formatCount(summary.avgLatencyMs)} ms
        </div>
        <div className="inline-flex items-center gap-2 rounded-full bg-slate-100 px-3 py-1.5 text-xs font-semibold text-slate-600">
          <Clock3 size={14} />
          当前显示 {formatCount(filteredRecords.length)} / {formatCount(records.length)}
        </div>
      </div>

      <div className="p-2 sm:p-4">
        <Table<ApplicationOpsSceneItem>
          rowKey="channelCode"
          loading={loading}
          columns={columns}
          dataSource={filteredRecords}
          pagination={false}
          tableLayout="auto"
          locale={{
            emptyText: (
              <Empty
                description={
                  scopeFilter === "ALL"
                    ? "近窗口暂无带场景归因的经营样本，可切换时间窗口或等待新调用回流"
                    : "当前筛选条件下没有匹配场景"
                }
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            ),
          }}
        />
      </div>
    </section>
  );

  const policiesSection = (
    <AdminSurfaceCard
      title="权益策略与积分配置"
      description="运营侧在这里维护普通 / VIP 的免费次数、单次积分、每日上限和优先模型，并清楚区分独立定价、共享权益和随主功能结算三类口径。"
      extra={(
        <>
          <Button
            className="!h-10 !rounded-xl !border-slate-200 !px-4"
            onClick={() => void loadData()}
            disabled={loading}
          >
            <RefreshCcw size={15} className={loading ? "mr-2 animate-spin" : "mr-2"} />
            刷新数据
          </Button>
        </>
      )}
      bodyClassName="p-6"
    >
      <AdminFilterBar className="mb-5">
        {policyFilterOptions.map((option) => (
          <button
            key={option.value}
            type="button"
            className={joinClassNames(
              "inline-flex items-center gap-2 rounded-full px-4 py-2 text-sm font-semibold transition-colors",
              getPolicyFilterButtonClass(policyFilter === option.value),
            )}
            onClick={() => setPolicyFilter(option.value)}
          >
            <span>{option.label}</span>
            <span className={joinClassNames(
              "rounded-full px-2 py-0.5 text-[11px] font-bold",
              policyFilter === option.value ? "bg-white/16 text-white" : "bg-slate-100 text-slate-500",
            )}>
              {formatCount(option.count)}
            </span>
          </button>
        ))}
        <div className="ml-auto inline-flex items-center rounded-full bg-white px-4 py-2 text-xs font-medium text-slate-500 shadow-[inset_0_0_0_1px_rgba(148,163,184,0.16)]">
          当前显示 {formatCount(visibleSceneQuotaStrategies.length)} / {formatCount(sceneQuotaStrategies.length)}，默认已按优先确认顺序排列
        </div>
      </AdminFilterBar>

      <div className="grid gap-5 xl:grid-cols-2">
        {visibleSceneQuotaStrategies.map((strategy) => {
          const palette = getPolicyCardPalette(strategy.scope);
          const ScopeIcon = palette.icon;
          const needsReview = shouldReviewPolicyFirst(strategy);

          return (
            <section
              key={strategy.key}
              className={joinClassNames(
                "relative overflow-hidden rounded-[28px] border px-5 py-5 shadow-none",
                palette.panel,
              )}
            >
              <div className={joinClassNames("absolute inset-x-0 top-0 h-1 bg-gradient-to-r", palette.line)} />

              <div className="relative flex items-start gap-4">
                <div className={joinClassNames("flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl", palette.iconWrap)}>
                  <ScopeIcon size={20} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="text-lg font-black tracking-tight text-slate-950">{strategy.title}</div>
                      <div className="mt-1 text-xs text-slate-500">
                        {strategy.primaryScene.ownerDomain} · {strategy.primaryScene.frontEntry}
                      </div>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {needsReview ? (
                        <span className="inline-flex rounded-full bg-rose-100 px-2.5 py-1 text-[11px] font-bold text-rose-700 ring-1 ring-rose-200">
                          优先确认
                        </span>
                      ) : null}
                      <span className={joinClassNames("inline-flex rounded-full px-2.5 py-1 text-[11px] font-bold", getPolicyScopeBadgeClass(strategy.scope))}>
                        {strategy.scopeLabel}
                      </span>
                      <span
                        className={joinClassNames(
                          "inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-bold",
                          getStatusBadgeClass(strategy.primaryScene.status),
                        )}
                      >
                        <span className={joinClassNames("h-1.5 w-1.5 rounded-full", getStatusDotClass(strategy.primaryScene.status))} />
                        {strategy.primaryScene.statusLabel}
                      </span>
                    </div>
                  </div>

                  <div className="mt-3 text-sm leading-6 text-slate-500">
                    {strategy.description}
                  </div>
                </div>
              </div>

              <div className="mt-5 grid gap-3 xl:grid-cols-2">
                <PolicyTierPanel
                  title="普通权益"
                  badge="普通"
                  badgeClassName="bg-slate-100 text-slate-600 ring-1 ring-slate-200"
                  policy={strategy.freePolicy}
                  editable={strategy.editable}
                  emptyText={strategy.editable ? "普通层级暂未配置" : "普通层级跟随主功能规则"}
                  onEdit={() => openPolicyEditor(strategy.freePolicy, strategy.title, strategy.sharedScenes)}
                />
                <PolicyTierPanel
                  title="VIP 权益"
                  badge="VIP"
                  badgeClassName="bg-amber-100 text-amber-700 ring-1 ring-amber-200"
                  policy={strategy.premiumPolicy}
                  editable={strategy.editable}
                  emptyText={strategy.editable ? "VIP 层级暂未配置" : "VIP 层级跟随主功能规则"}
                  onEdit={() => openPolicyEditor(strategy.premiumPolicy, strategy.title, strategy.sharedScenes)}
                />
              </div>

              <div className="mt-4 flex flex-wrap gap-2">
                <span className="inline-flex rounded-full bg-white/88 px-3 py-1.5 text-xs font-medium text-slate-600 shadow-[inset_0_0_0_1px_rgba(148,163,184,0.16)]">
                  {strategy.coverageNote}
                </span>
                <span className="inline-flex rounded-full bg-white/88 px-3 py-1.5 text-xs font-medium text-slate-600 shadow-[inset_0_0_0_1px_rgba(148,163,184,0.16)]">
                  单次内容上限：普通 {strategy.freePolicy ? formatCount(strategy.freePolicy.maxInputTokens) : "—"} / VIP {strategy.premiumPolicy ? formatCount(strategy.premiumPolicy.maxInputTokens) : "—"}
                </span>
              </div>

              <div className="mt-4 border-t border-slate-200/70 pt-4 text-xs font-medium text-slate-500">
                {strategy.referenceLabel ?? `运营动作：${strategy.primaryScene.nextActionLabel}`}
              </div>
            </section>
          );
        })}
      </div>
      {visibleSceneQuotaStrategies.length === 0 ? (
        <div className="pt-6">
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前筛选条件下没有匹配的权益策略" />
        </div>
      ) : null}
    </AdminSurfaceCard>
  );

  const scenesWorkspace = (
    <>
      {errorNotice}
      <AdminPageHeader
        sectionLabel="SCENE OPERATIONS"
        title="场景清单"
        description="集中查看各个 AI 场景的调用、成本、质量和下一步动作，避免在运营页反复切换多个榜单。"
        tone="emerald"
        actions={(
          <>
            <Segmented
              value={windowDays}
              options={[
                { label: "近 7 日", value: 7 },
                { label: "近 15 日", value: 15 },
                { label: "近 30 日", value: 30 },
              ]}
              onChange={(value) => setWindowDays(value as WindowValue)}
            />
            <Button
              className="!h-11 !rounded-2xl !border-slate-200"
              onClick={() => void loadData()}
              disabled={loading}
            >
              <RefreshCcw size={16} className={loading ? "mr-2 animate-spin" : "mr-2"} />
              刷新数据
            </Button>
          </>
        )}
      />
      {scenesSection}
    </>
  );

  const policiesWorkspace = (
    <>
      {errorNotice}
      <AdminPageHeader
        sectionLabel="POLICY OPERATIONS"
        title="权益策略"
        description="这里集中维护普通与 VIP 的权益口径、价格规则和优先模型，并和 AI 网关的渠道配置职责明确分开。"
        tone="slate"
      />
      {policiesSection}
    </>
  );

  const homeWorkspace = (
    <>
      <section className="relative overflow-hidden rounded-[34px] border border-[#e3f1ec] bg-[linear-gradient(135deg,#f8fcf8_0%,#f1faf8_55%,#edf7f3_100%)] px-8 py-8 text-slate-900 shadow-[0px_10px_40px_rgba(44,47,49,0.06)]">
        <div className="absolute -left-20 top-[-96px] h-64 w-64 rounded-full bg-[radial-gradient(circle,rgba(94,234,212,0.16),transparent_72%)] blur-2xl" />
        <div className="absolute right-[-60px] top-[-68px] h-72 w-72 rounded-full bg-[radial-gradient(circle,rgba(163,230,53,0.14),transparent_72%)] blur-3xl" />
        <div className="absolute bottom-[-120px] right-20 h-64 w-64 rounded-full bg-[radial-gradient(circle,rgba(34,197,94,0.10),transparent_72%)] blur-3xl" />

        <div className="relative grid gap-8 xl:grid-cols-[minmax(0,1fr)_360px] xl:items-end">
          <div>
            <span className="admin-typography-section-label inline-flex rounded-full bg-emerald-100 px-4 py-1.5 text-emerald-700">
              Application Operations
            </span>
            <div className="admin-typography-hero-title mt-5 font-['Manrope'] text-slate-950">
              AI 应用运营
            </div>
            <Paragraph className="admin-typography-hero-description !mb-0 !mt-4 !max-w-3xl !text-slate-500">
              运营侧只看真实场景经营结果：哪些场景在被使用、哪些场景成本高、哪些场景需要立即处理。
              底层链路、模型和内容配置的治理留在 AI 网关，这里不再重复展开。
            </Paragraph>
            <div className="mt-6 flex flex-wrap gap-3">
              <Button
                className="!h-11 !rounded-2xl !border-slate-200 !bg-white !text-slate-700 !shadow-none hover:!border-slate-300 hover:!text-slate-900"
                onClick={() => navigate("/admin/ai/gateway?tab=scenes")}
              >
                <Workflow size={16} className="mr-2" />
                进入 AI 网关
              </Button>
              <Button
                type="primary"
                className="!h-11 !rounded-2xl !border-none !bg-emerald-600 !text-white !shadow-[0_12px_30px_rgba(22,163,74,0.18)] hover:!bg-emerald-700"
                onClick={() => navigate("/admin/runtime?tab=ai-channels")}
              >
                <ArrowRight size={16} className="mr-2" />
                进入运行配置
              </Button>
            </div>
          </div>

          <div className="rounded-[28px] border border-white bg-white/82 p-5 shadow-[0px_10px_30px_rgba(44,47,49,0.04)] backdrop-blur-sm">
            <div className="admin-typography-data-label text-slate-400">运营窗口概览</div>
            <div className="mt-4 space-y-3">
              <div className="flex items-center justify-between rounded-2xl bg-slate-50 px-4 py-3">
                <span className="admin-typography-chip text-slate-500">整体成功率</span>
                <span className="admin-typography-mini-value text-slate-900">{summary.overallSuccessRate}</span>
              </div>
              <div className="flex items-center justify-between rounded-2xl bg-slate-50 px-4 py-3">
                <span className="admin-typography-chip text-slate-500">平均时延</span>
                <span className="admin-typography-mini-value text-slate-900">{formatCount(summary.avgLatencyMs)} ms</span>
              </div>
              <div className="flex items-center justify-between rounded-2xl bg-slate-50 px-4 py-3">
                <span className="admin-typography-chip text-slate-500">规划中场景</span>
                <span className="admin-typography-mini-value text-slate-900">{formatCount(summary.plannedChannels)}</span>
              </div>
            </div>
            <div className="mt-5">
              <Segmented
                block
                value={windowDays}
                options={[
                  { label: "近 7 日", value: 7 },
                  { label: "近 15 日", value: 15 },
                  { label: "近 30 日", value: 30 },
                ]}
                onChange={(value) => setWindowDays(value as WindowValue)}
              />
            </div>
          </div>
        </div>
      </section>

      {errorNotice}

      <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          icon={TrendingUp}
          label="场景调用量"
          value={formatCount(summary.totalCalls)}
          note={`整体成功率 ${summary.overallSuccessRate}`}
          tone="mint"
        />
        <MetricCard
          icon={Coins}
          label="成本观察"
          value={formatCny(summary.totalCost, 2)}
          note={`近 ${payload?.days ?? windowDays} 日人民币预估成本`}
          tone="teal"
        />
        <MetricCard
          icon={Bot}
          label="经营中场景"
          value={formatCount(summary.activeChannels)}
          note={`已就绪 ${formatCount(summary.readyChannels)} 个`}
          tone="amber"
        />
        <MetricCard
          icon={AlertTriangle}
          label="需跟进场景"
          value={formatCount(summary.followUpChannels)}
          note={`静默 ${formatCount(summary.dormantChannels)} 个 · 规划中 ${formatCount(summary.plannedChannels)} 个`}
          tone="rose"
        />
      </div>

      <section className="rounded-[28px] border border-white bg-white px-6 py-6 shadow-[0px_10px_40px_rgba(44,47,49,0.06)]">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <div className="admin-typography-surface-title text-slate-900">经营聚焦</div>
            <div className="admin-typography-surface-description mt-1 text-slate-500">
              折中保留一层高价值聚焦信息：先看放量、成本和风险，再进入下方主表做完整比对。
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            <span className="inline-flex items-center gap-2 rounded-full bg-slate-100 px-3 py-1.5 text-xs font-semibold text-slate-600">
              <Activity size={14} />
              当前窗口 {payload?.days ?? windowDays} 日
            </span>
            <span className="inline-flex rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-700 ring-1 ring-emerald-100">
              热门 {payload?.hotScenes.length ?? 0}
            </span>
            <span className="inline-flex rounded-full bg-amber-50 px-3 py-1.5 text-xs font-semibold text-amber-700 ring-1 ring-amber-100">
              成本偏高 {payload?.costScenes.length ?? 0}
            </span>
            <span className="inline-flex rounded-full bg-rose-50 px-3 py-1.5 text-xs font-semibold text-rose-700 ring-1 ring-rose-100">
              优先风险 {payload?.riskScenes.length ?? 0}
            </span>
            <span className="inline-flex rounded-full bg-sky-50 px-3 py-1.5 text-xs font-semibold text-sky-700 ring-1 ring-sky-100">
              待激活 {payload?.dormantScenes.length ?? 0}
            </span>
          </div>
        </div>

        <div className="mt-5 grid gap-4 xl:grid-cols-3">
          <FocusList
            icon={Sparkles}
            title="放量优先"
            description="近窗口调用最强、可继续放量的场景"
            items={(payload?.hotScenes ?? []).slice(0, 3)}
            emptyText="当前窗口没有明显的放量优先场景。"
            navigate={navigate}
            renderMeta={(item) => `${formatCount(item.calls)} 次 · 成功率 ${item.successRate} · ${item.ownerDomain}`}
          />
          <FocusList
            icon={Coins}
            title="成本关注"
            description="优先核对投入产出的高成本场景"
            items={(payload?.costScenes ?? []).slice(0, 3)}
            emptyText="当前窗口尚未形成需要单独盯看的成本消耗。"
            navigate={navigate}
            renderMeta={(item) => `${formatCny(item.totalCost, 2)} · ${formatCount(item.calls)} 次 · ${item.ownerDomain}`}
          />
          <FocusList
            icon={AlertTriangle}
            title="风险处理"
            description="体验或转化风险需要先处理"
            items={(payload?.riskScenes ?? []).slice(0, 3)}
            emptyText="当前窗口未识别出优先级更高的经营风险。"
            navigate={navigate}
            renderMeta={(item) => {
              if (item.riskType === "LOW_SUCCESS") {
                return `${item.ownerDomain} · 成功率 ${item.successRate} · 建议优先排查体验损耗`;
              }
              if (item.riskType === "HIGH_LATENCY") {
                return `${item.ownerDomain} · 响应偏慢 · 建议优先优化等待体验`;
              }
              if (item.riskType === "MISSING_TEMPLATE") {
                return `${item.ownerDomain} · 场景内容尚未完善 · 建议尽快补齐投放素材`;
              }
              if (item.riskType === "ROUTE_DISABLED") {
                return `${item.ownerDomain} · 当前场景不可用 · 建议尽快恢复`;
              }
              if (item.usesBuiltinPrompt) {
                return `${item.ownerDomain} · 仍在使用默认内容 · 建议补齐正式投放素材`;
              }
              return `${item.ownerDomain} · ${item.riskLabel}`;
            }}
          />
        </div>
      </section>
    </>
  );

  const activeWorkspace = activeTab === "scenes"
    ? scenesWorkspace
    : activeTab === "policies"
      ? policiesWorkspace
      : homeWorkspace;

  return (
    <AdminPageFrame>
      {activeWorkspace}

      {policyEditorOpen ? (
        <Suspense fallback={null}>
          <AdminAiApplicationsPolicyModal
            context={{
              policyEditorOpen,
              editingPolicy,
              editingPolicyTargetLabel,
              policySaving,
              handleSavePolicy,
              setPolicyEditorOpen,
              setEditingPolicyTargetLabel,
              editingPolicySharedScenes,
              policyForm,
            }}
          />
        </Suspense>
      ) : null}
    </AdminPageFrame>
  );
}
