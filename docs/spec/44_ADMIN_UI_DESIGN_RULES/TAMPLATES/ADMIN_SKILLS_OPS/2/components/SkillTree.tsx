import { ChevronDown, ChevronRight, Folder, FileText, Plus } from 'lucide-react';

export default function SkillTree() {
  return (
    <div className="w-80 h-screen overflow-y-auto bg-surface-low sticky top-0 p-6">
      <div className="flex items-center justify-between mb-6">
        <h3 className="text-xs font-bold text-on-surface uppercase tracking-wider">Skill Tree</h3>
        <button className="text-primary-content hover:bg-primary-container p-1.5 rounded-lg transition-colors">
          <Plus className="w-4 h-4" />
        </button>
      </div>

      <div className="space-y-2">
        {/* Root Node 1 */}
        <div className="space-y-1">
          <div className="flex items-center gap-2 px-2 py-2 rounded-lg hover:bg-surface-lowest cursor-pointer transition-colors">
            <ChevronDown className="text-on-surface-variant w-4 h-4" />
            <Folder className="text-primary-content w-4 h-4" />
            <span className="text-sm font-medium text-on-surface">Frontend Development</span>
          </div>
          {/* L2 Nodes */}
          <div className="ml-6 space-y-1">
            <div className="flex items-center gap-2 px-2 py-2 rounded-lg hover:bg-surface-lowest cursor-pointer transition-colors">
              <ChevronDown className="text-on-surface-variant w-4 h-4" />
              <Folder className="text-primary-content w-4 h-4" />
              <span className="text-sm font-medium text-on-surface">Frameworks</span>
            </div>
            {/* L3 Nodes (Selected) */}
            <div className="ml-6 space-y-1">
              <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-surface-lowest shadow-sm text-primary-content cursor-pointer">
                <FileText className="w-4 h-4" />
                <span className="text-sm font-bold">React Hooks</span>
              </div>
              <div className="flex items-center gap-2 px-3 py-2 rounded-lg hover:bg-surface-lowest text-on-surface-variant cursor-pointer transition-colors">
                <FileText className="w-4 h-4" />
                <span className="text-sm font-medium">Vue Composition API</span>
              </div>
            </div>
            <div className="flex items-center gap-2 px-2 py-2 rounded-lg hover:bg-surface-lowest cursor-pointer transition-colors">
              <ChevronRight className="text-on-surface-variant w-4 h-4" />
              <Folder className="text-primary-content w-4 h-4" />
              <span className="text-sm font-medium text-on-surface">Web Performance</span>
            </div>
          </div>
        </div>

        {/* Root Node 2 */}
        <div className="flex items-center gap-2 px-2 py-2 rounded-lg hover:bg-surface-lowest cursor-pointer transition-colors mt-2">
          <ChevronRight className="text-on-surface-variant w-4 h-4" />
          <Folder className="text-on-surface-variant w-4 h-4" />
          <span className="text-sm font-medium text-on-surface">Backend Systems</span>
        </div>

        {/* Root Node 3 */}
        <div className="flex items-center gap-2 px-2 py-2 rounded-lg hover:bg-surface-lowest cursor-pointer transition-colors mt-2">
          <ChevronRight className="text-on-surface-variant w-4 h-4" />
          <Folder className="text-secondary w-4 h-4" />
          <span className="text-sm font-medium text-on-surface">UI/UX Design</span>
        </div>
      </div>
    </div>
  );
}
