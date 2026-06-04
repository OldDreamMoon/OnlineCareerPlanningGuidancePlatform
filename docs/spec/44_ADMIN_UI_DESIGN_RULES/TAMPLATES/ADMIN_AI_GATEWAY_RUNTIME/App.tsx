import React from 'react';
import {
  Rocket,
  LayoutGrid,
  Network,
  Route,
  Terminal,
  CreditCard,
  FileText,
  Bell,
  Settings,
  HelpCircle,
  TrendingUp,
  Star,
  MoreVertical,
  Plus,
  Book,
  LifeBuoy
} from 'lucide-react';

export default function App() {
  return (
    <div className="min-h-screen bg-[#f5f7f9] text-[#2c2f31] font-sans flex">
      {/* Sidebar */}
      <aside className="w-64 fixed left-0 top-0 h-screen bg-slate-50 flex flex-col py-6 z-40 border-r border-slate-100/50">
        <div className="px-6 mb-8">
          <h1 className="text-lg font-bold text-slate-900 font-display">AI Management</h1>
          <p className="text-xs text-slate-500 font-medium">Graduation Project</p>
        </div>
        
        <nav className="flex-1 space-y-1">
          <NavItem icon={<Rocket size={20} />} label="Operations" />
          <NavItem icon={<LayoutGrid size={20} />} label="Dashboard" />
          <NavItem icon={<Network size={20} />} label="Providers" />
          <NavItem icon={<Route size={20} />} label="Routing" />
          <NavItem icon={<Terminal size={20} />} label="Prompts" />
          <NavItem icon={<CreditCard size={20} />} label="Costs" active />
          <NavItem icon={<FileText size={20} />} label="Logs" />
        </nav>

        <div className="mt-auto px-4 space-y-2">
          <button className="w-full bg-cyan-500 hover:bg-cyan-600 text-white font-semibold py-2.5 rounded-xl mb-4 text-sm transition-colors shadow-sm">
            New App
          </button>
          <NavItem icon={<Book size={20} />} label="Docs" />
          <NavItem icon={<LifeBuoy size={20} />} label="Support" />
        </div>
      </aside>

      {/* Main Content Wrapper */}
      <div className="flex-1 ml-64 flex flex-col min-h-screen">
        {/* Top App Bar */}
        <header className="sticky top-0 z-50 bg-white/70 backdrop-blur-xl flex justify-between items-center px-8 h-16 shadow-[0px_10px_40px_rgba(44,47,49,0.04)]">
          <div className="flex items-center">
            <span className="text-xl font-black text-slate-900 font-display tracking-tight">Luminous AI Suite</span>
          </div>
          <div className="flex items-center gap-6">
            <div className="hidden md:flex items-center gap-2">
              <button className="text-slate-400 hover:text-slate-600 hover:bg-slate-100 p-2 rounded-full transition-colors">
                <Bell size={20} />
              </button>
              <button className="text-slate-400 hover:text-slate-600 hover:bg-slate-100 p-2 rounded-full transition-colors">
                <Settings size={20} />
              </button>
              <button className="text-slate-400 hover:text-slate-600 hover:bg-slate-100 p-2 rounded-full transition-colors">
                <HelpCircle size={20} />
              </button>
            </div>
            <div className="h-9 w-9 rounded-full bg-indigo-100 overflow-hidden ring-2 ring-white shadow-sm cursor-pointer">
              <img 
                src="https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?ixlib=rb-1.2.1&auto=format&fit=facearea&facepad=2&w=256&h=256&q=80" 
                alt="User profile" 
                className="w-full h-full object-cover"
              />
            </div>
          </div>
        </header>

        {/* Main Content */}
        <main className="flex-1 p-8 max-w-7xl mx-auto w-full">
          
          {/* Header Section */}
          <div className="flex flex-col md:flex-row justify-between items-start md:items-end mb-8 gap-4">
            <div>
              <h2 className="text-3xl font-extrabold tracking-tight text-slate-900 font-display mb-1">Costs & Quotas</h2>
              <p className="text-slate-500 font-medium text-sm">Detailed resource allocation and expenditure analysis.</p>
            </div>
            
            {/* Segmented Control */}
            <div className="bg-[#eef1f3] p-1 rounded-xl flex gap-1">
              <button className="px-5 py-2 text-sm font-semibold text-slate-500 hover:text-slate-900 transition-colors rounded-lg">Today</button>
              <button className="px-5 py-2 text-sm font-semibold bg-white text-cyan-600 shadow-sm rounded-lg">Week</button>
              <button className="px-5 py-2 text-sm font-semibold text-slate-500 hover:text-slate-900 transition-colors rounded-lg">Month</button>
            </div>
          </div>

          {/* Top Row: Overview & Top Contributors */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-8">
            
            {/* Expenditure Card (Dark) */}
            <div className="lg:col-span-2 relative overflow-hidden bg-[#0f172a] rounded-[1.5rem] p-8 text-white shadow-[0px_10px_40px_rgba(44,47,49,0.08)]">
              <div className="absolute top-0 right-0 w-64 h-64 bg-cyan-500/20 rounded-full -mr-32 -mt-32 blur-3xl pointer-events-none"></div>
              
              <div className="relative z-10 flex flex-col h-full justify-between">
                <div>
                  <div className="flex justify-between items-start mb-8">
                    <div>
                      <p className="text-cyan-400 text-xs font-bold uppercase tracking-widest mb-2">Total Expenditure</p>
                      <h3 className="text-5xl font-black font-display tracking-tight">$4,281.92</h3>
                    </div>
                    <div className="bg-white/10 backdrop-blur-md px-3 py-1.5 rounded-full flex items-center gap-1.5 border border-white/5">
                      <TrendingUp size={14} className="text-emerald-400" />
                      <span className="text-xs font-bold text-emerald-400">+12.4%</span>
                    </div>
                  </div>
                  
                  <div className="grid grid-cols-2 gap-8">
                    <div>
                      <p className="text-slate-400 text-xs font-semibold mb-1">Total API Calls</p>
                      <p className="text-2xl font-bold font-display">1,842,019</p>
                    </div>
                    <div>
                      <p className="text-slate-400 text-xs font-semibold mb-1">Active Users</p>
                      <p className="text-2xl font-bold font-display">428</p>
                    </div>
                  </div>
                </div>
                
                <div className="mt-10 pt-6 border-t border-slate-800/50">
                  <p className="text-slate-400 text-[10px] font-bold tracking-wider uppercase mb-3">Cost Projection</p>
                  <div className="w-full h-2.5 bg-slate-800/80 rounded-full overflow-hidden">
                    <div className="h-full bg-gradient-to-r from-cyan-500 to-blue-500 rounded-full" style={{ width: '68%' }}></div>
                  </div>
                  <div className="flex justify-between mt-2">
                    <span className="text-[10px] text-slate-500 font-medium">68% of monthly budget ($6,500)</span>
                    <span className="text-[10px] text-slate-500 font-medium">Estimated end: $6,140</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Top Contributors Card */}
            <div className="bg-white rounded-[1.5rem] p-6 shadow-[0px_10px_40px_rgba(44,47,49,0.04)] flex flex-col">
              <h4 className="text-sm font-bold text-slate-900 mb-6 flex items-center gap-2 font-display">
                <Star size={18} className="text-indigo-500 fill-indigo-500/20" />
                Top Cost Contributors
              </h4>
              
              <div className="space-y-5 flex-1">
                <ContributorRow rank={1} name="Main_App_Prod" id="40221" amount="$1,420" />
                <ContributorRow rank={2} name="Data_Science_Lab" id="99102" amount="$985" />
                <ContributorRow rank={3} name="Marketing_QA" id="11045" amount="$512" />
              </div>
              
              <button className="mt-6 w-full py-2.5 bg-[#eef1f3] hover:bg-[#e2e6e9] text-slate-700 text-xs font-bold rounded-xl transition-colors">
                View All Consumers
              </button>
            </div>
          </div>

          {/* Middle Row: Distribution Tags */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
            {/* Cost by Provider */}
            <div className="bg-white rounded-[1.5rem] p-6 shadow-[0px_10px_40px_rgba(44,47,49,0.04)]">
              <h4 className="text-sm font-bold text-slate-900 mb-4 font-display">Cost by Provider</h4>
              <div className="flex flex-wrap gap-2">
                <ProviderTag color="bg-indigo-500" name="OpenAI" percentage="54%" />
                <ProviderTag color="bg-emerald-500" name="Anthropic" percentage="28%" />
                <ProviderTag color="bg-amber-500" name="Google Cloud" percentage="12%" />
                <ProviderTag color="bg-slate-400" name="HuggingFace" percentage="6%" />
              </div>
            </div>

            {/* Cost by Model */}
            <div className="bg-white rounded-[1.5rem] p-6 shadow-[0px_10px_40px_rgba(44,47,49,0.04)]">
              <h4 className="text-sm font-bold text-slate-900 mb-4 font-display">Cost by Model</h4>
              <div className="flex flex-wrap gap-2">
                <ModelTag name="GPT-4 Turbo" amount="$2,104" />
                <ModelTag name="Claude 3 Opus" amount="$1,240" />
                <ModelTag name="GPT-3.5" amount="$480" />
                <ModelTag name="Gemini Pro" amount="$312" />
              </div>
            </div>
          </div>

          {/* Bottom Row: Quota Policies Table */}
          <div className="bg-white rounded-[1.5rem] shadow-[0px_10px_40px_rgba(44,47,49,0.04)] overflow-hidden">
            <div className="p-6 flex justify-between items-center bg-white">
              <h4 className="text-lg font-bold text-slate-900 font-display">Quota Policies</h4>
              <button className="flex items-center gap-1.5 bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-xl text-sm font-bold shadow-md shadow-indigo-500/20 transition-all">
                <Plus size={16} />
                Create Policy
              </button>
            </div>
            
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-[#f8fafc] border-y border-slate-100">
                    <th className="p-4 pl-6 text-[11px] font-bold uppercase tracking-wider text-slate-500">Policy Name</th>
                    <th className="p-4 text-[11px] font-bold uppercase tracking-wider text-slate-500">Target Groups</th>
                    <th className="p-4 text-[11px] font-bold uppercase tracking-wider text-slate-500">Hard Limit (USD)</th>
                    <th className="p-4 text-[11px] font-bold uppercase tracking-wider text-slate-500">Current Usage</th>
                    <th className="p-4 text-[11px] font-bold uppercase tracking-wider text-slate-500">Status</th>
                    <th className="p-4 pr-6 text-[11px] font-bold uppercase tracking-wider text-slate-500 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-50">
                  <PolicyRow 
                    name="Default Research Quota" 
                    desc="Applies to all labs"
                    groups={[{ label: 'DS', color: 'bg-indigo-100 text-indigo-700' }, { label: 'ML', color: 'bg-emerald-100 text-emerald-700' }, { label: 'QA', color: 'bg-amber-100 text-amber-700' }]}
                    limit="$500.00 / mo"
                    usagePct={72}
                    usageAmt="$360.40"
                    status="ACTIVE"
                    statusColor="bg-emerald-100 text-emerald-700"
                  />
                  <PolicyRow 
                    name="External Contractor Cap" 
                    desc="Vendor specific limit"
                    groups={[{ label: 'V1', color: 'bg-slate-200 text-slate-600' }]}
                    limit="$150.00 / wk"
                    usagePct={94}
                    usageAmt="$141.20"
                    status="WARNING"
                    statusColor="bg-rose-100 text-rose-700"
                    isWarning
                  />
                  <PolicyRow 
                    name="Internal Dev Sandbox" 
                    desc="Strict low-tier models only"
                    groups={[{ label: 'DEV', color: 'bg-indigo-100 text-indigo-700' }]}
                    limit="$1,000.00 / mo"
                    usagePct={12}
                    usageAmt="$120.00"
                    status="ACTIVE"
                    statusColor="bg-emerald-100 text-emerald-700"
                  />
                </tbody>
              </table>
            </div>
            
            <div className="p-4 px-6 bg-[#f8fafc] border-t border-slate-100 flex justify-between items-center text-xs font-medium text-slate-500 rounded-b-[1.5rem]">
              <span>Showing 3 of 12 policies</span>
              <div className="flex gap-2">
                <button className="px-3 py-1.5 rounded-lg bg-white border border-slate-200 hover:bg-slate-50 transition-colors font-semibold text-slate-700">Prev</button>
                <button className="px-3 py-1.5 rounded-lg bg-white border border-slate-200 hover:bg-slate-50 transition-colors font-semibold text-slate-700">Next</button>
              </div>
            </div>
          </div>

        </main>
      </div>
    </div>
  );
}

// --- Subcomponents ---

function NavItem({ icon, label, active = false }: { icon: React.ReactNode, label: string, active?: boolean }) {
  return (
    <a 
      href="#" 
      className={`mx-3 flex items-center px-3 py-2.5 rounded-xl transition-all duration-200 ease-in-out ${
        active 
          ? 'bg-white text-cyan-600 shadow-sm font-semibold' 
          : 'text-slate-500 hover:bg-slate-200/50 hover:text-slate-700 font-medium'
      }`}
    >
      <span className={`mr-3 ${active ? 'text-cyan-500' : 'text-slate-400'}`}>
        {icon}
      </span>
      <span className="text-sm">{label}</span>
    </a>
  );
}

function ContributorRow({ rank, name, id, amount }: { rank: number, name: string, id: string, amount: string }) {
  return (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-3">
        <div className="w-8 h-8 rounded-lg bg-indigo-50 flex items-center justify-center font-bold text-xs text-indigo-600">
          #{rank}
        </div>
        <div>
          <p className="text-sm font-bold text-slate-900">{name}</p>
          <p className="text-[10px] text-slate-500 font-medium">Project ID: {id}</p>
        </div>
      </div>
      <p className="text-sm font-bold text-slate-900">{amount}</p>
    </div>
  );
}

function ProviderTag({ color, name, percentage }: { color: string, name: string, percentage: string }) {
  return (
    <div className="flex items-center gap-2 bg-[#f8fafc] border border-slate-100 px-3 py-1.5 rounded-xl">
      <div className={`w-2 h-2 rounded-full ${color}`}></div>
      <span className="text-xs font-bold text-slate-700">{name}</span>
      <span className="text-xs text-slate-400 font-medium">{percentage}</span>
    </div>
  );
}

function ModelTag({ name, amount }: { name: string, amount: string }) {
  return (
    <div className="flex items-center gap-2 bg-[#eef1f3] px-3 py-1.5 rounded-xl">
      <span className="text-xs font-bold text-indigo-600">{name}</span>
      <span className="text-xs font-medium text-slate-500">{amount}</span>
    </div>
  );
}

function PolicyRow({ 
  name, desc, groups, limit, usagePct, usageAmt, status, statusColor, isWarning = false 
}: { 
  name: string, desc: string, groups: {label: string, color: string}[], limit: string, usagePct: number, usageAmt: string, status: string, statusColor: string, isWarning?: boolean 
}) {
  return (
    <tr className="hover:bg-slate-50/50 transition-colors group">
      <td className="p-4 pl-6">
        <p className="text-sm font-bold text-slate-900">{name}</p>
        <p className="text-[11px] text-slate-500 mt-0.5">{desc}</p>
      </td>
      <td className="p-4">
        <div className="flex -space-x-1.5">
          {groups.map((g, i) => (
            <div key={i} className={`w-6 h-6 rounded-full border-2 border-white flex items-center justify-center text-[8px] font-bold ${g.color}`}>
              {g.label}
            </div>
          ))}
        </div>
      </td>
      <td className="p-4 text-sm font-semibold text-slate-700">{limit}</td>
      <td className="p-4">
        <div className="w-32">
          <div className="flex justify-between text-[10px] mb-1.5">
            <span className={`font-bold ${isWarning ? 'text-rose-600' : 'text-slate-700'}`}>{usagePct}%</span>
            <span className="text-slate-500 font-medium">{usageAmt}</span>
          </div>
          <div className="w-full h-1.5 bg-slate-100 rounded-full overflow-hidden">
            <div className={`h-full rounded-full ${isWarning ? 'bg-rose-500' : 'bg-indigo-500'}`} style={{ width: `${usagePct}%` }}></div>
          </div>
        </div>
      </td>
      <td className="p-4">
        <span className={`px-2.5 py-1 rounded-md text-[10px] font-bold tracking-wide ${statusColor}`}>
          {status}
        </span>
      </td>
      <td className="p-4 pr-6 text-right">
        <button className="text-slate-400 hover:text-slate-700 transition-colors p-1 rounded-lg hover:bg-slate-100 opacity-0 group-hover:opacity-100 focus:opacity-100">
          <MoreVertical size={18} />
        </button>
      </td>
    </tr>
  );
}
