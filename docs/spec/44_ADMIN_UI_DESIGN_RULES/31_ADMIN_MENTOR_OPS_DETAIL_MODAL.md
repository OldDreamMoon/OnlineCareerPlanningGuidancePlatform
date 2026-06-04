# 导师经营详情弹窗 UI 设计需求

## 页面标识
- 页面名称：导师经营详情弹窗
- 所属页面：导师经营治理台
- 所属路由：`/admin/mentors/operations`
- 对应组件：`AdminMentorOperationsPage` 内详情弹窗
- 页面定位：在导师经营治理台列表上下文中打开单个导师的经营详情、风险判断和平台动作工作区

## 项目基线
- 前端技术栈：React 18 + TypeScript + Vite 5 + React Router 6
- UI 架构必须以 Ant Design 5 为主，允许使用 `@ant-design/pro-components`；Tailwind CSS 4 只做布局与间距辅助
- 弹窗运行在 `AdminShellLayout` 内容区内
- 当前验收基线为宽屏桌面端，建议按 `1440px` 画板设计
- 主题主色 `#6366f1`，成功 `#10b981`，警告 `#f59e0b`，错误 `#ef4444`
- 弹窗是平台治理层，不是导师个人主页，也不是导师财务中心

## 页面目标
- 让管理员在不离开导师经营列表的前提下快速查看单个导师的经营状态
- 将“对象识别、经营判断、风险识别、提现处理、跨页联动”收敛在一个高信息密度详情层内
- 减少常驻侧栏详情对主列表宽度的挤压，改为按需展开的弹窗工作区
- 保持平台最小干预语义，不扩成复杂 CRM 或财务后台

## 容器与打开方式
- 触发方式：点击导师经营列表行，或点击“查看详情”按钮
- 容器形式：Ant Design `Modal`
- 建议宽度：`1040px - 1120px`
- 建议使用居中大弹窗，不使用窄抽屉
- 弹窗内容允许纵向滚动
- 弹窗内部不应出现横向滚动
- 标题建议格式：`{displayName} · 导师经营详情`
- 支持右上角关闭、遮罩关闭、Esc 关闭

## 页面目标信息架构
- 弹窗标题栏
- 导师 Hero 头部卡
- 关键状态摘要区
- 经营摘要区
- 风险与建议区
- 平台动作区
- 提现备注与刷新区

## 推荐布局
- 顶部为整宽 Hero 卡，承担对象识别与总览信息
- Hero 下方采用双栏布局
- 左栏作为信息阅读主区：关键状态摘要、经营摘要、风险与建议
- 右栏作为平台动作区：查看详情、认证审核、支付售后、提现流转、备注、刷新
- 右栏建议宽度 `300px - 340px`
- 当可用宽度不足时，右栏下落到左栏下方，不要继续压缩成过窄列

## 必备模块

### 1. 导师 Hero 卡
- 真实头像
  - 仅在存在真实上传头像时显示图片
  - 无真实上传头像时显示首字占位
  - 不使用导师广场预置占位头像
- 昵称 `displayName`
- 身份行：真实姓名、机构名称，使用 `·` 分隔
- 职位行：`jobTitle`，单独一行
- 状态标签：认证状态、可接单状态、风险等级
- 核心指标：起步价格、当前评分

### 2. 关键状态摘要区
- 资料更新时间
- 最近订单活动
- 下次可预约时间
- 下次已预定时间
- 最近提现状态
- 最近提现金额
- 该区应使用小卡片或简洁描述块呈现，适合扫读，不适合大段文案

### 3. 经营摘要区
- 服务场景标签
- 套餐结构说明
- 已启用套餐数 / 总套餐数
- 预约型套餐数量
- 启用套餐名称标签
- 履约摘要
  - 总成交单数
  - 已答复单数
  - 已关闭单数
  - 退款单数
- 该区重点是帮助平台判断导师供给是否完整、履约是否稳定

### 4. 风险与建议区
- 无风险时展示绿色稳定态提示
- 有风险时按条展示风险卡
- 每条风险卡包含：
  - 风险标签
  - 风险标题
  - 风险说明
- 风险信号优先按严重度排序：`CRITICAL > HIGH > MEDIUM > LOW`
- 风险区需明显强于经营摘要区的视觉聚焦，但不能做成大面积报警面板

### 5. 平台动作区
- 查看导师详情
- 去认证审核
  - 仅未认证或未通过认证时展示
- 查看支付与售后
- 提现状态流转主动作
- 提现状态流转次动作
- 最近提现备注
- 刷新治理视图
- 该区应保持平台操作面板语义，避免与左栏内容混排

## 数据承接

### 主要数据来源
- 不新增独立详情接口
- 继续复用导师经营列表接口：
  - `GET /admin/mentors/operations?page&size&keyword&approvalStatus&riskLevel&withdrawalStatus`
- 详情弹窗消费单条 `MentorOpsRecord`

### 详情对象 `MentorOpsRecord`
- 主体信息：
  - `mentorUserId`
  - `displayName`
  - `realName`
  - `showRealName`
  - `companyName`
  - `jobTitle`
  - `avatarUrl`
  - `approvalStatus`
  - `available`
- 套餐与服务：
  - `totalPackageCount`
  - `enabledPackageCount`
  - `enabledAppointmentPackageCount`
  - `startingPriceFen`
  - `enabledPackageNames[]`
  - `serviceScenes[]`
  - `avgRating`
- 排期与履约：
  - `weekAvailableSlotCount`
  - `nextThreeDayAvailableSlotCount`
  - `upcomingBookedSlotCount`
  - `nextAvailableAt`
  - `nextBookedAt`
  - `totalOrderCount`
  - `paidOrderCount`
  - `answeredOrderCount`
  - `closedOrderCount`
  - `refundedOrderCount`
  - `overdueReplyOrderCount`
  - `expiringReplyOrderCount`
  - `pendingAfterSalesCount`
  - `afterSalesImpactCount`
  - `latestOrderActivityAt`
- 提现与风险：
  - `pendingWithdrawalCount`
  - `processingWithdrawalCount`
  - `completedWithdrawalCount`
  - `rejectedWithdrawalCount`
  - `openWithdrawalCount`
  - `openWithdrawalAmountFen`
  - `latestWithdrawalId`
  - `latestWithdrawalAmountFen`
  - `latestWithdrawalStatus`
  - `latestWithdrawalNote`
  - `latestWithdrawalCreatedAt`
  - `latestWithdrawalUpdatedAt`
  - `profileUpdatedAt`
  - `highestRiskLevel`
  - `riskSignals[]`

### 提现动作接口
- `POST /admin/mentors/operations/withdrawals/:withdrawalId/status`
- 请求体：`{ status: string }`
- 界面至少承接：
  - `PENDING -> PROCESSING`
  - `PENDING -> REJECTED`
  - `PROCESSING -> COMPLETED`
  - `PROCESSING -> REJECTED`

### 联动路由
- 查看导师详情：`/admin/users/:userId`
- 去认证审核：`/admin/users/reviews/:userId`
- 查看支付与售后：`/admin/consult/orders`

### 字段格式约束
- 时间字段统一兼容 `number | string | null`
- 金额字段后端以分为单位返回，前端统一转换为人民币
- 标签数组为空时必须有空态兜底，不允许空白块

## 关键交互
- 点击导师列表行或“查看详情”按钮打开弹窗
- 弹窗内不再承接“切换上一位/下一位导师”的交互，避免形成新的复杂详情导航
- 提现状态流转必须使用确认弹窗
- 成功处理提现后，需要同步刷新：
  - 概览卡
  - 当前列表
  - 当前弹窗内容
- 跳转到用户详情、认证审核、支付售后时，允许直接离开当前页级上下文
- 关闭弹窗后，返回原分页与筛选状态，不重新拉回第一页

## 文案与展示规则
- 导师名称主标题使用昵称 `displayName`
- 第二行使用：`realName · companyName`
- 若 `realName` 为空，则只显示公司名称
- 若 `companyName` 为空，则显示“未补充机构”
- 第三行单独显示职位
- 若 `jobTitle` 为空，则显示“未补充职位”
- 风险为空时应明确写出“当前未命中导师经营巡检规则”
- 无提现动作时应明确写出“当前没有待处理提现动作”

## 视觉要求
- Hero 卡应明显区别于普通信息卡，但仍属于后台治理气质
- 左栏信息卡应强调阅读层级：先状态，再经营，再风险
- 右栏动作区应明显比左栏更克制，按钮层级清楚，不要堆过多彩色按钮
- 标签色只用于状态和风险信号，不要把所有信息都做成标签
- 金额、时间、状态标签在视觉上要统一口径
- 对真实姓名、机构、职位的展示要稳定：
  - 第二行：真实姓名 `·` 公司名称
  - 第三行：职位
- 若没有真实姓名，则第二行从公司名称开始
- 若没有职位，则展示“未补充职位”

## 设计限制
- 优先使用 Ant Design 的 `Modal`、`Card`、`Descriptions`、`Tag`、`Button`、`Alert`、`Popconfirm`
- 不新增多 tab 弹窗
- 不新增复杂时间线、审批流、财务流水表
- 不设计成导师前台资料页风格
- 不设计成独立后台新页面的缩小版
- 当前仅按宽屏桌面端验收，不要求移动端适配

## 验收标准
- 管理员可以在不离开导师经营治理台列表的前提下读懂单个导师的经营状态
- 弹窗宽度足够，内部不出现横向滚动
- Hero、摘要、风险、动作四层结构清晰
- 提现流转、认证联动、用户详情联动、支付售后联动都可直接触发
- 关闭弹窗后列表筛选和分页状态不丢失
- 有真实上传头像时显示真实头像，无真实上传头像时回退首字占位，不显示导师广场预置占位头像
