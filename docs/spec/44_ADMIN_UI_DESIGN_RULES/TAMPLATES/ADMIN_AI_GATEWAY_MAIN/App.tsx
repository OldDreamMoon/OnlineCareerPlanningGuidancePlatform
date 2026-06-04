/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import {
  Rocket,
  LayoutDashboard,
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
  TrendingUp,
  Activity,
  MoreVertical,
  Filter,
  PlusCircle,
  AlertCircle,
  Clock,
  ChevronDown
} from 'lucide-react';

export default function App() {
  return (
    <div className="min-h-screen bg-[#f5f7f9] text-[#2c2f31] font-sans selection:bg-indigo-100 selection:text-indigo-900 pb-16 md:pb-0">
      <Header />
      <Sidebar />
      <MainContent />
      <MobileNav />
    </div>
  );
}

const Header = () => (
  <header className="fixed top-0 w-full z-50 bg-white/70 backdrop-blur-xl flex justify-between items-center px-4 md:px-6 h-16 shadow-[0px_10px_40px_rgba(44,29,49,0.06)]">
    <div className="flex items-center gap-8">
      <div className="text-xl font-black text-slate-900 tracking-tight font-heading">Luminous AI Suite</div>
      <nav className="hidden md:flex items-center gap-6 h-full">
        <a href="#" className="text-cyan-600 border-b-2 border-cyan-500 font-bold h-16 flex items-center px-2 transition-all duration-200">Operations</a>
        <a href="#" className="text-slate-500 hover:bg-slate-100/50 px-2 py-1.5 rounded-md transition-colors font-medium">Dashboard</a>
        <a href="#" className="text-slate-500 hover:bg-slate-100/50 px-2 py-1.5 rounded-md transition-colors font-medium">Integrations</a>
      </nav>
    </div>
    <div className="flex items-center gap-2 md:gap-4">
      <div className="relative hidden lg:block">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
        <input type="text" placeholder="Search resources..." className="bg-[#eef1f3] border-none rounded-full py-1.5 pl-9 pr-4 text-sm w-64 focus:ring-2 focus:ring-[#4647d3]/20 transition-all outline-none text-slate-700 placeholder-slate-400" />
      </div>
      <button className="p-2 text-slate-500 hover:bg-slate-100/50 rounded-full transition-colors">
        <Bell size={20} />
      </button>
      <button className="p-2 text-slate-500 hover:bg-slate-100/50 rounded-full transition-colors hidden sm:block">
        <Settings size={20} />
      </button>
      <div className="w-8 h-8 rounded-full bg-[#9396ff] flex items-center justify-center overflow-hidden ml-1 md:ml-2">
        <img src="https://lh3.googleusercontent.com/aida-public/AB6AXuDnHKYQuUrgq1JYpG_dYbbqRx8xDvwNQJ1Xl_0XHGq0dPnohCrUNJimZV31u-rA8WSNA4hNYlBzk0s9o-ZaTZy7M0a_wfL7Cg3QwesGpR2FOCaJKH3A0j8i0-HeBHMSoVeE7qg6PlZPVXy70gOoo1xPenqc5XkCATwpOphAz0PJm4mAFJF3wvvcsTeUe-bxo9n7KkdUc97-sG0UnATX3XAGE3Mz-R2-5OeCJKAgzwhtEqBLXyoxexgdCqPjL5WVIc0hAhhSaIt7iPo" alt="User" className="w-full h-full object-cover" />
      </div>
    </div>
  </header>
);

const Sidebar = () => (
  <aside className="w-64 h-screen fixed left-0 top-0 bg-slate-50 flex flex-col py-4 z-40 hidden md:flex">
    <div className="px-6 mb-8 mt-16">
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-[#4647d3] to-[#9396ff] flex items-center justify-center text-white">
          <Rocket size={20} />
        </div>
        <div>
          <div className="text-lg font-bold text-slate-900 font-heading">AI Management</div>
          <div className="text-xs text-slate-500">Graduation Project</div>
        </div>
      </div>
    </div>
    <nav className="flex-1 space-y-1">
      <NavItem icon={<Rocket size={18} />} label="Operations" active />
      <NavItem icon={<LayoutDashboard size={18} />} label="Dashboard" />
      <NavItem icon={<Network size={18} />} label="Providers" />
      <NavItem icon={<Route size={18} />} label="Routing" />
      <NavItem icon={<TerminalSquare size={18} />} label="Prompts" />
      <NavItem icon={<CircleDollarSign size={18} />} label="Costs" />
      <NavItem icon={<FileText size={18} />} label="Logs" />
    </nav>
    <div className="px-4 mt-auto space-y-4">
      <button className="w-full bg-gradient-to-r from-[#4647d3] to-[#9396ff] text-white py-2.5 rounded-xl font-semibold shadow-lg shadow-[#4647d3]/20 flex items-center justify-center gap-2 hover:scale-[1.02] transition-transform">
        <Plus size={18} /> New App
      </button>
      <div className="border-t border-slate-200 pt-4 pb-2 space-y-1">
        <a href="#" className="flex items-center gap-3 py-2 px-4 text-slate-500 text-sm font-medium hover:text-slate-900 transition-colors">
          <Book size={18} /> Docs
        </a>
        <a href="#" className="flex items-center gap-3 py-2 px-4 text-slate-500 text-sm font-medium hover:text-slate-900 transition-colors">
          <LifeBuoy size={18} /> Support
        </a>
      </div>
    </div>
  </aside>
);

const NavItem = ({ icon, label, active }: { icon: React.ReactNode, label: string, active?: boolean }) => (
  <a href="#" className={`flex items-center gap-3 py-2.5 px-4 mx-2 rounded-lg transition-all text-sm font-medium ${active ? 'bg-white text-cyan-600 shadow-sm' : 'text-slate-600 hover:bg-slate-200/50'}`}>
    {icon} {label}
  </a>
);

const MobileNav = () => (
  <nav className="md:hidden fixed bottom-0 left-0 w-full bg-white/70 backdrop-blur-xl border-t border-slate-200 flex justify-around py-3 px-2 z-50 pb-safe">
    <a href="#" className="flex flex-col items-center gap-1 text-cyan-600">
      <Rocket size={20} />
      <span className="text-[10px] font-bold">Ops</span>
    </a>
    <a href="#" className="flex flex-col items-center gap-1 text-slate-500">
      <LayoutDashboard size={20} />
      <span className="text-[10px] font-medium">Dash</span>
    </a>
    <a href="#" className="flex flex-col items-center gap-1 text-slate-500">
      <Network size={20} />
      <span className="text-[10px] font-medium">Nodes</span>
    </a>
    <a href="#" className="flex flex-col items-center gap-1 text-slate-500">
      <CircleDollarSign size={20} />
      <span className="text-[10px] font-medium">Costs</span>
    </a>
  </nav>
);

const MainContent = () => (
  <main className="md:ml-64 pt-24 px-4 sm:px-8 pb-12 transition-all">
    <header className="mb-10 flex flex-col md:flex-row md:items-end justify-between gap-6">
      <div>
        <h1 className="text-3xl sm:text-4xl font-extrabold text-[#2c2f31] tracking-tight mb-2 font-heading">AI 网关管理台</h1>
        <p className="text-[#595c5e] max-w-2xl leading-relaxed text-sm sm:text-base">Centralized control for multi-model LLM routing, prompt management, and unified performance monitoring for Luminous AI services.</p>
      </div>
      <div className="flex items-center gap-3">
        <div className="flex items-center bg-[#eef1f3] p-1 rounded-xl">
          <button className="px-4 py-1.5 text-xs font-bold bg-white shadow-sm rounded-lg text-[#4647d3] transition-all">Today</button>
          <button className="px-4 py-1.5 text-xs font-medium text-[#595c5e] hover:text-[#2c2f31] transition-all">7 Days</button>
          <button className="px-4 py-1.5 text-xs font-medium text-[#595c5e] hover:text-[#2c2f31] transition-all">30 Days</button>
        </div>
      </div>
    </header>

    <SummaryCards />
    <TabbedSection />
    <BottomSection />
  </main>
);

const SummaryCards = () => (
  <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-6 mb-10">
    <SummaryCard
      icon={<Network size={24} />}
      iconColor="text-[#4647d3]"
      iconBg="bg-[#4647d3]/10"
      badgeText="+2"
      badgeIcon={<TrendingUp size={14} className="mr-1" />}
      badgeColor="text-[#006947]"
      value="12"
      label="Enabled Providers"
      bgDecoration="bg-[#4647d3]/5"
    />
    <SummaryCard
      icon={<Route size={24} />}
      iconColor="text-[#006947]"
      iconBg="bg-[#006947]/10"
      badgeText="Active"
      badgeColor="text-[#595c5e]"
      value="48"
      label="Enabled Routes"
      bgDecoration="bg-[#006947]/5"
    />
    <SummaryCard
      icon={<TerminalSquare size={24} />}
      iconColor="text-[#815100]"
      iconBg="bg-[#815100]/10"
      badgeText="98% Success"
      badgeColor="text-[#006947]"
      value="156"
      label="Active Templates"
      bgDecoration="bg-[#815100]/5"
    />
    <SummaryCard
      icon={<Activity size={24} />}
      iconColor="text-[#b41340]"
      iconBg="bg-[#b41340]/10"
      badgeText="Real-time"
      badgeColor="text-[#595c5e]"
      value="2.4k"
      label="Current Window Volume"
      bgDecoration="bg-[#b41340]/5"
    />
  </section>
);

const SummaryCard = ({ icon, iconColor, iconBg, badgeText, badgeIcon, badgeColor, value, label, bgDecoration }: any) => (
  <div className="bg-white p-6 rounded-xl shadow-[0px_10px_40px_rgba(44,47,49,0.06)] relative overflow-hidden">
    <div className={`absolute top-0 right-0 w-24 h-24 rounded-full -mr-8 -mt-8 ${bgDecoration}`}></div>
    <div className="flex items-center justify-between mb-4 relative z-10">
      <div className={`p-2 rounded-lg ${iconBg} ${iconColor}`}>
        {icon}
      </div>
      <span className={`text-xs font-bold flex items-center gap-1 ${badgeColor}`}>
        {badgeIcon} {badgeText}
      </span>
    </div>
    <div className="text-3xl font-black mb-1 text-[#2c2f31] relative z-10">{value}</div>
    <div className="text-sm font-medium text-[#595c5e] relative z-10">{label}</div>
  </div>
);

const TabbedSection = () => (
  <section className="bg-white rounded-xl shadow-[0px_10px_40px_rgba(44,47,49,0.06)] overflow-hidden">
    <div className="flex items-center px-2 sm:px-6 border-b border-[#dfe3e6] overflow-x-auto scrollbar-hide">
      <button className="px-4 sm:px-6 py-4 sm:py-5 text-sm font-bold border-b-2 border-[#4647d3] text-[#4647d3] transition-all whitespace-nowrap">Providers</button>
      <button className="px-4 sm:px-6 py-4 sm:py-5 text-sm font-medium text-[#595c5e] hover:text-[#2c2f31] transition-all whitespace-nowrap">Routes</button>
      <button className="px-4 sm:px-6 py-4 sm:py-5 text-sm font-medium text-[#595c5e] hover:text-[#2c2f31] transition-all whitespace-nowrap">Prompt Templates</button>
      <button className="px-4 sm:px-6 py-4 sm:py-5 text-sm font-medium text-[#595c5e] hover:text-[#2c2f31] transition-all whitespace-nowrap">Costs & Quota</button>
      <button className="px-4 sm:px-6 py-4 sm:py-5 text-sm font-medium text-[#595c5e] hover:text-[#2c2f31] transition-all whitespace-nowrap">Call Logs</button>
    </div>
    <div className="p-4 sm:p-8">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
        <div className="flex items-center gap-4">
          <h2 className="text-lg sm:text-xl font-bold text-[#2c2f31] font-heading">Active Providers</h2>
          <span className="px-2 py-0.5 bg-[#eef1f3] text-[10px] font-bold rounded uppercase tracking-wider text-[#595c5e]">Global Reach</span>
        </div>
        <div className="flex items-center gap-3">
          <button className="flex items-center gap-2 px-4 py-2 bg-[#eef1f3] text-[#2c2f31] rounded-lg text-sm font-semibold hover:bg-[#e5e9eb] transition-colors">
            <Filter size={16} /> Filter
          </button>
          <button className="flex items-center gap-2 px-4 py-2 bg-[#4647d3] text-white rounded-lg text-sm font-semibold shadow-lg shadow-[#4647d3]/20 hover:scale-[1.02] transition-transform">
            <PlusCircle size={16} /> Add Provider
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-4 sm:gap-6">
        <ProviderCard
          name="OpenAI"
          logo="https://lh3.googleusercontent.com/aida-public/AB6AXuBOcuGmsECUyYtFUjt0ka-t4JH-eCofzrjg9zswgH5FrUcs89YwS86xw9TjVp2JDuyZ3wh_KHCHkAJ-6No2zOVw-3czBkyRwZNA6cQzy0SxzAPhH99hpJBMA5CKakmOIIQiIeWiH28wnKj1yVCt5k3LB-jCRGAbPmRvo2B-umYmyL27NDqy-XAeCznZYxNid7Y2e9ybz3VDQxTKW5CKqbF5lwzQpNyrPjHwDiVixkguAYFj_rLH3tXgX1ilirly_ojJcU-Eqsv9s8w"
          status="Online"
          statusColor="bg-[#006947]"
          models="4 Models Active"
          latency="12ms"
          uptime="99.9%"
        />
        <ProviderCard
          name="Anthropic"
          logo="https://lh3.googleusercontent.com/aida-public/AB6AXuCaGxltXpcXCs4UrGq4x161hBEmBjc7kLl1f1Zb7ojbS3mSTFa8Xbw5o1Xk5TTnUHbEqCh6Nwthc0e4KwaRdfJ7wwvikaLZD2UXrsf1ac3D-4arz8DHYyoN_suGE9Tq0EqMu600l5FHoChcH4Ozri0r9lnKy5_MvQn26JkNRPLijfO34ISdw3SEwdXiKg_PrOqqvpCobf5hiOnOCOfx4gV7btMArWyzNsq0K6MxU12OM27yJk2-pfcWb8mzGBhi1FR_euLtOpG8OqI"
          status="Online"
          statusColor="bg-[#006947]"
          models="2 Models Active"
          latency="45ms"
          uptime="99.4%"
        />
        <ProviderCard
          name="Azure AI"
          logo="https://lh3.googleusercontent.com/aida-public/AB6AXuACEGInkBK3GjtPLMHqDHBqEkLGaSwJHyWDiIMNk7eDq3OW9_Rd8efL-s5LRearF6PlyQtI6VZEMyVYw99aCpuT65tsOdrG2rlITjY2zAOfccyWnfIDtjkZoWJvf5TByzhD21I3czUu8gahonjxYWl5Zb38CGfqqr7BQNM-MYSxqU_RoxaRMAIaXajXol82XbkSXrvyalmF4V6bbg5KFUhhKPlYYTNN3PVYlmVvuZGrveQGCQdWujBrmIliW7AuctF8BFy3XO1tsyE"
          status="Online"
          statusColor="bg-[#006947]"
          models="8 Models Active"
          latency="18ms"
          uptime="99.9%"
        />
        <ProviderCard
          name="Google Gemini"
          logo="https://lh3.googleusercontent.com/aida-public/AB6AXuAyj_CHgMhOvVBpZlgGN0XLg5m91K7VAem7HpGpDRefBhpTaKCou81ck050z61bE4LL6KKTP9p6BAV_v3f9t9gXKGy6xriXzdOkM1ADJLzs4xvn-W1YB32AWzQNTnAtE3joMym0oh7sRhRgwZmeroqqm4C_ozHvPQ13BfUBZvIijOZjNJhSA9PKFxxKn24-sleYpHMEVwzqQ27w6D2p5O9PpZXINQreBXtsxM5f2UeBZUTEpei9OhSvA6bx1IjpIv-M0EFpwaRwkG0"
          status="Busy"
          statusColor="bg-[#815100]"
          models="3 Models Active"
          latency="110ms"
          uptime="98.2%"
        />
      </div>

      <div className="mt-8 sm:mt-12 pt-6 sm:pt-8 border-t border-[#e5e9eb] flex flex-col md:flex-row gap-6 sm:gap-8 items-start md:items-center justify-between">
        <div className="flex flex-wrap items-center gap-8 sm:gap-12">
          <div>
            <div className="text-[10px] sm:text-xs font-bold text-[#595c5e] uppercase tracking-widest mb-1">Global Error Rate</div>
            <div className="text-lg sm:text-xl font-bold text-[#2c2f31] flex items-center gap-2">
              0.024% <span className="text-[#006947] text-xs sm:text-sm flex items-center"><TrendingUp size={14} className="mr-1 rotate-180" /> 0.001%</span>
            </div>
          </div>
          <div>
            <div className="text-[10px] sm:text-xs font-bold text-[#595c5e] uppercase tracking-widest mb-1">Avg. Tokens/Min</div>
            <div className="text-lg sm:text-xl font-bold text-[#2c2f31]">142,000</div>
          </div>
        </div>
        <div className="w-full md:w-64 h-2 bg-[#eef1f3] rounded-full overflow-hidden">
          <div className="h-full bg-gradient-to-r from-[#4647d3] to-[#006947] w-[85%] rounded-full shadow-[0_0_8px_rgba(70,71,211,0.3)]"></div>
        </div>
      </div>
    </div>
  </section>
);

const ProviderCard = ({ name, logo, status, statusColor, models, latency, uptime }: any) => (
  <div className="flex items-center justify-between p-4 sm:p-5 rounded-xl bg-[#eef1f3]/50 hover:bg-[#eef1f3] transition-all group">
    <div className="flex items-center gap-4 sm:gap-5">
      <div className="w-12 h-12 sm:w-14 sm:h-14 rounded-xl bg-white flex items-center justify-center p-2 sm:p-2.5 shadow-sm shrink-0">
        <img src={logo} alt={name} className="w-full h-full object-contain" />
      </div>
      <div>
        <h3 className="font-bold text-[#2c2f31] text-sm sm:text-base">{name}</h3>
        <div className="flex items-center gap-2 sm:gap-3 text-xs text-[#595c5e] mt-1">
          <span className="flex items-center gap-1.5">
            <span className={`w-1.5 h-1.5 rounded-full ${statusColor}`}></span> {status}
          </span>
          <span>•</span>
          <span className="truncate">{models}</span>
        </div>
      </div>
    </div>
    <div className="flex items-center gap-4 sm:gap-8">
      <div className="text-right hidden sm:block">
        <div className="text-sm font-bold text-[#2c2f31]">{latency}</div>
        <div className="text-[10px] uppercase font-bold text-[#595c5e] mt-0.5 tracking-wider">Latency</div>
      </div>
      <div className="text-right hidden sm:block">
        <div className="text-sm font-bold text-[#006947]">{uptime}</div>
        <div className="text-[10px] uppercase font-bold text-[#595c5e] mt-0.5 tracking-wider">Uptime</div>
      </div>
      <button className="p-2 rounded-lg hover:bg-white text-[#595c5e] transition-colors">
        <MoreVertical size={20} />
      </button>
    </div>
  </div>
);

const BottomSection = () => (
  <section className="mt-6 sm:mt-10 grid grid-cols-1 lg:grid-cols-3 gap-6">
    <div className="lg:col-span-2 bg-white p-4 sm:p-6 rounded-xl shadow-[0px_10px_40px_rgba(44,47,49,0.06)]">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <h3 className="font-bold text-[#2c2f31] flex items-center gap-2">
          <Activity size={20} className="text-[#4647d3]" /> Recent Call Volume
        </h3>
        <div className="relative self-start sm:self-auto">
          <select className="appearance-none bg-[#eef1f3] border-none text-xs font-bold text-[#2c2f31] rounded-lg py-1.5 pl-3 pr-8 focus:outline-none cursor-pointer">
            <option>Real-time (Last 15m)</option>
            <option>Last 1 Hour</option>
          </select>
          <ChevronDown size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-[#595c5e] pointer-events-none" />
        </div>
      </div>
      <div className="h-48 w-full bg-[#eef1f3]/30 rounded-xl flex items-end justify-between p-2 sm:p-4 gap-1 sm:gap-2 mt-2">
        {[40, 60, 45, 80, 95, 70, 30, 55, 65, 90, 100, 75].map((height, i) => (
          <div key={i} className="bg-[#4647d3]/20 hover:bg-[#4647d3] w-full rounded-t transition-all duration-300 cursor-pointer" style={{ height: `${height}%` }}></div>
        ))}
      </div>
    </div>

    <div className="bg-white p-4 sm:p-6 rounded-xl shadow-[0px_10px_40px_rgba(44,47,49,0.06)] flex flex-col">
      <h3 className="font-bold text-[#2c2f31] flex items-center gap-2 mb-6">
        <AlertCircle size={20} className="text-[#815100]" /> System Alerts
      </h3>
      <div className="space-y-4 flex-1">
        <div className="flex gap-3 sm:gap-4 p-3 sm:p-4 rounded-lg bg-[#f74b6d]/10 border-l-4 border-[#b41340]">
          <AlertCircle size={20} className="text-[#b41340] shrink-0 mt-0.5" />
          <div>
            <div className="text-xs font-bold text-[#b41340] mb-1">Latency Spike: Azure AI</div>
            <div className="text-[10px] text-[#595c5e] leading-relaxed">US-East region experiencing 400ms+ delay.</div>
          </div>
        </div>
        <div className="flex gap-3 sm:gap-4 p-3 sm:p-4 rounded-lg bg-[#f8a010]/10 border-l-4 border-[#815100]">
          <Clock size={20} className="text-[#815100] shrink-0 mt-0.5" />
          <div>
            <div className="text-xs font-bold text-[#815100] mb-1">Quota Approaching</div>
            <div className="text-[10px] text-[#595c5e] leading-relaxed">OpenAI API used 85% of monthly credit.</div>
          </div>
        </div>
      </div>
      <button className="mt-6 w-full py-2 bg-[#eef1f3] text-[#2c2f31] text-xs font-bold rounded-lg hover:bg-[#e5e9eb] transition-all">
        View All Alerts
      </button>
    </div>
  </section>
);

