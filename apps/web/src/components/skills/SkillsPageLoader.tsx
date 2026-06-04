import { useEffect, useId, useRef, useState } from "react";
import { motion } from "framer-motion";

type SkillsPageLoaderPhase = "enter" | "loading" | "exit";

type SkillsPageLoaderProps = {
  active: boolean;
  showLoading: boolean;
  onCovered?: () => void;
  onExited?: () => void;
};

const CONFIG = {
  loadingTextOffsetX: -4,
  lOffsetX: -4.5,
  oadingOffsetX: -2.8,
  dotsOffsetX: 18,
  enterDuration: 1,
  loadingDuration: 0.4,
  exitDuration: 0.96,
  enterToLoadingDelay: 1000,
  minimumLoadingDisplayMs: 1480,
  backgroundTop: "#1c3f78",
  backgroundMiddle: "#0f2651",
  backgroundBottom: "#091225",
  backgroundGlow: "#2f67aa",
};

const EASING = {
  enter: [0.22, 1, 0.36, 1] as const,
  exit: [0.7, 0, 0.84, 0] as const,
  loading: [0.4, 0, 0.2, 1] as const,
};

const L_ANIMATION = {
  initial: {
    scale: 80,
    rotate: -90,
    x: -15,
    y: -20,
    strokeWidth: 0.8,
  },
  exit: {
    scale: 80,
    rotate: 90,
    x: 15,
    y: 20,
    strokeWidth: 0.8,
  },
  colorTransition: {
    fillDuration: 0.3,
    strokeDuration: 0.3,
    strokeWidthDuration: 0.5,
    enterColorDelay: 0.5,
    exitStrokeDelay: 0.5,
  },
  opacityTransition: {
    enterDelay: 0.5,
    enterDuration: 0.3,
    exitDuration: 0.3,
  },
};

const OADING_ANIMATION = {
  enterTransition: {
    opacityDelay: 0.1,
    opacityDuration: 0.2,
  },
  exitTransition: {
    duration: 0.25,
    xOffset: 5,
  },
  dots: {
    opacityRange: [0.3, 1, 0.3] as const,
    cycleDuration: 0.8,
    staggerDelay: 0.2,
    spacing: 1.2,
  },
};

function LoaderDecorationLayer({ phase }: { phase: SkillsPageLoaderPhase }) {
  const layerOpacity = 1;
  const layerScale = 1;
  const ringCenterX = "41.4%";
  const ringCenterY = "49.4%";
  const ringTransformOrigin = `${ringCenterX} ${ringCenterY}`;
  const ringScaleEase = [0.08, 0.82, 0.22, 1] as const;
  const syncedAxisScale = phase === "loading" ? 1 : 0;
  const syncedAxisDuration = phase === "loading" ? 1.82 : 0.98;
  const syncedAxisEase =
    phase === "loading"
      ? ([0.14, 0.74, 0.18, 1] as const)
      : ([0.46, 0.02, 0.18, 1] as const);
  const ringAnimate = {
    opacity: phase === "loading" ? 1 : 0,
    scale: phase === "loading" ? 1 : 0.2,
  };
  const ringTransition =
    phase === "loading"
      ? {
          opacity: { duration: 0.42, ease: [0.22, 1, 0.36, 1] as const },
          scale: {
            duration: syncedAxisDuration,
            ease: syncedAxisEase,
          },
        }
      : {
          opacity: { duration: 0.24, ease: EASING.loading },
          scale: {
            duration: syncedAxisDuration,
            ease: syncedAxisEase,
          },
        };

  return (
    <motion.div
      className="pointer-events-none absolute inset-0 overflow-hidden"
      initial={{ opacity: 0, scale: 0.988 }}
      animate={{ opacity: layerOpacity, scale: layerScale }}
      transition={{
        opacity: { duration: 0.28, ease: "easeOut" },
        scale: { duration: 0.3, ease: "easeOut" },
      }}
    >
      <motion.div
        className="absolute -left-[12%] -top-[10%] h-[32rem] w-[32rem] rounded-full"
        style={{
          background:
            "radial-gradient(circle, rgba(56,189,248,0.18) 0%, rgba(56,189,248,0.1) 28%, rgba(56,189,248,0.05) 42%, transparent 72%)",
          willChange: "transform, opacity",
        }}
        animate={{
          opacity: [0.18, 0.3, 0.2],
          scale: [0.96, 1.03, 0.98],
        }}
        transition={{ duration: 6.8, ease: "easeInOut", repeat: Infinity }}
      />

      <motion.div
        className="absolute -right-[10%] top-[4%] h-[30rem] w-[30rem] rounded-full"
        style={{
          background:
            "radial-gradient(circle, rgba(129,140,248,0.16) 0%, rgba(129,140,248,0.08) 26%, rgba(129,140,248,0.04) 40%, transparent 72%)",
          willChange: "transform, opacity",
        }}
        animate={{
          opacity: [0.16, 0.26, 0.18],
          scale: [0.97, 1.04, 0.99],
        }}
        transition={{ duration: 7.4, ease: "easeInOut", repeat: Infinity, delay: 0.4 }}
      />

      <motion.div
        className="absolute right-[2%] bottom-[-14%] h-[28rem] w-[28rem] rounded-full"
        style={{
          background:
            "radial-gradient(circle, rgba(45,212,191,0.14) 0%, rgba(45,212,191,0.07) 24%, rgba(45,212,191,0.035) 38%, transparent 74%)",
          willChange: "transform, opacity",
        }}
        animate={{
          opacity: [0.12, 0.2, 0.14],
          scale: [0.97, 1.05, 1],
        }}
        transition={{ duration: 8.1, ease: "easeInOut", repeat: Infinity, delay: 0.8 }}
      />

      <motion.div
        className="absolute h-14 w-14"
        style={{ left: "calc(50% - 11rem)", top: "calc(50% - 5.4rem)", willChange: "transform, opacity" }}
        animate={{ opacity: [0.46, 0.82, 0.46], y: [0, -2, 0] }}
        transition={{ duration: 2.8, ease: "easeInOut", repeat: Infinity }}
      >
        <div className="absolute inset-0 border-l border-t border-cyan-200/28" />
        <div className="absolute left-0 top-0 h-px w-8 bg-cyan-200/46" />
        <div className="absolute left-0 top-0 h-8 w-px bg-cyan-200/46" />
        <div className="absolute left-3 top-5 text-[11px] uppercase tracking-[0.28em] text-cyan-100/48">
          SYS
        </div>
      </motion.div>

      <motion.div
        className="absolute h-14 w-14"
        style={{ left: "calc(50% + 7.5rem)", top: "calc(50% - 5.4rem)", willChange: "transform, opacity" }}
        animate={{ opacity: [0.42, 0.78, 0.42], y: [0, -2, 0] }}
        transition={{ duration: 3, ease: "easeInOut", repeat: Infinity, delay: 0.24 }}
      >
        <div className="absolute inset-0 border-r border-t border-indigo-200/28" />
        <div className="absolute right-0 top-0 h-px w-8 bg-indigo-200/44" />
        <div className="absolute right-0 top-0 h-8 w-px bg-indigo-200/44" />
        <div className="absolute right-[-0.1rem] top-5 text-[11px] uppercase tracking-[0.28em] text-indigo-100/46">
          LOCK
        </div>
      </motion.div>

      <motion.div
        className="absolute h-14 w-14"
        style={{ left: "calc(50% - 11rem)", top: "calc(50% + 2rem)", willChange: "transform, opacity" }}
        animate={{ opacity: [0.4, 0.72, 0.4], y: [0, 2, 0] }}
        transition={{ duration: 2.9, ease: "easeInOut", repeat: Infinity, delay: 0.12 }}
      >
        <div className="absolute inset-0 border-b border-l border-teal-200/24" />
        <div className="absolute bottom-0 left-0 h-px w-8 bg-teal-200/42" />
        <div className="absolute bottom-0 left-0 h-8 w-px bg-teal-200/42" />
        <div className="absolute left-3 top-8 text-[11px] uppercase tracking-[0.28em] text-teal-100/44">
          SYNC
        </div>
      </motion.div>

      <motion.div
        className="absolute h-14 w-14"
        style={{ left: "calc(50% + 7.5rem)", top: "calc(50% + 2rem)", willChange: "transform, opacity" }}
        animate={{ opacity: [0.38, 0.68, 0.38], y: [0, 2, 0] }}
        transition={{ duration: 3.1, ease: "easeInOut", repeat: Infinity, delay: 0.34 }}
      >
        <div className="absolute inset-0 border-b border-r border-cyan-100/24" />
        <div className="absolute bottom-0 right-0 h-px w-8 bg-cyan-100/38" />
        <div className="absolute bottom-0 right-0 h-8 w-px bg-cyan-100/38" />
        <div className="absolute right-[-0.1rem] top-8 text-[11px] uppercase tracking-[0.28em] text-cyan-50/42">
          MAP
        </div>
      </motion.div>

      <motion.div
        className="absolute inset-0"
        style={{ transformOrigin: ringTransformOrigin }}
        initial={{ opacity: 0, scale: 0.2 }}
        animate={ringAnimate}
        transition={ringTransition}
      >
        <motion.div
          className="absolute inset-0"
          style={{ transformOrigin: ringTransformOrigin, willChange: "transform" }}
          animate={{ rotate: 360 }}
          transition={{ rotate: { duration: 21.6, ease: "linear", repeat: Infinity } }}
        >
          <motion.div
            className="absolute inset-0"
            style={{ transformOrigin: ringTransformOrigin, willChange: "transform" }}
            animate={{ scaleX: syncedAxisScale }}
            transition={{ duration: syncedAxisDuration, ease: syncedAxisEase }}
          >
            <motion.div
              className="absolute h-[0.9rem] w-[160vw] -translate-x-1/2 -translate-y-1/2"
              style={{
                left: ringCenterX,
                top: ringCenterY,
                background:
                  "linear-gradient(90deg, transparent 0%, rgba(103,232,249,0.02) 10%, rgba(103,232,249,0.12) 28%, rgba(236,254,255,0.34) 50%, rgba(103,232,249,0.12) 72%, rgba(103,232,249,0.02) 90%, transparent 100%)",
                willChange: "transform, opacity",
              }}
              animate={{
                opacity: [0.14, 0.24, 0.14],
              }}
              transition={{ duration: 2.8, ease: "easeInOut", repeat: Infinity }}
            />

            <motion.div
              className="absolute h-[2px] w-[160vw] -translate-x-1/2 -translate-y-1/2"
              style={{
                left: ringCenterX,
                top: ringCenterY,
                background:
                  "linear-gradient(90deg, transparent 0%, rgba(103,232,249,0.05) 12%, rgba(103,232,249,0.2) 30%, rgba(236,254,255,0.9) 50%, rgba(103,232,249,0.2) 70%, rgba(103,232,249,0.05) 88%, transparent 100%)",
                boxShadow: "0 0 10px rgba(103,232,249,0.32), 0 0 22px rgba(103,232,249,0.16)",
                willChange: "transform, opacity",
              }}
              animate={{
                opacity: [0.28, 0.56, 0.28],
              }}
              transition={{ duration: 2.3, ease: "easeInOut", repeat: Infinity }}
            />
          </motion.div>

          <motion.div
            className="absolute inset-0"
            style={{ transformOrigin: ringTransformOrigin, willChange: "transform" }}
            animate={{ scaleY: syncedAxisScale }}
            transition={{ duration: syncedAxisDuration, ease: syncedAxisEase }}
          >
            <motion.div
              className="absolute h-[160vh] w-[0.9rem] -translate-x-1/2 -translate-y-1/2"
              style={{
                left: ringCenterX,
                top: ringCenterY,
                background:
                  "linear-gradient(180deg, transparent 0%, rgba(103,232,249,0.02) 10%, rgba(103,232,249,0.11) 28%, rgba(236,254,255,0.32) 50%, rgba(103,232,249,0.11) 72%, rgba(103,232,249,0.02) 90%, transparent 100%)",
                willChange: "transform, opacity",
              }}
              animate={{
                opacity: [0.1, 0.2, 0.1],
              }}
              transition={{ duration: 3, ease: "easeInOut", repeat: Infinity, delay: 0.12 }}
            />

            <motion.div
              className="absolute h-[160vh] w-[2px] -translate-x-1/2 -translate-y-1/2"
              style={{
                left: ringCenterX,
                top: ringCenterY,
                background:
                  "linear-gradient(180deg, transparent 0%, rgba(103,232,249,0.04) 12%, rgba(103,232,249,0.18) 30%, rgba(236,254,255,0.82) 50%, rgba(103,232,249,0.18) 70%, rgba(103,232,249,0.04) 88%, transparent 100%)",
                boxShadow: "0 0 10px rgba(103,232,249,0.28), 0 0 20px rgba(103,232,249,0.14)",
                willChange: "transform, opacity",
              }}
              animate={{
                opacity: [0.22, 0.48, 0.22],
              }}
              transition={{ duration: 2.5, ease: "easeInOut", repeat: Infinity, delay: 0.08 }}
            />
          </motion.div>
        </motion.div>

        <motion.div
          className="absolute h-[19.2rem] w-[19.2rem] -translate-x-1/2 -translate-y-1/2 rounded-full"
          style={{
            left: ringCenterX,
            top: ringCenterY,
            background:
              "conic-gradient(from 0deg, transparent 0deg, transparent 36deg, rgba(186,230,253,0.16) 78deg, rgba(103,232,249,0.44) 138deg, transparent 184deg, transparent 226deg, rgba(103,232,249,0.24) 272deg, rgba(186,230,253,0.14) 320deg, transparent 360deg)",
            maskImage:
              "radial-gradient(circle, transparent calc(50% - 5px), black calc(50% - 2px), black calc(50% + 2px), transparent calc(50% + 5px))",
            WebkitMaskImage:
              "radial-gradient(circle, transparent calc(50% - 5px), black calc(50% - 2px), black calc(50% + 2px), transparent calc(50% + 5px))",
            willChange: "transform, opacity",
          }}
          animate={{
            rotate: -360,
            opacity: [0.16, 0.34, 0.16],
          }}
          transition={{
            rotate: { duration: 7.6, ease: "linear", repeat: Infinity },
            opacity: { duration: 2.8, ease: "easeInOut", repeat: Infinity },
          }}
        />

        <motion.div
          className="absolute h-[15.4rem] w-[15.4rem] -translate-x-1/2 -translate-y-1/2 rounded-full"
          style={{
            left: ringCenterX,
            top: ringCenterY,
            background:
              "conic-gradient(from 0deg, transparent 0deg, transparent 212deg, rgba(103,232,249,0.12) 238deg, rgba(103,232,249,0.6) 300deg, rgba(103,232,249,0.16) 340deg, transparent 360deg)",
            maskImage:
              "radial-gradient(circle, transparent calc(50% - 8px), black calc(50% - 5px), black calc(50% + 5px), transparent calc(50% + 8px))",
            WebkitMaskImage:
              "radial-gradient(circle, transparent calc(50% - 8px), black calc(50% - 5px), black calc(50% + 5px), transparent calc(50% + 8px))",
            willChange: "transform, opacity",
          }}
          animate={{
            rotate: 360,
            opacity: [0.38, 0.62, 0.38],
          }}
          transition={{
            rotate: { duration: 5.2, ease: "linear", repeat: Infinity },
            opacity: { duration: 2.2, ease: "easeInOut", repeat: Infinity },
          }}
        />

        <motion.div
          className="absolute h-[13rem] w-[13rem] -translate-x-1/2 -translate-y-1/2 rounded-full border-[5px] border-transparent border-r-cyan-100/72 border-t-cyan-100/64"
          style={{
            left: ringCenterX,
            top: ringCenterY,
            willChange: "transform, opacity",
            boxShadow: "0 0 34px rgba(103,232,249,0.34)",
          }}
          animate={{
            rotate: -360,
            opacity: [0.7, 1, 0.7],
          }}
          transition={{
            rotate: { duration: 4, ease: "linear", repeat: Infinity },
            opacity: { duration: 1.7, ease: "easeInOut", repeat: Infinity },
          }}
        />
      </motion.div>

      <motion.div
        className="absolute h-px w-[13rem] -translate-x-1/2"
        style={{
          left: "48%",
          top: "57.8%",
          background:
            "linear-gradient(90deg, transparent 0%, rgba(103,232,249,0.16) 18%, rgba(236,254,255,0.92) 50%, rgba(103,232,249,0.16) 82%, transparent 100%)",
          willChange: "transform, opacity",
        }}
        animate={{
          x: [-12, 0, 12],
          scaleX: [0.2, 1, 0.34],
          opacity: [0.12, 0.8, 0.16],
        }}
        transition={{ duration: 1.3, ease: "easeInOut", repeat: Infinity }}
      />
    </motion.div>
  );
}

export function SkillsPageLoader({ active, showLoading, onCovered, onExited }: SkillsPageLoaderProps) {
  const [phase, setPhase] = useState<SkillsPageLoaderPhase>("enter");
  const maskId = useId();
  const backgroundGradientId = `${maskId}-background`;
  const backgroundGlowId = `${maskId}-background-glow`;
  const textGlowFilterId = `${maskId}-text-glow`;
  const loadingStartedAtRef = useRef<number | null>(null);
  const coveredNotifiedRef = useRef(false);

  useEffect(() => {
    const timerId = window.setTimeout(() => {
      if (!coveredNotifiedRef.current) {
        coveredNotifiedRef.current = true;
        onCovered?.();
      }
    }, CONFIG.enterToLoadingDelay);

    return () => {
      window.clearTimeout(timerId);
    };
  }, [onCovered]);

  useEffect(() => {
    if (!showLoading || phase !== "enter" || !coveredNotifiedRef.current) {
      return;
    }

    loadingStartedAtRef.current = performance.now();
    setPhase("loading");
  }, [phase, showLoading]);

  useEffect(() => {
    if (active || phase !== "loading") {
      return;
    }

    const loadingStartedAt = loadingStartedAtRef.current ?? performance.now();
    const elapsed = performance.now() - loadingStartedAt;
    const remaining = Math.max(CONFIG.minimumLoadingDisplayMs - elapsed, 0);
    const timerId = window.setTimeout(() => {
      setPhase("exit");
    }, remaining);

    return () => {
      window.clearTimeout(timerId);
    };
  }, [active, phase]);

  const centerX = 50;
  const centerY = 50;
  const baseOffset = CONFIG.loadingTextOffsetX;
  const lX = phase === "loading" ? CONFIG.lOffsetX + baseOffset : 0;
  const oadingX = phase === "loading" ? CONFIG.oadingOffsetX + baseOffset : 0;

  const maskLAnimation =
    phase === "enter"
      ? {
          scale: 1,
          rotate: 0,
          x: 0,
          y: 0,
          fill: "#FFFFFF",
          stroke: "#FFFFFF",
          strokeWidth: 0,
          transition: {
            duration: CONFIG.enterDuration,
            ease: EASING.enter,
            fill: {
              duration: L_ANIMATION.colorTransition.fillDuration,
              delay: L_ANIMATION.colorTransition.enterColorDelay,
            },
            stroke: {
              duration: L_ANIMATION.colorTransition.strokeDuration,
              delay: L_ANIMATION.colorTransition.enterColorDelay,
            },
            strokeWidth: {
              duration: L_ANIMATION.colorTransition.strokeWidthDuration,
            },
          },
        }
      : phase === "loading"
        ? {
            scale: 1,
            rotate: 0,
            x: lX,
            y: 0,
            fill: "#FFFFFF",
            stroke: "#FFFFFF",
            strokeWidth: 0,
            transition: { duration: CONFIG.loadingDuration, ease: EASING.loading },
          }
        : {
            scale: L_ANIMATION.exit.scale,
            rotate: L_ANIMATION.exit.rotate,
            x: L_ANIMATION.exit.x,
            y: L_ANIMATION.exit.y,
            fill: "#000000",
            stroke: "#000000",
            strokeWidth: L_ANIMATION.exit.strokeWidth,
            transition: {
              duration: CONFIG.exitDuration,
              ease: EASING.exit,
              fill: {
                duration: L_ANIMATION.colorTransition.fillDuration,
              },
              stroke: {
                duration: L_ANIMATION.colorTransition.strokeDuration,
              },
              strokeWidth: {
                duration: L_ANIMATION.colorTransition.strokeDuration,
                delay: L_ANIMATION.colorTransition.exitStrokeDelay,
              },
            },
          };

  const visibleLAnimation =
    phase === "enter"
      ? {
          scale: 1,
          rotate: 0,
          x: 0,
          y: 0,
          opacity: 1,
          transition: {
            duration: CONFIG.enterDuration,
            ease: EASING.enter,
            opacity: {
              delay: L_ANIMATION.opacityTransition.enterDelay,
              duration: L_ANIMATION.opacityTransition.enterDuration,
            },
          },
        }
      : phase === "loading"
        ? {
            scale: 1,
            rotate: 0,
            x: lX,
            y: 0,
            opacity: 1,
            transition: { duration: CONFIG.loadingDuration, ease: EASING.loading },
          }
        : {
            scale: L_ANIMATION.exit.scale,
            rotate: L_ANIMATION.exit.rotate,
            x: L_ANIMATION.exit.x,
            y: L_ANIMATION.exit.y,
            opacity: 0,
            transition: {
              duration: CONFIG.exitDuration,
              ease: EASING.exit,
              opacity: { duration: L_ANIMATION.opacityTransition.exitDuration },
            },
          };

  const glowLAnimation =
    phase === "enter"
      ? {
          scale: 1,
          rotate: 0,
          x: 0,
          y: 0,
          opacity: 0.34,
          transition: {
            duration: CONFIG.enterDuration,
            ease: EASING.enter,
            opacity: {
              delay: L_ANIMATION.opacityTransition.enterDelay,
              duration: L_ANIMATION.opacityTransition.enterDuration,
            },
          },
        }
      : phase === "loading"
        ? {
            scale: 1,
            rotate: 0,
            x: lX,
            y: 0,
            opacity: 0.44,
            transition: { duration: CONFIG.loadingDuration, ease: EASING.loading },
          }
        : {
            scale: L_ANIMATION.exit.scale,
            rotate: L_ANIMATION.exit.rotate,
            x: L_ANIMATION.exit.x,
            y: L_ANIMATION.exit.y,
            opacity: 0,
            transition: {
              duration: CONFIG.exitDuration,
              ease: EASING.exit,
              opacity: { duration: L_ANIMATION.opacityTransition.exitDuration },
            },
          };

  const oadingAnimation =
    phase === "enter"
      ? { opacity: 0, x: 0 }
      : phase === "loading"
        ? {
            opacity: 1,
            x: oadingX,
            transition: {
              x: { duration: CONFIG.loadingDuration, ease: EASING.loading },
              opacity: {
                delay: OADING_ANIMATION.enterTransition.opacityDelay,
                duration: OADING_ANIMATION.enterTransition.opacityDuration,
              },
            },
          }
        : {
            opacity: 0,
            x: oadingX + OADING_ANIMATION.exitTransition.xOffset,
            transition: {
              duration: OADING_ANIMATION.exitTransition.duration,
              ease: EASING.exit,
            },
          };

  const oadingGlowAnimation =
    phase === "enter"
      ? { opacity: 0, x: 0 }
      : phase === "loading"
        ? {
            opacity: 0.34,
            x: oadingX,
            transition: {
              x: { duration: CONFIG.loadingDuration, ease: EASING.loading },
              opacity: {
                delay: OADING_ANIMATION.enterTransition.opacityDelay,
                duration: OADING_ANIMATION.enterTransition.opacityDuration,
              },
            },
          }
        : {
            opacity: 0,
            x: oadingX + OADING_ANIMATION.exitTransition.xOffset,
            transition: {
              duration: OADING_ANIMATION.exitTransition.duration,
              ease: EASING.exit,
            },
          };

  const getDotAnimation = (dotIndex: number) =>
    phase === "enter"
      ? { opacity: 0, x: 0 }
      : phase === "loading"
        ? {
            opacity: [...OADING_ANIMATION.dots.opacityRange],
            x: oadingX,
            transition: {
              x: { duration: CONFIG.loadingDuration, ease: EASING.loading },
              opacity: {
                duration: OADING_ANIMATION.dots.cycleDuration,
                repeat: Infinity,
                delay: dotIndex * OADING_ANIMATION.dots.staggerDelay,
                ease: "easeInOut",
              },
            },
          }
        : {
            opacity: 0,
            x: oadingX + OADING_ANIMATION.exitTransition.xOffset,
            transition: {
              duration: OADING_ANIMATION.exitTransition.duration,
              ease: EASING.exit,
            },
          };

  const loadingCaptionAnimation =
    phase === "enter"
      ? { opacity: 0, y: 1.2 }
      : phase === "loading"
        ? {
            opacity: 1,
            y: 0,
            transition: {
              opacity: { delay: 0.18, duration: 0.28, ease: EASING.loading },
              y: { delay: 0.12, duration: 0.44, ease: EASING.loading },
            },
          }
        : {
            opacity: 0,
            y: 0.8,
            transition: {
              duration: 0.26,
              ease: EASING.exit,
            },
          };

  const loadingCaptionGlowAnimation =
    phase === "enter"
      ? { opacity: 0, y: 1.2 }
      : phase === "loading"
        ? {
            opacity: 0.42,
            y: 0,
            transition: {
              opacity: { delay: 0.18, duration: 0.28, ease: EASING.loading },
              y: { delay: 0.12, duration: 0.44, ease: EASING.loading },
            },
          }
        : {
            opacity: 0,
            y: 0.8,
            transition: {
              duration: 0.26,
              ease: EASING.exit,
            },
          };

  return (
    <motion.div className="fixed inset-0 z-[120] pointer-events-auto" aria-hidden="true">
      <svg
        className="absolute inset-0 h-full w-full"
        viewBox="0 0 100 100"
        preserveAspectRatio="xMidYMid slice"
      >
        <defs>
          <linearGradient id={backgroundGradientId} x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stopColor={CONFIG.backgroundTop} />
            <stop offset="52%" stopColor={CONFIG.backgroundMiddle} />
            <stop offset="100%" stopColor={CONFIG.backgroundBottom} />
          </linearGradient>

          <radialGradient id={backgroundGlowId} cx="50%" cy="44%" r="66%">
            <stop offset="0%" stopColor={CONFIG.backgroundGlow} stopOpacity="0.72" />
            <stop offset="42%" stopColor={CONFIG.backgroundGlow} stopOpacity="0.18" />
            <stop offset="100%" stopColor={CONFIG.backgroundGlow} stopOpacity="0" />
          </radialGradient>

          <filter id={textGlowFilterId} x="-180%" y="-180%" width="460%" height="460%">
            <feGaussianBlur stdDeviation="0.72" />
          </filter>

          <mask id={maskId}>
            <rect x="0" y="0" width="100" height="100" fill="white" />

            <motion.text
              x={centerX}
              y={centerY}
              fontSize="5"
              fontWeight="900"
              fontFamily="Arial, sans-serif"
              textAnchor="middle"
              dominantBaseline="middle"
              initial={{
                scale: L_ANIMATION.initial.scale,
                rotate: L_ANIMATION.initial.rotate,
                x: L_ANIMATION.initial.x,
                y: L_ANIMATION.initial.y,
                fill: "#000000",
                stroke: "#000000",
                strokeWidth: L_ANIMATION.initial.strokeWidth,
              }}
              animate={maskLAnimation}
              onAnimationComplete={() => {
                if (phase === "exit") {
                  onExited?.();
                }
              }}
            >
              L
            </motion.text>
          </mask>
        </defs>

        <rect
          x="0"
          y="0"
          width="100"
          height="100"
          fill={`url(#${backgroundGradientId})`}
          mask={`url(#${maskId})`}
        />
        <rect
          x="0"
          y="0"
          width="100"
          height="100"
          fill={`url(#${backgroundGlowId})`}
          opacity="0.92"
          mask={`url(#${maskId})`}
        />
      </svg>

      <LoaderDecorationLayer phase={phase} />

      <svg
        className="absolute inset-0 h-full w-full"
        viewBox="0 0 100 100"
        preserveAspectRatio="xMidYMid slice"
      >
        <motion.text
          x={centerX}
          y={centerY}
          fontSize="5"
          fontWeight="900"
          fontFamily="Arial, sans-serif"
          textAnchor="middle"
          dominantBaseline="middle"
          fill="#dff9ff"
          stroke="#a5f3fc"
          strokeWidth="0.18"
          filter={`url(#${textGlowFilterId})`}
          initial={{
            scale: L_ANIMATION.initial.scale,
            rotate: L_ANIMATION.initial.rotate,
            x: L_ANIMATION.initial.x,
            y: L_ANIMATION.initial.y,
            opacity: 0,
          }}
          animate={glowLAnimation}
        >
          L
        </motion.text>

        <motion.text
          x={centerX}
          y={centerY}
          fontSize="5"
          fontWeight="900"
          fontFamily="Arial, sans-serif"
          textAnchor="middle"
          dominantBaseline="middle"
          fill="white"
          initial={{
            scale: L_ANIMATION.initial.scale,
            rotate: L_ANIMATION.initial.rotate,
            x: L_ANIMATION.initial.x,
            y: L_ANIMATION.initial.y,
            opacity: 0,
          }}
          animate={visibleLAnimation}
        >
          L
        </motion.text>

        <motion.text
          x={centerX}
          y={centerY}
          fontSize="5"
          fontWeight="900"
          fontFamily="Arial, sans-serif"
          dominantBaseline="middle"
          fill="#dff9ff"
          stroke="#c9f8ff"
          strokeWidth="0.1"
          filter={`url(#${textGlowFilterId})`}
          initial={{ opacity: 0, x: 0 }}
          animate={oadingGlowAnimation}
        >
          oading
        </motion.text>

        <motion.text
          x={centerX}
          y={centerY}
          fontSize="5"
          fontWeight="900"
          fontFamily="Arial, sans-serif"
          dominantBaseline="middle"
          fill="white"
          initial={{ opacity: 0, x: 0 }}
          animate={oadingAnimation}
        >
          oading
        </motion.text>

        {[0, 1, 2].map((dotIndex) => (
          <motion.text
            key={dotIndex}
            x={centerX + CONFIG.dotsOffsetX + dotIndex * OADING_ANIMATION.dots.spacing}
            y={centerY}
            fontSize="5"
            fontWeight="900"
            fontFamily="Arial, sans-serif"
            dominantBaseline="middle"
            fill="white"
            initial={{ opacity: 0, x: 0 }}
            animate={getDotAnimation(dotIndex)}
          >
            .
          </motion.text>
        ))}

        <motion.text
          x={centerX}
          y={centerY + 7.8}
          fontSize="1.4"
          fontWeight="600"
          fontFamily="Arial, sans-serif"
          letterSpacing="0.08em"
          textAnchor="middle"
          dominantBaseline="middle"
          fill="#cfe7ef"
          stroke="#b6d7e4"
          strokeWidth="0.03"
          filter={`url(#${textGlowFilterId})`}
          initial={{ opacity: 0, y: 1.2 }}
          animate={loadingCaptionGlowAnimation}
        >
          加载中
        </motion.text>

        <motion.text
          x={centerX}
          y={centerY + 7.8}
          fontSize="1.4"
          fontWeight="600"
          fontFamily="Arial, sans-serif"
          letterSpacing="0.08em"
          textAnchor="middle"
          dominantBaseline="middle"
          fill="#d7eaee"
          initial={{ opacity: 0, y: 1.2 }}
          animate={loadingCaptionAnimation}
        >
          加载中
        </motion.text>
      </svg>
    </motion.div>
  );
}
