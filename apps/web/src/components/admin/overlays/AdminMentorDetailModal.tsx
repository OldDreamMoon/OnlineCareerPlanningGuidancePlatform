import { Modal, Popconfirm } from "antd";
import {
  AlertTriangle,
  Banknote,
  Briefcase,
  Calendar,
  CalendarCheck,
  CheckCircle2,
  ChevronRight,
  History,
  Package,
  RefreshCcw,
  ShieldCheck,
  Star,
  User,
  XCircle,
} from "lucide-react";
import AdminIdentityAvatar from "../AdminIdentityAvatar";
import { AdminDetailPlaceholder } from "../AdminOpsPrimitives";
import { getLabel, riskLevelLabelMap } from "../../../lib/adminLabels";
import { formatCount, formatDateTime, formatMoneyFen, formatRelativeTime } from "../../../lib/formatters";
import type { AdminWorkspaceModuleProps } from "../AdminLazyWorkspace";

function joinClassNames(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

export default function AdminMentorDetailModal({ context }: AdminWorkspaceModuleProps<any>) {
  const {
    detailModalOpen,
    setDetailModalOpen,
    selectedRecord,
    getApprovalTag,
    getAvailabilityTag,
    buildMentorIdentityLine,
    selectedFulfillmentRate,
    getRiskSignalTag,
    handleRefresh,
    refreshButtonLoading,
    navigate,
    selectedWithdrawalActions,
    handleManageWithdrawal,
    formatRating,
  } = context;

  return (
    <Modal
      open={detailModalOpen}
      onCancel={() => setDetailModalOpen(false)}
      footer={null}
      width={1220}
      centered
      destroyOnHidden={false}
      title={null}
      styles={{
        body: { padding: 0 },
        content: {
          padding: 0,
          background: "transparent",
          boxShadow: "none",
        },
      }}
    >
      {!selectedRecord ? (
        <div className="rounded-[32px] bg-white px-8 py-10 shadow-[0_24px_80px_rgba(15,23,42,0.12)]">
          <AdminDetailPlaceholder description="请选择一位导师查看经营详情" />
        </div>
      ) : (
        <div className="rounded-[36px] bg-[#f8f9fc] p-4 shadow-[0_24px_80px_rgba(15,23,42,0.12)] md:p-5">
          <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
            <div className="space-y-6 xl:col-span-2">
              <section className="relative overflow-hidden rounded-[30px] border border-slate-100 bg-white p-7 shadow-sm">
                <div className="pointer-events-none absolute -right-24 -top-24 h-72 w-72 rounded-full bg-indigo-50/80 blur-3xl" />
                <div className="relative z-10 flex flex-col gap-6 xl:flex-row xl:items-center xl:justify-between">
                  <div className="flex min-w-0 flex-1 items-start gap-5">
                    <div className="relative shrink-0">
                      <AdminIdentityAvatar
                        role="MENTOR"
                        userId={selectedRecord.mentorUserId}
                        displayName={selectedRecord.displayName}
                        mentorAvatarUrl={selectedRecord.avatarUrl}
                        className="!h-28 !w-28 !min-w-28 overflow-hidden !rounded-[28px] border-4 border-white bg-indigo-50 shadow-[0_18px_36px_rgba(99,102,241,0.16)]"
                        imageClassName="!rounded-[24px]"
                        textClassName="text-3xl font-black text-indigo-700"
                      />
                    </div>
                    <div className="min-w-0 flex-1 pt-1">
                      <div className="font-['Manrope'] text-[2.1rem] font-black tracking-[-0.04em] text-slate-950">
                        {selectedRecord.displayName}
                      </div>
                      <div className="mt-3 flex flex-wrap gap-2">
                        {getApprovalTag(selectedRecord.approvalStatus)}
                        {getAvailabilityTag(selectedRecord.available)}
                      </div>
                      <div className="mt-4 text-lg font-semibold text-slate-700">
                        {buildMentorIdentityLine(selectedRecord)}
                      </div>
                      <div className="mt-1 text-base font-semibold text-indigo-600">
                        {selectedRecord.jobTitle || "未补充职位"}
                      </div>
                      <div className="mt-5 flex flex-wrap items-center gap-3">
                        <div className="inline-flex items-center gap-2 rounded-xl border border-rose-100 bg-rose-50 px-4 py-2 text-xs font-bold tracking-[0.14em] text-rose-600">
                          <AlertTriangle size={16} />
                          风险等级：{selectedRecord.highestRiskLevel ? getLabel(selectedRecord.highestRiskLevel, riskLevelLabelMap, selectedRecord.highestRiskLevel) : "当前平稳"}
                        </div>
                        <div className="inline-flex items-center gap-2 text-sm font-medium text-slate-500">
                          <History size={16} />
                          最近更新 {formatRelativeTime(selectedRecord.profileUpdatedAt, "暂无")}
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="flex shrink-0 gap-4">
                    <div className="flex h-32 min-w-[9.5rem] flex-col items-center justify-center rounded-[28px] bg-indigo-600 px-7 text-white shadow-[0_18px_38px_rgba(79,70,229,0.22)]">
                      <div className="text-[13px] font-black text-indigo-100">起步价格</div>
                      <div className="mt-2 font-['Manrope'] text-[2.2rem] font-black tracking-[-0.05em]">
                        {formatMoneyFen(selectedRecord.startingPriceFen)}
                      </div>
                    </div>
                    <div className="flex h-32 min-w-[8.75rem] flex-col items-center justify-center rounded-[28px] border border-indigo-100 bg-white px-6 shadow-sm">
                      <div className="text-[13px] font-black text-indigo-500">当前评分</div>
                      <div className="mt-2 flex items-center gap-1 font-['Manrope'] text-[2.2rem] font-black tracking-[-0.05em] text-slate-950">
                        {formatRating(selectedRecord.avgRating)}
                        <Star size={18} className="fill-amber-400 text-amber-400" />
                      </div>
                    </div>
                  </div>
                </div>
              </section>

              <section className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                {[
                  {
                    key: "latest-order-activity",
                    icon: History,
                    iconClassName: "bg-indigo-50 text-indigo-600",
                    label: "最近活动",
                    value: formatRelativeTime(selectedRecord.latestOrderActivityAt, "暂无"),
                  },
                  {
                    key: "next-available",
                    icon: Calendar,
                    iconClassName: "bg-emerald-50 text-emerald-600",
                    label: "下次可约",
                    value: formatDateTime(selectedRecord.nextAvailableAt, "未排期"),
                  },
                  {
                    key: "next-booking",
                    icon: CalendarCheck,
                    iconClassName: "bg-amber-50 text-amber-600",
                    label: "下次已预定",
                    value: formatDateTime(selectedRecord.nextBookedAt, "暂无"),
                  },
                  {
                    key: "profile-updated",
                    icon: RefreshCcw,
                    iconClassName: "bg-slate-100 text-slate-700",
                    label: "资料更新",
                    value: formatDateTime(selectedRecord.profileUpdatedAt, "暂无更新"),
                  },
                ].map((item) => (
                  <div key={item.key} className="flex h-36 flex-col justify-between rounded-[28px] border border-slate-100 bg-white p-5 shadow-sm">
                    <div className={joinClassNames("flex h-10 w-10 items-center justify-center rounded-xl", item.iconClassName)}>
                      <item.icon size={18} />
                    </div>
                    <div>
                      <div className="text-[13px] font-black text-slate-500">{item.label}</div>
                      <div className="mt-2 text-[15px] font-semibold leading-6 text-slate-800">{item.value}</div>
                    </div>
                  </div>
                ))}
              </section>

              <section className="grid grid-cols-1 gap-6 lg:grid-cols-2">
                <div className="rounded-[30px] border border-slate-100 bg-white p-7 shadow-sm">
                  <div className="mb-6 flex items-center gap-3">
                    <Briefcase className="text-indigo-600" size={22} />
                    <h3 className="font-['Manrope'] text-xl font-black tracking-tight text-slate-950">服务版图</h3>
                  </div>

                  <div className="flex flex-wrap gap-2">
                    {selectedRecord.serviceScenes.length > 0
                      ? selectedRecord.serviceScenes.map((item: string) => (
                        <span key={`${selectedRecord.mentorUserId}-${item}`} className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-2 text-sm font-semibold text-slate-700">
                          {item}
                        </span>
                      ))
                      : <span className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-2 text-sm font-semibold text-slate-500">待补充服务场景</span>}
                  </div>

                  <div className="mt-8">
                    <div className="mb-3 flex items-end justify-between">
                      <span className="text-sm font-semibold text-slate-600">答复覆盖率</span>
                      <span className="font-['Manrope'] text-[2rem] font-black tracking-[-0.04em] text-indigo-600">
                        {selectedFulfillmentRate.toFixed(1)}%
                      </span>
                    </div>
                    <div className="h-3 w-full overflow-hidden rounded-full bg-slate-100">
                      <div className="h-full rounded-full bg-emerald-500" style={{ width: `${selectedFulfillmentRate}%` }} />
                    </div>
                    <div className="mt-3 text-xs font-medium italic text-slate-400">
                      共成交 {formatCount(selectedRecord.totalOrderCount)} 单，已答复 {formatCount(selectedRecord.answeredOrderCount)} 单
                    </div>
                  </div>
                </div>

                <div className="rounded-[30px] border border-slate-100 bg-white p-7 shadow-sm">
                  <div className="mb-6 flex items-center gap-3">
                    <Package className="text-indigo-600" size={22} />
                    <h3 className="font-['Manrope'] text-xl font-black tracking-tight text-slate-950">套餐结构</h3>
                  </div>

                  <div className="space-y-3">
                    {(selectedRecord.enabledPackageNames.length > 0 ? selectedRecord.enabledPackageNames.slice(0, 4) : ["暂无启用套餐"]).map((item: string, index: number) => (
                      <div
                        key={`${selectedRecord.mentorUserId}-modal-pkg-${item}`}
                        className={joinClassNames(
                          "flex items-center justify-between rounded-2xl border px-4 py-4",
                          index === 0 && selectedRecord.enabledPackageNames.length > 0
                            ? "border-indigo-100 bg-indigo-50"
                            : "border-transparent bg-slate-50",
                        )}
                      >
                        <div className="flex items-center gap-4">
                          <div className={joinClassNames(
                            "flex h-6 w-6 items-center justify-center rounded-md text-xs font-black",
                            index === 0 && selectedRecord.enabledPackageNames.length > 0
                              ? "bg-indigo-600 text-white"
                              : "bg-indigo-100 text-indigo-700",
                          )}
                          >
                            {index + 1}
                          </div>
                          <span className="text-sm font-semibold text-slate-800">{item}</span>
                        </div>
                        <span className="rounded-lg bg-white px-3 py-1 text-sm font-bold text-slate-900 shadow-sm">
                          {index === 0 && selectedRecord.enabledPackageNames.length > 0 ? formatMoneyFen(selectedRecord.startingPriceFen) : "已启用"}
                        </span>
                      </div>
                    ))}
                  </div>

                  <div className="mt-6 rounded-2xl border border-slate-100 bg-slate-50 px-4 py-4 text-sm leading-7 text-slate-600">
                    已启用 {formatCount(selectedRecord.enabledPackageCount)} / {formatCount(selectedRecord.totalPackageCount)} 个套餐，
                    其中预约型套餐 {formatCount(selectedRecord.enabledAppointmentPackageCount)} 个。
                  </div>
                </div>
              </section>

              <section className="rounded-[30px] border border-slate-100 bg-white p-7 shadow-sm">
                <div className="mb-6 flex items-center justify-between gap-4">
                  <div className="flex items-center gap-3">
                    <AlertTriangle className="text-rose-500" size={22} />
                    <h3 className="font-['Manrope'] text-2xl font-black tracking-tight text-slate-950">治理建议</h3>
                  </div>
                  <button
                    type="button"
                    className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-100"
                    onClick={handleRefresh}
                    disabled={refreshButtonLoading}
                  >
                    <RefreshCcw size={15} className={refreshButtonLoading ? "animate-spin" : ""} />
                    刷新视图
                  </button>
                </div>

                {selectedRecord.riskSignals.length === 0 ? (
                  <div className="rounded-[24px] border border-emerald-100 bg-emerald-50/70 px-5 py-4 text-sm leading-7 text-emerald-700">
                    当前未命中导师经营巡检规则，可继续观察排期覆盖、履约节奏和提现收口情况。
                  </div>
                ) : (
                  <div className="overflow-hidden rounded-[24px] border border-slate-100">
                    <div className="hidden grid-cols-[130px_220px_minmax(0,1fr)] gap-4 border-b border-slate-100 bg-slate-50/80 px-5 py-4 text-[11px] font-black text-slate-400 md:grid">
                      <div>风险等级</div>
                      <div>风险标题</div>
                      <div>风险说明</div>
                    </div>
                    <div className="divide-y divide-slate-50">
                      {selectedRecord.riskSignals.map((signal: any) => (
                        <div key={signal.code} className="grid gap-3 px-5 py-5 md:grid-cols-[130px_220px_minmax(0,1fr)] md:items-start md:gap-4">
                          <div>{getRiskSignalTag(signal)}</div>
                          <div className="text-sm font-semibold text-slate-900">{signal.label}</div>
                          <div className="text-sm leading-7 text-slate-500">{signal.description}</div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </section>
            </div>

            <div className="space-y-6">
              <section className="rounded-[30px] border border-slate-100 bg-white p-7 shadow-sm">
                <h3 className="font-['Manrope'] text-xl font-black tracking-tight text-slate-950">平台动作</h3>

                <div className="mt-6 space-y-4">
                  {[
                    {
                      key: "user-detail",
                      icon: User,
                      title: "用户详情",
                      description: "查看导师账号的完整后台档案",
                      iconClassName: "bg-indigo-50 text-indigo-600",
                      onClick: () => navigate(`/admin/users/${selectedRecord.mentorUserId}`),
                    },
                    ...(selectedRecord.approvalStatus !== "APPROVED" ? [{
                      key: "cert-review",
                      icon: ShieldCheck,
                      title: "认证审核",
                      description: "继续处理当前导师的认证资料",
                      iconClassName: "bg-emerald-50 text-emerald-600",
                      onClick: () => navigate(`/admin/users/reviews/${selectedRecord.mentorUserId}`),
                    }] : []),
                    {
                      key: "financial-flows",
                      icon: Banknote,
                      title: "支付与售后",
                      description: "联查提现、订单和退款治理信息",
                      iconClassName: "bg-amber-50 text-amber-600",
                      onClick: () => navigate("/admin/consult/orders"),
                    },
                  ].map((item) => (
                    <button
                      key={item.key}
                      type="button"
                      className="group flex w-full items-center justify-between rounded-2xl border border-slate-100 bg-slate-50 px-4 py-4 text-left transition-colors hover:bg-slate-100"
                      onClick={item.onClick}
                    >
                      <div className="flex items-center gap-4">
                        <div className={joinClassNames("flex h-10 w-10 items-center justify-center rounded-xl", item.iconClassName)}>
                          <item.icon size={19} />
                        </div>
                        <div>
                          <div className="text-sm font-semibold text-slate-900">{item.title}</div>
                          <div className="text-xs font-medium text-slate-500">{item.description}</div>
                        </div>
                      </div>
                      <ChevronRight size={18} className="text-slate-300 transition-colors group-hover:text-slate-500" />
                    </button>
                  ))}
                </div>
              </section>

              <section className="rounded-[30px] border border-slate-100 bg-white p-7 shadow-sm">
                <div className="text-[10px] font-black text-slate-400">提现工作流</div>
                <div className="mt-5 flex gap-4">
                  {selectedWithdrawalActions ? (
                    <>
                      <Popconfirm
                        title={selectedWithdrawalActions.primary.title}
                        description={selectedWithdrawalActions.primary.description}
                        okText="确认"
                        cancelText="取消"
                        onConfirm={() => handleManageWithdrawal(selectedRecord, selectedWithdrawalActions.primary.status)}
                      >
                        <button
                          type="button"
                          className="group flex flex-1 flex-col items-center justify-center gap-3 rounded-[22px] border border-slate-200 px-4 py-5 transition-colors hover:border-emerald-500 hover:bg-emerald-50"
                        >
                          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-slate-100 text-slate-600 transition-colors group-hover:bg-emerald-100 group-hover:text-emerald-600">
                            <CheckCircle2 size={20} />
                          </div>
                          <span className="text-[10px] font-black text-slate-600 group-hover:text-emerald-700">
                            {selectedWithdrawalActions.primary.label}
                          </span>
                        </button>
                      </Popconfirm>
                      <Popconfirm
                        title={selectedWithdrawalActions.secondary.title}
                        description={selectedWithdrawalActions.secondary.description}
                        okText="确认"
                        cancelText="取消"
                        onConfirm={() => handleManageWithdrawal(selectedRecord, selectedWithdrawalActions.secondary.status)}
                      >
                        <button
                          type="button"
                          className="group flex flex-1 flex-col items-center justify-center gap-3 rounded-[22px] border border-slate-200 px-4 py-5 transition-colors hover:border-rose-500 hover:bg-rose-50"
                        >
                          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-slate-100 text-slate-600 transition-colors group-hover:bg-rose-100 group-hover:text-rose-600">
                            <XCircle size={20} />
                          </div>
                          <span className="text-[10px] font-black text-slate-600 group-hover:text-rose-700">
                            {selectedWithdrawalActions.secondary.label}
                          </span>
                        </button>
                      </Popconfirm>
                    </>
                  ) : (
                    <div className="w-full rounded-[22px] border border-slate-100 bg-slate-50 px-4 py-5 text-sm leading-7 text-slate-500">
                      当前没有待处理提现动作，可继续观察最新提现备注和经营风险变化。
                    </div>
                  )}
                </div>
              </section>

              <section className="rounded-[30px] border border-slate-100 bg-white p-7 shadow-sm">
                <div className="text-[10px] font-black text-slate-400">最近提现备注</div>
                <div className="mt-5 min-h-32 rounded-[22px] border border-slate-200 bg-slate-50 px-4 py-4 text-sm leading-7 text-slate-600">
                  {selectedRecord.latestWithdrawalNote || "当前没有提现备注，可在后续治理中继续观察提现进度与异常情况。"}
                </div>
              </section>

              <button
                type="button"
                className="flex w-full items-center justify-center gap-2 rounded-[24px] bg-indigo-600 py-4 font-bold text-white shadow-[0_16px_36px_rgba(79,70,229,0.22)] transition-colors hover:bg-indigo-700"
                onClick={handleRefresh}
                disabled={refreshButtonLoading}
              >
                <RefreshCcw size={18} className={refreshButtonLoading ? "animate-spin" : ""} />
                刷新治理视图
              </button>
            </div>
          </div>
        </div>
      )}
    </Modal>
  );
}
