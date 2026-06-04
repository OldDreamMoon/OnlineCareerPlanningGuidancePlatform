import {
  AlertTriangle,
  Award,
  Bell,
  CheckCircle2,
  Clock,
  Cpu,
  Flag,
  Gauge,
  HelpCircle,
  History,
  LayoutDashboard,
  ListChecks,
  ListTodo,
  MoreVertical,
  Search,
  Shield,
  SlidersHorizontal,
  Sparkles,
  Type,
  Zap,
  TrendingUp
} from 'lucide-react';

export default function App() {
  return (
    <div className="flex min-h-screen bg-surface font-body text-on-surface">
      {/* Sidebar */}
      <aside className="hidden md:flex h-screen w-64 bg-surface-container-lowest flex-col p-4 gap-2 sticky top-0 border-r border-surface-variant/50">
        <div className="px-2 py-4 mb-6">
          <h1 className="text-lg font-black text-slate-900 font-headline">Moderation Engine</h1>
          <p className="text-xs text-on-surface-variant font-medium">Safety & Compliance</p>
        </div>
        <nav className="flex-1 space-y-1">
          <a href="#" className="flex items-center gap-3 px-4 py-2.5 text-on-surface-variant font-medium text-sm hover:bg-surface-container-low transition-all active:translate-x-1 duration-150 rounded-lg">
            <LayoutDashboard size={20} />
            Dashboard
          </a>
          <a href="#" className="flex items-center gap-3 px-4 py-2.5 bg-surface-container-lowest text-primary custom-shadow rounded-lg font-medium text-sm transition-all active:translate-x-1 duration-150">
            <Flag size={20} />
            Reports
          </a>
          <a href="#" className="flex items-center gap-3 px-4 py-2.5 text-on-surface-variant font-medium text-sm hover:bg-surface-container-low transition-all active:translate-x-1 duration-150 rounded-lg">
            <ListChecks size={20} />
            Review Queue
          </a>
          <a href="#" className="flex items-center gap-3 px-4 py-2.5 text-on-surface-variant font-medium text-sm hover:bg-surface-container-low transition-all active:translate-x-1 duration-150 rounded-lg">
            <Type size={20} />
            Sensitive Words
          </a>
          <a href="#" className="flex items-center gap-3 px-4 py-2.5 text-on-surface-variant font-medium text-sm hover:bg-surface-container-low transition-all active:translate-x-1 duration-150 rounded-lg">
            <History size={20} />
            Audit Logs
          </a>
        </nav>
        <div className="mt-auto p-2">
          <button className="w-full bg-primary text-on-primary py-3 px-4 rounded-xl font-bold text-sm shadow-lg shadow-primary/20 hover:shadow-xl hover:shadow-primary/30 transition-all active:scale-95">
            Urgent Reviews
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 flex flex-col min-w-0">
        {/* Header */}
        <header className="w-full sticky top-0 z-50 glass-panel custom-shadow flex justify-between items-center px-6 py-3">
          <div className="flex items-center gap-8 flex-1">
            <span className="text-xl font-bold tracking-tighter text-primary font-headline">Luminous Mod</span>
            <div className="hidden lg:flex items-center bg-surface-container-low px-4 py-2 rounded-full w-96 transition-colors focus-within:bg-surface-container-lowest focus-within:ring-2 focus-within:ring-primary/20">
              <Search size={18} className="text-on-surface-variant mr-2" />
              <input
                type="text"
                placeholder="Search moderation items..."
                className="bg-transparent border-none focus:outline-none text-sm w-full placeholder:text-on-surface-variant/70 text-on-surface"
              />
            </div>
          </div>
          <div className="flex items-center gap-4">
            <button className="p-2 text-on-surface-variant hover:bg-surface-container-low transition-colors rounded-full active:scale-95 duration-200">
              <Bell size={20} />
            </button>
            <button className="p-2 text-on-surface-variant hover:bg-surface-container-low transition-colors rounded-full active:scale-95 duration-200">
              <HelpCircle size={20} />
            </button>
            <div className="h-8 w-8 rounded-full overflow-hidden border-2 border-primary/20 ml-2">
              <img
                src="https://lh3.googleusercontent.com/aida-public/AB6AXuCG1tp6BaCxIZnf0b3wjVr5V_JiK_JCZTWtvyZ86BjKa80zz4ICFwMhpRKnTTBrM4Wg9slIhucyC5qYCOFOwSHZbX3qJyMTZAe7RMvA0R3DPjhOWJ0-LIokSirmC_FM4B2_EIuWgnGc9iQYHq4kNpFhIA8RKeDQzr2kN6LXkYpzO9mXs5FdU080pTesB66QwuIhF-AOas89oyBO6db9aJwJXIWlt937qk3IcYSBtVTt0ZAuSTocSBJagECbCTr7vY9vegJLbooJLYA"
                alt="Moderator Avatar"
                className="h-full w-full object-cover"
              />
            </div>
          </div>
        </header>

        {/* Dashboard Content */}
        <div className="p-8 space-y-8 max-w-7xl mx-auto w-full">
          {/* Stats Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            <StatCard
              title="Pending Reports"
              value="1,284"
              trend="+12% from yesterday"
              trendUp={true}
              icon={<Clock size={48} strokeWidth={1} />}
              colorClass="text-on-surface-variant"
            />
            <StatCard
              title="Review Queue"
              value="452"
              trend="High priority active"
              trendIcon={<Zap size={14} />}
              icon={<ListTodo size={48} strokeWidth={1} />}
              colorClass="text-secondary"
            />
            <StatCard
              title="Accepted Today"
              value="8,912"
              trend="98.2% accuracy rate"
              trendIcon={<Gauge size={14} />}
              icon={<CheckCircle2 size={48} strokeWidth={1} />}
              colorClass="text-secondary"
            />
            <StatCard
              title="Enabled Terms"
              value="14,205"
              trend="Last updated 2h ago"
              trendIcon={<Clock size={14} />}
              icon={<Shield size={48} strokeWidth={1} />}
              colorClass="text-tertiary"
            />
          </div>

          {/* Workspace Area */}
          <div className="space-y-6">
            {/* Tabs & Filters */}
            <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
              <div className="flex p-1 bg-surface-container-low rounded-xl overflow-x-auto max-w-full">
                <button className="px-6 py-2 text-sm font-bold bg-surface-container-lowest text-primary custom-shadow rounded-lg transition-all whitespace-nowrap">
                  Global Workspace
                </button>
                <button className="px-6 py-2 text-sm font-medium text-on-surface-variant hover:text-on-surface transition-all whitespace-nowrap">
                  AI Training
                </button>
                <button className="px-6 py-2 text-sm font-medium text-on-surface-variant hover:text-on-surface transition-all whitespace-nowrap">
                  Community Appeals
                </button>
              </div>
              <button className="flex items-center gap-2 text-sm font-bold text-primary hover:text-primary-dim transition-colors whitespace-nowrap">
                <SlidersHorizontal size={18} />
                Advanced Filters
              </button>
            </div>

            {/* Main Content Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
              {/* Left Column: Feed */}
              <div className="lg:col-span-2 space-y-4">
                <div className="flex items-center justify-between mb-2 px-2">
                  <h2 className="text-xl font-headline font-bold tracking-tight text-on-surface">Active Moderation Feed</h2>
                  <span className="text-xs font-bold text-on-surface-variant uppercase tracking-widest">Real-time update</span>
                </div>

                {/* Feed Item 1 */}
                <div className="bg-surface-container-lowest rounded-xl p-5 custom-shadow flex gap-4 items-start group hover:bg-surface-container-low/50 transition-colors">
                  <div className="h-12 w-12 rounded-lg bg-error-container/20 flex items-center justify-center flex-shrink-0 text-error">
                    <AlertTriangle size={24} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex justify-between items-start mb-2">
                      <div>
                        <h4 className="font-bold text-on-surface font-headline">Potential Harassment Detected</h4>
                        <p className="text-xs text-on-surface-variant">Posted by @user_8829 • 4 minutes ago</p>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="px-2 py-1 bg-error-container text-[10px] font-bold rounded-md text-on-error-container">CRITICAL</span>
                        <button className="p-1 text-on-surface-variant hover:bg-surface-container-high rounded transition-colors">
                          <MoreVertical size={18} />
                        </button>
                      </div>
                    </div>
                    <p className="text-sm text-on-surface-variant mb-4 leading-relaxed bg-surface-container-low p-3 rounded-lg border-l-4 border-error">
                      "I cannot believe how stupid these people are. They should all just leave the platform before I make them."
                    </p>
                    <div className="flex flex-wrap items-center gap-3">
                      <button className="bg-secondary text-on-secondary px-4 py-2 rounded-lg text-xs font-bold hover:brightness-110 active:scale-95 transition-all">
                        Keep Content
                      </button>
                      <button className="bg-error text-on-error px-4 py-2 rounded-lg text-xs font-bold hover:brightness-110 active:scale-95 transition-all">
                        Remove & Warn
                      </button>
                      <button className="bg-surface-container-high text-on-surface px-4 py-2 rounded-lg text-xs font-bold hover:bg-surface-variant active:scale-95 transition-all">
                        Escalate
                      </button>
                    </div>
                  </div>
                </div>

                {/* Feed Item 2 */}
                <div className="bg-surface-container-lowest rounded-xl p-5 custom-shadow flex gap-4 items-start group hover:bg-surface-container-low/50 transition-colors">
                  <div className="h-12 w-12 rounded-lg bg-tertiary-container/20 flex items-center justify-center flex-shrink-0 text-tertiary">
                    <Sparkles size={24} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex justify-between items-start mb-2">
                      <div>
                        <h4 className="font-bold text-on-surface font-headline">Spam Pattern Recognition</h4>
                        <p className="text-xs text-on-surface-variant">System flag • 12 minutes ago</p>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="px-2 py-1 bg-tertiary-container text-[10px] font-bold rounded-md text-on-tertiary-container">MEDIUM</span>
                        <button className="p-1 text-on-surface-variant hover:bg-surface-container-high rounded transition-colors">
                          <MoreVertical size={18} />
                        </button>
                      </div>
                    </div>
                    <div className="flex items-center gap-4 p-3 bg-surface-container-low rounded-lg mb-4">
                      <div className="w-16 h-16 rounded overflow-hidden flex-shrink-0 border border-surface-variant">
                        <img
                          src="https://lh3.googleusercontent.com/aida-public/AB6AXuAyWcjvPWUOZrtSvFLheBXajtJtCVo6BBXAeR_NIqk6n373uVoQOF7wJ1_p8ysW6DCKvQ-UrPftTFs1s4W9TyqhDgBdv8jeAFQyZrj4rBYVVbG7NWbImr3hcaRIfzfTTXzkrNbUgchTsJehMCNNgUaYSUQioK_rzQwX8vMg6gIyJbSO9pRpUQbFusl5KqG49P6Kp8I5FI7PHW8hlA4d6yyKnjKJYm0-5obgPzvCy53r5ucH7za-ZrTC-pmN4vToO4Uzk_2x5CAnGJw"
                          alt="Flagged content"
                          className="w-full h-full object-cover"
                        />
                      </div>
                      <div>
                        <p className="text-sm font-medium text-on-surface">Repetitive image post detected across 15 sub-channels.</p>
                        <p className="text-xs text-on-surface-variant mt-1">Similarity score: 94.2%</p>
                      </div>
                    </div>
                    <div className="flex flex-wrap items-center gap-3">
                      <button className="bg-secondary text-on-secondary px-4 py-2 rounded-lg text-xs font-bold hover:brightness-110 active:scale-95 transition-all">
                        Approve All
                      </button>
                      <button className="bg-error text-on-error px-4 py-2 rounded-lg text-xs font-bold hover:brightness-110 active:scale-95 transition-all">
                        Bulk Ban
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              {/* Right Column: Widgets */}
              <div className="space-y-6">
                {/* Trending Risks */}
                <div className="bg-surface-container-lowest p-6 rounded-xl custom-shadow">
                  <h3 className="text-sm font-bold text-on-surface mb-4 font-headline">Trending Risks</h3>
                  <div className="space-y-5">
                    <RiskBar label="Hate Speech" value="+24%" percentage={75} colorClass="bg-error" textClass="text-error" />
                    <RiskBar label="Misinformation" value="+8%" percentage={40} colorClass="bg-tertiary" textClass="text-tertiary" />
                    <RiskBar label="Bot Spam" value="-15%" percentage={25} colorClass="bg-primary" textClass="text-primary" />
                  </div>
                </div>

                {/* System Health */}
                <div className="bg-primary-container/10 p-6 rounded-xl relative overflow-hidden border border-primary/10">
                  <div className="relative z-10">
                    <h3 className="text-sm font-bold text-on-primary-container mb-2 font-headline">System Health</h3>
                    <p className="text-xs text-on-primary-container/70 mb-4 leading-relaxed">Automation engines are operating at peak efficiency.</p>
                    <div className="flex items-center gap-2">
                      <span className="flex h-2 w-2 rounded-full bg-secondary animate-pulse"></span>
                      <span className="text-[10px] font-bold text-secondary uppercase tracking-wider">All Systems Optimal</span>
                    </div>
                  </div>
                  <div className="absolute -bottom-6 -right-6 text-primary-container/20">
                    <Cpu size={120} strokeWidth={1} />
                  </div>
                </div>

                {/* Leaderboard */}
                <div className="bg-surface-container-lowest p-6 rounded-xl custom-shadow">
                  <h3 className="text-sm font-bold text-on-surface mb-4 font-headline">Moderator Leaderboard</h3>
                  <div className="space-y-4">
                    <LeaderboardItem initials="JD" name="Jane Doe" actions="1,402" isTop={true} />
                    <LeaderboardItem initials="MS" name="Mark Smith" actions="1,215" isTop={false} />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}

// Subcomponents

function StatCard({ title, value, trend, trendUp, trendIcon, icon, colorClass }: any) {
  return (
    <div className="bg-surface-container-lowest p-6 rounded-xl custom-shadow relative overflow-hidden group">
      <div className={`absolute top-0 right-0 p-4 opacity-10 transition-transform group-hover:scale-110 duration-300 ${colorClass}`}>
        {icon}
      </div>
      <h3 className="text-sm font-label text-on-surface-variant font-medium">{title}</h3>
      <p className="text-3xl font-headline font-bold text-on-surface mt-2">{value}</p>
      <div className={`mt-4 flex items-center text-xs font-medium ${trendUp ? 'text-error' : colorClass}`}>
        {trendIcon || (trendUp ? <TrendingUp size={14} className="mr-1" /> : null)}
        <span className={trendIcon ? "ml-1" : ""}>{trend}</span>
      </div>
    </div>
  );
}

function RiskBar({ label, value, percentage, colorClass, textClass }: any) {
  return (
    <div>
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-3">
          <span className={`w-2 h-2 rounded-full ${colorClass}`}></span>
          <span className="text-sm font-medium text-on-surface">{label}</span>
        </div>
        <span className={`text-xs font-bold ${textClass}`}>{value}</span>
      </div>
      <div className="w-full bg-surface-container-low h-1.5 rounded-full overflow-hidden">
        <div className={`${colorClass} h-full rounded-full transition-all duration-1000 ease-out`} style={{ width: `${percentage}%` }}></div>
      </div>
    </div>
  );
}

function LeaderboardItem({ initials, name, actions, isTop }: any) {
  return (
    <div className="flex items-center gap-3 p-2 hover:bg-surface-container-low rounded-lg transition-colors">
      <div className="w-8 h-8 rounded-full bg-surface-container-high flex items-center justify-center text-xs font-bold text-on-surface">
        {initials}
      </div>
      <div className="flex-1">
        <p className="text-xs font-bold text-on-surface">{name}</p>
        <p className="text-[10px] text-on-surface-variant">{actions} actions</p>
      </div>
      {isTop && <Award size={16} className="text-tertiary fill-tertiary/20" />}
    </div>
  );
}
