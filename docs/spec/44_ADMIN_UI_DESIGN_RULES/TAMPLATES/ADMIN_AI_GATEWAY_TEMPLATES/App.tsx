/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import {
  Bell,
  Book,
  Bot,
  ChevronLeft,
  ChevronRight,
  CircleDollarSign,
  Copy,
  Eye,
  FileEdit,
  FileText,
  Filter,
  HelpCircle,
  History,
  LayoutDashboard,
  Network,
  Plus,
  PlusSquare,
  RefreshCw,
  Rocket,
  Route,
  Search,
  Settings,
  TerminalSquare,
  Trash2,
  User,
} from 'lucide-react';

export default function App() {
  return (
    <div className="min-h-screen bg-surface text-on-surface flex">
      {/* Sidebar */}
      <aside className="w-64 h-screen fixed left-0 top-0 bg-surface-container-lowest border-r border-surface-container flex flex-col py-6 z-50 shadow-ambient">
        <div className="px-6 mb-8">
          <h1 className="text-lg font-extrabold text-on-surface tracking-tight">AI Management</h1>
          <p className="text-xs text-on-surface-variant font-medium mt-1">Graduation Project</p>
        </div>

        <nav className="flex-1 space-y-1 px-3">
          <NavItem icon={<Rocket size={20} />} label="Operations" />
          <NavItem icon={<LayoutDashboard size={20} />} label="Dashboard" />
          <NavItem icon={<Network size={20} />} label="Providers" />
          <NavItem icon={<Route size={20} />} label="Routing" />
          <NavItem icon={<TerminalSquare size={20} />} label="Prompts" active />
          <NavItem icon={<CircleDollarSign size={20} />} label="Costs" />
          <NavItem icon={<FileText size={20} />} label="Logs" />
        </nav>

        <div className="px-4 mt-auto space-y-4">
          <button className="w-full bg-primary text-white py-3 rounded-xl font-bold flex items-center justify-center gap-2 shadow-[0_8px_16px_rgba(70,71,211,0.2)] hover:scale-[1.02] transition-transform">
            <Plus size={18} />
            New App
          </button>
          <div className="space-y-1">
            <NavItem icon={<Book size={18} />} label="Docs" small />
            <NavItem icon={<HelpCircle size={18} />} label="Support" small />
          </div>
        </div>
      </aside>

      {/* Main Content Wrapper */}
      <div className="flex-1 ml-64 flex flex-col min-h-screen">
        {/* Top Header */}
        <header className="sticky top-0 z-40 bg-white/70 backdrop-blur-xl h-16 flex justify-between items-center px-8 shadow-ambient">
          <div className="flex items-center gap-4">
            <span className="text-xl font-extrabold text-on-surface tracking-tight">Luminous AI Suite</span>
            <div className="h-5 w-[1px] bg-outline-variant/40"></div>
            <span className="text-sm font-medium text-on-surface-variant">Prompt Templates</span>
          </div>

          <div className="flex items-center gap-5">
            <div className="relative">
              <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant/70" />
              <input
                type="text"
                placeholder="Search templates..."
                className="pl-9 pr-4 py-2 bg-surface-container-low border-none rounded-full text-sm focus:ring-2 focus:ring-primary/20 w-64 outline-none transition-all placeholder:text-on-surface-variant/70"
              />
            </div>
            <button className="text-on-surface-variant hover:text-primary transition-colors">
              <Bell size={20} />
            </button>
            <button className="text-on-surface-variant hover:text-primary transition-colors">
              <Settings size={20} />
            </button>
            <div className="h-8 w-8 rounded-full overflow-hidden border-2 border-primary-container">
              <img
                src="https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&h=150&fit=crop&crop=faces&auto=format&q=80"
                alt="User profile"
                className="w-full h-full object-cover"
                referrerPolicy="no-referrer"
              />
            </div>
          </div>
        </header>

        {/* Main Canvas */}
        <main className="flex-1 p-8 max-w-[1600px] w-full mx-auto">
          {/* Page Header */}
          <div className="mb-8 flex justify-between items-end">
            <div>
              <h2 className="text-3xl font-extrabold tracking-tight text-on-surface mb-3">Customer Support Assistant</h2>
              <div className="flex items-center gap-3">
                <span className="bg-secondary-container text-on-secondary-container px-3 py-1 rounded-full text-xs font-bold tracking-wide">
                  Production
                </span>
                <span className="text-sm text-on-surface-variant font-medium">ID: prompt-772-ca</span>
                <span className="text-sm text-outline-variant">•</span>
                <span className="text-sm text-on-surface-variant font-medium">Last updated 2h ago</span>
              </div>
            </div>
            <div className="flex gap-3">
              <button className="px-5 py-2.5 bg-surface-container-highest text-on-surface rounded-xl text-sm font-bold flex items-center gap-2 hover:bg-surface-container-highest/80 transition-colors">
                <History size={18} />
                Version History
              </button>
              <button className="px-6 py-2.5 bg-gradient-to-br from-primary to-primary-dim text-white rounded-xl text-sm font-bold shadow-[0_8px_20px_rgba(70,71,211,0.25)] hover:scale-105 transition-all">
                Deploy v4.2.0
              </button>
            </div>
          </div>

          {/* Split Screen: Editor & Preview */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mb-8">
            {/* Left: Template Structure */}
            <div className="bg-surface-container-lowest rounded-2xl shadow-ambient overflow-hidden flex flex-col h-[520px]">
              <div className="px-6 py-4 border-b border-surface-container flex justify-between items-center">
                <div className="flex items-center gap-3">
                  <FileEdit size={20} className="text-primary" />
                  <h3 className="font-bold text-on-surface">Template Structure</h3>
                </div>
                <select className="text-xs font-bold bg-surface-container-low border-none rounded-lg py-1.5 px-3 outline-none focus:ring-2 focus:ring-primary/20 cursor-pointer">
                  <option>MESSAGE_BUNDLE</option>
                  <option>TEXT_RAW</option>
                </select>
              </div>
              <div className="flex-1 overflow-auto p-6 bg-surface-container-low/30 scrollbar-hide">
                <div className="space-y-5">
                  {/* System Message Block */}
                  <div className="bg-white rounded-xl border border-outline-variant/20 shadow-sm overflow-hidden focus-within:border-primary/40 focus-within:ring-2 focus-within:ring-primary/10 transition-all">
                    <div className="bg-surface-container-low/50 px-4 py-2.5 border-b border-outline-variant/10 flex justify-between items-center">
                      <div className="flex items-center gap-2">
                        <Bot size={14} className="text-on-surface-variant" />
                        <span className="text-xs font-bold text-on-surface-variant uppercase tracking-wider">System</span>
                      </div>
                      <button className="text-outline-variant hover:text-error transition-colors" title="Delete message">
                        <Trash2 size={14} />
                      </button>
                    </div>
                    <textarea
                      className="w-full p-4 text-sm text-on-surface bg-transparent border-none focus:ring-0 resize-none outline-none font-mono leading-relaxed"
                      rows={4}
                      defaultValue="You are a highly professional customer support agent for {{company_name}}. Your tone is {{tone}}. Always mention the ticket ID {{ticket_id}} in the closing."
                    />
                  </div>

                  {/* User Message Block */}
                  <div className="bg-white rounded-xl border border-outline-variant/20 shadow-sm overflow-hidden focus-within:border-primary/40 focus-within:ring-2 focus-within:ring-primary/10 transition-all">
                    <div className="bg-surface-container-low/50 px-4 py-2.5 border-b border-outline-variant/10 flex justify-between items-center">
                      <div className="flex items-center gap-2">
                        <User size={14} className="text-on-surface-variant" />
                        <span className="text-xs font-bold text-on-surface-variant uppercase tracking-wider">User</span>
                      </div>
                      <button className="text-outline-variant hover:text-error transition-colors" title="Delete message">
                        <Trash2 size={14} />
                      </button>
                    </div>
                    <textarea
                      className="w-full p-4 text-sm text-on-surface bg-transparent border-none focus:ring-0 resize-none outline-none font-mono leading-relaxed"
                      rows={2}
                      defaultValue="I need help with my recent order {{order_id}}. It hasn't arrived yet."
                    />
                  </div>

                  {/* Add Message Button */}
                  <button className="w-full py-3 border-2 border-dashed border-outline-variant/30 rounded-xl text-on-surface-variant font-bold text-sm flex items-center justify-center gap-2 hover:bg-surface-container-low hover:text-on-surface hover:border-outline-variant/50 transition-all">
                    <Plus size={16} />
                    Add Message
                  </button>

                  {/* Variables Configuration */}
                  <div className="pt-6 mt-2 border-t border-outline-variant/10">
                    <div className="flex items-center justify-between mb-4">
                      <span className="text-xs font-bold text-on-surface-variant uppercase tracking-widest">Test Variables</span>
                      <button className="text-xs text-primary font-bold hover:underline flex items-center gap-1">
                        <RefreshCw size={12} />
                        Auto-detect
                      </button>
                    </div>
                    <div className="space-y-3">
                      <div className="flex items-center gap-3">
                        <div className="w-1/3 bg-surface-container-low/50 px-3 py-2.5 rounded-lg border border-outline-variant/10 text-xs font-mono text-on-surface-variant font-medium">company_name</div>
                        <input type="text" defaultValue="Luminous AI" className="flex-1 px-3 py-2.5 bg-white border border-outline-variant/20 rounded-lg text-sm focus:ring-2 focus:ring-primary/20 outline-none transition-all shadow-sm" placeholder="Enter test value..." />
                      </div>
                      <div className="flex items-center gap-3">
                        <div className="w-1/3 bg-surface-container-low/50 px-3 py-2.5 rounded-lg border border-outline-variant/10 text-xs font-mono text-on-surface-variant font-medium">tone</div>
                        <input type="text" defaultValue="Empathetic" className="flex-1 px-3 py-2.5 bg-white border border-outline-variant/20 rounded-lg text-sm focus:ring-2 focus:ring-primary/20 outline-none transition-all shadow-sm" placeholder="Enter test value..." />
                      </div>
                      <div className="flex items-center gap-3">
                        <div className="w-1/3 bg-surface-container-low/50 px-3 py-2.5 rounded-lg border border-outline-variant/10 text-xs font-mono text-on-surface-variant font-medium">ticket_id</div>
                        <input type="text" defaultValue="[PENDING]" className="flex-1 px-3 py-2.5 bg-white border border-outline-variant/20 rounded-lg text-sm focus:ring-2 focus:ring-primary/20 outline-none transition-all shadow-sm" placeholder="Enter test value..." />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Right: Rendered Preview */}
            <div className="bg-surface-container-lowest rounded-2xl shadow-ambient overflow-hidden flex flex-col h-[520px]">
              <div className="px-6 py-4 border-b border-surface-container flex justify-between items-center">
                <div className="flex items-center gap-3">
                  <Eye size={20} className="text-secondary" />
                  <h3 className="font-bold text-on-surface">Rendered Preview</h3>
                </div>
                <div className="flex items-center gap-1">
                  <button className="p-2 hover:bg-surface-container-low rounded-lg transition-colors text-on-surface-variant">
                    <Copy size={16} />
                  </button>
                  <button className="p-2 hover:bg-surface-container-low rounded-lg transition-colors text-on-surface-variant">
                    <RefreshCw size={16} />
                  </button>
                </div>
              </div>
              <div className="flex-1 p-8 overflow-auto bg-surface-container-low/50 scrollbar-hide">
                <div className="space-y-6">
                  <div className="bg-white p-6 rounded-2xl shadow-sm border border-outline-variant/10">
                    <p className="text-sm leading-relaxed text-on-surface">
                      <span className="font-bold text-primary block mb-3 text-xs tracking-wider uppercase">System:</span>
                      You are a highly professional customer support agent for Luminous AI. Your tone is Empathetic. Always mention the ticket ID [PENDING] in the closing.
                    </p>
                  </div>
                  <div className="bg-white p-6 rounded-2xl shadow-sm border border-outline-variant/10 relative overflow-hidden">
                    <div className="absolute top-0 left-0 w-1.5 h-full bg-primary"></div>
                    <p className="text-sm leading-relaxed text-on-surface">
                      <span className="font-bold text-primary block mb-3 text-xs tracking-wider uppercase">User:</span>
                      I need help with my recent order ORD-88921. It hasn't arrived yet.
                    </p>
                  </div>
                  <div className="flex justify-center pt-4">
                    <span className="bg-surface-container-highest px-5 py-2 rounded-full text-[10px] font-bold text-on-surface-variant tracking-widest uppercase">
                      End of Prompt Chain
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Version Library Table */}
          <div className="bg-surface-container-lowest rounded-2xl shadow-ambient overflow-hidden mb-24">
            <div className="px-8 py-6 border-b border-surface-container flex justify-between items-center">
              <h3 className="text-xl font-extrabold text-on-surface">Version Library</h3>
              <button className="flex items-center gap-2 bg-surface-container-low hover:bg-surface-container transition-colors px-4 py-2 rounded-xl text-sm font-medium text-on-surface-variant">
                <Filter size={16} />
                Filter by tag
              </button>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-surface-container-low/50">
                    <th className="px-8 py-4 text-xs font-bold text-on-surface-variant uppercase tracking-wider">Version</th>
                    <th className="px-8 py-4 text-xs font-bold text-on-surface-variant uppercase tracking-wider">Format</th>
                    <th className="px-8 py-4 text-xs font-bold text-on-surface-variant uppercase tracking-wider">Commit Message</th>
                    <th className="px-8 py-4 text-xs font-bold text-on-surface-variant uppercase tracking-wider">Latency</th>
                    <th className="px-8 py-4 text-xs font-bold text-on-surface-variant uppercase tracking-wider">Date</th>
                    <th className="px-8 py-4 text-xs font-bold text-on-surface-variant uppercase tracking-wider text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-surface-container">
                  <VersionRow
                    version="v4.2.0"
                    isLatest
                    format="BUNDLE"
                    message="Updated system instructions for refund edge-cases."
                    latency="1.2s"
                    latencyPercent={30}
                    latencyColor="bg-secondary"
                    date="Oct 24, 2023"
                    action="Rollback"
                  />
                  <VersionRow
                    version="v4.1.2"
                    format="TEXT"
                    message="Initial production release for Q4 campaign."
                    latency="0.8s"
                    latencyPercent={15}
                    latencyColor="bg-primary"
                    date="Oct 12, 2023"
                    action="Restore"
                  />
                  <VersionRow
                    version="v4.0.0"
                    format="BUNDLE"
                    message="Major refactor to message-bundle format."
                    latency="2.4s"
                    latencyPercent={80}
                    latencyColor="bg-error"
                    date="Sep 28, 2023"
                    action="Restore"
                  />
                </tbody>
              </table>
            </div>
            <div className="px-8 py-5 bg-surface-container-low/30 border-t border-surface-container flex justify-between items-center">
              <span className="text-sm font-medium text-on-surface-variant">Showing 3 of 42 versions</span>
              <div className="flex gap-2">
                <button className="p-2 rounded-xl bg-white border border-outline-variant/20 text-on-surface-variant disabled:opacity-50 hover:bg-surface-container-low transition-colors" disabled>
                  <ChevronLeft size={18} />
                </button>
                <button className="p-2 rounded-xl bg-white border border-outline-variant/20 text-on-surface-variant hover:bg-surface-container-low transition-colors">
                  <ChevronRight size={18} />
                </button>
              </div>
            </div>
          </div>
        </main>
      </div>

      {/* Floating Action Button */}
      <button className="fixed bottom-8 right-8 bg-primary text-white h-14 px-6 rounded-full shadow-[0_12px_24px_rgba(70,71,211,0.3)] flex items-center gap-3 hover:scale-105 active:scale-95 transition-all z-50">
        <PlusSquare size={20} />
        <span className="font-bold tracking-wide">Create Variant</span>
      </button>
    </div>
  );
}

// --- Subcomponents ---

function NavItem({ icon, label, active = false, small = false }: { icon: React.ReactNode, label: string, active?: boolean, small?: boolean }) {
  return (
    <a
      href="#"
      className={`flex items-center px-4 py-3 rounded-xl mx-2 transition-all duration-200 group ${
        active
          ? 'bg-white text-primary shadow-sm'
          : 'text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface'
      }`}
    >
      <span className={`mr-3 ${active ? 'text-primary' : 'text-on-surface-variant group-hover:text-primary transition-colors'}`}>
        {icon}
      </span>
      <span className={`font-medium ${small ? 'text-sm' : 'text-[15px]'}`}>{label}</span>
    </a>
  );
}

function VersionRow({
  version,
  isLatest = false,
  format,
  message,
  latency,
  latencyPercent,
  latencyColor,
  date,
  action
}: {
  version: string;
  isLatest?: boolean;
  format: string;
  message: string;
  latency: string;
  latencyPercent: number;
  latencyColor: string;
  date: string;
  action: string;
}) {
  return (
    <tr className="hover:bg-surface-container-low/40 transition-colors group">
      <td className="px-8 py-5">
        <div className="flex items-center gap-3">
          <span className={`font-bold ${isLatest ? 'text-primary' : 'text-on-surface'}`}>{version}</span>
          {isLatest && (
            <span className="bg-secondary-container/30 text-secondary text-[10px] px-2 py-0.5 rounded font-bold tracking-wider">
              LATEST
            </span>
          )}
        </div>
      </td>
      <td className="px-8 py-5">
        <span className={`text-[10px] font-bold px-2.5 py-1 rounded-md tracking-wider ${
          format === 'BUNDLE' ? 'bg-primary-container/20 text-primary-dim' : 'bg-surface-container-highest text-on-surface-variant'
        }`}>
          {format}
        </span>
      </td>
      <td className="px-8 py-5 text-sm text-on-surface-variant font-medium">{message}</td>
      <td className="px-8 py-5">
        <div className="flex items-center gap-3">
          <div className="w-20 h-1.5 bg-surface-container rounded-full overflow-hidden">
            <div className={`h-full ${latencyColor} rounded-full`} style={{ width: `${latencyPercent}%` }}></div>
          </div>
          <span className="text-xs font-bold text-on-surface">{latency}</span>
        </div>
      </td>
      <td className="px-8 py-5 text-sm text-on-surface-variant font-medium">{date}</td>
      <td className="px-8 py-5 text-right">
        <button className="text-primary font-bold text-sm hover:underline opacity-0 group-hover:opacity-100 transition-opacity">
          {action}
        </button>
      </td>
    </tr>
  );
}
