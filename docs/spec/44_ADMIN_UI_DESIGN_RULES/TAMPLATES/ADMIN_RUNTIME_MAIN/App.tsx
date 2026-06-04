import React from 'react';
import {
  Search, Bell, Settings, HelpCircle, Network, Flag, Sliders, Brain, ShieldAlert,
  Terminal, LifeBuoy, History, Plus, Activity, TrendingUp, ToggleRight, Sparkles,
  ShieldCheck, Zap, Bot, Shield, MoreVertical, ArrowRight
} from 'lucide-react';

// --- Components ---

const TopNav = () => (
  <header className="fixed top-0 w-full z-50 bg-white/70 backdrop-blur-xl shadow-sm h-16 px-6 flex justify-between items-center">
    <div className="flex items-center gap-8">
      <span className="text-xl font-bold tracking-tight text-slate-900">Runtime Config Center</span>
      <div className="hidden md:flex items-center bg-slate-100 rounded-full px-4 py-1.5 gap-2 w-96">
        <Search className="w-4 h-4 text-slate-400" />
        <input
          className="bg-transparent border-none focus:outline-none text-sm w-full text-slate-700 placeholder-slate-400"
          placeholder="Search parameters..."
          type="text"
        />
      </div>
    </div>
    <div className="flex items-center gap-4">
      <button className="p-2 text-slate-500 hover:bg-slate-100 transition-colors rounded-full">
        <Bell className="w-5 h-5" />
      </button>
      <button className="p-2 text-slate-500 hover:bg-slate-100 transition-colors rounded-full">
        <Settings className="w-5 h-5" />
      </button>
      <button className="p-2 text-slate-500 hover:bg-slate-100 transition-colors rounded-full">
        <HelpCircle className="w-5 h-5" />
      </button>
      <div className="h-8 w-8 rounded-full bg-indigo-100 overflow-hidden ml-2 border border-slate-200">
        <img
          alt="User profile"
          src="https://api.dicebear.com/7.x/avataaars/svg?seed=Felix"
          className="w-full h-full object-cover"
        />
      </div>
    </div>
  </header>
);

const Sidebar = () => (
  <aside className="fixed left-0 top-16 h-[calc(100vh-64px)] w-64 bg-slate-50 flex flex-col py-4 space-y-2 z-40 border-r border-slate-100">
    <div className="px-6 py-4 mb-4">
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-xl bg-indigo-600 flex items-center justify-center text-white shadow-sm">
          <Network className="w-6 h-6" />
        </div>
        <div>
          <p className="font-headline font-bold text-sm text-slate-900">Admin Core</p>
          <p className="text-[10px] text-slate-500 font-medium tracking-wider">v2.4.0-STABLE</p>
        </div>
      </div>
    </div>
    <nav className="flex-1 space-y-1">
      <a href="#" className="flex items-center gap-3 px-4 py-2.5 bg-indigo-50 text-indigo-700 font-semibold rounded-lg mx-2 transition-all">
        <Flag className="w-5 h-5" />
        <span className="text-sm">Business Flags</span>
      </a>
      <a href="#" className="flex items-center gap-3 px-4 py-2.5 text-slate-600 hover:bg-slate-100 mx-2 rounded-lg transition-all">
        <Sliders className="w-5 h-5" />
        <span className="text-sm">Operations Parameters</span>
      </a>
      <a href="#" className="flex items-center gap-3 px-4 py-2.5 text-slate-600 hover:bg-slate-100 mx-2 rounded-lg transition-all">
        <Brain className="w-5 h-5" />
        <span className="text-sm">AI Channels</span>
      </a>
      <a href="#" className="flex items-center gap-3 px-4 py-2.5 text-slate-600 hover:bg-slate-100 mx-2 rounded-lg transition-all">
        <ShieldAlert className="w-5 h-5" />
        <span className="text-sm">Risk Degradation</span>
      </a>
    </nav>
    <div className="px-4 py-4 space-y-1 border-t border-slate-200">
      <a href="#" className="flex items-center gap-3 px-4 py-2 text-slate-600 hover:bg-slate-100 rounded-lg transition-colors">
        <Terminal className="w-5 h-5" />
        <span className="text-sm">Logs</span>
      </a>
      <a href="#" className="flex items-center gap-3 px-4 py-2 text-slate-600 hover:bg-slate-100 rounded-lg transition-colors">
        <LifeBuoy className="w-5 h-5" />
        <span className="text-sm">Support</span>
      </a>
      <div className="mt-4 px-2">
        <button className="w-full py-2.5 bg-gradient-to-br from-indigo-600 to-indigo-700 text-white rounded-xl font-bold text-sm shadow-md hover:shadow-lg transition-all active:scale-95">
          Deploy Changes
        </button>
      </div>
    </div>
  </aside>
);

const StatCard = ({ title, value, unit, icon: Icon, trendIcon: TrendIcon, trendText, colorClass, bgClass, iconColorClass }: any) => (
  <div className="relative overflow-hidden bg-white p-6 rounded-2xl shadow-sm group border border-slate-100">
    <div className={`absolute top-0 right-0 w-32 h-32 ${bgClass} rounded-full -mr-16 -mt-16 opacity-50 group-hover:scale-110 transition-transform duration-500`}></div>
    <div className="flex items-start justify-between relative z-10">
      <div>
        <p className="text-xs font-bold text-slate-400 uppercase tracking-widest">{title}</p>
        <h3 className="font-headline text-3xl font-extrabold mt-1 text-slate-900">
          {value}<span className="text-lg text-slate-500 ml-1">{unit}</span>
        </h3>
      </div>
      <div className={`p-2 ${bgClass} ${iconColorClass} rounded-xl`}>
        <Icon className="w-6 h-6" />
      </div>
    </div>
    <div className={`mt-4 flex items-center gap-2 text-xs font-medium ${colorClass}`}>
      {TrendIcon ? <TrendIcon className="w-4 h-4" /> : <span className={`w-2 h-2 rounded-full ${colorClass.replace('text-', 'bg-')}`}></span>}
      <span>{trendText}</span>
    </div>
  </div>
);

const Toggle = ({ active }: { active: boolean }) => (
  <div className={`w-12 h-6 rounded-full relative cursor-pointer transition-colors shadow-inner ${active ? 'bg-indigo-600' : 'bg-slate-200'}`}>
    <div className={`absolute top-1 w-4 h-4 bg-white rounded-full shadow-sm transition-all ${active ? 'right-1' : 'left-1'}`}></div>
  </div>
);

const FlagItem = ({ icon: Icon, title, desc, tags, status, active, iconBg, iconColor, statusColor }: any) => (
  <div className="group bg-white p-5 rounded-2xl shadow-sm border border-slate-100 hover:shadow-md transition-all flex items-center justify-between">
    <div className="flex items-center gap-5">
      <div className={`w-12 h-12 ${iconBg} rounded-xl flex items-center justify-center ${iconColor}`}>
        <Icon className="w-6 h-6" />
      </div>
      <div>
        <h4 className="font-headline font-bold text-base text-slate-900">{title}</h4>
        <p className="text-xs text-slate-500 mt-0.5">{desc}</p>
        <div className="flex gap-2 mt-2">
          {tags.map((tag: any) => (
            <span key={tag.label} className={`px-2 py-0.5 text-[10px] font-bold rounded uppercase ${tag.bg} ${tag.text}`}>
              {tag.label}
            </span>
          ))}
        </div>
      </div>
    </div>
    <div className="flex items-center gap-6">
      <div className="text-right">
        <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Health</p>
        <div className="flex items-center justify-end gap-1 mt-0.5">
          <span className={`w-1.5 h-1.5 rounded-full ${statusColor.replace('text-', 'bg-')}`}></span>
          <span className={`text-xs font-bold ${statusColor}`}>{status}</span>
        </div>
      </div>
      <Toggle active={active} />
      <button className="p-2 text-slate-400 hover:text-slate-700 transition-colors">
        <MoreVertical className="w-5 h-5" />
      </button>
    </div>
  </div>
);

// --- Main App ---

export default function App() {
  return (
    <div className="min-h-screen bg-[#f5f7f9] font-sans text-slate-900">
      <TopNav />
      <Sidebar />
      
      <main className="ml-64 pt-24 px-8 pb-12">
        {/* Header Section */}
        <header className="mb-10 flex justify-between items-end">
          <div>
            <h1 className="font-headline text-3xl font-extrabold tracking-tight text-slate-900">Business Flags</h1>
            <p className="text-slate-500 mt-1">Manage global feature toggles and operational runtime states.</p>
          </div>
          <div className="flex gap-3">
            <button className="flex items-center gap-2 px-4 py-2 bg-white text-indigo-600 font-semibold rounded-xl text-sm shadow-sm border border-slate-200 hover:bg-slate-50 transition-all">
              <History className="w-4 h-4" />
              Audit Log
            </button>
            <button className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white font-semibold rounded-xl text-sm shadow-md hover:bg-indigo-700 transition-all">
              <Plus className="w-4 h-4" />
              Create New Flag
            </button>
          </div>
        </header>

        {/* Stats Grid */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-10">
          <StatCard 
            title="Global Health" value="99.9" unit="%" 
            icon={Activity} trendIcon={TrendingUp} trendText="Stable Performance"
            colorClass="text-emerald-600" bgClass="bg-emerald-100" iconColorClass="text-emerald-600"
          />
          <StatCard 
            title="Active Flags" value="124" 
            icon={ToggleRight} trendText="8 Deployed today"
            colorClass="text-slate-500" bgClass="bg-indigo-100" iconColorClass="text-indigo-600"
          />
          <StatCard 
            title="AI Channels" value="14" 
            icon={Sparkles} trendText="4 under observation"
            colorClass="text-slate-500" bgClass="bg-amber-100" iconColorClass="text-amber-600"
          />
          <StatCard 
            title="Risk Level" value="Low" 
            icon={ShieldCheck} trendText="No active degradations"
            colorClass="text-slate-500" bgClass="bg-rose-100" iconColorClass="text-rose-600"
          />
        </div>

        {/* Bento Grid Layout */}
        <div className="grid grid-cols-12 gap-6">
          
          {/* Primary Config Panel (8 cols) */}
          <section className="col-span-12 lg:col-span-8 space-y-6">
            {/* Tabs */}
            <div className="bg-white rounded-xl shadow-sm p-1 flex gap-1 w-max mb-2 border border-slate-100">
              <button className="px-6 py-2 bg-indigo-600 text-white rounded-lg font-semibold text-sm transition-all shadow-sm">Active Flags</button>
              <button className="px-6 py-2 text-slate-500 hover:bg-slate-50 rounded-lg font-semibold text-sm transition-all">Scheduled</button>
              <button className="px-6 py-2 text-slate-500 hover:bg-slate-50 rounded-lg font-semibold text-sm transition-all">Archived</button>
            </div>

            {/* Config Cards List */}
            <div className="space-y-4">
              <FlagItem 
                icon={Zap} title="v3_payment_gateway_fallback" 
                desc="Enables automatic routing to backup payment processor on high latency."
                tags={[{label: 'CORE', bg: 'bg-slate-100', text: 'text-slate-600'}, {label: 'PAYMENTS', bg: 'bg-slate-100', text: 'text-slate-600'}]}
                status="Optimal" statusColor="text-emerald-500" active={true}
                iconBg="bg-emerald-50" iconColor="text-emerald-500"
              />
              <FlagItem 
                icon={Bot} title="ai_chat_model_switch_experimental" 
                desc="Routes 5% of traffic to GPT-4o for performance benchmarking."
                tags={[{label: 'EXPERIMENTAL', bg: 'bg-amber-100', text: 'text-amber-700'}, {label: 'AI_CHANNELS', bg: 'bg-slate-100', text: 'text-slate-600'}]}
                status="Warning" statusColor="text-amber-500" active={true}
                iconBg="bg-amber-50" iconColor="text-amber-500"
              />
              <FlagItem 
                icon={Shield} title="force_mfa_region_ap_southeast" 
                desc="Global enforcement of multi-factor auth for AP-Southeast region users."
                tags={[{label: 'SECURITY', bg: 'bg-slate-100', text: 'text-slate-600'}, {label: 'REGIONAL', bg: 'bg-slate-100', text: 'text-slate-600'}]}
                status="Disabled" statusColor="text-slate-400" active={false}
                iconBg="bg-rose-50" iconColor="text-rose-500"
              />
            </div>

            {/* Load More Button */}
            <button className="w-full py-4 bg-slate-200/50 text-slate-600 font-bold text-sm rounded-2xl hover:bg-slate-200 transition-all border border-slate-200/50">
              View All 124 Active Flags
            </button>
          </section>

          {/* Sidebar Panels (4 cols) */}
          <aside className="col-span-12 lg:col-span-4 space-y-6">
            
            {/* Activity Feed */}
            <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
              <div className="flex items-center justify-between mb-6">
                <h3 className="font-headline font-bold text-lg text-slate-900">System Activity</h3>
                <span className="text-[10px] font-bold text-indigo-600 px-2 py-0.5 bg-indigo-50 rounded uppercase">Live</span>
              </div>
              
              <div className="space-y-6">
                <div className="flex gap-4">
                  <div className="relative">
                    <div className="w-2 h-2 bg-indigo-500 rounded-full ring-4 ring-indigo-50 mt-1.5 z-10 relative"></div>
                    <div className="absolute top-5 bottom-[-24px] left-1 w-0.5 bg-slate-100"></div>
                  </div>
                  <div>
                    <p className="text-xs font-bold text-slate-900">Chen Wei <span className="font-normal text-slate-500">updated</span> ai_channels_v2</p>
                    <p className="text-[10px] text-slate-400 mt-0.5">2 minutes ago</p>
                  </div>
                </div>
                
                <div className="flex gap-4">
                  <div className="relative">
                    <div className="w-2 h-2 bg-emerald-500 rounded-full ring-4 ring-emerald-50 mt-1.5 z-10 relative"></div>
                    <div className="absolute top-5 bottom-[-24px] left-1 w-0.5 bg-slate-100"></div>
                  </div>
                  <div>
                    <p className="text-xs font-bold text-slate-900">System <span className="font-normal text-slate-500">auto-scaled</span> ops_param_load</p>
                    <p className="text-[10px] text-slate-400 mt-0.5">14 minutes ago</p>
                  </div>
                </div>
                
                <div className="flex gap-4">
                  <div className="relative">
                    <div className="w-2 h-2 bg-rose-500 rounded-full ring-4 ring-rose-50 mt-1.5 z-10 relative"></div>
                  </div>
                  <div>
                    <p className="text-xs font-bold text-slate-900">Risk Degradation <span className="font-normal text-slate-500">triggered for</span> region_us_east</p>
                    <p className="text-[10px] text-slate-400 mt-0.5">1 hour ago</p>
                  </div>
                </div>
              </div>
              
              <button className="mt-8 text-indigo-600 font-bold text-xs flex items-center gap-1 hover:underline">
                View Full History <ArrowRight className="w-4 h-4" />
              </button>
            </div>

            {/* System Usage Card */}
            <div className="relative bg-gradient-to-br from-indigo-600 to-indigo-900 p-6 rounded-2xl shadow-xl overflow-hidden text-white">
              <div className="absolute top-0 right-0 w-32 h-32 bg-white/10 rounded-full -mr-16 -mt-16"></div>
              <h3 className="font-headline font-bold text-lg mb-1 relative z-10">Resource Usage</h3>
              <p className="text-xs text-indigo-200 mb-6 relative z-10">Center workload across 4 clusters</p>
              
              <div className="space-y-5 relative z-10">
                <div>
                  <div className="flex justify-between text-[10px] font-bold uppercase mb-2 text-indigo-100">
                    <span>CPU Allocation</span>
                    <span>64%</span>
                  </div>
                  <div className="h-2 bg-black/20 rounded-full overflow-hidden">
                    <div className="h-full bg-emerald-400 w-[64%] shadow-[0_0_8px_rgba(52,211,153,0.5)] rounded-full"></div>
                  </div>
                </div>
                <div>
                  <div className="flex justify-between text-[10px] font-bold uppercase mb-2 text-indigo-100">
                    <span>Memory Heap</span>
                    <span>42%</span>
                  </div>
                  <div className="h-2 bg-black/20 rounded-full overflow-hidden">
                    <div className="h-full bg-white/90 w-[42%] rounded-full"></div>
                  </div>
                </div>
              </div>
              
              <div className="mt-8 p-4 bg-white/10 rounded-xl backdrop-blur-md border border-white/10">
                <p className="text-[11px] font-medium leading-relaxed text-indigo-50">
                  "System is operating within normal parameters. No scaling actions required at this time."
                </p>
              </div>
            </div>
            
          </aside>
        </div>
      </main>

      {/* FAB */}
      <button className="fixed bottom-8 right-8 w-14 h-14 bg-indigo-600 text-white rounded-full shadow-2xl flex items-center justify-center group active:scale-90 transition-transform hover:bg-indigo-700 z-50">
        <Plus className="w-6 h-6 transition-transform group-hover:rotate-90" />
      </button>
    </div>
  );
}
