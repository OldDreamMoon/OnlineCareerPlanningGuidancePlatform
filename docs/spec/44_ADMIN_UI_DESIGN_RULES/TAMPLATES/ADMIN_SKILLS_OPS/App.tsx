/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import {
  Bell,
  HelpCircle,
  Settings,
  LayoutDashboard,
  Network,
  Database,
  CheckSquare,
  FileText,
  PlusCircle,
  Search,
  File as FileIcon,
  Video,
  Download,
  Trash2,
  ExternalLink,
  Eye,
  Filter,
  ChevronLeft,
  ChevronRight,
  Sparkles,
  Edit3,
  ListAlt,
  Hub,
  Insights
} from 'lucide-react';

const TopNav = () => (
  <nav className="fixed top-0 w-full z-50 bg-white/70 backdrop-blur-xl flex justify-between items-center px-6 h-16 shadow-[0_10px_40px_rgba(44,47,49,0.06)]">
    <div className="flex items-center gap-8">
      <span className="text-xl font-black text-slate-900 font-display tracking-tight">技能资源治理</span>
      <div className="hidden md:flex gap-6 items-center">
        <a className="font-display font-bold text-sm tracking-tight text-slate-500 hover:text-slate-900 transition-colors" href="#">控制台</a>
        <a className="font-display font-bold text-sm tracking-tight text-cyan-500 border-b-2 border-cyan-500 pb-1" href="#">治理引擎</a>
        <a className="font-display font-bold text-sm tracking-tight text-slate-500 hover:text-slate-900 transition-colors" href="#">操作审计</a>
      </div>
    </div>
    <div className="flex items-center gap-4">
      <button className="p-2 hover:bg-slate-100/50 rounded-lg transition-all active:scale-95 text-slate-500">
        <Bell size={20} />
      </button>
      <button className="p-2 hover:bg-slate-100/50 rounded-lg transition-all active:scale-95 text-slate-500">
        <HelpCircle size={20} />
      </button>
      <button className="p-2 hover:bg-slate-100/50 rounded-lg transition-all active:scale-95 text-slate-500">
        <Settings size={20} />
      </button>
      <img 
        alt="User Avatar" 
        className="w-8 h-8 rounded-full ml-2 object-cover border border-slate-200" 
        src="https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?q=80&w=150&auto=format&fit=crop" 
      />
    </div>
  </nav>
);

const Sidebar = () => (
  <aside className="h-screen w-64 fixed left-0 top-0 pt-20 bg-slate-50 flex flex-col border-r border-slate-200/50">
    <div className="px-6 py-4">
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-xl bg-cyan-100 flex items-center justify-center text-cyan-600 shadow-sm">
          <Network size={20} />
        </div>
        <div>
          <p className="text-lg font-bold text-cyan-600 font-display tracking-tight">治理工作台</p>
          <p className="text-[10px] text-slate-400 font-bold tracking-widest uppercase">Vibrant Aqua Edition</p>
        </div>
      </div>
    </div>
    <nav className="flex-1 mt-4 space-y-1 px-2">
      <a className="flex items-center gap-3 text-slate-500 px-4 py-3 hover:bg-cyan-50/50 rounded-xl transition-transform hover:translate-x-1" href="#">
        <LayoutDashboard size={18} />
        <span className="font-display font-semibold text-sm">资源概览</span>
      </a>
      <a className="flex items-center gap-3 bg-white text-cyan-600 shadow-sm rounded-xl px-4 py-3 transition-transform hover:translate-x-1" href="#">
        <Network size={18} />
        <span className="font-display font-semibold text-sm">技能树配置</span>
      </a>
      <a className="flex items-center gap-3 text-slate-500 px-4 py-3 hover:bg-cyan-50/50 rounded-xl transition-transform hover:translate-x-1" href="#">
        <Database size={18} />
        <span className="font-display font-semibold text-sm">资源池管理</span>
      </a>
      <a className="flex items-center gap-3 text-slate-500 px-4 py-3 hover:bg-cyan-50/50 rounded-xl transition-transform hover:translate-x-1" href="#">
        <CheckSquare size={18} />
        <span className="font-display font-semibold text-sm">审核流</span>
      </a>
      <a className="flex items-center gap-3 text-slate-500 px-4 py-3 hover:bg-cyan-50/50 rounded-xl transition-transform hover:translate-x-1" href="#">
        <Settings size={18} />
        <span className="font-display font-semibold text-sm">系统设置</span>
      </a>
    </nav>
    <div className="p-4 border-t border-slate-200/50">
      <button className="w-full py-3 bg-gradient-to-br from-cyan-400 to-cyan-500 text-white rounded-xl font-bold shadow-lg shadow-cyan-200/50 active:scale-95 transition-all mb-4">
        快速入库
      </button>
      <a className="flex items-center gap-3 text-slate-500 px-4 py-2 hover:bg-slate-100 rounded-lg transition-colors" href="#">
        <FileText size={18} />
        <span className="text-sm font-medium">文档说明</span>
      </a>
    </div>
  </aside>
);

const StatsGrid = () => (
  <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
    <div className="bg-white p-6 rounded-2xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] group hover:shadow-[0_8px_30px_rgb(0,0,0,0.08)] transition-shadow">
      <div className="flex items-center gap-4">
        <div className="w-12 h-12 rounded-full bg-indigo-50 flex items-center justify-center text-indigo-500">
          <Network size={24} />
        </div>
        <div>
          <p className="text-sm font-semibold text-slate-500">技能节点总数</p>
          <h3 className="text-2xl font-black text-slate-900 tracking-tight">1,284</h3>
        </div>
      </div>
    </div>
    <div className="bg-white p-6 rounded-2xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] group hover:shadow-[0_8px_30px_rgb(0,0,0,0.08)] transition-shadow">
      <div className="flex items-center gap-4">
        <div className="w-12 h-12 rounded-full bg-emerald-50 flex items-center justify-center text-emerald-500">
          <Network size={24} className="rotate-90" />
        </div>
        <div>
          <p className="text-sm font-semibold text-slate-500">技能关系总数</p>
          <h3 className="text-2xl font-black text-slate-900 tracking-tight">4,592</h3>
        </div>
      </div>
    </div>
    <div className="bg-white p-6 rounded-2xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] group hover:shadow-[0_8px_30px_rgb(0,0,0,0.08)] transition-shadow">
      <div className="flex items-center gap-4">
        <div className="w-12 h-12 rounded-full bg-amber-50 flex items-center justify-center text-amber-500">
          <Database size={24} />
        </div>
        <div>
          <p className="text-sm font-semibold text-slate-500">资源条目总数</p>
          <h3 className="text-2xl font-black text-slate-900 tracking-tight">28,940</h3>
        </div>
      </div>
    </div>
  </div>
);

const NodeList = () => (
  <div className="lg:col-span-7 bg-white rounded-2xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] overflow-hidden flex flex-col">
    <div className="p-6 border-b border-slate-100 flex justify-between items-center">
      <h2 className="text-xl font-bold flex items-center gap-2 text-slate-900">
        <LayoutDashboard className="text-indigo-500" size={20} />
        技能节点列表
      </h2>
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
        <input 
          className="pl-9 pr-4 py-2 bg-slate-50 border-none rounded-lg text-sm w-64 focus:ring-2 focus:ring-cyan-500/20 outline-none transition-all placeholder:text-slate-400" 
          placeholder="搜索节点名称..." 
          type="text"
        />
      </div>
    </div>
    <div className="overflow-x-auto flex-1">
      <table className="w-full text-left border-collapse">
        <thead className="bg-slate-50/50">
          <tr>
            <th className="py-4 px-6 text-xs font-bold text-slate-500 uppercase tracking-wider">节点名称</th>
            <th className="py-4 px-6 text-xs font-bold text-slate-500 uppercase tracking-wider">层级</th>
            <th className="py-4 px-6 text-xs font-bold text-slate-500 uppercase tracking-wider">资源数</th>
            <th className="py-4 px-6 text-xs font-bold text-slate-500 uppercase tracking-wider">更新时间</th>
            <th className="py-4 px-6 text-xs font-bold text-slate-500 uppercase tracking-wider text-right">操作</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          <tr className="hover:bg-slate-50/80 transition-colors cursor-pointer bg-indigo-50/30">
            <td className="py-4 px-6 font-semibold text-slate-900">深度学习算法分析</td>
            <td className="py-4 px-6 text-sm text-slate-500">L3 专家级</td>
            <td className="py-4 px-6">
              <span className="px-2 py-1 bg-indigo-100 text-indigo-600 text-xs font-bold rounded-md">142</span>
            </td>
            <td className="py-4 px-6 text-sm text-slate-500">2023-11-24</td>
            <td className="py-4 px-6 text-right">
              <button className="text-indigo-600 hover:text-indigo-700 font-bold text-xs uppercase mr-4 transition-colors">详情</button>
              <button className="text-slate-400 hover:text-slate-600 transition-colors"><Edit3 size={16} /></button>
            </td>
          </tr>
          <tr className="hover:bg-slate-50/80 transition-colors cursor-pointer">
            <td className="py-4 px-6 font-semibold text-slate-900">React 高级模式应用</td>
            <td className="py-4 px-6 text-sm text-slate-500">L2 专业级</td>
            <td className="py-4 px-6">
              <span className="px-2 py-1 bg-indigo-50 text-indigo-600 text-xs font-bold rounded-md">86</span>
            </td>
            <td className="py-4 px-6 text-sm text-slate-500">2023-11-20</td>
            <td className="py-4 px-6 text-right">
              <button className="text-indigo-600 hover:text-indigo-700 font-bold text-xs uppercase mr-4 transition-colors">详情</button>
              <button className="text-slate-400 hover:text-slate-600 transition-colors"><Edit3 size={16} /></button>
            </td>
          </tr>
          <tr className="hover:bg-slate-50/80 transition-colors cursor-pointer">
            <td className="py-4 px-6 font-semibold text-slate-900">微服务架构演进</td>
            <td className="py-4 px-6 text-sm text-slate-500">L4 架构级</td>
            <td className="py-4 px-6">
              <span className="px-2 py-1 bg-indigo-50 text-indigo-600 text-xs font-bold rounded-md">215</span>
            </td>
            <td className="py-4 px-6 text-sm text-slate-500">2023-11-18</td>
            <td className="py-4 px-6 text-right">
              <button className="text-indigo-600 hover:text-indigo-700 font-bold text-xs uppercase mr-4 transition-colors">详情</button>
              <button className="text-slate-400 hover:text-slate-600 transition-colors"><Edit3 size={16} /></button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
);

const NodeDetail = () => (
  <div className="lg:col-span-5 space-y-6">
    <div className="bg-white rounded-2xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] p-8">
      <div className="flex justify-between items-start mb-6">
        <div>
          <h3 className="text-2xl font-bold text-slate-900 tracking-tight">深度学习算法分析</h3>
          <p className="text-slate-500 font-mono text-xs mt-1.5 uppercase tracking-wider">SKILL_UID: AI_DL_003</p>
        </div>
        <Sparkles className="text-indigo-400" size={28} />
      </div>
      <div className="space-y-6">
        <div>
          <p className="text-[11px] font-bold text-slate-400 uppercase tracking-widest mb-2">节点描述</p>
          <p className="text-slate-600 text-sm leading-relaxed">
            该节点涵盖了从基础卷积神经网络 (CNN) 到高级生成对抗网络 (GAN) 和 Transformer 架构的全面技术细节，包括模型优化、超参数调整及实战部署方案。
          </p>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div className="p-4 bg-slate-50 rounded-xl">
            <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">子节点数</p>
            <p className="text-xl font-black text-slate-900">12</p>
          </div>
          <div className="p-4 bg-slate-50 rounded-xl">
            <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">活跃贡献者</p>
            <p className="text-xl font-black text-slate-900">8</p>
          </div>
        </div>
        <div>
          <p className="text-[11px] font-bold text-slate-400 uppercase tracking-widest mb-3">关联资源预览</p>
          <div className="space-y-3">
            <div className="flex items-center gap-3 p-3 bg-white border border-slate-100 rounded-xl hover:border-indigo-200 hover:shadow-sm transition-all cursor-pointer">
              <div className="p-2 bg-red-50 rounded-lg text-red-500">
                <FileIcon size={18} />
              </div>
              <div className="flex-1 overflow-hidden">
                <p className="text-sm font-semibold text-slate-800 truncate">2024年深度学习前沿综述.pdf</p>
                <p className="text-[11px] text-slate-400 mt-0.5">PDF • 4.2 MB</p>
              </div>
            </div>
            <div className="flex items-center gap-3 p-3 bg-white border border-slate-100 rounded-xl hover:border-indigo-200 hover:shadow-sm transition-all cursor-pointer">
              <div className="p-2 bg-blue-50 rounded-lg text-blue-500">
                <Video size={18} />
              </div>
              <div className="flex-1 overflow-hidden">
                <p className="text-sm font-semibold text-slate-800 truncate">Transformer 源码深度拆解课程</p>
                <p className="text-[11px] text-slate-400 mt-0.5">Video • 45:12</p>
              </div>
            </div>
          </div>
          <button className="w-full mt-4 py-2.5 text-indigo-600 text-xs font-bold uppercase tracking-widest hover:bg-indigo-50 rounded-xl transition-colors">
            查看全部关联资源
          </button>
        </div>
      </div>
    </div>
  </div>
);

const ResourcePool = () => (
  <div className="bg-white rounded-2xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] overflow-hidden">
    <div className="p-6 border-b border-slate-100 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
      <div>
        <h2 className="text-xl font-bold text-slate-900">资源池管理</h2>
        <p className="text-xs text-slate-500 font-medium mt-1">中心化存储与调度全量治理资源</p>
      </div>
      <div className="flex gap-3">
        <div className="flex bg-slate-50 p-1 rounded-xl border border-slate-100">
          <button className="px-4 py-1.5 text-xs font-bold bg-white rounded-lg shadow-sm text-slate-800">全部资源</button>
          <button className="px-4 py-1.5 text-xs font-bold text-slate-500 hover:text-slate-800 transition-colors">已归档</button>
        </div>
        <button className="p-2 border border-slate-200 rounded-xl hover:bg-slate-50 text-slate-500 transition-colors">
          <Filter size={18} />
        </button>
      </div>
    </div>
    <div className="overflow-x-auto">
      <table className="w-full text-left border-collapse">
        <thead className="bg-slate-50/50">
          <tr>
            <th className="py-4 px-6 text-xs font-bold text-slate-500 uppercase tracking-wider">资源名称</th>
            <th className="py-4 px-6 text-xs font-bold text-slate-500 uppercase tracking-wider">类型</th>
            <th className="py-4 px-6 text-xs font-bold text-slate-500 uppercase tracking-wider">所属节点</th>
            <th className="py-4 px-6 text-xs font-bold text-slate-500 uppercase tracking-wider">来源</th>
            <th className="py-4 px-6 text-xs font-bold text-slate-500 uppercase tracking-wider">链接</th>
            <th className="py-4 px-6 text-xs font-bold text-slate-500 uppercase tracking-wider text-right">操作</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          <tr className="hover:bg-slate-50/80 transition-colors">
            <td className="py-4 px-6">
              <div className="flex items-center gap-3">
                <div className="p-1.5 bg-indigo-50 rounded-lg text-indigo-500">
                  <FileIcon size={16} />
                </div>
                <span className="text-sm font-semibold text-slate-900">PyTorch 分布式训练指南</span>
              </div>
            </td>
            <td className="py-4 px-6">
              <span className="text-[11px] px-2.5 py-1 bg-slate-100 rounded-md text-slate-600 font-semibold">技术文档</span>
            </td>
            <td className="py-4 px-6 text-sm text-slate-600">深度学习算法分析</td>
            <td className="py-4 px-6 text-sm text-slate-500">内部研发中心</td>
            <td className="py-4 px-6">
              <a className="text-cyan-600 hover:text-cyan-700 hover:underline text-xs flex items-center gap-1 font-medium" href="#">
                oss.internal/pytorch_guide
                <ExternalLink size={12} />
              </a>
            </td>
            <td className="py-4 px-6 text-right">
              <div className="flex justify-end gap-2">
                <button className="p-1.5 text-slate-400 hover:text-indigo-600 transition-colors rounded-lg hover:bg-indigo-50"><Download size={16} /></button>
                <button className="p-1.5 text-slate-400 hover:text-red-500 transition-colors rounded-lg hover:bg-red-50"><Trash2 size={16} /></button>
              </div>
            </td>
          </tr>
          <tr className="hover:bg-slate-50/80 transition-colors">
            <td className="py-4 px-6">
              <div className="flex items-center gap-3">
                <div className="p-1.5 bg-emerald-50 rounded-lg text-emerald-500">
                  <ExternalLink size={16} />
                </div>
                <span className="text-sm font-semibold text-slate-900">Google AI 伦理白皮书 2024</span>
              </div>
            </td>
            <td className="py-4 px-6">
              <span className="text-[11px] px-2.5 py-1 bg-slate-100 rounded-md text-slate-600 font-semibold">外部参考</span>
            </td>
            <td className="py-4 px-6 text-sm text-slate-600">AI 伦理与规范</td>
            <td className="py-4 px-6 text-sm text-slate-500">外部网络</td>
            <td className="py-4 px-6">
              <a className="text-cyan-600 hover:text-cyan-700 hover:underline text-xs flex items-center gap-1 font-medium" href="#">
                ai.google/ethics/whitepaper
                <ExternalLink size={12} />
              </a>
            </td>
            <td className="py-4 px-6 text-right">
              <div className="flex justify-end gap-2">
                <button className="p-1.5 text-slate-400 hover:text-indigo-600 transition-colors rounded-lg hover:bg-indigo-50"><Eye size={16} /></button>
                <button className="p-1.5 text-slate-400 hover:text-red-500 transition-colors rounded-lg hover:bg-red-50"><Trash2 size={16} /></button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div className="p-4 bg-slate-50/50 border-t border-slate-100 flex justify-between items-center px-6">
      <p className="text-xs text-slate-500 font-medium">共 1,284 条资源</p>
      <div className="flex items-center gap-1.5">
        <button className="w-8 h-8 flex items-center justify-center rounded-lg border border-slate-200 text-slate-400 hover:bg-white hover:text-slate-600 transition-colors"><ChevronLeft size={16} /></button>
        <button className="w-8 h-8 flex items-center justify-center rounded-lg bg-cyan-500 text-white text-xs font-bold shadow-sm shadow-cyan-200">1</button>
        <button className="w-8 h-8 flex items-center justify-center rounded-lg border border-slate-200 text-slate-600 hover:bg-white transition-colors text-xs font-bold">2</button>
        <button className="w-8 h-8 flex items-center justify-center rounded-lg border border-slate-200 text-slate-600 hover:bg-white transition-colors text-xs font-bold">3</button>
        <button className="w-8 h-8 flex items-center justify-center rounded-lg border border-slate-200 text-slate-400 hover:bg-white hover:text-slate-600 transition-colors"><ChevronRight size={16} /></button>
      </div>
    </div>
  </div>
);

export default function App() {
  return (
    <div className="min-h-screen bg-slate-50 font-sans text-slate-900 selection:bg-cyan-100 selection:text-cyan-900">
      <TopNav />
      <Sidebar />
      <main className="pl-64 pt-16">
        <div className="p-8 max-w-[1600px] mx-auto space-y-8">
          
          {/* Header Section */}
          <div className="flex justify-between items-end">
            <div>
              <h1 className="text-4xl font-extrabold text-slate-900 tracking-tight font-display">技能资源治理台</h1>
              <p className="text-slate-500 mt-2 font-medium">全局技能知识体系图谱与关联资源调度中心</p>
            </div>
            <div className="flex gap-4">
              <button className="px-6 py-2.5 bg-white border border-slate-200 text-slate-700 rounded-xl font-bold hover:bg-slate-50 transition-colors flex items-center gap-2 shadow-sm">
                <PlusCircle size={18} className="text-slate-400" />
                新建资源
              </button>
              <button className="px-6 py-2.5 bg-gradient-to-r from-cyan-400 to-cyan-500 text-white rounded-xl font-bold shadow-lg shadow-cyan-200/50 hover:scale-[1.02] transition-transform flex items-center gap-2">
                <Network size={18} />
                新建节点
              </button>
            </div>
          </div>

          <StatsGrid />

          {/* Dual Column Layout */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            <NodeList />
            <NodeDetail />
          </div>

          <ResourcePool />
          
        </div>
      </main>
    </div>
  );
}
