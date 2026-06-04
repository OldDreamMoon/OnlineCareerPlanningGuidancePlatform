import { 
  LayoutDashboard, 
  Grid3X3, 
  ShieldAlert, 
  CreditCard, 
  BrainCircuit, 
  Terminal, 
  LifeBuoy, 
  LogOut,
  Diamond
} from 'lucide-react';
import { motion } from 'motion/react';

export default function Sidebar() {
  const navItems = [
    { icon: LayoutDashboard, label: 'Overview', active: true },
    { icon: Grid3X3, label: 'Domain Snapshots' },
    { icon: ShieldAlert, label: 'Risk Engine' },
    { icon: CreditCard, label: 'Cost Analysis' },
    { icon: BrainCircuit, label: 'AI Insights' },
    { icon: Terminal, label: 'System Logs' },
  ];

  return (
    <aside className="fixed left-0 top-0 h-screen w-64 bg-slate-50 flex flex-col p-4 space-y-2 z-[60] hidden md:flex">
      <div className="mb-8 px-2 flex items-center gap-3">
        <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary to-primary-container flex items-center justify-center text-white shadow-lg">
          <Diamond size={20} fill="currentColor" />
        </div>
        <div>
          <h2 className="text-xl font-bold text-slate-900 font-headline">Governance Pro</h2>
          <p className="text-[10px] uppercase tracking-widest text-slate-500 font-bold">Enterprise Tier</p>
        </div>
      </div>

      <nav className="flex-1 space-y-1">
        {navItems.map((item) => (
          <a
            key={item.label}
            href="#"
            className={`flex items-center gap-3 px-4 py-3 rounded-xl transition-all font-semibold text-sm ${
              item.active 
                ? 'bg-white text-primary shadow-sm translate-x-1' 
                : 'text-slate-500 hover:bg-indigo-50 hover:text-primary'
            }`}
          >
            <item.icon size={18} />
            <span>{item.label}</span>
          </a>
        ))}
      </nav>

      <div className="mt-auto pt-6 border-t border-slate-200 space-y-1">
        <motion.button 
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          className="w-full mb-4 py-3 px-4 bg-gradient-to-r from-primary to-primary-container text-white rounded-xl font-bold text-sm shadow-md hover:shadow-lg transition-all"
        >
          Generate Report
        </motion.button>
        
        <a href="#" className="flex items-center gap-3 px-4 py-3 text-slate-500 hover:text-primary transition-colors font-semibold text-sm">
          <LifeBuoy size={18} />
          <span>Support</span>
        </a>
        <a href="#" className="flex items-center gap-3 px-4 py-3 text-slate-500 hover:text-error transition-colors font-semibold text-sm">
          <LogOut size={18} />
          <span>Sign Out</span>
        </a>
      </div>
    </aside>
  );
}
