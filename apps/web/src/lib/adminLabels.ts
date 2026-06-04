import { Bot, DollarSign, Mic, Server, Sparkles, Users, Volume2, type LucideIcon } from "lucide-react";

export const roleLabelMap: Record<string, string> = {
  STUDENT: "学生",
  MENTOR: "导师",
  ENTERPRISE: "企业",
  ADMIN: "管理员",
};

export const roleColorMap: Record<string, string> = {
  STUDENT: "cyan",
  MENTOR: "purple",
  ENTERPRISE: "blue",
  ADMIN: "gold",
};

export const accountStatusLabelMap: Record<string, string> = {
  ACTIVE: "正常",
  PENDING: "待激活",
  SUSPENDED: "已封禁",
};

export const accountStatusBadgeMap: Record<string, "success" | "warning" | "error"> = {
  ACTIVE: "success",
  PENDING: "warning",
  SUSPENDED: "error",
};

export const tierLabelMap: Record<string, string> = {
  FREE: "免费版",
  PREMIUM: "高级版",
};

export const approvalStatusLabelMap: Record<string, string> = {
  PENDING: "待认证",
  APPROVED: "已认证",
  REJECTED: "已驳回",
};

export const bountyTaskStatusLabelMap: Record<string, string> = {
  OPEN: "招募中",
  CLOSED: "已关闭",
};

export const riskLevelLabelMap: Record<string, string> = {
  LOW: "低风险",
  MEDIUM: "中风险",
  HIGH: "高风险",
  CRITICAL: "极高风险",
};

export const targetTypeLabelMap: Record<string, string> = {
  POST: "帖子",
  COMMENT: "评论",
  USER: "用户",
  ORDER: "咨询订单",
  AFTER_SALES_REQUEST: "售后申请",
  AI_REQUEST: "AI 请求",
  AI_CONTENT: "AI 内容",
};

export const sourceTypeLabelMap: Record<string, string> = {
  COMMUNITY_POST: "社区帖子",
  COMMUNITY_COMMENT: "社区评论",
  AI_INPUT: "AI 输入",
  AI_OUTPUT: "AI 输出",
  ALL: "全局",
};

export const moderationActionLabelMap: Record<string, string> = {
  PASS: "放行",
  MASK: "脱敏",
  BLOCK: "拦截",
  REVIEW: "转人工审核",
  WARN: "告警",
  TAKE_DOWN: "下架内容",
  RESTORE: "恢复内容",
  NO_ACTION: "不做动作",
  APPROVE: "通过",
  REJECT: "驳回",
  ACCEPTED: "已采纳",
  REJECTED: "已驳回",
  CLOSED: "已关闭",
};

export const moderationReasonLabelMap: Record<string, string> = {
  ABUSE: "辱骂攻击",
  SPAM: "垃圾广告",
  OFF_TOPIC: "内容跑题",
  RISK_LINK: "风险链接",
  PORNOGRAPHY: "色情低俗",
  VIOLENCE: "暴力血腥",
  FRAUD: "欺诈诱导",
  ILLEGAL: "违法违规",
  HARASSMENT: "骚扰冒犯",
  COPYRIGHT: "侵权内容",
  MISLEADING: "误导信息",
  SENSITIVE_TERM: "命中敏感词",
  SENSITIVE_TERM_MATCHED: "命中敏感词",
  POLICY_HIGH_RISK: "命中高风险策略",
  POLICY_REVIEW_REQUIRED: "命中复核策略",
  POLICY_MASK_REQUIRED: "命中脱敏策略",
  POLICY_DISABLED: "策略已关闭",
  REPORT_THRESHOLD_AUTO_HIDE: "达到举报阈值自动隐藏",
  REPORT_DECISION_TAKE_DOWN: "举报处置下架",
  REPORT_DECISION_RESTORE: "举报处置恢复",
  RULE_CLEAR: "规则通过",
  MANUAL_APPROVE: "人工审核通过",
  MANUAL_REJECT: "人工审核驳回",
};

export const sensitiveTermTypeLabelMap: Record<string, string> = {
  POLITICS: "政治",
  PORNOGRAPHY: "色情",
  TERROR: "恐怖",
  VIOLENCE: "暴力",
  FRAUD: "欺诈",
  ABUSE: "辱骂",
  ADVERTISEMENT: "广告",
  ILLEGAL: "违规",
  OTHER: "其他",
};

export const orderStatusLabelMap: Record<string, string> = {
  CREATED: "已创建",
  PAYING: "待支付",
  PAID: "已支付",
  ANSWERED: "已答复",
  CLOSED: "已关闭",
  REFUNDED: "已退款",
  CANCELED: "已取消",
  FAILED: "已失败",
};

export const afterSalesStatusLabelMap: Record<string, string> = {
  PENDING: "待审核",
  APPROVED: "已通过",
  REJECTED: "已拒绝",
};

export const afterSalesRequestTypeLabelMap: Record<string, string> = {
  REFUND: "退款",
};

export const reconciliationStatusLabelMap: Record<string, string> = {
  MATCHED: "已匹配",
  REVIEW_REQUIRED: "异常待处理",
  REVIEWED_PENDING: "人工处理中",
  MANUALLY_RESOLVED: "人工已解决",
  PENDING: "处理中",
};

export const paymentModeLabelMap: Record<string, string> = {
  MOCK: "模拟支付",
  SANDBOX: "支付宝沙箱",
};

export const paymentChannelLabelMap: Record<string, string> = {
  ALIPAY: "支付宝",
  MANUAL: "人工补记",
};

export const paymentStatusLabelMap: Record<string, string> = {
  INIT: "待支付",
  SUCCESS: "支付成功",
  CLOSED: "已关闭",
  REFUND_SUCCESS: "退款成功",
  FAILED: "失败",
  ERROR: "失败",
  TIMEOUT: "超时",
  PENDING: "处理中",
};

export const reconciliationIssueTagLabelMap: Record<string, string> = {
  PAYING_WITHOUT_RECORD: "支付中无流水",
  SUCCESS_NOT_APPLIED: "支付成功未落账",
  UNPAID_STUCK: "未支付订单长时间卡住",
  PAID_WITHOUT_SUCCESS_RECORD: "订单已支付但缺少成功流水",
  PAYMENT_AMOUNT_MISMATCH: "支付金额不一致",
  REFUND_EXTERNAL_PENDING: "外部退款待确认",
};

export const reconciliationActionLabelMap: Record<string, string> = {
  MARK_PAID: "标记为已支付",
  CANCEL_UNPAID: "取消未支付订单",
  CONFIRM_EXTERNAL_REFUND: "确认外部退款完成",
  MARK_REVIEWED: "标记为已复核",
};

export const gatewayPromptStatusLabelMap: Record<string, string> = {
  DRAFT: "草稿",
  ACTIVE: "生效中",
  INACTIVE: "已停用",
};

export const gatewayLogStatusLabelMap: Record<string, string> = {
  SUCCESS: "成功",
  FAILED: "失败",
  ERROR: "失败",
  TIMEOUT: "超时",
  RUNNING: "执行中",
  UNKNOWN: "未知",
};

export const gatewayRuntimeStatusLabelMap: Record<string, string> = {
  HEALTHY: "健康",
  DEGRADED: "波动",
  DOWN: "异常",
  IDLE: "空闲",
  DISABLED: "停用",
};

export const gatewayProviderTypeLabelMap: Record<string, string> = {
  OPENAI_COMPATIBLE: "OpenAI 兼容接口",
  GEMINI_NATIVE: "Gemini 原生接口",
};

export const gatewayTaskTypeLabelMap: Record<string, string> = {
  RESUME: "简历优化",
  INTERVIEW_TEXT: "文字面试",
  INTERVIEW_SUMMARY: "面试总结",
  COMMUNITY_REPLY: "社区回复",
  PORTRAIT_SUMMARY: "画像总结",
  ICEBREAK: "破冰话术",
  STT: "语音转文字",
  TTS: "文字转语音",
};

export const gatewayExecutionModeLabelMap: Record<string, string> = {
  SYNC_BLOCKING: "同步阻塞",
  STREAM_SSE: "流式输出",
  ASYNC_JOB: "异步任务",
  REALTIME_SESSION: "实时会话",
};

export const gatewayTemplateFormatLabelMap: Record<string, string> = {
  TEXT: "纯文本",
  MESSAGE_BUNDLE: "消息 Bundle",
};

export const notificationChannelLabelMap: Record<string, string> = {
  WEBSOCKET: "站内实时",
  EMAIL: "邮件",
};

export const notificationDispatchStatusLabelMap: Record<string, string> = {
  PENDING: "待发送",
  RUNNING: "发送中",
  RETRY_WAIT: "等待重试",
  SENT: "已发送",
  ACKED: "已确认",
  SKIPPED: "已跳过",
  FAILED: "失败",
  DEAD: "已进入死信",
};

export const notificationPriorityLabelMap: Record<string, string> = {
  LOW: "低",
  NORMAL: "普通",
  HIGH: "高",
  URGENT: "紧急",
};

export const timePeriodLabelMap: Record<string, string> = {
  today: "今日",
  week: "本周",
  month: "本月",
};

export const auditActionLabelMap: Record<string, string> = {
  REPORT_DECISION: "举报处置",
  CONTENT_REVIEW_DECISION: "待审内容人工审核",
  AUTO_HIDE: "自动隐藏",
  MOCK_PAYMENT_SUCCESS: "模拟支付成功",
  ADMIN_PAYMENT_MARK_PAID: "人工标记支付成功",
  ADMIN_PAYMENT_CANCEL_UNPAID: "人工取消未支付订单",
  ADMIN_PAYMENT_CONFIRM_EXTERNAL_REFUND: "确认外部退款",
  ADMIN_PAYMENT_MARK_REVIEWED: "支付异常标记已复核",
  ADMIN_CONSULT_REFUND: "管理员退款处理",
  CONSULT_AFTER_SALES_CREATED: "创建售后申请",
  CONSULT_AFTER_SALES_REVIEWED: "审核售后申请",
  CONSULT_AFTER_SALES_AUTO_REFUND_DEFERRED: "自动退款暂缓处理",
  ADMIN_SANDBOX_TRADE_QUERY: "查询沙箱交易",
  ADMIN_SANDBOX_TRADE_CLOSE: "关闭沙箱交易",
  ADMIN_SANDBOX_REFUND_QUERY: "查询沙箱退款",
  ADMIN_SANDBOX_REFUND_EXECUTE: "执行沙箱退款",
  STUDENT_SANDBOX_TRADE_QUERY: "学生查询沙箱交易",
  STUDENT_SANDBOX_TRADE_CLOSE: "学生关闭沙箱交易",
};

const backendCodeFragmentLabelMap: Record<string, string> = {
  ADMIN: "管理员",
  STUDENT: "学生",
  CONSULT: "咨询",
  AFTER_SALES: "售后",
  CONTENT: "内容",
  REPORT: "举报",
  REVIEW: "审核",
  PAYMENT: "支付",
  ORDER: "订单",
  AUTO: "自动",
  REFUND: "退款",
  DEFERRED: "暂缓",
  REQUEST: "申请",
  CREATED: "创建",
  REVIEWED: "审核",
  SANDBOX: "沙箱",
  TRADE: "交易",
  QUERY: "查询",
  CLOSE: "关闭",
  EXECUTE: "执行",
  CONFIRM: "确认",
  EXTERNAL: "外部",
  HIDE: "隐藏",
  MARK: "标记",
  PAID: "已支付",
  CANCEL: "取消",
  UNPAID: "未支付",
  REQUIRED: "必需",
  DECISION: "处置",
  MANUAL: "人工",
  SUCCESS: "成功",
};

function splitBackendCodeFragments(value: string) {
  const normalized = value.trim().toUpperCase();
  if (!normalized.includes("_")) {
    return [normalized];
  }
  const fragments = Object.keys(backendCodeFragmentLabelMap).sort((left, right) => right.length - left.length);
  const tokens: string[] = [];
  let cursor = normalized;
  while (cursor) {
    let matched = false;
    for (const fragment of fragments) {
      if (cursor.startsWith(fragment)) {
        tokens.push(fragment);
        cursor = cursor.slice(fragment.length);
        if (cursor.startsWith("_")) {
          cursor = cursor.slice(1);
        }
        matched = true;
        break;
      }
    }
    if (matched) {
      continue;
    }
    const separatorIndex = cursor.indexOf("_");
    if (separatorIndex === -1) {
      tokens.push(cursor);
      break;
    }
    tokens.push(cursor.slice(0, separatorIndex));
    cursor = cursor.slice(separatorIndex + 1);
  }
  return tokens.filter(Boolean);
}

export function getReadableCodeLabel(
  value: string | null | undefined,
  labelMap: Record<string, string>,
  fallback = "—",
) {
  if (!value) {
    return fallback;
  }
  const directLabel = labelMap[value];
  if (directLabel) {
    return directLabel;
  }
  const translated = splitBackendCodeFragments(value)
    .map((fragment) => backendCodeFragmentLabelMap[fragment] ?? fragment)
    .join("");
  return translated || value;
}

export const growthReasonCodeLabelMap: Record<string, string> = {
  TEST_TOPUP: "测试补发",
};

export type FeatureFlagMeta = {
  group: string;
  risk: "LOW" | "MEDIUM" | "HIGH";
  accentClassName: string;
  borderClassName: string;
  icon: LucideIcon;
  helper: string;
};

export const featureFlagMetaMap: Record<string, FeatureFlagMeta> = {
  "payment.enabled": {
    group: "PAYMENT",
    risk: "HIGH",
    accentClassName: "bg-rose-100 text-rose-600",
    borderClassName: "border-rose-100",
    icon: DollarSign,
    helper: "控制用户是否还能继续下单并完成支付。",
  },
  "payment.mode": {
    group: "PAYMENT",
    risk: "HIGH",
    accentClassName: "bg-indigo-100 text-indigo-600",
    borderClassName: "border-indigo-100",
    icon: DollarSign,
    helper: "切换新订单默认使用的支付环境。",
  },
  "voice.interview.enabled": {
    group: "AI",
    risk: "MEDIUM",
    accentClassName: "bg-emerald-100 text-emerald-600",
    borderClassName: "border-emerald-100",
    icon: Bot,
    helper: "控制用户是否还能选择语音作答开始面试。",
  },
  "voice.stt.enabled": {
    group: "AI",
    risk: "MEDIUM",
    accentClassName: "bg-sky-100 text-sky-600",
    borderClassName: "border-sky-100",
    icon: Mic,
    helper: "控制语音回答是否会自动整理成文字。",
  },
  "voice.tts.enabled": {
    group: "AI",
    risk: "LOW",
    accentClassName: "bg-violet-100 text-violet-600",
    borderClassName: "border-violet-100",
    icon: Volume2,
    helper: "控制题目播报和朗读提醒是否继续提供。",
  },
  "community.ai-draft.enabled": {
    group: "COMMUNITY",
    risk: "LOW",
    accentClassName: "bg-amber-100 text-amber-600",
    borderClassName: "border-amber-100",
    icon: Sparkles,
    helper: "控制帖子详情中的智能回复参考是否继续提供。",
  },
  "community.ai-first-reply.enabled": {
    group: "COMMUNITY",
    risk: "MEDIUM",
    accentClassName: "bg-orange-100 text-orange-600",
    borderClassName: "border-orange-100",
    icon: Sparkles,
    helper: "控制新帖发布后是否自动补充参考回复。",
  },
  "student.portrait.async-refresh.enabled": {
    group: "AI",
    risk: "MEDIUM",
    accentClassName: "bg-cyan-100 text-cyan-700",
    borderClassName: "border-cyan-100",
    icon: Sparkles,
    helper: "仅控制凌晨定时批刷新；不影响资料编辑、技能进度和 AI 完成后的按需更新。",
  },
  "student.portrait.summary.mode": {
    group: "AI",
    risk: "MEDIUM",
    accentClassName: "bg-fuchsia-100 text-fuchsia-700",
    borderClassName: "border-fuchsia-100",
    icon: Bot,
    helper: "控制画像总结的生成方式；LLM 润色当前只在高价值触发链路中低频启用。",
  },
};

export const featureFlagGroupIconMap: Record<string, LucideIcon> = {
  PAYMENT: DollarSign,
  AI: Bot,
  COMMUNITY: Sparkles,
  USER: Users,
  SYSTEM: Server,
};

export function getLabel(value: string | null | undefined, labelMap: Record<string, string>, fallback = "—") {
  if (!value) {
    return fallback;
  }
  return labelMap[value] ?? value;
}
