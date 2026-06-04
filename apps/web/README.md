# Web App Workspace

当前前端已经不再是初始化骨架，而是本项目唯一的正式 Web 客户端，统一承载学生端、导师端、企业端和管理员端页面。

## 技术栈
- React 18
- TypeScript
- Vite
- React Router 6
- Ant Design 5
- Framer Motion
- Tailwind CSS 4

## 已落地页面范围
- 鉴权：登录、注册、注册第二步、忘记密码三步流。
- 学生端：工作台、资料中心、技能树、AI 简历优化、AI 面试、AI 复盘中心、社区、通知中心、导师咨询、悬赏大厅与详情。
- 导师端：工作台、资料页、订单工作台、财务中心。
- 企业端：工作台、资料页、任务发布、任务中心、任务审核工作区。
- 管理端：总览、用户与认证审核、内容治理、AI 网关、运行管理、通知、导师运营、企业任务运营、技能运营、订单对账等。

## 开发命令
```bash
cd apps/web
npm_config_cache=/home/olddream/.cache/dev/npm mise exec -- npm install
npm_config_cache=/home/olddream/.cache/dev/npm mise exec -- npm run dev
```

构建：
```bash
cd apps/web
npm_config_cache=/home/olddream/.cache/dev/npm mise exec -- npm run build
```

## 约束说明
- 默认 API 基础路径走 `/api/v1`，开发态由 `vite.config.ts` 代理到本地 Java 服务。
- `/ai/interview/live/**` 开发态会代理到 `127.0.0.1:8765`，仅在联调 Live 测试模式时需要额外启动 `apps/interview-live-python`。
- 当前阶段以前端宽屏桌面端为人工验收基线；移动端/平板适配不作为默认验收范围。
- 页面与接口契约以 [`docs/spec/04_API_SPEC.md`](../../docs/spec/04_API_SPEC.md) 及相关 UI 规则文档为准，但若文档与代码不一致，应以当前已落地代码和联调结果为准并及时回写文档。
