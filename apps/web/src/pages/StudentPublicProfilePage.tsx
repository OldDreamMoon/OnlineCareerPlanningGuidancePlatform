import { motion } from "framer-motion";
import {
  ArrowLeft,
  Award,
  Bot,
  BriefcaseBusiness,
  Compass,
  ExternalLink,
  FileText,
  Flame,
  Globe,
  GraduationCap,
  History,
  Link as LinkIcon,
  MessageSquare,
  Sparkles,
  Target,
  Trophy,
  TrendingUp,
  UserRound,
  Users,
  type LucideIcon,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import StudentIdentityAvatar from "../components/avatar/StudentIdentityAvatar";
import WorkspacePageLoadingScreen from "../components/WorkspacePageLoadingScreen";
import StudentWorkspaceTopbar, { buildStudentWorkspacePrimaryNav } from "../components/student/StudentWorkspaceTopbar";
import { ApiClientError, apiRequest } from "../lib/apiClient";
import { formatCount, formatDateTime } from "../lib/formatters";
import { buildStudentNickname } from "../lib/studentNames";
import { buildStudentPublicProfileHref, readStudentPublicProfileSnapshot, type StudentPublicProfileSnapshot } from "../lib/studentPublicProfile";

type TimeValue = number | string;

type VisibilityMatrix = {
  guest: boolean;
  student: boolean;
  platformStudent: boolean;
  mentor: boolean;
  enterprise: boolean;
};

type ViewerAudienceKey = keyof VisibilityMatrix;

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

type StudentPublicProfileResponse = {
  userId: number;
  displayName: string;
  tier: string | null;
  realName: string | null;
  jobStatus: string | null;
  schoolName: string | null;
  major: string | null;
  grade: string | null;
  gpa: string | null;
  targetPosition: string | null;
  honors: string | null;
  skillTags: string[] | null;
  selfIntro: string | null;
  avatar: {
    uploaded: boolean;
    contentType: string | null;
    updatedAt: TimeValue | null;
  } | null;
  socialLinks: StudentSocialLink[] | null;
  privacy: {
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
  portrait: {
    tags: Array<{
      label: string;
      source: string;
    }> | null;
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

const SOCIAL_PLATFORM_META: Record<SocialPlatformCode, { label: string; helper: string; assetPath: string; baseUrl?: string }> = {
  GITHUB: {
    label: "GitHub",
    helper: "代码仓库 / 开源主页",
    assetPath: "/social-platform-icons/github.svg",
    baseUrl: "https://github.com/",
  },
  PORTFOLIO: {
    label: "作品集 / 个人站",
    helper: "个人网站与展示主页",
    assetPath: "/social-platform-icons/portfolio-placeholder.svg",
  },
  GITEE: {
    label: "Gitee",
    helper: "国内代码托管主页",
    assetPath: "/social-platform-icons/gitee.svg",
    baseUrl: "https://gitee.com/",
  },
  JUEJIN: {
    label: "掘金",
    helper: "技术文章与个人主页",
    assetPath: "/social-platform-icons/juejin.svg",
    baseUrl: "https://juejin.cn/user/",
  },
  CSDN: {
    label: "CSDN",
    helper: "技术博客与专栏主页",
    assetPath: "/social-platform-icons/csdn.svg",
    baseUrl: "https://blog.csdn.net/",
  },
  ZHIHU: {
    label: "知乎",
    helper: "问答与专栏主页",
    assetPath: "/social-platform-icons/zhihu.svg",
    baseUrl: "https://www.zhihu.com/people/",
  },
  BILIBILI: {
    label: "哔哩哔哩",
    helper: "视频内容与个人空间",
    assetPath: "/social-platform-icons/bilibili.svg",
    baseUrl: "https://space.bilibili.com/",
  },
  XIAOHONGSHU: {
    label: "小红书",
    helper: "内容笔记与主页账号",
    assetPath: "/social-platform-icons/xiaohongshu.svg",
    baseUrl: "https://www.xiaohongshu.com/user/profile/",
  },
  WEIBO: {
    label: "微博",
    helper: "公开动态与账号主页",
    assetPath: "/social-platform-icons/weibo-multicolor.svg",
    baseUrl: "https://weibo.com/u/",
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

  const baseUrl = SOCIAL_PLATFORM_META[platform]?.baseUrl;
  return baseUrl ? `${baseUrl}${encodeURIComponent(normalized.replace(/^@/, ""))}` : `https://${normalized}`;
}

function fallbackProfileTitle(snapshot: StudentPublicProfileSnapshot | null, studentUserId: number) {
  return snapshot?.displayName?.trim() || `学生 ${studentUserId}`;
}

function normalizeTextValue(value: string | null | undefined) {
  const normalized = value?.trim().replace(/\s+/g, " ");
  return normalized ? normalized : null;
}

function splitMultilineText(value: string | null | undefined) {
  return (value ?? "")
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function resolveViewerAudienceKey(role: string | null | undefined, isOwnProfile: boolean): ViewerAudienceKey {
  if (role === "STUDENT") {
    return isOwnProfile ? "student" : "platformStudent";
  }
  if (role === "MENTOR") {
    return "mentor";
  }
  if (role === "ENTERPRISE") {
    return "enterprise";
  }
  return "guest";
}

const VIEWER_AUDIENCE_LABEL_MAP: Record<ViewerAudienceKey, string> = {
  guest: "访客视角",
  student: "本人视角",
  platformStudent: "学生视角",
  mentor: "导师视角",
  enterprise: "企业视角",
};

function isVisibleToCurrentAudience(
  visibility: VisibilityMatrix | null | undefined,
  audienceKey: ViewerAudienceKey,
) {
  if (!visibility) {
    return false;
  }
  return visibility[audienceKey];
}

function shouldRenderAudienceControlledField(
  value: string | null | undefined,
  visibility: VisibilityMatrix | null | undefined,
  audienceKey: ViewerAudienceKey,
) {
  return Boolean(normalizeTextValue(value)) || isVisibleToCurrentAudience(visibility, audienceKey);
}

function getTierLabel(tier: string | null | undefined) {
  return tier === "PREMIUM" ? "VIP 会员" : "普通用户";
}

function GlassCard({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section
      className={joinClasses(
        "relative rounded-[2rem] border border-white/70 bg-white/88 p-6 shadow-[0_22px_60px_rgba(148,163,184,0.16)] backdrop-blur-xl",
        className,
      )}
    >
      {children}
    </section>
  );
}

function SectionHeader({
  sectionLabel: _sectionLabel,
  title,
  description,
  icon: Icon,
}: {
  sectionLabel: string;
  title: string;
  description?: string;
  icon: LucideIcon;
}) {
  return (
    <div className="mb-5 flex items-center justify-between gap-4 border-b border-slate-100 pb-5">
      <div className="flex min-h-[3.65rem] flex-col justify-center">
        <h2 className="text-[1.75rem] font-black leading-tight tracking-tight text-slate-950">{title}</h2>
        {description ? (
          <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">{description}</p>
        ) : null}
      </div>
      <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border border-slate-100 bg-slate-50 text-slate-700 shadow-sm">
        <Icon size={19} />
      </div>
    </div>
  );
}

function PublicStatCard({
  label,
  value,
  helper,
  icon: Icon,
  tone,
}: {
  label: string;
  value: string;
  helper: string;
  icon: LucideIcon;
  tone: {
    iconWrap: string;
    iconColor: string;
    valueColor: string;
  };
}) {
  return (
    <div className="relative overflow-hidden rounded-[1.55rem] border border-white/70 bg-white/84 p-5 shadow-[0_14px_34px_rgba(148,163,184,0.12)] backdrop-blur-md">
      <div className="flex items-center justify-between gap-3">
        <div className="text-[13px] font-bold tracking-[0.08em] text-slate-500">{label}</div>
        <div className={joinClasses("flex h-11 w-11 items-center justify-center rounded-full", tone.iconWrap)}>
          <Icon size={18} className={tone.iconColor} />
        </div>
      </div>
      <div className={joinClasses("mt-4 text-[2rem] font-black tracking-tight", tone.valueColor)}>{value}</div>
      <div className="mt-2 text-sm leading-6 text-slate-500">{helper}</div>
    </div>
  );
}

function BasicInfoItemCard({
  label,
  value,
  icon: Icon,
  tone,
}: {
  label: string;
  value: string;
  icon: LucideIcon;
  tone: {
    iconWrap: string;
    iconColor: string;
    valueColor?: string;
  };
}) {
  return (
    <div className="rounded-[1.35rem] border border-slate-200/80 bg-slate-50/92 px-4 py-4 shadow-[0_10px_24px_rgba(148,163,184,0.08)]">
      <div className="flex items-center gap-3">
        <div className={joinClasses("flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl", tone.iconWrap)}>
          <Icon size={18} className={tone.iconColor} />
        </div>
        <div className="min-w-0">
          <div className="text-sm font-semibold text-slate-500">{label}</div>
          <div className={joinClasses("mt-1 text-[15px] font-semibold leading-6 text-slate-900", tone.valueColor)}>{value}</div>
        </div>
      </div>
    </div>
  );
}

function footprintMetaTone(detail: string | null | undefined) {
  const text = detail?.trim() ?? "";
  if (text.includes("评论")) {
    return {
      dotWrap: "bg-violet-100 text-violet-600",
      icon: MessageSquare,
    };
  }
  if (text.includes("榜") || text.includes("成就")) {
    return {
      dotWrap: "bg-amber-100 text-amber-600",
      icon: Trophy,
    };
  }
  return {
    dotWrap: "bg-sky-100 text-sky-600",
    icon: FileText,
  };
}

export default function StudentPublicProfilePage() {
  const { studentUserId: studentUserIdParam } = useParams<{ studentUserId: string }>();
  const { role, userId } = useAuth();

  const studentUserId = Number(studentUserIdParam);
  const isValidStudentUserId = Number.isInteger(studentUserId) && studentUserId > 0;
  const snapshot = useMemo(
    () => (isValidStudentUserId ? readStudentPublicProfileSnapshot(studentUserId) : null),
    [studentUserId],
  );
  const [profile, setProfile] = useState<StudentPublicProfileResponse | null>(null);
  const [loading, setLoading] = useState(isValidStudentUserId);
  const [error, setError] = useState<string | null>(null);

  const isOwnProfile = role === "STUDENT" && userId === studentUserId;
  const isProfileStale = profile !== null && profile.userId !== studentUserId;
  const viewerAudienceKey = resolveViewerAudienceKey(role, isOwnProfile);
  const viewerAudienceLabel = VIEWER_AUDIENCE_LABEL_MAP[viewerAudienceKey];

  useEffect(() => {
    if (!isValidStudentUserId) {
      setProfile(null);
      setError(null);
      setLoading(false);
      return;
    }

    let active = true;
    setLoading(true);
    setError(null);
    setProfile(null);

    void apiRequest<StudentPublicProfileResponse>(`/profiles/students/${studentUserId}/public`)
      .then((response) => {
        if (active) {
          setProfile(response);
        }
      })
      .catch((requestError) => {
        if (active) {
          setError(snapshot ? null : toUserMessage(requestError, "个人空间加载失败，请稍后再试。"));
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [isValidStudentUserId, snapshot, studentUserId]);

  if (!isValidStudentUserId) {
    return <Navigate to="/community" replace />;
  }

  if (role === "ADMIN") {
    return <Navigate to="/admin/dashboard" replace />;
  }

  if (loading || isProfileStale) {
    return (
      <WorkspacePageLoadingScreen
        title="正在准备个人空间"
        description="正在整合个人资料、社区互动与平台成长数据，请稍候。"
      />
    );
  }

  const fallbackName = fallbackProfileTitle(snapshot, studentUserId);
  const nickname = buildStudentNickname(profile, snapshot?.displayName, fallbackName);
  const visibleRealName = normalizeTextValue(profile?.realName);
  const visibleJobStatus = profile?.jobStatus?.trim() || null;
  const visibleSchool = profile?.schoolName?.trim() || null;
  const visibleMajor = profile?.major?.trim() || null;
  const visibleGrade = profile?.grade?.trim() || null;
  const visibleGpa = profile?.gpa?.trim() || null;
  const visibleTargetPosition = profile?.targetPosition?.trim() || null;
  const visibleIntro = profile?.selfIntro?.trim() || null;
  const visibleHonors = splitMultilineText(profile?.honors);
  const visibleSkillTags = (profile?.skillTags ?? []).filter(Boolean);
  const visiblePortraitTags = profile?.portrait?.tags?.map((item) => item.label).filter(Boolean) ?? [];
  const visibleSocialLinks = (profile?.socialLinks ?? []).filter((item) => item.value?.trim());
  const communityScore = profile?.communityScore7d ?? snapshot?.communityScore7d ?? null;
  const postCount = profile?.portrait?.evidence?.posts7d ?? snapshot?.postCount ?? null;
  const commentCount = profile?.portrait?.evidence?.comments7d ?? snapshot?.commentCount ?? null;
  const likeReceivedCount = profile?.portrait?.evidence?.likesReceived7d ?? snapshot?.likeReceivedCount ?? null;
  const recentActivities = snapshot?.recentActivities ?? [];
  const latestActivityLabel = snapshot?.latestActivityAt ? formatDateTime(snapshot.latestActivityAt) : "待同步";
  const masteredSkills = profile?.portrait?.evidence?.masteredSkills ?? null;
  const learningSkills = profile?.portrait?.evidence?.learningSkills ?? null;
  const interviewMessages = profile?.portrait?.evidence?.interviewMessages7d ?? null;
  const leaderboardRankLabel = snapshot?.leaderboardRank ? `Top ${snapshot.leaderboardRank}` : "待同步";
  const canShowRealName = shouldRenderAudienceControlledField(
    visibleRealName,
    profile?.privacy.realName,
    viewerAudienceKey,
  );
  const canShowAcademic = shouldRenderAudienceControlledField(
    visibleGpa,
    profile?.privacy.academic,
    viewerAudienceKey,
  );
  const basicInfoItems = [
    canShowRealName
      ? {
          label: "真实姓名",
          value: visibleRealName || (isOwnProfile ? "你还没有填写真实姓名" : "当前未展示真实姓名"),
          icon: UserRound,
          tone: {
            iconWrap: "bg-indigo-50",
            iconColor: "text-indigo-600",
          },
        }
      : null,
    canShowAcademic
      ? {
          label: "学业表现",
          value: visibleGpa || (isOwnProfile ? "你还没有补充学业表现" : "当前未展示学业表现"),
          icon: GraduationCap,
          tone: {
            iconWrap: "bg-emerald-50",
            iconColor: "text-emerald-600",
          },
        }
      : null,
    {
      label: "平台身份",
      value: getTierLabel(profile?.tier),
      icon: Sparkles,
      tone: {
        iconWrap: profile?.tier === "PREMIUM" ? "bg-amber-50" : "bg-slate-100",
        iconColor: profile?.tier === "PREMIUM" ? "text-amber-600" : "text-slate-600",
        valueColor: profile?.tier === "PREMIUM" ? "text-amber-700" : undefined,
      },
    },
    {
      label: "最近活跃",
      value: latestActivityLabel,
      icon: History,
      tone: {
        iconWrap: "bg-sky-50",
        iconColor: "text-sky-600",
      },
    },
  ].filter((item): item is NonNullable<typeof item> => Boolean(item));
  const introText = visibleIntro || (isOwnProfile
    ? "这里展示的是你当前个人空间的实际对外呈现效果，更多资料维护仍在资料中心完成。"
    : "这里展示的是学生选择公开的信息，以及平台侧聚合出的成长信号与社区足迹。");
  const heroTags = [
    visibleJobStatus
      ? {
          label: visibleJobStatus,
          tone: "border-emerald-200/20 bg-emerald-400/12 text-emerald-100",
          icon: Flame,
        }
      : null,
    visibleTargetPosition
      ? {
          label: visibleTargetPosition,
          tone: "border-sky-200/20 bg-sky-400/12 text-sky-100",
          icon: Target,
        }
      : null,
    [visibleSchool, visibleMajor, visibleGrade].filter(Boolean).join(" · ")
      ? {
          label: [visibleSchool, visibleMajor, visibleGrade].filter(Boolean).join(" · "),
          tone: "border-white/15 bg-white/10 text-indigo-50",
          icon: GraduationCap,
        }
      : null,
  ].filter(Boolean) as Array<{
    label: string;
    tone: string;
    icon: LucideIcon;
  }>;

  return (
    <div className="relative min-h-screen bg-[#eef3ff] text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(79,70,229,0.14),transparent_34%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.10),transparent_28%),linear-gradient(180deg,#edf3ff_0%,#f6f8fc_56%,#f8fafc_100%)]" />
        <div className="absolute -left-20 top-40 h-64 w-64 rounded-full bg-sky-200/30 blur-3xl" />
        <div className="absolute bottom-10 right-0 h-72 w-72 rounded-full bg-indigo-200/35 blur-3xl" />
      </div>

      {role === "STUDENT" ? (
        <StudentWorkspaceTopbar
          sectionLabel="Personal Space"
          title="个人空间"
          navItems={buildStudentWorkspacePrimaryNav()}
          leftAddon={(
            <Link
              to="/community"
              className="inline-flex h-10 items-center rounded-full border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
            >
              <ArrowLeft size={16} className="mr-2" />
              返回社区
            </Link>
          )}
          rightActions={(
            <Link
              to="/community/leaderboard"
              className="hidden h-10 items-center rounded-full border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600 lg:inline-flex"
            >
              <Trophy size={16} className="mr-2" />
              贡献榜
            </Link>
          )}
          position="sticky"
        />
      ) : (
        <nav className="sticky top-0 z-40 border-b border-white/60 bg-white/78 px-4 py-3 backdrop-blur-xl sm:px-6">
          <div className="mx-auto flex w-full max-w-[96rem] items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-600 via-violet-500 to-teal-400 text-white shadow-[0_18px_35px_rgba(79,70,229,0.28)]">
                <Users size={20} />
              </div>
              <div className="flex min-h-11 items-center">
                <div className="text-xl font-bold leading-tight text-slate-950">个人空间</div>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <Link
                to="/community"
                className="inline-flex h-10 items-center rounded-full border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
              >
                <ArrowLeft size={16} className="mr-2" />
                返回社区
              </Link>
              <Link
                to="/community/leaderboard"
                className="inline-flex h-10 items-center rounded-full border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-600 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
              >
                <Trophy size={16} className="mr-2" />
                贡献榜
              </Link>
            </div>
          </div>
        </nav>
      )}

      <main className="relative z-10 mx-auto w-full max-w-[96rem] px-4 pb-12 pt-8 sm:px-6 lg:px-8">
        <motion.div initial={{ opacity: 0, y: 18 }} animate={{ opacity: 1, y: 0 }} className="space-y-6">
          <section className="relative overflow-hidden rounded-[2.35rem] bg-gradient-to-br from-indigo-950 via-slate-900 to-indigo-900 px-6 py-8 text-white shadow-[0_24px_58px_rgba(30,27,75,0.28)] lg:px-10 lg:py-10">
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(255,255,255,0.10),transparent_36%),linear-gradient(135deg,transparent_0%,rgba(255,255,255,0.04)_100%)]" />
            <div className="absolute -right-16 -top-16 h-56 w-56 rounded-full bg-indigo-400/16 blur-[90px]" />
            <div className="absolute -bottom-16 left-10 h-52 w-52 rounded-full bg-teal-400/12 blur-[100px]" />
            <div className="absolute -right-10 top-2 opacity-[0.06]">
              <Sparkles size={220} />
            </div>

            <div className="relative z-10 flex flex-col gap-8 xl:flex-row xl:items-center xl:justify-between">
              <div className="flex flex-col gap-6 xl:flex-row xl:items-center">
                {profile ? (
                  <StudentIdentityAvatar
                    userId={profile.userId}
                    role="STUDENT"
                    displayName={nickname}
                    avatar={profile.avatar}
                    tier={profile.tier}
                    avatarPath={`/profiles/students/${profile.userId}/avatar`}
                    className="h-24 w-24 border-4 border-white/10 text-2xl shadow-[0_18px_40px_rgba(15,23,42,0.24)] md:h-32 md:w-32"
                    textClassName="text-3xl"
                  />
                ) : (
                  <div className="flex h-24 w-24 items-center justify-center rounded-full border-4 border-white/10 bg-gradient-to-br from-indigo-400 to-violet-500 text-3xl font-black text-white shadow-[0_18px_40px_rgba(15,23,42,0.24)] md:h-32 md:w-32">
                    {nickname.slice(0, 1)}
                  </div>
                )}

                <div className="max-w-3xl">
                  <h1 className="text-3xl font-black tracking-tight text-white md:text-[2.8rem]">{nickname}</h1>
                  <p className="mt-2 text-sm font-semibold text-indigo-100/80">
                    在社区里留下可被看见的成长轨迹
                  </p>
                  <p className="mt-4 max-w-2xl text-sm leading-7 text-indigo-50/88 lg:text-[15px]">
                    {introText}
                  </p>

                  {heroTags.length > 0 ? (
                    <div className="mt-5 flex flex-wrap gap-2.5">
                      {heroTags.map((tag) => (
                        <div
                          key={tag.label}
                          className={joinClasses(
                            "inline-flex items-center gap-2 rounded-full border px-3.5 py-2 text-sm font-semibold backdrop-blur-sm",
                            tag.tone,
                          )}
                        >
                          <tag.icon size={14} />
                          {tag.label}
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="mt-5 inline-flex rounded-full border border-white/12 bg-white/8 px-4 py-2 text-sm font-semibold text-indigo-50/80 backdrop-blur-sm">
                      当前个人空间更偏向平台内成长名片，会逐步补足身份、学习与互动形成的公开线索。
                    </div>
                  )}
                </div>
              </div>

              <div className="grid gap-3 sm:grid-cols-2 xl:w-[460px]">
                <div className="rounded-[1.45rem] border border-white/12 bg-white/8 p-5 backdrop-blur-md">
                  <div className="flex items-center gap-2 text-sm font-bold text-indigo-100">
                    <TrendingUp size={14} className="text-indigo-100" />
                    社区贡献分
                  </div>
                  <div className="mt-4 text-[2rem] font-black tracking-tight text-white">
                    {communityScore !== null ? formatCount(communityScore) : "—"}
                  </div>
                  <div className="mt-2 text-sm leading-6 text-indigo-100/74">近 7 天公开互动沉淀出的成长热度与内容影响力。</div>
                </div>
                <div className="rounded-[1.45rem] border border-white/12 bg-white/8 p-5 backdrop-blur-md">
                  <div className="flex items-center gap-2 text-sm font-bold text-emerald-100">
                    <Trophy size={14} className="text-emerald-100" />
                    贡献榜表现
                  </div>
                  <div className="mt-4 text-[2rem] font-black tracking-tight text-emerald-300">{leaderboardRankLabel}</div>
                  <div className="mt-2 text-sm leading-6 text-indigo-100/74">结合公开内容表现与互动反馈，快速感知当前影响力位置。</div>
                </div>
              </div>
            </div>
          </section>

          {error ? (
            <div className="rounded-[1.5rem] border border-rose-200 bg-rose-50 px-5 py-4 text-sm text-rose-700">
              {error}
            </div>
          ) : null}

          <section className="grid gap-4 lg:grid-cols-4">
            <PublicStatCard
              label="近 7 天发帖"
              value={postCount !== null ? formatCount(postCount) : "—"}
              helper="持续输出经验与思考，是人物空间可信度的第一层信号。"
              icon={FileText}
              tone={{
                iconWrap: "bg-sky-50",
                iconColor: "text-sky-600",
                valueColor: "text-sky-600",
              }}
            />
            <PublicStatCard
              label="近 7 天评论"
              value={commentCount !== null ? formatCount(commentCount) : "—"}
              helper="互动频率会直接影响社区里的活跃感、回应感与协作感。"
              icon={MessageSquare}
              tone={{
                iconWrap: "bg-violet-50",
                iconColor: "text-violet-600",
                valueColor: "text-violet-600",
              }}
            />
            <PublicStatCard
              label="近 7 天获赞"
              value={likeReceivedCount !== null ? formatCount(likeReceivedCount) : "—"}
              helper="被动获得的认可，更能体现内容价值被他人接收的程度。"
              icon={Trophy}
              tone={{
                iconWrap: "bg-amber-50",
                iconColor: "text-amber-600",
                valueColor: "text-amber-600",
              }}
            />
            <PublicStatCard
              label="最近活跃"
              value={latestActivityLabel}
              helper="最近活跃时间会优先承接社区公开行为，用来补足成长时间线。"
              icon={History}
              tone={{
                iconWrap: "bg-slate-100",
                iconColor: "text-slate-600",
                valueColor: "text-slate-800",
              }}
            />
          </section>

          <div className="grid gap-6 lg:grid-cols-12">
            <div className="space-y-6 lg:col-span-7">
              <GlassCard>
                <SectionHeader
                  sectionLabel="Profile Story"
                  title="个人简介"
                  description="这一部分会优先展示公开简介、荣誉与对当前访问身份可见的基础资料。"
                  icon={UserRound}
                />

                <div className="space-y-5">
                  <div className="space-y-4">
                    <div className="rounded-[1.55rem] border border-slate-200/80 bg-slate-50/92 px-5 py-5">
                      <div className="flex items-center gap-2 text-lg font-bold text-slate-900">
                        <UserRound size={18} className="text-slate-500" />
                        个人简介
                      </div>
                      <p className="mt-4 text-[15px] leading-8 text-slate-600">{introText}</p>
                    </div>

                    <div className="rounded-[1.55rem] border border-amber-100 bg-gradient-to-br from-amber-50/92 to-orange-50/50 px-5 py-5">
                      <div className="flex items-center gap-2 text-lg font-bold text-amber-900">
                        <Award size={18} className="text-amber-600" />
                        荣誉与成就
                      </div>
                      {visibleHonors.length > 0 ? (
                        <div className="mt-4 space-y-2.5">
                          {visibleHonors.map((honor) => (
                            <div key={honor} className="flex items-start gap-2.5 text-sm leading-7 text-amber-900/80">
                              <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-amber-400" />
                              <span>{honor}</span>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <div className="mt-4 rounded-[1.15rem] border border-amber-100/80 bg-white/65 px-4 py-4 text-sm leading-7 text-amber-900/70">
                          当前还没有展示更多公开荣誉信息。
                        </div>
                      )}
                    </div>
                  </div>

                  <div className="rounded-[1.55rem] border border-slate-200/85 bg-white/96 px-5 py-5 shadow-[0_10px_24px_rgba(148,163,184,0.08)]">
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                      <div className="flex min-h-[4rem] flex-col justify-center">
                        <div className="text-xl font-bold leading-tight text-slate-950">基础资料</div>
                        <p className="mt-2 text-sm leading-6 text-slate-500">
                          以下内容会根据隐私设置与当前访问身份动态呈现，Hero 已展示的信息不再重复放入这里。
                        </p>
                      </div>
                      <div className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs font-semibold text-slate-500">
                        当前身份：{viewerAudienceLabel}
                      </div>
                    </div>

                    {basicInfoItems.length > 0 ? (
                      <div className="mt-5 grid gap-3 sm:grid-cols-2">
                        {basicInfoItems.map((item) => (
                          <BasicInfoItemCard
                            key={item.label}
                            label={item.label}
                            value={item.value}
                            icon={item.icon}
                            tone={item.tone}
                          />
                        ))}
                      </div>
                    ) : (
                      <div className="mt-5 rounded-[1.2rem] border border-dashed border-slate-200 bg-slate-50/85 px-4 py-5 text-sm leading-7 text-slate-500">
                        当前对你可见的基础资料还不多，后续可以结合隐私设置继续补充公开信息。
                      </div>
                    )}
                  </div>
                </div>
              </GlassCard>

              <GlassCard>
                <SectionHeader
                  sectionLabel="Social Links"
                  title="外部足迹"
                  description="这里会承接学生愿意公开的主页、代码仓库或内容平台，形成更完整的对外印象。"
                  icon={Globe}
                />

                <div className="grid gap-3 md:grid-cols-2">
                  {visibleSocialLinks.length > 0 ? visibleSocialLinks.map((item) => (
                    <a
                      key={`${item.platform}-${item.value}`}
                      href={buildSocialLinkHref(item.platform, item.value)}
                      target="_blank"
                      rel="noreferrer"
                      className="group flex items-center justify-between gap-4 rounded-[1.35rem] border border-slate-200 bg-white px-4 py-4 shadow-sm transition-all hover:-translate-y-0.5 hover:border-indigo-200 hover:shadow-md"
                    >
                      <div className="flex min-w-0 items-center gap-3">
                        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-[1.1rem] border border-slate-200/80 bg-slate-50 p-2.5 shadow-sm">
                          <img
                            src={SOCIAL_PLATFORM_META[item.platform].assetPath}
                            alt={SOCIAL_PLATFORM_META[item.platform].label}
                            className="h-full w-full object-contain"
                            loading="lazy"
                          />
                        </div>
                        <div className="min-w-0">
                          <div className="text-sm font-bold text-slate-900">
                            {SOCIAL_PLATFORM_META[item.platform].label}
                          </div>
                          <div className="mt-1 text-xs font-medium text-slate-500">
                            {SOCIAL_PLATFORM_META[item.platform].helper}
                          </div>
                          <div className="mt-2 truncate text-xs text-slate-400">{item.value}</div>
                        </div>
                      </div>
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-slate-50 text-slate-500 transition-colors group-hover:bg-indigo-50 group-hover:text-indigo-600">
                        <ExternalLink size={15} />
                      </div>
                    </a>
                  )) : (
                    <div className="rounded-[1.35rem] border border-dashed border-slate-200 bg-slate-50/80 px-4 py-6 text-center text-sm leading-7 text-slate-500 md:col-span-2">
                      当前还没有对外公开的社交主页入口。
                    </div>
                  )}
                </div>
              </GlassCard>

              <GlassCard>
                <SectionHeader
                  sectionLabel="Public Tags"
                  title="技能与画像"
                  description="保留学生对外开放的能力标签，同时强化平台成长信号的识别度。"
                  icon={Compass}
                />

                <div className="space-y-6">
                  <div>
                    <div className="mb-3 text-sm font-bold text-slate-600">技能标签</div>
                    <div className="flex flex-wrap gap-2.5">
                      {visibleSkillTags.length > 0 ? visibleSkillTags.map((tag) => (
                        <span
                          key={tag}
                          className="inline-flex items-center rounded-full border border-slate-200 bg-white px-3.5 py-1.5 text-sm font-semibold text-slate-700 shadow-sm transition-colors hover:border-indigo-200 hover:text-indigo-600"
                        >
                          {tag}
                        </span>
                      )) : (
                        <span className="rounded-full border border-dashed border-slate-200 bg-slate-50 px-3.5 py-1.5 text-sm font-semibold text-slate-500">
                          暂未公开技能标签
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="h-px w-full bg-slate-100" />

                  <div>
                    <div className="mb-3 text-sm font-bold text-slate-600">平台画像标签</div>
                    <div className="flex flex-wrap gap-2.5">
                      {visiblePortraitTags.length > 0 ? visiblePortraitTags.map((tag) => (
                        <span
                          key={tag}
                          className="inline-flex items-center gap-1.5 rounded-full border border-indigo-100 bg-indigo-50 px-3.5 py-1.5 text-sm font-semibold text-indigo-700"
                        >
                          <Sparkles size={13} className="text-indigo-400" />
                          {tag}
                        </span>
                      )) : (
                        <span className="rounded-full border border-dashed border-slate-200 bg-slate-50 px-3.5 py-1.5 text-sm font-semibold text-slate-500">
                          画像还在逐步形成中
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              </GlassCard>
            </div>

            <div className="space-y-6 lg:col-span-5">
              <GlassCard>
                <SectionHeader
                  sectionLabel="Growth Overview"
                  title="平台成长"
                  description="相比传统简历式展示，这里更强调持续学习、AI 练习和社区互动沉淀出来的成长信号。"
                  icon={TrendingUp}
                />

                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="rounded-[1.3rem] border border-slate-100 bg-slate-50 p-4 text-center">
                    <div className="text-[1.85rem] font-black text-slate-900">
                      {masteredSkills !== null ? formatCount(masteredSkills) : "—"}
                    </div>
                    <div className="mt-1 text-sm font-semibold text-slate-500">已掌握技能</div>
                  </div>
                  <div className="rounded-[1.3rem] border border-slate-100 bg-slate-50 p-4 text-center">
                    <div className="text-[1.85rem] font-black text-slate-900">
                      {learningSkills !== null ? formatCount(learningSkills) : "—"}
                    </div>
                    <div className="mt-1 text-sm font-semibold text-slate-500">学习中技能</div>
                  </div>
                  <div className="rounded-[1.3rem] border border-slate-100 bg-slate-50 p-4 text-center">
                    <div className="text-[1.85rem] font-black text-slate-900">
                      {communityScore !== null ? formatCount(communityScore) : "—"}
                    </div>
                    <div className="mt-1 text-sm font-semibold text-slate-500">近 7 天成长分</div>
                  </div>
                  <div className="rounded-[1.3rem] border border-slate-100 bg-slate-50 p-4 text-center">
                    <div className="text-[1.85rem] font-black text-slate-900">
                      {snapshot?.leaderboardRank ? `#${snapshot.leaderboardRank}` : "—"}
                    </div>
                    <div className="mt-1 text-sm font-semibold text-slate-500">贡献榜定位</div>
                  </div>
                </div>

                <div className="mt-4 rounded-[1.45rem] border border-teal-100 bg-gradient-to-br from-teal-50 to-emerald-50/40 p-4">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <div className="flex items-center gap-1.5 text-sm font-bold text-teal-700">
                        <Bot size={14} />
                        AI 练习活跃度
                      </div>
                      <div className="mt-2 text-sm leading-7 text-teal-900/80">
                        近 7 天 AI 模拟面试与复盘互动，能够补充学生在平台内的持续训练状态。
                      </div>
                    </div>
                    <div className="text-[2rem] font-black text-teal-600">
                      {interviewMessages !== null ? formatCount(interviewMessages) : "—"}
                    </div>
                  </div>
                </div>
              </GlassCard>

              <GlassCard className="flex flex-col">
                <SectionHeader
                  sectionLabel="Recent Activity"
                  title="社区足迹"
                  description="时间线部分承接社区里的公开互动，强化人物空间的持续感与真实感。"
                  icon={History}
                />

                <div className="relative flex-1">
                  <div className="absolute bottom-0 left-[14px] top-2 w-[2px] bg-slate-100" />
                  <div className="space-y-5">
                    {recentActivities.length > 0 ? recentActivities.map((activity) => {
                      const meta = footprintMetaTone(activity.detail);
                      return (
                        <Link
                          key={activity.id}
                          to={activity.href}
                          className="group relative block pl-10 pr-2"
                        >
                          <div className={joinClasses("absolute left-0 top-1 flex h-8 w-8 items-center justify-center rounded-full border-[3px] border-white shadow-sm", meta.dotWrap)}>
                            <meta.icon size={12} />
                          </div>
                          <div className="text-xs font-semibold leading-5 text-slate-400">
                            {[activity.detail, activity.createdAt ? formatDateTime(activity.createdAt) : null].filter(Boolean).join(" · ") || "来自社区公开互动"}
                          </div>
                          <div className="mt-2 rounded-[1.25rem] border border-slate-200/80 bg-slate-50/92 px-4 py-4 text-sm font-semibold leading-7 text-slate-800 transition-all group-hover:border-indigo-200 group-hover:bg-indigo-50/60 group-hover:text-indigo-700">
                            {activity.title}
                          </div>
                        </Link>
                      );
                    }) : (
                      <div className="rounded-[1.35rem] border border-dashed border-slate-200 bg-slate-50/80 px-4 py-6 text-center text-sm leading-7 text-slate-500">
                        当前还没有同步到更多公开互动线索，你可以先从社区帖子或贡献榜进入，再看个人空间会更完整。
                      </div>
                    )}
                  </div>
                </div>
              </GlassCard>

            </div>
          </div>

          {!profile && !snapshot ? (
            <section className="rounded-[2rem] border border-dashed border-slate-200 bg-white/85 px-6 py-8 text-center shadow-sm">
              <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-100 text-slate-500">
                <Users size={24} />
              </div>
              <h2 className="mt-4 text-xl font-bold text-slate-900">当前还没有足够的公开资料线索</h2>
              <p className="mx-auto mt-3 max-w-2xl text-sm leading-7 text-slate-500">
                这位同学暂时还没有补充出足够的公开资料内容，你可以先从社区里的作者入口进入，后续再回来查看更完整的个人空间。
              </p>
            </section>
          ) : null}
        </motion.div>
      </main>
    </div>
  );
}
