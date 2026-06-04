import { Alert, Button, Form, Input, InputNumber, Modal, Select, Tag, Typography } from "antd";
import {
  ArrowLeft,
  Crown,
  GraduationCap,
  KeyRound,
  Shield,
  Sparkles,
  UserCheck,
  UserCog,
  UserX,
} from "lucide-react";
import AdminIdentityAvatar from "../AdminIdentityAvatar";
import { accountStatusLabelMap, roleColorMap, roleLabelMap, tierLabelMap } from "../../../lib/adminLabels";
import { formatDateTime } from "../../../lib/formatters";
import type { AdminWorkspaceModuleProps } from "../AdminLazyWorkspace";

const { Paragraph, Text } = Typography;

function joinClassNames(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

export default function AdminUsersDetailModal({ context }: AdminWorkspaceModuleProps<any>) {
  const {
    activeUserId,
    currentUser,
    selectedUserFromList,
    detailError,
    detailLoading,
    studentProfileCompleteness,
    actionLoading,
    actionLoadingKey,
    passwordForm,
    pointsForm,
    grantReasonOptions,
    handleCloseUser,
    handleUpdateStatus,
    handleUpdateTier,
    handleResetPassword,
    handleGrantPoints,
    handleOpenCertificationWorkspace,
    navigate,
    renderTierTag,
    renderApprovalTag,
    isApprovalManagedRole,
  } = context;

  return (
    <Modal
      open={Boolean(activeUserId)}
      onCancel={handleCloseUser}
      footer={null}
      title={null}
      width={1320}
      centered
      destroyOnClose
      closable={false}
      styles={{
        content: {
          padding: 0,
          overflow: "hidden",
          borderRadius: 32,
          background: "rgba(255,255,255,0.88)",
          boxShadow: "0 28px 90px rgba(15,23,42,0.18)",
        },
        body: {
          padding: 0,
          background: "transparent",
        },
      }}
    >
      {detailError ? (
        <div className="p-6">
          <Alert type="error" showIcon className="rounded-2xl" message="用户详情加载失败" description={detailError} />
        </div>
      ) : detailLoading && !currentUser ? (
        <div className="py-16 text-center text-slate-500">正在加载用户详情...</div>
      ) : currentUser ? (
        <div className="flex h-[84vh] flex-col overflow-hidden rounded-[32px] bg-white/92 backdrop-blur-2xl">
          <div className="border-b border-slate-200/70 bg-white/70 px-10 py-8 backdrop-blur-xl">
            <div className="flex flex-col gap-6">
              <div className="flex items-start gap-5">
                <button
                  type="button"
                  onClick={handleCloseUser}
                  className="mt-1 inline-flex h-11 w-11 items-center justify-center rounded-full text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900"
                >
                  <ArrowLeft size={22} />
                </button>

                <AdminIdentityAvatar
                  role={currentUser.role}
                  userId={currentUser.userId}
                  displayName={currentUser.displayName}
                  className="!h-24 !w-24 !min-w-24 !shrink-0 !overflow-hidden"
                  textClassName="text-[2rem]"
                />

                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-3">
                    <h1 className="font-['Manrope'] text-[2.2rem] font-extrabold tracking-[-0.05em] text-slate-950">
                      {currentUser.displayName}
                    </h1>
                    <span className={joinClassNames(
                      "rounded-full px-3 py-1 text-xs font-bold",
                      currentUser.status === "SUSPENDED"
                        ? "bg-rose-100 text-rose-600"
                        : "bg-emerald-100 text-emerald-600",
                    )}>
                      {accountStatusLabelMap[currentUser.status] ?? currentUser.status}
                    </span>
                  </div>

                  <p className="mt-2 text-[15px] font-medium text-slate-500">{currentUser.email}</p>

                  <div className="mt-4 flex flex-wrap items-center gap-2">
                    <Tag color={roleColorMap[currentUser.role]} bordered={false}>
                      {roleLabelMap[currentUser.role] ?? currentUser.role}
                    </Tag>
                    {renderTierTag(currentUser.tier)}
                    {isApprovalManagedRole(currentUser.role) ? renderApprovalTag(currentUser.role, currentUser.approvalStatus) : null}
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto bg-slate-50/80 p-8">
            <div className="grid grid-cols-12 items-start gap-6">
              <div className="col-span-12 space-y-6 xl:col-span-8">
                <div className="grid grid-cols-1 items-start gap-4 md:grid-cols-3">
                  {[
                    { label: "用户编号", value: `#${currentUser.userId}` },
                    { label: "注册时间", value: formatDateTime(currentUser.createdAt) },
                    { label: "最近活跃", value: formatDateTime(selectedUserFromList?.lastLoginAt ?? null), dot: true },
                  ].map((item) => (
                    <div key={item.label} className="rounded-2xl border border-slate-200/80 bg-white px-5 py-5 shadow-sm">
                      <div className="mb-3 text-[11px] font-black text-slate-400">{item.label}</div>
                      <div className="flex items-center gap-2">
                        {item.dot ? <span className="h-2 w-2 rounded-full bg-emerald-500" /> : null}
                        <span className="font-['Manrope'] text-[1.15rem] font-bold text-slate-950">{item.value}</span>
                      </div>
                    </div>
                  ))}
                </div>

                <div className="rounded-[28px] border border-slate-200/80 bg-white p-8 shadow-none">
                  <div className="mb-8 flex items-center gap-3">
                    {currentUser.studentProfile ? (
                      <GraduationCap size={22} className="text-[#5b61f6]" />
                    ) : (
                      <UserCog size={22} className="text-[#5b61f6]" />
                    )}
                    <h3 className="font-['Manrope'] text-xl font-bold text-slate-950">
                      {currentUser.studentProfile ? "学生资料" : "账号资料"}
                    </h3>
                  </div>

                  {currentUser.studentProfile ? (
                    <div className="grid gap-x-8 gap-y-6 md:grid-cols-2">
                      <div className="space-y-1">
                        <label className="text-[10px] font-black text-slate-400">专业</label>
                        <p className="text-lg font-semibold text-slate-950">{currentUser.studentProfile.major || "—"}</p>
                      </div>
                      <div className="space-y-1">
                        <label className="text-[10px] font-black text-slate-400">年级</label>
                        <p className="text-lg font-semibold text-slate-950">{currentUser.studentProfile.grade || "—"}</p>
                      </div>
                      <div className="space-y-1">
                        <label className="text-[10px] font-black text-slate-400">目标岗位</label>
                        <p className="text-lg font-semibold text-slate-950">{currentUser.studentProfile.targetPosition || "—"}</p>
                      </div>
                      <div className="space-y-1">
                        <label className="text-[10px] font-black text-slate-400">认证状态</label>
                        <div className="pt-1">{renderApprovalTag(currentUser.role, currentUser.approvalStatus)}</div>
                      </div>
                      <div className="space-y-1 md:col-span-2">
                        <label className="text-[10px] font-black text-slate-400">技能标签</label>
                        <div className="flex flex-wrap gap-2 pt-2">
                          {currentUser.studentProfile.skillTags.length > 0 ? (
                            currentUser.studentProfile.skillTags.map((tag: string) => (
                              <Tag key={tag} bordered={false} className="m-0 rounded-full bg-slate-100 px-3 py-1 text-slate-600">
                                {tag}
                              </Tag>
                            ))
                          ) : (
                            <Text type="secondary">暂无标签</Text>
                          )}
                        </div>
                      </div>
                      <div className="space-y-1 md:col-span-2">
                        <label className="text-[10px] font-black text-slate-400">自我介绍</label>
                        <Paragraph className="!mb-0 !mt-2 !text-sm !leading-7 !text-slate-600">
                          {currentUser.studentProfile.selfIntro || "暂无自我介绍"}
                        </Paragraph>
                      </div>
                    </div>
                  ) : (
                    <div className="grid gap-6 md:grid-cols-2">
                      <div className="space-y-1">
                        <label className="text-[10px] font-black text-slate-400">主角色</label>
                        <p className="text-lg font-semibold text-slate-950">{roleLabelMap[currentUser.role] ?? currentUser.role}</p>
                      </div>
                      <div className="space-y-1">
                        <label className="text-[10px] font-black text-slate-400">套餐等级</label>
                        <p className="text-lg font-semibold text-slate-950">{tierLabelMap[currentUser.tier] ?? currentUser.tier}</p>
                      </div>
                      <div className="space-y-1">
                        <label className="text-[10px] font-black text-slate-400">认证状态</label>
                        <div className="pt-1">{renderApprovalTag(currentUser.role, currentUser.approvalStatus)}</div>
                      </div>
                      <div className="space-y-1">
                        <label className="text-[10px] font-black text-slate-400">账户状态</label>
                        <p className="text-lg font-semibold text-slate-950">{accountStatusLabelMap[currentUser.status] ?? currentUser.status}</p>
                      </div>
                    </div>
                  )}
                </div>

                <div className={joinClassNames("grid grid-cols-1 items-start gap-6", currentUser.role === "STUDENT" && "lg:grid-cols-2")}>
                  {currentUser.role === "STUDENT" ? (
                    <div className="rounded-[28px] bg-gradient-to-br from-[#5b61f6] via-[#4f46e5] to-[#4338ca] p-8 text-white shadow-[0_20px_40px_rgba(79,70,229,0.2)]">
                      <div className="mb-6 flex items-start justify-between">
                        <span className="text-xs font-bold text-white/75">近 7 天社区贡献分</span>
                        <span className="rounded-full bg-white/16 px-3 py-1 text-xs font-bold">
                          活跃摘要
                        </span>
                      </div>
                      <div className="font-['Manrope'] text-5xl font-black">
                        {currentUser.communityScore7d ?? 0}
                      </div>
                      <div className="mt-6 h-2 w-full overflow-hidden rounded-full bg-white/16">
                        <div className="h-full w-[76%] rounded-full bg-white" />
                      </div>
                    </div>
                  ) : null}

                  {currentUser.role === "STUDENT" || !isApprovalManagedRole(currentUser.role) ? (
                    <div className={joinClassNames(
                      "rounded-[28px] border border-slate-200/80 bg-white p-8 shadow-none",
                      currentUser.role !== "STUDENT" && "lg:col-span-2",
                    )}>
                      <div className="flex items-center gap-4">
                        <div
                          className={joinClassNames(
                            "flex h-16 w-16 items-center justify-center rounded-[20px]",
                            currentUser.role === "STUDENT" ? "bg-emerald-100 text-emerald-600" : "bg-amber-100 text-amber-600",
                          )}
                        >
                          {currentUser.role === "STUDENT" ? <UserCheck size={28} /> : <Shield size={28} />}
                        </div>
                        <div>
                          <div className="text-xs font-bold text-slate-400">
                            {currentUser.role === "STUDENT" ? "资料完整度" : "账号概览"}
                          </div>
                          <div className="mt-2 font-['Manrope'] text-3xl font-black text-slate-950">
                            {currentUser.role === "STUDENT"
                              ? `${studentProfileCompleteness ?? 0}%`
                              : accountStatusLabelMap[currentUser.status] ?? currentUser.status}
                          </div>
                        </div>
                      </div>
                    </div>
                  ) : (
                    <div className="rounded-[24px] border-2 border-slate-200 bg-white p-6 shadow-sm">
                      <div className="flex items-start justify-between gap-4">
                        <div className="flex items-center gap-4">
                          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
                            <UserCog size={18} />
                          </div>
                          <div className="font-bold text-slate-950">认证审核</div>
                        </div>
                        <Tag bordered={false} className="m-0 rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-600">
                          审核中心
                        </Tag>
                      </div>
                      <div className="mt-5 space-y-3">
                        <div className="rounded-2xl bg-slate-50 px-4 py-4">
                          <div className="text-[11px] font-bold text-slate-400">当前审核状态</div>
                          <div className="mt-2">{renderApprovalTag(currentUser.role, currentUser.approvalStatus)}</div>
                        </div>
                        <Button block className="!h-11 !rounded-2xl !border-slate-200" onClick={() => navigate("/admin/users/reviews")}>
                          打开认证审核队列
                        </Button>
                        <Button
                          block
                          type="primary"
                          className="!h-11 !rounded-2xl !border-0 !bg-gradient-to-r !from-[#5b61f6] !to-[#4338ca] !font-semibold shadow-lg shadow-[#4f46e5]/15"
                          onClick={() => handleOpenCertificationWorkspace(currentUser.userId)}
                        >
                          查看当前用户审核
                        </Button>
                      </div>
                    </div>
                  )}
                </div>
              </div>

              <div className="col-span-12 space-y-6 xl:col-span-4">
                {isApprovalManagedRole(currentUser.role) ? (
                  <div className="rounded-[28px] border border-slate-200/80 bg-white p-8 shadow-none">
                    <div className="flex items-center gap-4">
                      <div className="flex h-16 w-16 items-center justify-center rounded-[20px] bg-amber-100 text-amber-600">
                        <Shield size={28} />
                      </div>
                      <div>
                        <div className="text-xs font-bold text-slate-400">账号概览</div>
                        <div className="mt-2 font-['Manrope'] text-3xl font-black text-slate-950">
                          {accountStatusLabelMap[currentUser.status] ?? currentUser.status}
                        </div>
                      </div>
                    </div>
                  </div>
                ) : null}

                {currentUser.role === "STUDENT" ? (
                  <div className="rounded-[28px] border border-slate-200/80 bg-white p-6 shadow-sm">
                    <div className="mb-5 flex items-center gap-3">
                      <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-amber-100 text-amber-600">
                        <Crown size={18} />
                      </div>
                      <div>
                        <div className="text-sm font-semibold text-slate-950">用户组调整</div>
                        <div className="mt-1 text-xs font-semibold text-slate-400">
                          当前为 {currentUser.tier === "PREMIUM" ? "VIP 会员" : "普通用户"}
                        </div>
                      </div>
                    </div>

                    <div className="grid gap-3">
                      <Button
                        block
                        loading={actionLoadingKey === "TIER_FREE"}
                        disabled={actionLoading && actionLoadingKey !== "TIER_FREE"}
                        className={joinClassNames(
                          "!h-12 !rounded-2xl !border !font-bold",
                          currentUser.tier === "FREE"
                            ? "!border-slate-900 !bg-slate-900 !text-white hover:!border-slate-900 hover:!bg-slate-900 hover:!text-white"
                            : "!border-slate-200 !bg-white !text-slate-700 hover:!border-slate-300 hover:!text-slate-950",
                        )}
                        onClick={() => void handleUpdateTier("FREE")}
                      >
                        普通用户
                      </Button>
                      <Button
                        block
                        loading={actionLoadingKey === "TIER_PREMIUM"}
                        disabled={actionLoading && actionLoadingKey !== "TIER_PREMIUM"}
                        className={joinClassNames(
                          "!h-12 !rounded-2xl !border-0 !font-bold",
                          currentUser.tier === "PREMIUM"
                            ? "!bg-gradient-to-r !from-[#f59e0b] !to-[#f97316] !text-white hover:!from-[#f59e0b] hover:!to-[#f97316] hover:!text-white"
                            : "!bg-amber-50 !text-amber-700 hover:!bg-amber-100 hover:!text-amber-800",
                        )}
                        onClick={() => void handleUpdateTier("PREMIUM")}
                      >
                        VIP 会员
                      </Button>
                    </div>
                  </div>
                ) : null}

                <div className="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm">
                  <div className="mb-4 text-sm font-bold text-slate-400">账户控制</div>
                  <div className="flex items-center justify-between rounded-2xl bg-slate-50 p-4">
                    <div className="font-semibold text-slate-950">
                      {currentUser.status === "SUSPENDED" ? "账号已封禁" : "账号正常"}
                    </div>
                    <span className={joinClassNames(
                      "relative h-7 w-14 rounded-full transition-colors",
                      currentUser.status === "SUSPENDED" ? "bg-slate-300" : "bg-emerald-500",
                    )}>
                      <span className={joinClassNames(
                        "absolute top-1 h-5 w-5 rounded-full bg-white shadow-sm transition-all",
                        currentUser.status === "SUSPENDED" ? "left-1" : "left-8",
                      )} />
                    </span>
                  </div>
                  <div className="mt-4">
                    {currentUser.status === "SUSPENDED" ? (
                      <Button
                        loading={actionLoadingKey === "STATUS"}
                        disabled={actionLoading && actionLoadingKey !== "STATUS"}
                        icon={<UserCheck size={16} />}
                        className="!h-11 !w-full !rounded-xl !border-emerald-500 !text-emerald-600 hover:!border-emerald-500 hover:!bg-emerald-50 hover:!text-emerald-600"
                        onClick={() => void handleUpdateStatus("ACTIVE")}
                      >
                        解除封禁
                      </Button>
                    ) : (
                      <Button
                        danger
                        loading={actionLoadingKey === "STATUS"}
                        disabled={actionLoading && actionLoadingKey !== "STATUS"}
                        icon={<UserX size={16} />}
                        className="!h-11 !w-full !rounded-xl"
                        onClick={() => void handleUpdateStatus("SUSPENDED")}
                      >
                        封禁账号
                      </Button>
                    )}
                  </div>
                </div>

                <div className="rounded-[28px] border border-slate-200/80 bg-white p-6 shadow-sm">
                  <div className="mb-6 flex items-center gap-3">
                    <KeyRound size={18} className="text-slate-500" />
                    <div className="font-semibold text-slate-950">密码重置</div>
                  </div>
                  <Form form={passwordForm} layout="vertical">
                    <Form.Item
                      name="newPassword"
                      label="新密码"
                      rules={[
                        { required: true, message: "请输入新密码" },
                        { min: 6, message: "密码至少 6 位" },
                      ]}
                    >
                      <Input.Password placeholder="请输入新的登录密码" className="!rounded-xl" />
                    </Form.Item>
                    <Form.Item
                      name="confirmPassword"
                      label="确认新密码"
                      dependencies={["newPassword"]}
                      rules={[
                        { required: true, message: "请再次输入新密码" },
                        ({ getFieldValue }) => ({
                          validator(_rule, value) {
                            if (!value || getFieldValue("newPassword") === value) {
                              return Promise.resolve();
                            }

                            return Promise.reject(new Error("两次输入的密码不一致"));
                          },
                        }),
                      ]}
                    >
                      <Input.Password placeholder="请再次输入新的登录密码" className="!rounded-xl" />
                    </Form.Item>
                    <Button
                      type="primary"
                      className="!h-11 !w-full !rounded-xl"
                      loading={actionLoadingKey === "PASSWORD"}
                      disabled={actionLoading && actionLoadingKey !== "PASSWORD"}
                      onClick={() => void handleResetPassword()}
                    >
                      重置登录密码
                    </Button>
                  </Form>
                </div>

                {currentUser.role === "STUDENT" ? (
                  <div className="rounded-[28px] border border-slate-200/80 bg-white p-6 shadow-sm">
                    <div className="mb-6 flex items-center gap-3">
                      <Sparkles size={18} className="text-indigo-600" />
                      <div className="font-semibold text-slate-950">积分补发</div>
                    </div>
                    <Form form={pointsForm} layout="vertical">
                      <div className="grid gap-4 md:grid-cols-[1fr_180px]">
                        <Form.Item
                          name="points"
                          label="补发积分"
                          rules={[{ required: true, message: "请输入补发积分" }]}
                        >
                          <InputNumber className="w-full" min={1} max={100000} precision={0} />
                        </Form.Item>
                        <Form.Item
                          name="reasonCode"
                          label="原因"
                          rules={[{ required: true, message: "请选择原因" }]}
                        >
                          <Select options={grantReasonOptions} />
                        </Form.Item>
                      </div>
                      <Button
                        type="primary"
                        className="!h-11 !w-full !rounded-xl"
                        loading={actionLoadingKey === "POINTS"}
                        disabled={actionLoading && actionLoadingKey !== "POINTS"}
                        onClick={() => void handleGrantPoints()}
                      >
                        补发积分
                      </Button>
                    </Form>
                  </div>
                ) : null}
              </div>
            </div>
          </div>
        </div>
      ) : (
        <div className="py-16 text-center text-slate-500">请选择一位用户查看详情。</div>
      )}
    </Modal>
  );
}
