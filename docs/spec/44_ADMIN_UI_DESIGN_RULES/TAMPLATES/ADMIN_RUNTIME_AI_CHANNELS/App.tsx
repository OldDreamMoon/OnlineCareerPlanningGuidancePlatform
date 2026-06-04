import React from 'react';
import {
  Search,
  Bell,
  Settings,
  ShieldCheck,
  Flag,
  SlidersHorizontal,
  BrainCircuit,
  ShieldAlert,
  Terminal,
  LifeBuoy,
  RefreshCw,
  Plus,
  TrendingUp,
  Route,
  Edit2,
  Zap,
  Network,
  Wand2,
  ArrowRight,
  AlertTriangle
} from 'lucide-react';

const TopNav = () => (
  <header className="fixed top-0 w-full z-50 bg-white/70 backdrop-blur-xl shadow-sm h-16 px-6 flex justify-between items-center">
    <div className="flex items-center gap-8">
      <span className="text-xl font-bold tracking-tight text-slate-900">Runtime Config Center</span>
      <div className="hidden md:flex items-center space-x-6 h-full">
        <button className="font-headline text-sm font-medium text-slate-500 hover:bg-slate-50 transition-colors px-3 py-2 rounded-lg">Dashboard</button>
        <button className="font-headline text-sm font-medium text-indigo-600 border-b-2 border-indigo-600 px-3 py-2">Infrastructure</button>
        <button className="font-headline text-sm font-medium text-slate-500 hover:bg-slate-50 transition-colors px-3 py-2 rounded-lg">Security</button>
      </div>
    </div>
    <div className="flex items-center gap-4">
      <div className="relative hidden lg:block">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 w-4 h-4" />
        <input
          className="bg-surface-container-low border-none rounded-full py-1.5 pl-10 pr-4 text-sm w-64 focus:ring-2 focus:ring-primary/20 outline-none transition-all"
          placeholder="Search configs..."
          type="text"
        />
      </div>
      <div className="flex items-center gap-2">
        <button className="p-2 text-slate-500 hover:bg-slate-100 rounded-full transition-colors active:scale-95">
          <Bell className="w-5 h-5" />
        </button>
        <button className="p-2 text-slate-500 hover:bg-slate-100 rounded-full transition-colors active:scale-95">
          <Settings className="w-5 h-5" />
        </button>
        <div className="h-8 w-8 rounded-full bg-primary-container flex items-center justify-center overflow-hidden ml-2">
          <img
            className="h-full w-full object-cover"
            alt="User avatar"
            src="https://lh3.googleusercontent.com/aida-public/AB6AXuCTi6QvjtLWtdQJsBpKeD6ZRaPLQ8n823GfYQ_jv5Jg2mZ1T78_x9jbg17XI3NA3HZHBS3Gzq_Y-BJI75rVGqEpuwtz-2sk0kXRLfwFh9FKAfCCxFhV6DTgyvPNC_RsaHoH2h49rOMnu5Pb3uk0U1C1HYmU0hLc7V8s4eJ4EY5zjSB9lmD1iHAaNx3Ir5Prt1GdwTcJu9CyHia5tVMmAEHaY7sY96qWpxIONjs5Qdp6ZR8zP8mnE91_sPc7PctcXqY_ObvyJIAKhxk"
          />
        </div>
      </div>
    </div>
  </header>
);

const SideNav = () => (
  <aside className="fixed left-0 top-16 h-[calc(100vh-64px)] w-64 bg-slate-50 flex flex-col py-4 space-y-2 border-r border-transparent z-40">
    <div className="px-6 py-4 mb-4">
      <div className="flex items-center gap-3">
        <div className="h-10 w-10 rounded-xl bg-primary flex items-center justify-center text-white shadow-sm">
          <ShieldCheck className="w-6 h-6" />
        </div>
        <div>
          <div className="text-sm font-semibold text-on-surface">Admin Core</div>
          <div className="text-[10px] text-outline">v2.4.0-stable</div>
        </div>
      </div>
    </div>
    <nav className="flex-1 space-y-1">
      <a className="flex items-center gap-3 py-2.5 text-slate-600 hover:bg-slate-100 mx-2 rounded-lg px-4 transition-all duration-300" href="#">
        <Flag className="w-5 h-5" />
        <span className="font-headline text-sm">Business Flags</span>
      </a>
      <a className="flex items-center gap-3 py-2.5 text-slate-600 hover:bg-slate-100 mx-2 rounded-lg px-4 transition-all duration-300" href="#">
        <SlidersHorizontal className="w-5 h-5" />
        <span className="font-headline text-sm">Operations Parameters</span>
      </a>
      <a className="flex items-center gap-3 py-2.5 bg-indigo-50 text-indigo-700 font-semibold rounded-lg mx-2 px-4 transition-all duration-300" href="#">
        <BrainCircuit className="w-5 h-5" />
        <span className="font-headline text-sm">AI Channels</span>
      </a>
      <a className="flex items-center gap-3 py-2.5 text-slate-600 hover:bg-slate-100 mx-2 rounded-lg px-4 transition-all duration-300" href="#">
        <ShieldAlert className="w-5 h-5" />
        <span className="font-headline text-sm">Risk Degradation</span>
      </a>
    </nav>
    <div className="px-4 mt-auto space-y-4">
      <button className="w-full bg-gradient-to-br from-primary to-primary-container text-white py-3 rounded-xl font-bold text-sm shadow-lg shadow-primary/20 active:scale-95 transition-all">
        Deploy Changes
      </button>
      <div className="pt-4 border-t border-slate-200 space-y-1">
        <a className="flex items-center gap-3 py-2 px-4 text-slate-600 hover:bg-slate-100 rounded-lg text-sm transition-colors" href="#">
          <Terminal className="w-4 h-4" /> Logs
        </a>
        <a className="flex items-center gap-3 py-2 px-4 text-slate-600 hover:bg-slate-100 rounded-lg text-sm transition-colors" href="#">
          <LifeBuoy className="w-4 h-4" /> Support
        </a>
      </div>
    </div>
  </aside>
);

const MetricCard = ({ title, value, trend, trendLabel, borderColor, valueColor = "text-on-surface" }: any) => (
  <div className={`bg-surface-container-lowest p-5 rounded-xl ambient-shadow border-l-4 ${borderColor}`}>
    <div className="text-outline text-xs font-semibold uppercase tracking-wider mb-2">{title}</div>
    <div className="flex items-end gap-2">
      <span className={`text-3xl font-extrabold font-headline ${valueColor}`}>{value}</span>
      {trend && (
        <span className={`text-xs font-bold mb-1 flex items-center ${trend.includes('up') ? 'text-secondary' : trend.includes('down') ? 'text-error' : 'text-outline'}`}>
          {trend === 'up' && <TrendingUp className="w-3 h-3 mr-1" />}
          {trend === 'down' && <TrendingUp className="w-3 h-3 mr-1 transform rotate-180" />}
          {trendLabel}
        </span>
      )}
    </div>
  </div>
);

const ActiveScenariosTable = () => (
  <div className="col-span-12 lg:col-span-8 bg-surface-container-lowest rounded-xl ambient-shadow overflow-hidden flex flex-col">
    <div className="p-6 border-b border-surface-container flex justify-between items-center">
      <h2 className="font-headline text-lg font-bold flex items-center gap-2">
        <Route className="w-5 h-5 text-primary" /> Active Scenario Routing
      </h2>
      <div className="flex gap-2">
        <span className="px-2 py-1 bg-surface-container-low text-[10px] font-bold rounded text-outline uppercase">Live Updates</span>
      </div>
    </div>
    <div className="overflow-x-auto">
      <table className="w-full text-left border-collapse">
        <thead>
          <tr className="bg-surface-container-low/50">
            <th className="px-6 py-3 text-xs font-bold text-outline uppercase tracking-wider">Scenario Code</th>
            <th className="px-6 py-3 text-xs font-bold text-outline uppercase tracking-wider">Primary Provider</th>
            <th className="px-6 py-3 text-xs font-bold text-outline uppercase tracking-wider">Success</th>
            <th className="px-6 py-3 text-xs font-bold text-outline uppercase tracking-wider">Latency</th>
            <th className="px-6 py-3 text-xs font-bold text-outline uppercase tracking-wider">Status</th>
            <th className="px-6 py-3 text-xs font-bold text-outline uppercase tracking-wider text-right">Action</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-surface-container">
          <tr className="hover:bg-surface-container-low/30 transition-colors">
            <td className="px-6 py-4 font-mono text-xs font-semibold text-primary">chat_agent_support</td>
            <td className="px-6 py-4">
              <div className="flex items-center gap-2">
                <div className="h-6 w-6 rounded bg-slate-900 flex items-center justify-center text-[10px] text-white font-bold">GPT</div>
                <span className="text-sm font-medium">GPT-4o</span>
              </div>
            </td>
            <td className="px-6 py-4">
              <div className="flex items-center gap-2">
                <div className="w-16 bg-surface-container rounded-full h-1.5">
                  <div className="vibrant-bar h-1.5 rounded-full" style={{ width: '98%' }}></div>
                </div>
                <span className="text-xs font-bold">98%</span>
              </div>
            </td>
            <td className="px-6 py-4 text-sm font-medium">1.2s</td>
            <td className="px-6 py-4"><span className="px-2 py-0.5 bg-secondary-container text-on-secondary-container text-[10px] font-bold rounded-full">ACTIVE</span></td>
            <td className="px-6 py-4 text-right">
              <button className="text-primary hover:bg-primary/10 p-1.5 rounded transition-colors"><Edit2 className="w-4 h-4" /></button>
            </td>
          </tr>
          <tr className="hover:bg-surface-container-low/30 transition-colors">
            <td className="px-6 py-4 font-mono text-xs font-semibold text-primary">doc_summarizer_v2</td>
            <td className="px-6 py-4">
              <div className="flex items-center gap-2">
                <div className="h-6 w-6 rounded bg-indigo-600 flex items-center justify-center text-[10px] text-white font-bold">CLD</div>
                <span className="text-sm font-medium">Claude 3.5 Sonnet</span>
              </div>
            </td>
            <td className="px-6 py-4">
              <div className="flex items-center gap-2">
                <div className="w-16 bg-surface-container rounded-full h-1.5">
                  <div className="bg-secondary-fixed-dim h-1.5 rounded-full" style={{ width: '94%' }}></div>
                </div>
                <span className="text-xs font-bold">94%</span>
              </div>
            </td>
            <td className="px-6 py-4 text-sm font-medium">0.8s</td>
            <td className="px-6 py-4"><span className="px-2 py-0.5 bg-secondary-container text-on-secondary-container text-[10px] font-bold rounded-full">ACTIVE</span></td>
            <td className="px-6 py-4 text-right">
              <button className="text-primary hover:bg-primary/10 p-1.5 rounded transition-colors"><Edit2 className="w-4 h-4" /></button>
            </td>
          </tr>
          <tr className="hover:bg-surface-container-low/30 transition-colors bg-error/5">
            <td className="px-6 py-4 font-mono text-xs font-semibold text-error">sql_query_generator</td>
            <td className="px-6 py-4">
              <div className="flex items-center gap-2">
                <div className="h-6 w-6 rounded bg-slate-400 flex items-center justify-center text-[10px] text-white font-bold">LM2</div>
                <span className="text-sm font-medium">Llama 3 70B</span>
              </div>
            </td>
            <td className="px-6 py-4">
              <div className="flex items-center gap-2">
                <div className="w-16 bg-surface-container rounded-full h-1.5">
                  <div className="bg-error h-1.5 rounded-full" style={{ width: '62%' }}></div>
                </div>
                <span className="text-xs font-bold text-error">62%</span>
              </div>
            </td>
            <td className="px-6 py-4 text-sm font-medium">3.4s</td>
            <td className="px-6 py-4"><span className="px-2 py-0.5 bg-error-container text-on-error-container text-[10px] font-bold rounded-full">DEGRADED</span></td>
            <td className="px-6 py-4 text-right">
              <button className="text-primary hover:bg-primary/10 p-1.5 rounded transition-colors"><Zap className="w-4 h-4" /></button>
            </td>
          </tr>
          <tr className="hover:bg-surface-container-low/30 transition-colors">
            <td className="px-6 py-4 font-mono text-xs font-semibold text-primary">image_gen_internal</td>
            <td className="px-6 py-4">
              <div className="flex items-center gap-2">
                <div className="h-6 w-6 rounded bg-orange-500 flex items-center justify-center text-[10px] text-white font-bold">D3</div>
                <span className="text-sm font-medium">DALL-E 3</span>
              </div>
            </td>
            <td className="px-6 py-4">
              <div className="flex items-center gap-2">
                <div className="w-16 bg-surface-container rounded-full h-1.5">
                  <div className="vibrant-bar h-1.5 rounded-full" style={{ width: '100%' }}></div>
                </div>
                <span className="text-xs font-bold">100%</span>
              </div>
            </td>
            <td className="px-6 py-4 text-sm font-medium">6.2s</td>
            <td className="px-6 py-4"><span className="px-2 py-0.5 bg-secondary-container text-on-secondary-container text-[10px] font-bold rounded-full">ACTIVE</span></td>
            <td className="px-6 py-4 text-right">
              <button className="text-primary hover:bg-primary/10 p-1.5 rounded transition-colors"><Edit2 className="w-4 h-4" /></button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div className="mt-auto p-4 bg-surface-container-low/30 flex justify-between items-center text-xs text-outline">
      <span>Showing 4 of 128 active scenarios</span>
      <button className="text-primary font-bold hover:underline">View All Routes</button>
    </div>
  </div>
);

const ProviderStatus = () => (
  <div className="bg-surface-container-lowest p-6 rounded-xl ambient-shadow border border-transparent">
    <h2 className="font-headline text-lg font-bold mb-4 flex items-center gap-2">
      <Network className="w-5 h-5 text-tertiary" /> Provider Status
    </h2>
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="h-8 w-8 rounded-lg bg-slate-900 flex items-center justify-center text-xs text-white font-bold">OAI</div>
          <div>
            <div className="text-sm font-bold">OpenAI</div>
            <div className="text-[10px] text-outline">32 endpoints active</div>
          </div>
        </div>
        <div className="h-2 w-2 rounded-full bg-secondary shadow-[0_0_8px_rgba(16,185,129,0.5)]"></div>
      </div>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="h-8 w-8 rounded-lg bg-indigo-600 flex items-center justify-center text-xs text-white font-bold">ANT</div>
          <div>
            <div className="text-sm font-bold">Anthropic</div>
            <div className="text-[10px] text-outline">12 endpoints active</div>
          </div>
        </div>
        <div className="h-2 w-2 rounded-full bg-secondary shadow-[0_0_8px_rgba(16,185,129,0.5)]"></div>
      </div>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="h-8 w-8 rounded-lg bg-blue-500 flex items-center justify-center text-xs text-white font-bold">GCP</div>
          <div>
            <div className="text-sm font-bold">Google Vertex</div>
            <div className="text-[10px] text-outline">8 endpoints active</div>
          </div>
        </div>
        <div className="h-2 w-2 rounded-full bg-tertiary shadow-[0_0_8px_rgba(245,158,11,0.5)]"></div>
      </div>
    </div>
  </div>
);

const QuickTemplateOverride = () => (
  <div className="bg-gradient-to-br from-indigo-900 to-slate-900 p-6 rounded-xl shadow-xl text-white">
    <h2 className="font-headline text-lg font-bold mb-4 flex items-center gap-2">
      <Wand2 className="w-5 h-5 text-indigo-400" /> Quick Template Override
    </h2>
    <p className="text-indigo-200 text-xs mb-6">Instantly patch prompts across all channels for critical bug fixes.</p>
    <div className="space-y-3">
      <div className="bg-white/10 p-3 rounded-lg border border-white/10 hover:bg-white/15 transition-all cursor-pointer group">
        <div className="flex justify-between items-start mb-2">
          <span className="text-xs font-bold text-indigo-300 uppercase">System Prompt</span>
          <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
        </div>
        <div className="text-sm font-medium line-clamp-2 italic text-white/80">"You are a professional assistant... [v2.3 Patch]"</div>
      </div>
      <div className="bg-white/10 p-3 rounded-lg border border-white/10 hover:bg-white/15 transition-all cursor-pointer group">
        <div className="flex justify-between items-start mb-2">
          <span className="text-xs font-bold text-indigo-300 uppercase">JSON Schema</span>
          <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
        </div>
        <div className="text-sm font-medium line-clamp-2 italic text-white/80">"Output strict RFC-8259 compliant objects only..."</div>
      </div>
    </div>
    <button className="w-full mt-6 bg-indigo-500 hover:bg-indigo-400 text-white font-bold py-2.5 rounded-lg text-sm transition-colors shadow-lg shadow-indigo-500/20">
      Open Prompt Editor
    </button>
  </div>
);

const RiskMonitoring = () => (
  <div className="col-span-12">
    <div className="bg-white p-6 rounded-xl ambient-shadow border border-error/20">
      <div className="flex items-center justify-between mb-6">
        <h2 className="font-headline text-lg font-bold text-error flex items-center gap-2">
          <AlertTriangle className="w-5 h-5 fill-error/20" /> Risk Threshold Monitoring
        </h2>
        <button className="text-sm font-semibold text-primary hover:underline">Notification Settings</button>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="bg-surface-container-low p-4 rounded-xl border border-error/10">
          <div className="flex justify-between items-start mb-3">
            <div className="px-2 py-1 bg-error-container text-on-error-container text-[10px] font-bold rounded">COST CRITICAL</div>
            <span className="text-[10px] text-outline">Real-time</span>
          </div>
          <h3 className="font-bold text-sm mb-1">Scenario: code_copilot_beta</h3>
          <p className="text-xs text-on-surface-variant mb-4">Cost per 1k tokens exceeded $0.05 budget (Current: $0.12).</p>
          <div className="flex gap-2">
            <button className="flex-1 py-1.5 bg-primary hover:bg-primary/90 transition-colors text-white text-[10px] font-bold rounded-lg">Switch to Llama</button>
            <button className="px-3 py-1.5 bg-surface-container-highest hover:bg-surface-variant transition-colors text-on-surface text-[10px] font-bold rounded-lg">Ignore</button>
          </div>
        </div>
        <div className="bg-surface-container-low p-4 rounded-xl border border-tertiary/20">
          <div className="flex justify-between items-start mb-3">
            <div className="px-2 py-1 bg-tertiary-container text-on-tertiary-container text-[10px] font-bold rounded">LATENCY SPIKE</div>
            <span className="text-[10px] text-outline">2 min ago</span>
          </div>
          <h3 className="font-bold text-sm mb-1">Scenario: customer_intent_parser</h3>
          <p className="text-xs text-on-surface-variant mb-4">P99 Latency jumped from 450ms to 2400ms on US-East-1.</p>
          <div className="flex gap-2">
            <button className="flex-1 py-1.5 bg-primary hover:bg-primary/90 transition-colors text-white text-[10px] font-bold rounded-lg">Reroute to EU</button>
            <button className="px-3 py-1.5 bg-surface-container-highest hover:bg-surface-variant transition-colors text-on-surface text-[10px] font-bold rounded-lg">Details</button>
          </div>
        </div>
        <div className="bg-surface-container-low p-4 rounded-xl border border-error/10">
          <div className="flex justify-between items-start mb-3">
            <div className="px-2 py-1 bg-error-container text-on-error-container text-[10px] font-bold rounded">ERROR RATE</div>
            <span className="text-[10px] text-outline">Live</span>
          </div>
          <h3 className="font-bold text-sm mb-1">Scenario: translate_realtime</h3>
          <p className="text-xs text-on-surface-variant mb-4">Connection timeouts on Azure OpenAI endpoints reaching 15%.</p>
          <div className="flex gap-2">
            <button className="flex-1 py-1.5 bg-primary hover:bg-primary/90 transition-colors text-white text-[10px] font-bold rounded-lg">Failover Now</button>
            <button className="px-3 py-1.5 bg-surface-container-highest hover:bg-surface-variant transition-colors text-on-surface text-[10px] font-bold rounded-lg">Log</button>
          </div>
        </div>
      </div>
    </div>
  </div>
);

export default function App() {
  return (
    <div className="bg-surface font-body text-on-surface antialiased min-h-screen flex">
      <TopNav />
      <SideNav />
      
      <main className="ml-64 pt-16 p-8 min-h-screen w-full">
        {/* Header Section */}
        <div className="flex justify-between items-end mb-8">
          <div>
            <nav className="flex text-xs text-outline mb-2 gap-2">
              <span>Config Center</span> <span>/</span> <span className="text-primary font-medium">AI Channels</span>
            </nav>
            <h1 className="font-headline text-3xl font-extrabold tracking-tight text-on-surface">AI Channels Control</h1>
            <p className="text-on-surface-variant text-sm mt-1">Manage scenario-level LLM providers, routing logic, and template overrides.</p>
          </div>
          <div className="flex gap-3">
            <button className="flex items-center gap-2 bg-surface-container-lowest px-4 py-2 rounded-xl text-sm font-semibold text-on-surface ambient-shadow hover:shadow-md transition-all">
              <RefreshCw className="w-4 h-4" /> System Sync
            </button>
            <button className="flex items-center gap-2 bg-primary text-white px-5 py-2 rounded-xl text-sm font-bold shadow-lg shadow-primary/20 hover:scale-105 transition-all">
              <Plus className="w-4 h-4" /> New Scenario
            </button>
          </div>
        </div>

        {/* Metric Grid */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
          <MetricCard title="Total Scenarios" value="128" trend="up" trendLabel="12%" borderColor="border-primary" />
          <MetricCard title="Global Success Rate" value="99.2%" trend="neutral" trendLabel="Stable" borderColor="border-secondary" />
          <MetricCard title="Avg. Latency" value="840ms" trend="down" trendLabel="45ms" borderColor="border-tertiary" />
          <MetricCard title="Hourly Cost" value="$42.10" trend="neutral" trendLabel="Quota: 65%" borderColor="border-indigo-400" />
        </div>

        {/* Bento Grid Main Content */}
        <div className="grid grid-cols-12 gap-6">
          <ActiveScenariosTable />
          
          <div className="col-span-12 lg:col-span-4 space-y-6">
            <ProviderStatus />
            <QuickTemplateOverride />
          </div>

          <RiskMonitoring />
        </div>
      </main>

      {/* Contextual FAB */}
      <button className="fixed bottom-8 right-8 h-14 w-14 bg-primary hover:bg-primary/90 text-white rounded-full shadow-2xl shadow-primary/40 flex items-center justify-center active:scale-90 transition-all z-50">
        <Zap className="w-6 h-6 fill-current" />
      </button>
    </div>
  );
}
