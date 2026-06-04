import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Button,
  Empty,
  Input,
  Pagination,
  Select,
  Tag,
  Timeline,
  Typography,
  message,
} from "antd";
import {
  ArrowLeft,
  Building2,
  CheckCircle2,
  Clock3,
  ExternalLink,
  FileText,
  RefreshCcw,
  Search,
  UserCheck,
  X,
} from "lucide-react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  AdminDetailPlaceholder,
  AdminMetricCard,
  AdminMiniStat,
  AdminPageFrame,
  AdminPageHeader,
  AdminSurfaceCard,
  joinAdminClassNames,
} from "../components/admin/AdminOpsPrimitives";
import { useLatestRequest } from "../hooks/useLatestRequest";
import { buildAdminStaleCacheKey, useAdminStaleCache } from "../hooks/useAdminStaleCache";
import { ApiClientError, apiRequest, buildQuery, isAbortError } from "../lib/apiClient";
import { approvalStatusLabelMap, roleColorMap, roleLabelMap } from "../lib/adminLabels";
import { formatCount, formatDateTime } from "../lib/formatters";
import AdminIdentityAvatar from "../components/admin/AdminIdentityAvatar";

const { Paragraph } = Typography;

type ApprovalStatus = "PENDING" | "APPROVED" | "REJECTED";
type ReviewRole = "MENTOR" | "ENTERPRISE";

type ReviewListItem = {
  userId: number;
  email: string;
  displayName: string;
  role: ReviewRole;
  approvalStatus: ApprovalStatus;
  submissionId: number;
  submissionStatus: ApprovalStatus;
  realName: string;
  companyName: string | null;
  jobTitle: string | null;
  activeAssetCount: number;
  primaryAssetName: string | null;
  submittedAt: string | null;
  reviewedAt: string | null;
};

type ReviewListResponse = {
  records: ReviewListItem[];
  total: number;
  page: number;
  size: number;
};

type ReviewListCachePayload = {
  records: ReviewListItem[];
  total: number;
};

type CertificationAsset = {
  assetId: number;
  bucket: string;
  objectKey: string;
  originalFilename: string;
  contentType: string | null;
  sizeBytes: number;
  lifecycleStatus: string;
  deleteReason: string | null;
  uploadedAt: string | null;
  deletedAt: string | null;
};

type CertificationSubmission = {
  submissionId: number;
  userId: number;
  role: ReviewRole;
  realName: string;
  companyName: string | null;
  jobTitle: string | null;
  status: ApprovalStatus;
  current: boolean;
  reviewNote: string | null;
  previousSubmissionId: number | null;
  submittedAt: string | null;
  reviewedAt: string | null;
  assets: CertificationAsset[];
};

type ReviewDetailResponse = {
  userId: number;
  email: string;
  displayName: string;
  role: ReviewRole;
  approvalStatus: ApprovalStatus;
  currentSubmission: CertificationSubmission | null;
  submissions: CertificationSubmission[];
};

type ReviewQueryState = {
  page: number;
  size: number;
  keyword: string;
  role: ReviewRole | "";
  status: ApprovalStatus | "";
};

const DEBUG_MENTOR_REVIEW_USER_ID = 900001;

const DEBUG_MENTOR_REVIEW_DETAIL: ReviewDetailResponse = {
  userId: DEBUG_MENTOR_REVIEW_USER_ID,
  email: "mentor-cloud@example.com",
  displayName: "导师云",
  role: "MENTOR",
  approvalStatus: "PENDING",
  currentSubmission: {
    submissionId: 900031,
    userId: DEBUG_MENTOR_REVIEW_USER_ID,
    role: "MENTOR",
    realName: "云知行",
    companyName: "云梯智能研究院",
    jobTitle: "AI 导师 / 首席顾问",
    status: "PENDING",
    current: true,
    reviewNote: "待补充最近半年项目交付证明与导师服务截图。",
    previousSubmissionId: 900021,
    submittedAt: "2026-04-11T09:40:00+08:00",
    reviewedAt: null,
    assets: [
      {
        assetId: -900101,
        bucket: "debug",
        objectKey: "mentor-cloud-resume.pdf",
        originalFilename: "导师云-导师履历.pdf",
        contentType: "application/pdf",
        sizeBytes: 482314,
        lifecycleStatus: "ACTIVE",
        deleteReason: null,
        uploadedAt: "2026-04-11T09:38:00+08:00",
        deletedAt: null,
      },
      {
        assetId: -900102,
        bucket: "debug",
        objectKey: "mentor-cloud-proof.png",
        originalFilename: "项目交付证明.png",
        contentType: "image/png",
        sizeBytes: 291842,
        lifecycleStatus: "ACTIVE",
        deleteReason: null,
        uploadedAt: "2026-04-11T09:39:00+08:00",
        deletedAt: null,
      },
    ],
  },
  submissions: [
    {
      submissionId: 900031,
      userId: DEBUG_MENTOR_REVIEW_USER_ID,
      role: "MENTOR",
      realName: "云知行",
      companyName: "云梯智能研究院",
      jobTitle: "AI 导师 / 首席顾问",
      status: "PENDING",
      current: true,
      reviewNote: "待补充最近半年项目交付证明与导师服务截图。",
      previousSubmissionId: 900021,
      submittedAt: "2026-04-11T09:40:00+08:00",
      reviewedAt: null,
      assets: [
        {
          assetId: -900101,
          bucket: "debug",
          objectKey: "mentor-cloud-resume.pdf",
          originalFilename: "导师云-导师履历.pdf",
          contentType: "application/pdf",
          sizeBytes: 482314,
          lifecycleStatus: "ACTIVE",
          deleteReason: null,
          uploadedAt: "2026-04-11T09:38:00+08:00",
          deletedAt: null,
        },
        {
          assetId: -900102,
          bucket: "debug",
          objectKey: "mentor-cloud-proof.png",
          originalFilename: "项目交付证明.png",
          contentType: "image/png",
          sizeBytes: 291842,
          lifecycleStatus: "ACTIVE",
          deleteReason: null,
          uploadedAt: "2026-04-11T09:39:00+08:00",
          deletedAt: null,
        },
      ],
    },
    {
      submissionId: 900021,
      userId: DEBUG_MENTOR_REVIEW_USER_ID,
      role: "MENTOR",
      realName: "云知行",
      companyName: "云梯智能研究院",
      jobTitle: "职业规划导师",
      status: "REJECTED",
      current: false,
      reviewNote: "缺少能够体现真实导师身份的对外服务证明，请补充企业工牌或官网主页截图。",
      previousSubmissionId: 900011,
      submittedAt: "2026-04-02T14:15:00+08:00",
      reviewedAt: "2026-04-03T11:20:00+08:00",
      assets: [
        {
          assetId: -900103,
          bucket: "debug",
          objectKey: "mentor-cloud-card.png",
          originalFilename: "名片截图.png",
          contentType: "image/png",
          sizeBytes: 152840,
          lifecycleStatus: "REPLACED",
          deleteReason: "已替换为新版证明材料",
          uploadedAt: "2026-04-02T14:10:00+08:00",
          deletedAt: "2026-04-11T09:38:00+08:00",
        },
      ],
    },
    {
      submissionId: 900011,
      userId: DEBUG_MENTOR_REVIEW_USER_ID,
      role: "MENTOR",
      realName: "云知行",
      companyName: "青云教育科技",
      jobTitle: "课程顾问",
      status: "APPROVED",
      current: false,
      reviewNote: "首版材料可识别导师经历，先予通过；后续账号资料升级后重新发起了本轮复核。",
      previousSubmissionId: null,
      submittedAt: "2026-03-21T18:05:00+08:00",
      reviewedAt: "2026-03-22T10:00:00+08:00",
      assets: [
        {
          assetId: -900104,
          bucket: "debug",
          objectKey: "mentor-cloud-history.pdf",
          originalFilename: "历史导师资质.pdf",
          contentType: "application/pdf",
          sizeBytes: 362004,
          lifecycleStatus: "REPLACED",
          deleteReason: "历史版本材料已归档",
          uploadedAt: "2026-03-21T18:01:00+08:00",
          deletedAt: "2026-04-02T14:10:00+08:00",
        },
      ],
    },
  ],
};

function cloneReviewDetail(detail: ReviewDetailResponse): ReviewDetailResponse {
  return {
    ...detail,
    currentSubmission: detail.currentSubmission
      ? {
          ...detail.currentSubmission,
          assets: detail.currentSubmission.assets.map((asset) => ({ ...asset })),
        }
      : null,
    submissions: detail.submissions.map((submission) => ({
      ...submission,
      assets: submission.assets.map((asset) => ({ ...asset })),
    })),
  };
}

function createSyntheticHistory(detail: ReviewDetailResponse) {
  if (!detail.currentSubmission || detail.submissions.length >= 3) {
    return detail;
  }

  const current = detail.currentSubmission;
  const firstSynthetic: CertificationSubmission = {
    ...current,
    submissionId: current.submissionId - 1000,
    current: false,
    status: "REJECTED",
    reviewNote: "调试模拟：上一版材料缺少补充证明，已退回补件。",
    previousSubmissionId: current.submissionId - 2000,
    submittedAt: "2026-04-05T11:10:00+08:00",
    reviewedAt: "2026-04-06T15:40:00+08:00",
    assets: current.assets.map((asset, index) => ({
      ...asset,
      assetId: -(detail.userId * 100 + index + 1),
      lifecycleStatus: "REPLACED",
      deleteReason: "调试模拟历史材料",
      uploadedAt: "2026-04-05T11:00:00+08:00",
      deletedAt: "2026-04-09T16:10:00+08:00",
    })),
  };
  const secondSynthetic: CertificationSubmission = {
    ...current,
    submissionId: current.submissionId - 2000,
    current: false,
    status: "APPROVED",
    reviewNote: "调试模拟：早期版本已通过，后续因资料升级再次提交复核。",
    previousSubmissionId: null,
    submittedAt: "2026-03-24T19:20:00+08:00",
    reviewedAt: "2026-03-25T10:15:00+08:00",
    companyName: current.companyName || "星河顾问工作室",
    jobTitle: current.jobTitle || "导师顾问",
    assets: current.assets.map((asset, index) => ({
      ...asset,
      assetId: -(detail.userId * 100 + index + 11),
      lifecycleStatus: "REPLACED",
      deleteReason: "调试模拟历史材料",
      uploadedAt: "2026-03-24T19:10:00+08:00",
      deletedAt: "2026-04-05T11:00:00+08:00",
    })),
  };

  return {
    ...detail,
    submissions: [current, firstSynthetic, secondSynthetic],
  };
}

function matchesReviewFilters(record: ReviewListItem, queryState: ReviewQueryState) {
  if (queryState.role && record.role !== queryState.role) {
    return false;
  }
  if (queryState.status && record.submissionStatus !== queryState.status) {
    return false;
  }
  if (!queryState.keyword.trim()) {
    return true;
  }
  const keyword = queryState.keyword.trim().toLowerCase();
  return [record.displayName, record.email, record.realName, record.companyName ?? "", record.jobTitle ?? ""]
    .join(" ")
    .toLowerCase()
    .includes(keyword);
}

function buildDebugQueueRecord(detail: ReviewDetailResponse): ReviewListItem {
  return {
    userId: detail.userId,
    email: detail.email,
    displayName: detail.displayName,
    role: "MENTOR",
    approvalStatus: detail.approvalStatus,
    submissionId: detail.currentSubmission?.submissionId ?? 0,
    submissionStatus: detail.currentSubmission?.status ?? detail.approvalStatus,
    realName: detail.currentSubmission?.realName ?? "云知行",
    companyName: detail.currentSubmission?.companyName ?? null,
    jobTitle: detail.currentSubmission?.jobTitle ?? null,
    activeAssetCount: detail.currentSubmission?.assets.filter((asset) => asset.lifecycleStatus === "ACTIVE").length ?? 0,
    primaryAssetName: detail.currentSubmission?.assets[0]?.originalFilename ?? null,
    submittedAt: detail.currentSubmission?.submittedAt ?? null,
    reviewedAt: detail.currentSubmission?.reviewedAt ?? null,
  };
}

const reviewRoleOptions = [
  { label: "全部主体", value: "" },
  { label: "导师", value: "MENTOR" },
  { label: "企业", value: "ENTERPRISE" },
];

const reviewStatusOptions = [
  { label: "全部状态", value: "" },
  { label: "待审核", value: "PENDING" },
  { label: "已通过", value: "APPROVED" },
  { label: "已驳回", value: "REJECTED" },
];

function parsePositiveInt(value: string | null, fallback: number) {
  if (!value) {
    return fallback;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function parseReviewRole(value: string | null): ReviewRole | "" {
  return value === "MENTOR" || value === "ENTERPRISE" ? value : "";
}

function parseApprovalStatus(value: string | null): ApprovalStatus | "" {
  return value === "PENDING" || value === "APPROVED" || value === "REJECTED" ? value : "";
}

function formatFileSize(sizeBytes: number) {
  if (sizeBytes >= 1024 * 1024) {
    return `${(sizeBytes / (1024 * 1024)).toFixed(2)} MB`;
  }
  if (sizeBytes >= 1024) {
    return `${(sizeBytes / 1024).toFixed(1)} KB`;
  }
  return `${sizeBytes} B`;
}

function getSubmissionStatusTag(status: ApprovalStatus) {
  if (status === "APPROVED") {
    return <Tag color="success">已通过</Tag>;
  }
  if (status === "REJECTED") {
    return <Tag color="error">已驳回</Tag>;
  }
  return <Tag color="processing">待审核</Tag>;
}

function buildReviewRoute(targetUserId: number | null | undefined, queryState: ReviewQueryState) {
  const pathname = targetUserId ? `/admin/users/reviews/${targetUserId}` : "/admin/users/reviews";
  return `${pathname}${buildQuery({
    page: queryState.page,
    size: queryState.size,
    keyword: queryState.keyword || undefined,
    role: queryState.role || undefined,
    status: queryState.status || undefined,
  })}`;
}

export default function AdminUserCertificationReviewsPage() {
  const navigate = useNavigate();
  const { userId: userIdParam } = useParams();
  const [searchParams] = useSearchParams();
  const activeUserId = userIdParam ? Number(userIdParam) : null;

  const queryState = useMemo<ReviewQueryState>(
    () => ({
      page: parsePositiveInt(searchParams.get("page"), 1),
      size: parsePositiveInt(searchParams.get("size"), 8),
      keyword: searchParams.get("keyword") ?? "",
      role: parseReviewRole(searchParams.get("role")),
      status: parseApprovalStatus(searchParams.get("status")) || "PENDING",
    }),
    [searchParams],
  );

  const [keywordInput, setKeywordInput] = useState(queryState.keyword);
  const [roleFilterInput, setRoleFilterInput] = useState<ReviewRole | "">(queryState.role);
  const [statusFilterInput, setStatusFilterInput] = useState<ApprovalStatus | "">(queryState.status);
  const reviewListCache = useAdminStaleCache<ReviewListCachePayload>(
    buildAdminStaleCacheKey("admin-certification-reviews:list", queryState),
  );
  const reviewDetailCache = useAdminStaleCache<ReviewDetailResponse | null>(
    buildAdminStaleCacheKey("admin-certification-reviews:detail", {
      userId: activeUserId ?? "none",
    }),
  );
  const [reviewRecords, setReviewRecords] = useState<ReviewListItem[]>(() => reviewListCache.cached?.records ?? []);
  const [reviewTotal, setReviewTotal] = useState(() => reviewListCache.cached?.total ?? 0);
  const [reviewListLoading, setReviewListLoading] = useState(() => !reviewListCache.hasCache);
  const [reviewListError, setReviewListError] = useState<string | null>(null);
  const [reviewDetailLoading, setReviewDetailLoading] = useState(() => Boolean(activeUserId) && !reviewDetailCache.hasCache);
  const [reviewDetailError, setReviewDetailError] = useState<string | null>(null);
  const [reviewDetail, setReviewDetail] = useState<ReviewDetailResponse | null>(() => reviewDetailCache.cached);
  const [reviewNote, setReviewNote] = useState("");
  const [reviewActionLoading, setReviewActionLoading] = useState(false);
  const [debugMentorReviewDetail, setDebugMentorReviewDetail] = useState<ReviewDetailResponse>(() => cloneReviewDetail(DEBUG_MENTOR_REVIEW_DETAIL));

  const createReviewListRequest = useLatestRequest();
  const createReviewDetailRequest = useLatestRequest();

  useEffect(() => {
    setKeywordInput(queryState.keyword);
    setRoleFilterInput(queryState.role);
    setStatusFilterInput(queryState.status);
  }, [queryState.keyword, queryState.role, queryState.status]);

  const pushReviewRoute = useCallback(
    (targetUserId: number | null | undefined, patch: Partial<ReviewQueryState>) => {
      // 审核队列的筛选和选中用户都落到 URL，刷新页面仍能回到同一工作位。
      navigate(
        buildReviewRoute(targetUserId, {
          ...queryState,
          ...patch,
        }),
      );
    },
    [navigate, queryState],
  );

  const loadReviewQueue = useCallback(async (showLoading = true) => {
    const request = createReviewListRequest();
    if (showLoading) {
      setReviewListLoading(true);
    }
    setReviewListError(null);

    try {
      // 队列由服务端分页返回，前端只注入一条调试样本用于演示历史版本。
      const response = await apiRequest<ReviewListResponse>(
        `/admin/users/certification-reviews${buildQuery({
          page: queryState.page,
          size: queryState.size,
          keyword: queryState.keyword || undefined,
          role: queryState.role || undefined,
          status: queryState.status || undefined,
        })}`,
        { signal: request.signal },
      );
      if (!request.isCurrent()) {
        return;
      }
      const debugRecord = buildDebugQueueRecord(debugMentorReviewDetail);
      const allowDebugRecord = queryState.page === 1 && matchesReviewFilters(debugRecord, queryState);
      const nextRecords = allowDebugRecord
        ? [debugRecord, ...response.records.filter((record) => record.userId !== DEBUG_MENTOR_REVIEW_USER_ID)]
        : response.records.filter((record) => record.userId !== DEBUG_MENTOR_REVIEW_USER_ID);
      setReviewRecords(nextRecords);
      setReviewTotal(response.total + (allowDebugRecord ? 1 : 0));
      reviewListCache.write({
        records: nextRecords,
        total: response.total + (allowDebugRecord ? 1 : 0),
      });
    } catch (error) {
      if (isAbortError(error) || !request.isCurrent()) {
        return;
      }
      const apiError = error as ApiClientError;
      setReviewListError(apiError.message || "加载认证审核队列失败");
    } finally {
      if (request.isCurrent()) {
        setReviewListLoading(false);
      }
    }
  }, [createReviewListRequest, debugMentorReviewDetail, queryState.keyword, queryState.page, queryState.role, queryState.size, queryState.status, reviewListCache]);

  const loadReviewDetail = useCallback(
    async (targetUserId: number, showLoading = true) => {
      const request = createReviewDetailRequest();
      if (showLoading) {
        setReviewDetailLoading(true);
      }
      setReviewDetailError(null);

      try {
        if (targetUserId === DEBUG_MENTOR_REVIEW_USER_ID) {
          // 调试样本不访问后端，便于无真实材料时演示附件和历史版本 UI。
          if (!request.isCurrent()) {
            return;
          }
          const debugDetail = cloneReviewDetail(debugMentorReviewDetail);
          setReviewDetail(debugDetail);
          setReviewNote(debugDetail.currentSubmission?.reviewNote ?? "");
          return;
        }
        const response = await apiRequest<ReviewDetailResponse>(`/admin/users/${targetUserId}/certification-review`, {
          signal: request.signal,
        });
        if (!request.isCurrent()) {
          return;
        }
        // 历史版本不足时补 synthetic history，只影响前端展示，不写回服务端。
        const enrichedResponse = createSyntheticHistory(response);
        setReviewDetail(enrichedResponse);
        reviewDetailCache.write(enrichedResponse);
        setReviewNote(enrichedResponse.currentSubmission?.reviewNote ?? "");
      } catch (error) {
        if (isAbortError(error) || !request.isCurrent()) {
          return;
        }
        const apiError = error as ApiClientError;
        setReviewDetail(null);
        setReviewDetailError(apiError.message || "加载认证审核详情失败");
      } finally {
        if (request.isCurrent()) {
          setReviewDetailLoading(false);
        }
      }
    },
    [createReviewDetailRequest, debugMentorReviewDetail, reviewDetailCache],
  );

  useEffect(() => {
    if (!reviewListCache.cached) {
      return;
    }
    setReviewRecords(reviewListCache.cached.records);
    setReviewTotal(reviewListCache.cached.total);
  }, [reviewListCache.cached]);

  useEffect(() => {
    if (!reviewDetailCache.cached) {
      return;
    }
    setReviewDetail(reviewDetailCache.cached);
    setReviewNote(reviewDetailCache.cached?.currentSubmission?.reviewNote ?? "");
  }, [reviewDetailCache.cached]);

  useEffect(() => {
    void loadReviewQueue(!reviewListCache.hasCache);
  }, [loadReviewQueue, reviewListCache.hasCache]);

  useEffect(() => {
    if (!activeUserId || Number.isNaN(activeUserId)) {
      setReviewDetail(null);
      setReviewDetailError(null);
      setReviewNote("");
      return;
    }

    void loadReviewDetail(activeUserId, !reviewDetailCache.hasCache);
  }, [activeUserId, loadReviewDetail, reviewDetailCache.hasCache]);

  const currentReviewAssets = useMemo(
    () => reviewDetail?.currentSubmission?.assets.filter((asset) => asset.lifecycleStatus === "ACTIVE") ?? [],
    [reviewDetail],
  );

  const reviewTimelineItems = useMemo(
    () =>
      (reviewDetail?.submissions ?? []).map((submission) => ({
        color: submission.current ? "#4f46e5" : submission.status === "APPROVED" ? "#10b981" : submission.status === "REJECTED" ? "#ef4444" : "#94a3b8",
        children: (
          <div className="rounded-[24px] border border-slate-200/80 bg-[linear-gradient(180deg,#ffffff_0%,#f8fafc_100%)] px-4 py-4 shadow-[0_10px_26px_rgba(15,23,42,0.05)]">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-semibold text-slate-900">提交 #{submission.submissionId}</span>
              {submission.current ? <Tag color="processing">当前版本</Tag> : null}
              {getSubmissionStatusTag(submission.status)}
            </div>
            <div className="mt-3 text-sm text-slate-500">
              提交于 {formatDateTime(submission.submittedAt)}
              {submission.reviewedAt ? ` · 审核于 ${formatDateTime(submission.reviewedAt)}` : ""}
            </div>
            <div className="mt-3 text-sm leading-6 text-slate-600">
              {submission.realName}
              {submission.companyName ? ` · ${submission.companyName}` : ""}
              {submission.jobTitle ? ` · ${submission.jobTitle}` : ""}
            </div>
            {submission.reviewNote ? (
              <Paragraph className="!mb-0 !mt-3 !rounded-2xl !bg-white !px-4 !py-3 !text-sm !leading-7 !text-slate-600 !shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
                {submission.reviewNote}
              </Paragraph>
            ) : null}
          </div>
        ),
      })),
    [reviewDetail],
  );

  const summaryMetrics = useMemo(() => {
    const pendingCount = reviewRecords.filter((item) => item.submissionStatus === "PENDING").length;
    const processedCount = reviewRecords.filter((item) => item.submissionStatus !== "PENDING").length;
    const mentorCount = reviewRecords.filter((item) => item.role === "MENTOR").length;
    const enterpriseCount = reviewRecords.filter((item) => item.role === "ENTERPRISE").length;
    return {
      pendingCount,
      processedCount,
      mentorCount,
      enterpriseCount,
    };
  }, [reviewRecords]);

  const handleApplyFilters = useCallback(() => {
    pushReviewRoute(activeUserId, {
      page: 1,
      keyword: keywordInput.trim(),
      role: roleFilterInput,
      status: statusFilterInput,
    });
  }, [activeUserId, keywordInput, pushReviewRoute, roleFilterInput, statusFilterInput]);

  const handleResetFilters = useCallback(() => {
    pushReviewRoute(activeUserId, {
      page: 1,
      keyword: "",
      role: "",
      status: "PENDING",
    });
  }, [activeUserId, pushReviewRoute]);

  const handleOpenReview = useCallback(
    (userId: number) => {
      pushReviewRoute(userId, {});
    },
    [pushReviewRoute],
  );

  const handlePreviewAsset = useCallback(async (asset: CertificationAsset) => {
    try {
      if (asset.assetId < 0) {
        // 负数 assetId 是前端调试材料，生成临时文本文件模拟预览体验。
        const blob = new Blob(
          [
            `调试材料：${asset.originalFilename}\n类型：${asset.contentType || "未知"}\n大小：${formatFileSize(asset.sizeBytes)}\n上传时间：${formatDateTime(asset.uploadedAt)}\n\n此文件为前端调试模拟材料，用于认证审核页面样式联调。`,
          ],
          { type: "text/plain;charset=utf-8" },
        );
        const objectUrl = window.URL.createObjectURL(blob);
        window.open(objectUrl, "_blank", "noopener,noreferrer");
        window.setTimeout(() => {
          window.URL.revokeObjectURL(objectUrl);
        }, 60_000);
        return;
      }
      const response = await apiRequest<Response>(`/certification/assets/${asset.assetId}/content`, {
        rawResponse: true,
      });
      if (!response.ok) {
        throw new ApiClientError("认证材料读取失败", response.status);
      }
      const blob = await response.blob();
      const objectUrl = window.URL.createObjectURL(blob);
      window.open(objectUrl, "_blank", "noopener,noreferrer");
      window.setTimeout(() => {
        window.URL.revokeObjectURL(objectUrl);
      }, 60_000);
    } catch (error) {
      const apiError = error as ApiClientError;
      message.error(apiError.message || "打开认证材料失败");
    }
  }, []);

  const handleSubmitReview = useCallback(
    async (nextStatus: ApprovalStatus) => {
      if (!activeUserId || !reviewDetail) {
        return;
      }

      setReviewActionLoading(true);
      try {
        if (activeUserId === DEBUG_MENTOR_REVIEW_USER_ID && reviewDetail.currentSubmission) {
          // 调试样本只在前端内存推进状态，真实审核仍走后端接口。
          const reviewedAt = new Date().toISOString();
          const nextDetail: ReviewDetailResponse = {
            ...reviewDetail,
            approvalStatus: nextStatus,
            currentSubmission: {
              ...reviewDetail.currentSubmission,
              status: nextStatus,
              reviewNote,
              reviewedAt,
            },
            submissions: reviewDetail.submissions.map((submission) =>
              submission.submissionId === reviewDetail.currentSubmission?.submissionId
                ? {
                    ...submission,
                    status: nextStatus,
                    reviewNote,
                    reviewedAt,
                  }
                : submission,
            ),
          };
          setDebugMentorReviewDetail(nextDetail);
          setReviewDetail(nextDetail);
          setReviewRecords((currentRecords) =>
            currentRecords.map((record) =>
              record.userId === DEBUG_MENTOR_REVIEW_USER_ID
                ? {
                    ...record,
                    approvalStatus: nextStatus,
                    submissionStatus: nextStatus,
                    reviewedAt,
                  }
                : record,
            ),
          );
          setReviewNote(nextDetail.currentSubmission?.reviewNote ?? reviewNote);
          message.success(`已将认证状态更新为 ${approvalStatusLabelMap[nextStatus] ?? nextStatus}`);
          return;
        }
        const response = await apiRequest<ReviewDetailResponse>(`/admin/users/${activeUserId}/certification-review`, {
          method: "POST",
          body: JSON.stringify({
            approvalStatus: nextStatus,
            reviewNote,
          }),
        });
        setReviewDetail(response);
        setReviewNote(response.currentSubmission?.reviewNote ?? reviewNote);
        message.success(`已将认证状态更新为 ${approvalStatusLabelMap[nextStatus] ?? nextStatus}`);
        await Promise.all([loadReviewQueue(), loadReviewDetail(activeUserId)]);
      } catch (error) {
        const apiError = error as ApiClientError;
        message.error(apiError.message || "提交审核结论失败");
      } finally {
        setReviewActionLoading(false);
      }
    },
    [activeUserId, loadReviewDetail, loadReviewQueue, reviewDetail, reviewNote],
  );

  const refreshButtonLoading = reviewListLoading || reviewDetailLoading;

  return (
    <AdminPageFrame className="max-w-[1680px]">
      <AdminPageHeader
        sectionLabel="CERT REVIEW"
        title="认证审核"
        description="集中处理导师与企业的认证申请。"
        tone="indigo"
        actions={(
          <>
            <Button className="!h-11 !rounded-2xl !border-slate-200" onClick={() => navigate("/admin/users")}>
              <ArrowLeft size={16} className="mr-2" />
              返回用户与认证
            </Button>
            <Button
              className="!h-11 !rounded-2xl !border-slate-200 !px-5 !shadow-none"
              disabled={refreshButtonLoading}
              onClick={() => {
                void loadReviewQueue();
                if (activeUserId) {
                  void loadReviewDetail(activeUserId);
                }
              }}
            >
              <RefreshCcw size={16} className={refreshButtonLoading ? "mr-2 animate-spin" : "mr-2"} />
              刷新数据
            </Button>
          </>
        )}
      />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <AdminMetricCard
          icon={Clock3}
          label="待审核主体"
          value={reviewListLoading ? "—" : formatCount(summaryMetrics.pendingCount)}
          note="当前筛选页中的待处理记录"
          tone="amber"
        />
        <AdminMetricCard
          icon={CheckCircle2}
          label="已处理记录"
          value={reviewListLoading ? "—" : formatCount(summaryMetrics.processedCount)}
          note="已通过和已驳回的历史审核条目"
          tone="emerald"
        />
        <AdminMetricCard
          icon={UserCheck}
          label="导师主体"
          value={reviewListLoading ? "—" : formatCount(summaryMetrics.mentorCount)}
          note="本页导师审核数量"
          tone="violet"
        />
        <AdminMetricCard
          icon={Building2}
          label="企业主体"
          value={reviewListLoading ? "—" : formatCount(summaryMetrics.enterpriseCount)}
          note={`当前筛选命中 ${formatCount(reviewTotal)} 条审核记录`}
          tone="sky"
        />
      </div>

      <div className="grid gap-6 xl:grid-cols-[400px_minmax(0,1fr)] xl:items-start">
        <div className="xl:sticky xl:top-24 flex flex-col rounded-[30px] border border-slate-200/70 bg-white shadow-none">
          <div className="flex items-center justify-between border-b border-slate-200/70 px-6 py-5">
            <div className="text-lg font-bold text-slate-900">审核队列</div>
            <Tag color="processing" className="!m-0 !rounded-full !text-xs !font-bold">{formatCount(summaryMetrics.pendingCount)} 待审核</Tag>
          </div>

          <div className="border-b border-slate-200/70 px-5 py-4">
            <div className="flex flex-wrap items-center gap-3">
              <Input
                value={keywordInput}
                onChange={(event) => setKeywordInput(event.target.value)}
                onPressEnter={handleApplyFilters}
                placeholder="搜索邮箱、昵称或企业"
                prefix={<Search size={16} className="text-slate-400" />}
                className="min-w-[180px] flex-1 !h-10 !rounded-xl !border-transparent !bg-slate-50 !shadow-none hover:!border-transparent focus-within:!border-transparent"
              />
              <Select
                value={roleFilterInput}
                onChange={(value: ReviewRole | "") => {
                  setRoleFilterInput(value);
                  pushReviewRoute(activeUserId, { page: 1, role: value });
                }}
                options={reviewRoleOptions}
                className="min-w-[100px] [&_.ant-select-selector]:!h-10 [&_.ant-select-selector]:!rounded-xl [&_.ant-select-selector]:!border-transparent [&_.ant-select-selector]:!bg-slate-50"
              />
              <Select
                value={statusFilterInput}
                onChange={(value: ApprovalStatus | "") => {
                  setStatusFilterInput(value);
                  pushReviewRoute(activeUserId, { page: 1, status: value });
                }}
                options={reviewStatusOptions}
                className="min-w-[100px] [&_.ant-select-selector]:!h-10 [&_.ant-select-selector]:!rounded-xl [&_.ant-select-selector]:!border-transparent [&_.ant-select-selector]:!bg-slate-50"
              />
            </div>
          </div>

          {reviewListError ? (
            <div className="px-5 pt-5">
              <Alert type="warning" showIcon className="rounded-2xl" message="认证审核队列加载失败" description={reviewListError} />
            </div>
          ) : null}

          <div className="max-h-[calc(100vh-360px)] space-y-2 overflow-y-auto px-4 py-4">
            {reviewListLoading ? (
              <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-5 py-10 text-center text-sm text-slate-500">
                正在加载认证审核队列...
              </div>
            ) : reviewRecords.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={reviewListError ? "认证审核队列加载失败" : "当前没有匹配的审核主体"} />
            ) : (
              reviewRecords.map((record) => {
                const active = activeUserId === record.userId;
                return (
                  <button
                    key={record.submissionId}
                    type="button"
                    onClick={() => handleOpenReview(record.userId)}
                    className={joinAdminClassNames(
                      "w-full rounded-2xl border px-4 py-4 text-left transition-all",
                      active
                        ? "border-l-4 border-l-indigo-600 border-indigo-200 bg-indigo-50/80 shadow-[0_14px_28px_rgba(79,70,229,0.12)]"
                        : "border-transparent bg-transparent hover:bg-white hover:shadow-[0_4px_16px_rgba(15,23,42,0.04)]",
                    )}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex min-w-0 items-center gap-3">
                        <AdminIdentityAvatar
                          role={record.role}
                          userId={record.userId}
                          displayName={record.displayName}
                          enterpriseName={record.companyName}
                          className="!h-10 !w-10 !min-w-10 !shrink-0"
                          textClassName="text-sm"
                        />
                        <div className="min-w-0">
                          <div className="truncate text-sm font-bold text-slate-900">{record.displayName}</div>
                          <div className="truncate text-xs text-slate-500">{record.realName || record.email}</div>
                        </div>
                      </div>
                      {getSubmissionStatusTag(record.submissionStatus)}
                    </div>
                    <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-slate-500">
                      <Tag color={roleColorMap[record.role]} bordered={false} className="!text-xs">
                        {roleLabelMap[record.role] ?? record.role}
                      </Tag>
                      <span>{record.companyName || record.jobTitle}</span>
                    </div>
                    <div className="mt-3 text-[11px] text-slate-400">
                      {formatDateTime(record.submittedAt)}
                    </div>
                  </button>
                );
              })
            )}
          </div>

          <div className="border-t border-slate-200/70 px-5 py-4">
            <div className="flex flex-col gap-3 xl:gap-4">
              <div className="text-xs text-slate-500">
                {reviewTotal > 0
                  ? `显示 ${(queryState.page - 1) * queryState.size + 1}-${Math.min(queryState.page * queryState.size, reviewTotal)} 条，共 ${reviewTotal} 条`
                  : "暂无审核记录"}
              </div>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2 text-sm text-slate-500">
                  <span>每页</span>
                  <Select
                    value={queryState.size}
                    options={[
                      { label: "8 条", value: 8 },
                      { label: "12 条", value: 12 },
                      { label: "20 条", value: 20 },
                    ]}
                    onChange={(value: number) => {
                      pushReviewRoute(activeUserId, { page: 1, size: value });
                    }}
                    className="w-[96px] [&_.ant-select-selector]:!h-9 [&_.ant-select-selector]:!rounded-xl [&_.ant-select-selector]:!border-slate-200"
                  />
                </div>
                <Pagination
                  size="small"
                  current={queryState.page}
                  pageSize={queryState.size}
                  total={reviewTotal}
                  showSizeChanger={false}
                  onChange={(nextPage) => {
                    pushReviewRoute(activeUserId, { page: nextPage });
                  }}
                />
              </div>
            </div>
          </div>
        </div>

        <div className="relative flex min-h-[600px] flex-col">
          {!activeUserId ? (
            <div className="flex flex-1 items-center justify-center rounded-[30px] border border-slate-200/70 bg-white shadow-none">
              <AdminDetailPlaceholder description="请从左侧选择审核主体，开始处理认证申请" />
            </div>
          ) : reviewDetailLoading ? (
            <div className="flex flex-1 items-center justify-center rounded-[30px] border border-slate-200/70 bg-white shadow-none">
              <div className="text-sm text-slate-500">正在加载认证审核详情...</div>
            </div>
          ) : reviewDetailError ? (
            <div className="rounded-[30px] border border-slate-200/70 bg-white p-6 shadow-none">
              <Alert type="error" showIcon className="rounded-2xl" message="认证审核详情加载失败" description={reviewDetailError} />
            </div>
          ) : reviewDetail ? (
            <div className="flex flex-1 flex-col overflow-hidden">
              <div className="flex-1 overflow-y-auto">
                <div className="space-y-6">
                  <div className="grid gap-5 xl:grid-cols-[minmax(0,0.92fr)_400px]">
                    <div className="space-y-6">
                      <div className="rounded-[30px] border border-slate-200/70 bg-white p-8 shadow-none">
                        <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
                          <div className="flex min-w-0 items-start gap-4">
                            <AdminIdentityAvatar
                              role={reviewDetail.role}
                              userId={reviewDetail.userId}
                              displayName={reviewDetail.displayName}
                              enterpriseName={reviewDetail.currentSubmission?.companyName ?? null}
                              className="!h-[72px] !w-[72px] !min-w-[72px] !shrink-0"
                              textClassName="text-xl"
                            />
                            <div className="min-w-0">
                              <div className="flex flex-wrap items-center gap-2">
                                <div className="font-['Manrope'] text-[2rem] font-black tracking-[-0.04em] text-slate-950">
                                  {reviewDetail.displayName}
                                </div>
                                {getSubmissionStatusTag(reviewDetail.currentSubmission?.status ?? reviewDetail.approvalStatus)}
                              </div>
                              <div className="mt-2 text-sm text-slate-500">{reviewDetail.email}</div>
                              <div className="mt-4 flex flex-wrap gap-2">
                                <Tag color={roleColorMap[reviewDetail.role]} bordered={false}>
                                  {roleLabelMap[reviewDetail.role] ?? reviewDetail.role}
                                </Tag>
                                <Tag bordered={false} className="m-0 rounded-full bg-slate-100 px-3 py-1 text-slate-500">
                                  用户 #{reviewDetail.userId}
                                </Tag>
                                <Tag bordered={false} className="m-0 rounded-full bg-slate-100 px-3 py-1 text-slate-500">
                                  当前提交 {reviewDetail.currentSubmission ? `#${reviewDetail.currentSubmission.submissionId}` : "暂无提交"}
                                </Tag>
                                <Tag bordered={false} className="m-0 rounded-full bg-slate-100 px-3 py-1 text-slate-500">
                                  提交于 {reviewDetail.currentSubmission ? formatDateTime(reviewDetail.currentSubmission.submittedAt) : "—"}
                                </Tag>
                                <Tag bordered={false} className="m-0 rounded-full bg-slate-100 px-3 py-1 text-slate-500">
                                  历史版本 {formatCount(reviewDetail.submissions.length)} 版
                                </Tag>
                              </div>
                            </div>
                          </div>
                          <div className="flex flex-wrap gap-3">
                            <Button className="!h-11 !rounded-2xl !border-slate-200" onClick={() => navigate(`/admin/users/${reviewDetail.userId}`)}>
                              <ExternalLink size={16} className="mr-2" />
                              打开用户详情
                            </Button>
                            <Button className="!h-11 !rounded-2xl !border-slate-200" onClick={() => pushReviewRoute(null, {})}>
                              返回队列
                            </Button>
                          </div>
                        </div>

                        <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                          <AdminMiniStat label="实名信息" value={reviewDetail.currentSubmission?.realName || "—"} />
                          <AdminMiniStat label="企业 / 机构" value={reviewDetail.currentSubmission?.companyName || "—"} />
                          <AdminMiniStat label="岗位 / 头衔" value={reviewDetail.currentSubmission?.jobTitle || "—"} />
                          <AdminMiniStat label="当前认证状态" value={approvalStatusLabelMap[reviewDetail.approvalStatus] ?? reviewDetail.approvalStatus} />
                        </div>

                        <div className="mt-6 border-t border-slate-200/70 pt-6">
                          <div className="mb-4 flex items-center justify-between gap-3">
                            <div>
                              <div className="text-base font-bold text-slate-900">有效材料</div>
                              <div className="mt-1 text-sm text-slate-500">支持直接预览或下载附件，便于完成材料核验。</div>
                            </div>
                            <Tag bordered={false} className="m-0 rounded-full bg-indigo-50 px-3 py-1 text-xs font-semibold text-indigo-600">
                              {formatCount(currentReviewAssets.length)} 份有效材料
                            </Tag>
                          </div>
                          {currentReviewAssets.length === 0 ? (
                            <Alert
                              type="warning"
                              showIcon
                              className="rounded-2xl"
                              message="当前提交没有有效附件"
                              description="建议先联系用户补件，再继续完成最终审核。"
                            />
                          ) : (
                            <div className="space-y-3">
                              {currentReviewAssets.map((asset) => (
                                <div
                                  key={asset.assetId}
                                  className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-slate-50/80 px-4 py-4 xl:flex-row xl:items-center xl:justify-between"
                                >
                                  <div className="space-y-1">
                                    <div className="font-semibold text-slate-900">{asset.originalFilename}</div>
                                    <div className="text-xs text-slate-500">
                                      {(asset.contentType || "未知类型").toUpperCase()} · {formatFileSize(asset.sizeBytes)} · 上传于 {formatDateTime(asset.uploadedAt)}
                                    </div>
                                  </div>
                                  <Button className="!h-10 !rounded-xl !border-slate-200" onClick={() => void handlePreviewAsset(asset)}>
                                    <FileText size={15} className="mr-2" />
                                    预览 / 下载
                                  </Button>
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      </div>

                      <AdminSurfaceCard
                        title="审核依据与操作"
                        description="填写审核依据后，直接在此完成认证通过或驳回。"
                      >
                        {reviewDetail.currentSubmission ? (
                          <div className="space-y-4">
                            <Input.TextArea
                              value={reviewNote}
                              onChange={(event) => setReviewNote(event.target.value)}
                              rows={4}
                              maxLength={1000}
                              showCount
                              className="!rounded-2xl !bg-slate-50"
                              placeholder="填写审核依据..."
                            />
                            <div className="flex flex-wrap justify-end gap-3">
                              <Button
                                danger
                                className="!h-11 !rounded-2xl !px-5 !font-semibold"
                                loading={reviewActionLoading}
                                onClick={() => void handleSubmitReview("REJECTED")}
                              >
                                <X size={16} className="mr-2" />
                                驳回
                              </Button>
                              <Button
                                type="primary"
                                className="!h-11 !rounded-2xl !border-0 !bg-gradient-to-r !from-emerald-500 !to-emerald-600 !px-5 !font-semibold !shadow-lg !shadow-emerald-200"
                                loading={reviewActionLoading}
                                onClick={() => void handleSubmitReview("APPROVED")}
                              >
                                <CheckCircle2 size={16} className="mr-2" />
                                认证通过
                              </Button>
                            </div>
                          </div>
                        ) : (
                          <Alert type="info" showIcon className="!mb-0 !rounded-2xl" message="当前主体暂无可处理的认证提交" />
                        )}
                      </AdminSurfaceCard>

                    </div>

                    <AdminSurfaceCard
                      title="提交历史"
                      description="按版本时间线查看历次提交与审核记录。"
                      bodyClassName="max-h-[calc(100vh-320px)] overflow-y-auto pr-4"
                      extra={(
                        <Tag bordered={false} className="m-0 rounded-full bg-slate-100 px-3 py-1 text-xs text-slate-500">
                          {formatCount(reviewDetail.submissions.length)} 版
                        </Tag>
                      )}
                    >
                      {reviewTimelineItems.length > 0 ? (
                        <Timeline
                          className="[&_.ant-timeline-item-tail]:!border-slate-200 [&_.ant-timeline-item-head]:!h-3 [&_.ant-timeline-item-head]:!w-3 [&_.ant-timeline-item-content]:!pb-6"
                          items={reviewTimelineItems}
                        />
                      ) : (
                        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无历史提交记录" />
                      )}
                    </AdminSurfaceCard>
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <div className="flex flex-1 items-center justify-center rounded-[30px] border border-slate-200/70 bg-white shadow-none">
              <AdminDetailPlaceholder description="当前用户没有可查看的认证提交" />
            </div>
          )}
        </div>
      </div>
    </AdminPageFrame>
  );
}
