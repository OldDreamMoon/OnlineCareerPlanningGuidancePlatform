import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  ArrowLeft,
  BellRing,
  Building2,
  Check,
  CheckCircle2,
  ChevronDown,
  Clock3,
  Edit3,
  FileCheck2,
  FileText,
  Globe,
  Info,
  KeyRound,
  Plus,
  RefreshCw,
  ShieldCheck,
  Tags,
  UploadCloud,
  X,
  type LucideIcon,
} from "lucide-react";
import { startTransition, useEffect, useMemo, useRef, useState, type ChangeEvent, type ReactNode } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import EnterpriseIdentityLogo from "../components/avatar/EnterpriseIdentityLogo";
import LazyProfileNotificationPreferencesPanel from "../components/notifications/LazyProfileNotificationPreferencesPanel";
import EnterpriseLogoEditor, { type EnterpriseLogoUploadResult } from "../components/profile/EnterpriseLogoEditor";
import WorkspacePageLoadingScreen from "../components/WorkspacePageLoadingScreen";
import WorkspaceRoleTopbar from "../components/workspace/WorkspaceRoleTopbar";
import { ApiClientError, apiRequest, isAbortError } from "../lib/apiClient";
import { resolveEnterpriseAccountName } from "../lib/enterpriseIdentity";
import { buildEnterpriseLogoUrl } from "../lib/enterpriseLogo";
import { formatDateTime, toTimestamp } from "../lib/formatters";
import { getEnterpriseWorkspaceNavItems } from "../lib/workspaceNav";
import {
  buildWorkspaceSnapshotStorageKey,
  readWorkspaceSnapshot,
  writeWorkspaceSnapshot,
} from "../lib/workspaceSnapshot";

type PageState = "loading" | "ready" | "error";
type TimeValue = number | string | null;
type EnterpriseProfileTab = "profile" | "certification" | "notifications";

type EnterpriseProfileResponse = {
  userId: number;
  displayName: string;
  realName: string | null;
  companyName: string | null;
  jobTitle: string | null;
  industry: string | null;
  companySize: string | null;
  hiringTags: string[];
  bio: string | null;
  externalLinks: string | null;
  preferences: string | null;
  logoUrl: string | null;
  logoConfigured: boolean;
  logoContentType: string | null;
  logoUpdatedAt: number | string | null;
  approvalStatus: string;
};

type CertificationAsset = {
  assetId: number;
  originalFilename: string;
  contentType: string | null;
  sizeBytes: number | null;
  lifecycleStatus: string | null;
  uploadedAt: string | null;
};

type CertificationSubmission = {
  submissionId: number;
  userId: number;
  role: string;
  realName: string | null;
  companyName: string | null;
  jobTitle: string | null;
  status: string;
  current: boolean;
  reviewNote: string | null;
  previousSubmissionId: number | null;
  submittedAt: string | null;
  reviewedAt: string | null;
  assets: CertificationAsset[];
};

type CertificationOwnViewResponse = {
  userId: number;
  role: string;
  approvalStatus: string;
  currentSubmission: CertificationSubmission | null;
  submissions: CertificationSubmission[];
};

type SendCodeResponse = {
  sent: boolean;
  stage: string;
  targetEmail: string;
  deliveryChannel: string;
  expiresAt: TimeValue;
  nextSendAt: TimeValue;
  debugCode: string | null;
};

type VerifyCodeResponse = {
  verified: boolean;
  verificationToken: string;
  expiresAt: TimeValue;
};

type ProfileFormState = {
  companyName: string;
  realName: string;
  jobTitle: string;
  industry: string;
  companySize: string;
  hiringTags: string[];
  bio: string;
  externalLinks: string;
  preferences: string;
};

type CertificationFormState = {
  realName: string;
  companyName: string;
  jobTitle: string;
  file: File | null;
  busy: boolean;
};

type ToastState = {
  tone: "success" | "error" | "info";
  message: string;
};

type DebugCodeHint = {
  code: string;
  targetEmail: string;
};

type PasswordModalState = {
  isOpen: boolean;
  step: "verifyCode" | "changePassword";
  code: string;
  newPassword: string;
  passwordResetToken: string;
  busy: boolean;
  debugHint: DebugCodeHint | null;
};

type EnterpriseProfileWorkspaceSnapshot = {
  profile: EnterpriseProfileResponse;
  certification: CertificationOwnViewResponse | null;
};

const containerVariants = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: { staggerChildren: 0.08 },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 24 },
  show: {
    opacity: 1,
    y: 0,
    transition: { type: "spring" as const, stiffness: 250, damping: 24 },
  },
};

const INDUSTRY_OPTIONS = [
  "互联网 / 软件服务",
  "人工智能 / 大模型",
  "企业服务 / SaaS",
  "教育科技",
  "消费品牌 / 电商",
  "智能硬件 / 制造",
  "金融科技",
  "待补充",
] as const;

const COMPANY_SIZE_OPTIONS = [
  "20 人以下",
  "20-50 人",
  "50-200 人",
  "200-500 人",
  "500-1000 人",
  "1000 人以上",
  "待补充",
] as const;

const TAG_SUGGESTIONS = [
  "算法工程",
  "AI 应用产品",
  "前端产品化",
  "数据策略",
  "增长运营",
  "雇主品牌",
  "校园合作",
  "设计策略",
  "内容营销",
  "B 端交付",
] as const;

const PROFILE_TABS: Array<{ id: EnterpriseProfileTab; label: string; icon: LucideIcon }> = [
  { id: "profile", label: "企业资料", icon: Building2 },
  { id: "certification", label: "认证状态", icon: ShieldCheck },
  { id: "notifications", label: "通知提醒", icon: BellRing },
];

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function resolveProfileTab(value: string | null): EnterpriseProfileTab {
  return PROFILE_TABS.some((tab) => tab.id === value) ? (value as EnterpriseProfileTab) : "profile";
}

function buildErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiClientError && error.message) {
    return error.message;
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}

function getToastPresentation(tone: ToastState["tone"]) {
  if (tone === "success") {
    return {
      title: "保存成功",
      icon: CheckCircle2,
      shellClassName: "border border-emerald-100/90 bg-[#f2fcf7] ring-1 ring-emerald-100/90 shadow-[0_18px_40px_rgba(16,185,129,0.12)]",
      iconClassName: "bg-emerald-100 text-emerald-600",
      titleClassName: "text-emerald-700",
      messageClassName: "text-slate-600",
    };
  }

  if (tone === "info") {
    return {
      title: "提示",
      icon: Info,
      shellClassName: "border border-sky-100/90 bg-[#f3f9ff] ring-1 ring-sky-100/90 shadow-[0_18px_40px_rgba(96,165,250,0.12)]",
      iconClassName: "bg-sky-100 text-sky-600",
      titleClassName: "text-sky-700",
      messageClassName: "text-slate-600",
    };
  }

  return {
    title: "请稍后重试",
    icon: AlertCircle,
    shellClassName: "border border-rose-100/90 bg-[#fff5f5] ring-1 ring-rose-100/90 shadow-[0_18px_40px_rgba(244,63,94,0.12)]",
    iconClassName: "bg-rose-100 text-rose-600",
    titleClassName: "text-rose-700",
    messageClassName: "text-slate-600",
  };
}

function formatFileSize(sizeBytes: number | null | undefined) {
  if (!sizeBytes || sizeBytes <= 0) {
    return "—";
  }
  if (sizeBytes < 1024) {
    return `${sizeBytes} B`;
  }
  if (sizeBytes < 1024 * 1024) {
    return `${(sizeBytes / 1024).toFixed(1)} KB`;
  }
  return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`;
}

function buildProfileForm(profile: EnterpriseProfileResponse): ProfileFormState {
  return {
    companyName: profile.companyName ?? "",
    realName: profile.realName ?? "",
    jobTitle: profile.jobTitle ?? "",
    industry: profile.industry ?? "",
    companySize: profile.companySize ?? "",
    hiringTags: profile.hiringTags ?? [],
    bio: profile.bio ?? "",
    externalLinks: profile.externalLinks ?? "",
    preferences: profile.preferences ?? "",
  };
}

function createCertificationForm(profile: EnterpriseProfileResponse, certification: CertificationOwnViewResponse | null): CertificationFormState {
  const currentSubmission = certification?.currentSubmission;
  return {
    realName: profile.realName ?? currentSubmission?.realName ?? "",
    companyName: profile.companyName ?? currentSubmission?.companyName ?? "",
    jobTitle: profile.jobTitle ?? currentSubmission?.jobTitle ?? "",
    file: null,
    busy: false,
  };
}

function getApprovalMeta(status: string | null | undefined, reviewNote?: string | null) {
  switch (status) {
    case "APPROVED":
      return {
        label: "认证已通过",
        badgeClassName: "border-emerald-500/30 bg-emerald-500/10 text-emerald-300",
        panelClassName: "border-emerald-100 bg-emerald-50/80 text-emerald-700",
        title: "企业认证已通过",
        description: "你可以继续发布任务，并持续完善企业资料。",
        reviewMessage: reviewNote?.trim() || "认证状态稳定。",
      };
    case "REJECTED":
      return {
        label: "资料待补件",
        badgeClassName: "border-rose-500/30 bg-rose-500/10 text-rose-200",
        panelClassName: "border-rose-100 bg-rose-50/80 text-rose-700",
        title: "认证资料待补充",
        description: "请根据审核意见补充材料后重新提交。",
        reviewMessage: reviewNote?.trim() || "请补充或修正认证材料后再次提交。",
      };
    default:
      return {
        label: "认证审核中",
        badgeClassName: "border-amber-400/30 bg-amber-400/10 text-amber-200",
        panelClassName: "border-amber-100 bg-amber-50/80 text-amber-700",
        title: "认证审核中",
        description: "审核期间可继续完善企业资料。",
        reviewMessage: reviewNote?.trim() || "平台正在审核本次提交。",
      };
  }
}

function buildOptions(currentValue: string, options: readonly string[]) {
  if (currentValue.trim() && !options.includes(currentValue.trim())) {
    return [currentValue.trim(), ...options];
  }
  return [...options];
}

function calculateCountdown(nextSendAt: TimeValue | null | undefined) {
  if (!nextSendAt) {
    return 60;
  }

  const seconds = Math.ceil((toTimestamp(nextSendAt) - Date.now()) / 1000);
  return Math.max(seconds, 0);
}

function createPasswordModalState(overrides?: Partial<PasswordModalState>): PasswordModalState {
  return {
    isOpen: false,
    step: "verifyCode",
    code: "",
    newPassword: "",
    passwordResetToken: "",
    busy: false,
    debugHint: null,
    ...overrides,
  };
}

function SummaryLine({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div className="flex items-center justify-between gap-4 rounded-xl border border-slate-100 bg-white/75 px-3 py-2.5">
      <span className="text-[14px] font-semibold text-slate-500">{label}</span>
      <span className="text-[16px] font-semibold text-slate-700">{value?.trim() || "待补充"}</span>
    </div>
  );
}

function ProfileDropdownField({
  label,
  value,
  options,
  disabled,
  placeholder,
  onChange,
}: {
  label: string;
  value: string;
  options: string[];
  disabled: boolean;
  placeholder: string;
  onChange: (nextValue: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (disabled && open) {
      setOpen(false);
    }
  }, [disabled, open]);

  useEffect(() => {
    const handlePointerDown = (event: MouseEvent) => {
      if (!rootRef.current || !(event.target instanceof Node) || rootRef.current.contains(event.target)) {
        return;
      }
      setOpen(false);
    };

    document.addEventListener("mousedown", handlePointerDown);
    return () => document.removeEventListener("mousedown", handlePointerDown);
  }, []);

  return (
    <div ref={rootRef} className="space-y-2">
      <span className="text-[15px] font-semibold text-slate-700">{label}</span>
      <div className="relative">
        <button
          type="button"
          disabled={disabled}
          onClick={() => {
            if (!disabled) {
              setOpen((current) => !current);
            }
          }}
          className={joinClasses(
            "flex w-full items-center justify-between rounded-[1.15rem] border px-4 py-3 text-[15px] outline-none transition-colors",
            disabled
              ? "cursor-not-allowed border-slate-200 bg-slate-50 text-slate-500"
              : "border-slate-200 bg-white text-slate-800 hover:border-slate-300",
          )}
        >
          <span className={joinClasses("truncate text-left", value.trim() ? "text-slate-800" : "text-slate-400")}>
            {value.trim() || placeholder}
          </span>
          <ChevronDown size={16} className={joinClasses("ml-3 shrink-0 text-slate-400 transition-transform", open && "rotate-180")} />
        </button>

        <AnimatePresence>
          {open && !disabled ? (
            <motion.div
              initial={{ opacity: 0, y: 10, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 8, scale: 0.98 }}
              transition={{ duration: 0.15 }}
              className="absolute left-0 top-full z-30 mt-2 w-full overflow-hidden rounded-[1.25rem] border border-slate-200 bg-white shadow-[0_24px_50px_rgba(15,23,42,0.15)] ring-1 ring-slate-900/5"
            >
              <div className="max-h-72 overflow-y-auto p-2 [&::-webkit-scrollbar]:w-1.5 [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-slate-200">
                {options.map((option) => {
                  const selected = option === value;
                  return (
                    <button
                      key={option}
                      type="button"
                      onClick={() => {
                        onChange(option);
                        setOpen(false);
                      }}
                      className={joinClasses(
                        "flex w-full items-center justify-between rounded-xl px-3 py-3 text-left text-[15px] transition-colors",
                        selected
                          ? "bg-indigo-50 text-indigo-700"
                          : "text-slate-700 hover:bg-slate-50",
                      )}
                    >
                      <span className="truncate pr-3">{option}</span>
                      {selected ? <Check size={16} className="shrink-0" /> : null}
                    </button>
                  );
                })}
              </div>
            </motion.div>
          ) : null}
        </AnimatePresence>
      </div>
    </div>
  );
}

function EnterpriseProfileFrame({
  displayName,
  companyName,
  logoUrl,
  loading,
  lastUpdatedAt,
  onRefresh,
  children,
}: {
  displayName: string | null | undefined;
  companyName: string | null | undefined;
  logoUrl?: string | null;
  loading: boolean;
  lastUpdatedAt: string | null;
  onRefresh: () => void;
  children: ReactNode;
}) {
  return (
    <div className="relative min-h-screen bg-[#eef3ff] text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(71,85,105,0.12),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(14,165,233,0.08),transparent_28%),linear-gradient(180deg,#f1f5f9_0%,#f8fafc_58%,#f8fafc_100%)]" />
        <div className="page-top-glow page-top-glow--slate" />
      </div>

      <WorkspaceRoleTopbar
        sectionLabel="Enterprise Profile"
        title="企业资料与认证"
        icon={Building2}
        navItems={getEnterpriseWorkspaceNavItems("profile")}
        displayName={displayName}
        userSubtitle={companyName?.trim() || "企业账号"}
        userFallbackLabel="企业代表"
        userFallbackInitial="企"
        userAvatarUrl={logoUrl}
        userAvatarDisplayName={companyName?.trim() || displayName?.trim() || "企业"}
        lastUpdatedAt={lastUpdatedAt}
        onRefresh={onRefresh}
        refreshing={loading}
        refreshTitle="刷新企业资料页数据"
      />

      <main className="relative z-10 mx-auto w-full max-w-[96rem] px-4 pb-6 pt-8 sm:px-6 lg:px-8 lg:pb-8 lg:pt-10">
        {children}
      </main>
    </div>
  );
}

function EnterpriseProfileSkeleton({ displayName }: { displayName: string | null | undefined }) {
  return (
    <WorkspacePageLoadingScreen
      title="正在准备企业资料与认证"
      description="正在加载企业名片、认证信息和招聘配置，请稍候。"
    />
  );
}

function EnterpriseProfileErrorState({
  displayName,
  companyName,
  logoUrl,
  refreshing,
  lastUpdatedAt,
  onRefresh,
  errorMessage,
}: {
  displayName: string | null | undefined;
  companyName: string | null | undefined;
  logoUrl?: string | null;
  refreshing: boolean;
  lastUpdatedAt: string | null;
  onRefresh: () => void;
  errorMessage: string;
}) {
  return (
    <EnterpriseProfileFrame
      displayName={displayName}
      companyName={companyName}
      logoUrl={logoUrl}
      loading={refreshing}
      lastUpdatedAt={lastUpdatedAt}
      onRefresh={onRefresh}
    >
      <div className="mx-auto max-w-3xl rounded-[2rem] border border-rose-200 bg-white/90 p-8 shadow-[0_22px_60px_rgba(148,163,184,0.15)] backdrop-blur-xl">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex items-start gap-4">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-rose-50 text-rose-500">
              <AlertCircle size={24} />
            </div>
            <div>
              <div className="text-[12px] text-rose-400">读取失败</div>
              <h1 className="mt-2 text-2xl font-bold text-slate-900">企业资料暂时无法加载</h1>
              <p className="mt-3 text-sm leading-7 text-slate-600">{errorMessage}</p>
              <p className="mt-3 text-sm leading-7 text-slate-500">
                请重新加载后继续查看企业资料与认证状态。
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onRefresh}
            className="inline-flex items-center justify-center rounded-full bg-slate-900 px-6 py-3 text-sm font-semibold text-white transition-transform hover:-translate-y-0.5 hover:bg-slate-800"
          >
            重新加载
            <RefreshCw size={16} className="ml-2" />
          </button>
        </div>
      </div>
    </EnterpriseProfileFrame>
  );
}

export default function EnterpriseProfilePage() {
  const {
    role,
    displayName: authDisplayName,
    userId,
    email,
  } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const [pageState, setPageState] = useState<PageState>("loading");
  const [activeTab, setActiveTab] = useState<EnterpriseProfileTab>(() => resolveProfileTab(searchParams.get("tab")));
  const [profile, setProfile] = useState<EnterpriseProfileResponse | null>(null);
  const [formData, setFormData] = useState<ProfileFormState | null>(null);
  const [certification, setCertification] = useState<CertificationOwnViewResponse | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [tagInput, setTagInput] = useState("");
  const [toast, setToast] = useState<ToastState | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string | null>(null);
  const [certModalOpen, setCertModalOpen] = useState(false);
  const [certForm, setCertForm] = useState<CertificationFormState | null>(null);
  const [assetLoadingId, setAssetLoadingId] = useState<number | null>(null);
  const [passwordCountdown, setPasswordCountdown] = useState(0);
  const [passwordModal, setPasswordModal] = useState<PasswordModalState>(createPasswordModalState());
  const snapshotKey = buildWorkspaceSnapshotStorageKey("enterprise", "profile", userId ?? "current");

  const showToast = (message: string, tone: ToastState["tone"] = "success") => {
    setToast({ message, tone });
  };

  useEffect(() => {
    if (!toast) {
      return undefined;
    }
    const timer = window.setTimeout(() => setToast(null), 3200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    if (passwordCountdown <= 0) {
      return undefined;
    }

    const timer = window.setTimeout(() => setPasswordCountdown((current) => current - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [passwordCountdown]);

  const applyWorkspaceSnapshot = (snapshot: EnterpriseProfileWorkspaceSnapshot, updatedAt: string) => {
    // 企业资料和认证状态一起恢复，编辑中的草稿不被后台回刷覆盖。
    setProfile(snapshot.profile);
    setFormData((current) => {
      if (isEditing && current) {
        return current;
      }
      return buildProfileForm(snapshot.profile);
    });
    setCertification(snapshot.certification);
    setLastUpdatedAt(updatedAt);
    setPageState("ready");
  };

  const persistWorkspaceSnapshot = (
    snapshot: EnterpriseProfileWorkspaceSnapshot,
    updatedAt = new Date().toISOString(),
  ) => {
    writeWorkspaceSnapshot(snapshotKey, snapshot, updatedAt);
    setLastUpdatedAt(updatedAt);
    return updatedAt;
  };

  const loadAll = async (options?: { silent?: boolean; background?: boolean; signal?: AbortSignal }) => {
    const silent = options?.silent ?? false;
    const background = options?.background ?? false;
    if (!silent) {
      setPageState((current) => (profile ? current : "loading"));
    }
    setRefreshing(true);
    setLoadError(null);

    try {
      // 企业资料页只并行拉两类真相：资料主体和当前认证 submission。
      const [profileResponse, certificationResponse] = await Promise.all([
        apiRequest<EnterpriseProfileResponse>("/profiles/enterprises/me", { signal: options?.signal }),
        apiRequest<CertificationOwnViewResponse>("/certification/me", { signal: options?.signal }),
      ]);
      const snapshot = {
        profile: profileResponse,
        certification: certificationResponse,
      } satisfies EnterpriseProfileWorkspaceSnapshot;
      const updatedAt = persistWorkspaceSnapshot(snapshot);

      startTransition(() => {
        setProfile(profileResponse);
        setFormData((current) => {
          if (isEditing && current) {
            return current;
          }
          return buildProfileForm(profileResponse);
        });
        setCertification(certificationResponse);
        setLastUpdatedAt(updatedAt);
        setPageState("ready");
      });
    } catch (error) {
      if (isAbortError(error)) {
        return;
      }
      const message = buildErrorMessage(error, "企业资料页加载失败，请稍后重试。");
      if (background) {
        setLoadError(null);
        return;
      }
      setLoadError(message);
      if (!profile) {
        setPageState("error");
      } else {
        showToast(message, "error");
      }
    } finally {
      setRefreshing(false);
    }
  };

  useEffect(() => {
    if (role !== "ENTERPRISE") {
      return undefined;
    }

    const controller = new AbortController();
    const snapshot = readWorkspaceSnapshot<EnterpriseProfileWorkspaceSnapshot>(snapshotKey);

    if (snapshot?.data) {
      // 先读快照减少空白时间，再用 background 模式刷新但不打断用户阅读。
      applyWorkspaceSnapshot(snapshot.data, snapshot.updatedAt);
      setLoadError(null);
      void loadAll({ silent: true, background: true, signal: controller.signal });
    } else {
      void loadAll({ signal: controller.signal });
    }

    return () => controller.abort();
  }, [role, snapshotKey]);

  useEffect(() => {
    const nextTab = resolveProfileTab(searchParams.get("tab"));
    setActiveTab((current) => (current === nextTab ? current : nextTab));
  }, [searchParams]);

  useEffect(() => {
    if (pageState !== "ready" || searchParams.get("action") !== "password") {
      return;
    }

    // 通知 deep-link 进入资料页后直接展开改密弹窗，并消费 action 参数。
    setPasswordModal(createPasswordModalState({ isOpen: true }));
    setPasswordCountdown(0);

    const nextSearchParams = new URLSearchParams(searchParams);
    nextSearchParams.delete("action");
    setSearchParams(nextSearchParams, { replace: true });
  }, [pageState, searchParams, setSearchParams]);

  const initialFormData = useMemo(
    () => (profile ? buildProfileForm(profile) : null),
    [profile],
  );

  const isDirty = Boolean(
    initialFormData
      && formData
      && JSON.stringify(initialFormData) !== JSON.stringify(formData),
  );

  if (pageState === "loading") {
    return <EnterpriseProfileSkeleton displayName={authDisplayName} />;
  }

  if (pageState === "error" || !profile || !formData) {
    return (
      <EnterpriseProfileErrorState
        displayName={profile?.realName || authDisplayName}
        companyName={profile?.companyName || profile?.displayName || "企业"}
        logoUrl={profile?.logoUrl ?? null}
        refreshing={refreshing}
        lastUpdatedAt={lastUpdatedAt}
        onRefresh={() => {
          void loadAll();
        }}
        errorMessage={loadError ?? "企业资料页加载失败，请稍后重试。"}
      />
    );
  }

  const approvalMeta = getApprovalMeta(certification?.approvalStatus ?? profile.approvalStatus, certification?.currentSubmission?.reviewNote);
  const currentSubmission = certification?.currentSubmission;
  const companyName = formData.companyName || profile.companyName || profile.displayName;
  const heroName = formData.realName || resolveEnterpriseAccountName(profile);
  const suggestedTags = TAG_SUGGESTIONS.filter((item) => !formData.hiringTags.includes(item)).slice(0, 6);
  const currentAssets = currentSubmission?.assets.filter((asset) => asset.lifecycleStatus === "ACTIVE") ?? [];

  const handleManualRefresh = () => {
    void loadAll({ silent: true });
  };

  const handleTabChange = (nextTab: EnterpriseProfileTab) => {
    // 资料编辑态禁止切换到认证/安全页，避免未保存草稿丢失。
    if (isEditing && isDirty) {
      showToast("请先保存或取消本次修改，再切换板块。", "error");
      return;
    }
    setActiveTab(nextTab);
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous);
      next.set("tab", nextTab);
      return next;
    }, { replace: true });
  };

  const handleStartEdit = () => {
    setIsEditing(true);
    showToast("已进入编辑状态。", "info");
  };

  const handleReset = () => {
    if (!initialFormData) {
      return;
    }
    setFormData(initialFormData);
    setIsEditing(false);
    setTagInput("");
    showToast("本次修改已取消。", "info");
  };

  const handleAddTag = (value = tagInput) => {
    const normalized = value.trim();
    if (!normalized || !formData) {
      return;
    }
    if (formData.hiringTags.includes(normalized)) {
      setTagInput("");
      return;
    }
    if (formData.hiringTags.length >= 8) {
      showToast("招聘方向标签最多保留 8 个，请保留最重要的方向。", "error");
      return;
    }
    setFormData({
      ...formData,
      hiringTags: [...formData.hiringTags, normalized],
    });
    setTagInput("");
  };

  const handleRemoveTag = (tag: string) => {
    if (!formData) {
      return;
    }
    setFormData({
      ...formData,
      hiringTags: formData.hiringTags.filter((item) => item !== tag),
    });
  };

  const handleSave = async () => {
    if (!formData) {
      return;
    }
    setSaving(true);
    try {
      // 企业资料保存只更新运营展示字段，认证状态仍由 certification 链路控制。
      const response = await apiRequest<EnterpriseProfileResponse>("/profiles/enterprises/me", {
        method: "PUT",
        body: JSON.stringify({
          realName: formData.realName,
          companyName: formData.companyName,
          jobTitle: formData.jobTitle,
          industry: formData.industry,
          companySize: formData.companySize,
          hiringTags: formData.hiringTags,
          bio: formData.bio,
          externalLinks: formData.externalLinks,
          preferences: formData.preferences,
        }),
      });
      const updatedAt = persistWorkspaceSnapshot({
        profile: response,
        certification,
      });
      setProfile(response);
      setFormData(buildProfileForm(response));
      setIsEditing(false);
      setLastUpdatedAt(updatedAt);
      showToast("企业资料已更新。");
    } catch (error) {
      showToast(buildErrorMessage(error, "企业资料保存失败，请稍后重试。"), "error");
    } finally {
      setSaving(false);
    }
  };

  const handleOpenCertificationModal = () => {
    setCertForm(createCertificationForm(profile, certification));
    setCertModalOpen(true);
  };

  const handleLogoUploaded = (payload: EnterpriseLogoUploadResult) => {
    const updatedAt = new Date().toISOString();
    setProfile((current) => {
      if (!current) {
        return current;
      }
      const nextProfile = {
        ...current,
        logoUrl: payload.logoUrl,
        logoConfigured: payload.logoConfigured,
        logoContentType: payload.contentType,
        logoUpdatedAt: payload.updatedAt ?? null,
      };
      // Logo 上传组件已经拿到新版本时间，立刻写快照让任务页和资料页刷新 URL。
      writeWorkspaceSnapshot(snapshotKey, {
        profile: nextProfile,
        certification,
      } satisfies EnterpriseProfileWorkspaceSnapshot, updatedAt);
      return nextProfile;
    });
    setLastUpdatedAt(updatedAt);
  };

  const handleCertificationFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null;
    setCertForm((current) => (current ? { ...current, file } : current));
  };

  const handleSubmitCertification = async () => {
    if (!certForm) {
      return;
    }
    if (!certForm.realName.trim() || !certForm.companyName.trim() || !certForm.jobTitle.trim()) {
      showToast("请先补全真实姓名、企业名称和联系人岗位后再重新提交认证。", "error");
      return;
    }
    if (!certForm.file) {
      showToast("请先选择最新的认证材料文件。", "error");
      return;
    }

    setCertForm({ ...certForm, busy: true });
    try {
      // 企业补件沿用统一认证接口，资料页提交成功后重新拉取审核视图。
      const payload = new FormData();
      payload.append("realName", certForm.realName.trim());
      payload.append("companyName", certForm.companyName.trim());
      payload.append("jobTitle", certForm.jobTitle.trim());
      payload.append("file", certForm.file);
      await apiRequest<CertificationSubmission>("/certification/me", {
        method: "POST",
        body: payload,
      });
      setCertModalOpen(false);
      await loadAll({ silent: true });
      showToast("认证材料已重新提交，等待平台审核。");
    } catch (error) {
      setCertForm((current) => (current ? { ...current, busy: false } : current));
      showToast(buildErrorMessage(error, "认证材料提交失败，请稍后重试。"), "error");
    }
  };

  const handleSendPasswordCode = async () => {
    setPasswordModal((current) => ({ ...current, busy: true }));
    try {
      // 企业改密复用资料安全服务，验证码始终发送到当前登录邮箱。
      const response = await apiRequest<SendCodeResponse>("/profiles/enterprises/me/security/password/send-code", {
        method: "POST",
      });
      setPasswordCountdown(calculateCountdown(response.nextSendAt));
      setPasswordModal((current) => ({
        ...current,
        debugHint: null,
      }));
      showToast(`验证码已发送至 ${response.targetEmail}`);
    } catch (error) {
      showToast(buildErrorMessage(error, "密码验证码发送失败，请稍后重试。"), "error");
    } finally {
      setPasswordModal((current) => ({ ...current, busy: false }));
    }
  };

  const handleVerifyPasswordCode = async () => {
    if (!passwordModal.code.trim()) {
      showToast("请输入邮箱收到的验证码。", "error");
      return;
    }

    setPasswordModal((current) => ({ ...current, busy: true }));
    try {
      const response = await apiRequest<VerifyCodeResponse>("/profiles/enterprises/me/security/password/verify-code", {
        method: "POST",
        body: JSON.stringify({ code: passwordModal.code.trim() }),
      });
      setPasswordModal((current) => ({
        ...current,
        busy: false,
        step: "changePassword",
        code: "",
        passwordResetToken: response.verificationToken,
        debugHint: null,
      }));
      setPasswordCountdown(0);
      showToast("验证通过，请设置新的登录密码。");
    } catch (error) {
      setPasswordModal((current) => ({ ...current, busy: false }));
      showToast(buildErrorMessage(error, "验证失败，请稍后重试。"), "error");
    }
  };

  const handleChangePassword = async () => {
    const nextPassword = passwordModal.newPassword.trim();
    if (!nextPassword) {
      showToast("请输入新的登录密码。", "error");
      return;
    }
    if (!/^(?=.*[A-Za-z])(?=.*\d).{8,64}$/.test(nextPassword)) {
      showToast("密码至少 8 位，并且需要同时包含字母和数字。", "error");
      return;
    }

    setPasswordModal((current) => ({ ...current, busy: true }));
    try {
      await apiRequest("/profiles/enterprises/me/security/password/change", {
        method: "POST",
        body: JSON.stringify({
          passwordResetToken: passwordModal.passwordResetToken,
          newPassword: nextPassword,
        }),
      });
      setPasswordModal(createPasswordModalState());
      setPasswordCountdown(0);
      showToast("登录密码已更新。");
    } catch (error) {
      setPasswordModal((current) => ({ ...current, busy: false }));
      showToast(buildErrorMessage(error, "密码修改失败，请稍后重试。"), "error");
    }
  };

  const handleOpenAsset = async (asset: CertificationAsset) => {
    setAssetLoadingId(asset.assetId);
    try {
      const response = await apiRequest<Response>(`/certification/assets/${asset.assetId}/content`, {
        rawResponse: true,
      });
      if (!(response instanceof Response) || !response.ok) {
        throw new ApiClientError("认证附件读取失败", response instanceof Response ? response.status : 500);
      }
      const blob = await response.blob();
      const objectUrl = window.URL.createObjectURL(blob);
      window.open(objectUrl, "_blank", "noopener,noreferrer");
      window.setTimeout(() => window.URL.revokeObjectURL(objectUrl), 60_000);
    } catch (error) {
      showToast(buildErrorMessage(error, "认证附件读取失败，请稍后重试。"), "error");
    } finally {
      setAssetLoadingId(null);
    }
  };

  const resolvedLogoUrl = buildEnterpriseLogoUrl(profile.logoUrl, profile.logoUpdatedAt);

  return (
    <EnterpriseProfileFrame
      displayName={heroName}
      companyName={companyName}
      logoUrl={resolvedLogoUrl}
      loading={refreshing}
      lastUpdatedAt={lastUpdatedAt}
      onRefresh={handleManualRefresh}
    >
      <motion.div variants={containerVariants} initial="hidden" animate="show" className="space-y-6">
        <motion.section variants={itemVariants} className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 className="text-3xl font-bold tracking-tight text-slate-900">企业资料与认证</h1>
            <p className="mt-2 text-[15px] text-slate-500">
              维护企业主体信息、企业介绍、招聘方向和认证资料。
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Link
              to="/enterprise/dashboard"
              className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:bg-slate-50 hover:text-slate-900"
            >
              <ArrowLeft size={16} className="mr-2" />
              返回工作台
            </Link>
            <Link
              to="/enterprise/tasks"
              className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:bg-slate-50 hover:text-slate-900"
            >
              去任务中心
            </Link>
          </div>
        </motion.section>

        <div className="sticky top-[5.25rem] z-30 rounded-[1.6rem] border border-white/70 bg-white/70 p-2 shadow-sm backdrop-blur-xl">
          <div className="mx-auto grid w-full max-w-3xl grid-cols-3 gap-2">
            {PROFILE_TABS.map((tab) => {
              const active = activeTab === tab.id;
              const Icon = tab.icon;
              return (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => handleTabChange(tab.id)}
                  className={joinClasses(
                    "relative flex min-h-[54px] items-center justify-center rounded-[1.15rem] px-2 py-2 text-sm font-semibold transition-all md:text-base",
                    active ? "text-indigo-700" : "text-slate-500 hover:bg-white/60 hover:text-slate-700",
                  )}
                >
                  {active ? (
                    <motion.div
                      layoutId="enterprise-profile-tab-indicator"
                      className="absolute inset-0 rounded-[1.15rem] border border-indigo-100/60 bg-white shadow-[0_4px_12px_rgba(99,102,241,0.08)]"
                    />
                  ) : null}
                  <span
                    className={joinClasses(
                      "relative z-10 mr-2 hidden h-7 w-7 items-center justify-center rounded-xl border transition-colors sm:flex",
                      active ? "border-indigo-100 bg-indigo-50 text-indigo-600" : "border-slate-200 bg-white/80 text-slate-400",
                    )}
                  >
                    <Icon size={14} />
                  </span>
                  <span className="relative z-10 whitespace-nowrap">{tab.label}</span>
                </button>
              );
            })}
          </div>
        </div>

        <AnimatePresence mode="wait">
          {activeTab === "profile" ? (
            <motion.div
              key="profile"
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -12 }}
              className="space-y-6"
            >
              <section
                id="identity"
                className="rounded-[2rem] border border-white/80 bg-white/85 p-6 shadow-[0_22px_60px_rgba(148,163,184,0.15)] backdrop-blur-xl sm:p-8"
              >
                <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <div className="flex items-center gap-2">
                      <Building2 size={20} className="text-indigo-500" />
                      <h2 className="text-[1.4rem] font-bold text-slate-900">企业身份主卡</h2>
                    </div>
                    <p className="mt-1.5 text-base leading-7 text-slate-500">
                      在这里维护企业名称、联系人身份、行业与规模等基础资料。
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-3">
                    {!isEditing ? (
                      <button
                        type="button"
                        onClick={handleStartEdit}
                        className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-4 py-2 text-[15px] font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
                      >
                        <Edit3 size={15} className="mr-2" />
                        编辑资料
                      </button>
                    ) : null}
                  </div>
                </div>

                <div className="grid gap-5 xl:grid-cols-[minmax(0,22rem)_minmax(0,1fr)] xl:items-start">
                  <EnterpriseLogoEditor
                    companyName={companyName}
                    logoUrl={resolvedLogoUrl}
                    logoConfigured={profile.logoConfigured}
                    logoUpdatedAt={profile.logoUpdatedAt}
                    onLogoUploaded={handleLogoUploaded}
                    onToast={showToast}
                  />

                  <div className="grid gap-5 md:grid-cols-2">
                    <label className="space-y-2">
                      <span className="text-[15px] font-semibold text-slate-700">平台账号名</span>
                      <input
                        value={profile.displayName}
                        disabled
                        className="w-full rounded-[1.15rem] border border-slate-200 bg-slate-50 px-4 py-3 text-[15px] text-slate-400 outline-none"
                      />
                    </label>

                    <label className="space-y-2">
                      <span className="text-[15px] font-semibold text-slate-700">企业名称</span>
                      <input
                        value={formData.companyName}
                        onChange={(event) => setFormData({ ...formData, companyName: event.target.value })}
                        disabled={!isEditing}
                        className={joinClasses(
                          "w-full rounded-[1.15rem] border px-4 py-3 text-[15px] outline-none transition-colors",
                          isEditing ? "border-slate-200 bg-white text-slate-800 focus:border-indigo-300" : "border-slate-200 bg-slate-50 text-slate-500",
                        )}
                      />
                    </label>

                    <label className="space-y-2">
                      <span className="text-[15px] font-semibold text-slate-700">联系人真实姓名</span>
                      <input
                        value={formData.realName}
                        onChange={(event) => setFormData({ ...formData, realName: event.target.value })}
                        disabled={!isEditing}
                        className={joinClasses(
                          "w-full rounded-[1.15rem] border px-4 py-3 text-[15px] outline-none transition-colors",
                          isEditing ? "border-slate-200 bg-white text-slate-800 focus:border-indigo-300" : "border-slate-200 bg-slate-50 text-slate-500",
                        )}
                      />
                    </label>

                    <label className="space-y-2">
                      <span className="text-[15px] font-semibold text-slate-700">职位 / 身份</span>
                      <input
                        value={formData.jobTitle}
                        onChange={(event) => setFormData({ ...formData, jobTitle: event.target.value })}
                        disabled={!isEditing}
                        className={joinClasses(
                          "w-full rounded-[1.15rem] border px-4 py-3 text-[15px] outline-none transition-colors",
                          isEditing ? "border-slate-200 bg-white text-slate-800 focus:border-indigo-300" : "border-slate-200 bg-slate-50 text-slate-500",
                        )}
                      />
                    </label>

                    <ProfileDropdownField
                      label="所在行业"
                      value={formData.industry}
                      options={buildOptions(formData.industry, INDUSTRY_OPTIONS)}
                      disabled={!isEditing}
                      placeholder="请选择所在行业"
                      onChange={(nextValue) => setFormData({ ...formData, industry: nextValue })}
                    />

                    <ProfileDropdownField
                      label="企业规模"
                      value={formData.companySize}
                      options={buildOptions(formData.companySize, COMPANY_SIZE_OPTIONS)}
                      disabled={!isEditing}
                      placeholder="请选择企业规模"
                      onChange={(nextValue) => setFormData({ ...formData, companySize: nextValue })}
                    />
                  </div>
                </div>
              </section>

              <section
                id="intro"
                className="rounded-[2rem] border border-white/80 bg-white/85 p-6 shadow-[0_22px_60px_rgba(148,163,184,0.15)] backdrop-blur-xl sm:p-8"
              >
                <div className="mb-6 flex items-center gap-2">
                  <FileText size={20} className="text-blue-500" />
                  <div>
                    <h2 className="text-[1.4rem] font-bold text-slate-900">企业介绍与补充信息</h2>
                    <p className="mt-1.5 text-base leading-7 text-slate-500">
                      补充企业介绍、公开链接和面向学生的说明。
                    </p>
                  </div>
                </div>

                <div className="space-y-5">
                  <div className="rounded-[1.5rem] border border-slate-100 bg-slate-50/60 p-5">
                    <label className="mb-3 flex items-center text-base font-bold text-slate-700">
                      <FileText size={16} className="mr-2 text-blue-600" />
                      企业简介
                    </label>
                    {isEditing ? (
                      <textarea
                        value={formData.bio}
                        onChange={(event) => setFormData({ ...formData, bio: event.target.value })}
                        rows={5}
                        placeholder="介绍企业背景、团队氛围或业务方向，帮助学生理解这家企业为什么值得参与。"
                        className="w-full rounded-[1.15rem] border border-slate-200 bg-white px-4 py-3 text-[15px] leading-7 text-slate-800 outline-none transition-colors focus:border-indigo-300"
                      />
                    ) : (
                      <p className="whitespace-pre-wrap text-[15px] leading-7 text-slate-600">
                        {formData.bio.trim() || "暂未填写企业简介。"}
                      </p>
                    )}
                  </div>

                  <div className="rounded-[1.5rem] border border-slate-100 bg-slate-50/60 p-5">
                    <label className="mb-3 flex items-center text-base font-bold text-slate-700">
                      <Globe size={16} className="mr-2 text-blue-600" />
                      联系方式与外部链接 <span className="ml-2 text-[14px] font-normal text-slate-400">可选</span>
                    </label>
                    {isEditing ? (
                      <textarea
                        value={formData.externalLinks}
                        onChange={(event) => setFormData({ ...formData, externalLinks: event.target.value })}
                        rows={4}
                        placeholder="可填写官网、招聘邮箱、公众号或其他对外链接。"
                        className="w-full rounded-[1.15rem] border border-slate-200 bg-white px-4 py-3 text-[15px] leading-7 text-slate-800 outline-none transition-colors focus:border-indigo-300"
                      />
                    ) : (
                      <p className="whitespace-pre-wrap text-[15px] leading-7 text-slate-600">
                        {formData.externalLinks.trim() || "暂未填写联系方式与外部链接。"}
                      </p>
                    )}
                  </div>

                  <div className="rounded-[1.5rem] border border-slate-100 bg-slate-50/60 p-5">
                    <label className="mb-3 flex items-center text-base font-bold text-slate-700">
                      <Info size={16} className="mr-2 text-blue-600" />
                      招募偏好与学生提示 <span className="ml-2 text-[14px] font-normal text-slate-400">可选</span>
                    </label>
                    {isEditing ? (
                      <textarea
                        value={formData.preferences}
                        onChange={(event) => setFormData({ ...formData, preferences: event.target.value })}
                        rows={4}
                        placeholder="可补充适合人群、希望重点关注的材料，或沟通说明。"
                        className="w-full rounded-[1.15rem] border border-slate-200 bg-white px-4 py-3 text-[15px] leading-7 text-slate-800 outline-none transition-colors focus:border-indigo-300"
                      />
                    ) : (
                      <p className="whitespace-pre-wrap text-[15px] leading-7 text-slate-600">
                        {formData.preferences.trim() || "暂未填写额外提示。"}
                      </p>
                    )}
                  </div>
                </div>
              </section>

              <section
                id="tags"
                className="rounded-[2rem] border border-white/80 bg-white/85 p-6 shadow-[0_22px_60px_rgba(148,163,184,0.15)] backdrop-blur-xl sm:p-8"
              >
                <div className="mb-6 flex items-center gap-2">
                  <Tags size={20} className="text-emerald-500" />
                  <div>
                    <h2 className="text-[1.4rem] font-bold text-slate-900">招聘方向标签</h2>
                    <p className="mt-1.5 text-base leading-7 text-slate-500">
                      用标签展示企业重点关注的方向。
                    </p>
                  </div>
                </div>

                <div className="space-y-5">
                  <div className="rounded-[1.5rem] border border-slate-100 bg-slate-50/70 p-4">
                    <div className="mb-3 text-base font-bold text-slate-700">已添加标签</div>
                    <div className="flex flex-wrap gap-2">
                      {formData.hiringTags.length > 0 ? (
                        formData.hiringTags.map((tag) => (
                          <span
                            key={tag}
                            className="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1.5 text-[15px] font-semibold text-emerald-700"
                          >
                            {tag}
                            {isEditing ? (
                              <button type="button" onClick={() => handleRemoveTag(tag)} className="text-emerald-500 hover:text-emerald-700">
                                <X size={14} />
                              </button>
                            ) : null}
                          </span>
                        ))
                      ) : (
                        <div className="rounded-2xl border border-dashed border-slate-200 bg-white px-4 py-5 text-[15px] leading-7 text-slate-500">
                          暂未设置招聘方向标签。
                        </div>
                      )}
                    </div>
                  </div>

                  {isEditing ? (
                    <div className="rounded-[1.5rem] border border-slate-100 bg-white p-4 shadow-sm">
                      <div className="mb-3 text-base font-bold text-slate-700">添加标签</div>
                      <div className="flex flex-col gap-3 sm:flex-row">
                        <input
                          value={tagInput}
                          onChange={(event) => setTagInput(event.target.value)}
                          onKeyDown={(event) => {
                            if (event.key === "Enter") {
                              event.preventDefault();
                              handleAddTag();
                            }
                          }}
                          placeholder="输入招聘方向，如：算法工程 / 增长运营"
                          className="flex-1 rounded-[1.15rem] border border-slate-200 px-4 py-3 text-[15px] text-slate-800 outline-none transition-colors focus:border-indigo-300"
                        />
                        <button
                          type="button"
                          onClick={() => handleAddTag()}
                          className="inline-flex items-center justify-center rounded-[1.15rem] bg-slate-900 px-5 py-3 text-[15px] font-semibold text-white transition-colors hover:bg-slate-800"
                        >
                          <Plus size={16} className="mr-2" />
                          添加方向
                        </button>
                      </div>
                      <div className="mt-4 flex flex-wrap gap-2">
                        {suggestedTags.map((tag) => (
                          <button
                            key={tag}
                            type="button"
                            onClick={() => handleAddTag(tag)}
                            className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-[14px] font-semibold text-slate-600 transition-colors hover:border-indigo-200 hover:bg-indigo-50 hover:text-indigo-600"
                          >
                            + {tag}
                          </button>
                        ))}
                      </div>
                    </div>
                  ) : null}
                </div>
              </section>
            </motion.div>
          ) : null}

          {activeTab === "certification" ? (
            <motion.div
              key="certification"
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -12 }}
              className="space-y-6"
            >
              <section
                id="certification"
                className="mx-auto w-full max-w-4xl rounded-[2rem] border border-white/80 bg-white/85 p-8 text-center shadow-[0_22px_60px_rgba(148,163,184,0.15)] backdrop-blur-xl"
              >
                <div className={joinClasses("mx-auto mb-5 flex h-20 w-20 items-center justify-center rounded-full", approvalMeta.panelClassName)}>
                  <ShieldCheck size={36} className="opacity-90" />
                </div>
                <h2 className="text-[1.85rem] font-bold text-slate-900">{approvalMeta.title}</h2>
                <p className="mx-auto mt-3 max-w-2xl text-base leading-7 text-slate-500">{approvalMeta.description}</p>
                <div className="mt-6 flex justify-center">
                  <button
                    type="button"
                    onClick={handleOpenCertificationModal}
                    className="inline-flex items-center justify-center rounded-full bg-slate-900 px-5 py-2.5 text-[15px] font-semibold text-white shadow-sm transition-colors hover:bg-slate-800"
                  >
                    <UploadCloud size={15} className="mr-2" />
                    重新提交认证
                  </button>
                </div>

                <div className="mt-8 rounded-[1.6rem] border border-slate-100 bg-slate-50/85 p-5 text-left">
                  <div className="mb-4 flex items-center justify-between gap-3">
                    <div className="text-base font-bold text-slate-800">认证资料概览</div>
                    {currentSubmission?.current ? (
                      <span className="rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-1 text-[12px] font-semibold text-emerald-700">
                        提交 #{currentSubmission.submissionId}
                      </span>
                    ) : null}
                  </div>

                  {currentSubmission ? (
                    <div className="space-y-4">
                      <div className="grid gap-3 md:grid-cols-2">
                        <SummaryLine label="认证状态" value={approvalMeta.label} />
                        <SummaryLine label="最近提交" value={formatDateTime(currentSubmission.submittedAt)} />
                        <SummaryLine label="认证实名" value={currentSubmission.realName} />
                        <SummaryLine label="企业名称" value={currentSubmission.companyName} />
                        <SummaryLine label="岗位身份" value={currentSubmission.jobTitle} />
                        <SummaryLine label="最近审核" value={formatDateTime(currentSubmission.reviewedAt)} />
                      </div>

                      <div className="rounded-xl border border-white bg-white px-4 py-4">
                        <div className="text-sm font-bold text-slate-500">审核备注</div>
                        <p className="mt-3 text-[15px] leading-7 text-slate-600">{approvalMeta.reviewMessage}</p>
                      </div>

                      <div className="rounded-xl border border-white bg-white px-4 py-4">
                        <div className="flex items-center gap-2 text-sm font-bold text-slate-500">
                          <Clock3 size={14} />
                          认证附件
                        </div>
                        <div className="mt-3 space-y-2">
                          {currentAssets.length > 0 ? (
                            currentAssets.map((asset) => (
                              <button
                                key={asset.assetId}
                                type="button"
                                onClick={() => void handleOpenAsset(asset)}
                                disabled={assetLoadingId === asset.assetId}
                                className="flex w-full items-center justify-between rounded-xl border border-slate-200 bg-slate-50/60 px-4 py-3 text-left transition-colors hover:border-indigo-200 hover:bg-indigo-50/70 disabled:cursor-not-allowed disabled:opacity-60"
                              >
                                <div className="min-w-0">
                                  <div className="truncate text-[15px] font-semibold text-slate-800">{asset.originalFilename}</div>
                                  <div className="mt-1 text-[13px] text-slate-500">{formatFileSize(asset.sizeBytes)} · {formatDateTime(asset.uploadedAt)}</div>
                                </div>
                                <span className="shrink-0 text-[13px] font-semibold text-indigo-600">
                                  {assetLoadingId === asset.assetId ? "打开中..." : "查看"}
                                </span>
                              </button>
                            ))
                          ) : (
                            <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50/60 px-4 py-4 text-[15px] leading-7 text-slate-500">
                              暂未上传可查看的认证附件。
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  ) : (
                    <div className="rounded-xl border border-dashed border-slate-200 bg-white px-4 py-5 text-[15px] leading-7 text-slate-500">
                      暂无认证提交记录，请先准备企业认证材料。
                    </div>
                  )}
                </div>
              </section>
            </motion.div>
          ) : null}

          {activeTab === "notifications" ? (
            <motion.div
              key="notifications"
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -12 }}
              className="space-y-6"
            >
              <section id="notifications">
                <LazyProfileNotificationPreferencesPanel
                  variant="enterprise"
                  title="通知提醒"
                  description="在这里设置企业侧通知提醒方式。"
                />
              </section>
            </motion.div>
          ) : null}
        </AnimatePresence>

        <AnimatePresence>
          {toast || isEditing ? (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="pointer-events-none fixed bottom-0 left-0 right-0 z-[60] p-4 sm:p-6"
            >
              <div className="mx-auto flex w-full max-w-3xl flex-col gap-3">
                {toast ? (
                  <motion.div
                    key={`toast-${toast.tone}-${toast.message}`}
                    initial={{ y: 30, opacity: 0, scale: 0.97 }}
                    animate={{ y: 0, opacity: 1, scale: 1 }}
                    exit={{ y: 18, opacity: 0, scale: 0.98 }}
                    className="pointer-events-auto"
                  >
                    <div className={joinClasses(
                      "mx-auto flex w-full items-center gap-4 overflow-hidden rounded-full px-6 py-3.5 shadow-2xl backdrop-blur-xl",
                      "transition-[background-color,border-color,box-shadow] duration-300 ease-out",
                      getToastPresentation(toast.tone).shellClassName,
                    )}>
                      <div className={joinClasses(
                        "flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition-colors duration-300",
                        getToastPresentation(toast.tone).iconClassName,
                      )}>
                        {(() => {
                          const ToastIcon = getToastPresentation(toast.tone).icon;
                          return <ToastIcon size={18} />;
                        })()}
                      </div>
                      <div className="min-w-0 flex-1 text-center">
                        <div className={joinClasses(
                          "truncate text-base font-black tracking-[0.02em]",
                          getToastPresentation(toast.tone).titleClassName,
                        )}>
                          {getToastPresentation(toast.tone).title}
                        </div>
                        <p className={joinClasses(
                          "truncate text-[15px] leading-5",
                          getToastPresentation(toast.tone).messageClassName,
                        )}>
                          {toast.message}
                        </p>
                      </div>
                      <button
                        type="button"
                        onClick={() => setToast(null)}
                        className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-slate-200 bg-white/80 text-slate-500 transition-colors duration-300 hover:border-slate-300 hover:bg-white hover:text-slate-700"
                      >
                        <X size={15} />
                      </button>
                    </div>
                  </motion.div>
                ) : null}

                {isEditing ? (
                  <motion.div
                    key="enterprise-editor-floating-bar"
                    initial={{ y: 44, opacity: 0 }}
                    animate={{ y: 0, opacity: 1 }}
                    exit={{ y: 44, opacity: 0 }}
                    transition={{ type: "spring", stiffness: 300, damping: 30 }}
                    className="pointer-events-auto"
                  >
                    <div className="flex w-full items-center justify-between gap-4 rounded-[1.5rem] border border-white/80 bg-white/92 px-5 py-4 shadow-[0_24px_48px_rgba(15,23,42,0.16)] backdrop-blur-xl">
                      <div>
                        <div className="text-sm font-bold text-slate-900">企业资料正在编辑中</div>
                        <div className="text-[13px] text-slate-500">修改内容将在保存后生效。</div>
                      </div>
                      <div className="flex items-center gap-3">
                        <button
                          type="button"
                          onClick={handleReset}
                          className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900"
                        >
                          取消修改
                        </button>
                        <button
                          type="button"
                          onClick={() => void handleSave()}
                          disabled={!isDirty || saving}
                          className="inline-flex items-center justify-center rounded-full bg-slate-900 px-5 py-2 text-sm font-semibold text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          {saving ? "保存中..." : "保存资料"}
                        </button>
                      </div>
                    </div>
                  </motion.div>
                ) : null}
              </div>
            </motion.div>
          ) : null}
        </AnimatePresence>

        <AnimatePresence>
          {certModalOpen && certForm ? (
            <>
              <motion.button
                type="button"
                aria-label="关闭认证提交弹窗"
                className="fixed inset-0 z-40 bg-slate-950/30 backdrop-blur-[2px]"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                onClick={() => setCertModalOpen(false)}
              />
              <motion.div
                initial={{ opacity: 0, y: 24 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: 24 }}
                className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,42rem)] -translate-x-1/2 -translate-y-1/2 rounded-[2rem] border border-white/80 bg-white/96 p-6 shadow-[0_30px_80px_rgba(15,23,42,0.2)] backdrop-blur-xl sm:p-7"
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="flex min-h-[4.1rem] flex-col justify-center">
                    <h3 className="text-2xl font-bold leading-tight text-slate-950">重新提交企业认证材料</h3>
                    <p className="mt-2 text-[15px] leading-7 text-slate-500">
                      将基于现有资料重新提交认证材料。
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setCertModalOpen(false)}
                    className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:border-slate-300 hover:text-slate-900"
                  >
                    <X size={18} />
                  </button>
                </div>

                <div className="mt-6 grid gap-4 md:grid-cols-2">
                  <label className="space-y-2">
                    <span className="text-sm font-semibold text-slate-700">联系人真实姓名</span>
                    <input
                      value={certForm.realName}
                      onChange={(event) => setCertForm({ ...certForm, realName: event.target.value })}
                      className="w-full rounded-[1.15rem] border border-slate-200 px-4 py-3 text-sm text-slate-800 outline-none transition-colors focus:border-indigo-300"
                    />
                  </label>

                  <label className="space-y-2">
                    <span className="text-sm font-semibold text-slate-700">企业名称</span>
                    <input
                      value={certForm.companyName}
                      onChange={(event) => setCertForm({ ...certForm, companyName: event.target.value })}
                      className="w-full rounded-[1.15rem] border border-slate-200 px-4 py-3 text-sm text-slate-800 outline-none transition-colors focus:border-indigo-300"
                    />
                  </label>

                  <label className="space-y-2 md:col-span-2">
                    <span className="text-sm font-semibold text-slate-700">职位 / 身份</span>
                    <input
                      value={certForm.jobTitle}
                      onChange={(event) => setCertForm({ ...certForm, jobTitle: event.target.value })}
                      className="w-full rounded-[1.15rem] border border-slate-200 px-4 py-3 text-sm text-slate-800 outline-none transition-colors focus:border-indigo-300"
                    />
                  </label>
                </div>

                <div className="mt-5 rounded-[1.5rem] border border-slate-100 bg-slate-50/80 p-4">
                  <div className="text-sm font-semibold text-slate-700">认证材料文件</div>
                  <p className="mt-1 text-[13px] leading-6 text-slate-500">请上传联系人身份证明、企业工牌、营业执照或其他有效材料。</p>
                  <input
                    type="file"
                    onChange={handleCertificationFileChange}
                    className="mt-4 block w-full text-sm text-slate-600 file:mr-4 file:rounded-full file:border-0 file:bg-slate-900 file:px-4 file:py-2 file:text-sm file:font-semibold file:text-white hover:file:bg-slate-800"
                  />
                  {certForm.file ? (
                    <div className="mt-3 rounded-xl border border-emerald-100 bg-white px-3 py-2 text-[13px] text-slate-600">
                      已选择：{certForm.file.name}
                    </div>
                  ) : null}
                </div>

                <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                  <button
                    type="button"
                    onClick={() => setCertModalOpen(false)}
                    className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900"
                  >
                    稍后再说
                  </button>
                  <button
                    type="button"
                    onClick={() => void handleSubmitCertification()}
                    disabled={certForm.busy}
                    className="inline-flex items-center justify-center rounded-full bg-slate-900 px-6 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {certForm.busy ? "提交中..." : "提交新版本"}
                  </button>
                </div>
              </motion.div>
            </>
          ) : null}
        </AnimatePresence>

        <AnimatePresence>
          {passwordModal.isOpen ? (
            <ModalOverlay
              onClose={() => {
                if (passwordModal.busy) {
                  return;
                }
                setPasswordModal(createPasswordModalState());
                setPasswordCountdown(0);
              }}
            >
              <div className="mb-6 text-center">
                <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-rose-50 text-rose-500">
                  <KeyRound size={32} />
                </div>
                <h3 className="text-2xl font-semibold text-slate-900">修改登录密码</h3>
                <p className="mt-2 text-sm font-medium text-slate-500">
                  {passwordModal.step === "verifyCode"
                    ? `请先完成邮箱 ${email ?? "当前邮箱"} 的验证`
                    : "请设置新的登录密码"}
                </p>
              </div>

              <div className="space-y-5">
                {passwordModal.step === "verifyCode" ? (
                  <div>
                    <label className="mb-2 block text-sm font-bold text-slate-700">验证码</label>
                    <div className="flex gap-3">
                      <input
                        type="text"
                        maxLength={6}
                        value={passwordModal.code}
                        onChange={(event) => setPasswordModal((current) => ({
                          ...current,
                          code: event.target.value.replace(/\D/g, ""),
                        }))}
                        placeholder="6位数字"
                        className="min-w-0 flex-1 rounded-xl border-2 border-slate-200 bg-slate-50 px-4 py-3 text-center text-sm font-semibold tracking-[0.28em] text-slate-700 outline-none transition-colors focus:border-emerald-400 focus:bg-white"
                      />
                      <button
                        type="button"
                        disabled={passwordCountdown > 0 || passwordModal.busy}
                        onClick={() => void handleSendPasswordCode()}
                        className="whitespace-nowrap rounded-xl border border-emerald-100 bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-700 transition-colors hover:bg-emerald-100 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        {passwordCountdown > 0 ? `${passwordCountdown}s 后重发` : "获取验证码"}
                      </button>
                    </div>
                  </div>
                ) : (
                  <div>
                    <label className="mb-2 block text-sm font-bold text-slate-700">新密码</label>
                    <input
                      type="password"
                      value={passwordModal.newPassword}
                      onChange={(event) => setPasswordModal((current) => ({ ...current, newPassword: event.target.value }))}
                      placeholder="至少 8 位，包含字母和数字"
                      className="w-full rounded-xl border-2 border-slate-200 bg-slate-50 px-4 py-3 text-sm font-medium text-slate-700 outline-none transition-colors focus:border-emerald-400 focus:bg-white"
                    />
                    <p className="mt-2 text-xs leading-6 text-slate-500">
                      新密码至少 8 位，并需同时包含字母和数字。
                    </p>
                  </div>
                )}

                <button
                  type="button"
                  disabled={passwordModal.busy}
                  onClick={() => {
                    if (passwordModal.step === "verifyCode") {
                      void handleVerifyPasswordCode();
                    } else {
                      void handleChangePassword();
                    }
                  }}
                  className="inline-flex w-full items-center justify-center rounded-xl bg-slate-900 px-5 py-3.5 text-sm font-semibold text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {passwordModal.busy ? (
                    <>
                      <RefreshCw size={16} className="mr-2 animate-spin" />
                      正在处理
                    </>
                  ) : passwordModal.step === "verifyCode" ? (
                    "验证并下一步"
                  ) : (
                    "完成修改"
                  )}
                </button>
              </div>
            </ModalOverlay>
          ) : null}
        </AnimatePresence>
      </motion.div>
    </EnterpriseProfileFrame>
  );
}

function ModalOverlay({
  children,
  onClose,
  maxWidthClassName = "max-w-md",
}: {
  children: ReactNode;
  onClose: () => void;
  maxWidthClassName?: string;
}) {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-[70] flex items-center justify-center bg-slate-900/40 p-4 backdrop-blur-sm"
      onClick={onClose}
    >
      <motion.div
        initial={{ scale: 0.94, y: 18, opacity: 0 }}
        animate={{ scale: 1, y: 0, opacity: 1 }}
        exit={{ scale: 0.96, y: 18, opacity: 0 }}
        transition={{ type: "spring", bounce: 0.3 }}
        className={joinClasses("relative w-full rounded-[2rem] bg-white p-8 shadow-2xl", maxWidthClassName)}
        onClick={(event) => event.stopPropagation()}
      >
        <button
          type="button"
          onClick={onClose}
          className="absolute right-5 top-5 flex h-8 w-8 items-center justify-center rounded-full bg-slate-100 text-slate-500 transition-colors hover:bg-slate-200"
        >
          <X size={18} />
        </button>
        {children}
      </motion.div>
    </motion.div>
  );
}
