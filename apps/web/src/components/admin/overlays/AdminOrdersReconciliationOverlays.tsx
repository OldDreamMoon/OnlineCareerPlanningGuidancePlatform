import { Alert, Button, Descriptions, Drawer, Empty, Form, Input, Modal, Select, Skeleton, Space, Table, Tag, Timeline, Typography } from "antd";
import { AdminSurfaceCard } from "../AdminOpsPrimitives";
import { afterSalesRequestTypeLabelMap, getLabel, orderStatusLabelMap, paymentChannelLabelMap, paymentModeLabelMap, paymentStatusLabelMap, reconciliationActionLabelMap, reconciliationIssueTagLabelMap, reconciliationStatusLabelMap } from "../../../lib/adminLabels";
import { formatCount, formatDateTime, formatMoneyFen } from "../../../lib/formatters";
import type { AdminWorkspaceModuleProps } from "../AdminLazyWorkspace";

const { Paragraph, Text } = Typography;
const { TextArea } = Input;

export default function AdminOrdersReconciliationOverlays({ context }: AdminWorkspaceModuleProps<any>) {
  const {
    afterSalesReviewOpen,
    setAfterSalesReviewOpen,
    handleSubmitAfterSalesReview,
    afterSalesReviewSaving,
    currentAfterSalesRecord,
    afterSalesReviewForm,
    orderDetailOpen,
    setOrderDetailOpen,
    setOrderDetail,
    setCurrentAfterSalesRecord,
    orderDetail,
    orderDetailLoading,
    mentorReplyOverdue,
    orderLifecycleTimelineItems,
    navigate,
    handleQueryTrade,
    handleCloseTrade,
    handleQueryRefund,
    refundForm,
    refundOpen,
    setRefundOpen,
    handleManualRefund,
    refundSaving,
    getAfterSalesStatusTag,
    reconciliationDetailOpen,
    setReconciliationDetailOpen,
    reconciliationDetailLoading,
    reconciliationDetail,
    handleOpenManualAction,
    paymentRecordColumns,
    reconciliationTimelineItems,
    currentRecommendedAction,
    handleActionOpen,
    setHandleActionOpen,
    handleSubmitManualAction,
    handleActionSaving,
    handleActionForm,
    gatewayResultOpen,
    setGatewayResultOpen,
    gatewayResultTitle,
    gatewayResultContent,
  } = context;

  return (
    <>
      <Modal
        open={afterSalesReviewOpen}
        title="审核售后申请"
        onCancel={() => setAfterSalesReviewOpen(false)}
        onOk={() => void handleSubmitAfterSalesReview()}
        okText="提交审核"
        cancelText="取消"
        okButtonProps={{ loading: afterSalesReviewSaving, className: "!bg-indigo-600" }}
      >
        {currentAfterSalesRecord ? (
          <div className="mb-4 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
            <div className="font-semibold text-slate-900">#{currentAfterSalesRecord.requestId} · {currentAfterSalesRecord.orderNo}</div>
            <div className="mt-2 text-sm text-slate-500">{currentAfterSalesRecord.reason}</div>
          </div>
        ) : null}

        <Form form={afterSalesReviewForm} layout="vertical">
          <Form.Item label="审核结论" name="decision" rules={[{ required: true, message: "请选择审核结论" }]}>
            <Select
              options={[
                { label: "同意退款 / 售后", value: "approve" },
                { label: "拒绝申请", value: "reject" },
              ]}
            />
          </Form.Item>
          <Form.Item label="审核备注" name="reviewNote">
            <TextArea rows={4} maxLength={1000} showCount placeholder="填写审核依据、沟通记录或补充说明" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={orderDetailOpen}
        title="订单详情与售后处理"
        width={1200}
        centered
        destroyOnClose
        footer={null}
        onCancel={() => {
          setOrderDetailOpen(false);
          setOrderDetail(null);
          setCurrentAfterSalesRecord(null);
        }}
        styles={{ body: { maxHeight: "78vh", overflowY: "auto", paddingTop: 12 } }}
      >
        {orderDetail ? (
          <div className="space-y-5">
            {orderDetailLoading ? (
              <Alert
                type="info"
                showIcon
                message="订单详情正在刷新"
                description="已保留当前内容，最新支付与退款状态将在刷新完成后自动更新。"
              />
            ) : null}

            <div className="grid gap-4 md:grid-cols-3">
              <div className="rounded-[24px] border border-indigo-100 bg-indigo-50 px-5 py-5">
                <div className="text-xs text-indigo-600">订单金额</div>
                <div className="mt-2 font-['Manrope'] text-[1.9rem] font-black text-indigo-950">{formatMoneyFen(orderDetail.amountFen)}</div>
              </div>
              <div className="rounded-[24px] border border-emerald-100 bg-emerald-50 px-5 py-5">
                <div className="text-xs text-emerald-600">订单状态</div>
                <div className="mt-2 font-['Manrope'] text-[1.9rem] font-black text-emerald-950">{getLabel(orderDetail.status, orderStatusLabelMap, orderDetail.status || "—")}</div>
              </div>
              <div className="rounded-[24px] border border-amber-100 bg-amber-50 px-5 py-5">
                <div className="text-xs text-amber-600">支付模式</div>
                <div className="mt-2 font-['Manrope'] text-[1.9rem] font-black text-amber-950">{getLabel(orderDetail.paymentMode, paymentModeLabelMap, orderDetail.paymentMode || "—")}</div>
              </div>
            </div>

            {mentorReplyOverdue ? (
              <Alert
                type="warning"
                showIcon
                message="导师回复时限已超出"
                description="订单仍处于已支付状态，建议结合售后结论继续核对履约链路，必要时再执行手工退款。"
              />
            ) : null}

            <div className="mt-5 grid gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
              <div className="space-y-5">
                <AdminSurfaceCard title="订单基本信息" description="集中查看订单主体、用户身份和关键时间。">
                  <Descriptions bordered size="small" column={2}>
                    <Descriptions.Item label="订单号" span={2}>{orderDetail.orderNo}</Descriptions.Item>
                    <Descriptions.Item label="学生">{orderDetail.studentDisplayName}</Descriptions.Item>
                    <Descriptions.Item label="导师">{orderDetail.mentorDisplayName}</Descriptions.Item>
                    <Descriptions.Item label="预约开始">{formatDateTime(orderDetail.appointmentStartAt)}</Descriptions.Item>
                    <Descriptions.Item label="预约结束">{formatDateTime(orderDetail.appointmentEndAt)}</Descriptions.Item>
                    <Descriptions.Item label="创建时间">{formatDateTime(orderDetail.createdAt)}</Descriptions.Item>
                    <Descriptions.Item label="支付时间">{formatDateTime(orderDetail.paidAt)}</Descriptions.Item>
                    <Descriptions.Item label="回复时限" span={2}>{formatDateTime(orderDetail.mentorReplyDeadlineAt)}</Descriptions.Item>
                  </Descriptions>
                  <div className="mt-4 flex flex-wrap gap-3">
                    <Button className="!rounded-xl !border-slate-200" onClick={() => navigate(`/admin/users/${orderDetail.studentUserId}`)}>
                      查看学生详情
                    </Button>
                    <Button className="!rounded-xl !border-slate-200" onClick={() => navigate(`/admin/users/${orderDetail.mentorUserId}`)}>
                      查看导师详情
                    </Button>
                  </div>
                </AdminSurfaceCard>

                <AdminSurfaceCard title="咨询问题" description="用于辅助判断是否属于履约争议、误拍或服务未达成。">
                  <Paragraph className="!mb-0 !whitespace-pre-wrap !text-sm !leading-7 !text-slate-500">
                    {orderDetail.questionText || "暂无问题描述"}
                  </Paragraph>
                </AdminSurfaceCard>

                <AdminSurfaceCard title="订单生命周期时间线" description="查看订单从创建到完成处理的关键节点。">
                  {orderLifecycleTimelineItems.length === 0 ? (
                    <Empty description="暂无可展示的订单生命周期节点" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  ) : (
                    <Timeline items={orderLifecycleTimelineItems} />
                  )}
                </AdminSurfaceCard>
              </div>

              <div className="space-y-5">
                <AdminSurfaceCard title="处理操作" description="在这里继续完成退款与支付状态核验。">
                  <div className="flex flex-wrap gap-3">
                    <Button type="primary" danger className="!rounded-xl" onClick={() => { refundForm.resetFields(); setRefundOpen(true); }}>
                      手工退款
                    </Button>
                    {orderDetail.paymentMode === "SANDBOX" ? (
                      <>
                        <Button className="!rounded-xl !border-slate-200" onClick={() => void handleQueryTrade()}>查询交易</Button>
                        <Button className="!rounded-xl !border-slate-200" onClick={() => void handleCloseTrade()}>关闭交易</Button>
                        <Button className="!rounded-xl !border-slate-200" onClick={() => void handleQueryRefund()}>查询退款</Button>
                      </>
                    ) : null}
                  </div>
                </AdminSurfaceCard>

                {orderDetail.payment ? (
                  <AdminSurfaceCard title="最近支付记录" description="查看最近一次支付状态与交易信息。">
                    <Descriptions size="small" column={1}>
                      <Descriptions.Item label="渠道 / 模式">
                        {getLabel(orderDetail.payment.channel, paymentChannelLabelMap, orderDetail.payment.channel || "—")} / {getLabel(orderDetail.payment.mode, paymentModeLabelMap, orderDetail.payment.mode || "—")}
                      </Descriptions.Item>
                      <Descriptions.Item label="支付状态">{getLabel(orderDetail.payment.status, paymentStatusLabelMap, orderDetail.payment.status || "—")}</Descriptions.Item>
                      <Descriptions.Item label="交易号">{orderDetail.payment.providerTradeNo || "—"}</Descriptions.Item>
                      <Descriptions.Item label="记录时间">{formatDateTime(orderDetail.payment.recordedAt)}</Descriptions.Item>
                    </Descriptions>
                  </AdminSurfaceCard>
                ) : null}

                {orderDetail.refund ? (
                  <AdminSurfaceCard title="退款处理摘要" description="查看退款结果及相关处理影响。">
                    <Descriptions size="small" column={1}>
                      <Descriptions.Item label="处理原因">{orderDetail.refund.reason || "—"}</Descriptions.Item>
                      <Descriptions.Item label="前序状态">{getLabel(orderDetail.refund.previousStatus, orderStatusLabelMap, orderDetail.refund.previousStatus || "—")}</Descriptions.Item>
                      <Descriptions.Item label="处理时间">{formatDateTime(orderDetail.refund.processedAt)}</Descriptions.Item>
                      <Descriptions.Item label="释放席位 / 删除评价">
                        {orderDetail.refund.slotReleased ? "是" : "否"} / {orderDetail.refund.reviewRemoved ? "是" : "否"}
                      </Descriptions.Item>
                    </Descriptions>
                  </AdminSurfaceCard>
                ) : null}

                <AdminSurfaceCard title="售后申请时间线" description="查看该订单历次售后申请与处理结果。">
                  {orderDetail.afterSalesRequests.length === 0 ? (
                    <Empty description="该订单暂无售后申请历史" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  ) : (
                    <Timeline
                      items={orderDetail.afterSalesRequests.map((item: any) => ({
                        color: item.status === "APPROVED" ? "green" : item.status === "REJECTED" ? "gray" : "blue",
                        children: (
                          <div className="pb-3">
                            <Space size={8} wrap>
                              <Text strong>#{item.id}</Text>
                              <Tag>{getLabel(item.requestType, afterSalesRequestTypeLabelMap, item.requestType)}</Tag>
                              {getAfterSalesStatusTag(item.status)}
                              {item.autoTriggered ? <Tag color="warning">自动触发</Tag> : null}
                            </Space>
                            <Paragraph className="!mb-1 !mt-2 !text-sm !leading-6 !text-slate-500">{item.reason}</Paragraph>
                            <Text type="secondary" className="text-xs">
                              提交 {formatDateTime(item.createdAt)} · 审核 {formatDateTime(item.reviewedAt)}
                            </Text>
                          </div>
                        ),
                      }))}
                    />
                  )}
                </AdminSurfaceCard>
              </div>
            </div>
          </div>
        ) : (
          <Empty description="暂无订单详情" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </Modal>

      <Modal
        open={refundOpen}
        title="手工退款"
        onCancel={() => setRefundOpen(false)}
        onOk={() => void handleManualRefund()}
        okText="确认退款"
        cancelText="取消"
        okButtonProps={{ loading: refundSaving, className: "!bg-indigo-600" }}
      >
        <Form form={refundForm} layout="vertical">
          <Form.Item label="退款原因" name="reason" rules={[{ required: true, message: "请输入退款原因" }]}>
            <TextArea rows={4} maxLength={1000} showCount placeholder="例如：服务未履约 / 沟通异常 / 管理员人工纠偏" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        open={reconciliationDetailOpen}
        width={720}
        title="对账详情"
        onClose={() => setReconciliationDetailOpen(false)}
      >
        {reconciliationDetailLoading ? (
          <div className="space-y-4 py-4">
            <Skeleton active paragraph={{ rows: 4 }} />
            <Skeleton active paragraph={{ rows: 4 }} />
            <Skeleton active paragraph={{ rows: 6 }} />
          </div>
        ) : reconciliationDetail ? (
          <div>
            <div className="grid grid-cols-3 gap-3">
              <div className="rounded-[20px] border border-indigo-100 bg-indigo-50 px-4 py-4">
                <div className="text-xs text-indigo-600">订单金额</div>
                <div className="mt-2 font-['Manrope'] text-xl font-black text-indigo-950">{formatMoneyFen(reconciliationDetail.amountFen)}</div>
              </div>
              <div className="rounded-[20px] border border-amber-100 bg-amber-50 px-4 py-4">
                <div className="text-xs text-amber-600">支付状态</div>
                <div className="mt-2 font-['Manrope'] text-xl font-black text-amber-950">
                  {getLabel(reconciliationDetail.latestPaymentStatus, paymentStatusLabelMap, reconciliationDetail.latestPaymentStatus || "—")}
                </div>
              </div>
              <div className="rounded-[20px] border border-rose-100 bg-rose-50 px-4 py-4">
                <div className="text-xs text-rose-600">对账状态</div>
                <div className="mt-2 font-['Manrope'] text-xl font-black text-rose-950">
                  {getLabel(reconciliationDetail.reconciliationStatus, reconciliationStatusLabelMap, reconciliationDetail.reconciliationStatus)}
                </div>
              </div>
            </div>

            <div className="mt-5">
              <Descriptions bordered size="small" column={1}>
                <Descriptions.Item label="订单号">{reconciliationDetail.orderNo}</Descriptions.Item>
                <Descriptions.Item label="学生 / 导师">{reconciliationDetail.studentDisplayName} / {reconciliationDetail.mentorDisplayName}</Descriptions.Item>
                <Descriptions.Item label="订单状态">{getLabel(reconciliationDetail.orderStatus, orderStatusLabelMap, reconciliationDetail.orderStatus)}</Descriptions.Item>
                <Descriptions.Item label="支付模式 / 渠道">
                  {getLabel(reconciliationDetail.paymentMode, paymentModeLabelMap, reconciliationDetail.paymentMode || "—")} / {getLabel(reconciliationDetail.paymentChannel, paymentChannelLabelMap, reconciliationDetail.paymentChannel || "—")}
                </Descriptions.Item>
                <Descriptions.Item label="交易号">{reconciliationDetail.providerTradeNo || "—"}</Descriptions.Item>
                <Descriptions.Item label="创建 / 支付 / 关闭">
                  {formatDateTime(reconciliationDetail.createdAt)} / {formatDateTime(reconciliationDetail.paidAt)} / {formatDateTime(reconciliationDetail.closedAt)}
                </Descriptions.Item>
              </Descriptions>
            </div>

            <div className="mt-5 flex flex-wrap gap-3">
              <Button
                className="!rounded-xl !border-indigo-200 !bg-indigo-50 !text-indigo-700 hover:!border-indigo-300 hover:!bg-indigo-100"
                onClick={() => navigate(`/admin/users/${reconciliationDetail.studentUserId}`)}
              >
                查看学生详情
              </Button>
              <Button
                className="!rounded-xl !border-emerald-200 !bg-emerald-50 !text-emerald-700 hover:!border-emerald-300 hover:!bg-emerald-100"
                onClick={() => navigate(`/admin/users/${reconciliationDetail.mentorUserId}`)}
              >
                查看导师详情
              </Button>
            </div>

            {reconciliationDetail.issueTags.length > 0 ? (
              <div className="mt-5">
                <Alert
                  type="warning"
                  showIcon
                  message="异常标签"
                  description={
                    <div className="flex flex-wrap gap-2">
                      {reconciliationDetail.issueTags.map((tag: string) => (
                        <Tag key={tag} color="error">
                          {getLabel(tag, reconciliationIssueTagLabelMap, tag)}
                        </Tag>
                      ))}
                    </div>
                  }
                />
              </div>
            ) : null}

            <div className="mt-5">
              <AdminSurfaceCard title="订单与对账时间线" description="快速了解订单、支付与处理记录的先后关系。">
                {reconciliationTimelineItems.length === 0 ? (
                  <Empty description="暂无可展示的对账时间线" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                ) : (
                  <Timeline items={reconciliationTimelineItems} />
                )}
              </AdminSurfaceCard>
            </div>

            <div className="mt-5">
              <AdminSurfaceCard title="建议处理动作" description="根据当前异常情况给出建议，确认后可直接提交。">
                <div className="flex flex-wrap gap-3">
                  {reconciliationDetail.recommendedActions.length > 0 ? reconciliationDetail.recommendedActions.map((action: string) => (
                    <Button
                      key={action}
                      type="primary"
                      className="!rounded-xl !border-none !bg-indigo-600"
                      onClick={() => handleOpenManualAction(action)}
                    >
                      {getLabel(action, reconciliationActionLabelMap, action)}
                    </Button>
                  )) : <Text type="secondary">当前没有可执行动作</Text>}
                </div>
              </AdminSurfaceCard>
            </div>

            {reconciliationDetail.latestManualHandling ? (
              <div className="mt-5">
                <AdminSurfaceCard title="最近一次处理记录" description="便于继续跟进当前订单的处理进度。">
                  <Descriptions size="small" column={1}>
                    <Descriptions.Item label="动作">
                      {getLabel(reconciliationDetail.latestManualHandling.action, reconciliationActionLabelMap, reconciliationDetail.latestManualHandling.action)}
                    </Descriptions.Item>
                    <Descriptions.Item label="备注">{reconciliationDetail.latestManualHandling.note}</Descriptions.Item>
                    <Descriptions.Item label="操作时间">{formatDateTime(reconciliationDetail.latestManualHandling.processedAt)}</Descriptions.Item>
                  </Descriptions>
                </AdminSurfaceCard>
              </div>
            ) : null}

            <div className="mt-5">
              <AdminSurfaceCard title="支付记录" description="集中查看本次订单的支付流水与关键标识。">
                <Table
                  rowKey={(record: any) => `${record.createdAt}-${record.providerTradeNo}-${record.status}`}
                  dataSource={reconciliationDetail.paymentRecords}
                  columns={paymentRecordColumns}
                  size="small"
                  tableLayout="auto"
                  pagination={false}
                  locale={{ emptyText: <Empty description="暂无支付记录" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
                />
              </AdminSurfaceCard>
            </div>

            <div className="mt-5">
              <AdminSurfaceCard title="问题说明" description="查看用户提交的问题说明，辅助判断。">
                <Paragraph className="!mb-0 !whitespace-pre-wrap !text-sm !leading-7 !text-slate-500">
                  {reconciliationDetail.questionText || "暂无订单问题描述"}
                </Paragraph>
              </AdminSurfaceCard>
            </div>
          </div>
        ) : (
          <Empty description="暂无对账详情" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </Drawer>

      <Modal
        open={handleActionOpen}
        title="确认处理动作"
        onCancel={() => setHandleActionOpen(false)}
        onOk={() => void handleSubmitManualAction()}
        okText="确认处理"
        cancelText="取消"
        okButtonProps={{ loading: handleActionSaving, className: "!bg-indigo-600" }}
      >
        <Form form={handleActionForm} layout="vertical">
          <Form.Item label="处理动作" name="action" rules={[{ required: true, message: "请选择处理动作" }]}>
            <Select
              options={(reconciliationDetail?.recommendedActions ?? (currentRecommendedAction ? [currentRecommendedAction] : [])).map((action: string) => ({
                label: getLabel(action, reconciliationActionLabelMap, action),
                value: action,
              }))}
            />
          </Form.Item>
          <Form.Item label="处理备注" name="note" rules={[{ required: true, message: "请输入处理备注" }]}>
            <TextArea rows={4} maxLength={1000} showCount placeholder="记录为何执行该动作、核对依据与后续影响" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={gatewayResultOpen}
        width={760}
        title={gatewayResultTitle}
        onCancel={() => setGatewayResultOpen(false)}
        footer={(
          <Button type="primary" className="!rounded-xl !border-none !bg-indigo-600" onClick={() => setGatewayResultOpen(false)}>
            我知道了
          </Button>
        )}
      >
        <div className="overflow-hidden rounded-3xl bg-slate-950 px-4 py-4 text-xs text-emerald-300">
          <pre className="m-0 overflow-x-auto whitespace-pre-wrap break-all">{gatewayResultContent}</pre>
        </div>
      </Modal>
    </>
  );
}
