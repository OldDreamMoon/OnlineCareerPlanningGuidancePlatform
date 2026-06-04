import { useState } from "react";
import { motion } from "motion/react";
import {
  Rocket,
  LayoutDashboard,
  Network,
  Route,
  Terminal,
  CircleDollarSign,
  FileText,
  Book,
  HelpCircle,
  Search,
  Bell,
  Settings,
  Plus,
  PlayCircle,
  CheckCircle2,
  Server,
  Cpu,
  Filter,
  Download,
  Edit2,
  Trash2,
} from "lucide-react";
import { cn } from "./lib/utils";

export default function App() {
  return (
    <div className="min-h-screen bg-surface flex font-sans">
      <Sidebar />
      <div className="flex-1 flex flex-col min-w-0">
        <TopNav />
        <main className="flex-1 pt-24 pb-12 px-4 sm:px-8 overflow-y-auto">
          <div className="max-w-7xl mx-auto space-y-8">
            <PageHeader />
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              <HitPreview />
              <ResolutionResult />
            </div>
            <RoutingTable />
          </div>
        </main>
      </div>
      
      {/* Mobile FAB */}
      <button className="fixed bottom-8 right-8 w-14 h-14 bg-gradient-to-br from-primary to-primary-container text-slate-900 rounded-full shadow-luminous flex items-center justify-center hover:scale-110 transition-transform active:scale-95 z-50 md:hidden">
        <Plus className="w-6 h-6" />
      </button>
    </div>
  );
}

function Sidebar() {
  const navItems = [
    { icon: Rocket, label: "Operations" },
    { icon: LayoutDashboard, label: "Dashboard" },
    { icon: Network, label: "Providers" },
    { icon: Route, label: "Routing", active: true },
    { icon: Terminal, label: "Prompts" },
    { icon: CircleDollarSign, label: "Costs" },
    { icon: FileText, label: "Logs" },
  ];

  return (
    <aside className="w-64 h-screen sticky top-0 bg-surface-low flex-col py-4 z-40 hidden md:flex pt-20">
      <div className="px-6 mb-8">
        <h2 className="text-lg font-bold text-slate-900 font-display">AI Management</h2>
        <p className="text-xs text-slate-500 font-medium mt-1">Graduation Project</p>
      </div>
      <nav className="flex-1 space-y-1 px-2">
        {navItems.map((item) => (
          <a
            key={item.label}
            href="#"
            className={cn(
              "flex items-center gap-3 px-4 py-3 rounded-xl transition-all",
              item.active
                ? "bg-surface-lowest text-cyan-600 shadow-sm"
                : "text-slate-600 hover:bg-slate-200/50"
            )}
          >
            <item.icon className="w-5 h-5" />
            <span className="font-semibold text-sm">{item.label}</span>
          </a>
        ))}
      </nav>
      <div className="mt-auto px-2 space-y-1 pt-4 border-t border-slate-200/50">
        <a href="#" className="flex items-center gap-3 px-4 py-3 text-slate-600 hover:bg-slate-200/50 rounded-xl transition-all">
          <Book className="w-5 h-5" />
          <span className="font-semibold text-sm">Docs</span>
        </a>
        <a href="#" className="flex items-center gap-3 px-4 py-3 text-slate-600 hover:bg-slate-200/50 rounded-xl transition-all">
          <HelpCircle className="w-5 h-5" />
          <span className="font-semibold text-sm">Support</span>
        </a>
      </div>
    </aside>
  );
}

function TopNav() {
  return (
    <header className="fixed top-0 right-0 left-0 md:left-64 z-50 glass-nav flex justify-between items-center px-6 h-16">
      <div className="flex items-center gap-4 md:hidden">
        <span className="text-xl font-black text-slate-900 font-display tracking-tight">Luminous</span>
      </div>
      <div className="hidden md:flex items-center gap-8 font-display font-bold tracking-tight">
        <a href="#" className="text-slate-500 hover:text-slate-900 transition-colors">Operations</a>
        <a href="#" className="text-slate-500 hover:text-slate-900 transition-colors">Dashboard</a>
        <a href="#" className="text-cyan-600 border-b-2 border-cyan-500 py-5">Routing</a>
        <a href="#" className="text-slate-500 hover:text-slate-900 transition-colors">Prompts</a>
      </div>
      <div className="flex items-center gap-4 ml-auto">
        <div className="relative hidden sm:block">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <input
            type="text"
            placeholder="Search routes..."
            className="bg-surface-low border-none rounded-full pl-10 pr-4 py-2 text-sm focus:ring-2 focus:ring-primary outline-none transition-all focus:bg-surface-lowest w-64"
          />
        </div>
        <button className="text-slate-500 hover:bg-slate-100 p-2 rounded-full transition-colors">
          <Bell className="w-5 h-5" />
        </button>
        <button className="text-slate-500 hover:bg-slate-100 p-2 rounded-full transition-colors">
          <Settings className="w-5 h-5" />
        </button>
        <img
          src="https://lh3.googleusercontent.com/aida-public/AB6AXuAT93Qj3T7YoV-xWIxRN9I8l04rbtx7PvnUv1-lTk7gfukyrl59ci4HqLSyXPH_P7vzcIXg5-ZIGBRT6_8v4igKontugpfEpMDqVZaa3nVFk8hQXjSwVhVBsOExNvQYHRMwqFgAaipnDQEIUzIuIBMPRwgbC-YFcfZiVCCHrh_ozkCQI2pPlvcHbSim5dohAQFG8ailYVCxsaTd6RrCwrdsxZBQKCZUF2NlDkr89AqpWSsUqULzGzfJFOfQ1bVwThzwTDhterwO0As"
          alt="User"
          className="w-8 h-8 rounded-full border-2 border-primary-container object-cover"
        />
      </div>
    </header>
  );
}

function PageHeader() {
  return (
    <div className="flex flex-col sm:flex-row justify-between items-start sm:items-end gap-4">
      <div>
        <h1 className="text-3xl font-extrabold text-slate-900 tracking-tight font-display">Routing Rules</h1>
        <p className="text-slate-500 mt-1 font-medium">Manage traffic distribution and model fallbacks across your AI providers.</p>
      </div>
      <motion.button
        whileHover={{ scale: 1.02 }}
        whileTap={{ scale: 0.98 }}
        className="hidden sm:flex bg-gradient-to-br from-primary to-primary-container text-slate-900 px-6 py-2.5 rounded-full font-bold items-center gap-2 shadow-luminous"
      >
        <Plus className="w-5 h-5" />
        New Rule
      </motion.button>
    </div>
  );
}

function HitPreview() {
  const [pref, setPref] = useState("fastest");

  return (
    <motion.div 
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="lg:col-span-7 bg-surface-lowest p-6 sm:p-8 rounded-3xl shadow-luminous"
    >
      <div className="flex items-center gap-3 mb-8">
        <div className="p-2 bg-primary/10 rounded-xl">
          <PlayCircle className="w-6 h-6 text-primary" />
        </div>
        <h3 className="text-xl font-bold font-display text-slate-900">Hit Preview</h3>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="space-y-2">
          <label className="text-xs font-bold text-slate-500 uppercase tracking-wider">Task Type</label>
          <select className="w-full bg-surface-low border-none rounded-xl py-3.5 px-4 focus:ring-2 focus:ring-primary appearance-none outline-none font-medium text-slate-700">
            <option>Chat Completion</option>
            <option>Text Embedding</option>
            <option>Image Generation</option>
          </select>
        </div>
        <div className="space-y-2">
          <label className="text-xs font-bold text-slate-500 uppercase tracking-wider">Scene Code</label>
          <input
            type="text"
            placeholder="e.g. customer_support_v1"
            className="w-full bg-surface-low border-none rounded-xl py-3.5 px-4 focus:ring-2 focus:ring-primary outline-none font-medium text-slate-700 placeholder:text-slate-400"
          />
        </div>
        <div className="md:col-span-2 space-y-3">
          <label className="text-xs font-bold text-slate-500 uppercase tracking-wider">Model Preference</label>
          <div className="flex flex-col sm:flex-row gap-4">
            {[
              { id: "fastest", label: "Fastest Response" },
              { id: "lowest", label: "Lowest Cost" },
              { id: "highest", label: "Highest Quality" },
            ].map((option) => (
              <button
                key={option.id}
                onClick={() => setPref(option.id)}
                className={cn(
                  "flex-1 py-3.5 px-4 text-center rounded-xl font-semibold text-sm transition-all border-2",
                  pref === option.id
                    ? "border-primary bg-primary/5 text-primary"
                    : "border-transparent bg-surface-low text-slate-600 hover:bg-slate-200/50"
                )}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      <button className="w-full mt-8 py-4 bg-primary/10 text-primary font-bold rounded-xl hover:bg-primary/20 transition-colors flex items-center justify-center gap-2">
        <PlayCircle className="w-5 h-5" />
        Simulate Route Resolution
      </button>
    </motion.div>
  );
}

function ResolutionResult() {
  return (
    <motion.div 
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.1 }}
      className="lg:col-span-5 bg-surface-low p-6 sm:p-8 rounded-3xl flex flex-col"
    >
      <div className="flex items-center justify-between mb-8">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-secondary/10 rounded-xl">
            <CheckCircle2 className="w-6 h-6 text-secondary" />
          </div>
          <h3 className="text-xl font-bold font-display text-slate-900">Resolution Result</h3>
        </div>
        <span className="px-3 py-1.5 bg-secondary/20 text-secondary text-[10px] font-bold rounded-md uppercase tracking-widest">
          Hit Success
        </span>
      </div>

      <div className="flex-1 space-y-4">
        <div className="flex items-start gap-4 p-5 bg-surface-lowest rounded-2xl shadow-sm">
          <div className="bg-primary/10 p-3 rounded-xl">
            <Server className="w-6 h-6 text-primary" />
          </div>
          <div>
            <p className="text-xs text-slate-500 font-bold uppercase tracking-wider mb-1">Selected Provider</p>
            <p className="text-lg font-bold text-slate-900">Anthropic Cloud</p>
          </div>
        </div>

        <div className="flex items-start gap-4 p-5 bg-surface-lowest rounded-2xl shadow-sm relative overflow-hidden">
          <div className="absolute left-0 top-0 bottom-0 w-1.5 bg-secondary"></div>
          <div className="bg-secondary/10 p-3 rounded-xl">
            <Cpu className="w-6 h-6 text-secondary" />
          </div>
          <div>
            <p className="text-xs text-slate-500 font-bold uppercase tracking-wider mb-1">Resolved Model</p>
            <p className="text-lg font-bold text-slate-900">Claude 3.5 Sonnet</p>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div className="p-5 bg-surface-lowest rounded-2xl shadow-sm">
            <p className="text-xs text-slate-500 font-bold uppercase tracking-wider mb-1">Latency (Est.)</p>
            <p className="text-2xl font-bold text-slate-900">140ms</p>
          </div>
          <div className="p-5 bg-surface-lowest rounded-2xl shadow-sm">
            <p className="text-xs text-slate-500 font-bold uppercase tracking-wider mb-1">Cost / 1k</p>
            <p className="text-2xl font-bold text-slate-900">$0.003</p>
          </div>
        </div>
      </div>
    </motion.div>
  );
}

function RoutingTable() {
  const rules = [
    {
      code: "RT-CHAT-001",
      type: "Chat Completion",
      scene: "Global Default",
      provider: { name: "Anthropic", initial: "A", color: "bg-slate-800" },
      model: "Claude 3.5 Sonnet",
      priority: { label: "High (1)", color: "text-secondary" },
      status: "Active",
    },
    {
      code: "RT-CHAT-002",
      type: "Chat Completion",
      scene: "Customer_Support",
      provider: { name: "OpenAI", initial: "O", color: "bg-blue-600" },
      model: "GPT-4o",
      priority: { label: "Normal (2)", color: "text-slate-500" },
      status: "Active",
    },
    {
      code: "RT-EMBED-01",
      type: "Text Embedding",
      scene: "Vector_Search",
      provider: { name: "Cohere", initial: "C", color: "bg-orange-500" },
      model: "embed-english-v3.0",
      priority: { label: "Normal (2)", color: "text-slate-500" },
      status: "Disabled",
    },
    {
      code: "RT-IMG-042",
      type: "Image Gen",
      scene: "Creative_Studio",
      provider: { name: "Stability AI", initial: "S", color: "bg-purple-600" },
      model: "SDXL 1.0",
      priority: { label: "Low (5)", color: "text-tertiary" },
      status: "Active",
    },
  ];

  return (
    <motion.section 
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.2 }}
      className="space-y-6"
    >
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold font-display text-slate-900">Active Routing Rules</h2>
        <div className="flex gap-3">
          <button className="p-2.5 bg-surface-low rounded-xl text-slate-500 hover:bg-slate-200 transition-colors">
            <Filter className="w-5 h-5" />
          </button>
          <button className="p-2.5 bg-surface-low rounded-xl text-slate-500 hover:bg-slate-200 transition-colors">
            <Download className="w-5 h-5" />
          </button>
        </div>
      </div>

      <div className="bg-surface-lowest rounded-3xl shadow-luminous overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse min-w-[800px]">
            <thead>
              <tr className="bg-surface-low/50">
                <th className="px-6 py-5 text-xs font-bold text-slate-500 uppercase tracking-wider">Route Code</th>
                <th className="px-6 py-5 text-xs font-bold text-slate-500 uppercase tracking-wider">Task Type</th>
                <th className="px-6 py-5 text-xs font-bold text-slate-500 uppercase tracking-wider">Scene</th>
                <th className="px-6 py-5 text-xs font-bold text-slate-500 uppercase tracking-wider">Provider</th>
                <th className="px-6 py-5 text-xs font-bold text-slate-500 uppercase tracking-wider">Model</th>
                <th className="px-6 py-5 text-xs font-bold text-slate-500 uppercase tracking-wider text-center">Priority</th>
                <th className="px-6 py-5 text-xs font-bold text-slate-500 uppercase tracking-wider">Status</th>
                <th className="px-6 py-5"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rules.map((rule, idx) => (
                <tr key={idx} className="hover:bg-slate-50/80 transition-colors group">
                  <td className="px-6 py-5">
                    <span className="font-mono text-sm font-bold text-primary bg-primary/10 px-2 py-1 rounded-md">{rule.code}</span>
                  </td>
                  <td className="px-6 py-5 text-sm font-semibold text-slate-900">{rule.type}</td>
                  <td className="px-6 py-5">
                    <span className="px-3 py-1.5 bg-surface-low rounded-full text-xs font-bold text-slate-600">
                      {rule.scene}
                    </span>
                  </td>
                  <td className="px-6 py-5">
                    <div className="flex items-center gap-3">
                      <div className={cn("w-7 h-7 rounded-lg flex items-center justify-center text-xs text-white font-bold", rule.provider.color)}>
                        {rule.provider.initial}
                      </div>
                      <span className="text-sm font-bold text-slate-900">{rule.provider.name}</span>
                    </div>
                  </td>
                  <td className="px-6 py-5 text-sm font-medium text-slate-700">{rule.model}</td>
                  <td className="px-6 py-5 text-center">
                    <span className={cn("text-xs font-bold", rule.priority.color)}>{rule.priority.label}</span>
                  </td>
                  <td className="px-6 py-5">
                    <span className={cn(
                      "flex items-center gap-2 text-xs font-bold uppercase tracking-wider",
                      rule.status === "Active" ? "text-secondary" : "text-slate-400"
                    )}>
                      <span className={cn(
                        "w-2 h-2 rounded-full",
                        rule.status === "Active" ? "bg-secondary" : "bg-slate-300"
                      )}></span>
                      {rule.status}
                    </span>
                  </td>
                  <td className="px-6 py-5 text-right opacity-0 group-hover:opacity-100 transition-opacity">
                    <div className="flex justify-end gap-2">
                      <button className="p-2 text-slate-400 hover:text-primary hover:bg-primary/10 rounded-lg transition-colors">
                        <Edit2 className="w-4 h-4" />
                      </button>
                      <button className="p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors">
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="px-6 py-4 bg-surface-low/30 flex justify-between items-center border-t border-slate-100">
          <span className="text-sm font-medium text-slate-500">Showing 4 of 24 rules</span>
          <div className="flex gap-2">
            <button className="px-4 py-2 text-sm font-bold text-slate-400 hover:text-slate-600 disabled:opacity-50 transition-colors" disabled>
              Previous
            </button>
            <button className="px-4 py-2 text-sm font-bold text-primary hover:bg-primary/10 rounded-xl transition-colors">
              Next
            </button>
          </div>
        </div>
      </div>
    </motion.section>
  );
}
