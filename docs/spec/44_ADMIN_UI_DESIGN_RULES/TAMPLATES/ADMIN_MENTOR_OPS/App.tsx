/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export default function App() {
  return (
    <div className="min-h-screen bg-surface flex flex-col font-body text-on-surface">
      {/* Top Navigation Bar */}
      <header className="fixed top-0 w-full z-50 bg-white/70 backdrop-blur-xl shadow-sm h-16 flex justify-between items-center px-6">
        <div className="flex items-center gap-8">
          <span className="font-headline text-xl font-bold tracking-tight text-primary">
            Luminous Admin
          </span>
          <div className="hidden md:flex items-center gap-6">
            <a href="#" className="text-primary font-semibold border-b-2 border-primary font-headline py-5">
              Overview
            </a>
            <a href="#" className="text-on-surface-variant font-headline py-5 hover:bg-slate-50 transition-colors">
              Analytics
            </a>
            <a href="#" className="text-on-surface-variant font-headline py-5 hover:bg-slate-50 transition-colors">
              Audit Logs
            </a>
          </div>
        </div>
        <div className="flex items-center gap-4">
          <div className="relative hidden sm:block">
            <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline-variant">
              search
            </span>
            <input
              type="text"
              placeholder="Search governance..."
              className="bg-surface-container-low border-none rounded-xl pl-10 pr-4 py-2 text-sm focus:ring-2 focus:ring-primary focus:bg-surface-container-lowest transition-all w-64 outline-none"
            />
          </div>
          <button className="p-2 text-on-surface-variant hover:bg-slate-50 rounded-lg transition-colors flex items-center justify-center">
            <span className="material-symbols-outlined">notifications</span>
          </button>
          <button className="p-2 text-on-surface-variant hover:bg-slate-50 rounded-lg transition-colors flex items-center justify-center">
            <span className="material-symbols-outlined">settings</span>
          </button>
          <img
            src="https://lh3.googleusercontent.com/aida-public/AB6AXuColub3lVp6WXcl-N6W-SoqZ_IpLH9qsX4dLxRLIRlaRdSlHDKW0RY_g6wPQTSqcQLV7mbqtu6kGCSoISYRVF1jDTxnGqJrUsBIXjc5t8jpo7YgSfbHe7K-e-x9RmqdLpLuy59gzOoxhxAZXyDgTuLEQxcEM_8238wexAeq0x49roXEBlSycwEvpuGfKJpr08VsQyBoRoPFayvpTHxVGMSgeCDS_Gio4So7Fy2ideCYNBoK1V_o1RIDGR3SrY2vFnOpPwZsmLcJobU"
            alt="Admin Avatar"
            className="w-8 h-8 rounded-full border border-outline-variant/30 ml-2 object-cover"
          />
        </div>
      </header>

      <div className="flex flex-1 pt-16">
        {/* Side Navigation */}
        <aside className="fixed left-0 h-[calc(100vh-4rem)] w-64 flex flex-col py-4 px-3 bg-slate-50 font-headline font-medium text-sm border-r border-surface-container-low/50">
          <div className="px-4 mb-8">
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 bg-primary rounded-lg flex items-center justify-center text-white">
                <span className="material-symbols-outlined text-xl">verified</span>
              </div>
              <div>
                <h2 className="text-lg font-bold text-on-surface leading-none">Core Ops</h2>
                <span className="text-[10px] text-on-surface-variant uppercase tracking-wider">Management Suite</span>
              </div>
            </div>
          </div>
          <nav className="flex-1 space-y-1">
            <a href="#" className="flex items-center gap-3 px-4 py-3 text-on-surface-variant hover:bg-slate-100 hover:translate-x-1 transition-all rounded-lg">
              <span className="material-symbols-outlined">verified</span> Certifications
            </a>
            <a href="#" className="flex items-center gap-3 px-4 py-3 text-on-surface-variant hover:bg-slate-100 hover:translate-x-1 transition-all rounded-lg">
              <span className="material-symbols-outlined">desktop_windows</span> Workspaces
            </a>
            <a href="#" className="flex items-center gap-3 px-4 py-3 bg-primary/10 text-primary rounded-lg transition-all">
              <span className="material-symbols-outlined">groups</span> Mentors
            </a>
            <a href="#" className="flex items-center gap-3 px-4 py-3 text-on-surface-variant hover:bg-slate-100 hover:translate-x-1 transition-all rounded-lg">
              <span className="material-symbols-outlined">campaign</span> Notifications
            </a>
          </nav>
          <div className="mt-auto border-t border-slate-200/50 pt-4 space-y-1">
            <a href="#" className="flex items-center gap-3 px-4 py-3 text-on-surface-variant hover:bg-slate-100 transition-all rounded-lg">
              <span className="material-symbols-outlined">contact_support</span> Support
            </a>
            <a href="#" className="flex items-center gap-3 px-4 py-3 text-error hover:bg-error-container/10 transition-all rounded-lg">
              <span className="material-symbols-outlined">logout</span> Logout
            </a>
          </div>
        </aside>

        {/* Main Content Area */}
        <main className="flex-1 ml-64 p-8 relative">
          {/* Background Decoration */}
          <div className="fixed top-0 left-0 -z-10 w-full h-full pointer-events-none opacity-20">
            <div className="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] bg-primary-container rounded-full blur-[120px]"></div>
            <div className="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-secondary-container rounded-full blur-[100px]"></div>
          </div>

          {/* Header Section */}
          <div className="mb-10 flex flex-col md:flex-row md:items-end justify-between gap-6">
            <div>
              <h1 className="font-headline text-3xl font-extrabold text-on-surface tracking-tight mb-2">
                Mentor Operations Governance
              </h1>
              <p className="text-on-surface-variant max-w-2xl">
                Oversee mentor quality, risk signals, and withdrawal requests within the Luminous ecosystem.
              </p>
            </div>
            <div className="flex items-center gap-3">
              <button className="px-5 py-2.5 bg-surface-container-low text-on-surface font-semibold rounded-xl hover:bg-surface-container-high transition-colors flex items-center gap-2">
                <span className="material-symbols-outlined">file_download</span> Export Report
              </button>
              <button className="px-5 py-2.5 bg-gradient-to-br from-primary to-primary-dim text-white font-semibold rounded-xl shadow-[0_8px_20px_rgba(70,71,211,0.2)] hover:scale-[1.02] transition-transform flex items-center gap-2">
                <span className="material-symbols-outlined">add</span> Create Audit
              </button>
            </div>
          </div>

          {/* Summary Cards Bento */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-10">
            <div className="bg-surface-container-lowest p-6 rounded-2xl shadow-[0_10px_40px_rgba(44,47,49,0.04)] border border-transparent hover:border-primary-fixed-dim/30 transition-all">
              <div className="flex items-center justify-between mb-4">
                <div className="p-2 bg-primary/10 text-primary rounded-lg flex items-center justify-center">
                  <span className="material-symbols-outlined">group</span>
                </div>
                <span className="text-secondary text-sm font-semibold flex items-center gap-1">
                  <span className="material-symbols-outlined text-xs">trending_up</span> +12%
                </span>
              </div>
              <p className="text-on-surface-variant text-sm mb-1">Total Mentors</p>
              <h3 className="font-headline text-2xl font-bold">1,284</h3>
            </div>

            <div className="bg-surface-container-lowest p-6 rounded-2xl shadow-[0_10px_40px_rgba(44,47,49,0.04)] border border-transparent hover:border-tertiary-fixed/30 transition-all">
              <div className="flex items-center justify-between mb-4">
                <div className="p-2 bg-tertiary/10 text-tertiary rounded-lg flex items-center justify-center">
                  <span className="material-symbols-outlined">pending_actions</span>
                </div>
                <span className="text-tertiary text-sm font-semibold">Active Review</span>
              </div>
              <p className="text-on-surface-variant text-sm mb-1">Pending Inspections</p>
              <h3 className="font-headline text-2xl font-bold">42</h3>
            </div>

            <div className="bg-surface-container-lowest p-6 rounded-2xl shadow-[0_10px_40px_rgba(44,47,49,0.04)] border border-transparent hover:border-error-container/30 transition-all">
              <div className="flex items-center justify-between mb-4">
                <div className="p-2 bg-error/10 text-error rounded-lg flex items-center justify-center">
                  <span className="material-symbols-outlined">warning</span>
                </div>
                <span className="text-error text-sm font-semibold">High Priority</span>
              </div>
              <p className="text-on-surface-variant text-sm mb-1">Risky Profiles</p>
              <h3 className="font-headline text-2xl font-bold">18</h3>
            </div>

            <div className="bg-surface-container-lowest p-6 rounded-2xl shadow-[0_10px_40px_rgba(44,47,49,0.04)] border border-transparent hover:border-secondary-container/30 transition-all">
              <div className="flex items-center justify-between mb-4">
                <div className="p-2 bg-secondary/10 text-secondary rounded-lg flex items-center justify-center">
                  <span className="material-symbols-outlined">payments</span>
                </div>
                <span className="text-on-surface-variant text-sm font-semibold">MTD</span>
              </div>
              <p className="text-on-surface-variant text-sm mb-1">Total Withdrawals</p>
              <h3 className="font-headline text-2xl font-bold">$142.8k</h3>
            </div>
          </div>

          <div className="grid grid-cols-12 gap-8">
            {/* Main Governance Table */}
            <div className="col-span-12 lg:col-span-8 space-y-6">
              <div className="bg-surface-container-lowest rounded-2xl shadow-[0_10px_40px_rgba(44,47,49,0.04)] overflow-hidden">
                <div className="p-6 border-b border-surface-container-low flex items-center justify-between flex-wrap gap-4">
                  <h2 className="font-headline text-xl font-bold">Mentor Inspection Registry</h2>
                  <div className="flex items-center gap-2 bg-surface-container-low p-1 rounded-xl">
                    <button className="px-4 py-1.5 text-sm font-medium bg-surface-container-lowest shadow-sm rounded-lg transition-colors">All</button>
                    <button className="px-4 py-1.5 text-sm font-medium text-on-surface-variant hover:text-on-surface transition-colors">Critical</button>
                    <button className="px-4 py-1.5 text-sm font-medium text-on-surface-variant hover:text-on-surface transition-colors">Pending</button>
                  </div>
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full text-left">
                    <thead className="bg-slate-50/50">
                      <tr>
                        <th className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-on-surface-variant">Mentor & Subject</th>
                        <th className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-on-surface-variant text-center">Packages</th>
                        <th className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-on-surface-variant text-center">Quality Score</th>
                        <th className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-on-surface-variant">Risk Level</th>
                        <th className="px-6 py-4"></th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-surface-container-low/50">
                      <tr className="hover:bg-slate-50/50 transition-colors cursor-pointer group">
                        <td className="px-6 py-5">
                          <div className="flex items-center gap-4">
                            <img
                              src="https://lh3.googleusercontent.com/aida-public/AB6AXuCgzJVWnqdZdBL6qNyl8ymjI3m7v0Uvw-buMRFaz30npi-UkF3zpSVRL7EVX2044AjZC3FEtGJVwxdpaC0FQ7LWIJXlOfriwog2aNxwO9xasETyq_4Sg_YTzv5r6zn82zZjKdJpnSaZowuw8HFRMFkLW9j-yxqATdzf0YS5Xz2b8oguSX8EA3QPPMRECLklTK03xm7fsL0BDufRsa5x2HU5Gp-KXibF7ONlEdmpCZOm4iqkBarUYemAYvi70LHu-avn4vPQfQFIukY"
                              alt="Dr. Aris Thorne"
                              className="w-10 h-10 rounded-xl object-cover shadow-sm"
                            />
                            <div>
                              <p className="font-bold text-on-surface leading-none mb-1">Dr. Aris Thorne</p>
                              <p className="text-xs text-on-surface-variant">Quantum Computing</p>
                            </div>
                          </div>
                        </td>
                        <td className="px-6 py-5 text-center">
                          <div className="flex flex-col items-center">
                            <span className="text-sm font-semibold">124</span>
                            <span className="text-[10px] text-on-surface-variant font-medium">98% Fulfillment</span>
                          </div>
                        </td>
                        <td className="px-6 py-5">
                          <div className="w-24 h-1.5 bg-surface-container-high rounded-full mx-auto overflow-hidden">
                            <div className="h-full bg-secondary-fixed-dim" style={{ width: '94%' }}></div>
                          </div>
                          <p className="text-center text-[10px] mt-1.5 font-bold text-secondary">4.9/5.0</p>
                        </td>
                        <td className="px-6 py-5">
                          <span className="px-2.5 py-1 text-[10px] font-bold bg-secondary-container/20 text-secondary rounded-lg uppercase">Low Risk</span>
                        </td>
                        <td className="px-6 py-5 text-right">
                          <button className="text-on-surface-variant group-hover:text-primary transition-colors flex items-center justify-end w-full">
                            <span className="material-symbols-outlined">chevron_right</span>
                          </button>
                        </td>
                      </tr>

                      <tr className="hover:bg-slate-50/50 transition-colors cursor-pointer group">
                        <td className="px-6 py-5">
                          <div className="flex items-center gap-4">
                            <img
                              src="https://lh3.googleusercontent.com/aida-public/AB6AXuAw9LPiQcmUBi4UAZ5d11oOaYxH5T3H0VL-q2zQAolu78dfYJgZBAV88Fd8fjLdJS0GBRBzIgye1zk1I5uZ78aIXqyLc6td_MIBDjNLxDQaefJ0zrHMLdSpEv8A56JbB1M0uSH9aUhgjD_HF_9KL2PXsKI54FgEPNCWClHLKigMQzb3irtQCTdNxCL62Uvn8PaWugLJtq6BSIGQMM8aycNEDHDF-gcQuWnASUrr6DTf6CsYzrYeA58pqXHPDGBBBSzWXqX0X-KteVc"
                              alt="Elena Vance"
                              className="w-10 h-10 rounded-xl object-cover shadow-sm"
                            />
                            <div>
                              <p className="font-bold text-on-surface leading-none mb-1">Elena Vance</p>
                              <p className="text-xs text-on-surface-variant">System Architecture</p>
                            </div>
                          </div>
                        </td>
                        <td className="px-6 py-5 text-center">
                          <div className="flex flex-col items-center">
                            <span className="text-sm font-semibold">58</span>
                            <span className="text-[10px] text-on-surface-variant font-medium">72% Fulfillment</span>
                          </div>
                        </td>
                        <td className="px-6 py-5">
                          <div className="w-24 h-1.5 bg-surface-container-high rounded-full mx-auto overflow-hidden">
                            <div className="h-full bg-tertiary-fixed" style={{ width: '65%' }}></div>
                          </div>
                          <p className="text-center text-[10px] mt-1.5 font-bold text-tertiary">3.2/5.0</p>
                        </td>
                        <td className="px-6 py-5">
                          <span className="px-2.5 py-1 text-[10px] font-bold bg-tertiary-container/20 text-tertiary rounded-lg uppercase">Moderate</span>
                        </td>
                        <td className="px-6 py-5 text-right">
                          <button className="text-on-surface-variant group-hover:text-primary transition-colors flex items-center justify-end w-full">
                            <span className="material-symbols-outlined">chevron_right</span>
                          </button>
                        </td>
                      </tr>

                      <tr className="hover:bg-error-container/5 transition-colors cursor-pointer bg-error-container/5 group">
                        <td className="px-6 py-5">
                          <div className="flex items-center gap-4">
                            <div className="w-10 h-10 rounded-xl bg-surface-container-high flex items-center justify-center font-bold text-on-surface-variant">
                              KS
                            </div>
                            <div>
                              <p className="font-bold text-on-surface leading-none mb-1">Kai Sterling</p>
                              <p className="text-xs text-on-surface-variant">Cryptoeconomics</p>
                            </div>
                          </div>
                        </td>
                        <td className="px-6 py-5 text-center">
                          <div className="flex flex-col items-center">
                            <span className="text-sm font-semibold">12</span>
                            <span className="text-[10px] text-error font-medium">30% Fulfillment</span>
                          </div>
                        </td>
                        <td className="px-6 py-5">
                          <div className="w-24 h-1.5 bg-surface-container-high rounded-full mx-auto overflow-hidden">
                            <div className="h-full bg-error" style={{ width: '25%' }}></div>
                          </div>
                          <p className="text-center text-[10px] mt-1.5 font-bold text-error">1.8/5.0</p>
                        </td>
                        <td className="px-6 py-5">
                          <span className="px-2.5 py-1 text-[10px] font-bold bg-error-container/20 text-error rounded-lg uppercase flex flex-col items-center leading-tight">
                            <span>CRITICAL</span>
                            <span>RISK</span>
                          </span>
                        </td>
                        <td className="px-6 py-5 text-right">
                          <button className="text-error group-hover:scale-110 transition-transform flex items-center justify-end w-full">
                            <span className="material-symbols-outlined">report</span>
                          </button>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>

            {/* Risk Signals & Detail Workspace */}
            <div className="col-span-12 lg:col-span-4 space-y-6">
              {/* Withdrawal Operations Panel */}
              <div className="bg-surface-container-lowest rounded-2xl shadow-[0_10px_40px_rgba(44,47,49,0.04)] p-6">
                <div className="flex items-center justify-between mb-6">
                  <h3 className="font-headline font-bold text-lg">Active Withdrawal</h3>
                  <span className="px-2 py-1 bg-primary/10 text-primary text-[10px] font-bold rounded-lg uppercase">Urgent</span>
                </div>
                <div className="flex items-center gap-4 p-4 bg-surface-container-low rounded-xl mb-6">
                  <div className="w-12 h-12 rounded-full bg-white flex items-center justify-center shadow-sm">
                    <span className="material-symbols-outlined text-primary">account_balance_wallet</span>
                  </div>
                  <div>
                    <p className="text-sm font-bold">$12,450.00</p>
                    <p className="text-[11px] text-on-surface-variant">Request by Kai Sterling</p>
                  </div>
                </div>
                <div className="space-y-4 mb-6">
                  <div className="flex justify-between items-center text-sm">
                    <span className="text-on-surface-variant">Verification Status</span>
                    <span className="text-error font-semibold flex items-center gap-1">
                      <span className="material-symbols-outlined text-sm">cancel</span> Disputed
                    </span>
                  </div>
                  <div className="flex justify-between items-center text-sm">
                    <span className="text-on-surface-variant">Account Age</span>
                    <span className="text-on-surface font-semibold">14 Days</span>
                  </div>
                  <div className="flex justify-between items-center text-sm">
                    <span className="text-on-surface-variant">IP Locality</span>
                    <span className="text-on-surface font-semibold">New York (VPN)</span>
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <button className="py-2.5 rounded-xl border border-outline-variant/30 font-semibold text-sm hover:bg-surface-container-low transition-colors">
                    Freeze Funds
                  </button>
                  <button className="py-2.5 rounded-xl bg-error text-white font-semibold text-sm hover:opacity-90 transition-opacity">
                    Deny Payout
                  </button>
                </div>
              </div>

              {/* Risk Signal Log */}
              <div className="bg-surface-container-lowest rounded-2xl shadow-[0_10px_40px_rgba(44,47,49,0.04)] p-6">
                <h3 className="font-headline font-bold text-lg mb-6">Anomaly Signals</h3>
                <div className="space-y-6">
                  <div className="flex gap-4">
                    <div className="w-1.5 h-auto bg-error rounded-full"></div>
                    <div>
                      <p className="text-sm font-bold leading-none mb-1">Rapid Successive Logins</p>
                      <p className="text-xs text-on-surface-variant mb-2">Account: Elena Vance • 2m ago</p>
                      <span className="px-2 py-0.5 bg-error-container/10 text-error text-[10px] font-bold rounded">High Alert</span>
                    </div>
                  </div>
                  <div className="flex gap-4">
                    <div className="w-1.5 h-auto bg-tertiary rounded-full"></div>
                    <div>
                      <p className="text-sm font-bold leading-none mb-1">Quality Drop-off (Fulfillment)</p>
                      <p className="text-xs text-on-surface-variant mb-2">Account: Sarah Jenkins • 15m ago</p>
                      <span className="px-2 py-0.5 bg-tertiary-container/10 text-tertiary text-[10px] font-bold rounded">Investigation</span>
                    </div>
                  </div>
                  <div className="flex gap-4">
                    <div className="w-1.5 h-auto bg-primary rounded-full"></div>
                    <div>
                      <p className="text-sm font-bold leading-none mb-1">Mentor Verification Passed</p>
                      <p className="text-xs text-on-surface-variant mb-2">Account: Marcus Aurel • 1h ago</p>
                      <span className="px-2 py-0.5 bg-secondary-container/10 text-secondary text-[10px] font-bold rounded">Log Entry</span>
                    </div>
                  </div>
                </div>
                <button className="w-full mt-6 py-3 border border-dashed border-outline-variant/50 text-on-surface-variant text-xs font-bold rounded-xl hover:bg-slate-50 transition-colors">
                  VIEW ALL AUDIT LOGS
                </button>
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
