import { AnimatePresence, motion, useAnimationControls } from "framer-motion";
import {
  AlertCircle,
  ArrowRight,
  CheckCircle2,
  HelpCircle,
  Info,
  Lock,
  Mail,
  Shield,
  ShieldCheck,
  Sparkles,
  User,
  X,
} from "lucide-react";
import { memo, useEffect, useRef, useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { ApiClientError, apiRequest } from "../lib/apiClient";
import RegisterDetailsPanel from "../components/auth/RegisterDetailsPanel";
import {
  fetchGeetestCaptchaConfig,
  loadGeetestScript,
  openGeetestCaptcha,
  resetGeetestCaptcha,
  verifyGeetestCaptcha,
  type GeetestCaptchaObject,
  type GeetestCaptchaPublicConfig,
} from "../lib/geetest";
import {
  sendEmailVerificationCode,
  verifyEmailVerificationCode,
} from "../lib/registerEmailVerification";
import {
  clearPendingRegistration,
  getPendingRegistrationSnapshot,
  setPendingRegistration,
} from "../lib/pendingRegistration";
import { formatTime, toTimestamp } from "../lib/formatters";
import { getSessionSnapshot } from "../lib/sessionStore";
import { resolveRoleCompatiblePath, resolveWorkspaceDashboardRoute } from "../lib/workspaceRoutes";

type TimeValue = number | string;

type AuthMode = "login" | "register";
type AuthPanel = "none" | "register-details";
type AuthStage = "auth" | "register-details";
type MascotState = "idle" | "typing" | "password" | "error" | "success" | "questioning";
type RegisterRole = "STUDENT" | "MENTOR" | "ENTERPRISE";
type FeedbackTone = "info" | "error" | "success";

type FeedbackState = {
  tone: FeedbackTone;
  message: string;
};

type EmailCodeState = {
  sentEmail: string;
  sentAt: number;
  expiresAt: TimeValue;
  nextSendAt: TimeValue;
};

type EmailVerificationState = {
  verificationToken: string;
  verifiedEmail: string;
  verifiedAt: number;
  expiresAt: TimeValue;
  code: string;
};

type CaptchaActionState = {
  type: "send-email-code" | "send-forgot-password-code";
};

type PasswordResetSendCodeResponse = {
  sent: boolean;
  stage: string;
  targetEmail: string;
  deliveryChannel: string;
  expiresAt: TimeValue;
  nextSendAt: TimeValue;
  debugCode: string | null;
};

type PasswordResetVerifyCodeResponse = {
  verified: boolean;
  verificationToken: string;
  expiresAt: TimeValue;
};

type PasswordResetChangeResponse = {
  updated: boolean;
};

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function detectDesktopViewport() {
  if (typeof window === "undefined") {
    return false;
  }

  return window.matchMedia("(min-width: 1024px)").matches;
}

function resolveAuthMode(modeParam: string | null): AuthMode {
  return modeParam === "register" ? "register" : "login";
}

function resolveAuthPanel(panelParam: string | null, authMode: AuthMode): AuthPanel {
  if (panelParam === "register-details" && authMode === "register") {
    return "register-details";
  }

  return "none";
}

function resolveRegisterRole(roleParam: string | null): RegisterRole | null {
  if (roleParam === "STUDENT" || roleParam === "MENTOR" || roleParam === "ENTERPRISE") {
    return roleParam;
  }

  return null;
}

function isValidEmail(email: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
}

function isValidPassword(password: string) {
  return /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/.test(password);
}

function normalizeEmail(email: string) {
  return email.trim().toLowerCase();
}

function isTimestampExpired(expiresAt: TimeValue | null) {
  if (!expiresAt) {
    return true;
  }

  const expiresTime = toTimestamp(expiresAt);
  return Number.isNaN(expiresTime) || expiresTime <= Date.now();
}

function formatShortExpiry(expiresAt: TimeValue) {
  return formatTime(expiresAt, "短时有效");
}

function secondsUntil(expiresAt: TimeValue) {
  const expiresTime = toTimestamp(expiresAt);

  if (Number.isNaN(expiresTime)) {
    return 0;
  }

  return Math.max(0, Math.ceil((expiresTime - Date.now()) / 1000));
}

function extractRetryAfterSeconds(message: string) {
  const match = message.match(/retry after (\d+) seconds/i);

  if (!match) {
    return null;
  }

  const seconds = Number(match[1]);
  return Number.isFinite(seconds) && seconds > 0 ? seconds : null;
}

function resolveAuthDestination(role: string | null, from: string | null) {
  const defaultDestination = resolveWorkspaceDashboardRoute(role);
  if (from && from !== "/login" && from !== "/register" && !from.startsWith("/auth")) {
    // 登录回跳只接受当前角色兼容的路径，避免学生误入企业/后台私有域。
    return resolveRoleCompatiblePath(from, role) ?? defaultDestination;
  }

  return defaultDestination;
}

function feedbackStyles(tone: FeedbackTone) {
  switch (tone) {
    case "error":
      return "border-rose-200 bg-rose-50 text-rose-700";
    case "success":
      return "border-emerald-200 bg-emerald-50 text-emerald-700";
    default:
      return "border-indigo-200 bg-indigo-50 text-indigo-700";
  }
}

function getForgotPasswordToastPresentation(tone: FeedbackTone) {
  switch (tone) {
    case "error":
      return {
        title: "操作失败",
        icon: AlertCircle,
        shellClassName: "border border-rose-100/90 bg-[#fff4f6] ring-1 ring-rose-100/90 shadow-[0_18px_40px_rgba(244,63,94,0.14)]",
        iconClassName: "bg-rose-100 text-rose-600",
        titleClassName: "text-rose-700",
        messageClassName: "text-slate-600",
      };
    case "success":
      return {
        title: "已完成",
        icon: CheckCircle2,
        shellClassName: "border border-emerald-100/90 bg-[#f2fcf7] ring-1 ring-emerald-100/90 shadow-[0_18px_40px_rgba(16,185,129,0.12)]",
        iconClassName: "bg-emerald-100 text-emerald-600",
        titleClassName: "text-emerald-700",
        messageClassName: "text-slate-600",
      };
    default:
      return {
        title: "提示",
        icon: Info,
        shellClassName: "border border-sky-100/90 bg-[#f3f9ff] ring-1 ring-sky-100/90 shadow-[0_18px_40px_rgba(96,165,250,0.12)]",
        iconClassName: "bg-sky-100 text-sky-600",
        titleClassName: "text-sky-700",
        messageClassName: "text-slate-600",
      };
  }
}

const stageTransitionVariants = {
  enter: (direction: number) => ({
    y: direction > 0 ? "104%" : "-104%",
    opacity: 0,
    scale: 0.992,
    zIndex: 1,
  }),
  center: {
    y: 0,
    opacity: 1,
    scale: 1,
    zIndex: 2,
    transition: {
      duration: 0.34,
      ease: [0.22, 1, 0.36, 1] as const,
    },
  },
  exit: (direction: number) => ({
    y: direction > 0 ? "-104%" : "104%",
    opacity: 0,
    scale: 0.992,
    zIndex: 1,
    transition: {
      duration: 0.28,
      ease: [0.4, 0, 0.2, 1] as const,
    },
  }),
};

const forgotPasswordStepTransitionVariants = {
  enter: (direction: number) => ({
    x: direction > 0 ? "16%" : "-16%",
    opacity: 0,
  }),
  center: {
    x: 0,
    opacity: 1,
    transition: {
      duration: 0.3,
      ease: [0.22, 1, 0.36, 1] as const,
    },
  },
  exit: (direction: number) => ({
    x: direction > 0 ? "-16%" : "16%",
    opacity: 0,
    transition: {
      duration: 0.24,
      ease: [0.4, 0, 0.2, 1] as const,
    },
  }),
};

const stageShellClassName = "relative flex h-full w-full min-h-0 overflow-hidden rounded-[2rem] border border-white/60 bg-white";

const DEBUG_LOGIN_ACCOUNTS = [
  { roleLabel: "学生", displayName: "安然", email: "student.xuanran@bishe.local", password: "Passw0rd!" },
  { roleLabel: "导师", displayName: "顾航", email: "mentor.guhang@bishe.local", password: "Passw0rd!" },
  { roleLabel: "企业", displayName: "北辰", email: "enterprise.beichenhr@bishe.local", password: "Passw0rd!" },
  { roleLabel: "管理员", displayName: "后台", email: "admin@bishe.local", password: "Passw0rd!" },
] as const;

type DebugLoginAccount = (typeof DEBUG_LOGIN_ACCOUNTS)[number];

function InteractiveMascotBase({ state, motionEnabled = true }: { state: MascotState; motionEnabled?: boolean }) {
  const defaultSpring = { type: "spring" as const, stiffness: 200, damping: 20 };
  const idleMotionActive = motionEnabled && state === "idle";
  const idleBlinkFrames = [1, 1, 1, 1, 1, 0.1, 1, 1, 1, 1];
  const idleFloatControls = useAnimationControls();
  const idleLeftEyeControls = useAnimationControls();
  const idleRightEyeControls = useAnimationControls();

  useEffect(() => {
    if (idleMotionActive) {
      void idleFloatControls.start({
        y: [0, -4, 0],
        transition: { duration: 4, repeat: Number.POSITIVE_INFINITY, ease: "easeInOut" },
      });
      void idleLeftEyeControls.start({
        scaleY: idleBlinkFrames,
        y: 0,
        x: 0,
        rotate: 0,
        transition: { duration: 5, repeat: Number.POSITIVE_INFINITY, ease: "linear" },
      });
      void idleRightEyeControls.start({
        scaleY: idleBlinkFrames,
        y: 0,
        x: 0,
        rotate: 0,
        transition: { duration: 5, repeat: Number.POSITIVE_INFINITY, ease: "linear" },
      });
      return;
    }

    idleFloatControls.stop();
    idleLeftEyeControls.stop();
    idleRightEyeControls.stop();
    void idleFloatControls.start({ y: 0, transition: { duration: 0.16 } });
    void idleLeftEyeControls.start({ scaleY: 1, transition: { duration: 0.12 } });
    void idleRightEyeControls.start({ scaleY: 1, transition: { duration: 0.12 } });
  }, [idleLeftEyeControls, idleMotionActive, idleRightEyeControls, idleFloatControls]);

  const faceVariants = {
    idle: { y: 0, rotate: 0 },
    typing: { y: 0, rotate: 0 },
    password: { y: 0, rotate: 0 },
    error: { x: [-5, 5, -5, 5, 0], y: 0, rotate: 0, transition: { x: { duration: 0.4 } } },
    success: { y: -10, rotate: 0 },
    questioning: { y: 0, rotate: 5 },
  };

  const eyeLeftVariants = {
    idle: { scaleY: 1, y: 0, x: 0, rotate: 0 },
    typing: { scaleY: 1, x: [0, 2, -2, 0], y: 0, rotate: 0, transition: { x: { repeat: Number.POSITIVE_INFINITY, duration: 3, ease: "easeInOut" as const } } },
    password: { scaleY: 0.1, y: 2, x: 0, rotate: 0 },
    error: { scaleY: 1, rotate: 20, x: 0, y: 0 },
    success: { scaleY: 0.2, y: -2, rotate: -15, x: 0 },
    questioning: { scaleY: 1, x: 2, y: -2, rotate: 0 },
  };

  const eyeRightVariants = {
    idle: { scaleY: 1, y: 0, x: 0, rotate: 0 },
    typing: { scaleY: 1, x: [0, 2, -2, 0], y: 0, rotate: 0, transition: { x: { repeat: Number.POSITIVE_INFINITY, duration: 3, ease: "easeInOut" as const } } },
    password: { scaleY: 0.1, y: 2, x: 0, rotate: 0 },
    error: { scaleY: 1, rotate: -20, x: 0, y: 0 },
    success: { scaleY: 0.2, y: -2, rotate: 15, x: 0 },
    questioning: { scaleY: 0.5, x: 2, y: -2, rotate: 0 },
  };

  const mouthVariants = {
    idle: { d: "M 75 120 Q 100 135 125 120" },
    typing: { d: "M 85 125 Q 100 125 115 125" },
    password: { d: "M 85 125 Q 100 115 115 125" },
    error: { d: "M 75 130 Q 100 110 125 130" },
    success: { d: "M 70 120 Q 100 155 130 120" },
    questioning: { d: "M 90 125 Q 100 120 110 125" },
  };

  const handLeftVariants = {
    idle: { x: -20, y: 60, opacity: 0, rotate: -20, scale: 1 },
    typing: { x: 10, y: 15, opacity: 1, rotate: 10, scale: 1 },
    password: { x: 28, y: -58, opacity: 1, rotate: 30, scale: 1.1 },
    error: { x: 15, y: -30, opacity: 1, rotate: -15, scale: 1 },
    success: { x: -10, y: -50, opacity: 1, rotate: -20, scale: 1 },
    questioning: { x: -20, y: 60, opacity: 0, rotate: -20, scale: 1 },
  };

  const handRightVariants = {
    idle: { x: 20, y: 60, opacity: 0, rotate: 20, scale: 1 },
    typing: { x: -10, y: 15, opacity: 1, rotate: -10, scale: 1 },
    password: { x: -28, y: -58, opacity: 1, rotate: -30, scale: 1.1 },
    error: { x: -15, y: -30, opacity: 1, rotate: 15, scale: 1 },
    success: { x: 10, y: -50, opacity: 1, rotate: 20, scale: 1 },
    questioning: { x: -15, y: -20, opacity: 1, rotate: -15, scale: 1 },
  };

  return (
    <motion.div animate={idleFloatControls} className="relative mx-auto mb-6 h-40 w-40 drop-shadow-2xl lg:h-48 lg:w-48">
      <motion.svg viewBox="0 0 200 200" className="h-full w-full overflow-visible">
        <motion.rect
          x="30"
          y="40"
          width="140"
          height="130"
          rx="60"
          fill="rgba(255,255,255,0.15)"
          stroke="rgba(255,255,255,0.8)"
          strokeWidth="6"
          className="backdrop-blur-sm"
        />

        <motion.g variants={faceVariants} animate={state} initial="idle" style={{ transformOrigin: "center" }} transition={defaultSpring}>
          <motion.circle
            cx="75"
            cy="95"
            r="10"
            fill="white"
            variants={eyeLeftVariants}
            animate={idleMotionActive ? idleLeftEyeControls : state}
            initial={false}
            style={{ transformOrigin: "75px 95px" }}
            transition={defaultSpring}
          />
          <motion.circle
            cx="125"
            cy="95"
            r="10"
            fill="white"
            variants={eyeRightVariants}
            animate={idleMotionActive ? idleRightEyeControls : state}
            initial={false}
            style={{ transformOrigin: "125px 95px" }}
            transition={defaultSpring}
          />
          <motion.path fill="transparent" stroke="white" strokeWidth="6" strokeLinecap="round" variants={mouthVariants} transition={defaultSpring} />

          <motion.circle cx="55" cy="110" r="8" fill="#f43f5e" animate={{ opacity: state === "password" || state === "success" ? 0.6 : 0 }} transition={defaultSpring} />
          <motion.circle cx="145" cy="110" r="8" fill="#f43f5e" animate={{ opacity: state === "password" || state === "success" ? 0.6 : 0 }} transition={defaultSpring} />

          <motion.text
            x="160"
            y="35"
            fontSize="56"
            fill="white"
            fontWeight="black"
            style={{ filter: "drop-shadow(0px 4px 8px rgba(0,0,0,0.2))" }}
            initial={{ opacity: 0, y: 15, x: -10, rotate: -20 }}
            animate={state === "questioning" ? { opacity: 1, y: 0, x: 0, rotate: 15 } : { opacity: 0, y: 15, x: -10, rotate: -20 }}
            transition={{ type: "spring", bounce: 0.6 }}
          >
            ?
          </motion.text>
        </motion.g>

        <motion.rect
          x="30"
          y="130"
          width="35"
          height="45"
          rx="17.5"
          fill="white"
          variants={handLeftVariants}
          animate={state}
          initial="idle"
          style={{ transformOrigin: "center" }}
          transition={defaultSpring}
        />
        <motion.rect
          x="135"
          y="130"
          width="35"
          height="45"
          rx="17.5"
          fill="white"
          variants={handRightVariants}
          animate={state}
          initial="idle"
          style={{ transformOrigin: "center" }}
          transition={defaultSpring}
        />
      </motion.svg>
    </motion.div>
  );
}

const InteractiveMascot = memo(InteractiveMascotBase);

export default function AuthPage() {
  const { ready, isAuthenticated, role, login, logout, loading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const authMode = resolveAuthMode(searchParams.get("mode"));
  const activePanel = resolveAuthPanel(searchParams.get("panel"), authMode);
  const roleParam = searchParams.get("role");
  // URL query 是认证页的状态入口：mode 控制登录/注册，panel 控制注册二阶段。
  const [isDesktop, setIsDesktop] = useState(detectDesktopViewport);
  const [mascotState, setMascotState] = useState<MascotState>("idle");
  const [isShake, setIsShake] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [loginFeedback, setLoginFeedback] = useState<FeedbackState | null>(null);
  const [registerFeedback, setRegisterFeedback] = useState<FeedbackState | null>(null);
  const [loginForm, setLoginForm] = useState({
    email: "",
    password: "",
  });
  const [registerForm, setRegisterForm] = useState({
    email: "",
    password: "",
    displayName: "",
    code: "",
  });
  const [pendingRegistrationState, setPendingRegistrationState] = useState(() => getPendingRegistrationSnapshot());
  const [captchaPreparationRequested, setCaptchaPreparationRequested] = useState(() => authMode === "register");
  const [captchaConfig, setCaptchaConfig] = useState<GeetestCaptchaPublicConfig | null>(null);
  const [captchaLoading, setCaptchaLoading] = useState(false);
  const [captchaReady, setCaptchaReady] = useState(false);
  const [captchaBusy, setCaptchaBusy] = useState(false);
  const [captchaLoadError, setCaptchaLoadError] = useState<string | null>(null);
  const [sendingEmailCode, setSendingEmailCode] = useState(false);
  const [verifyingEmailCode, setVerifyingEmailCode] = useState(false);
  const [emailCodeState, setEmailCodeState] = useState<EmailCodeState | null>(null);
  const [emailVerification, setEmailVerification] = useState<EmailVerificationState | null>(null);
  const [sendCodeCountdown, setSendCodeCountdown] = useState(0);
  const [forgotPasswordOpen, setForgotPasswordOpen] = useState(false);
  const [forgotPasswordFeedback, setForgotPasswordFeedback] = useState<FeedbackState | null>(null);
  const [forgotPasswordForm, setForgotPasswordForm] = useState({
    email: "",
    code: "",
    newPassword: "",
    confirmPassword: "",
  });
  const [forgotPasswordSendingCode, setForgotPasswordSendingCode] = useState(false);
  const [forgotPasswordVerifyingCode, setForgotPasswordVerifyingCode] = useState(false);
  const [forgotPasswordResetting, setForgotPasswordResetting] = useState(false);
  const [forgotPasswordEmailCodeState, setForgotPasswordEmailCodeState] = useState<EmailCodeState | null>(null);
  const [forgotPasswordVerification, setForgotPasswordVerification] = useState<EmailVerificationState | null>(null);
  const [forgotPasswordSendCodeCountdown, setForgotPasswordSendCodeCountdown] = useState(0);
  const [stageTransitioning, setStageTransitioning] = useState(false);
  const blurTimeoutRef = useRef<number | null>(null);
  const navigateTimeoutRef = useRef<number | null>(null);
  const stageTransitionTimeoutRef = useRef<number | null>(null);
  const stageMountedRef = useRef(false);
  const captchaObjRef = useRef<GeetestCaptchaObject | null>(null);
  const captchaVerifyingRef = useRef(false);
  const captchaActionRef = useRef<CaptchaActionState | null>(null);
  const forgotPasswordAutoVerifyKeyRef = useRef<string | null>(null);
  const forgotPasswordAutoVerifyTimeoutRef = useRef<number | null>(null);
  const forgotPasswordCodeInputRef = useRef<HTMLInputElement | null>(null);
  const registerEmailRef = useRef(registerForm.email);
  const forgotPasswordEmailRef = useRef(forgotPasswordForm.email);
  const from = ((location.state as { from?: string } | null)?.from ?? null) as string | null;
  const isLogin = authMode === "login";
  const showRegisterDetailsPanel = activePanel === "register-details" && !!pendingRegistrationState;
  const activeStage: AuthStage = showRegisterDetailsPanel
    ? "register-details"
    : "auth";
  const stageTransitionSourceRef = useRef<AuthStage>(activeStage);
  const previousStageRef = useRef<AuthStage>(activeStage);
  const stageDirectionRef = useRef(1);
  if (previousStageRef.current !== activeStage) {
    stageDirectionRef.current = activeStage === "auth" ? -1 : 1;
    previousStageRef.current = activeStage;
  }
  const stageDirection = stageDirectionRef.current;
  const forgotPasswordBusy = forgotPasswordSendingCode || forgotPasswordVerifyingCode || forgotPasswordResetting;
  const busy = submitting || loading || captchaBusy || sendingEmailCode || verifyingEmailCode || forgotPasswordBusy;
  const forgotPasswordNormalizedEmail = normalizeEmail(forgotPasswordForm.email);
  const forgotPasswordCodeValue = forgotPasswordForm.code.trim();
  const forgotPasswordEmailCodeValid = !!forgotPasswordEmailCodeState
    && forgotPasswordEmailCodeState.sentEmail === forgotPasswordNormalizedEmail
    && !isTimestampExpired(forgotPasswordEmailCodeState.expiresAt);
  const forgotPasswordVerificationValid = !!forgotPasswordVerification
    && forgotPasswordVerification.verifiedEmail === forgotPasswordNormalizedEmail
    && forgotPasswordVerification.code === forgotPasswordCodeValue
    && !isTimestampExpired(forgotPasswordVerification.expiresAt);
  const forgotPasswordCurrentStep = forgotPasswordVerificationValid ? 3 : forgotPasswordEmailCodeValid ? 2 : 1;
  const forgotPasswordPreviousStepRef = useRef(forgotPasswordCurrentStep);
  const forgotPasswordStepDirectionRef = useRef(1);
  if (forgotPasswordPreviousStepRef.current !== forgotPasswordCurrentStep) {
    forgotPasswordStepDirectionRef.current = forgotPasswordCurrentStep > forgotPasswordPreviousStepRef.current ? 1 : -1;
    forgotPasswordPreviousStepRef.current = forgotPasswordCurrentStep;
  }
  const forgotPasswordStepDirection = forgotPasswordStepDirectionRef.current;

  useEffect(() => {
    const mediaQuery = window.matchMedia("(min-width: 1024px)");
    const syncDesktopState = (event: MediaQueryList | MediaQueryListEvent) => {
      setIsDesktop(event.matches);
    };

    syncDesktopState(mediaQuery);
    mediaQuery.addEventListener("change", syncDesktopState);

    return () => {
      mediaQuery.removeEventListener("change", syncDesktopState);
    };
  }, []);

  useEffect(() => {
    registerEmailRef.current = registerForm.email;
  }, [registerForm.email]);

  useEffect(() => {
    forgotPasswordEmailRef.current = forgotPasswordForm.email;
  }, [forgotPasswordForm.email]);

  useEffect(() => {
    if (!stageMountedRef.current) {
      stageMountedRef.current = true;
      stageTransitionSourceRef.current = activeStage;
      return;
    }

    if (stageTransitionSourceRef.current === activeStage) {
      return;
    }

    stageTransitionSourceRef.current = activeStage;

    if (stageTransitionTimeoutRef.current !== null) {
      window.clearTimeout(stageTransitionTimeoutRef.current);
    }

    setStageTransitioning(true);
    stageTransitionTimeoutRef.current = window.setTimeout(() => {
      setStageTransitioning(false);
      stageTransitionTimeoutRef.current = null;
    }, 420);

    return () => {
      if (stageTransitionTimeoutRef.current !== null) {
        window.clearTimeout(stageTransitionTimeoutRef.current);
        stageTransitionTimeoutRef.current = null;
      }
    };
  }, [activeStage]);

  useEffect(() => {
    if (sendCodeCountdown <= 0) {
      return;
    }

    const timer = window.setInterval(() => {
      setSendCodeCountdown((current) => (current > 0 ? current - 1 : 0));
    }, 1000);

    return () => {
      window.clearInterval(timer);
    };
  }, [sendCodeCountdown]);

  useEffect(() => {
    if (forgotPasswordSendCodeCountdown <= 0) {
      return;
    }

    const timer = window.setInterval(() => {
      setForgotPasswordSendCodeCountdown((current) => (current > 0 ? current - 1 : 0));
    }, 1000);

    return () => {
      window.clearInterval(timer);
    };
  }, [forgotPasswordSendCodeCountdown]);

  useEffect(() => {
    const nextEmail = normalizeEmail(registerForm.email);
    if (!emailCodeState) {
      return;
    }
    if (emailCodeState.sentEmail === nextEmail && !isTimestampExpired(emailCodeState.expiresAt)) {
      return;
    }

    setEmailCodeState(null);
    setSendCodeCountdown(0);
  }, [emailCodeState, registerForm.email]);

  useEffect(() => {
    const nextEmail = normalizeEmail(forgotPasswordForm.email);
    if (!forgotPasswordEmailCodeState) {
      return;
    }
    if (forgotPasswordEmailCodeState.sentEmail === nextEmail && !isTimestampExpired(forgotPasswordEmailCodeState.expiresAt)) {
      return;
    }

    setForgotPasswordEmailCodeState(null);
    setForgotPasswordSendCodeCountdown(0);
  }, [forgotPasswordEmailCodeState, forgotPasswordForm.email]);

  useEffect(() => {
    const nextEmail = normalizeEmail(registerForm.email);
    if (!emailVerification) {
      return;
    }
    if (
      emailVerification.verifiedEmail === nextEmail
      && emailVerification.code === registerForm.code.trim()
      && !isTimestampExpired(emailVerification.expiresAt)
    ) {
      return;
    }

    setEmailVerification(null);
  }, [emailVerification, registerForm.code, registerForm.email]);

  useEffect(() => {
    const nextEmail = normalizeEmail(forgotPasswordForm.email);
    if (!forgotPasswordVerification) {
      return;
    }
    if (
      forgotPasswordVerification.verifiedEmail === nextEmail
      && forgotPasswordVerification.code === forgotPasswordForm.code.trim()
      && !isTimestampExpired(forgotPasswordVerification.expiresAt)
    ) {
      return;
    }

    setForgotPasswordVerification(null);
  }, [forgotPasswordForm.code, forgotPasswordForm.email, forgotPasswordVerification]);

  useEffect(() => {
    setMascotState("idle");
    setLoginFeedback(null);
    setRegisterFeedback(null);
    setForgotPasswordFeedback(null);

    return () => {
      if (blurTimeoutRef.current !== null) {
        window.clearTimeout(blurTimeoutRef.current);
      }
      if (navigateTimeoutRef.current !== null) {
        window.clearTimeout(navigateTimeoutRef.current);
      }
    };
  }, [activePanel, authMode]);

  useEffect(() => {
    if (!forgotPasswordFeedback) {
      return;
    }

    const timer = window.setTimeout(() => {
      setForgotPasswordFeedback(null);
    }, 3200);

    return () => {
      window.clearTimeout(timer);
    };
  }, [forgotPasswordFeedback]);

  useEffect(() => {
    return () => {
      if (forgotPasswordAutoVerifyTimeoutRef.current !== null) {
        window.clearTimeout(forgotPasswordAutoVerifyTimeoutRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (activePanel !== "register-details" || pendingRegistrationState) {
      return;
    }

    // 二阶段注册没有 pendingRegistration 时退回注册主面板，防止刷新后直接空表单。
    const nextSearchParams = new URLSearchParams(searchParams);
    nextSearchParams.delete("panel");
    nextSearchParams.set("mode", "register");
    navigate(`/auth?${nextSearchParams.toString()}`, { replace: true, state: location.state });
  }, [activePanel, location.state, navigate, pendingRegistrationState, searchParams]);

  useEffect(() => {
    if (authMode === "register" || forgotPasswordOpen) {
      setCaptchaPreparationRequested(true);
    }
  }, [authMode, forgotPasswordOpen]);

  useEffect(() => {
    if (!captchaPreparationRequested) {
      return;
    }

    // 仅在注册或找回密码真正需要时再初始化 Geetest，避免登录页首屏预拉取挑战资源。
    let active = true;
    let fetchedConfig: GeetestCaptchaPublicConfig | null = null;

    const initCaptcha = async () => {
      setCaptchaLoading(true);
      setCaptchaLoadError(null);

      try {
        const config = await fetchGeetestCaptchaConfig();
        fetchedConfig = config;

        if (!active) {
          return;
        }

        setCaptchaConfig(config);
        if (!config.enabled || !config.captchaId) {
          setCaptchaReady(false);
          return;
        }

        await loadGeetestScript();
        if (!active || !window.initGeetest4) {
          return;
        }

        window.initGeetest4(
          {
            captchaId: config.captchaId,
            product: config.product,
            protocol: "https://",
          },
          (captchaObj) => {
            if (!active) {
              captchaObj.destroy?.();
              return;
            }

            captchaObjRef.current = captchaObj;
            captchaObj
              .onReady(() => {
                if (!active) {
                  return;
                }
                setCaptchaReady(true);
                setCaptchaLoadError(null);
              })
              .onSuccess(() => {
                const action = captchaActionRef.current;
                const validateResult = captchaObj.getValidate();

                if (action?.type !== "send-email-code" && action?.type !== "send-forgot-password-code") {
                  resetGeetestCaptcha(captchaObj);
                  return;
                }

                const nextEmail = action.type === "send-forgot-password-code"
                  ? normalizeEmail(forgotPasswordEmailRef.current)
                  : normalizeEmail(registerEmailRef.current);

                if (!nextEmail) {
                  captchaActionRef.current = null;
                  if (action.type === "send-forgot-password-code") {
                    setForgotPasswordFeedback({
                      tone: "error",
                      message: "请先填写需要找回的登录邮箱。",
                    });
                  } else {
                    setRegisterFeedback({
                      tone: "error",
                      message: "请先填写注册邮箱，再获取验证码。",
                    });
                    setMascotState("error");
                    triggerShake();
                  }
                  resetGeetestCaptcha(captchaObj);
                  return;
                }

                if (!isValidEmail(nextEmail)) {
                  captchaActionRef.current = null;
                  if (action.type === "send-forgot-password-code") {
                    setForgotPasswordFeedback({
                      tone: "error",
                      message: "请输入正确的邮箱格式。",
                    });
                  } else {
                    setRegisterFeedback({
                      tone: "error",
                      message: "请先填写正确的注册邮箱，再获取验证码。",
                    });
                    setMascotState("error");
                    triggerShake();
                  }
                  resetGeetestCaptcha(captchaObj);
                  return;
                }

                if (!validateResult || captchaVerifyingRef.current) {
                  return;
                }

                captchaVerifyingRef.current = true;
                setCaptchaBusy(true);
                if (action.type === "send-forgot-password-code") {
                  setForgotPasswordSendingCode(true);
                } else {
                  setSendingEmailCode(true);
                }

                void (async () => {
                  try {
                    const captchaResponse = await verifyGeetestCaptcha({
                      email: nextEmail,
                      lotNumber: validateResult.lot_number,
                      captchaOutput: validateResult.captcha_output,
                      passToken: validateResult.pass_token,
                      genTime: validateResult.gen_time,
                    });

                    if (action.type === "send-forgot-password-code") {
                      await sendForgotPasswordCodeRequest(nextEmail, captchaResponse.verificationToken);

                      if (!active) {
                        return;
                      }
                    } else {
                      const emailResponse = await sendEmailVerificationCode({
                        email: nextEmail,
                        captchaVerificationToken: captchaResponse.verificationToken,
                      });

                      if (!active) {
                        return;
                      }

                      setEmailCodeState({
                        sentEmail: emailResponse.email,
                        sentAt: Date.now(),
                        expiresAt: emailResponse.expiresAt,
                        nextSendAt: emailResponse.nextSendAt,
                      });
                      setEmailVerification(null);
                      setRegisterForm((current) => ({ ...current, code: "" }));
                      setSendCodeCountdown(secondsUntil(emailResponse.nextSendAt));
                      setRegisterFeedback({
                        tone: "success",
                        message: `验证码已发送至 ${emailResponse.email}，请在 ${formatShortExpiry(emailResponse.expiresAt)} 前完成验证；若暂未收到，请检查垃圾箱。${emailResponse.debugCode ? ` 演示模式验证码：${emailResponse.debugCode}` : ""}`,
                      });
                      setMascotState("success");
                    }
                  } catch (error) {
                    if (!active) {
                      return;
                    }

                    const apiError = error as ApiClientError;
                    if (action.type === "send-forgot-password-code") {
                      applyForgotPasswordSendCodeError(apiError);
                    } else {
                      const retryAfterSeconds = extractRetryAfterSeconds(apiError.message);
                      if (retryAfterSeconds) {
                        setSendCodeCountdown(retryAfterSeconds);
                      }

                      setRegisterFeedback({
                        tone: "error",
                        message: apiError.code === "AUTH-1001"
                          ? "该邮箱已注册，请直接登录。"
                          : apiError.code === "AUTH-1008"
                            ? "当前安全校验已失效，请重新获取验证码。"
                            : apiError.code === "AUTH-1009"
                              ? "安全校验服务暂时不可用，请稍后重试。"
                              : apiError.code === "AUTH-1011"
                                ? "验证码发送服务暂时不可用，请稍后重试。"
                                : apiError.code === "AUTH-1012"
                                  ? retryAfterSeconds
                                    ? `发送过于频繁，请在 ${retryAfterSeconds} 秒后再试。`
                                    : "发送过于频繁，请稍后再试。"
                                  : apiError.message || "验证码发送失败，请重新尝试。",
                      });
                      setMascotState("error");
                      triggerShake();
                    }
                  } finally {
                    captchaActionRef.current = null;
                    if (active) {
                      setCaptchaBusy(false);
                      setSendingEmailCode(false);
                      setForgotPasswordSendingCode(false);
                    }
                    captchaVerifyingRef.current = false;
                    resetGeetestCaptcha(captchaObj);
                  }
                })();
              })
              .onError((error) => {
                if (!active) {
                  return;
                }

                captchaActionRef.current = null;
                setCaptchaReady(false);
                setCaptchaLoadError(error.msg || error.desc?.detail || "安全校验加载失败，请稍后重试。");
              });

            if (typeof captchaObj.onClose === "function") {
              captchaObj.onClose(() => {
                const action = captchaActionRef.current;
                if (!active || !action || captchaVerifyingRef.current) {
                  return;
                }

                captchaActionRef.current = null;
                if (action.type === "send-forgot-password-code") {
                  setForgotPasswordFeedback({
                    tone: "info",
                    message: "发送验证码前需要先完成安全校验，关闭后可再次点击继续。",
                  });
                } else {
                  setRegisterFeedback({
                    tone: "info",
                    message: "发送验证码前需要先完成安全校验，关闭后可再次点击继续。",
                  });
                  setMascotState("questioning");
                }
              });
            }
          },
        );
      } catch (error) {
        if (!active) {
          return;
        }

        const message = error instanceof Error ? error.message : "安全校验初始化失败";
        setCaptchaConfig(fetchedConfig);
        setCaptchaReady(false);
        setCaptchaLoadError(message);
      } finally {
        if (active) {
          setCaptchaLoading(false);
        }
      }
    };

    void initCaptcha();

    return () => {
      active = false;
      captchaObjRef.current?.destroy?.();
      captchaObjRef.current = null;
      captchaActionRef.current = null;
      captchaVerifyingRef.current = false;
    };
  }, [captchaPreparationRequested]);

  const updateAuthSearch = (mutate: (nextSearchParams: URLSearchParams) => void, options?: { replace?: boolean }) => {
    const nextSearchParams = new URLSearchParams(searchParams);
    mutate(nextSearchParams);
    navigate(`/auth?${nextSearchParams.toString()}`, { replace: options?.replace ?? false, state: location.state });
  };

  const openPanel = (panel: Exclude<AuthPanel, "none">) => {
    updateAuthSearch((nextSearchParams) => {
      nextSearchParams.set("panel", panel);
    });
  };

  const closePanel = () => {
    updateAuthSearch((nextSearchParams) => {
      nextSearchParams.delete("panel");
    });
  };

  const requestCaptchaPreparation = () => {
    setCaptchaPreparationRequested(true);
  };

  const handleFocus = (stateName: MascotState) => {
    if (blurTimeoutRef.current !== null) {
      window.clearTimeout(blurTimeoutRef.current);
    }
    setMascotState(stateName);
  };

  const handleBlur = () => {
    blurTimeoutRef.current = window.setTimeout(() => {
      setMascotState((current) => (current === "error" ? "error" : "idle"));
    }, 150);
  };

  const triggerShake = () => {
    setIsShake(true);
    window.setTimeout(() => setIsShake(false), 500);
  };

  const switchMode = (nextMode: AuthMode) => {
    if (busy) {
      return;
    }

    if (blurTimeoutRef.current !== null) {
      window.clearTimeout(blurTimeoutRef.current);
    }
    setLoginFeedback(null);
    setRegisterFeedback(null);
    setMascotState("idle");
    updateAuthSearch((nextSearchParams) => {
      nextSearchParams.set("mode", nextMode);
      nextSearchParams.delete("panel");
    });
  };

  const queueNavigation = (destination: string) => {
    if (navigateTimeoutRef.current !== null) {
      window.clearTimeout(navigateTimeoutRef.current);
    }

    navigateTimeoutRef.current = window.setTimeout(() => {
      navigate(destination, { replace: true });
    }, 800);
  };

  const handleSendEmailCode = () => {
    const normalizedRegisterEmail = normalizeEmail(registerForm.email);

    // 发验证码前先校验邮箱和密码，减少无效 captcha 挑战与邮件请求。
    if (!normalizedRegisterEmail) {
      setRegisterFeedback({ tone: "error", message: "请先填写注册邮箱，再获取验证码。" });
      setMascotState("error");
      triggerShake();
      return;
    }

    if (!isValidEmail(normalizedRegisterEmail)) {
      setRegisterFeedback({ tone: "error", message: "请先填写正确的注册邮箱，再获取验证码。" });
      setMascotState("error");
      triggerShake();
      return;
    }

    if (!registerForm.password) {
      setRegisterFeedback({ tone: "error", message: "请先设置登录密码，再获取验证码。" });
      setMascotState("error");
      triggerShake();
      return;
    }

    if (!isValidPassword(registerForm.password)) {
      setRegisterFeedback({ tone: "error", message: "请先设置符合要求的登录密码，再获取验证码。" });
      setMascotState("error");
      triggerShake();
      return;
    }

    if (sendCodeCountdown > 0) {
      setRegisterFeedback({ tone: "info", message: `验证码已发送，请在 ${sendCodeCountdown} 秒后再试。` });
      setMascotState("questioning");
      return;
    }

    if (!captchaPreparationRequested || captchaLoading || (!captchaConfig && !captchaLoadError)) {
      requestCaptchaPreparation();
      setRegisterFeedback({ tone: "info", message: "安全校验正在准备，请稍后再试。" });
      setMascotState("questioning");
      return;
    }

    if (captchaLoadError) {
      setRegisterFeedback({ tone: "error", message: "当前暂时无法发送验证码，请刷新页面后重试。" });
      setMascotState("error");
      triggerShake();
      return;
    }

    if (!captchaConfig?.enabled) {
      setRegisterFeedback({ tone: "error", message: "当前注册暂时不可用，请稍后重试。" });
      setMascotState("error");
      triggerShake();
      return;
    }

    if (!captchaReady || !captchaObjRef.current) {
      setRegisterFeedback({ tone: "info", message: "安全校验正在准备，请稍后再试。" });
      setMascotState("questioning");
      return;
    }

    captchaActionRef.current = { type: "send-email-code" };
    // 注册和找回密码共用同一个 Geetest 实例，通过 actionRef 区分后续回调。
    setRegisterFeedback({ tone: "info", message: "请完成安全校验后发送验证码。" });
    setMascotState("questioning");
    openGeetestCaptcha(captchaObjRef.current);
  };

  const clearForgotPasswordAutoVerifyTimer = () => {
    if (forgotPasswordAutoVerifyTimeoutRef.current !== null) {
      window.clearTimeout(forgotPasswordAutoVerifyTimeoutRef.current);
      forgotPasswordAutoVerifyTimeoutRef.current = null;
    }
  };

  const applyForgotPasswordSendCodeSuccess = (response: PasswordResetSendCodeResponse) => {
    setForgotPasswordEmailCodeState({
      sentEmail: response.targetEmail,
      sentAt: Date.now(),
      expiresAt: response.expiresAt,
      nextSendAt: response.nextSendAt,
    });
    setForgotPasswordVerification(null);
    setForgotPasswordForm((current) => ({
      ...current,
      email: response.targetEmail,
      code: "",
    }));
    forgotPasswordAutoVerifyKeyRef.current = null;
    clearForgotPasswordAutoVerifyTimer();
    setForgotPasswordSendCodeCountdown(secondsUntil(response.nextSendAt));
    setForgotPasswordFeedback({
      tone: "success",
      message: `验证码已发送至 ${response.targetEmail}，请在 ${formatShortExpiry(response.expiresAt)} 前完成操作。${response.debugCode ? ` 开发态验证码：${response.debugCode}` : ""}`,
    });
    window.setTimeout(() => {
      forgotPasswordCodeInputRef.current?.focus();
    }, 0);
  };

  const applyForgotPasswordSendCodeError = (error: ApiClientError) => {
    const retryAfterSeconds = extractRetryAfterSeconds(error.message);
    if (retryAfterSeconds) {
      setForgotPasswordSendCodeCountdown(retryAfterSeconds);
    }
    setForgotPasswordFeedback({
      tone: "error",
      message: error.code === "AUTH-1016"
        ? "这个邮箱暂时没有找到可找回的账号。"
        : error.code === "AUTH-1008"
          ? "当前安全校验已失效，请重新获取验证码。"
          : error.code === "AUTH-1009"
            ? "安全校验服务暂时不可用，请稍后重试。"
            : error.code === "BIZ-1406"
              ? "邮件服务暂时不可用，请稍后再试。"
              : error.code === "BIZ-1405"
                ? retryAfterSeconds
                  ? `发送过于频繁，请在 ${retryAfterSeconds} 秒后再试。`
                  : "发送过于频繁，请稍后再试。"
                : error.message || "验证码发送失败，请稍后重试。",
    });
  };

  const sendForgotPasswordCodeRequest = async (normalizedEmail: string, captchaVerificationToken?: string) => {
    // 找回密码验证码可带 captcha token；服务端会用 LOGIN_RECOVERY 场景发送邮件。
    const requestBody = captchaVerificationToken
      ? { email: normalizedEmail, captchaVerificationToken }
      : { email: normalizedEmail };
    const response = await apiRequest<PasswordResetSendCodeResponse>("/auth/password/reset/send-code", {
      method: "POST",
      skipAuth: true,
      body: JSON.stringify(requestBody),
    });
    applyForgotPasswordSendCodeSuccess(response);
  };

  const scheduleForgotPasswordAutoVerify = (emailValue: string, codeValue: string) => {
    const normalizedEmail = normalizeEmail(emailValue);
    const trimmedCode = codeValue.trim();
    const codeValid = /^\d{6}$/.test(trimmedCode);
    const emailCodeValid = !!forgotPasswordEmailCodeState
      && forgotPasswordEmailCodeState.sentEmail === normalizedEmail
      && !isTimestampExpired(forgotPasswordEmailCodeState.expiresAt);
    const verificationValid = !!forgotPasswordVerification
      && forgotPasswordVerification.verifiedEmail === normalizedEmail
      && forgotPasswordVerification.code === trimmedCode
      && !isTimestampExpired(forgotPasswordVerification.expiresAt);

    if (!codeValid) {
      forgotPasswordAutoVerifyKeyRef.current = null;
      clearForgotPasswordAutoVerifyTimer();
      return;
    }

    if (!forgotPasswordOpen || forgotPasswordVerifyingCode || !emailCodeValid || verificationValid) {
      clearForgotPasswordAutoVerifyTimer();
      return;
    }

    const currentKey = `${normalizedEmail}:${trimmedCode}`;
    if (forgotPasswordAutoVerifyKeyRef.current === currentKey) {
      return;
    }

    clearForgotPasswordAutoVerifyTimer();
    forgotPasswordAutoVerifyKeyRef.current = currentKey;
    // 6 位验证码输入完成后短延迟自动校验，避免用户还在连续输入时抢跑。
    forgotPasswordAutoVerifyTimeoutRef.current = window.setTimeout(() => {
      forgotPasswordAutoVerifyTimeoutRef.current = null;
      void handleForgotPasswordVerifyCode({
        email: normalizedEmail,
        code: trimmedCode,
      });
    }, 180);
  };

  const openForgotPasswordDialog = () => {
    requestCaptchaPreparation();
    setForgotPasswordFeedback(null);
    forgotPasswordAutoVerifyKeyRef.current = null;
    clearForgotPasswordAutoVerifyTimer();
    setForgotPasswordOpen(true);
    setForgotPasswordForm((current) => {
      if (current.email.trim()) {
        return current;
      }

      return {
        ...current,
        email: loginForm.email.trim(),
      };
    });
  };

  const closeForgotPasswordDialog = () => {
    if (forgotPasswordBusy) {
      return;
    }
    forgotPasswordAutoVerifyKeyRef.current = null;
    clearForgotPasswordAutoVerifyTimer();
    setForgotPasswordOpen(false);
    setForgotPasswordFeedback(null);
  };

  const handleForgotPasswordSendCode = async () => {
    const normalizedEmail = normalizeEmail(forgotPasswordForm.email);
    setForgotPasswordFeedback(null);

    // 找回密码三步流的第一步只负责拿到邮箱验证码，不直接暴露重置入口。
    if (!normalizedEmail) {
      setForgotPasswordFeedback({ tone: "error", message: "请先填写需要找回的登录邮箱。" });
      return;
    }

    if (!isValidEmail(normalizedEmail)) {
      setForgotPasswordFeedback({ tone: "error", message: "请输入正确的邮箱格式。" });
      return;
    }

    if (forgotPasswordSendCodeCountdown > 0) {
      setForgotPasswordFeedback({ tone: "info", message: `验证码已发送，请在 ${forgotPasswordSendCodeCountdown} 秒后再试。` });
      return;
    }

    if (!captchaPreparationRequested || captchaLoading || (!captchaConfig && !captchaLoadError)) {
      requestCaptchaPreparation();
      setForgotPasswordFeedback({ tone: "info", message: "安全校验正在准备，请稍后再试。" });
      return;
    }

    if (forgotPasswordCaptchaRequired) {
      if (captchaLoadError) {
        setForgotPasswordFeedback({ tone: "error", message: "当前暂时无法发送验证码，请刷新页面后重试。" });
        return;
      }

      if (!captchaReady || !captchaObjRef.current) {
        setForgotPasswordFeedback({ tone: "info", message: "安全校验正在准备，请稍后再试。" });
        return;
      }

      captchaActionRef.current = { type: "send-forgot-password-code" };
      // 同一套 captcha 回调根据 actionRef 走找回密码发送码分支。
      setForgotPasswordFeedback({ tone: "info", message: "请完成安全校验后发送验证码。" });
      openGeetestCaptcha(captchaObjRef.current);
      return;
    }

    setForgotPasswordSendingCode(true);
    try {
      await sendForgotPasswordCodeRequest(normalizedEmail);
    } catch (error) {
      applyForgotPasswordSendCodeError(error as ApiClientError);
    } finally {
      setForgotPasswordSendingCode(false);
    }
  };

  const handleForgotPasswordVerifyCode = async (options?: { email?: string; code?: string }) => {
    const normalizedEmail = normalizeEmail(options?.email ?? forgotPasswordForm.email);
    const trimmedCode = (options?.code ?? forgotPasswordForm.code).trim();
    setForgotPasswordFeedback(null);
    clearForgotPasswordAutoVerifyTimer();

    // 第二步只换取短期 passwordResetToken，真正改密放到第三步提交。
    if (!normalizedEmail || !isValidEmail(normalizedEmail)) {
      setForgotPasswordFeedback({ tone: "error", message: "请先填写正确的登录邮箱。" });
      return;
    }

    if (!forgotPasswordEmailCodeState || forgotPasswordEmailCodeState.sentEmail !== normalizedEmail || isTimestampExpired(forgotPasswordEmailCodeState.expiresAt)) {
      setForgotPasswordFeedback({ tone: "error", message: "请先获取有效的验证码。" });
      return;
    }

    if (!/^\d{6}$/.test(trimmedCode)) {
      setForgotPasswordFeedback({ tone: "error", message: "请输入 6 位数字验证码。" });
      return;
    }

    setForgotPasswordVerifyingCode(true);

    try {
      const response = await apiRequest<PasswordResetVerifyCodeResponse>("/auth/password/reset/verify-code", {
        method: "POST",
        skipAuth: true,
        body: JSON.stringify({
          email: normalizedEmail,
          code: trimmedCode,
        }),
      });

      setForgotPasswordVerification({
        verificationToken: response.verificationToken,
        verifiedEmail: normalizedEmail,
        verifiedAt: Date.now(),
        expiresAt: response.expiresAt,
        code: trimmedCode,
      });
      setForgotPasswordFeedback({
        tone: "success",
        message: `验证码校验通过，现在可以直接设置新密码。请在 ${formatShortExpiry(response.expiresAt)} 前完成重置。`,
      });
    } catch (error) {
      const apiError = error as ApiClientError;
      setForgotPasswordFeedback({
        tone: "error",
        message: apiError.code === "BIZ-1407"
          ? "验证码不正确或已过期，请重新获取。"
          : apiError.message || "验证码校验失败，请稍后重试。",
      });
    } finally {
      setForgotPasswordVerifyingCode(false);
    }
  };

  const resetForgotPasswordToEmailStep = () => {
    forgotPasswordAutoVerifyKeyRef.current = null;
    clearForgotPasswordAutoVerifyTimer();
    setForgotPasswordFeedback(null);
    setForgotPasswordEmailCodeState(null);
    setForgotPasswordVerification(null);
    setForgotPasswordSendCodeCountdown(0);
    setForgotPasswordForm((current) => ({
      ...current,
      code: "",
      newPassword: "",
      confirmPassword: "",
    }));
  };

  const resetForgotPasswordToCodeStep = () => {
    forgotPasswordAutoVerifyKeyRef.current = null;
    clearForgotPasswordAutoVerifyTimer();
    setForgotPasswordFeedback(null);
    setForgotPasswordVerification(null);
    setForgotPasswordForm((current) => ({
      ...current,
      code: "",
      newPassword: "",
      confirmPassword: "",
    }));
    window.setTimeout(() => {
      forgotPasswordCodeInputRef.current?.focus();
    }, 0);
  };

  useEffect(() => {
    scheduleForgotPasswordAutoVerify(forgotPasswordForm.email, forgotPasswordForm.code);
  }, [
    forgotPasswordEmailCodeState,
    forgotPasswordForm.code,
    forgotPasswordForm.email,
    forgotPasswordOpen,
    forgotPasswordVerification,
    forgotPasswordVerifyingCode,
  ]);

  if (ready && isAuthenticated) {
    // 已登录用户进入认证页时直接分诊到工作台或安全的 from 路径。
    return <Navigate to={resolveAuthDestination(role, from)} replace />;
  }

  const handleForgotPasswordChange = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const normalizedEmail = normalizeEmail(forgotPasswordForm.email);
    const trimmedCode = forgotPasswordForm.code.trim();
    setForgotPasswordFeedback(null);

    if (!normalizedEmail || !isValidEmail(normalizedEmail)) {
      setForgotPasswordFeedback({ tone: "error", message: "请先填写正确的登录邮箱。" });
      return;
    }

    if (
      !forgotPasswordVerification
      || forgotPasswordVerification.verifiedEmail !== normalizedEmail
      || forgotPasswordVerification.code !== trimmedCode
      || isTimestampExpired(forgotPasswordVerification.expiresAt)
    ) {
      setForgotPasswordFeedback({ tone: "error", message: "请先完成验证码校验，再设置新密码。" });
      return;
    }

    if (!forgotPasswordForm.newPassword) {
      setForgotPasswordFeedback({ tone: "error", message: "请输入新的登录密码。" });
      return;
    }

    if (!isValidPassword(forgotPasswordForm.newPassword)) {
      setForgotPasswordFeedback({ tone: "error", message: "新密码需为 8-64 位，并同时包含字母和数字。" });
      return;
    }

    if (forgotPasswordForm.newPassword !== forgotPasswordForm.confirmPassword) {
      setForgotPasswordFeedback({ tone: "error", message: "两次输入的新密码不一致，请重新确认。" });
      return;
    }

    setForgotPasswordResetting(true);

    try {
      // 第三步提交 passwordResetToken 和新密码，成功后把登录邮箱回填到主登录表单。
      await apiRequest<PasswordResetChangeResponse>("/auth/password/reset/change", {
        method: "POST",
        skipAuth: true,
        body: JSON.stringify({
          email: normalizedEmail,
          passwordResetToken: forgotPasswordVerification.verificationToken,
          newPassword: forgotPasswordForm.newPassword,
        }),
      });

      setLoginForm((current) => ({
        ...current,
        email: normalizedEmail,
        password: "",
      }));
      setLoginFeedback({
        tone: "success",
        message: "密码已重置成功，请使用新密码登录。",
      });
      setForgotPasswordFeedback({
        tone: "success",
        message: "密码已经更新成功，请使用新密码重新登录。",
      });
      setForgotPasswordOpen(false);
      setForgotPasswordForm({
        email: normalizedEmail,
        code: "",
        newPassword: "",
        confirmPassword: "",
      });
      setForgotPasswordEmailCodeState(null);
      setForgotPasswordVerification(null);
      setForgotPasswordSendCodeCountdown(0);
    } catch (error) {
      const apiError = error as ApiClientError;
      setForgotPasswordFeedback({
        tone: "error",
        message: apiError.code === "BIZ-1409"
          ? "校验凭证已失效，请重新获取验证码。"
          : apiError.message || "密码重置失败，请稍后重试。",
      });
    } finally {
      setForgotPasswordResetting(false);
    }
  };

  const handleLoginSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setLoginFeedback(null);

    if (!loginForm.email.trim()) {
      setLoginFeedback({ tone: "error", message: "请输入登录邮箱。" });
      setMascotState("error");
      triggerShake();
      return;
    }

    if (!isValidEmail(loginForm.email)) {
      setLoginFeedback({ tone: "error", message: "请输入正确的邮箱格式。" });
      setMascotState("error");
      triggerShake();
      return;
    }

    if (!loginForm.password) {
      setLoginFeedback({ tone: "error", message: "请输入登录密码。" });
      setMascotState("error");
      triggerShake();
      return;
    }

    setSubmitting(true);

    try {
      await login(loginForm.email, loginForm.password);
      clearPendingRegistration();
      setPendingRegistrationState(null);
      // 登录成功后立刻读取 sessionStore 中的角色真相，保证管理员和业务角色分流一致。
      const nextRole = getSessionSnapshot().role;
      const destination = resolveAuthDestination(nextRole, from);

      setMascotState("success");
      setLoginFeedback({
        tone: "success",
        message: nextRole === "ADMIN" ? "登录成功，正在进入管理后台。" : "登录成功，正在进入你的工作台。",
      });
      queueNavigation(destination);
    } catch (error) {
      const apiError = error as ApiClientError;
      const message = apiError.code === "AUTH-1002"
        ? "账号或密码不正确，请重新检查。"
        : apiError.code === "AUTH-1006"
          ? "当前账号尚未激活，暂时无法登录。"
          : apiError.message || "登录失败，请稍后再试。";

      setMascotState("error");
      setLoginFeedback({ tone: "error", message });
      triggerShake();
    } finally {
      setSubmitting(false);
    }
  };

  const handleDebugLoginFill = (account: DebugLoginAccount) => {
    if (blurTimeoutRef.current !== null) {
      window.clearTimeout(blurTimeoutRef.current);
      blurTimeoutRef.current = null;
    }

    setLoginForm({
      email: account.email,
      password: account.password,
    });
    setLoginFeedback(null);
    setMascotState("typing");

    blurTimeoutRef.current = window.setTimeout(() => {
      setMascotState("idle");
      blurTimeoutRef.current = null;
    }, 650);
  };

  const handleRegisterSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setRegisterFeedback(null);

    if (!registerForm.displayName.trim()) {
      setRegisterFeedback({ tone: "error", message: "请先填写昵称。" });
      setMascotState("error");
      triggerShake();
      return;
    }

    if (!registerForm.email.trim()) {
      setRegisterFeedback({ tone: "error", message: "请先填写注册邮箱。" });
      setMascotState("error");
      triggerShake();
      return;
    }

    if (!isValidEmail(registerForm.email)) {
      setRegisterFeedback({ tone: "error", message: "注册邮箱格式不正确。" });
      setMascotState("error");
      triggerShake();
      return;
    }

    if (!registerForm.password) {
      setRegisterFeedback({ tone: "error", message: "请先设置登录密码。" });
      setMascotState("error");
      triggerShake();
      return;
    }

    if (!isValidPassword(registerForm.password)) {
      setRegisterFeedback({ tone: "error", message: "密码需为 8-64 位，并同时包含字母和数字。" });
      setMascotState("error");
      triggerShake();
      return;
    }

    const normalizedEmail = normalizeEmail(registerForm.email);
    const trimmedCode = registerForm.code.trim();

    if (!emailCodeState || emailCodeState.sentEmail !== normalizedEmail || isTimestampExpired(emailCodeState.expiresAt)) {
      setRegisterFeedback({ tone: "error", message: "请先获取有效的邮箱验证码。" });
      setMascotState("error");
      triggerShake();
      return;
    }

    if (!trimmedCode) {
      setRegisterFeedback({ tone: "error", message: "请输入邮箱验证码。" });
      setMascotState("error");
      triggerShake();
      return;
    }

    if (!/^\d{6}$/.test(trimmedCode)) {
      setRegisterFeedback({ tone: "error", message: "邮箱验证码为 6 位数字。" });
      setMascotState("error");
      triggerShake();
      return;
    }

    setSubmitting(true);
    let shouldStopVerifying = false;

    try {
      let verifiedState = emailVerification;

      // 注册主表单只负责邮箱 proof；真实建号和资料提交放到下一阶段处理。
      if (
        !verifiedState
        || verifiedState.verifiedEmail !== normalizedEmail
        || verifiedState.code !== trimmedCode
        || isTimestampExpired(verifiedState.expiresAt)
      ) {
        shouldStopVerifying = true;
        setVerifyingEmailCode(true);
        const verificationResponse = await verifyEmailVerificationCode({
          email: normalizedEmail,
          code: trimmedCode,
        });
        verifiedState = {
          verificationToken: verificationResponse.verificationToken,
          verifiedEmail: normalizedEmail,
          verifiedAt: Date.now(),
          expiresAt: verificationResponse.expiresAt,
          code: trimmedCode,
        };
        setEmailVerification(verifiedState);
      }

      const preferredRole = resolveRegisterRole(roleParam);
      const nextPendingRegistration = {
        email: normalizedEmail,
        password: registerForm.password,
        displayName: registerForm.displayName.trim(),
        preferredRole,
        emailVerificationToken: verifiedState.verificationToken,
        emailVerifiedEmail: verifiedState.verifiedEmail,
        emailVerifiedAt: verifiedState.verifiedAt,
        emailVerificationExpiresAt: verifiedState.expiresAt,
        createdAt: Date.now(),
      };

      setPendingRegistration(nextPendingRegistration);
      setPendingRegistrationState(nextPendingRegistration);

      // pendingRegistration 写入 sessionStorage，刷新后仍能继续完成二阶段资料。
      setMascotState("success");
      setRegisterFeedback({
        tone: "success",
        message: "邮箱验证已完成，正在展开身份资料填写。",
      });
      openPanel("register-details");
    } catch (error) {
      const apiError = error as ApiClientError;
      const message = apiError.code === "AUTH-1013"
        ? "验证码不正确或已过期，请重新获取。"
        : apiError.code === "AUTH-1011"
          ? "邮箱验证服务暂时不可用，请稍后重试。"
          : apiError.message || "进入下一步失败，请稍后再试。";

      setMascotState("error");
      setRegisterFeedback({ tone: "error", message });
      triggerShake();
    } finally {
      if (shouldStopVerifying) {
        setVerifyingEmailCode(false);
      }
      setSubmitting(false);
    }
  };

  const captchaEnabled = captchaConfig?.enabled === true;
  const forgotPasswordCaptchaRequired = captchaEnabled && !captchaConfig?.demoPasswordResetBypassEnabled;
  const emailVerificationValid = !!emailVerification
    && emailVerification.verifiedEmail === normalizeEmail(registerForm.email)
    && emailVerification.code === registerForm.code.trim()
    && !isTimestampExpired(emailVerification.expiresAt);
  const forgotPasswordSendCodeButtonLabel = forgotPasswordSendingCode
    ? "发送中..."
    : forgotPasswordSendCodeCountdown > 0
      ? `${forgotPasswordSendCodeCountdown}s 后重发`
      : forgotPasswordEmailCodeValid
        ? "重新发送"
        : "发送验证码";
  const forgotPasswordCodeHint = forgotPasswordVerificationValid
    ? "验证码已通过校验，继续设置新密码即可。"
    : forgotPasswordVerifyingCode
      ? "验证码校验中，请稍候。"
      : forgotPasswordEmailCodeValid
        ? `验证码已发送到 ${forgotPasswordEmailCodeState.sentEmail}，请在 ${formatShortExpiry(forgotPasswordEmailCodeState.expiresAt)} 前完成操作。`
        : "先发送验证码。";
  const sendCodeButtonLabel = captchaBusy || sendingEmailCode
    ? "发送中..."
    : sendCodeCountdown > 0
      ? `${sendCodeCountdown}s 后重发`
      : emailCodeState && !isTimestampExpired(emailCodeState.expiresAt)
        ? "重新发送"
        : "发送验证码";
  const registerSubmitDisabled = busy;

  const loginPane = (
    <motion.div animate={isShake && isLogin ? { x: [0, -10, 10, -10, 10, 0], transition: { duration: 0.4 } } : {}} className="w-full">
      <h2 className="mb-2 text-3xl font-extrabold text-slate-800">欢迎回来</h2>
      <p className="mb-8 text-sm text-slate-500">继续你的 AI 求职训练与后台协作流程。</p>

      <form onSubmit={handleLoginSubmit} className="space-y-5">
        <div>
          <label className="mb-1.5 block text-xs font-bold text-slate-500">邮箱地址</label>
          <div className="relative">
            <Mail size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="email"
              name="email"
              value={loginForm.email}
              onChange={(event) => setLoginForm((current) => ({ ...current, email: event.target.value }))}
              onFocus={() => handleFocus("typing")}
              onBlur={handleBlur}
              className="w-full rounded-xl border border-slate-200 bg-slate-50 py-3 pl-10 pr-4 text-sm transition-all focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
              placeholder="student@example.com"
              autoComplete="email"
            />
          </div>
        </div>

        <div>
          <div className="mb-1.5 flex items-center justify-between">
            <label className="block text-xs font-bold text-slate-500">登录密码</label>
            <button
              type="button"
              className="text-xs font-medium text-indigo-600 transition-colors hover:text-indigo-500"
              onClick={openForgotPasswordDialog}
            >
              忘记密码?
            </button>
          </div>
          <div className="relative">
            <Lock size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="password"
              name="password"
              value={loginForm.password}
              onChange={(event) => setLoginForm((current) => ({ ...current, password: event.target.value }))}
              onFocus={() => handleFocus("password")}
              onBlur={handleBlur}
              className="w-full rounded-xl border border-slate-200 bg-slate-50 py-3 pl-10 pr-4 text-sm transition-all focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
              placeholder="••••••••"
              autoComplete="current-password"
            />
          </div>
        </div>

        {loginFeedback ? (
          <div className={joinClasses("rounded-2xl border px-4 py-3 text-sm leading-6", feedbackStyles(loginFeedback.tone))}>
            {loginFeedback.message}
          </div>
        ) : null}

        <button
          type="submit"
          disabled={busy}
          className="group flex w-full items-center justify-center rounded-xl bg-slate-800 py-3.5 font-bold text-white shadow-lg shadow-slate-200 transition-colors hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-70"
        >
          {busy ? "登录中..." : "安全登录"}
          <ArrowRight size={18} className="ml-2 transition-transform group-hover:translate-x-1" />
        </button>

        <div className="space-y-2 pt-1">
          <div className="flex items-center justify-between text-[11px] font-semibold text-slate-400">
            <span>答辩调试登录</span>
            <span>点击填充账号</span>
          </div>
          <div className="grid grid-cols-2 gap-2">
            {DEBUG_LOGIN_ACCOUNTS.map((account) => (
              <button
                key={account.email}
                type="button"
                disabled={busy}
                onClick={() => handleDebugLoginFill(account)}
                className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-left text-xs font-semibold text-slate-600 transition-colors hover:border-indigo-200 hover:bg-indigo-50 hover:text-indigo-700 disabled:cursor-not-allowed disabled:opacity-60"
                aria-label={`填充${account.roleLabel}${account.displayName}账号`}
              >
                <span className="block text-[11px] font-medium text-slate-400">{account.roleLabel}</span>
                <span className="block">{account.displayName}</span>
              </button>
            ))}
          </div>
        </div>

      </form>
    </motion.div>
  );

  const registerPane = (
    <motion.div animate={isShake && !isLogin ? { x: [0, -10, 10, -10, 10, 0], transition: { duration: 0.4 } } : {}} className="relative w-full pt-16">
      <AnimatePresence>
        {registerFeedback ? (
          <motion.div
            key={`${registerFeedback.tone}-${registerFeedback.message}`}
            initial={{ opacity: 0, y: -12, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -8, scale: 0.98 }}
            transition={{ duration: 0.2 }}
            className="pointer-events-none absolute left-0 right-0 top-0 z-20"
          >
            <div className={joinClasses("rounded-2xl border px-4 py-3 text-sm leading-6 shadow-lg shadow-slate-200/80 backdrop-blur-sm", feedbackStyles(registerFeedback.tone))}>
              {registerFeedback.message}
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>

      <h2 className="mb-2 text-3xl font-extrabold text-slate-800">创建新账号</h2>
      <p className="mb-6 text-sm text-slate-500">先完成账号创建与邮箱验证，通过后会在当前窗口内继续完善身份资料并一次性完成注册。</p>

      <form onSubmit={handleRegisterSubmit} className="space-y-3">
        <div>
          <label className="mb-1.5 block text-xs font-bold text-slate-500">你的昵称</label>
          <div className="relative">
            <User size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              name="displayName"
              value={registerForm.displayName}
              onChange={(event) => setRegisterForm((current) => ({ ...current, displayName: event.target.value }))}
              onFocus={() => handleFocus("questioning")}
              onBlur={handleBlur}
              className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm transition-all focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
              placeholder="怎么称呼你？"
              autoComplete="nickname"
            />
          </div>
        </div>

        <div>
          <label className="mb-1.5 block text-xs font-bold text-slate-500">邮箱地址</label>
          <div className="relative">
            <Mail size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="email"
              name="email"
              value={registerForm.email}
              onChange={(event) => setRegisterForm((current) => ({ ...current, email: event.target.value }))}
              onFocus={() => handleFocus("typing")}
              onBlur={handleBlur}
              className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm transition-all focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
              placeholder="name@example.com"
              autoComplete="email"
            />
          </div>
        </div>

        <div>
          <div className="group relative mb-1.5 flex w-max items-center">
            <label className="block cursor-help text-xs font-bold text-slate-500">设置密码</label>
            <HelpCircle size={14} className="ml-1 cursor-help text-slate-400" />

            <div className="pointer-events-none absolute bottom-full left-0 z-50 mb-2 w-52 translate-y-1 rounded-lg bg-slate-800 p-2.5 text-[10px] leading-relaxed text-white opacity-0 shadow-xl transition-all duration-200 group-hover:translate-y-0 group-hover:opacity-100">
              <span className="mb-1 block font-bold text-indigo-300">当前密码要求：</span>
              • 长度 8 到 64 位
              <br />
              • 需同时包含字母和数字
              <div className="absolute left-6 top-full border-[5px] border-transparent border-t-slate-800" />
            </div>
          </div>
          <div className="relative">
            <Lock size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="password"
              name="password"
              value={registerForm.password}
              onChange={(event) => setRegisterForm((current) => ({ ...current, password: event.target.value }))}
              onFocus={() => handleFocus("password")}
              onBlur={handleBlur}
              className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm transition-all focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
              placeholder="至少 8 位字符"
              autoComplete="new-password"
            />
          </div>
        </div>

        <div>
          <div className="mb-1.5 flex items-center justify-between">
            <label className="block text-xs font-bold text-slate-500">邮箱验证</label>
            {emailVerificationValid ? (
              <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-[11px] font-bold text-emerald-700">已验证</span>
            ) : null}
          </div>

          <div className="flex flex-col gap-2 sm:flex-row">
            <div className="relative flex-1">
              <Mail size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                name="code"
                inputMode="numeric"
                maxLength={6}
                value={registerForm.code}
                onChange={(event) => setRegisterForm((current) => ({ ...current, code: event.target.value.replace(/\D+/g, "").slice(0, 6) }))}
                onFocus={() => handleFocus("typing")}
                onBlur={handleBlur}
                className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm transition-all focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
                placeholder="输入 6 位验证码"
                autoComplete="one-time-code"
              />
            </div>

            <button
              type="button"
              disabled={sendingEmailCode || captchaBusy || captchaLoading || !captchaEnabled || !captchaReady || sendCodeCountdown > 0}
              onClick={handleSendEmailCode}
              className={joinClasses(
                "inline-flex shrink-0 items-center justify-center rounded-xl border px-4 py-2.5 text-sm font-bold transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500/50 disabled:cursor-not-allowed disabled:opacity-60 sm:min-w-[132px]",
                emailVerificationValid
                  ? "border-emerald-200 bg-emerald-50 text-emerald-700 hover:bg-emerald-100"
                  : "border-slate-200 bg-slate-50 text-slate-700 hover:bg-slate-100",
              )}
            >
              <ShieldCheck size={16} className={joinClasses("mr-2", emailVerificationValid ? "text-emerald-600" : "text-indigo-500")} />
              {sendCodeButtonLabel}
            </button>
          </div>

          <p className="mt-2 min-h-10 text-xs leading-5 text-slate-500">
            发送验证码前会自动完成一次安全校验，验证码仅用于本次注册。
          </p>
        </div>

        <button
          type="submit"
          disabled={registerSubmitDisabled}
          className="group flex w-full items-center justify-center rounded-xl bg-indigo-600 py-3.5 font-bold text-white shadow-lg shadow-indigo-200 transition-colors hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-70"
        >
          {busy ? "处理中..." : "继续下一步"}
          <Sparkles size={18} className="ml-2 transition-transform group-hover:scale-110" />
        </button>
      </form>
    </motion.div>
  );

  return (
    <div className="relative h-[100dvh] overflow-hidden bg-slate-100 px-4 py-4 selection:bg-indigo-500/30 sm:px-6 sm:py-6 lg:px-8 lg:py-8">
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        {stageTransitioning ? (
          <>
            <div className="absolute -left-[10%] -top-[30%] h-[70vw] w-[70vw] rounded-full bg-indigo-300/20 blur-[120px]" />
            <div className="absolute -bottom-[20%] -right-[10%] h-[60vw] w-[60vw] rounded-full bg-teal-300/20 blur-[120px]" />
          </>
        ) : (
          <>
            <motion.div
              animate={{ rotate: 360 }}
              transition={{ duration: 100, repeat: Number.POSITIVE_INFINITY, ease: "linear" }}
              className="absolute -left-[10%] -top-[30%] h-[70vw] w-[70vw] rounded-full bg-indigo-300/20 blur-[120px]"
            />
            <motion.div
              animate={{ rotate: -360 }}
              transition={{ duration: 80, repeat: Number.POSITIVE_INFINITY, ease: "linear" }}
              className="absolute -bottom-[20%] -right-[10%] h-[60vw] w-[60vw] rounded-full bg-teal-300/20 blur-[120px]"
            />
          </>
        )}
      </div>

      <div
        className={joinClasses(
          "relative z-10 mx-auto flex h-full w-full items-center justify-center",
          activeStage === "register-details" ? "max-w-[1320px]" : "max-w-[1120px]",
        )}
      >
        <div
          className={joinClasses(
            "relative w-full overflow-hidden",
            activeStage === "register-details"
              ? "h-full max-h-[860px] xl:max-h-[900px]"
              : "h-full max-h-[780px]",
          )}
        >
          <AnimatePresence
            initial={false}
            custom={stageDirection}
            onExitComplete={() => {
              if (stageTransitionTimeoutRef.current !== null) {
                window.clearTimeout(stageTransitionTimeoutRef.current);
                stageTransitionTimeoutRef.current = null;
              }
              setStageTransitioning(false);
            }}
          >
            {activeStage === "register-details" && pendingRegistrationState ? (
              <motion.div
                key="register-details-stage"
                custom={stageDirection}
                variants={stageTransitionVariants}
                initial="enter"
                animate="center"
                exit="exit"
                className="absolute inset-0 transform-gpu will-change-transform"
                style={{ backfaceVisibility: "hidden" }}
              >
                <div className={stageShellClassName}>
                  <div className="h-full w-full min-h-0 overflow-y-auto lg:overflow-hidden">
                    <RegisterDetailsPanel
                      initialRole={resolveRegisterRole(roleParam)}
                      onBack={closePanel}
                      pendingRegistration={pendingRegistrationState}
                      demoModeEnabled={captchaConfig?.demoModeEnabled === true}
                      demoCertificationBypassEnabled={captchaConfig?.demoCertificationBypassEnabled === true}
                    />
                  </div>
                </div>
              </motion.div>
            ) : null}

            {activeStage === "auth" ? (
              <motion.div
                key="auth-stage"
                custom={stageDirection}
                variants={stageTransitionVariants}
                initial="enter"
                animate="center"
                exit="exit"
                className="absolute inset-0 transform-gpu will-change-transform"
                style={{ backfaceVisibility: "hidden" }}
              >
                <div className={stageShellClassName}>
                  <div className="flex h-full w-full min-h-0 overflow-y-auto lg:overflow-hidden">
                    <div className="flex h-full w-full min-h-0 flex-col lg:flex-row">
                      <div
                        className={joinClasses(
                          "w-full bg-white p-6 sm:p-8 lg:flex lg:h-full lg:w-1/2 lg:flex-col lg:justify-center lg:p-10",
                          isLogin ? "flex" : "hidden lg:flex",
                        )}
                      >
                        {loginPane}
                      </div>

                      <div
                        className={joinClasses(
                          "w-full bg-white p-6 sm:p-8 lg:flex lg:h-full lg:w-1/2 lg:flex-col lg:justify-center lg:p-10",
                          !isLogin ? "flex" : "hidden lg:flex",
                        )}
                      >
                        {registerPane}
                      </div>
                    </div>

                    <motion.div
                      initial={false}
                      className="order-first relative flex min-h-[280px] w-full flex-col items-center justify-center overflow-hidden bg-[linear-gradient(135deg,#22d3ee_0%,#3b82f6_52%,#4f46e5_100%)] p-8 text-center shadow-2xl lg:absolute lg:inset-y-0 lg:left-0 lg:w-1/2 lg:p-10"
                      animate={
                        isDesktop
                          ? { x: isLogin ? "100%" : "0%", opacity: 1 }
                          : { x: 0, opacity: 1 }
                      }
                      transition={{ type: "spring", stiffness: 60, damping: 15, mass: 1 }}
                    >
                      <div className="pointer-events-none absolute -right-14 -top-14 h-56 w-56 rounded-full bg-white/20 blur-[56px]" />
                      <div className="pointer-events-none absolute -bottom-16 -left-12 h-64 w-64 rounded-full bg-[#67e8f9]/30 blur-[64px]" />
                      <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(to_right,#ffffff10_1px,transparent_1px),linear-gradient(to_bottom,#ffffff10_1px,transparent_1px)] bg-[size:20px_20px] opacity-30" />

                      <div className="relative z-10 flex w-full flex-col items-center">
                        <InteractiveMascot state={mascotState} motionEnabled={!stageTransitioning} />

                        <div className="flex h-32 flex-col items-center justify-center">
                          <AnimatePresence initial={false} mode="wait">
                            {isLogin ? (
                              <motion.div
                                key="login-text"
                                initial={{ opacity: 0, y: 20 }}
                                animate={{ opacity: 1, y: 0 }}
                                exit={{ opacity: 0, y: -20 }}
                                transition={{ duration: 0.3 }}
                                className="flex flex-col items-center"
                              >
                                <h3 className="mb-3 text-3xl font-extrabold text-white">没有账号？</h3>
                                <p className="mb-6 max-w-[250px] text-sm text-indigo-100">先创建学生、导师或企业账号，再逐步打开各自的体验入口与入驻流程。</p>
                                <button
                                  type="button"
                                  onMouseDown={() => {
                                    if (blurTimeoutRef.current !== null) {
                                      window.clearTimeout(blurTimeoutRef.current);
                                    }
                                    setMascotState("idle");
                                  }}
                                  onClick={() => switchMode("register")}
                                  className="rounded-full border-2 border-white px-8 py-2.5 font-bold text-white transition-colors hover:bg-white hover:text-indigo-600"
                                >
                                  去注册
                                </button>
                              </motion.div>
                            ) : (
                              <motion.div
                                key="register-text"
                                initial={{ opacity: 0, y: 20 }}
                                animate={{ opacity: 1, y: 0 }}
                                exit={{ opacity: 0, y: -20 }}
                                transition={{ duration: 0.3 }}
                                className="flex flex-col items-center"
                              >
                                <h3 className="mb-3 text-3xl font-extrabold text-white">已有账号！</h3>
                                <p className="mb-6 max-w-[250px] text-sm text-indigo-100">欢迎回来，你的登录态、后台入口与后续训练链路会继续从这里衔接。</p>
                                <button
                                  type="button"
                                  onMouseDown={() => {
                                    if (blurTimeoutRef.current !== null) {
                                      window.clearTimeout(blurTimeoutRef.current);
                                    }
                                    setMascotState("idle");
                                  }}
                                  onClick={() => switchMode("login")}
                                  className="rounded-full border-2 border-white px-8 py-2.5 font-bold text-white transition-colors hover:bg-white hover:text-indigo-600"
                                >
                                  去登录
                                </button>
                              </motion.div>
                            )}
                          </AnimatePresence>
                        </div>
                      </div>
                    </motion.div>
                  </div>
                </div>
              </motion.div>
            ) : null}
          </AnimatePresence>
        </div>
      </div>

      <AnimatePresence>
        {forgotPasswordFeedback ? (
          <motion.div
            key={`forgot-password-toast-${forgotPasswordFeedback.tone}-${forgotPasswordFeedback.message}`}
            initial={{ y: -18, opacity: 0, scale: 0.97 }}
            animate={{ y: 0, opacity: 1, scale: 1 }}
            exit={{ y: -12, opacity: 0, scale: 0.98 }}
            className="pointer-events-none fixed left-1/2 top-5 z-[70] w-full max-w-xl -translate-x-1/2 px-4"
          >
            <div className={joinClasses(
              "pointer-events-auto flex items-center gap-4 overflow-hidden rounded-full px-6 py-3.5 backdrop-blur-xl transition-[background-color,border-color,box-shadow] duration-300 ease-out",
              getForgotPasswordToastPresentation(forgotPasswordFeedback.tone).shellClassName,
            )}>
              <div className={joinClasses("flex h-10 w-10 shrink-0 items-center justify-center rounded-full", getForgotPasswordToastPresentation(forgotPasswordFeedback.tone).iconClassName)}>
                {(() => {
                  const ToastIcon = getForgotPasswordToastPresentation(forgotPasswordFeedback.tone).icon;
                  return <ToastIcon size={18} />;
                })()}
              </div>
              <div className="min-w-0 flex-1 text-center">
                <div className={joinClasses("truncate text-base font-black tracking-[0.02em]", getForgotPasswordToastPresentation(forgotPasswordFeedback.tone).titleClassName)}>
                  {getForgotPasswordToastPresentation(forgotPasswordFeedback.tone).title}
                </div>
                <p className={joinClasses("truncate text-[15px] leading-5", getForgotPasswordToastPresentation(forgotPasswordFeedback.tone).messageClassName)}>
                  {forgotPasswordFeedback.message}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setForgotPasswordFeedback(null)}
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-slate-200 bg-white/80 text-slate-500 transition-colors duration-300 hover:border-slate-300 hover:bg-white hover:text-slate-700"
              >
                <X size={15} />
              </button>
            </div>
          </motion.div>
        ) : null}

        {forgotPasswordOpen ? (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-4 backdrop-blur-sm"
          >
            <motion.div
              initial={{ opacity: 0, y: 20, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 12, scale: 0.96 }}
              layout
              transition={{
                duration: 0.28,
                ease: [0.22, 1, 0.36, 1],
                layout: {
                  duration: 0.26,
                  ease: [0.22, 1, 0.36, 1],
                },
              }}
              className="w-full max-w-xl overflow-hidden rounded-[2rem] border border-white/70 bg-white shadow-[0_28px_90px_rgba(15,23,42,0.18)]"
            >
              <div className="bg-gradient-to-r from-indigo-600 via-indigo-500 to-sky-500 px-6 py-6 text-white sm:px-8">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <div className="inline-flex items-center gap-2 rounded-full border border-white/20 bg-white/10 px-3 py-1 text-[11px] text-white/85">
                      <ShieldCheck size={13} />
                      Password Recovery
                    </div>
                    <h3 className="mt-4 text-2xl font-extrabold">重置登录密码</h3>
                  </div>

                  <button
                    type="button"
                    onClick={closeForgotPasswordDialog}
                    disabled={forgotPasswordBusy}
                    className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-white/20 bg-white/10 text-white transition-colors hover:bg-white/20 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    <span className="sr-only">关闭</span>
                    <span aria-hidden className="text-xl leading-none">×</span>
                  </button>
                </div>
              </div>

              <motion.form
                layout
                onSubmit={handleForgotPasswordChange}
                className="space-y-5 px-6 py-6 sm:px-8 sm:py-7"
              >
                <div className="grid gap-3 sm:grid-cols-3">
                  {[
                    { step: 1, title: "填写邮箱", done: forgotPasswordCurrentStep > 1 },
                    { step: 2, title: "输入验证码", done: forgotPasswordCurrentStep > 2 },
                    { step: 3, title: "设置新密码", done: false },
                  ].map((item) => {
                    const isCurrent = item.step === forgotPasswordCurrentStep;
                    const isDone = item.done;

                    return (
                      <div
                        key={item.step}
                        className={joinClasses(
                          "relative flex min-h-[88px] flex-col justify-between overflow-hidden rounded-[1.4rem] border px-4 py-4 text-sm transition-all duration-300",
                          isCurrent
                            ? "border-indigo-200 bg-[linear-gradient(135deg,rgba(238,242,255,0.96),rgba(224,231,255,0.82))] text-indigo-700 shadow-[0_18px_34px_rgba(99,102,241,0.16)]"
                            : isDone
                              ? "border-emerald-200 bg-[linear-gradient(135deg,rgba(236,253,245,0.98),rgba(209,250,229,0.88))] text-emerald-700 shadow-[0_16px_28px_rgba(16,185,129,0.12)]"
                              : "border-slate-200 bg-[linear-gradient(135deg,rgba(248,250,252,0.98),rgba(241,245,249,0.92))] text-slate-500",
                        )}
                      >
                        <div
                          className={joinClasses(
                            "pointer-events-none absolute inset-x-0 top-0 h-px opacity-80",
                            isCurrent
                              ? "bg-gradient-to-r from-transparent via-indigo-300 to-transparent"
                              : isDone
                                ? "bg-gradient-to-r from-transparent via-emerald-300 to-transparent"
                                : "bg-gradient-to-r from-transparent via-slate-200 to-transparent",
                          )}
                        />
                        {isDone ? (
                          <div className="absolute right-3 top-3 inline-flex h-7 w-7 items-center justify-center rounded-full border border-emerald-200 bg-white/92 text-emerald-600 shadow-[inset_0_1px_0_rgba(255,255,255,0.7)] transition-all duration-300">
                            <CheckCircle2 size={15} />
                          </div>
                        ) : isCurrent ? (
                          <div className="absolute right-4 top-4 h-2.5 w-2.5 rounded-full bg-indigo-500 shadow-[0_0_0_4px_rgba(99,102,241,0.12)]" />
                        ) : (
                          <div className="absolute right-4 top-4 h-2.5 w-2.5 rounded-full bg-slate-300/90" />
                        )}
                        <div
                          className={joinClasses(
                            "text-[11px] font-bold",
                            isCurrent
                              ? "text-indigo-500"
                              : isDone
                                ? "text-emerald-500"
                                : "text-slate-400",
                          )}
                        >
                          Step {item.step}
                        </div>
                        <div className="mt-1.5 pr-9 font-semibold leading-5">{item.title}</div>
                      </div>
                    );
                  })}
                </div>

                <motion.div
                  layout
                  transition={{
                    layout: {
                      duration: 0.24,
                      ease: [0.22, 1, 0.36, 1],
                    },
                  }}
                  className="relative overflow-hidden pt-1"
                >
                  <AnimatePresence initial={false} mode="wait" custom={forgotPasswordStepDirection}>
                    {forgotPasswordCurrentStep === 1 ? (
                      <motion.div
                        key="forgot-password-step-1"
                        custom={forgotPasswordStepDirection}
                        variants={forgotPasswordStepTransitionVariants}
                        initial="enter"
                        animate="center"
                        exit="exit"
                        className="w-full space-y-5"
                      >
                        <div>
                          <label className="mb-1.5 block text-xs font-bold text-slate-500">登录邮箱</label>
                          <div className="group relative flex h-14 items-center rounded-2xl border border-slate-200 bg-slate-50 px-4 transition-all duration-300 focus-within:border-indigo-400 focus-within:bg-white focus-within:[box-shadow:inset_0_0_0_1px_rgba(99,102,241,0.28)]">
                            <Mail size={18} className="shrink-0 text-slate-400 transition-colors duration-300 group-focus-within:text-indigo-500" />
                            <input
                              type="email"
                              value={forgotPasswordForm.email}
                              onChange={(event) => setForgotPasswordForm((current) => ({ ...current, email: event.target.value }))}
                              className="block h-full w-full self-stretch bg-transparent pl-3 pr-12 text-sm text-slate-700 outline-none placeholder:text-slate-400"
                              placeholder="student@example.com"
                              autoComplete="email"
                            />
                          </div>
                        </div>

                        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                          <button
                            type="button"
                            onClick={closeForgotPasswordDialog}
                            disabled={forgotPasswordBusy}
                            className="inline-flex items-center justify-center rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-600 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            取消
                          </button>
                          <button
                            type="button"
                            onClick={() => {
                              void handleForgotPasswordSendCode();
                            }}
                            disabled={forgotPasswordSendingCode || captchaBusy || forgotPasswordSendCodeCountdown > 0}
                            className="inline-flex items-center justify-center rounded-xl bg-indigo-600 px-5 py-3 text-sm font-bold text-white shadow-lg shadow-indigo-200 transition-colors hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            <ShieldCheck size={16} className="mr-2" />
                            {forgotPasswordSendCodeButtonLabel}
                          </button>
                        </div>
                      </motion.div>
                    ) : null}

                    {forgotPasswordCurrentStep === 2 ? (
                      <motion.div
                        key="forgot-password-step-2"
                        custom={forgotPasswordStepDirection}
                        variants={forgotPasswordStepTransitionVariants}
                        initial="enter"
                        animate="center"
                        exit="exit"
                        className="w-full space-y-5"
                      >
                        <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                          验证邮箱：<span className="font-semibold text-slate-900">{forgotPasswordEmailCodeState?.sentEmail}</span>
                        </div>

                        <div>
                          <div className="mb-1.5 flex items-center justify-between gap-3">
                            <label className="block text-xs font-bold text-slate-500">邮箱验证码</label>
                            {forgotPasswordVerifyingCode ? (
                              <span className="rounded-full bg-indigo-50 px-2.5 py-1 text-[11px] font-bold text-indigo-700">校验中</span>
                            ) : null}
                          </div>

                          <div className="group relative flex h-14 items-center rounded-2xl border border-slate-200 bg-slate-50 px-4 transition-all duration-300 focus-within:border-indigo-400 focus-within:bg-white focus-within:[box-shadow:inset_0_0_0_1px_rgba(99,102,241,0.28)]">
                            <Mail size={18} className="shrink-0 text-slate-400 transition-colors duration-300 group-focus-within:text-indigo-500" />
                            <input
                              ref={forgotPasswordCodeInputRef}
                              type="text"
                              inputMode="numeric"
                              maxLength={6}
                              value={forgotPasswordForm.code}
                              onChange={(event) => {
                                const nextCode = event.target.value.replace(/\D+/g, "").slice(0, 6);
                                scheduleForgotPasswordAutoVerify(forgotPasswordForm.email, nextCode);
                                setForgotPasswordForm((current) => ({ ...current, code: nextCode }));
                              }}
                              className="block h-full w-full self-stretch bg-transparent pl-3 pr-12 text-sm text-slate-700 outline-none placeholder:text-slate-400"
                              placeholder="输入 6 位验证码"
                              autoComplete="one-time-code"
                            />
                          </div>

                          <p className="mt-2 min-h-10 text-xs leading-5 text-slate-500">
                            {forgotPasswordCodeHint}
                          </p>
                        </div>

                        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-between">
                          <button
                            type="button"
                            onClick={resetForgotPasswordToEmailStep}
                            disabled={forgotPasswordBusy}
                            className="inline-flex items-center justify-center rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-600 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            返回上一步
                          </button>
                          <button
                            type="button"
                            onClick={() => {
                              void handleForgotPasswordSendCode();
                            }}
                            disabled={forgotPasswordSendingCode || captchaBusy || forgotPasswordSendCodeCountdown > 0}
                            className="inline-flex items-center justify-center rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm font-bold text-slate-700 transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            <ShieldCheck size={16} className="mr-2 text-indigo-500" />
                            {forgotPasswordSendCodeButtonLabel}
                          </button>
                        </div>
                      </motion.div>
                    ) : null}

                    {forgotPasswordCurrentStep === 3 ? (
                      <motion.div
                        key="forgot-password-step-3"
                        custom={forgotPasswordStepDirection}
                        variants={forgotPasswordStepTransitionVariants}
                        initial="enter"
                        animate="center"
                        exit="exit"
                        className="w-full space-y-5"
                      >
                        <div className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
                          当前账号 <span className="font-semibold">{forgotPasswordVerification?.verifiedEmail}</span> 已完成身份校验。
                        </div>

                        <div className="grid gap-4 sm:grid-cols-2">
                          <div>
                            <label className="mb-1.5 block text-xs font-bold text-slate-500">新密码</label>
                            <div className="group relative flex h-14 items-center rounded-2xl border border-slate-200 bg-slate-50 px-4 transition-all duration-300 focus-within:border-indigo-400 focus-within:bg-white focus-within:[box-shadow:inset_0_0_0_1px_rgba(99,102,241,0.28)]">
                              <Lock size={18} className="shrink-0 text-slate-400 transition-colors duration-300 group-focus-within:text-indigo-500" />
                              <input
                                type="password"
                                value={forgotPasswordForm.newPassword}
                                onChange={(event) => setForgotPasswordForm((current) => ({ ...current, newPassword: event.target.value }))}
                                disabled={forgotPasswordResetting}
                                className="block h-full w-full self-stretch bg-transparent pl-3 pr-12 text-sm text-slate-700 outline-none placeholder:text-slate-400 disabled:cursor-not-allowed disabled:opacity-60"
                                placeholder="8-64 位，含字母和数字"
                                autoComplete="new-password"
                              />
                            </div>
                          </div>

                          <div>
                            <label className="mb-1.5 block text-xs font-bold text-slate-500">确认新密码</label>
                            <div className="group relative flex h-14 items-center rounded-2xl border border-slate-200 bg-slate-50 px-4 transition-all duration-300 focus-within:border-indigo-400 focus-within:bg-white focus-within:[box-shadow:inset_0_0_0_1px_rgba(99,102,241,0.28)]">
                              <Lock size={18} className="shrink-0 text-slate-400 transition-colors duration-300 group-focus-within:text-indigo-500" />
                              <input
                                type="password"
                                value={forgotPasswordForm.confirmPassword}
                                onChange={(event) => setForgotPasswordForm((current) => ({ ...current, confirmPassword: event.target.value }))}
                                disabled={forgotPasswordResetting}
                                className="block h-full w-full self-stretch bg-transparent pl-3 pr-12 text-sm text-slate-700 outline-none placeholder:text-slate-400 disabled:cursor-not-allowed disabled:opacity-60"
                                placeholder="再次输入新密码"
                                autoComplete="new-password"
                              />
                            </div>
                          </div>
                        </div>

                        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                          <button
                            type="button"
                            onClick={resetForgotPasswordToCodeStep}
                            disabled={forgotPasswordBusy}
                            className="inline-flex items-center justify-center rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-600 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            返回验证码
                          </button>
                          <button
                            type="submit"
                            disabled={forgotPasswordResetting || !forgotPasswordVerificationValid}
                            className="inline-flex items-center justify-center rounded-xl bg-indigo-600 px-5 py-3 text-sm font-bold text-white shadow-lg shadow-indigo-200 transition-colors hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            {forgotPasswordResetting ? "重置中..." : "更新密码"}
                            <ArrowRight size={16} className="ml-2" />
                          </button>
                        </div>
                      </motion.div>
                    ) : null}
                  </AnimatePresence>
                </motion.div>
              </motion.form>
            </motion.div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}
