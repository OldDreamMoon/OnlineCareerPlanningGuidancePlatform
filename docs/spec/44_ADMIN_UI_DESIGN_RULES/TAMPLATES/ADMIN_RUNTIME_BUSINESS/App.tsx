/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import {
  Search, Bell, Settings, Terminal, Flag, Sliders, Brain, ShieldAlert, HelpCircle,
  ChevronRight, CreditCard, ArrowRightLeft, ShieldCheck, History, AlertTriangle,
  Sparkles, Shield, FileSearch, Settings2, Zap, Users, CheckCircle2
} from 'lucide-react';

const TopNav = () => (
  <header className="fixed top-0 w-full z-50 bg-white/70 backdrop-blur-xl shadow-sm border-b border-slate-200/50">
    <div className="flex justify-between items-center h-16 px-6 w-full">
      <div className="flex items-center gap-8">
        <span className="text-xl font-bold tracking-tight text-slate-900 font-display">Runtime Config Center</span>
        <nav className="hidden md:flex gap-6 items-center h-full">
          <a className="font-display text-sm font-medium text-indigo-600 border-b-2 border-indigo-600 h-16 flex items-center" href="#">Business Flags</a>
          <a className="font-display text-sm font-medium text-slate-500 hover:text-slate-900 transition-colors h-16 flex items-center px-2" href="#">Operations Parameters</a>
          <a className="font-display text-sm font-medium text-slate-500 hover:text-slate-900 transition-colors h-16 flex items-center px-2" href="#">AI Channels</a>
          <a className="font-display text-sm font-medium text-slate-500 hover:text-slate-900 transition-colors h-16 flex items-center px-2" href="#">Risk Degradation</a>
        </nav>
      </div>
      <div className="flex items-center gap-4">
        <div className="bg-slate-100/50 flex items-center px-3 py-1.5 rounded-full border border-slate-200/50">
          <Search className="text-slate-400 w-4 h-4 mr-2" />
          <input className="bg-transparent border-none focus:ring-0 text-sm w-48 placeholder-slate-400 outline-none" placeholder="Search flags..." type="text" />
        </div>
        <div className="flex gap-2">
          <button className="p-2 text-slate-500 hover:bg-slate-100 rounded-full transition-colors">
            <Bell className="w-5 h-5" />
          </button>
          <button className="p-2 text-slate-500 hover:bg-slate-100 rounded-full transition-colors">
            <Settings className="w-5 h-5" />
          </button>
        </div>
        <img alt="User profile" className="w-8 h-8 rounded-full border border-slate-200" src="https://lh3.googleusercontent.com/aida-public/AB6AXuAiqN2B82X4Sb2EgWNCLqE4N7LPtBuGRtSO_QdBR8Z29LnNzMP5Taclnm3QlGnBqXwvy5rFkV29Dkmgg9SKV4A_wrFe_ZmxWAhDlZcedgtIsSaenhTW8gVdcYMIO-vp1IoQHHr70cpf5g4rvqgv0QsqYIHcbkHpqNglbmmFbh1FC9Nq3hYWLeTaW_gqZLBo8ndGKVmRrWDsbf9zCoqwIAFL5s9hQKrJeF9GHVOpk0CCCzCJyiQHfZrf_jHvQdFfrg6EV_FfnWY5F7I" />
      </div>
    </div>
  </header>
);

const SideNav = () => (
  <aside className="fixed left-0 top-16 h-[calc(100vh-64px)] w-64 bg-slate-50 flex flex-col py-4 space-y-2 border-r border-slate-200/50 z-40">
    <div className="px-6 py-4 mb-4">
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 bg-indigo-100 rounded-xl flex items-center justify-center">
          <Terminal className="text-indigo-600 w-5 h-5" />
        </div>
        <div>
          <h3 className="text-sm font-bold text-slate-900 leading-none">Admin Core</h3>
          <p className="text-[10px] text-slate-500 mt-1">v2.4.0-stable</p>
        </div>
      </div>
    </div>
    <nav className="flex-1 space-y-1">
      <a className="bg-indigo-50 text-indigo-700 font-semibold rounded-lg mx-2 flex items-center px-4 py-2.5 gap-3" href="#">
        <Flag className="w-5 h-5" />
        <span className="font-display text-sm">Business Flags</span>
      </a>
      <a className="text-slate-600 hover:bg-slate-100 mx-2 rounded-lg flex items-center px-4 py-2.5 gap-3 transition-colors" href="#">
        <Sliders className="w-5 h-5" />
        <span className="font-display text-sm">Operations Parameters</span>
      </a>
      <a className="text-slate-600 hover:bg-slate-100 mx-2 rounded-lg flex items-center px-4 py-2.5 gap-3 transition-colors" href="#">
        <Brain className="w-5 h-5" />
        <span className="font-display text-sm">AI Channels</span>
      </a>
      <a className="text-slate-600 hover:bg-slate-100 mx-2 rounded-lg flex items-center px-4 py-2.5 gap-3 transition-colors" href="#">
        <ShieldAlert className="w-5 h-5" />
        <span className="font-display text-sm">Risk Degradation</span>
      </a>
    </nav>
    <div className="px-4 py-4 space-y-4">
      <button className="w-full bg-gradient-to-br from-indigo-500 to-indigo-700 text-white font-semibold py-2.5 rounded-xl shadow-lg shadow-indigo-500/20 hover:scale-[1.02] active:scale-95 transition-all">
        Deploy Changes
      </button>
      <div className="pt-4 border-t border-slate-200/50">
        <a className="text-slate-500 hover:bg-slate-100 mx-[-8px] px-4 py-2 rounded-lg flex items-center gap-3 transition-colors" href="#">
          <Terminal className="w-5 h-5" />
          <span className="font-display text-sm">Logs</span>
        </a>
        <a className="text-slate-500 hover:bg-slate-100 mx-[-8px] px-4 py-2 rounded-lg flex items-center gap-3 transition-colors" href="#">
          <HelpCircle className="w-5 h-5" />
          <span className="font-display text-sm">Support</span>
        </a>
      </div>
    </div>
  </aside>
);

export default function App() {
  return (
    <div className="min-h-screen bg-[#f5f7f9] text-[#2c2f31] font-sans">
      <TopNav />
      <SideNav />
      
      <main className="pl-64 pt-16">
        <div className="p-8 max-w-7xl mx-auto">
          
          {/* Page Header */}
          <div className="mb-10 flex justify-between items-end">
            <div>
              <nav className="flex items-center gap-2 text-xs font-medium text-slate-400 mb-2">
                <span>CORE ADMIN</span>
                <ChevronRight className="w-3 h-3" />
                <span className="text-indigo-600">BUSINESS FLAGS</span>
              </nav>
              <h1 className="text-4xl font-extrabold tracking-tight text-slate-900 font-display">Business Flags</h1>
              <p className="text-slate-500 mt-2 text-lg">Control global feature behaviors across runtime environments.</p>
            </div>
            <div className="flex gap-3">
              <div className="bg-slate-200/50 px-4 py-2 rounded-xl flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-600"></span>
                <span className="text-sm font-semibold text-slate-700">Production Sync Active</span>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-12 gap-6">
            
            {/* Payment Domain Section */}
            <div className="col-span-12">
              <div className="flex items-center gap-3 mb-4">
                <CreditCard className="text-indigo-600 w-6 h-6" />
                <h2 className="text-xl font-bold font-display">Payment Services</h2>
                <span className="text-xs bg-slate-200/50 px-2 py-0.5 rounded font-mono text-slate-500">PAY-DOM-01</span>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                
                {/* Flag Card 1 */}
                <div className="bg-white p-6 rounded-2xl shadow-sm border-l-4 border-emerald-500 hover:shadow-md transition-all">
                  <div className="flex justify-between items-start mb-4">
                    <div>
                      <h3 className="font-bold text-lg font-display">Instant Payouts</h3>
                      <p className="text-sm text-slate-500 line-clamp-1">Enables T+0 settlement for premium merchants.</p>
                    </div>
                    <div className="bg-emerald-100 text-emerald-800 px-2 py-1 rounded text-[10px] font-bold uppercase tracking-wider">Active</div>
                  </div>
                  <div className="flex items-center justify-between py-3 border-y border-slate-100 mb-4">
                    <div className="text-center">
                      <p className="text-[10px] uppercase text-slate-400 font-bold">Current</p>
                      <p className="font-mono text-sm font-bold text-indigo-600">ENABLED</p>
                    </div>
                    <ArrowRightLeft className="text-slate-300 w-5 h-5" />
                    <div className="text-center">
                      <p className="text-[10px] uppercase text-slate-400 font-bold">Default</p>
                      <p className="font-mono text-sm text-slate-400">DISABLED</p>
                    </div>
                  </div>
                  <div className="flex justify-between items-center">
                    <div className="flex items-center gap-1.5">
                      <ShieldCheck className="w-4 h-4 text-emerald-600" />
                      <span className="text-xs font-semibold text-slate-600">Risk: Low</span>
                    </div>
                    <span className="text-[10px] font-bold text-amber-700 px-2 py-0.5 bg-amber-100 rounded-full flex items-center gap-1">
                      <History className="w-3 h-3" /> OVERRIDDEN
                    </span>
                  </div>
                </div>

                {/* Flag Card 2 */}
                <div className="bg-white p-6 rounded-2xl shadow-sm border-l-4 border-slate-200 hover:shadow-md transition-all">
                  <div className="flex justify-between items-start mb-4">
                    <div>
                      <h3 className="font-bold text-lg font-display">Crypto Gateway</h3>
                      <p className="text-sm text-slate-500 line-clamp-1">Support for BTC/ETH on checkout screens.</p>
                    </div>
                    <div className="bg-slate-100 text-slate-500 px-2 py-1 rounded text-[10px] font-bold uppercase tracking-wider">Off</div>
                  </div>
                  <div className="flex items-center justify-between py-3 border-y border-slate-100 mb-4">
                    <div className="text-center">
                      <p className="text-[10px] uppercase text-slate-400 font-bold">Current</p>
                      <p className="font-mono text-sm font-bold text-slate-600">DISABLED</p>
                    </div>
                    <ArrowRightLeft className="text-slate-300 w-5 h-5" />
                    <div className="text-center">
                      <p className="text-[10px] uppercase text-slate-400 font-bold">Default</p>
                      <p className="font-mono text-sm text-slate-400">DISABLED</p>
                    </div>
                  </div>
                  <div className="flex justify-between items-center">
                    <div className="flex items-center gap-1.5">
                      <AlertTriangle className="w-4 h-4 text-amber-600" />
                      <span className="text-xs font-semibold text-slate-600">Risk: Medium</span>
                    </div>
                    <span className="text-[10px] font-bold text-slate-400">SYSTEM DEFAULT</span>
                  </div>
                </div>

              </div>
            </div>

            {/* AI Channels Section */}
            <div className="col-span-12 lg:col-span-8 mt-4">
              <div className="flex items-center gap-3 mb-4">
                <Brain className="text-indigo-600 w-6 h-6" />
                <h2 className="text-xl font-bold font-display">AI & Intelligence</h2>
              </div>
              <div className="grid grid-cols-2 gap-6">
                
                {/* Hero Card */}
                <div className="col-span-2 bg-gradient-to-br from-indigo-600 to-indigo-800 p-8 rounded-3xl text-white relative overflow-hidden shadow-lg shadow-indigo-900/20">
                  <div className="relative z-10">
                    <div className="flex justify-between items-start mb-6">
                      <div>
                        <h3 className="text-2xl font-bold font-display">LLM Orchestration v4</h3>
                        <p className="text-indigo-200 mt-1">Multi-model fallback routing for global API requests.</p>
                      </div>
                      <div className="bg-white/20 backdrop-blur-md px-3 py-1 rounded-full text-xs font-bold">CRITICAL SYSTEM</div>
                    </div>
                    <div className="flex gap-12 items-center">
                      <div className="flex flex-col">
                        <span className="text-[10px] uppercase text-indigo-300 font-bold tracking-widest">Active Model</span>
                        <span className="text-2xl font-mono font-bold tracking-tight">GPT-4-TURBO</span>
                      </div>
                      <div className="flex flex-col">
                        <span className="text-[10px] uppercase text-indigo-300 font-bold tracking-widest">Override Status</span>
                        <span className="flex items-center gap-2 text-emerald-400 font-bold">
                          <CheckCircle2 className="w-4 h-4" /> PROD OVERRIDE
                        </span>
                      </div>
                    </div>
                  </div>
                  <div className="absolute -right-16 -bottom-16 opacity-10">
                    <Sparkles className="w-64 h-64" />
                  </div>
                </div>

                {/* Small AI Cards */}
                <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-100 flex flex-col justify-between">
                  <div>
                    <div className="flex justify-between items-start mb-2">
                      <h3 className="font-bold text-lg font-display">Smart Moderation</h3>
                      <Shield className="text-emerald-600 w-5 h-5" />
                    </div>
                    <p className="text-sm text-slate-500">Real-time content filtering using vision transformers.</p>
                  </div>
                  <div className="mt-6 flex items-center justify-between">
                    <span className="text-xs font-bold bg-emerald-50 text-emerald-700 px-2 py-1 rounded">BETA ENABLED</span>
                    <span className="text-[10px] text-slate-400 font-mono">ID: MOD-772</span>
                  </div>
                </div>

                <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-100 flex flex-col justify-between">
                  <div>
                    <div className="flex justify-between items-start mb-2">
                      <h3 className="font-bold text-lg font-display">Semantic Search</h3>
                      <FileSearch className="text-slate-300 w-5 h-5" />
                    </div>
                    <p className="text-sm text-slate-500">Vector-based discovery for marketplace items.</p>
                  </div>
                  <div className="mt-6 flex items-center justify-between">
                    <span className="text-xs font-bold bg-slate-100 text-slate-500 px-2 py-1 rounded">DISABLED</span>
                    <span className="text-[10px] text-slate-400 font-mono">ID: AI-SRCH</span>
                  </div>
                </div>

              </div>
            </div>

            {/* Core & User Section */}
            <div className="col-span-12 lg:col-span-4 mt-4">
              <div className="flex items-center gap-3 mb-4">
                <Settings2 className="text-indigo-600 w-6 h-6" />
                <h2 className="text-xl font-bold font-display">Core & User</h2>
              </div>
              <div className="flex flex-col gap-6">
                
                {/* Canary Rollout */}
                <div className="bg-indigo-50/50 p-6 rounded-3xl border-2 border-dashed border-indigo-200/50 relative">
                  <h3 className="font-bold text-indigo-900 mb-2 font-display">Canary Rollout: Social Graph</h3>
                  <p className="text-sm text-indigo-700/70 mb-4">Traffic steering for new networking features.</p>
                  <div className="bg-white rounded-2xl p-4 shadow-sm">
                    <div className="flex justify-between text-xs mb-2">
                      <span className="font-bold text-slate-600">Deployment Progress</span>
                      <span className="text-indigo-600 font-extrabold">35%</span>
                    </div>
                    <div className="w-full bg-slate-100 h-2 rounded-full overflow-hidden">
                      <div className="bg-gradient-to-r from-indigo-500 to-emerald-400 h-full w-[35%] rounded-full"></div>
                    </div>
                  </div>
                </div>

                {/* Circuit Breaker */}
                <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-100">
                  <div className="flex items-center gap-3 mb-4">
                    <div className="w-10 h-10 bg-rose-100 flex items-center justify-center rounded-xl">
                      <ShieldAlert className="text-rose-600 w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="font-bold font-display">Circuit Breaker</h3>
                      <p className="text-[10px] uppercase tracking-tighter text-rose-600 font-bold">High Risk Level</p>
                    </div>
                  </div>
                  <div className="space-y-3">
                    <div className="flex justify-between items-center px-3 py-2 bg-slate-50 rounded-xl">
                      <span className="text-sm text-slate-600">Auto-Scaling Max</span>
                      <span className="font-mono text-sm font-bold">500 Nodes</span>
                    </div>
                    <div className="flex justify-between items-center px-3 py-2 bg-slate-50 rounded-xl">
                      <span className="text-sm text-slate-600">DB Kill-Switch</span>
                      <span className="text-[10px] font-bold text-emerald-600 flex items-center gap-1">
                        <Zap className="w-3 h-3" /> ARMED
                      </span>
                    </div>
                  </div>
                </div>

              </div>
            </div>

            {/* Community Features Section */}
            <div className="col-span-12 mt-8">
              <div className="flex items-center gap-3 mb-4">
                <Users className="text-indigo-600 w-6 h-6" />
                <h2 className="text-xl font-bold font-display">Community Features</h2>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
                
                <div className="bg-white p-6 rounded-2xl border-t-4 border-indigo-200 shadow-sm">
                  <h3 className="font-bold mb-1 font-display">Live Events</h3>
                  <p className="text-xs text-slate-500 mb-4">Broadcast mode for creator rooms.</p>
                  <div className="flex items-center gap-2">
                    <div className="w-2 h-2 rounded-full bg-emerald-600"></div>
                    <span className="text-sm font-bold text-slate-800">ON</span>
                  </div>
                </div>

                <div className="bg-white p-6 rounded-2xl border-t-4 border-indigo-200 shadow-sm">
                  <h3 className="font-bold mb-1 font-display">Gift Gifting</h3>
                  <p className="text-xs text-slate-500 mb-4">Virtual economy micro-transactions.</p>
                  <div className="flex items-center gap-2">
                    <div className="w-2 h-2 rounded-full bg-emerald-600"></div>
                    <span className="text-sm font-bold text-slate-800">ON</span>
                  </div>
                </div>

                <div className="bg-white p-6 rounded-2xl border-t-4 border-indigo-200 shadow-sm">
                  <h3 className="font-bold mb-1 font-display">Private Hubs</h3>
                  <p className="text-xs text-slate-500 mb-4">Invite-only community clusters.</p>
                  <div className="flex items-center gap-2">
                    <div className="w-2 h-2 rounded-full bg-slate-300"></div>
                    <span className="text-sm font-bold text-slate-400">OFF</span>
                  </div>
                </div>

                <div className="bg-white p-6 rounded-2xl border-t-4 border-indigo-200 shadow-sm">
                  <h3 className="font-bold mb-1 font-display">Audio Spaces</h3>
                  <p className="text-xs text-slate-500 mb-4">Real-time voice conversation rooms.</p>
                  <div className="flex items-center gap-2">
                    <div className="w-2 h-2 rounded-full bg-emerald-600"></div>
                    <span className="text-sm font-bold text-slate-800">ON</span>
                  </div>
                </div>

              </div>
            </div>

            {/* Deployment Visualizer */}
            <div className="col-span-12 mt-12 bg-white p-1 rounded-3xl shadow-xl shadow-indigo-100/50 border border-indigo-50 relative overflow-hidden">
              <div className="flex flex-col md:flex-row items-stretch">
                <div className="bg-slate-900 text-white p-8 md:w-1/3 rounded-2xl md:rounded-r-none">
                  <div className="inline-block bg-indigo-500/20 text-indigo-300 text-[10px] font-bold px-2 py-1 rounded mb-4">LIVE METRIC</div>
                  <h4 className="text-2xl font-bold mb-2 font-display">Propagation Heatmap</h4>
                  <p className="text-slate-400 text-sm mb-6">Real-time status of flag syncing across global edge nodes.</p>
                  <div className="space-y-4">
                    <div className="flex justify-between items-center text-xs">
                      <span className="font-mono">US-EAST-1</span>
                      <span className="text-emerald-400">Sync Complete</span>
                    </div>
                    <div className="flex justify-between items-center text-xs">
                      <span className="font-mono">EU-CENTRAL-1</span>
                      <span className="text-emerald-400">Sync Complete</span>
                    </div>
                    <div className="flex justify-between items-center text-xs">
                      <span className="font-mono">AP-NORTHEAST-1</span>
                      <span className="text-amber-500">In Progress...</span>
                    </div>
                  </div>
                </div>
                <div className="flex-1 p-8 bg-slate-50/50 rounded-2xl md:rounded-l-none flex flex-col justify-center">
                  <div className="grid grid-cols-10 gap-2 content-center">
                    {/* Simulated Grid of Nodes */}
                    {Array.from({ length: 30 }).map((_, i) => {
                      const isAmber = i === 7 || i === 17;
                      const opacity = isAmber ? 'opacity-100 animate-pulse' : `opacity-${[60, 70, 80, 90, 100][Math.floor(Math.random() * 5)]}`;
                      const bgColor = isAmber ? 'bg-amber-600' : 'bg-emerald-700';
                      return (
                        <div key={i} className={`aspect-square rounded-md ${bgColor} ${opacity}`}></div>
                      );
                    })}
                  </div>
                  <p className="text-center text-[10px] font-bold text-slate-400 mt-6 uppercase tracking-widest">Edge Propagation Visualizer v1.2</p>
                </div>
              </div>
            </div>

          </div>
        </div>
      </main>
    </div>
  );
}
