# 导师资料页 UI 设计规则

> 文档状态：`evolving`
> 最近同步：2026-04-08
> 适用范围：导师端 `/mentor/profile`
> 用途：以当前真实代码与已接通接口为准，定义导师资料页的页面职责、字段边界与验收口径。

## 1. 页面定位

`/mentor/profile` 当前已经是导师侧正式落地的资料中心，不再是待实现页面。

它的职责是：

- 维护导师公开服务名片
- 维护头像、昵称、套餐、接单状态与服务说明
- 预览学生视角下的导师展示
- 查看并补交认证材料
- 维护常规排期与特殊日期排期
- 承接密码修改与通知设置等个人侧操作

一句话：

`导师资料页是“服务资料编辑台 + 学生视角预览台 + 认证/排期管理页”。`

## 2. 当前真实实现（2026-04-08）

- 页面已真实接通，不再存在“前端未正式接入”的情况
- 顶部导航已统一接入导师壳层，资料入口收进用户下拉菜单，不再在顶栏单独重复暴露
- 顶层 Tab 当前真实存在 5 个：
  - `profile`
  - `preview`
  - `certification`
  - `schedule`
  - `notifications`
- 页面已支持通过 `?tab=` 直接定位到对应工作区
- 修改密码不是独立页面，而是由资料页接收 `?action=password` 并拉起三步验证弹层
- 页面已统一为产品化文案，不再保留“待接通”“待聚合”“接口缺口说明”式开发口吻

## 3. 当前已接通的真实契约

### 3.1 `GET /mentor/profile`

当前真实返回并已被页面消费的核心字段包括：

- `displayName`
- `realName`
- `showRealName`
- `companyName`
- `jobTitle`
- `avatarUrl`
- `avatarConfigured`
- `avatarContentType`
- `avatarUpdatedAt`
- `expertiseTags`
- `serviceScenes`
- `bio`
- `suitableFor`
- `notSuitableFor`
- `prepMaterials`
- `replyRhythm`
- `priceFen`
- `packages`
- `avgRating`
- `totalOrders`
- `available`
- `approvalStatus`

### 3.2 `PUT /mentor/profile`

当前真实允许更新：

- `displayName`
- `showRealName`
- `jobTitle`
- `expertiseTags`
- `serviceScenes`
- `bio`
- `suitableFor`
- `notSuitableFor`
- `prepMaterials`
- `replyRhythm`
- `priceFen`
- `packages`
- `available`

当前真实不在资料页直接编辑：

- `realName`
- `companyName`

这两个字段当前按认证资料只读展示，如需更正走管理员处理。

### 3.3 头像链路

当前真实已接通：

- `POST /api/v1/mentor/profile/avatar`

前端交互当前已与学生资料页保持同类体验：

- 头像上传
- 1:1 裁剪
- 前端压缩
- 上传成功后即时回写页面与全局导师身份快照

导师头像当前已同步消费到：

- 导师资料页
- 导师顶栏用户区
- 导师工作台
- 社区导师作者身份区
- 学生侧导师列表 / 详情预览相关展示

### 3.4 密码修改链路

当前真实已接通以下三步接口：

- `POST /api/v1/mentor/profile/security/password/send-code`
- `POST /api/v1/mentor/profile/security/password/verify-code`
- `POST /api/v1/mentor/profile/security/password/change`

页面当前用弹层承接，不额外拆出独立路由页。

### 3.5 认证链路

当前真实已接通：

- `GET /api/v1/certification/me`
- `POST /api/v1/certification/me`

资料页内已可完成：

- 查看当前有效提交
- 查看审核备注
- 查看历史提交记录
- 重新提交认证材料

### 3.6 排期链路

当前真实已接通：

- `GET /api/v1/mentor/schedule/slots/me`
- `POST /api/v1/mentor/schedule/slots`
- `POST /api/v1/mentor/schedule/slots/batch`
- `POST /api/v1/mentor/schedule/slots/batch-delete`
- `DELETE /api/v1/mentor/schedule/slots/{slotId}`

页面当前已经支持：

- 单次新增可预约时段
- 固定排期批量补充
- 固定排期批量覆盖
- 固定排期批量清空未来未预约时段
- 特殊日期临时调整
- 已预约时段只读锁定

## 4. 当前页面结构

### 4.1 `profile` Tab

这是资料页当前的主编辑区，负责：

- 昵称
- 真实姓名展示开关
- 公司与职位摘要
- 标签与服务场景
- 个人介绍
- 套餐编辑
- 接单状态
- 服务说明类字段

### 4.2 `preview` Tab

当前预览不是静态占位，而是按学生侧真实展示规则回放：

- 顶部身份信息
- 服务标签与场景
- 套餐卡片
- 服务说明与匹配建议

当前预览的目标是帮助导师确认“学生看到的我是什么样”，不是另起一套独立设计语言。

### 4.3 `certification` Tab

当前负责：

- 当前认证状态
- 当前提交摘要
- 历史版本
- 材料补交

### 4.4 `schedule` Tab

当前负责：

- 全部时段读取
- 固定排期批量维护
- 特殊日期维护
- 已预约锁定态展示

### 4.5 `notifications` Tab

当前负责承接导师自己的通知查看与偏好配置，不另起第二套资料页壳层。

## 5. 字段与展示规则

### 5.1 昵称与真名

- `displayName` 当前可编辑，是学生看到的主称呼
- `realName` 当前只读，不在资料页允许直接改动
- `showRealName` 当前可切换

当前公开展示规则已经真实落地为：

- 默认只展示昵称
- 开启后，在需要展示实名的场景使用“昵称 +（真实姓名）”或“真实姓名补在身份行”两类产品规则

### 5.2 公司与职位

- `companyName` 当前只读展示
- `jobTitle` 当前可编辑

### 5.3 套餐

当前真实规则：

- 最多 `4` 个套餐
- 至少保留 `1` 个可用套餐
- 预约型套餐必须填写时长
- `priceFen` 在兼容层保留为“套餐起步价”

学生侧创单与导师广场当前都已消费套餐或起步价语义，不再只认单一固定价格。

### 5.4 服务说明字段

当前真实已接入的产品字段包括：

- `suitableFor`
- `notSuitableFor`
- `prepMaterials`
- `replyRhythm`

这些字段已不仅停留在资料页输入框，也会进入学生侧展示与创单链路。

## 6. 与其他页面的职责边界

### 6.1 资料页负责什么

- 资料编辑
- 头像维护
- 套餐维护
- 学生侧预览
- 认证材料管理
- 排期管理
- 密码修改

### 6.2 资料页不负责什么

- 今日待办分诊
- 多单搜索与优先级判断
- 单笔订单履约
- 账单与提现

这些职责分别留给：

- `/mentor/dashboard`
- `/mentor/orders`
- `/mentor/orders/:orderNo/workspace`
- `/mentor/finance`

## 7. 当前页面级交互规则

- 资料编辑期间，页面内其它展示位不应跟随输入实时漂移；统一以提交成功后的正式数据为准同步
- 头像操作已收口到头像主区域，不再在下方额外放重复的“修改头像”按钮
- 提交成功后前端应同步刷新导师身份快照与相关公开展示位，避免顶栏、工作台、社区等处继续显示旧头像或旧昵称
- 资料页应继续保持产品化表达，不回退到“技术字段解释”或“开发态提示”

## 8. 当前不再成立的旧口径

以下描述已经失效，后续文档不得继续使用：

- “导师资料页还未正式接入”
- “`displayName` 当前只读”
- “导师侧没有头像上传链路”
- “学生侧还未消费导师头像”
- “排期只支持单条新增 / 删除”
- “资料页暂未支持密码修改”

## 9. 验收关注点

- 导师资料页打开后不应再出现 hooks 顺序报错
- `profile / preview / certification / schedule / notifications` 五个 Tab 能稳定切换
- 头像上传后应同步反映到资料页、顶栏、工作台和已消费导师头像的公开场景
- 昵称修改应在提交成功后再同步各展示位
- 真实姓名与所在公司应保持只读，`showRealName` 仅控制展示策略
- 排期页应支持批量补充、批量覆盖、批量清空与特殊日期调整
- 密码修改应通过验证码三步流程完成，不要求跳转独立页面
