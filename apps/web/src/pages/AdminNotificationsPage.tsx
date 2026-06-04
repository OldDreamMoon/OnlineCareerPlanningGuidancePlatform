import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Button,
  Checkbox,
  Form,
  Input,
  Space,
  Table,
  Tag,
  Typography,
  message,
  type TableColumnsType,
} from "antd";
import {
  Bell,
  Mail,
  Megaphone,
  RefreshCcw,
  Send,
  Workflow,
  AlertTriangle,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useLatestRequest } from "../hooks/useLatestRequest";
import { buildAdminStaleCacheKey, useAdminStaleCache } from "../hooks/useAdminStaleCache";
import { ApiClientError, apiRequest, buildQuery, isAbortError } from "../lib/apiClient";
import {
  getLabel,
  notificationChannelLabelMap,
  notificationDispatchStatusLabelMap,
  notificationPriorityLabelMap,
  roleLabelMap,
} from "../lib/adminLabels";
import { formatCount, formatDateTime } from "../lib/formatters";
import {
  AdminMetricCard,
  AdminPageHeader,
  AdminMiniStat,
  AdminPageFrame,
  AdminSurfaceCard,
} from "../components/admin/AdminOpsPrimitives";

const { Paragraph, Text } = Typography;

const AdminNotificationsDispatchDrawer = lazy(() => import("../components/admin/overlays/AdminNotificationsDispatchDrawer"));

type TimeValue = number | string | null;
type AnnouncementTargetRole = "STUDENT" | "MENTOR" | "ENTERPRISE";

type ChannelOpsSummaryItem = {
  channel: string;
  total: number;
  sent: number;
  acked: number;
  retryWait: number;
  dead: number;
  skipped: number;
};

type NotificationOpsOverviewPayload = {
  announcementCount: number;
  announcementsLast7Days: number;
  pendingJobCount: number;
  retryJobCount: number;
  deadJobCount: number;
  emailReady: boolean;
  emailSender: string | null;
  websocketReady: boolean;
  lastAnnouncementAt: TimeValue;
  channelStats: ChannelOpsSummaryItem[];
};

type AnnouncementItem = {
  eventId: string;
  title: string;
  content: string;
  priority: string;
  targetRoles: string[];
  deliveryChannels: string[];
  notificationCount: number;
  refType: string | null;
  refId: string | null;
  actionCode: string | null;
  createdAt: TimeValue;
};

type AnnouncementListPayload = {
  records: AnnouncementItem[];
};

type DispatchJobItem = {
  jobId: string;
  notificationId: number;
  eventId: string;
  userId: number;
  channel: string;
  status: string;
  attemptCount: number;
  maxAttempts: number;
  nextRunAt: TimeValue;
  sentAt: TimeValue;
  ackedAt: TimeValue;
  failedAt: TimeValue;
  errorCode: string | null;
  errorMessage: string | null;
  title: string | null;
  content: string | null;
  type: string | null;
  createdAt: TimeValue;
  updatedAt: TimeValue;
};

type DispatchJobListPayload = {
  records: DispatchJobItem[];
  total: number;
};

type NotificationBaseCachePayload = {
  overview: NotificationOpsOverviewPayload | null;
  announcements: AnnouncementItem[];
};

type DispatchJobCachePayload = {
  records: DispatchJobItem[];
  total: number;
};

type DispatchJobRetryResponse = {
  jobId: string;
  status: string;
  nextRunAt: TimeValue;
};

type DispatchJobCleanupResponse = {
  deletedCount: number;
  deletedBeforeAt: TimeValue;
};

type PublishAnnouncementResponse = {
  eventId: string;
  notificationCount: number;
  targetRoles: string[];
  createdAt: TimeValue;
};

type PublishAnnouncementFormValues = {
  title?: string;
  content?: string;
  targetRoles?: AnnouncementTargetRole[];
  emailRequested?: boolean;
};

const roleOptions = [
  { label: roleLabelMap.STUDENT, value: "STUDENT" },
  { label: roleLabelMap.MENTOR, value: "MENTOR" },
  { label: roleLabelMap.ENTERPRISE, value: "ENTERPRISE" },
] as const;

const dispatchStatusOptions = [
  { label: "派发状态：全部", value: "" },
  { label: getLabel("DEAD", notificationDispatchStatusLabelMap, "已进入死信"), value: "DEAD" },
  { label: getLabel("RETRY_WAIT", notificationDispatchStatusLabelMap, "等待重试"), value: "RETRY_WAIT" },
  { label: getLabel("SKIPPED", notificationDispatchStatusLabelMap, "已跳过"), value: "SKIPPED" },
  { label: getLabel("PENDING", notificationDispatchStatusLabelMap, "待发送"), value: "PENDING" },
  { label: getLabel("ACKED", notificationDispatchStatusLabelMap, "已确认"), value: "ACKED" },
];

const channelOptions = [
  { label: "渠道：全部", value: "" },
  { label: getLabel("WEBSOCKET", notificationChannelLabelMap, "站内实时"), value: "WEBSOCKET" },
  { label: getLabel("EMAIL", notificationChannelLabelMap, "邮件"), value: "EMAIL" },
];

const defaultAnnouncementTargetRoles: AnnouncementTargetRole[] = ["STUDENT", "MENTOR", "ENTERPRISE"];
const announcementChannelLabelMap = {
  IN_APP: "站内信",
  EMAIL: "邮件",
} as const;

function getDispatchStatusTag(status: string) {
  if (status === "DEAD") {
    return <Tag color="error">{getLabel(status, notificationDispatchStatusLabelMap, status)}</Tag>;
  }
  if (status === "RETRY_WAIT") {
    return <Tag color="warning">{getLabel(status, notificationDispatchStatusLabelMap, status)}</Tag>;
  }
  if (status === "ACKED" || status === "SENT") {
    return <Tag color="success">{getLabel(status, notificationDispatchStatusLabelMap, status)}</Tag>;
  }
  if (status === "SKIPPED") {
    return <Tag>{getLabel(status, notificationDispatchStatusLabelMap, status)}</Tag>;
  }
  return <Tag color="processing">{getLabel(status, notificationDispatchStatusLabelMap, status)}</Tag>;
}

function getPriorityTag(priority: string) {
  if (priority === "HIGH" || priority === "URGENT") {
    return <Tag color="error">{getLabel(priority, notificationPriorityLabelMap, priority)}</Tag>;
  }
  return <Tag>{getLabel(priority, notificationPriorityLabelMap, priority)}</Tag>;
}

export default function AdminNotificationsPage() {
  const navigate = useNavigate();
  const [publishForm] = Form.useForm();
  const createBaseRequest = useLatestRequest();
  const createDispatchJobsRequest = useLatestRequest();
  const [dispatchJobsPage, setDispatchJobsPage] = useState(1);
  const [dispatchJobsPageSize, setDispatchJobsPageSize] = useState(10);
  const [dispatchStatusFilter, setDispatchStatusFilter] = useState("");
  const [dispatchChannelFilter, setDispatchChannelFilter] = useState("");
  const baseCache = useAdminStaleCache<NotificationBaseCachePayload>("admin-notifications:base");
  const dispatchJobsCache = useAdminStaleCache<DispatchJobCachePayload>(
    buildAdminStaleCacheKey("admin-notifications:dispatch-jobs", {
      dispatchJobsPage,
      dispatchJobsPageSize,
      dispatchStatusFilter,
      dispatchChannelFilter,
    }),
  );
  const [overview, setOverview] = useState<NotificationOpsOverviewPayload | null>(() => baseCache.cached?.overview ?? null);
  const [announcements, setAnnouncements] = useState<AnnouncementItem[]>(() => baseCache.cached?.announcements ?? []);
  const [baseLoading, setBaseLoading] = useState(() => !baseCache.hasCache);
  const [baseError, setBaseError] = useState<string | null>(null);
  const [dispatchJobs, setDispatchJobs] = useState<DispatchJobItem[]>(() => dispatchJobsCache.cached?.records ?? []);
  const [dispatchJobsTotal, setDispatchJobsTotal] = useState(() => dispatchJobsCache.cached?.total ?? 0);
  const [dispatchJobsLoading, setDispatchJobsLoading] = useState(false);
  const [dispatchJobsError, setDispatchJobsError] = useState<string | null>(null);
  const [dispatchDrawerOpen, setDispatchDrawerOpen] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [purgingDispatchJobs, setPurgingDispatchJobs] = useState(false);
  const [retryingJobId, setRetryingJobId] = useState<string | null>(null);

  const watchedAnnouncementTargetRoles = Form.useWatch("targetRoles", publishForm) as AnnouncementTargetRole[] | undefined;
  const watchedAnnouncementEmailRequested = Form.useWatch("emailRequested", publishForm) as boolean | undefined;

  const resetPublishForm = useCallback((targetRoles: AnnouncementTargetRole[] = defaultAnnouncementTargetRoles) => {
    // 发布公告默认覆盖三类业务角色，邮箱派发默认关闭，避免误触发外部邮件。
    publishForm.resetFields();
    publishForm.setFieldsValue({
      targetRoles: [...targetRoles],
      emailRequested: false,
    });
  }, [publishForm]);

  useEffect(() => {
    resetPublishForm();
  }, [resetPublishForm]);

  const loadBaseData = useCallback(async (showLoading = true) => {
    const request = createBaseRequest();
    if (showLoading) {
      setBaseLoading(true);
    }
    setBaseError(null);
    try {
      // 概览和公告列表同属通知运营首页，一起缓存以保证口径同步。
      const [overviewPayload, announcementsPayload] = await Promise.all([
        apiRequest<NotificationOpsOverviewPayload>("/admin/notifications/overview", { signal: request.signal }),
        apiRequest<AnnouncementListPayload>("/admin/notifications/announcements", { signal: request.signal }),
      ]);
      if (!request.isCurrent()) {
        return;
      }
      setOverview(overviewPayload);
      setAnnouncements(announcementsPayload.records);
      baseCache.write({
        overview: overviewPayload,
        announcements: announcementsPayload.records,
      });
    } catch (error) {
      if (isAbortError(error) || !request.isCurrent()) {
        return;
      }
      const apiError = error as ApiClientError;
      setBaseError(apiError.message || "加载通知运营失败");
    } finally {
      if (request.isCurrent()) {
        setBaseLoading(false);
      }
    }
  }, [baseCache, createBaseRequest]);

  const loadDispatchJobs = useCallback(async (showLoading = true) => {
    const request = createDispatchJobsRequest();
    if (showLoading) {
      setDispatchJobsLoading(true);
    }
    setDispatchJobsError(null);
    try {
      // 派发队列按抽屉懒加载，后台首页不主动拉大量 job 记录。
      const query = buildQuery({
        page: dispatchJobsPage,
        size: dispatchJobsPageSize,
        status: dispatchStatusFilter || undefined,
        channel: dispatchChannelFilter || undefined,
      });
      const payload = await apiRequest<DispatchJobListPayload>(`/admin/notifications/dispatch-jobs${query}`, {
        signal: request.signal,
      });
      if (!request.isCurrent()) {
        return;
      }
      setDispatchJobs(payload.records);
      setDispatchJobsTotal(payload.total);
      dispatchJobsCache.write({
        records: payload.records,
        total: payload.total,
      });
    } catch (error) {
      if (isAbortError(error) || !request.isCurrent()) {
        return;
      }
      const apiError = error as ApiClientError;
      setDispatchJobsError(apiError.message || "加载派发队列失败");
    } finally {
      if (request.isCurrent()) {
        setDispatchJobsLoading(false);
      }
    }
  }, [createDispatchJobsRequest, dispatchChannelFilter, dispatchJobsCache, dispatchJobsPage, dispatchJobsPageSize, dispatchStatusFilter]);

  useEffect(() => {
    if (!baseCache.cached) {
      return;
    }
    setOverview(baseCache.cached.overview);
    setAnnouncements(baseCache.cached.announcements);
  }, [baseCache.cached]);

  useEffect(() => {
    if (!dispatchJobsCache.cached) {
      return;
    }
    setDispatchJobs(dispatchJobsCache.cached.records);
    setDispatchJobsTotal(dispatchJobsCache.cached.total);
  }, [dispatchJobsCache.cached]);

  useEffect(() => {
    void loadBaseData(!baseCache.hasCache);
  }, [baseCache.hasCache, loadBaseData]);

  useEffect(() => {
    if (!dispatchDrawerOpen) {
      return;
    }
    void loadDispatchJobs(!dispatchJobsCache.hasCache);
  }, [dispatchDrawerOpen, dispatchJobsCache.hasCache, loadDispatchJobs]);

  const handleRefresh = useCallback(() => {
    const tasks: Promise<unknown>[] = [loadBaseData()];
    if (dispatchDrawerOpen) {
      tasks.push(loadDispatchJobs());
    }
    void Promise.all(tasks);
  }, [dispatchDrawerOpen, loadBaseData, loadDispatchJobs]);

  const handlePublish = async () => {
    try {
      const values = await publishForm.validateFields() as PublishAnnouncementFormValues;
      const payload = {
        title: values.title?.trim() ?? "",
        content: values.content?.trim() ?? "",
        targetRoles: values.targetRoles && values.targetRoles.length > 0 ? values.targetRoles : undefined,
        priority: "NORMAL",
        refType: "NOTIFICATION_CENTER",
        actionCode: "VIEW_NOTIFICATION_CENTER",
        emailRequested: values.emailRequested === true,
      };
      setPublishing(true);
      try {
        // 公告最终也走通知 descriptor 链，actionCode 固定回通知中心。
        const response = await apiRequest<PublishAnnouncementResponse>("/admin/notifications/announcements", {
          method: "POST",
          body: JSON.stringify(payload),
        });
        message.success(`系统公告已发布，入箱 ${formatCount(response.notificationCount)} 人`);
        resetPublishForm(
          response.targetRoles.length > 0
            ? response.targetRoles.filter((role): role is AnnouncementTargetRole => defaultAnnouncementTargetRoles.includes(role as AnnouncementTargetRole))
            : defaultAnnouncementTargetRoles,
        );
        const tasks: Promise<unknown>[] = [loadBaseData()];
        if (dispatchDrawerOpen) {
          tasks.push(loadDispatchJobs());
        }
        await Promise.all(tasks);
      } catch (error) {
        const apiError = error as ApiClientError;
        message.error(apiError.message || "发布系统公告失败");
      } finally {
        setPublishing(false);
      }
    } catch {
      return;
    }
  };

  const handleRetryDispatchJob = async (record: DispatchJobItem) => {
    setRetryingJobId(record.jobId);
    try {
      // 重试只重新入队当前 job，之后重新拉概览和队列确认状态。
      await apiRequest<DispatchJobRetryResponse>(`/admin/notifications/dispatch-jobs/${record.jobId}/retry`, {
        method: "POST",
      });
      message.success(`任务 ${record.jobId} 已重新入队`);
      await Promise.all([loadBaseData(), loadDispatchJobs()]);
    } catch (error) {
      const apiError = error as ApiClientError;
      message.error(apiError.message || "重新入队失败");
    } finally {
      setRetryingJobId(null);
    }
  };

  const handlePurgeDispatchJobs = async () => {
    setPurgingDispatchJobs(true);
    try {
      // 清理只作用于终态 job，不影响待派发和重试中的通知任务。
      const response = await apiRequest<DispatchJobCleanupResponse>("/admin/notifications/dispatch-jobs/purge-terminal?olderThanHours=0", {
        method: "POST",
      });
      if (response.deletedCount > 0) {
        message.success(`已清理 ${formatCount(response.deletedCount)} 条旧终态任务`);
      } else {
        message.info("当前没有可清理的旧终态任务");
      }
      await Promise.all([loadBaseData(), loadDispatchJobs()]);
    } catch (error) {
      const apiError = error as ApiClientError;
      message.error(apiError.message || "清理旧任务失败");
    } finally {
      setPurgingDispatchJobs(false);
    }
  };

  const publishTargetSummary = useMemo(() => {
    const effectiveRoles = watchedAnnouncementTargetRoles && watchedAnnouncementTargetRoles.length > 0
      ? watchedAnnouncementTargetRoles
      : defaultAnnouncementTargetRoles;
    return effectiveRoles.map((role) => roleLabelMap[role] ?? role).join(" / ");
  }, [watchedAnnouncementTargetRoles]);

  const refreshButtonLoading = baseLoading || (dispatchDrawerOpen && dispatchJobsLoading);

  const summaryCards = useMemo(
    () => [
      {
        key: "announcementCount",
        label: "累计公告批次",
        value: formatCount(overview?.announcementCount ?? 0),
        note: `近 7 天新增 ${formatCount(overview?.announcementsLast7Days ?? 0)} 批`,
        icon: Megaphone,
        tone: "teal" as const,
      },
      {
        key: "pendingJobCount",
        label: "待发送任务",
        value: formatCount(overview?.pendingJobCount ?? 0),
        note: "当前仍在派发队列中的任务",
        icon: Workflow,
        tone: "indigo" as const,
      },
      {
        key: "retryJobCount",
        label: "等待重试",
        value: formatCount(overview?.retryJobCount ?? 0),
        note: "由 worker 自动回退后的任务",
        icon: RefreshCcw,
        tone: "amber" as const,
      },
      {
        key: "deadJobCount",
        label: "死信任务",
        value: formatCount(overview?.deadJobCount ?? 0),
        note: "需要管理员人工关注的失败任务",
        icon: AlertTriangle,
        tone: "rose" as const,
      },
    ],
    [overview],
  );

  const announcementColumns = useMemo<TableColumnsType<AnnouncementItem>>(
    () => [
      {
        title: "公告",
        key: "title",
        render: (_value, record) => (
          <div className="min-w-0 space-y-1.5">
            <div className="flex flex-wrap items-start gap-2">
              <Text
                strong
                className="block text-sm leading-6 text-slate-900"
                title={record.title}
              >
                {record.title}
              </Text>
              {getPriorityTag(record.priority)}
            </div>
            <Text
              className="block line-clamp-2 text-sm leading-6 text-slate-600"
              title={record.content}
            >
              {record.content}
            </Text>
            <Text type="secondary" className="block break-all text-xs" title={record.eventId}>
              事件 {record.eventId}
            </Text>
          </div>
        ),
      },
      {
        title: "目标角色",
        key: "roles",
        render: (_value, record) => (
          <Space size={[4, 4]} wrap>
            {record.targetRoles.length > 0
              ? record.targetRoles.map((role) => (
                <Tag key={role} color="blue" className="!mr-1 !px-2 !text-[11px]">
                  {getLabel(role, roleLabelMap, role)}
                </Tag>
              ))
              : <Tag className="!mr-1 !px-2 !text-[11px]">全角色</Tag>}
          </Space>
        ),
      },
      {
        title: "通知方式",
        key: "deliveryChannels",
        render: (_value, record) => (
          <Space size={[4, 4]} wrap>
            {(record.deliveryChannels.length > 0 ? record.deliveryChannels : ["IN_APP"]).map((channel) => (
              <Tag
                key={`${record.eventId}-${channel}`}
                color={channel === "EMAIL" ? "gold" : "blue"}
                className="!mr-1 !px-2 !text-[11px]"
              >
                {getLabel(channel, announcementChannelLabelMap, channel)}
              </Tag>
            ))}
          </Space>
        ),
      },
      {
        title: "入箱量",
        dataIndex: "notificationCount",
        align: "center",
        render: (value: number) => <Text className="text-sm font-medium text-slate-700">{formatCount(value)}</Text>,
      },
      {
        title: "发布时间",
        dataIndex: "createdAt",
        render: (value: TimeValue) => (
          <Text className="text-xs leading-5 text-slate-500">{formatDateTime(value)}</Text>
        ),
      },
    ],
    [],
  );

  const dispatchJobColumns = useMemo<TableColumnsType<DispatchJobItem>>(
    () => [
      {
        title: "任务",
        key: "job",
        render: (_value, record) => (
          <div className="min-w-0 space-y-1">
            <Text
              strong
              className="block text-sm leading-6 text-slate-900"
              title={record.title || record.type || "通知派发任务"}
            >
              {record.title || record.type || "通知派发任务"}
            </Text>
            <Text type="secondary" className="block break-all text-xs" title={record.jobId}>
              {record.jobId}
            </Text>
            <Text
              type="secondary"
              className="block break-all text-xs"
              title={`用户 #${record.userId} · 通知 #${record.notificationId}`}
            >
              用户 #{record.userId} · 通知 #{record.notificationId}
            </Text>
          </div>
        ),
      },
      {
        title: "渠道与状态",
        key: "status",
        render: (_value, record) => (
          <div className="space-y-2">
            <Space size={[6, 6]} wrap>
              <Tag color={record.channel === "EMAIL" ? "gold" : "blue"}>
                {getLabel(record.channel, notificationChannelLabelMap, record.channel)}
              </Tag>
              {getDispatchStatusTag(record.status)}
            </Space>
            <Text type="secondary" className="block text-xs">
              尝试 {record.attemptCount} / {record.maxAttempts}
            </Text>
          </div>
        ),
      },
      {
        title: "错误摘要",
        key: "error",
        render: (_value, record) => (
          <div className="space-y-1">
            <Text
              type={record.errorCode ? "danger" : "secondary"}
              className="block break-all text-xs"
              title={record.errorCode || "—"}
            >
              {record.errorCode || "—"}
            </Text>
            <Text
              type="secondary"
              className="block break-all text-xs"
              title={record.errorMessage || "无错误信息"}
            >
              {record.errorMessage || "无错误信息"}
            </Text>
          </div>
        ),
      },
      {
        title: "时间",
        key: "time",
        render: (_value, record) => (
          <div className="space-y-1">
            <Text type="secondary" className="block text-xs">创建 {formatDateTime(record.createdAt)}</Text>
            <Text type="secondary" className="block text-xs">下次 {formatDateTime(record.nextRunAt)}</Text>
          </div>
        ),
      },
      {
        title: "操作",
        key: "action",
        render: (_value, record) => (
          <Space size={[4, 4]} wrap>
            <Button type="link" size="small" onClick={() => navigate(`/admin/users/${record.userId}`)}>
              用户详情
            </Button>
            {(record.status === "DEAD" || record.status === "SKIPPED") ? (
              <Button
                type="link"
                size="small"
                loading={retryingJobId === record.jobId}
                onClick={() => void handleRetryDispatchJob(record)}
              >
                重新入队
              </Button>
            ) : null}
          </Space>
        ),
      },
    ],
    [navigate, retryingJobId],
  );

  const dispatchQueueButton = (
    <Button
      className="!h-10 !rounded-2xl !border-slate-200 !bg-white !px-4 !font-semibold !text-slate-700 hover:!border-indigo-200 hover:!text-indigo-600"
      onClick={() => setDispatchDrawerOpen(true)}
    >
      <Workflow size={16} className="mr-2" />
      查看派发队列
    </Button>
  );

  return (
    <AdminPageFrame>
      <AdminPageHeader
        sectionLabel="NOTIFICATION OPS"
        title="通知运营"
        description="发布系统公告、查看通道状态与失败任务重投。"
        tone="teal"
        actions={(
          <Button
            className="!h-11 !rounded-2xl !border-slate-200 !px-5 !shadow-none"
            onClick={handleRefresh}
            disabled={refreshButtonLoading}
          >
            <RefreshCcw size={16} className={`mr-2 ${refreshButtonLoading ? "animate-spin" : ""}`} />
            刷新数据
          </Button>
        )}
      />

      {baseError ? <Alert type="error" showIcon className="rounded-[28px]" message={baseError} /> : null}

      <div className="grid grid-cols-1 gap-5 md:grid-cols-2 2xl:grid-cols-4">
        {summaryCards.map((item) => (
          <AdminMetricCard
            key={item.key}
            icon={item.icon}
            label={item.label}
            value={item.value}
            note={item.note}
            tone={item.tone}
            badge="CHANNEL"
          />
        ))}
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1.5fr)_420px] xl:items-start">
        <AdminSurfaceCard
          title={(
            <div className="flex items-center gap-3">
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-indigo-100 text-indigo-600">
                <Megaphone size={20} />
              </span>
              <span>发布系统公告</span>
            </div>
          )}
          description="填写标题、对象和正文后即可发布。"
          className="h-full"
        >
          <Form layout="vertical" form={publishForm}>
            <div className="grid gap-5 md:grid-cols-[minmax(0,1fr)_minmax(0,1.08fr)]">
              <Form.Item
                label={<span className="text-sm font-medium text-[#262626]">公告标题</span>}
                name="title"
                rules={[{ required: true, message: "请输入公告标题" }]}
                className="!mb-0"
              >
                <Input placeholder="例如：本周六晚 22:00 进行短时维护" maxLength={160} />
              </Form.Item>

              <div>
                <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_168px]">
                  <div>
                    <div className="mb-2 text-sm font-medium text-[#262626]">发送对象</div>
                    <Form.Item name="targetRoles" className="!mb-0">
                      <Checkbox.Group className="w-full">
                        <div className="grid gap-2 md:grid-cols-3">
                          {roleOptions.map((option) => (
                            <label
                              key={option.value}
                              className="flex h-10 items-center gap-2.5 rounded-[18px] border border-slate-200 bg-slate-50 px-3 text-sm font-medium text-slate-700"
                            >
                              <Checkbox value={option.value} />
                              <span>{option.label}</span>
                            </label>
                          ))}
                        </div>
                      </Checkbox.Group>
                    </Form.Item>
                  </div>

                  <div>
                    <div className="mb-2 text-sm font-medium text-[#262626]">邮件提醒</div>
                    <label className="flex h-10 items-center gap-2.5 rounded-[18px] border border-slate-200 bg-slate-50 px-3 text-sm font-medium text-slate-700">
                      <Form.Item name="emailRequested" valuePropName="checked" noStyle>
                        <Checkbox className="!m-0 !inline-flex !items-center !leading-none [&_.ant-checkbox]:!top-0 [&_.ant-checkbox+span]:!pl-2.5 [&_.ant-checkbox+span]:!text-sm [&_.ant-checkbox+span]:!font-medium [&_.ant-checkbox+span]:!leading-none [&_.ant-checkbox+span]:!text-slate-700">
                          同步邮件提醒
                        </Checkbox>
                      </Form.Item>
                    </label>
                  </div>
                </div>
              </div>
            </div>

            <Form.Item
              label={<span className="text-sm font-medium text-[#262626]">公告正文</span>}
              name="content"
              rules={[{ required: true, message: "请输入公告正文" }]}
            >
              <Input.TextArea rows={5} placeholder="说明影响范围、恢复预期与建议用户动作" maxLength={1000} />
            </Form.Item>

            <div className="mt-5 grid gap-4 md:grid-cols-3">
              <AdminMiniStat
                label="发送对象"
                value={(
                  <div>
                    <div className="text-sm font-semibold text-slate-900">{publishTargetSummary}</div>
                    <div className="mt-1 text-xs leading-6 text-slate-500">默认全角色</div>
                  </div>
                )}
                className="rounded-[24px] border border-slate-200/80 bg-slate-50/90"
              />
              <AdminMiniStat
                label="点击去向"
                value={(
                  <div>
                    <div className="flex items-center gap-2 text-sm font-semibold text-slate-900">
                      <Bell size={15} className="text-indigo-500" />
                      通知中心
                    </div>
                    <div className="mt-1 text-xs leading-6 text-slate-500">统一收件箱</div>
                  </div>
                )}
                className="rounded-[24px] border border-slate-200/80 bg-slate-50/90"
              />
              <AdminMiniStat
                label="邮件提醒"
                value={(
                  <div>
                    <div className="flex items-center gap-2 text-sm font-semibold text-slate-900">
                      <Mail size={15} className="text-emerald-500" />
                      {watchedAnnouncementEmailRequested ? "已开启" : "未开启"}
                    </div>
                    <div className="mt-1 text-xs leading-6 text-slate-500">
                      {watchedAnnouncementEmailRequested ? "追加邮件提醒" : "仅站内信"}
                    </div>
                  </div>
                )}
                className="rounded-[24px] border border-slate-200/80 bg-slate-50/90"
              />
            </div>

            <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-slate-200/70 pt-6">
              <Text type="secondary" className="text-sm leading-7 text-slate-500">
                发布后会同步更新公告历史与派发队列。
              </Text>
              <div className="flex flex-wrap gap-3">
                <Button
                  className="!h-11 !rounded-2xl !border-slate-200 !bg-white !px-5 !font-semibold !text-slate-600 hover:!border-indigo-200 hover:!text-indigo-600"
                  onClick={() => resetPublishForm()}
                >
                  清空重填
                </Button>
                <Button
                  type="primary"
                  loading={publishing}
                  className="!h-11 !rounded-2xl !border-none !bg-gradient-to-r !from-indigo-500 !to-sky-500 !px-5 !shadow-none"
                  onClick={() => void handlePublish()}
                >
                  <Send size={16} className="mr-2" />
                  立即发布系统公告
                </Button>
              </div>
            </div>
          </Form>
        </AdminSurfaceCard>

        <div className="space-y-6">
          <AdminSurfaceCard title="通道状态" description="各通道状态与任务分布。">
            <div className="space-y-4">
              <div className="grid grid-cols-1 gap-3">
                <div className="rounded-[24px] border border-slate-200 bg-slate-50 px-4 py-4">
                  <div className="text-[11px] font-bold text-slate-400">最近一次公告</div>
                  <div className="mt-2 text-sm font-semibold text-slate-900">{formatDateTime(overview?.lastAnnouncementAt ?? null)}</div>
                </div>
                <div className="rounded-[24px] border border-slate-200 bg-slate-50 px-4 py-4">
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <div className="text-sm font-semibold text-slate-900">WebSocket 通道</div>
                      <div className="mt-1 text-xs text-slate-500">站内实时派发健康度</div>
                    </div>
                    <Tag color={overview?.websocketReady ? "success" : "default"}>
                      {overview?.websocketReady ? "已启用" : "未启用"}
                    </Tag>
                  </div>
                </div>
                <div className="rounded-[24px] border border-slate-200 bg-slate-50 px-4 py-4">
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <div className="text-sm font-semibold text-slate-900">邮件通道</div>
                      <div className="mt-1 text-xs text-slate-500">{overview?.emailSender || "当前未配置发件地址"}</div>
                    </div>
                    <Tag color={overview?.emailReady ? "success" : "warning"}>
                      {overview?.emailReady ? "可发送" : "待配置"}
                    </Tag>
                  </div>
                </div>
              </div>

              <div className="space-y-3">
                {(overview?.channelStats ?? []).map((item) => (
                  <div key={item.channel} className="rounded-[24px] border border-slate-200 bg-white px-4 py-4 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
                    <div className="flex items-center justify-between gap-3">
                      <div className="text-sm font-semibold text-slate-900">
                        {getLabel(item.channel, notificationChannelLabelMap, item.channel)}
                      </div>
                      <Tag color={item.dead > 0 ? "warning" : "success"}>
                        总量 {formatCount(item.total)}
                      </Tag>
                    </div>
                    <div className="mt-3 grid grid-cols-2 gap-2 text-xs text-slate-500">
                      <div>已发送 {formatCount(item.sent)}</div>
                      <div>已确认 {formatCount(item.acked)}</div>
                      <div>等待重试 {formatCount(item.retryWait)}</div>
                      <div>死信 {formatCount(item.dead)}</div>
                    </div>
                  </div>
                ))}
              </div>

            </div>
          </AdminSurfaceCard>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6">
        <AdminSurfaceCard
          title="最近系统公告"
          description="查看公告记录、通知方式与目标角色。"
          extra={dispatchQueueButton}
        >
          <Table<AnnouncementItem>
            rowKey="eventId"
            loading={baseLoading}
            columns={announcementColumns}
            dataSource={announcements}
            pagination={{ pageSize: 6, showSizeChanger: false }}
            tableLayout="auto"
          />
        </AdminSurfaceCard>
      </div>

      {dispatchDrawerOpen ? (
        <Suspense fallback={null}>
          <AdminNotificationsDispatchDrawer
            context={{
              dispatchDrawerOpen,
              setDispatchDrawerOpen,
              handlePurgeDispatchJobs,
              purgingDispatchJobs,
              loadDispatchJobs,
              dispatchJobsLoading,
              dispatchStatusFilter,
              dispatchStatusOptions,
              setDispatchJobsPage,
              setDispatchStatusFilter,
              dispatchChannelFilter,
              channelOptions,
              setDispatchChannelFilter,
              dispatchJobsError,
              dispatchJobColumns,
              dispatchJobs,
              dispatchJobsPage,
              dispatchJobsPageSize,
              dispatchJobsTotal,
              setDispatchJobsPageSize,
            }}
          />
        </Suspense>
      ) : null}
    </AdminPageFrame>
  );
}
