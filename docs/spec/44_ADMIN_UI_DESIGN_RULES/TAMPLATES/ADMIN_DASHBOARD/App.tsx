import { RefreshCcw, AlertCircle, LayoutDashboard, Share2, Activity, Wallet } from 'lucide-react';
import Sidebar from './components/Sidebar';
import TopNav from './components/TopNav';
import StatCard from './components/StatCard';
import { QuickAccess, PendingTasks } from './components/QuickAccess';
import { DomainSnapshots, RuntimeSummary } from './components/DomainSnapshots';
import { UserReports, RightPanel } from './components/RightPanel';
import { motion } from 'motion/react';

export default function App() {
  return (
    <div className="min-h-screen bg-surface selection:bg-primary/20">
      <Sidebar />
      
      <main className="md:ml-64 min-h-screen">
        <TopNav />
        
        <div className="p-8 max-w-[1600px] mx-auto">
          {/* Page Header */}
          <div className="flex flex-col md:flex-row justify-between items-start md:items-end mb-10 gap-4">
            <div>
              <h1 className="text-4xl font-extrabold font-headline tracking-tight text-on-surface">
                Administrator Overview
              </h1>
              <p className="text-on-surface-variant font-medium mt-2">
                Cross-domain governance, business snapshot & risk overview
              </p>
            </div>
            <motion.button 
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              className="flex items-center gap-2 bg-surface-container-lowest px-6 py-3 rounded-xl shadow-sm hover:shadow-md transition-all group"
            >
              <RefreshCcw size={18} className="text-primary group-hover:rotate-180 transition-transform duration-500" />
              <span className="font-semibold text-on-surface">Refresh Data</span>
            </motion.button>
          </div>

          <div className="grid grid-cols-12 gap-8">
            {/* Left Column: Main Dashboard Content */}
            <div className="col-span-12 lg:col-span-9 space-y-10">
              
              {/* Top Stats Row */}
              <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6">
                <StatCard 
                  icon={AlertCircle} 
                  label="Pending Tasks" 
                  value="128" 
                  trend="+12 today" 
                />
                <StatCard 
                  icon={AlertCircle} 
                  label="High-Risk Items" 
                  value="14" 
                  trend="Critical" 
                  trendColor="error"
                  isCritical
                />
                <StatCard 
                  icon={Share2} 
                  label="AI Channel Coverage" 
                  value="94.2%" 
                  trendColor="primary"
                  progress={94.2}
                />
                <StatCard 
                  icon={Wallet} 
                  label="7D AI Cost" 
                  value="$12,408" 
                  trend="-5.2%" 
                  trendColor="tertiary"
                />
              </div>

              <QuickAccess />
              
              <PendingTasks />

              <DomainSnapshots />

              <RuntimeSummary />

              <UserReports />
            </div>

            {/* Right Column: Summary Sticky Panel */}
            <div className="col-span-12 lg:col-span-3">
              <div className="sticky top-24">
                <RightPanel />
              </div>
            </div>
          </div>
        </div>
      </main>

      {/* Mobile Bottom Nav */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 h-16 glass-card border-t border-surface-container shadow-2xl flex justify-around items-center px-6 z-50">
        <button className="flex flex-col items-center gap-1 text-primary">
          <LayoutDashboard size={20} />
          <span className="text-[10px] font-bold">Overview</span>
        </button>
        <button className="flex flex-col items-center gap-1 text-slate-400">
          <Activity size={20} />
          <span className="text-[10px] font-bold">Domains</span>
        </button>
        <button className="flex flex-col items-center gap-1 text-slate-400">
          <AlertCircle size={20} />
          <span className="text-[10px] font-bold">Risk</span>
        </button>
        <button className="flex flex-col items-center gap-1 text-slate-400">
          <Activity size={20} />
          <span className="text-[10px] font-bold">Metrics</span>
        </button>
      </nav>
    </div>
  );
}
