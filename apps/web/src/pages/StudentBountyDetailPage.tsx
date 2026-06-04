import {
  AlertCircle,
  ArrowLeft,
  CalendarDays,
  CheckCircle2,
  Check,
  Compass,
  Clock3,
  Eye,
  ExternalLink,
  FileText,
  Info,
  Link as LinkIcon,
  Loader2,
  Lock,
  Plus,
  RefreshCw,
  Send,
  Sparkles,
  Target,
  Trophy,
  Trash2,
  Users,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import EnterpriseIdentityLogo from "../components/avatar/EnterpriseIdentityLogo";
import StudentWorkspaceNav from "../components/student/StudentWorkspaceNav";
import { ApiClientError, apiRequest } from "../lib/apiClient";
import {
  buildExternalResourceHref,
  buildTaskPreviewChips,
  getEnterpriseTaskUiStatus,
  parseEnterpriseTaskDescription,
} from "../lib/enterpriseTasks";
import { formatDateTime } from "../lib/formatters";

type BountyTaskDetailResponse = {
  taskId: number;
  enterpriseUserId: number;
  enterpriseName: string;
  enterpriseLogoUrl: string | null;
  title: string;
  description: string;
  rewardDescription: string;
  status: string;
  submissionCount: number;
  acceptedSubmissionId: number | null;
  mine: boolean;
  deadlineAt: string | null;
  closedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  mySubmission?: {
    submissionId: number;
    status: string;
    contentText: string | null;
    attachmentLinks: string[];
    reviewComment: string | null;
    reviewedAt: string | null;
    createdAt: string | null;
    updatedAt: string | null;
  } | null;
};

type BountySubmissionCreateResponse = {
  submissionId: number;
  taskId: number;
  status: string;
  createdAt: string;
};

type NoticeState = {
  tone: "success" | "error" | "info";
  message: string;
};

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function buildErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiClientError && error.message) {
    return error.traceId ? `${error.message}（traceId: ${error.traceId}）` : error.message;
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}

function getTaskStatusClassName(task: Pick<BountyTaskDetailResponse, "status" | "acceptedSubmissionId" | "deadlineAt">) {
  const uiStatus = getEnterpriseTaskUiStatus(task);
  switch (uiStatus) {
    case "即将截止":
      return "border-amber-200 bg-amber-50 text-amber-700";
    case "已完成筛选":
      return "border-emerald-200 bg-emerald-50 text-emerald-700";
    case "已结束":
      return "border-slate-200 bg-slate-100 text-slate-600";
    case "进行中":
    default:
      return "border-indigo-200 bg-indigo-50 text-indigo-700";
  }
}

function getSubmissionStatusMeta(status: string | null | undefined) {
  switch (status) {
    case "ACCEPTED":
      return {
        label: "已进入继续接触",
        className: "border-emerald-200 bg-emerald-50 text-emerald-700",
        tone: "accepted" as const,
      };
    case "REVIEWING":
      return {
        label: "企业审核中",
        className: "border-blue-200 bg-blue-50 text-blue-700",
        tone: "reviewing" as const,
      };
    case "REJECTED":
      return {
        label: "本次未继续推进",
        className: "border-slate-200 bg-slate-100 text-slate-600",
        tone: "rejected" as const,
      };
    case "SUBMITTED":
    default:
      return {
        label: "已提交待审核",
        className: "border-indigo-200 bg-indigo-50 text-indigo-700",
        tone: "submitted" as const,
      };
  }
}

function normalizeAttachmentLinks(values: string[]) {
  return values.map((item) => item.trim()).filter(Boolean);
}

function splitMultilineContent(value: string) {
  return value
    .split(/\n+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export default function StudentBountyDetailPage() {
  const { role, displayName } = useAuth();
  const { taskId } = useParams();

  const [detail, setDetail] = useState<BountyTaskDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [submitLoading, setSubmitLoading] = useState(false);
  const [submissionText, setSubmissionText] = useState("");
  const [attachmentLinks, setAttachmentLinks] = useState<string[]>([""]);
  const [notice, setNotice] = useState<NoticeState | null>(null);
  const [refreshSeed, setRefreshSeed] = useState(0);

  // 后端 description 仍是文本字段，前端按约定协议还原出结构化阅读区。
  const parsedDescription = useMemo(
    () => parseEnterpriseTaskDescription(detail?.description),
    [detail?.description],
  );
  const previewChips = useMemo(
    () => buildTaskPreviewChips(parsedDescription),
    [parsedDescription],
  );
  const mySubmissionStatusMeta = useMemo(
    () => getSubmissionStatusMeta(detail?.mySubmission?.status),
    [detail?.mySubmission?.status],
  );
  const referenceHref = useMemo(
    () => buildExternalResourceHref(parsedDescription.referenceLink),
    [parsedDescription.referenceLink],
  );
  const valueFocusItems = useMemo(() => {
    const items = splitMultilineContent(parsedDescription.valueMost);
    return items.length ? items : (parsedDescription.valueMost ? [parsedDescription.valueMost] : []);
  }, [parsedDescription.valueMost]);
  const deliveryItems = useMemo(() => {
    const items = splitMultilineContent(parsedDescription.deliveryWhat);
    return items.length ? items : (parsedDescription.deliveryWhat ? [parsedDescription.deliveryWhat] : []);
  }, [parsedDescription.deliveryWhat]);
  const hasStructuredDescription = Boolean(
    parsedDescription.background
    || parsedDescription.problem
    || parsedDescription.deliveryWhat
    || parsedDescription.deliveryFormats.length
    || parsedDescription.valueMost
    || parsedDescription.referenceLink
    || parsedDescription.notes
    || parsedDescription.directionTags.length
    || parsedDescription.audienceTags.length,
  );

  useEffect(() => {
    if (role !== "STUDENT" || !taskId) {
      return;
    }

    let active = true;
    setLoading(true);

    // 详情接口会合成 viewer 态，包括当前学生是否已提交和自己的审核结果。
    void apiRequest<BountyTaskDetailResponse>(`/bounty/tasks/${taskId}`)
      .then((response) => {
        if (!active) {
          return;
        }
        setDetail(response);
        setErrorMessage(null);
      })
      .catch((error) => {
        if (!active) {
          return;
        }
        setDetail(null);
        setErrorMessage(buildErrorMessage(error, "加载任务详情失败"));
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [refreshSeed, role, taskId]);

  useEffect(() => {
    if (!detail?.mySubmission) {
      return;
    }
    // 已提交的成果允许回看，表单区直接回填后端保存的说明和链接。
    setSubmissionText(detail.mySubmission.contentText ?? "");
    setAttachmentLinks(detail.mySubmission.attachmentLinks.length ? detail.mySubmission.attachmentLinks : [""]);
  }, [detail?.mySubmission]);

  const taskStatusLabel = detail ? getEnterpriseTaskUiStatus(detail) : "进行中";
  // 学生侧只允许开放任务的首次提交，后续修改和审核统一由企业工作区处理。
  const canSubmit = Boolean(detail && detail.status === "OPEN" && !detail.mySubmission);

  const handleSubmit = async () => {
    if (!detail || !canSubmit) {
      return;
    }

    const normalizedText = submissionText.trim();
    const normalizedLinks = normalizeAttachmentLinks(attachmentLinks);

    // 成果说明和附件链接至少保留一个，避免给企业生成空提交。
    if (!normalizedText && normalizedLinks.length === 0) {
      setNotice({
        tone: "error",
        message: "请至少填写成果说明或补充一个可访问的链接。",
      });
      return;
    }

    setSubmitLoading(true);
    try {
      // 提交后重新读取详情，让 mySubmission、状态标签和通知提示都以服务端为准。
      const response = await apiRequest<BountySubmissionCreateResponse>(`/bounty/tasks/${detail.taskId}/submissions`, {
        method: "POST",
        body: JSON.stringify({
          contentText: normalizedText || null,
          attachmentLinks: normalizedLinks,
        }),
      });
      setNotice({
        tone: "success",
        message: `成果已提交，当前状态：${response.status}。现在可以留在本页等待审核结果。`,
      });
      setRefreshSeed((current) => current + 1);
    } catch (error) {
      setNotice({
        tone: "error",
        message: buildErrorMessage(error, "提交成果失败"),
      });
    } finally {
      setSubmitLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#eef3ff] text-slate-900">
      <div className="pointer-events-none fixed inset-0 z-0 bg-[radial-gradient(circle_at_top,rgba(79,70,229,0.14),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.12),transparent_28%),linear-gradient(180deg,#eff6ff_0%,#f8fafc_58%,#f8fafc_100%)]" />
      <StudentWorkspaceNav
        displayName={displayName}
        activeKey="bounty"
        sectionLabel="Task Detail"
        title="任务详情与提交"
      />

      <main className="page-enter-float relative z-10 mx-auto w-full max-w-[96rem] px-4 py-8 sm:px-6 lg:px-8">
        <div className="relative overflow-hidden rounded-[2rem] border border-white/80 bg-white p-6 shadow-[0_20px_50px_rgba(148,163,184,0.12)] backdrop-blur-xl lg:p-10">
          <div className="pointer-events-none absolute right-0 top-0 h-full w-1/3 bg-gradient-to-l from-indigo-50/50 to-transparent" />
          <div className="pointer-events-none absolute -right-10 -top-24 h-64 w-64 rounded-full bg-indigo-100/40 blur-3xl" />

          <div className="relative z-10 flex flex-col gap-6">
            <div className="flex items-center justify-between gap-4">
              <div className="flex items-center gap-2">
                <EnterpriseIdentityLogo
                  companyName={detail?.enterpriseName}
                  logoUrl={detail?.enterpriseLogoUrl}
                  className="h-9 w-9 rounded-[0.95rem] shadow-sm"
                  imageClassName="p-0"
                  fallbackClassName="bg-indigo-50 text-indigo-600"
                  textClassName="text-sm"
                />
                <span className="text-sm font-bold text-indigo-600">{detail?.enterpriseName || "企业实战任务"}</span>
                <span className="mx-1 text-slate-300">|</span>
                <span className="text-sm font-medium text-slate-500">企业实战任务</span>
              </div>
              <span className={joinClasses("inline-flex rounded-full border px-3 py-1.5 text-sm font-semibold shadow-sm", detail ? getTaskStatusClassName(detail) : "border-slate-200 bg-slate-100 text-slate-500")}>
                {taskStatusLabel}
              </span>
            </div>

            <div>
              <h1 className="text-3xl font-black tracking-tight leading-tight text-slate-900 lg:text-[2.5rem]">
                {detail?.title || "企业实战任务"}
              </h1>
            </div>

            <div className="flex flex-wrap items-center gap-x-8 gap-y-4 border-y border-slate-100 py-5">
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-amber-100/50 bg-amber-50 text-amber-500">
                  <Trophy size={20} />
                </div>
                <div>
                  <div className="text-sm font-semibold text-slate-500">奖励说明</div>
                  <div className="mt-0.5 text-sm font-bold text-slate-800">{detail?.rewardDescription || "待企业补充"}</div>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-slate-100/50 bg-slate-50 text-slate-500">
                  <CalendarDays size={20} />
                </div>
                <div>
                  <div className="text-sm font-semibold text-slate-500">截止时间</div>
                  <div className="mt-0.5 text-sm font-bold text-slate-800">{detail?.deadlineAt ? formatDateTime(detail.deadlineAt) : "未设置"}</div>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-slate-100/50 bg-slate-50 text-slate-500">
                  <Users size={20} />
                </div>
                <div>
                  <div className="text-sm font-semibold text-slate-500">当前提交</div>
                  <div className="mt-0.5 text-sm font-bold text-slate-800">{detail?.submissionCount ?? "—"} 人已提交</div>
                </div>
              </div>
            </div>

            <div className="flex flex-col gap-3 pt-1">
              {parsedDescription.directionTags.length ? (
                <div className="flex flex-wrap items-center gap-2">
                  <span className="mr-2 w-16 text-xs font-semibold text-slate-400">适合方向</span>
                  {parsedDescription.directionTags.map((tag) => (
                    <span key={tag} className="inline-flex items-center rounded-md border border-slate-200/60 bg-slate-50 px-2.5 py-1 text-xs font-medium text-slate-600">
                      {tag}
                    </span>
                  ))}
                </div>
              ) : null}
              {parsedDescription.audienceTags.length ? (
                <div className="flex flex-wrap items-center gap-2">
                  <span className="mr-2 w-16 text-xs font-semibold text-slate-400">适合人群</span>
                  {parsedDescription.audienceTags.map((tag) => (
                    <span key={tag} className="inline-flex items-center rounded-md border border-slate-200/60 bg-slate-50 px-2.5 py-1 text-xs font-medium text-slate-600">
                      {tag}
                    </span>
                  ))}
                </div>
              ) : null}
              {!parsedDescription.directionTags.length && !parsedDescription.audienceTags.length && previewChips.length ? (
                <div className="flex flex-wrap items-center gap-2">
                  {previewChips.map((chip) => (
                    <span key={chip} className="inline-flex items-center rounded-md border border-slate-200/60 bg-slate-50 px-2.5 py-1 text-xs font-medium text-slate-600">
                      {chip}
                    </span>
                  ))}
                </div>
              ) : null}
            </div>

            <div className="mt-2 flex items-center justify-between border-t border-slate-100/50 pt-5">
              <Link
                to="/bounty"
                className="group inline-flex items-center text-sm font-semibold text-slate-500 transition-colors hover:text-slate-900"
              >
                <span className="mr-2 flex h-8 w-8 items-center justify-center rounded-full border border-slate-200 bg-white shadow-sm transition-transform group-hover:-translate-x-1">
                  <ArrowLeft size={16} />
                </span>
                阅读完毕，返回任务大厅
              </Link>
            </div>
          </div>
        </div>

        {notice ? (
          <section
            className={joinClasses(
              "mt-6 rounded-[1.8rem] border px-5 py-4 text-sm",
              notice.tone === "success"
                ? "border-emerald-200 bg-emerald-50 text-emerald-800"
                : notice.tone === "info"
                  ? "border-indigo-200 bg-indigo-50 text-indigo-700"
                  : "border-rose-200 bg-rose-50 text-rose-700",
            )}
          >
            <div className="flex items-start gap-3">
              {notice.tone === "success" ? <CheckCircle2 size={18} className="mt-0.5 shrink-0" /> : <AlertCircle size={18} className="mt-0.5 shrink-0" />}
              <div>{notice.message}</div>
            </div>
          </section>
        ) : null}

        {loading ? (
          <section className="mt-6 grid gap-6 xl:grid-cols-[1.3fr_0.7fr]">
            <div className="h-[520px] rounded-[2rem] border border-white/80 bg-white/80 shadow-[0_20px_50px_rgba(148,163,184,0.10)]" />
            <div className="h-[420px] rounded-[2rem] border border-white/80 bg-white/80 shadow-[0_20px_50px_rgba(148,163,184,0.10)]" />
          </section>
        ) : errorMessage || !detail ? (
          <section className="mt-6 rounded-[1.8rem] border border-rose-200 bg-rose-50 px-5 py-5 text-sm text-rose-700">
            <div className="flex items-start gap-3">
              <AlertCircle size={18} className="mt-0.5 shrink-0" />
              <div>
                <div className="font-semibold">任务详情加载失败</div>
                <div className="mt-1">{errorMessage || "当前没有读取到任务详情。"}</div>
              </div>
            </div>
          </section>
        ) : (
          <div className="mt-6 grid gap-6 xl:grid-cols-[1.3fr_0.7fr]">
            <section className="rounded-[2rem] border border-white/80 bg-white/84 p-8 shadow-[0_22px_60px_rgba(148,163,184,0.12)] backdrop-blur-xl">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex min-h-[3.65rem] items-center">
                  <h2 className="text-2xl font-black leading-tight text-slate-950">任务说明与交付要求</h2>
                </div>
                <button
                  type="button"
                  onClick={() => setRefreshSeed((current) => current + 1)}
                  className="inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:border-slate-300 hover:text-slate-900"
                >
                  <RefreshCw size={15} className="mr-2" />
                  刷新状态
                </button>
              </div>

              {hasStructuredDescription ? (
                <div className="space-y-8">
                  <section>
                    <h3 className="flex items-center text-lg font-black text-slate-900">
                      <span className="mr-3 h-4 w-1.5 rounded-full bg-indigo-500" />
                      任务背景
                    </h3>
                    <p className="mt-4 text-[15px] leading-relaxed text-slate-600 whitespace-pre-wrap">
                      {parsedDescription.background || parsedDescription.summary || "当前没有额外背景说明。"}
                    </p>
                  </section>

                  <section>
                    <h3 className="flex items-center text-lg font-black text-slate-900">
                      <span className="mr-3 h-4 w-1.5 rounded-full bg-rose-400" />
                      希望解决的问题
                    </h3>
                    <div className="mt-4 rounded-2xl border border-rose-100/50 bg-rose-50/50 p-5 text-[15px] leading-relaxed text-rose-900/80 whitespace-pre-wrap">
                      {parsedDescription.problem || "当前没有补充具体问题。"}
                    </div>
                  </section>

                  <hr className="border-slate-100" />

                  <div className="space-y-6">
                    <div className="inline-flex items-center rounded-full border border-indigo-100 bg-indigo-50 px-4 py-1.5 text-sm font-bold text-indigo-700">
                      <Target size={16} className="mr-2" />
                      交付重点要求
                    </div>

                    <div className="grid gap-4 md:grid-cols-2">
                      <div className="rounded-[1.5rem] border border-indigo-100/80 bg-gradient-to-br from-indigo-50/80 to-white p-6 shadow-sm">
                        <div className="mb-4 flex items-center gap-2 text-indigo-900">
                          <Target size={18} />
                          <h4 className="font-bold">需要提交什么</h4>
                        </div>
                        <div className="space-y-3">
                          {deliveryItems.length ? deliveryItems.map((item) => (
                            <div key={item} className="flex items-start text-sm text-slate-700">
                              <span className="mr-2 mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-indigo-400" />
                              <span className="leading-relaxed">{item}</span>
                            </div>
                          )) : (
                            <div className="text-sm leading-relaxed text-slate-500">当前没有补充交付说明。</div>
                          )}
                        </div>
                      </div>

                      <div className="rounded-[1.5rem] border border-sky-100/80 bg-gradient-to-br from-sky-50/80 to-white p-6 shadow-sm">
                        <div className="mb-4 flex items-center gap-2 text-sky-900">
                          <LinkIcon size={18} />
                          <h4 className="font-bold">支持的交付形式</h4>
                        </div>
                        <div className="space-y-3">
                          {parsedDescription.deliveryFormats.length ? parsedDescription.deliveryFormats.map((item) => (
                            <div key={item} className="flex items-start text-sm text-slate-700">
                              <span className="mr-2 mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-sky-400" />
                              <span className="leading-relaxed">{item}</span>
                            </div>
                          )) : (
                            <div className="text-sm leading-relaxed text-slate-500">当前没有补充交付形式。</div>
                          )}
                        </div>
                      </div>

                      <div className="md:col-span-2 rounded-[1.5rem] border border-amber-100/80 bg-gradient-to-br from-amber-50/80 to-white p-6 shadow-sm">
                        <div className="mb-4 flex items-center gap-2 text-amber-900">
                          <Eye size={18} />
                          <h4 className="font-bold">企业最看重什么</h4>
                        </div>
                        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
                          {valueFocusItems.length ? valueFocusItems.map((item) => (
                            <div key={item} className="flex items-start gap-2 rounded-xl border border-amber-100/50 bg-white/60 p-3">
                              <Check size={16} className="mt-0.5 shrink-0 text-amber-500" />
                              <span className="text-sm font-medium text-slate-700">{item}</span>
                            </div>
                          )) : (
                            <div className="text-sm leading-relaxed text-slate-500">当前没有补充评估重点。</div>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>

                  <hr className="border-slate-100" />

                  <div className="space-y-8">
                    <section>
                      <h3 className="mb-4 text-base font-bold text-slate-900">参考资料</h3>
                      {parsedDescription.referenceLink ? (
                        referenceHref ? (
                          <a
                            href={referenceHref}
                            target="_blank"
                            rel="noreferrer"
                            className="group flex items-center justify-between rounded-xl border border-slate-200 bg-white p-4 transition-all hover:border-indigo-300 hover:shadow-md"
                          >
                            <div className="flex items-center gap-3 overflow-hidden">
                              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-slate-50 text-slate-500 transition-colors group-hover:bg-indigo-50 group-hover:text-indigo-600">
                                <FileText size={18} />
                              </div>
                              <div className="truncate">
                                <div className="truncate text-sm font-semibold text-slate-800">任务参考资料</div>
                                <div className="mt-0.5 truncate text-xs font-mono text-slate-400">{parsedDescription.referenceLink}</div>
                              </div>
                            </div>
                            <ExternalLink size={16} className="ml-2 shrink-0 text-slate-300 group-hover:text-indigo-400" />
                          </a>
                        ) : (
                          <div className="rounded-2xl border border-slate-100 bg-slate-50 px-5 py-4 text-sm leading-7 text-slate-600 whitespace-pre-wrap">
                            {parsedDescription.referenceLink}
                          </div>
                        )
                      ) : (
                        <div className="rounded-2xl border border-slate-100 bg-slate-50 px-5 py-4 text-sm text-slate-500">当前没有补充参考资料。</div>
                      )}
                    </section>

                    <section>
                      <div className="rounded-2xl border border-slate-200/60 bg-slate-50 p-5">
                        <h3 className="mb-3 flex items-center gap-2 text-sm font-bold text-slate-700">
                          <AlertCircle size={16} className="text-slate-400" />
                          注意事项
                        </h3>
                        <div className="text-sm leading-7 text-slate-600 whitespace-pre-wrap">
                          {parsedDescription.notes || "当前没有额外注意事项。"}
                        </div>
                      </div>
                    </section>
                  </div>
                </div>
              ) : (
                <div className="rounded-[1.8rem] border border-slate-100 bg-slate-50 px-5 py-5">
                  <div className="text-sm font-bold text-slate-800">完整任务说明</div>
                  <div className="mt-4 whitespace-pre-wrap text-sm leading-7 text-slate-600">{detail.description || "当前没有补充任务说明。"}</div>
                </div>
              )}
            </section>

            <aside className="space-y-6">
              <section className="rounded-[2rem] border border-white/80 bg-white/84 p-6 shadow-[0_20px_50px_rgba(148,163,184,0.10)]">
                <div className="flex items-center justify-between gap-3">
                  <div className="flex min-h-[3.65rem] items-center">
                    <h2 className="text-2xl font-black leading-tight text-slate-950">我的提交进度</h2>
                  </div>
                  {detail.mySubmission ? (
                    <span className={joinClasses("rounded-full border px-3 py-1 text-xs font-semibold", mySubmissionStatusMeta.className)}>
                      {mySubmissionStatusMeta.label}
                    </span>
                  ) : (
                    <span className={joinClasses("rounded-full border px-3 py-1 text-xs font-semibold", getTaskStatusClassName(detail))}>
                      {detail.status === "OPEN" ? "尚未提交" : "已结束未提交"}
                    </span>
                  )}
                </div>

                {detail.mySubmission ? (
                  <div className="mt-5 space-y-4">
                    <div
                      className={joinClasses(
                        "rounded-[1.5rem] border p-6 shadow-sm",
                        mySubmissionStatusMeta.tone === "accepted"
                          ? "border-emerald-200 bg-emerald-50/80"
                          : mySubmissionStatusMeta.tone === "rejected"
                            ? "border-slate-200 bg-slate-50"
                            : "border-dashed border-slate-200 bg-slate-50/80",
                      )}
                    >
                      <div className="mb-2 flex items-center gap-2">
                        <div
                          className={joinClasses(
                            "flex h-8 w-8 items-center justify-center rounded-full",
                            mySubmissionStatusMeta.tone === "accepted"
                              ? "bg-emerald-100 text-emerald-600"
                              : mySubmissionStatusMeta.tone === "rejected"
                                ? "bg-slate-200 text-slate-500"
                                : mySubmissionStatusMeta.tone === "reviewing"
                                  ? "bg-blue-100 text-blue-600"
                                  : "bg-amber-100 text-amber-500",
                          )}
                        >
                          {mySubmissionStatusMeta.tone === "accepted" ? (
                            <Sparkles size={16} />
                          ) : mySubmissionStatusMeta.tone === "reviewing" ? (
                            <Compass size={16} className="animate-spin" />
                          ) : mySubmissionStatusMeta.tone === "rejected" ? (
                            <Lock size={16} />
                          ) : (
                            <Clock3 size={16} />
                          )}
                        </div>
                        <h3
                          className={joinClasses(
                            "text-lg font-bold",
                            mySubmissionStatusMeta.tone === "accepted"
                              ? "text-emerald-900"
                              : mySubmissionStatusMeta.tone === "rejected"
                                ? "text-slate-800"
                                : "text-slate-500",
                          )}
                        >
                          {mySubmissionStatusMeta.tone === "accepted"
                            ? "企业意向：进一步交流"
                            : mySubmissionStatusMeta.tone === "rejected"
                              ? "未继续推进"
                              : "企业结果说明"}
                        </h3>
                      </div>
                      <div
                        className={joinClasses(
                          "mt-3 whitespace-pre-wrap text-sm leading-relaxed",
                          mySubmissionStatusMeta.tone === "accepted"
                            ? "text-emerald-800"
                            : mySubmissionStatusMeta.tone === "rejected"
                              ? "text-slate-600"
                              : "text-slate-400",
                        )}
                      >
                        {detail.mySubmission.reviewComment?.trim() || "企业暂未反馈。成果已送达，请耐心等待企业查阅与评估。"}
                      </div>
                      <div className="mt-4 text-[11px] font-mono text-slate-400">
                        审核时间：{detail.mySubmission.reviewedAt ? formatDateTime(detail.mySubmission.reviewedAt) : "暂未审核"}
                      </div>
                    </div>

                    <div className="rounded-[2rem] border border-white/80 bg-white/84 p-6 shadow-[0_22px_60px_rgba(148,163,184,0.18)] backdrop-blur-xl">
                      <div className="mb-5 flex items-center justify-between">
                        <h3 className="text-lg font-bold text-slate-900">我的提交内容</h3>
                        <span className="text-xs font-mono text-slate-400">
                          {detail.mySubmission.createdAt ? formatDateTime(detail.mySubmission.createdAt) : "提交时间待补充"}
                        </span>
                      </div>

                      <div className="space-y-5">
                        <div>
                          <div className="mb-2 text-sm font-bold text-slate-600">成果说明</div>
                          <div className="rounded-xl border border-slate-100 bg-slate-50 p-4 text-[14px] leading-relaxed text-slate-700">
                            {detail.mySubmission.contentText?.trim() || "本次主要通过外部链接提交，没有额外文字说明。"}
                          </div>
                        </div>

                        <div>
                          <div className="mb-2 text-sm font-bold text-slate-600">
                            附加链接 ({detail.mySubmission.attachmentLinks.length})
                          </div>
                          <div className="space-y-2">
                            {detail.mySubmission.attachmentLinks.length ? detail.mySubmission.attachmentLinks.map((item) => (
                              <a
                                key={item}
                                href={item}
                                target="_blank"
                                rel="noreferrer"
                                className="group flex items-center justify-between rounded-xl border border-slate-200 bg-white p-3 transition-colors hover:border-indigo-300"
                              >
                                <div className="flex items-center gap-3 overflow-hidden">
                                  <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-slate-50 text-slate-400 transition-colors group-hover:text-indigo-500">
                                    <LinkIcon size={14} />
                                  </div>
                                  <div className="truncate">
                                    <div className="text-xs font-bold text-slate-700">外部成果链接</div>
                                    <div className="truncate text-[11px] font-mono text-slate-400">{item}</div>
                                  </div>
                                </div>
                                <ExternalLink size={16} className="ml-3 shrink-0 text-slate-300 group-hover:text-indigo-400" />
                              </a>
                            )) : (
                              <div className="text-sm text-slate-500">当前没有附加链接。</div>
                            )}
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                ) : canSubmit ? (
                  <div className="mt-5 space-y-6">
                    <div className="border-b border-slate-100 pb-5">
                      <h3 className="text-xl font-black text-slate-950">提交任务成果</h3>
                      <p className="mt-2 text-sm leading-relaxed text-slate-500">
                        请把成果文档或设计稿整理成在线链接后提交。企业会优先阅读你的成果说明。
                      </p>
                    </div>

                    <div>
                      <label className="mb-2 flex items-center justify-between text-sm font-bold text-slate-800">
                        <span>成果说明</span>
                        <span className="text-xs font-normal text-slate-400">企业首阅区</span>
                      </label>
                      <textarea
                        value={submissionText}
                        onChange={(event) => setSubmissionText(event.target.value)}
                        placeholder={"建议说明：\n1. 我完成了什么，对应哪些任务要求\n2. 建议优先看哪个链接的哪个部分\n3. 其他补充思路..."}
                        className="min-h-[140px] w-full rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700 placeholder:text-slate-400 transition-all outline-none resize-y focus:border-indigo-400 focus:bg-white focus:ring-4 focus:ring-indigo-100"
                      />
                    </div>

                    <div>
                      <label className="mb-2 flex items-center justify-between text-sm font-bold text-slate-800">
                        <span>外部链接</span>
                        <span className="text-xs font-normal text-slate-400">资源索引</span>
                      </label>

                      <div className="mb-4 mt-1 rounded-xl border border-slate-200 bg-slate-50 p-3.5">
                        <div className="mb-2.5 flex items-center gap-2 text-[13px] font-bold text-slate-700">
                          <Info size={14} className="text-slate-400" />
                          推荐类型
                        </div>
                        <div className="mb-3 flex flex-wrap gap-2">
                          {["说明文档", "设计稿链接", "代码仓库链接", "演示视频 / 在线演示"].map((item) => (
                            <span key={item} className="rounded border border-slate-200 bg-white px-2 py-1 text-xs text-slate-600">
                              {item}
                            </span>
                          ))}
                        </div>
                        <div className="flex items-start gap-1.5 text-xs font-medium text-rose-500">
                          <AlertCircle size={14} className="mt-0.5 shrink-0" />
                          <span>仅支持填写有效站外链接，不支持也不需要本地文件上传。</span>
                        </div>
                      </div>

                      <div className="mb-2 flex items-center justify-between gap-3">
                        <button
                          type="button"
                          onClick={() => setAttachmentLinks((current) => [...current, ""])}
                          className="inline-flex items-center rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-600 transition-colors hover:border-indigo-200 hover:text-indigo-600"
                        >
                          <Plus size={14} className="mr-1.5" />
                          添加链接
                        </button>
                      </div>

                      <div className="space-y-3">
                        {attachmentLinks.map((item, index) => (
                          <div key={`link-${index}`} className="flex items-start gap-2">
                            <div className="flex-1 overflow-hidden rounded-xl border border-slate-200 bg-white transition-all focus-within:border-indigo-400 focus-within:ring-4 focus-within:ring-indigo-100">
                              <div className="flex items-center border-b border-slate-100 bg-slate-50/50 px-3 py-2">
                                <LinkIcon size={14} className="mr-2 text-slate-400" />
                                <input
                                  value={index === 0 ? "成果链接" : `补充链接 ${index + 1}`}
                                  readOnly
                                  className="w-full bg-transparent text-xs text-slate-500 outline-none"
                                />
                              </div>
                              <input
                                value={item}
                                onChange={(event) => {
                                  const nextLinks = [...attachmentLinks];
                                  nextLinks[index] = event.target.value;
                                  setAttachmentLinks(nextLinks);
                                }}
                                placeholder={index === 0 ? "https://github.com/your-project" : "https://demo.example.com"}
                                className="w-full px-3 py-2.5 text-sm outline-none"
                              />
                            </div>
                            {attachmentLinks.length > 1 ? (
                              <button
                                type="button"
                                onClick={() => setAttachmentLinks((current) => current.filter((_, currentIndex) => currentIndex !== index))}
                                className="mt-2 rounded-lg bg-slate-50 p-2 text-slate-400 transition-colors hover:text-rose-500"
                              >
                                <Trash2 size={15} />
                              </button>
                            ) : null}
                          </div>
                        ))}
                      </div>
                    </div>

                    <div className="border-t border-slate-100 pt-4">
                      <div className="mb-4 flex items-start gap-2 rounded-lg border border-amber-100 bg-amber-50 p-3 text-xs leading-relaxed text-amber-700">
                        <AlertCircle size={14} className="mt-0.5 shrink-0 text-amber-500" />
                        <div>
                          <strong>单次提交规则：</strong>当前任务仅允许提交一次，提交后不支持页面内反复改版重投。请确保说明与链接已整理完善。
                        </div>
                      </div>

                      <button
                        type="button"
                        onClick={() => void handleSubmit()}
                        disabled={submitLoading}
                        className="group relative flex w-full items-center justify-center overflow-hidden rounded-xl bg-slate-900 px-6 py-4 text-sm font-bold text-white transition-all hover:bg-slate-800 hover:shadow-[0_10px_20px_rgba(15,23,42,0.2)] disabled:cursor-not-allowed disabled:opacity-70"
                      >
                        <span className="relative z-10 flex items-center gap-2">
                          {submitLoading ? <Loader2 size={16} className="animate-spin" /> : "确认并提交成果"}
                          {!submitLoading ? <Send size={16} className="transition-transform group-hover:-translate-y-1 group-hover:translate-x-1" /> : null}
                        </span>
                      </button>
                    </div>

                  </div>
                ) : (
                  <div className="mt-5 rounded-[2rem] border border-slate-200 bg-slate-50/80 p-8 text-center shadow-sm backdrop-blur-xl">
                    <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-slate-200 text-slate-500">
                      <Lock size={24} />
                    </div>
                    <h3 className="text-lg font-bold text-slate-800">{detail.status === "OPEN" ? "你已提交过成果" : "任务已关闭"}</h3>
                    <p className="mt-2 text-sm leading-relaxed text-slate-500">
                      {detail.status === "OPEN"
                        ? "当前任务不支持重复提交，你已经有一条个人成果记录留在本页，可继续回看审核结果。"
                        : "该实战任务已过截止时间或企业已主动关闭，不再接受新的成果提交。"}
                    </p>
                    <Link
                      to="/bounty"
                      className="mt-6 inline-flex items-center justify-center rounded-full border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50"
                    >
                      查看其他任务
                    </Link>
                  </div>
                )}
              </section>
            </aside>
          </div>
        )}
      </main>
    </div>
  );
}
