import React from 'react';

const termsData = [
  { id: 1, term: 'crypto_scam_01', addedBy: 'AI Engine', time: '2h ago', risk: 'High Risk', riskClass: 'bg-error-container text-on-error-container', status: 'Active', statusClass: 'text-secondary', statusDotClass: 'bg-secondary', obsolete: false },
  { id: 2, term: 'external_link_bypass', addedBy: 'System', time: '1d ago', risk: 'Medium Risk', riskClass: 'bg-tertiary-container text-on-tertiary-container', status: 'Active', statusClass: 'text-secondary', statusDotClass: 'bg-secondary', obsolete: false },
  { id: 3, term: 'obsolete_term_99', addedBy: 'Admin', time: '2w ago', risk: 'Low Risk', riskClass: 'bg-slate-100 text-slate-500', status: 'Disabled', statusClass: 'text-slate-400', statusDotClass: 'bg-slate-300', obsolete: true },
];

export function SensitiveWords() {
  return (
    <div className="p-8 max-w-7xl mx-auto w-full">
      <div className="mb-10 flex flex-col md:flex-row md:items-end justify-between gap-6">
        <div>
          <h1 className="text-4xl font-extrabold tracking-tight text-on-surface mb-2">Sensitive Words</h1>
          <p className="text-on-surface-variant max-w-md">Configure and manage automated content filtering rules to maintain platform safety standards.</p>
        </div>
        <div className="flex gap-3">
          <button className="flex items-center gap-2 px-5 py-2.5 bg-surface-container-lowest text-on-surface border border-outline-variant/15 font-semibold rounded-xl hover:bg-surface-container-low transition-all shadow-sm">
            <span className="material-symbols-outlined text-sm">upload</span>
            <span>Import CSV</span>
          </button>
          <button className="flex items-center gap-2 px-5 py-2.5 bg-surface-container-lowest text-on-surface border border-outline-variant/15 font-semibold rounded-xl hover:bg-surface-container-low transition-all shadow-sm">
            <span className="material-symbols-outlined text-sm">download</span>
            <span>Export CSV</span>
          </button>
        </div>
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        {/* Form Card (Bento-style asymmetry) */}
        <div className="lg:col-span-4 space-y-6">
          <div className="bg-surface-container-lowest p-6 rounded-xl shadow-[0px_10px_40px_rgba(44,47,49,0.06)] border border-white/50 backdrop-blur-sm">
            <h3 className="text-xl font-bold mb-6 flex items-center gap-2">
              <span className="material-symbols-outlined text-primary">add_circle</span>
              Register New Term
            </h3>
            <form className="space-y-4">
              <div>
                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5 ml-1">Restricted Term</label>
                <input className="w-full bg-surface-container-low border-none rounded-xl px-4 py-3 focus:ring-2 focus:ring-primary focus:bg-white transition-all" placeholder="e.g. spam_keyword" type="text" />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5 ml-1">Severity Level</label>
                <select className="w-full bg-surface-container-low border-none rounded-xl px-4 py-3 focus:ring-2 focus:ring-primary focus:bg-white transition-all appearance-none">
                  <option>Low (Warning)</option>
                  <option>Medium (Flag)</option>
                  <option>High (Auto-Block)</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5 ml-1">Context Category</label>
                <div className="flex flex-wrap gap-2 mt-2">
                  <button className="px-3 py-1.5 bg-primary-container text-on-primary-container text-xs font-bold rounded-full" type="button">Harassment</button>
                  <button className="px-3 py-1.5 bg-surface-container-high text-on-surface-variant text-xs font-bold rounded-full hover:bg-primary-container transition-colors" type="button">Profanity</button>
                  <button className="px-3 py-1.5 bg-surface-container-high text-on-surface-variant text-xs font-bold rounded-full hover:bg-primary-container transition-colors" type="button">Scam</button>
                </div>
              </div>
              <div className="pt-4">
                <button className="w-full bg-gradient-to-r from-primary to-primary-dim text-white font-bold py-3 rounded-xl shadow-lg shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all">
                  Add to Watchlist
                </button>
              </div>
            </form>
          </div>
          {/* Mini Stats (Floating effect) */}
          <div className="bg-cyan-600 rounded-xl p-6 text-white relative overflow-hidden group">
            <div className="relative z-10">
              <div className="text-cyan-200 text-xs font-bold uppercase tracking-widest mb-1">System Health</div>
              <div className="text-3xl font-extrabold mb-4 tracking-tight">Active Coverage</div>
              <div className="flex items-end justify-between">
                <span className="text-5xl font-black">94%</span>
                <span className="material-symbols-outlined text-4xl opacity-50 group-hover:scale-110 transition-transform">auto_awesome</span>
              </div>
            </div>
            <div className="absolute -right-4 -bottom-4 w-24 h-24 bg-white/10 rounded-full blur-2xl"></div>
          </div>
        </div>
        {/* Main Table Area */}
        <div className="lg:col-span-8 flex flex-col gap-6">
          {/* Toolbar */}
          <div className="bg-surface-container-low rounded-xl p-3 flex flex-col sm:flex-row items-center justify-between gap-4">
            <div className="relative w-full sm:w-72">
              <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline-variant">search</span>
              <input className="w-full bg-white border-none rounded-lg pl-10 pr-4 py-2 text-sm focus:ring-1 focus:ring-primary shadow-sm" placeholder="Filter current list..." type="text" />
            </div>
            <div className="flex items-center gap-2 w-full sm:w-auto">
              <span className="text-xs font-bold text-slate-400 uppercase tracking-tight whitespace-nowrap px-2">Bulk Actions:</span>
              <div className="flex bg-white rounded-lg p-1 shadow-sm gap-1">
                <button className="p-1.5 text-secondary hover:bg-secondary-container/30 rounded transition-colors" title="Enable All">
                  <span className="material-symbols-outlined">check_circle</span>
                </button>
                <button className="p-1.5 text-tertiary hover:bg-tertiary-container/30 rounded transition-colors" title="Disable All">
                  <span className="material-symbols-outlined">block</span>
                </button>
                <button className="p-1.5 text-error hover:bg-error-container/30 rounded transition-colors" title="Delete All">
                  <span className="material-symbols-outlined">delete</span>
                </button>
              </div>
            </div>
          </div>
          {/* Table */}
          <div className="bg-white rounded-xl shadow-[0px_10px_40px_rgba(44,47,49,0.06)] overflow-hidden">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-50/50">
                  <th className="px-6 py-4 text-xs font-black text-slate-400 uppercase tracking-widest">
                    <input className="rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" />
                  </th>
                  <th className="px-6 py-4 text-xs font-black text-slate-400 uppercase tracking-widest">Restricted Term</th>
                  <th className="px-6 py-4 text-xs font-black text-slate-400 uppercase tracking-widest">Risk Level</th>
                  <th className="px-6 py-4 text-xs font-black text-slate-400 uppercase tracking-widest">Status</th>
                  <th className="px-6 py-4 text-xs font-black text-slate-400 uppercase tracking-widest text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {termsData.map((item) => (
                  <tr key={item.id} className="hover:bg-slate-50 transition-colors group">
                    <td className="px-6 py-4">
                      <input className="rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" />
                    </td>
                    <td className="px-6 py-4">
                      <div className={`font-bold text-slate-900 ${item.obsolete ? 'text-opacity-50 line-through' : ''}`}>{item.term}</div>
                      <div className="text-[10px] text-slate-400 font-medium">Added {item.time} by {item.addedBy}</div>
                    </td>
                    <td className={`px-6 py-4 ${item.obsolete ? 'opacity-50' : ''}`}>
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold ${item.riskClass}`}>
                        {item.risk}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <span className={`flex items-center gap-1.5 text-xs font-bold ${item.statusClass}`}>
                        <span className={`w-1.5 h-1.5 rounded-full ${item.statusDotClass}`}></span>
                        {item.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-right">
                      <button className="p-2 text-slate-400 hover:text-primary transition-colors">
                        <span className="material-symbols-outlined">edit</span>
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="px-6 py-4 bg-slate-50/30 flex items-center justify-between border-t border-slate-100">
              <span className="text-xs font-medium text-slate-500">Showing 3 of 1,248 terms</span>
              <div className="flex gap-2">
                <button className="p-1.5 rounded-lg border border-slate-200 hover:bg-white transition-colors">
                  <span className="material-symbols-outlined text-sm">chevron_left</span>
                </button>
                <button className="p-1.5 rounded-lg border border-slate-200 bg-white shadow-sm font-bold text-xs px-3">1</button>
                <button className="p-1.5 rounded-lg border border-slate-200 hover:bg-white transition-colors font-medium text-xs px-3 text-slate-500">2</button>
                <button className="p-1.5 rounded-lg border border-slate-200 hover:bg-white transition-colors">
                  <span className="material-symbols-outlined text-sm">chevron_right</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
