import {
  Bell,
  Settings,
  HelpCircle,
  Search,
  CheckCircle2,
  Monitor,
  Users,
  Megaphone,
  LifeBuoy,
  LogOut,
  ChevronRight,
  Share,
  History,
  FileText,
  Video,
  FileArchive,
  Eye,
  Download,
  PlayCircle,
  X,
  Check,
  AlignLeft
} from 'lucide-react';

export default function App() {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 font-sans flex flex-col overflow-hidden">
      {/* Top Navigation */}
      <nav className="fixed top-0 w-full z-50 glass-panel flex justify-between items-center px-6 h-16">
        <div className="flex items-center gap-8">
          <div className="text-xl font-bold tracking-tight text-indigo-600 font-display">
            Luminous Admin
          </div>
          <div className="hidden md:flex items-center space-x-2 bg-slate-100/80 rounded-full px-4 py-2 w-72">
            <Search className="w-4 h-4 text-slate-400" />
            <input
              className="bg-transparent border-none focus:outline-none text-sm w-full placeholder:text-slate-400"
              placeholder="Search operations..."
              type="text"
            />
          </div>
        </div>
        <div className="flex items-center gap-4">
          <button className="p-2 text-slate-500 hover:bg-slate-100 transition-colors rounded-full">
            <Bell className="w-5 h-5" />
          </button>
          <button className="p-2 text-slate-500 hover:bg-slate-100 transition-colors rounded-full">
            <Settings className="w-5 h-5" />
          </button>
          <button className="p-2 text-slate-500 hover:bg-slate-100 transition-colors rounded-full">
            <HelpCircle className="w-5 h-5" />
          </button>
          <div className="h-8 w-8 rounded-full bg-indigo-100 flex items-center justify-center overflow-hidden border-2 border-indigo-200 ml-2">
            <img
              alt="Admin Avatar"
              src="https://lh3.googleusercontent.com/aida-public/AB6AXuCPNBdZLYzfKiQ3obdUS4_Nu-bm9xO3PNS95HwhNmEQvm0wr7M5RnQnE5wHFojPa53NNtFGZR_zeU_goqp8MqXMziId5rj-MYXvgmUHz5L1nnJgxGURXdg-P2JZR7GdPHFo02OC1vBki_bPEwHAGNEm7neoqiNTIH1IIcvJxN7QF_TggloibQT19Ws323gXeOjqd5GeSU3MuODmWo2k3oCjgOuE6JBamsXxsGP-8zrvgKV8RlnNUYGp2NziD05Rb8d1WNxAiACOchg"
              className="w-full h-full object-cover"
              referrerPolicy="no-referrer"
            />
          </div>
        </div>
      </nav>

      <div className="flex flex-1 pt-16 h-screen">
        {/* Sidebar */}
        <aside className="hidden md:flex flex-col w-64 bg-slate-50 py-6 px-4 shrink-0 z-10">
          <div className="px-3 mb-8">
            <h3 className="text-lg font-bold text-slate-900 font-display">Core Operations</h3>
            <p className="text-xs text-slate-500 mt-0.5">Management Suite</p>
          </div>
          <div className="flex-1 space-y-1.5">
            <a href="#" className="flex items-center gap-3 px-3 py-2.5 bg-indigo-50 text-indigo-700 rounded-xl font-medium text-sm transition-all">
              <CheckCircle2 className="w-5 h-5 fill-indigo-100" />
              <span>Certifications</span>
            </a>
            <a href="#" className="flex items-center gap-3 px-3 py-2.5 text-slate-600 hover:bg-slate-100 hover:translate-x-1 rounded-xl font-medium text-sm transition-all">
              <Monitor className="w-5 h-5" />
              <span>Workspaces</span>
            </a>
            <a href="#" className="flex items-center gap-3 px-3 py-2.5 text-slate-600 hover:bg-slate-100 hover:translate-x-1 rounded-xl font-medium text-sm transition-all">
              <Users className="w-5 h-5" />
              <span>Mentors</span>
            </a>
            <a href="#" className="flex items-center gap-3 px-3 py-2.5 text-slate-600 hover:bg-slate-100 hover:translate-x-1 rounded-xl font-medium text-sm transition-all">
              <Megaphone className="w-5 h-5" />
              <span>Notifications</span>
            </a>
          </div>
          <div className="pt-4 space-y-1.5 mt-auto">
            <a href="#" className="flex items-center gap-3 px-3 py-2.5 text-slate-600 hover:bg-slate-100 hover:translate-x-1 rounded-xl font-medium text-sm transition-all">
              <LifeBuoy className="w-5 h-5" />
              <span>Support</span>
            </a>
            <a href="#" className="flex items-center gap-3 px-3 py-2.5 text-slate-600 hover:bg-slate-100 hover:translate-x-1 rounded-xl font-medium text-sm transition-all">
              <LogOut className="w-5 h-5" />
              <span>Logout</span>
            </a>
          </div>
        </aside>

        {/* Main Content Area */}
        <main className="flex-1 flex flex-col lg:flex-row overflow-hidden bg-white rounded-tl-3xl shadow-sm border-t border-l border-slate-200/50">
          
          {/* List Context Panel (Review Queue) */}
          <section className="w-full lg:w-96 bg-slate-50/50 flex flex-col h-full shrink-0 border-r border-slate-100">
            <div className="p-6 pb-4">
              <div className="flex items-center justify-between mb-5">
                <h2 className="text-xl font-bold font-display tracking-tight">Review Queue</h2>
                <span className="bg-indigo-600 text-white text-xs px-2.5 py-1 rounded-full font-bold shadow-sm shadow-indigo-200">12 Pending</span>
              </div>
              <div className="relative">
                <input
                  className="w-full bg-white border-none rounded-xl text-sm py-3 px-4 shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 placeholder:text-slate-400"
                  placeholder="Filter candidates..."
                  type="text"
                />
              </div>
            </div>
            
            <div className="flex-1 overflow-y-auto px-4 pb-6 space-y-3 custom-scrollbar">
              {/* Active Item */}
              <div className="bg-white p-4 rounded-2xl shadow-sm border-l-4 border-indigo-600 cursor-pointer">
                <div className="flex justify-between items-start mb-2">
                  <span className="text-[11px] font-bold text-indigo-600 uppercase tracking-wider">Level 3 Expert</span>
                  <span className="text-[10px] text-slate-400 font-medium">2h ago</span>
                </div>
                <h4 className="font-bold text-slate-900 mb-0.5">Eleanor Fitzwilliam</h4>
                <p className="text-xs text-slate-500 mb-3">Cloud Architecture Portfolio Review</p>
                <div className="flex items-center gap-1.5">
                  <div className="w-5 h-5 rounded-full bg-emerald-100 flex items-center justify-center">
                    <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600 fill-emerald-100" />
                  </div>
                  <span className="text-[11px] font-semibold text-emerald-700">Verified Identity</span>
                </div>
              </div>

              {/* Inactive Items */}
              <div className="hover:bg-white transition-colors p-4 rounded-2xl cursor-pointer">
                <div className="flex justify-between items-start mb-2">
                  <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">Level 2 Adv.</span>
                  <span className="text-[10px] text-slate-400 font-medium">5h ago</span>
                </div>
                <h4 className="font-bold text-slate-900 mb-0.5">Marcus Thorne</h4>
                <p className="text-xs text-slate-500">Cybersecurity Ethics Compliance</p>
              </div>

              <div className="hover:bg-white transition-colors p-4 rounded-2xl cursor-pointer">
                <div className="flex justify-between items-start mb-2">
                  <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">Foundation</span>
                  <span className="text-[10px] text-slate-400 font-medium">Yesterday</span>
                </div>
                <h4 className="font-bold text-slate-900 mb-0.5">Sarah Jenkins</h4>
                <p className="text-xs text-slate-500">Basic Systems Administration</p>
              </div>

              <div className="hover:bg-white transition-colors p-4 rounded-2xl cursor-pointer">
                <div className="flex justify-between items-start mb-2">
                  <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">Level 3 Expert</span>
                  <span className="text-[10px] text-slate-400 font-medium">2 days ago</span>
                </div>
                <h4 className="font-bold text-slate-900 mb-0.5">Julian Chen</h4>
                <p className="text-xs text-slate-500">AI Engineering Capstone</p>
              </div>
            </div>
          </section>

          {/* Detail Area */}
          <section className="flex-1 flex flex-col relative overflow-y-auto bg-white">
            <div className="p-8 lg:p-10 max-w-5xl mx-auto w-full space-y-8 pb-32">
              
              {/* Header Actions */}
              <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
                <div>
                  <nav className="flex items-center gap-1.5 text-sm text-slate-500 mb-3 font-medium">
                    <span>Certifications</span>
                    <ChevronRight className="w-4 h-4 text-slate-400" />
                    <span>Expert Reviews</span>
                  </nav>
                  <h1 className="text-3xl lg:text-4xl font-extrabold font-display tracking-tight text-slate-900">Eleanor Fitzwilliam</h1>
                </div>
                <div className="flex gap-3">
                  <button className="bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold px-4 py-2.5 rounded-xl text-sm transition-all flex items-center gap-2">
                    <Share className="w-4 h-4" /> Share
                  </button>
                  <button className="bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold px-4 py-2.5 rounded-xl text-sm transition-all flex items-center gap-2">
                    <History className="w-4 h-4" /> Full Logs
                  </button>
                </div>
              </div>

              {/* Bento Grid Info Section */}
              <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
                
                {/* Subject Info Card */}
                <div className="xl:col-span-2 bg-white rounded-3xl p-8 shadow-[0_8px_30px_rgb(0,0,0,0.04)] border border-slate-100">
                  <div className="flex items-start justify-between mb-8">
                    <div className="flex items-center gap-5">
                      <img 
                        alt="Candidate Portrait" 
                        className="w-20 h-20 rounded-2xl object-cover shadow-sm" 
                        src="https://lh3.googleusercontent.com/aida-public/AB6AXuB8r3mS4keLtn0hXRNNT9kKIx-xNEUfDsGUyDklY1xi-U8sAgAs1cuUptBmKDEe6fG04OPfjD3IyMx1ool7H5ThMg9a2pK5V5IiS7N8pCbrVOQlrDBnMqkhPDQeX9je9BWCt4R4BK7Y_GwpGstZuKUmmEIieFCTMG8LOXYRpUMQD2qWvc-de0UWIflSHzFUsRg9jPLKHNQ3jUkoZssN3mOU3Gmmd7XB1FONu8_ygsJf7FfyggMEvB7BrMcbP-MADOm6Xf8QLMCN1gw"
                        referrerPolicy="no-referrer"
                      />
                      <div>
                        <h3 className="text-xl font-bold font-display text-slate-900 mb-1">Candidate Profile</h3>
                        <p className="text-sm text-slate-500 font-medium">Senior Solutions Architect @ CloudScale</p>
                      </div>
                    </div>
                    <span className="bg-emerald-100 text-emerald-800 text-[10px] px-3 py-1.5 rounded-full font-bold tracking-wide">LEGACY CERTIFIED</span>
                  </div>
                  
                  <div className="grid grid-cols-2 gap-y-8 gap-x-12">
                    <div>
                      <p className="text-[11px] text-slate-400 font-bold uppercase tracking-wider mb-1.5">Email</p>
                      <p className="text-sm font-semibold text-slate-800">e.fitz@cloudscale.tech</p>
                    </div>
                    <div>
                      <p className="text-[11px] text-slate-400 font-bold uppercase tracking-wider mb-1.5">Experience</p>
                      <p className="text-sm font-semibold text-slate-800">8.5 Years</p>
                    </div>
                    <div>
                      <p className="text-[11px] text-slate-400 font-bold uppercase tracking-wider mb-1.5">Current Score</p>
                      <div className="flex items-center gap-3 mt-1">
                        <div className="flex-1 h-2.5 bg-slate-100 rounded-full overflow-hidden">
                          <div className="h-full bg-gradient-to-r from-indigo-500 to-indigo-400 rounded-full" style={{ width: '92%' }}></div>
                        </div>
                        <span className="text-sm font-bold text-indigo-600">92%</span>
                      </div>
                    </div>
                    <div>
                      <p className="text-[11px] text-slate-400 font-bold uppercase tracking-wider mb-1.5">Location</p>
                      <p className="text-sm font-semibold text-slate-800">London, UK</p>
                    </div>
                  </div>
                </div>

                {/* Submission Info Card */}
                <div className="bg-indigo-600 text-white rounded-3xl p-8 shadow-xl shadow-indigo-200 relative overflow-hidden flex flex-col justify-between">
                  <div className="relative z-10">
                    <h3 className="text-xl font-bold font-display mb-6">Review Detail</h3>
                    <div className="space-y-4">
                      <div className="flex items-start justify-between border-b border-indigo-500/50 pb-3">
                        <span className="text-sm text-indigo-100 font-medium">Reference ID</span>
                        <span className="text-sm font-mono font-bold text-right">CERT-2023-<br/>9981</span>
                      </div>
                      <div className="flex items-start justify-between border-b border-indigo-500/50 pb-3">
                        <span className="text-sm text-indigo-100 font-medium">Submitted</span>
                        <span className="text-sm font-bold text-right">Oct 24,<br/>14:22</span>
                      </div>
                      <div className="flex items-center justify-between border-b border-indigo-500/50 pb-3">
                        <span className="text-sm text-indigo-100 font-medium">Priority</span>
                        <span className="text-[11px] bg-rose-500 text-white px-2.5 py-1 rounded-md font-bold tracking-wide">CRITICAL</span>
                      </div>
                    </div>
                  </div>
                  <div className="relative z-10 pt-6">
                    <p className="text-[11px] text-indigo-200 font-medium mb-1 uppercase tracking-wider">Review SLA Timer</p>
                    <p className="text-3xl font-bold font-display tracking-tight">01:42:15</p>
                  </div>
                  {/* Background Decor */}
                  <div className="absolute -bottom-12 -right-12 w-48 h-48 bg-white/10 rounded-full blur-3xl"></div>
                  <div className="absolute top-0 right-0 w-32 h-32 bg-indigo-400/20 rounded-full blur-2xl"></div>
                </div>
              </div>

              {/* Materials and History Section */}
              <div className="grid grid-cols-1 xl:grid-cols-2 gap-10">
                
                {/* Assets/Material List */}
                <div className="space-y-5">
                  <h3 className="text-xl font-bold font-display text-slate-900">Submission Materials</h3>
                  <div className="space-y-3">
                    <div className="bg-white p-4 rounded-2xl flex items-center justify-between group hover:shadow-[0_4px_20px_rgb(0,0,0,0.04)] transition-all border border-slate-100">
                      <div className="flex items-center gap-4">
                        <div className="w-12 h-12 bg-indigo-50 rounded-xl flex items-center justify-center shrink-0">
                          <FileText className="w-6 h-6 text-indigo-600" />
                        </div>
                        <div>
                          <p className="text-sm font-bold text-slate-900 mb-0.5">Technical Portfolio.pdf</p>
                          <p className="text-[11px] text-slate-500 font-medium">12.4 MB • Application/PDF</p>
                        </div>
                      </div>
                      <div className="flex gap-1 pr-2">
                        <button className="p-2 text-slate-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-colors">
                          <Eye className="w-5 h-5" />
                        </button>
                        <button className="p-2 text-slate-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-colors">
                          <Download className="w-5 h-5" />
                        </button>
                      </div>
                    </div>

                    <div className="bg-white p-4 rounded-2xl flex items-center justify-between group hover:shadow-[0_4px_20px_rgb(0,0,0,0.04)] transition-all border border-slate-100">
                      <div className="flex items-center gap-4">
                        <div className="w-12 h-12 bg-emerald-50 rounded-xl flex items-center justify-center shrink-0">
                          <Video className="w-6 h-6 text-emerald-600" />
                        </div>
                        <div>
                          <p className="text-sm font-bold text-slate-900 mb-0.5">Practical Demonstration.mp4</p>
                          <p className="text-[11px] text-slate-500 font-medium">145 MB • Video/MPEG4</p>
                        </div>
                      </div>
                      <div className="flex gap-1 pr-2">
                        <button className="p-2 text-slate-400 hover:text-emerald-600 hover:bg-emerald-50 rounded-lg transition-colors">
                          <PlayCircle className="w-5 h-5" />
                        </button>
                        <button className="p-2 text-slate-400 hover:text-emerald-600 hover:bg-emerald-50 rounded-lg transition-colors">
                          <Download className="w-5 h-5" />
                        </button>
                      </div>
                    </div>

                    <div className="bg-white p-4 rounded-2xl flex items-center justify-between group hover:shadow-[0_4px_20px_rgb(0,0,0,0.04)] transition-all border border-slate-100">
                      <div className="flex items-center gap-4">
                        <div className="w-12 h-12 bg-amber-50 rounded-xl flex items-center justify-center shrink-0">
                          <FileArchive className="w-6 h-6 text-amber-600" />
                        </div>
                        <div>
                          <p className="text-sm font-bold text-slate-900 mb-0.5">Project Codebase.zip</p>
                          <p className="text-[11px] text-slate-500 font-medium">2.1 MB • Compressed File</p>
                        </div>
                      </div>
                      <div className="flex gap-1 pr-2">
                        <button className="p-2 text-slate-400 hover:text-amber-600 hover:bg-amber-50 rounded-lg transition-colors">
                          <Download className="w-5 h-5" />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>

                {/* History Timeline */}
                <div className="space-y-5">
                  <h3 className="text-xl font-bold font-display text-slate-900">History Timeline</h3>
                  <div className="relative pl-6 space-y-8 border-l-2 border-slate-100 ml-3 mt-2">
                    
                    <div className="relative">
                      <div className="absolute -left-[33px] top-1 w-4 h-4 bg-indigo-600 rounded-full ring-4 ring-white shadow-sm"></div>
                      <div>
                        <p className="text-[11px] font-bold text-indigo-600 mb-1 tracking-wide">Oct 24, 14:22</p>
                        <p className="text-sm font-bold text-slate-900 mb-1">Application Submitted</p>
                        <p className="text-xs text-slate-500 leading-relaxed">System received final assets for Level 3 Review.</p>
                      </div>
                    </div>

                    <div className="relative">
                      <div className="absolute -left-[33px] top-1 w-4 h-4 bg-emerald-500 rounded-full ring-4 ring-white shadow-sm"></div>
                      <div>
                        <p className="text-[11px] font-bold text-emerald-600 mb-1 tracking-wide">Oct 24, 11:05</p>
                        <p className="text-sm font-bold text-slate-900 mb-1">Identity Verified</p>
                        <p className="text-xs text-slate-500 leading-relaxed">Biometric scan and ID check passed automatically.</p>
                      </div>
                    </div>

                    <div className="relative">
                      <div className="absolute -left-[33px] top-1 w-4 h-4 bg-slate-300 rounded-full ring-4 ring-white shadow-sm"></div>
                      <div>
                        <p className="text-[11px] font-bold text-slate-500 mb-1 tracking-wide">Oct 20, 09:15</p>
                        <p className="text-sm font-bold text-slate-900 mb-1">Certification Path Initialized</p>
                        <p className="text-xs text-slate-500 leading-relaxed">User started the Cloud Architect expert track.</p>
                      </div>
                    </div>

                  </div>
                </div>

              </div>
            </div>

            {/* Fixed Audit Operation Area */}
            <div className="absolute bottom-0 w-full glass-panel border-t border-slate-200/50 p-5 lg:p-6 flex items-center justify-center z-20">
              <div className="max-w-5xl w-full flex flex-col md:flex-row items-center gap-4 lg:gap-6">
                <div className="flex-1 relative w-full">
                  <AlignLeft className="absolute left-4 top-3.5 w-5 h-5 text-slate-400" />
                  <textarea 
                    className="w-full bg-slate-100/80 border-none rounded-2xl text-sm py-3.5 pl-12 pr-4 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 resize-none h-12 flex items-center placeholder:text-slate-400 font-medium" 
                    placeholder="Add audit notes or reasoning for the candidate..."
                  ></textarea>
                </div>
                <div className="flex gap-3 w-full md:w-auto shrink-0">
                  <button className="flex-1 md:flex-none px-6 py-3.5 bg-rose-600 text-white font-bold rounded-2xl hover:bg-rose-700 transition-all flex items-center justify-center gap-2 shadow-sm shadow-rose-200">
                    <X className="w-5 h-5" /> Reject
                  </button>
                  <button className="flex-1 md:flex-none px-6 py-3.5 bg-gradient-to-r from-indigo-500 to-indigo-600 text-white font-bold rounded-2xl shadow-lg shadow-indigo-200 hover:scale-[1.02] active:scale-[0.98] transition-all flex items-center justify-center gap-2">
                    <CheckCircle2 className="w-5 h-5 fill-indigo-400 text-white" /> Approve Candidate
                  </button>
                </div>
              </div>
            </div>
          </section>
        </main>
      </div>
    </div>
  );
}
