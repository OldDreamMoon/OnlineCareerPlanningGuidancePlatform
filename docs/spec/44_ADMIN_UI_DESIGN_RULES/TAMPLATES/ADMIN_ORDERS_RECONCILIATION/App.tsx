import React from 'react';
import {
  Search, Bell, Settings, LayoutDashboard, ClipboardCheck, Wallet, Receipt, Settings2,
  Download, RefreshCw, AlertTriangle, ShieldCheck, Tags, Clock, History, CreditCard,
  Landmark, MoreVertical, ChevronLeft, ChevronRight, Bot
} from 'lucide-react';

const TopNav = () => (
  <header className="fixed top-0 w-full z-50 bg-white/70 backdrop-blur-xl shadow-[0_10px_40px_rgba(44,47,49,0.06)] font-headline">
    <div className="flex justify-between items-center h-16 px-6 w-full">
      <div className="flex items-center gap-8">
        <span className="text-xl font-bold tracking-tight text-on-surface">交易中枢管理</span>
        <div className="hidden md:flex gap-6 items-center">
          <nav className="flex gap-6 font-body text-sm">
            <a className="text-on-surface-variant hover:text-primary transition-colors" href="#">首页</a>
            <a className="text-primary font-semibold border-b-2 border-primary pb-1 transition-colors" href="#">对账管理</a>
            <a className="text-on-surface-variant hover:text-primary transition-colors" href="#">统计报表</a>
          </nav>
        </div>
      </div>
      <div className="flex items-center gap-4">
        <div className="relative hidden sm:block">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-outline w-4 h-4" />
          <input
            className="pl-10 pr-4 py-2 bg-surface-container-low border-none rounded-full text-sm focus:ring-2 focus:ring-primary/20 w-64 outline-none font-body transition-all focus:bg-surface-container-lowest"
            placeholder="搜索订单或交易号..."
            type="text"
          />
        </div>
        <div className="flex items-center gap-2">
          <button className="p-2 text-on-surface-variant hover:bg-surface-container-low rounded-full transition-all">
            <Bell className="w-5 h-5" />
          </button>
          <button className="p-2 text-on-surface-variant hover:bg-surface-container-low rounded-full transition-all">
            <Settings className="w-5 h-5" />
          </button>
          <div className="h-8 w-8 rounded-full overflow-hidden ml-2 border border-outline-variant">
            <img
              alt="User Avatar"
              className="w-full h-full object-cover"
              src="https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?ixlib=rb-1.2.1&auto=format&fit=facearea&facepad=2&w=256&h=256&q=80"
              referrerPolicy="no-referrer"
            />
          </div>
        </div>
      </div>
    </div>
  </header>
);

const SideNav = () => (
  <aside className="fixed left-0 h-screen w-64 bg-surface flex flex-col gap-2 p-4 pt-4 overflow-y-auto z-40 border-r border-outline-variant">
    <div className="mb-8 px-2 mt-4">
      <h2 className="text-lg font-black font-headline text-on-surface">交易管理系统</h2>
      <p className="text-[10px] uppercase tracking-widest text-outline font-body mt-1">Transaction Hub</p>
    </div>
    <nav className="flex flex-col gap-2 font-body">
      <a className="flex items-center gap-3 px-4 py-3 text-on-surface-variant hover:bg-surface-container-low hover:translate-x-1 transition-all rounded-xl" href="#">
        <LayoutDashboard className="w-5 h-5" />
        <span className="font-medium text-sm">概览</span>
      </a>
      <a className="flex items-center gap-3 px-4 py-3 text-on-surface-variant hover:bg-surface-container-low hover:translate-x-1 transition-all rounded-xl" href="#">
        <ClipboardCheck className="w-5 h-5" />
        <span className="font-medium text-sm">售后审核</span>
      </a>
      <a className="flex items-center gap-3 px-4 py-3 bg-surface-container-lowest text-primary rounded-xl shadow-[0_4px_20px_rgba(44,47,49,0.04)] font-semibold transition-all relative overflow-hidden" href="#">
        <div className="absolute left-0 top-0 bottom-0 w-1 bg-primary rounded-r-full"></div>
        <Wallet className="w-5 h-5" />
        <span className="font-medium text-sm">支付对账</span>
      </a>
      <a className="flex items-center gap-3 px-4 py-3 text-on-surface-variant hover:bg-surface-container-low hover:translate-x-1 transition-all rounded-xl" href="#">
        <Receipt className="w-5 h-5" />
        <span className="font-medium text-sm">交易流水</span>
      </a>
      <a className="flex items-center gap-3 px-4 py-3 text-on-surface-variant hover:bg-surface-container-low hover:translate-x-1 transition-all rounded-xl" href="#">
        <Settings2 className="w-5 h-5" />
        <span className="font-medium text-sm">系统设置</span>
      </a>
    </nav>
    <div className="mt-auto p-4 bg-primary-container/50 rounded-xl border border-primary/10">
      <p className="text-xs font-semibold text-primary mb-2 font-body">系统状态</p>
      <div className="flex items-center gap-2">
        <span className="h-2 w-2 rounded-full bg-secondary shadow-[0_0_8px_rgba(16,185,129,0.6)]"></span>
        <span className="text-xs text-on-surface-variant font-body">所有服务运行正常</span>
      </div>
    </div>
  </aside>
);

const StatCard = ({ title, value, icon: Icon, colorClass, bgColorClass, badgeText, footerIcon: FooterIcon, footerText, decorativeElements }) => (
  <div className="bg-surface-container-lowest p-6 rounded-[1.5rem] shadow-[0_10px_40px_rgba(44,47,49,0.06)] relative overflow-hidden group transition-transform hover:-translate-y-1 duration-300">
    <div className={`absolute top-0 right-0 w-32 h-32 rounded-full -mr-16 -mt-16 group-hover:scale-110 transition-transform duration-500 ${bgColorClass} opacity-20`}></div>
    <div className="flex justify-between items-start mb-6 relative z-10">
      <div className={`p-3 rounded-2xl ${bgColorClass}`}>
        <Icon className={`w-6 h-6 ${colorClass}`} />
      </div>
      {badgeText && (
        <span className={`text-xs font-bold px-3 py-1 rounded-full ${colorClass} ${bgColorClass} bg-opacity-20`}>
          {badgeText}
        </span>
      )}
      {decorativeElements && (
        <div className="flex -space-x-2">
          <div className="w-6 h-6 rounded-full border-2 border-white bg-surface-container-low"></div>
          <div className="w-6 h-6 rounded-full border-2 border-white bg-outline-variant"></div>
        </div>
      )}
    </div>
    <div className="relative z-10">
      <p className="text-sm font-medium text-on-surface-variant mb-2 font-body">{title}</p>
      <h3 className="font-headline text-4xl font-black text-on-surface tracking-tight">{value}</h3>
    </div>
    <div className="mt-6 flex items-center gap-2 text-xs text-outline font-body relative z-10">
      {FooterIcon && <FooterIcon className="w-4 h-4" />}
      {footerText}
    </div>
  </div>
);

const FilterBar = () => (
  <div className="bg-surface-container-low p-6 rounded-[1.5rem] mb-8 flex flex-wrap items-end gap-6">
    <div className="flex-1 min-w-[240px]">
      <label className="block text-xs font-bold text-on-surface-variant uppercase tracking-wider mb-3 font-body">搜索关键词</label>
      <div className="relative">
        <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-outline w-4 h-4" />
        <input
          className="w-full pl-11 pr-4 py-3 bg-surface-container-lowest border-none rounded-xl text-sm focus:ring-2 focus:ring-primary/30 outline-none font-body transition-all shadow-sm"
          placeholder="输入订单号、交易流水号..."
          type="text"
        />
      </div>
    </div>
    <div className="w-56">
      <label className="block text-xs font-bold text-on-surface-variant uppercase tracking-wider mb-3 font-body">订单状态</label>
      <select className="w-full px-4 py-3 bg-surface-container-lowest border-none rounded-xl text-sm focus:ring-2 focus:ring-primary/30 outline-none font-body appearance-none shadow-sm cursor-pointer">
        <option>全部状态</option>
        <option>已支付</option>
        <option>支付中</option>
        <option>已关闭</option>
      </select>
    </div>
    <div className="w-56">
      <label className="block text-xs font-bold text-on-surface-variant uppercase tracking-wider mb-3 font-body">对账状态</label>
      <select className="w-full px-4 py-3 bg-surface-container-lowest border-none rounded-xl text-sm focus:ring-2 focus:ring-primary/30 outline-none font-body appearance-none shadow-sm cursor-pointer">
        <option>异常未处理</option>
        <option>诊断中</option>
        <option>对账一致</option>
        <option>手动补单</option>
      </select>
    </div>
    <div>
      <button className="px-6 py-3 bg-primary-container/50 text-primary hover:bg-primary-container rounded-xl text-sm font-bold transition-all font-body">
        重置筛选
      </button>
    </div>
  </div>
);

const DataTable = () => {
  const data = [
    {
      id: 'ORD-20240915-001',
      date: '2024-09-15 14:22:10',
      amount: '¥ 1,299.00',
      channel: 'Alipay',
      channelIcon: <CreditCard className="w-4 h-4 text-blue-500" />,
      channelBg: 'bg-blue-50',
      payStatus: '成功',
      payStatusColor: 'text-secondary bg-secondary-container',
      reconStatus: '对账不一致',
      reconColor: 'text-error',
      reconDot: 'bg-error',
      tag: '金额差异',
      tagColor: 'text-error bg-error-container',
      actionText: '人工对账'
    },
    {
      id: 'ORD-20240915-084',
      date: '2024-09-15 15:45:32',
      amount: '¥ 88.50',
      channel: 'WeChat Pay',
      channelIcon: <Landmark className="w-4 h-4 text-green-500" />,
      channelBg: 'bg-green-50',
      payStatus: '处理中',
      payStatusColor: 'text-on-surface-variant bg-surface-container-low',
      reconStatus: '等待渠道回调',
      reconColor: 'text-tertiary',
      reconDot: 'bg-tertiary',
      tag: '超时风险',
      tagColor: 'text-tertiary bg-tertiary-container',
      actionText: '核销处理'
    },
    {
      id: 'ORD-20240915-112',
      date: '2024-09-15 16:10:05',
      amount: '¥ 4,500.00',
      channel: 'UnionPay',
      channelIcon: <CreditCard className="w-4 h-4 text-slate-500" />,
      channelBg: 'bg-slate-100',
      payStatus: '失败',
      payStatusColor: 'text-error bg-error-container',
      reconStatus: '单边账(渠道有)',
      reconColor: 'text-error',
      reconDot: 'bg-error',
      tag: '漏单异常',
      tagColor: 'text-error bg-error-container',
      actionText: '手工补单'
    }
  ];

  return (
    <div className="bg-surface-container-lowest rounded-[1.5rem] shadow-[0_10px_40px_rgba(44,47,49,0.06)] overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse font-body">
          <thead>
            <tr className="bg-surface-container-lowest border-b border-surface-container-low">
              <th className="px-8 py-6 text-xs font-bold text-on-surface-variant uppercase tracking-wider">订单号</th>
              <th className="px-6 py-6 text-xs font-bold text-on-surface-variant uppercase tracking-wider">金额</th>
              <th className="px-6 py-6 text-xs font-bold text-on-surface-variant uppercase tracking-wider">支付渠道</th>
              <th className="px-6 py-6 text-xs font-bold text-on-surface-variant uppercase tracking-wider">支付状态</th>
              <th className="px-6 py-6 text-xs font-bold text-on-surface-variant uppercase tracking-wider">对账状态</th>
              <th className="px-6 py-6 text-xs font-bold text-on-surface-variant uppercase tracking-wider">异常标签</th>
              <th className="px-8 py-6 text-xs font-bold text-on-surface-variant uppercase tracking-wider text-right">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-surface-container-low">
            {data.map((row, index) => (
              <tr key={index} className="hover:bg-surface-container-low/50 transition-colors group">
                <td className="px-8 py-6">
                  <div className="font-headline font-bold text-sm text-on-surface">{row.id}</div>
                  <div className="text-xs text-outline mt-1">{row.date}</div>
                </td>
                <td className="px-6 py-6">
                  <div className="font-headline font-black text-base text-on-surface">{row.amount}</div>
                </td>
                <td className="px-6 py-6">
                  <div className="flex items-center gap-3">
                    <div className={`w-8 h-8 rounded-lg ${row.channelBg} flex items-center justify-center`}>
                      {row.channelIcon}
                    </div>
                    <span className="text-sm font-medium text-on-surface">{row.channel}</span>
                  </div>
                </td>
                <td className="px-6 py-6">
                  <span className={`px-3 py-1.5 ${row.payStatusColor} bg-opacity-30 text-xs font-bold rounded-lg`}>
                    {row.payStatus}
                  </span>
                </td>
                <td className="px-6 py-6">
                  <div className="flex items-center gap-2">
                    <span className={`h-2 w-2 rounded-full ${row.reconDot}`}></span>
                    <span className={`text-sm font-semibold ${row.reconColor}`}>{row.reconStatus}</span>
                  </div>
                </td>
                <td className="px-6 py-6">
                  <span className={`px-2.5 py-1 ${row.tagColor} bg-opacity-20 text-xs font-bold rounded-md`}>
                    {row.tag}
                  </span>
                </td>
                <td className="px-8 py-6 text-right">
                  <div className="flex justify-end items-center gap-3 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button className="px-4 py-2 bg-primary-container/30 text-primary text-xs font-bold rounded-lg hover:bg-primary hover:text-white transition-all">
                      {row.actionText}
                    </button>
                    <button className="p-2 text-on-surface-variant hover:bg-surface-container-low rounded-lg transition-colors">
                      <MoreVertical className="w-5 h-5" />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {/* Pagination */}
      <div className="px-8 py-6 border-t border-surface-container-low flex justify-between items-center bg-surface-container-lowest">
        <p className="text-sm text-on-surface-variant font-body">显示 1-10 条，共 128 条异常记录</p>
        <div className="flex items-center gap-2 font-body">
          <button className="p-2 rounded-lg hover:bg-surface-container-low disabled:opacity-30 transition-colors" disabled>
            <ChevronLeft className="w-5 h-5 text-on-surface-variant" />
          </button>
          <button className="w-10 h-10 rounded-lg bg-primary text-white text-sm font-bold shadow-md shadow-primary/20">1</button>
          <button className="w-10 h-10 rounded-lg hover:bg-surface-container-low text-on-surface text-sm font-bold transition-colors">2</button>
          <button className="w-10 h-10 rounded-lg hover:bg-surface-container-low text-on-surface text-sm font-bold transition-colors">3</button>
          <span className="px-2 text-outline">...</span>
          <button className="w-10 h-10 rounded-lg hover:bg-surface-container-low text-on-surface text-sm font-bold transition-colors">13</button>
          <button className="p-2 rounded-lg hover:bg-surface-container-low transition-colors">
            <ChevronRight className="w-5 h-5 text-on-surface-variant" />
          </button>
        </div>
      </div>
    </div>
  );
};

export default function App() {
  return (
    <div className="flex min-h-screen pt-16 bg-surface">
      <TopNav />
      <SideNav />
      
      <main className="ml-64 flex-1 p-10 max-w-[1600px] mx-auto w-full">
        {/* Header Section */}
        <div className="mb-10 flex justify-between items-end">
          <div>
            <h1 className="font-headline text-4xl font-extrabold tracking-tight text-on-surface mb-3">支付异常对账</h1>
            <p className="text-on-surface-variant max-w-2xl font-body leading-relaxed">
              实时监控支付链路，识别并处理对账差异。通过人工诊断模块确保每一笔交易的合规性与准确性。
            </p>
          </div>
          <div className="flex gap-4">
            <button className="flex items-center gap-2 px-5 py-3 bg-surface-container-lowest rounded-xl text-sm font-bold text-on-surface hover:bg-surface-container-low transition-all shadow-sm">
              <Download className="w-4 h-4" />
              导出报表
            </button>
            <button className="flex items-center gap-2 px-6 py-3 bg-gradient-to-br from-primary to-primary-dim text-on-primary rounded-xl text-sm font-bold shadow-lg shadow-primary/30 hover:shadow-xl hover:-translate-y-0.5 active:translate-y-0 transition-all">
              <RefreshCw className="w-4 h-4" />
              开始对账
            </button>
          </div>
        </div>

        {/* Bento Statistics Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 mb-10">
          <StatCard
            title="异常待处理"
            value="128"
            icon={AlertTriangle}
            colorClass="text-error"
            bgColorClass="bg-error"
            badgeText="+12.5%"
            footerIcon={Clock}
            footerText="最近更新：5分钟前"
          />
          <StatCard
            title="已人工处理"
            value="2,456"
            icon={ShieldCheck}
            colorClass="text-secondary"
            bgColorClass="bg-secondary"
            badgeText="94% 完成率"
            footerIcon={History}
            footerText="累计处理总数"
          />
          <StatCard
            title="命中异常标签"
            value="42"
            icon={Tags}
            colorClass="text-tertiary"
            bgColorClass="bg-tertiary"
            decorativeElements={true}
            footerText={
              <div className="flex flex-wrap gap-2 mt-1">
                <span className="px-3 py-1 bg-surface-container-low text-xs rounded-full text-on-surface-variant font-medium">金额不符</span>
                <span className="px-3 py-1 bg-surface-container-low text-xs rounded-full text-on-surface-variant font-medium">延迟到账</span>
                <span className="px-3 py-1 bg-surface-container-low text-xs rounded-full text-on-surface-variant font-medium">重复支付</span>
              </div>
            }
          />
        </div>

        <FilterBar />
        <DataTable />
      </main>

      {/* Floating Assistant */}
      <div className="fixed bottom-10 right-10 z-[60]">
        <button className="w-16 h-16 rounded-full bg-gradient-to-br from-primary to-primary-dim shadow-[0_10px_30px_rgba(99,219,242,0.4)] flex items-center justify-center text-on-primary hover:scale-110 active:scale-95 transition-all">
          <Bot className="w-8 h-8" />
        </button>
      </div>
    </div>
  );
}
