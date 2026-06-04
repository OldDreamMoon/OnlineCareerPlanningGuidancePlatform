import React from 'react';
import {
  Search, Bell, Settings, Shield, Flag, Sliders, Cpu,
  ArrowRightToLine, ArrowLeftFromLine, MessageSquare, Link as LinkIcon, Info, RotateCcw,
  ShieldAlert, Terminal, HelpCircle, AlertTriangle
} from 'lucide-react';

const Toggle = ({ active, color = '#63dbf2' }: { active: boolean, color?: string }) => (
  <div 
    className={`w-11 h-6 rounded-full relative cursor-pointer transition-colors ${active ? '' : 'bg-[#abadaf]/30'}`} 
    style={{ backgroundColor: active ? color : undefined }}
  >
    <div className={`absolute top-1 w-4 h-4 rounded-full bg-white transition-transform shadow-sm ${active ? 'left-1 translate-x-5' : 'left-1'}`} />
  </div>
);

const Badge = ({ text, color, bgColor }: { text: string, color: string, bgColor: string }) => (
  <span className="px-2.5 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider" style={{ color, backgroundColor: bgColor }}>
    {text}
  </span>
);

export default function App() {
  return (
    <div className="min-h-screen bg-[#f5f7f9] text-[#2c2f31] font-sans selection:bg-[#63dbf2]/30">
      {/* Top Navigation */}
      <header className="fixed top-0 w-full z-50 bg-[#ffffff]/70 backdrop-blur-[24px] flex justify-between items-center h-16 px-6 border-b border-[#abadaf]/10">
        <div className="flex items-center gap-8">
          <span className="text-xl font-bold font-display tracking-tight text-[#2c2f31]">Runtime Config Center</span>
          <nav className="hidden md:flex gap-6 h-full items-center">
            <a className="font-display text-sm font-medium text-[#2c2f31]/60 hover:text-[#2c2f31] transition-colors px-3 py-2" href="#">Dashboard</a>
            <a className="font-display text-sm font-bold text-[#63dbf2] border-b-2 border-[#63dbf2] px-3 py-2" href="#">Risk Controls</a>
            <a className="font-display text-sm font-medium text-[#2c2f31]/60 hover:text-[#2c2f31] transition-colors px-3 py-2" href="#">System Health</a>
          </nav>
        </div>
        <div className="flex items-center gap-4">
          <div className="bg-[#eef1f3] flex items-center px-3 py-1.5 rounded-full">
            <Search className="w-4 h-4 text-[#2c2f31]/40" />
            <input className="bg-transparent border-none focus:ring-0 text-sm w-48 ml-2 outline-none placeholder:text-[#2c2f31]/40" placeholder="Search parameters..." type="text" />
          </div>
          <div className="flex gap-2">
            <button className="p-2 text-[#2c2f31]/60 hover:bg-[#eef1f3] rounded-full transition-colors">
              <Bell className="w-5 h-5" />
            </button>
            <button className="p-2 text-[#2c2f31]/60 hover:bg-[#eef1f3] rounded-full transition-colors">
              <Settings className="w-5 h-5" />
            </button>
          </div>
          <div className="h-8 w-8 rounded-full bg-[#63dbf2]/20 flex items-center justify-center overflow-hidden">
            <img alt="User profile" className="h-full w-full object-cover" src="https://i.pravatar.cc/150?img=11" />
          </div>
        </div>
      </header>

      {/* Side Navigation */}
      <aside className="fixed left-0 top-16 h-[calc(100vh-64px)] w-64 bg-[#eef1f3] flex flex-col py-4 space-y-2 rounded-br-[1.5rem] z-40">
        <div className="px-6 mb-6 mt-2">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-[12px] bg-[#63dbf2] flex items-center justify-center text-white shadow-sm">
              <Shield className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-display text-sm font-bold text-[#2c2f31]">Admin Core</h3>
              <p className="text-xs text-[#2c2f31]/60">v2.4.0-stable</p>
            </div>
          </div>
        </div>
        <div className="flex-1 space-y-1">
          <a className="flex items-center gap-3 px-4 py-2.5 text-[#2c2f31]/70 hover:bg-[#ffffff]/50 mx-2 rounded-[12px] transition-all font-display text-sm font-medium" href="#">
            <Flag className="w-4 h-4" /> Business Flags
          </a>
          <a className="flex items-center gap-3 px-4 py-2.5 text-[#2c2f31]/70 hover:bg-[#ffffff]/50 mx-2 rounded-[12px] transition-all font-display text-sm font-medium" href="#">
            <Sliders className="w-4 h-4" /> Operations Parameters
          </a>
          <a className="flex items-center gap-3 px-4 py-2.5 text-[#2c2f31]/70 hover:bg-[#ffffff]/50 mx-2 rounded-[12px] transition-all font-display text-sm font-medium" href="#">
            <Cpu className="w-4 h-4" /> AI Channels
          </a>
          <a className="flex items-center gap-3 px-4 py-2.5 bg-[#ffffff] text-[#63dbf2] shadow-sm mx-2 rounded-[12px] transition-all font-display text-sm font-bold" href="#">
            <ShieldAlert className="w-4 h-4" /> Risk Degradation
          </a>
        </div>
        <div className="px-4 py-4 mt-auto">
          <button className="w-full py-3 bg-gradient-to-br from-[#63dbf2] to-[#22c1e3] text-white rounded-[1.5rem] font-bold text-sm shadow-[0_4px_14px_rgba(99,219,242,0.4)] hover:shadow-[0_6px_20px_rgba(99,219,242,0.5)] hover:-translate-y-0.5 transition-all">
            Deploy Changes
          </button>
          <div className="mt-4 flex flex-col gap-1">
            <a className="flex items-center gap-3 px-4 py-2 text-[#2c2f31]/50 hover:bg-[#ffffff]/50 rounded-[12px] text-xs font-display font-medium transition-colors" href="#">
              <Terminal className="w-4 h-4" /> Logs
            </a>
            <a className="flex items-center gap-3 px-4 py-2 text-[#2c2f31]/50 hover:bg-[#ffffff]/50 rounded-[12px] text-xs font-display font-medium transition-colors" href="#">
              <HelpCircle className="w-4 h-4" /> Support
            </a>
          </div>
        </div>
      </aside>

      {/* Main Content */}
      <main className="ml-64 pt-24 p-8 min-h-screen">
        <div className="max-w-[1200px] mx-auto">
          
          {/* Header */}
          <div className="mb-10">
            <h1 className="text-[2.5rem] leading-tight font-extrabold font-display tracking-tight text-[#2c2f31] mb-3">Risk Degradation Control</h1>
            <p className="text-[#2c2f31]/70 max-w-2xl text-base leading-relaxed">
              Manage real-time protection layers. Relaxing these controls will increase system throughput but significantly elevate content risks. Use with extreme caution.
            </p>
          </div>

          {/* Warning Banner */}
          <div className="mb-8 p-5 bg-[#f59e0b]/10 rounded-[1.5rem] flex gap-4 items-start relative overflow-hidden">
            <div className="absolute left-0 top-0 bottom-0 w-1.5 bg-[#f59e0b]"></div>
            <AlertTriangle className="w-6 h-6 text-[#f59e0b] shrink-0 mt-0.5" />
            <div>
              <h4 className="font-display font-bold text-[#f59e0b] text-lg mb-1">Critical Alert: Partial Degradation Active</h4>
              <p className="text-sm text-[#f59e0b]/80 font-medium">Community Strict Review is currently set to 'Relaxed'. Monitoring systems show a 12% uptick in flagged reports.</p>
            </div>
          </div>

          {/* Bento Grid */}
          <div className="grid grid-cols-1 md:grid-cols-12 gap-6">
            
            {/* Card 1: AI Input Protection */}
            <div className="md:col-span-4 bg-[#ffffff] p-6 rounded-[1.5rem] shadow-[0_10px_40px_rgba(44,47,49,0.06)] flex flex-col justify-between">
              <div>
                <div className="flex justify-between items-start mb-6">
                  <div className="w-12 h-12 rounded-[12px] bg-[#63dbf2]/10 flex items-center justify-center text-[#63dbf2]">
                    <ArrowRightToLine className="w-6 h-6" />
                  </div>
                  <Badge text="Normal State" color="#10b981" bgColor="rgba(16, 185, 129, 0.1)" />
                </div>
                <h3 className="font-display text-xl font-bold mb-2 text-[#2c2f31]">AI Input Protection</h3>
                <p className="text-sm text-[#2c2f31]/60 mb-8 leading-relaxed">Filters prompt injections and malicious instructions before they reach the model.</p>
              </div>
              <div className="flex items-center justify-between p-4 bg-[#eef1f3] rounded-[12px]">
                <span className="text-sm font-bold text-[#2c2f31]">Active Shielding</span>
                <Toggle active={true} color="#63dbf2" />
              </div>
            </div>

            {/* Card 2: AI Output Guard */}
            <div className="md:col-span-4 bg-[#ffffff] p-6 rounded-[1.5rem] shadow-[0_10px_40px_rgba(44,47,49,0.06)] flex flex-col justify-between">
              <div>
                <div className="flex justify-between items-start mb-6">
                  <div className="w-12 h-12 rounded-[12px] bg-[#f74b6d]/10 flex items-center justify-center text-[#f74b6d]">
                    <ArrowLeftFromLine className="w-6 h-6" />
                  </div>
                  <Badge text="Critical Control" color="#f74b6d" bgColor="rgba(247, 75, 109, 0.1)" />
                </div>
                <h3 className="font-display text-xl font-bold mb-2 text-[#2c2f31]">AI Output Guard</h3>
                <p className="text-sm text-[#2c2f31]/60 mb-8 leading-relaxed">Real-time content moderation for generated responses to prevent NSFW/Harmful leaks.</p>
              </div>
              <div className="flex items-center justify-between p-4 bg-[#f74b6d]/5 rounded-[12px] border border-[#f74b6d]/20">
                <span className="text-sm font-bold text-[#f74b6d]">Protection Relaxed</span>
                <Toggle active={true} color="#f74b6d" />
              </div>
            </div>

            {/* Card 3: Community Strict Review */}
            <div className="md:col-span-4 bg-[#ffffff] p-6 rounded-[1.5rem] shadow-[0_10px_40px_rgba(44,47,49,0.06)] flex flex-col justify-between relative overflow-hidden">
              <div className="absolute top-0 right-0 w-32 h-32 bg-[#f59e0b]/5 rounded-full blur-2xl -mr-10 -mt-10"></div>
              <div className="relative z-10">
                <div className="flex justify-between items-start mb-6">
                  <div className="w-12 h-12 rounded-[12px] bg-[#f59e0b]/10 flex items-center justify-center text-[#f59e0b]">
                    <MessageSquare className="w-6 h-6" />
                  </div>
                  <Badge text="Warning" color="#f59e0b" bgColor="rgba(245, 158, 11, 0.1)" />
                </div>
                <h3 className="font-display text-xl font-bold mb-2 text-[#2c2f31]">Community Strict Review</h3>
                <p className="text-sm text-[#2c2f31]/60 mb-8 leading-relaxed">Enhanced moderation for high-traffic public channels and user-to-user interactions.</p>
              </div>
              <div className="space-y-3 relative z-10">
                <div className="flex items-center justify-between p-4 bg-[#ffffff] rounded-[12px] shadow-[0_2px_10px_rgba(0,0,0,0.02)] border border-[#abadaf]/15">
                  <span className="text-sm font-bold text-[#2c2f31]">Strict Mode</span>
                  <Toggle active={false} />
                </div>
                <div className="flex items-start gap-2 text-xs text-[#f59e0b] font-medium px-1">
                  <Info className="w-4 h-4 shrink-0 mt-0.5" />
                  <span>Degradation increases throughput by 40%</span>
                </div>
              </div>
            </div>

            {/* Card 4: System Impact (Wide) */}
            <div className="md:col-span-8 bg-[#0b0f10] p-8 rounded-[1.5rem] text-white relative overflow-hidden shadow-[0_10px_40px_rgba(44,47,49,0.1)]">
              <div className="absolute -right-20 -bottom-20 w-64 h-64 bg-[#63dbf2]/20 rounded-full blur-[60px]"></div>
              <div className="relative z-10">
                <h3 className="font-display text-xl font-bold mb-8 flex items-center gap-3">
                  <LinkIcon className="w-5 h-5 text-[#63dbf2]" />
                  System Impact & Linkage Analysis
                </h3>
                <div className="grid grid-cols-2 gap-10">
                  <div className="space-y-3">
                    <label className="text-[10px] font-bold uppercase tracking-widest text-white/50">Report Threshold</label>
                    <div className="text-4xl font-display font-extrabold text-[#f59e0b]">-25%</div>
                    <p className="text-xs text-white/60 leading-relaxed">Degradation causes automatic reduction in sensitivity for community reports to prevent moderator fatigue.</p>
                  </div>
                  <div className="space-y-3">
                    <label className="text-[10px] font-bold uppercase tracking-widest text-white/50">False Positives</label>
                    <div className="text-4xl font-display font-extrabold text-[#f74b6d]">+18%</div>
                    <p className="text-xs text-white/60 leading-relaxed">Relaxed protection layers may lead to increased erroneous blocks in neighboring AI channels.</p>
                  </div>
                </div>
                <div className="mt-10 pt-6 border-t border-white/10 flex items-center justify-between">
                  <div className="flex -space-x-3">
                    <img className="w-10 h-10 rounded-full border-2 border-[#0b0f10]" src="https://i.pravatar.cc/150?img=32" alt="User" />
                    <img className="w-10 h-10 rounded-full border-2 border-[#0b0f10]" src="https://i.pravatar.cc/150?img=12" alt="User" />
                    <img className="w-10 h-10 rounded-full border-2 border-[#0b0f10]" src="https://i.pravatar.cc/150?img=5" alt="User" />
                    <div className="w-10 h-10 rounded-full border-2 border-[#0b0f10] bg-[#63dbf2] flex items-center justify-center text-[#0b0f10] text-[10px] font-bold z-10">+12</div>
                  </div>
                  <span className="text-xs text-white/40 italic font-medium">Data synchronized with Ops Parameters v2.1</span>
                </div>
              </div>
            </div>

            {/* Card 5: Emergency Recovery */}
            <div className="md:col-span-4 bg-[#eef1f3] p-6 rounded-[1.5rem] flex flex-col gap-4 shadow-inner">
              <h3 className="font-display text-lg font-bold text-[#2c2f31] mb-2">Emergency Recovery</h3>
              <button className="w-full py-4 bg-[#ffffff] text-[#2c2f31] rounded-[1.5rem] font-bold border border-[#abadaf]/15 hover:bg-[#f5f7f9] transition-colors flex items-center justify-center gap-3 shadow-sm">
                <RotateCcw className="w-5 h-5 text-[#63dbf2]" />
                Reset to Defaults
              </button>
              <button className="w-full py-4 bg-[#f74b6d] text-white rounded-[1.5rem] font-bold shadow-[0_4px_14px_rgba(247,75,109,0.3)] hover:bg-[#e03e5e] transition-colors flex items-center justify-center gap-3">
                <ShieldAlert className="w-5 h-5" />
                Full Lockdown Mode
              </button>
              <div className="mt-auto p-4 bg-[#ffffff]/60 rounded-[12px] border border-dashed border-[#abadaf]/30">
                <p className="text-[11px] text-[#2c2f31]/60 leading-relaxed">
                  <strong className="text-[#2c2f31]">Note:</strong> All changes made here are logged with user ID and timestamp for compliance auditing.
                </p>
              </div>
            </div>

          </div>
        </div>
      </main>
    </div>
  );
}
