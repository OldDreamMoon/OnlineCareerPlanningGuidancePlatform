import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Alert, Button, Empty, message } from "antd";
import type { LucideIcon } from "lucide-react";
import {
  Activity,
  AlertCircle,
  BookOpen,
  BriefcaseBusiness,
  CheckCircle2,
  Clock3,
  Layers3,
  MessageSquareMore,
  RefreshCcw,
  Share2,
  ShieldAlert,
  Sparkles,
  TrendingUp,
  Users,
  Wallet,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { AdminAnimatedNumber, canAnimateAdminValue } from "../components/admin/AdminAnimatedNumber";
import { AdminPageFrame, AdminPageHeader } from "../components/admin/AdminOpsPrimitives";
import { useAdminStaleCache } from "../hooks/useAdminStaleCache";
import { adminAiChannelCatalog } from "../lib/adminAiChannels";
import { useLatestRequest } from "../hooks/useLatestRequest";
import { ApiClientError, apiRequest, buildQuery, isAbortError } from "../lib/apiClient";
import {
  gatewayExecutionModeLabelMap,
  gatewayTaskTypeLabelMap,
  getLabel,
  moderationActionLabelMap,
  moderationReasonLabelMap,
  riskLevelLabelMap,
  targetTypeLabelMap,
  tierLabelMap,
} from "../lib/adminLabels";
import {
  formatCount,
  formatDateTime,
  formatMoneyFen,
  formatPercent,
  formatRelativeTime,
} from "../lib/formatters";

const AdminDashboardReportModal = lazy(() => import("../components/admin/overlays/AdminDashboardReportModal"));

type TimeValue = number | string | null;

type ProviderRuntimeSummaryPayload = {
  totalProviders: number;
  healthyProviders: number;
  degradedProviders: number;
  downProviders: number;
  disabledProviders: number;
  idleProviders?: number;
  unhealthyProviders?: number;
  hours?: number;
  timezone?: string | null;
};

type WorkbenchSummary = {
  generatedAt: TimeValue;
  pendingReports: number;
  reviewQueue: number;
  afterSalesRequests: number;
  reconciliationReviewRequired: number;
  totalPendingTasks: number;
  providerRuntime: ProviderRuntimeSummaryPayload;
};

type DashboardCachePayload = {
  workbench: WorkbenchSummary | null;
  userSummary: UserSummaryResponse | null;
  enterpriseOverview: EnterpriseTaskOpsOverviewPayload | null;
  mentorOverview: MentorOpsOverviewPayload | null;
  notificationOverview: NotificationOpsOverviewPayload | null;
  skillOverview: SkillOpsOverviewPayload | null;
  consoleSnapshot: ConsoleSnapshotPayload | null;
  routes: RouteItem[];
  templates: PromptTemplateItem[];
  costDashboard: CostDashboardPayload | null;
  sceneMetricsPayload: ApplicationSceneMetricsPayload | null;
  pendingReports: ReportItem[];
};

type UserSummaryResponse = {
  totalUsers: number;
  mentorUsers: number;
  enterpriseUsers: number;
  premiumUsers: number;
  pendingApprovalUsers: number;
  suspendedUsers: number;
  activeUsers7d: number;
  newUsers7d: number;
};

type EnterpriseTaskOpsOverviewPayload = {
  totalTaskCount: number;
  openTaskCount: number;
  riskyTaskCount: number;
  highRiskTaskCount: number;
  staleReviewTaskCount: number;
  deadlinePressureTaskCount: number;
  closedWithoutAcceptedTaskCount: number;
};

type NotificationOpsOverviewPayload = {
  announcementCount: number;
  announcementsLast7Days: number;
  pendingJobCount: number;
  retryJobCount: number;
  deadJobCount: number;
  emailReady: boolean;
  websocketReady: boolean;
  lastAnnouncementAt: TimeValue;
};

type MentorOpsOverviewPayload = {
  totalMentorCount: number;
  approvedMentorCount: number;
  pendingApprovalCount: number;
  riskyMentorCount: number;
  scheduleRiskMentorCount: number;
  fulfillmentRiskMentorCount: number;
  pendingWithdrawalMentorCount: number;
  pendingWithdrawalAmountFen: number;
};

type SkillOpsOverviewPayload = {
  totalNodeCount: number;
  rootNodeCount: number;
  leafNodeCount: number;
  relationCount: number;
  totalResourceCount: number;
  nodesWithoutResourceCount: number;
};

type FeatureFlagItem = {
  key: string;
  currentValue: string;
  overridden: boolean;
};

type RuntimeSettingsPayload = {
  debugModeEnabled: boolean;
  aiRequestLogEnabled: boolean;
};

type ModerationPoliciesResponse = {
  aiInputEnabled: boolean;
  aiOutputEnabled: boolean;
  communityStrictReviewEnabled: boolean;
  autoHideReportThreshold: number;
};

type AiChannelOverviewPayload = {
  totalProviders: number;
  enabledProviders: number;
  totalRoutes: number;
  enabledRoutes: number;
  totalPromptTemplates: number;
  activePromptTemplates: number;
};

type ConsoleSnapshotPayload = {
  generatedAt: TimeValue;
  featureFlags: FeatureFlagItem[];
  runtimeSettings: RuntimeSettingsPayload;
  moderationPolicies: ModerationPoliciesResponse;
  aiChannels: AiChannelOverviewPayload;
};

type RouteItem = {
  taskType: string;
  sceneCode: string | null;
  promptTemplateName: string | null;
  executionMode: string;
  enabled: boolean;
  priorityNo: number;
};

type PromptTemplateItem = {
  taskType: string;
  templateName: string;
  status: string;
};

type MetricItem = {
  name: string;
  calls: number;
  cost: string;
};

type TopUserItem = {
  userId: number;
  email: string;
  displayName: string;
  calls: number;
  cost: string;
};

type CostDashboardPayload = {
  period: string;
  totalCalls: number;
  totalCost: string;
  byModel: MetricItem[];
  byProvider: MetricItem[];
  byTaskType: MetricItem[];
  byTier: MetricItem[];
  topUsers: TopUserItem[];
};

type ApplicationSceneMetricItem = {
  taskType: string;
  sceneCode: string;
  calls: number;
  successCalls: number;
  successRate: string;
  avgLatencyMs: number;
  totalCost: string;
  lastCallAt: TimeValue;
};

type ApplicationSceneMetricsPayload = {
  days: number;
  totalCalls: number;
  totalCost: string;
  records: ApplicationSceneMetricItem[];
};

type ReportItem = {
  reportId: number;
  targetType: string;
  contentTitle: string;
  reasonCode: string;
  status: string;
  latestAction: string | null;
  reportCount: number;
  createdAt: TimeValue;
  updatedAt: TimeValue;
};

type ReportListResponse = {
  records: ReportItem[];
};

type ReportDetailResponse = {
  reportId: number;
  reporterUserId: number | null;
  reporterDisplayName: string | null;
  targetType: string;
  targetId: string | null;
  contentPostId: string | null;
  contentTitle: string | null;
  contentBody: string | null;
  reasonCode: string | null;
  reportDetail: string | null;
  status: string;
  latestAction: string | null;
  reportCount: number;
  targetStatus: string | null;
  targetRiskLevel: string | null;
  createdAt: TimeValue;
  updatedAt: TimeValue;
};

type DashboardSceneMetricRow = ApplicationSceneMetricItem & {
  sceneKey: string;
  displayName: string;
  ownerDomain: string;
  frontEntry: string;
  summary: string;
  registered: boolean;
};

type GlassPanelProps = {
  className?: string;
  children: ReactNode;
};

type TrendTone = "primary" | "error" | "tertiary" | "secondary";

type StatCardProps = {
  icon: LucideIcon;
  label: string;
  value: string;
  trend?: string;
  note?: string;
  trendTone?: TrendTone;
  isCritical?: boolean;
  progress?: number;
};

type PendingTaskTone = "primary" | "error" | "tertiary" | "secondary";

type PendingTaskItem = {
  key: string;
  label: string;
  count: number;
  value: string;
  note: string;
  tone: PendingTaskTone;
  to: string;
};

type DomainTone = "indigo" | "amber" | "rose" | "emerald";

type DomainHealthTone = "stable" | "attention" | "warning";

type DomainHealthItem = {
  key: string;
  label: string;
  signals: number;
  status: string;
  note: string;
  tone: DomainHealthTone;
  to: string;
};

type DomainMatrixCardProps = {
  icon: LucideIcon;
  title: string;
  headline: string;
  note: string;
  lines: Array<{
    text: string;
    tone: "primary" | "positive" | "warning" | "danger" | "neutral";
  }>;
  tone: DomainTone;
  action?: ReactNode;
};

type RuntimeFocusCardProps = {
  title: string;
  value: string;
  sectionLabel: string;
  note: string;
  detailTitle: string;
  detailValue: string;
  detailNote: string;
  tone: "primary" | "tertiary" | "error";
};

type SnapshotPanelTone = "indigo" | "slate";

const reportStatusLabelMap: Record<string, string> = {
  PENDING: "待处理",
  ACCEPTED: "已采纳",
  REJECTED: "已驳回",
  CLOSED: "已关闭",
};

function joinClassNames(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function buildSummaryItemVisual(config: {
  icon: LucideIcon;
  iconClassName: string;
  titleClassName: string;
  valueClassName: string;
  barClassName: string;
}) {
  return {
    Icon: config.icon,
    iconClassName: config.iconClassName,
    titleClassName: config.titleClassName,
    valueClassName: config.valueClassName,
    barClassName: config.barClassName,
  };
}

function getProviderCostVisual(name: string) {
  const normalized = name.trim().toUpperCase();
  if (normalized.includes("INTERVIEW")) {
    return buildSummaryItemVisual({
      icon: MessageSquareMore,
      iconClassName: "bg-indigo-50 text-indigo-600",
      titleClassName: "text-indigo-700",
      valueClassName: "text-indigo-700",
      barClassName: "bg-[#4647d3]",
    });
  }
  if (normalized.includes("RESUME")) {
    return buildSummaryItemVisual({
      icon: BriefcaseBusiness,
      iconClassName: "bg-amber-50 text-amber-600",
      titleClassName: "text-amber-700",
      valueClassName: "text-amber-700",
      barClassName: "bg-[#f8a010]",
    });
  }
  if (normalized.includes("SUMMARY")) {
    return buildSummaryItemVisual({
      icon: BookOpen,
      iconClassName: "bg-emerald-50 text-emerald-600",
      titleClassName: "text-emerald-700",
      valueClassName: "text-emerald-700",
      barClassName: "bg-[#10b981]",
    });
  }
  if (normalized.includes("STT") || normalized.includes("TTS") || normalized.includes("VOICE") || normalized.includes("AUDIO")) {
    return buildSummaryItemVisual({
      icon: Activity,
      iconClassName: "bg-sky-50 text-sky-600",
      titleClassName: "text-sky-700",
      valueClassName: "text-sky-700",
      barClassName: "bg-sky-500",
    });
  }
  if (normalized.includes("GEMINI") || normalized.includes("AI")) {
    return buildSummaryItemVisual({
      icon: Sparkles,
      iconClassName: "bg-violet-50 text-violet-600",
      titleClassName: "text-violet-700",
      valueClassName: "text-violet-700",
      barClassName: "bg-violet-500",
    });
  }
  return buildSummaryItemVisual({
    icon: Share2,
    iconClassName: "bg-slate-100 text-slate-600",
    titleClassName: "text-slate-700",
    valueClassName: "text-slate-700",
    barClassName: "bg-slate-500",
  });
}

function getTaskTypeVisual(name: string) {
  const normalized = name.trim().toUpperCase();
  if (normalized.includes("INTERVIEW") && normalized.includes("SUMMARY")) {
    return buildSummaryItemVisual({
      icon: BookOpen,
      iconClassName: "bg-rose-50 text-rose-600",
      titleClassName: "text-rose-700",
      valueClassName: "text-rose-700",
      barClassName: "bg-[#f74b6d]",
    });
  }
  if (normalized.includes("INTERVIEW")) {
    return buildSummaryItemVisual({
      icon: MessageSquareMore,
      iconClassName: "bg-amber-50 text-amber-600",
      titleClassName: "text-amber-700",
      valueClassName: "text-amber-700",
      barClassName: "bg-[#f8a010]",
    });
  }
  if (normalized.includes("RESUME")) {
    return buildSummaryItemVisual({
      icon: BriefcaseBusiness,
      iconClassName: "bg-indigo-50 text-indigo-600",
      titleClassName: "text-indigo-700",
      valueClassName: "text-indigo-700",
      barClassName: "bg-[#4647d3]",
    });
  }
  if (normalized.includes("STT") || normalized.includes("TTS") || normalized.includes("VOICE") || normalized.includes("AUDIO")) {
    return buildSummaryItemVisual({
      icon: Activity,
      iconClassName: "bg-sky-50 text-sky-600",
      titleClassName: "text-sky-700",
      valueClassName: "text-sky-700",
      barClassName: "bg-sky-500",
    });
  }
  return buildSummaryItemVisual({
    icon: Sparkles,
    iconClassName: "bg-amber-50 text-amber-600",
    titleClassName: "text-amber-700",
    valueClassName: "text-amber-700",
    barClassName: "bg-[#f8a010]",
  });
}

function getTierVisual(name: string) {
  const normalized = name.trim().toUpperCase();
  if (normalized.includes("PREMIUM") || normalized.includes("PRO")) {
    return buildSummaryItemVisual({
      icon: Wallet,
      iconClassName: "bg-amber-50 text-amber-600",
      titleClassName: "text-amber-700",
      valueClassName: "text-amber-700",
      barClassName: "bg-[#f8a010]",
    });
  }
  if (normalized.includes("ENTERPRISE") || normalized.includes("BUSINESS")) {
    return buildSummaryItemVisual({
      icon: BriefcaseBusiness,
      iconClassName: "bg-indigo-50 text-indigo-600",
      titleClassName: "text-indigo-700",
      valueClassName: "text-indigo-700",
      barClassName: "bg-[#4647d3]",
    });
  }
  if (normalized.includes("FREE")) {
    return buildSummaryItemVisual({
      icon: Users,
      iconClassName: "bg-emerald-50 text-emerald-600",
      titleClassName: "text-emerald-700",
      valueClassName: "text-emerald-700",
      barClassName: "bg-[#006947]",
    });
  }
  return buildSummaryItemVisual({
    icon: ShieldAlert,
    iconClassName: "bg-slate-100 text-slate-600",
    titleClassName: "text-slate-700",
    valueClassName: "text-slate-700",
    barClassName: "bg-slate-500",
  });
}

function getTopCostUserVisual(index: number) {
  if (index === 0) {
    return buildSummaryItemVisual({
      icon: Wallet,
      iconClassName: "bg-violet-50 text-violet-600",
      titleClassName: "text-violet-700",
      valueClassName: "text-violet-700",
      barClassName: "bg-violet-500",
    });
  }
  if (index === 1) {
    return buildSummaryItemVisual({
      icon: Users,
      iconClassName: "bg-indigo-50 text-indigo-600",
      titleClassName: "text-indigo-700",
      valueClassName: "text-indigo-700",
      barClassName: "bg-[#4647d3]",
    });
  }
  return buildSummaryItemVisual({
    icon: BriefcaseBusiness,
    iconClassName: "bg-emerald-50 text-emerald-600",
    titleClassName: "text-emerald-700",
    valueClassName: "text-emerald-700",
    barClassName: "bg-[#10b981]",
  });
}

function parseNumericValue(value: string | number | null | undefined) {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : 0;
  }
  if (typeof value !== "string") {
    return 0;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function parsePercentValue(value: string | number | null | undefined) {
  if (typeof value === "number") {
    return value;
  }
  if (typeof value !== "string") {
    return 0;
  }
  return Number.parseFloat(value.replace("%", "")) || 0;
}

function normalizeSceneCode(value: string | null | undefined) {
  return value?.trim().toUpperCase() || "";
}

function buildSceneMetricKey(taskType: string, sceneCode: string | null | undefined) {
  return `${taskType}::${normalizeSceneCode(sceneCode)}`;
}

function sortRoutes(left: RouteItem, right: RouteItem) {
  if (left.enabled !== right.enabled) {
    return left.enabled ? -1 : 1;
  }
  return left.priorityNo - right.priorityNo;
}

function clampPercent(value: number) {
  if (!Number.isFinite(value)) {
    return 0;
  }
  return Math.max(0, Math.min(100, value));
}

function resolveDomainHealthTone(signalCount: number): DomainHealthTone {
  if (signalCount >= 8) {
    return "warning";
  }
  if (signalCount >= 3) {
    return "attention";
  }
  return "stable";
}

function getDomainHealthMeta(tone: DomainHealthTone) {
  if (tone === "warning") {
    return {
      badge: "bg-[#f74b6d]/12 text-[#b41340]",
      bar: "from-[#f74b6d] to-[#fb7185]",
      icon: "bg-[#f74b6d]/10 text-[#b41340]",
      text: "text-[#b41340]",
    };
  }
  if (tone === "attention") {
    return {
      badge: "bg-[#f8a010]/14 text-[#815100]",
      bar: "from-[#f8a010] to-[#fbbf24]",
      icon: "bg-[#f8a010]/12 text-[#815100]",
      text: "text-[#815100]",
    };
  }
  return {
    badge: "bg-[#69f6b8]/18 text-[#006947]",
    bar: "from-[#10b981] to-[#34d399]",
    icon: "bg-[#69f6b8]/18 text-[#006947]",
    text: "text-[#006947]",
  };
}

function formatAiCurrency(value: string | number | null | undefined, digits = 2) {
  if (value === null || value === undefined || value === "") {
    return "¥0.00";
  }
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? `¥${numericValue.toFixed(digits)}` : `¥${value}`;
}

function getSceneDisplayName(
  item: Pick<DashboardSceneMetricRow, "displayName"> | Pick<ApplicationSceneMetricItem, "sceneCode" | "taskType"> | null | undefined,
) {
  if (!item) {
    return "暂无样本";
  }
  if ("displayName" in item) {
    return item.displayName;
  }
  return item.sceneCode || item.taskType;
}

function getReportStatusMeta(status: string | null | undefined) {
  const normalized = status?.trim().toUpperCase() || "PENDING";
  if (normalized === "ACCEPTED") {
    return {
      label: getLabel(normalized, reportStatusLabelMap, normalized),
      className: "bg-[#69f6b8]/18 text-[#006947]",
    };
  }
  if (normalized === "REJECTED") {
    return {
      label: getLabel(normalized, reportStatusLabelMap, normalized),
      className: "bg-[#f8a010]/18 text-[#815100]",
    };
  }
  if (normalized === "CLOSED") {
    return {
      label: getLabel(normalized, reportStatusLabelMap, normalized),
      className: "bg-slate-200 text-slate-600",
    };
  }
  return {
    label: getLabel(normalized, reportStatusLabelMap, normalized),
    className: "bg-[#4647d3]/12 text-[#4647d3]",
  };
}

function getReportPriorityMeta(reportCount: number) {
  if (reportCount >= 5) {
    return {
      label: "高压",
      className: "bg-[#f74b6d]/16 text-[#b41340]",
    };
  }
  if (reportCount >= 3) {
    return {
      label: "优先",
      className: "bg-[#f8a010]/16 text-[#815100]",
    };
  }
  return {
    label: "排队",
    className: "bg-slate-100 text-slate-500",
  };
}

function GlassPanel({ className, children }: GlassPanelProps) {
  return (
    <div
      className={joinClassNames(
        "rounded-[22px] border border-white/70 bg-white/78 shadow-[0px_8px_24px_rgba(44,47,49,0.045)] backdrop-blur-xl",
        className,
      )}
    >
      {children}
    </div>
  );
}

function SectionTitle({ title, subtitle, icon: Icon }: { title: string; subtitle?: string; icon?: LucideIcon }) {
  return (
    <div className="mb-3.5">
      <div className="flex items-center gap-3">
        {Icon ? (
          <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-white text-[#4647d3] shadow-[0_10px_20px_rgba(15,23,42,0.04)]">
            <Icon size={18} />
          </div>
        ) : null}
        <h2 className="admin-typography-section-title font-['Manrope'] text-[#2c2f31]">{title}</h2>
      </div>
      {subtitle ? (
        <p className="admin-typography-section-description mt-1 text-slate-500">{subtitle}</p>
      ) : null}
    </div>
  );
}

function SnapshotPanel({
  tone,
  title,
  subtitle,
  icon: Icon,
  children,
  footer,
}: {
  tone: SnapshotPanelTone;
  title: string;
  subtitle: string;
  icon: LucideIcon;
  children: ReactNode;
  footer?: ReactNode;
}) {
  const paletteMap: Record<SnapshotPanelTone, { shell: string; line: string; header: string; title: string; iconWrap: string; watermark: string }> = {
    indigo: {
      shell: "border-indigo-100 bg-[linear-gradient(180deg,rgba(99,102,241,0.05),rgba(255,255,255,0.98)_22%,rgba(248,250,252,0.96))]",
      line: "from-[#6366f1] to-[#a5b4fc]",
      header: "bg-[linear-gradient(135deg,rgba(238,242,255,0.96),rgba(255,255,255,0.92))]",
      title: "text-[#312e81]",
      iconWrap: "bg-indigo-100/90 text-indigo-600",
      watermark: "text-indigo-200/60",
    },
    slate: {
      shell: "border-slate-200 bg-[linear-gradient(180deg,rgba(248,250,252,0.98),rgba(255,255,255,0.98)_22%,rgba(248,250,252,0.96))]",
      line: "from-[#475569] to-[#94a3b8]",
      header: "bg-[linear-gradient(135deg,rgba(248,250,252,0.98),rgba(255,255,255,0.96))]",
      title: "text-slate-950",
      iconWrap: "bg-slate-200/90 text-slate-700",
      watermark: "text-slate-200/70",
    },
  };

  const palette = paletteMap[tone];

  return (
    <section className={joinClassNames("relative overflow-hidden rounded-[26px] border shadow-[0_12px_28px_rgba(15,23,42,0.04)]", palette.shell)}>
      <div className={joinClassNames("absolute inset-x-0 top-0 h-1 bg-gradient-to-r", palette.line)} />
      <div className={joinClassNames("pointer-events-none absolute -right-2 -top-2 z-0 opacity-70", palette.watermark)}>
        <Icon size={40} strokeWidth={1.35} />
      </div>
      <div className={joinClassNames("relative z-10 border-b border-slate-200/80 px-5 pb-4 pt-5", palette.header)}>
        <div className="flex items-center gap-3">
          <div className={joinClassNames("flex h-10 w-10 items-center justify-center rounded-2xl shadow-sm", palette.iconWrap)}>
            <Icon size={18} />
          </div>
          <h3 className={joinClassNames("admin-typography-surface-title font-['Manrope']", palette.title)}>{title}</h3>
        </div>
        <p className="admin-typography-section-description mt-1 text-slate-500">{subtitle}</p>
      </div>
      <div className="relative z-10 p-5">{children}</div>
      {footer ? <div className="relative z-10 border-t border-slate-200/80 bg-slate-50/85 px-5 py-3.5">{footer}</div> : null}
    </section>
  );
}

function StatCard({
  icon: Icon,
  label,
  value,
  trend,
  note,
  trendTone = "primary",
  isCritical,
  progress,
}: StatCardProps) {
  const renderedValue = canAnimateAdminValue(value) ? <AdminAnimatedNumber value={value} /> : value;
  const colorClasses: Record<TrendTone, string> = {
    primary: "text-[#4647d3] bg-[#4647d3]/10",
    error: "text-[#b41340] bg-[#f74b6d]/18",
    tertiary: "text-[#815100] bg-[#f8a010]/20",
    secondary: "text-[#006947] bg-[#69f6b8]/22",
  };

  const trendTextClasses: Record<TrendTone, string> = {
    primary: "text-[#4647d3]",
    error: "text-[#b41340]",
    tertiary: "text-[#815100]",
    secondary: "text-[#006947]",
  };

  const stripeClasses: Record<TrendTone, string> = {
    primary: "border-l-[#4647d3]",
    error: "border-l-[#b41340]",
    tertiary: "border-l-[#815100]",
    secondary: "border-l-[#006947]",
  };

  return (
    <GlassPanel
      className={joinClassNames(
        "border-l-4 p-4 transition-all duration-200 hover:-translate-y-1",
        stripeClasses[trendTone],
        isCritical && "shadow-[0_12px_24px_rgba(180,19,64,0.06)]",
      )}
    >
      <div className="flex items-center gap-2.5">
        <div className={joinClassNames("flex h-9 w-9 items-center justify-center rounded-2xl", colorClasses[trendTone])}>
          <Icon size={20} />
        </div>
        <h3 className="admin-typography-card-title text-slate-700">{label}</h3>
      </div>
      <div className="mt-2.5 flex items-end gap-2">
        <span className={joinClassNames("admin-typography-card-value font-['Manrope']", isCritical ? "text-[#b41340]" : "text-[#2c2f31]")}>
          {renderedValue}
        </span>
        {trend ? (
          <span className={joinClassNames("admin-typography-chip pb-1", trendTextClasses[trendTone])}>
            {trend}
          </span>
        ) : null}
      </div>
      {note ? (
        <div className="admin-typography-data-note mt-2.5 text-slate-500">{note}</div>
      ) : null}
      {progress !== undefined ? (
        <div className="mt-3.5 h-1.5 w-full overflow-hidden rounded-full bg-slate-200">
          <div
            className="h-full rounded-full bg-gradient-to-r from-[#4647d3] to-[#006947] transition-all duration-700"
            style={{ width: `${clampPercent(progress)}%` }}
          />
        </div>
      ) : null}
    </GlassPanel>
  );
}

function DomainMatrixCard({ icon: Icon, title, headline, note, lines, tone, action }: DomainMatrixCardProps) {
  const renderedHeadline = canAnimateAdminValue(headline) ? <AdminAnimatedNumber value={headline} /> : headline;
  const paletteMap: Record<DomainTone, { stripe: string; icon: string }> = {
    indigo: {
      stripe: "border-t-[#5b61f6]",
      icon: "bg-[#4647d3]/10 text-[#4647d3]",
    },
    amber: {
      stripe: "border-t-[#f8a010]",
      icon: "bg-[#f8a010]/14 text-[#815100]",
    },
    rose: {
      stripe: "border-t-[#f74b6d]",
      icon: "bg-[#f74b6d]/12 text-[#b41340]",
    },
    emerald: {
      stripe: "border-t-[#006947]",
      icon: "bg-[#69f6b8]/18 text-[#006947]",
    },
  };

  const palette = paletteMap[tone];
  const lineToneMap = {
    primary: {
      shell: "border border-indigo-100/90 bg-indigo-50/85",
      dot: "bg-indigo-500",
      text: "text-indigo-700",
    },
    positive: {
      shell: "border border-emerald-100/90 bg-emerald-50/90",
      dot: "bg-emerald-500",
      text: "text-emerald-700",
    },
    warning: {
      shell: "border border-amber-100/90 bg-amber-50/90",
      dot: "bg-amber-500",
      text: "text-amber-700",
    },
    danger: {
      shell: "border border-rose-100/90 bg-rose-50/90",
      dot: "bg-rose-500",
      text: "text-rose-700",
    },
    neutral: {
      shell: "border border-slate-200/90 bg-slate-50/85",
      dot: "bg-slate-400",
      text: "text-slate-600",
    },
  } as const;

  return (
    <GlassPanel className={joinClassNames("border-t-4 p-4", palette.stripe)}>
      <div className="flex items-center gap-2.5">
        <div className={joinClassNames("flex h-10 w-10 items-center justify-center rounded-[15px]", palette.icon)}>
          <Icon size={18} />
        </div>
        <div className="admin-typography-card-title text-slate-700">{title}</div>
      </div>
      <div className="admin-typography-subsection-title mt-2.5 font-['Manrope'] leading-none tracking-[-0.05em] text-[#2c2f31]">
        {renderedHeadline}
      </div>
      <div className="admin-typography-data-note mt-2 text-slate-500">{note}</div>
      <div className="mt-3.5 space-y-2">
        {lines.map((line) => {
          const linePalette = lineToneMap[line.tone];

          return (
            <div
              key={line.text}
              className={joinClassNames("admin-typography-caption flex items-center gap-2.5 rounded-2xl px-3.5 py-2.5", linePalette.shell)}
            >
              <span className={joinClassNames("h-2 w-2 shrink-0 rounded-full", linePalette.dot)} />
              <span className={joinClassNames("min-w-0", linePalette.text)}>{line.text}</span>
            </div>
          );
        })}
      </div>
      {action ? <div className="mt-3.5">{action}</div> : null}
    </GlassPanel>
  );
}

function RuntimeFocusCard({
  title,
  value,
  sectionLabel,
  note,
  detailTitle,
  detailValue,
  detailNote,
  tone,
}: RuntimeFocusCardProps) {
  const renderedValue = canAnimateAdminValue(value) ? <AdminAnimatedNumber value={value} /> : value;
  const renderedDetailValue = canAnimateAdminValue(detailValue) ? <AdminAnimatedNumber value={detailValue} /> : detailValue;
  const paletteMap = {
    primary: {
      value: "text-[#4647d3]",
      icon: "text-[#006947]",
      panel: "bg-[linear-gradient(180deg,rgba(255,255,255,0.92),rgba(238,242,255,0.98))]",
      label: "bg-indigo-100/90 text-indigo-700",
      sectionTitle: "text-indigo-700",
      footerTitle: "text-indigo-700",
    },
    tertiary: {
      value: "text-[#815100]",
      icon: "text-[#815100]",
      panel: "bg-[linear-gradient(180deg,rgba(255,255,255,0.92),rgba(255,247,237,0.98))]",
      label: "bg-amber-100/90 text-amber-700",
      sectionTitle: "text-amber-700",
      footerTitle: "text-amber-700",
    },
    error: {
      value: "text-[#b41340]",
      icon: "text-[#b41340]",
      panel: "bg-[linear-gradient(180deg,rgba(255,255,255,0.92),rgba(255,241,242,0.98))]",
      label: "bg-rose-100/90 text-rose-700",
      sectionTitle: "text-rose-700",
      footerTitle: "text-rose-700",
    },
  } as const;

  const palette = paletteMap[tone];

  return (
    <div className={joinClassNames("h-full rounded-[20px] p-4 shadow-sm", palette.panel)}>
      <div className={joinClassNames("inline-flex w-fit rounded-full px-3 py-1 text-[13px] font-bold tracking-[0.04em]", palette.label)}>
        {sectionLabel}
      </div>
      <div className={joinClassNames("admin-typography-subsection-title mt-1.5 font-['Manrope'] tracking-[-0.05em]", palette.value)}>
        {renderedValue}
      </div>
      <div className="admin-typography-data-note mt-2 text-slate-600">{note}</div>
      <div className="mt-3 rounded-[18px] bg-white/78 px-3.5 py-3 shadow-sm">
        <div className={joinClassNames("flex items-center gap-2 text-[13px] font-bold", palette.sectionTitle)}>
          <CheckCircle2 size={12} className={palette.icon} />
          {detailTitle}
        </div>
        <div className="admin-typography-mini-value mt-1.5 text-slate-950">{renderedDetailValue}</div>
        <div className="admin-typography-data-note mt-1.5 text-slate-600">{detailNote}</div>
      </div>
      <div className={joinClassNames("mt-3 text-[13px] font-semibold", palette.footerTitle)}>{title}</div>
    </div>
  );
}

export default function AdminDashboardPage() {
  const navigate = useNavigate();
  const createDashboardRequest = useLatestRequest();
  const createReportDetailRequest = useLatestRequest();
  const dashboardCache = useAdminStaleCache<DashboardCachePayload>("admin-dashboard:overview");
  const dashboardCacheRef = useRef<DashboardCachePayload | null>(dashboardCache.cached);

  const [loading, setLoading] = useState(() => !dashboardCache.hasCache);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [workbench, setWorkbench] = useState<WorkbenchSummary | null>(() => dashboardCache.cached?.workbench ?? null);
  const [userSummary, setUserSummary] = useState<UserSummaryResponse | null>(() => dashboardCache.cached?.userSummary ?? null);
  const [enterpriseOverview, setEnterpriseOverview] = useState<EnterpriseTaskOpsOverviewPayload | null>(() => dashboardCache.cached?.enterpriseOverview ?? null);
  const [mentorOverview, setMentorOverview] = useState<MentorOpsOverviewPayload | null>(() => dashboardCache.cached?.mentorOverview ?? null);
  const [notificationOverview, setNotificationOverview] = useState<NotificationOpsOverviewPayload | null>(() => dashboardCache.cached?.notificationOverview ?? null);
  const [skillOverview, setSkillOverview] = useState<SkillOpsOverviewPayload | null>(() => dashboardCache.cached?.skillOverview ?? null);
  const [consoleSnapshot, setConsoleSnapshot] = useState<ConsoleSnapshotPayload | null>(() => dashboardCache.cached?.consoleSnapshot ?? null);
  const [routes, setRoutes] = useState<RouteItem[]>(() => dashboardCache.cached?.routes ?? []);
  const [templates, setTemplates] = useState<PromptTemplateItem[]>(() => dashboardCache.cached?.templates ?? []);
  const [costDashboard, setCostDashboard] = useState<CostDashboardPayload | null>(() => dashboardCache.cached?.costDashboard ?? null);
  const [sceneMetricsPayload, setSceneMetricsPayload] = useState<ApplicationSceneMetricsPayload | null>(() => dashboardCache.cached?.sceneMetricsPayload ?? null);
  const [pendingReports, setPendingReports] = useState<ReportItem[]>(() => dashboardCache.cached?.pendingReports ?? []);

  const [activeReport, setActiveReport] = useState<ReportItem | null>(null);
  const [reportDetail, setReportDetail] = useState<ReportDetailResponse | null>(null);
  const [reportDetailLoading, setReportDetailLoading] = useState(false);
  const [reportDetailError, setReportDetailError] = useState<string | null>(null);

  const loadDashboard = useCallback(async (silent = false) => {
    const request = createDashboardRequest();
    if (silent) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    setError(null);

    try {
      // 管理员首页聚合多个运营域，单域失败不阻断其他域展示。
      const [
        workbenchResult,
        userSummaryResult,
        enterpriseResult,
        mentorResult,
        notificationResult,
        skillResult,
        consoleSnapshotResult,
        routesResult,
        templatesResult,
        costResult,
        sceneMetricsResult,
        reportsResult,
      ] = await Promise.allSettled([
        apiRequest<WorkbenchSummary>("/admin/dashboard/workbench", { signal: request.signal }),
        apiRequest<UserSummaryResponse>("/admin/users/summary", { signal: request.signal }),
        apiRequest<EnterpriseTaskOpsOverviewPayload>("/admin/enterprise/tasks/overview", { signal: request.signal }),
        apiRequest<MentorOpsOverviewPayload>("/admin/mentors/operations/overview", { signal: request.signal }),
        apiRequest<NotificationOpsOverviewPayload>("/admin/notifications/overview", { signal: request.signal }),
        apiRequest<SkillOpsOverviewPayload>("/admin/skills/overview", { signal: request.signal }),
        apiRequest<ConsoleSnapshotPayload>("/admin/system/console-snapshot", { signal: request.signal }),
        apiRequest<{ records: RouteItem[] }>("/admin/ai/routes", { signal: request.signal }),
        apiRequest<{ records: PromptTemplateItem[] }>("/admin/ai/prompt-templates", { signal: request.signal }),
        apiRequest<CostDashboardPayload>(`/admin/ai/cost-dashboard${buildQuery({ period: "week" })}`, { signal: request.signal }),
        apiRequest<ApplicationSceneMetricsPayload>(
          `/admin/ai/application-scene-metrics${buildQuery({ days: 7 })}`,
          { signal: request.signal },
        ),
        apiRequest<ReportListResponse>(
          `/admin/content/reports${buildQuery({ page: 1, size: 5, status: "PENDING" })}`,
          { signal: request.signal },
        ),
      ]);

      if (!request.isCurrent()) {
        return;
      }

      const failures: string[] = [];
      const readFailure = (result: PromiseSettledResult<unknown>, fallback: string) => {
        if (result.status !== "rejected") {
          return;
        }
        if (isAbortError(result.reason)) {
          return;
        }
        const apiError = result.reason as ApiClientError;
        failures.push(apiError.message || fallback);
      };

      if (workbenchResult.status === "fulfilled") {
        setWorkbench(workbenchResult.value);
      } else {
        readFailure(workbenchResult, "工作台摘要加载失败");
      }

      if (userSummaryResult.status === "fulfilled") {
        setUserSummary(userSummaryResult.value);
      } else {
        readFailure(userSummaryResult, "用户概览加载失败");
      }

      if (enterpriseResult.status === "fulfilled") {
        setEnterpriseOverview(enterpriseResult.value);
      } else {
        readFailure(enterpriseResult, "企业任务概览加载失败");
      }

      if (mentorResult.status === "fulfilled") {
        setMentorOverview(mentorResult.value);
      } else {
        readFailure(mentorResult, "导师经营概览加载失败");
      }

      if (notificationResult.status === "fulfilled") {
        setNotificationOverview(notificationResult.value);
      } else {
        readFailure(notificationResult, "通知运营概览加载失败");
      }

      if (skillResult.status === "fulfilled") {
        setSkillOverview(skillResult.value);
      } else {
        readFailure(skillResult, "技能资源概览加载失败");
      }

      if (consoleSnapshotResult.status === "fulfilled") {
        setConsoleSnapshot(consoleSnapshotResult.value);
      } else {
        readFailure(consoleSnapshotResult, "运行配置快照加载失败");
      }

      if (routesResult.status === "fulfilled") {
        setRoutes(routesResult.value.records);
      } else {
        readFailure(routesResult, "AI 路由概览加载失败");
      }

      if (templatesResult.status === "fulfilled") {
        setTemplates(templatesResult.value.records);
      } else {
        readFailure(templatesResult, "AI 模板概览加载失败");
      }

      if (costResult.status === "fulfilled") {
        setCostDashboard(costResult.value);
      } else {
        readFailure(costResult, "AI 成本看板加载失败");
      }

      if (sceneMetricsResult.status === "fulfilled") {
        setSceneMetricsPayload(sceneMetricsResult.value);
      } else {
        readFailure(sceneMetricsResult, "AI 场景摘要加载失败");
      }

      if (reportsResult.status === "fulfilled") {
        setPendingReports(reportsResult.value.records);
      } else {
        readFailure(reportsResult, "举报样本加载失败");
      }

      const nextCache: DashboardCachePayload = {
        // 成功域覆盖缓存，失败域沿用旧缓存，避免一次局部异常清空首页。
        workbench: workbenchResult.status === "fulfilled" ? workbenchResult.value : dashboardCacheRef.current?.workbench ?? null,
        userSummary: userSummaryResult.status === "fulfilled" ? userSummaryResult.value : dashboardCacheRef.current?.userSummary ?? null,
        enterpriseOverview: enterpriseResult.status === "fulfilled" ? enterpriseResult.value : dashboardCacheRef.current?.enterpriseOverview ?? null,
        mentorOverview: mentorResult.status === "fulfilled" ? mentorResult.value : dashboardCacheRef.current?.mentorOverview ?? null,
        notificationOverview: notificationResult.status === "fulfilled" ? notificationResult.value : dashboardCacheRef.current?.notificationOverview ?? null,
        skillOverview: skillResult.status === "fulfilled" ? skillResult.value : dashboardCacheRef.current?.skillOverview ?? null,
        consoleSnapshot: consoleSnapshotResult.status === "fulfilled" ? consoleSnapshotResult.value : dashboardCacheRef.current?.consoleSnapshot ?? null,
        routes: routesResult.status === "fulfilled" ? routesResult.value.records : dashboardCacheRef.current?.routes ?? [],
        templates: templatesResult.status === "fulfilled" ? templatesResult.value.records : dashboardCacheRef.current?.templates ?? [],
        costDashboard: costResult.status === "fulfilled" ? costResult.value : dashboardCacheRef.current?.costDashboard ?? null,
        sceneMetricsPayload: sceneMetricsResult.status === "fulfilled" ? sceneMetricsResult.value : dashboardCacheRef.current?.sceneMetricsPayload ?? null,
        pendingReports: reportsResult.status === "fulfilled" ? reportsResult.value.records : dashboardCacheRef.current?.pendingReports ?? [],
      };
      dashboardCacheRef.current = nextCache;
      dashboardCache.write(nextCache);

      const nextError = failures.length > 0 ? failures.join("；") : null;
      setError(nextError);
      if (silent && nextError) {
        message.warning(nextError);
      }
    } finally {
      if (request.isCurrent()) {
        setLoading(false);
        setRefreshing(false);
      }
    }
  }, [createDashboardRequest, dashboardCache]);

  const handleOpenReportDetail = useCallback(async (report: ReportItem) => {
    const request = createReportDetailRequest();
    setActiveReport(report);
    setReportDetail(null);
    setReportDetailError(null);
    setReportDetailLoading(true);

    try {
      // 首页只取举报样本，展开弹层时再按 reportId 加载完整详情。
      const response = await apiRequest<ReportDetailResponse>(`/admin/content/reports/${report.reportId}`, {
        signal: request.signal,
      });
      if (!request.isCurrent()) {
        return;
      }
      setReportDetail(response);
    } catch (requestError) {
      if (!request.isCurrent() || isAbortError(requestError)) {
        return;
      }
      const apiError = requestError as ApiClientError;
      setReportDetailError(apiError.message || "举报详情加载失败");
    } finally {
      if (request.isCurrent()) {
        setReportDetailLoading(false);
      }
    }
  }, [createReportDetailRequest]);

  const handleCloseReportDetail = useCallback(() => {
    setActiveReport(null);
    setReportDetail(null);
    setReportDetailError(null);
    setReportDetailLoading(false);
  }, []);

  useEffect(() => {
    if (!dashboardCache.cached) {
      return;
    }

    // stale cache 先恢复首页所有域，后台请求完成后再按域覆盖。
    dashboardCacheRef.current = dashboardCache.cached;
    setWorkbench(dashboardCache.cached.workbench);
    setUserSummary(dashboardCache.cached.userSummary);
    setEnterpriseOverview(dashboardCache.cached.enterpriseOverview);
    setMentorOverview(dashboardCache.cached.mentorOverview);
    setNotificationOverview(dashboardCache.cached.notificationOverview);
    setSkillOverview(dashboardCache.cached.skillOverview);
    setConsoleSnapshot(dashboardCache.cached.consoleSnapshot);
    setRoutes(dashboardCache.cached.routes);
    setTemplates(dashboardCache.cached.templates);
    setCostDashboard(dashboardCache.cached.costDashboard);
    setSceneMetricsPayload(dashboardCache.cached.sceneMetricsPayload);
    setPendingReports(dashboardCache.cached.pendingReports);
  }, [dashboardCache.cached]);

  useEffect(() => {
    void loadDashboard(dashboardCache.hasCache);
  }, [dashboardCache.hasCache, loadDashboard]);

  const applicationSceneMetrics = sceneMetricsPayload?.records ?? [];
  const metricsWindowDays = sceneMetricsPayload?.days ?? 7;

  const sceneCatalogMap = useMemo(
    () => new Map(adminAiChannelCatalog.map((item) => [buildSceneMetricKey(item.taskType, item.sceneCode), item])),
    [],
  );

  const promptTemplateStatusMap = useMemo(() => {
    return new Map(
      templates.map((item) => [`${item.taskType}::${item.templateName}`, item.status]),
    );
  }, [templates]);

  const configuredRoutes = consoleSnapshot?.aiChannels.totalRoutes ?? routes.length;
  const enabledRoutes = consoleSnapshot?.aiChannels.enabledRoutes ?? routes.filter((item) => item.enabled).length;
  const activePromptTemplates = consoleSnapshot?.aiChannels.activePromptTemplates
    ?? templates.filter((item) => item.status === "ACTIVE").length;
  const routeCoveragePercent = configuredRoutes > 0 ? (enabledRoutes / configuredRoutes) * 100 : 0;

  const aiSummary = useMemo(() => {
    // 以 AI 场景目录为主，反查已配置路由和 ACTIVE 模板，得到配置覆盖率。
    const matchedChannels = adminAiChannelCatalog.map((channel) => {
      const matchedRoutes = routes
        .filter(
          (route) =>
            route.taskType === channel.taskType &&
            normalizeSceneCode(route.sceneCode) === normalizeSceneCode(channel.sceneCode),
        )
        .sort(sortRoutes);
      const primaryRoute = matchedRoutes[0] ?? null;
      const activeTemplate = primaryRoute?.promptTemplateName
        ? templates.find(
            (item) =>
              item.taskType === channel.taskType &&
              item.templateName === primaryRoute.promptTemplateName &&
              item.status === "ACTIVE",
          ) ?? null
        : null;

      return {
        primaryRoute,
        activeTemplate,
      };
    });

    const configuredChannels = matchedChannels.filter((item) => item.primaryRoute).length;
    const enabledChannels = matchedChannels.filter((item) => item.primaryRoute?.enabled).length;
    const missingTemplateChannels = matchedChannels.filter(
      (item) => item.primaryRoute?.promptTemplateName && !item.activeTemplate,
    ).length;
    const asyncCapableChannels = matchedChannels.filter(
      (item) => item.primaryRoute?.executionMode && item.primaryRoute.executionMode !== "SYNC_BLOCKING",
    ).length;

    return {
      totalChannels: adminAiChannelCatalog.length,
      configuredChannels,
      enabledChannels,
      missingTemplateChannels,
      asyncCapableChannels,
    };
  }, [routes, templates]);

  const runtimeSummary = useMemo(() => {
    const featureFlags = consoleSnapshot?.featureFlags ?? [];
    const runtimeSettings = consoleSnapshot?.runtimeSettings;
    const moderationPolicies = consoleSnapshot?.moderationPolicies;
    const aiChannels = consoleSnapshot?.aiChannels;

    return {
      overriddenFlagCount: featureFlags.filter((item) => item.overridden).length,
      totalFlags: featureFlags.length,
      enabledGuardrailCount: moderationPolicies
        ? Number(moderationPolicies.aiInputEnabled) + Number(moderationPolicies.aiOutputEnabled) + Number(moderationPolicies.communityStrictReviewEnabled)
        : 0,
      enabledObservabilityCount: runtimeSettings
        ? Number(runtimeSettings.debugModeEnabled) + Number(runtimeSettings.aiRequestLogEnabled)
        : 0,
      autoHideReportThreshold: moderationPolicies?.autoHideReportThreshold ?? 0,
      enabledProviders: aiChannels?.enabledProviders ?? 0,
      totalProviders: aiChannels?.totalProviders ?? 0,
      totalRoutes: aiChannels?.totalRoutes ?? configuredRoutes,
      enabledRoutes: aiChannels?.enabledRoutes ?? enabledRoutes,
      activePromptTemplates: aiChannels?.activePromptTemplates ?? activePromptTemplates,
      totalPromptTemplates: aiChannels?.totalPromptTemplates ?? templates.length,
      generatedAt: consoleSnapshot?.generatedAt ?? null,
    };
  }, [activePromptTemplates, configuredRoutes, consoleSnapshot, enabledRoutes, templates.length]);

  const notificationSummary = useMemo(() => {
    const pendingJobCount = notificationOverview?.pendingJobCount ?? 0;
    const retryJobCount = notificationOverview?.retryJobCount ?? 0;
    const deadJobCount = notificationOverview?.deadJobCount ?? 0;
    const channelGapCount = notificationOverview
      ? Number(!notificationOverview.emailReady) + Number(!notificationOverview.websocketReady)
      : 0;

    return {
      pendingJobCount,
      retryJobCount,
      deadJobCount,
      channelGapCount,
      totalSignals: retryJobCount + deadJobCount + channelGapCount,
      lastAnnouncementAt: notificationOverview?.lastAnnouncementAt ?? null,
    };
  }, [notificationOverview]);

  const aiSceneSummary = useMemo(() => {
    // 场景指标先映射到前端目录，未登记场景保留出来方便后台补配置。
    const rows: DashboardSceneMetricRow[] = applicationSceneMetrics
      .map((item) => {
        const sceneKey = buildSceneMetricKey(item.taskType, item.sceneCode);
        const catalogItem = sceneCatalogMap.get(sceneKey);
        return {
          ...item,
          sceneKey,
          displayName: catalogItem?.displayName ?? item.sceneCode ?? item.taskType,
          ownerDomain: catalogItem?.ownerDomain ?? "未登记场景",
          frontEntry: catalogItem?.frontEntry ?? "—",
          summary: catalogItem?.summary ?? "当前场景尚未登记到后台目录。",
          registered: Boolean(catalogItem),
        };
      })
      .sort((left, right) => right.calls - left.calls || parseNumericValue(right.totalCost) - parseNumericValue(left.totalCost));

    const totalCalls = sceneMetricsPayload?.totalCalls ?? rows.reduce((sum, item) => sum + item.calls, 0);
    const totalCost = parseNumericValue(sceneMetricsPayload?.totalCost) || rows.reduce((sum, item) => sum + parseNumericValue(item.totalCost), 0);
    const activeSceneCount = rows.filter((item) => item.calls > 0).length;
    const topVolumeScene = rows[0] ?? null;
    const topCostScene = [...rows].sort(
      (left, right) => parseNumericValue(right.totalCost) - parseNumericValue(left.totalCost) || right.calls - left.calls,
    )[0] ?? null;
    const topRiskScene = [...rows]
      .filter((item) => item.calls >= 5 && parsePercentValue(item.successRate) < 95)
      .sort(
        (left, right) => parsePercentValue(left.successRate) - parsePercentValue(right.successRate) || right.calls - left.calls,
      )[0] ?? null;
    const averageSceneCost = rows.length > 0
      ? rows.reduce((sum, item) => sum + parseNumericValue(item.totalCost), 0) / rows.length
      : 0;
    const highCostSceneCount = averageSceneCost > 0
      ? rows.filter((item) => parseNumericValue(item.totalCost) >= averageSceneCost).length
      : 0;
    const successAnomalyCount = rows.filter((item) => parsePercentValue(item.successRate) < 95).length;

    return {
      rows,
      totalCalls,
      totalCost,
      activeSceneCount,
      topVolumeScene,
      topCostScene,
      topRiskScene,
      highCostSceneCount,
      successAnomalyCount,
    };
  }, [applicationSceneMetrics, sceneCatalogMap, sceneMetricsPayload]);

  const paymentOpsSummary = useMemo(() => {
    const afterSalesRequests = workbench?.afterSalesRequests ?? 0;
    const reconciliationReviewRequired = workbench?.reconciliationReviewRequired ?? 0;

    return {
      afterSalesRequests,
      reconciliationReviewRequired,
      totalPending: afterSalesRequests + reconciliationReviewRequired,
    };
  }, [workbench?.afterSalesRequests, workbench?.reconciliationReviewRequired]);

  const aiRiskSummary = useMemo(() => {
    const unhealthyProviders = workbench?.providerRuntime.unhealthyProviders ?? workbench?.providerRuntime.downProviders ?? 0;
    const missingTemplateChannels = aiSummary.missingTemplateChannels;
    const topRiskScene = aiSceneSummary.topRiskScene;
    const disabledConfiguredChannels = Math.max(aiSummary.configuredChannels - aiSummary.enabledChannels, 0);
    const observabilityGapCount = Math.max(2 - runtimeSummary.enabledObservabilityCount, 0);

    return {
      unhealthyProviders,
      missingTemplateChannels,
      disabledConfiguredChannels,
      observabilityGapCount,
      topRiskScene,
      totalSignals:
        unhealthyProviders +
        missingTemplateChannels +
        disabledConfiguredChannels +
        observabilityGapCount +
        (topRiskScene ? 1 : 0),
    };
  }, [
    aiSceneSummary.topRiskScene,
    aiSummary.configuredChannels,
    aiSummary.enabledChannels,
    aiSummary.missingTemplateChannels,
    runtimeSummary.enabledObservabilityCount,
    workbench?.providerRuntime.downProviders,
    workbench?.providerRuntime.unhealthyProviders,
  ]);

  const opsChainSummary = useMemo(() => {
    // 运营链风险把企业审核、导师履约、通知派发合成一组跨域待办信号。
    const enterpriseReviewPressureCount = (enterpriseOverview?.staleReviewTaskCount ?? 0) + (enterpriseOverview?.deadlinePressureTaskCount ?? 0);
    const mentorDeliveryRiskCount = (mentorOverview?.scheduleRiskMentorCount ?? 0) + (mentorOverview?.fulfillmentRiskMentorCount ?? 0);
    const mentorWithdrawalPressureCount = mentorOverview?.pendingWithdrawalMentorCount ?? 0;
    const notificationRetryPressureCount = (notificationOverview?.retryJobCount ?? 0) + (notificationOverview?.deadJobCount ?? 0);
    const notificationChannelGapCount = notificationOverview
      ? Number(!notificationOverview.emailReady) + Number(!notificationOverview.websocketReady)
      : 0;

    return {
      enterpriseReviewPressureCount,
      mentorDeliveryRiskCount,
      mentorWithdrawalPressureCount,
      notificationRetryPressureCount,
      notificationChannelGapCount,
      totalSignals:
        enterpriseReviewPressureCount +
        mentorDeliveryRiskCount +
        mentorWithdrawalPressureCount +
        notificationRetryPressureCount +
        notificationChannelGapCount,
    };
  }, [
    enterpriseOverview?.deadlinePressureTaskCount,
    enterpriseOverview?.staleReviewTaskCount,
    mentorOverview?.fulfillmentRiskMentorCount,
    mentorOverview?.pendingWithdrawalMentorCount,
    mentorOverview?.scheduleRiskMentorCount,
    notificationOverview,
  ]);

  const deliveryReadinessSummary = useMemo(() => {
    const enterpriseFulfillmentGapCount = (enterpriseOverview?.staleReviewTaskCount ?? 0) + (enterpriseOverview?.closedWithoutAcceptedTaskCount ?? 0);
    const mentorSupplyRiskCount = (mentorOverview?.scheduleRiskMentorCount ?? 0) + (mentorOverview?.fulfillmentRiskMentorCount ?? 0);
    const mentorPipelineCount = mentorOverview?.pendingApprovalCount ?? 0;
    const skillCoverageGapCount = skillOverview?.nodesWithoutResourceCount ?? 0;

    return {
      enterpriseFulfillmentGapCount,
      mentorSupplyRiskCount,
      mentorPipelineCount,
      skillCoverageGapCount,
      totalSignals: enterpriseFulfillmentGapCount + mentorSupplyRiskCount + skillCoverageGapCount,
    };
  }, [
    enterpriseOverview?.closedWithoutAcceptedTaskCount,
    enterpriseOverview?.staleReviewTaskCount,
    mentorOverview?.fulfillmentRiskMentorCount,
    mentorOverview?.pendingApprovalCount,
    mentorOverview?.scheduleRiskMentorCount,
    skillOverview?.nodesWithoutResourceCount,
  ]);

  const trustAndReachSummary = useMemo(() => {
    const pendingApprovalUsers = userSummary?.pendingApprovalUsers ?? 0;
    const suspendedUsers = userSummary?.suspendedUsers ?? 0;
    const moderationQueueCount = (workbench?.pendingReports ?? 0) + (workbench?.reviewQueue ?? 0);

    return {
      pendingApprovalUsers,
      suspendedUsers,
      moderationQueueCount,
      notificationFailureCount: notificationSummary.retryJobCount + notificationSummary.deadJobCount,
      notificationChannelGapCount: notificationSummary.channelGapCount,
      totalSignals:
        pendingApprovalUsers +
        suspendedUsers +
        moderationQueueCount +
        notificationSummary.retryJobCount +
        notificationSummary.deadJobCount +
        notificationSummary.channelGapCount,
    };
  }, [
    notificationSummary.channelGapCount,
    notificationSummary.deadJobCount,
    notificationSummary.retryJobCount,
    userSummary?.pendingApprovalUsers,
    userSummary?.suspendedUsers,
    workbench?.pendingReports,
    workbench?.reviewQueue,
  ]);

  const statCards = useMemo<StatCardProps[]>(() => {
    const highRiskTotal =
      (enterpriseOverview?.highRiskTaskCount ?? 0) +
      (mentorOverview?.riskyMentorCount ?? 0) +
      notificationSummary.deadJobCount +
      (workbench?.providerRuntime.unhealthyProviders ?? workbench?.providerRuntime.downProviders ?? 0);
    const topModel = costDashboard?.byModel?.[0] ?? null;

    return [
      {
        icon: AlertCircle,
        label: "待处理事项",
        value: formatCount(workbench?.totalPendingTasks ?? 0),
        trend: `举报 ${formatCount(workbench?.pendingReports ?? 0)}`,
        note: `认证 ${formatCount(userSummary?.pendingApprovalUsers ?? 0)} · 售后 ${formatCount(workbench?.afterSalesRequests ?? 0)} · 通知失败 ${formatCount(notificationSummary.deadJobCount)}`,
        trendTone: "primary",
      },
      {
        icon: ShieldAlert,
        label: "高风险事项",
        value: formatCount(highRiskTotal),
        trend: "需优先跟进",
        note: `企业高风险 ${formatCount(enterpriseOverview?.highRiskTaskCount ?? 0)} · 导师风险 ${formatCount(mentorOverview?.riskyMentorCount ?? 0)} · 服务商异常 ${formatCount(workbench?.providerRuntime.unhealthyProviders ?? workbench?.providerRuntime.downProviders ?? 0)}`,
        trendTone: "error",
        isCritical: true,
      },
      {
        icon: Share2,
        label: "AI 通道覆盖",
        value: formatPercent(enabledRoutes, configuredRoutes || 1, 1),
        trend: `${formatCount(aiSummary.enabledChannels)} / ${formatCount(aiSummary.totalChannels)}`,
        note: `待补齐能力配置 ${formatCount(aiSummary.missingTemplateChannels)} 个，可自动处理场景 ${formatCount(aiSummary.asyncCapableChannels)} 个。`,
        trendTone: "primary",
        progress: routeCoveragePercent,
      },
      {
        icon: Wallet,
        label: `近 ${metricsWindowDays} 日 AI 成本`,
        value: formatAiCurrency(costDashboard?.totalCost ?? 0),
        trend: `${formatCount(costDashboard?.totalCalls ?? 0)} 次调用`,
        note: topModel ? `当前模型投入最高 ${topModel.name} · ${formatAiCurrency(topModel.cost)}` : "当前还没有可用的模型成本样本。",
        trendTone: "tertiary",
      },
    ];
  }, [
    aiSummary.asyncCapableChannels,
    aiSummary.enabledChannels,
    aiSummary.missingTemplateChannels,
    aiSummary.totalChannels,
    configuredRoutes,
    costDashboard?.byModel,
    costDashboard?.totalCalls,
    costDashboard?.totalCost,
    enabledRoutes,
    enterpriseOverview?.highRiskTaskCount,
    mentorOverview?.riskyMentorCount,
    metricsWindowDays,
    notificationSummary.deadJobCount,
    routeCoveragePercent,
    userSummary?.pendingApprovalUsers,
    workbench?.afterSalesRequests,
    workbench?.pendingReports,
    workbench?.providerRuntime.downProviders,
    workbench?.providerRuntime.unhealthyProviders,
    workbench?.totalPendingTasks,
  ]);

  const pendingTaskItems = useMemo<PendingTaskItem[]>(() => [
      {
        key: "approval",
        label: "认证待处理",
        count: userSummary?.pendingApprovalUsers ?? 0,
        value: formatCount(userSummary?.pendingApprovalUsers ?? 0),
        note: "企业与导师主体准入仍待审核。",
        tone: "primary",
        to: "/admin/users/reviews",
      },
      {
        key: "reports",
        label: "待处理举报",
        count: workbench?.pendingReports ?? 0,
        value: formatCount(workbench?.pendingReports ?? 0),
        note: "社区举报样本等待人工处理。",
        tone: "secondary",
        to: "/admin/content?tab=reports",
      },
      {
        key: "after-sales",
        label: "售后待处理",
        count: workbench?.afterSalesRequests ?? 0,
        value: formatCount(workbench?.afterSalesRequests ?? 0),
        note: "咨询售后与退款申请待跟进。",
        tone: "secondary",
        to: "/admin/consult/orders?tab=after-sales",
      },
      {
        key: "notification-dead",
        label: "通知失败",
        count: notificationSummary.deadJobCount + notificationSummary.retryJobCount + notificationSummary.channelGapCount,
        value: formatCount(notificationSummary.deadJobCount),
        note: `重试 ${formatCount(notificationSummary.retryJobCount)}，通道缺口 ${formatCount(notificationSummary.channelGapCount)}。`,
        tone: notificationSummary.deadJobCount > 0 ? "error" : "primary",
        to: "/admin/notifications",
      },
      {
        key: "enterprise-risk",
        label: "任务风险",
        count: (enterpriseOverview?.highRiskTaskCount ?? 0) + (enterpriseOverview?.deadlinePressureTaskCount ?? 0),
        value: formatCount(enterpriseOverview?.highRiskTaskCount ?? 0),
        note: `临期 ${formatCount(enterpriseOverview?.deadlinePressureTaskCount ?? 0)}，提交积压 ${formatCount(enterpriseOverview?.staleReviewTaskCount ?? 0)}。`,
        tone: "error",
        to: "/admin/enterprise/tasks",
      },
      {
        key: "withdrawal",
        label: "提现审核",
        count: mentorOverview?.pendingWithdrawalMentorCount ?? 0,
        value: formatCount(mentorOverview?.pendingWithdrawalMentorCount ?? 0),
        note: `待打款金额 ${formatMoneyFen(mentorOverview?.pendingWithdrawalAmountFen ?? 0)}。`,
        tone: "tertiary",
        to: "/admin/mentors/operations",
      },
  ], [
    enterpriseOverview?.deadlinePressureTaskCount,
    enterpriseOverview?.highRiskTaskCount,
    enterpriseOverview?.staleReviewTaskCount,
    mentorOverview?.pendingWithdrawalAmountFen,
    mentorOverview?.pendingWithdrawalMentorCount,
    notificationSummary.channelGapCount,
    notificationSummary.deadJobCount,
    notificationSummary.retryJobCount,
    userSummary?.pendingApprovalUsers,
    workbench?.afterSalesRequests,
    workbench?.pendingReports,
  ]);

  const prioritizedTaskItems = useMemo(
    () => [...pendingTaskItems].sort((left, right) => right.count - left.count || left.label.localeCompare(right.label, "zh-CN")),
    [pendingTaskItems],
  );

  const domainHealthItems = useMemo<DomainHealthItem[]>(() => {
    const items = [
      {
        key: "ai",
        label: "AI 运行",
        signals: aiRiskSummary.totalSignals,
        note: aiRiskSummary.topRiskScene
          ? `${aiRiskSummary.topRiskScene.displayName} 成功率 ${aiRiskSummary.topRiskScene.successRate}，模板缺口 ${formatCount(aiRiskSummary.missingTemplateChannels)} 个。`
          : `服务商异常 ${formatCount(aiRiskSummary.unhealthyProviders)}，观测缺口 ${formatCount(aiRiskSummary.observabilityGapCount)}。`,
        to: "/admin/ai/gateway",
      },
      {
        key: "ops",
        label: "交付链路",
        signals: opsChainSummary.totalSignals,
        note: `任务积压 ${formatCount(opsChainSummary.enterpriseReviewPressureCount)}，导师交付风险 ${formatCount(opsChainSummary.mentorDeliveryRiskCount)}。`,
        to: "/admin/enterprise/tasks",
      },
      {
        key: "trust",
        label: "认证与触达",
        signals: trustAndReachSummary.totalSignals,
        note: `待认证 ${formatCount(trustAndReachSummary.pendingApprovalUsers)}，通知失败 ${formatCount(trustAndReachSummary.notificationFailureCount)}。`,
        to: "/admin/users/reviews",
      },
      {
        key: "supply",
        label: "供需准备",
        signals: deliveryReadinessSummary.totalSignals,
        note: `交付缺口 ${formatCount(deliveryReadinessSummary.enterpriseFulfillmentGapCount)}，资源缺口 ${formatCount(deliveryReadinessSummary.skillCoverageGapCount)}。`,
        to: "/admin/skills",
      },
    ];

    return items.map((item) => {
      const tone = resolveDomainHealthTone(item.signals);
      return {
        ...item,
        tone,
        status: tone === "warning" ? "需介入" : tone === "attention" ? "需关注" : "稳定",
      };
    });
  }, [
    aiRiskSummary.missingTemplateChannels,
    aiRiskSummary.observabilityGapCount,
    aiRiskSummary.topRiskScene,
    aiRiskSummary.totalSignals,
    aiRiskSummary.unhealthyProviders,
    deliveryReadinessSummary.enterpriseFulfillmentGapCount,
    deliveryReadinessSummary.skillCoverageGapCount,
    deliveryReadinessSummary.totalSignals,
    opsChainSummary.enterpriseReviewPressureCount,
    opsChainSummary.mentorDeliveryRiskCount,
    opsChainSummary.totalSignals,
    trustAndReachSummary.notificationFailureCount,
    trustAndReachSummary.pendingApprovalUsers,
    trustAndReachSummary.totalSignals,
  ]);

  const maxPendingTaskCount = useMemo(
    () => Math.max(...prioritizedTaskItems.map((item) => item.count), 1),
    [prioritizedTaskItems],
  );

  const maxDomainSignalCount = useMemo(
    () => Math.max(...domainHealthItems.map((item) => item.signals), 1),
    [domainHealthItems],
  );

  const systemHealthSummary = useMemo(() => {
    const totalSignals = domainHealthItems.reduce((sum, item) => sum + item.signals, 0);
    const topTask = prioritizedTaskItems[0] ?? null;
    const topDomain = [...domainHealthItems].sort((left, right) => right.signals - left.signals)[0] ?? null;
    const tone: DomainHealthTone = totalSignals >= 24 ? "warning" : totalSignals >= 8 ? "attention" : "stable";

    return {
      totalSignals,
      topTask,
      topDomain,
      tone,
      label: tone === "warning" ? "系统需介入" : tone === "attention" ? "系统需关注" : "系统稳定",
      note: tone === "warning"
        ? "当前跨域信号偏高，建议先处理队列积压和高风险域。"
        : tone === "attention"
          ? "当前存在局部异常，优先确认最靠前的处理项。"
          : "当前主链路整体稳定，可转入运营和结构巡检。",
    };
  }, [domainHealthItems, prioritizedTaskItems]);

  const userDomainStats = useMemo(() => [
    { label: "平台用户", value: formatCount(userSummary?.totalUsers ?? 0) },
    {
      label: "近 7 日活跃",
      value: formatCount(userSummary?.activeUsers7d ?? 0),
      trend: formatPercent(userSummary?.activeUsers7d ?? 0, userSummary?.totalUsers || 1, 1),
    },
    {
      label: "近 7 日新增",
      value: formatCount(userSummary?.newUsers7d ?? 0),
      trend: formatPercent(userSummary?.newUsers7d ?? 0, userSummary?.totalUsers || 1, 1),
    },
    {
      label: "待认证主体",
      value: formatCount(userSummary?.pendingApprovalUsers ?? 0),
      trend: userSummary?.pendingApprovalUsers ? `${formatCount(userSummary?.enterpriseUsers ?? 0)} 家企业在运营` : undefined,
    },
  ], [
    userSummary?.activeUsers7d,
    userSummary?.enterpriseUsers,
    userSummary?.newUsers7d,
    userSummary?.pendingApprovalUsers,
    userSummary?.totalUsers,
  ]);

  const governanceRuntimeStats = useMemo(() => [
    {
      label: "智能能力覆盖",
      value: formatPercent(enabledRoutes, configuredRoutes || 1, 1),
      dot: "bg-[#4647d3]",
    },
    {
      label: "已启用能力",
      value: `${formatCount(runtimeSummary.activePromptTemplates)} / ${formatCount(runtimeSummary.totalPromptTemplates)}`,
      dot: "bg-[#f8a010]",
    },
    {
      label: "治理保护",
      value: `${formatCount(runtimeSummary.enabledGuardrailCount)} / 3`,
      dot: "bg-[#006947]",
    },
    {
      label: "服务健康",
      value: `${formatCount(workbench?.providerRuntime.healthyProviders ?? 0)} / ${formatCount(workbench?.providerRuntime.totalProviders ?? 0)}`,
      dot: "bg-sky-500",
    },
    {
      label: "触达异常",
      value: notificationSummary.totalSignals > 0 ? formatCount(notificationSummary.totalSignals) : "稳定",
      dot: notificationSummary.totalSignals > 0 ? "bg-[#b41340]" : "bg-[#006947]",
    },
  ], [
    configuredRoutes,
    enabledRoutes,
    notificationSummary.totalSignals,
    runtimeSummary.activePromptTemplates,
    runtimeSummary.enabledGuardrailCount,
    runtimeSummary.totalPromptTemplates,
    workbench?.providerRuntime.healthyProviders,
    workbench?.providerRuntime.totalProviders,
  ]);

  const businessMatrixCards = useMemo<DomainMatrixCardProps[]>(() => [
    {
      icon: BriefcaseBusiness,
      title: "企业任务",
      headline: `${formatCount(enterpriseOverview?.riskyTaskCount ?? 0)} 需介入`,
      note: "聚焦招募中任务、提交流转和临期压力，可直接进入企业任务治理继续处理。",
      lines: [
        { text: `招募中 ${formatCount(enterpriseOverview?.openTaskCount ?? 0)} 个`, tone: "primary" },
        { text: `待审提交 ${formatCount(enterpriseOverview?.staleReviewTaskCount ?? 0)} 个`, tone: "warning" },
        { text: `关闭未采纳 ${formatCount(enterpriseOverview?.closedWithoutAcceptedTaskCount ?? 0)} 个`, tone: "danger" },
      ],
      tone: "amber",
      action: (
        <Button className="!h-9 !rounded-xl !border-slate-200 !px-4" onClick={() => navigate("/admin/enterprise/tasks")}>
          进入企业任务治理
        </Button>
      ),
    },
    {
      icon: Users,
      title: "导师经营",
      headline: `${formatCount(mentorOverview?.riskyMentorCount ?? 0)} 风险导师`,
      note: "从履约、排期和提现三条线查看导师经营压力，可继续进入导师经营治理查看详情。",
      lines: [
        { text: `待打款导师 ${formatCount(mentorOverview?.pendingWithdrawalMentorCount ?? 0)} 位`, tone: "warning" },
        { text: `待打款金额 ${formatMoneyFen(mentorOverview?.pendingWithdrawalAmountFen ?? 0)}`, tone: "primary" },
        { text: `排期风险 ${formatCount(mentorOverview?.scheduleRiskMentorCount ?? 0)} 位 · 履约风险 ${formatCount(mentorOverview?.fulfillmentRiskMentorCount ?? 0)} 位`, tone: "danger" },
      ],
      tone: "indigo",
      action: (
        <Button className="!h-9 !rounded-xl !border-slate-200 !px-4" onClick={() => navigate("/admin/mentors/operations")}>
          进入导师经营治理
        </Button>
      ),
    },
    {
      icon: MessageSquareMore,
      title: "通知运营",
      headline: `${formatCount(notificationOverview?.deadJobCount ?? 0)} 死信`,
      note: "公告、补发和通道可用性都已接到通知运营，可直接进入对应页面处理。",
      lines: [
        { text: `等待发送 ${formatCount(notificationOverview?.pendingJobCount ?? 0)} 个`, tone: "primary" },
        { text: `等待重试 ${formatCount(notificationOverview?.retryJobCount ?? 0)} 个`, tone: "warning" },
        { text: notificationOverview?.emailReady ? "邮件通道已就绪" : "邮件通道待修复", tone: notificationOverview?.emailReady ? "positive" : "danger" },
      ],
      tone: "rose",
      action: (
        <Button className="!h-9 !rounded-xl !border-slate-200 !px-4" onClick={() => navigate("/admin/notifications")}>
          进入通知运营
        </Button>
      ),
    },
    {
      icon: BookOpen,
      title: "技能资源",
      headline: `${formatCount(skillOverview?.nodesWithoutResourceCount ?? 0)} 节点待补资源`,
      note: "技能树结构与资源覆盖已经接入技能资源治理，可直接进入继续维护。",
      lines: [
        { text: `总节点 ${formatCount(skillOverview?.totalNodeCount ?? 0)} 个`, tone: "primary" },
        { text: `末级节点 ${formatCount(skillOverview?.leafNodeCount ?? 0)} 个 · 关系 ${formatCount(skillOverview?.relationCount ?? 0)} 条`, tone: "neutral" },
        { text: `资源 ${formatCount(skillOverview?.totalResourceCount ?? 0)} 条`, tone: "positive" },
      ],
      tone: "emerald",
      action: (
        <Button className="!h-9 !rounded-xl !border-slate-200 !px-4" onClick={() => navigate("/admin/skills")}>
          进入技能资源治理
        </Button>
      ),
    },
  ], [
    enterpriseOverview?.closedWithoutAcceptedTaskCount,
    enterpriseOverview?.openTaskCount,
    enterpriseOverview?.riskyTaskCount,
    enterpriseOverview?.staleReviewTaskCount,
    mentorOverview?.fulfillmentRiskMentorCount,
    mentorOverview?.pendingWithdrawalAmountFen,
    mentorOverview?.pendingWithdrawalMentorCount,
    mentorOverview?.riskyMentorCount,
    mentorOverview?.scheduleRiskMentorCount,
    notificationOverview?.deadJobCount,
    notificationOverview?.emailReady,
    notificationOverview?.pendingJobCount,
    notificationOverview?.retryJobCount,
    skillOverview?.leafNodeCount,
    skillOverview?.nodesWithoutResourceCount,
    skillOverview?.relationCount,
    skillOverview?.totalNodeCount,
    skillOverview?.totalResourceCount,
    navigate,
  ]);

  const runtimeFocusCards = useMemo<RuntimeFocusCardProps[]>(() => [
    {
      title: "近期活跃场景",
      sectionLabel: "活跃场景",
      value: formatCount(aiSceneSummary.activeSceneCount),
      note: `近 ${metricsWindowDays} 日累计 ${formatCount(aiSceneSummary.totalCalls)} 次调用。`,
      detailTitle: "最活跃场景",
      detailValue: aiSceneSummary.topVolumeScene?.displayName ?? "暂无数据",
      detailNote: aiSceneSummary.topVolumeScene
        ? `${aiSceneSummary.topVolumeScene.ownerDomain} · 成功率 ${aiSceneSummary.topVolumeScene.successRate} · ${formatCount(aiSceneSummary.topVolumeScene.calls)} 次`
        : "当前还没有活跃场景样本。",
      tone: "primary",
    },
    {
      title: "成本观察",
      sectionLabel: "成本观察",
      value: formatCount(aiSceneSummary.highCostSceneCount),
      note: `近 ${metricsWindowDays} 日场景总投入 ${formatAiCurrency(aiSceneSummary.totalCost)}。`,
      detailTitle: "最高成本场景",
      detailValue: aiSceneSummary.topCostScene?.displayName ?? "暂无数据",
      detailNote: aiSceneSummary.topCostScene
        ? `${formatAiCurrency(aiSceneSummary.topCostScene.totalCost)} · ${aiSceneSummary.topCostScene.ownerDomain} · 入口 ${aiSceneSummary.topCostScene.frontEntry}`
        : "当前还没有足够的成本样本。",
      tone: "tertiary",
    },
    {
      title: "成功率异常",
      sectionLabel: "稳定性",
      value: formatCount(aiSceneSummary.successAnomalyCount),
      note: aiSceneSummary.topRiskScene
        ? `重点异常 ${aiSceneSummary.topRiskScene.displayName} · 最近调用 ${formatDateTime(aiSceneSummary.topRiskScene.lastCallAt)}`
        : "当前高频场景整体稳定，暂未发现明显异常。",
      detailTitle: "重点异常场景",
      detailValue: aiSceneSummary.topRiskScene?.displayName ?? "暂无明显异常",
      detailNote: aiSceneSummary.topRiskScene
        ? `成功率 ${aiSceneSummary.topRiskScene.successRate} · 平均延迟 ${aiSceneSummary.topRiskScene.avgLatencyMs} ms`
        : "继续关注模板、路由和服务商的联动波动。",
      tone: "error",
    },
  ], [
    aiSceneSummary.activeSceneCount,
    aiSceneSummary.highCostSceneCount,
    aiSceneSummary.successAnomalyCount,
    aiSceneSummary.topCostScene,
    aiSceneSummary.topRiskScene,
    aiSceneSummary.topVolumeScene,
    aiSceneSummary.totalCalls,
    aiSceneSummary.totalCost,
    metricsWindowDays,
  ]);

  const supplyDemandStatus = useMemo(() => {
    const openTasks = enterpriseOverview?.openTaskCount ?? 0;
    const approvedMentors = mentorOverview?.approvedMentorCount ?? 0;
    if (openTasks === 0) {
      return "稳定";
    }
    const ratio = approvedMentors / Math.max(openTasks, 1);
    if (ratio >= 1.3) {
      return "充足";
    }
    if (ratio >= 0.9) {
      return "平衡";
    }
    return "偏紧";
  }, [enterpriseOverview?.openTaskCount, mentorOverview?.approvedMentorCount]);

  const providerCostItems = costDashboard?.byProvider?.slice(0, 3) ?? [];
  const taskTypeCostItems = costDashboard?.byTaskType?.slice(0, 3) ?? [];
  const tierCostItems = costDashboard?.byTier?.slice(0, 3) ?? [];
  const topCostUsers = costDashboard?.topUsers?.slice(0, 3) ?? [];
  const topSceneRows = aiSceneSummary.rows.slice(0, 4);
  const providerCostMax = Math.max(...providerCostItems.map((item) => parseNumericValue(item.cost)), 1);
  const taskTypeCostMax = Math.max(...taskTypeCostItems.map((item) => parseNumericValue(item.cost)), 1);
  const tierCostMax = Math.max(...tierCostItems.map((item) => parseNumericValue(item.cost)), 1);
  const topCostUserMax = Math.max(...topCostUsers.map((item) => parseNumericValue(item.cost)), 1);

  const headerTimestamp = workbench?.generatedAt ?? consoleSnapshot?.generatedAt ?? sceneMetricsPayload?.records?.[0]?.lastCallAt ?? null;

  const reportTargetVisualMap: Record<
    string,
    {
      icon: LucideIcon;
      iconBg: string;
      iconColor: string;
    }
  > = {
    USER: {
      icon: Users,
      iconBg: "bg-[#4647d3]/10",
      iconColor: "text-[#4647d3]",
    },
    POST: {
      icon: BookOpen,
      iconBg: "bg-[#f8a010]/14",
      iconColor: "text-[#815100]",
    },
    COMMENT: {
      icon: MessageSquareMore,
      iconBg: "bg-[#69f6b8]/18",
      iconColor: "text-[#006947]",
    },
  };

  const reportTargetUserId = useMemo(() => {
    if (!reportDetail || reportDetail.targetType !== "USER" || !reportDetail.targetId) {
      return null;
    }
    const parsed = Number(reportDetail.targetId);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }, [reportDetail]);

  const reportModalStatus = getReportStatusMeta(reportDetail?.status ?? activeReport?.status);
  const reportModalPriority = getReportPriorityMeta(reportDetail?.reportCount ?? activeReport?.reportCount ?? 0);

  const refreshButtonLoading = loading || refreshing;

  return (
    <AdminPageFrame className="max-w-[1680px]">
      <AdminPageHeader
        sectionLabel="OVERVIEW"
        title="系统总览"
        description="跨域治理、业务快照与风险联动总览。"
        tone="indigo"
        actions={(
          <Button
            disabled={refreshButtonLoading}
            className="!h-11 !rounded-2xl !border-slate-200 !px-5 !shadow-none"
            onClick={() => void loadDashboard(true)}
          >
            <RefreshCcw size={18} className={refreshButtonLoading ? "mr-2 animate-spin" : "mr-2"} />
            刷新数据
          </Button>
        )}
      />

      <div className="flex flex-wrap items-center gap-2">
        <span className="admin-typography-chip rounded-full bg-white px-3 py-1 text-slate-400 shadow-sm">
          数据刷新于 {formatDateTime(headerTimestamp)}
        </span>
      </div>

      {error ? (
        <Alert
          type="warning"
          showIcon
          className="rounded-2xl"
          message="部分模块加载失败"
          description={error}
        />
      ) : null}

      <div className="space-y-8">
          <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-4">
            {statCards.map((item) => (
              <StatCard key={item.label} {...item} />
            ))}
          </div>

          <div className="grid grid-cols-1 gap-5 xl:grid-cols-[1.35fr_1.05fr]">
            <GlassPanel className="relative flex h-full flex-col overflow-hidden p-5">
              <div className="pointer-events-none absolute -right-2 -top-2 z-0 text-indigo-100/70">
                <Layers3 size={44} strokeWidth={1.3} />
              </div>
              <div className="relative z-10 flex flex-col gap-4 border-b border-slate-200/80 pb-4 lg:flex-row lg:items-start lg:justify-between">
                <div className="space-y-3">
                  <div className="admin-typography-data-label flex items-center gap-2 text-slate-400">
                    <div className="flex h-7 w-7 items-center justify-center rounded-xl bg-[#4647d3]/10 text-[#4647d3]">
                      <Layers3 size={14} />
                    </div>
                    系统总态
                  </div>
                  <div className="flex flex-wrap items-center gap-3">
                    <h3 className="font-['Manrope'] text-[2rem] font-black tracking-[-0.05em] text-slate-950">
                      {systemHealthSummary.label}
                    </h3>
                    <span className={joinClassNames("admin-typography-chip rounded-full px-3 py-1", getDomainHealthMeta(systemHealthSummary.tone).badge)}>
                      跨域信号 {formatCount(systemHealthSummary.totalSignals)}
                    </span>
                  </div>
                  <p className="max-w-3xl text-sm leading-6 text-slate-500">
                    {systemHealthSummary.note}
                    {systemHealthSummary.topTask ? ` 当前最高优先级是“${systemHealthSummary.topTask.label}”。` : ""}
                  </p>
                </div>
                <div className="grid gap-3 [grid-template-columns:repeat(4,minmax(max-content,1fr))]">
                  {[
                    { label: "待处理总量", value: formatCount(workbench?.totalPendingTasks ?? 0) },
                    { label: "高风险事项", value: statCards[1]?.value ?? formatCount(0) },
                    { label: "售后与对账", value: formatCount(paymentOpsSummary.totalPending) },
                    { label: "供需状态", value: supplyDemandStatus },
                  ].map((item) => (
                    <div key={item.label} className="min-w-0 rounded-2xl bg-slate-50 px-4 py-3.5">
                      <div className="admin-typography-data-label whitespace-nowrap text-slate-400">{item.label}</div>
                      <div className="admin-typography-mini-value mt-1.5 text-slate-950">{item.value}</div>
                    </div>
                  ))}
                </div>
              </div>

              <div className="relative z-10 mt-4">
                <div className="grid gap-3 md:grid-cols-2 2xl:grid-cols-3">
                  {prioritizedTaskItems.map((item) => {
                    const palette = item.tone === "error"
                      ? { accent: "bg-[#f74b6d]", text: "text-[#b41340]" }
                      : item.tone === "tertiary"
                        ? { accent: "bg-[#f8a010]", text: "text-[#815100]" }
                        : item.tone === "secondary"
                          ? { accent: "bg-[#10b981]", text: "text-[#006947]" }
                          : { accent: "bg-[#4647d3]", text: "text-[#4647d3]" };
                    const progress = item.count > 0 ? clampPercent((item.count / maxPendingTaskCount) * 100) : 6;

                    return (
                      <div key={item.key} className="flex h-full flex-col rounded-[20px] border border-slate-200/80 bg-white px-4 py-3.5 shadow-sm">
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0">
                            <div className="flex items-center gap-3">
                              <span className={joinClassNames("h-2.5 w-2.5 rounded-full", palette.accent)} />
                              <div className="text-sm font-semibold text-slate-950">{item.label}</div>
                              <span className={joinClassNames("text-sm font-black", palette.text)}>{item.value}</span>
                            </div>
                            <div className="admin-typography-data-note mt-1.5 text-slate-500">{item.note}</div>
                          </div>
                          <Button className="!h-9 !rounded-xl !border-slate-200 !px-3.5" onClick={() => navigate(item.to)}>
                            去处理
                          </Button>
                        </div>
                        <div className="mt-auto pt-3">
                          <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
                          <div className={joinClassNames("h-full rounded-full", palette.accent)} style={{ width: `${progress}%` }} />
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </GlassPanel>

            <GlassPanel className="relative overflow-hidden p-5">
              <div className="pointer-events-none absolute -right-2 -top-2 z-0 text-emerald-100/70">
                <Activity size={44} strokeWidth={1.3} />
              </div>
              <div className="relative z-10 flex items-center justify-between gap-3">
                <div>
                  <div className="admin-typography-data-label flex items-center gap-2 text-slate-400">
                    <div className="flex h-7 w-7 items-center justify-center rounded-xl bg-emerald-100 text-emerald-600">
                      <Activity size={14} />
                    </div>
                    重点域状态
                  </div>
                  <h3 className="mt-2 font-['Manrope'] text-xl font-black text-slate-950">四域健康状态</h3>
                </div>
                <span className={joinClassNames("admin-typography-chip rounded-full px-3 py-1", getDomainHealthMeta(systemHealthSummary.tone).badge)}>
                  {systemHealthSummary.label}
                </span>
              </div>

              <div className="relative z-10 mt-4 grid flex-1 auto-rows-fr content-stretch gap-3 sm:grid-cols-2">
                {domainHealthItems.map((item) => {
                  const palette = getDomainHealthMeta(item.tone);
                  const progress = item.signals > 0 ? clampPercent((item.signals / maxDomainSignalCount) * 100) : 6;

                  return (
                    <div key={item.key} className="flex h-full flex-col rounded-[20px] border border-slate-200/80 bg-white px-4 py-3.5 shadow-sm">
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="flex items-center gap-3">
                            <span className={joinClassNames("admin-typography-chip flex h-8 w-8 items-center justify-center rounded-2xl font-black", palette.icon)}>
                              {formatCount(item.signals)}
                            </span>
                            <div>
                              <div className="text-sm font-semibold text-slate-950">{item.label}</div>
                              <div className="admin-typography-caption mt-1 text-slate-500">{item.note}</div>
                            </div>
                          </div>
                        </div>
                        <span className={joinClassNames("admin-typography-chip rounded-full px-3 py-1", palette.badge)}>
                          {item.status}
                        </span>
                      </div>
                      <div className="mt-auto pt-3">
                        <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
                          <div
                            className={joinClassNames("h-full rounded-full bg-gradient-to-r", palette.bar)}
                            style={{ width: `${progress}%` }}
                          />
                        </div>
                        <Button className="mt-3 !h-8 !rounded-xl !border-slate-200 !px-3.5" onClick={() => navigate(item.to)}>
                          查看工作台
                        </Button>
                      </div>
                    </div>
                  );
                })}
              </div>
            </GlassPanel>

          </div>

          <section>
            <SectionTitle
              title="治理域快照"
              subtitle="用户、认证、AI 运行与平台治理的核心状态。"
              icon={Layers3}
            />
            <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
              <SnapshotPanel
                tone="indigo"
                title="用户与认证域"
                subtitle="账户规模、活跃走势与认证节奏"
                icon={Users}
                footer={(
                  <div className="grid auto-rows-fr gap-3 md:grid-cols-3">
                    {[
                      {
                        key: "enterprise",
                        icon: BriefcaseBusiness,
                        label: "企业账号",
                        value: `${formatCount(userSummary?.enterpriseUsers ?? 0)} 个`,
                        note: "已接入企业主体",
                        iconClassName: "bg-indigo-100 text-indigo-600",
                        accentClassName: "from-indigo-400/20 to-transparent",
                        labelClassName: "text-indigo-500",
                      },
                      {
                        key: "premium",
                        icon: Wallet,
                        label: "高级会员",
                        value: `${formatCount(userSummary?.premiumUsers ?? 0)} 人`,
                        note: "当前付费层级用户",
                        iconClassName: "bg-amber-100 text-amber-600",
                        accentClassName: "from-amber-400/20 to-transparent",
                        labelClassName: "text-amber-500",
                      },
                      {
                        key: "risk",
                        icon: ShieldAlert,
                        label: "风控账号",
                        value: `${formatCount(userSummary?.suspendedUsers ?? 0)} 人`,
                        note: "需继续观察与处置",
                        iconClassName: "bg-rose-100 text-rose-600",
                        accentClassName: "from-rose-400/20 to-transparent",
                        labelClassName: "text-rose-500",
                      },
                    ].map((item) => {
                      const Icon = item.icon;

                      return (
                        <div
                          key={item.key}
                          className="relative flex h-full min-h-[112px] overflow-hidden rounded-[20px] border border-white/80 bg-white px-4 py-4 shadow-[0_8px_18px_rgba(15,23,42,0.035)]"
                        >
                          <div className={joinClassNames("pointer-events-none absolute inset-x-0 top-0 h-10 bg-gradient-to-b", item.accentClassName)} />
                          <div className="relative z-10 flex w-full items-start gap-3">
                            <div className={joinClassNames("mt-0.5 flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl", item.iconClassName)}>
                              <Icon size={17} />
                            </div>
                            <div className="flex min-h-[72px] min-w-0 flex-1 flex-col justify-between">
                              <div className={joinClassNames("admin-typography-data-label", item.labelClassName)}>{item.label}</div>
                              <div className="mt-2 font-['Manrope'] text-[1.35rem] font-black leading-none text-slate-950">{item.value}</div>
                              <div className="admin-typography-data-note mt-auto pt-2 text-slate-500">{item.note}</div>
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              >
                <div className="grid grid-cols-2 gap-4">
                  {userDomainStats.map((item) => (
                    <div key={item.label} className="space-y-1.5">
                      <p className="admin-typography-data-label text-slate-500">{item.label}</p>
                      <p className="font-['Manrope'] text-[1.75rem] font-black leading-none text-[#2c2f31]">{item.value}</p>
                      {item.trend ? (
                        <div className="flex items-center gap-1.5 text-[13px] font-bold text-[#006947]">
                          <TrendingUp size={13} />
                          {item.trend}
                        </div>
                      ) : null}
                    </div>
                  ))}
                </div>
              </SnapshotPanel>

              <SnapshotPanel
                tone="slate"
                title="AI 与治理运行"
                subtitle="能力可用、服务健康与治理保护"
                icon={Share2}
              >
                <div className="space-y-4">
                  {governanceRuntimeStats.map((item) => (
                    <div key={item.label} className="flex items-center justify-between gap-4">
                      <div className="flex items-center gap-3">
                        <div className={joinClassNames("h-2 w-2 rounded-full", item.dot)} />
                        <span className="text-[15px] font-semibold text-[#2c2f31]">{item.label}</span>
                      </div>
                      <span className="text-[1.05rem] font-black text-[#2c2f31]">{item.value}</span>
                    </div>
                  ))}

                  <div className="pt-2">
                    <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-200">
                      <div
                        className="h-full rounded-full bg-gradient-to-r from-[#4647d3] to-[#006947] transition-all duration-1000"
                        style={{ width: `${clampPercent(routeCoveragePercent)}%` }}
                      />
                    </div>
                  </div>

                  <div className="rounded-2xl border border-slate-200/80 bg-slate-50/85 px-4 py-3.5">
                    <div className="admin-typography-data-label flex items-center gap-2 text-slate-400">
                      <Sparkles size={14} className="text-[#4647d3]" />
                      运行摘要
                    </div>
                    <div className="mt-3 grid gap-2 sm:grid-cols-3">
                      {[
                        { label: "人工调整", value: `${formatCount(runtimeSummary.overriddenFlagCount)} 项` },
                        { label: "内容隐藏阈值", value: formatCount(runtimeSummary.autoHideReportThreshold) },
                        { label: "最近公告", value: notificationSummary.lastAnnouncementAt ? formatDateTime(notificationSummary.lastAnnouncementAt) : "暂无" },
                      ].map((item) => (
                        <div key={item.label} className="rounded-2xl bg-white px-4 py-3.5 shadow-sm">
                          <div className="admin-typography-data-label text-slate-400">{item.label}</div>
                          <div className="mt-2 text-[1.05rem] font-semibold text-slate-950">{item.value}</div>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              </SnapshotPanel>
            </div>
          </section>

          <section>
            <SectionTitle
              title="平台经营矩阵"
              subtitle="企业任务、导师经营、交易、技能和运行配置的跨域概览。"
              icon={BriefcaseBusiness}
            />
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
              {businessMatrixCards.map((card) => (
                <DomainMatrixCard key={card.title} {...card} />
              ))}
            </div>
          </section>

          <section>
            <GlassPanel className="relative overflow-hidden p-5">
              <div className="pointer-events-none absolute -right-2 -top-2 z-0 text-sky-100/70">
                <Activity size={46} strokeWidth={1.3} />
              </div>
              <div className="relative z-10 mb-5 flex flex-col gap-3 xl:flex-row xl:items-end xl:justify-between">
                <div>
                  <div className="flex items-center gap-3">
                    <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-sky-100 text-sky-600 shadow-[0_10px_20px_rgba(14,165,233,0.08)]">
                      <Activity size={18} />
                    </div>
                    <h2 className="font-['Manrope'] text-[1.45rem] font-bold tracking-tight text-[#2c2f31]">
                      AI 场景运行摘要
                    </h2>
                  </div>
                  <p className="mt-1 text-[13px] leading-5 text-slate-500">
                    重点场景的调用量、成功率、成本与延迟。
                  </p>
                </div>
                <span className="w-fit rounded-full bg-[#69f6b8]/22 px-3 py-1 text-xs font-bold text-[#005a3c]">
                  实时监控
                </span>
              </div>

              <div className="relative z-10 grid grid-cols-1 gap-4 lg:grid-cols-3">
                {runtimeFocusCards.map((card) => (
                  <RuntimeFocusCard key={card.title} {...card} />
                ))}
              </div>

              <div className="relative z-10 mt-5 grid grid-cols-1 gap-4 xl:grid-cols-[1.42fr_1fr]">
                <div className="rounded-[22px] border border-slate-200/80 bg-slate-50/80 p-4">
                  <div className="flex items-center justify-between gap-4">
                    <div className="font-['Manrope'] text-[1.15rem] font-bold tracking-tight text-slate-950">重点场景排行</div>
                    <div className="text-sm font-semibold text-slate-500">
                      近 {metricsWindowDays} 日
                    </div>
                  </div>

                  <div className="mt-4 space-y-2.5">
                    {topSceneRows.length > 0 ? topSceneRows.map((scene, index) => (
                      <div key={scene.sceneKey} className="rounded-[18px] bg-white px-4 py-3.5 shadow-sm">
                        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                          <div className="min-w-0">
                            <div className="flex items-center gap-3">
                              <span
                                className={joinClassNames(
                                  "flex h-8 w-8 items-center justify-center rounded-xl text-[10px] font-bold",
                                  index === 0
                                    ? "bg-[#4647d3] text-white"
                                    : index === 1
                                      ? "bg-[#9396ff]/18 text-[#0a0081]"
                                      : "bg-slate-100 text-slate-500",
                                )}
                              >
                                {String(index + 1).padStart(2, "0")}
                              </span>
                              <div className="min-w-0">
                                <div className="truncate text-sm font-semibold text-slate-950">{scene.displayName}</div>
                                <div className="mt-1 text-[12px] text-slate-500">
                                  {scene.ownerDomain} · 入口 {scene.frontEntry}
                                </div>
                              </div>
                            </div>
                            <div className="mt-1.5 text-[13px] leading-5 text-slate-500">
                              {scene.summary}
                            </div>
                          </div>

                          <div className="grid shrink-0 grid-cols-2 gap-2 text-right sm:grid-cols-4">
                            {[
                              {
                                label: "调用",
                                value: formatCount(scene.calls),
                                icon: Activity,
                                shell: "bg-white border border-indigo-100/70",
                                iconWrap: "bg-indigo-50 text-indigo-500",
                                labelClassName: "text-indigo-500",
                                valueClassName: "text-slate-950",
                              },
                              {
                                label: "成功",
                                value: scene.successRate,
                                icon: CheckCircle2,
                                shell: "bg-white border border-emerald-100/75",
                                iconWrap: "bg-emerald-50 text-emerald-500",
                                labelClassName: "text-emerald-500",
                                valueClassName: "text-slate-950",
                              },
                              {
                                label: "延迟",
                                value: `${formatCount(scene.avgLatencyMs)} ms`,
                                icon: Clock3,
                                shell: "bg-white border border-amber-100/75",
                                iconWrap: "bg-amber-50 text-amber-500",
                                labelClassName: "text-amber-600",
                                valueClassName: "text-slate-950",
                              },
                              {
                                label: "成本",
                                value: formatAiCurrency(scene.totalCost),
                                icon: Wallet,
                                shell: "bg-white border border-violet-100/75",
                                iconWrap: "bg-violet-50 text-violet-500",
                                labelClassName: "text-violet-500",
                                valueClassName: "text-slate-950",
                              },
                            ].map((item) => {
                              const Icon = item.icon;

                              return (
                              <div key={item.label} className={joinClassNames("min-w-[84px] rounded-2xl px-3 py-2.5", item.shell)}>
                                <div className="flex items-center justify-end gap-2">
                                  <span className={joinClassNames("flex h-6 w-6 shrink-0 items-center justify-center rounded-xl", item.iconWrap)}>
                                    <Icon size={12} />
                                  </span>
                                  <div className={joinClassNames("text-[10px] font-bold", item.labelClassName)}>{item.label}</div>
                                </div>
                                <div className={joinClassNames("mt-2 text-sm font-black", item.valueClassName)}>{item.value}</div>
                              </div>
                              );
                            })}
                          </div>
                        </div>
                        <div className="mt-2.5 text-xs text-slate-400">
                          最近调用 {formatDateTime(scene.lastCallAt)}
                        </div>
                      </div>
                    )) : (
                      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前还没有可展示的 AI 场景数据" />
                    )}
                  </div>
                </div>

                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="rounded-[20px] border border-slate-200/80 bg-slate-50/80 p-4">
                    <div className="flex items-center gap-2.5">
                      <div className="flex h-8 w-8 items-center justify-center rounded-2xl bg-indigo-100 text-indigo-600">
                        <Activity size={14} />
                      </div>
                      <div className="flex min-h-8 items-center text-[13px] font-bold leading-none text-indigo-700">服务商成本结构</div>
                    </div>
                    <div className="mt-3 space-y-2.5">
                      {providerCostItems.length > 0 ? providerCostItems.map((item) => {
                        const visual = getProviderCostVisual(item.name);

                        return (
                        <div key={item.name} className="rounded-2xl bg-white px-4 py-3 shadow-sm">
                          <div className="space-y-2">
                            <div className="flex items-center gap-3">
                              <div className={joinClassNames("flex h-8 w-8 shrink-0 items-center justify-center rounded-2xl", visual.iconClassName)}>
                                <visual.Icon size={14} />
                              </div>
                              <div className="min-w-0 flex-1">
                                <div className={joinClassNames("flex min-h-8 items-center break-all text-sm font-semibold leading-5", visual.titleClassName)}>{item.name}</div>
                              </div>
                            </div>
                            <div className="flex items-center justify-between gap-3">
                              <div className="text-xs text-slate-500">{formatCount(item.calls)} 次调用</div>
                              <div className={joinClassNames("shrink-0 text-sm font-black", visual.valueClassName)}>{formatAiCurrency(item.cost)}</div>
                            </div>
                          </div>
                          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100">
                            <div
                              className={joinClassNames("h-full rounded-full", visual.barClassName)}
                              style={{ width: `${clampPercent((parseNumericValue(item.cost) / providerCostMax) * 100)}%` }}
                            />
                          </div>
                        </div>
                        );
                      }) : (
                        <div className="rounded-2xl bg-white px-4 py-4 text-sm text-slate-500 shadow-sm">暂无服务商成本数据。</div>
                      )}
                    </div>
                  </div>

                  <div className="rounded-[20px] border border-slate-200/80 bg-slate-50/80 p-4">
                    <div className="flex items-center gap-2.5">
                      <div className="flex h-8 w-8 items-center justify-center rounded-2xl bg-emerald-100 text-emerald-600">
                        <Users size={14} />
                      </div>
                      <div className="flex min-h-8 items-center text-[13px] font-bold leading-none text-emerald-700">用户层级分布</div>
                    </div>
                    <div className="mt-3 space-y-2.5">
                      {tierCostItems.length > 0 ? tierCostItems.map((item) => {
                        const visual = getTierVisual(item.name);
                        return (
                        <div key={item.name} className="rounded-2xl bg-white px-4 py-3 shadow-sm">
                          <div className="space-y-2">
                            <div className="flex items-center gap-3">
                              <div className={joinClassNames("flex h-8 w-8 shrink-0 items-center justify-center rounded-2xl", visual.iconClassName)}>
                                <visual.Icon size={14} />
                              </div>
                              <div className={joinClassNames("flex min-h-8 min-w-0 flex-1 items-center break-all text-sm font-semibold leading-5", visual.titleClassName)}>
                                {getLabel(item.name, tierLabelMap, item.name)}
                              </div>
                            </div>
                            <div className="flex items-center justify-between gap-3">
                              <div className="text-xs text-slate-500">{formatCount(item.calls)} 次调用</div>
                              <div className={joinClassNames("shrink-0 text-sm font-black", visual.valueClassName)}>{formatAiCurrency(item.cost)}</div>
                            </div>
                          </div>
                          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100">
                            <div
                              className={joinClassNames("h-full rounded-full", visual.barClassName)}
                              style={{ width: `${clampPercent((parseNumericValue(item.cost) / tierCostMax) * 100)}%` }}
                            />
                          </div>
                        </div>
                        );
                      }) : (
                        <div className="rounded-2xl bg-white px-4 py-4 text-sm text-slate-500 shadow-sm">暂无用户层级数据。</div>
                      )}
                    </div>
                  </div>

                  <div className="rounded-[20px] border border-slate-200/80 bg-slate-50/80 p-4">
                    <div className="flex items-center gap-2.5">
                      <div className="flex h-8 w-8 items-center justify-center rounded-2xl bg-amber-100 text-amber-600">
                        <Sparkles size={14} />
                      </div>
                      <div className="flex min-h-8 items-center text-[13px] font-bold leading-none text-amber-700">任务类型分布</div>
                    </div>
                    <div className="mt-3 space-y-2.5">
                      {taskTypeCostItems.length > 0 ? taskTypeCostItems.map((item) => {
                        const visual = getTaskTypeVisual(item.name);
                        return (
                        <div key={item.name} className="rounded-2xl bg-white px-4 py-3 shadow-sm">
                          <div className="space-y-2">
                            <div className="flex items-center gap-3">
                              <div className={joinClassNames("flex h-8 w-8 shrink-0 items-center justify-center rounded-2xl", visual.iconClassName)}>
                                <visual.Icon size={14} />
                              </div>
                              <div className={joinClassNames("flex min-h-8 min-w-0 flex-1 items-center break-all text-sm font-semibold leading-5", visual.titleClassName)}>
                                {getLabel(item.name, gatewayTaskTypeLabelMap, item.name)}
                              </div>
                            </div>
                            <div className="flex items-center justify-between gap-3">
                              <div className="text-xs text-slate-500">{formatCount(item.calls)} 次调用</div>
                              <div className={joinClassNames("shrink-0 text-sm font-black", visual.valueClassName)}>{formatAiCurrency(item.cost)}</div>
                            </div>
                          </div>
                          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100">
                            <div
                              className={joinClassNames("h-full rounded-full", visual.barClassName)}
                              style={{ width: `${clampPercent((parseNumericValue(item.cost) / taskTypeCostMax) * 100)}%` }}
                            />
                          </div>
                        </div>
                        );
                      }) : (
                        <div className="rounded-2xl bg-white px-4 py-4 text-sm text-slate-500 shadow-sm">暂无任务类型分布。</div>
                      )}
                    </div>
                  </div>

                  <div className="rounded-[20px] border border-slate-200/80 bg-slate-50/80 p-4">
                    <div className="flex items-center gap-2.5">
                      <div className="flex h-8 w-8 items-center justify-center rounded-2xl bg-violet-100 text-violet-600">
                        <Wallet size={14} />
                      </div>
                      <div className="flex min-h-8 items-center text-[13px] font-bold leading-none text-violet-700">重点消费用户</div>
                    </div>
                    <div className="mt-3 space-y-2.5">
                      {topCostUsers.length > 0 ? topCostUsers.map((item, index) => {
                        const visual = getTopCostUserVisual(index);
                        return (
                        <button
                          key={item.userId}
                          type="button"
                          className="w-full rounded-2xl bg-white px-4 py-3 text-left shadow-sm transition-colors hover:bg-white"
                          onClick={() => navigate(`/admin/users/${item.userId}`)}
                        >
                          <div className="space-y-2">
                            <div className="flex items-center gap-3">
                              <div className={joinClassNames("flex h-8 w-8 shrink-0 items-center justify-center rounded-2xl", visual.iconClassName)}>
                                <visual.Icon size={14} />
                              </div>
                              <div className={joinClassNames("flex min-h-8 min-w-0 flex-1 items-center break-all text-sm font-semibold leading-5", visual.titleClassName)}>
                                {item.displayName || item.email || `用户 #${item.userId}`}
                              </div>
                            </div>
                            <div className="flex items-center justify-between gap-3">
                              <div className="truncate text-xs text-slate-500">
                                #{item.userId} · {formatCount(item.calls)} 次调用
                              </div>
                              <div className={joinClassNames("shrink-0 text-sm font-black", visual.valueClassName)}>{formatAiCurrency(item.cost)}</div>
                            </div>
                          </div>
                          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100">
                            <div
                              className={joinClassNames("h-full rounded-full", visual.barClassName)}
                              style={{ width: `${clampPercent((parseNumericValue(item.cost) / topCostUserMax) * 100)}%` }}
                            />
                          </div>
                        </button>
                        );
                      }) : (
                        <div className="rounded-2xl bg-white px-4 py-4 text-sm text-slate-500 shadow-sm">暂无重点消费用户样本。</div>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            </GlassPanel>
          </section>

          <section>
            <GlassPanel className="overflow-hidden">
              <div className="flex flex-col gap-3 border-b border-slate-200/70 px-5 py-5 md:flex-row md:items-end md:justify-between">
                <div>
                  <h2 className="font-['Manrope'] text-xl font-bold text-[#2c2f31]">待处理举报样本</h2>
                  <p className="mt-1.5 text-sm leading-6 text-slate-500">
                    当前列表直接展示后端真实状态、最新动作和更新时间，不再用举报次数伪造状态语义。
                  </p>
                </div>
                <Button className="!h-9 !rounded-xl !border-slate-200 !px-4" onClick={() => navigate("/admin/content?tab=reports")}>
                  前往内容治理页
                </Button>
              </div>

              {pendingReports.length > 0 ? (
                <div className="overflow-x-auto">
                  <table className="min-w-[880px] w-full text-left">
                    <thead className="bg-slate-100/85">
                      <tr>
                        {["对象", "原因与热度", "最近变化", "真实状态", "操作"].map((header) => (
                          <th
                            key={header}
                            className={joinClassNames(
                              "px-5 py-3 text-[11px] font-black text-slate-500",
                              header === "操作" && "text-right",
                            )}
                          >
                            {header}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200/70">
                      {pendingReports.map((report) => {
                        const visual = reportTargetVisualMap[report.targetType] ?? reportTargetVisualMap.POST;
                        const status = getReportStatusMeta(report.status);
                        const priority = getReportPriorityMeta(report.reportCount);

                        return (
                          <tr key={report.reportId} className="transition-colors hover:bg-white/80">
                            <td className="px-5 py-3.5">
                              <div className="flex items-start gap-3">
                                <div className={joinClassNames("flex h-9 w-9 items-center justify-center rounded-xl", visual.iconBg, visual.iconColor)}>
                                  <visual.icon size={16} />
                                </div>
                                <div className="min-w-0">
                                  <div className="truncate text-sm font-semibold text-slate-950">
                                    {report.contentTitle || "未命名内容"}
                                  </div>
                                  <div className="mt-1 text-xs text-slate-500">
                                    {getLabel(report.targetType, targetTypeLabelMap, report.targetType)} · 举报 #{report.reportId}
                                  </div>
                                </div>
                              </div>
                            </td>
                            <td className="px-5 py-3.5">
                              <div className="space-y-1.5">
                                <div className="text-sm font-semibold text-slate-950">
                                  {getLabel(report.reasonCode, moderationReasonLabelMap, report.reasonCode)}
                                </div>
                                <div className="flex flex-wrap gap-2">
                                  <span className={joinClassNames("rounded-full px-3 py-1 text-[11px] font-bold", priority.className)}>
                                    {priority.label}
                                  </span>
                                  <span className="rounded-full bg-slate-100 px-3 py-1 text-[11px] font-bold text-slate-500">
                                    累计 {formatCount(report.reportCount)} 次举报
                                  </span>
                                </div>
                              </div>
                            </td>
                            <td className="px-5 py-3.5">
                              <div className="text-sm font-semibold text-slate-950">
                                {formatRelativeTime(report.updatedAt || report.createdAt)}
                              </div>
                              <div className="mt-1 text-xs text-slate-500">
                                创建 {formatDateTime(report.createdAt)}
                              </div>
                              <div className="mt-1 text-xs text-slate-400">
                                更新 {formatDateTime(report.updatedAt)}
                              </div>
                            </td>
                            <td className="px-5 py-3.5">
                              <div className="space-y-1.5">
                                <span className={joinClassNames("inline-flex rounded-full px-3 py-1 text-[11px] font-bold", status.className)}>
                                  {status.label}
                                </span>
                                <div className="text-xs text-slate-500">
                                  最新动作 {getLabel(report.latestAction, moderationActionLabelMap, report.latestAction ?? "暂无动作")}
                                </div>
                              </div>
                            </td>
                            <td className="px-5 py-3.5 text-right">
                              <button
                                type="button"
                                className="rounded-lg bg-[#4647d3]/10 px-3.5 py-1.5 text-[13px] font-bold text-[#4647d3] transition-colors hover:bg-[#4647d3]/14"
                                onClick={() => void handleOpenReportDetail(report)}
                              >
                                查看详情
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="px-5 py-9">
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无待处理举报样本" />
                </div>
              )}
            </GlassPanel>
          </section>
      </div>

      {activeReport ? (
        <Suspense fallback={null}>
          <AdminDashboardReportModal
            context={{
              activeReport,
              reportDetail,
              reportDetailLoading,
              reportDetailError,
              reportModalStatus,
              reportModalPriority,
              reportTargetUserId,
              handleCloseReportDetail,
              navigate,
            }}
          />
        </Suspense>
      ) : null}
    </AdminPageFrame>
  );
}
