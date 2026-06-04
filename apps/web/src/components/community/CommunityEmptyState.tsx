import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";

type CommunityEmptyStateProps = {
  icon: LucideIcon;
  title: string;
  description: string;
  action?: ReactNode;
};

export default function CommunityEmptyState({
  icon: Icon,
  title,
  description,
  action,
}: CommunityEmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center rounded-[1.8rem] border border-slate-100 bg-white px-6 py-14 text-center shadow-sm">
      <div className="flex h-16 w-16 items-center justify-center rounded-full bg-slate-100 text-slate-400">
        <Icon size={28} />
      </div>
      <h3 className="mt-5 text-xl font-bold text-slate-900">{title}</h3>
      <p className="mt-2 max-w-md text-base leading-8 text-slate-500">{description}</p>
      {action ? <div className="mt-6">{action}</div> : null}
    </div>
  );
}
