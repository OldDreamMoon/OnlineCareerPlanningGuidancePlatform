import { AlertTriangle, CheckCircle2, Info, Lock, ShieldAlert } from "lucide-react";

type CommunityModerationBannerProps = {
  moderationStatus?: string | null;
  resolvedStatus?: string | null;
  riskLevel?: string | null;
};

export default function CommunityModerationBanner({
  moderationStatus,
  resolvedStatus,
  riskLevel,
}: CommunityModerationBannerProps) {
  const normalizedModerationStatus = (moderationStatus ?? "").toUpperCase();
  const normalizedResolvedStatus = (resolvedStatus ?? "").toUpperCase();

  if (normalizedModerationStatus === "REVIEW") {
    return (
      <div className="flex items-start gap-3 rounded-[1.4rem] border border-amber-200 bg-amber-50 px-4 py-4 text-[15px] leading-8 text-amber-800">
        <AlertTriangle size={18} className="mt-1 shrink-0" />
        <div>
          <div className="font-semibold">该内容正在等待审核</div>
          <div className="text-amber-700/90">
            审核完成前只有作者本人可见，社区主内容流中暂不公开展示。
          </div>
        </div>
      </div>
    );
  }

  if (normalizedModerationStatus === "BLOCK") {
    return (
      <div className="flex items-start gap-3 rounded-[1.4rem] border border-rose-200 bg-rose-50 px-4 py-4 text-[15px] leading-8 text-rose-800">
        <ShieldAlert size={18} className="mt-1 shrink-0" />
        <div>
          <div className="font-semibold">该内容当前处于受限展示状态</div>
          <div className="text-rose-700/90">
            平台已限制这条内容的公开曝光，你仍可在这里查看治理结果和后续处理建议。
          </div>
        </div>
      </div>
    );
  }

  if (normalizedResolvedStatus === "CLOSED") {
    return (
      <div className="flex items-start gap-3 rounded-[1.4rem] border border-slate-200 bg-slate-100 px-4 py-4 text-[15px] leading-8 text-slate-700">
        <Lock size={18} className="mt-1 shrink-0" />
        <div>
          <div className="font-semibold">讨论已关闭</div>
          <div>当前帖子不再接收新的回复，但仍可继续回看上下文与历史建议。</div>
        </div>
      </div>
    );
  }

  if (normalizedResolvedStatus === "RESOLVED") {
    return (
      <div className="flex items-start gap-3 rounded-[1.4rem] border border-emerald-200 bg-emerald-50 px-4 py-4 text-[15px] leading-8 text-emerald-800">
        <CheckCircle2 size={18} className="mt-1 shrink-0" />
        <div>
          <div className="font-semibold">题主已标记为已解决</div>
          <div>这条讨论已经形成可参考结论，后续回复建议以补充经验和延展视角为主。</div>
        </div>
      </div>
    );
  }

  if (riskLevel && riskLevel !== "LOW") {
    return (
      <div className="flex items-start gap-3 rounded-[1.4rem] border border-sky-200 bg-sky-50 px-4 py-4 text-[15px] leading-8 text-sky-800">
        <Info size={18} className="mt-1 shrink-0" />
        <div>
          <div className="font-semibold">系统提示</div>
          <div>当前内容包含 {riskLevel} 风险标记，阅读和引用时请结合实际情况再做判断。</div>
        </div>
      </div>
    );
  }

  return null;
}
