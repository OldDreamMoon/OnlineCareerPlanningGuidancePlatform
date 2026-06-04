/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { 
  LayoutDashboard, 
  BarChart3, 
  ShieldCheck, 
  FileText, 
  Users, 
  HelpCircle, 
  LogOut,
  Search,
  Bell,
  Settings,
  User as UserIcon,
  GraduationCap,
  Building2,
  Activity,
  Star,
  Clock,
  AlertCircle,
  UserPlus,
  MoreVertical,
  Edit2,
  Eye,
  ChevronLeft,
  ChevronRight,
  ArrowLeft,
  Award,
  Wallet,
  MessageSquare,
  Lock
} from 'lucide-react';

// --- Mock Data ---
type User = {
  id: string;
  name: string;
  email: string;
  role: string;
  tier: string;
  certification: string;
  status: 'Active' | 'Suspended' | 'Pending';
  lastLogin: string;
  avatar: string;
  major?: string;
  grade?: string;
  targetPosition?: string;
  skills?: string[];
  intro?: string;
  communityScore?: number;
  studentPoints?: number;
  registrationDate?: string;
};

const MOCK_USERS: User[] = [
  {
    id: '#UX-9021',
    name: 'Alex Rivera',
    email: 'alex.rivera@edu.indigoether.com',
    role: 'Student',
    tier: 'Premium',
    certification: 'Verified',
    status: 'Active',
    lastLogin: '2 mins ago',
    registrationDate: 'Oct 12, 2023',
    avatar: 'https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?q=80&w=256&auto=format&fit=crop',
    major: 'Applied Cryptography & AI',
    grade: 'Senior (Year 4)',
    targetPosition: 'Blockchain Architect',
    skills: ['Solidity', 'Python', 'Rust', '+4'],
    intro: '"Passionate about decentralized systems and their application in future energy markets. Currently researching zero-knowledge proofs for smart grid privacy."',
    communityScore: 942,
    studentPoints: 2850
  },
  {
    id: '#UX-8842',
    name: 'Sarah Wong',
    email: 's.wong@design.com',
    role: 'Enterprise',
    tier: 'Free',
    certification: 'Pending',
    status: 'Active',
    lastLogin: '1 hour ago',
    registrationDate: 'Jan 05, 2024',
    avatar: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?q=80&w=256&auto=format&fit=crop',
  },
  {
    id: '#UX-7153',
    name: 'Boris Knight',
    email: 'b.knight@spam.me',
    role: 'User',
    tier: 'Free',
    certification: 'Not Applied',
    status: 'Suspended',
    lastLogin: '12 days ago',
    registrationDate: 'Nov 20, 2023',
    avatar: 'https://images.unsplash.com/photo-1599566150163-29194dcaad36?q=80&w=256&auto=format&fit=crop',
  }
];

// --- Components ---

const Sidebar = () => (
  <aside className="h-screen w-64 fixed left-0 top-0 bg-surface-lowest border-r border-surface-low flex flex-col p-4 gap-2 z-50">
    <div className="flex items-center gap-3 px-2 mb-8 mt-2">
      <div className="w-10 h-10 bg-gradient-to-br from-primary to-primary-dim rounded-xl flex items-center justify-center text-white shadow-lg shadow-primary/20">
        <Star className="w-5 h-5 fill-current" />
      </div>
      <div>
        <h1 className="text-lg font-display font-black text-on-surface leading-tight">Indigo Ether</h1>
        <p className="text-[10px] uppercase tracking-widest text-outline font-bold">Enterprise Admin</p>
      </div>
    </div>
    <nav className="flex-1 space-y-1">
      {[
        { icon: LayoutDashboard, label: 'Overview' },
        { icon: BarChart3, label: 'Analytics' },
        { icon: ShieldCheck, label: 'Governance' },
        { icon: FileText, label: 'Reports' },
      ].map((item, i) => (
        <a key={i} href="#" className="flex items-center gap-3 px-4 py-3 text-on-surface-variant hover:bg-surface-low rounded-xl transition-all duration-200">
          <item.icon className="w-5 h-5" />
          <span className="font-medium text-sm">{item.label}</span>
        </a>
      ))}
      <a href="#" className="flex items-center gap-3 px-4 py-3 bg-primary/10 text-primary-dim rounded-xl font-bold transition-all duration-200">
        <Users className="w-5 h-5" />
        <span className="font-medium text-sm">Users</span>
      </a>
    </nav>
    <div className="mt-auto space-y-1 pt-4 border-t border-surface-low">
      <a href="#" className="flex items-center gap-3 px-4 py-3 text-on-surface-variant hover:bg-surface-low rounded-xl transition-all">
        <HelpCircle className="w-5 h-5" />
        <span className="font-medium text-sm">Help</span>
      </a>
      <a href="#" className="flex items-center gap-3 px-4 py-3 text-red-500 hover:bg-red-50 rounded-xl transition-all">
        <LogOut className="w-5 h-5" />
        <span className="font-medium text-sm">Logout</span>
      </a>
    </div>
  </aside>
);

const TopBar = () => (
  <header className="w-full sticky top-0 z-40 bg-surface/80 backdrop-blur-xl flex justify-between items-center px-8 py-4">
    <div className="flex items-center gap-8">
      <h2 className="font-display text-xl font-extrabold tracking-tight text-on-surface">用户与风控中心</h2>
      <div className="hidden md:flex items-center bg-surface-low px-4 py-2.5 rounded-full w-96 focus-within:bg-surface-lowest focus-within:ring-2 focus-within:ring-primary/30 transition-all shadow-sm">
        <Search className="text-outline w-4 h-4 mr-3" />
        <input 
          type="text" 
          placeholder="搜索用户ID、邮箱或姓名..." 
          className="bg-transparent border-none focus:outline-none text-sm w-full placeholder:text-outline-variant"
        />
      </div>
    </div>
    <div className="flex items-center gap-4">
      <button className="p-2 rounded-full text-on-surface-variant hover:bg-surface-low transition-colors relative">
        <Bell className="w-5 h-5" />
        <span className="absolute top-2 right-2 w-2 h-2 bg-red-500 rounded-full"></span>
      </button>
      <button className="p-2 rounded-full text-on-surface-variant hover:bg-surface-low transition-colors">
        <Settings className="w-5 h-5" />
      </button>
      <div className="h-8 w-[1px] bg-outline-variant/30 mx-2"></div>
      <div className="flex items-center gap-3 pl-2">
        <div className="text-right hidden lg:block">
          <p className="text-xs font-bold text-on-surface">Admin Zero</p>
          <p className="text-[10px] text-outline uppercase tracking-wider">Super Admin</p>
        </div>
        <img 
          src="https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?q=80&w=128&auto=format&fit=crop" 
          alt="Profile" 
          className="w-10 h-10 rounded-full border-2 border-primary/20 object-cover"
        />
      </div>
    </div>
  </header>
);

const StatCard = ({ title, value, icon: Icon, trend, trendUp, highlighted = false }: any) => (
  <div className={`p-6 rounded-[1.5rem] shadow-[0_10px_40px_rgba(44,47,49,0.04)] transition-transform hover:-translate-y-1 ${highlighted ? 'bg-gradient-to-br from-primary to-primary-dim text-white shadow-primary/20' : 'bg-surface-lowest border border-white'}`}>
    <div className="flex justify-between items-start mb-4">
      <div className={`p-3 rounded-xl ${highlighted ? 'bg-white/20' : 'bg-surface-low text-on-surface'}`}>
        <Icon className="w-5 h-5" />
      </div>
      {trend && (
        <span className={`font-bold text-xs px-2.5 py-1 rounded-full ${highlighted ? 'bg-white/20 text-white' : trendUp ? 'bg-secondary/10 text-secondary' : 'bg-red-500/10 text-red-500'}`}>
          {trend}
        </span>
      )}
    </div>
    <h3 className={`text-xs font-bold uppercase tracking-widest mb-1 ${highlighted ? 'text-white/80' : 'text-outline'}`}>{title}</h3>
    <p className="font-display text-2xl font-extrabold">{value}</p>
    {highlighted && (
      <div className="w-full bg-white/20 h-1.5 rounded-full mt-4 overflow-hidden">
        <div className="bg-white h-full w-[24.8%]"></div>
      </div>
    )}
  </div>
);

const UserDetailModal = ({ user, onClose }: { user: User, onClose: () => void }) => {
  if (!user) return null;

  return (
    <motion.div 
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-50 flex items-center justify-center p-8"
    >
      {/* Backdrop */}
      <div 
        className="absolute inset-0 bg-surface/60 backdrop-blur-md"
        onClick={onClose}
      />
      
      {/* Modal Content */}
      <motion.div 
        initial={{ y: 40, opacity: 0, scale: 0.95 }}
        animate={{ y: 0, opacity: 1, scale: 1 }}
        exit={{ y: 20, opacity: 0, scale: 0.95 }}
        transition={{ type: "spring", damping: 25, stiffness: 300 }}
        className="relative w-full max-w-6xl h-[85vh] bg-surface-lowest/90 backdrop-blur-2xl rounded-[2rem] shadow-[0_20px_60px_rgba(0,0,0,0.1)] border border-white/50 overflow-hidden flex flex-col"
      >
        {/* Header */}
        <div className="px-10 py-8 flex justify-between items-start border-b border-surface-low/50 bg-white/50">
          <div className="flex gap-6 items-center">
            <button onClick={onClose} className="p-2 -ml-2 hover:bg-surface-low rounded-full transition-colors mr-2">
              <ArrowLeft className="w-6 h-6 text-on-surface-variant" />
            </button>
            <div className="relative">
              <img src={user.avatar} alt={user.name} className="w-24 h-24 rounded-[1.5rem] object-cover ring-4 ring-surface-low shadow-xl" />
              {user.certification === 'Verified' && (
                <div className="absolute -bottom-2 -right-2 bg-primary text-white p-1.5 rounded-xl border-4 border-white shadow-lg">
                  <ShieldCheck className="w-4 h-4" />
                </div>
              )}
            </div>
            <div className="space-y-1.5">
              <div className="flex items-center gap-3">
                <h1 className="text-3xl font-display font-extrabold text-on-surface tracking-tight">{user.name}</h1>
                <span className={`px-3 py-1 text-xs font-bold rounded-full uppercase tracking-wider ${user.status === 'Active' ? 'bg-secondary/10 text-secondary' : 'bg-red-500/10 text-red-500'}`}>
                  {user.status}
                </span>
              </div>
              <p className="text-on-surface-variant font-medium">{user.email}</p>
              <div className="flex items-center gap-4 pt-2">
                <span className="flex items-center gap-1.5 text-xs font-semibold text-primary bg-primary/10 px-2.5 py-1 rounded-lg">
                  <GraduationCap className="w-3.5 h-3.5" /> {user.role.toUpperCase()}
                </span>
                <span className="flex items-center gap-1.5 text-xs font-semibold text-tertiary bg-tertiary/10 px-2.5 py-1 rounded-lg">
                  <Award className="w-3.5 h-3.5" /> {user.tier.toUpperCase()} TIER
                </span>
              </div>
            </div>
          </div>
          <div className="flex gap-3">
            <button className="bg-gradient-to-r from-primary to-primary-dim text-white px-6 py-3 rounded-full font-bold shadow-lg shadow-primary/20 flex items-center gap-2 hover:-translate-y-0.5 transition-all">
              <Edit2 className="w-4 h-4" /> Edit Profile
            </button>
            <button className="p-3 bg-surface-low text-on-surface-variant rounded-full hover:bg-surface-low/80 transition-colors">
              <MoreVertical className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Scrollable Body */}
        <div className="flex-1 overflow-y-auto p-10 bg-surface/30">
          <div className="grid grid-cols-12 gap-8">
            {/* Left Column */}
            <div className="col-span-8 space-y-8">
              {/* Basic Info */}
              <div className="grid grid-cols-3 gap-4">
                {[
                  { label: 'User ID', value: user.id },
                  { label: 'Registration', value: user.registrationDate || 'N/A' },
                  { label: 'Last Active', value: user.lastLogin, dot: true }
                ].map((item, i) => (
                  <div key={i} className="bg-surface-lowest p-5 rounded-2xl shadow-sm border border-surface-low flex flex-col justify-between">
                    <span className="text-xs font-bold text-outline uppercase tracking-widest mb-2">{item.label}</span>
                    <div className="flex items-center gap-2">
                      {item.dot && <span className="w-2 h-2 rounded-full bg-secondary"></span>}
                      <span className="text-lg font-display font-bold text-on-surface">{item.value}</span>
                    </div>
                  </div>
                ))}
              </div>

              {/* Role Specific Profile */}
              <div className="bg-surface-lowest rounded-[1.5rem] p-8 shadow-sm border border-surface-low">
                <div className="flex items-center gap-3 mb-8">
                  <GraduationCap className="w-6 h-6 text-primary" />
                  <h3 className="text-xl font-bold font-display">Student Profile</h3>
                </div>
                <div className="grid grid-cols-2 gap-x-12 gap-y-8">
                  <div className="space-y-1">
                    <label className="text-[10px] font-black text-outline uppercase tracking-wider">Major</label>
                    <p className="text-on-surface font-semibold text-lg">{user.major || 'N/A'}</p>
                  </div>
                  <div className="space-y-1">
                    <label className="text-[10px] font-black text-outline uppercase tracking-wider">Current Grade</label>
                    <p className="text-on-surface font-semibold text-lg">{user.grade || 'N/A'}</p>
                  </div>
                  <div className="space-y-1">
                    <label className="text-[10px] font-black text-outline uppercase tracking-wider">Target Position</label>
                    <p className="text-on-surface font-semibold text-lg">{user.targetPosition || 'N/A'}</p>
                  </div>
                  <div className="space-y-1">
                    <label className="text-[10px] font-black text-outline uppercase tracking-wider">Skills</label>
                    <div className="flex flex-wrap gap-2 pt-1">
                      {user.skills?.map(skill => (
                        <span key={skill} className="px-2 py-1 bg-surface-low text-on-surface-variant rounded text-xs font-medium">{skill}</span>
                      )) || <span className="text-sm text-outline">No skills listed</span>}
                    </div>
                  </div>
                  <div className="col-span-2 space-y-1">
                    <label className="text-[10px] font-black text-outline uppercase tracking-wider">Intro</label>
                    <p className="text-on-surface-variant text-sm leading-relaxed italic">{user.intro || 'No introduction provided.'}</p>
                  </div>
                </div>
              </div>

              {/* Metrics */}
              <div className="grid grid-cols-2 gap-6">
                <div className="bg-gradient-to-br from-primary to-primary-dim p-8 rounded-[1.5rem] text-white shadow-xl shadow-primary/20">
                  <div className="flex justify-between items-start mb-6">
                    <span className="text-white/80 text-xs font-bold uppercase tracking-widest">7d Community Score</span>
                    <span className="bg-white/20 px-2 py-1 rounded text-xs font-bold">+12%</span>
                  </div>
                  <div className="text-5xl font-black font-display mb-2">{user.communityScore || 0}</div>
                  <div className="h-2 w-full bg-white/20 rounded-full overflow-hidden mt-6">
                    <div className="h-full bg-white w-[94%] shadow-[0_0_15px_rgba(255,255,255,0.8)]"></div>
                  </div>
                </div>
                <div className="bg-surface-lowest p-8 rounded-[1.5rem] border border-surface-low shadow-sm flex flex-col justify-center">
                  <div className="flex items-center gap-4">
                    <div className="w-16 h-16 bg-tertiary/10 rounded-2xl flex items-center justify-center text-tertiary">
                      <Wallet className="w-8 h-8" />
                    </div>
                    <div>
                      <span className="text-xs font-bold text-outline uppercase tracking-widest">Student Points</span>
                      <div className="text-3xl font-black font-display text-on-surface">{user.studentPoints?.toLocaleString() || 0}</div>
                    </div>
                  </div>
                  <div className="mt-6">
                    <button className="w-full py-3 bg-tertiary text-white rounded-xl font-bold hover:brightness-110 transition-all shadow-md shadow-tertiary/20">
                      Point Top-up
                    </button>
                  </div>
                </div>
              </div>
            </div>

            {/* Right Column */}
            <div className="col-span-4 space-y-6">
              <a href="#" className="group block p-6 bg-surface-lowest rounded-2xl border-2 border-surface-low hover:border-primary/50 transition-all shadow-sm">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-4">
                    <div className="w-12 h-12 bg-primary/10 text-primary rounded-xl flex items-center justify-center group-hover:bg-primary group-hover:text-white transition-colors">
                      <MessageSquare className="w-5 h-5" />
                    </div>
                    <h4 className="font-bold text-on-surface">Review Portal</h4>
                  </div>
                  <ChevronRight className="text-outline group-hover:text-primary group-hover:translate-x-1 transition-all" />
                </div>
              </a>

              <div className="p-6 bg-surface-lowest rounded-2xl border border-surface-low shadow-sm">
                <h4 className="text-sm font-bold text-outline uppercase tracking-wider mb-4">Account Control</h4>
                <div className="flex items-center justify-between p-4 bg-surface rounded-xl">
                  <div>
                    <p className="font-bold text-on-surface">Account Active</p>
                    <p className="text-xs text-on-surface-variant">Toggle to suspend access</p>
                  </div>
                  <button className={`w-12 h-6 rounded-full relative transition-colors ${user.status === 'Active' ? 'bg-secondary' : 'bg-outline-variant'}`}>
                    <div className={`absolute top-1 w-4 h-4 bg-white rounded-full transition-transform ${user.status === 'Active' ? 'right-1' : 'left-1'}`}></div>
                  </button>
                </div>
              </div>

              <div className="p-8 bg-surface-lowest rounded-[1.5rem] border border-surface-low shadow-sm">
                <div className="flex items-center gap-3 mb-6">
                  <Lock className="w-5 h-5 text-outline" />
                  <h4 className="font-bold text-on-surface">Reset Password</h4>
                </div>
                <form className="space-y-4" onSubmit={e => e.preventDefault()}>
                  <div className="space-y-1">
                    <label className="text-[10px] font-black text-outline uppercase tracking-widest">New Password</label>
                    <input type="password" placeholder="••••••••" className="w-full bg-surface border-0 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary outline-none transition-all" />
                  </div>
                  <div className="space-y-1">
                    <label className="text-[10px] font-black text-outline uppercase tracking-widest">Confirm Password</label>
                    <input type="password" placeholder="••••••••" className="w-full bg-surface border-0 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-primary outline-none transition-all" />
                  </div>
                  <button type="submit" className="w-full py-3 bg-on-surface text-white rounded-xl font-bold hover:bg-black transition-colors">
                    Update Security
                  </button>
                </form>
              </div>
            </div>
          </div>
        </div>
      </motion.div>
    </motion.div>
  );
};

export default function App() {
  const [selectedUser, setSelectedUser] = useState<User | null>(null);

  return (
    <div className="min-h-screen flex bg-surface font-sans text-on-surface">
      <Sidebar />
      
      <main className="flex-1 ml-64 relative">
        <TopBar />
        
        <div className={`p-8 space-y-8 transition-all duration-300 ${selectedUser ? 'blur-sm opacity-40 pointer-events-none' : ''}`}>
          {/* Hero */}
          <section className="max-w-4xl">
            <p className="text-on-surface-variant leading-relaxed text-lg">
              欢迎来到中央控制台。在此您可以全面管理平台生态内的用户权限、监控风险预警、处理企业认证申请。系统已自动标记近24小时内的异常登录行为，请及时复核。
            </p>
          </section>

          {/* Stats Grid */}
          <section className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            <StatCard title="Total Users" value="124,892" icon={UserIcon} trend="+12%" trendUp={true} />
            <StatCard title="Mentors" value="3,450" icon={GraduationCap} />
            <StatCard title="Enterprise" value="892" icon={Building2} />
            <StatCard title="7d Active" value="45.2k" icon={Activity} trend="-2%" trendUp={false} />
            <StatCard title="Premium %" value="24.8%" icon={Star} highlighted={true} />
            <StatCard title="Pending Approvals" value="128" icon={Clock} />
            <StatCard title="Suspended" value="42" icon={AlertCircle} />
            <StatCard title="7d New" value="+1,204" icon={UserPlus} />
          </section>

          {/* Filters */}
          <section className="bg-surface-low p-6 rounded-[1.5rem] flex flex-wrap items-center gap-6">
            <div className="flex flex-col gap-1.5 flex-1 min-w-[240px]">
              <label className="text-xs font-bold text-outline-variant px-1">Keyword</label>
              <div className="bg-surface-lowest rounded-xl px-4 py-2.5 flex items-center border border-transparent focus-within:border-primary/50 transition-all shadow-sm">
                <Search className="text-outline w-4 h-4 mr-2" />
                <input type="text" placeholder="Search ID, email..." className="bg-transparent border-none p-0 focus:outline-none text-sm w-full" />
              </div>
            </div>
            {['Role', 'Account Status', 'Certification'].map((label, i) => (
              <div key={i} className="flex flex-col gap-1.5 min-w-[160px]">
                <label className="text-xs font-bold text-outline-variant px-1">{label}</label>
                <select className="bg-surface-lowest rounded-xl px-4 py-2.5 border-none focus:ring-2 focus:ring-primary/20 text-sm appearance-none cursor-pointer shadow-sm outline-none">
                  <option>All {label.split(' ')[0]}s</option>
                </select>
              </div>
            ))}
            <div className="flex items-end h-full self-end">
              <button className="bg-gradient-to-r from-primary to-primary-dim text-white px-6 py-2.5 rounded-xl font-bold text-sm shadow-md shadow-primary/20 hover:brightness-105 transition-all">
                Apply Filters
              </button>
              <button className="ml-2 text-primary-dim font-bold text-sm px-4 py-2.5 hover:bg-primary/5 rounded-xl transition-all">
                Reset
              </button>
            </div>
          </section>

          {/* Table */}
          <section className="bg-surface-lowest rounded-[1.5rem] shadow-[0_10px_40px_rgba(44,47,49,0.04)] overflow-hidden border border-white">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-surface-low/50 border-b border-surface-low">
                    {['User ID', 'Email & Name', 'Role', 'Tier', 'Certification', 'Status', 'Last Login', 'Actions'].map((h, i) => (
                      <th key={i} className={`px-6 py-5 text-[11px] font-bold text-outline uppercase tracking-widest ${i === 7 ? 'text-right' : ''}`}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-surface-low">
                  {MOCK_USERS.map((user, i) => (
                    <tr 
                      key={i} 
                      onClick={() => setSelectedUser(user)}
                      className="hover:bg-surface-low/50 transition-colors cursor-pointer group"
                    >
                      <td className="px-6 py-4">
                        <span className="font-mono text-xs text-outline font-medium">{user.id}</span>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-3">
                          <img src={user.avatar} alt="" className="w-9 h-9 rounded-full object-cover" />
                          <div>
                            <p className="font-bold text-sm text-on-surface">{user.name}</p>
                            <p className="text-xs text-outline">{user.email}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-4 text-sm font-medium">{user.role}</td>
                      <td className="px-6 py-4">
                        <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[10px] font-bold uppercase ${user.tier === 'Premium' ? 'bg-tertiary/10 text-tertiary' : 'bg-surface-low text-outline'}`}>
                          {user.tier}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <span className={`inline-flex items-center gap-1 font-bold text-xs ${user.certification === 'Verified' ? 'text-secondary' : user.certification === 'Pending' ? 'text-tertiary bg-tertiary/10 px-2 py-0.5 rounded-md' : 'text-outline-variant italic'}`}>
                          {user.certification === 'Verified' && <ShieldCheck className="w-3.5 h-3.5" />}
                          {user.certification}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-2">
                          <span className={`w-2 h-2 rounded-full ${user.status === 'Active' ? 'bg-secondary' : 'bg-red-500'}`}></span>
                          <span className={`text-xs font-medium ${user.status === 'Suspended' ? 'text-red-500' : ''}`}>{user.status}</span>
                        </div>
                      </td>
                      <td className="px-6 py-4 text-xs text-outline">{user.lastLogin}</td>
                      <td className="px-6 py-4 text-right">
                        <div className="flex justify-end gap-2">
                          {user.status === 'Pending' ? (
                            <button className="px-3 py-1 bg-primary text-white rounded-lg text-xs font-bold hover:bg-primary-dim transition-all shadow-sm" onClick={(e) => e.stopPropagation()}>Review</button>
                          ) : user.status === 'Suspended' ? (
                            <button className="px-3 py-1 bg-surface-low text-on-surface rounded-lg text-xs font-bold hover:bg-surface-low/80 transition-all" onClick={(e) => e.stopPropagation()}>Unban</button>
                          ) : (
                            <button className="p-2 text-primary hover:bg-primary/10 rounded-lg transition-all" onClick={(e) => e.stopPropagation()}>
                              <Edit2 className="w-4 h-4" />
                            </button>
                          )}
                          <button className="p-2 text-outline hover:bg-surface-low rounded-lg transition-all" onClick={(e) => e.stopPropagation()}>
                            <MoreVertical className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="px-6 py-4 flex items-center justify-between border-t border-surface-low bg-surface-low/30">
              <p className="text-xs text-outline font-medium">Showing <span className="text-on-surface font-bold">1-3</span> of 1,204 results</p>
              <div className="flex items-center gap-1">
                <button className="p-1.5 rounded-lg border border-outline-variant/30 text-outline hover:bg-surface-low transition-all">
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <button className="w-8 h-8 flex items-center justify-center rounded-lg bg-primary text-white text-xs font-bold shadow-sm">1</button>
                <button className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-surface-low text-xs font-bold">2</button>
                <button className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-surface-low text-xs font-bold">3</button>
                <span className="px-1 text-outline">...</span>
                <button className="p-1.5 rounded-lg border border-outline-variant/30 text-outline hover:bg-surface-low transition-all">
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          </section>
        </div>

        {/* FAB */}
        <button className="fixed bottom-8 right-8 w-14 h-14 bg-gradient-to-r from-primary to-primary-dim text-white rounded-2xl shadow-xl shadow-primary/30 flex items-center justify-center hover:-translate-y-1 active:scale-95 transition-all z-40 group">
          <UserPlus className="w-6 h-6" />
          <span className="absolute right-full mr-4 bg-on-surface text-white text-xs font-bold px-3 py-1.5 rounded-lg opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap pointer-events-none">
            Create New User
          </span>
        </button>
      </main>

      {/* Modal Overlay */}
      <AnimatePresence>
        {selectedUser && (
          <UserDetailModal user={selectedUser} onClose={() => setSelectedUser(null)} />
        )}
      </AnimatePresence>
    </div>
  );
}
