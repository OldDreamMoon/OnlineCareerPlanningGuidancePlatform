export default function RouteSpinnerScreen({ label = "正在加载..." }: { label?: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-white px-6 py-10">
      <div className="flex flex-col items-center gap-3 text-slate-400">
        <div className="h-9 w-9 animate-spin rounded-full border-2 border-slate-200 border-t-slate-900" />
        <span className="text-xs font-medium tracking-[0.18em] text-slate-500">{label}</span>
      </div>
    </div>
  );
}
