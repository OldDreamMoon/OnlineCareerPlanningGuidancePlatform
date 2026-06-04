import React, { useState } from 'react';
import {
  LayoutDashboard,
  Flag,
  MessageSquareWarning,
  ShieldAlert,
  History,
  Search,
  Bell,
  HelpCircle,
  RefreshCw,
  ExternalLink,
  Eye,
  X,
  Zap,
  Copy
} from 'lucide-react';

// Mock Data
const MOCK_LOGS = [
  {
    id: 'log_882901-xa',
    timestamp: 'Oct 24, 14:20:12',
    action: 'UPDATE',
    actor: 'admin_j_smith',
    target: 'content_item_9921',
    targetDesc: 'Policy: HATE_SPEECH',
    traceId: 'trc_fa2210b',
    type: 'primary',
    ip: '192.168.1.104',
    region: 'US-EAST-1',
    hasAiLog: true,
    rawJson: `{
  "action": "UPDATE",
  "actor": {
    "id": "usr_771",
    "role": "MODERATOR_LEVEL_2",
    "username": "admin_j_smith"
  },
  "target": {
    "type": "CONTENT",
    "id": "cnt_9921",
    "previous_state": "PENDING",
    "new_state": "REJECTED"
  },
  "metadata": {
    "reason_code": "HS_V3",
    "confidence": 0.98,
    "source": "manual_override"
  },
  "trace_id": "trc_fa2210b"
}`
  },
  {
    id: 'log_882902-xb',
    timestamp: 'Oct 24, 14:18:05',
    action: 'DELETE',
    actor: 'system_auto_purge',
    target: 'user_banned_662',
    targetDesc: 'Reason: RETENTION_EXPIRED',
    traceId: 'trc_bb9910a',
    type: 'error',
    ip: '10.0.0.5',
    region: 'EU-WEST-2',
    hasAiLog: false,
    rawJson: `{
  "action": "DELETE",
  "actor": {
    "id": "sys_001",
    "role": "SYSTEM"
  },
  "target": {
    "type": "USER",
    "id": "user_banned_662"
  },
  "reason": "RETENTION_EXPIRED"
}`
  },
  {
    id: 'log_882903-xc',
    timestamp: 'Oct 24, 14:15:44',
    action: 'OVERRIDE',
    actor: 'admin_m_doe',
    target: 'ai_model_v4_config',
    targetDesc: 'False Positive Adjustment',
    traceId: 'trc_99120xf',
    type: 'tertiary',
    ip: '192.168.1.105',
    region: 'US-WEST-1',
    hasAiLog: true,
    rawJson: `{
  "action": "OVERRIDE",
  "actor": {
    "id": "usr_882",
    "role": "MODERATOR_LEVEL_3",
    "username": "admin_m_doe"
  },
  "target": {
    "type": "CONFIG",
    "id": "ai_model_v4_config"
  },
  "notes": "False Positive Adjustment"
}`
  }
];

export default function App() {
  const [selectedLog, setSelectedLog] = useState<typeof MOCK_LOGS[0] | null>(null);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);

  const openDrawer = (log: typeof MOCK_LOGS[0]) => {
    setSelectedLog(log);
    setIsDrawerOpen(true);
  };

  const closeDrawer = () => {
    setIsDrawerOpen(false);
    // Optional: delay clearing selected log to allow exit animation to finish smoothly
    setTimeout(() => setSelectedLog(null), 300);
  };

  return (
    <div className="flex min-h-screen w-full bg-surface">
      {/* Sidebar */}
      <aside className="h-screen w-64 bg-slate-50 flex flex-col p-4 gap-2 shrink-0 border-r border-outline-variant/10">
        <div className="mb-8 px-2">
          <h1 className="text-lg font-black text-slate-900 font-headline">Moderation Engine</h1>
          <p className="text-xs text-slate-500 font-medium uppercase tracking-widest">Safety & Compliance</p>
        </div>
        <nav className="flex-1 space-y-1">
          <a href="#" className="flex items-center gap-3 px-3 py-2 text-slate-600 hover:bg-cyan-50 transition-all font-medium text-sm rounded-lg active:translate-x-1 duration-150">
            <LayoutDashboard size={20} />
            <span>Dashboard</span>
          </a>
          <a href="#" className="flex items-center gap-3 px-3 py-2 text-slate-600 hover:bg-cyan-50 transition-all font-medium text-sm rounded-lg active:translate-x-1 duration-150">
            <Flag size={20} />
            <span>Reports</span>
          </a>
          <a href="#" className="flex items-center gap-3 px-3 py-2 text-slate-600 hover:bg-cyan-50 transition-all font-medium text-sm rounded-lg active:translate-x-1 duration-150">
            <MessageSquareWarning size={20} />
            <span>Review Queue</span>
          </a>
          <a href="#" className="flex items-center gap-3 px-3 py-2 text-slate-600 hover:bg-cyan-50 transition-all font-medium text-sm rounded-lg active:translate-x-1 duration-150">
            <ShieldAlert size={20} />
            <span>Sensitive Words</span>
          </a>
          <a href="#" className="flex items-center gap-3 px-3 py-2 bg-white text-cyan-600 shadow-sm rounded-lg font-medium text-sm active:translate-x-1 duration-150">
            <History size={20} />
            <span>Audit Logs</span>
          </a>
        </nav>
        <div className="mt-auto">
          <button className="w-full py-3 px-4 bg-primary text-on-primary rounded-xl font-bold text-sm shadow-lg hover:shadow-primary/20 active:scale-95 transition-all">
            Urgent Reviews
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <div className="flex-1 flex flex-col h-screen overflow-hidden relative">
        {/* Header */}
        <header className="w-full sticky top-0 z-40 bg-white/70 backdrop-blur-xl flex justify-between items-center px-6 py-3 shadow-[0px_10px_40px_rgba(44,47,49,0.06)]">
          <div className="flex items-center gap-8">
            <span className="text-xl font-bold tracking-tighter text-cyan-600 font-headline">Luminous Mod</span>
            <div className="hidden md:flex bg-slate-100/50 rounded-full px-4 py-1.5 items-center gap-2">
              <Search size={18} className="text-slate-400" />
              <input
                type="text"
                placeholder="Search traceId or user..."
                className="bg-transparent border-none focus:ring-0 text-sm w-64 placeholder:text-slate-400 outline-none"
              />
            </div>
          </div>
          <div className="flex items-center gap-4">
            <button className="p-2 text-slate-500 hover:bg-slate-50 transition-colors rounded-full">
              <Bell size={20} />
            </button>
            <button className="p-2 text-slate-500 hover:bg-slate-50 transition-colors rounded-full">
              <HelpCircle size={20} />
            </button>
            <div className="h-8 w-8 rounded-full bg-slate-200 overflow-hidden border-2 border-primary/20">
              <img
                src="https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?ixlib=rb-1.2.1&auto=format&fit=facearea&facepad=2&w=256&h=256&q=80"
                alt="User Avatar"
                className="w-full h-full object-cover"
              />
            </div>
          </div>
        </header>

        {/* Page Content */}
        <main className="flex-1 overflow-y-auto p-8 space-y-8">
          {/* Hero Section */}
          <section className="flex flex-col md:flex-row md:items-end justify-between gap-6">
            <div>
              <h2 className="text-4xl font-extrabold font-headline text-on-surface tracking-tight mb-1">Audit Logs</h2>
              <p className="text-on-surface-variant max-w-lg">
                Track every system interaction and automated action within the Luminous Mod ecosystem. Full traceability for safety compliance.
              </p>
            </div>
            <div className="flex gap-4">
              <div className="bg-surface-container-lowest p-4 rounded-xl shadow-sm min-w-[140px] flex flex-col gap-1">
                <span className="text-xs font-bold text-on-surface-variant uppercase tracking-wider">Total Logs</span>
                <span className="text-2xl font-black font-headline text-primary">124.5k</span>
              </div>
              <div className="bg-surface-container-lowest p-4 rounded-xl shadow-sm min-w-[140px] flex flex-col gap-1 border-l-4 border-secondary">
                <span className="text-xs font-bold text-on-surface-variant uppercase tracking-wider">24h Events</span>
                <span className="text-2xl font-black font-headline text-secondary">+1,204</span>
              </div>
            </div>
          </section>

          {/* Filter Toolbar */}
          <section className="bg-surface-container-low p-6 rounded-xl space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-bold text-on-surface-variant ml-1">Target Type</label>
                <select className="bg-surface-container-lowest border-none rounded-lg text-sm p-2.5 outline-none focus:ring-2 focus:ring-primary/20">
                  <option>All Types</option>
                  <option>User Account</option>
                  <option>Content Thread</option>
                  <option>AI Gateway</option>
                </select>
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-bold text-on-surface-variant ml-1">Action</label>
                <select className="bg-surface-container-lowest border-none rounded-lg text-sm p-2.5 outline-none focus:ring-2 focus:ring-primary/20">
                  <option>All Actions</option>
                  <option>CREATE</option>
                  <option>UPDATE</option>
                  <option>DELETE</option>
                  <option>OVERRIDE</option>
                </select>
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-bold text-on-surface-variant ml-1">Trace ID</label>
                <input
                  type="text"
                  placeholder="e.g. trc_90812..."
                  className="bg-surface-container-lowest border-none rounded-lg text-sm p-2.5 outline-none focus:ring-2 focus:ring-primary/20"
                />
              </div>
              <div className="flex items-end gap-2">
                <button className="flex-1 py-2.5 bg-primary/10 text-primary font-bold rounded-lg hover:bg-primary/20 transition-colors text-sm">
                  Apply Filters
                </button>
                <button className="p-2.5 bg-surface-container-lowest text-on-surface-variant rounded-lg hover:bg-white shadow-sm transition-all">
                  <RefreshCw size={18} />
                </button>
              </div>
            </div>
          </section>

          {/* Data Table */}
          <section className="bg-surface-container-lowest rounded-xl overflow-hidden shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead className="bg-surface-container-low/50">
                  <tr>
                    <th className="px-6 py-4 text-xs font-black text-on-surface-variant uppercase tracking-widest">Timestamp</th>
                    <th className="px-6 py-4 text-xs font-black text-on-surface-variant uppercase tracking-widest">Action / Actor</th>
                    <th className="px-6 py-4 text-xs font-black text-on-surface-variant uppercase tracking-widest">Target</th>
                    <th className="px-6 py-4 text-xs font-black text-on-surface-variant uppercase tracking-widest">Trace ID</th>
                    <th className="px-6 py-4 text-xs font-black text-on-surface-variant uppercase tracking-widest text-right">Details</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/10">
                  {MOCK_LOGS.map((log) => (
                    <tr key={log.id} className="hover:bg-primary/5 transition-colors group">
                      <td className="px-6 py-4">
                        <div className="text-sm font-semibold text-on-surface">{log.timestamp}</div>
                        <div className="text-[10px] text-on-surface-variant">UTC +00:00</div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-2">
                          <span className={`px-2 py-0.5 text-[10px] font-bold rounded uppercase ${
                            log.type === 'primary' ? 'bg-secondary-container text-on-secondary-container' :
                            log.type === 'error' ? 'bg-error-container text-on-error-container' :
                            'bg-tertiary-container text-on-tertiary-container'
                          }`}>
                            {log.action}
                          </span>
                          <span className="text-sm font-medium">{log.actor}</span>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="text-sm font-medium text-on-surface">{log.target}</div>
                        <div className="text-xs text-on-surface-variant italic">{log.targetDesc}</div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-1.5">
                          <code className="text-xs bg-surface-container-low px-2 py-1 rounded">{log.traceId}</code>
                          {log.hasAiLog && (
                            <a href="#" className="text-primary hover:underline text-[10px] font-bold flex items-center gap-0.5">
                              AI GATEWAY <ExternalLink size={12} />
                            </a>
                          )}
                        </div>
                      </td>
                      <td className="px-6 py-4 text-right">
                        <button 
                          onClick={() => openDrawer(log)}
                          className="p-2 hover:bg-primary-container/20 text-primary rounded-lg transition-all"
                        >
                          <Eye size={20} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {/* Pagination */}
            <div className="px-6 py-4 bg-surface-container-low/20 flex items-center justify-between border-t border-outline-variant/10">
              <span className="text-xs text-on-surface-variant font-medium">Showing 1 to 25 of 124,500 logs</span>
              <div className="flex gap-2">
                <button className="px-3 py-1.5 border border-outline-variant/30 rounded-lg text-xs font-bold hover:bg-white disabled:opacity-50" disabled>Previous</button>
                <button className="px-3 py-1.5 border border-outline-variant/30 rounded-lg text-xs font-bold bg-primary text-on-primary">1</button>
                <button className="px-3 py-1.5 border border-outline-variant/30 rounded-lg text-xs font-bold hover:bg-white">2</button>
                <button className="px-3 py-1.5 border border-outline-variant/30 rounded-lg text-xs font-bold hover:bg-white">3</button>
                <button className="px-3 py-1.5 border border-outline-variant/30 rounded-lg text-xs font-bold hover:bg-white">Next</button>
              </div>
            </div>
          </section>
        </main>

        {/* Drawer Overlay */}
        {isDrawerOpen && (
          <div 
            className="fixed inset-0 bg-black/10 backdrop-blur-[2px] z-50 transition-opacity"
            onClick={closeDrawer}
          />
        )}

        {/* Drawer */}
        <div 
          className={`fixed inset-y-0 right-0 w-[450px] bg-white shadow-2xl z-[60] flex flex-col border-l border-outline-variant/20 transform transition-transform duration-300 ease-in-out ${
            isDrawerOpen ? 'translate-x-0' : 'translate-x-full'
          }`}
        >
          {selectedLog && (
            <>
              <div className="p-6 border-b border-outline-variant/10 flex items-center justify-between">
                <div>
                  <h3 className="text-xl font-black font-headline">Log Details</h3>
                  <p className="text-xs text-on-surface-variant font-medium tracking-tight">Event ID: {selectedLog.id}</p>
                </div>
                <button onClick={closeDrawer} className="p-2 hover:bg-surface-container-low rounded-full transition-colors">
                  <X size={20} />
                </button>
              </div>
              
              <div className="flex-1 overflow-y-auto p-6 space-y-6">
                {/* Summary Info */}
                <div className="grid grid-cols-2 gap-4">
                  <div className="p-3 bg-surface-container-low rounded-lg">
                    <span className="text-[10px] font-black text-on-surface-variant uppercase block mb-1">Actor IP</span>
                    <span className="text-sm font-mono">{selectedLog.ip}</span>
                  </div>
                  <div className="p-3 bg-surface-container-low rounded-lg">
                    <span className="text-[10px] font-black text-on-surface-variant uppercase block mb-1">Region</span>
                    <span className="text-sm font-mono">{selectedLog.region}</span>
                  </div>
                </div>

                {/* Traceability */}
                <div className="space-y-3">
                  <h4 className="text-xs font-bold text-on-surface uppercase tracking-widest border-b border-outline-variant/10 pb-2">Traceability</h4>
                  <div className="flex flex-col gap-3">
                    <div className="flex justify-between items-center">
                      <span className="text-sm text-on-surface-variant">Trace ID</span>
                      <code className="text-xs font-bold bg-primary/5 text-primary px-2 py-1 rounded">{selectedLog.traceId}</code>
                    </div>
                    
                    {selectedLog.hasAiLog && (
                      <div className="bg-primary/5 border border-primary/20 p-4 rounded-xl flex items-center justify-between">
                        <div className="flex items-center gap-3">
                          <div className="h-10 w-10 bg-primary/10 rounded-full flex items-center justify-center text-primary">
                            <Zap size={20} />
                          </div>
                          <div>
                            <p className="text-sm font-bold">AI Gateway Log Found</p>
                            <p className="text-[10px] text-on-surface-variant">Detailed inference & token metrics available</p>
                          </div>
                        </div>
                        <button className="px-3 py-1.5 bg-primary text-on-primary rounded-lg text-xs font-bold shadow-sm hover:bg-primary/90 transition-colors">
                          View Flow
                        </button>
                      </div>
                    )}
                  </div>
                </div>

                {/* Raw JSON View */}
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <h4 className="text-xs font-bold text-on-surface uppercase tracking-widest">Raw Data payload</h4>
                    <button className="text-primary text-[10px] font-bold flex items-center gap-1 hover:text-primary/80 transition-colors">
                      <Copy size={14} /> COPY JSON
                    </button>
                  </div>
                  <div className="bg-slate-900 rounded-xl p-4 overflow-x-auto">
                    <pre className="text-[11px] text-cyan-400 font-mono leading-relaxed">
                      {selectedLog.rawJson}
                    </pre>
                  </div>
                </div>
              </div>

              <div className="p-6 bg-surface-container-low/30 border-t border-outline-variant/10">
                <button className="w-full py-3 border-2 border-primary text-primary font-bold rounded-xl hover:bg-primary/5 transition-all">
                  Download Audit Report (PDF)
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
