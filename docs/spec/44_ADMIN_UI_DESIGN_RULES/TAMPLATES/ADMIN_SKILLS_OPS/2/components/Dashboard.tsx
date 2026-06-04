import { Sparkles, ListTree, UploadCloud, FileIcon, Video, Code, ExternalLink, PlayCircle, History, Network, Share2, Archive, ChevronRight } from 'lucide-react';

export default function Dashboard() {
  return (
    <main className="flex-1 overflow-x-hidden p-8">
      <div className="max-w-7xl mx-auto space-y-8">
        {/* Hero Stats Section */}
        <section className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <StatCard title="Total Nodes" value="1,284" icon={<Network className="w-24 h-24" />} color="text-primary-content" />
          <StatCard title="Relations" value="4,812" icon={<Share2 className="w-24 h-24" />} color="text-secondary" />
          <StatCard title="Resources" value="8,590" icon={<Archive className="w-24 h-24" />} color="text-tertiary" />
        </section>

        {/* Workspace Layout */}
        <div className="grid grid-cols-12 gap-8 items-start">
          {/* Left Column (Main Detail & AI) */}
          <div className="col-span-12 lg:col-span-7 space-y-6">
            {/* Node Information Panel */}
            <div className="bg-surface-lowest rounded-xl shadow-ambient p-8 space-y-8">
              <div className="flex justify-between items-start">
                <div className="space-y-2">
                  <div className="flex items-center gap-2 text-primary-content text-xs font-bold uppercase tracking-widest">
                    <span>Frontend</span>
                    <ChevronRight className="w-3 h-3" />
                    <span>Frameworks</span>
                  </div>
                  <h3 className="text-4xl font-display font-extrabold text-on-surface tracking-tight">React Hooks</h3>
                </div>
                <button className="bg-gradient-to-br from-primary to-primary-container text-primary-content px-6 py-3 rounded-xl text-sm font-bold shadow-ambient flex items-center gap-2 hover:scale-105 transition-transform">
                  <Sparkles className="w-5 h-5" />
                  Preview Star Map
                </button>
              </div>

              <div className="grid grid-cols-2 gap-6">
                <div className="bg-surface-low p-5 rounded-xl">
                  <p className="text-[10px] text-on-surface-variant font-bold uppercase mb-2">Node Code</p>
                  <p className="text-base font-mono font-semibold text-primary-content">SK-FE-R-HOOKS</p>
                </div>
                <div className="bg-surface-low p-5 rounded-xl">
                  <p className="text-[10px] text-on-surface-variant font-bold uppercase mb-2">Project</p>
                  <p className="text-base font-semibold text-on-surface">Standard Core Library</p>
                </div>
              </div>

              <div className="space-y-3">
                <p className="text-xs font-bold text-on-surface-variant uppercase">Description</p>
                <p className="text-sm text-on-surface-variant leading-relaxed">
                  Covers all Hook types introduced in React 16.8+. Includes state management (useState), lifecycle simulation (useEffect), context handling, and custom Hook design patterns. This node is a core part of the frontend framework learning path.
                </p>
              </div>

              <div className="pt-8 mt-8 relative">
                {/* Ghost divider using background shift instead of border */}
                <div className="absolute top-0 left-0 w-full h-px bg-surface-low"></div>
                <div className="flex items-center justify-between mb-6">
                  <h4 className="text-sm font-bold flex items-center gap-2 text-on-surface">
                    <ListTree className="w-5 h-5 text-primary-content" />
                    Sub-nodes Management
                  </h4>
                  <button className="text-xs font-bold text-primary-content hover:text-primary-content/80 transition-colors">Add Sub-node</button>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <SubNodeItem title="Basic Hooks (useState, useEffect)" />
                  <SubNodeItem title="Custom Hooks Patterns" />
                </div>
              </div>
            </div>

            {/* AI Suggestions Card */}
            <div className="relative rounded-xl overflow-hidden p-8 text-on-surface shadow-ambient bg-gradient-to-br from-primary-container to-surface-lowest">
              <div className="relative z-10">
                <h4 className="text-lg font-display font-extrabold flex items-center gap-2 mb-3 text-primary-content">
                  <Sparkles className="w-5 h-5" />
                  AI Governance Suggestion
                </h4>
                <p className="text-sm font-medium text-on-surface-variant leading-relaxed max-w-lg">
                  Current node resources are concentrated in videos. It is recommended to add more "Best Practice Documentation" resources and increase lateral connections with the "State Management" node to improve star map completeness.
                </p>
              </div>
            </div>
          </div>

          {/* Right Column (Resources & Logs) */}
          <div className="col-span-12 lg:col-span-5 space-y-6">
            {/* Resources List */}
            <div className="bg-surface-lowest rounded-xl shadow-ambient p-8 space-y-6">
              <div className="flex items-center justify-between">
                <h4 className="text-sm font-bold flex items-center gap-2 uppercase tracking-wide text-on-surface">
                  <Archive className="w-5 h-5 text-tertiary" />
                  Resources (42)
                </h4>
                <button className="bg-surface-low text-primary-content px-4 py-2 rounded-xl text-xs font-bold hover:bg-primary-container transition-colors">
                  Batch Manage
                </button>
              </div>

              <div className="space-y-4">
                <ResourceItem
                  icon={<FileIcon className="w-5 h-5" />}
                  iconBg="bg-red-50 text-red-500"
                  title="React Hooks Official Whitepaper.pdf"
                  meta="PDF • 1.2 MB"
                  tag="Must Read"
                  tagColor="text-primary-content"
                  actionIcon={<ExternalLink className="w-4 h-4" />}
                />
                <ResourceItem
                  icon={<Video className="w-5 h-5" />}
                  iconBg="bg-primary-container text-primary-content"
                  title="In-depth useEffect Full Tutorial"
                  meta="VIDEO • 24:15"
                  tag="Tutorial"
                  tagColor="text-secondary"
                  actionIcon={<PlayCircle className="w-4 h-4" />}
                />
                <ResourceItem
                  icon={<Code className="w-5 h-5" />}
                  iconBg="bg-amber-50 text-tertiary"
                  title="Best Practices: Custom Hooks Repo"
                  meta="GITHUB • Repo"
                  tag="Reference"
                  tagColor="text-tertiary"
                  actionIcon={<ExternalLink className="w-4 h-4" />}
                />
              </div>

              <button className="w-full py-4 bg-surface-low rounded-xl text-on-surface-variant text-sm font-bold hover:bg-primary-container hover:text-primary-content transition-all flex items-center justify-center gap-2">
                <UploadCloud className="w-5 h-5" />
                Upload / Link More Resources
              </button>
            </div>

            {/* Audit Logs */}
            <div className="bg-surface-lowest rounded-xl shadow-ambient p-8 space-y-6">
              <h4 className="text-sm font-bold flex items-center gap-2 uppercase tracking-wide text-on-surface-variant">
                <History className="w-5 h-5" />
                Audit Logs
              </h4>
              <div className="space-y-6">
                <AuditLogItem title="Updated node description" user="Admin_Leo" time="2023-11-02 14:20" active />
                <AuditLogItem title='Added resource "Hook Lifecycle Diagram"' user="Admin_Leo" time="2023-10-28 09:15" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}

function StatCard({ title, value, icon, color }: { title: string; value: string; icon: React.ReactNode; color: string }) {
  return (
    <div className="bg-surface-lowest p-8 rounded-xl shadow-ambient flex items-center justify-between group overflow-hidden relative">
      <div className="relative z-10">
        <p className="text-on-surface-variant text-xs font-bold uppercase tracking-wider mb-2">{title}</p>
        <h2 className={`text-5xl font-display font-extrabold tracking-tight ${color}`}>{value}</h2>
      </div>
      <div className={`opacity-10 absolute -right-6 -bottom-6 transition-transform group-hover:scale-110 ${color}`}>
        {icon}
      </div>
    </div>
  );
}

function SubNodeItem({ title }: { title: string }) {
  return (
    <div className="p-4 bg-surface-low rounded-xl flex justify-between items-center hover:bg-primary-container transition-colors cursor-pointer group">
      <span className="text-sm font-medium text-on-surface group-hover:text-primary-content">{title}</span>
      <ChevronRight className="w-4 h-4 text-on-surface-variant group-hover:text-primary-content" />
    </div>
  );
}

function ResourceItem({ icon, iconBg, title, meta, tag, tagColor, actionIcon }: any) {
  return (
    <div className="group p-4 rounded-xl bg-surface hover:bg-surface-low transition-all flex items-center justify-between">
      <div className="flex items-center gap-4">
        <div className={`w-12 h-12 rounded-xl flex items-center justify-center ${iconBg}`}>
          {icon}
        </div>
        <div>
          <p className="text-sm font-bold text-on-surface mb-1">{title}</p>
          <div className="flex items-center gap-2">
            <span className="text-[10px] text-on-surface-variant font-medium uppercase tracking-wider">{meta}</span>
            <span className="w-1 h-1 rounded-full bg-on-surface-variant/30"></span>
            <span className={`text-[10px] font-bold uppercase tracking-wider ${tagColor}`}>{tag}</span>
          </div>
        </div>
      </div>
      <button className="opacity-0 group-hover:opacity-100 transition-opacity p-2 text-primary-content hover:bg-surface-lowest rounded-lg shadow-sm">
        {actionIcon}
      </button>
    </div>
  );
}

function AuditLogItem({ title, user, time, active = false }: { title: string; user: string; time: string; active?: boolean }) {
  return (
    <div className="flex gap-4 items-start relative pl-8 before:absolute before:left-3 before:top-3 before:bottom-[-24px] last:before:hidden before:w-px before:bg-surface-low">
      <div className={`absolute left-1.5 top-1.5 w-3 h-3 rounded-full ring-4 ring-surface-lowest ${active ? 'bg-primary-content' : 'bg-surface-low'}`}></div>
      <div>
        <p className={`text-sm font-bold ${active ? 'text-on-surface' : 'text-on-surface-variant'}`}>{title}</p>
        <p className="text-[10px] text-on-surface-variant font-medium mt-1 uppercase tracking-wider">{user} • {time}</p>
      </div>
    </div>
  );
}
