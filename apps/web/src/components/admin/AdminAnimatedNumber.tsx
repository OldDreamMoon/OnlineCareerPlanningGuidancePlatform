import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";

type ParsedNumericValue = {
  prefix: string;
  suffix: string;
  value: number;
  decimals: number;
};

type AdminAnimatedNumberProps = {
  value: number | string;
  className?: string;
  durationMs?: number;
};

const numericValuePattern = /^([^0-9+\-]*)([-+]?\d[\d,]*(?:\.\d+)?)(.*)$/u;

function joinClassNames(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function prefersReducedMotion() {
  return typeof window !== "undefined"
    && typeof window.matchMedia === "function"
    && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

function parseNumericValue(value: number | string): ParsedNumericValue | null {
  if (typeof value === "number") {
    return {
      prefix: "",
      suffix: "",
      value,
      decimals: Number.isInteger(value) ? 0 : String(value).split(".")[1]?.length ?? 0,
    };
  }

  const trimmedValue = value.trim();
  const matched = trimmedValue.match(numericValuePattern);
  if (!matched) {
    return null;
  }

  const [, prefix, rawNumber, suffix] = matched;
  const numericValue = Number(rawNumber.replace(/,/g, ""));
  if (!Number.isFinite(numericValue)) {
    return null;
  }

  return {
    prefix,
    suffix,
    value: numericValue,
    decimals: rawNumber.includes(".") ? rawNumber.split(".")[1]?.length ?? 0 : 0,
  };
}

function formatAnimatedValue(parsedValue: ParsedNumericValue, currentValue: number) {
  const formatter = new Intl.NumberFormat("zh-CN", {
    minimumFractionDigits: parsedValue.decimals,
    maximumFractionDigits: parsedValue.decimals,
  });
  return `${parsedValue.prefix}${formatter.format(currentValue)}${parsedValue.suffix}`;
}

export function canAnimateAdminValue(value: ReactNode): value is number | string {
  return (typeof value === "number" || typeof value === "string") && parseNumericValue(value) !== null;
}

export function AdminAnimatedNumber({
  value,
  className,
  durationMs = 360,
}: AdminAnimatedNumberProps) {
  const parsedValue = useMemo(() => parseNumericValue(value), [value]);
  const [displayValue, setDisplayValue] = useState(() => {
    if (!parsedValue) {
      return String(value);
    }
    return formatAnimatedValue(parsedValue, parsedValue.value);
  });
  const previousValueRef = useRef(parsedValue?.value ?? null);

  useEffect(() => {
    if (!parsedValue) {
      previousValueRef.current = null;
      setDisplayValue(String(value));
      return;
    }

    const nextValue = parsedValue.value;
    const previousValue = previousValueRef.current;
    previousValueRef.current = nextValue;

    if (previousValue === null || !Number.isFinite(previousValue) || prefersReducedMotion()) {
      setDisplayValue(formatAnimatedValue(parsedValue, nextValue));
      return;
    }

    if (Math.abs(nextValue - previousValue) < Number.EPSILON) {
      setDisplayValue(formatAnimatedValue(parsedValue, nextValue));
      return;
    }

    let animationFrameId = 0;
    const animationStartAt = performance.now();

    const animate = (currentTime: number) => {
      const progress = Math.min(1, (currentTime - animationStartAt) / durationMs);
      const easedProgress = 1 - (1 - progress) ** 3;
      const currentAnimatedValue = previousValue + (nextValue - previousValue) * easedProgress;
      setDisplayValue(formatAnimatedValue(parsedValue, currentAnimatedValue));

      if (progress < 1) {
        animationFrameId = window.requestAnimationFrame(animate);
      }
    };

    animationFrameId = window.requestAnimationFrame(animate);
    return () => {
      window.cancelAnimationFrame(animationFrameId);
    };
  }, [durationMs, parsedValue, value]);

  return (
    <span className={joinClassNames("inline-flex transition-[opacity,transform] duration-300 ease-out", className)}>
      {displayValue}
    </span>
  );
}
