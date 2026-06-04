/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export default function App() {
  return (
    <>
      {/* TopNavBar */}
      <nav className="fixed top-0 w-full z-50 glass-nav flex justify-between items-center px-6 h-16 shadow-sm">
        <div className="flex items-center gap-4">
          <span className="text-xl font-bold tracking-tight text-primary">Luminous Admin</span>
          <div className="hidden md:flex items-center ml-8 gap-6">
            <a className="text-primary font-semibold border-b-2 border-primary py-1 text-sm" href="#">Dashboard</a>
            <a className="text-on-surface-variant hover:text-primary transition-colors text-sm" href="#">Operations</a>
            <a className="text-on-surface-variant hover:text-primary transition-colors text-sm" href="#">Systems</a>
          </div>
        </div>
        <div className="flex items-center gap-4">
          <div className="bg-surface-container-low px-3 py-1.5 rounded-full flex items-center gap-2">
            <span className="material-symbols-outlined text-outline text-sm">search</span>
            <input className="bg-transparent border-none text-xs focus:ring-0 w-32 md:w-64 outline-none" placeholder="Global Search" type="text" />
          </div>
          <div className="flex gap-2">
            <button className="p-2 hover:bg-surface-container rounded-full transition-colors"><span className="material-symbols-outlined text-outline">notifications</span></button>
            <button className="p-2 hover:bg-surface-container rounded-full transition-colors"><span className="material-symbols-outlined text-outline">settings</span></button>
          </div>
          <img alt="Administrator Avatar" className="w-8 h-8 rounded-full border-2 border-primary-container" data-alt="professional portrait of a confident male executive in a dark suit with a modern office background" src="https://lh3.googleusercontent.com/aida-public/AB6AXuBxFgd1AyOY5ESndkUtxOVTxTfdmni2vyBmM64ZENNvwOerWwoy2tsbalMb3eznU8SVJ5_ZQCtZ25SKv5MuoYfCkG7HKqAP9mZlRtSd1FA92epYn7vdak78uB-5D1YsktXI0KxCKk-ThclzcGsfqptQT9aDfCDwnV56TFiAATMxsmR-UrOb-S4hu8zC56dPe7qdXXp8C000S841mZw_-Fxzr0obp6M6ywgScsfmMX2IlAVeJt0N35OnLYDp_dWU389NizNE8yhu4LQ" />
        </div>
      </nav>
      <div className="flex pt-16">
        {/* SideNavBar */}
        <aside className="hidden md:flex flex-col h-[calc(100vh-64px)] w-64 bg-slate-50 py-4 px-3 sticky top-16">
          <div className="px-3 mb-8">
            <h2 className="text-lg font-bold text-on-background">Core Operations</h2>
            <p className="text-[10px] uppercase tracking-widest text-outline">Management Suite</p>
          </div>
          <div className="flex-1 space-y-1">
            <div className="flex items-center gap-3 px-3 py-2 text-on-surface-variant hover:translate-x-1 transition-transform cursor-pointer rounded-lg hover:bg-surface-container">
              <span className="material-symbols-outlined">verified</span>
              <span className="text-sm font-medium">Certifications</span>
            </div>
            <div className="flex items-center gap-3 px-3 py-2 text-on-surface-variant hover:translate-x-1 transition-transform cursor-pointer rounded-lg hover:bg-surface-container">
              <span className="material-symbols-outlined">desktop_windows</span>
              <span className="text-sm font-medium">Workspaces</span>
            </div>
            <div className="flex items-center gap-3 px-3 py-2 text-on-surface-variant hover:translate-x-1 transition-transform cursor-pointer rounded-lg hover:bg-surface-container">
              <span className="material-symbols-outlined">groups</span>
              <span className="text-sm font-medium">Mentors</span>
            </div>
            <div className="flex items-center gap-3 px-3 py-2 bg-primary-container/20 text-primary rounded-lg">
              <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>campaign</span>
              <span className="text-sm font-semibold">Notifications</span>
            </div>
          </div>
          <div className="mt-auto space-y-1 border-t border-outline-variant/10 pt-4">
            <div className="flex items-center gap-3 px-3 py-2 text-on-surface-variant hover:translate-x-1 transition-transform cursor-pointer rounded-lg">
              <span className="material-symbols-outlined">contact_support</span>
              <span className="text-sm font-medium">Support</span>
            </div>
            <div className="flex items-center gap-3 px-3 py-2 text-error-dim hover:translate-x-1 transition-transform cursor-pointer rounded-lg">
              <span className="material-symbols-outlined">logout</span>
              <span className="text-sm font-medium">Logout</span>
            </div>
          </div>
        </aside>
        {/* Main Content Canvas */}
        <main className="flex-1 min-h-screen p-8 bg-surface">
          {/* Dark Hero Section */}
          <section className="hero-gradient rounded-xl p-10 mb-10 text-white relative overflow-hidden shadow-2xl">
            <div className="relative z-10 max-w-2xl">
              <span className="text-secondary-fixed text-xs font-bold uppercase tracking-[0.2em] mb-3 block">System Controller</span>
              <h1 className="text-4xl md:text-5xl font-extrabold tracking-tight mb-4 leading-tight">Notification Operations Desk</h1>
              <p className="text-surface-variant text-lg font-light leading-relaxed">
                Orchestrate global communication protocols. Deploy system-wide announcements, manage real-time dispatch queues, and monitor channel health with precision.
              </p>
            </div>
            <div className="absolute right-0 top-0 w-1/3 h-full opacity-10">
              <span className="material-symbols-outlined text-[200px]" style={{ fontVariationSettings: "'wght' 100" }}>hub</span>
            </div>
          </section>
          {/* Summary Cards Bento Grid */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-10">
            <div className="bg-surface-container-lowest p-6 rounded-xl shadow-sm flex flex-col justify-between group hover:translate-y-[-4px] transition-all duration-300">
              <div className="flex justify-between items-start">
                <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center text-primary group-hover:bg-primary group-hover:text-white transition-colors">
                  <span className="material-symbols-outlined">send</span>
                </div>
                <span className="text-secondary font-bold text-xs">+12.5%</span>
              </div>
              <div className="mt-4">
                <p className="text-on-surface-variant text-xs font-medium uppercase tracking-wider">Total Dispatched</p>
                <h3 className="text-2xl font-extrabold text-on-surface">1,284,059</h3>
              </div>
            </div>
            <div className="bg-surface-container-lowest p-6 rounded-xl shadow-sm flex flex-col justify-between group hover:translate-y-[-4px] transition-all duration-300">
              <div className="flex justify-between items-start">
                <div className="w-10 h-10 rounded-lg bg-tertiary-container/10 flex items-center justify-center text-tertiary group-hover:bg-tertiary group-hover:text-white transition-colors">
                  <span className="material-symbols-outlined">schedule</span>
                </div>
                <span className="text-outline text-xs">Queue</span>
              </div>
              <div className="mt-4">
                <p className="text-on-surface-variant text-xs font-medium uppercase tracking-wider">Pending Tasks</p>
                <h3 className="text-2xl font-extrabold text-on-surface">432</h3>
              </div>
            </div>
            <div className="bg-surface-container-lowest p-6 rounded-xl shadow-sm flex flex-col justify-between group hover:translate-y-[-4px] transition-all duration-300">
              <div className="flex justify-between items-start">
                <div className="w-10 h-10 rounded-lg bg-secondary-container/20 flex items-center justify-center text-secondary group-hover:bg-secondary group-hover:text-white transition-colors">
                  <span className="material-symbols-outlined">check_circle</span>
                </div>
                <span className="text-secondary font-bold text-xs">99.8%</span>
              </div>
              <div className="mt-4">
                <p className="text-on-surface-variant text-xs font-medium uppercase tracking-wider">Delivery Rate</p>
                <h3 className="text-2xl font-extrabold text-on-surface">Stable</h3>
              </div>
            </div>
            <div className="bg-surface-container-lowest p-6 rounded-xl shadow-sm flex flex-col justify-between group hover:translate-y-[-4px] transition-all duration-300">
              <div className="flex justify-between items-start">
                <div className="w-10 h-10 rounded-lg bg-error-container/10 flex items-center justify-center text-error group-hover:bg-error group-hover:text-white transition-colors">
                  <span className="material-symbols-outlined">error</span>
                </div>
                <span className="text-error font-bold text-xs">-2.1%</span>
              </div>
              <div className="mt-4">
                <p className="text-on-surface-variant text-xs font-medium uppercase tracking-wider">Failed Delivery</p>
                <h3 className="text-2xl font-extrabold text-on-surface">1,042</h3>
              </div>
            </div>
          </div>
          {/* Operations Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 mb-10">
            {/* Announcement Form */}
            <div className="lg:col-span-2 bg-surface-container-lowest rounded-xl p-8 shadow-sm">
              <div className="flex items-center gap-3 mb-6">
                <span className="material-symbols-outlined text-primary text-3xl">campaign</span>
                <h2 className="text-xl font-bold">New System Announcement</h2>
              </div>
              <form className="space-y-6" onSubmit={(e) => e.preventDefault()}>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  <div className="space-y-2">
                    <label className="text-xs font-bold text-on-surface-variant uppercase tracking-wider">Announcement Title</label>
                    <input className="w-full bg-surface-container-low border-none rounded-lg focus:ring-2 focus:ring-primary py-3 outline-none px-4" placeholder="e.g. Scheduled Maintenance" type="text" />
                  </div>
                  <div className="space-y-2">
                    <label className="text-xs font-bold text-on-surface-variant uppercase tracking-wider">Target Role</label>
                    <select className="w-full bg-surface-container-low border-none rounded-lg focus:ring-2 focus:ring-primary py-3 outline-none px-4">
                      <option>All Users</option>
                      <option>Administrators</option>
                      <option>Premium Members</option>
                      <option>New Onboarding</option>
                    </select>
                  </div>
                </div>
                <div className="space-y-2">
                  <label className="text-xs font-bold text-on-surface-variant uppercase tracking-wider">Message Content</label>
                  <textarea className="w-full bg-surface-container-low border-none rounded-lg focus:ring-2 focus:ring-primary py-3 outline-none px-4" placeholder="Draft your message here..." rows={4}></textarea>
                </div>
                <div className="flex items-center justify-between pt-4">
                  <div className="flex items-center gap-6">
                    <span className="text-xs font-bold text-on-surface-variant uppercase tracking-wider">Priority</span>
                    <div className="flex gap-3">
                      <label className="flex items-center gap-2 cursor-pointer">
                        <input className="text-primary focus:ring-primary" name="priority" type="radio" defaultChecked />
                        <span className="text-sm font-medium">Standard</span>
                      </label>
                      <label className="flex items-center gap-2 cursor-pointer">
                        <input className="text-tertiary focus:ring-tertiary" name="priority" type="radio" />
                        <span className="text-sm font-medium">High</span>
                      </label>
                      <label className="flex items-center gap-2 cursor-pointer">
                        <input className="text-error focus:ring-error" name="priority" type="radio" />
                        <span className="text-sm font-medium">Urgent</span>
                      </label>
                    </div>
                  </div>
                  <button className="bg-gradient-to-r from-primary to-primary-dim text-white px-8 py-3 rounded-full font-bold shadow-lg shadow-primary/20 hover:scale-[1.02] transition-transform flex items-center gap-2">
                    <span className="material-symbols-outlined">send</span>
                    Broadcast Now
                  </button>
                </div>
              </form>
            </div>
            {/* Channel Readiness */}
            <div className="space-y-8">
              <div className="bg-surface-container-lowest rounded-xl p-8 shadow-sm">
                <h3 className="text-lg font-bold mb-6 flex items-center gap-2">
                  <span className="material-symbols-outlined text-secondary">sensors</span>
                  Channel Health
                </h3>
                <div className="space-y-6">
                  <div className="flex items-center justify-between p-4 rounded-xl bg-secondary-container/10 border border-secondary/20">
                    <div className="flex items-center gap-3">
                      <span className="material-symbols-outlined text-secondary" style={{ fontVariationSettings: "'FILL' 1" }}>wifi_tethering</span>
                      <div>
                        <p className="font-bold text-sm">WebSocket</p>
                        <p className="text-[10px] text-secondary font-medium">Live Connection: ACTIVE</p>
                      </div>
                    </div>
                    <div className="w-2 h-2 rounded-full bg-secondary animate-pulse"></div>
                  </div>
                  <div className="flex items-center justify-between p-4 rounded-xl bg-secondary-container/10 border border-secondary/20">
                    <div className="flex items-center gap-3">
                      <span className="material-symbols-outlined text-secondary" style={{ fontVariationSettings: "'FILL' 1" }}>mail</span>
                      <div>
                        <p className="font-bold text-sm">Email Gateway</p>
                        <p className="text-[10px] text-secondary font-medium">SMTP: OPERATIONAL</p>
                      </div>
                    </div>
                    <div className="w-2 h-2 rounded-full bg-secondary animate-pulse"></div>
                  </div>
                  <div className="flex items-center justify-between p-4 rounded-xl bg-tertiary-container/10 border border-tertiary/20">
                    <div className="flex items-center gap-3">
                      <span className="material-symbols-outlined text-tertiary" style={{ fontVariationSettings: "'FILL' 1" }}>sms</span>
                      <div>
                        <p className="font-bold text-sm">SMS Relay</p>
                        <p className="text-[10px] text-tertiary font-medium">Latency: 240ms (High)</p>
                      </div>
                    </div>
                    <div className="w-2 h-2 rounded-full bg-tertiary"></div>
                  </div>
                </div>
              </div>
              <div className="bg-indigo-900 text-white rounded-xl p-6 shadow-xl relative overflow-hidden">
                <div className="relative z-10">
                  <h4 className="font-bold text-sm mb-2">Automated Cleanup</h4>
                  <p className="text-xs text-indigo-200 mb-4">Database clearing in 2h 43m</p>
                  <div className="w-full bg-white/10 h-1.5 rounded-full overflow-hidden">
                    <div className="bg-secondary-container h-full w-[65%]"></div>
                  </div>
                </div>
                <div className="absolute -right-4 -bottom-4 opacity-10">
                  <span className="material-symbols-outlined text-[80px]">auto_delete</span>
                </div>
              </div>
            </div>
          </div>
          {/* Tables Section */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
            {/* Announcement History */}
            <div className="bg-surface-container-lowest rounded-xl overflow-hidden shadow-sm">
              <div className="px-6 py-4 border-b border-surface-container flex justify-between items-center bg-surface-container-lowest">
                <h3 className="font-bold">Announcement History</h3>
                <button className="text-primary text-xs font-bold hover:underline">Export CSV</button>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-left">
                  <thead className="bg-surface-container-low text-[10px] uppercase tracking-wider text-on-surface-variant font-bold">
                    <tr>
                      <th className="px-6 py-3">Subject</th>
                      <th className="px-6 py-3">Audience</th>
                      <th className="px-6 py-3">Date</th>
                      <th className="px-6 py-3">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-surface-container text-sm">
                    <tr className="hover:bg-slate-50 transition-colors">
                      <td className="px-6 py-4 font-semibold">V2.4 Update Live</td>
                      <td className="px-6 py-4 text-on-surface-variant">Global</td>
                      <td className="px-6 py-4 text-on-surface-variant">Oct 24, 14:02</td>
                      <td className="px-6 py-4">
                        <span className="px-2 py-1 rounded bg-secondary-container/20 text-on-secondary-container text-[10px] font-bold">SENT</span>
                      </td>
                    </tr>
                    <tr className="hover:bg-slate-50 transition-colors">
                      <td className="px-6 py-4 font-semibold">Maintenance Window</td>
                      <td className="px-6 py-4 text-on-surface-variant">Admins</td>
                      <td className="px-6 py-4 text-on-surface-variant">Oct 23, 09:15</td>
                      <td className="px-6 py-4">
                        <span className="px-2 py-1 rounded bg-secondary-container/20 text-on-secondary-container text-[10px] font-bold">SENT</span>
                      </td>
                    </tr>
                    <tr className="hover:bg-slate-50 transition-colors">
                      <td className="px-6 py-4 font-semibold">New Policy Draft</td>
                      <td className="px-6 py-4 text-on-surface-variant">Internal</td>
                      <td className="px-6 py-4 text-on-surface-variant">Oct 22, 18:45</td>
                      <td className="px-6 py-4">
                        <span className="px-2 py-1 rounded bg-surface-container-high text-on-surface-variant text-[10px] font-bold">DRAFT</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
            {/* Dispatch Queue */}
            <div className="bg-surface-container-lowest rounded-xl overflow-hidden shadow-sm">
              <div className="px-6 py-4 border-b border-surface-container flex justify-between items-center bg-surface-container-lowest">
                <h3 className="font-bold">Dispatch Queue</h3>
                <div className="flex gap-2 items-center">
                  <span className="w-2 h-2 rounded-full bg-error animate-pulse"></span>
                  <span className="text-[10px] font-bold text-error uppercase">3 Issues Detected</span>
                </div>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-left">
                  <thead className="bg-surface-container-low text-[10px] uppercase tracking-wider text-on-surface-variant font-bold">
                    <tr>
                      <th className="px-6 py-3">Task ID</th>
                      <th className="px-6 py-3">Channel</th>
                      <th className="px-6 py-3">Reason</th>
                      <th className="px-6 py-3">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-surface-container text-sm">
                    <tr className="hover:bg-slate-50 transition-colors">
                      <td className="px-6 py-4 font-mono text-xs">TX_88291</td>
                      <td className="px-6 py-4 text-on-surface-variant">Email</td>
                      <td className="px-6 py-4 text-error font-medium">Timeout</td>
                      <td className="px-6 py-4">
                        <button className="flex items-center gap-1 text-primary hover:text-primary-dim font-bold text-xs">
                          <span className="material-symbols-outlined text-sm">refresh</span> Retry
                        </button>
                      </td>
                    </tr>
                    <tr className="hover:bg-slate-50 transition-colors">
                      <td className="px-6 py-4 font-mono text-xs">TX_88295</td>
                      <td className="px-6 py-4 text-on-surface-variant">SMS</td>
                      <td className="px-6 py-4 text-error font-medium">Provider Error</td>
                      <td className="px-6 py-4">
                        <button className="flex items-center gap-1 text-primary hover:text-primary-dim font-bold text-xs">
                          <span className="material-symbols-outlined text-sm">refresh</span> Retry
                        </button>
                      </td>
                    </tr>
                    <tr className="hover:bg-slate-50 transition-colors">
                      <td className="px-6 py-4 font-mono text-xs">TX_88301</td>
                      <td className="px-6 py-4 text-on-surface-variant">Push</td>
                      <td className="px-6 py-4 text-tertiary font-medium">Retrying...</td>
                      <td className="px-6 py-4">
                        <span className="text-outline text-xs italic">Auto-retry</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </main>
      </div>
      {/* Mobile Navigation */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 glass-nav h-16 flex items-center justify-around border-t border-surface-container z-50">
        <button className="flex flex-col items-center text-on-surface-variant">
          <span className="material-symbols-outlined">dashboard</span>
          <span className="text-[10px] font-bold">Dash</span>
        </button>
        <button className="flex flex-col items-center text-primary">
          <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>campaign</span>
          <span className="text-[10px] font-bold">Notify</span>
        </button>
        <button className="flex flex-col items-center text-on-surface-variant">
          <span className="material-symbols-outlined">settings</span>
          <span className="text-[10px] font-bold">Ops</span>
        </button>
      </nav>
    </>
  );
}
