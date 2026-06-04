import React from 'react';
import {
  Rocket,
  LayoutGrid,
  Network,
  Route,
  TerminalSquare,
  CircleDollarSign,
  FileText,
  Plus,
  Book,
  LifeBuoy,
  Search,
  Bell,
  Settings,
  MoreVertical,
  Edit2,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';

export default function App() {
  return (
    <div className="min-h-screen bg-surface text-on-surface font-sans flex">
      {/* Sidebar */}
      <aside className="w-64 fixed left-0 top-0 h-screen bg-slate-50 flex flex-col py-4 z-40 hidden md:flex border-r border-slate-200/50">
        <div className="px-6 mb-8 mt-4">
          <div className="flex items-center gap-3 mb-2">
            <div className="w-8 h-8 bg-primary rounded-lg flex items-center justify-center shadow-sm">
              <Rocket className="text-white w-4 h-4" />
            </div>
            <div>
              <div className="text-lg font-bold text-slate-900 leading-tight font-headline">
                AI Management
              </div>
              <div className="text-[10px] text-slate-500 uppercase tracking-widest font-bold">
                Graduation Project
              </div>
            </div>
          </div>
        </div>

        <nav className="flex-1 space-y-1 px-2">
          <NavItem icon={<Rocket className="w-5 h-5" />} label="Operations" />
          <NavItem icon={<LayoutGrid className="w-5 h-5" />} label="Dashboard" />
          <NavItem
            icon={<Network className="w-5 h-5" />}
            label="Providers"
            active
          />
          <NavItem icon={<Route className="w-5 h-5" />} label="Routing" />
          <NavItem icon={<TerminalSquare className="w-5 h-5" />} label="Prompts" />
          <NavItem icon={<CircleDollarSign className="w-5 h-5" />} label="Costs" />
          <NavItem icon={<FileText className="w-5 h-5" />} label="Logs" />
        </nav>

        <div className="px-4 mt-auto pt-4 space-y-4">
          <button className="w-full bg-primary text-white py-3 rounded-xl font-bold text-sm shadow-lg shadow-primary/20 flex items-center justify-center gap-2 hover:opacity-90 transition-opacity">
            <Plus className="w-4 h-4" /> New App
          </button>
          <div className="space-y-1">
            <SecondaryNavItem icon={<Book className="w-4 h-4" />} label="Docs" />
            <SecondaryNavItem
              icon={<LifeBuoy className="w-4 h-4" />}
              label="Support"
            />
          </div>
        </div>
      </aside>

      {/* Main Content Wrapper */}
      <div className="flex-1 md:ml-64 flex flex-col min-h-screen">
        {/* Header */}
        <header className="sticky top-0 z-30 bg-white/70 backdrop-blur-xl flex justify-between items-center px-6 h-16 shadow-[0px_10px_40px_rgba(44,29,49,0.06)]">
          <div className="flex items-center gap-8">
            <span className="text-xl font-black text-slate-900 font-headline tracking-tight">
              Luminous AI Suite
            </span>
            <nav className="hidden lg:flex gap-6 items-center h-full">
              <TopNavItem label="Operations" />
              <TopNavItem label="Dashboard" />
              <TopNavItem label="Providers" active />
              <TopNavItem label="Routing" />
            </nav>
          </div>
          <div className="flex items-center gap-4">
            <div className="bg-surface-container-low px-4 py-1.5 rounded-full flex items-center gap-2">
              <Search className="w-4 h-4 text-slate-400" />
              <input
                type="text"
                placeholder="Search providers..."
                className="bg-transparent border-none focus:ring-0 text-sm w-48 outline-none placeholder:text-slate-500"
              />
            </div>
            <div className="flex items-center gap-2">
              <button className="p-2 text-slate-500 hover:bg-slate-100 rounded-full transition-colors">
                <Bell className="w-5 h-5" />
              </button>
              <button className="p-2 text-slate-500 hover:bg-slate-100 rounded-full transition-colors">
                <Settings className="w-5 h-5" />
              </button>
              <img
                src="https://lh3.googleusercontent.com/aida-public/AB6AXuBK9voBoY4S9xLS5BU5UXkvFYoT7045LiX0ErLbq55BgLYhyRmY2g6SZU7e2lMdKM80T7UhGTn2FuGv7zh4RRKi_wG3Ph9dJ8SDuxEaF4mXlsKZsutPtLCyaEavjSIHadXY3EUBZOaINr9rUJ3uLRyB97q0RrzAaZBIaimqllbB6fjULSTlcmy9v0y01NrHQuQ5-jZdSymrTCxZTW4Ozyp5MY85NdU_0QC1y8LKMk-tOQaRbsjRupAIXlraap10eHr3M9Gmp77pCbo"
                alt="User profile"
                className="w-8 h-8 rounded-full border border-slate-200 object-cover"
              />
            </div>
          </div>
        </header>

        {/* Main Content */}
        <main className="p-8 max-w-7xl mx-auto w-full space-y-8 flex-1">
          {/* Page Header & Global Stats */}
          <section className="flex flex-col lg:flex-row justify-between items-start lg:items-end gap-6">
            <div>
              <h1 className="text-3xl font-black text-on-background tracking-tight font-headline">
                Provider Management
              </h1>
              <p className="text-on-surface-variant font-medium mt-1">
                Monitor and configure AI inference endpoints in real-time.
              </p>
            </div>
            {/* Runtime Status Summary Cards */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 w-full lg:w-auto">
              <StatCard label="Healthy" value="12" color="bg-secondary" textColor="text-secondary" />
              <StatCard label="Degraded" value="2" color="bg-tertiary" textColor="text-tertiary" />
              <StatCard label="Down" value="1" color="bg-error" textColor="text-error" pulse />
              <StatCard label="Maintenance" value="0" color="bg-primary" textColor="text-primary" />
            </div>
          </section>

          {/* Bento Grid - Analytics and Heatmaps */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Traffic Heatmap */}
            <div className="lg:col-span-2 bg-surface-container-lowest p-6 rounded-2xl shadow-sm border border-outline-variant/10 flex flex-col">
              <div className="flex justify-between items-center mb-6">
                <h3 className="text-lg font-bold text-on-background font-headline">
                  Traffic Intensity (7 Days)
                </h3>
                <div className="flex items-center gap-4 text-[10px] font-bold text-on-surface-variant uppercase tracking-widest">
                  <span>Low</span>
                  <div className="flex gap-1">
                    <div className="w-3 h-3 rounded-sm bg-surface-container-high"></div>
                    <div className="w-3 h-3 rounded-sm bg-primary-fixed"></div>
                    <div className="w-3 h-3 rounded-sm bg-primary"></div>
                    <div className="w-3 h-3 rounded-sm bg-primary-dim"></div>
                  </div>
                  <span>High</span>
                </div>
              </div>
              <div className="flex-1 flex flex-col gap-1.5">
                <HeatmapRow label="MON" intensities={[2, 1, 3, 4, 5, 6, 6, 5, 3, 2, 0, 0, 0, 0, 1, 2, 3, 5, 6, 7, 5, 3, 2, 1]} />
                <HeatmapRow label="" intensities={[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]} />
                <HeatmapRow label="" intensities={[2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2]} />
                <HeatmapRow label="" intensities={[3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3]} />
                <HeatmapRow label="" intensities={[4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4]} />
                <HeatmapRow label="" intensities={[5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5]} />
                <HeatmapRow label="" intensities={[6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6]} />
                <HeatmapRow label="" intensities={[4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4]} />
                <HeatmapRow label="" intensities={[3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3]} />
                <HeatmapRow label="" intensities={[2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2]} />
                <HeatmapRow label="TUE" intensities={[3, 2, 1, 0, 0, 2, 3, 4, 5, 6, 6, 7, 5, 4, 3, 1, 0, 0, 0, 2, 3, 4, 3, 1]} />
                <HeatmapRow label="WED" intensities={[6, 7, 5, 4, 3, 2, 3, 4, 2, 1, 0, 0, 0, 0, 0, 2, 3, 4, 5, 6, 6, 7, 5, 4]} />
                
                <div className="mt-4 flex justify-between text-[9px] text-outline font-bold px-8 uppercase tracking-widest">
                  <span>00:00</span>
                  <span>04:00</span>
                  <span>08:00</span>
                  <span>12:00</span>
                  <span>16:00</span>
                  <span>20:00</span>
                  <span>23:59</span>
                </div>
              </div>
            </div>

            {/* Fast Actions / Stats */}
            <div className="bg-primary/5 border border-primary/10 p-6 rounded-2xl flex flex-col justify-between">
              <div>
                <h3 className="text-lg font-bold text-primary mb-2 font-headline">
                  Cost Optimization
                </h3>
                <p className="text-sm text-on-surface-variant leading-relaxed">
                  Your routing is currently favoring <span className="font-bold text-on-background">latency</span> over cost. Switching to <span className="font-bold text-on-background">Balanced</span> could save $240/mo.
                </p>
              </div>
              <div className="mt-6 space-y-3">
                <button className="w-full py-2.5 bg-white border border-primary/20 rounded-xl text-sm font-bold text-primary hover:bg-primary hover:text-white transition-all shadow-sm">
                  Optimize Routing
                </button>
                <button className="w-full py-2 text-slate-500 text-sm font-medium hover:text-slate-700 transition-colors">
                  View Detailed Billing
                </button>
              </div>
            </div>
          </div>

          {/* Provider Runtime List */}
          <section className="space-y-4">
            <div className="flex justify-between items-center">
              <h2 className="text-xl font-bold text-on-background font-headline">
                Live Runtime Performance
              </h2>
              <span className="text-xs font-bold text-on-surface-variant flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-secondary"></span> Live Updates Active
              </span>
            </div>
            <div className="grid grid-cols-1 gap-3">
              <ProviderPerformanceRow
                initials="OA"
                name="OpenAI - GPT-4o"
                region="US-EAST-1 • PRODUCTION"
                successRate="99.98%"
                latency="342ms"
                history={[1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1]}
                colorClass="text-primary bg-surface-container-high"
                successColor="text-secondary"
              />
              <ProviderPerformanceRow
                initials="AN"
                name="Anthropic - Claude 3.5"
                region="AWS-US-WEST-2 • PRODUCTION"
                successRate="96.42%"
                latency="892ms"
                history={[1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0.5, 0.5, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1]}
                colorClass="text-orange-500 bg-surface-container-high"
                successColor="text-tertiary"
              />
              <ProviderPerformanceRow
                initials="GR"
                name="Groq - Llama 3 70B"
                region="GLOBAL-EDGE • EXPERIMENTAL"
                successRate="52.10%"
                latency="112ms"
                history={[1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1]}
                colorClass="text-blue-600 bg-surface-container-high"
                successColor="text-error"
              />
            </div>
          </section>

          {/* Provider Pool Table */}
          <section className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/10 overflow-hidden">
            <div className="p-6 border-b border-outline-variant/10 flex justify-between items-center">
              <h2 className="text-xl font-bold text-on-background font-headline">
                Provider Pool Details
              </h2>
              <div className="flex gap-2">
                <button className="px-4 py-2 bg-surface-container-low text-on-surface text-xs font-bold rounded-lg hover:bg-surface-container-high transition-colors">
                  Export Config
                </button>
                <button className="px-4 py-2 bg-primary text-white text-xs font-bold rounded-lg hover:opacity-90 transition-opacity">
                  Add Provider
                </button>
              </div>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left">
                <thead className="bg-surface-container-low/50 text-[10px] font-bold text-on-surface-variant uppercase tracking-widest">
                  <tr>
                    <th className="px-6 py-4">Provider</th>
                    <th className="px-6 py-4">Base URL</th>
                    <th className="px-6 py-4">Timeout</th>
                    <th className="px-6 py-4">Cost / 1M Tokens</th>
                    <th className="px-6 py-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="text-sm divide-y divide-outline-variant/10">
                  <ProviderTableRow
                    name="OpenAI"
                    url="api.openai.com/v1"
                    timeout="30,000ms"
                    cost="$10.00"
                  />
                  <ProviderTableRow
                    name="Anthropic"
                    url="api.anthropic.com/v1"
                    timeout="45,000ms"
                    cost="$15.00"
                  />
                  <ProviderTableRow
                    name="Google Vertex"
                    url="us-central1-aiplatform.googleapis.com"
                    timeout="60,000ms"
                    cost="$7.50"
                  />
                  <ProviderTableRow
                    name="Mistral AI"
                    url="api.mistral.ai/v1"
                    timeout="25,000ms"
                    cost="$2.00"
                  />
                </tbody>
              </table>
            </div>
            <div className="px-6 py-4 bg-surface-container-low/20 flex justify-between items-center border-t border-outline-variant/10">
              <span className="text-xs text-on-surface-variant font-medium">
                Showing 4 of 12 Providers
              </span>
              <div className="flex gap-1">
                <button className="p-1.5 rounded-lg bg-white border border-outline-variant/20 hover:bg-surface-container-low transition-colors">
                  <ChevronLeft className="w-4 h-4 text-on-surface-variant" />
                </button>
                <button className="p-1.5 rounded-lg bg-white border border-outline-variant/20 hover:bg-surface-container-low transition-colors">
                  <ChevronRight className="w-4 h-4 text-on-surface-variant" />
                </button>
              </div>
            </div>
          </section>
        </main>
      </div>
    </div>
  );
}

// --- Subcomponents ---

function NavItem({ icon, label, active = false }: { icon: React.ReactNode; label: string; active?: boolean }) {
  return (
    <a
      href="#"
      className={`flex items-center gap-3 px-4 py-2.5 rounded-xl mx-2 text-sm font-medium transition-all duration-200 ${
        active
          ? 'bg-white text-cyan-600 shadow-sm font-semibold'
          : 'text-slate-600 hover:bg-slate-200/50'
      }`}
    >
      {icon}
      {label}
    </a>
  );
}

function SecondaryNavItem({ icon, label }: { icon: React.ReactNode; label: string }) {
  return (
    <a
      href="#"
      className="flex items-center gap-3 px-4 py-2 text-slate-500 hover:bg-slate-200/50 rounded-lg text-xs font-medium transition-colors"
    >
      {icon}
      {label}
    </a>
  );
}

function TopNavItem({ label, active = false }: { label: string; active?: boolean }) {
  return (
    <a
      href="#"
      className={`px-3 py-4 text-sm font-medium border-b-2 transition-colors ${
        active
          ? 'text-cyan-600 border-cyan-500 font-bold'
          : 'text-slate-500 border-transparent hover:text-slate-800'
      }`}
    >
      {label}
    </a>
  );
}

function StatCard({ label, value, color, textColor, pulse = false }: { label: string; value: string; color: string; textColor: string; pulse?: boolean }) {
  return (
    <div className="bg-surface-container-lowest p-5 rounded-2xl shadow-sm border border-outline-variant/10 min-w-[140px]">
      <div className={`flex items-center gap-2 mb-2 ${textColor}`}>
        <span className={`w-2 h-2 rounded-full ${color} ${pulse ? 'animate-pulse' : ''}`}></span>
        <span className="text-[10px] uppercase font-bold tracking-wider">{label}</span>
      </div>
      <div className="text-3xl font-black text-on-background font-headline">{value}</div>
    </div>
  );
}

function HeatmapRow({ label, intensities }: { label: string; intensities: number[] }) {
  // Map intensity (0-7) to opacity classes
  const getOpacityClass = (intensity: number) => {
    if (intensity === 0) return 'bg-surface-container-high';
    const opacities = [
      'bg-primary/10',
      'bg-primary/20',
      'bg-primary/30',
      'bg-primary/40',
      'bg-primary/60',
      'bg-primary/80',
      'bg-primary-dim',
    ];
    return opacities[Math.min(intensity - 1, opacities.length - 1)];
  };

  return (
    <div className="flex gap-1.5 items-center">
      <span className="text-[10px] w-8 text-on-surface-variant font-bold">{label}</span>
      <div className="flex-1 flex gap-1 h-6">
        {intensities.map((intensity, i) => (
          <div key={i} className={`flex-1 rounded-sm ${getOpacityClass(intensity)}`}></div>
        ))}
      </div>
    </div>
  );
}

function ProviderPerformanceRow({
  initials,
  name,
  region,
  successRate,
  latency,
  history,
  colorClass,
  successColor,
}: {
  initials: string;
  name: string;
  region: string;
  successRate: string;
  latency: string;
  history: number[];
  colorClass: string;
  successColor: string;
}) {
  return (
    <div className="bg-surface-container-lowest p-5 rounded-2xl shadow-sm border border-outline-variant/5 flex flex-col md:flex-row items-center gap-6">
      <div className="flex items-center gap-4 min-w-[220px] w-full md:w-auto">
        <div className={`w-10 h-10 rounded-xl flex items-center justify-center font-bold ${colorClass}`}>
          {initials}
        </div>
        <div>
          <div className="font-bold text-on-background">{name}</div>
          <div className="text-[10px] text-on-surface-variant font-bold uppercase tracking-tighter mt-0.5">
            {region}
          </div>
        </div>
      </div>
      <div className="flex-1 flex flex-col gap-2 w-full">
        <div className="flex justify-between text-[10px] font-bold text-on-surface-variant uppercase tracking-widest px-1">
          <span>24h Health History</span>
          <span className={successColor}>{successRate}</span>
        </div>
        <div className="flex gap-1 h-8">
          {history.map((val, i) => {
            let bgClass = 'bg-secondary';
            if (val === 0) bgClass = 'bg-error';
            else if (val < 1) bgClass = 'bg-tertiary';
            return <div key={i} className={`flex-1 rounded-sm ${bgClass}`}></div>;
          })}
        </div>
      </div>
      <div className="min-w-[120px] text-left md:text-right w-full md:w-auto">
        <div className="text-[10px] text-on-surface-variant font-bold uppercase mb-1">Avg Latency</div>
        <div className="text-2xl font-black text-on-background font-headline">{latency}</div>
      </div>
    </div>
  );
}

function ProviderTableRow({ name, url, timeout, cost }: { name: string; url: string; timeout: string; cost: string }) {
  return (
    <tr className="hover:bg-slate-50 transition-colors group">
      <td className="px-6 py-4 font-bold text-on-background">{name}</td>
      <td className="px-6 py-4 font-mono text-xs text-on-surface-variant">{url}</td>
      <td className="px-6 py-4 text-on-surface-variant">{timeout}</td>
      <td className="px-6 py-4">
        <span className="text-secondary-dim font-bold">{cost}</span>
      </td>
      <td className="px-6 py-4 text-right">
        <div className="flex justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
          <button className="p-1.5 hover:bg-surface-container-low rounded-lg transition-colors text-on-surface-variant">
            <Edit2 className="w-4 h-4" />
          </button>
          <button className="p-1.5 hover:bg-surface-container-low rounded-lg transition-colors text-on-surface-variant">
            <MoreVertical className="w-4 h-4" />
          </button>
        </div>
      </td>
    </tr>
  );
}
