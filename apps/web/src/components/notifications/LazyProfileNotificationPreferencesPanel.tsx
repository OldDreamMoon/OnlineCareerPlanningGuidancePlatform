import { lazy, Suspense } from "react";

const ProfileNotificationPreferencesPanel = lazy(() => import("./ProfileNotificationPreferencesPanel"));

type LazyProfileNotificationPreferencesPanelProps = {
  variant: "student" | "mentor" | "enterprise";
  title?: string;
  description?: string;
};

function ProfileNotificationPreferencesPanelFallback() {
  return (
    <section className="overflow-hidden rounded-[2rem] border border-slate-200/80 bg-white/90 shadow-[0_18px_48px_rgba(148,163,184,0.14)]">
      <div className="border-b border-slate-200/75 bg-slate-50/80 px-6 py-5">
        <div className="h-4 w-28 rounded-full bg-slate-200/80" />
        <div className="mt-3 h-3 w-3/4 rounded-full bg-slate-200/70" />
      </div>
      <div className="space-y-4 px-6 py-6">
        <div className="h-20 rounded-[1.4rem] border border-slate-200/75 bg-slate-50/70" />
        <div className="h-32 rounded-[1.4rem] border border-slate-200/75 bg-slate-50/60" />
        <div className="h-32 rounded-[1.4rem] border border-slate-200/75 bg-slate-50/60" />
      </div>
    </section>
  );
}

export default function LazyProfileNotificationPreferencesPanel(props: LazyProfileNotificationPreferencesPanelProps) {
  return (
    <Suspense fallback={<ProfileNotificationPreferencesPanelFallback />}>
      <ProfileNotificationPreferencesPanel {...props} />
    </Suspense>
  );
}
