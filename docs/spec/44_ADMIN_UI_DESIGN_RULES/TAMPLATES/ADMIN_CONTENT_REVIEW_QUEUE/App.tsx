import React from 'react';
import {
  LayoutDashboard,
  Flag,
  MessageSquareWarning,
  ShieldAlert,
  History,
  AlertCircle,
  Search,
  Bell,
  HelpCircle,
  User,
  ChevronRight,
  ArrowLeft,
  ArrowRight,
  Ban,
  CheckCircle2,
  AlertTriangle,
  Link as LinkIcon,
  CircleDollarSign,
  BellRing
} from 'lucide-react';

export default function App() {
  return (
    <div className="bg-surface font-body text-on-surface flex min-h-screen">
      {/* Sidebar */}
      <aside className="h-screen w-64 bg-slate-50 flex flex-col p-4 gap-2 sticky top-0 shrink-0">
        <div className="px-2 py-4 mb-4">
          <h1 className="text-lg font-black text-slate-900">Moderation Engine</h1>
          <p className="text-xs text-slate-500 font-medium">Safety & Compliance</p>
        </div>
        <nav className="flex-1 space-y-1">
          <NavItem icon={<LayoutDashboard size={20} />} label="Dashboard" />
          <NavItem icon={<Flag size={20} />} label="Reports" />
          <NavItem icon={<MessageSquareWarning size={20} />} label="Review Queue" active />
          <NavItem icon={<ShieldAlert size={20} />} label="Sensitive Words" />
          <NavItem icon={<History size={20} />} label="Audit Logs" />
        </nav>
        <div className="mt-auto p-4 bg-primary-container/20 rounded-xl">
          <button className="w-full py-2 bg-primary text-on-primary font-bold rounded-lg flex items-center justify-center gap-2 text-sm hover:bg-primary-dim transition-colors">
            <AlertCircle size={16} />
            Urgent Reviews
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 flex flex-col min-w-0">
        {/* Header */}
        <header className="w-full sticky top-0 z-50 bg-white/70 backdrop-blur-xl flex justify-between items-center px-6 py-3 shadow-[0px_10px_40px_rgba(44,47,49,0.06)]">
          <div className="flex items-center gap-6">
            <span className="text-xl font-bold tracking-tighter text-cyan-600">Luminous Mod</span>
            <div className="relative w-64">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={18} />
              <input
                type="text"
                placeholder="Search queue..."
                className="w-full pl-10 pr-4 py-1.5 bg-slate-100/50 border-none rounded-full text-sm focus:ring-2 focus:ring-cyan-500 focus:bg-white transition-all outline-none"
              />
            </div>
          </div>
          <div className="flex items-center gap-4">
            <button className="p-2 text-slate-500 hover:bg-slate-50 transition-colors rounded-full relative">
              <Bell size={20} />
              <span className="absolute top-2 right-2 w-2 h-2 bg-error rounded-full"></span>
            </button>
            <button className="p-2 text-slate-500 hover:bg-slate-50 transition-colors rounded-full">
              <HelpCircle size={20} />
            </button>
            <div className="h-8 w-8 rounded-full bg-slate-200 overflow-hidden ring-2 ring-white ring-offset-2 ring-offset-slate-100">
              <img
                src="https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?ixlib=rb-1.2.1&auto=format&fit=facearea&facepad=2&w=256&h=256&q=80"
                alt="Profile"
                className="w-full h-full object-cover"
              />
            </div>
          </div>
        </header>

        {/* Page Content */}
        <div className="p-8 space-y-8">
          {/* Page Header */}
          <div className="flex justify-between items-end">
            <div>
              <h2 className="font-headline text-3xl font-extrabold tracking-tight text-on-surface">Review Queue</h2>
              <p className="text-on-surface-variant mt-1">Manage pending content items and safety violations.</p>
            </div>
            <div className="flex gap-2 bg-surface-container-low p-1 rounded-xl">
              <button className="px-4 py-1.5 bg-white shadow-sm rounded-lg text-sm font-semibold text-primary">Pending (128)</button>
              <button className="px-4 py-1.5 text-sm font-medium text-on-surface-variant hover:text-on-surface transition-colors">Resolved</button>
              <button className="px-4 py-1.5 text-sm font-medium text-on-surface-variant hover:text-on-surface transition-colors">Archived</button>
            </div>
          </div>

          {/* Grid Layout */}
          <div className="grid grid-cols-12 gap-6">
            {/* Left Column: Filters */}
            <div className="col-span-12 lg:col-span-3 space-y-6">
              <section className="bg-surface-container-lowest rounded-xl p-6 shadow-[0px_10px_40px_rgba(44,47,49,0.06)]">
                <h3 className="font-headline text-lg font-bold mb-4">Source Type</h3>
                <div className="space-y-2">
                  <Checkbox label="User Comments" defaultChecked />
                  <Checkbox label="Profile Bios" defaultChecked />
                  <Checkbox label="Forum Posts" />
                  <Checkbox label="Direct Messages" />
                </div>

                <hr className="my-6 border-outline-variant/15" />

                <h3 className="font-headline text-lg font-bold mb-4">Risk Severity</h3>
                <div className="space-y-3">
                  <RiskButton label="High Risk" count={24} colorClass="bg-error-container text-on-error-container" />
                  <RiskButton label="Medium Risk" count={56} colorClass="bg-tertiary-container text-on-tertiary-container" />
                  <RiskButton label="Low Risk" count={48} colorClass="bg-secondary-container text-on-secondary-container" />
                </div>
              </section>

              <section className="bg-primary p-6 rounded-xl text-on-primary shadow-[0px_10px_40px_rgba(70,71,211,0.2)]">
                <div className="flex items-center gap-3 mb-2">
                  <AlertCircle className="text-secondary-container" size={20} />
                  <span className="font-bold text-sm uppercase tracking-widest">Efficiency Tip</span>
                </div>
                <p className="text-sm text-on-primary/80 leading-relaxed">
                  Use shortcut <kbd className="bg-white/20 px-1.5 py-0.5 rounded font-mono text-xs">A</kbd> for Approve and <kbd className="bg-white/20 px-1.5 py-0.5 rounded font-mono text-xs">R</kbd> for Reject.
                </p>
              </section>
            </div>

            {/* Right Column: Table & Details */}
            <div className="col-span-12 lg:col-span-9 space-y-6">
              {/* Table */}
              <div className="bg-surface-container-lowest rounded-xl overflow-hidden shadow-[0px_10px_40px_rgba(44,47,49,0.06)]">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-surface-container-low/50">
                      <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-on-surface-variant">Content Item</th>
                      <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-on-surface-variant">Source</th>
                      <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-on-surface-variant">Risk Level</th>
                      <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-on-surface-variant text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant/10">
                    <TableRow
                      content="This system is absolute garbage, I'm going to find where you live and..."
                      author="User_7721"
                      time="3 mins ago"
                      source="Product Comment"
                      riskLevel="CRITICAL"
                      riskColor="bg-error text-error"
                      riskType="Threat detection"
                    />
                    <TableRow
                      content="Visit bit.ly/free-tokens-now to double your wallet balance instantly!"
                      author="SpamKing_99"
                      time="8 mins ago"
                      source="Forum Post"
                      riskLevel="MEDIUM"
                      riskColor="bg-tertiary text-tertiary"
                      riskType="Potential Scam"
                      active
                    />
                    <TableRow
                      content="I think the updated UI is actually quite confusing compared to..."
                      author="FeedbackLoop"
                      time="12 mins ago"
                      source="App Review"
                      riskLevel="LOW"
                      riskColor="bg-secondary text-secondary"
                      riskType="Keyword Flag"
                    />
                  </tbody>
                </table>
                <div className="px-6 py-4 bg-surface-container-low/30 border-t border-outline-variant/10 flex justify-between items-center">
                  <span className="text-xs font-medium text-on-surface-variant">Showing 3 of 128 items</span>
                  <div className="flex gap-2">
                    <button className="p-1.5 rounded-lg border border-outline-variant/30 hover:bg-white transition-all text-on-surface-variant">
                      <ArrowLeft size={16} />
                    </button>
                    <button className="p-1.5 rounded-lg border border-outline-variant/30 hover:bg-white transition-all text-on-surface-variant">
                      <ArrowRight size={16} />
                    </button>
                  </div>
                </div>
              </div>

              {/* Details Panel */}
              <div className="bg-surface-container-lowest rounded-xl p-8 shadow-[0px_20px_60px_rgba(44,47,49,0.12)] border border-primary/10 relative overflow-hidden">
                {/* Decorative background glow */}
                <div className="absolute top-0 right-0 w-64 h-64 bg-tertiary/5 rounded-full blur-3xl -mr-20 -mt-20 pointer-events-none"></div>
                
                <div className="flex justify-between items-start mb-8 relative z-10">
                  <div>
                    <div className="flex items-center gap-3 mb-2">
                      <span className="px-3 py-1 bg-tertiary-container/30 text-tertiary font-bold text-xs rounded-full uppercase tracking-widest">Medium Risk</span>
                      <span className="text-on-surface-variant text-sm font-medium">Flagged by AI Scanner v4.2</span>
                    </div>
                    <h3 className="font-headline text-2xl font-extrabold text-on-surface">Content Detail Analysis</h3>
                  </div>
                  <div className="flex gap-3">
                    <button className="px-6 py-2 bg-error text-white font-bold rounded-lg flex items-center gap-2 hover:bg-error/90 transition-colors shadow-sm">
                      <Ban size={18} />
                      Reject
                    </button>
                    <button className="px-6 py-2 bg-secondary text-white font-bold rounded-lg flex items-center gap-2 hover:bg-secondary-dim transition-colors shadow-sm">
                      <CheckCircle2 size={18} />
                      Approve
                    </button>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-8 relative z-10">
                  <div className="space-y-6">
                    <div className="p-5 bg-surface rounded-xl border border-outline-variant/10 italic text-on-surface leading-relaxed shadow-inner">
                      "Visit bit.ly/free-tokens-now to double your wallet balance instantly! Join 10k users who already claimed their prize. Don't miss out on this limited time crypto event hosted by the Luminous team."
                    </div>
                    <div className="flex gap-4">
                      <div className="flex-1 p-4 bg-surface-container-low rounded-xl">
                        <p className="text-[10px] font-bold text-on-surface-variant uppercase tracking-widest mb-1">Author Reputation</p>
                        <div className="flex items-center gap-2">
                          <AlertTriangle className="text-error" size={18} />
                          <span className="font-bold text-on-surface">Low (New User)</span>
                        </div>
                      </div>
                      <div className="flex-1 p-4 bg-surface-container-low rounded-xl">
                        <p className="text-[10px] font-bold text-on-surface-variant uppercase tracking-widest mb-1">Previous Violations</p>
                        <div className="flex items-center gap-2">
                          <History className="text-on-surface-variant" size={18} />
                          <span className="font-bold text-on-surface">0 Records</span>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="space-y-4">
                    <h4 className="font-headline font-bold text-sm text-on-surface-variant uppercase tracking-widest">Reasoning & Context</h4>
                    <ul className="space-y-4">
                      <ReasoningItem
                        icon={<LinkIcon size={18} className="text-tertiary" />}
                        title="Unverified Short-link"
                        description="Contains a bit.ly link pointing to an external domain not on the whitelist."
                      />
                      <ReasoningItem
                        icon={<CircleDollarSign size={18} className="text-tertiary" />}
                        title="Crypto Keywords"
                        description='Matches patterns for "giveaway" and "tokens" scams.'
                      />
                      <ReasoningItem
                        icon={<BellRing size={18} className="text-tertiary" />}
                        title="Urgency Tactics"
                        description={'Uses FOMO language ("limited time", "don\'t miss out").'}
                      />
                    </ul>
                    <div className="mt-6">
                      <p className="text-xs font-bold text-on-surface-variant uppercase mb-2">Internal Moderator Note</p>
                      <textarea
                        className="w-full h-24 bg-surface-container-low border-none rounded-xl p-3 text-sm focus:ring-2 focus:ring-primary focus:bg-white transition-all outline-none resize-none"
                        placeholder="Add a reason for your decision..."
                      ></textarea>
                    </div>
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

// --- Subcomponents ---

function NavItem({ icon, label, active = false }: { icon: React.ReactNode; label: string; active?: boolean }) {
  return (
    <a
      href="#"
      className={`flex items-center gap-3 px-3 py-2 rounded-lg transition-all font-medium text-sm ${
        active
          ? 'bg-white text-cyan-600 shadow-sm'
          : 'text-slate-600 hover:bg-cyan-50 hover:text-cyan-700'
      }`}
    >
      {icon}
      <span>{label}</span>
    </a>
  );
}

function Checkbox({ label, defaultChecked = false }: { label: string; defaultChecked?: boolean }) {
  return (
    <label className="flex items-center gap-3 p-2 rounded-lg hover:bg-surface-container-low cursor-pointer transition-colors">
      <input
        type="checkbox"
        defaultChecked={defaultChecked}
        className="rounded border-outline-variant text-primary focus:ring-primary h-4 w-4"
      />
      <span className="text-sm font-medium text-on-surface">{label}</span>
    </label>
  );
}

function RiskButton({ label, count, colorClass }: { label: string; count: number; colorClass: string }) {
  return (
    <button className={`w-full flex justify-between items-center px-4 py-2 rounded-lg text-sm font-bold ${colorClass}`}>
      <span>{label}</span>
      <span className="bg-white/30 px-2 rounded-full text-xs py-0.5">{count}</span>
    </button>
  );
}

function TableRow({
  content,
  author,
  time,
  source,
  riskLevel,
  riskColor,
  riskType,
  active = false,
}: {
  content: string;
  author: string;
  time: string;
  source: string;
  riskLevel: string;
  riskColor: string;
  riskType: string;
  active?: boolean;
}) {
  const [dotColor, textColor] = riskColor.split(' ');
  
  return (
    <tr
      className={`transition-colors group cursor-pointer ${
        active ? 'bg-primary/5 hover:bg-primary/10 ring-1 ring-inset ring-primary/20' : 'hover:bg-slate-50'
      }`}
    >
      <td className="px-6 py-5">
        <div className="flex flex-col gap-1 max-w-md">
          <span className="text-sm font-semibold text-on-surface truncate">{content}</span>
          <span className="text-xs text-on-surface-variant flex items-center gap-1">
            <User size={12} />
            {author} • {time}
          </span>
        </div>
      </td>
      <td className="px-6 py-5">
        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-surface-container-low text-on-surface-variant">
          {source}
        </span>
      </td>
      <td className="px-6 py-5">
        <div className="flex items-center gap-2">
          <span className={`w-2 h-2 rounded-full ${dotColor}`}></span>
          <span className={`text-sm font-bold ${textColor}`}>{riskLevel}</span>
        </div>
        <span className="text-[10px] uppercase font-bold text-on-surface-variant block mt-1 tracking-tight">
          {riskType}
        </span>
      </td>
      <td className="px-6 py-5 text-right">
        {active ? (
          <div className="flex items-center justify-end">
            <span className="text-xs font-bold text-primary mr-2 italic">Active View</span>
            <button className="p-2 bg-primary text-white rounded-lg transition-all shadow-sm">
              <ChevronRight size={18} />
            </button>
          </div>
        ) : (
          <button className="p-2 hover:bg-surface-container-high rounded-lg transition-all text-on-surface-variant group-hover:text-primary">
            <ChevronRight size={18} />
          </button>
        )}
      </td>
    </tr>
  );
}

function ReasoningItem({ icon, title, description }: { icon: React.ReactNode; title: string; description: string }) {
  return (
    <li className="flex items-start gap-3">
      <div className="mt-0.5">{icon}</div>
      <div>
        <p className="text-sm font-bold text-on-surface">{title}</p>
        <p className="text-xs text-on-surface-variant leading-relaxed">{description}</p>
      </div>
    </li>
  );
}

