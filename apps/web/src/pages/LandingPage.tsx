// @ts-nocheck
import React, { useEffect, useRef, useState } from "react";
import { motion } from "framer-motion";
import {
  Sparkles,
  Briefcase,
  Star,
  ArrowRight,
  Zap,
  Play,
  CheckCircle2,
  LineChart,
  Users,
  ShieldCheck,
  Mic,
  FileCheck,
  Lock,
  CreditCard,
  MessageSquare,
  GraduationCap,
  Bell,
  LayoutDashboard,
  LogOut,
  ChevronDown,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import StudentIdentityAvatar from "../components/avatar/StudentIdentityAvatar";
import { getRoleDisplayLabel } from "../lib/roleLabels";

const NAV_SECTIONS = [
  { id: "hero", title: "起点" },
  { id: "students", title: "适合谁" },
  { id: "ai-tools", title: "AI 赋能" },
  { id: "growth", title: "成长体系" },
  { id: "mentors", title: "导师连接" },
  { id: "bounties", title: "企业实践" },
  { id: "trust", title: "平台保障" },
  { id: "journey", title: "服务闭环" },
  { id: "action", title: "立即开始" },
];

const BACKGROUND_STATES = [
  {
    rotateX: 30,
    rotateY: -10,
    rotateZ: -5,
    scale: 1.5,
    depth: 0,
    orbOneX: "-20%",
    orbOneY: "-20%",
    orbTwoX: "60%",
    orbTwoY: "40%",
    primary: "rgba(99,102,241,0.15)",
    secondary: "rgba(232,121,249,0.15)",
  },
  {
    rotateX: 60,
    rotateY: 15,
    rotateZ: 15,
    scale: 2.2,
    depth: -300,
    orbOneX: "50%",
    orbOneY: "-10%",
    orbTwoX: "-10%",
    orbTwoY: "60%",
    primary: "rgba(168,85,247,0.15)",
    secondary: "rgba(56,189,248,0.15)",
  },
  {
    rotateX: 20,
    rotateY: -5,
    rotateZ: -10,
    scale: 1.6,
    depth: -100,
    orbOneX: "-10%",
    orbOneY: "40%",
    orbTwoX: "62%",
    orbTwoY: "-20%",
    primary: "rgba(99,102,241,0.1)",
    secondary: "rgba(16,185,129,0.1)",
  },
  {
    rotateX: 45,
    rotateY: 20,
    rotateZ: 25,
    scale: 2,
    depth: -400,
    orbOneX: "60%",
    orbOneY: "50%",
    orbTwoX: "-20%",
    orbTwoY: "-10%",
    primary: "rgba(16,185,129,0.15)",
    secondary: "rgba(56,189,248,0.15)",
  },
  {
    rotateX: 75,
    rotateY: -15,
    rotateZ: -20,
    scale: 2.5,
    depth: -600,
    orbOneX: "20%",
    orbOneY: "60%",
    orbTwoX: "60%",
    orbTwoY: "-20%",
    primary: "rgba(245,158,11,0.15)",
    secondary: "rgba(244,63,94,0.15)",
  },
  {
    rotateX: 36,
    rotateY: 10,
    rotateZ: 8,
    scale: 1.92,
    depth: -220,
    orbOneX: "8%",
    orbOneY: "16%",
    orbTwoX: "56%",
    orbTwoY: "54%",
    primary: "rgba(99,102,241,0.14)",
    secondary: "rgba(16,185,129,0.14)",
  },
  {
    rotateX: 28,
    rotateY: -18,
    rotateZ: -8,
    scale: 1.7,
    depth: -180,
    orbOneX: "15%",
    orbOneY: "8%",
    orbTwoX: "58%",
    orbTwoY: "46%",
    primary: "rgba(59,130,246,0.14)",
    secondary: "rgba(234,179,8,0.12)",
  },
  {
    rotateX: 42,
    rotateY: 14,
    rotateZ: 14,
    scale: 2.05,
    depth: -260,
    orbOneX: "-8%",
    orbOneY: "10%",
    orbTwoX: "60%",
    orbTwoY: "58%",
    primary: "rgba(99,102,241,0.12)",
    secondary: "rgba(244,114,182,0.14)",
  },
  {
    rotateX: 34,
    rotateY: -6,
    rotateZ: 6,
    scale: 1.86,
    depth: -140,
    orbOneX: "12%",
    orbOneY: "14%",
    orbTwoX: "60%",
    orbTwoY: "42%",
    primary: "rgba(59,130,246,0.12)",
    secondary: "rgba(245,158,11,0.12)",
  },
];

const GRID_LIGHT = `url("data:image/svg+xml,%3Csvg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='none' fill-rule='evenodd'%3E%3Cg fill='%2394a3b8' fill-opacity='0.15'%3E%3Cpath d='M36 34v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zm0-30V0h-2v4h-4v2h4v4h2V6h4V4h-4zM6 34v-4H4v4H0v2h4v4h2v-4h4v-2H6zM6 4V0H4v4H0v2h4v4h2V6h4V4H6z'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E")`;

const BACKGROUND_LAYER_STYLE = {
  perspective: "1000px",
  contain: "layout paint style",
  isolation: "isolate",
};

const BACKGROUND_MOVING_LAYER_STYLE = {
  contain: "layout paint style",
  willChange: "transform",
};

const SECTION_ACCENTS = [
  {
    dotClass: "bg-indigo-600 shadow-[0_0_10px_rgba(79,70,229,0.5)]",
    logoHoverClass: "group-hover:text-indigo-600",
  },
  {
    dotClass: "bg-violet-600 shadow-[0_0_10px_rgba(124,58,237,0.45)]",
    logoHoverClass: "group-hover:text-violet-600",
  },
  {
    dotClass: "bg-indigo-600 shadow-[0_0_10px_rgba(79,70,229,0.5)]",
    logoHoverClass: "group-hover:text-indigo-600",
  },
  {
    dotClass: "bg-sky-600 shadow-[0_0_10px_rgba(2,132,199,0.4)]",
    logoHoverClass: "group-hover:text-sky-600",
  },
  {
    dotClass: "bg-emerald-600 shadow-[0_0_10px_rgba(5,150,105,0.4)]",
    logoHoverClass: "group-hover:text-emerald-600",
  },
  {
    dotClass: "bg-amber-500 shadow-[0_0_10px_rgba(245,158,11,0.4)]",
    logoHoverClass: "group-hover:text-amber-600",
  },
  {
    dotClass: "bg-[#8E6C46] shadow-[0_0_10px_rgba(142,108,70,0.28)]",
    logoHoverClass: "group-hover:text-[#8E6C46]",
  },
  {
    dotClass: "bg-cyan-600 shadow-[0_0_10px_rgba(8,145,178,0.38)]",
    logoHoverClass: "group-hover:text-cyan-600",
  },
  {
    dotClass: "bg-slate-700 shadow-[0_0_10px_rgba(51,65,85,0.35)]",
    logoHoverClass: "group-hover:text-slate-700",
  },
];

const HERO_HIGHLIGHTS = [
  "先从最需要的一步开始",
  "AI 复盘、社区互助与导师咨询持续承接",
  "企业实战与通知中心接住后续反馈",
];

const STUDENT_STAGE_PATHS = [
  {
    step: "01",
    title: "方向探索期",
    subtitle: "如果你还没完全想清楚方向",
    issue: "先搞清自己更适合从哪里开始，不用一上来就盲目准备。",
    start: "先看成长工作台",
    hint: "拿到画像关键词、签到任务和下一步建议",
    icon: GraduationCap,
    sectionIndex: 3,
    shellClass: "rounded-[2rem] rounded-tr-[4.5rem] rounded-bl-[3rem] border-indigo-100/80 shadow-indigo-200/60",
    iconClass: "border-indigo-100 bg-gradient-to-br from-indigo-500/15 via-white to-sky-500/20 text-indigo-700",
    stepClass: "border-indigo-100 bg-white/80 text-indigo-600",
    chipClass: "border-indigo-100 bg-indigo-50 text-indigo-700",
    arrowClass: "border-indigo-100 bg-white/85 text-indigo-600",
    glowClass: "from-indigo-400/30 via-sky-300/16 to-transparent",
  },
  {
    step: "02",
    title: "求职准备期",
    subtitle: "如果你正在准备实习、秋招或第一版简历",
    issue: "先把校园经历整理成更适合投递的岗位表达。",
    start: "先看 AI 简历优化",
    hint: "拿到亮点、风险提醒和 AI 复盘入口",
    icon: FileCheck,
    sectionIndex: 2,
    shellClass: "rounded-[2rem] rounded-tl-[4.2rem] rounded-br-[3.2rem] border-violet-100/80 shadow-violet-200/60",
    iconClass: "border-violet-100 bg-gradient-to-br from-violet-500/15 via-white to-fuchsia-500/18 text-violet-700",
    stepClass: "border-violet-100 bg-white/80 text-violet-600",
    chipClass: "border-violet-100 bg-violet-50 text-violet-700",
    arrowClass: "border-violet-100 bg-white/85 text-violet-600",
    glowClass: "from-violet-400/30 via-fuchsia-300/14 to-transparent",
  },
  {
    step: "03",
    title: "表达打磨期",
    subtitle: "如果你有经历，但还想把它讲得更稳",
    issue: "把项目过程、结果和价值讲得更稳定、更具体。",
    start: "先做一轮模拟面试",
    hint: "文字、语音或 Live 测试模式都能继续练",
    icon: Mic,
    sectionIndex: 2,
    shellClass: "rounded-[2rem] rounded-tr-[3.6rem] rounded-bl-[4.2rem] border-emerald-100/80 shadow-emerald-200/60",
    iconClass: "border-emerald-100 bg-gradient-to-br from-emerald-500/15 via-white to-teal-500/18 text-emerald-700",
    stepClass: "border-emerald-100 bg-white/80 text-emerald-600",
    chipClass: "border-emerald-100 bg-emerald-50 text-emerald-700",
    arrowClass: "border-emerald-100 bg-white/85 text-emerald-600",
    glowClass: "from-emerald-400/30 via-teal-300/14 to-transparent",
  },
  {
    step: "04",
    title: "实战冲刺期",
    subtitle: "如果你想试试更接近真实工作的反馈",
    issue: "把准备结果带到更接近真实业务的场景里验证。",
    start: "先看企业实战任务",
    hint: "让练习成果进入真实反馈链路",
    icon: Briefcase,
    sectionIndex: 5,
    shellClass: "rounded-[2rem] rounded-tl-[3.8rem] rounded-br-[4.3rem] border-amber-100/80 shadow-amber-200/70",
    iconClass: "border-amber-100 bg-gradient-to-br from-amber-500/18 via-white to-orange-500/18 text-amber-700",
    stepClass: "border-amber-100 bg-white/80 text-amber-700",
    chipClass: "border-amber-100 bg-amber-50 text-amber-700",
    arrowClass: "border-amber-100 bg-white/85 text-amber-700",
    glowClass: "from-amber-400/30 via-orange-300/14 to-transparent",
  },
];

const JOURNEY_STEPS = [
  {
    id: "practice",
    step: "01",
    icon: Play,
    title: "先完成你的第一轮准备",
    description: "从简历优化或模拟面试开始，先把岗位目标、简历亮点和表达短板讲清楚。",
    detail: "适合职业探索后的第一轮求职准备",
  },
  {
    id: "portrait",
    step: "02",
    icon: LineChart,
    title: "把你的努力沉淀成成长路径",
    description: "签到、每日任务、画像标签和技能树会把零散练习整理成持续可追踪的成长记录。",
    detail: "知道自己现在在哪一步，接下来更适合做什么",
  },
  {
    id: "mentor",
    step: "03",
    icon: MessageSquare,
    title: "关键卡点继续交给真人导师",
    description: "当你想把问题拆得更细，咨询订单会继续承接更具体、更真实的问题。",
    detail: "服务节点、支付、评价和售后都能继续留痕",
  },
  {
    id: "enterprise",
    step: "04",
    icon: Users,
    title: "再把能力带到真实任务里",
    description: "企业实战任务会继续承接你的练习结果，让能力展示不只停留在简历描述和面试表达上。",
    detail: "从校园准备一步步走到真实连接",
  },
];

const STUDENT_SUPPORT_PILLARS = [
  "AI 练习会继续沉淀到 AI 复盘中心",
  "签到、社区互动和技能进展会持续刷新画像",
  "导师咨询、企业实战与通知中心会接住反馈",
];

const CONSULT_STAGES = ["已提问", "已支付", "已回复", "已完成"];

const BOUNTY_TASKS = [
  {
    title: "校招 JD 亮点整理",
    reward: "企业内推机会 + 导师点评",
    tags: ["后端校招", "关键词梳理"],
    status: "企业复看中",
    accent: "warning",
    summary: "先看奖励、时间和进度，再决定是否提交。",
    progress: "12 份提交 · 3 份进入复看",
  },
  {
    title: "数据分析岗位笔试题复盘",
    reward: "远程体验面试资格",
    tags: ["数据分析", "笔试题型"],
    status: "已采纳",
    accent: "success",
    summary: "采纳、继续接触或未入选结果，都会继续回到任务链路和通知中心里。",
    progress: "8 份提交 · 已采纳 1 份",
  },
];

const AI_PREP_FLOW = [
  { title: "先看清目标岗位", detail: "根据 JD 先抓住投递重点，让准备更有方向。" },
  { title: "再整理投递材料", detail: "把项目经历和亮点整理成更适合投递的版本。" },
  { title: "进入模拟练习", detail: "用文字、语音或 Live 测试模式提前暴露表达短板。" },
  { title: "沉淀下一轮动作", detail: "把结果同步到 AI 复盘中心和成长记录里，为下一轮练习做准备。" },
];

const AI_RESULT_POINTS = ["更适合岗位的简历亮点", "更具体的追问风险提醒", "可继续回看的 AI 复盘记录"];

const AI_ENTRY_POINTS = ["第一次整理简历或项目经历时", "面试前想集中打磨表达，或切到语音 / Live 模式时", "复盘一次失利经历后想重新整理时"];

const GROWTH_PORTRAIT_TAGS = ["表达更清晰", "连续 7 天活跃", "任务反馈响应及时", "社区互动上升"];

const GROWTH_NEXT_ACTIONS = [
  { title: "补 1 条可量化成果", detail: "预计 15 分钟，完成后会同步到画像亮点。" },
  { title: "完成 1 次模拟面试", detail: "练习结束后会同步更新表达标签与 AI 复盘记录。" },
];

const GROWTH_SIGNAL_SOURCES = ["AI 练习结果", "社区互动表现", "技能进展与资料完善", "企业任务反馈"];

const GROWTH_SUMMARY_STATS = [
  { label: "本周完成", value: "6 项", tone: "text-indigo-600" },
  { label: "画像刷新", value: "4 次", tone: "text-emerald-600" },
  { label: "连续活跃", value: "7 天", tone: "text-amber-600" },
];

const MENTOR_TOPICS = ["项目经历不会讲清楚", "简历有经历但缺亮点", "转岗或求职路线还没想明白"];

const MENTOR_GUARANTEES = ["支持预约时段选择", "订单内持续留痕", "超时自动售后兜底"];

const MENTOR_SLOTS = ["周三 20:00", "周四 19:30", "周六 14:00"];

const MENTOR_VALUE_CHAIN = [
  { title: "咨询前", detail: "先把你真正卡住的问题说清楚，不用泛泛地描述焦虑。" },
  { title: "咨询中", detail: "围绕简历、项目表达或求职路线，给你更具体的拆解建议。" },
  { title: "咨询后", detail: "建议会继续留在订单链路和成长路径里，方便你后续复盘。" },
];

const ENTERPRISE_PIPELINE = [
  { title: "发布任务", detail: "企业给出真实业务场景、奖励和截止时间。" },
  { title: "提交成果", detail: "你可以提交成果说明和外部链接，让企业先看清你做了什么。" },
  { title: "企业复看", detail: "企业会继续查看提交内容，并决定是否继续接触。" },
  { title: "结果通知", detail: "继续接触或未入选结果都会回到通知中心和任务链路。" },
];

const ENTERPRISE_VALUES = [
  { title: "企业看到什么", detail: "不只看简历，也能更快看到你如何完成真实任务。" },
  { title: "你得到什么", detail: "你可以把练习成果带到真实机会里，并获得更接近工作场景的反馈。" },
];

const ENTERPRISE_FIT_POINTS = [
  "适合你在实习、校招和作品表达阶段继续往前走",
  "别人看到的不只是自我描述，而是更接近真实业务的提交结果",
  "采纳与未采纳反馈都会继续回流到成长记录中",
];

const PLATFORM_SUPPORTS = [
  {
    title: "通知中心统一承接",
    detail: "AI 复盘、咨询进度、企业任务结果和平台提醒都会回到统一通知中心，方便你接着处理。",
    icon: Bell,
    tone: "border-indigo-100 bg-indigo-50/75 text-indigo-700",
  },
  {
    title: "社区互助与治理反馈",
    detail: "发帖、回复、举报处理和社区提醒都有明确去处，你能在互助里找到相似问题，也能看见结果。",
    icon: ShieldCheck,
    tone: "border-emerald-100 bg-emerald-50/80 text-emerald-700",
  },
  {
    title: "订单留痕与售后闭环",
    detail: "导师咨询不是支付结束就断开，订单消息、评价与售后申请都在同一条服务链路里。",
    icon: CreditCard,
    tone: "border-amber-100 bg-amber-50/80 text-amber-700",
  },
  {
    title: "工作台与 AI 复盘中心",
    detail: "签到、成长记录、继续事项和 AI 历史会分别留在工作台与复盘中心，方便你回到上一轮结果继续往前走。",
    icon: LayoutDashboard,
    tone: "border-slate-200 bg-slate-50 text-slate-700",
  },
];

const COMMON_QUESTIONS = [
  {
    question: "没有完整简历，能不能开始？",
    answer: "可以。你可以先从岗位理解、经历整理和第一轮简历优化开始，不需要一上来就准备好完整材料。",
  },
  {
    question: "每次练习的结果能不能回看？",
    answer: "可以。简历优化、模拟面试和 AI 复盘记录都会留在历史里，也会继续影响工作台里的成长提示。",
  },
  {
    question: "导师咨询结束后还会留下什么？",
    answer: "订单链路里会保留进度、回复、评价与售后记录，你之后复盘时还能继续回看。",
  },
  {
    question: "企业任务的反馈会不会继续回到成长路径里？",
    answer: "会。任务状态、继续接触结果和未入选反馈都会回到任务链路、通知中心与成长记录里。",
  },
];

const STUDENT_WEEK_PATH = [
  {
    day: "第 1 天",
    detail: "先整理方向与岗位目标",
    shellClass: "border-slate-200 bg-slate-50/92",
    dayClass: "bg-slate-100 text-slate-600 border-slate-200",
    accentClass: "bg-slate-400",
    trackClass: "bg-slate-200",
    nodeClass: "border-slate-200 text-slate-500",
    metaClass: "text-slate-500",
  },
  {
    day: "第 2 天",
    detail: "完成一次简历优化或模拟面试",
    shellClass: "border-indigo-100 bg-indigo-50/72",
    dayClass: "bg-white text-indigo-600 border-indigo-100",
    accentClass: "bg-indigo-400",
    trackClass: "bg-indigo-200",
    nodeClass: "border-indigo-100 text-indigo-500",
    metaClass: "text-indigo-500",
  },
  {
    day: "第 3 天",
    detail: "去 AI 复盘中心回看结果，顺手刷新画像",
    shellClass: "border-sky-100 bg-sky-50/75",
    dayClass: "bg-white text-sky-600 border-sky-100",
    accentClass: "bg-sky-400",
    trackClass: "bg-sky-200",
    nodeClass: "border-sky-100 text-sky-500",
    metaClass: "text-sky-500",
  },
  {
    day: "第 4 天",
    detail: "去社区找相似问题，或带着具体疑问进入导师咨询",
    shellClass: "border-emerald-100 bg-emerald-50/72",
    dayClass: "bg-white text-emerald-600 border-emerald-100",
    accentClass: "bg-emerald-400",
    trackClass: "bg-emerald-200",
    nodeClass: "border-emerald-100 text-emerald-500",
    metaClass: "text-emerald-500",
  },
  {
    day: "第 5 天",
    detail: "尝试企业实战任务，并在通知中心回看反馈",
    shellClass: "border-amber-100 bg-amber-50/72",
    dayClass: "bg-white text-amber-600 border-amber-100",
    accentClass: "bg-amber-400",
    trackClass: "bg-amber-200",
    nodeClass: "border-amber-100 text-amber-500",
    metaClass: "text-amber-500",
  },
];

const STUDENT_START_CARD = {
  primaryLabel: "立即开始",
};

const PARTNER_ENTRY_CARDS = [
  {
    title: "导师入驻",
    action: "mentor",
  },
  {
    title: "企业入驻",
    action: "enterprise",
  },
];

const FORMAL_FOOTER_COLUMNS = [
  {
    title: "产品定位",
    items: ["大学生职业成长服务平台", "职业规划与求职训练一体化", "连接能力提升与实践机会"],
  },
  {
    title: "核心服务",
    items: ["AI 简历优化", "模拟面试训练", "成长工作台与复盘记录"],
  },
  {
    title: "成长支持",
    items: ["导师咨询服务", "企业实战任务", "社区交流与通知提醒"],
  },
  {
    title: "合作服务",
    items: ["导师合作入驻", "企业人才合作", "院校成长服务共建"],
  },
];

const BUTTON_FONT_STACK = "\"PingFang SC\", \"Microsoft YaHei\", \"Noto Sans SC\", sans-serif";
const BUTTON_TEXT_STYLE = {
  fontFamily: BUTTON_FONT_STACK,
  WebkitFontSmoothing: "antialiased",
  MozOsxFontSmoothing: "grayscale",
  textRendering: "optimizeLegibility",
};

const Button = ({ children, variant = "primary", size = "default", className = "", icon: Icon, ...props }) => {
  const baseStyle =
    "group relative inline-flex items-center justify-center rounded-full font-bold transition-colors transition-transform transition-shadow duration-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 active:scale-95 disabled:pointer-events-none disabled:opacity-50 overflow-hidden";
  const variants = {
    primary: "bg-slate-900 text-white hover:bg-slate-800 hover:shadow-lg hover:shadow-slate-900/20 hover:-translate-y-0.5",
    gradient: "bg-gradient-to-r from-sky-500 via-cyan-500 to-teal-400 text-white hover:shadow-lg hover:shadow-cyan-500/25 bg-[length:200%_auto] hover:bg-right hover:-translate-y-0.5",
    emerald: "bg-emerald-600 text-white hover:bg-emerald-700 hover:shadow-lg hover:shadow-emerald-500/20 hover:-translate-y-0.5",
    outline: "border border-slate-200 bg-white/10 backdrop-blur-md hover:bg-white/50 text-current hover:border-indigo-300 hover:-translate-y-0.5",
    ghost: "bg-transparent hover:bg-slate-500/10 text-current",
  };
  const sizes = {
    sm: "h-10 px-5 text-sm",
    default: "h-10 px-4 py-2 text-sm",
    lg: "h-12 px-8 text-base",
  };

  return (
    <button className={`${baseStyle} ${variants[variant]} ${sizes[size]} ${className}`} style={BUTTON_TEXT_STYLE} {...props}>
      <span className="relative z-10 flex items-center gap-2">
        {children}
        {Icon && <Icon className="w-4 h-4 transition-transform duration-300 group-hover:translate-x-1" />}
      </span>
      {variant === "gradient" && (
        <div className="absolute top-0 -left-[100%] h-full w-1/2 z-0 transform skew-x-[-20deg] bg-white/20 group-hover:left-[200%] transition-all duration-700 ease-in-out" />
      )}
    </button>
  );
};

const Badge = ({ children, variant = "default", className = "" }) => {
  const variants = {
    default: "bg-slate-100 text-slate-800 border border-slate-200",
    primary: "bg-indigo-50 text-indigo-700 border border-indigo-200",
    glow: "bg-indigo-50 text-indigo-600 border border-indigo-200 shadow-[0_0_10px_rgba(99,102,241,0.15)]",
    success: "bg-emerald-50 text-emerald-700 border border-emerald-200",
    warning: "bg-amber-50 text-amber-700 border border-amber-200",
    dark: "bg-slate-800 text-slate-300 border border-slate-700",
    rose: "bg-rose-50 text-rose-700 border border-rose-200",
  };

  return (
    <span className={`landing-small-text inline-flex items-center rounded-full px-3 py-1 text-xs font-semibold transition-colors ${variants[variant]} ${className}`}>
      {children}
    </span>
  );
};

const fadeUp = {
  hidden: { opacity: 0, y: 40 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.7, ease: [0.22, 1, 0.36, 1] } },
};

const fadeLeft = {
  hidden: { opacity: 0, x: 40 },
  visible: { opacity: 1, x: 0, transition: { duration: 0.7, ease: [0.22, 1, 0.36, 1], delay: 0.16 } },
};

const fadeRight = {
  hidden: { opacity: 0, x: -40 },
  visible: { opacity: 1, x: 0, transition: { duration: 0.7, ease: [0.22, 1, 0.36, 1], delay: 0.16 } },
};

const staggerContainer = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { staggerChildren: 0.12 } },
};

const HERO_BADGE_CLASS = "landing-small-text py-2 px-5 text-[14px] md:text-[15px]";
const HERO_LEAD_CLASS = "text-[1.02rem] md:text-[1.18rem] lg:text-[1.26rem] text-slate-600 mb-8 max-w-3xl mx-auto leading-[1.82]";
const HERO_HIGHLIGHT_CLASS = "mt-8 flex flex-wrap justify-center gap-3 text-[13px] md:text-[15px] text-slate-500";
const SECTION_TITLE_CLASS = "text-[2.35rem] md:text-[3.15rem] lg:text-[3.55rem] font-bold tracking-tight leading-[1.08] mb-6";
const SECTION_BODY_LIGHT_CLASS = "text-slate-600 text-[1.02rem] md:text-[1.1rem] lg:text-[1.16rem] leading-[1.85] mb-8 max-w-xl";
const SECTION_BADGE_CLASS = "landing-small-text text-[14px] md:text-[15px]";
const CARD_TITLE_CLASS = "text-[15px] font-bold text-slate-800";
const CARD_META_CLASS = "landing-small-text text-[12px] leading-5";
const UI_META_CLASS = "landing-small-text text-[12px] leading-5 text-slate-500";
const UI_META_TINT_CLASS = "landing-small-text text-[12px] leading-5 font-semibold";
const UI_CAPTION_CLASS = "landing-small-text text-[13px] leading-6";
const UI_CAPTION_MUTED_CLASS = "landing-small-text text-[13px] leading-6 text-slate-500";
const UI_CAPTION_LABEL_CLASS = "landing-small-text text-[13px] font-semibold";
const UI_BODY_SM_CLASS = "landing-small-text text-[14px] leading-6 text-slate-600";
const UI_BODY_SM_MUTED_CLASS = "landing-small-text text-[14px] leading-6 text-slate-500";
const UI_BODY_EMPHASIS_CLASS = "landing-small-text text-[14px] font-semibold text-slate-800";
const HERO_STAGE_CLASS = "relative w-full max-w-6xl mx-auto h-[500px] xl:h-[520px] mt-12 hidden lg:block text-slate-900";
const HERO_FLOATING_CARD_SIZE_CLASS = "w-[340px] xl:w-[360px] h-[252px] xl:h-[264px]";

function getFloatingMotion(options = {}) {
  // 首页动效只提供轻量漂浮感，避免影响全屏分段切换的主节奏。
  const { delay = 0, distance = 8, duration = 8, direction = "up", enabled = true } = options;
  const startY = direction === "down" ? distance : -distance;

  if (!enabled) {
    return {
      animate: { y: 0 },
      transition: { duration: 0.28, ease: "easeOut" },
    };
  }

  return {
    animate: { y: [startY, -startY, startY] },
    transition: { repeat: Infinity, duration, ease: "easeInOut", delay },
  };
}

function resolveWorkspacePath(role) {
  // 已登录用户从公开首页回流到自己的工作台，公开域不直接进入私有业务页。
  if (role === "ADMIN") {
    return "/admin/dashboard";
  }

  if (role === "MENTOR") {
    return "/mentor/dashboard";
  }

  if (role === "ENTERPRISE") {
    return "/enterprise/dashboard";
  }

  return "/student/dashboard";
}

export default function LandingPage() {
  const [activeSection, setActiveSection] = useState(0);
  const [visitedSections, setVisitedSections] = useState(() => new Set([0]));
  const [viewportHeight, setViewportHeight] = useState(() => (typeof window === "undefined" ? 0 : Math.round(window.innerHeight)));
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const isScrolling = useRef(false);
  const touchStartY = useRef(0);
  const scrollLockTimerRef = useRef(null);
  const userMenuRef = useRef(null);
  const navigate = useNavigate();
  const { ready, isAuthenticated, role, userId, displayName, logout } = useAuth();

  useEffect(() => {
    return () => {
      if (scrollLockTimerRef.current) {
        clearTimeout(scrollLockTimerRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (typeof window === "undefined") {
      return undefined;
    }

    const updateViewportHeight = () => {
      setViewportHeight(Math.round(window.innerHeight));
    };

    updateViewportHeight();
    window.addEventListener("resize", updateViewportHeight);

    return () => {
      window.removeEventListener("resize", updateViewportHeight);
    };
  }, []);

  useEffect(() => {
    if (!userMenuOpen || typeof document === "undefined") {
      return undefined;
    }

    // 用户菜单是公开首页里唯一浮层，点击外部即可关闭。
    const handlePointerDown = (event) => {
      if (userMenuRef.current && !userMenuRef.current.contains(event.target)) {
        setUserMenuOpen(false);
      }
    };

    document.addEventListener("mousedown", handlePointerDown);

    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
    };
  }, [userMenuOpen]);

  useEffect(() => {
    if (!isAuthenticated && userMenuOpen) {
      setUserMenuOpen(false);
    }
  }, [isAuthenticated, userMenuOpen]);

  useEffect(() => {
    setVisitedSections((current) => {
      if (current.has(activeSection)) {
        return current;
      }

      const next = new Set(current);
      next.add(activeSection);
      return next;
    });
  }, [activeSection]);

  const currentBackground = BACKGROUND_STATES[activeSection];
  const currentAccent = SECTION_ACCENTS[activeSection];
  const sectionTrackY = viewportHeight > 0 ? -activeSection * viewportHeight : `-${activeSection * 100}dvh`;
  const hasSectionEntered = (index) => activeSection === index || visitedSections.has(index);
  const getSectionMotionProps = (index) => ({
    initial: "hidden",
    animate: hasSectionEntered(index) ? "visible" : "hidden",
  });
  const isSectionLive = (index) => activeSection === index;

  const backgroundTransition = {
    duration: 1.2,
    ease: [0.76, 0, 0.24, 1],
  };

  const changeSection = (newIndex) => {
    // 全屏滚动需要短暂加锁，防止触控板一次惯性滚过多个章节。
    if (newIndex === activeSection || newIndex < 0 || newIndex > NAV_SECTIONS.length - 1) {
      return;
    }

    isScrolling.current = true;
    setActiveSection(newIndex);

    if (scrollLockTimerRef.current) {
      clearTimeout(scrollLockTimerRef.current);
    }

    scrollLockTimerRef.current = setTimeout(() => {
      isScrolling.current = false;
    }, 1000);
  };

  const goToAuth = () => {
    navigate("/auth");
  };

  const currentUserName = displayName?.trim() || "当前用户";
  const currentRoleLabel = getRoleDisplayLabel(role);
  const workspacePath = resolveWorkspacePath(role);
  const workspaceLabel = role === "ADMIN" ? "进入后台" : "进入工作台";

  const handleFinalAction = (action) => {
    // 合作入口当前仍是路径教育层，先切到对应分屏再引导注册。
    if (action === "mentor") {
      changeSection(4);
      return;
    }

    if (action === "enterprise") {
      changeSection(5);
      return;
    }

    goToAuth();
  };

  const handleWheel = (event) => {
    // 小幅滚动多来自触控板微抖动，不触发章节切换。
    if (isScrolling.current || Math.abs(event.deltaY) < 30) {
      return;
    }

    const direction = event.deltaY > 0 ? 1 : -1;
    changeSection(activeSection + direction);
  };

  const handleTouchStart = (event) => {
    touchStartY.current = event.touches[0].clientY;
  };

  const handleTouchEnd = (event) => {
    if (isScrolling.current) {
      return;
    }

    const diff = touchStartY.current - event.changedTouches[0].clientY;
    if (diff > 50) {
      changeSection(activeSection + 1);
    } else if (diff < -50) {
      changeSection(activeSection - 1);
    }
  };

  return (
    <div
      className="h-[100dvh] w-full overflow-hidden relative bg-slate-50 text-slate-900 font-sans selection:bg-indigo-500/30"
      onWheel={handleWheel}
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
    >
      <div className="absolute inset-0 pointer-events-none z-0 overflow-hidden" style={BACKGROUND_LAYER_STYLE}>
        <motion.div
          className="absolute inset-[-150%] z-0 transform-gpu will-change-transform"
          animate={{
            rotateX: currentBackground.rotateX,
            rotateY: currentBackground.rotateY,
            rotateZ: currentBackground.rotateZ,
            scale: currentBackground.scale,
            z: currentBackground.depth,
          }}
          transition={backgroundTransition}
          style={{
            ...BACKGROUND_MOVING_LAYER_STYLE,
            backgroundImage: GRID_LIGHT,
            backgroundSize: "100px 100px",
            transformStyle: "preserve-3d",
            backfaceVisibility: "hidden",
          }}
        />

        <motion.div
          className="absolute w-[800px] h-[800px] rounded-full z-0 transform-gpu will-change-transform"
          animate={{
            x: currentBackground.orbOneX,
            y: currentBackground.orbOneY,
            scale: 1,
          }}
          transition={{ ...backgroundTransition, duration: 1.5 }}
          style={{
            ...BACKGROUND_MOVING_LAYER_STYLE,
          }}
        >
          <div
            className="absolute inset-0 rounded-full blur-3xl"
            style={{
              background: `radial-gradient(circle at center, ${currentBackground.primary} 0%, transparent 70%)`,
            }}
          />
        </motion.div>

        <motion.div
          className="absolute w-[600px] h-[600px] rounded-full z-0 transform-gpu will-change-transform"
          animate={{
            x: currentBackground.orbTwoX,
            y: currentBackground.orbTwoY,
            scale: 1,
          }}
          transition={{ ...backgroundTransition, duration: 1.5 }}
          style={{
            ...BACKGROUND_MOVING_LAYER_STYLE,
          }}
        >
          <div
            className="absolute inset-0 rounded-full blur-3xl"
            style={{
              background: `radial-gradient(circle at center, ${currentBackground.secondary} 0%, transparent 70%)`,
            }}
          />
        </motion.div>
      </div>

      <header className="fixed inset-x-0 top-0 z-50 border-b border-slate-200/50 bg-white/60 text-slate-900 backdrop-blur-md">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
          <button type="button" className="flex items-center gap-2 cursor-pointer group" onClick={() => changeSection(0)}>
            <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-indigo-600 to-fuchsia-600 text-white flex items-center justify-center transform group-hover:rotate-6 transition-transform shadow-md">
              <Zap className="w-4 h-4 fill-white" />
            </div>
            <span className={`max-w-[16rem] text-left font-bold text-[1rem] leading-tight tracking-tight transition-colors md:text-[1.08rem] ${currentAccent.logoHoverClass}`}>
              大学生就业规划指导平台
            </span>
          </button>
          <div className="flex items-center gap-3">
            {ready ? (
              isAuthenticated ? (
                <>
                  <Button variant="outline" className="hidden md:inline-flex" onClick={() => navigate(workspacePath)}>
                    <LayoutDashboard className="w-4 h-4 mr-2" />
                    {workspaceLabel}
                  </Button>
                  <div ref={userMenuRef} className="relative hidden md:block">
                    <button
                      type="button"
                      onClick={() => setUserMenuOpen((current) => !current)}
                      className="inline-flex items-center gap-3 rounded-full border border-slate-200/80 bg-white/82 px-3 py-2 shadow-sm transition-colors hover:border-indigo-200"
                      aria-expanded={userMenuOpen}
                      aria-haspopup="menu"
                    >
                      <StudentIdentityAvatar
                        userId={userId}
                        role={role}
                        displayName={currentUserName}
                        allowLatestFallback
                        className="h-10 w-10"
                        textClassName="text-sm"
                      />
                      <div className="hidden leading-tight lg:block">
                        <div className="text-sm font-semibold text-slate-900">{currentUserName}</div>
                        <div className="text-xs text-slate-500">{currentRoleLabel}</div>
                      </div>
                      <ChevronDown className={`h-4 w-4 text-slate-400 transition-transform ${userMenuOpen ? "rotate-180" : ""}`} />
                    </button>

                    {userMenuOpen ? (
                      <motion.div
                        initial={{ opacity: 0, y: 8 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.18 }}
                        className="absolute right-0 top-full z-50 mt-3 w-44 overflow-hidden rounded-[1.2rem] border border-white/90 bg-white/95 p-2 shadow-[0_24px_60px_rgba(15,23,42,0.12)] backdrop-blur-xl"
                      >
                        <button
                          type="button"
                          onClick={() => {
                            setUserMenuOpen(false);
                            logout();
                          }}
                          className="flex w-full items-center gap-2 rounded-[0.9rem] px-4 py-3 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-100 hover:text-slate-900"
                        >
                          <LogOut className="h-4 w-4" />
                          退出登录
                        </button>
                      </motion.div>
                    ) : null}
                  </div>
                </>
              ) : (
                <Button variant="outline" className="hidden md:inline-flex" onClick={goToAuth}>
                  登录
                </Button>
              )
            ) : (
              <div className="hidden h-11 w-28 rounded-full border border-slate-200/60 bg-white/55 md:block" />
            )}
            {ready && !isAuthenticated ? (
              <Button variant="gradient" size="sm" onClick={() => changeSection(1)}>
                查看适合人群
              </Button>
            ) : null}
          </div>
        </div>
      </header>

      <div className="absolute right-6 top-1/2 -translate-y-1/2 z-50 hidden md:flex flex-col gap-4">
        {NAV_SECTIONS.map((section, index) => (
          <button key={section.id} onClick={() => changeSection(index)} className="group relative flex items-center justify-end" aria-label={`Go to ${section.title}`}>
            <span className="absolute right-6 text-xs font-bold px-2 py-1 rounded bg-slate-800 text-white transition-opacity translate-x-4 group-hover:translate-x-0 pointer-events-none whitespace-nowrap opacity-0 group-hover:opacity-100">
              {section.title}
            </span>
            <div className={`w-2.5 h-2.5 rounded-full transition-all duration-300 ${activeSection === index ? `${currentAccent.dotClass} scale-150` : "bg-slate-300 hover:bg-slate-400"}`} />
          </button>
        ))}
      </div>

      <motion.div
        className="w-full h-full flex flex-col relative z-10"
        animate={{ y: sectionTrackY }}
        transition={{ duration: 0.8, ease: [0.76, 0, 0.24, 1] }}
      >
        <section className="h-[100dvh] w-full relative flex items-center justify-center shrink-0">
          <div className="max-w-7xl mx-auto px-6 w-full flex flex-col items-center justify-center mt-10">
            <motion.div className="text-center relative z-10" variants={staggerContainer} {...getSectionMotionProps(0)}>
              <motion.div variants={fadeUp} className="flex justify-center mb-6">
                <Badge variant="glow" className={`bg-white/82 ${HERO_BADGE_CLASS}`}>
                  <Sparkles className="w-3.5 h-3.5 mr-2 text-indigo-500" />
                  陪你把 AI 练习、导师反馈与真实任务接成一条线
                </Badge>
              </motion.div>

              <motion.h1 variants={fadeUp} className="text-5xl md:text-7xl lg:text-8xl font-extrabold tracking-tight text-slate-900 mb-6 leading-[1.1]">
                重塑你的<br className="md:hidden" />
                <span className="relative inline-block text-transparent bg-clip-text bg-gradient-to-r from-indigo-600 via-purple-600 to-fuchsia-600 pb-2">
                  职业成长起点
                  <svg className="absolute w-full h-3 -bottom-1 left-0 text-fuchsia-500/20" viewBox="0 0 100 10" preserveAspectRatio="none">
                    <path d="M0 5 Q 50 15 100 5" fill="none" stroke="currentColor" strokeWidth="4" />
                  </svg>
                </span>
              </motion.h1>

              <motion.p variants={fadeUp} className={HERO_LEAD_CLASS}>
                如果你正在找方向、准备第一版简历、反复练面试，或者想把已有经历讲得更稳，这里会把这些步骤接成一条能继续往前走的路径。<br className="hidden md:block" />
                AI 练习、AI 复盘、成长记录、导师咨询和企业实战会一层层接上，不需要你每次都重新开始。
              </motion.p>

              <motion.div variants={fadeUp} className="flex flex-col sm:flex-row justify-center gap-4">
                <Button variant="gradient" size="lg" icon={ArrowRight} onClick={() => changeSection(1)}>
                  看看我适合从哪里开始
                </Button>
                <Button variant="outline" size="lg" className="bg-white/70 text-slate-900 border-slate-200 hover:bg-white" onClick={goToAuth}>
                  进入平台入口
                </Button>
              </motion.div>

              <motion.div variants={fadeUp} className={HERO_HIGHLIGHT_CLASS}>
                {HERO_HIGHLIGHTS.map((item) => (
                  <span key={item} className="inline-flex items-center gap-2 rounded-full bg-white/72 px-4 py-2 border border-slate-200 shadow-sm whitespace-nowrap">
                    <CheckCircle2 className="w-4 h-4 text-indigo-500" />
                    {item}
                  </span>
                ))}
              </motion.div>
            </motion.div>

            <motion.div
              initial={{ opacity: 0, y: 50 }}
              animate={hasSectionEntered(0) ? { opacity: 1, y: 0 } : { opacity: 0, y: 50 }}
              transition={{ duration: 0.8, delay: 0.45 }}
              className={HERO_STAGE_CLASS}
            >
              <motion.div
                {...getFloatingMotion({ delay: 0, distance: 8, duration: 8, direction: "up", enabled: isSectionLive(0) })}
                className={`absolute left-0 top-0 z-10 bg-white p-5 rounded-3xl border border-slate-100 shadow-xl shadow-indigo-900/5 flex flex-col justify-between pointer-events-auto cursor-pointer hover:!scale-105 transition-transform ${HERO_FLOATING_CARD_SIZE_CLASS}`}
              >
                <div className="flex items-center gap-3 border-b border-slate-100 pb-3">
                  <div className="w-8 h-8 rounded-full bg-indigo-50 flex items-center justify-center relative shrink-0">
                    <Mic className="w-4 h-4 text-indigo-600 z-10" />
                    <motion.div
                      animate={isSectionLive(0) ? { scale: [1, 1.4, 1], opacity: [0.5, 0, 0.5] } : { scale: 1, opacity: 0.45 }}
                      transition={isSectionLive(0) ? { repeat: Infinity, duration: 1.5 } : { duration: 0.2 }}
                      className="absolute inset-0 bg-indigo-200 rounded-full"
                    />
                  </div>
                  <div className="flex-1 overflow-hidden">
                    <h4 className={`${CARD_TITLE_CLASS} leading-tight`}>AI 模拟面试</h4>
                    <div className={`text-slate-400 font-mono mt-0.5 truncate ${CARD_META_CLASS}`}>支持文字 / 语音 / Live 测试模式</div>
                  </div>
                  <Badge variant="default" className="text-[11px] bg-slate-50 shrink-0">模拟面试</Badge>
                </div>
                <div className="flex-1 bg-slate-50 rounded-xl p-3 border border-slate-100/50 mt-3 flex flex-col justify-between">
                  <div>
                    <p className={`${UI_META_CLASS} font-mono flex items-center gap-1.5 mb-2`}>
                      <span className="w-1.5 h-1.5 rounded-full bg-rose-500 animate-pulse"></span>
                      用户表达中 <span className="text-slate-300 ml-auto">~120ms</span>
                    </p>
                    <p className="text-[13px] leading-relaxed text-slate-700">
                      “主要负责把
                      <span className="bg-indigo-100 text-indigo-700 px-1 rounded mx-0.5">复杂流程</span>
                      重新梳理，让团队更容易
                      <span className="bg-indigo-100 text-indigo-700 px-1 rounded ml-0.5">协作推进</span>
                      …”
                      <span className="w-1.5 h-3 bg-slate-400 inline-block animate-pulse align-middle ml-1"></span>
                    </p>
                  </div>
                  <div className="flex items-end justify-center gap-1.5 h-6">
                    {[1, 2, 3, 4, 5, 6, 7, 8].map((index) => (
                      <motion.div
                        key={index}
                        className="w-1.5 bg-indigo-400/80 rounded-full"
                        animate={isSectionLive(0) ? { height: ["20%", index % 2 === 0 ? "100%" : "70%", "20%"] } : { height: index % 2 === 0 ? "55%" : "38%" }}
                        transition={isSectionLive(0) ? { repeat: Infinity, duration: 0.8 + index * 0.1 } : { duration: 0.2 }}
                      />
                    ))}
                  </div>
                </div>
              </motion.div>

              <motion.div
                {...getFloatingMotion({ delay: 1, distance: 8, duration: 8, direction: "up", enabled: isSectionLive(0) })}
                className={`absolute right-0 top-0 z-10 bg-white p-5 rounded-3xl border border-slate-100 shadow-xl shadow-emerald-900/5 flex flex-col justify-between pointer-events-auto cursor-pointer hover:!scale-105 transition-transform ${HERO_FLOATING_CARD_SIZE_CLASS}`}
              >
                <div className="flex justify-between items-center pb-2">
                  <span className={`font-bold text-slate-800 flex items-center gap-1.5 bg-slate-100 px-2 py-1 rounded-md shrink-0 ${CARD_META_CLASS}`}><Briefcase className="w-3.5 h-3.5 text-slate-500" /> 任务 102</span>
                  <span className="text-amber-500 font-bold text-[15px] shrink-0">企业复看中</span>
                </div>
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-lg bg-slate-900 text-white flex justify-center items-center text-[11px] font-bold shrink-0">企</div>
                  <div className="flex-1 overflow-hidden">
                    <h4 className={`${CARD_TITLE_CLASS} truncate`}>校招 JD 亮点整理</h4>
                    <p className={`${UI_CAPTION_MUTED_CLASS} mt-0.5 flex items-center gap-1`}><ShieldCheck className="w-3 h-3 text-emerald-500" /> 奖励、截止时间和提交状态一眼能看清</p>
                  </div>
                </div>
                <div className={`flex items-center justify-between border-t border-slate-100 pt-3 ${UI_META_TINT_CLASS}`}>
                  <span className="bg-slate-100 text-slate-500 px-2 py-1 rounded">已提交</span>
                  <ArrowRight className="w-3 h-3 text-slate-300" />
                  <span className="bg-emerald-100 text-emerald-700 px-2 py-1 rounded border border-emerald-200 shadow-[0_0_8px_rgba(16,185,129,0.22)]">复看中</span>
                </div>
                <div className={`bg-emerald-50 rounded p-2 flex justify-between items-center border border-emerald-100/50 ${UI_META_CLASS} text-emerald-700`}>
                  <span className="flex items-center gap-1"><CheckCircle2 className="w-3 h-3" /> 结果会继续回到通知中心</span>
                  <span className="font-bold">等待反馈</span>
                </div>
              </motion.div>

              <motion.div
                {...getFloatingMotion({ delay: 0.5, distance: 8, duration: 8.5, direction: "down", enabled: isSectionLive(0) })}
                className={`absolute left-[18%] bottom-0 z-20 bg-white p-5 rounded-3xl border border-indigo-100 shadow-2xl shadow-indigo-900/10 flex flex-col justify-between pointer-events-auto cursor-pointer hover:!scale-105 transition-transform ${HERO_FLOATING_CARD_SIZE_CLASS}`}
              >
                <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                  <div className="flex items-center gap-2">
                    <Badge variant="warning" className="text-[11px] px-1.5 py-0.5 shrink-0">成长画像</Badge>
                    <span className={`${CARD_TITLE_CLASS} truncate`}>本周能力摘要</span>
                  </div>
                  <Badge variant="default" className="text-[11px] bg-slate-50 text-slate-500 shrink-0">近 7 天</Badge>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Badge className={`${CARD_META_CLASS} bg-indigo-50 text-indigo-700`}>表达清晰</Badge>
                  <Badge className={`${CARD_META_CLASS} bg-emerald-50 text-emerald-700`}>按时交付</Badge>
                  <Badge className={`${CARD_META_CLASS} bg-amber-50 text-amber-700`}>主动复盘</Badge>
                </div>
                <div className="space-y-3">
                  <div>
                    <div className={`flex justify-between mb-1 ${UI_META_CLASS}`}><span>前端开发能力</span><span className="font-bold text-indigo-600">92%</span></div>
                    <div className="w-full bg-slate-100 h-1.5 rounded-full overflow-hidden">
                      <motion.div initial={{ width: 0 }} animate={{ width: hasSectionEntered(0) ? "92%" : 0 }} transition={{ duration: 1.05, ease: "easeOut" }} className="bg-indigo-500 h-full rounded-full" />
                    </div>
                  </div>
                  <div>
                    <div className={`flex justify-between mb-1 ${UI_META_CLASS}`}><span>社区贡献度</span><span className="font-bold text-emerald-600">78%</span></div>
                    <div className="w-full bg-slate-100 h-1.5 rounded-full overflow-hidden">
                      <motion.div initial={{ width: 0 }} animate={{ width: hasSectionEntered(0) ? "78%" : 0 }} transition={{ duration: 1.05, ease: "easeOut", delay: 0.18 }} className="bg-emerald-500 h-full rounded-full" />
                    </div>
                  </div>
                </div>
              </motion.div>

              <motion.div
                {...getFloatingMotion({ delay: 1.5, distance: 8, duration: 8.5, direction: "down", enabled: isSectionLive(0) })}
                className={`absolute right-[18%] bottom-0 z-20 bg-white p-5 rounded-3xl border border-purple-100 shadow-2xl shadow-purple-900/10 flex flex-col justify-between pointer-events-auto cursor-pointer hover:!scale-105 transition-transform ${HERO_FLOATING_CARD_SIZE_CLASS}`}
              >
                <div className="flex items-center justify-between pb-2 border-b border-slate-100">
                  <div className="flex items-center gap-2">
                    <div className="w-7 h-7 rounded-full bg-purple-50 flex items-center justify-center shrink-0"><FileCheck className="w-3.5 h-3.5 text-purple-600" /></div>
                    <span className={CARD_TITLE_CLASS}>AI 结构化分析</span>
                  </div>
                  <Badge variant="glow" className="text-[11px] bg-purple-50 text-purple-600 shadow-none border-purple-100 shrink-0">持续更新</Badge>
                </div>
                <div className="bg-slate-50 rounded-xl p-3 border border-slate-100/50 flex-1 my-3 flex flex-col justify-center">
                  <div className={`flex justify-between items-center mb-3 ${UI_BODY_SM_CLASS}`}>
                    <span className="text-slate-500 font-medium">综合竞争力</span>
                    <motion.span
                      animate={isSectionLive(0) ? { color: ["#94a3b8", "#10b981", "#10b981", "#94a3b8"] } : { color: "#10b981" }}
                      transition={isSectionLive(0) ? { repeat: Infinity, duration: 4 } : { duration: 0.2 }}
                      className="font-bold text-emerald-500"
                    >
                      85 ↗ 92
                    </motion.span>
                  </div>
                  <div className="space-y-2">
                    <div className="h-1.5 bg-slate-200 rounded-full w-full"></div>
                    <div className="h-1.5 bg-slate-100 rounded-full w-full overflow-hidden relative">
                      <motion.div
                        animate={isSectionLive(0) ? { width: ["0%", "100%", "100%", "0%"] } : { width: "76%" }}
                        transition={isSectionLive(0) ? { repeat: Infinity, duration: 4 } : { duration: 0.2 }}
                        className="absolute h-full bg-purple-400 rounded-full"
                      />
                    </div>
                    <div className="h-1.5 bg-slate-100 rounded-full w-4/5 overflow-hidden relative">
                      <motion.div
                        animate={isSectionLive(0) ? { width: ["0%", "80%", "80%", "0%"] } : { width: "58%" }}
                        transition={isSectionLive(0) ? { repeat: Infinity, duration: 4, delay: 0.4 } : { duration: 0.2 }}
                        className="absolute h-full bg-purple-400 rounded-full"
                      />
                    </div>
                  </div>
                </div>
                <div className={`flex items-center justify-between bg-emerald-50/60 border border-emerald-100 p-2 rounded shrink-0 ${CARD_META_CLASS}`}>
                  <span className="flex items-center gap-1.5 text-emerald-700"><CheckCircle2 className="w-3 h-3" /> 建议已整理成后续行动</span>
                  <span className="text-emerald-600 font-bold">可继续跟进</span>
                </div>
              </motion.div>
            </motion.div>
          </div>
        </section>

        <section className="h-[100dvh] w-full relative flex items-center justify-center shrink-0">
          <div className="max-w-7xl mx-auto px-6 w-full grid grid-cols-1 xl:grid-cols-[0.9fr_1.1fr] gap-8 xl:gap-12 items-center relative z-10 mt-12">
            <motion.div {...getSectionMotionProps(1)} variants={staggerContainer} className="max-w-xl">
              <motion.div variants={fadeUp}><Badge variant="default" className={`mb-4 bg-violet-100/85 text-violet-700 border-violet-200 ${SECTION_BADGE_CLASS}`}>适合谁</Badge></motion.div>
              <motion.h2 variants={fadeUp} className={SECTION_TITLE_CLASS}>看看你更适合<br /><span className="text-violet-600">从哪里开始</span></motion.h2>
              <motion.p variants={fadeUp} className="text-slate-600 text-[1rem] md:text-[1.08rem] lg:text-[1.12rem] leading-[1.82] max-w-lg">
                你不用一次看完整个平台。先找到现在最像你的那个阶段，从最需要的一步开始就好。
              </motion.p>
              <motion.div variants={fadeUp} className="mt-6 rounded-[2rem] border border-violet-100/80 bg-violet-50/78 px-5 py-5 shadow-lg shadow-violet-100/40">
                <p className="text-[15px] font-semibold text-slate-900">你可以先从最需要的一步开始，后面的导师咨询和真实任务会继续接上。</p>
                <div className="flex flex-wrap gap-2.5 mt-4">
                  {STUDENT_SUPPORT_PILLARS.map((item) => (
                    <span key={item} className={`inline-flex items-center rounded-full border border-violet-100 bg-white/90 px-3.5 py-2 text-violet-700 ${UI_CAPTION_CLASS}`}>
                      {item}
                    </span>
                  ))}
                </div>
              </motion.div>
            </motion.div>

            <motion.div variants={fadeLeft} {...getSectionMotionProps(1)} className="grid grid-cols-1 sm:grid-cols-2 gap-4 xl:gap-5 max-w-3xl xl:max-w-none w-full">
              {STUDENT_STAGE_PATHS.map((item, index) => {
                const Icon = item.icon;
                const offsetClass = index % 2 === 1 ? "xl:translate-y-5" : "";
                return (
                  <motion.button
                    key={item.title}
                    variants={fadeUp}
                    type="button"
                    onClick={() => changeSection(item.sectionIndex)}
                    className={`group relative isolate overflow-hidden text-left border bg-white/92 px-5 py-5 shadow-xl transition-colors duration-300 ${item.shellClass} ${offsetClass}`}
                  >
                    <div className={`pointer-events-none absolute -right-10 -top-10 h-28 w-28 rounded-full bg-gradient-to-br blur-2xl ${item.glowClass}`} />
                    <div className={`pointer-events-none absolute -left-6 bottom-6 h-16 w-16 rounded-full bg-gradient-to-br opacity-70 blur-xl ${item.glowClass}`} />
                    <div className="flex items-start justify-between gap-4">
                      <div className="relative z-10">
                        <span className={`inline-flex items-center rounded-full border px-3 py-1 text-[11px] font-semibold tracking-[0.18em] ${item.stepClass}`}>
                          {item.step}
                        </span>
                        <h3 className="mt-3 text-[1.18rem] font-bold text-slate-900">{item.title}</h3>
                        <p className={`mt-2 ${UI_CAPTION_MUTED_CLASS}`}>{item.subtitle}</p>
                      </div>
                      <div className={`relative z-10 w-11 h-11 rounded-[1.25rem] border flex items-center justify-center shadow-sm ${item.iconClass}`}>
                        <Icon className="w-5 h-5" />
                      </div>
                    </div>

                    <p className={`relative z-10 mt-5 ${UI_BODY_SM_CLASS}`}>{item.issue}</p>

                    <div className="relative z-10 mt-5 flex flex-wrap items-center gap-2">
                        <span className={`inline-flex items-center rounded-full border px-3 py-1.5 text-[12px] font-semibold ${item.chipClass}`}>
                        {item.start}
                      </span>
                      <span className={UI_CAPTION_MUTED_CLASS}>{item.hint}</span>
                    </div>

                    <div className="relative z-10 mt-6 flex items-center justify-between">
                      <span className={`inline-flex items-center gap-2 font-semibold text-slate-800 group-hover:text-violet-600 ${UI_CAPTION_CLASS}`}>
                        从这里开始看看
                      </span>
                      <span className={`inline-flex h-9 w-9 items-center justify-center rounded-full border transition-transform duration-300 group-hover:translate-x-1 ${item.arrowClass}`}>
                        <ArrowRight className="w-3.5 h-3.5" />
                      </span>
                    </div>
                  </motion.button>
                );
              })}
            </motion.div>
          </div>
        </section>

        <section className="h-[100dvh] w-full relative flex items-center justify-center shrink-0">
          <div className="max-w-7xl mx-auto px-6 w-full grid grid-cols-1 lg:grid-cols-2 gap-12 lg:gap-16 items-center lg:items-stretch relative z-10">
            <motion.div variants={fadeRight} {...getSectionMotionProps(2)} className="h-full">
              <motion.div variants={fadeUp} className="h-full lg:min-h-[39rem] flex flex-col justify-between">
                <Badge variant="primary" className={`self-start mb-4 bg-indigo-100/85 text-indigo-700 border-indigo-200 ${SECTION_BADGE_CLASS}`}>AI 核心工具</Badge>
                <h2 className={SECTION_TITLE_CLASS}>简历优化、模拟面试与<br /><span className="text-indigo-600">你的求职准备工作台</span></h2>
                <p className={SECTION_BODY_LIGHT_CLASS}>
                  如果你现在卡在材料没整理顺、表达还不够稳，或者每次练完都像重新开始，可以先从这里做第一轮准备。这里会帮你看清岗位重点、整理材料、暴露问题，也把每一轮结果留下来。
                </p>
                <ul className="space-y-4 mb-8">
                  {[
                    "支持文本 / PDF 简历双入口，适合第一次整理校园经历和项目亮点",
                    "模拟面试支持文字 / 语音 / Live 测试模式，可以按更顺手的方式练习",
                    "每轮结果都会同步到 AI 复盘中心，方便继续回看和接着练",
                  ].map((item) => (
                    <li key={item} className="flex items-center text-slate-600 text-[15px] md:text-base">
                      <CheckCircle2 className="w-5 h-5 mr-3 text-indigo-500" /> {item}
                    </li>
                  ))}
                </ul>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 max-w-2xl">
                  <motion.div variants={fadeUp} className="rounded-2xl border border-indigo-100/80 bg-white/86 p-4 shadow-sm shadow-indigo-100/40 h-full">
                    <p className={`text-indigo-700 mb-3 ${UI_CAPTION_LABEL_CLASS}`}>你可能会在这些时候先来试试</p>
                    <div className="space-y-3">
                      {AI_ENTRY_POINTS.map((item) => (
                        <div key={item} className={`flex items-start gap-2 ${UI_BODY_SM_CLASS}`}>
                          <CheckCircle2 className="w-4 h-4 text-indigo-500 mt-0.5 shrink-0" />
                          <span>{item}</span>
                        </div>
                      ))}
                    </div>
                  </motion.div>
                  <motion.div variants={fadeUp} className="rounded-2xl border border-indigo-100/80 bg-white/86 p-4 shadow-sm shadow-indigo-100/40 h-full flex flex-col">
                    <p className={`text-indigo-700 mb-3 ${UI_CAPTION_LABEL_CLASS}`}>一次准备会走过</p>
                    <div className="space-y-3">
                      {AI_PREP_FLOW.map((item, index) => (
                        <div key={item.title} className="flex items-start gap-3">
                          <span className="mt-0.5 inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full border border-indigo-100 bg-indigo-50 text-[12px] font-bold text-indigo-600">
                            0{index + 1}
                          </span>
                          <div>
                            <p className="text-[14px] font-semibold text-slate-900">{item.title}</p>
                            <p className={UI_CAPTION_MUTED_CLASS}>{item.detail}</p>
                          </div>
                        </div>
                      ))}
                    </div>
                    <div className={`mt-auto pt-4 rounded-xl border border-indigo-100 bg-indigo-50/90 px-3 py-2 text-indigo-700 ${UI_CAPTION_CLASS}`}>
                      这些结果会继续沉淀到 AI 复盘中心和成长记录里，方便下一轮直接接上。
                    </div>
                  </motion.div>
                </div>
              </motion.div>
            </motion.div>
            <motion.div variants={fadeLeft} {...getSectionMotionProps(2)} className="relative h-full flex">
              <div className="bg-white/96 border border-indigo-100/80 rounded-2xl overflow-hidden shadow-2xl shadow-indigo-200/32 h-full min-h-[39rem] flex flex-col">
                <div className="flex items-center gap-2 p-3 bg-white/88 border-b border-slate-200">
                  <div className="w-3 h-3 rounded-full bg-rose-500/80"></div>
                  <div className="w-3 h-3 rounded-full bg-amber-500/80"></div>
                  <div className="w-3 h-3 rounded-full bg-emerald-500/80"></div>
                  <span className={`ml-2 font-mono ${UI_META_CLASS}`}>你的求职准备工作台</span>
                </div>
                <div className="p-6 h-full flex flex-col gap-4">
                  <motion.div variants={fadeUp} className="rounded-2xl border border-indigo-100 bg-indigo-50/85 p-4">
                    <div className="flex items-start justify-between gap-4 mb-3">
                      <div>
                        <p className={`text-indigo-600 mb-1 ${UI_CAPTION_CLASS}`}>你的当前目标</p>
                        <h3 className="text-[1.35rem] font-semibold text-slate-900">大三学生 · Java 后端实习准备</h3>
                      </div>
                      <Badge variant="glow" className="bg-white/85 text-indigo-700 border-indigo-200 shadow-none shrink-0">已整理 3 项行动</Badge>
                    </div>
                    <p className="text-[14px] text-slate-600 leading-7">
                      简历亮点、追问风险和下一步建议已经整理完成，现在可以继续进入文字或语音模拟面试，把校园经历逐步讲成更稳定的岗位表达。
                    </p>
                  </motion.div>

                  <div className="grid grid-cols-3 gap-3">
                    {[
                      { label: "简历完成度", value: "88%", width: "88%" },
                      { label: "表达稳定度", value: "76%", width: "76%" },
                      { label: "历史沉淀", value: "已同步", width: "100%" },
                    ].map((item, index) => (
                      <motion.div key={item.label} variants={fadeUp} className="rounded-2xl border border-slate-200 bg-white/82 p-3 shadow-sm shadow-slate-100/60">
                        <p className={`mb-2 ${UI_META_CLASS}`}>{item.label}</p>
                        <div className="flex items-center justify-between mb-2">
                          <span className="text-[15px] font-semibold text-slate-900">{item.value}</span>
                          <span className={`text-indigo-600 ${UI_META_CLASS}`}>本轮</span>
                        </div>
                        <div className="h-1.5 rounded-full bg-slate-100 overflow-hidden">
                          <motion.div
                            initial={{ width: 0 }}
                            animate={{ width: hasSectionEntered(2) ? item.width : 0 }}
                            transition={{ duration: 0.9, delay: 0.18 + index * 0.08 }}
                            className="h-full rounded-full bg-gradient-to-r from-indigo-500 via-violet-500 to-fuchsia-500"
                          />
                        </div>
                      </motion.div>
                    ))}
                  </div>

                  <motion.div variants={fadeUp} className="rounded-2xl border border-slate-200 bg-white/82 p-4 shadow-sm shadow-slate-100/60">
                    <div className="flex items-center justify-between mb-3">
                      <p className={UI_BODY_EMPHASIS_CLASS}>本轮会直接得到</p>
                      <span className={`text-emerald-600 ${UI_CAPTION_CLASS}`}>可以继续承接</span>
                    </div>
                    <div className="space-y-3">
                      {AI_RESULT_POINTS.map((item) => (
                        <div key={item} className={`flex items-start gap-2 ${UI_BODY_SM_CLASS}`}>
                          <CheckCircle2 className="w-4 h-4 text-emerald-500 mt-0.5 shrink-0" />
                          <span>{item}</span>
                        </div>
                      ))}
                    </div>
                  </motion.div>

                  <motion.div variants={fadeUp} className="rounded-2xl border border-slate-200 bg-white/82 p-4 shadow-sm shadow-slate-100/60">
                    <div className="flex items-center justify-between mb-3">
                      <p className={UI_BODY_EMPHASIS_CLASS}>下一步推荐</p>
                      <span className={`text-emerald-600 ${UI_CAPTION_CLASS}`}>可以直接执行</span>
                    </div>
                    <div className="space-y-3">
                      {[
                        "补 1 条能体现结果的校园项目数据，让简历更像真实经历。",
                        "用 STAR 结构复述一次项目挑战，减少面试时的停顿感。",
                        "完成 1 次语音练习，把表达问题继续沉淀到成长记录中。",
                      ].map((item) => (
                        <div key={item} className={`flex items-start gap-2 ${UI_BODY_SM_CLASS}`}>
                          <CheckCircle2 className="w-4 h-4 text-emerald-500 mt-0.5 shrink-0" />
                          <span>{item}</span>
                        </div>
                      ))}
                    </div>
                  </motion.div>

                  <div className="flex flex-wrap gap-2 mt-auto pt-1">
                    {["已同步 AI 复盘中心", "支持文字 / 语音 / Live", "结果会带入成长画像"].map((item) => (
                      <span key={item} className={`inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 ${UI_CAPTION_CLASS} text-slate-600`}>
                        {item}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            </motion.div>
          </div>
        </section>

        <section className="h-[100dvh] w-full relative flex items-center justify-center shrink-0">
          <div className="max-w-7xl mx-auto px-6 w-full grid grid-cols-1 lg:grid-cols-2 gap-12 lg:gap-16 items-center lg:items-stretch relative z-10">
            <motion.div variants={fadeRight} {...getSectionMotionProps(3)} className="order-2 lg:order-1 relative h-full flex">
              <div className="bg-white/96 p-8 rounded-3xl border border-slate-100 shadow-xl shadow-[#2F6B5F]/10 h-full min-h-[41rem] flex flex-col">
                <div className="flex items-center justify-between mb-6">
                  <span className="text-[15px] font-bold text-slate-800 flex items-center gap-2">
                    <LineChart className="w-5 h-5 text-sky-600" /> 成长路径看板
                  </span>
                  <Badge variant="default" className={`bg-sky-100/85 text-sky-700 border-sky-200 ${CARD_META_CLASS}`}>工作台摘要</Badge>
                </div>
                <div className="grid grid-cols-3 gap-3 mb-6">
                  {GROWTH_SUMMARY_STATS.map((item) => (
                    <motion.div key={item.label} variants={fadeUp} className="rounded-2xl border border-slate-100 bg-slate-50/80 px-3 py-3">
                      <p className={`mb-1 ${UI_META_CLASS}`}>{item.label}</p>
                      <p className={`text-[15px] font-bold ${item.tone}`}>{item.value}</p>
                    </motion.div>
                  ))}
                </div>
                <motion.div variants={fadeUp} className="rounded-2xl border border-slate-100 bg-slate-50/85 p-4 mb-6">
                  <p className={`text-slate-500 mb-2 ${UI_CAPTION_LABEL_CLASS}`}>成长状态概览</p>
                  <p className={UI_BODY_SM_CLASS}>
                    这里会把签到、AI 练习、画像刷新和下一阶段入口整理成一条可以继续推进的路径，让你随时知道自己离下一步还差什么。
                  </p>
                </motion.div>
                <div className="space-y-6 flex-1">
                  <div className="flex items-start gap-4">
                    <div className="mt-1 w-8 h-8 rounded-full bg-emerald-100 flex items-center justify-center shrink-0"><CheckCircle2 className="w-4 h-4 text-emerald-600" /></div>
                    <div>
                      <h4 className="font-bold text-[17px] text-slate-800">基础表达与项目整理</h4>
                      <p className={`mt-1 ${UI_BODY_SM_MUTED_CLASS}`}>状态：<Badge variant="success" className="px-1.5 py-0 text-[11px]">已掌握</Badge></p>
                      <p className={`mt-2 ${UI_CAPTION_MUTED_CLASS}`}>已经完成简历亮点梳理和基础自我介绍整理，能够支撑投递前的第一轮准备。</p>
                    </div>
                  </div>
                  <div className="w-0.5 h-6 bg-slate-200 ml-4 -my-4"></div>
                  <div className="flex items-start gap-4">
                    <div className="mt-1 w-8 h-8 rounded-full border-2 border-[#5F9F92] flex items-center justify-center shrink-0 relative">
                      <div className="w-3 h-3 bg-[#5F9F92] rounded-full"></div>
                      <motion.div
                        animate={isSectionLive(3) ? { scale: [1, 1.45, 1], opacity: [0.45, 0, 0.45] } : { scale: 1, opacity: 0.35 }}
                        transition={isSectionLive(3) ? { repeat: Infinity, duration: 2.1 } : { duration: 0.2 }}
                        className="absolute inset-0 rounded-full border border-[#5F9F92]"
                      />
                    </div>
                    <div>
                      <h4 className="font-bold text-[17px] text-slate-800">模拟面试与复盘表达</h4>
                      <p className={`mt-1 ${UI_BODY_SM_MUTED_CLASS}`}>状态：<Badge variant="primary" className="px-1.5 py-0 text-[11px]">进阶中（60%）</Badge></p>
                      <p className={`mt-2 ${UI_CAPTION_MUTED_CLASS}`}>当前重点是把项目经历说得更稳定，减少停顿和泛化表述，让每次练习都能沉淀成下一轮改进动作。</p>
                    </div>
                  </div>
                  <div className="w-0.5 h-6 bg-slate-100 ml-4 -my-4"></div>
                  <div className="flex items-start gap-4 opacity-70">
                    <div className="mt-1 w-8 h-8 rounded-full bg-slate-100 flex items-center justify-center shrink-0"><Lock className="w-4 h-4 text-slate-400" /></div>
                    <div>
                      <h4 className="font-bold text-[17px] text-slate-800">导师深度答疑与企业实践</h4>
                      <p className={`mt-1 ${UI_BODY_SM_MUTED_CLASS}`}>状态：<Badge variant="default" className="px-1.5 py-0 text-[11px]">待开启</Badge></p>
                      <p className={`mt-2 ${UI_CAPTION_MUTED_CLASS}`}>当你想把问题拆得更深、或者想把成果带到真实任务里时，就可以继续去找导师咨询或企业实战任务。</p>
                    </div>
                  </div>
                </div>
                <motion.div variants={fadeUp} className="mt-6 rounded-2xl border border-sky-100 bg-sky-50/75 px-4 py-4">
                  <div className="flex items-center justify-between gap-4 mb-2">
                    <p className={`text-sky-700 ${UI_CAPTION_LABEL_CLASS}`}>下一步建议</p>
                    <span className={`text-sky-500 font-medium ${UI_CAPTION_CLASS}`}>做完这一轮后更容易判断</span>
                  </div>
                  <p className={UI_BODY_SM_CLASS}>
                    再完成一轮模拟面试和一次结果复盘后，你会更清楚是继续打磨表达，还是直接带着问题去找导师咨询。
                  </p>
                </motion.div>
              </div>
            </motion.div>
            <motion.div variants={fadeLeft} {...getSectionMotionProps(3)} className="order-1 lg:order-2 h-full">
              <motion.div variants={fadeUp} className="h-full min-h-[41rem] flex flex-col justify-between">
                <Badge variant="default" className={`self-start mb-4 bg-sky-100/90 text-sky-700 border-sky-200 ${SECTION_BADGE_CLASS}`}>画像与成长</Badge>
                <h2 className={SECTION_TITLE_CLASS}>画像、技能树与<br /><span className="text-sky-600">你的成长工作台</span></h2>
                <p className={SECTION_BODY_LIGHT_CLASS}>
                  这里会把练习、互动和任务记录成一条看得见的成长路径。你不用每次都从头开始，系统会告诉你现在走到哪一步，下一步更值得做什么。
                </p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 max-w-xl mb-6">
                  <motion.div variants={fadeUp} className="rounded-2xl border border-sky-100 bg-white/86 p-4">
                    <p className={`text-sky-600 mb-3 ${UI_CAPTION_LABEL_CLASS}`}>动态画像标签</p>
                    <div className="flex flex-wrap gap-2 mb-3">
                      {GROWTH_PORTRAIT_TAGS.map((tag) => (
                        <span key={tag} className={`inline-flex items-center rounded-full bg-sky-50 px-3 py-1.5 text-sky-700 border border-sky-100 ${UI_CAPTION_CLASS}`}>
                          {tag}
                        </span>
                      ))}
                    </div>
                    <p className={UI_BODY_SM_MUTED_CLASS}>AI 练习、社区互动和任务参与都会继续更新这些标签，而不是只留下零散记录。</p>
                  </motion.div>
                  <motion.div variants={fadeUp} className="rounded-2xl border border-emerald-100 bg-emerald-50/85 p-4">
                    <p className={`text-emerald-700 mb-3 ${UI_CAPTION_LABEL_CLASS}`}>今日推荐动作</p>
                    <div className="space-y-3">
                      {GROWTH_NEXT_ACTIONS.map((item) => (
                        <div key={item.title} className="rounded-xl border border-emerald-100 bg-white/65 px-3 py-2.5">
                          <p className={UI_BODY_EMPHASIS_CLASS}>{item.title}</p>
                          <p className={`mt-1 ${UI_CAPTION_MUTED_CLASS}`}>{item.detail}</p>
                        </div>
                      ))}
                    </div>
                  </motion.div>
                </div>
                <motion.div variants={fadeUp} className="rounded-2xl border border-slate-200 bg-white/88 p-4 max-w-xl mb-6">
                  <p className={`text-slate-500 mb-3 ${UI_CAPTION_LABEL_CLASS}`}>成长记录会从哪里来</p>
                  <div className="flex flex-wrap gap-2">
                    {GROWTH_SIGNAL_SOURCES.map((item) => (
                      <span key={item} className={`inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-slate-600 ${UI_CAPTION_CLASS}`}>
                        {item}
                      </span>
                    ))}
                  </div>
                  <p className={`mt-3 ${UI_BODY_SM_MUTED_CLASS}`}>所以你做过的每一次练习，都会被接住，而不是散在不同地方。</p>
                </motion.div>
                <motion.div variants={fadeUp} className="bg-sky-50/88 rounded-2xl p-6 border border-sky-100 max-w-lg">
                  <div className="flex justify-between items-center mb-3">
                    <span className="text-[15px] font-bold text-slate-800">最近 7 天成长记录</span>
                    <span className={UI_BODY_SM_MUTED_CLASS}>余额：1,250 积分</span>
                  </div>
                  <div className="space-y-3 text-[15px]">
                    <div className="flex justify-between border-b border-sky-100/70 pb-3">
                      <span className="text-slate-600">连续签到 7 天</span>
                      <span className="text-emerald-600 font-bold">+100</span>
                    </div>
                    <div className="flex justify-between border-b border-sky-100/70 pb-3">
                      <span className="text-slate-600">完成一次简历优化</span>
                      <span className="text-emerald-600 font-bold">+10</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-slate-600">完成一轮模拟面试</span>
                      <span className="text-emerald-600 font-bold">+20</span>
                    </div>
                  </div>
                </motion.div>
              </motion.div>
            </motion.div>
          </div>
        </section>

        <section className="h-[100dvh] w-full relative flex items-center justify-center shrink-0">
          <div className="max-w-7xl mx-auto px-6 w-full grid grid-cols-1 lg:grid-cols-2 gap-12 lg:gap-16 items-center lg:items-stretch relative z-10">
            <motion.div variants={fadeRight} {...getSectionMotionProps(4)} className="h-full">
              <motion.div variants={fadeUp} className="h-full min-h-[38rem] flex flex-col justify-between">
                <Badge variant="success" className={`self-start mb-4 bg-emerald-100/90 text-emerald-800 border-emerald-200 ${SECTION_BADGE_CLASS}`}>导师咨询</Badge>
                <h2 className={SECTION_TITLE_CLASS}>真人导师咨询与<br /><span className="text-emerald-600">订单承接闭环</span></h2>
                <p className={SECTION_BODY_LIGHT_CLASS}>
                  当你已经知道问题大概在哪，但还想有人陪你把它拆开讲透，这里就该接上了。你可以继续带着简历、项目表达或求职路线的问题来问，整次咨询也都会被完整记录下来。
                </p>
                <motion.div variants={fadeUp} className="bg-white/94 p-5 rounded-2xl border border-slate-200 shadow-sm mt-8 max-w-xl">
                  <p className={`text-slate-500 mb-4 ${UI_CAPTION_LABEL_CLASS}`}>咨询流程</p>
                  <div className="flex items-center gap-2 overflow-x-auto pb-2">
                    {CONSULT_STAGES.map((status, index) => (
                      <React.Fragment key={status}>
                        <Badge variant={status === "已支付" ? "success" : "default"} className={`shrink-0 ${CARD_META_CLASS}`}>{status}</Badge>
                        {index < CONSULT_STAGES.length - 1 && <ArrowRight className="w-3 h-3 text-slate-300 shrink-0" />}
                      </React.Fragment>
                    ))}
                  </div>
                </motion.div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 max-w-xl mt-6">
                  <motion.div variants={fadeUp} className="rounded-2xl border border-slate-200 bg-white/88 p-4">
                    <p className={`text-slate-500 mb-3 ${UI_CAPTION_LABEL_CLASS}`}>适合现在来问</p>
                    <div className="space-y-3">
                      {MENTOR_TOPICS.map((item) => (
                        <div key={item} className={`flex items-start gap-2 ${UI_BODY_SM_CLASS}`}>
                          <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0 mt-0.5" />
                          <span>{item}</span>
                        </div>
                      ))}
                    </div>
                  </motion.div>
                  <motion.div variants={fadeUp} className="rounded-2xl border border-emerald-100 bg-emerald-50/85 p-4">
                    <p className={`text-emerald-700 mb-3 ${UI_CAPTION_LABEL_CLASS}`}>服务保障</p>
                    <div className="flex flex-wrap gap-2 mb-3">
                      {MENTOR_GUARANTEES.map((item) => (
                        <span key={item} className={`inline-flex items-center rounded-full border border-emerald-100 bg-white px-3 py-1.5 text-emerald-700 ${UI_CAPTION_CLASS}`}>
                          {item}
                        </span>
                      ))}
                    </div>
                    <p className={UI_BODY_SM_CLASS}>支付、回复、评价和售后都在同一条订单链路里完成，你随时都能回看这次咨询怎么推进。</p>
                  </motion.div>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 max-w-2xl mt-6">
                  {MENTOR_VALUE_CHAIN.map((item) => (
                    <motion.div key={item.title} variants={fadeUp} className="rounded-2xl border border-slate-200 bg-white/88 p-4">
                      <p className={`text-slate-500 mb-2 ${UI_CAPTION_LABEL_CLASS}`}>{item.title}</p>
                      <p className={UI_BODY_SM_CLASS}>{item.detail}</p>
                    </motion.div>
                  ))}
                </div>
              </motion.div>
            </motion.div>
            <motion.div variants={fadeLeft} {...getSectionMotionProps(4)} className="h-full flex">
              <div className="bg-white/96 p-7 rounded-3xl border border-slate-200 shadow-xl shadow-slate-200/50 max-w-[31rem] mx-auto h-full min-h-[38rem] flex flex-col">
                <div className="flex items-start gap-4 mb-6">
                  <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-emerald-400 to-teal-600 text-white flex items-center justify-center font-bold text-xl shadow-lg">李</div>
                  <div className="flex-1 min-w-0">
                    <h3 className="font-bold text-[1.35rem] text-slate-900">导师李然</h3>
                    <p className="text-[15px] text-slate-500 whitespace-nowrap">职业规划 / 简历诊断 / 后端面试</p>
                    <div className={`flex items-center gap-1 mt-1 text-amber-500 font-bold ${UI_BODY_SM_CLASS}`}>
                      <Star className="w-3 h-3 fill-amber-500" /> 4.9（近期完成 4 单）
                    </div>
                  </div>
                </div>
                <div className="space-y-3 mb-6">
                  <div className="flex justify-between text-[15px]"><span className="text-slate-500">单次咨询定价</span><span className="font-bold text-slate-800">￥99.00</span></div>
                  <div className="flex justify-between text-[15px] gap-4"><span className="text-slate-500 shrink-0">支付方式</span><span className="text-emerald-600 font-bold flex items-center gap-1 whitespace-nowrap"><CreditCard className="w-3.5 h-3.5" /> 平台支付</span></div>
                </div>
                <div className="rounded-2xl bg-slate-50 border border-slate-100 p-4 mb-5">
                  <p className={`text-slate-500 mb-3 ${UI_CAPTION_LABEL_CLASS}`}>近期可预约时段</p>
                  <div className="flex flex-wrap gap-2 mb-3">
                    {MENTOR_SLOTS.map((slot) => (
                      <span key={slot} className={`inline-flex items-center rounded-full border border-slate-200 bg-white px-3 py-1.5 text-slate-600 ${UI_CAPTION_CLASS}`}>
                        {slot}
                      </span>
                    ))}
                  </div>
                  <p className={UI_BODY_SM_MUTED_CLASS}>下单后会进入订单消息区，咨询进度、服务节点和处理结果都能持续查看。</p>
                </div>
                <div className="rounded-2xl bg-emerald-50 border border-emerald-100 p-4 mb-5">
                  <p className={`text-emerald-700 mb-2 ${UI_CAPTION_LABEL_CLASS}`}>最近评价</p>
                  <p className="text-[15px] text-slate-700 leading-7">“回复非常具体，帮我把问题拆成了可以马上动手改的三件事，第二次再看会明显更有方向。”</p>
                </div>
                <div className="rounded-2xl border border-indigo-100 bg-indigo-50/70 px-4 py-3 mb-5">
                  <p className={`text-indigo-700 mb-2 ${UI_CAPTION_LABEL_CLASS}`}>什么时候值得来找导师</p>
                  <p className={UI_BODY_SM_CLASS}>如果你已经在 AI 练习里看到了问题，但还想有人更具体地陪你拆路线、讲项目、判断方向，这里会更适合你。</p>
                </div>
                <Button variant="emerald" className="w-full mt-auto">看看咨询流程</Button>
              </div>
            </motion.div>
          </div>
        </section>

        <section className="h-[100dvh] w-full relative flex items-center justify-center shrink-0">
          <div className="max-w-7xl mx-auto px-6 w-full grid grid-cols-1 lg:grid-cols-[1.14fr_0.86fr] gap-9 lg:gap-12 items-center lg:items-stretch relative z-10 mt-16">
            <motion.div variants={fadeRight} {...getSectionMotionProps(5)} className="order-2 lg:order-1 relative h-full flex">
              <div className="bg-white/95 p-7 rounded-3xl border border-amber-100/80 shadow-xl shadow-amber-100/40 h-full min-h-[39rem] flex flex-col">
                <div className="flex items-center justify-between gap-4 mb-6">
                  <span className="text-[15px] font-bold text-slate-800 flex items-center gap-2">
                    <Briefcase className="w-5 h-5 text-amber-600" /> 真实任务看板
                  </span>
                  <Badge variant="warning" className={CARD_META_CLASS}>企业复看链路</Badge>
                </div>

                <div className="grid grid-cols-2 gap-3 mb-6">
                  {ENTERPRISE_PIPELINE.map((item, index) => (
                    <motion.div key={item.title} variants={fadeUp} className="rounded-2xl border border-amber-100/80 bg-amber-50/55 p-4 shadow-sm shadow-amber-100/30">
                      <p className={`font-semibold tracking-[0.16em] text-amber-600 ${CARD_META_CLASS}`}>0{index + 1}</p>
                      <p className="mt-2.5 text-[1.02rem] md:text-[1.08rem] font-bold leading-6 text-slate-950">{item.title}</p>
                      <p className={`mt-2 ${UI_CAPTION_MUTED_CLASS}`}>{item.detail}</p>
                    </motion.div>
                  ))}
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-[1.06fr_0.94fr] gap-4 flex-1">
                  {BOUNTY_TASKS.map((task, index) => (
                    <motion.div key={task.title} variants={fadeUp} {...getSectionMotionProps(5)} transition={{ delay: index * 0.08 }}>
                      <div className="bg-white/90 p-5 rounded-3xl border border-amber-100/80 shadow-lg shadow-amber-100/35 hover:border-amber-300/60 transition-colors h-full flex flex-col">
                        <div className="flex justify-between items-start mb-4 gap-4">
                          <Badge variant={task.accent === "success" ? "success" : "warning"} className={CARD_META_CLASS}>{task.status}</Badge>
                          <span className={`text-amber-600 font-bold text-right ${UI_BODY_SM_CLASS}`}>{task.reward}</span>
                        </div>
                        <h3 className="text-[1.16rem] font-bold text-slate-900 mb-3 leading-7">{task.title}</h3>
                        <div className="flex flex-wrap gap-2 mb-4">
                          {task.tags.map((tag) => (
                            <span key={tag} className={`bg-amber-50/75 text-amber-700 px-2 py-1 rounded-full border border-amber-100 ${UI_CAPTION_CLASS}`}>{tag}</span>
                          ))}
                        </div>
                        <div className="mt-auto rounded-2xl border border-slate-200 bg-slate-50/85 px-4 py-3">
                          <p className={UI_BODY_SM_CLASS}>{task.summary}</p>
                          <div className={`mt-3 flex items-center justify-between ${UI_CAPTION_MUTED_CLASS}`}>
                            <span>当前进度</span>
                            <span className="font-medium text-slate-800">{task.progress}</span>
                          </div>
                        </div>
                      </div>
                    </motion.div>
                  ))}
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-6">
                  {ENTERPRISE_VALUES.map((item) => (
                    <motion.div key={item.title} variants={fadeUp} className="rounded-2xl border border-slate-200 bg-white/86 px-4 py-4 shadow-sm shadow-slate-100/60">
                      <p className={`text-amber-700 mb-2 ${UI_CAPTION_LABEL_CLASS}`}>{item.title}</p>
                      <p className={UI_BODY_SM_CLASS}>{item.detail}</p>
                    </motion.div>
                  ))}
                </div>
              </div>
            </motion.div>

            <motion.div variants={fadeLeft} {...getSectionMotionProps(5)} className="order-1 lg:order-2 h-full">
              <motion.div variants={fadeUp} className="h-full min-h-[39rem] flex flex-col justify-between gap-7">
                <div>
                  <Badge variant="warning" className={`self-start mb-4 bg-amber-100/85 text-amber-700 border-amber-200 ${SECTION_BADGE_CLASS}`}>企业实战任务</Badge>
                  <h2 className={SECTION_TITLE_CLASS}>把你的准备结果带进<br /><span className="text-amber-600">真实任务场景</span></h2>
                  <p className={SECTION_BODY_LIGHT_CLASS}>
                    如果你想把准备结果带进更接近真实工作的场景，可以从这里继续。你不只是在介绍自己会什么，也能让别人看到你怎么做、做成什么样。
                  </p>
                </div>

                <motion.div variants={fadeUp} className="rounded-2xl border border-amber-100/80 bg-white p-5 shadow-sm shadow-amber-100/35 max-w-xl">
                  <p className="mb-3 text-[1rem] md:text-[1.12rem] font-bold tracking-[0.06em] text-amber-700">到了这一屏，你会看到什么</p>
                  <div className="space-y-3">
                    {[
                      "你可以按关键词、任务状态和企业名称筛选，再决定先看哪一个机会。",
                      "任务详情会清楚展示奖励、截止时间、提交说明和外部链接要求。",
                      "继续接触或未入选结果会回到任务链路与通知中心，不会只停在提交那一刻。",
                    ].map((item) => (
                      <div key={item} className={`flex items-start gap-2 ${UI_BODY_SM_CLASS}`}>
                        <CheckCircle2 className="w-4 h-4 text-amber-500 mt-0.5 shrink-0" />
                        <span>{item}</span>
                      </div>
                    ))}
                  </div>
                </motion.div>

                <motion.div variants={fadeUp} className="rounded-3xl border border-amber-100/80 bg-white px-5 py-5 max-w-xl shadow-sm shadow-amber-100/35">
                  <p className="mb-3 text-[1rem] md:text-[1.12rem] font-bold tracking-[0.06em] text-amber-700">为什么你可以在这一步试试真实任务</p>
                  <div className="grid grid-cols-1 gap-3">
                    {ENTERPRISE_FIT_POINTS.map((item) => (
                      <motion.div key={item} variants={fadeUp} className={`rounded-2xl border border-white/80 bg-white/80 px-4 py-3 ${UI_BODY_SM_CLASS}`}>
                        {item}
                      </motion.div>
                    ))}
                  </div>
                </motion.div>
              </motion.div>
            </motion.div>
          </div>
        </section>

        <section className="h-[100dvh] w-full relative flex items-center justify-center shrink-0">
          <div className="max-w-7xl mx-auto px-6 w-full grid grid-cols-1 lg:grid-cols-[1.05fr_0.95fr] gap-10 lg:gap-14 items-center relative z-10 mt-16">
            <motion.div variants={fadeRight} {...getSectionMotionProps(6)} className="h-full">
              <motion.div variants={fadeUp} className="h-full min-h-[39rem] flex flex-col justify-between">
                <div>
                  <Badge variant="rose" className={`mb-4 bg-rose-100/85 text-rose-700 border-rose-200 ${SECTION_BADGE_CLASS}`}>平台保障</Badge>
                  <h2 className={SECTION_TITLE_CLASS}>让你的每一次准备<br /><span className="text-rose-600">都有人接住</span></h2>
                  <p className={SECTION_BODY_LIGHT_CLASS}>
                    你在这里的练习、咨询、订单和反馈，不会断在某一步。通知、AI 复盘、售后和成长工作台会一起把这条链路接住。
                  </p>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  {PLATFORM_SUPPORTS.map((item) => {
                    const Icon = item.icon;
                    return (
                      <motion.div key={item.title} variants={fadeUp} className="rounded-3xl border border-slate-200 bg-white/86 p-5 shadow-lg shadow-slate-200/40 h-full">
                        <div className={`w-12 h-12 rounded-2xl border flex items-center justify-center ${item.tone}`}>
                          <Icon className="w-5 h-5" />
                        </div>
                        <h3 className="text-[1.1rem] font-bold text-slate-900 mt-4">{item.title}</h3>
                        <p className={`mt-3 ${UI_BODY_SM_CLASS}`}>{item.detail}</p>
                      </motion.div>
                    );
                  })}
                </div>
              </motion.div>
            </motion.div>

            <motion.div variants={fadeLeft} {...getSectionMotionProps(6)} className="h-full flex">
              <div className="rounded-3xl border border-slate-200 bg-white/92 p-7 shadow-xl shadow-slate-200/50 h-full min-h-[39rem] flex flex-col">
                <div className="flex items-center justify-between mb-6">
                  <div>
                    <p className={`tracking-[0.16em] text-rose-400 mb-2 ${UI_CAPTION_LABEL_CLASS}`}>FAQ</p>
                    <h3 className="text-[1.5rem] font-bold text-slate-900">你最常关心的 4 个问题</h3>
                  </div>
                  <Badge variant="default" className="bg-rose-50 text-rose-700 border-rose-100 text-[11px]">常见问题</Badge>
                </div>

                <div className="space-y-4 flex-1">
                  {COMMON_QUESTIONS.map((item, index) => (
                    <motion.div key={item.question} variants={fadeUp} className="rounded-2xl border border-rose-100/80 bg-rose-50/45 px-4 py-4">
                      <div className="flex items-start gap-3">
                        <span className={`inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-white border border-rose-100 font-bold text-rose-500 ${CARD_META_CLASS}`}>
                          0{index + 1}
                        </span>
                        <div>
                          <p className="text-[15px] font-semibold text-slate-900">{item.question}</p>
                          <p className={`mt-2 ${UI_BODY_SM_CLASS}`}>{item.answer}</p>
                        </div>
                      </div>
                    </motion.div>
                  ))}
                </div>

                <div className="mt-6 rounded-2xl border border-rose-100 bg-rose-50/70 px-4 py-4">
                  <p className="text-[14px] font-semibold text-slate-900">你可以放心的是：</p>
                  <p className={`mt-2 ${UI_BODY_SM_CLASS}`}>你做过的练习、咨询、任务和提醒都会留在各自的链路里，方便下一次继续往下走。</p>
                </div>
              </div>
            </motion.div>
          </div>
        </section>

        <section className="h-[100dvh] w-full relative flex items-center justify-center shrink-0">
          <div className="max-w-7xl mx-auto px-6 w-full flex-1 flex flex-col justify-center relative z-10 mt-16">
            <motion.div {...getSectionMotionProps(7)} variants={staggerContainer} className="text-center max-w-3xl mx-auto mb-12">
              <motion.div variants={fadeUp}><Badge variant="default" className={`mb-4 bg-cyan-100/85 text-cyan-700 border-cyan-200 ${SECTION_BADGE_CLASS}`}>服务闭环</Badge></motion.div>
              <motion.h2 variants={fadeUp} className={SECTION_TITLE_CLASS}>从第一步开始，慢慢走成<br /><span className="text-cyan-600">你的职业成长闭环</span></motion.h2>
              <motion.p variants={fadeUp} className="text-slate-600 text-[1.02rem] md:text-[1.1rem] lg:text-[1.16rem] leading-[1.85]">
                你不用一开始就准备得很完整，也不用一次把所有功能都看完。先从现在最需要的一步开始，后面的练习、记录、咨询和真实机会会一点点接上。
              </motion.p>
            </motion.div>

            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-5">
              {JOURNEY_STEPS.map((step, index) => {
                const Icon = step.icon;
                return (
                  <motion.div key={step.id} variants={fadeUp} {...getSectionMotionProps(7)} transition={{ delay: index * 0.08 }} className="rounded-3xl bg-white/92 border border-slate-200 p-6 shadow-lg shadow-slate-200/50">
                    <div className="flex items-center justify-between mb-5">
                      <span className="text-[14px] md:text-[15px] font-bold tracking-[0.18em] text-slate-400">{step.step}</span>
                      <div className="w-11 h-11 rounded-2xl bg-cyan-50 text-cyan-600 flex items-center justify-center">
                        <Icon className="w-5 h-5" />
                      </div>
                    </div>
                    <h3 className="text-[1.32rem] font-bold text-slate-900 mb-3">{step.title}</h3>
                    <p className="text-[15px] text-slate-600 leading-7 mb-4">{step.description}</p>
                    <div className={`rounded-2xl bg-slate-50 border border-slate-100 px-4 py-3 ${UI_BODY_SM_MUTED_CLASS}`}>{step.detail}</div>
                  </motion.div>
                );
              })}
            </div>

            <motion.div variants={fadeUp} {...getSectionMotionProps(7)} className="mt-6 rounded-3xl border border-slate-200 bg-white/92 p-6 shadow-lg shadow-slate-200/50">
              <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4 mb-4">
                <div>
                  <p className="text-[15px] font-semibold text-slate-900">你可能真的会走过的 5 步</p>
                  <p className="text-[14px] text-slate-600 mt-2 leading-7">你不用一开始就准备得很完整，可以从探索、准备、复盘到连接机会慢慢推进。</p>
                </div>
                <Badge variant="default" className="bg-cyan-50 text-cyan-700 border-cyan-100 text-[11px] self-start lg:self-auto">职业规划 + 就业指导</Badge>
              </div>
              <div className="relative pt-2">
                <div className="grid grid-cols-1 md:grid-cols-5 gap-6 md:gap-4">
                  {STUDENT_WEEK_PATH.map((item, index) => (
                    <motion.div key={item.day} variants={fadeUp} className="relative min-w-0">
                      <div className="flex items-center justify-between gap-3">
                        <span className={`inline-flex items-center rounded-full border px-3 py-1 font-semibold ${UI_CAPTION_CLASS} ${item.dayClass}`}>
                          {item.day}
                        </span>
                        <span className={`text-[11px] font-semibold tracking-[0.08em] ${item.metaClass}`}>阶段 0{index + 1}</span>
                      </div>

                      <div className="relative mt-5 h-10">
                        {index > 0 ? (
                          <span className={`hidden md:block absolute left-0 right-1/2 top-1/2 h-[3px] -translate-y-1/2 rounded-full ${item.trackClass}`} />
                        ) : null}
                        {index < STUDENT_WEEK_PATH.length - 1 ? (
                          <span className={`hidden md:block absolute left-1/2 right-0 top-1/2 h-[3px] -translate-y-1/2 rounded-full ${item.trackClass}`} />
                        ) : null}
                        <span className={`absolute left-1/2 top-1/2 z-10 inline-flex h-5 w-5 -translate-x-1/2 -translate-y-1/2 rounded-full border-[4px] border-white shadow-[0_0_0_1px_rgba(148,163,184,0.12)] ${item.accentClass}`} />
                        <span className={`absolute right-0 top-1/2 z-10 inline-flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full border bg-white shadow-sm ${item.nodeClass}`}>
                          {index < STUDENT_WEEK_PATH.length - 1 ? <ArrowRight className="w-3.5 h-3.5" /> : <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" />}
                        </span>
                        <span className={`absolute left-2 right-2 top-1/2 h-[2px] -translate-y-1/2 rounded-full ${item.trackClass} md:hidden`} />
                      </div>

                      <div className="mt-5 min-w-0">
                        <p className={`leading-7 ${UI_BODY_SM_CLASS}`}>{item.detail}</p>
                      </div>
                    </motion.div>
                  ))}
                </div>
              </div>
            </motion.div>
          </div>
        </section>

        <section className="h-[100dvh] w-full relative flex flex-col justify-between shrink-0">
          <div className="max-w-7xl mx-auto px-6 w-full flex-1 flex flex-col justify-center relative z-10 mt-8 py-2 md:mt-10 md:py-0">
            <motion.div {...getSectionMotionProps(8)} variants={staggerContainer} className="text-center max-w-5xl mx-auto mb-6 md:mb-7">
              <motion.div variants={fadeUp}>
                <Badge variant="default" className={`mb-4 bg-slate-100/90 text-slate-700 border-slate-200 px-5 py-2 text-[14px] md:text-[15px]`}>立即开始</Badge>
              </motion.div>
              <motion.h2 variants={fadeUp} className="text-[2.65rem] md:text-[3.45rem] lg:text-[4.05rem] font-bold tracking-tight leading-[1.06] mb-5">
                从你的当前阶段开始<br /><span className="text-slate-700">把准备继续往前推</span>
              </motion.h2>
              <motion.p variants={fadeUp} className="mx-auto max-w-3xl text-[1.05rem] md:text-[1.16rem] leading-8 md:leading-9 text-slate-500">
                先把眼前最需要的一步走起来，后面的准备会慢慢接上。
              </motion.p>
            </motion.div>

            <div className="mx-auto w-full max-w-[52rem]">
              <div className="flex flex-col gap-5">
                <div className="px-4 py-4 md:px-6 md:py-5">
                  <div className="flex justify-center">
                    <Button
                      variant="gradient"
                      className="h-[5.15rem] md:h-[5.85rem] w-full max-w-[31rem] text-[1.56rem] md:text-[1.92rem] font-bold shadow-[0_26px_65px_rgba(6,182,212,0.28)] hover:shadow-[0_32px_82px_rgba(6,182,212,0.36)]"
                      size="lg"
                      onClick={goToAuth}
                    >
                      {STUDENT_START_CARD.primaryLabel}
                    </Button>
                  </div>
                </div>

                <div className="flex flex-wrap items-center justify-center gap-3.5">
                  {PARTNER_ENTRY_CARDS.map((card) => {
                    const Icon = card.action === "mentor" ? MessageSquare : Briefcase;

                    return (
                      <motion.button
                        key={card.title}
                        variants={fadeUp}
                        type="button"
                        onClick={() => handleFinalAction(card.action)}
                        className="inline-flex items-center gap-2.5 rounded-full border border-slate-200 bg-white/92 px-5 py-3 text-[13px] md:text-[14px] font-bold text-slate-700 shadow-md shadow-slate-200/20 transition-colors duration-200 hover:border-slate-300 hover:bg-white"
                        style={BUTTON_TEXT_STYLE}
                      >
                        <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-slate-100 text-slate-600 shrink-0">
                          <Icon className="w-4 h-4" />
                        </span>
                        <span>{card.title}</span>
                      </motion.button>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>

          <footer className="w-full border-t border-slate-200/70 bg-white/86 py-4 md:py-6 relative z-10 shrink-0">
            <div className="max-w-7xl mx-auto px-6">
              <div className="flex flex-col lg:flex-row lg:items-end lg:justify-between gap-4 md:gap-6 pb-4 md:pb-6 border-b border-slate-200/80">
                <div>
                  <div className="flex items-center gap-2 mb-3">
                    <Zap className="w-4 h-4 text-indigo-500" />
                    <span className="font-bold leading-tight text-slate-800 text-[0.98rem] md:text-[1.02rem]">大学生就业规划指导平台</span>
                  </div>
                  <p className="max-w-2xl text-[13px] md:text-[14px] text-slate-600 leading-6 md:leading-7">
                    聚焦大学生职业规划、求职训练与实践连接，提供从简历优化、模拟面试到导师咨询、企业实战的一站式成长服务。
                  </p>
                </div>
                <div className={`inline-flex self-start lg:self-auto items-center rounded-full border border-slate-200 bg-white/80 px-4 py-2 text-slate-500 ${UI_CAPTION_CLASS}`}>
                  职业规划 · 求职训练 · 实践连接
                </div>
              </div>

              <div className="grid grid-cols-2 xl:grid-cols-4 gap-4 md:gap-5 py-4 md:py-6">
                {FORMAL_FOOTER_COLUMNS.map((column) => (
                  <div key={column.title}>
                    <p className={`tracking-[0.16em] text-slate-400 mb-3 ${UI_CAPTION_LABEL_CLASS}`}>{column.title}</p>
                    <div className="space-y-2">
                      {column.items.map((item) => (
                        <p key={item} className={UI_BODY_SM_CLASS}>
                          {item}
                        </p>
                      ))}
                    </div>
                  </div>
                ))}
              </div>

              <div className={`pt-4 md:pt-5 border-t border-slate-200/80 flex flex-col md:flex-row md:items-center md:justify-between gap-2 md:gap-3 text-slate-500 ${UI_CAPTION_CLASS}`}>
                <p>© 2026 大学生就业规划指导平台。All rights reserved.</p>
                <div className="flex flex-wrap items-center gap-3">
                  <span>服务学生 / 导师 / 企业</span>
                  <span className="w-1 h-1 rounded-full bg-slate-300"></span>
                  <span>聚焦规划、训练与实践</span>
                  <span className="w-1 h-1 rounded-full bg-slate-300"></span>
                  <span>具体服务以平台页面展示为准</span>
                </div>
              </div>
            </div>
          </footer>
        </section>
      </motion.div>
    </div>
  );
}
