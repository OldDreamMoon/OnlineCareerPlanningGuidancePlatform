/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import {
  Shield,
  LayoutDashboard,
  Flag,
  ListChecks,
  Type,
  ScrollText,
  Search,
  Bell,
  HelpCircle,
  SlidersHorizontal,
  Download,
  ChevronRight,
  MessageSquare,
} from 'lucide-react';

export default function App() {
  return (
    <div className="flex h-screen overflow-hidden bg-surface text-on-surface font-body">
      <Sidebar />
      <main className="flex-1 flex flex-col min-w-0 overflow-hidden relative">
        <TopNav />
        <div className="flex-1 flex overflow-hidden">
          <MainContent />
          <ReportDetails />
        </div>
      </main>
    </div>
  );
}

function Sidebar() {
  return (
    <aside className="h-screen w-64 bg-slate-50 flex flex-col p-4 gap-2 shrink-0">
      <div className="flex items-center gap-3 px-2 mb-8">
        <div className="w-8 h-8 bg-primary rounded-lg flex items-center justify-center text-white">
          <Shield className="w-5 h-5" />
        </div>
        <div>
          <h1 className="text-lg font-black text-slate-900 leading-tight">
            Moderation Engine
          </h1>
          <p className="text-[10px] uppercase tracking-widest text-slate-500 font-bold">
            Safety & Compliance
          </p>
        </div>
      </div>

      <nav className="flex-1 flex flex-col gap-1">
        <a
          href="#"
          className="flex items-center gap-3 px-3 py-2.5 text-slate-600 font-medium text-sm hover:bg-cyan-50 transition-all active:translate-x-1 duration-150 rounded-lg"
        >
          <LayoutDashboard className="w-5 h-5" />
          Dashboard
        </a>
        <a
          href="#"
          className="flex items-center gap-3 px-3 py-2.5 bg-white text-cyan-600 shadow-sm rounded-lg font-medium text-sm active:translate-x-1 duration-150"
        >
          <Flag className="w-5 h-5 fill-current" />
          Reports
        </a>
        <a
          href="#"
          className="flex items-center gap-3 px-3 py-2.5 text-slate-600 font-medium text-sm hover:bg-cyan-50 transition-all active:translate-x-1 duration-150 rounded-lg"
        >
          <ListChecks className="w-5 h-5" />
          Review Queue
        </a>
        <a
          href="#"
          className="flex items-center gap-3 px-3 py-2.5 text-slate-600 font-medium text-sm hover:bg-cyan-50 transition-all active:translate-x-1 duration-150 rounded-lg"
        >
          <Type className="w-5 h-5" />
          Sensitive Words
        </a>
        <a
          href="#"
          className="flex items-center gap-3 px-3 py-2.5 text-slate-600 font-medium text-sm hover:bg-cyan-50 transition-all active:translate-x-1 duration-150 rounded-lg"
        >
          <ScrollText className="w-5 h-5" />
          Audit Logs
        </a>
      </nav>

      <div className="mt-auto p-4 rounded-xl bg-primary/5 border border-primary/10">
        <p className="text-xs font-bold text-primary mb-2">Urgent Reviews</p>
        <div className="flex items-center justify-between text-[11px] text-slate-500">
          <span>Pending: 124</span>
          <span className="w-1.5 h-1.5 rounded-full bg-error animate-pulse"></span>
        </div>
      </div>
    </aside>
  );
}

function TopNav() {
  return (
    <header className="w-full sticky top-0 z-50 bg-white/70 backdrop-blur-xl flex justify-between items-center px-6 py-3 shadow-[0px_10px_40px_rgba(44,47,49,0.06)]">
      <div className="flex items-center gap-6">
        <span className="text-xl font-bold tracking-tighter text-cyan-600 font-headline">
          Luminous Mod
        </span>
        <div className="relative group">
          <span className="absolute inset-y-0 left-3 flex items-center text-slate-400">
            <Search className="w-4 h-4" />
          </span>
          <input
            type="text"
            placeholder="Search report ID..."
            className="pl-10 pr-4 py-1.5 bg-slate-100/50 border-none rounded-full text-xs w-64 focus:ring-2 focus:ring-primary/20 transition-all outline-none"
          />
        </div>
      </div>

      <div className="flex items-center gap-4">
        <button className="w-10 h-10 flex items-center justify-center rounded-full hover:bg-slate-50 transition-colors text-slate-500 active:scale-95 duration-200">
          <Bell className="w-5 h-5" />
        </button>
        <button className="w-10 h-10 flex items-center justify-center rounded-full hover:bg-slate-50 transition-colors text-slate-500 active:scale-95 duration-200">
          <HelpCircle className="w-5 h-5" />
        </button>
        <div className="h-8 w-[1px] bg-slate-200 mx-2"></div>
        <img
          src="https://lh3.googleusercontent.com/aida-public/AB6AXuDq2o1aI6_LpmRikI6a7N55BfR8WDIZX82JK4-BTo3u1y_ofs5UWuPmZf86Agr-f7tAeFN4NhTODVcDwQ3_dR_GGqanQVZuw3TvbmhhHakmRXuVooeKY5hNc4z3p5BSPOx55waCVynL1fSXzEmr2K952MP9oXhVuORe-M_kj2YeRz6cZ16YA5ynv4lgRLRzJlsIP6kUrNGTWyBLPHvsR-qc6IS9EMXyVEIS7GIjcu9ZIteFINs5iR9sF23DS4QBLFCMNQ7hkSWyFDY"
          alt="Moderator Profile Avatar"
          className="w-8 h-8 rounded-full object-cover ring-2 ring-white shadow-sm"
        />
      </div>
    </header>
  );
}

function MainContent() {
  return (
    <div className="flex-1 overflow-y-auto p-8 space-y-8 no-scrollbar">
      {/* Header Section */}
      <div className="flex items-end justify-between">
        <div>
          <h2 className="text-4xl font-extrabold font-headline tracking-tight text-on-surface">
            Report Center
          </h2>
          <p className="text-on-surface-variant mt-1 font-body">
            Manage and review user-generated reports across the platform.
          </p>
        </div>
        <div className="flex gap-2">
          <div className="flex items-center gap-1 bg-surface-container-low px-3 py-2 rounded-lg text-xs font-semibold text-on-surface-variant cursor-pointer hover:bg-surface-container transition-colors">
            <SlidersHorizontal className="w-4 h-4" />
            Filter
          </div>
          <div className="flex items-center gap-1 bg-primary text-white px-4 py-2 rounded-lg text-xs font-bold shadow-md hover:shadow-lg transition-all cursor-pointer">
            <Download className="w-4 h-4" />
            Export CSV
          </div>
        </div>
      </div>

      {/* Filter Tools Bento */}
      <div className="grid grid-cols-12 gap-4">
        <div className="col-span-3 p-4 bg-surface-container-lowest rounded-xl shadow-sm space-y-2">
          <label className="text-[10px] font-bold text-outline-variant uppercase tracking-widest block">
            Report ID
          </label>
          <input
            type="text"
            placeholder="REP-7829..."
            className="w-full bg-surface-container-low border-none rounded-lg text-sm focus:ring-2 focus:ring-primary outline-none px-3 py-2"
          />
        </div>
        <div className="col-span-3 p-4 bg-surface-container-lowest rounded-xl shadow-sm space-y-2">
          <label className="text-[10px] font-bold text-outline-variant uppercase tracking-widest block">
            Status
          </label>
          <select className="w-full bg-surface-container-low border-none rounded-lg text-sm focus:ring-2 focus:ring-primary outline-none px-3 py-2 appearance-none">
            <option>All Pending</option>
            <option>In Review</option>
            <option>Escalated</option>
          </select>
        </div>
        <div className="col-span-3 p-4 bg-surface-container-lowest rounded-xl shadow-sm space-y-2">
          <label className="text-[10px] font-bold text-outline-variant uppercase tracking-widest block">
            Target Type
          </label>
          <select className="w-full bg-surface-container-low border-none rounded-lg text-sm focus:ring-2 focus:ring-primary outline-none px-3 py-2 appearance-none">
            <option>User Profiles</option>
            <option>Comments</option>
            <option>Media/Post</option>
          </select>
        </div>
        <div className="col-span-3 p-4 bg-surface-container-lowest rounded-xl shadow-sm space-y-2 flex flex-col justify-end">
          <button className="w-full h-[38px] bg-surface-container text-on-surface font-bold text-xs rounded-lg hover:bg-surface-variant transition-colors">
            Clear All Filters
          </button>
        </div>
      </div>

      {/* Report Table */}
      <div className="bg-surface-container-lowest rounded-2xl shadow-[0px_10px_40px_rgba(44,47,49,0.06)] overflow-hidden">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-surface-container-low">
              <th className="px-6 py-4 text-[10px] font-bold text-outline-variant uppercase tracking-widest">
                Target
              </th>
              <th className="px-6 py-4 text-[10px] font-bold text-outline-variant uppercase tracking-widest">
                Reason
              </th>
              <th className="px-6 py-4 text-[10px] font-bold text-outline-variant uppercase tracking-widest text-center">
                Reports
              </th>
              <th className="px-6 py-4 text-[10px] font-bold text-outline-variant uppercase tracking-widest">
                Status
              </th>
              <th className="px-6 py-4 text-[10px] font-bold text-outline-variant uppercase tracking-widest text-right">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-surface-container">
            {/* Row 1 */}
            <tr className="hover:bg-primary/5 transition-colors group cursor-pointer border-l-4 border-transparent hover:border-primary">
              <td className="px-6 py-5">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-lg bg-surface-container overflow-hidden shrink-0">
                    <img
                      src="https://lh3.googleusercontent.com/aida-public/AB6AXuAP8iEEsOkuThYuLm6sT6JCseQkGVpkt0BSnujwRB0fwdORf7oCf8XMdgdEXcxEf3YiaUmV5iihpjroIPxT_kUwZlx_Qz8Z8vR9vjFxHrMVMjOcpdaP7CPRwi8F9iVVSIv7CYs5cL5vmC81Q4DFGYDWveCltwDv2DZjRcBZf7HosEKvXG2v3F4rwS1GWLEQAx5tmbMh-I1ucDIFCYvqZ8zQ9St5h2TIlHoBnN8mXeTcnDnyhFfSWksUjf61kAVfkD5MINEMxM-TRI0"
                      alt="Reported Content"
                      className="w-full h-full object-cover"
                    />
                  </div>
                  <div>
                    <div className="text-sm font-bold text-on-surface">
                      Post #88219
                    </div>
                    <div className="text-xs text-on-surface-variant">
                      by @urban_explorer
                    </div>
                  </div>
                </div>
              </td>
              <td className="px-6 py-5">
                <span className="text-xs font-semibold px-2 py-1 rounded bg-error-container/20 text-error-dim">
                  Harassment
                </span>
              </td>
              <td className="px-6 py-5 text-center">
                <div className="inline-flex flex-col">
                  <span className="text-sm font-bold text-on-surface">12</span>
                  <span className="text-[10px] text-outline-variant">Total</span>
                </div>
              </td>
              <td className="px-6 py-5">
                <div className="flex items-center gap-2">
                  <span className="w-2 h-2 rounded-full bg-tertiary"></span>
                  <span className="text-xs font-medium">In Review</span>
                </div>
              </td>
              <td className="px-6 py-5 text-right">
                <button className="p-2 text-on-surface-variant hover:text-primary transition-colors">
                  <ChevronRight className="w-5 h-5" />
                </button>
              </td>
            </tr>

            {/* Row 2 */}
            <tr className="hover:bg-primary/5 transition-colors group cursor-pointer border-l-4 border-transparent hover:border-primary">
              <td className="px-6 py-5">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-lg bg-surface-container overflow-hidden shrink-0">
                    <img
                      src="https://lh3.googleusercontent.com/aida-public/AB6AXuAYaiuWf8ujHpB-cuddVrjdvT3jZmztA7xDeiKaTo3RRGmQFrzrodpex74GpGoXHIA-rI3CJXFXmo3M_7jpOwD_uMT113gIQX4yxFmXL1gEVQvz5EkG9ypB_7ii5w5VYm0t7vuDBA63daPx54r1AvYSnPMBfSZdXIiZgO_kp5o532RbsNcffZJKVysjz2H-zSJUaQDtGqryZs_u2xoMf2WPTB9nArpUmH5aQ5nDCcxhJWSVbUR39MUyrHlMkifOCftQ5eK61wDez78"
                      alt="Reported Profile"
                      className="w-full h-full object-cover"
                    />
                  </div>
                  <div>
                    <div className="text-sm font-bold text-on-surface">
                      User: j_doe_99
                    </div>
                    <div className="text-xs text-on-surface-variant">
                      Joined 2 days ago
                    </div>
                  </div>
                </div>
              </td>
              <td className="px-6 py-5">
                <span className="text-xs font-semibold px-2 py-1 rounded bg-tertiary-container/20 text-tertiary">
                  Spam
                </span>
              </td>
              <td className="px-6 py-5 text-center">
                <div className="inline-flex flex-col">
                  <span className="text-sm font-bold text-on-surface">45</span>
                  <span className="text-[10px] text-outline-variant text-error font-bold">
                    Rising
                  </span>
                </div>
              </td>
              <td className="px-6 py-5">
                <div className="flex items-center gap-2">
                  <span className="w-2 h-2 rounded-full bg-error animate-pulse"></span>
                  <span className="text-xs font-medium text-error">Critical</span>
                </div>
              </td>
              <td className="px-6 py-5 text-right">
                <button className="p-2 text-on-surface-variant hover:text-primary transition-colors">
                  <ChevronRight className="w-5 h-5" />
                </button>
              </td>
            </tr>

            {/* Row 3 */}
            <tr className="hover:bg-primary/5 transition-colors group cursor-pointer border-l-4 border-transparent hover:border-primary">
              <td className="px-6 py-5">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-lg bg-surface-container flex items-center justify-center shrink-0">
                    <MessageSquare className="w-5 h-5 text-outline" />
                  </div>
                  <div>
                    <div className="text-sm font-bold text-on-surface">
                      Comment ID: 5543
                    </div>
                    <div className="text-xs text-on-surface-variant">
                      Thread: "New Tech..."
                    </div>
                  </div>
                </div>
              </td>
              <td className="px-6 py-5">
                <span className="text-xs font-semibold px-2 py-1 rounded bg-outline-variant/10 text-outline">
                  Misinfo
                </span>
              </td>
              <td className="px-6 py-5 text-center">
                <div className="inline-flex flex-col">
                  <span className="text-sm font-bold text-on-surface">3</span>
                  <span className="text-[10px] text-outline-variant">Total</span>
                </div>
              </td>
              <td className="px-6 py-5">
                <div className="flex items-center gap-2">
                  <span className="w-2 h-2 rounded-full bg-outline-variant"></span>
                  <span className="text-xs font-medium">Pending</span>
                </div>
              </td>
              <td className="px-6 py-5 text-right">
                <button className="p-2 text-on-surface-variant hover:text-primary transition-colors">
                  <ChevronRight className="w-5 h-5" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>

        <div className="px-6 py-4 bg-surface-container-low flex justify-between items-center text-xs font-medium text-on-surface-variant">
          <span>Showing 1-3 of 124 reports</span>
          <div className="flex gap-1">
            <button className="px-3 py-1 bg-white rounded border border-surface-variant">
              1
            </button>
            <button className="px-3 py-1 hover:bg-white rounded transition-colors">
              2
            </button>
            <button className="px-3 py-1 hover:bg-white rounded transition-colors">
              3
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function ReportDetails() {
  return (
    <div className="w-96 bg-white border-l border-surface-container flex flex-col shrink-0">
      <div className="p-6 border-b border-surface-container flex justify-between items-center bg-surface-container-low/30">
        <h3 className="font-headline font-bold text-lg">Report Details</h3>
        <span className="text-[10px] bg-error text-white px-2 py-0.5 rounded-full font-bold">
          REP-7829
        </span>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-6 no-scrollbar">
        {/* Context Section */}
        <div className="space-y-3">
          <p className="text-[10px] font-black uppercase text-outline-variant tracking-widest">
            Context
          </p>
          <div className="bg-surface-container-low rounded-xl p-4 italic text-sm text-on-surface-variant border-l-4 border-primary">
            "This user has been repeatedly posting inflammatory content in the
            general discussion thread for the past two hours. It clearly violates
            our community guidelines regarding targeted harassment."
          </div>
        </div>

        {/* Content Preview */}
        <div className="space-y-3">
          <p className="text-[10px] font-black uppercase text-outline-variant tracking-widest">
            Targeted Content
          </p>
          <div className="rounded-xl overflow-hidden shadow-sm">
            <img
              src="https://lh3.googleusercontent.com/aida-public/AB6AXuCfLnJ3Rbsboi-DyvA2Zdchh3xLmOTX1M2ZGFX7Hq9YVwyQ-4bO2SXgSf0nXGcyEl9oSt1Uw58x0LQy6EY0XVPMFchEyIr0lF-L6i6HqTgBATOjsDGudGABsRvbHKQntEzmcNWi8UYUQ8WBdcTpE21OoSX-vXiMyzE86JY8M6BXxXR08LSv85YRd9Q7GoS5XKRVr06MsMnpwT3CSySkAN1QsZ6SqoZL_c4nJG4dyo2hp_bP9DH4bQk0qg0eaPpxO7c-1E2RQQoO0Lk"
              alt="Full Size Context"
              className="w-full aspect-video object-cover"
            />
          </div>
        </div>

        {/* Timeline */}
        <div className="space-y-4">
          <p className="text-[10px] font-black uppercase text-outline-variant tracking-widest">
            History Timeline
          </p>
          <div className="space-y-4 relative before:absolute before:left-2 before:top-2 before:bottom-2 before:w-[2px] before:bg-surface-container">
            <div className="relative pl-8">
              <span className="absolute left-0 w-4 h-4 bg-primary rounded-full border-4 border-white"></span>
              <p className="text-xs font-bold">Reported Created</p>
              <p className="text-[10px] text-outline-variant">
                Oct 24, 10:20 AM
              </p>
            </div>
            <div className="relative pl-8">
              <span className="absolute left-0 w-4 h-4 bg-tertiary rounded-full border-4 border-white"></span>
              <p className="text-xs font-bold">Auto-Flagged for Review</p>
              <p className="text-[10px] text-outline-variant">
                Oct 24, 10:21 AM
              </p>
            </div>
            <div className="relative pl-8">
              <span className="absolute left-0 w-4 h-4 bg-outline-variant rounded-full border-4 border-white"></span>
              <p className="text-xs font-bold">Assigned to Moderator</p>
              <p className="text-[10px] text-outline-variant">
                Oct 24, 11:05 AM
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Actions */}
      <div className="p-6 bg-surface-container-low/50 grid grid-cols-2 gap-3">
        <button className="px-4 py-2 bg-surface-container-lowest text-on-surface font-bold text-xs rounded-lg border border-surface-variant hover:bg-surface-container-low transition-all shadow-sm">
          Close
        </button>
        <button className="px-4 py-2 bg-secondary text-white font-bold text-xs rounded-lg hover:bg-secondary-dim transition-all shadow-md">
          Accept
        </button>
        <button className="px-4 py-2 bg-error text-white font-bold text-xs rounded-lg hover:bg-error-dim transition-all shadow-md">
          Take Down
        </button>
        <button className="px-4 py-2 bg-surface-container text-on-surface-variant font-bold text-xs rounded-lg hover:bg-surface-variant transition-all">
          Reject
        </button>
      </div>
    </div>
  );
}
