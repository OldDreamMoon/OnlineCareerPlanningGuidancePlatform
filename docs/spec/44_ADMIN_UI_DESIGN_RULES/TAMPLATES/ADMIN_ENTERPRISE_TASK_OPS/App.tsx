import React, { useState } from 'react';
import { 
  LayoutDashboard, 
  ListTodo, 
  Activity, 
  BarChart2, 
  History, 
  LogOut, 
  Download, 
  Lightbulb, 
  Search, 
  ChevronLeft, 
  ChevronRight, 
  ExternalLink,
  AlertCircle,
  Clock,
  CheckCircle2,
  XCircle,
  MoreHorizontal,
  Smartphone
} from 'lucide-react';

export default function App() {
  const [activeTab, setActiveTab] = useState('overview');

  return (
    <div className="flex h-screen w-full overflow-hidden bg-[var(--color-surface-base)] text-[var(--color-text-main)] font-sans">
      
      {/* Left Sidebar */}
      <aside className="w-64 flex-shrink-0 flex flex-col justify-between h-full py-8 px-6 bg-[var(--color-surface-lowest)] luminous-shadow z-10">
        <div>
          <div className="mb-10">
            <h1 className="font-display font-bold text-2xl text-indigo-600 tracking-tight">Task Governance</h1>
            <p className="text-xs text-[var(--color-text-muted)] font-medium tracking-wider uppercase mt-1">Enterprise Admin</p>
          </div>

          <nav className="space-y-2">
            <NavItem icon={<LayoutDashboard size={20} />} label="治理概览" active={activeTab === 'overview'} onClick={() => setActiveTab('overview')} />
            <NavItem icon={<ListTodo size={20} />} label="任务大厅" active={activeTab === 'tasks'} onClick={() => setActiveTab('tasks')} />
            <NavItem icon={<Activity size={20} />} label="流程监控" active={activeTab === 'monitor'} onClick={() => setActiveTab('monitor')} />
            <NavItem icon={<BarChart2 size={20} />} label="效能分析" active={activeTab === 'analysis'} onClick={() => setActiveTab('analysis')} />
            <NavItem icon={<History size={20} />} label="系统日志" active={activeTab === 'logs'} onClick={() => setActiveTab('logs')} />
          </nav>
        </div>

        <div className="space-y-4">
          <button className="w-full py-3 px-4 rounded-2xl bg-gradient-to-br from-[#63dbf2] to-[#3b82f6] text-white font-semibold text-sm luminous-shadow hover:shadow-lg transition-all transform hover:-translate-y-0.5">
            快速创建任务
          </button>
          <button className="w-full flex items-center gap-3 py-3 px-4 text-[var(--color-text-muted)] hover:text-[var(--color-text-main)] transition-colors font-medium text-sm">
            <LogOut size={20} />
            退出登录
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 h-full overflow-y-auto p-8 xl:p-10">
        
        {/* Header */}
        <header className="flex justify-between items-start mb-10">
          <div>
            <h2 className="font-display font-bold text-3xl mb-2">企业任务治理台</h2>
            <p className="text-[var(--color-text-muted)] text-sm max-w-2xl leading-relaxed">
              实时监控全量任务状态，深度治理高风险流程。通过 AI 驱动的质量抽检与效能分析，确保企业数字化任务的透明度与履约质量。
            </p>
          </div>
          <div className="flex gap-4">
            <button className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-[var(--color-surface-lowest)] text-[var(--color-text-main)] font-medium text-sm luminous-shadow hover:bg-[var(--color-surface-low)] transition-colors">
              <Download size={18} />
              导出报表
            </button>
            <button className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-gradient-to-br from-[#63dbf2] to-[#3b82f6] text-white font-medium text-sm luminous-shadow hover:shadow-lg transition-all">
              治理决策建议
            </button>
          </div>
        </header>

        {/* Metrics Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-5 gap-6 mb-10">
          <MetricCard 
            icon={<DatabaseIcon />} 
            value="12,842" 
            label="平台任务总数" 
            trend="+12%" 
            trendColor="text-blue-500" 
            iconBg="bg-blue-50"
            accentColor="bg-blue-500"
          />
          <MetricCard 
            icon={<UsersIcon />} 
            value="1,248" 
            label="招募中任务" 
            trend="3,421" 
            trendColor="text-green-500" 
            iconBg="bg-green-50"
            accentColor="bg-green-500"
          />
          <MetricCard 
            icon={<AlertIcon />} 
            value="42" 
            label="需要介入" 
            tag="URGENT" 
            iconBg="bg-red-50"
            accentColor="bg-red-500"
          />
          <MetricCard 
            icon={<ClipboardIcon />} 
            value="892" 
            label="提交积压" 
            iconBg="bg-amber-50"
            accentColor="bg-amber-500"
          />
          <MetricCard 
            icon={<ClockIcon />} 
            value="156" 
            label="临期压力" 
            iconBg="bg-indigo-50"
            accentColor="bg-indigo-500"
          />
        </div>

        {/* Main Content Two-Column Layout */}
        <div className="flex flex-col xl:flex-row gap-8 items-start">
          
          {/* Left Column: Filters & Table */}
          <div className="flex-1 w-full min-w-0">
            {/* Filters */}
            <div className="flex items-center gap-4 mb-6 bg-[var(--color-surface-lowest)] p-3 rounded-2xl luminous-shadow">
              <div className="flex-1 flex items-center gap-2 px-4 py-2 bg-[var(--color-surface-low)] rounded-xl focus-within:bg-white focus-within:ring-2 focus-within:ring-[#63dbf2] transition-all">
                <Search size={18} className="text-[var(--color-text-muted)]" />
                <input 
                  type="text" 
                  placeholder="搜索任务名称、ID或企业" 
                  className="bg-transparent border-none outline-none w-full text-sm text-[var(--color-text-main)] placeholder-[var(--color-text-muted)]"
                />
              </div>
              <select className="px-4 py-2.5 bg-[var(--color-surface-low)] rounded-xl text-sm font-medium text-[var(--color-text-main)] outline-none border-none cursor-pointer hover:bg-gray-200 transition-colors">
                <option>任务状态: 全部</option>
              </select>
              <select className="px-4 py-2.5 bg-[var(--color-surface-low)] rounded-xl text-sm font-medium text-[var(--color-text-main)] outline-none border-none cursor-pointer hover:bg-gray-200 transition-colors">
                <option>风险等级: 全部</option>
              </select>
              <button className="px-5 py-2.5 bg-[var(--color-surface-low)] rounded-xl text-sm font-medium text-[var(--color-text-main)] hover:bg-gray-200 transition-colors">
                重置重置
              </button>
            </div>

            {/* Table */}
            <div className="bg-[var(--color-surface-lowest)] rounded-3xl p-6 luminous-shadow">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="text-[var(--color-text-muted)] text-sm border-b border-gray-100">
                    <th className="pb-4 font-medium w-1/4">任务名称</th>
                    <th className="pb-4 font-medium w-1/5">关联企业</th>
                    <th className="pb-4 font-medium w-1/6">状态</th>
                    <th className="pb-4 font-medium w-1/5">进度</th>
                    <th className="pb-4 font-medium">风险信号</th>
                    <th className="pb-4 font-medium text-right">操作</th>
                  </tr>
                </thead>
                <tbody className="text-sm">
                  <TableRow 
                    title="AI 语义标注治理项目 v2"
                    id="TASK-8829"
                    company="极客智造科技"
                    status="Open"
                    progress={75}
                    progressText="1.2k/1.6k"
                    risk="HIGH RISK"
                  />
                  <TableRow 
                    title="短视频素材自动化剪辑"
                    id="TASK-9104"
                    company="无限流媒体"
                    status="Open"
                    progress={12}
                    progressText="120/1000"
                    risk="QUALITY"
                    risk2="WARN"
                  />
                  <TableRow 
                    title="企业合规文档初审"
                    id="TASK-4412"
                    company="正大法律顾问"
                    status="Closed"
                    progress={100}
                    progressText=""
                    risk="RESOLVED"
                  />
                </tbody>
              </table>

              {/* Pagination */}
              <div className="flex items-center justify-between mt-8 text-sm text-[var(--color-text-muted)]">
                <span>显示 1 到 10，共 1,248 条任务</span>
                <div className="flex items-center gap-2">
                  <button className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-[var(--color-surface-low)] transition-colors"><ChevronLeft size={16} /></button>
                  <button className="w-8 h-8 flex items-center justify-center rounded-lg bg-indigo-600 text-white font-medium luminous-shadow">1</button>
                  <button className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-[var(--color-surface-low)] transition-colors">2</button>
                  <button className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-[var(--color-surface-low)] transition-colors">3</button>
                  <button className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-[var(--color-surface-low)] transition-colors"><ChevronRight size={16} /></button>
                </div>
              </div>
            </div>
          </div>

          {/* Right Column: Information Cards */}
          <div className="w-full xl:w-[380px] flex-shrink-0 space-y-6">
            
            {/* Task Details Card */}
            <div className="bg-[var(--color-surface-lowest)] rounded-3xl overflow-hidden luminous-shadow">
              <div className="bg-indigo-600 p-4 flex justify-between items-center text-white">
                <span className="font-medium text-sm">任务详情摘要</span>
                <ExternalLink size={18} className="opacity-80 cursor-pointer hover:opacity-100" />
              </div>
              <div className="p-6">
                <div className="flex items-start gap-4 mb-6">
                  <div className="w-12 h-12 rounded-2xl bg-indigo-50 flex items-center justify-center text-indigo-600 flex-shrink-0">
                    <Lightbulb size={24} />
                  </div>
                  <div>
                    <h3 className="font-display font-bold text-lg leading-tight mb-1">AI 语义标注治理项目</h3>
                    <p className="text-xs text-[var(--color-text-muted)]">创建于 2023.10.12</p>
                  </div>
                </div>

                <div className="flex justify-between mb-6">
                  <div>
                    <p className="text-xs text-[var(--color-text-muted)] mb-1">任务奖励</p>
                    <p className="font-display font-bold text-xl text-indigo-600">¥12.50 <span className="text-sm text-[var(--color-text-muted)] font-normal">/件</span></p>
                  </div>
                  <div className="text-right">
                    <p className="text-xs text-[var(--color-text-muted)] mb-1">截止日期</p>
                    <p className="font-display font-bold text-sm">2023.12.31</p>
                  </div>
                </div>

                <div className="mb-6">
                  <div className="flex justify-between text-xs mb-2">
                    <span className="font-medium">提交质量抽检</span>
                    <span className="text-indigo-600 font-bold">88.4%</span>
                  </div>
                  <div className="h-2 w-full bg-[var(--color-surface-low)] rounded-full overflow-hidden">
                    <div className="h-full bg-gradient-to-r from-indigo-400 to-indigo-600 rounded-full" style={{ width: '88.4%' }}></div>
                  </div>
                </div>

                <div className="bg-red-50 text-red-600 text-xs font-medium p-3 rounded-xl flex items-center gap-2 mb-6">
                  <AlertCircle size={16} />
                  检测到压力异常：35% 的提交集中在凌晨
                </div>

                <div>
                  <p className="text-xs font-medium mb-3">治理动作</p>
                  <button className="w-full py-2.5 bg-[var(--color-surface-low)] hover:bg-gray-200 text-[var(--color-text-main)] font-medium text-sm rounded-xl transition-colors mb-3">
                    查看企业详情
                  </button>
                  <div className="flex gap-3">
                    <button className="flex-1 py-2.5 bg-rose-600 hover:bg-rose-700 text-white font-medium text-sm rounded-xl transition-colors">
                      关闭任务
                    </button>
                    <button className="flex-1 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white font-medium text-sm rounded-xl transition-colors">
                      重新开放
                    </button>
                  </div>
                </div>
              </div>
            </div>

            {/* Activity Timeline */}
            <div className="bg-[var(--color-surface-lowest)] rounded-3xl p-6 luminous-shadow relative">
              <button className="absolute -top-4 -right-4 w-12 h-12 bg-indigo-600 text-white rounded-full flex items-center justify-center luminous-shadow hover:scale-105 transition-transform z-10">
                <PlusIcon />
              </button>
              
              <div className="flex items-center gap-2 mb-6">
                <History size={20} className="text-indigo-600" />
                <h3 className="font-display font-bold text-lg">最近治理动态</h3>
              </div>

              <div className="space-y-6 relative before:absolute before:inset-0 before:ml-[11px] before:-translate-x-px md:before:mx-auto md:before:translate-x-0 before:h-full before:w-0.5 before:bg-gradient-to-b before:from-transparent before:via-gray-200 before:to-transparent">
                <TimelineItem 
                  color="bg-indigo-500" 
                  title="系统自动触发抽检任务" 
                  time="10 分钟前 · 标注任务 #8829" 
                />
                <TimelineItem 
                  color="bg-emerald-500" 
                  title="管理员 [Admin_Zhang] 修改了规则" 
                  time="1 小时前 · 治理引擎 v1.2" 
                />
                <TimelineItem 
                  color="bg-rose-500" 
                  title="高风险预警：企业提交积压超阈值" 
                  time="3 小时前 · 极客智造科技" 
                />
              </div>

              <button className="w-full mt-6 py-2.5 border border-gray-100 text-indigo-600 font-medium text-sm rounded-xl hover:bg-indigo-50 transition-colors">
                查看全部记录
              </button>
            </div>

          </div>
        </div>
      </main>
    </div>
  );
}

// --- Subcomponents ---

function NavItem({ icon, label, active, onClick }: { icon: React.ReactNode, label: string, active?: boolean, onClick?: () => void }) {
  return (
    <button 
      onClick={onClick}
      className={`w-full flex items-center gap-3 px-4 py-3 rounded-2xl transition-all ${
        active 
          ? 'bg-indigo-50 text-indigo-600 font-semibold' 
          : 'text-[var(--color-text-muted)] hover:bg-[var(--color-surface-low)] hover:text-[var(--color-text-main)] font-medium'
      }`}
    >
      <div className={active ? 'text-indigo-600' : ''}>{icon}</div>
      <span className="text-sm">{label}</span>
    </button>
  );
}

function MetricCard({ icon, value, label, trend, trendColor, tag, iconBg, accentColor }: any) {
  return (
    <div className="bg-[var(--color-surface-lowest)] p-5 rounded-3xl luminous-shadow relative overflow-hidden group">
      <div className={`absolute left-0 top-0 bottom-0 w-1 ${accentColor} opacity-0 group-hover:opacity-100 transition-opacity`}></div>
      <div className="flex justify-between items-start mb-4">
        <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${iconBg}`}>
          {icon}
        </div>
        {trend && <span className={`text-xs font-bold ${trendColor} bg-white/50 px-2 py-1 rounded-md`}>{trend}</span>}
        {tag && <span className="text-[10px] font-bold text-white bg-rose-600 px-2 py-1 rounded-full tracking-wider">{tag}</span>}
      </div>
      <div>
        <h3 className="font-display font-bold text-3xl mb-1">{value}</h3>
        <p className="text-xs text-[var(--color-text-muted)] font-medium">{label}</p>
      </div>
    </div>
  );
}

function TableRow({ title, id, company, status, progress, progressText, risk, risk2 }: any) {
  return (
    <tr className="border-b border-gray-50 hover:bg-[var(--color-surface-base)] transition-colors group">
      <td className="py-4 pr-4">
        <div className="font-display font-bold text-[var(--color-text-main)] mb-1">{title}</div>
        <div className="text-xs text-[var(--color-text-muted)]">ID: {id}</div>
      </td>
      <td className="py-4">
        <div className="flex items-center gap-2">
          <div className="w-6 h-6 rounded-md bg-gray-100 flex items-center justify-center text-gray-500">
            <Smartphone size={14} />
          </div>
          <span className="font-medium">{company}</span>
        </div>
      </td>
      <td className="py-4">
        <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium ${
          status === 'Open' ? 'bg-emerald-100 text-emerald-700' : 'bg-gray-100 text-gray-600'
        }`}>
          <span className={`w-1.5 h-1.5 rounded-full ${status === 'Open' ? 'bg-emerald-500' : 'bg-gray-400'}`}></span>
          {status}
        </span>
      </td>
      <td className="py-4 pr-4">
        <div className="flex items-center gap-3">
          <span className="text-xs font-bold w-8">{progress}%</span>
          <div className="flex-1 h-1.5 bg-[var(--color-surface-low)] rounded-full overflow-hidden">
            <div className={`h-full rounded-full ${status === 'Open' ? 'bg-indigo-500' : 'bg-gray-400'}`} style={{ width: `${progress}%` }}></div>
          </div>
          {progressText && <span className="text-xs text-[var(--color-text-muted)] w-12 text-right">{progressText}</span>}
        </div>
      </td>
      <td className="py-4">
        <div className="flex flex-col gap-1 items-start">
          {risk && (
            <span className={`text-[10px] font-bold px-2 py-0.5 rounded-md ${
              risk === 'HIGH RISK' ? 'bg-rose-100 text-rose-700' : 
              risk === 'QUALITY' ? 'bg-amber-100 text-amber-700' : 
              'bg-gray-100 text-gray-500'
            }`}>
              {risk}
            </span>
          )}
          {risk2 && (
            <span className="text-[10px] font-bold px-2 py-0.5 rounded-md bg-amber-100 text-amber-700">
              {risk2}
            </span>
          )}
        </div>
      </td>
      <td className="py-4 text-right">
        <button className="text-indigo-600 font-medium text-sm hover:text-indigo-800 transition-colors opacity-0 group-hover:opacity-100">
          管理
        </button>
      </td>
    </tr>
  );
}

function TimelineItem({ color, title, time }: { color: string, title: string, time: string }) {
  return (
    <div className="relative flex items-start gap-4 pl-4">
      <div className={`absolute left-0 w-2.5 h-2.5 rounded-full ${color} ring-4 ring-white mt-1.5`}></div>
      <div>
        <h4 className="text-sm font-medium text-[var(--color-text-main)] mb-0.5">{title}</h4>
        <p className="text-xs text-[var(--color-text-muted)]">{time}</p>
      </div>
    </div>
  );
}

// --- Icons ---
function DatabaseIcon() { return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-blue-500"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M3 5V19A9 3 0 0 0 21 19V5"/><path d="M3 12A9 3 0 0 0 21 12"/></svg>; }
function UsersIcon() { return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-green-500"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>; }
function AlertIcon() { return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-red-500"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><path d="M12 9v4"/><path d="M12 17h.01"/></svg>; }
function ClipboardIcon() { return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-amber-500"><rect width="8" height="4" x="8" y="2" rx="1" ry="1"/><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/></svg>; }
function ClockIcon() { return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-indigo-500"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>; }
function PlusIcon() { return <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14"/><path d="M12 5v14"/></svg>; }
