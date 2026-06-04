import React from 'react';
import {
  History, Calendar, CalendarCheck, RefreshCw, Briefcase, Package,
  AlertTriangle, CheckCircle2, XCircle, User, ShieldCheck, Banknote,
  Save, ChevronRight, Star, Clock
} from 'lucide-react';

const ProfileHeader = () => (
  <div className="bg-white rounded-[2rem] p-8 shadow-sm border border-slate-100 relative overflow-hidden">
    {/* Decorative background circle */}
    <div className="absolute -top-32 -right-32 w-[30rem] h-[30rem] bg-indigo-50/60 rounded-full blur-3xl pointer-events-none"></div>
    
    <div className="flex flex-col xl:flex-row gap-8 relative z-10">
      {/* Avatar Section */}
      <div className="relative shrink-0">
        <div className="w-44 h-44 rounded-3xl overflow-hidden shadow-lg border-4 border-white">
          <img src="https://images.unsplash.com/photo-1560250097-0b93528c311a?q=80&w=256&auto=format&fit=crop" alt="Alex Strathmore" className="w-full h-full object-cover" />
        </div>
        <div className="absolute -bottom-3 right-4 bg-emerald-600 text-white text-[10px] font-bold px-3 py-1.5 rounded-full border-4 border-white shadow-sm tracking-wider">
          VERIFIED
        </div>
      </div>

      {/* Info Section */}
      <div className="flex-1 flex flex-col justify-center py-2">
        <h1 className="text-4xl font-bold text-slate-900 mb-3">Alex Strathmore</h1>
        
        <div className="flex items-center gap-3 mb-4">
          <span className="px-3 py-1 bg-indigo-50 text-indigo-600 text-[10px] font-bold rounded-full tracking-wider uppercase">TIER 1 CERTIFICATION</span>
          <span className="px-3 py-1 bg-emerald-50 text-emerald-600 text-[10px] font-bold rounded-full tracking-wider uppercase">AVAILABLE NOW</span>
        </div>
        
        <h2 className="text-xl text-slate-700 mb-1">Alexandra Strathmore · Quantum Leap Tech</h2>
        <p className="text-indigo-600 font-semibold mb-6">Principal Systems Architect & Career Strategist</p>
        
        <div className="flex items-center gap-6">
          <div className="flex items-center gap-2 bg-rose-50 text-rose-600 px-4 py-2 rounded-xl border border-rose-100/50">
            <AlertTriangle size={18} className="fill-rose-100" />
            <span className="font-bold text-xs tracking-wide">RISK LEVEL: STABLE</span>
          </div>
          <div className="flex items-center gap-2 text-slate-500 text-sm font-medium">
            <Clock size={16} />
            <span>Updated 2 mins ago</span>
          </div>
        </div>
      </div>

      {/* Right Stats Cards */}
      <div className="flex gap-4 items-center shrink-0">
        <div className="bg-indigo-600 text-white rounded-[2rem] p-6 flex flex-col justify-center items-center h-36 w-36 shadow-xl shadow-indigo-200/50">
          <span className="text-indigo-200 text-[10px] font-bold tracking-widest mb-2 uppercase">STARTING</span>
          <span className="text-4xl font-bold">$149</span>
        </div>
        <div className="bg-white rounded-[2rem] p-6 flex flex-col justify-center items-center h-36 w-36 shadow-sm border border-indigo-50">
          <span className="text-indigo-600 text-[10px] font-bold tracking-widest mb-2 uppercase">AVG RATING</span>
          <div className="flex items-center gap-1">
            <span className="text-4xl font-bold text-slate-900">4.9</span>
            <Star className="fill-amber-400 text-amber-400" size={20} />
          </div>
        </div>
      </div>
    </div>
  </div>
);

const StatsGrid = () => {
  const stats = [
    { icon: History, label: 'LATEST ACTIVITY', value: 'Profile Edit', iconColor: 'text-indigo-500', bgColor: 'bg-indigo-50' },
    { icon: Calendar, label: 'NEXT AVAILABLE', value: 'Oct 24, 09:00', iconColor: 'text-emerald-500', bgColor: 'bg-emerald-50' },
    { icon: CalendarCheck, label: 'NEXT BOOKING', value: 'Confirmed (3h)', iconColor: 'text-amber-500', bgColor: 'bg-amber-50' },
    { icon: RefreshCw, label: 'SYNC STATUS', value: 'Synchronized', iconColor: 'text-emerald-600', bgColor: 'bg-emerald-50/50', valueColor: 'text-emerald-600' },
  ];

  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
      {stats.map((stat, idx) => (
        <div key={idx} className="bg-white rounded-3xl p-6 shadow-sm border border-slate-100 flex flex-col justify-between h-36">
          <div className={`w-10 h-10 rounded-xl flex items-center justify-center mb-4 ${stat.bgColor} ${stat.iconColor}`}>
            <stat.icon size={20} />
          </div>
          <div>
            <div className="text-[10px] font-bold text-slate-400 tracking-wider mb-1 uppercase">{stat.label}</div>
            <div className={`font-semibold text-slate-800 ${stat.valueColor || ''}`}>{stat.value}</div>
          </div>
        </div>
      ))}
    </div>
  );
};

const ServiceLandscape = () => (
  <div className="bg-white rounded-3xl p-8 shadow-sm border border-slate-100 h-full flex flex-col">
    <div className="flex items-center gap-3 mb-6">
      <Briefcase className="text-indigo-600" size={24} />
      <h3 className="text-xl font-bold text-slate-900">Service Landscape</h3>
    </div>
    
    <div className="flex flex-wrap gap-2 mb-auto">
      {['Career Mentoring', 'Code Review', 'System Design', 'Mock Interviews'].map(tag => (
        <span key={tag} className="px-4 py-2 bg-slate-50 text-slate-700 text-sm font-semibold rounded-xl border border-slate-100">
          {tag}
        </span>
      ))}
    </div>

    <div className="mt-8">
      <div className="flex justify-between items-end mb-3">
        <span className="text-sm font-semibold text-slate-600">Fulfillment Rate</span>
        <span className="text-3xl font-bold text-indigo-600">98.4%</span>
      </div>
      <div className="h-3 w-full bg-slate-100 rounded-full overflow-hidden mb-3">
        <div className="h-full bg-emerald-500 rounded-full" style={{ width: '98.4%' }}></div>
      </div>
      <p className="text-xs text-slate-400 font-medium italic">42 orders completed this month</p>
    </div>
  </div>
);

const PackageArchitecture = () => (
  <div className="bg-white rounded-3xl p-8 shadow-sm border border-slate-100 h-full">
    <div className="flex items-center gap-3 mb-6">
      <Package className="text-indigo-600" size={24} />
      <h3 className="text-xl font-bold text-slate-900">Package Architecture</h3>
    </div>
    
    <div className="space-y-4">
      {[
        { num: 1, name: 'Quick Consultation', price: '$49' },
        { num: 2, name: 'In-Depth Review', price: '$199', active: true },
        { num: 3, name: 'Career Sprint (4 weeks)', price: '$599' },
      ].map(pkg => (
        <div key={pkg.num} className={`flex items-center justify-between p-4 rounded-2xl border ${pkg.active ? 'bg-indigo-50 border-indigo-100' : 'bg-slate-50 border-transparent'}`}>
          <div className="flex items-center gap-4">
            <div className={`w-6 h-6 rounded flex items-center justify-center text-xs font-bold ${pkg.active ? 'bg-indigo-600 text-white' : 'bg-indigo-200 text-indigo-700'}`}>
              {pkg.num}
            </div>
            <span className="font-semibold text-slate-800">{pkg.name}</span>
          </div>
          <span className="font-bold text-slate-900 bg-white px-3 py-1 rounded-lg shadow-sm text-sm">{pkg.price}</span>
        </div>
      ))}
    </div>
  </div>
);

const GovernanceAdvice = () => {
  const advices = [
    { level: 'CRITICAL', levelColor: 'bg-rose-600', title: 'Withdrawal Latency Spike', desc: 'Detected 40% increase in payout processing time for this account.', action: 'AUDIT GATEWAY' },
    { level: 'HIGH', levelColor: 'bg-amber-600', title: 'Optimization Suggestion', desc: 'Engagement drops after 8 PM. Shift availability to morning slots.', action: 'ADJUST WINDOW', actionColor: 'text-amber-600' },
    { level: 'MEDIUM', levelColor: 'bg-amber-500', title: 'Incomplete Credential Doc', desc: 'Secondary certification image has low resolution for OCR validation.', action: 'REQUEST RESUBMIT', actionColor: 'text-emerald-600' },
  ];

  return (
    <div className="bg-white rounded-3xl p-8 shadow-sm border border-slate-100">
      <div className="flex items-center justify-between mb-8">
        <div className="flex items-center gap-3">
          <AlertTriangle className="text-rose-500" size={24} />
          <h3 className="text-2xl font-bold text-slate-900">Governance Advice</h3>
        </div>
        <button className="flex items-center gap-2 px-4 py-2 bg-slate-50 hover:bg-slate-100 text-slate-700 text-sm font-semibold rounded-xl transition-colors border border-slate-200">
          <RefreshCw size={16} />
          Refresh View
        </button>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="border-b border-slate-100 text-xs font-bold text-slate-400 uppercase tracking-wider">
              <th className="pb-4 font-semibold w-28">Risk Level</th>
              <th className="pb-4 font-semibold w-1/4">Risk Title</th>
              <th className="pb-4 font-semibold w-1/2">Risk Description</th>
              <th className="pb-4 font-semibold text-right">Suggested Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-50">
            {advices.map((advice, idx) => (
              <tr key={idx} className="group hover:bg-slate-50/50 transition-colors">
                <td className="py-5 pr-4">
                  <span className={`px-2.5 py-1 rounded text-[10px] font-bold text-white tracking-wider ${advice.levelColor}`}>
                    {advice.level}
                  </span>
                </td>
                <td className="py-5 pr-4 font-semibold text-slate-800">{advice.title}</td>
                <td className="py-5 pr-4 text-sm text-slate-500 leading-relaxed">{advice.desc}</td>
                <td className="py-5 text-right">
                  <button className={`text-xs font-bold tracking-wider uppercase hover:underline ${advice.actionColor || 'text-indigo-600'}`}>
                    {advice.action}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

const PlatformControl = () => (
  <div className="bg-white rounded-3xl p-8 shadow-sm border border-slate-100">
    <h3 className="text-xl font-bold text-slate-900 mb-6">Platform Control</h3>
    
    <div className="space-y-4">
      {[
        { icon: User, title: 'User Identity', desc: 'View deep profile data', iconBg: 'bg-indigo-50', iconColor: 'text-indigo-600' },
        { icon: ShieldCheck, title: 'Certification Audit', desc: 'Review credential docs', iconBg: 'bg-emerald-50', iconColor: 'text-emerald-600' },
        { icon: Banknote, title: 'Financial Flows', desc: 'Payouts & Refund logs', iconBg: 'bg-amber-50', iconColor: 'text-amber-600' },
      ].map((item, idx) => (
        <button key={idx} className="w-full flex items-center justify-between p-4 rounded-2xl bg-slate-50 hover:bg-slate-100 transition-colors border border-slate-100 group">
          <div className="flex items-center gap-4 text-left">
            <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${item.iconBg} ${item.iconColor}`}>
              <item.icon size={20} />
            </div>
            <div>
              <div className="font-semibold text-slate-800">{item.title}</div>
              <div className="text-xs text-slate-500 font-medium">{item.desc}</div>
            </div>
          </div>
          <ChevronRight size={20} className="text-slate-300 group-hover:text-slate-500 transition-colors" />
        </button>
      ))}
    </div>
  </div>
);

const WithdrawalWorkflow = () => (
  <div className="bg-white rounded-3xl p-8 shadow-sm border border-slate-100">
    <h3 className="text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-5">WITHDRAWAL WORKFLOW</h3>
    <div className="flex gap-4">
      <button className="flex-1 flex flex-col items-center justify-center gap-3 p-5 rounded-2xl border border-slate-200 hover:border-emerald-500 hover:bg-emerald-50 transition-colors group">
        <div className="w-10 h-10 rounded-full bg-slate-100 group-hover:bg-emerald-100 flex items-center justify-center text-slate-600 group-hover:text-emerald-600 transition-colors">
          <CheckCircle2 size={20} />
        </div>
        <span className="text-[10px] font-bold text-slate-600 group-hover:text-emerald-700 tracking-widest">APPROVE</span>
      </button>
      <button className="flex-1 flex flex-col items-center justify-center gap-3 p-5 rounded-2xl border border-slate-200 hover:border-rose-500 hover:bg-rose-50 transition-colors group">
        <div className="w-10 h-10 rounded-full bg-slate-100 group-hover:bg-rose-100 flex items-center justify-center text-slate-600 group-hover:text-rose-600 transition-colors">
          <XCircle size={20} />
        </div>
        <span className="text-[10px] font-bold text-slate-600 group-hover:text-rose-700 tracking-widest">REJECT</span>
      </button>
    </div>
  </div>
);

const InternalNotes = () => (
  <div className="bg-white rounded-3xl p-8 shadow-sm border border-slate-100">
    <h3 className="text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-5">INTERNAL ADMIN NOTES</h3>
    <textarea 
      className="w-full h-32 bg-slate-50 border border-slate-200 rounded-2xl p-4 text-sm text-slate-700 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent resize-none font-medium"
      placeholder="Type governance notes here..."
    ></textarea>
  </div>
);

const SaveButton = () => (
  <button className="w-full flex items-center justify-center gap-2 bg-indigo-600 hover:bg-indigo-700 text-white py-4 rounded-2xl font-bold transition-colors shadow-lg shadow-indigo-200/50">
    <Save size={20} />
    Save Governance State
  </button>
);

export default function App() {
  return (
    <div className="min-h-screen bg-[#f8f9fc] font-sans text-slate-900 p-4 md:p-8">
      <div className="max-w-[1400px] mx-auto grid grid-cols-1 xl:grid-cols-3 gap-8">
        {/* Left Column */}
        <div className="xl:col-span-2 space-y-8">
          <ProfileHeader />
          <StatsGrid />
          <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
            <ServiceLandscape />
            <PackageArchitecture />
          </div>
          <GovernanceAdvice />
        </div>
        {/* Right Column */}
        <div className="xl:col-span-1 space-y-8">
          <PlatformControl />
          <WithdrawalWorkflow />
          <InternalNotes />
          <SaveButton />
        </div>
      </div>
    </div>
  );
}
