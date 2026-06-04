/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import {
  Search,
  Bell,
  Settings,
  BadgeCheck,
  Monitor,
  Users,
  Megaphone,
  HelpCircle,
  LogOut,
  Plus,
  Clock,
  CheckCircle2,
  Timer,
  Filter,
  XCircle,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';

const mockData = [
  {
    id: 'SUB-88219',
    user: {
      name: 'Alex Rivera',
      email: 'arivera@corp.com',
      avatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuCbWTJ6eDpywiBmO_VjcdLfO86PCtoPv-NDm9hU79pRyp7-83Zm07p7Ddko7CHlHtdu0E4EzKx9IvubsvewD_qf5Gpq5tSIIJs9rM2Oi0ahtQzvfck_rMA3ap50JjeW--OdDqM6ZayjpeUJEfNN_3tPrJpVjk-mU1T9KdergZQ0lsT4Jchg-5sKY-umadHuoygsGoUcf3vD7qZKB4-GEL2mAEv3wc34EMPqcgNchogu8_eKXQWXsg4VPF4JEJYOyral7obGHPUGzUk'
    },
    role: 'Cloud Architect',
    status: 'PENDING',
    entity: { name: 'Zenith Infotech Ltd.', tier: 'Enterprise Tier' },
    assets: [{ type: 'PDF', color: 'blue' }, { type: 'JPG', color: 'green' }, { type: '+2', color: 'slate' }],
    time: { relative: '2 hours ago', exact: 'Oct 24, 14:32' },
    action: 'Review Assets'
  },
  {
    id: 'SUB-87502',
    user: {
      name: 'Sarah Jenkins',
      email: 's.jenkins@globex.io',
      avatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuCY14soL2pgdcLFkB7Ugw5-xYprc1F7N-dtM80H7O16NrMd4iEAxzSsbMM4oxB71vyS2VZVRzhnrSpGJ3HZPXgYLyvHbC9Y_-r1L-90jhIypgPHwrTXnxPS4Hmd6hfqBFMcNRhxyGXd19fX8XhnLNZLBsBcLt-8MLRheZc-cPC_3aQHhlSDC6buqBHPmbQ1SUwBaCy1pyNsC7eJaXI0Jeu5NSLD4kuh0MgfkCollUrCremqviRBMujDQrAJYIpMcuQhx1Zy-ut_YrE'
    },
    role: 'Lead Data Scientist',
    status: 'APPROVED',
    entity: { name: 'Globex Corporation', tier: 'Global Partner' },
    assets: [{ type: '8 Assets', color: 'slate', isPill: true }],
    time: { relative: '5 hours ago', exact: 'Oct 24, 11:15' },
    action: 'View Report'
  },
  {
    id: 'SUB-89112',
    user: {
      name: 'Michael Chen',
      email: 'mchen@nexus.tech',
      avatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuCrrxgmA6ACIxZO707ezUxeaxo34XlgcWrdyZxA2vYJpVk1Qju0CGrriNBfLoau43kM6oKunIDvYuxUj7vJl-aCr4KcajyzIiOtf2kKOJ8wrHdI9aWTgQa5Z8vyJ4_ntiLNew4GCUaxIXNptH79U6uFPNqbzK17k-YTmbqMSD-jJ0DwmrTm_H7yL9R9SPcuh9XUhfbT9s-vQg5j2Q7BMpw82Ic-bholsDCtg2sP0JAZOmg6N1Vs1aK7E7zokVnq5DVzJS0Kt8p3CWw'
    },
    role: 'Security Engineer',
    status: 'PENDING',
    entity: { name: 'Nexus Technologies', tier: 'Start-up Program' },
    assets: [{ type: 'PDF', color: 'blue' }, { type: '+1', color: 'slate' }],
    time: { relative: '8 hours ago', exact: 'Oct 24, 08:45' },
    action: 'Review Assets'
  },
  {
    id: 'SUB-87441',
    user: {
      name: 'Elena Rodriguez',
      email: 'e.rodriguez@vision.co',
      avatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuD30mJeGvVjYIl3rDNifGoJe6rwSX8olNOIPBdlOr5niRg1KcowpQGEOngGm39N9jbB4G0iZ5rYUsb8_2Tr6QjTMIyVNFLe-R9wCzZ-GdNxl43R_NfPULql8LTRM0s7mCsSLw0PlW9LrCsCfi4uv0LNMQ1zlAAxd5fteqSGFpfjviDy-B_C5_cilmREJmJADEkQyK76NbCtb7nFGjzQxkQQXAB_POZPh4g0NTFv0IySv5h0sHG_J-S3c7rrBqySuKXiHs_TKegFbyc'
    },
    role: 'Product Designer',
    status: 'REJECTED',
    entity: { name: 'Visionary Studio', tier: 'Individual Creator' },
    assets: [{ type: '12 Assets', color: 'slate', isPill: true }],
    time: { relative: '1 day ago', exact: 'Oct 23, 16:20' },
    action: 'See Rejection'
  }
];

export default function App() {
  return (
    <div className="min-h-screen bg-[#f5f7f9] text-slate-800 font-sans selection:bg-indigo-100">
      {/* Top Navigation Bar */}
      <nav className="fixed top-0 w-full z-50 bg-white/70 backdrop-blur-xl h-16 px-6 flex justify-between items-center shadow-sm">
        <div className="flex items-center gap-8">
          <span className="text-xl font-bold tracking-tight text-indigo-600">Luminous Admin</span>
          <div className="hidden md:flex gap-6 items-center">
            <a href="#" className="text-indigo-600 font-semibold border-b-2 border-indigo-600 px-1 py-4">Workspaces</a>
            <a href="#" className="text-slate-500 hover:text-slate-800 transition-colors px-1 py-4">Mentors</a>
            <a href="#" className="text-slate-500 hover:text-slate-800 transition-colors px-1 py-4">Certifications</a>
          </div>
        </div>
        <div className="flex items-center gap-4">
          <div className="relative hidden sm:block">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 w-4 h-4" />
            <input
              type="text"
              placeholder="Search..."
              className="bg-slate-100/80 border-none rounded-full pl-10 pr-4 py-2 text-sm focus:ring-2 focus:ring-indigo-500/20 w-64 outline-none transition-all"
            />
          </div>
          <button className="text-slate-500 hover:bg-slate-100 p-2 rounded-full transition-colors">
            <Bell className="w-5 h-5" />
          </button>
          <button className="text-slate-500 hover:bg-slate-100 p-2 rounded-full transition-colors">
            <Settings className="w-5 h-5" />
          </button>
          <div className="h-8 w-8 rounded-full overflow-hidden border-2 border-indigo-100">
            <img
              src="https://lh3.googleusercontent.com/aida-public/AB6AXuCbWTJ6eDpywiBmO_VjcdLfO86PCtoPv-NDm9hU79pRyp7-83Zm07p7Ddko7CHlHtdu0E4EzKx9IvubsvewD_qf5Gpq5tSIIJs9rM2Oi0ahtQzvfck_rMA3ap50JjeW--OdDqM6ZayjpeUJEfNN_3tPrJpVjk-mU1T9KdergZQ0lsT4Jchg-5sKY-umadHuoygsGoUcf3vD7qZKB4-GEL2mAEv3wc34EMPqcgNchogu8_eKXQWXsg4VPF4JEJYOyral7obGHPUGzUk"
              alt="Admin"
              className="w-full h-full object-cover"
            />
          </div>
        </div>
      </nav>

      <div className="flex pt-16 min-h-screen">
        {/* Side Navigation Bar */}
        <aside className="w-64 bg-slate-50 flex-col py-6 px-4 sticky top-16 h-[calc(100vh-4rem)] hidden lg:flex">
          <div className="mb-8 px-2">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-indigo-600 flex items-center justify-center text-white shadow-md shadow-indigo-200">
                <BadgeCheck className="w-6 h-6" />
              </div>
              <div>
                <h2 className="text-sm font-bold text-slate-900 leading-tight">Core Operations</h2>
                <p className="text-xs text-slate-500 font-medium">Management Suite</p>
              </div>
            </div>
          </div>

          <nav className="flex-1 space-y-1.5">
            <a href="#" className="flex items-center gap-3 px-3 py-2.5 text-slate-600 hover:bg-slate-100 hover:translate-x-1 transition-all rounded-xl">
              <BadgeCheck className="w-5 h-5" />
              <span className="font-medium text-sm">Certifications</span>
            </a>
            <a href="#" className="flex items-center gap-3 px-3 py-2.5 bg-indigo-50 text-indigo-700 rounded-xl transition-all">
              <Monitor className="w-5 h-5" />
              <span className="font-medium text-sm">Workspaces</span>
            </a>
            <a href="#" className="flex items-center gap-3 px-3 py-2.5 text-slate-600 hover:bg-slate-100 hover:translate-x-1 transition-all rounded-xl">
              <Users className="w-5 h-5" />
              <span className="font-medium text-sm">Mentors</span>
            </a>
            <a href="#" className="flex items-center gap-3 px-3 py-2.5 text-slate-600 hover:bg-slate-100 hover:translate-x-1 transition-all rounded-xl">
              <Megaphone className="w-5 h-5" />
              <span className="font-medium text-sm">Notifications</span>
            </a>
          </nav>

          <div className="mt-auto pt-4 space-y-1.5">
            <a href="#" className="flex items-center gap-3 px-3 py-2.5 text-slate-600 hover:bg-slate-100 hover:translate-x-1 transition-all rounded-xl">
              <HelpCircle className="w-5 h-5" />
              <span className="font-medium text-sm">Support</span>
            </a>
            <a href="#" className="flex items-center gap-3 px-3 py-2.5 text-slate-600 hover:bg-slate-100 hover:translate-x-1 transition-all rounded-xl">
              <LogOut className="w-5 h-5" />
              <span className="font-medium text-sm">Logout</span>
            </a>
          </div>
        </aside>

        {/* Main Content Area */}
        <main className="flex-1 p-6 md:p-8 overflow-y-auto">
          {/* Header Section */}
          <div className="mb-10 flex flex-col md:flex-row md:items-end justify-between gap-6">
            <div>
              <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight text-slate-900 mb-3">
                Certification Review Workspace
              </h1>
              <p className="text-slate-500 max-w-2xl text-sm md:text-base leading-relaxed">
                Assess and validate incoming certification requests. Manage verification workflows, audit submission assets, and track progress across the professional network.
              </p>
            </div>
            <button className="bg-indigo-600 text-white px-6 py-3 rounded-full font-semibold flex items-center justify-center gap-2 shadow-lg shadow-indigo-600/20 hover:bg-indigo-700 hover:scale-[1.02] transition-all active:scale-95 whitespace-nowrap">
              <Plus className="w-5 h-5" />
              Create Review Task
            </button>
          </div>

          {/* Dashboard Overview Stats */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
            <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
              <div className="flex items-center justify-between mb-4">
                <div className="bg-indigo-50 text-indigo-600 p-2.5 rounded-xl">
                  <Clock className="w-5 h-5" />
                </div>
                <span className="text-xs font-bold text-indigo-700 px-2.5 py-1 bg-indigo-50 rounded-full">+12%</span>
              </div>
              <div className="text-3xl font-bold text-slate-900 mb-1">142</div>
              <div className="text-sm text-slate-500 font-medium">Pending Reviews</div>
            </div>

            <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
              <div className="flex items-center justify-between mb-4">
                <div className="bg-emerald-50 text-emerald-600 p-2.5 rounded-xl">
                  <CheckCircle2 className="w-5 h-5" />
                </div>
              </div>
              <div className="text-3xl font-bold text-slate-900 mb-1">1,284</div>
              <div className="text-sm text-slate-500 font-medium">Approved Today</div>
            </div>

            <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
              <div className="flex items-center justify-between mb-4">
                <div className="bg-amber-50 text-amber-600 p-2.5 rounded-xl">
                  <Timer className="w-5 h-5" />
                </div>
              </div>
              <div className="text-3xl font-bold text-slate-900 mb-1">4.2h</div>
              <div className="text-sm text-slate-500 font-medium">Avg. Response Time</div>
            </div>

            <div className="bg-indigo-600 text-white p-6 rounded-2xl shadow-xl shadow-indigo-600/20 relative overflow-hidden">
              <div className="relative z-10 flex flex-col h-full justify-between">
                <div>
                  <div className="text-sm font-medium text-indigo-100 mb-1">System Health</div>
                  <div className="text-2xl font-bold mb-4">Optimal</div>
                </div>
                <div>
                  <div className="w-full bg-indigo-900/30 h-1.5 rounded-full overflow-hidden mb-2">
                    <div className="bg-white h-full w-[94%] rounded-full"></div>
                  </div>
                  <div className="text-[10px] text-indigo-200 uppercase tracking-wider font-bold">Node: Indigo-041</div>
                </div>
              </div>
              <div className="absolute -right-8 -bottom-8 w-32 h-32 bg-white/10 rounded-full blur-2xl"></div>
              <div className="absolute -left-4 -top-4 w-24 h-24 bg-indigo-400/20 rounded-full blur-xl"></div>
            </div>
          </div>

          {/* Filter Toolbar */}
          <div className="bg-slate-100/50 p-3 rounded-2xl mb-6 flex flex-col lg:flex-row gap-3 items-center">
            <div className="relative flex-1 w-full">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400 w-4 h-4" />
              <input
                type="text"
                placeholder="Search by name, ID, or keyword..."
                className="w-full bg-white border-none rounded-xl pl-11 pr-4 py-3 text-sm focus:ring-2 focus:ring-indigo-500/20 outline-none shadow-sm"
              />
            </div>
            <div className="flex gap-3 w-full lg:w-auto">
              <select className="bg-white border-none rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-indigo-500/20 outline-none shadow-sm flex-1 lg:flex-none text-slate-600 font-medium appearance-none pr-10 cursor-pointer">
                <option>All Roles</option>
                <option>Senior Architect</option>
                <option>Data Scientist</option>
                <option>UX Lead</option>
              </select>
              <select className="bg-white border-none rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-indigo-500/20 outline-none shadow-sm flex-1 lg:flex-none text-slate-600 font-medium appearance-none pr-10 cursor-pointer">
                <option>All Status</option>
                <option>Pending</option>
                <option>Approved</option>
                <option>Rejected</option>
              </select>
              <button className="bg-white p-3 rounded-xl text-slate-500 hover:text-indigo-600 shadow-sm transition-colors flex-shrink-0">
                <Filter className="w-5 h-5" />
              </button>
            </div>
          </div>

          {/* Data Table */}
          <div className="bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-slate-50/50 border-b border-slate-100">
                    <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-slate-500">User Identity</th>
                    <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-slate-500">Role & ID</th>
                    <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-slate-500">Status</th>
                    <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-slate-500">Entity Details</th>
                    <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-slate-500">Assets</th>
                    <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-slate-500">Submission Time</th>
                    <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-slate-500 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {mockData.map((row, idx) => (
                    <tr key={idx} className="hover:bg-slate-50/80 transition-colors group">
                      <td className="px-6 py-5">
                        <div className="flex items-center gap-3">
                          <img src={row.user.avatar} alt={row.user.name} className="w-10 h-10 rounded-full object-cover border border-slate-200" />
                          <div>
                            <div className="text-sm font-bold text-slate-900">{row.user.name}</div>
                            <div className="text-xs text-slate-500">{row.user.email}</div>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-5">
                        <div className="text-sm font-semibold text-slate-900">{row.role}</div>
                        <div className="text-xs font-mono text-slate-400 mt-0.5">{row.id}</div>
                      </td>
                      <td className="px-6 py-5">
                        {row.status === 'PENDING' && (
                          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-amber-100 text-amber-700">
                            <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse"></span>
                            PENDING
                          </span>
                        )}
                        {row.status === 'APPROVED' && (
                          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-emerald-100 text-emerald-700">
                            <CheckCircle2 className="w-3.5 h-3.5" />
                            APPROVED
                          </span>
                        )}
                        {row.status === 'REJECTED' && (
                          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-rose-100 text-rose-700">
                            <XCircle className="w-3.5 h-3.5" />
                            REJECTED
                          </span>
                        )}
                      </td>
                      <td className="px-6 py-5">
                        <div className="text-sm font-medium text-slate-900">{row.entity.name}</div>
                        <div className="text-xs text-slate-500 mt-0.5">{row.entity.tier}</div>
                      </td>
                      <td className="px-6 py-5">
                        <div className="flex -space-x-2">
                          {row.assets.map((asset, i) => (
                            asset.isPill ? (
                              <div key={i} className="text-xs font-bold text-slate-600 bg-slate-100 px-2.5 py-1 rounded-md border border-slate-200">
                                {asset.type}
                              </div>
                            ) : (
                              <div key={i} className={`w-8 h-8 rounded-lg border-2 border-white flex items-center justify-center text-[10px] font-bold z-${10-i}
                                ${asset.color === 'blue' ? 'bg-blue-100 text-blue-600' : 
                                  asset.color === 'green' ? 'bg-emerald-100 text-emerald-600' : 
                                  'bg-slate-100 text-slate-600'}`}>
                                {asset.type}
                              </div>
                            )
                          ))}
                        </div>
                      </td>
                      <td className="px-6 py-5">
                        <div className="text-sm font-medium text-slate-900">{row.time.relative}</div>
                        <div className="text-xs text-slate-500 mt-0.5">{row.time.exact}</div>
                      </td>
                      <td className="px-6 py-5 text-right">
                        <button className="text-indigo-600 font-bold text-xs hover:text-indigo-800 transition-colors">
                          {row.action}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            
            {/* Pagination Footer */}
            <div className="px-6 py-4 bg-slate-50/50 border-t border-slate-100 flex items-center justify-between">
              <span className="text-xs text-slate-500 font-medium">Showing 1 to 4 of 248 reviews</span>
              <div className="flex gap-1.5">
                <button className="w-8 h-8 rounded-lg flex items-center justify-center text-slate-400 hover:bg-white hover:text-indigo-600 transition-colors border border-transparent hover:border-slate-200 hover:shadow-sm">
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <button className="w-8 h-8 rounded-lg flex items-center justify-center bg-indigo-600 text-white font-bold text-xs shadow-md shadow-indigo-600/20">1</button>
                <button className="w-8 h-8 rounded-lg flex items-center justify-center text-slate-600 hover:bg-white hover:text-indigo-600 transition-colors border border-transparent hover:border-slate-200 hover:shadow-sm font-bold text-xs">2</button>
                <button className="w-8 h-8 rounded-lg flex items-center justify-center text-slate-600 hover:bg-white hover:text-indigo-600 transition-colors border border-transparent hover:border-slate-200 hover:shadow-sm font-bold text-xs">3</button>
                <button className="w-8 h-8 rounded-lg flex items-center justify-center text-slate-400 hover:bg-white hover:text-indigo-600 transition-colors border border-transparent hover:border-slate-200 hover:shadow-sm">
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>
        </main>
      </div>

      {/* Floating Action Button */}
      <div className="fixed bottom-8 right-8 z-50">
        <button className="w-14 h-14 bg-indigo-600 text-white rounded-full shadow-xl shadow-indigo-600/30 flex items-center justify-center hover:bg-indigo-700 hover:scale-105 active:scale-95 transition-all">
          <HelpCircle className="w-6 h-6" />
        </button>
      </div>
    </div>
  );
}
