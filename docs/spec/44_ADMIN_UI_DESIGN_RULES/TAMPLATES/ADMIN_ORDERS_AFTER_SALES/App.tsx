import Sidebar from './components/Sidebar';
import TopNav from './components/TopNav';

export default function App() {
  return (
    <div className="bg-surface font-body text-on-surface antialiased flex min-h-screen overflow-hidden">
      <Sidebar />
      
      <main className="flex-1 ml-64 flex flex-col h-screen overflow-hidden">
        <TopNav />
        
        <div className="mt-16 p-8 overflow-y-auto flex-1 custom-scrollbar">
          {/* Page Title & Header */}
          <div className="flex justify-between items-end mb-8">
            <div>
              <h2 className="text-3xl font-extrabold font-headline tracking-tight text-on-surface">售后退款审核</h2>
              <p className="text-on-surface-variant mt-1">处理客户售后申请，快速定位异常订单</p>
            </div>
            <div className="flex gap-3">
              <button className="px-5 py-2.5 rounded-xl border border-outline-variant/15 text-sm font-semibold flex items-center gap-2 bg-white hover:bg-surface-container-low transition-all cursor-pointer">
                <span className="material-symbols-outlined text-lg">download</span>
                导出报表
              </button>
              <button className="px-5 py-2.5 rounded-xl bg-primary text-on-primary text-sm font-bold flex items-center gap-2 shadow-lg shadow-primary/20 hover:translate-y-[-2px] transition-all cursor-pointer">
                <span className="material-symbols-outlined text-lg">bolt</span>
                自动处理策略
              </button>
            </div>
          </div>

          {/* Stats Bento Grid */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
            <div className="bg-surface-container-lowest p-6 rounded-xl shadow-[0_10px_40px_rgba(44,47,49,0.06)] flex flex-col justify-between overflow-hidden relative group">
              <div className="z-10">
                <span className="text-on-surface-variant text-xs font-bold tracking-widest uppercase">待审核申请</span>
                <div className="text-4xl font-extrabold font-headline text-on-surface mt-2">128 <span className="text-sm font-medium text-error ml-1">+12%</span></div>
              </div>
              <div className="mt-4 flex items-center gap-2 z-10">
                <div className="w-full bg-surface-container-low h-1.5 rounded-full overflow-hidden">
                  <div className="vibrant-bar h-full w-2/3"></div>
                </div>
                <span className="text-[10px] font-bold text-primary whitespace-nowrap">需优先处理</span>
              </div>
              <span className="material-symbols-outlined absolute -right-4 -bottom-4 text-8xl text-primary/5 group-hover:rotate-12 transition-transform">pending_actions</span>
            </div>
            
            <div className="bg-surface-container-lowest p-6 rounded-xl shadow-[0_10px_40px_rgba(44,47,49,0.06)] flex flex-col justify-between overflow-hidden relative group">
              <div className="z-10">
                <span className="text-on-surface-variant text-xs font-bold tracking-widest uppercase">本页申请金额</span>
                <div className="text-4xl font-extrabold font-headline text-on-surface mt-2">¥42,890.00</div>
              </div>
              <div className="mt-4 flex items-center gap-3 z-10">
                <span className="text-[11px] px-2 py-0.5 rounded bg-secondary-container/20 text-on-secondary-container font-semibold">实付比例 82%</span>
                <span className="text-[11px] px-2 py-0.5 rounded bg-tertiary-container/20 text-on-tertiary-container font-semibold">退款率 1.4%</span>
              </div>
              <span className="material-symbols-outlined absolute -right-4 -bottom-4 text-8xl text-secondary/5 group-hover:rotate-12 transition-transform">payments</span>
            </div>
            
            <div className="bg-primary text-white p-6 rounded-xl shadow-xl shadow-primary/20 flex flex-col justify-between overflow-hidden relative group">
              <div className="z-10">
                <span className="text-primary-container text-xs font-bold tracking-widest uppercase">自动触发状态</span>
                <div className="text-2xl font-bold font-headline mt-2">风控规则运行中</div>
              </div>
              <div className="mt-4 flex items-center gap-2 z-10">
                <div className="flex -space-x-2">
                  <div className="w-6 h-6 rounded-full border-2 border-primary bg-white/20 backdrop-blur-sm"></div>
                  <div className="w-6 h-6 rounded-full border-2 border-primary bg-white/40 backdrop-blur-sm"></div>
                  <div className="w-6 h-6 rounded-full border-2 border-primary bg-white/60 backdrop-blur-sm"></div>
                </div>
                <span className="text-[11px] font-medium opacity-90">32 个订单已被标记</span>
              </div>
              <span className="material-symbols-outlined absolute -right-4 -bottom-4 text-8xl text-white/10 group-hover:rotate-12 transition-transform">verified_user</span>
            </div>
          </div>

          {/* Filter Section */}
          <div className="bg-surface-container-low p-5 rounded-xl mb-6 flex flex-wrap items-center gap-4">
            <div className="flex-1 min-w-[240px] relative">
              <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant">search</span>
              <input 
                className="w-full pl-10 pr-4 py-2.5 bg-surface-container-lowest border-none rounded-xl text-sm focus:ring-2 focus:ring-primary/20 outline-none" 
                placeholder="关键词搜索 (手机号/商品名)..." 
                type="text"
              />
            </div>
            <div className="flex gap-4">
              <select className="pl-4 pr-10 py-2.5 bg-surface-container-lowest border-none rounded-xl text-sm focus:ring-2 focus:ring-primary/20 cursor-pointer appearance-none outline-none">
                <option>审核状态 (全部)</option>
                <option>待处理</option>
                <option>处理中</option>
                <option>已完成</option>
                <option>已驳回</option>
              </select>
              <select className="pl-4 pr-10 py-2.5 bg-surface-container-lowest border-none rounded-xl text-sm focus:ring-2 focus:ring-primary/20 cursor-pointer appearance-none outline-none">
                <option>售后类型 (全部)</option>
                <option>仅退款</option>
                <option>退货退款</option>
                <option>换货</option>
              </select>
            </div>
            <button className="bg-on-surface text-surface-container-lowest px-6 py-2.5 rounded-xl text-sm font-bold hover:opacity-90 transition-all cursor-pointer">
              应用筛选
            </button>
            <button className="p-2.5 text-on-surface-variant hover:bg-white rounded-xl transition-all cursor-pointer">
              <span className="material-symbols-outlined">restart_alt</span>
            </button>
          </div>

          {/* Table Section */}
          <div className="bg-surface-container-lowest rounded-xl shadow-[0_10px_40px_rgba(44,47,49,0.06)] overflow-hidden">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-surface-container-low/50 border-b border-outline-variant/10">
                  <th className="px-6 py-4 text-xs font-bold text-on-surface-variant uppercase tracking-wider">申请单号</th>
                  <th className="px-6 py-4 text-xs font-bold text-on-surface-variant uppercase tracking-wider">订单号</th>
                  <th className="px-6 py-4 text-xs font-bold text-on-surface-variant uppercase tracking-wider">金额</th>
                  <th className="px-6 py-4 text-xs font-bold text-on-surface-variant uppercase tracking-wider">原因</th>
                  <th className="px-6 py-4 text-xs font-bold text-on-surface-variant uppercase tracking-wider">状态</th>
                  <th className="px-6 py-4 text-xs font-bold text-on-surface-variant uppercase tracking-wider">提交时间</th>
                  <th className="px-6 py-4 text-xs font-bold text-on-surface-variant uppercase tracking-wider text-right">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/5">
                <tr className="hover:bg-surface-container-low/30 transition-colors group">
                  <td className="px-6 py-5 font-medium text-sm text-primary">AS2024090122</td>
                  <td className="px-6 py-5 text-sm text-on-surface-variant">ORD-991204123</td>
                  <td className="px-6 py-5">
                    <span className="text-sm font-bold">¥2,499.00</span>
                  </td>
                  <td className="px-6 py-5">
                    <div className="flex items-center gap-2">
                      <span className="text-xs bg-surface-container-low px-2 py-0.5 rounded text-on-surface">质量缺陷</span>
                      <span className="text-xs text-on-surface-variant truncate max-w-[120px]">无法开机，屏幕闪烁</span>
                    </div>
                  </td>
                  <td className="px-6 py-5">
                    <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-error-container/20 text-error">
                      <span className="w-1.5 h-1.5 rounded-full bg-error"></span>
                      待审核
                    </span>
                  </td>
                  <td className="px-6 py-5 text-sm text-on-surface-variant">2024-09-01 14:22</td>
                  <td className="px-6 py-5 text-right">
                    <button className="text-primary font-bold text-xs hover:underline mr-4 cursor-pointer">审核</button>
                    <button className="text-on-surface-variant hover:text-on-surface transition-colors cursor-pointer">
                      <span className="material-symbols-outlined text-xl">more_vert</span>
                    </button>
                  </td>
                </tr>
                
                <tr className="hover:bg-surface-container-low/30 transition-colors group">
                  <td className="px-6 py-5 font-medium text-sm text-primary">AS2024090118</td>
                  <td className="px-6 py-5 text-sm text-on-surface-variant">ORD-991204055</td>
                  <td className="px-6 py-5">
                    <span className="text-sm font-bold">¥128.50</span>
                  </td>
                  <td className="px-6 py-5">
                    <div className="flex items-center gap-2">
                      <span className="text-xs bg-surface-container-low px-2 py-0.5 rounded text-on-surface">拍错不想要</span>
                      <span className="text-xs text-on-surface-variant truncate max-w-[120px]">客户误触，未拆封</span>
                    </div>
                  </td>
                  <td className="px-6 py-5">
                    <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-secondary-container/20 text-on-secondary-container">
                      <span className="w-1.5 h-1.5 rounded-full bg-secondary"></span>
                      处理中
                    </span>
                  </td>
                  <td className="px-6 py-5 text-sm text-on-surface-variant">2024-09-01 12:45</td>
                  <td className="px-6 py-5 text-right">
                    <button className="text-primary font-bold text-xs hover:underline mr-4 cursor-pointer">跟进</button>
                    <button className="text-on-surface-variant hover:text-on-surface transition-colors cursor-pointer">
                      <span className="material-symbols-outlined text-xl">more_vert</span>
                    </button>
                  </td>
                </tr>
                
                <tr className="hover:bg-surface-container-low/30 transition-colors group">
                  <td className="px-6 py-5 font-medium text-sm text-primary">AS2024090105</td>
                  <td className="px-6 py-5 text-sm text-on-surface-variant">ORD-991203982</td>
                  <td className="px-6 py-5">
                    <span className="text-sm font-bold">¥590.00</span>
                  </td>
                  <td className="px-6 py-5">
                    <div className="flex items-center gap-2">
                      <span className="text-xs bg-surface-container-low px-2 py-0.5 rounded text-on-surface">物流损毁</span>
                      <span className="text-xs text-on-surface-variant truncate max-w-[120px]">外包装严重变形，商品破损</span>
                    </div>
                  </td>
                  <td className="px-6 py-5">
                    <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-surface-variant text-on-surface-variant">
                      <span className="w-1.5 h-1.5 rounded-full bg-on-surface-variant"></span>
                      已关闭
                    </span>
                  </td>
                  <td className="px-6 py-5 text-sm text-on-surface-variant">2024-09-01 09:10</td>
                  <td className="px-6 py-5 text-right">
                    <button className="text-primary font-bold text-xs hover:underline mr-4 cursor-pointer">详情</button>
                    <button className="text-on-surface-variant hover:text-on-surface transition-colors cursor-pointer">
                      <span className="material-symbols-outlined text-xl">more_vert</span>
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            
            {/* Pagination */}
            <div className="px-6 py-4 flex items-center justify-between bg-surface-container-low/20">
              <span className="text-xs text-on-surface-variant">共 128 条记录，显示 1 - 10</span>
              <div className="flex gap-2">
                <button className="w-8 h-8 rounded-lg flex items-center justify-center bg-white border border-outline-variant/15 text-on-surface hover:bg-surface-container-low transition-all cursor-pointer">
                  <span className="material-symbols-outlined text-sm">chevron_left</span>
                </button>
                <button className="w-8 h-8 rounded-lg flex items-center justify-center bg-primary text-on-primary font-bold text-xs cursor-pointer">1</button>
                <button className="w-8 h-8 rounded-lg flex items-center justify-center bg-white border border-outline-variant/15 text-on-surface hover:bg-surface-container-low transition-all text-xs cursor-pointer">2</button>
                <button className="w-8 h-8 rounded-lg flex items-center justify-center bg-white border border-outline-variant/15 text-on-surface hover:bg-surface-container-low transition-all text-xs cursor-pointer">3</button>
                <button className="w-8 h-8 rounded-lg flex items-center justify-center bg-white border border-outline-variant/15 text-on-surface hover:bg-surface-container-low transition-all cursor-pointer">
                  <span className="material-symbols-outlined text-sm">chevron_right</span>
                </button>
              </div>
            </div>
          </div>

          {/* Floating Quick Insights */}
          <div className="mt-8 grid grid-cols-1 lg:grid-cols-2 gap-8">
            <div className="p-6 bg-white rounded-xl shadow-sm border border-outline-variant/10">
              <h3 className="font-headline font-bold text-lg mb-4 flex items-center gap-2">
                <span className="material-symbols-outlined text-tertiary">warning</span>
                高风险订单提醒
              </h3>
              <div className="space-y-4">
                <div className="flex items-center justify-between p-3 rounded-xl bg-surface-container-low/50">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-full bg-error-container/20 flex items-center justify-center">
                      <span className="material-symbols-outlined text-error">priority_high</span>
                    </div>
                    <div>
                      <p className="text-sm font-bold">同一用户短时多次申请</p>
                      <p className="text-[11px] text-on-surface-variant">UID: 887291 • 2小时内申请4次</p>
                    </div>
                  </div>
                  <button className="px-3 py-1 text-xs font-bold border border-error/30 text-error rounded-lg hover:bg-error/10 transition-all cursor-pointer">立即拦截</button>
                </div>
                
                <div className="flex items-center justify-between p-3 rounded-xl bg-surface-container-low/50">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-full bg-tertiary-container/20 flex items-center justify-center">
                      <span className="material-symbols-outlined text-tertiary">distance</span>
                    </div>
                    <div>
                      <p className="text-sm font-bold">收货地址异常偏移</p>
                      <p className="text-[11px] text-on-surface-variant">收货地与下单IP归属地相距3000km</p>
                    </div>
                  </div>
                  <button className="px-3 py-1 text-xs font-bold border border-tertiary/30 text-tertiary rounded-lg hover:bg-tertiary/10 transition-all cursor-pointer">标记查验</button>
                </div>
              </div>
            </div>
            
            <div className="p-6 bg-white rounded-xl shadow-sm border border-outline-variant/10">
              <h3 className="font-headline font-bold text-lg mb-4 flex items-center gap-2">
                <span className="material-symbols-outlined text-primary">analytics</span>
                退款原因分布 (Top 3)
              </h3>
              <div className="space-y-5">
                <div>
                  <div className="flex justify-between text-xs font-bold mb-1.5">
                    <span>1. 商品质量问题</span>
                    <span className="text-on-surface-variant">42%</span>
                  </div>
                  <div className="w-full h-2 bg-surface-container-low rounded-full overflow-hidden">
                    <div className="h-full bg-primary rounded-full w-[42%]"></div>
                  </div>
                </div>
                <div>
                  <div className="flex justify-between text-xs font-bold mb-1.5">
                    <span>2. 物流配送延迟</span>
                    <span className="text-on-surface-variant">28%</span>
                  </div>
                  <div className="w-full h-2 bg-surface-container-low rounded-full overflow-hidden">
                    <div className="h-full bg-secondary-dim rounded-full w-[28%]"></div>
                  </div>
                </div>
                <div>
                  <div className="flex justify-between text-xs font-bold mb-1.5">
                    <span>3. 拍错/不想要</span>
                    <span className="text-on-surface-variant">15%</span>
                  </div>
                  <div className="w-full h-2 bg-surface-container-low rounded-full overflow-hidden">
                    <div className="h-full bg-tertiary-fixed rounded-full w-[15%]"></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>

      {/* Contextual FAB */}
      <button className="fixed bottom-8 right-8 w-14 h-14 rounded-full bg-primary text-on-primary shadow-2xl shadow-primary/40 flex items-center justify-center group active:scale-95 transition-all z-50 cursor-pointer">
        <span className="material-symbols-outlined text-2xl group-hover:rotate-90 transition-transform">add</span>
        <span className="absolute right-16 px-3 py-1.5 bg-on-surface text-surface text-xs font-bold rounded-lg opacity-0 group-hover:opacity-100 translate-x-4 group-hover:translate-x-0 transition-all pointer-events-none whitespace-nowrap">新建手动审核</span>
      </button>
    </div>
  );
}
