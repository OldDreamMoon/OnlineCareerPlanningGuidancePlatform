import { Alert, Button, Drawer, Popconfirm, Select, Space, Table, Typography } from "antd";
import { RefreshCcw } from "lucide-react";
import { AdminWorkspaceModuleProps } from "../AdminLazyWorkspace";

const { Text } = Typography;

export default function AdminNotificationsDispatchDrawer({ context }: AdminWorkspaceModuleProps<any>) {
  const {
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
  } = context;

  return (
    <Drawer
      title={(
        <div className="flex flex-wrap items-center gap-3 pr-4">
          <span>派发任务队列</span>
          <Popconfirm
            title="清理旧终态任务"
            description="会删除已结束的旧派发任务及其尝试记录，保留仍在执行中的任务。"
            okText="确认清理"
            cancelText="取消"
            placement="bottomLeft"
            onConfirm={() => void handlePurgeDispatchJobs()}
          >
            <Button
              danger
              className="!h-10 !rounded-2xl !px-4 !font-semibold"
              loading={purgingDispatchJobs}
            >
              清理旧任务
            </Button>
          </Popconfirm>
        </div>
      )}
      placement="right"
      width={1120}
      open={dispatchDrawerOpen}
      onClose={() => setDispatchDrawerOpen(false)}
      extra={(
        <Space wrap size={[8, 8]}>
          <Button
            className="!h-10 !rounded-2xl !border-slate-200 !bg-white !px-4 !font-semibold !text-slate-700 hover:!border-indigo-200 hover:!text-indigo-600"
            onClick={() => void loadDispatchJobs()}
            disabled={dispatchJobsLoading}
          >
            <RefreshCcw size={16} className={`mr-2 ${dispatchJobsLoading ? "animate-spin" : ""}`} />
            刷新队列
          </Button>
        </Space>
      )}
    >
      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-3 rounded-[24px] border border-slate-200 bg-slate-50 px-4 py-4">
          <Select
            className="min-w-[180px]"
            value={dispatchStatusFilter}
            options={dispatchStatusOptions}
            onChange={(value) => {
              setDispatchJobsPage(1);
              setDispatchStatusFilter(value);
            }}
          />
          <Select
            className="min-w-[160px]"
            value={dispatchChannelFilter}
            options={channelOptions}
            onChange={(value) => {
              setDispatchJobsPage(1);
              setDispatchChannelFilter(value);
            }}
          />
          <Text type="secondary" className="text-xs">
            仅对死信和可重试任务开放人工重投。
          </Text>
        </div>

        {dispatchJobsError ? <Alert type="error" showIcon className="rounded-2xl" message={dispatchJobsError} /> : null}

        <Table
          rowKey="jobId"
          tableLayout="auto"
          size="middle"
          loading={dispatchJobsLoading}
          columns={dispatchJobColumns}
          dataSource={dispatchJobs}
          pagination={{
            current: dispatchJobsPage,
            pageSize: dispatchJobsPageSize,
            total: dispatchJobsTotal,
            showSizeChanger: true,
            pageSizeOptions: ["10", "20", "50"],
            onChange: (nextPage, nextPageSize) => {
              setDispatchJobsPage(nextPage);
              setDispatchJobsPageSize(nextPageSize);
            },
          }}
        />
      </div>
    </Drawer>
  );
}
