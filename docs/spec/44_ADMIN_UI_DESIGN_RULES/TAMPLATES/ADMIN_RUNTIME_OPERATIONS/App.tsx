/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export default function App() {
  return (
    <div className="bg-surface font-body text-on-surface antialiased min-h-screen">
      {/* TopNavBar Shell */}
      <nav className="fixed top-0 w-full z-50 bg-white/70 backdrop-blur-xl shadow-sm flex justify-between items-center h-16 px-6">
        <div className="flex items-center gap-8">
          <span className="text-xl font-bold tracking-tight text-slate-900">Runtime Config Center</span>
          <div className="hidden md:flex gap-6 items-center">
            <a className="font-headline text-sm font-medium text-slate-500 hover:bg-slate-50 transition-colors px-3 py-2 rounded-lg" href="#">Dashboard</a>
            <a className="font-headline text-sm font-medium text-indigo-600 border-b-2 border-indigo-600 px-3 py-2" href="#">Operations</a>
            <a className="font-headline text-sm font-medium text-slate-500 hover:bg-slate-50 transition-colors px-3 py-2 rounded-lg" href="#">AI Engine</a>
          </div>
        </div>
        <div className="flex items-center gap-4">
          <div className="bg-slate-100/50 rounded-full px-4 py-2 flex items-center gap-2">
            <span className="material-symbols-outlined text-slate-400 text-sm">search</span>
            <input className="bg-transparent border-none focus:ring-0 text-sm w-48 outline-none" placeholder="Global Search..." type="text" />
          </div>
          <div className="flex gap-2">
            <button className="p-2 text-slate-500 hover:bg-slate-50 rounded-full transition-colors active:scale-95 duration-200 cursor-pointer">
              <span className="material-symbols-outlined">notifications</span>
            </button>
            <button className="p-2 text-slate-500 hover:bg-slate-50 rounded-full transition-colors active:scale-95 duration-200 cursor-pointer">
              <span className="material-symbols-outlined">settings</span>
            </button>
          </div>
          <img alt="User profile" className="w-10 h-10 rounded-full object-cover ring-2 ring-indigo-50" src="https://lh3.googleusercontent.com/aida-public/AB6AXuAaR3Vonh4wHhLJf9iZvuc8qBVQ-0JXQAkRbyJAvhraj1klnQWiDVLBuzQ2-iEGnufn5hyTUyl-KLn4n63YGVELUh5Ihck4KSgkMW_wmGjiwIdgS4ZD7w7SfwdAY7-5m6zoOMe3dnYrgVl7QWJ0_-G3Xv2ESuVZh0rPwFsEOuQCGmbnXe1I3Bj77auVvILRaumSbazYng00ApD4qCYH9LeAywzkedx7Xn0PHajlFcntGsm8EE-Mwl8M9a468MJvMQOo6gcPqAeI_kU" />
        </div>
      </nav>

      {/* SideNavBar Shell */}
      <aside className="fixed left-0 top-16 h-[calc(100vh-64px)] w-64 bg-slate-50 flex flex-col py-4 space-y-2 rounded-r-lg border-r border-surface-container">
        <div className="px-6 mb-6">
          <h3 className="font-headline font-bold text-on-surface">Admin Core</h3>
          <p className="text-xs text-slate-500">v2.4.0-stable</p>
        </div>
        <nav className="flex-1 px-2 space-y-1">
          <a className="flex items-center gap-3 py-2.5 px-4 text-slate-600 hover:bg-slate-100 rounded-lg transition-all duration-300" href="#">
            <span className="material-symbols-outlined">flag</span>
            <span className="font-headline text-sm">Business Flags</span>
          </a>
          <a className="flex items-center gap-3 py-2.5 px-4 bg-indigo-50 text-indigo-700 font-semibold rounded-lg transition-all duration-300" href="#">
            <span className="material-symbols-outlined">settings_input_component</span>
            <span className="font-headline text-sm">Operations Parameters</span>
          </a>
          <a className="flex items-center gap-3 py-2.5 px-4 text-slate-600 hover:bg-slate-100 rounded-lg transition-all duration-300" href="#">
            <span className="material-symbols-outlined">psychology</span>
            <span className="font-headline text-sm">AI Channels</span>
          </a>
          <a className="flex items-center gap-3 py-2.5 px-4 text-slate-600 hover:bg-slate-100 rounded-lg transition-all duration-300" href="#">
            <span className="material-symbols-outlined">gpp_maybe</span>
            <span className="font-headline text-sm">Risk Degradation</span>
          </a>
        </nav>
        <div className="px-4 mt-auto">
          <button className="w-full py-3 bg-gradient-to-br from-primary to-primary-dim text-white rounded-xl font-bold shadow-lg shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all cursor-pointer">
            Deploy Changes
          </button>
        </div>
        <div className="pt-4 px-2 mt-4 border-t border-surface-container">
          <a className="flex items-center gap-3 py-2 px-4 text-slate-500 hover:bg-slate-100 rounded-lg text-sm" href="#">
            <span className="material-symbols-outlined">terminal</span> Logs
          </a>
          <a className="flex items-center gap-3 py-2 px-4 text-slate-500 hover:bg-slate-100 rounded-lg text-sm" href="#">
            <span className="material-symbols-outlined">contact_support</span> Support
          </a>
        </div>
      </aside>

      {/* Main Content Canvas */}
      <main className="ml-64 pt-24 px-8 pb-12 min-h-screen">
        <header className="mb-10 flex justify-between items-end">
          <div>
            <h1 className="font-headline text-4xl font-extrabold tracking-tight text-on-surface mb-2">Operations Parameters</h1>
            <p className="text-on-surface-variant max-w-2xl">Configure real-time system behaviors, AI observation depth, and automated platform threshold triggers.</p>
          </div>
          <div className="flex gap-3">
            <button className="px-4 py-2 bg-surface-container-low text-on-surface font-semibold rounded-lg hover:bg-surface-container transition-colors cursor-pointer">
              Reset to Default
            </button>
            <button className="px-4 py-2 bg-primary text-white font-semibold rounded-lg shadow-md hover:bg-primary-dim transition-colors cursor-pointer">
              Push Config Update
            </button>
          </div>
        </header>

        <div className="grid grid-cols-12 gap-8">
          {/* Main Section: AI Observation Parameters */}
          <section className="col-span-8 space-y-8">
            {/* Bento Block 1: Logging Controls */}
            <div className="bg-surface-container-lowest rounded-lg p-6 shadow-sm ring-1 ring-black/[0.03]">
              <div className="flex items-center justify-between mb-6">
                <div className="flex items-center gap-3">
                  <span className="p-2 bg-primary-container/20 text-primary rounded-lg flex items-center justify-center">
                    <span className="material-symbols-outlined">troubleshoot</span>
                  </span>
                  <h2 className="font-headline text-lg font-bold">AI Observation Parameters</h2>
                </div>
                <span className="text-xs font-mono text-slate-400 bg-slate-50 px-2 py-1 rounded">NAMESPACE: ai.obs.runtime</span>
              </div>

              <div className="grid grid-cols-2 gap-6">
                {/* Debug Logs Card */}
                <div className="p-5 bg-surface-container-low rounded-lg group hover:bg-surface-container-lowest hover:shadow-md transition-all duration-300">
                  <div className="flex justify-between items-start mb-4">
                    <div>
                      <h3 className="font-bold text-sm text-on-surface">Debug Logs Retention</h3>
                      <p className="text-xs text-on-surface-variant">Active storage for verbose AI trace</p>
                    </div>
                    <div className="w-10 h-10 bg-white rounded-lg flex items-center justify-center shadow-sm">
                      <span className="material-symbols-outlined text-primary text-xl">history</span>
                    </div>
                  </div>
                  <div className="space-y-4">
                    <div className="flex justify-between items-end">
                      <div className="text-2xl font-bold text-primary">48<span className="text-sm font-medium ml-1">Hours</span></div>
                      <div className="text-xs text-slate-400 line-through">24 Hours (Default)</div>
                    </div>
                    <div className="h-1.5 bg-slate-200 rounded-full overflow-hidden">
                      <div className="h-full bg-primary w-[75%]" style={{ boxShadow: '0 0 8px rgba(70, 71, 211, 0.4)' }}></div>
                    </div>
                  </div>
                </div>

                {/* Request Logs Card */}
                <div className="p-5 bg-surface-container-low rounded-lg group hover:bg-surface-container-lowest hover:shadow-md transition-all duration-300">
                  <div className="flex justify-between items-start mb-4">
                    <div>
                      <h3 className="font-bold text-sm text-on-surface">Request Logs Sampling</h3>
                      <p className="text-xs text-on-surface-variant">Probability for deep inspection</p>
                    </div>
                    <div className="w-10 h-10 bg-white rounded-lg flex items-center justify-center shadow-sm">
                      <span className="material-symbols-outlined text-secondary text-xl">analytics</span>
                    </div>
                  </div>
                  <div className="space-y-4">
                    <div className="flex justify-between items-end">
                      <div className="text-2xl font-bold text-secondary">5<span className="text-sm font-medium ml-1">%</span></div>
                      <div className="text-xs text-slate-400">1% (Default)</div>
                    </div>
                    <div className="h-1.5 bg-slate-200 rounded-full overflow-hidden">
                      <div className="h-full bg-secondary w-[20%]" style={{ boxShadow: '0 0 8px rgba(0, 105, 71, 0.4)' }}></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Bento Block 2: Live Observation Stream (Redesigned) */}
            <div className="bg-surface-container-lowest rounded-lg p-6 shadow-sm ring-1 ring-black/[0.03]">
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-3">
                  <span className="p-2 bg-surface-container-low text-on-surface-variant rounded-lg flex items-center justify-center">
                    <span className="material-symbols-outlined">terminal</span>
                  </span>
                  <h2 className="font-headline text-lg font-bold">Live Observation Stream</h2>
                </div>
                <div className="flex items-center gap-2 px-3 py-1 bg-secondary-container/20 rounded-full">
                  <div className="w-2 h-2 rounded-full bg-secondary animate-pulse"></div>
                  <span className="text-xs font-bold text-secondary-dim tracking-wide">LIVE</span>
                </div>
              </div>

              {/* Log Container - Recessed luminous look */}
              <div className="bg-surface-container-low/50 rounded-xl p-5 font-mono text-sm space-y-3 h-[280px] overflow-y-auto shadow-inner ring-1 ring-black/[0.02]">
                <div className="flex gap-4">
                  <span className="text-slate-400 shrink-0">[14:22:01]</span>
                  <span className="text-primary font-bold shrink-0 w-12">INFO</span>
                  <span className="text-on-surface">AI Observation probe initialized successfully.</span>
                </div>
                <div className="flex gap-4">
                  <span className="text-slate-400 shrink-0">[14:22:05]</span>
                  <span className="text-slate-500 font-bold shrink-0 w-12">TRACE</span>
                  <span className="text-on-surface-variant">Request #ID-8829-X sampled at 5% rate.</span>
                </div>
                <div className="flex gap-4">
                  <span className="text-slate-400 shrink-0">[14:22:15]</span>
                  <span className="text-tertiary-fixed-dim font-bold shrink-0 w-12">WARN</span>
                  <span className="text-on-surface">Response latency spike detected in 'Query-Cluster-Alpha'.</span>
                </div>
                <div className="flex gap-4">
                  <span className="text-slate-400 shrink-0">[14:22:30]</span>
                  <span className="text-slate-500 font-bold shrink-0 w-12">TRACE</span>
                  <span className="text-on-surface-variant">Garbage collection started on ObsPool-01.</span>
                </div>
                <div className="flex gap-4">
                  <span className="text-slate-400 shrink-0">[14:23:01]</span>
                  <span className="text-primary font-bold shrink-0 w-12">INFO</span>
                  <span className="text-on-surface">Config push detected. Re-aligning retention to 48h.</span>
                </div>
                
                {/* Listening indicator */}
                <div className="flex gap-2 items-center opacity-60 pt-4 pl-1">
                  <div className="flex gap-1">
                    <span className="w-1.5 h-1.5 rounded-full bg-slate-400 animate-bounce"></span>
                    <span className="w-1.5 h-1.5 rounded-full bg-slate-400 animate-bounce" style={{animationDelay: '150ms'}}></span>
                    <span className="w-1.5 h-1.5 rounded-full bg-slate-400 animate-bounce" style={{animationDelay: '300ms'}}></span>
                  </div>
                  <span className="text-slate-500 italic ml-2 text-xs">listening for incoming packets...</span>
                </div>
              </div>
            </div>
          </section>

          {/* Sidebar: Platform Thresholds */}
          <aside className="col-span-4 space-y-6">
            <div className="bg-surface-container-lowest rounded-lg p-6 shadow-sm ring-1 ring-black/[0.03]">
              <h2 className="font-headline text-lg font-bold mb-6 flex items-center gap-2">
                <span className="material-symbols-outlined text-tertiary">speed</span>
                Platform Thresholds
              </h2>
              
              <div className="space-y-6">
                {/* Threshold Item 1 */}
                <div>
                  <div className="flex justify-between items-center mb-2">
                    <label className="text-sm font-semibold text-on-surface">Auto-hide report threshold</label>
                    <span className="text-xs bg-tertiary-container/20 text-on-tertiary-container px-2 py-0.5 rounded-full font-bold">ACTIVE</span>
                  </div>
                  <div className="p-4 bg-surface-container-low rounded-lg">
                    <div className="flex justify-between items-baseline mb-3">
                      <div className="text-3xl font-bold text-on-surface">150</div>
                      <div className="text-xs font-medium text-slate-500">Reports/hr</div>
                    </div>
                    <input className="w-full h-1.5 bg-slate-300 rounded-lg appearance-none cursor-pointer accent-tertiary" type="range" defaultValue="65" />
                    <div className="flex justify-between mt-3 text-[10px] font-bold text-slate-400">
                      <span>DEFAULT: 50</span>
                      <span className="text-tertiary">MAX: 500</span>
                    </div>
                  </div>
                  <p className="mt-2 text-xs text-on-surface-variant leading-relaxed">System will automatically suppress alerts once this volume is breached to prevent notification fatigue.</p>
                </div>

                {/* Threshold Item 2 */}
                <div className="pt-6 border-t border-surface-container-low">
                  <div className="flex justify-between items-center mb-2">
                    <label className="text-sm font-semibold text-on-surface">Risk Score Cut-off</label>
                  </div>
                  <div className="p-4 bg-surface-container-low rounded-lg">
                    <div className="flex justify-between items-baseline mb-3">
                      <div className="text-3xl font-bold text-on-surface">0.82</div>
                      <div className="text-xs font-medium text-slate-500">Probability</div>
                    </div>
                    <div className="h-2 bg-slate-300 rounded-full overflow-hidden relative">
                      <div className="h-full bg-error-container w-[82%]"></div>
                      <div className="absolute top-0 left-[75%] h-full w-0.5 bg-on-surface"></div>
                    </div>
                    <div className="flex justify-between mt-3 text-[10px] font-bold text-slate-400">
                      <span>DEFAULT: 0.75</span>
                      <span>CRITICAL: 0.95</span>
                    </div>
                  </div>
                </div>

                {/* Info Card */}
                <div className="p-4 bg-primary-container/10 border border-primary-container/20 rounded-lg">
                  <div className="flex gap-3">
                    <span className="material-symbols-outlined text-primary text-sm">info</span>
                    <p className="text-xs text-on-primary-container/80 leading-relaxed">Changes to platform thresholds undergo a 5-minute propagation delay across edge nodes.</p>
                  </div>
                </div>
              </div>
            </div>


          </aside>
        </div>
      </main>
    </div>
  );
}
