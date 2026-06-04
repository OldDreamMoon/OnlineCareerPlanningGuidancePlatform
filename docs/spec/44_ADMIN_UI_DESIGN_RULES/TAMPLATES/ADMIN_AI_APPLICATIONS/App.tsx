/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';

const Sidebar = () => (
  <aside className="w-64 h-screen fixed left-0 top-0 border-r-0 bg-slate-50 dark:bg-slate-950 flex flex-col py-4 z-40">
    <div className="px-6 mb-8">
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 bg-primary-container rounded-xl flex items-center justify-center">
          <span className="material-symbols-outlined text-on-primary-container" style={{ fontVariationSettings: "'FILL' 1" }}>rocket_launch</span>
        </div>
        <div>
          <h1 className="text-lg font-bold text-slate-900 dark:text-white leading-tight">AI Management</h1>
          <p className="text-xs text-on-surface-variant">Graduation Project</p>
        </div>
      </div>
    </div>
    <nav className="flex-1 space-y-1">
      <a className="bg-white dark:bg-slate-800 text-cyan-600 dark:text-cyan-400 shadow-sm rounded-lg mx-2 flex items-center px-4 py-3 transition-all duration-300 ease-in-out group" href="#">
        <span className="material-symbols-outlined mr-3" style={{ fontVariationSettings: "'FILL' 1" }}>rocket_launch</span>
        <span className="font-medium">Operations</span>
      </a>
      <a className="text-slate-600 dark:text-slate-400 mx-2 flex items-center px-4 py-3 hover:bg-slate-200 dark:hover:bg-slate-800/50 transition-all duration-300 ease-in-out rounded-lg group" href="#">
        <span className="material-symbols-outlined mr-3">grid_view</span>
        <span>Dashboard</span>
      </a>
      <a className="text-slate-600 dark:text-slate-400 mx-2 flex items-center px-4 py-3 hover:bg-slate-200 dark:hover:bg-slate-800/50 transition-all duration-300 ease-in-out rounded-lg group" href="#">
        <span className="material-symbols-outlined mr-3">hub</span>
        <span>Providers</span>
      </a>
      <a className="text-slate-600 dark:text-slate-400 mx-2 flex items-center px-4 py-3 hover:bg-slate-200 dark:hover:bg-slate-800/50 transition-all duration-300 ease-in-out rounded-lg group" href="#">
        <span className="material-symbols-outlined mr-3">alt_route</span>
        <span>Routing</span>
      </a>
      <a className="text-slate-600 dark:text-slate-400 mx-2 flex items-center px-4 py-3 hover:bg-slate-200 dark:hover:bg-slate-800/50 transition-all duration-300 ease-in-out rounded-lg group" href="#">
        <span className="material-symbols-outlined mr-3">terminal</span>
        <span>Prompts</span>
      </a>
      <a className="text-slate-600 dark:text-slate-400 mx-2 flex items-center px-4 py-3 hover:bg-slate-200 dark:hover:bg-slate-800/50 transition-all duration-300 ease-in-out rounded-lg group" href="#">
        <span className="material-symbols-outlined mr-3">payments</span>
        <span>Costs</span>
      </a>
      <a className="text-slate-600 dark:text-slate-400 mx-2 flex items-center px-4 py-3 hover:bg-slate-200 dark:hover:bg-slate-800/50 transition-all duration-300 ease-in-out rounded-lg group" href="#">
        <span className="material-symbols-outlined mr-3">description</span>
        <span>Logs</span>
      </a>
    </nav>
    <div className="mt-auto px-2 space-y-1 border-t border-slate-200 dark:border-slate-800 pt-4">
      <a className="text-slate-600 dark:text-slate-400 flex items-center px-4 py-2 hover:bg-slate-200 dark:hover:bg-slate-800/50 transition-all duration-300 rounded-lg" href="#">
        <span className="material-symbols-outlined mr-3">book</span>
        <span className="text-sm">Docs</span>
      </a>
      <a className="text-slate-600 dark:text-slate-400 flex items-center px-4 py-2 hover:bg-slate-200 dark:hover:bg-slate-800/50 transition-all duration-300 rounded-lg" href="#">
        <span className="material-symbols-outlined mr-3">contact_support</span>
        <span className="text-sm">Support</span>
      </a>
    </div>
  </aside>
);

const Header = () => (
  <header className="fixed top-0 right-0 left-64 z-50 h-16 bg-white/70 dark:bg-slate-900/70 backdrop-blur-xl shadow-[0px_10px_40px_rgba(44,29,49,0.06)] flex justify-between items-center px-8">
    <div className="flex items-center gap-6">
      <span className="text-xl font-black text-slate-900 dark:text-white tracking-tight font-headline">Luminous AI Suite</span>
      <div className="h-4 w-px bg-slate-200 dark:bg-slate-700"></div>
      <div className="hidden md:flex space-x-6 text-sm font-medium">
        <a className="text-cyan-600 dark:text-cyan-400 border-b-2 border-cyan-500 pb-1" href="#">Operations</a>
        <a className="text-slate-500 dark:text-slate-400 hover:text-cyan-600 transition-colors" href="#">Integrations</a>
        <a className="text-slate-500 dark:text-slate-400 hover:text-cyan-600 transition-colors" href="#">Security</a>
      </div>
    </div>
    <div className="flex items-center gap-4">
      <div className="bg-surface-container-low px-3 py-1.5 rounded-full flex items-center gap-2 w-64">
        <span className="material-symbols-outlined text-on-surface-variant text-sm">search</span>
        <input className="bg-transparent border-none focus:ring-0 text-sm w-full p-0 outline-none" placeholder="Search resources..." type="text" />
      </div>
      <div className="flex gap-2">
        <button className="p-2 text-on-surface-variant hover:bg-slate-100/50 rounded-full transition-colors"><span className="material-symbols-outlined">notifications</span></button>
        <button className="p-2 text-on-surface-variant hover:bg-slate-100/50 rounded-full transition-colors"><span className="material-symbols-outlined">settings</span></button>
      </div>
      <div className="w-8 h-8 rounded-full bg-primary overflow-hidden">
        <img alt="Profile" className="w-full h-full object-cover" src="https://lh3.googleusercontent.com/aida-public/AB6AXuC8TuceWI30YdgiUXHd7A2DqXjBSfqzLfn-7fHPRBw739LA8QIbJjumxxgR-YoKil9oRi8xn-NjpfllfeeuYN0STE0wD6V60_dR-zx-HeIWWkKS0brjxshbBcyn8Db1FQxuo3kuyFqPySNSKmvjJQfyrgKVLZhmik9i0ixhw2xSm6vmalVDMGTwrrLL-BBJGgPlTwIU-PTtmTnAry9754mNqWdqiMd6tFENEQi5ZJfWBOCA7nekp2bFu7pFYMwHiP5hqmuThVLFku4" />
      </div>
    </div>
  </header>
);

const Hero = () => (
  <section className="relative overflow-hidden bg-surface-container-lowest rounded-xl p-8 border border-white shadow-[0px_10px_40px_rgba(44,47,49,0.06)]">
    <div className="relative z-10 flex flex-col md:flex-row justify-between items-end gap-6">
      <div className="max-w-2xl">
        <div className="inline-flex items-center gap-2 px-3 py-1 bg-secondary-container text-on-secondary-container rounded-full text-xs font-bold mb-4">
          <span className="material-symbols-outlined text-sm" style={{ fontVariationSettings: "'FILL' 1" }}>verified</span>
          SYSTEM OPERATIONAL
        </div>
        <h2 className="text-4xl font-extrabold text-on-background font-headline tracking-tight mb-3">AI Application Operations</h2>
        <p className="text-on-surface-variant text-lg leading-relaxed">Centralized management for your enterprise AI ecosystem. Monitor routes, optimize costs, and manage model deployments with surgical precision.</p>
      </div>
      <div className="flex gap-4">
        <button className="bg-surface-container-low text-on-surface px-6 py-3 rounded-xl font-semibold hover:bg-surface-container-high transition-colors flex items-center gap-2">
          <span className="material-symbols-outlined">hub</span>
          AI Gateway
        </button>
        <button className="bg-primary text-on-primary px-6 py-3 rounded-xl font-semibold shadow-lg shadow-primary/20 hover:scale-105 active:scale-95 transition-all flex items-center gap-2">
          <span className="material-symbols-outlined">settings_input_component</span>
          Console Config
        </button>
      </div>
    </div>
    <div className="absolute -top-24 -right-24 w-96 h-96 bg-primary/5 rounded-full blur-3xl"></div>
  </section>
);

const Metrics = () => (
  <section className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
    <div className="bg-surface-container-lowest p-6 rounded-xl shadow-[0px_10px_40px_rgba(44,47,49,0.04)] group hover:translate-y-[-4px] transition-transform">
      <div className="flex justify-between items-start mb-4">
        <div className="p-3 bg-primary-fixed-dim/20 rounded-xl text-primary">
          <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>account_tree</span>
        </div>
        <span className="text-secondary text-sm font-bold flex items-center gap-1">
          <span className="material-symbols-outlined text-xs">trending_up</span>
          12%
        </span>
      </div>
      <p className="text-on-surface-variant text-sm font-medium">Registered Channels</p>
      <div className="flex items-baseline gap-2">
        <h3 className="text-3xl font-black font-headline">42</h3>
        <span className="text-outline text-xs">Total models</span>
      </div>
    </div>

    <div className="bg-surface-container-lowest p-6 rounded-xl shadow-[0px_10px_40px_rgba(44,47,49,0.04)] group hover:translate-y-[-4px] transition-transform">
      <div className="flex justify-between items-start mb-4">
        <div className="p-3 bg-secondary-container/30 rounded-xl text-secondary">
          <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>alt_route</span>
        </div>
        <span className="text-secondary text-sm font-bold flex items-center gap-1">
          <span className="material-symbols-outlined text-xs">trending_up</span>
          8%
        </span>
      </div>
      <p className="text-on-surface-variant text-sm font-medium">Configured Routes</p>
      <div className="flex items-baseline gap-2">
        <h3 className="text-3xl font-black font-headline">156</h3>
        <span className="text-outline text-xs">Active paths</span>
      </div>
    </div>

    <div className="bg-surface-container-lowest p-6 rounded-xl shadow-[0px_10px_40px_rgba(44,47,49,0.04)] group hover:translate-y-[-4px] transition-transform">
      <div className="flex justify-between items-start mb-4">
        <div className="p-3 bg-tertiary-container/30 rounded-xl text-tertiary">
          <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>bolt</span>
        </div>
        <div className="px-2 py-0.5 bg-tertiary-container/20 rounded text-[10px] text-tertiary font-bold">L7 DAYS</div>
      </div>
      <p className="text-on-surface-variant text-sm font-medium">Active Channels</p>
      <div className="flex items-baseline gap-2">
        <h3 className="text-3xl font-black font-headline">38</h3>
        <span className="text-outline text-xs">Last 7 days</span>
      </div>
    </div>

    <div className="bg-surface-container-lowest p-6 rounded-xl shadow-[0px_10px_40px_rgba(44,47,49,0.04)] group hover:translate-y-[-4px] transition-transform">
      <div className="flex justify-between items-start mb-4">
        <div className="p-3 bg-error-container/20 rounded-xl text-error">
          <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>payments</span>
        </div>
        <span className="text-error text-sm font-bold flex items-center gap-1">
          <span className="material-symbols-outlined text-xs">trending_down</span>
          4%
        </span>
      </div>
      <p className="text-on-surface-variant text-sm font-medium">Estimated Cost</p>
      <div className="flex items-baseline gap-2">
        <h3 className="text-3xl font-black font-headline">$1,240</h3>
        <span className="text-outline text-xs">L7 days</span>
      </div>
    </div>
  </section>
);

const Table = () => (
  <section className="bg-surface-container-lowest rounded-xl overflow-hidden border border-white shadow-[0px_10px_40px_rgba(44,47,49,0.06)]">
    <div className="p-6 flex flex-col md:flex-row justify-between items-center gap-4 border-b border-surface-container-low">
      <div>
        <h3 className="text-xl font-bold font-headline">Channel Master Repository</h3>
        <p className="text-sm text-on-surface-variant">Global oversight of app-to-scene mappings and status.</p>
      </div>
      <div className="flex gap-2">
        <button className="bg-surface-container-low px-4 py-2 rounded-lg text-sm font-medium hover:bg-surface-container-high transition-colors">Export Report</button>
        <button className="bg-primary text-on-primary px-4 py-2 rounded-lg text-sm font-medium hover:opacity-90 transition-opacity flex items-center gap-2">
          <span className="material-symbols-outlined text-sm">add</span>
          Register New
        </button>
      </div>
    </div>
    <div className="overflow-x-auto">
      <table className="w-full text-left border-collapse">
        <thead className="bg-surface-container-low/50">
          <tr>
            <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-on-surface-variant border-b border-surface-container-low">App Channel</th>
            <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-on-surface-variant border-b border-surface-container-low">Scene Mapping</th>
            <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-on-surface-variant border-b border-surface-container-low">Primary Route</th>
            <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-on-surface-variant border-b border-surface-container-low">Template Binding</th>
            <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-on-surface-variant border-b border-surface-container-low">Coverage Status</th>
            <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-on-surface-variant border-b border-surface-container-low">Next Steps</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-surface-container-low">
          <tr className="hover:bg-surface-container-low/30 transition-colors">
            <td className="px-6 py-5">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-primary-container/20 flex items-center justify-center">
                  <span className="material-symbols-outlined text-primary text-lg">smart_toy</span>
                </div>
                <div>
                  <p className="font-semibold text-sm">Customer-Assist-V2</p>
                  <p className="text-[10px] text-outline">ID: 88201-AX</p>
                </div>
              </div>
            </td>
            <td className="px-6 py-5">
              <span className="px-2 py-1 bg-surface-container-low rounded text-xs font-medium">Support_Chat</span>
            </td>
            <td className="px-6 py-5">
              <div className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-secondary"></span>
                <span className="text-sm">GPT-4-Turbo</span>
              </div>
            </td>
            <td className="px-6 py-5">
              <span className="text-sm text-on-surface-variant">Standard_Support_V1</span>
            </td>
            <td className="px-6 py-5">
              <div className="flex items-center gap-2">
                <div className="flex-1 h-1.5 bg-surface-container-low rounded-full overflow-hidden w-24">
                  <div className="h-full bg-secondary w-[100%]"></div>
                </div>
                <span className="text-[10px] font-bold text-secondary">ACTIVE</span>
              </div>
            </td>
            <td className="px-6 py-5 text-right">
              <button className="text-primary hover:bg-primary/10 p-1.5 rounded-lg transition-colors"><span className="material-symbols-outlined">more_vert</span></button>
            </td>
          </tr>
          <tr className="hover:bg-surface-container-low/30 transition-colors">
            <td className="px-6 py-5">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-surface-container-high/50 flex items-center justify-center text-outline">
                  <span className="material-symbols-outlined text-lg">pending</span>
                </div>
                <div>
                  <p className="font-semibold text-sm">Legal-Summarizer</p>
                  <p className="text-[10px] text-outline">ID: 99120-BL</p>
                </div>
              </div>
            </td>
            <td className="px-6 py-5">
              <span className="px-2 py-1 bg-surface-container-low rounded text-xs font-medium">Document_Review</span>
            </td>
            <td className="px-6 py-5">
              <span className="text-xs italic text-outline">Not assigned</span>
            </td>
            <td className="px-6 py-5">
              <span className="text-xs italic text-outline">No binding</span>
            </td>
            <td className="px-6 py-5">
              <div className="inline-flex items-center gap-1 px-2 py-1 bg-surface-container-high rounded text-[10px] font-bold text-on-surface-variant">
                REGISTERED
              </div>
            </td>
            <td className="px-6 py-5">
              <button className="text-primary text-xs font-bold hover:underline">Configure Route</button>
            </td>
          </tr>
          <tr className="hover:bg-surface-container-low/30 transition-colors">
            <td className="px-6 py-5">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-tertiary-container/20 flex items-center justify-center text-tertiary">
                  <span className="material-symbols-outlined text-lg">route</span>
                </div>
                <div>
                  <p className="font-semibold text-sm">Internal-HR-Bot</p>
                  <p className="text-[10px] text-outline">ID: 44301-HR</p>
                </div>
              </div>
            </td>
            <td className="px-6 py-5">
              <span className="px-2 py-1 bg-surface-container-low rounded text-xs font-medium">Employee_FAQ</span>
            </td>
            <td className="px-6 py-5">
              <div className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-secondary"></span>
                <span className="text-sm">Claude-3-Sonnet</span>
              </div>
            </td>
            <td className="px-6 py-5">
              <span className="text-xs italic text-error flex items-center gap-1 font-medium">
                <span className="material-symbols-outlined text-xs">error</span>
                No template
              </span>
            </td>
            <td className="px-6 py-5">
              <div className="inline-flex items-center gap-1 px-2 py-1 bg-tertiary-container/30 rounded text-[10px] font-bold text-tertiary">
                PARTIAL
              </div>
            </td>
            <td className="px-6 py-5">
              <button className="text-primary text-xs font-bold hover:underline">Bind Template</button>
            </td>
          </tr>
          <tr className="hover:bg-surface-container-low/30 transition-colors">
            <td className="px-6 py-5">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-error-container/10 flex items-center justify-center text-error">
                  <span className="material-symbols-outlined text-lg">warning</span>
                </div>
                <div>
                  <p className="font-semibold text-sm">Marketing-Copy-Gen</p>
                  <p className="text-[10px] text-outline">ID: 11044-MK</p>
                </div>
              </div>
            </td>
            <td className="px-6 py-5">
              <span className="px-2 py-1 bg-surface-container-low rounded text-xs font-medium">Creative_Writing</span>
            </td>
            <td className="px-6 py-5">
              <div className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-error"></span>
                <span className="text-sm text-error font-medium">Invalid Config</span>
              </div>
            </td>
            <td className="px-6 py-5">
              <span className="text-sm">Marketing_A_V4</span>
            </td>
            <td className="px-6 py-5">
              <div className="inline-flex items-center gap-1 px-2 py-1 bg-error-container/20 rounded text-[10px] font-bold text-error">
                ERROR
              </div>
            </td>
            <td className="px-6 py-5">
              <button className="text-error text-xs font-bold hover:underline">Fix Schema</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div className="p-4 border-t border-surface-container-low flex justify-between items-center bg-surface-container-low/20">
      <span className="text-xs text-on-surface-variant font-medium">Showing 1-4 of 42 channels</span>
      <div className="flex gap-1">
        <button className="w-8 h-8 flex items-center justify-center rounded-lg border border-surface-container-high hover:bg-white transition-colors text-outline">
          <span className="material-symbols-outlined text-sm">chevron_left</span>
        </button>
        <button className="w-8 h-8 flex items-center justify-center rounded-lg bg-primary text-on-primary text-xs font-bold">1</button>
        <button className="w-8 h-8 flex items-center justify-center rounded-lg border border-surface-container-high hover:bg-white transition-colors text-xs text-on-surface">2</button>
        <button className="w-8 h-8 flex items-center justify-center rounded-lg border border-surface-container-high hover:bg-white transition-colors text-xs text-on-surface">3</button>
        <button className="w-8 h-8 flex items-center justify-center rounded-lg border border-surface-container-high hover:bg-white transition-colors text-outline">
          <span className="material-symbols-outlined text-sm">chevron_right</span>
        </button>
      </div>
    </div>
  </section>
);

const Charts = () => (
  <section className="grid grid-cols-1 lg:grid-cols-3 gap-8">
    <div className="lg:col-span-2 bg-surface-container-lowest p-6 rounded-xl border border-white shadow-[0px_10px_40px_rgba(44,47,49,0.04)]">
      <h3 className="text-lg font-bold font-headline mb-4">Traffic Throughput (24h)</h3>
      <div className="h-64 flex items-end justify-between gap-2 px-4">
        <div className="flex-1 bg-primary-container/20 rounded-t-lg h-[40%] hover:bg-primary transition-colors cursor-pointer relative group">
          <div className="absolute -top-8 left-1/2 -translate-x-1/2 bg-on-background text-white text-[10px] py-1 px-2 rounded opacity-0 group-hover:opacity-100 transition-opacity">1.2k</div>
        </div>
        <div className="flex-1 bg-primary-container/20 rounded-t-lg h-[55%] hover:bg-primary transition-colors cursor-pointer relative group"></div>
        <div className="flex-1 bg-primary-container/20 rounded-t-lg h-[70%] hover:bg-primary transition-colors cursor-pointer relative group"></div>
        <div className="flex-1 bg-primary-container/20 rounded-t-lg h-[65%] hover:bg-primary transition-colors cursor-pointer relative group"></div>
        <div className="flex-1 bg-primary-container/20 rounded-t-lg h-[90%] hover:bg-primary transition-colors cursor-pointer relative group"></div>
        <div className="flex-1 bg-primary-container/20 rounded-t-lg h-[85%] hover:bg-primary transition-colors cursor-pointer relative group"></div>
        <div className="flex-1 bg-primary-container/30 rounded-t-lg h-[100%] hover:bg-primary transition-colors cursor-pointer relative group"></div>
        <div className="flex-1 bg-primary-container/20 rounded-t-lg h-[75%] hover:bg-primary transition-colors cursor-pointer relative group"></div>
        <div className="flex-1 bg-primary-container/20 rounded-t-lg h-[60%] hover:bg-primary transition-colors cursor-pointer relative group"></div>
        <div className="flex-1 bg-primary-container/20 rounded-t-lg h-[50%] hover:bg-primary transition-colors cursor-pointer relative group"></div>
      </div>
      <div className="mt-4 flex justify-between text-[10px] font-bold text-outline uppercase tracking-widest px-4">
        <span>00:00</span>
        <span>06:00</span>
        <span>12:00</span>
        <span>18:00</span>
        <span>23:59</span>
      </div>
    </div>
    <div className="bg-surface-container-lowest p-6 rounded-xl border border-white shadow-[0px_10px_40px_rgba(44,47,49,0.04)] flex flex-col justify-between">
      <div>
        <h3 className="text-lg font-bold font-headline mb-4">Resource Distribution</h3>
        <div className="space-y-4">
          <div className="space-y-1">
            <div className="flex justify-between text-xs font-medium">
              <span>Azure OpenAI</span>
              <span>65%</span>
            </div>
            <div className="h-2 w-full bg-surface-container-low rounded-full overflow-hidden">
              <div className="h-full bg-primary w-[65%]"></div>
            </div>
          </div>
          <div className="space-y-1">
            <div className="flex justify-between text-xs font-medium">
              <span>Anthropic</span>
              <span>22%</span>
            </div>
            <div className="h-2 w-full bg-surface-container-low rounded-full overflow-hidden">
              <div className="h-full bg-secondary w-[22%]"></div>
            </div>
          </div>
          <div className="space-y-1">
            <div className="flex justify-between text-xs font-medium">
              <span>HuggingFace</span>
              <span>13%</span>
            </div>
            <div className="h-2 w-full bg-surface-container-low rounded-full overflow-hidden">
              <div className="h-full bg-tertiary w-[13%]"></div>
            </div>
          </div>
        </div>
      </div>
      <button className="w-full mt-6 py-3 border border-surface-container-high rounded-xl text-sm font-semibold hover:bg-surface-container-low transition-colors">
        View Detailed Audit
      </button>
    </div>
  </section>
);

export default function App() {
  return (
    <div className="bg-surface text-on-surface selection:bg-primary-container selection:text-on-primary-container min-h-screen font-body">
      <Sidebar />
      <main className="ml-64 min-h-screen">
        <Header />
        <div className="pt-24 pb-12 px-8 max-w-7xl mx-auto space-y-8">
          <Hero />
          <Metrics />
          <Table />
          <Charts />
        </div>
      </main>
    </div>
  );
}
