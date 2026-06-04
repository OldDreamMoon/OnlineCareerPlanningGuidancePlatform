import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  Award,
  BellRing,
  BriefcaseBusiness,
  BookOpen,
  Building2,
  Check,
  CheckCircle2,
  Edit3,
  Eye,
  Github,
  Globe,
  GraduationCap,
  Heart,
  Info,
  KeyRound,
  Link as LinkIcon,
  Mail,
  MessageSquare,
  Plus,
  RefreshCw,
  Shield,
  Smartphone,
  Sparkles,
  Target,
  Trophy,
  User,
  Users,
  X,
  type LucideIcon,
} from "lucide-react";
import { lazy, Suspense, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import LazyProfileNotificationPreferencesPanel from "../components/notifications/LazyProfileNotificationPreferencesPanel";
import WorkspacePageLoadingScreen from "../components/WorkspacePageLoadingScreen";
import type { StudentAvatarMeta, StudentAvatarUploadResult } from "../components/profile/StudentAvatarEditor";
import StudentWorkspaceTopbar, { buildStudentWorkspacePrimaryNav } from "../components/student/StudentWorkspaceTopbar";
import { ApiClientError, apiRequest } from "../lib/apiClient";
import { formatCount, formatDateTime, toTimestamp } from "../lib/formatters";
import { getRoleDisplayLabel } from "../lib/roleLabels";
import { buildStudentNickname } from "../lib/studentNames";

const StudentAvatarEditor = lazy(() => import("../components/profile/StudentAvatarEditor"));

type TimeValue = number | string;

type VisibilityMatrix = {
  guest: boolean;
  student: boolean;
  platformStudent: boolean;
  mentor: boolean;
  enterprise: boolean;
};

type PrivacySettings = {
  realName: VisibilityMatrix;
  jobStatus: VisibilityMatrix;
  eduInfo: VisibilityMatrix;
  targetPos: VisibilityMatrix;
  academic: VisibilityMatrix;
  skills: VisibilityMatrix;
  intro: VisibilityMatrix;
  social: VisibilityMatrix;
  email: VisibilityMatrix;
  phone: VisibilityMatrix;
  wechat: VisibilityMatrix;
  portrait: VisibilityMatrix;
};

type StudentProfileResponse = {
  userId: number;
  displayName: string;
  email: string;
  tier: string;
  completionRate: number;
  realName: string | null;
  jobStatus: string | null;
  schoolName: string | null;
  major: string | null;
  grade: string | null;
  gpa: string | null;
  targetPosition: string | null;
  honors: string | null;
  github: string | null;
  portfolio: string | null;
  socialLinks: StudentSocialLink[] | null;
  phone: string | null;
  wechat: string | null;
  skillTags: string[] | null;
  selfIntro: string | null;
  avatar: StudentAvatarMeta | null;
  privacy: PrivacySettings;
  portrait: {
    tags: Array<{
      code: string;
      label: string;
      source: string;
      confidence: number | null;
    }> | null;
    strengthTags: string[] | null;
    riskTags: string[] | null;
    signalLevel: string | null;
    freshnessLevel: string | null;
    headline: string | null;
    summary: string | null;
    nextActions: string[] | null;
    summaryVersion: string | null;
    evidence: {
      masteredSkills: number;
      learningSkills: number;
      interviewMessages7d: number;
      posts7d: number;
      comments7d: number;
      likesReceived7d: number;
    } | null;
    updatedAt: TimeValue | null;
  } | null;
  communityScore7d: number;
};

type ProfileFormData = {
  displayName: string;
  realName: string;
  jobStatus: string;
  schoolName: string;
  grade: string;
  major: string;
  gpa: string;
  targetPosition: string;
  honors: string;
  socialLinks: StudentSocialLink[];
  phone: string;
  wechat: string;
  skillTags: string[];
  selfIntro: string;
};

type SocialPlatformCode =
  | "GITHUB"
  | "PORTFOLIO"
  | "GITEE"
  | "JUEJIN"
  | "CSDN"
  | "ZHIHU"
  | "BILIBILI"
  | "XIAOHONGSHU"
  | "WEIBO";

type StudentSocialLink = {
  platform: SocialPlatformCode;
  value: string;
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

type EmailChangeResponse = {
  updated: boolean;
  email: string;
};

type ToastState = {
  tone: "success" | "error" | "info" | "warning" | "neutral" | "cancel";
  message: string;
};

type DebugCodeHint = {
  code: string;
  targetEmail: string;
};

type EmailModalState = {
  isOpen: boolean;
  step: "verifyCurrent" | "changeNew";
  currentCode: string;
  newEmail: string;
  newEmailCode: string;
  currentEmailVerificationToken: string;
  busy: boolean;
  debugHint: DebugCodeHint | null;
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

type PageState = "loading" | "ready" | "error";
type TabKey = "profile" | "portrait" | "privacy" | "notifications" | "security";
type PrivacyFieldKey = keyof PrivacySettings;
type HeroBadgeTone = "target" | "school" | "major" | "grade";

const JOB_STATUS_OPTIONS = ["🟢 积极找工作", "🟡 观望机会中", "🔴 暂无求职意向"];
const GRADE_OPTIONS = ["2024届", "2025届", "2026届", "2027届"];
const SOCIAL_PLATFORM_OPTIONS: Array<{
  value: SocialPlatformCode;
  label: string;
  placeholder: string;
  helper: string;
  icon: LucideIcon;
  assetPath?: string;
  baseUrl?: string;
}> = [
  { value: "GITHUB", label: "GitHub", placeholder: "例如：lin-frontend 或 github.com/lin-frontend", helper: "代码仓库 / 开源主页", icon: Github, assetPath: "/social-platform-icons/github.svg", baseUrl: "https://github.com/" },
  { value: "PORTFOLIO", label: "个人网站 / 作品集", placeholder: "例如：lin-blog.tech", helper: "个人站、作品集或在线简历", icon: Globe, assetPath: "/social-platform-icons/portfolio-placeholder.svg" },
  { value: "GITEE", label: "Gitee", placeholder: "例如：lin-frontend", helper: "国内代码托管主页", icon: LinkIcon, assetPath: "/social-platform-icons/gitee.svg", baseUrl: "https://gitee.com/" },
  { value: "JUEJIN", label: "掘金", placeholder: "例如：lin_frontend", helper: "技术文章与个人主页", icon: Sparkles, assetPath: "/social-platform-icons/juejin.svg", baseUrl: "https://juejin.cn/user/" },
  { value: "CSDN", label: "CSDN", placeholder: "例如：lin_backend_notes", helper: "技术博客与专栏主页", icon: BookOpen, assetPath: "/social-platform-icons/csdn.svg", baseUrl: "https://blog.csdn.net/" },
  { value: "ZHIHU", label: "知乎", placeholder: "例如：lin-tech", helper: "问答、专栏与个人主页", icon: MessageSquare, assetPath: "/social-platform-icons/zhihu.svg", baseUrl: "https://www.zhihu.com/people/" },
  { value: "BILIBILI", label: "哔哩哔哩", placeholder: "例如：lin_frontend", helper: "视频内容与个人空间", icon: Sparkles, assetPath: "/social-platform-icons/bilibili.svg", baseUrl: "https://space.bilibili.com/" },
  { value: "XIAOHONGSHU", label: "小红书", placeholder: "例如：lin_notes", helper: "内容笔记与主页账号", icon: Heart, assetPath: "/social-platform-icons/xiaohongshu.svg", baseUrl: "https://www.xiaohongshu.com/user/profile/" },
  { value: "WEIBO", label: "微博", placeholder: "例如：lin_campus", helper: "公开动态与账号主页", icon: MessageSquare, assetPath: "/social-platform-icons/weibo.ico", baseUrl: "https://weibo.com/u/" },
];

const PROFILE_TABS: Array<{ id: TabKey; label: string; icon: LucideIcon }> = [
  { id: "profile", label: "基础信息", icon: BriefcaseBusiness },
  { id: "portrait", label: "成长画像", icon: Sparkles },
  { id: "privacy", label: "可见范围", icon: Eye },
  { id: "notifications", label: "通知提醒", icon: BellRing },
  { id: "security", label: "安全设置", icon: Shield },
];

const PROFILE_TAB_DESCRIPTIONS: Record<TabKey, string> = {
  profile: "完善基础资料、社交账号和联系方式。",
  portrait: "查看当前成长画像与阶段表现。",
  privacy: "设置不同身份可见的资料范围。",
  notifications: "管理各类通知的提醒方式与触达渠道。",
  security: "管理邮箱与密码等账号安全设置。",
};

const PRIVACY_GROUPS: Array<{
  title: string;
  icon: LucideIcon;
  rows: Array<{ key: PrivacyFieldKey; label: string; isSensitive?: boolean }>;
}> = [
  {
    title: "基础资料",
    icon: BookOpen,
    rows: [
      { key: "realName", label: "真实姓名" },
      { key: "jobStatus", label: "求职意向状态" },
      { key: "eduInfo", label: "学校 / 年级 / 专业" },
      { key: "targetPos", label: "目标岗位" },
      { key: "academic", label: "GPA 与荣誉奖项" },
      { key: "skills", label: "技能标签" },
      { key: "intro", label: "个人简介" },
    ],
  },
  {
    title: "个人主页与社交账号",
    icon: Globe,
    rows: [{ key: "social", label: "外部主页账号" }],
  },
  {
    title: "敏感联系方式",
    icon: Smartphone,
    rows: [
      { key: "email", label: "联系邮箱", isSensitive: true },
      { key: "phone", label: "手机号码", isSensitive: true },
      { key: "wechat", label: "微信号码", isSensitive: true },
    ],
  },
  {
    title: "成长画像",
    icon: Sparkles,
    rows: [{ key: "portrait", label: "成长画像" }],
  },
];

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function getSocialPlatformOption(platform: SocialPlatformCode) {
  return SOCIAL_PLATFORM_OPTIONS.find((option) => option.value === platform) ?? SOCIAL_PLATFORM_OPTIONS[0];
}

function normalizeClientSocialLinks(socialLinks: Array<StudentSocialLink | null | undefined> | null | undefined) {
  const normalized = new Map<SocialPlatformCode, StudentSocialLink>();

  for (const socialLink of socialLinks ?? []) {
    if (!socialLink) {
      continue;
    }

    const value = socialLink.value?.trim();
    if (!value) {
      continue;
    }

    normalized.set(socialLink.platform, {
      platform: socialLink.platform,
      value,
    });
  }

  return Array.from(normalized.values());
}

function buildInitialSocialLinks(profile: StudentProfileResponse) {
  const normalized = normalizeClientSocialLinks(profile.socialLinks);
  if (normalized.length > 0) {
    return normalized;
  }

  return normalizeClientSocialLinks([
    profile.github?.trim() ? { platform: "GITHUB", value: profile.github.trim() } : null,
    profile.portfolio?.trim() ? { platform: "PORTFOLIO", value: profile.portfolio.trim() } : null,
  ]);
}

function createEmptySocialLink(platform?: SocialPlatformCode): StudentSocialLink {
  return {
    platform: platform ?? SOCIAL_PLATFORM_OPTIONS[0].value,
    value: "",
  };
}

function findSocialLinkValue(socialLinks: StudentSocialLink[], platform: SocialPlatformCode) {
  return socialLinks.find((socialLink) => socialLink.platform === platform)?.value?.trim() ?? "";
}

function buildProfileFormData(profile: StudentProfileResponse): ProfileFormData {
  return {
    displayName: profile.displayName ?? "",
    realName: profile.realName ?? "",
    jobStatus: profile.jobStatus ?? "",
    schoolName: profile.schoolName ?? "",
    grade: profile.grade ?? "",
    major: profile.major ?? "",
    gpa: profile.gpa ?? "",
    targetPosition: profile.targetPosition ?? "",
    honors: profile.honors ?? "",
    socialLinks: buildInitialSocialLinks(profile),
    phone: profile.phone ?? "",
    wechat: profile.wechat ?? "",
    skillTags: [...(profile.skillTags ?? [])],
    selfIntro: profile.selfIntro ?? "",
  };
}

function clonePrivacySettings(privacy: PrivacySettings): PrivacySettings {
  return JSON.parse(JSON.stringify(privacy)) as PrivacySettings;
}

function calculateCountdown(nextSendAt: TimeValue | null | undefined) {
  if (!nextSendAt) {
    return 60;
  }

  const seconds = Math.ceil((toTimestamp(nextSendAt) - Date.now()) / 1000);
  return Math.max(seconds, 0);
}

function createEmailModalState(overrides?: Partial<EmailModalState>): EmailModalState {
  return {
    isOpen: false,
    step: "verifyCurrent",
    currentCode: "",
    newEmail: "",
    newEmailCode: "",
    currentEmailVerificationToken: "",
    busy: false,
    debugHint: null,
    ...overrides,
  };
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

function StudentAvatarEditorFallback({ heroName }: { heroName: string }) {
  const initial = heroName.trim().charAt(0).toUpperCase() || "S";

  return (
    <div className="flex items-center gap-4">
      <div className="relative flex h-24 w-24 items-center justify-center overflow-hidden rounded-[2rem] border border-slate-200/80 bg-gradient-to-br from-slate-100 to-white text-3xl font-black text-slate-500 shadow-[0_16px_35px_rgba(148,163,184,0.18)]">
        <span>{initial}</span>
        <div className="absolute bottom-2 right-2 flex h-8 w-8 animate-spin items-center justify-center rounded-full border border-white/90 bg-white/90 shadow-sm">
          <div className="h-3.5 w-3.5 rounded-full border-2 border-slate-200 border-t-slate-700" />
        </div>
      </div>
      <div className="hidden min-w-0 sm:block">
        <div className="text-xs font-bold text-slate-400">Avatar Editor</div>
        <div className="mt-1 text-sm font-semibold text-slate-600">正在加载头像编辑器...</div>
      </div>
    </div>
  );
}

function buildExternalHref(value: string) {
  const normalized = value.trim();
  if (!normalized) {
    return "#";
  }

  if (/^https?:\/\//i.test(normalized)) {
    return normalized;
  }

  return `https://${normalized}`;
}

function buildSocialLinkHref(platform: SocialPlatformCode, value: string) {
  const normalized = value.trim();
  if (!normalized) {
    return "#";
  }

  if (/^https?:\/\//i.test(normalized)) {
    return normalized;
  }

  if (/^(?:www\.)?[\w.-]+\.[a-z]{2,}(?:[/?#].*)?$/i.test(normalized)) {
    return `https://${normalized}`;
  }

  const option = getSocialPlatformOption(platform);
  if (option.baseUrl) {
    return `${option.baseUrl}${encodeURIComponent(normalized.replace(/^@/, ""))}`;
  }

  return buildExternalHref(normalized);
}

function getTierPresentation(tier: string | null | undefined) {
  if (tier === "PREMIUM") {
    return {
      label: "VIP 会员",
      description: "已解锁更多成长权益，并拥有更醒目的身份展示。",
      badgeClassName: "rounded-full border border-amber-200 bg-gradient-to-r from-amber-50 to-orange-50 px-3 py-1 text-xs font-bold text-amber-700 shadow-sm",
    };
  }

  return {
    label: "普通用户",
    description: "当前为基础会员身份，可正常展示个人资料与头像信息。",
    badgeClassName: "rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-bold text-slate-600 shadow-sm",
  };
}

function formatConfidence(confidence: number | null | undefined) {
  if (confidence === null || confidence === undefined) {
    return "待补充";
  }

  return `${Math.round(confidence * 100)}%`;
}

function buildErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiClientError) {
    return error.message || fallback;
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

  if (tone === "warning") {
    return {
      title: "请先处理",
      icon: AlertCircle,
      shellClassName: "border border-amber-100/90 bg-[#fff9ef] ring-1 ring-amber-100/90 shadow-[0_18px_40px_rgba(245,158,11,0.14)]",
      iconClassName: "bg-amber-100 text-amber-600",
      titleClassName: "text-amber-700",
      messageClassName: "text-slate-600",
    };
  }

  if (tone === "neutral") {
    return {
      title: "已恢复",
      icon: X,
      shellClassName: "border border-slate-200/90 bg-[#f7f9fc] ring-1 ring-slate-200/90 shadow-[0_18px_40px_rgba(148,163,184,0.12)]",
      iconClassName: "bg-slate-200/80 text-slate-600",
      titleClassName: "text-slate-700",
      messageClassName: "text-slate-600",
    };
  }

  if (tone === "cancel") {
    return {
      title: "已取消",
      icon: X,
      shellClassName: "border border-rose-100/90 bg-[#fff3f5] ring-1 ring-rose-100/90 shadow-[0_18px_40px_rgba(244,114,182,0.12)]",
      iconClassName: "bg-rose-100 text-rose-600",
      titleClassName: "text-rose-700",
      messageClassName: "text-slate-600",
    };
  }

  return {
    title: "操作未完成",
    icon: AlertCircle,
    shellClassName: "border border-rose-100/90 bg-[#fff4f6] ring-1 ring-rose-100/90 shadow-[0_18px_40px_rgba(244,114,182,0.12)]",
    iconClassName: "bg-rose-100 text-rose-600",
    titleClassName: "text-rose-700",
    messageClassName: "text-slate-600",
  };
}

function getEditorBarPresentation(mode: "editing" | "pending") {
  if (mode === "pending") {
    return {
      icon: AlertCircle,
      shellClassName: "border border-amber-100/90 bg-[#fff9ef] shadow-[0_18px_42px_rgba(245,158,11,0.14)]",
      iconClassName: "bg-amber-100 text-amber-600",
      textClassName: "text-amber-900/90",
      secondaryButtonClassName: "border-amber-100 bg-white/92 text-amber-700 hover:border-amber-200 hover:bg-white hover:text-amber-800",
      primaryButtonClassName: "bg-amber-500 text-white hover:bg-amber-400",
    };
  }

  return {
    icon: Edit3,
    shellClassName: "border border-sky-100/90 bg-[#f3f9ff] shadow-[0_18px_42px_rgba(96,165,250,0.14)]",
    iconClassName: "bg-sky-100 text-sky-600",
    textClassName: "text-slate-700",
    secondaryButtonClassName: "border-sky-100 bg-white/92 text-sky-700 hover:border-sky-200 hover:bg-white hover:text-sky-800",
    primaryButtonClassName: "bg-sky-600 text-white hover:bg-sky-500",
  };
}

function formatPortraitTagSource(source: string) {
  const normalized = source.trim().toUpperCase();
  const sourceMap: Record<string, string> = {
    PROFILE: "资料",
    RESUME: "资料",
    AI_RESUME: "简历",
    PORTRAIT: "资料",
    SKILL: "技能",
    SKILLS: "技能",
    SKILL_PROGRESS: "技能",
    COMMUNITY: "互动",
    ACTIVITY: "互动",
    BEHAVIOR: "互动",
    INTERVIEW: "练习",
    AI_INTERVIEW: "面试",
    SYSTEM: "综合",
  };

  return sourceMap[normalized] ?? (source.trim() || "综合");
}

function getPortraitSignalMeta(signalLevel: string | null | undefined) {
  switch ((signalLevel ?? "").trim().toUpperCase()) {
    case "STRONG":
      return {
        label: "强",
        description: "近期资料、练习和互动信号比较完整。",
        tone: "emerald" as const,
      };
    case "NORMAL":
      return {
        label: "中",
        description: "当前已有一定画像依据，继续补样本会更稳。",
        tone: "sky" as const,
      };
    case "WEAK":
      return {
        label: "弱",
        description: "可用行为样本仍偏少，建议继续补齐资料与练习。",
        tone: "amber" as const,
      };
    default:
      return {
        label: "待生成",
        description: "系统正在整理你的成长画像。",
        tone: "slate" as const,
      };
  }
}

function getPortraitFreshnessMeta(freshnessLevel: string | null | undefined) {
  switch ((freshnessLevel ?? "").trim().toUpperCase()) {
    case "FRESH":
      return {
        label: "新鲜",
        description: "最近有新的练习或互动，画像比较贴近当前状态。",
        tone: "emerald" as const,
      };
    case "RECENT":
      return {
        label: "近期",
        description: "当前画像仍有参考价值，再补样本会更稳。",
        tone: "sky" as const,
      };
    case "STALE":
      return {
        label: "偏旧",
        description: "最近有效信号更新较少，建议刷新一轮简历或面试样本。",
        tone: "amber" as const,
      };
    default:
      return {
        label: "待生成",
        description: "等待新的画像信号写入。",
        tone: "slate" as const,
      };
  }
}

export default function StudentProfilePage() {
  const {
    role,
    displayName,
    refreshProfile: refreshAuthProfile,
  } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [pageState, setPageState] = useState<PageState>("loading");
  const [loadError, setLoadError] = useState<string | null>(null);
  const [profile, setProfile] = useState<StudentProfileResponse | null>(null);
  const [formData, setFormData] = useState<ProfileFormData | null>(null);
  const [privacyData, setPrivacyData] = useState<PrivacySettings | null>(null);
  const [activeTab, setActiveTab] = useState<TabKey>("profile");
  const [isEditing, setIsEditing] = useState(false);
  const [savingProfile, setSavingProfile] = useState(false);
  const [savingPrivacy, setSavingPrivacy] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [tagInput, setTagInput] = useState("");
  const [toast, setToast] = useState<ToastState | null>(null);
  const [emailCountdown, setEmailCountdown] = useState(0);
  const [passwordCountdown, setPasswordCountdown] = useState(0);
  const [emailModal, setEmailModal] = useState<EmailModalState>(createEmailModalState());
  const [passwordModal, setPasswordModal] = useState<PasswordModalState>(createPasswordModalState());

  const showToast = (
    message: string,
    tone: ToastState["tone"] = "success",
  ) => {
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
    if (emailCountdown <= 0) {
      return undefined;
    }

    const timer = window.setTimeout(() => setEmailCountdown((current) => current - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [emailCountdown]);

  useEffect(() => {
    if (passwordCountdown <= 0) {
      return undefined;
    }

    const timer = window.setTimeout(() => setPasswordCountdown((current) => current - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [passwordCountdown]);

  const loadProfile = async (options?: { silent?: boolean }) => {
    const silent = options?.silent ?? false;
    if (!silent) {
      setPageState((current) => (profile ? current : "loading"));
    }
    setRefreshing(true);
    setLoadError(null);

    try {
      // 资料接口同时返回画像、隐私矩阵和账号安全展示信息，页面以它为真相源。
      const response = await apiRequest<StudentProfileResponse>("/profiles/students/me");
      setProfile(response);
      setFormData(buildProfileFormData(response));
      setPrivacyData(clonePrivacySettings(response.privacy));
      setPageState("ready");
    } catch (error) {
      const message = buildErrorMessage(error, "个人资料加载失败，请稍后重试。");
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
    if (role === "STUDENT") {
      void loadProfile();
    }
  }, [role]);

  const initialFormData = useMemo(
    () => (profile ? buildProfileFormData(profile) : null),
    [profile],
  );

  const isProfileDirty = Boolean(
    initialFormData
    && formData
    && JSON.stringify(initialFormData) !== JSON.stringify(formData),
  );

  const isPrivacyDirty = Boolean(
    profile
    && privacyData
    && JSON.stringify(profile.privacy) !== JSON.stringify(privacyData),
  );

  useEffect(() => {
    if (pageState !== "ready") {
      return;
    }

    const requestedTab = searchParams.get("tab");
    const requestedAction = searchParams.get("action");
    const normalizedTab = PROFILE_TABS.some((tab) => tab.id === requestedTab) ? (requestedTab as TabKey) : null;
    const shouldOpenPassword = requestedAction === "password";

    if (!normalizedTab && !shouldOpenPassword) {
      return;
    }

    if (normalizedTab) {
      setActiveTab(normalizedTab);
    }

    if (shouldOpenPassword) {
      // 通知或安全入口可直达改密弹层，消费 query 后立刻清理。
      setActiveTab("security");
      setPasswordModal(createPasswordModalState({ isOpen: true }));
      setPasswordCountdown(0);
    }

    const nextSearchParams = new URLSearchParams(searchParams);
    nextSearchParams.delete("tab");
    nextSearchParams.delete("action");
    setSearchParams(nextSearchParams, { replace: true });
  }, [pageState, searchParams, setSearchParams]);

  if (pageState === "loading") {
    return (
      <WorkspacePageLoadingScreen
        title="正在准备你的资料页"
        description="正在加载个人资料、成长画像和安全设置，请稍候。"
      />
    );
  }

  if (pageState === "error" || !profile || !formData || !privacyData) {
    return (
      <div className="relative min-h-screen bg-[#eef3ff]">
        <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(79,70,229,0.18),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.12),transparent_28%),linear-gradient(180deg,#eff6ff_0%,#f8fafc_58%,#f8fafc_100%)]" />
          <div className="page-top-glow page-top-glow--indigo" />
        </div>
        <div className="relative z-10 flex min-h-screen items-center justify-center px-6">
          <div className="rounded-[2rem] border border-white/70 bg-white/84 px-8 py-10 text-center shadow-[0_24px_60px_rgba(148,163,184,0.18)] backdrop-blur-xl">
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-rose-50 text-rose-500">
              <AlertCircle size={24} />
            </div>
            <h1 className="mt-5 text-xl font-semibold text-slate-900">个人页面暂时没有加载成功</h1>
            <p className="mt-3 max-w-md text-sm leading-7 text-slate-500">{loadError}</p>
            <button
              type="button"
              onClick={() => void loadProfile()}
              className="mt-6 inline-flex items-center justify-center rounded-full bg-slate-900 px-5 py-3 text-sm font-semibold text-white transition-transform hover:-translate-y-0.5"
            >
              重新加载
            </button>
          </div>
        </div>
      </div>
    );
  }

  const handleTabChange = (nextTab: TabKey) => {
    if ((isEditing && isProfileDirty) || (activeTab === "privacy" && isPrivacyDirty)) {
      showToast("请先保存或撤销当前修改，再切换到其他板块。", "warning");
      return;
    }

    setActiveTab(nextTab);
  };

  const handleManualRefresh = () => {
    if ((isEditing && isProfileDirty) || isPrivacyDirty) {
      showToast("你有未保存的修改，请先保存或取消后再刷新。", "warning");
      return;
    }

    void loadProfile({ silent: true });
  };

  const handleSaveProfile = async () => {
    if (!formData) {
      return;
    }
    if (!formData.displayName.trim()) {
      showToast("昵称不能为空。", "error");
      return;
    }

    const socialLinks = normalizeClientSocialLinks(formData.socialLinks);
    setSavingProfile(true);
    try {
      // 保存资料后刷新 AuthContext，顶栏昵称和头像版本才能同步更新。
      await apiRequest("/profiles/students/me", {
        method: "PUT",
        body: JSON.stringify({
          displayName: formData.displayName.trim(),
          realName: formData.realName.trim(),
          jobStatus: formData.jobStatus.trim(),
          schoolName: formData.schoolName.trim(),
          grade: formData.grade.trim(),
          major: formData.major.trim(),
          gpa: formData.gpa.trim(),
          targetPosition: formData.targetPosition.trim(),
          honors: formData.honors,
          github: findSocialLinkValue(socialLinks, "GITHUB"),
          portfolio: findSocialLinkValue(socialLinks, "PORTFOLIO"),
          socialLinks,
          phone: formData.phone.trim(),
          wechat: formData.wechat.trim(),
          skillTags: formData.skillTags,
          selfIntro: formData.selfIntro.trim(),
        }),
      });
      await refreshAuthProfile();
      await loadProfile({ silent: true });
      setIsEditing(false);
      showToast("个人资料已更新。");
    } catch (error) {
      showToast(buildErrorMessage(error, "个人资料保存失败，请稍后再试。"), "error");
    } finally {
      setSavingProfile(false);
    }
  };

  const handleSavePrivacy = async () => {
    setSavingPrivacy(true);
    try {
      // 隐私矩阵整体提交，公开资料页和导师/企业侧 viewer 态都会按后端规则读取。
      await apiRequest("/profiles/students/me/privacy", {
        method: "PUT",
        body: JSON.stringify({
          privacy: privacyData,
        }),
      });
      setProfile((current) => (current ? { ...current, privacy: clonePrivacySettings(privacyData) } : current));
      showToast("可见范围已更新。");
    } catch (error) {
      showToast(buildErrorMessage(error, "隐私设置保存失败，请稍后再试。"), "error");
    } finally {
      setSavingPrivacy(false);
    }
  };

  const handleResetChanges = () => {
    if (activeTab === "profile" && initialFormData) {
      setFormData(initialFormData);
      setIsEditing(false);
      setTagInput("");
      showToast("本次修改已取消。", "cancel");
      return;
    }

    if (activeTab === "privacy") {
      setPrivacyData(clonePrivacySettings(profile.privacy));
      showToast("未保存的设置已恢复。", "neutral");
    }
  };

  const handleAddSkillTag = () => {
    const normalized = tagInput.trim();
    if (!normalized) {
      return;
    }
    if (formData.skillTags.includes(normalized)) {
      setTagInput("");
      return;
    }
    if (formData.skillTags.length >= 10) {
      showToast("技能标签最多保留 10 个，先删掉一个再继续添加。", "warning");
      return;
    }

    // 技能标签属于资料画像信号，前端先去重限量，最终仍以后端保存结果为准。
    setFormData((current) => (current ? { ...current, skillTags: [...current.skillTags, normalized] } : current));
    setTagInput("");
  };

  const handleAddSocialLink = () => {
    const usedPlatforms = new Set(formData.socialLinks.map((socialLink) => socialLink.platform));
    const nextPlatform = SOCIAL_PLATFORM_OPTIONS.find((option) => !usedPlatforms.has(option.value))?.value;
    if (!nextPlatform) {
      showToast("可添加的平台已全部选完，如需调整可先删除一个。", "warning");
      return;
    }

    setFormData((current) => (
      current
        ? { ...current, socialLinks: [...current.socialLinks, createEmptySocialLink(nextPlatform)] }
        : current
    ));
  };

  const handleRemoveSocialLink = (index: number) => {
    setFormData((current) => (
      current
        ? { ...current, socialLinks: current.socialLinks.filter((_, itemIndex) => itemIndex !== index) }
        : current
    ));
  };

  const handleChangeSocialLink = (
    index: number,
    patch: Partial<StudentSocialLink>,
  ) => {
    setFormData((current) => {
      if (!current) {
        return current;
      }

      const nextSocialLinks = current.socialLinks.map((socialLink, itemIndex) => (
        itemIndex === index ? { ...socialLink, ...patch } : socialLink
      ));
      const nextPlatform = patch.platform;
      if (nextPlatform && nextSocialLinks.some((socialLink, itemIndex) => itemIndex !== index && socialLink.platform === nextPlatform)) {
        showToast("每个平台只能添加一次。", "warning");
        return current;
      }

      return {
        ...current,
        socialLinks: nextSocialLinks,
      };
    });
  };

  const handleSendEmailCode = async (stage: "CURRENT" | "NEW") => {
    if (stage === "NEW" && !emailModal.newEmail.trim()) {
      showToast("请先输入新的邮箱地址，再获取验证码。", "error");
      return;
    }

    setEmailModal((current) => ({ ...current, busy: true }));
    try {
      // 邮箱换绑分当前邮箱和新邮箱两段验证码，stage 决定后端发送目标。
      const response = await apiRequest<SendCodeResponse>("/profiles/students/me/security/email/send-code", {
        method: "POST",
        body: JSON.stringify({
          stage,
          newEmail: stage === "NEW" ? emailModal.newEmail.trim() : undefined,
        }),
      });
      setEmailCountdown(calculateCountdown(response.nextSendAt));
      setEmailModal((current) => ({
        ...current,
        debugHint: response.debugCode
          ? { code: response.debugCode, targetEmail: response.targetEmail }
          : null,
      }));
      showToast(
        response.debugCode
          ? "验证码已准备好，可直接使用下方验证码继续。"
          : `验证码已发送至 ${response.targetEmail}`,
        response.debugCode ? "info" : "success",
      );
    } catch (error) {
      showToast(buildErrorMessage(error, "验证码发送失败，请稍后再试。"), "error");
    } finally {
      setEmailModal((current) => ({ ...current, busy: false }));
    }
  };

  const handleVerifyCurrentEmail = async () => {
    if (!emailModal.currentCode.trim()) {
      showToast("请先输入当前邮箱收到的验证码。", "error");
      return;
    }

    setEmailModal((current) => ({ ...current, busy: true }));
    try {
      // 当前邮箱验证通过后拿一次性 token，再进入新邮箱验证码步骤。
      const response = await apiRequest<VerifyCodeResponse>("/profiles/students/me/security/email/verify-current-code", {
        method: "POST",
        body: JSON.stringify({ code: emailModal.currentCode.trim() }),
      });
      setEmailModal((current) => ({
        ...current,
        busy: false,
        step: "changeNew",
        currentCode: "",
        currentEmailVerificationToken: response.verificationToken,
        debugHint: null,
      }));
      setEmailCountdown(0);
      showToast("当前邮箱已经验证通过，继续填写新的邮箱地址。");
    } catch (error) {
      setEmailModal((current) => ({ ...current, busy: false }));
      showToast(buildErrorMessage(error, "当前邮箱验证失败，请稍后再试。"), "error");
    }
  };

  const handleChangeEmail = async () => {
    if (!emailModal.newEmail.trim()) {
      showToast("请先输入新的邮箱地址。", "error");
      return;
    }
    if (!emailModal.newEmailCode.trim()) {
      showToast("请输入新邮箱收到的验证码。", "error");
      return;
    }

    setEmailModal((current) => ({ ...current, busy: true }));
    try {
      // 换绑成功后刷新 auth profile，后续会话展示的新邮箱立即生效。
      await apiRequest<EmailChangeResponse>("/profiles/students/me/security/email/change", {
        method: "POST",
        body: JSON.stringify({
          currentEmailVerificationToken: emailModal.currentEmailVerificationToken,
          newEmail: emailModal.newEmail.trim(),
          newEmailCode: emailModal.newEmailCode.trim(),
        }),
      });
      await refreshAuthProfile();
      await loadProfile({ silent: true });
      setEmailModal(createEmailModalState());
      setEmailCountdown(0);
      showToast("登录邮箱已更新。");
    } catch (error) {
      setEmailModal((current) => ({ ...current, busy: false }));
      showToast(buildErrorMessage(error, "邮箱换绑失败，请稍后再试。"), "error");
    }
  };

  const handleSendPasswordCode = async () => {
    setPasswordModal((current) => ({ ...current, busy: true }));
    try {
      // 登录态改密仍通过邮箱验证码发起，和忘记密码链路共享安全服务。
      const response = await apiRequest<SendCodeResponse>("/profiles/students/me/security/password/send-code", {
        method: "POST",
      });
      setPasswordCountdown(calculateCountdown(response.nextSendAt));
      setPasswordModal((current) => ({
        ...current,
        debugHint: response.debugCode
          ? { code: response.debugCode, targetEmail: response.targetEmail }
          : null,
      }));
      showToast(
        response.debugCode
          ? "验证码已准备好，可直接使用下方验证码继续。"
          : `验证码已发送至 ${response.targetEmail}`,
        response.debugCode ? "info" : "success",
      );
    } catch (error) {
      showToast(buildErrorMessage(error, "密码重置验证码发送失败，请稍后再试。"), "error");
    } finally {
      setPasswordModal((current) => ({ ...current, busy: false }));
    }
  };

  const handleVerifyPasswordCode = async () => {
    if (!passwordModal.code.trim()) {
      showToast("请输入当前邮箱收到的验证码。", "error");
      return;
    }

    setPasswordModal((current) => ({ ...current, busy: true }));
    try {
      // 验证码换取 passwordResetToken，真正改密不直接信任六位码。
      const response = await apiRequest<VerifyCodeResponse>("/profiles/students/me/security/password/verify-code", {
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
      showToast(buildErrorMessage(error, "验证失败，请稍后再试。"), "error");
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
      // 新密码只提交 reset token，不把验证码继续留在请求里。
      await apiRequest("/profiles/students/me/security/password/change", {
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
      showToast(buildErrorMessage(error, "密码修改失败，请稍后再试。"), "error");
    }
  };

  const heroName = buildStudentNickname(profile, displayName, "同学");
  const portraitTags = profile.portrait?.tags ?? [];
  const portraitStrengthTags = profile.portrait?.strengthTags ?? [];
  const portraitRiskTags = profile.portrait?.riskTags ?? [];
  const portraitSignalMeta = getPortraitSignalMeta(profile.portrait?.signalLevel);
  const portraitFreshnessMeta = getPortraitFreshnessMeta(profile.portrait?.freshnessLevel);
  const portraitHeadline = profile.portrait?.headline?.trim() || null;
  const portraitSummary = profile.portrait?.summary?.trim() || null;
  const portraitNextActions = profile.portrait?.nextActions ?? [];
  const portraitSummaryVersion = profile.portrait?.summaryVersion?.trim() || null;
  const portraitEvidence = profile.portrait?.evidence;
  const portraitUpdatedLabel = profile.portrait?.updatedAt
    ? formatDateTime(profile.portrait.updatedAt)
    : "继续完善资料后会自动更新";
  const completionRate = profile.completionRate ?? 0;
  const roleLabel = getRoleDisplayLabel(role);
  const floatingBarVisible = (activeTab === "profile" && isEditing) || (activeTab === "privacy" && isPrivacyDirty);
  const editorBarMode = activeTab === "profile" && !isProfileDirty ? "editing" : "pending";
  const editorBarPresentation = getEditorBarPresentation(editorBarMode);
  const editorFloatingMessage = activeTab === "profile"
    ? (isProfileDirty ? "你正在编辑个人资料，记得保存或取消本次修改。" : "已进入编辑模式，修改内容后记得保存。")
    : "你修改了可见范围设置，记得保存。";
  const tierPresentation = getTierPresentation(profile.tier);
  const profilePrimaryFields: Array<{
    label: string;
    icon: LucideIcon;
    value: string;
    type?: "text" | "select";
    options?: string[];
    placeholder?: string;
    onChange: (value: string) => void;
  }> = [
    {
      label: "昵称",
      icon: User,
      value: formData.displayName,
      placeholder: "请输入希望展示的昵称",
      onChange: (value) => setFormData({ ...formData, displayName: value }),
    },
    {
      label: "真实姓名",
      icon: User,
      value: formData.realName,
      placeholder: "请输入真实姓名",
      onChange: (value) => setFormData({ ...formData, realName: value }),
    },
    {
      label: "当前求职状态",
      icon: BriefcaseBusiness,
      value: formData.jobStatus,
      type: "select",
      options: JOB_STATUS_OPTIONS,
      onChange: (value) => setFormData({ ...formData, jobStatus: value }),
    },
    {
      label: "学校名称",
      icon: Building2,
      value: formData.schoolName,
      placeholder: "例如：华东理工大学",
      onChange: (value) => setFormData({ ...formData, schoolName: value }),
    },
    {
      label: "所在年级",
      icon: GraduationCap,
      value: formData.grade,
      type: "select",
      options: GRADE_OPTIONS,
      onChange: (value) => setFormData({ ...formData, grade: value }),
    },
    {
      label: "就读专业",
      icon: BookOpen,
      value: formData.major,
      placeholder: "例如：软件工程",
      onChange: (value) => setFormData({ ...formData, major: value }),
    },
    {
      label: "意向目标岗位",
      icon: Target,
      value: formData.targetPosition,
      placeholder: "例如：前端工程师",
      onChange: (value) => setFormData({ ...formData, targetPosition: value }),
    },
    {
      label: "专业成绩 (GPA)",
      icon: Award,
      value: formData.gpa,
      placeholder: "例如：3.8 / 4.0",
      onChange: (value) => setFormData({ ...formData, gpa: value }),
    },
  ];
  const contactFields: Array<{
    label: string;
    icon: LucideIcon;
    value: string;
    placeholder: string;
    onChange: (value: string) => void;
  }> = [
    {
      label: "手机号码",
      icon: Smartphone,
      value: formData.phone,
      placeholder: "例如：13800138000",
      onChange: (value) => setFormData({ ...formData, phone: value }),
    },
    {
      label: "微信号",
      icon: MessageSquare,
      value: formData.wechat,
      placeholder: "例如：lin_frontend",
      onChange: (value) => setFormData({ ...formData, wechat: value }),
    },
  ];

  const startEditingProfile = () => {
    setIsEditing(true);
  };

  return (
    <div className="relative min-h-screen bg-[#e5ecf6] text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(79,70,229,0.16),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.10),transparent_28%),linear-gradient(180deg,#e4ecf8_0%,#edf2f8_58%,#f3f6fa_100%)]" />
        <div className="page-top-glow page-top-glow--indigo-soft" />
      </div>

      <StudentWorkspaceTopbar
        sectionLabel="Profile Center"
        title="账号与资料中心"
        navItems={buildStudentWorkspacePrimaryNav()}
        leftAddon={(
          <Link
            to="/student/dashboard"
            className="inline-flex h-11 items-center justify-center rounded-full border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
          >
            <ArrowLeft size={16} className="mr-2" />
            返回工作台
          </Link>
        )}
        rightActions={(
          <button
            type="button"
            onClick={handleManualRefresh}
            disabled={refreshing}
            className="inline-flex h-10 items-center justify-center rounded-full border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <RefreshCw size={17} className={joinClasses(refreshing && "animate-spin")} />
          </button>
        )}
        position="fixed"
        captureKey={`profile-refreshing:${refreshing ? "1" : "0"}`}
        userId={profile.userId}
        displayName={heroName}
        avatar={profile.avatar}
        tier={profile.tier}
        userSubtitle={roleLabel}
        className="border-slate-200/80 bg-[#f6f8fc]/88"
      />

      <main className="relative z-10 mx-auto w-full max-w-[96rem] px-4 pb-6 pt-24 sm:px-6 lg:px-8 lg:pb-8 lg:pt-24">
        <div className="space-y-8">
          <section>
            <motion.div
              initial={{ opacity: 0, y: 18 }}
              animate={{ opacity: 1, y: 0 }}
              className="relative overflow-hidden rounded-[2.35rem] border border-slate-200/80 bg-[#fbfcff]/92 p-6 shadow-[0_24px_60px_rgba(100,116,139,0.18)] backdrop-blur-xl lg:px-7 lg:py-6"
            >
              <div className="absolute -right-14 -top-14 h-44 w-44 rounded-full bg-indigo-200/70 blur-2xl" />
              <div className="absolute bottom-0 left-0 h-36 w-36 rounded-full bg-teal-200/55 blur-2xl" />

              <div className="relative z-10 flex flex-col gap-5 xl:flex-row xl:items-center xl:justify-between">
                <div className="flex flex-col gap-4 xl:flex-row xl:items-center">
                  <Suspense fallback={<StudentAvatarEditorFallback heroName={heroName} />}>
                    <StudentAvatarEditor
                      userId={profile.userId}
                      heroName={heroName}
                      avatar={profile.avatar}
                      tier={profile.tier}
                      onAvatarUploaded={(payload: StudentAvatarUploadResult) => {
                        setProfile((current) => (current
                          ? {
                            ...current,
                            avatar: {
                              uploaded: payload.uploaded,
                              contentType: payload.contentType,
                              updatedAt: payload.updatedAt,
                            },
                          }
                          : current));
                      }}
                      onToast={showToast}
                    />
                  </Suspense>

                  <div className="max-w-3xl">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="rounded-full border border-sky-100 bg-sky-50 px-3 py-1 text-xs font-bold text-sky-600 shadow-sm">
                        {roleLabel}
                      </span>
                      <span className={tierPresentation.badgeClassName}>{tierPresentation.label}</span>
                      <span className="rounded-full border border-emerald-100 bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-600 shadow-sm">
                        {profile.jobStatus?.trim() || "求职状态待设置"}
                      </span>
                    </div>

                    <div className="mt-3.5">
                      <h1 className="text-3xl font-semibold tracking-tight text-slate-900 lg:text-4xl">{heroName}</h1>
                      <p className="mt-1.5 text-sm font-medium text-slate-500">{profile.email}</p>
                      <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">{tierPresentation.description}</p>
                    </div>

                    <div className="mt-3.5 flex flex-wrap gap-2">
                      <HeroBadge tone="target" label={profile.targetPosition?.trim() ? `目标岗位：${profile.targetPosition}` : "目标岗位待完善"} />
                      <HeroBadge tone="school" label={profile.schoolName?.trim() ? `学校：${profile.schoolName}` : "学校信息待完善"} />
                      <HeroBadge tone="major" label={profile.major?.trim() ? `专业：${profile.major}` : "专业待补充"} />
                      <HeroBadge tone="grade" label={profile.grade?.trim() ? `年级：${profile.grade}` : "年级待补充"} />
                    </div>
                  </div>
                </div>

                <div className="flex flex-col gap-2.5 xl:w-[23.5rem] xl:min-w-[23.5rem]">
                  <div className="rounded-[1.55rem] border border-slate-200/80 bg-[#eef2f7] px-4 py-3.5 shadow-sm">
                    <div className="flex items-center justify-between gap-4">
                      <div className="min-w-0">
                        <div className="text-sm font-bold text-slate-700">资料完善度</div>
                        <div className="mt-1 text-sm font-medium text-slate-500">当前资料状态</div>
                      </div>
                      <div className="shrink-0 text-[1.7rem] font-black text-indigo-600">{completionRate}%</div>
                    </div>
                    <div className="mt-2.5 h-2.5 overflow-hidden rounded-full bg-slate-200">
                      <motion.div
                        initial={{ width: 0 }}
                        animate={{ width: `${completionRate}%` }}
                        transition={{ duration: 0.8, ease: "easeOut" }}
                        className="h-full rounded-full bg-gradient-to-r from-indigo-500 to-violet-500"
                      />
                    </div>
                  </div>

                  <div className="rounded-[1.55rem] border border-slate-200/80 bg-[#eef2f7] px-4 py-3.5 shadow-sm">
                    <div className="flex items-center justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <div className="text-sm font-bold text-slate-700">成长动态</div>
                        <div className="mt-1 text-sm font-semibold text-slate-900">
                          {portraitTags.length > 0 ? `${portraitTags.length} 个成长关键词` : "成长画像生成中"}
                        </div>
                        <div className="mt-1 text-xs font-medium text-slate-500">{portraitUpdatedLabel}</div>
                      </div>
                      <div className="shrink-0 rounded-full border border-amber-200 bg-amber-50 px-3 py-1.5 text-xs font-semibold text-amber-700">
                        {portraitTags.length > 0 ? "已生成" : "生成中"}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </motion.div>
          </section>

          <div className="grid gap-5 xl:grid-cols-[18.5rem_minmax(0,1fr)] xl:items-start">
            <aside className="xl:sticky xl:top-[5.25rem]">
              <div className="space-y-3">
                {PROFILE_TABS.map((tab) => {
                  const active = activeTab === tab.id;
                  const TabIcon = tab.icon;
                  return (
                    <button
                      key={tab.id}
                      type="button"
                      onClick={() => handleTabChange(tab.id)}
                      className={joinClasses(
                        "group relative flex w-full items-start gap-4 overflow-hidden rounded-[1.6rem] border px-5 py-4 text-left transition-all duration-300",
                        active
                          ? "border-indigo-300 bg-[linear-gradient(135deg,rgba(224,231,255,0.98),rgba(246,249,255,0.98))] shadow-[0_22px_36px_rgba(99,102,241,0.18)] ring-1 ring-indigo-200"
                          : "border-slate-200/80 bg-[#fbfcff] shadow-[0_14px_28px_rgba(100,116,139,0.10)] hover:-translate-y-1 hover:border-slate-300 hover:bg-white hover:shadow-[0_20px_34px_rgba(100,116,139,0.16)]",
                      )}
                    >
                      <span
                        className={joinClasses(
                          "absolute inset-y-4 left-0 w-1 rounded-r-full transition-all duration-300",
                          active ? "bg-indigo-500 opacity-100" : "bg-transparent opacity-0 group-hover:opacity-100",
                        )}
                      />
                      <span
                        className={joinClasses(
                          "flex h-12 w-12 shrink-0 items-center justify-center rounded-[1.1rem] border transition-all duration-300",
                          active
                            ? "border-indigo-200 bg-indigo-100 text-indigo-700 shadow-sm"
                            : "border-slate-200 bg-[#eef2f7] text-slate-500 group-hover:border-indigo-100 group-hover:bg-indigo-50 group-hover:text-indigo-600",
                        )}
                      >
                        <TabIcon size={20} />
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className={joinClasses("block text-base font-semibold", active ? "text-indigo-800" : "text-slate-800")}>
                          {tab.label}
                        </span>
                        <span className="mt-1.5 block text-sm leading-6 text-slate-500">
                          {PROFILE_TAB_DESCRIPTIONS[tab.id]}
                        </span>
                      </span>
                    </button>
                  );
                })}
              </div>
            </aside>

            <div className="min-w-0">
              <AnimatePresence mode="wait">
            {activeTab === "profile" ? (
              <motion.div
                key="profile"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -12 }}
                className="space-y-6"
              >
                <section className="overflow-hidden rounded-[2rem] border border-slate-200/85 bg-[#fcfdff]/96 shadow-[0_22px_60px_rgba(100,116,139,0.16)] backdrop-blur-xl">
                  <div className="flex flex-col gap-4 border-b border-slate-200/80 bg-[#eef2f7]/78 px-8 py-6 lg:flex-row lg:items-center lg:justify-between">
                    <div>
                      <h2 className="text-2xl font-semibold text-slate-900">基础信息</h2>
                      <p className="mt-2 text-base font-medium text-slate-500">
                        完善这里的信息后，你的个人主页会更完整，也能获得更准确的推荐与匹配。
                      </p>
                    </div>
                    {!isEditing ? (
                      <button
                        type="button"
                        onClick={startEditingProfile}
                        className="inline-flex items-center justify-center rounded-xl bg-indigo-100 px-4 py-2.5 text-base font-semibold text-indigo-700 transition-colors hover:bg-indigo-200"
                      >
                        <Edit3 size={16} className="mr-2" />
                        编辑资料
                      </button>
                    ) : (
                      <div className="flex flex-col items-start gap-3 lg:items-end">
                        <div className="flex flex-wrap items-center gap-2">
                          <button
                            type="button"
                            onClick={handleResetChanges}
                            className="inline-flex items-center justify-center rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-base font-semibold text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900"
                          >
                            <X size={15} className="mr-2" />
                            取消
                          </button>
                          <button
                            type="button"
                            disabled={savingProfile || !isProfileDirty}
                            onClick={() => void handleSaveProfile()}
                            className="inline-flex items-center justify-center rounded-xl bg-slate-900 px-4 py-2.5 text-base font-semibold text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            {savingProfile ? (
                              <>
                                <RefreshCw size={15} className="mr-2 animate-spin" />
                                正在保存
                              </>
                            ) : (
                              <>
                                <Check size={15} className="mr-2" />
                                保存资料
                              </>
                            )}
                          </button>
                        </div>
                      </div>
                    )}
                  </div>

                  <div className="space-y-6 p-7">
                    <div className="grid gap-2.5 md:grid-cols-2 xl:grid-cols-4">
                      {profilePrimaryFields.map((field) => (
                        <EditableField
                          key={field.label}
                          label={field.label}
                          icon={field.icon}
                          value={field.value}
                          isEditing={isEditing}
                          type={field.type}
                          options={field.options}
                          onChange={field.onChange}
                          placeholder={field.placeholder}
                          compact
                        />
                      ))}
                    </div>

                    <div className="grid gap-3 xl:grid-cols-[minmax(0,1.08fr)_minmax(0,0.92fr)]">
                      <div className="rounded-[1.45rem] border border-slate-200/80 bg-[linear-gradient(180deg,rgba(239,244,250,0.96),rgba(255,255,255,0.98))] px-5 py-5 shadow-[0_12px_28px_rgba(100,116,139,0.10)]">
                        <label className="flex items-center text-base font-bold text-slate-700">
                          <span className="mr-3 flex h-8 w-8 items-center justify-center rounded-2xl bg-amber-50 text-amber-500">
                            <Trophy size={15} />
                          </span>
                          荣誉与奖项
                        </label>
                        <div className="mt-2.5 h-px bg-[linear-gradient(90deg,rgba(245,158,11,0.18),rgba(148,163,184,0.12),transparent)]" />
                        {isEditing ? (
                          <textarea
                            value={formData.honors}
                            onChange={(event) => setFormData({ ...formData, honors: event.target.value })}
                            rows={4}
                            placeholder="每行填写一项荣誉，例如：2023年 国家励志奖学金"
                            className="mt-3 w-full rounded-[1.15rem] border-2 border-slate-200 bg-white px-4 py-3.5 text-base font-medium text-slate-700 outline-none transition-colors focus:border-indigo-400"
                          />
                        ) : (
                          <div className="mt-3">
                            <StaticPanel value={formData.honors} emptyLabel="暂无荣誉记录" />
                          </div>
                        )}
                      </div>

                      <div className="rounded-[1.45rem] border border-slate-200/80 bg-[linear-gradient(180deg,rgba(239,244,250,0.96),rgba(255,255,255,0.98))] px-5 py-5 shadow-[0_12px_28px_rgba(100,116,139,0.10)]">
                        <label className="flex items-center text-base font-bold text-slate-700">
                          <span className="mr-3 flex h-8 w-8 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-500">
                            <Sparkles size={15} />
                          </span>
                          技能标签
                        </label>
                        <div className="mt-2.5 h-px bg-[linear-gradient(90deg,rgba(99,102,241,0.18),rgba(148,163,184,0.12),transparent)]" />
                        {isEditing ? (
                          <div className="mt-3 rounded-[1.15rem] border-2 border-slate-200 bg-[#fbfcff] p-3 transition-colors focus-within:border-indigo-400">
                            <div className="flex flex-wrap gap-2">
                              {formData.skillTags.map((tag) => (
                                <span
                                  key={tag}
                                  className="inline-flex items-center rounded-xl border border-slate-200 bg-[#eef2f7] px-3 py-1.5 text-base font-semibold text-slate-700 shadow-sm"
                                >
                                  {tag}
                                  <button
                                    type="button"
                                    onClick={() => setFormData({
                                      ...formData,
                                      skillTags: formData.skillTags.filter((currentTag) => currentTag !== tag),
                                    })}
                                    className="ml-2 text-slate-400 transition-colors hover:text-rose-500"
                                  >
                                    <X size={14} />
                                  </button>
                                </span>
                              ))}
                            </div>
                            <div className="mt-3 flex flex-col gap-2.5 sm:flex-row">
                              <input
                                type="text"
                                value={tagInput}
                                onChange={(event) => setTagInput(event.target.value)}
                                onKeyDown={(event) => {
                                  if (event.key === "Enter") {
                                    event.preventDefault();
                                    handleAddSkillTag();
                                  }
                                }}
                                className="min-w-0 flex-1 rounded-xl border border-slate-200 bg-[#f3f6fa] px-4 py-3 text-base font-medium text-slate-700 outline-none transition-colors focus:border-indigo-400 focus:bg-white"
                                placeholder="输入技能后回车或点击右侧按钮添加"
                              />
                              <button
                                type="button"
                                onClick={handleAddSkillTag}
                                className="inline-flex items-center justify-center rounded-xl bg-slate-900 px-4 py-3 text-base font-semibold text-white transition-transform hover:-translate-y-0.5"
                              >
                                添加标签
                              </button>
                            </div>
                          </div>
                        ) : (
                          <div className="mt-3 flex flex-wrap gap-2">
                            {formData.skillTags.length > 0 ? formData.skillTags.map((tag) => (
                              <span
                                key={tag}
                                className="rounded-xl border border-indigo-200 bg-indigo-100 px-4 py-2 text-base font-semibold text-indigo-700"
                              >
                                {tag}
                              </span>
                            )) : (
                              <span className="text-base text-slate-400">还没有填写技能标签</span>
                            )}
                          </div>
                        )}
                      </div>
                    </div>

                    <div className="rounded-[1.45rem] border border-slate-200/80 bg-[linear-gradient(180deg,rgba(239,244,250,0.96),rgba(255,255,255,0.98))] px-5 py-5 shadow-[0_12px_28px_rgba(100,116,139,0.10)]">
                      <label className="flex items-center justify-between text-base font-bold text-slate-700">
                        <span className="flex items-center">
                          <span className="mr-3 flex h-8 w-8 items-center justify-center rounded-2xl bg-rose-50 text-rose-500">
                            <Edit3 size={15} />
                          </span>
                          个人介绍
                        </span>
                        {isEditing ? <span className="font-medium text-slate-400">{formData.selfIntro.length}/200</span> : null}
                      </label>
                      <div className="mt-2.5 h-px bg-[linear-gradient(90deg,rgba(244,63,94,0.18),rgba(148,163,184,0.12),transparent)]" />
                      {isEditing ? (
                        <textarea
                          value={formData.selfIntro}
                          onChange={(event) => setFormData({ ...formData, selfIntro: event.target.value })}
                          rows={3}
                          maxLength={200}
                          className="mt-3 w-full rounded-[1.15rem] border-2 border-slate-200 bg-white px-4 py-3.5 text-base font-medium text-slate-700 outline-none transition-colors focus:border-indigo-400"
                          placeholder="简单描述你的技术背景、当前阶段和你正在准备的方向"
                        />
                      ) : (
                        <div className="mt-3">
                          <StaticPanel value={formData.selfIntro} emptyLabel="还没有写自我介绍" />
                        </div>
                      )}
                    </div>
                  </div>
                </section>

                <section className="overflow-hidden rounded-[2rem] border border-slate-200/85 bg-[#fcfdff]/96 shadow-[0_22px_60px_rgba(100,116,139,0.16)] backdrop-blur-xl">
                  <div className="border-b border-slate-200/80 bg-[#eef2f7]/78 px-8 py-5">
                    <h2 className="flex items-center text-xl font-semibold text-slate-900">
                      <Globe size={18} className="mr-2 text-indigo-500" />
                      个人主页与社交账号
                    </h2>
                    <p className="mt-1 ml-6 text-sm font-medium text-slate-500">
                      你可以添加个人网站或常用社交账号，方便他人更全面地了解你；显示范围可在可见范围中调整。
                    </p>
                  </div>
                  <div className="p-8">
                    <SocialLinksField
                      socialLinks={formData.socialLinks}
                      isEditing={isEditing}
                      onAdd={handleAddSocialLink}
                      onRemove={handleRemoveSocialLink}
                      onChange={handleChangeSocialLink}
                    />
                  </div>
                </section>

                <section className="overflow-hidden rounded-[2rem] border border-slate-200/85 bg-[#fcfdff]/96 shadow-[0_22px_60px_rgba(100,116,139,0.16)] backdrop-blur-xl">
                  <div className="border-b border-slate-200/80 bg-[#eef2f7]/78 px-8 py-5">
                    <h2 className="flex items-center text-xl font-semibold text-slate-900">
                      <Smartphone size={18} className="mr-2 text-indigo-500" />
                      联系方式
                    </h2>
                    <p className="mt-1 ml-6 text-sm font-medium text-slate-500">仅在你允许的范围内展示，方便导师或招聘方联系你。</p>
                  </div>
                  <div className="grid gap-4 p-8 md:grid-cols-2">
                    {contactFields.map((field) => (
                      <EditableField
                        key={field.label}
                        label={field.label}
                        icon={field.icon}
                        value={field.value}
                        isEditing={isEditing}
                        onChange={field.onChange}
                        placeholder={field.placeholder}
                      />
                    ))}
                  </div>
                </section>
              </motion.div>
            ) : null}

            {activeTab === "portrait" ? (
              <motion.div
                key="portrait"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -12 }}
                className="space-y-5"
              >
                <section className="relative overflow-hidden rounded-[1.85rem] bg-gradient-to-br from-indigo-900 via-indigo-800 to-blue-950 px-6 py-6 text-white shadow-[0_28px_70px_rgba(67,56,202,0.28)] lg:px-7 lg:py-6">
                  <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(255,255,255,0.16),transparent_36%),linear-gradient(135deg,transparent_0%,rgba(255,255,255,0.04)_100%)]" />
                  <div className="absolute -bottom-10 right-0 h-48 w-48 rounded-full bg-cyan-400/18 blur-3xl" />

                  <div className="relative z-10">
                    <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                      <div className="min-w-0 flex-1">
                        <h2 className="flex items-center text-[1.8rem] font-semibold">
                          <Sparkles size={22} className="mr-2 text-teal-300" />
                          成长画像
                        </h2>
                        <p className="mt-2.5 max-w-2xl text-sm leading-7 text-indigo-100/88">
                          画像会结合资料完善、练习进展与互动表现持续更新。
                        </p>
                        <div className="mt-3 inline-flex items-center rounded-full border border-white/16 bg-white/10 px-4 py-2 text-sm font-semibold text-indigo-100 backdrop-blur-md">
                          更新于 {portraitUpdatedLabel}
                        </div>
                      </div>

                      <div className="xl:min-w-[18rem] xl:max-w-[18rem] xl:self-center">
                        <div className="grid gap-3">
                          <div className="rounded-[1.45rem] border border-white/14 bg-white/8 px-4 py-4 backdrop-blur-md">
                            <div className="text-sm font-bold text-indigo-100">关键词概览</div>
                            <div className="mt-2 text-[1.7rem] font-black text-white">
                              {portraitTags.length > 0 ? `${portraitTags.length} 个关键词` : "成长画像生成中"}
                            </div>
                          </div>
                          <div className="rounded-[1.45rem] border border-white/14 bg-white/8 px-4 py-4 backdrop-blur-md">
                            <div className="text-sm font-bold text-indigo-100">下一步建议</div>
                            <div className="mt-2 text-[1.35rem] font-black text-white">
                              {portraitNextActions.length > 0 ? `${portraitNextActions.length} 条` : "待生成"}
                            </div>
                            <div className="mt-1 text-sm leading-6 text-indigo-100/80">
                              {portraitSignalMeta.label === "待生成"
                                ? "继续完善资料、练习与互动后会逐步补齐。"
                                : `当前画像信号 ${portraitSignalMeta.label} · 新鲜度 ${portraitFreshnessMeta.label}`}
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className="mt-4 flex flex-wrap gap-2">
                      <PortraitStatusBadge label={`画像信号 · ${portraitSignalMeta.label}`} tone={portraitSignalMeta.tone} />
                      <PortraitStatusBadge label={`画像新鲜度 · ${portraitFreshnessMeta.label}`} tone={portraitFreshnessMeta.tone} />
                      {portraitSummaryVersion ? <PortraitStatusBadge label={`总结版本 · ${portraitSummaryVersion}`} tone="slate" /> : null}
                    </div>
                  </div>
                </section>

                <section className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                  <MetricCard
                    title="已掌握硬技能"
                    value={`${formatCount(portraitEvidence?.masteredSkills ?? 0)} 项`}
                    icon={Award}
                    tone="teal"
                  />
                  <MetricCard
                    title="学习中技能"
                    value={`${formatCount(portraitEvidence?.learningSkills ?? 0)} 项`}
                    icon={BookOpen}
                    tone="indigo"
                  />
                  <MetricCard
                    title="本周社区贡献"
                    value={`${formatCount(profile.communityScore7d)} 分`}
                    icon={Heart}
                    tone="amber"
                  />
                  <MetricCard
                    title="模拟面试消息"
                    value={`${formatCount(portraitEvidence?.interviewMessages7d ?? 0)} 条`}
                    icon={MessageSquare}
                    tone="rose"
                  />
                  <MetricCard
                    title="近 7 天发帖"
                    value={`${formatCount(portraitEvidence?.posts7d ?? 0)} 次`}
                    icon={Edit3}
                    tone="sky"
                  />
                  <MetricCard
                    title="近 7 天获赞"
                    value={`${formatCount(portraitEvidence?.likesReceived7d ?? 0)} 次`}
                    icon={CheckCircle2}
                    tone="emerald"
                  />
                </section>

                <section className="grid gap-4 xl:grid-cols-[minmax(0,1.15fr)_minmax(18rem,0.85fr)]">
                  <div className="overflow-hidden rounded-[2rem] border border-slate-200/85 bg-[linear-gradient(145deg,rgba(244,247,255,0.98),rgba(255,255,255,0.98))] px-6 py-6 shadow-[0_20px_55px_rgba(79,70,229,0.12)] backdrop-blur-xl">
                    <div className="flex items-center justify-between gap-3">
                      <div>
                        <h3 className="text-[1.6rem] font-semibold leading-9 text-slate-950">
                          {portraitHeadline ?? "成长画像仍在建立中，先继续补充资料、练习与互动信号。"}
                        </h3>
                      </div>
                      <div className="flex h-12 w-12 items-center justify-center rounded-[1.2rem] bg-indigo-100 text-indigo-600 shadow-sm">
                        <Sparkles size={22} />
                      </div>
                    </div>
                    <p className="mt-4 text-[0.98rem] leading-8 text-slate-600">
                      {portraitSummary ?? "系统会结合你的资料完善度、面试练习、社区互动和技能进展，持续生成更稳定的成长画像结论。"}
                    </p>
                    <div className="mt-5 grid gap-3 md:grid-cols-2">
                      <div className="rounded-[1.35rem] border border-emerald-100 bg-emerald-50/70 px-4 py-4">
                        <div className="text-sm font-semibold text-emerald-700">画像信号说明</div>
                        <div className="mt-1 text-base font-semibold text-slate-900">{portraitSignalMeta.description}</div>
                      </div>
                      <div className="rounded-[1.35rem] border border-sky-100 bg-sky-50/70 px-4 py-4">
                        <div className="text-sm font-semibold text-sky-700">画像时效说明</div>
                        <div className="mt-1 text-base font-semibold text-slate-900">{portraitFreshnessMeta.description}</div>
                      </div>
                    </div>
                  </div>

                  <PortraitInsightCard
                    title="下一步建议"
                    description="这些建议会跟随你的阶段信号变化持续更新。"
                    icon={Target}
                    tone="sky"
                    items={portraitNextActions}
                    emptyText="继续完成一轮简历优化、模拟面试或社区互动后，这里会生成更具体的下一步动作。"
                    ordered
                  />
                </section>

                <section className="grid gap-4 xl:grid-cols-2">
                  <PortraitInsightCard
                    title="当前优势"
                    description="系统识别到你目前更稳定的成长亮点。"
                    icon={Check}
                    tone="emerald"
                    items={portraitStrengthTags}
                    emptyText="当前还没有沉淀出稳定优势标签，继续积累技能、练习和互动后会逐步生成。"
                  />
                  <PortraitInsightCard
                    title="优先补强"
                    description="这些点更值得你在下一阶段优先处理。"
                    icon={AlertCircle}
                    tone="amber"
                    items={portraitRiskTags}
                    emptyText="当前还没有识别出明显短板，保持练习节奏并继续刷新画像即可。"
                  />
                </section>

                <section className="overflow-hidden rounded-[2rem] border border-slate-200/85 bg-[#fcfdff]/96 shadow-[0_22px_60px_rgba(100,116,139,0.16)] backdrop-blur-xl">
                  <div className="border-b border-slate-200/80 bg-[#eef2f7]/78 px-8 py-5">
                    <h3 className="flex items-center text-xl font-semibold text-slate-900">
                      <Sparkles size={18} className="mr-2 text-indigo-500" />
                      成长关键词
                    </h3>
                    <p className="mt-1 text-base font-medium text-slate-500">
                      这里会展示当前较能代表你的关键词，帮助你快速了解自己的阶段特点。
                    </p>
                  </div>

                  <div className="p-8">
                    {portraitTags.length > 0 ? (
                      <div className="flex flex-wrap gap-3">
                        {portraitTags.map((tag) => (
                          <div
                            key={tag.code}
                            className="rounded-[1.2rem] border border-indigo-100 bg-[linear-gradient(180deg,rgba(238,242,255,0.88),rgba(255,255,255,0.98))] px-4 py-3 text-base font-semibold text-slate-700 shadow-sm"
                          >
                            <div className="flex items-center gap-2">
                              <span className="text-indigo-600">{tag.label}</span>
                              <span className="rounded-full bg-white px-2 py-0.5 text-xs font-bold text-slate-400">
                                {formatPortraitTagSource(tag.source)}
                              </span>
                            </div>
                            <div className="mt-2 text-sm font-medium text-slate-500">
                              匹配度：{formatConfidence(tag.confidence)}
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="rounded-[1.5rem] border border-dashed border-slate-300 bg-[#eef2f7] px-5 py-6 text-base leading-8 text-slate-500">
                        继续完善资料、参与互动后，这里会逐步生成你的成长关键词。
                      </div>
                    )}
                  </div>
                </section>
              </motion.div>
            ) : null}

            {activeTab === "privacy" ? (
              <motion.div
                key="privacy"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -12 }}
              >
                <section className="overflow-hidden rounded-[2rem] border border-slate-200/85 bg-[#fcfdff]/96 shadow-[0_22px_60px_rgba(100,116,139,0.16)] backdrop-blur-xl">
                  <div className="border-b border-slate-200/80 bg-[#eef2f7]/78 px-8 py-6">
                    <h2 className="flex items-center text-2xl font-semibold text-slate-900">
                      <Eye size={20} className="mr-2 text-indigo-500" />
                      谁可以看到我的资料
                    </h2>
                    <p className="mt-2 text-base font-medium text-slate-500">
                      你可以设置不同身份的用户可查看哪些信息。填写学校后，还可以单独设置“同校学生”的可见范围。
                    </p>
                  </div>

                  <div className="overflow-x-auto p-8">
                    <div className="min-w-[900px]">
                      {!profile.schoolName?.trim() ? (
                        <div className="mb-5 rounded-[1.35rem] border border-amber-200 bg-amber-50/80 px-5 py-4 text-base leading-8 text-amber-900">
                          你还没有填写学校信息，暂时无法单独设置“同校学生”的查看范围。建议先完善学校信息，再回来继续设置。
                        </div>
                      ) : null}

                      <div className="grid grid-cols-6 gap-4 border-b-2 border-slate-200/80 pb-4">
                        <div className="pl-2 text-base font-bold text-slate-500">资料内容</div>
                        <PrivacyRoleHeader icon={Globe} label="访客" tone="slate" />
                        <PrivacyRoleHeader icon={GraduationCap} label="同校学生" tone="blue" />
                        <PrivacyRoleHeader icon={Users} label="平台学生" tone="emerald" />
                        <PrivacyRoleHeader icon={User} label="导师" tone="indigo" />
                        <PrivacyRoleHeader icon={Building2} label="企业" tone="amber" />
                      </div>

                      <div className="space-y-2 pt-4">
                        {PRIVACY_GROUPS.map((group) => (
                          <div key={group.title}>
                            <PrivacyGroupHeader icon={group.icon} title={group.title} />
                            {group.rows.map((row) => (
                              <PrivacyMatrixRow
                                key={row.key}
                                label={row.label}
                                isSensitive={row.isSensitive}
                                value={privacyData[row.key]}
                                onToggle={(roleKey) => {
                                  setPrivacyData({
                                    ...privacyData,
                                    [row.key]: {
                                      ...privacyData[row.key],
                                      [roleKey]: !privacyData[row.key][roleKey],
                                    },
                                  });
                                }}
                              />
                            ))}
                          </div>
                        ))}
                      </div>
                    </div>
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
                <LazyProfileNotificationPreferencesPanel
                  variant="student"
                  title="通知提醒"
                  description="把学生侧常用的业务提醒放到你习惯的节奏里。站内收件箱始终保留记录，邮箱提醒默认关闭；系统公告与维护提醒由平台统一保障送达。"
                />
              </motion.div>
            ) : null}

            {activeTab === "security" ? (
              <motion.div
                key="security"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -12 }}
                className="space-y-6"
              >
                <section className="overflow-hidden rounded-[2rem] border border-slate-200/85 bg-[#fcfdff]/96 p-8 shadow-[0_22px_60px_rgba(100,116,139,0.16)] backdrop-blur-xl">
                  <div className="mb-8 flex items-center gap-3">
                    <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-[#eef2f7] text-slate-600">
                      <Shield size={20} />
                    </div>
                    <div>
                      <h2 className="text-xl font-semibold text-slate-900">安全设置</h2>
                      <p className="mt-1 text-base font-medium text-slate-500">管理登录邮箱和密码，保护账号安全。</p>
                    </div>
                  </div>

                  <div className="space-y-4">
                    <SecurityActionCard
                      icon={Mail}
                      title="登录邮箱"
                      description={profile.email}
                      actionLabel="更换邮箱"
                      onAction={() => {
                        setEmailModal(createEmailModalState({ isOpen: true }));
                        setEmailCountdown(0);
                      }}
                    />
                    <SecurityActionCard
                      icon={KeyRound}
                      title="登录密码"
                      description="建议定期更新密码，保护账号安全"
                      actionLabel="修改密码"
                      onAction={() => {
                        setPasswordModal(createPasswordModalState({ isOpen: true }));
                        setPasswordCountdown(0);
                      }}
                    />
                  </div>

                  <div className="mt-6 rounded-[1.5rem] border border-indigo-200 bg-indigo-100/80 px-5 py-4 text-base leading-8 text-indigo-900">
                    为了保障安全，更换邮箱和修改密码都需要完成验证码验证。
                  </div>
                </section>
              </motion.div>
            ) : null}
              </AnimatePresence>
            </div>
          </div>
        </div>
      </main>

      <AnimatePresence>
        {emailModal.isOpen ? (
          <ModalOverlay
            onClose={() => {
              if (emailModal.busy) {
                return;
              }
              setEmailModal(createEmailModalState());
              setEmailCountdown(0);
            }}
          >
            <div className="mb-6 text-center">
              <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-indigo-50 text-indigo-600">
                <Mail size={32} />
              </div>
                <h3 className="text-2xl font-semibold text-slate-900">更换登录邮箱</h3>
                <p className="mt-2 text-sm font-medium text-slate-500">
                  {emailModal.step === "verifyCurrent"
                    ? `请先完成当前邮箱 ${profile.email} 的验证`
                    : "请输入新的邮箱地址并完成验证"}
                </p>
            </div>

            <div className="space-y-5">
              {emailModal.step === "changeNew" ? (
                <div>
                  <label className="mb-2 block text-sm font-bold text-slate-700">新邮箱地址</label>
                  <input
                    type="email"
                    value={emailModal.newEmail}
                    onChange={(event) => setEmailModal((current) => ({
                      ...current,
                      newEmail: event.target.value,
                      debugHint: null,
                    }))}
                    placeholder="example@domain.com"
                    className="w-full rounded-xl border-2 border-slate-200 bg-slate-50 px-4 py-3 text-sm font-medium text-slate-700 outline-none transition-colors focus:border-indigo-400 focus:bg-white"
                  />
                </div>
              ) : null}

              <div>
                <label className="mb-2 block text-sm font-bold text-slate-700">验证码</label>
                <div className="flex gap-3">
                  <input
                    type="text"
                    maxLength={6}
                    value={emailModal.step === "verifyCurrent" ? emailModal.currentCode : emailModal.newEmailCode}
                    onChange={(event) => {
                      const nextValue = event.target.value.replace(/\D/g, "");
                      setEmailModal((current) => current.step === "verifyCurrent"
                        ? { ...current, currentCode: nextValue }
                        : { ...current, newEmailCode: nextValue });
                    }}
                    placeholder="6位数字"
                    className="min-w-0 flex-1 rounded-xl border-2 border-slate-200 bg-slate-50 px-4 py-3 text-center text-sm font-semibold tracking-[0.28em] text-slate-700 outline-none transition-colors focus:border-indigo-400 focus:bg-white"
                  />
                  <button
                    type="button"
                    disabled={emailCountdown > 0 || emailModal.busy}
                    onClick={() => void handleSendEmailCode(emailModal.step === "verifyCurrent" ? "CURRENT" : "NEW")}
                    className="whitespace-nowrap rounded-xl border border-indigo-100 bg-indigo-50 px-4 py-3 text-sm font-semibold text-indigo-700 transition-colors hover:bg-indigo-100 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {emailCountdown > 0 ? `${emailCountdown}s 后重发` : "获取验证码"}
                  </button>
                </div>
              </div>

              {emailModal.debugHint ? (
                <InlineDebugCodeCard
                  targetEmail={emailModal.debugHint.targetEmail}
                  code={emailModal.debugHint.code}
                />
              ) : null}

              <button
                type="button"
                disabled={emailModal.busy}
                onClick={() => {
                  if (emailModal.step === "verifyCurrent") {
                    void handleVerifyCurrentEmail();
                  } else {
                    void handleChangeEmail();
                  }
                }}
                className="inline-flex w-full items-center justify-center rounded-xl bg-slate-900 px-5 py-3.5 text-sm font-semibold text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {emailModal.busy ? (
                  <>
                    <RefreshCw size={16} className="mr-2 animate-spin" />
                    正在处理
                  </>
                ) : emailModal.step === "verifyCurrent" ? (
                  <>
                    下一步
                    <ArrowRight size={16} className="ml-2" />
                  </>
                ) : (
                  "确认修改"
                )}
              </button>
            </div>
          </ModalOverlay>
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
                    ? `请先完成当前邮箱 ${profile.email} 的验证`
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
                      className="min-w-0 flex-1 rounded-xl border-2 border-slate-200 bg-slate-50 px-4 py-3 text-center text-sm font-semibold tracking-[0.28em] text-slate-700 outline-none transition-colors focus:border-indigo-400 focus:bg-white"
                    />
                    <button
                      type="button"
                      disabled={passwordCountdown > 0 || passwordModal.busy}
                      onClick={() => void handleSendPasswordCode()}
                      className="whitespace-nowrap rounded-xl border border-indigo-100 bg-indigo-50 px-4 py-3 text-sm font-semibold text-indigo-700 transition-colors hover:bg-indigo-100 disabled:cursor-not-allowed disabled:opacity-60"
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
                    className="w-full rounded-xl border-2 border-slate-200 bg-slate-50 px-4 py-3 text-sm font-medium text-slate-700 outline-none transition-colors focus:border-indigo-400 focus:bg-white"
                  />
                  <p className="mt-2 text-xs leading-6 text-slate-500">
                    新密码至少 8 位，并需同时包含字母和数字。
                  </p>
                </div>
              )}

              {passwordModal.debugHint ? (
                <InlineDebugCodeCard
                  targetEmail={passwordModal.debugHint.targetEmail}
                  code={passwordModal.debugHint.code}
                />
              ) : null}

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
                  <>
                    验证并下一步
                    <ArrowRight size={16} className="ml-2" />
                  </>
                ) : (
                  "完成重置"
                )}
              </button>
            </div>
          </ModalOverlay>
        ) : null}
      </AnimatePresence>

      <AnimatePresence>
        {toast || floatingBarVisible ? (
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
                    <div className={joinClasses("flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition-colors duration-300", getToastPresentation(toast.tone).iconClassName)}>
                      {(() => {
                        const ToastIcon = getToastPresentation(toast.tone).icon;
                        return <ToastIcon size={18} />;
                      })()}
                    </div>
                    <div className="min-w-0 flex-1 text-center">
                      <div className={joinClasses("truncate text-base font-black tracking-[0.02em]", getToastPresentation(toast.tone).titleClassName)}>
                        {toast.tone === "cancel" ? toast.message : getToastPresentation(toast.tone).title}
                      </div>
                      {toast.tone === "cancel" ? null : (
                        <p className={joinClasses("truncate text-[15px] leading-5", getToastPresentation(toast.tone).messageClassName)}>
                          {toast.message}
                        </p>
                      )}
                    </div>
                    {toast.tone === "cancel" ? (
                      <span aria-hidden="true" className="block h-8 w-8 shrink-0" />
                    ) : (
                      <button
                        type="button"
                        onClick={() => setToast(null)}
                        className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-slate-200 bg-white/80 text-slate-500 transition-colors duration-300 hover:border-slate-300 hover:bg-white hover:text-slate-700"
                      >
                        <X size={15} />
                      </button>
                    )}
                  </div>
                </motion.div>
              ) : null}

              {floatingBarVisible ? (
                <motion.div
                  key="editor-floating-bar"
                  initial={{ y: 44, opacity: 0 }}
                  animate={{ y: 0, opacity: 1 }}
                  exit={{ y: 44, opacity: 0 }}
                  transition={{ type: "spring", stiffness: 300, damping: 30 }}
                  className="pointer-events-auto"
                >
                  <div className={joinClasses(
                    "w-full overflow-hidden rounded-full px-6 py-3.5 backdrop-blur-xl",
                    "transition-[background-color,border-color,box-shadow] duration-300 ease-out",
                    editorBarPresentation.shellClassName,
                  )}>
                    <div className="flex items-center justify-between gap-4">
                      <div className="flex min-w-0 items-center gap-3.5">
                        <div className={joinClasses("flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition-colors duration-300", editorBarPresentation.iconClassName)}>
                          {(() => {
                            const EditorBarIcon = editorBarPresentation.icon;
                            return <EditorBarIcon size={17} />;
                          })()}
                        </div>
                        <div className="min-w-0 flex-1 overflow-hidden text-center">
                          <AnimatePresence mode="wait" initial={false}>
                            <motion.div
                              key={editorFloatingMessage}
                              initial={{ opacity: 0, y: 10 }}
                              animate={{ opacity: 1, y: 0 }}
                              exit={{ opacity: 0, y: -10 }}
                              transition={{ duration: 0.26, ease: "easeOut" }}
                              className={joinClasses("truncate text-[15px] font-medium leading-6 transition-colors duration-300", editorBarPresentation.textClassName)}
                            >
                              {editorFloatingMessage}
                            </motion.div>
                          </AnimatePresence>
                        </div>
                      </div>
                      <div className="flex shrink-0 items-center gap-2.5">
                        <button
                          type="button"
                          onClick={handleResetChanges}
                          className={joinClasses(
                            "inline-flex h-10 items-center rounded-full border px-4 text-[14px] font-semibold transition-colors duration-300",
                            editorBarPresentation.secondaryButtonClassName,
                          )}
                        >
                          {activeTab === "profile" ? "取消编辑" : "撤销"}
                        </button>
                        <button
                          type="button"
                          disabled={activeTab === "profile" ? (savingProfile || !isProfileDirty) : (savingPrivacy || !isPrivacyDirty)}
                          onClick={() => {
                            if (activeTab === "profile") {
                              void handleSaveProfile();
                            } else {
                              void handleSavePrivacy();
                            }
                          }}
                          className={joinClasses(
                            "inline-flex h-10 items-center rounded-full px-5 text-[14px] font-semibold shadow-sm transition-colors duration-300 disabled:cursor-not-allowed disabled:opacity-60",
                            editorBarPresentation.primaryButtonClassName,
                          )}
                        >
                          {savingProfile || savingPrivacy ? (
                            <>
                              <RefreshCw size={16} className="mr-2 animate-spin" />
                              正在保存
                            </>
                          ) : (
                            activeTab === "profile" ? "保存资料" : "确认生效"
                          )}
                        </button>
                      </div>
                    </div>
                  </div>
                </motion.div>
              ) : null}
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}

function HeroBadge({ label, tone }: { label: string; tone: HeroBadgeTone }) {
  const toneClassName = {
    target: "border-sky-100 bg-sky-50/92 text-sky-700 shadow-[0_10px_24px_rgba(125,211,252,0.18)]",
    school: "border-emerald-100 bg-emerald-50/92 text-emerald-700 shadow-[0_10px_24px_rgba(110,231,183,0.18)]",
    major: "border-violet-100 bg-violet-50/92 text-violet-700 shadow-[0_10px_24px_rgba(196,181,253,0.18)]",
    grade: "border-amber-100 bg-amber-50/92 text-amber-700 shadow-[0_10px_24px_rgba(252,211,77,0.18)]",
  } satisfies Record<HeroBadgeTone, string>;

  return (
    <div className={joinClasses("rounded-full border px-4 py-2 text-sm font-medium transition-colors", toneClassName[tone])}>
      {label}
    </div>
  );
}

function StaticPanel({ value, emptyLabel }: { value: string; emptyLabel: string }) {
  return (
    <div className="rounded-[1.15rem] border border-slate-200/80 bg-[#f7f9fc] px-4 py-3.5 text-base font-medium leading-8 text-slate-700 whitespace-pre-wrap shadow-sm">
      {value.trim() ? value : <span className="text-slate-400">{emptyLabel}</span>}
    </div>
  );
}

function SocialPlatformIcon({
  platform,
  className,
  fallbackClassName,
  imageClassName,
}: {
  platform: SocialPlatformCode;
  className: string;
  fallbackClassName: string;
  imageClassName?: string;
}) {
  const option = getSocialPlatformOption(platform);
  const [imageFailed, setImageFailed] = useState(false);
  const FallbackIcon = option.icon;

  return (
    <span className={className}>
      {option.assetPath && !imageFailed ? (
        <img
          src={option.assetPath}
          alt={`${option.label} icon`}
          className={joinClasses("h-4 w-4 object-contain", imageClassName)}
          onError={() => setImageFailed(true)}
        />
      ) : (
        <FallbackIcon size={15} className={fallbackClassName} />
      )}
    </span>
  );
}

function EditableField({
  label,
  icon: Icon,
  value,
  isEditing,
  onChange,
  placeholder,
  type = "text",
  options = [],
  compact = false,
}: {
  label: string;
  icon: LucideIcon;
  value: string;
  isEditing: boolean;
  onChange: (value: string) => void;
  placeholder?: string;
  type?: "text" | "select";
  options?: string[];
  compact?: boolean;
}) {
  return (
    <div
      className={joinClasses(
        "rounded-[1.4rem] border border-slate-200/80 bg-[linear-gradient(180deg,rgba(239,244,250,0.96),rgba(255,255,255,0.98))] shadow-[0_12px_28px_rgba(100,116,139,0.10)]",
        compact ? "px-4 py-3" : "px-4 py-4",
      )}
    >
      <label className={joinClasses("flex items-center font-bold text-slate-700", compact ? "text-sm" : "text-base")}>
        <span
          className={joinClasses(
            "flex items-center justify-center rounded-2xl bg-[#e8eef8] text-indigo-600",
            compact ? "mr-2 h-7 w-7" : "mr-2.5 h-8 w-8",
          )}
        >
          <Icon size={compact ? 14 : 15} />
        </span>
        {label}
      </label>
      <div className={joinClasses("h-px bg-[linear-gradient(90deg,rgba(99,102,241,0.16),rgba(148,163,184,0.12),transparent)]", compact ? "mt-2" : "mt-2.5")} />
      <div className={joinClasses(compact ? "mt-2" : "mt-3")}>
        {isEditing ? (
          type === "select" ? (
            <select
              value={value}
              onChange={(event) => onChange(event.target.value)}
              className={joinClasses(
                "w-full rounded-[0.95rem] border-2 border-slate-200 bg-white font-medium text-slate-700 outline-none transition-colors focus:border-indigo-400",
                compact ? "px-3 py-2 text-sm" : "px-3.5 py-2.5 text-base",
              )}
            >
              <option value="">请选择</option>
              {options.map((option) => (
                <option key={option} value={option}>{option}</option>
              ))}
            </select>
          ) : (
            <input
              type="text"
              value={value}
              onChange={(event) => onChange(event.target.value)}
              placeholder={placeholder}
              className={joinClasses(
                "w-full rounded-[0.95rem] border-2 border-slate-200 bg-white font-medium text-slate-700 outline-none transition-colors focus:border-indigo-400 placeholder:text-slate-400",
                compact ? "px-3 py-2 text-sm" : "px-3.5 py-2.5 text-base",
              )}
            />
          )
        ) : (
          <div className={joinClasses("py-0.5 font-semibold text-slate-900", compact ? "text-[15px] leading-6" : "text-lg")}>
            {value.trim() ? value : <span className={joinClasses("font-medium text-slate-400", compact ? "text-sm" : "text-base")}>未填写</span>}
          </div>
        )}
      </div>
    </div>
  );
}

function SocialLinksField({
  socialLinks,
  isEditing,
  onAdd,
  onRemove,
  onChange,
}: {
  socialLinks: StudentSocialLink[];
  isEditing: boolean;
  onAdd: () => void;
  onRemove: (index: number) => void;
  onChange: (index: number, patch: Partial<StudentSocialLink>) => void;
}) {
  const normalizedSocialLinks = normalizeClientSocialLinks(socialLinks);

  return (
    <div className="rounded-[1.4rem] border border-slate-200/80 bg-[linear-gradient(180deg,rgba(239,244,250,0.96),rgba(255,255,255,0.98))] px-5 py-5 shadow-[0_12px_28px_rgba(100,116,139,0.10)]">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <label className="flex items-center text-base font-bold text-slate-700">
          <span className="mr-2.5 flex h-8 w-8 items-center justify-center rounded-2xl bg-[#e8eef8] text-indigo-600">
            <LinkIcon size={15} />
          </span>
          个人主页与社交账号
        </label>
        {isEditing ? (
          <button
            type="button"
            onClick={onAdd}
            className="inline-flex items-center justify-center rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-base font-semibold text-slate-600 transition-colors hover:border-indigo-200 hover:text-indigo-600"
          >
            <Plus size={15} className="mr-2" />
            添加平台
          </button>
        ) : (
          <div className="text-sm font-medium text-slate-400">
            已添加 {normalizedSocialLinks.length} 个平台账号
          </div>
        )}
      </div>
      <div className="mt-2.5 h-px bg-[linear-gradient(90deg,rgba(99,102,241,0.16),rgba(148,163,184,0.12),transparent)]" />
      <div className="mt-3">
        {isEditing ? (
          socialLinks.length > 0 ? (
            <div className="space-y-3">
              {socialLinks.map((socialLink, index) => {
                const option = getSocialPlatformOption(socialLink.platform);
                return (
                  <div
                    key={`${socialLink.platform}-${index}`}
                    className="grid gap-3 rounded-[1.15rem] border border-slate-200 bg-white px-4 py-4 lg:grid-cols-[minmax(0,13rem)_minmax(0,1fr)_auto]"
                  >
                    <div>
                      <div className="mb-2 text-sm font-bold text-slate-600">平台</div>
                      <select
                        value={socialLink.platform}
                        onChange={(event) => onChange(index, { platform: event.target.value as SocialPlatformCode })}
                        className="w-full rounded-[0.95rem] border-2 border-slate-200 bg-[#fbfcff] px-3.5 py-2.5 text-base font-medium text-slate-700 outline-none transition-colors focus:border-indigo-400"
                      >
                        {SOCIAL_PLATFORM_OPTIONS.map((platformOption) => (
                          <option
                            key={platformOption.value}
                            value={platformOption.value}
                            disabled={socialLinks.some((currentLink, itemIndex) => itemIndex !== index && currentLink.platform === platformOption.value)}
                          >
                            {platformOption.label}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <div className="mb-2 text-sm font-bold text-slate-600">账号 / 链接</div>
                      <input
                        type="text"
                        value={socialLink.value}
                        onChange={(event) => onChange(index, { value: event.target.value })}
                        placeholder={option.placeholder}
                        className="w-full rounded-[0.95rem] border-2 border-slate-200 bg-[#fbfcff] px-3.5 py-2.5 text-base font-medium text-slate-700 outline-none transition-colors focus:border-indigo-400 placeholder:text-slate-400"
                      />
                    </div>
                    <div className="flex items-end">
                      <button
                        type="button"
                        onClick={() => onRemove(index)}
                        className="inline-flex w-full items-center justify-center rounded-[0.95rem] border border-rose-200 bg-rose-50 px-4 py-2.5 text-base font-semibold text-rose-600 transition-colors hover:bg-rose-100 lg:w-auto"
                      >
                        <X size={15} className="mr-2" />
                        删除
                      </button>
                    </div>
                    <div className="lg:col-span-3 flex items-center gap-2 text-sm font-medium text-slate-500">
                      <SocialPlatformIcon
                        platform={socialLink.platform}
                          className="flex h-8 w-8 items-center justify-center rounded-2xl bg-[#e8eef8]"
                          fallbackClassName="text-indigo-500"
                        />
                      <span>{option.helper}</span>
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="rounded-[1.15rem] border border-dashed border-slate-200 bg-white px-4 py-5 text-base leading-8 text-slate-500">
              还没有添加主页或社交账号。点击右上角“添加平台”后，可以选择 GitHub、掘金、CSDN、知乎、哔哩哔哩等常用平台。
            </div>
          )
        ) : normalizedSocialLinks.length > 0 ? (
          <div className="grid gap-3 md:grid-cols-2">
            {normalizedSocialLinks.map((socialLink) => {
              const option = getSocialPlatformOption(socialLink.platform);
              return (
                <a
                  key={socialLink.platform}
                  href={buildSocialLinkHref(socialLink.platform, socialLink.value)}
                  target="_blank"
                  rel="noreferrer"
                  className="group rounded-[1.1rem] border border-slate-200 bg-[#fbfcff] px-4 py-4 transition-colors hover:border-indigo-200 hover:bg-indigo-50/40"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2 text-base font-bold text-slate-800">
                        <SocialPlatformIcon
                          platform={socialLink.platform}
                          className="flex h-8 w-8 items-center justify-center rounded-2xl bg-[#e8eef8] transition-colors group-hover:bg-indigo-100"
                          fallbackClassName="text-indigo-500 group-hover:text-indigo-600"
                        />
                        {option.label}
                      </div>
                      <div className="mt-2 truncate text-base font-semibold text-indigo-600">{socialLink.value}</div>
                      <div className="mt-1 text-sm font-medium text-slate-500">{option.helper}</div>
                    </div>
                    <ArrowRight size={16} className="mt-1 shrink-0 text-slate-300 transition-transform group-hover:translate-x-0.5 group-hover:text-indigo-500" />
                  </div>
                </a>
              );
            })}
          </div>
        ) : (
          <div className="py-1 text-base font-medium text-slate-400">未填写</div>
        )}
      </div>
    </div>
  );
}

function PrivacyRoleHeader({
  icon: Icon,
  label,
  tone,
}: {
  icon: LucideIcon;
  label: string;
  tone: "slate" | "blue" | "emerald" | "indigo" | "amber";
}) {
  const toneClassName = {
    slate: "bg-slate-200 text-slate-600",
    blue: "bg-blue-100 text-blue-600",
    emerald: "bg-emerald-100 text-emerald-600",
    indigo: "bg-indigo-100 text-indigo-600",
    amber: "bg-amber-100 text-amber-600",
  }[tone];

  return (
    <div className="flex flex-col items-center justify-center gap-1">
      <div className={joinClasses("flex h-8 w-8 items-center justify-center rounded-full", toneClassName)}>
        <Icon size={16} />
      </div>
      <span className="text-base font-semibold text-slate-700">{label}</span>
    </div>
  );
}

function PrivacyGroupHeader({
  icon: Icon,
  title,
}: {
  icon: LucideIcon;
  title: string;
}) {
  return (
    <div className="col-span-6 flex items-center gap-3 py-4 pl-2">
      <div className="h-px flex-1 bg-slate-200/80" />
      <div className="flex items-center gap-2 text-base font-semibold text-slate-800">
        <Icon size={16} className="text-indigo-500" />
        {title}
      </div>
      <div className="h-px flex-[3] bg-slate-200/80" />
    </div>
  );
}

function PrivacyMatrixRow({
  label,
  value,
  onToggle,
  isSensitive,
}: {
  label: string;
  value: VisibilityMatrix;
  onToggle: (roleKey: keyof VisibilityMatrix) => void;
  isSensitive?: boolean;
}) {
  const roles: Array<keyof VisibilityMatrix> = ["guest", "student", "platformStudent", "mentor", "enterprise"];
  return (
    <div className="grid grid-cols-6 gap-4 rounded-xl px-2 py-3 transition-colors hover:bg-[#f2f5f9]">
      <div className="flex items-center text-base font-semibold text-slate-700">
        {label}
        {isSensitive ? (
          <span className="ml-2 rounded bg-rose-50 px-1.5 py-0.5 text-xs text-rose-500">
            敏感
          </span>
        ) : null}
      </div>
      {roles.map((roleKey) => (
        <div key={roleKey} className="flex items-center justify-center">
          <CompactToggle checked={value[roleKey]} onChange={() => onToggle(roleKey)} />
        </div>
      ))}
    </div>
  );
}

function CompactToggle({
  checked,
  onChange,
}: {
  checked: boolean;
  onChange: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onChange}
      className={joinClasses(
        "relative inline-flex h-5 w-9 rounded-full border-2 border-transparent transition-colors",
        checked ? "bg-indigo-600" : "bg-slate-300",
      )}
      role="switch"
      aria-checked={checked}
    >
      <span className="sr-only">切换可见性</span>
      <span
        className={joinClasses(
          "pointer-events-none inline-block h-4 w-4 rounded-full bg-white shadow transition-transform",
          checked ? "translate-x-4" : "translate-x-0",
        )}
      />
    </button>
  );
}

function SecurityActionCard({
  icon: Icon,
  title,
  description,
  actionLabel,
  onAction,
}: {
  icon: LucideIcon;
  title: string;
  description: string;
  actionLabel: string;
  onAction: () => void;
}) {
  return (
    <div className="flex flex-col gap-4 rounded-[1.5rem] border border-slate-200/80 bg-[#eef2f7] px-5 py-5 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-center gap-4">
        <div className="flex h-12 w-12 items-center justify-center rounded-xl border border-slate-200 bg-[#fbfcff] text-indigo-600 shadow-sm">
          <Icon size={22} />
        </div>
        <div>
          <div className="text-lg font-semibold text-slate-900">{title}</div>
          <div className="mt-1 text-base font-medium text-slate-500">{description}</div>
        </div>
      </div>
      <button
        type="button"
        onClick={onAction}
        className="rounded-xl border-2 border-slate-200 bg-white px-5 py-2.5 text-base font-semibold text-slate-600 transition-colors hover:border-indigo-500 hover:text-indigo-600"
      >
        {actionLabel}
      </button>
    </div>
  );
}

function InlineDebugCodeCard({
  targetEmail,
  code,
}: {
  targetEmail: string;
  code: string;
}) {
  return (
    <div className="rounded-[1.25rem] border border-sky-100 bg-sky-50/80 px-4 py-4">
      <div className="text-base font-bold text-sky-700">验证提示</div>
      <div className="mt-2 text-base leading-7 text-sky-900">
        当前可直接使用下方验证码完成 <span className="font-semibold">{targetEmail}</span> 的验证：
      </div>
      <div className="mt-3 rounded-xl border border-white/80 bg-white px-4 py-3 text-center text-lg font-black tracking-[0.4em] text-sky-700 shadow-sm">
        {code}
      </div>
    </div>
  );
}

function MetricCard({
  title,
  value,
  icon: Icon,
  tone,
}: {
  title: string;
  value: string;
  icon: LucideIcon;
  tone: "teal" | "indigo" | "amber" | "rose" | "sky" | "emerald";
}) {
  const toneClassName = {
    teal: "bg-teal-100 text-teal-600",
    indigo: "bg-indigo-100 text-indigo-600",
    amber: "bg-amber-100 text-amber-600",
    rose: "bg-rose-100 text-rose-600",
    sky: "bg-sky-100 text-sky-600",
    emerald: "bg-emerald-100 text-emerald-600",
  }[tone];

  return (
    <div className="rounded-[1.55rem] border border-slate-200/80 bg-[#fbfcff]/94 px-5 py-4 shadow-[0_18px_45px_rgba(100,116,139,0.14)] backdrop-blur-xl">
      <div className="flex items-center justify-between">
        <div>
          <div className="text-sm font-semibold text-slate-500">{title}</div>
          <div className="mt-1.5 text-[1.9rem] font-black text-slate-900">{value}</div>
        </div>
        <div className={joinClasses("flex h-11 w-11 items-center justify-center rounded-[1rem]", toneClassName)}>
          <Icon size={20} />
        </div>
      </div>
    </div>
  );
}

function PortraitStatusBadge({
  label,
  tone,
}: {
  label: string;
  tone: "emerald" | "sky" | "amber" | "slate";
}) {
  const toneClassName = {
    emerald: "border-emerald-200/80 bg-emerald-50 text-emerald-700",
    sky: "border-sky-200/80 bg-sky-50 text-sky-700",
    amber: "border-amber-200/80 bg-amber-50 text-amber-700",
    slate: "border-white/16 bg-white/10 text-indigo-100",
  }[tone];

  return (
    <span className={joinClasses("inline-flex items-center rounded-full border px-3 py-1.5 text-sm font-semibold backdrop-blur-md", toneClassName)}>
      {label}
    </span>
  );
}

function PortraitInsightCard({
  title,
  description,
  icon: Icon,
  tone,
  items,
  emptyText,
  ordered = false,
}: {
  title: string;
  description: string;
  icon: LucideIcon;
  tone: "emerald" | "amber" | "sky";
  items: string[];
  emptyText: string;
  ordered?: boolean;
}) {
  const toneStyles = {
    emerald: {
      shell: "border-emerald-100/90 bg-[linear-gradient(180deg,rgba(236,253,245,0.92),rgba(255,255,255,0.98))]",
      icon: "bg-emerald-100 text-emerald-700",
      marker: "bg-emerald-100 text-emerald-700",
      heading: "text-emerald-700",
    },
    amber: {
      shell: "border-amber-100/90 bg-[linear-gradient(180deg,rgba(255,251,235,0.94),rgba(255,255,255,0.98))]",
      icon: "bg-amber-100 text-amber-700",
      marker: "bg-amber-100 text-amber-700",
      heading: "text-amber-700",
    },
    sky: {
      shell: "border-sky-100/90 bg-[linear-gradient(180deg,rgba(240,249,255,0.94),rgba(255,255,255,0.98))]",
      icon: "bg-sky-100 text-sky-700",
      marker: "bg-sky-100 text-sky-700",
      heading: "text-sky-700",
    },
  }[tone];

  return (
    <div className={joinClasses("overflow-hidden rounded-[2rem] border px-6 py-6 shadow-[0_20px_55px_rgba(100,116,139,0.12)] backdrop-blur-xl", toneStyles.shell)}>
      <div className="flex items-start gap-4">
        <div className={joinClasses("flex h-12 w-12 items-center justify-center rounded-[1.15rem] shadow-sm", toneStyles.icon)}>
          <Icon size={20} />
        </div>
        <div className="min-w-0 flex-1">
          <h3 className={joinClasses("text-xl font-semibold", toneStyles.heading)}>{title}</h3>
          <p className="mt-2 text-sm leading-7 text-slate-500">{description}</p>
        </div>
      </div>
      {items.length > 0 ? (
        <div className="mt-5 space-y-3">
          {items.map((item, index) => (
            <div key={`${title}-${item}`} className="flex items-start gap-3 rounded-[1.2rem] border border-white/80 bg-white/88 px-4 py-4 shadow-sm">
              <span className={joinClasses("mt-0.5 inline-flex h-7 min-w-7 items-center justify-center rounded-full text-sm font-bold", toneStyles.marker)}>
                {ordered ? index + 1 : "•"}
              </span>
              <div className="min-w-0 flex-1 text-[0.98rem] leading-7 text-slate-700">{item}</div>
            </div>
          ))}
        </div>
      ) : (
        <div className="mt-5 rounded-[1.3rem] border border-dashed border-slate-300 bg-white/78 px-4 py-5 text-sm leading-7 text-slate-500">
          {emptyText}
        </div>
      )}
    </div>
  );
}

function ModalOverlay({
  children,
  onClose,
}: {
  children: React.ReactNode;
  onClose: () => void;
}) {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/45 p-4 backdrop-blur-sm"
      onClick={onClose}
    >
      <motion.div
        initial={{ scale: 0.94, y: 18, opacity: 0 }}
        animate={{ scale: 1, y: 0, opacity: 1 }}
        exit={{ scale: 0.96, y: 18, opacity: 0 }}
        transition={{ type: "spring", bounce: 0.3 }}
        className="relative w-full max-w-md rounded-[2rem] bg-white p-8 shadow-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <button
          type="button"
          onClick={onClose}
          className="absolute right-4 top-4 flex h-8 w-8 items-center justify-center rounded-full bg-slate-100 text-slate-500 transition-colors hover:bg-slate-200"
        >
          <X size={18} />
        </button>
        {children}
      </motion.div>
    </motion.div>
  );
}
