# 导师推荐 Embedding 落地说明

> **文档状态**：`evolving` · 最后审核：2026-04-13 · 当前为已落地版本说明，后续可继续升级为真实 embedding provider。

补充蓝图入口：
- 学生画像与内容推荐的最终收口方案见 [`45_STUDENT_PORTRAIT_AND_CONTENT_RECOMMENDATION_BLUEPRINT.md`](./45_STUDENT_PORTRAIT_AND_CONTENT_RECOMMENDATION_BLUEPRINT.md)。

## 1. 落地目标
- 不改变 frozen 的导师广场正式定位：
  - 最终榜单仍然是 `规则加权排序`
  - `AI / embedding` 只负责补强召回和推荐解释依据
- 当前推荐主线正式收口为：
  - `基于内容的推荐`
  - `embedding 召回`
  - `规则重排`
  - `运行留痕`
- 在当前模拟数据阶段，把以下能力先正式落到代码和数据库：
  - 学生 / 导师推荐快照
  - embedding 向量缓存
  - Top-K 召回
  - 六维规则重排
  - run / event 留痕

## 2. 当前实际链路
1. 学生请求导师推荐接口。
2. 后端读取学生资料、画像标签、最近一次 AI 简历诊断、最近一次 AI 面试总结。
3. 生成 `student_recommendation_snapshots` 快照，并持久化 / 复用 `recommendation_embedding_vectors`。
4. 后端读取当前筛选条件下的导师候选集，生成 `mentor_recommendation_snapshots` 与导师向量。
5. 使用 embedding 相似度做召回。
6. 对召回结果按 frozen spec 六维规则重排：
   - 问题场景匹配度
   - 能力短板 / AI 诊断问题匹配度
   - 目标岗位 / 擅长方向匹配度
   - 导师质量信号
   - 可接单状态
   - 价格适配度
7. 返回 Top 3，并写入 `mentor_recommendation_runs / mentor_recommendation_events`。

## 3. 为什么当前不用真实 embedding API
- 当前仓库里还没有现成、稳定、低风险的 embedding 路由复用点。
- 导师广场又要求推荐结果可复现、可解释，不能为了“接了 AI”而直接把最终排名交给黑盒。
- 当前阶段以本科毕设答辩和前端人工测试收口为主，优先级更高的是：
  - 先把召回层、数据结构、日志结构、快照结构落地
  - 先让画像、简历、面试三条链路真正进入推荐
- 同时，当前版本已经明确放弃把协同过滤作为主实现方向：
  - 缺少可支撑论文和答辩的真实用户行为矩阵
  - 难以验证协同过滤对当前冷启动平台的真实收益
  - 因此当前优先保证“内容推荐 + 规则重排”的工程可解释性

当前实现选择：
- `LOCAL_HASHED_BOW_V1`
- 本质是“稳定、可替换的本地 embedding”
- 优点：
  - 不依赖外部 provider
  - 可以立即落库、跑通、可重复
  - 后续切换到真实 embedding 时，不需要重做表结构和 rerank 主链路

## 4. 当前 embedding 实现说明
- 向量维度：`192`
- 生成方式：
  - 对中文 / 英文 / 技术关键词做标准化分词
  - 增补中文二元片段与方向性 canonical tokens
  - 使用哈希映射到固定维度并做 L2 归一化
- 相似度：
  - 当前使用 cosine similarity
- 召回范围：
  - 在当前筛选条件下的导师候选集中做 Top-K 召回
  - 默认取前 `12` 位进入规则重排

## 5. 当前重排策略
- embedding 不直接决定最终榜单。
- embedding 主要参与两件事：
  - 决定谁先进入候选召回池
  - 参与“能力短板 / AI 诊断问题匹配度”的得分补强

当前结果依然输出：
- Top 3 推荐导师
- 0-100 匹配分
- 推荐理由
- 风险提示
- 解释文本

## 6. 学生画像增强范围
当前画像刷新已正式纳入：
- 资料侧：
  - `target_position`
  - `skill_tags`
  - `self_intro`
- 行为侧：
  - 技能进度摘要
  - 社区 7 日互动
  - 面试 7 日消息量
- AI 历史侧：
  - 最近一次简历诊断 `summary / suggestions / targetRole`
  - 最近一次面试总结 `weaknesses / suggestions / targetRole`

当前新增标签示例：
- `TARGET_DIRECTION_BACKEND`
- `TARGET_DIRECTION_FRONTEND`
- `DATA_ANALYSIS_ORIENTATION`
- `RESUME_EXPRESSION_NEEDS_IMPROVEMENT`
- `RESUME_PROJECT_EXPRESSION_NEEDS_IMPROVEMENT`
- `INTERVIEW_PROJECT_EXPRESSION_GAP`
- `INTERVIEW_SYSTEM_DESIGN_GAP`
- `INTERVIEW_COMMUNICATION_GAP`

收口说明：
- 当前画像真相层仍由本地规则生成，不把 LLM 作为标签判定来源。
- 画像对推荐的主要价值是提供方向标签、能力短板和最近 AI 结果摘要，而不是产出一段不可解释的黑盒文案。
- 下一阶段若补 `headline / summary / nextActions`，也应作为表达层，不替代结构化标签。

## 7. 当前画像触发规则
当前确认会触发学生画像刷新：
- 学生资料更新
- 学生画像首次读取但本地还没有快照
- 学生公开资料读取
- 技能进度更新
- 社区发帖 / 回复等互动
- AI 面试回复写入
- AI 简历优化成功返回
- 每日定时刷新

当前代码入口可直接对应为：
- `StudentProfileService.updateMyProfile(...)`
- `StudentProfileService.loadMyPortraitSnapshot(...)`
- `StudentProfileService.loadPublicProfileSlice(...)`
- `SkillService.updateProgress(...)`
- `CommunityService.refreshPortraitIfStudent(...) / refreshPostAuthorPortrait(...)`
- `AiPracticeService.optimizeResumeText(...) / optimizeResumePdf(...)`
- `AiPracticeService.persistInterviewReply(...)`
- `StudentPortraitRefreshService.refreshAllDaily(...)`

说明：
- 本轮已把“AI 简历优化成功后刷新画像”补齐，避免简历诊断结果落库后却没有进入画像。
- 当前头像上传、隐私设置变更不会主动刷新画像，因为它们不影响推荐语义。

## 8. 模拟数据基线
本轮导师种子脚本已补：
- 4 位与导师广场推荐强相关的学生资料
- 对应的学生画像快照
- 最近一次简历优化 AI 记录
- 最近一次面试总结与消息样本

推荐演示时优先关注：
- `student.liujianing@bishe.local`
- `student.xuanran@bishe.local`
- `student.zhoumuyang@bishe.local`
- `student.heqingyan@bishe.local`

当前本地库（2026-04-02）可确认：
- 上述 4 位学生当前都已有 `student_portrait_snapshots`
- 每位学生至少有 1 条成功简历诊断记录
- 每位学生至少有 1 条已生成总结的面试记录
- 因项目本地库此前还叠加过其它演示数据，个别学生会出现多条历史简历 / 面试样本；导师推荐实际只读取“最近一次”成功记录

## 9. 后续升级路径
若后续要接真实 embedding provider，建议顺序如下：
1. 在 AI gateway 新增独立 embedding 路由，而不是复用 chat route。
2. 保持 `student_recommendation_snapshots / mentor_recommendation_snapshots` 不变。
3. 在 `recommendation_embedding_vectors` 中新增新的 `model_code`。
4. 后台或脚本批量重刷向量。
5. 对比新旧 `mentor_recommendation_runs` 的命中差异，再决定是否切换默认模型。

同时建议同步推进：
6. 学生画像结构化信号现已补齐到 `strengthTags / riskTags / signalLevel / freshnessLevel / headline / summary / nextActions / summaryVersion`，后续只继续做验证和文档同步，不再把这部分当作未落地缺口。
7. 画像刷新与画像总结优先复用 `ai_async_task_jobs + ai_async_task_events` 做后台重算与去重；该项当前仍属后续优化，不是导师推荐主链路的前置条件。

## 10. 当前边界
- 当前不是“真实大模型直接产榜”，而是“embedding 召回 + 规则重排”。
- 当前 embedding 还不是外部真实语义模型。
- 当前版本正式不落地协同过滤，因为缺少真实规模的用户行为样本与可信评估基线。
- 现阶段最重要的是把推荐从“纯关键词拼接”升级为“有快照、有语义召回、有运行日志”的正式工程实现。
- 当前导师推荐已经消费学生画像的结构化信号与表达层摘要，但画像刷新后不会主动推送刷新推荐快照；推荐快照与向量缓存仍在访问 `/mentors/recommendations` 时按 `content_hash` 懒重算。

## 11. 本地联调验证
2026-04-02 已使用本地演示学生账号在临时 `18080` 后端实例回放 `GET /api/v1/mentors/recommendations`，确认：
- 推荐响应中的 `basisSummary` 已纳入“最近简历诊断 / 最近面试复盘”
- 推荐响应中的 `basisTags` 已出现对应标签
- 一次请求后，MySQL 中成功写入：
  - `mentor_recommendation_runs = 1`
  - `mentor_recommendation_events = 15`
  - `student_recommendation_snapshots = 1`
  - `mentor_recommendation_snapshots = 14`
  - `recommendation_embedding_vectors = 15`
- 最新一条 run 的 `recall_model_code=LOCAL_HASHED_BOW_V1`，`rerank_version=EMBEDDING_RECALL_RULE_RERANK_V1`

补充说明：
- 当前常驻 `8080` 实例如果仍是旧进程，浏览器中会继续看到旧版推荐解释文案，也不会写入上述推荐表。
- 因此用户正式回归浏览器前，仍需重启或替换当前 `8080` 的后端实例。
