import { Alert, Button, Modal, Table, Tag, Typography } from "antd";
import { Activity, RefreshCcw } from "lucide-react";
import { AdminWorkspaceModuleProps } from "../AdminLazyWorkspace";
import { AdminDetailPlaceholder, AdminMiniStat, AdminSurfaceCard, joinAdminClassNames } from "../AdminOpsPrimitives";

const { Text } = Typography;

export default function AdminContentAiTraceModal({ context }: AdminWorkspaceModuleProps<any>) {
  const {
    aiTraceModalOpen,
    setAiTraceModalOpen,
    activeAiTraceId,
    aiTraceLoading,
    loadAiTraceLogs,
    formatCount,
    aiTraceTotal,
    aiTraceError,
    aiTraceLogs,
    formatDateTime,
    getReadableCodeLabel,
    gatewayTaskTypeLabelMap,
    getAiLogStatusTag,
    selectedAiTraceLogId,
    setSelectedAiTraceLogId,
    selectedAiTraceLog,
    aiTraceDetailLoading,
    aiTraceDetailError,
    aiTraceDetail,
    formatCny,
    safePrettifyJson,
    openUserDetail,
  } = context;

  return (
    <Modal
      title={null}
      open={aiTraceModalOpen}
      onCancel={() => setAiTraceModalOpen(false)}
      footer={null}
      width={1280}
      centered
      destroyOnHidden
      rootClassName="admin-content-ai-modal"
      styles={{
        body: {
          padding: 0,
        },
      }}
    >
      <div className="overflow-hidden rounded-[28px] bg-white">
        <div className="border-b border-slate-200/80 px-8 py-7">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div className="space-y-2">
              <div className="flex items-center gap-3">
                <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600">
                  <Activity size={20} />
                </div>
                <div>
                  <div className="font-['Manrope'] text-[1.7rem] font-black tracking-[-0.04em] text-slate-950">关联生成记录</div>
                  <div className="text-sm leading-6 text-slate-500">
                    当前关联编号为 <span className="font-mono text-slate-700">{activeAiTraceId || "—"}</span>，可用来回看相关生成记录。
                  </div>
                </div>
              </div>
            </div>
            <Button
              disabled={aiTraceLoading}
              className="!h-11 !rounded-2xl !border-slate-200 !bg-white !px-5 !font-semibold !text-slate-700 shadow-sm"
              onClick={() => {
                if (activeAiTraceId) {
                  void loadAiTraceLogs(activeAiTraceId);
                }
              }}
            >
              <RefreshCcw size={16} className={aiTraceLoading ? "mr-2 animate-spin" : "mr-2"} />
              刷新日志
            </Button>
          </div>
        </div>

        <div className="grid gap-6 bg-slate-50/70 px-8 py-8 xl:grid-cols-[minmax(0,0.96fr)_minmax(380px,1.04fr)]">
          <AdminSurfaceCard title={`记录列表 · ${formatCount(aiTraceTotal)} 条`} description="按同一关联编号汇总最近记录。">
            {aiTraceError ? (
              <Alert type="error" showIcon className="rounded-2xl" message="关联记录加载失败" description={aiTraceError} />
            ) : (
              <Table
                className="admin-content-table"
                rowKey="id"
                loading={aiTraceLoading}
                dataSource={aiTraceLogs}
                pagination={false}
                columns={[
                  {
                    title: "时间",
                    dataIndex: "createdAt",
                    render: (value: string | null) => <Text type="secondary">{formatDateTime(value)}</Text>,
                  },
                  {
                    title: "记录概览",
                    key: "summary",
                    render: (_value: unknown, record: any) => (
                      <div className="space-y-1">
                        <div className="font-semibold text-slate-900">{getReadableCodeLabel(record.taskType, gatewayTaskTypeLabelMap, record.taskType)}</div>
                        <div className="text-xs text-slate-500">{record.provider} / {record.model}</div>
                        <div className="text-xs text-slate-400">{record.userDisplayName || `用户 #${record.userId}`}</div>
                      </div>
                    ),
                  },
                  {
                    title: "状态",
                    dataIndex: "status",
                    render: (status: string) => getAiLogStatusTag(status),
                  },
                  {
                    title: "耗时",
                    dataIndex: "latencyMs",
                    render: (value: number) => `${value} ms`,
                  },
                ]}
                tableLayout="auto"
                onRow={(record: any) => ({
                  onClick: () => setSelectedAiTraceLogId(record.id),
                })}
                rowClassName={(record: any) => joinAdminClassNames(
                  "cursor-pointer transition-colors hover:!bg-slate-50",
                  record.id === selectedAiTraceLogId && "admin-content-table-row-active",
                )}
                locale={{
                  emptyText: aiTraceLoading ? "正在读取记录..." : "当前关联编号下暂无可用记录",
                }}
                scroll={{ y: 460 }}
              />
            )}
          </AdminSurfaceCard>

          <AdminSurfaceCard title="记录详情" description="展示概要信息、结果摘要、处理记录和详细返回内容。">
            {!selectedAiTraceLog ? (
              <AdminDetailPlaceholder description="请选择左侧一条记录查看详情" />
            ) : aiTraceDetailLoading ? (
              <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-6 py-10 text-center text-slate-500">
                正在加载记录详情...
              </div>
            ) : aiTraceDetailError ? (
              <Alert type="error" showIcon className="rounded-2xl" message="记录详情加载失败" description={aiTraceDetailError} />
            ) : aiTraceDetail ? (
              <div className="space-y-5">
                <div className="rounded-[26px] bg-gradient-to-br from-[#0f172a] via-[#111827] to-[#334155] p-6 text-white shadow-[0_18px_36px_rgba(15,23,42,0.18)]">
                  <div className="font-['Manrope'] text-xl font-black tracking-tight text-white">{getReadableCodeLabel(aiTraceDetail.taskType, gatewayTaskTypeLabelMap, aiTraceDetail.taskType)}</div>
                  <div className="mt-2 font-mono text-xs  text-white/70">{aiTraceDetail.traceId}</div>
                  <div className="mt-4 flex flex-wrap gap-2">
                    {getAiLogStatusTag(aiTraceDetail.status)}
                    <Tag bordered={false} className="m-0 rounded-full bg-white/10 px-3 py-1 text-white">
                      {aiTraceDetail.provider}
                    </Tag>
                    <Tag bordered={false} className="m-0 rounded-full bg-white/10 px-3 py-1 text-white">
                      {aiTraceDetail.model}
                    </Tag>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <AdminMiniStat className="admin-content-mini-stat" label="关联用户" value={aiTraceDetail.userDisplayName || `用户 #${aiTraceDetail.userId}`} />
                  <AdminMiniStat className="admin-content-mini-stat" label="记录时间" value={formatDateTime(aiTraceDetail.createdAt)} />
                  <AdminMiniStat className="admin-content-mini-stat" label="耗时" value={`${aiTraceDetail.latencyMs} ms`} />
                  <AdminMiniStat className="admin-content-mini-stat" label="总用量（Token）" value={formatCount(aiTraceDetail.totalTokens)} />
                  <AdminMiniStat className="admin-content-mini-stat" label="推理用量（Token）" value={formatCount(aiTraceDetail.thoughtsTokens)} />
                  <AdminMiniStat className="admin-content-mini-stat" label="预估成本（元）" value={formatCny(aiTraceDetail.estimatedCost)} />
                </div>

                <div className="rounded-[26px] border border-slate-200 bg-slate-50/80 px-4 py-4">
                  <div className="text-[11px] font-bold text-slate-400">结果摘要</div>
                  <div className="mt-3 text-sm leading-7 text-slate-600">{aiTraceDetail.resultSummary || "暂无结果摘要"}</div>
                  <div className="mt-4 flex flex-wrap gap-2">
                    <Tag bordered={false} className="m-0 rounded-full bg-white px-3 py-1 text-slate-600">
                      治理留痕 {formatCount(aiTraceDetail.governanceTraceSummary.auditCount)}
                    </Tag>
                    {aiTraceDetail.governanceTraceSummary.recentActionTypes.map((action: string) => (
                      <Tag key={action} bordered={false} className="m-0 rounded-full bg-white px-3 py-1 text-slate-600">
                        {action}
                      </Tag>
                    ))}
                  </div>
                </div>

                <div className="rounded-[26px] border border-slate-200 bg-slate-50/80 px-4 py-4">
                  <div className="text-[11px] font-bold text-slate-400">返回详情</div>
                  <pre className="mt-3 overflow-auto rounded-2xl bg-slate-900 px-4 py-4 text-xs leading-6 text-slate-100">
                    {safePrettifyJson(aiTraceDetail.resultPayloadJson || "{}")}
                  </pre>
                </div>

                <Button
                  block
                  className="!h-11 !rounded-2xl !border-slate-200"
                  onClick={() => openUserDetail(aiTraceDetail.userId)}
                >
                  查看关联用户详情
                </Button>
              </div>
            ) : (
              <AdminDetailPlaceholder description="当前日志还没有更多可展示的详情字段" />
            )}
          </AdminSurfaceCard>
        </div>
      </div>
    </Modal>
  );
}
