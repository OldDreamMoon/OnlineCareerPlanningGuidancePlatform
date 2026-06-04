import React, { useState } from 'react';
import {
  Activity,
  Search,
  Bell,
  Settings,
  HelpCircle,
  LayoutDashboard,
  ClipboardCheck,
  Wallet,
  ReceiptText,
  Settings2,
  ShieldCheck,
  AlertCircle,
  Clock,
  TrendingUp,
  Banknote,
  TrendingDown,
  Timer,
  CheckCircle2,
  RefreshCw,
  Filter,
  MoreVertical,
  Plus
} from 'lucide-react';

export default function App() {
  const [activeTab, setActiveTab] = useState('refund');

  const tableData = [
    {
      id: '#REF-882910',
      name: '张小凡',
      uid: '44921',
      amount: '¥299.00',
      reason: '商品质量问题',
      status: '等待初审',
      statusColor: 'text-tertiary',
      bgStatus: 'bg-tertiary',
      action: '立即审核',
      actionColor: 'text-primary',
      avatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuDm6jRIb1Jf3EYUi6Mpvf9EiaXbpWxLwiueVI04BPjO0TjYjRUqCcmSLPrcGPBZUBUHU4_aQUAUAv64lo1BLs5PtF94H0lVgpCNiCWzVKfPa6c0xGcjWKneedCh4LTaBIpOuqQq2PtlLrV3mqz8_vKkOhfEKpQZDk1Y-cumW2BjJbw2qQtxBtEVI8RjPv-McpNa8WlbRkV1xD0N9rM1I_de66RYeM_u7w3wARPgEt9di3YkmK7fB5Gd_-Erfv4FjIB8Uf2aONdPOiU'
    },
    {
      id: '#REF-882909',
      name: '王曼妮',
      uid: '33812',
      amount: '¥1,580.00',
      reason: '物流长时间未更新',
      status: '审核通过',
      statusColor: 'text-secondary',
      bgStatus: 'bg-secondary',
      action: '查看详情',
      actionColor: 'text-on-surface-variant',
      avatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuDu2AXk2fSLhYKRhKRFZpB-7xXjMOhilM0zatJrD7xZzteJUSJh1rz6Wg_c9DgyCR9g14eAY-hsiIjGrcsn-JZfQnj_ReJ0S9lky7d_3AYR-AyOb2O7JZWHTOk6iOoArqwKq07FegqX5au2k3FpvwR-5DOfWbz-tzddUSMpVAg_Mi__uKQA7DHpZ7SY3QamtE33XhrwhD2iMxcDbvc_Iz2U1i6Zgl6N9vETTL_lm9VH1gQy8rTAR1BKJGDRxL2hX4BJu8cyPXlxSSs'
    },
    {
      id: '#REF-882908',
      name: '陈志平',
      uid: '55210',
      amount: '¥45.00',
      reason: '重复下单',
      status: '审核驳回',
      statusColor: 'text-error',
      bgStatus: 'bg-error',
      action: '查看原因',
      actionColor: 'text-on-surface-variant',
      avatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuDi6qvH0N-q80aa7xv8-t4szn-qrGSj9155R62Xl8TFUjCfjyOJzH25l6R6H4H5SOzzbSNZK5KOaONWPJVw5jU8Y8CX1Tu_rIFBkFQvHVynQNoDYIc6FNSgyUyCdjYq7lMhZoHiUnX8MJatkcZnys83hDwifDT-CbHaG7KtOIeal7IukHyPrya-wwemk-AYPpihQTLl9iixMDPrZ8F09YWBPpC9f54YDNrqJjoQc1weL0AnzV-qNas75X37BkCrMfhJ1YrvVBQ0oec'
    }
  ];

  return (
    <div className="bg-surface font-sans text-on-surface antialiased min-h-screen">
      {/* Top Navigation Bar */}
      <header className="fixed top-0 w-full z-50 glass-nav shadow-[0_10px_40px_rgba(44,47,49,0.04)] h-16 flex justify-between items-center px-6">
        <div className="flex items-center gap-8">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 bg-gradient-to-br from-primary to-primary-container rounded-lg flex items-center justify-center text-white shadow-sm">
              <Activity size={20} strokeWidth={2.5} />
            </div>
            <h1 className="text-xl font-bold tracking-tight text-on-surface font-headline">交易中枢管理</h1>
          </div>
          <nav className="hidden md:flex items-center gap-6 h-16">
            <a className="text-primary font-semibold border-b-2 border-primary h-full flex items-center px-2" href="#">概览</a>
            <a className="text-on-surface-variant hover:text-primary transition-colors duration-300 h-full flex items-center px-2 font-medium" href="#">交易流水</a>
            <a className="text-on-surface-variant hover:text-primary transition-colors duration-300 h-full flex items-center px-2 font-medium" href="#">报表中心</a>
          </nav>
        </div>
        <div className="flex items-center gap-4">
          <div className="relative group hidden sm:block">
            <div className="absolute inset-y-0 left-3 flex items-center pointer-events-none text-on-surface-variant opacity-50">
              <Search size={16} />
            </div>
            <input
              className="bg-surface-low border-none rounded-xl pl-10 pr-4 py-2 text-sm w-64 focus:ring-2 focus:ring-primary/30 focus:bg-surface-lowest transition-all outline-none placeholder:text-on-surface-variant/50"
              placeholder="搜索流水号/订单号"
              type="text"
            />
          </div>
          <div className="flex items-center gap-1">
            <button className="p-2 text-on-surface-variant hover:bg-surface-low rounded-lg transition-all active:scale-95">
              <Bell size={20} />
            </button>
            <button className="p-2 text-on-surface-variant hover:bg-surface-low rounded-lg transition-all active:scale-95">
              <Settings size={20} />
            </button>
            <button className="p-2 text-on-surface-variant hover:bg-surface-low rounded-lg transition-all active:scale-95">
              <HelpCircle size={20} />
            </button>
            <div className="h-6 w-px bg-outline-variant/30 mx-2"></div>
            <img
              alt="User Avatar"
              className="w-8 h-8 rounded-full object-cover ml-1 border border-outline-variant/20"
              src="https://lh3.googleusercontent.com/aida-public/AB6AXuD-YMYaXOL0B-VqAc8_pD5vNbBpWMJ3AHPOLsoROUsTp7GhgRVJXoT2HEhOjcptw3fvU55hnA6PZJYb2jqOlMdNlhAmBOxMq7BAMPrsMOd8nZgW9b81Ev99AIFhE--6Bw5qN1vo4cjCQmKSWBvTwiwowq0y2fdyBd_e2g1Evd3cgeJSXMJQ7pMybIgUpT62Qgr5X3sKzI82lvy-Ns1hTVy3TRArpZvL4jr8eobGjebgLu-mdMnJZWK1cH381yROf9H6Cbt1f99uqy8"
              referrerPolicy="no-referrer"
            />
          </div>
        </div>
      </header>

      <div className="flex pt-16 min-h-screen">
        {/* Side Navigation Bar */}
        <aside className="h-[calc(100vh-64px)] w-64 bg-surface flex flex-col gap-2 p-4 fixed left-0 overflow-y-auto border-r border-outline-variant/10">
          <div className="px-2 py-4 mb-2">
            <p className="text-lg font-extrabold text-on-surface font-headline">交易管理系统</p>
            <p className="text-[10px] uppercase tracking-widest text-on-surface-variant/70 font-bold mt-0.5">Transaction Hub</p>
          </div>
          <a className="flex items-center gap-3 px-3 py-2.5 text-on-surface-variant hover:bg-surface-low hover:translate-x-1 transition-all rounded-xl" href="#">
            <LayoutDashboard size={20} />
            <span className="font-medium text-sm">概览</span>
          </a>
          <a className="flex items-center gap-3 px-3 py-2.5 bg-surface-lowest text-primary ambient-shadow font-semibold rounded-xl" href="#">
            <ClipboardCheck size={20} />
            <span className="font-medium text-sm">售后审核</span>
          </a>
          <a className="flex items-center gap-3 px-3 py-2.5 text-on-surface-variant hover:bg-surface-low hover:translate-x-1 transition-all rounded-xl" href="#">
            <Wallet size={20} />
            <span className="font-medium text-sm">支付对账</span>
          </a>
          <a className="flex items-center gap-3 px-3 py-2.5 text-on-surface-variant hover:bg-surface-low hover:translate-x-1 transition-all rounded-xl" href="#">
            <ReceiptText size={20} />
            <span className="font-medium text-sm">交易流水</span>
          </a>
          <div className="mt-auto pt-4">
            <a className="flex items-center gap-3 px-3 py-2.5 text-on-surface-variant hover:bg-surface-low hover:translate-x-1 transition-all rounded-xl" href="#">
              <Settings2 size={20} />
              <span className="font-medium text-sm">系统设置</span>
            </a>
          </div>
        </aside>

        {/* Main Content Area */}
        <main className="flex-1 ml-64 p-8">
          <div className="max-w-7xl mx-auto">
            
            {/* Page Header & Tab Controls */}
            <div className="mb-8">
              <div className="flex items-center justify-between mb-6">
                <div>
                  <h2 className="text-3xl font-extrabold text-on-surface font-headline tracking-tight">交易中枢</h2>
                  <p className="text-on-surface-variant mt-1 text-sm font-medium">管理并监控全平台的售后退款与异常对账流程</p>
                </div>
                <div className="flex gap-3">
                  <button className="px-5 py-2.5 bg-surface-lowest text-on-surface ghost-border rounded-xl text-sm font-semibold hover:bg-surface-low transition-all">
                    导出数据
                  </button>
                  <button className="px-5 py-2.5 bg-gradient-to-br from-primary to-primary-container text-white rounded-xl text-sm font-semibold shadow-lg shadow-primary/20 hover:shadow-xl hover:-translate-y-0.5 active:translate-y-0 transition-all">
                    新建工单
                  </button>
                </div>
              </div>

              {/* Functional Switch Tabs */}
              <div className="flex p-1.5 bg-surface-low rounded-2xl w-fit">
                <button 
                  onClick={() => setActiveTab('refund')}
                  className={`flex items-center gap-2 px-6 py-2.5 rounded-xl font-bold text-sm transition-all duration-300 ${activeTab === 'refund' ? 'bg-surface-lowest text-primary ambient-shadow' : 'text-on-surface-variant hover:text-on-surface'}`}
                >
                  <ShieldCheck size={18} />
                  售后退款审核
                </button>
                <button 
                  onClick={() => setActiveTab('reconciliation')}
                  className={`flex items-center gap-2 px-6 py-2.5 rounded-xl font-bold text-sm transition-all duration-300 ${activeTab === 'reconciliation' ? 'bg-surface-lowest text-primary ambient-shadow' : 'text-on-surface-variant hover:text-on-surface'}`}
                >
                  <AlertCircle size={18} />
                  支付异常对账
                </button>
              </div>
            </div>

            {/* Bento Grid Content Section */}
            <div className="grid grid-cols-12 gap-6">
              
              {/* Key Metrics (Bento Item) */}
              <div className="col-span-12 lg:col-span-8 grid grid-cols-1 md:grid-cols-3 gap-6">
                <div className="bg-surface-lowest p-6 rounded-[1.5rem] ambient-shadow hover:-translate-y-1 transition-transform duration-300">
                  <div className="w-12 h-12 bg-primary/10 rounded-2xl flex items-center justify-center text-primary mb-5">
                    <Clock size={24} />
                  </div>
                  <p className="text-sm font-semibold text-on-surface-variant">待审核工单</p>
                  <h3 className="text-3xl font-extrabold mt-1 font-headline text-on-surface">128</h3>
                  <div className="mt-3 text-xs text-error flex items-center gap-1 font-bold bg-error/10 w-fit px-2 py-1 rounded-md">
                    <TrendingUp size={14} />
                    +12% 较昨日
                  </div>
                </div>
                
                <div className="bg-surface-lowest p-6 rounded-[1.5rem] ambient-shadow hover:-translate-y-1 transition-transform duration-300">
                  <div className="w-12 h-12 bg-secondary/10 rounded-2xl flex items-center justify-center text-secondary mb-5">
                    <Banknote size={24} />
                  </div>
                  <p className="text-sm font-semibold text-on-surface-variant">今日退款额</p>
                  <h3 className="text-3xl font-extrabold mt-1 font-headline text-on-surface">¥42,850</h3>
                  <div className="mt-3 text-xs text-secondary flex items-center gap-1 font-bold bg-secondary/10 w-fit px-2 py-1 rounded-md">
                    <TrendingDown size={14} />
                    -5% 较昨日
                  </div>
                </div>
                
                <div className="bg-surface-lowest p-6 rounded-[1.5rem] ambient-shadow hover:-translate-y-1 transition-transform duration-300">
                  <div className="w-12 h-12 bg-tertiary/10 rounded-2xl flex items-center justify-center text-tertiary mb-5">
                    <Timer size={24} />
                  </div>
                  <p className="text-sm font-semibold text-on-surface-variant">平均审核时效</p>
                  <h3 className="text-3xl font-extrabold mt-1 font-headline text-on-surface">2.4h</h3>
                  <div className="mt-3 text-xs text-secondary flex items-center gap-1 font-bold bg-secondary/10 w-fit px-2 py-1 rounded-md">
                    <CheckCircle2 size={14} />
                    达标率 98%
                  </div>
                </div>
              </div>

              {/* Quick Actions (Bento Item) */}
              <div className="col-span-12 lg:col-span-4 bg-gradient-to-br from-primary to-primary-container text-white p-8 rounded-[1.5rem] relative overflow-hidden flex flex-col justify-between ambient-shadow">
                <div className="relative z-10">
                  <h4 className="text-xl font-extrabold font-headline mb-2">快速对账工具</h4>
                  <p className="text-white/80 text-sm font-medium leading-relaxed mb-8">一键拉取第三方支付渠道（微信/支付宝）异常流水并自动标记。</p>
                  <button className="w-full py-3.5 bg-surface-lowest text-primary rounded-xl font-bold flex items-center justify-center gap-2 hover:bg-surface-low transition-colors shadow-sm">
                    <RefreshCw size={18} />
                    开始全量对账
                  </button>
                </div>
                {/* Abstract Graphic Background */}
                <div className="absolute -right-12 -bottom-12 w-48 h-48 bg-white rounded-full opacity-10 blur-2xl"></div>
                <div className="absolute -right-4 -top-4 w-24 h-24 bg-white rounded-full opacity-5"></div>
              </div>

              {/* Main Data Table Container (Bento Item) */}
              <div className="col-span-12 bg-surface-lowest rounded-[1.5rem] ambient-shadow overflow-hidden mt-2">
                <div className="px-6 py-5 flex items-center justify-between border-b border-surface-low">
                  <h3 className="font-extrabold text-lg font-headline text-on-surface">近期退款审核申请</h3>
                  <div className="flex gap-2">
                    <button className="p-2 hover:bg-surface-low rounded-lg transition-all text-on-surface-variant">
                      <Filter size={18} />
                    </button>
                    <button className="p-2 hover:bg-surface-low rounded-lg transition-all text-on-surface-variant">
                      <MoreVertical size={18} />
                    </button>
                  </div>
                </div>
                
                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse">
                    <thead className="bg-surface-low/50">
                      <tr>
                        <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-on-surface-variant">申请编号</th>
                        <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-on-surface-variant">用户信息</th>
                        <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-on-surface-variant">退款金额</th>
                        <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-on-surface-variant">退款原因</th>
                        <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-on-surface-variant">状态</th>
                        <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-on-surface-variant">操作</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y-0">
                      {tableData.map((row, index) => (
                        <tr key={index} className="hover:bg-surface-low/50 transition-colors group border-b border-surface-low/50 last:border-0">
                          <td className="px-6 py-4">
                            <span className="font-mono text-sm font-bold text-primary">{row.id}</span>
                          </td>
                          <td className="px-6 py-4">
                            <div className="flex items-center gap-3">
                              <div className="w-9 h-9 rounded-full bg-surface-low overflow-hidden border border-outline-variant/10">
                                <img 
                                  alt={row.name} 
                                  className="w-full h-full object-cover" 
                                  src={row.avatar}
                                  referrerPolicy="no-referrer"
                                />
                              </div>
                              <div>
                                <p className="text-sm font-bold text-on-surface">{row.name}</p>
                                <p className="text-[11px] text-on-surface-variant font-medium mt-0.5">ID: {row.uid}</p>
                              </div>
                            </div>
                          </td>
                          <td className="px-6 py-4">
                            <span className="text-sm font-extrabold font-headline text-on-surface">{row.amount}</span>
                          </td>
                          <td className="px-6 py-4">
                            <span className="text-xs px-3 py-1.5 bg-surface-low text-on-surface-variant font-semibold rounded-full">
                              {row.reason}
                            </span>
                          </td>
                          <td className="px-6 py-4">
                            <div className={`flex items-center gap-2 ${row.statusColor} font-bold text-xs`}>
                              <span className={`w-2 h-2 rounded-full ${row.bgStatus}`}></span>
                              {row.status}
                            </div>
                          </td>
                          <td className="px-6 py-4">
                            <button className={`${row.actionColor} font-bold text-sm hover:opacity-80 transition-opacity`}>
                              {row.action}
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                
                <div className="p-6 flex items-center justify-between border-t border-surface-low">
                  <p className="text-xs font-medium text-on-surface-variant">显示第 1 到 10 条，共 1,284 条数据</p>
                  <div className="flex gap-2">
                    <button className="px-3 py-1.5 bg-surface-low text-on-surface-variant rounded-lg text-xs font-bold hover:bg-surface-low/80 transition-colors">上一页</button>
                    <button className="px-3 py-1.5 bg-primary text-white rounded-lg text-xs font-bold shadow-sm">1</button>
                    <button className="px-3 py-1.5 bg-surface-low text-on-surface-variant rounded-lg text-xs font-bold hover:bg-surface-low/80 transition-colors">2</button>
                    <button className="px-3 py-1.5 bg-surface-low text-on-surface-variant rounded-lg text-xs font-bold hover:bg-surface-low/80 transition-colors">3</button>
                    <button className="px-3 py-1.5 bg-surface-low text-on-surface-variant rounded-lg text-xs font-bold hover:bg-surface-low/80 transition-colors">下一页</button>
                  </div>
                </div>
              </div>

            </div>
          </div>
        </main>
      </div>

      {/* Floating Action Button */}
      <button className="fixed bottom-8 right-8 w-14 h-14 bg-gradient-to-br from-primary to-primary-container text-white rounded-full shadow-xl shadow-primary/30 flex items-center justify-center hover:scale-105 active:scale-95 transition-all z-50">
        <Plus size={28} strokeWidth={2.5} />
      </button>
    </div>
  );
}
