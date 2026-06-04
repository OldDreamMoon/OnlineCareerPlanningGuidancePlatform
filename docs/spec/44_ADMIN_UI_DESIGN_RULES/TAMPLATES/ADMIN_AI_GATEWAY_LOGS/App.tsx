/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { 
  Rocket, LayoutDashboard, Network, Route, TerminalSquare, Banknote, FileText, 
  Plus, Book, HelpCircle, Search, Bell, Settings, Filter, ChevronRight, ChevronLeft, 
  X, Copy, RotateCcw 
} from 'lucide-react';

const logs = [
  { id: 'tr_82y7v1', user: 'user_demo_99', task: 'CHAT', model: 'GPT-4o', latency: 1240, latencyStr: '1.2s', latencyPct: 45, tokens: '1,402', cost: '$0.0421', status: 'SUCCESS', isError: false },
  { id: 'tr_31x9p2', user: 'user_anon_22', task: 'IMAGE', model: 'DALL-E 3', latency: 12500, latencyStr: '12.5s', latencyPct: 90, tokens: '0', cost: '$0.0000', status: 'TIMEOUT', isError: true },
  { id: 'tr_a5s8k0', user: 'internal_crawler', task: 'EMBED', model: 'text-ada-002', latency: 82, latencyStr: '82ms', latencyPct: 12, tokens: '256', cost: '$0.0001', status: 'SUCCESS', isError: false },
  { id: 'tr_z1q4m7', user: 'user_pro_881', task: 'CHAT', model: 'Claude 3.5', latency: 2100, latencyStr: '2.1s', latencyPct: 60, tokens: '4,110', cost: '$0.1230', status: 'SUCCESS', isError: false },
];

export default function App() {
  const [selectedTrace, setSelectedTrace] = useState<string | null>('tr_82y7v1');

  return (
    <div className="min-h-screen flex bg-surface font-body text-on-surface relative overflow-hidden">
      {/* Global Layout Decorative Elements */}
      <div className="fixed top-0 right-0 -z-10 w-[600px] h-[600px] bg-primary/5 rounded-full blur-3xl pointer-events-none"></div>
      <div className="fixed bottom-0 left-0 -z-10 w-[400px] h-[400px] bg-secondary/5 rounded-full blur-3xl pointer-events-none"></div>

      {/* Sidebar */}
      <aside className="w-64 h-screen fixed left-0 top-0 bg-slate-50 flex flex-col py-4 z-40 border-r border-outline-variant/15">
        <div className="px-6 py-4 mb-4">
          <h2 className="text-lg font-bold text-slate-900 font-headline">AI Management</h2>
          <p className="text-xs text-slate-500 font-medium">Graduation Project</p>
        </div>
        <nav className="flex-1 space-y-1">
          <NavItem icon={<Rocket size={18} />} label="Operations" />
          <NavItem icon={<LayoutDashboard size={18} />} label="Dashboard" />
          <NavItem icon={<Network size={18} />} label="Providers" />
          <NavItem icon={<Route size={18} />} label="Routing" />
          <NavItem icon={<TerminalSquare size={18} />} label="Prompts" />
          <NavItem icon={<Banknote size={18} />} label="Costs" />
          <NavItem icon={<FileText size={18} />} label="Logs" active />
        </nav>
        <div className="mt-auto px-4 space-y-4">
          <button className="w-full bg-gradient-to-br from-primary to-primary-dim text-white py-2.5 rounded-xl font-bold shadow-[0px_10px_40px_rgba(70,71,211,0.2)] flex items-center justify-center gap-2 text-sm hover:opacity-90 transition-opacity">
            <Plus size={16} /> New App
          </button>
          <div className="pt-4 border-t border-outline-variant/15 space-y-1">
            <BottomNavItem icon={<Book size={16} />} label="Docs" />
            <BottomNavItem icon={<HelpCircle size={16} />} label="Support" />
          </div>
        </div>
      </aside>

      {/* Header */}
      <header className="fixed top-0 left-64 right-0 z-30 bg-surface/70 backdrop-blur-xl shadow-[0px_10px_40px_rgba(44,47,49,0.06)] flex justify-between items-center px-8 h-16">
        <div className="flex items-center gap-3">
          <span className="text-xl font-black text-slate-900 font-headline tracking-tight">Luminous AI Suite</span>
        </div>
        <div className="flex items-center gap-6">
          <div className="relative hidden md:block">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
            <input 
              type="text" 
              placeholder="Search logs..." 
              className="bg-surface-container-low border-none rounded-full pl-9 pr-4 py-1.5 text-sm w-64 focus:ring-2 focus:ring-primary/20 focus:outline-none placeholder:text-slate-400"
            />
          </div>
          <div className="flex items-center gap-4">
            <button className="text-slate-500 hover:bg-slate-100/50 p-2 rounded-full transition-colors">
              <Bell size={20} />
            </button>
            <button className="text-slate-500 hover:bg-slate-100/50 p-2 rounded-full transition-colors">
              <Settings size={20} />
            </button>
            <img 
              src="https://i.pravatar.cc/150?img=32" 
              alt="User" 
              className="w-8 h-8 rounded-full border-2 border-white shadow-sm"
            />
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className={`ml-64 pt-24 p-8 min-h-screen transition-all duration-300 w-full ${selectedTrace ? 'mr-[480px]' : ''}`}>
        <div className="mb-8 flex justify-between items-end">
          <div>
            <h1 className="text-3xl font-black font-headline text-on-surface tracking-tight mb-2">Call Logs</h1>
            <p className="text-on-surface-variant max-w-lg text-sm">Monitor every request processed through your AI Gateway with granular technical precision.</p>
          </div>
          <div className="flex gap-4">
            <div className="bg-surface-container-lowest p-4 rounded-xl shadow-[0px_10px_40px_rgba(44,47,49,0.06)] border border-outline-variant/15">
              <p className="text-[10px] font-bold text-on-surface-variant uppercase tracking-wider mb-1">Total Logs (24h)</p>
              <p className="text-2xl font-black font-headline text-primary">128,492</p>
            </div>
            <div className="bg-surface-container-lowest p-4 rounded-xl shadow-[0px_10px_40px_rgba(44,47,49,0.06)] border border-outline-variant/15">
              <p className="text-[10px] font-bold text-on-surface-variant uppercase tracking-wider mb-1">Avg Latency</p>
              <p className="text-2xl font-black font-headline text-secondary">420ms</p>
            </div>
          </div>
        </div>

        {/* Filter Bar */}
        <div className="bg-surface-container-low p-3 rounded-xl mb-6 flex flex-wrap gap-4 items-center">
          <div className="flex-1 min-w-[200px]">
            <label className="block text-[10px] font-bold text-on-surface-variant uppercase mb-1 ml-2">Search IDs</label>
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
              <input type="text" placeholder="Trace ID or User ID..." className="w-full bg-surface-container-lowest border-none rounded-lg pl-9 py-2 text-sm focus:ring-2 focus:ring-primary/20 focus:outline-none placeholder:text-slate-400" />
            </div>
          </div>
          <FilterSelect label="Task Type" options={['All Tasks', 'Chat', 'Embedding', 'Image']} />
          <FilterSelect label="Provider" options={['All Providers', 'OpenAI', 'Anthropic', 'Google Gemini']} />
          <FilterSelect label="Status" options={['All Status', 'Success', 'Error', 'Throttled']} />
          <div className="flex items-end h-full self-end pb-0.5">
            <button className="bg-on-surface text-surface py-2 px-4 rounded-lg text-sm font-bold flex items-center gap-2 hover:opacity-90 transition-opacity">
              <Filter size={16} /> Apply
            </button>
          </div>
        </div>

        {/* Table */}
        <div className="bg-surface-container-lowest rounded-xl shadow-[0px_10px_40px_rgba(44,47,49,0.06)] overflow-hidden border border-outline-variant/15">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse min-w-[800px]">
              <thead>
                <tr className="bg-surface-container-low/50 border-b border-outline-variant/15">
                  <th className="px-6 py-4 text-[10px] font-bold text-on-surface-variant uppercase tracking-widest">Trace ID</th>
                  <th className="px-4 py-4 text-[10px] font-bold text-on-surface-variant uppercase tracking-widest">User ID</th>
                  <th className="px-4 py-4 text-[10px] font-bold text-on-surface-variant uppercase tracking-widest">Task</th>
                  <th className="px-4 py-4 text-[10px] font-bold text-on-surface-variant uppercase tracking-widest">Model</th>
                  <th className="px-4 py-4 text-[10px] font-bold text-on-surface-variant uppercase tracking-widest">Latency</th>
                  <th className="px-4 py-4 text-[10px] font-bold text-on-surface-variant uppercase tracking-widest text-right">Tokens</th>
                  <th className="px-4 py-4 text-[10px] font-bold text-on-surface-variant uppercase tracking-widest text-right">Cost</th>
                  <th className="px-4 py-4 text-[10px] font-bold text-on-surface-variant uppercase tracking-widest text-center">Status</th>
                  <th className="px-6 py-4"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/15">
                {logs.map(log => (
                  <tr 
                    key={log.id} 
                    onClick={() => setSelectedTrace(log.id)}
                    className={`transition-colors cursor-pointer group ${log.isError ? 'bg-error-container/5 hover:bg-error-container/10' : 'hover:bg-surface-container-low/50'} ${selectedTrace === log.id ? 'bg-surface-container-low/50' : ''}`}
                  >
                    <td className="px-6 py-4">
                      <span className={`font-mono text-xs font-bold ${log.isError ? 'text-error' : 'text-primary'}`}>{log.id}</span>
                    </td>
                    <td className="px-4 py-4">
                      <span className="text-sm font-medium">{log.user}</span>
                    </td>
                    <td className="px-4 py-4">
                      <span className="px-2 py-0.5 bg-surface-container-high rounded text-[10px] font-bold text-on-surface-variant">{log.task}</span>
                    </td>
                    <td className="px-4 py-4 text-sm font-medium text-on-surface">{log.model}</td>
                    <td className="px-4 py-4">
                      <div className="flex items-center gap-2">
                        <div className="w-16 h-1.5 bg-surface-container rounded-full overflow-hidden">
                          <div className={`h-full ${log.isError ? 'bg-error' : 'bg-secondary'}`} style={{ width: `${log.latencyPct}%` }}></div>
                        </div>
                        <span className="text-xs font-mono">{log.latencyStr}</span>
                      </div>
                    </td>
                    <td className="px-4 py-4 text-right text-xs font-mono">{log.tokens}</td>
                    <td className="px-4 py-4 text-right text-xs font-mono">{log.cost}</td>
                    <td className="px-4 py-4 text-center">
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-[10px] font-bold ${log.isError ? 'bg-error-container text-on-error-container' : 'bg-secondary-container text-on-secondary-fixed'}`}>
                        {log.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-right">
                      <ChevronRight className="inline-block text-slate-400 group-hover:text-primary transition-colors" size={18} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="px-6 py-4 bg-surface-container-low/30 flex justify-between items-center border-t border-outline-variant/15">
            <span className="text-xs font-medium text-on-surface-variant">Showing 1-4 of 128,492 logs</span>
            <div className="flex gap-2">
              <button className="p-1.5 rounded-lg border border-outline-variant/20 hover:bg-white transition-colors disabled:opacity-30">
                <ChevronLeft size={16} />
              </button>
              <button className="p-1.5 rounded-lg border border-outline-variant/20 bg-white shadow-sm font-bold text-xs px-3">1</button>
              <button className="p-1.5 rounded-lg border border-outline-variant/20 hover:bg-white transition-colors font-medium text-xs px-3">2</button>
              <button className="p-1.5 rounded-lg border border-outline-variant/20 hover:bg-white transition-colors font-medium text-xs px-3">3</button>
              <button className="p-1.5 rounded-lg border border-outline-variant/20 hover:bg-white transition-colors disabled:opacity-30">
                <ChevronRight size={16} />
              </button>
            </div>
          </div>
        </div>
      </main>

      {/* Drawer */}
      <div 
        className={`fixed inset-y-0 right-0 w-[480px] bg-white shadow-2xl z-[60] border-l border-outline-variant/15 flex flex-col transform transition-transform duration-300 ${selectedTrace ? 'translate-x-0' : 'translate-x-full'}`}
      >
        <div className="p-6 border-b border-outline-variant/15 flex justify-between items-start bg-white/70 backdrop-blur-xl">
          <div>
            <h3 className="text-lg font-black font-headline tracking-tight">Trace: {selectedTrace}</h3>
            <p className="text-xs text-on-surface-variant mt-1">Nov 24, 2023 · 14:22:10.452 UTC</p>
          </div>
          <button onClick={() => setSelectedTrace(null)} className="p-2 hover:bg-surface-container rounded-full transition-colors">
            <X size={20} />
          </button>
        </div>
        
        <div className="flex-1 overflow-y-auto p-6 space-y-6 no-scrollbar">
          <div className="grid grid-cols-2 gap-4">
            <div className="bg-surface-container-low p-3 rounded-xl">
              <p className="text-[10px] font-bold text-on-surface-variant uppercase mb-1">Latency</p>
              <p className="text-sm font-mono font-bold">1,240 ms</p>
            </div>
            <div className="bg-surface-container-low p-3 rounded-xl">
              <p className="text-[10px] font-bold text-on-surface-variant uppercase mb-1">Tokens</p>
              <p className="text-sm font-mono font-bold">1,402 (310 / 1,092)</p>
            </div>
            <div className="bg-surface-container-low p-3 rounded-xl">
              <p className="text-[10px] font-bold text-on-surface-variant uppercase mb-1">Model</p>
              <p className="text-sm font-medium">gpt-4o-2024-05-13</p>
            </div>
            <div className="bg-surface-container-low p-3 rounded-xl">
              <p className="text-[10px] font-bold text-on-surface-variant uppercase mb-1">Cost</p>
              <p className="text-sm font-mono font-bold text-secondary">$0.04210</p>
            </div>
          </div>

          <div>
            <div className="flex items-center justify-between mb-2">
              <h4 className="text-sm font-bold text-on-surface tracking-wide">Request Payload</h4>
              <button className="text-[10px] font-bold text-primary flex items-center gap-1 hover:opacity-80">
                <Copy size={12} /> COPY
              </button>
            </div>
            <div className="bg-slate-900 rounded-xl p-4 overflow-hidden">
              <pre className="text-[11px] font-mono text-slate-300 leading-relaxed overflow-x-auto">
{`{
  "model": "gpt-4o",
  "messages": [
    {
      "role": "system",
      "content": "You are a helpful analyst."
    },
    {
      "role": "user",
      "content": "Analyze the Q3 quarterly report..."
    }
  ],
  "temperature": 0.7,
  "max_tokens": 1000
}`}
              </pre>
            </div>
          </div>

          <div>
            <div className="flex items-center justify-between mb-2">
              <h4 className="text-sm font-bold text-on-surface tracking-wide">Response Object</h4>
              <button className="text-[10px] font-bold text-primary flex items-center gap-1 hover:opacity-80">
                <Copy size={12} /> COPY
              </button>
            </div>
            <div className="bg-slate-900 rounded-xl p-4 overflow-hidden">
              <pre className="text-[11px] font-mono text-slate-300 leading-relaxed overflow-x-auto">
{`{
  "id": "chatcmpl-9A7v1...",
  "object": "chat.completion",
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": "The Q3 report indicates a 14% growth..."
      }
    }
  ],
  "usage": {
    "prompt_tokens": 310,
    "completion_tokens": 1092
  }
}`}
              </pre>
            </div>
          </div>

          <div>
            <h4 className="text-sm font-bold text-on-surface tracking-wide mb-3">Execution Timeline</h4>
            <div className="space-y-4 relative before:content-[''] before:absolute before:left-2 before:top-2 before:bottom-2 before:w-px before:bg-outline-variant/30">
              <div className="relative pl-8">
                <div className="absolute left-0 top-1 w-4 h-4 bg-primary rounded-full border-2 border-white shadow-sm"></div>
                <p className="text-xs font-bold text-on-surface">Request Received</p>
                <p className="text-[10px] text-on-surface-variant">0ms — validated API key</p>
              </div>
              <div className="relative pl-8">
                <div className="absolute left-0 top-1 w-4 h-4 bg-tertiary rounded-full border-2 border-white shadow-sm"></div>
                <p className="text-xs font-bold text-on-surface">Routing Logic</p>
                <p className="text-[10px] text-on-surface-variant">12ms — Selected Provider: OpenAI</p>
              </div>
              <div className="relative pl-8">
                <div className="absolute left-0 top-1 w-4 h-4 bg-secondary rounded-full border-2 border-white shadow-sm"></div>
                <p className="text-xs font-bold text-on-surface">Upstream Response</p>
                <p className="text-[10px] text-on-surface-variant">1,232ms — 200 OK from api.openai.com</p>
              </div>
            </div>
          </div>
        </div>

        <div className="p-6 border-t border-outline-variant/15 bg-surface-container-low/30">
          <button className="w-full py-2.5 bg-white border border-outline-variant/20 rounded-xl text-sm font-bold text-on-surface shadow-sm hover:bg-slate-50 transition-colors flex items-center justify-center gap-2">
            <RotateCcw size={16} /> Re-run this Request
          </button>
        </div>
      </div>
    </div>
  );
}

function NavItem({ icon, label, active }: { icon: React.ReactNode, label: string, active?: boolean }) {
  return (
    <a href="#" className={`flex items-center gap-3 py-2.5 px-4 mx-2 rounded-lg font-medium text-sm transition-all duration-300 ${active ? 'bg-white text-primary shadow-sm' : 'text-slate-600 hover:bg-slate-200/50'}`}>
      {icon} {label}
    </a>
  );
}

function BottomNavItem({ icon, label }: { icon: React.ReactNode, label: string }) {
  return (
    <a href="#" className="flex items-center gap-3 py-2 px-2 text-slate-500 text-xs hover:text-primary transition-colors">
      {icon} {label}
    </a>
  );
}

function FilterSelect({ label, options }: { label: string, options: string[] }) {
  return (
    <div className="w-40">
      <label className="block text-[10px] font-bold text-on-surface-variant uppercase mb-1 ml-2">{label}</label>
      <select className="w-full bg-surface-container-lowest border-none rounded-lg py-2 px-3 text-sm focus:ring-2 focus:ring-primary/20 focus:outline-none appearance-none cursor-pointer">
        {options.map(opt => <option key={opt}>{opt}</option>)}
      </select>
    </div>
  );
}
