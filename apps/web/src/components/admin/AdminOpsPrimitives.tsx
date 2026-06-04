import { Empty, Switch, Typography } from "antd";
import type { LucideIcon } from "lucide-react";
import type { ComponentProps, ReactNode } from "react";
import { AdminAnimatedNumber, canAnimateAdminValue } from "./AdminAnimatedNumber";

const { Paragraph } = Typography;

export type AdminAccentTone =
  | "indigo"
  | "sky"
  | "emerald"
  | "amber"
  | "rose"
  | "violet"
  | "teal"
  | "slate";

type PageHeaderProps = {
  sectionLabel?: string;
  title: string;
  description: ReactNode;
  actions?: ReactNode;
  tone?: AdminAccentTone;
};

type MetricCardProps = {
  icon: LucideIcon;
  label: string;
  value: ReactNode;
  note: ReactNode;
  badge?: string;
  tone?: AdminAccentTone;
};

type SurfaceCardProps = {
  title?: ReactNode;
  description?: ReactNode;
  extra?: ReactNode;
  className?: string;
  bodyClassName?: string;
  children: ReactNode;
};

const toneClassMap: Record<AdminAccentTone, {
  sectionLabel: string;
  card: string;
  icon: string;
  badge: string;
  line: string;
}> = {
  indigo: {
    sectionLabel: "bg-indigo-100 text-indigo-700",
    card: "border-indigo-100 bg-[linear-gradient(180deg,rgba(99,102,241,0.08),rgba(255,255,255,0.98))]",
    icon: "bg-indigo-100 text-indigo-600",
    badge: "bg-indigo-100 text-indigo-700",
    line: "from-indigo-400 to-indigo-500",
  },
  sky: {
    sectionLabel: "bg-sky-100 text-sky-700",
    card: "border-sky-100 bg-sky-50/80",
    icon: "bg-sky-100 text-sky-600",
    badge: "bg-sky-100 text-sky-700",
    line: "from-sky-400 to-cyan-400",
  },
  emerald: {
    sectionLabel: "bg-emerald-100 text-emerald-700",
    card: "border-emerald-100 bg-emerald-50/80",
    icon: "bg-emerald-100 text-emerald-600",
    badge: "bg-emerald-100 text-emerald-700",
    line: "from-emerald-400 to-emerald-500",
  },
  amber: {
    sectionLabel: "bg-amber-100 text-amber-700",
    card: "border-amber-100 bg-amber-50/85",
    icon: "bg-amber-100 text-amber-600",
    badge: "bg-amber-100 text-amber-700",
    line: "from-amber-400 to-orange-400",
  },
  rose: {
    sectionLabel: "bg-rose-100 text-rose-700",
    card: "border-rose-100 bg-rose-50/80",
    icon: "bg-rose-100 text-rose-600",
    badge: "bg-rose-100 text-rose-700",
    line: "from-rose-400 to-rose-500",
  },
  violet: {
    sectionLabel: "bg-violet-100 text-violet-700",
    card: "border-violet-100 bg-violet-50/80",
    icon: "bg-violet-100 text-violet-600",
    badge: "bg-violet-100 text-violet-700",
    line: "from-violet-400 to-fuchsia-400",
  },
  teal: {
    sectionLabel: "bg-teal-100 text-teal-700",
    card: "border-teal-100 bg-teal-50/80",
    icon: "bg-teal-100 text-teal-600",
    badge: "bg-teal-100 text-teal-700",
    line: "from-teal-400 to-cyan-400",
  },
  slate: {
    sectionLabel: "bg-slate-100 text-slate-700",
    card: "border-slate-200 bg-slate-50/95",
    icon: "bg-slate-200 text-slate-700",
    badge: "bg-slate-200 text-slate-700",
    line: "from-slate-400 to-slate-500",
  },
};

export function joinAdminClassNames(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function renderAnimatedMetricValue(value: ReactNode) {
  if (!canAnimateAdminValue(value)) {
    return value;
  }

  return <AdminAnimatedNumber value={value} />;
}

export function AdminPageFrame({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={joinAdminClassNames("admin-page-frame page-enter-float mx-auto w-full max-w-[1600px] space-y-8 pb-8", className)}>
      {children}
    </div>
  );
}

export function AdminPageHeader({
  sectionLabel: _sectionLabel,
  title,
  description,
  actions,
  tone: _tone = "indigo",
}: PageHeaderProps) {
  return (
    <section className="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
      <div className="max-w-4xl">
        <div className="admin-typography-hero-title font-['Manrope'] text-slate-950">
          {title}
        </div>
        <Paragraph className="admin-typography-hero-description !mb-0 !mt-2 !max-w-3xl !text-slate-500">
          {description}
        </Paragraph>
      </div>
      {actions ? <div className="flex flex-wrap gap-3">{actions}</div> : null}
    </section>
  );
}

export function AdminMetricCard({
  icon: Icon,
  label,
  value,
  note,
  badge: _badge = "平台巡检",
  tone = "indigo",
}: MetricCardProps) {
  const palette = toneClassMap[tone];

  return (
    <div className={joinAdminClassNames("relative overflow-hidden rounded-[28px] border p-6 shadow-none", palette.card)}>
      <div className={joinAdminClassNames("absolute inset-x-0 top-0 h-1 bg-gradient-to-r", palette.line)} />
      <div className="flex items-center gap-4">
        <div className={joinAdminClassNames("flex h-12 w-12 items-center justify-center rounded-2xl", palette.icon)}>
          <Icon size={20} />
        </div>
        <div className="admin-typography-card-title min-w-0 text-slate-700">{label}</div>
      </div>
      <div className="admin-typography-card-value mt-5 font-['Manrope'] text-slate-950">{renderAnimatedMetricValue(value)}</div>
      <div className="admin-typography-card-note mt-2 text-slate-500">{note}</div>
    </div>
  );
}

export function AdminSurfaceCard({
  title,
  description,
  extra,
  className,
  bodyClassName,
  children,
}: SurfaceCardProps) {
  return (
    <section className={joinAdminClassNames("overflow-hidden rounded-[30px] border border-slate-200/70 bg-white shadow-none", className)}>
      {(title || description || extra) ? (
        <div className="flex flex-col gap-3 border-b border-slate-200/70 px-6 py-5 lg:flex-row lg:items-start lg:justify-between">
          <div>
            {title ? <div className="admin-typography-surface-title font-['Manrope'] text-slate-900">{title}</div> : null}
            {description ? <div className="admin-typography-surface-description mt-1 text-slate-400">{description}</div> : null}
          </div>
          {extra ? <div className="flex shrink-0 flex-wrap gap-3">{extra}</div> : null}
        </div>
      ) : null}
      <div className={joinAdminClassNames("p-6", bodyClassName)}>{children}</div>
    </section>
  );
}

export function AdminFilterBar({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={joinAdminClassNames("rounded-[28px] border border-slate-200/70 bg-slate-100/95 p-4 shadow-none", className)}>
      <div className="flex flex-wrap items-center gap-3">{children}</div>
    </div>
  );
}

export function AdminMiniStat({
  label,
  value,
  className,
}: {
  label: string;
  value: ReactNode;
  className?: string;
}) {
  return (
    <div className={joinAdminClassNames("rounded-2xl bg-slate-50 px-4 py-4", className)}>
      <div className="admin-typography-mini-label text-slate-400">{label}</div>
      <div className="admin-typography-mini-value mt-2 text-slate-900">{renderAnimatedMetricValue(value)}</div>
    </div>
  );
}

type AdminSettingSwitchProps = ComponentProps<typeof Switch>;

export function AdminSettingSwitch({
  checked = false,
  className,
  style,
  ...props
}: AdminSettingSwitchProps) {
  return (
    <Switch
      {...props}
      checked={checked}
      className={joinAdminClassNames("admin-setting-switch", className)}
      style={{
        background: checked ? "linear-gradient(135deg, #4647d3 0%, #5b5cf0 100%)" : "rgba(148, 163, 184, 0.34)",
        ...style,
      }}
    />
  );
}

export function AdminDetailPlaceholder({
  description = "请选择一项查看详情",
}: {
  description?: string;
}) {
  return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={description} />;
}
