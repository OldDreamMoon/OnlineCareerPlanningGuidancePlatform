import { Button, Tag, Typography } from "antd";
import { ArrowRight, Layers3 } from "lucide-react";
import { useNavigate } from "react-router-dom";

const { Paragraph } = Typography;

type AdminPlaceholderPageProps = {
  title: string;
  route: string;
  description?: string;
};

export default function AdminPlaceholderPage({
  title,
  route,
  description = "该治理域正在按新的后台设计语言逐步建设，当前暂不开放具体功能内容。",
}: AdminPlaceholderPageProps) {
  const navigate = useNavigate();

  return (
    <div className="admin-page-frame page-enter-float mx-auto w-full max-w-[1600px]">
      <div className="flex min-h-[calc(100vh-180px)] items-center justify-center">
        <div className="relative w-full max-w-4xl overflow-hidden rounded-[40px] border border-white/60 bg-white/78 p-10 shadow-[0_30px_90px_rgba(15,23,42,0.12)] backdrop-blur-xl">
          <div className="pointer-events-none absolute right-[-6rem] top-[-6rem] h-60 w-60 rounded-full bg-[radial-gradient(circle,_rgba(91,97,246,0.18)_0%,_rgba(91,97,246,0.04)_58%,_rgba(255,255,255,0)_78%)] blur-3xl" />
          <div className="pointer-events-none absolute bottom-[-5rem] left-[-4rem] h-56 w-56 rounded-full bg-[radial-gradient(circle,_rgba(16,185,129,0.14)_0%,_rgba(16,185,129,0.05)_54%,_rgba(255,255,255,0)_78%)] blur-3xl" />

          <div className="relative">
            <Tag bordered={false} className="m-0 rounded-full bg-indigo-50 px-3 py-1 text-[11px] font-bold text-indigo-700">
              {route}
            </Tag>

            <div className="mt-6 flex h-20 w-20 items-center justify-center rounded-[28px] bg-gradient-to-br from-slate-950 via-indigo-950 to-indigo-700 text-white shadow-[0_20px_55px_rgba(37,47,107,0.32)]">
              <Layers3 size={34} />
            </div>

            <div className="mt-7 max-w-2xl font-['Manrope'] text-[2.6rem] font-extrabold leading-[1.02] tracking-[-0.05em] text-slate-950">
              {title}
            </div>
            <Paragraph className="!mb-0 !mt-4 !max-w-2xl !text-base !leading-8 !text-slate-500">
              {description}
            </Paragraph>

            <div className="mt-8 flex flex-wrap items-center gap-3">
              <Tag bordered={false} className="m-0 rounded-full bg-slate-100 px-3 py-1 text-slate-600">
                页面状态：建设中
              </Tag>
              <Tag bordered={false} className="m-0 rounded-full bg-emerald-50 px-3 py-1 text-emerald-700">
                旧版入口：已下线
              </Tag>
            </div>

            <div className="mt-10 grid gap-4 md:grid-cols-2">
              <div className="rounded-[28px] border border-slate-200/80 bg-slate-50/90 p-6">
                <div className="text-[11px] font-bold text-slate-400">页面说明</div>
                <div className="mt-3 text-base font-semibold text-slate-900">当前先保留统一壳层与导航入口</div>
                <div className="mt-2 text-sm leading-7 text-slate-500">
                  后续开放时会直接接入正式页面内容，并保持当前后台的统一视觉与交互风格。
                </div>
              </div>

              <div className="rounded-[28px] border border-indigo-100 bg-gradient-to-br from-indigo-50 via-white to-cyan-50 p-6">
                <div className="text-[11px] font-bold text-indigo-500">当前已开放</div>
                <div className="mt-3 text-base font-semibold text-slate-900">总览页与用户页已先行上线</div>
                <div className="mt-2 text-sm leading-7 text-slate-500">
                  可以先从这些已开放页面查看新的后台视觉、信息密度与交互基线。
                </div>
              </div>
            </div>

            <div className="mt-10 flex flex-wrap gap-3">
              <Button type="primary" size="large" className="rounded-2xl" onClick={() => navigate("/admin/dashboard")}>
                返回总览
                <ArrowRight size={16} className="ml-2" />
              </Button>
              <Button size="large" className="rounded-2xl" onClick={() => navigate("/admin/users")}>
                打开用户页
              </Button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
