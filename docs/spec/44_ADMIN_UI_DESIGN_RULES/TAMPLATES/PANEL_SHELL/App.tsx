import { 
  LayoutDashboard, 
  LineChart, 
  Scale, 
  FileText, 
  Users, 
  LogOut, 
  Search, 
  Bell, 
  Settings, 
  Plus, 
  Diamond,
  BarChart3,
  PieChart
} from 'lucide-react';

export default function App() {
  return (
    <div className="flex h-screen w-full bg-surface">
      {/* Background Gradient Foundation */}
      <div className="fixed inset-0 z-0 pointer-events-none">
        <div className="absolute top-0 right-0 w-[800px] h-[800px] bg-gradient-to-b from-[#63dbf2]/10 to-transparent rounded-full blur-[120px]"></div>
      </div>

      {/* Sidebar */}
      <aside className="relative z-50 h-screen w-64 flex flex-col p-4 space-y-2 bg-surface-container-lowest rounded-r-2xl shadow-[4px_0_24px_rgba(44,47,49,0.02)] font-headline text-sm font-medium">
        {/* Sidebar Header */}
        <div className="flex items-center gap-3 px-2 py-6 mb-4">
          <div className="w-10 h-10 rounded-xl bg-primary flex items-center justify-center text-white shadow-lg shadow-primary/30">
            <Diamond size={20} className="fill-current" />
          </div>
          <div>
            <h2 className="text-xl font-black text-on-surface leading-tight">Admin Panel</h2>
            <p className="text-[10px] uppercase tracking-widest text-on-surface-variant font-bold">Enterprise Suite</p>
          </div>
        </div>

        {/* Navigation Links */}
        <nav className="flex-1 space-y-1 overflow-y-auto no-scrollbar">
          <a href="#" className="flex items-center gap-3 px-4 py-3 bg-surface text-primary shadow-sm rounded-xl hover:translate-x-1 transition-transform duration-200">
            <LayoutDashboard size={20} />
            <span>Overview</span>
          </a>
          <a href="#" className="flex items-center gap-3 px-4 py-3 text-on-surface-variant hover:bg-surface-container-low rounded-xl hover:translate-x-1 transition-transform duration-200">
            <LineChart size={20} />
            <span>Analytics</span>
          </a>
          <a href="#" className="flex items-center gap-3 px-4 py-3 text-on-surface-variant hover:bg-surface-container-low rounded-xl hover:translate-x-1 transition-transform duration-200">
            <Scale size={20} />
            <span>Governance</span>
          </a>
          <a href="#" className="flex items-center gap-3 px-4 py-3 text-on-surface-variant hover:bg-surface-container-low rounded-xl hover:translate-x-1 transition-transform duration-200">
            <FileText size={20} />
            <span>Reports</span>
          </a>
          <a href="#" className="flex items-center gap-3 px-4 py-3 text-on-surface-variant hover:bg-surface-container-low rounded-xl hover:translate-x-1 transition-transform duration-200">
            <Users size={20} />
            <span>Users</span>
          </a>
        </nav>

        {/* Sidebar Footer */}
        <div className="pt-4 mt-auto border-t border-outline-variant/20">
          <a href="#" className="flex items-center gap-3 px-4 py-3 text-on-surface-variant hover:bg-surface-container-low rounded-xl transition-all duration-200">
            <LogOut size={20} />
            <span>Logout</span>
          </a>
          <div className="flex items-center gap-3 mt-4 px-2">
            <img 
              src="https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?ixlib=rb-1.2.1&auto=format&fit=facearea&facepad=2&w=256&h=256&q=80" 
              alt="Admin User" 
              className="w-9 h-9 rounded-full border-2 border-surface-container-lowest shadow-sm object-cover"
            />
            <div className="flex flex-col">
              <span className="text-xs font-bold text-on-surface">Admin User</span>
              <span className="text-[10px] text-on-surface-variant">Super Admin</span>
            </div>
          </div>
        </div>
      </aside>

      {/* Main Content Wrapper */}
      <main className="flex-1 flex flex-col min-w-0 overflow-hidden relative z-10">
        {/* TopAppBar */}
        <header className="w-full sticky top-0 z-40 bg-surface/70 backdrop-blur-xl flex items-center justify-between px-8 py-4">
          {/* Brand & Search */}
          <div className="flex items-center gap-8">
            <span className="text-2xl font-bold tracking-tight text-primary font-headline">Luminous</span>
            
            {/* Search Bar */}
            <div className="hidden md:flex items-center bg-surface-container-lowest rounded-2xl px-4 py-2.5 w-96 group focus-within:shadow-[0_10px_40px_rgba(44,47,49,0.06)] transition-all border border-transparent focus-within:border-primary/20">
              <Search size={18} className="text-outline group-focus-within:text-primary transition-colors" />
              <input 
                type="text" 
                placeholder="Search resources..." 
                className="bg-transparent border-none focus:outline-none focus:ring-0 text-sm font-body w-full placeholder:text-outline ml-3"
              />
              <kbd className="hidden sm:flex items-center justify-center px-2 py-1 text-[10px] font-bold text-outline bg-surface-container-low rounded-lg ml-2">⌘K</kbd>
            </div>
          </div>

          {/* Actions Right Group */}
          <div className="flex items-center gap-4">
            <button className="p-2.5 rounded-xl text-on-surface-variant hover:text-on-surface hover:bg-surface-container-lowest transition-colors relative">
              <Bell size={20} />
              <span className="absolute top-2.5 right-2.5 w-2 h-2 bg-error rounded-full border-2 border-surface-container-lowest"></span>
            </button>
            <button className="p-2.5 rounded-xl text-on-surface-variant hover:text-on-surface hover:bg-surface-container-lowest transition-colors">
              <Settings size={20} />
            </button>
            
            {/* Profile Image */}
            <div className="ml-2 pl-6 border-l border-outline-variant/20 flex items-center gap-3">
              <div className="flex flex-col items-end hidden sm:flex">
                <span className="text-sm font-bold text-on-surface font-headline">Alex Rivera</span>
                <span className="text-[10px] text-primary font-extrabold uppercase tracking-widest">Pro Plan</span>
              </div>
              <img 
                src="https://images.unsplash.com/photo-1500648767791-00dcc994a43e?ixlib=rb-1.2.1&auto=format&fit=facearea&facepad=2.2&w=256&h=256&q=80" 
                alt="Alex Rivera" 
                className="w-10 h-10 rounded-full ring-2 ring-surface-container-lowest shadow-sm object-cover"
              />
            </div>
          </div>
        </header>

        {/* Page Content Area */}
        <div className="flex-1 overflow-auto p-8 relative">
          {/* Background Decorative Element */}
          <div className="absolute inset-0 pointer-events-none opacity-50 overflow-hidden">
            <div className="absolute -top-24 -right-24 w-[500px] h-[500px] bg-primary-container/20 rounded-full blur-[100px]"></div>
            <div className="absolute bottom-48 -left-24 w-[400px] h-[400px] bg-secondary-container/20 rounded-full blur-[80px]"></div>
          </div>
          
          <div className="relative z-10 w-full h-full flex items-center justify-center">
            <div className="max-w-2xl w-full flex flex-col items-center text-center">
              <div className="w-24 h-24 mb-8 rounded-[2rem] bg-surface-container-lowest shadow-[0_10px_40px_rgba(44,47,49,0.06)] flex items-center justify-center">
                <LayoutDashboard size={40} className="text-primary-container" />
              </div>
              <h1 className="font-headline font-extrabold text-4xl text-on-surface tracking-tight mb-4">
                Page Content Area
              </h1>
              <p className="font-body text-on-surface-variant text-lg max-w-md leading-relaxed">
                Your high-performance dashboard modules and analytical bento-grids will appear here. Start building with Luminous components.
              </p>
              
              {/* Empty State Guide (Ghost Cards) */}
              <div className="grid grid-cols-2 gap-6 mt-12 w-full max-w-lg">
                <div className="h-32 rounded-3xl border-2 border-dashed border-outline-variant/30 flex items-center justify-center bg-surface-container-low/50 hover:bg-surface-container-low transition-colors cursor-pointer group">
                  <BarChart3 size={32} className="text-outline-variant/50 group-hover:text-primary/50 transition-colors" />
                </div>
                <div className="h-32 rounded-3xl border-2 border-dashed border-outline-variant/30 flex items-center justify-center bg-surface-container-low/50 hover:bg-surface-container-low transition-colors cursor-pointer group">
                  <PieChart size={32} className="text-outline-variant/50 group-hover:text-primary/50 transition-colors" />
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Contextual FAB */}
        <button className="fixed bottom-8 right-8 w-16 h-16 bg-gradient-to-br from-primary to-primary-dim text-white rounded-full shadow-[0_10px_40px_rgba(70,71,211,0.3)] flex items-center justify-center hover:scale-105 active:scale-95 transition-all duration-300 z-50">
          <Plus size={28} strokeWidth={2.5} />
        </button>
      </main>
    </div>
  );
}
