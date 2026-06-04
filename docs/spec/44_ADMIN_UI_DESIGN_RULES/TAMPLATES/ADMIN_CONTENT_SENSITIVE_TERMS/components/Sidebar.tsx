import React from 'react';

export function Sidebar() {
  return (
    <aside className="hidden md:flex flex-col h-screen w-64 bg-slate-50 sticky top-0 p-4 gap-2 z-40">
      <div className="flex items-center gap-3 px-2 mb-8">
        <div className="w-10 h-10 bg-primary-container flex items-center justify-center rounded-lg shadow-sm">
          <span className="material-symbols-outlined text-on-primary-container">shield</span>
        </div>
        <div>
          <div className="text-lg font-black text-slate-900 leading-tight">Moderation Engine</div>
          <div className="text-[10px] font-medium text-slate-500 uppercase tracking-widest">Safety & Compliance</div>
        </div>
      </div>
      <nav className="space-y-1">
        <a className="flex items-center gap-3 px-3 py-2.5 text-slate-600 hover:bg-cyan-50 transition-all active:translate-x-1 duration-150 font-medium text-sm" href="#">
          <span className="material-symbols-outlined">dashboard</span>
          <span>Dashboard</span>
        </a>
        <a className="flex items-center gap-3 px-3 py-2.5 text-slate-600 hover:bg-cyan-50 transition-all active:translate-x-1 duration-150 font-medium text-sm" href="#">
          <span className="material-symbols-outlined">flag</span>
          <span>Reports</span>
        </a>
        <a className="flex items-center gap-3 px-3 py-2.5 text-slate-600 hover:bg-cyan-50 transition-all active:translate-x-1 duration-150 font-medium text-sm" href="#">
          <span className="material-symbols-outlined">rate_review</span>
          <span>Review Queue</span>
        </a>
        <a className="flex items-center gap-3 px-3 py-2.5 bg-white text-cyan-600 shadow-sm rounded-lg transition-all active:translate-x-1 duration-150 font-medium text-sm" href="#">
          <span className="material-symbols-outlined">spellcheck</span>
          <span>Sensitive Words</span>
        </a>
        <a className="flex items-center gap-3 px-3 py-2.5 text-slate-600 hover:bg-cyan-50 transition-all active:translate-x-1 duration-150 font-medium text-sm" href="#">
          <span className="material-symbols-outlined">history_edu</span>
          <span>Audit Logs</span>
        </a>
      </nav>
      <div className="mt-auto">
        <button className="w-full flex items-center justify-center gap-2 px-4 py-3 bg-gradient-to-br from-primary to-primary-dim text-white rounded-xl font-semibold shadow-lg hover:shadow-primary/20 transition-all active:scale-95">
          <span className="material-symbols-outlined text-sm">priority_high</span>
          <span>Urgent Reviews</span>
        </button>
      </div>
    </aside>
  );
}
