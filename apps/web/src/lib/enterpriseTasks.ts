import { buildQuery } from "./apiClient";
import { formatDateInputValue, toTimestamp } from "./formatters";

export type EnterpriseTaskCreateFormState = {
  title: string;
  background: string;
  problem: string;
  deliveryWhat: string;
  deliveryFormats: string[];
  valueMost: string;
  reward: string;
  deadline: string;
  directionTags: string[];
  audienceTags: string[];
  referenceLink: string;
  notes: string;
};

export type ParsedEnterpriseTaskDescription = {
  summary: string;
  background: string;
  problem: string;
  deliveryWhat: string;
  deliveryFormats: string[];
  valueMost: string;
  directionTags: string[];
  audienceTags: string[];
  referenceLink: string;
  notes: string;
};

export type PagedRecordsResponse<T> = {
  records: T[];
  total: number;
  page: number;
  size: number;
};

export const ENTERPRISE_PAGED_BATCH_SIZE = 50;

// 企业任务表单展示为结构化字段，提交时仍压回后端 description 文本。
export const DELIVERY_FORMAT_OPTIONS = [
  { id: "doc", label: "说明文档" },
  { id: "design", label: "设计稿链接" },
  { id: "repo", label: "代码仓库链接" },
  { id: "demo", label: "演示视频/链接" },
] as const;

export const DIRECTION_TAGS = [
  "产品经理",
  "UI/UX设计",
  "前端开发",
  "后端架构",
  "数据分析",
  "AI算法",
  "市场营销",
] as const;

export const AUDIENCE_TAGS = [
  "应届生",
  "大三/研二",
  "有实习经验",
  "相关专业优先",
] as const;

const EMPTY_PARSED_DESCRIPTION: ParsedEnterpriseTaskDescription = {
  summary: "",
  background: "",
  problem: "",
  deliveryWhat: "",
  deliveryFormats: [],
  valueMost: "",
  directionTags: [],
  audienceTags: [],
  referenceLink: "",
  notes: "",
};

function normalizePositiveIdentifier(value: number | string | null | undefined) {
  if (typeof value === "number" && Number.isFinite(value) && value > 0) {
    return String(Math.trunc(value));
  }
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    if (Number.isFinite(parsed) && parsed > 0) {
      return String(Math.trunc(parsed));
    }
  }
  return null;
}

export function createEmptyEnterpriseTaskForm(): EnterpriseTaskCreateFormState {
  return {
    title: "",
    background: "",
    problem: "",
    deliveryWhat: "",
    deliveryFormats: [],
    valueMost: "",
    reward: "",
    deadline: "",
    directionTags: [],
    audienceTags: [],
    referenceLink: "",
    notes: "",
  };
}

export function buildEnterpriseTaskReviewHref(
  taskId: number | string | null | undefined,
  submissionId?: number | string | null,
) {
  const normalizedTaskId = normalizePositiveIdentifier(taskId);
  if (!normalizedTaskId) {
    return "/enterprise/tasks";
  }

  const normalizedSubmissionId = normalizePositiveIdentifier(submissionId);
  // 企业通知带 submissionId 时直接定位到审核工作区的目标提交。
  return `/enterprise/tasks/${encodeURIComponent(normalizedTaskId)}${buildQuery({
    submissionId: normalizedSubmissionId,
  })}`;
}

export function buildExternalResourceHref(value: string | null | undefined) {
  const normalizedValue = value?.trim() ?? "";
  if (!normalizedValue) {
    return null;
  }
  if (/^https?:\/\//i.test(normalizedValue)) {
    return normalizedValue;
  }
  // 学生/企业输入常省略协议，预览链接统一补 https 后再渲染。
  if (normalizedValue.startsWith("www.")) {
    return `https://${normalizedValue}`;
  }
  if (/^[^\s]+\.[^\s]+/.test(normalizedValue)) {
    return `https://${normalizedValue}`;
  }
  return null;
}

export function createEnterpriseTaskFormFromTask(task: {
  title: string;
  description: string;
  rewardDescription: string;
  deadlineAt: string | null;
}): EnterpriseTaskCreateFormState {
  // 编辑模式从文本协议反解表单，无法识别的旧 description 会落到摘要兜底。
  const parsed = parseEnterpriseTaskDescription(task.description);
  return {
    title: task.title,
    background: parsed.background,
    problem: parsed.problem,
    deliveryWhat: parsed.deliveryWhat,
    deliveryFormats: parsed.deliveryFormats,
    valueMost: parsed.valueMost,
    reward: task.rewardDescription,
    deadline: formatTaskDeadlineToInput(task.deadlineAt),
    directionTags: parsed.directionTags,
    audienceTags: parsed.audienceTags,
    referenceLink: parsed.referenceLink,
    notes: parsed.notes,
  };
}

export function normalizeTaskDateToDeadlineInstant(dateValue: string) {
  if (!dateValue.trim()) {
    return null;
  }
  const [yearRaw, monthRaw, dayRaw] = dateValue.split("-");
  const year = Number(yearRaw);
  const month = Number(monthRaw);
  const day = Number(dayRaw);
  if (!Number.isFinite(year) || !Number.isFinite(month) || !Number.isFinite(day)) {
    return null;
  }
  // 截止日期按本地当天 23:59:59 处理，再交给后端保存 ISO instant。
  const deadline = new Date(year, month - 1, day, 23, 59, 59, 0);
  return deadline.toISOString();
}

export function buildEnterpriseTaskDescription(formData: EnterpriseTaskCreateFormState) {
  // 摘要优先取“问题”，否则退到背景或交付要求，保证学生列表有稳定短文案。
  const summarySource = normalizeText(formData.problem) || normalizeText(formData.background) || normalizeText(formData.deliveryWhat) || "企业发布了一项新的实战任务";

  const sections = [
    `任务摘要：${truncate(summarySource, 96)}`,
    formatSection("任务背景", formData.background),
    formatSection("希望解决的问题", formData.problem || "待企业补充"),
    formatSection("需要提交什么", formData.deliveryWhat),
    formatSection("交付形式", formData.deliveryFormats.join(" / ") || "待企业补充"),
    formatSection("最看重什么", formData.valueMost || "待企业补充"),
    formatSection("适合方向", formData.directionTags.join(" / ") || "待企业补充"),
    formatSection("适合人群", formData.audienceTags.join(" / ") || "待企业补充"),
    formatSection("参考资料", formData.referenceLink || "待企业补充"),
    formatSection("注意事项", formData.notes || "待企业补充"),
  ];

  return sections.join("\n\n");
}

export function parseEnterpriseTaskDescription(description: string | null | undefined): ParsedEnterpriseTaskDescription {
  if (!description?.trim()) {
    return EMPTY_PARSED_DESCRIPTION;
  }

  const normalized = description.replace(/\r\n/g, "\n").trim();
  const summaryMatch = normalized.match(/^任务摘要：([^\n]+)/);
  const parsed: ParsedEnterpriseTaskDescription = {
    ...EMPTY_PARSED_DESCRIPTION,
    summary: summaryMatch?.[1]?.trim() ?? "",
  };

  // 只解析当前约定的【标题】段落，旧文本不抛错，保留 summary 兜底。
  const sectionRegex = /【([^】]+)】\n([\s\S]*?)(?=\n【[^】]+】\n|$)/g;
  let match: RegExpExecArray | null = sectionRegex.exec(normalized);
  while (match) {
    const title = match[1]?.trim();
    const body = match[2]?.trim() ?? "";
    if (title === "任务背景") {
      parsed.background = body;
    } else if (title === "希望解决的问题") {
      parsed.problem = normalizeOptionalPlaceholder(body);
    } else if (title === "需要提交什么") {
      parsed.deliveryWhat = body;
    } else if (title === "交付形式") {
      parsed.deliveryFormats = splitBySlash(body);
    } else if (title === "最看重什么") {
      parsed.valueMost = normalizeOptionalPlaceholder(body);
    } else if (title === "适合方向") {
      parsed.directionTags = splitBySlash(body);
    } else if (title === "适合人群") {
      parsed.audienceTags = splitBySlash(body);
    } else if (title === "参考资料") {
      parsed.referenceLink = normalizeOptionalPlaceholder(body);
    } else if (title === "注意事项") {
      parsed.notes = normalizeOptionalPlaceholder(body);
    }
    match = sectionRegex.exec(normalized);
  }

  if (!parsed.summary) {
    parsed.summary = truncate(parsed.problem || parsed.background || parsed.deliveryWhat || normalized, 96);
  }

  return parsed;
}

export function buildTaskPreviewChips(parsed: ParsedEnterpriseTaskDescription) {
  return [...parsed.directionTags, ...parsed.audienceTags].slice(0, 8);
}

export function getEnterpriseTaskUiStatus(task: {
  status: string;
  acceptedSubmissionId: number | null;
  deadlineAt: string | null;
}) {
  // CLOSED 根据是否已有中选提交区分“完成筛选”和普通结束。
  if (task.status === "CLOSED") {
    return task.acceptedSubmissionId ? "已完成筛选" : "已结束";
  }

  if (!task.deadlineAt) {
    return "进行中";
  }

  const deadlineAt = toTimestamp(task.deadlineAt);
  if (Number.isNaN(deadlineAt)) {
    return "进行中";
  }

  const now = Date.now();
  const diff = deadlineAt - now;
  if (diff > 0 && diff <= 1000 * 60 * 60 * 72) {
    // 72 小时内的开放任务给临期提醒，方便学生和企业都能快速识别。
    return "即将截止";
  }

  return "进行中";
}

export function buildEnterpriseTaskValidationMessages(formData: EnterpriseTaskCreateFormState) {
  const messages: string[] = [];
  if (!normalizeText(formData.title)) {
    messages.push("请填写任务标题");
  }
  if (!normalizeText(formData.background)) {
    messages.push("请填写任务背景");
  }
  if (!normalizeText(formData.deliveryWhat)) {
    messages.push("请填写交付要求");
  }
  if (!normalizeText(formData.reward)) {
    messages.push("请填写奖励与继续接触预期");
  }
  if (!normalizeText(formData.deadline)) {
    messages.push("请设置截止时间");
  }
  return messages;
}

export function summarizeTaskDescription(parsed: ParsedEnterpriseTaskDescription) {
  return parsed.summary || parsed.problem || parsed.background || parsed.deliveryWhat || "当前任务还没有补充更多说明。";
}

export async function fetchAllPagedRecords<T>(
  fetchPage: (params: { page: number; size: number }) => Promise<PagedRecordsResponse<T>>,
  options?: { pageSize?: number; maxPages?: number },
): Promise<PagedRecordsResponse<T>> {
  const pageSize = Math.min(
    ENTERPRISE_PAGED_BATCH_SIZE,
    Math.max(1, options?.pageSize ?? ENTERPRISE_PAGED_BATCH_SIZE),
  );
  const maxPages = Math.max(1, options?.maxPages ?? 100);
  // 先取第一页拿 total，再按 total 补齐剩余页，避免无限翻页。
  const firstPage = await fetchPage({ page: 1, size: pageSize });

  if (firstPage.total <= firstPage.records.length || firstPage.records.length === 0) {
    return firstPage;
  }

  const records = [...firstPage.records];

  for (let page = 2; page <= maxPages && records.length < firstPage.total; page += 1) {
    const nextPage = await fetchPage({ page, size: pageSize });
    if (nextPage.records.length === 0) {
      break;
    }
    records.push(...nextPage.records);
    // 短页说明服务端已经没有更多记录，提前停止。
    if (nextPage.records.length < pageSize) {
      break;
    }
  }

  return {
    records,
    total: firstPage.total,
    page: 1,
    size: pageSize,
  };
}

function formatSection(title: string, value: string) {
  return `【${title}】\n${value.trim()}`;
}

function splitBySlash(value: string) {
  return value
    .split("/")
    .map((item) => item.trim())
    .filter(Boolean)
    .filter((item) => item !== "待企业补充");
}

function normalizeOptionalPlaceholder(value: string) {
  return value === "待企业补充" ? "" : value;
}

function truncate(value: string, limit: number) {
  if (value.length <= limit) {
    return value;
  }
  return `${value.slice(0, Math.max(0, limit - 1))}…`;
}

function formatTaskDeadlineToInput(deadlineAt: string | null) {
  return formatDateInputValue(deadlineAt);
}

function normalizeText(value: string | null | undefined) {
  return value?.trim() ?? "";
}
