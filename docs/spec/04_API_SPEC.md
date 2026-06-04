# API 规范（v1）

> **文档状态**：`evolving` · 最后审核：2026-04-16 · 已按当前后端实现补齐 AI 面试 `live` 测试模式、独立 Python bridge 与 transcript 回灌契约；后续仍随增量迭代更新。
>
> **API 文档策略**：
> - 本文件为**设计层规格**，记录业务语义、模块边界、全局约定。
> - 后端实现时集成 **SpringDoc/Swagger**，自动生成精确的 API 参考文档（Swagger UI）。
> - 当前 Swagger UI 访问地址：`/swagger-ui/index.html`，底层读取 `/v3/api-docs`。
> - 后端接口稳定后，通过脚本从 OpenAPI JSON 自动生成精简 API 清单（`API_REFERENCE.md`），用于论文附录。
> - 实现与本文的偏差通过 `change_request_log` 追踪。
>

## 1. 全局约定
- 基础路径：`/api/v1`
- 鉴权头：`Authorization: Bearer <access_token>`
- 内容类型：`application/json`
- 时间格式：默认 ISO-8601 UTC；若字段说明明确为“UTC epoch 毫秒”，则以字段说明为准

### 1.1 通用响应体
```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "trc_20260302_000001",
  "timestamp": "2026-03-02T12:00:00Z"
}
```

### 1.2 错误码家族
- `AUTH-xxxx`：认证与授权
- `PAY-xxxx`：支付与回调
- `AI-xxxx`：AI 模块或模型错误
- `BIZ-xxxx`：业务校验与状态错误
- `MOD-xxxx`：内容审查与举报治理错误

最小治理错误码：
- `MOD-1001`：输入内容被拦截（AI 入站或文本提交场景）
- `MOD-1002`：输出内容被拦截（AI 出站场景）
- `MOD-1003`：内容进入待审队列
- `MOD-1004`：举报频率受限

错误示例：
```json
{
  "code": "BIZ-1002",
  "message": "invalid state transition",
  "data": null,
  "traceId": "trc_20260302_000002",
  "timestamp": "2026-03-02T12:00:01Z"
}
```

### 1.3 内容审查响应字段
涉及文本生产或发布的接口在 `data` 中返回 `moderation` 对象：

```json
{
  "moderation": {
    "sourceType": "AI_OUTPUT",
    "riskLevel": "MEDIUM",
    "action": "MASK",
    "reasonCode": "SENSITIVE_TERM_MATCHED"
  }
}
```

说明：
- `riskLevel`：`LOW|MEDIUM|HIGH|CRITICAL`
- `action`：`PASS|MASK|BLOCK|REVIEW`
- `reasonCode`：规则命中编码，不回显具体敏感词

### 1.4 社区贡献分口径（v1 固定）
- 公式：`communityScore7d = postCount*5 + commentCount*2 + likeReceivedCount*1`
- 窗口：近 7 天滚动窗口（含当日）
- 统计对象：仅 `STUDENT`
- 统计范围：仅统计 `moderation_status=PASS` 的社区内容
- 排序规则：`score desc -> latestActivity desc -> studentUserId asc`

### 1.5 健康检查
- `GET /health`
- 鉴权：否
- 用途：应用存活探测与本地联调。

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "status": "UP",
    "service": "server-java",
    "time": "2026-03-08T15:00:00Z"
  },
  "traceId": "trc_health_001",
  "timestamp": "2026-03-08T15:00:00Z"
}
```

## 2. 鉴权接口
### 2.0 注册页安全校验配置
- `GET /auth/captcha/config`
- 鉴权：否

说明：
- 当前公开返回注册页所需的人机验证配置，不暴露任何私钥。
- 若 `enabled=false`，前端注册态不应允许进入下一步，而应提示“当前注册暂时不可用，请稍后重试”或同等语义。
- 当前正式接入的 provider 为 `GEETEST_V4`，前端使用 `product=bind` 模式加载 `https://static.geetest.com/v4/gt4.js`。

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "enabled": true,
    "provider": "GEETEST_V4",
    "captchaId": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "product": "bind",
    "proofTtlSeconds": 600
  },
  "traceId": "trc_auth_captcha_001",
  "timestamp": "2026-03-17T15:00:00Z"
}
```

### 2.0.1 Geetest v4 二次校验
- `POST /auth/captcha/geetest/verify`
- 鉴权：否

请求：
```json
{
  "email": "alice@example.com",
  "lotNumber": "lot-20260317-001",
  "captchaOutput": "xxxxxxxx",
  "passToken": "xxxxxxxx",
  "genTime": "1710662400"
}
```

说明：
- 前端在 `captchaObj.getValidate()` 后，将 `lot_number / captcha_output / pass_token / gen_time` 映射为本接口字段提交。
- 后端会按 Geetest v4 官方流程生成 `sign_token`，请求 `https://gcaptcha4.geetest.com/validate?captcha_id=...` 做二次校验。
- 校验成功后返回短时 `verificationToken`，该凭证仅用于后续 `/auth/email/send-code` 发信请求。
- 正式注册接口不再直接使用 Geetest proof；前端需先通过 `/auth/email/verify-code` 换取 `emailVerificationToken`，再进入统一认证页内展开的身份资料阶段完成正式注册。

成功：
```json
{
  "code": "OK",
  "message": "captcha verified",
  "data": {
    "valid": true,
    "provider": "GEETEST_V4",
    "verificationToken": "base64url.payload.signature",
    "expiresAt": "2026-03-17T15:10:00Z",
    "reason": "geetest verify success"
  },
  "traceId": "trc_auth_captcha_002",
  "timestamp": "2026-03-17T15:00:05Z"
}
```

错误：
- `AUTH-1009`：Geetest 服务不可用或官方二次校验请求失败
- `AUTH-1010`：Geetest 返回校验失败

### 2.0.2 发送注册邮箱验证码
- `POST /auth/email/send-code`
- 鉴权：否

请求：
```json
{
  "email": "alice@example.com",
  "captchaVerificationToken": "base64url.payload.signature"
}
```

说明：
- 本接口由注册第一页的“发送验证码”按钮触发。
- 前端会先完成 Geetest v4 二次校验，再把换取到的 `captchaVerificationToken` 透传到本接口。
- 当 `AUTH_EMAIL_RESEND_API_KEY` 与 `AUTH_EMAIL_RESEND_FROM_EMAIL` 都已配置时，后端会通过 Resend 正式发送 6 位邮箱验证码。

成功：
```json
{
  "code": "OK",
  "message": "email verification code sent",
  "data": {
    "sent": true,
    "provider": "RESEND_EMAIL_CODE",
    "email": "alice@example.com",
    "expiresAt": "2026-03-17T15:20:00Z",
    "nextSendAt": "2026-03-17T15:11:00Z"
  },
  "traceId": "trc_auth_email_001",
  "timestamp": "2026-03-17T15:10:00Z"
}
```

错误：
- `AUTH-1001`：邮箱已被注册
- `AUTH-1008`：Geetest 凭证无效或已过期
- `AUTH-1009`：Geetest 服务不可用
- `AUTH-1011`：邮箱验证码发送服务不可用
- `AUTH-1012`：发送过于频繁

### 2.0.3 校验注册邮箱验证码
- `POST /auth/email/verify-code`
- 鉴权：否

请求：
```json
{
  "email": "alice@example.com",
  "code": "314159"
}
```

说明：
- 注册第一页点击“继续下一步”时，前端会调用本接口校验用户输入的邮箱验证码。
- 校验成功后返回短时 `emailVerificationToken`，后续 `/auth/register` 与 `/auth/register-with-certification` 只认该凭证，不再直接使用 Geetest proof。

成功：
```json
{
  "code": "OK",
  "message": "email verification code verified",
  "data": {
    "verified": true,
    "provider": "RESEND_EMAIL_CODE",
    "verificationToken": "base64url.payload.signature",
    "expiresAt": "2026-03-17T15:40:00Z"
  },
  "traceId": "trc_auth_email_002",
  "timestamp": "2026-03-17T15:12:00Z"
}
```

错误：
- `AUTH-1011`：邮箱验证码服务不可用
- `AUTH-1013`：邮箱验证码无效或已过期

### 2.1 注册
- `POST /auth/register`
- 鉴权：否

请求：
```json
{
  "role": "MENTOR",
  "email": "alice@example.com",
  "password": "Passw0rd!",
  "displayName": "Offer捕手",
  "realName": "王小明",
  "companyName": "字节跳动",
  "jobTitle": "高级前端工程师",
  "emailVerificationToken": "base64url.payload.signature"
}
```

说明：
- `displayName` 继续作为账号昵称 / 外显名。
- `realName` 用于真实姓名记录；若未传，后端会回落为 `displayName`。
- `companyName`、`jobTitle` 当前供 `MENTOR` / `ENTERPRISE` 的注册资料阶段与后续自助资料编辑复用。
- 当前正式主链路中，`STUDENT` 继续使用本接口；`MENTOR` / `ENTERPRISE` 在统一认证页展开的注册资料阶段应改走 `2.1.1 注册并提交认证资料`，以便一次性创建账号、写入基础认证身份并提交正式附件。
- 当邮箱验证码服务已启用时，本接口要求携带第一步通过 `/auth/email/verify-code` 换取的 `emailVerificationToken`；否则返回 `AUTH-1014` 或 `AUTH-1015`。

成功：
```json
{
  "code": "OK",
  "message": "registered",
  "data": {"userId": 1001},
  "traceId": "trc_auth_001",
  "timestamp": "2026-03-02T12:00:02Z"
}
```

错误：
```json
{
  "code": "AUTH-1001",
  "message": "email already exists",
  "data": null,
  "traceId": "trc_auth_002",
  "timestamp": "2026-03-02T12:00:03Z"
}
```

### 2.1.1 注册并提交认证资料
- `POST /auth/register-with-certification`
- 鉴权：否
- 内容类型：`multipart/form-data`
- 适用角色：`MENTOR`、`ENTERPRISE`

表单字段：
- `role`
- `email`
- `password`
- `displayName`
- `realName`
- `companyName`
- `jobTitle`
- `emailVerificationToken`
- `file`

说明：
- 本接口用于导师 / 企业在统一认证页展开的注册资料阶段一次性完成“账号创建 + 认证身份写入 + 附件上传 + 首次认证提交”。
- 提交成功后，导师 / 企业资料的 `approvalStatus` 会被置为 `PENDING`。
- 附件对象会进入正式认证资料存储目录，注册主链路不再保留独立的 auth debug 上传接口。
- 当邮箱验证码服务已启用时，本接口同样要求携带第一步换取到的 `emailVerificationToken`。

成功：
```json
{
  "code": "OK",
  "message": "registered",
  "data": {
    "userId": 2001,
    "role": "MENTOR",
    "approvalStatus": "PENDING",
    "currentSubmission": {
      "submissionId": 7001,
      "userId": 2001,
      "role": "MENTOR",
      "realName": "王老师",
      "companyName": "字节跳动",
      "jobTitle": "高级前端工程师",
      "status": "PENDING",
      "current": true,
      "reviewNote": null,
      "previousSubmissionId": null,
      "submittedAt": "2026-03-17T06:10:00Z",
      "reviewedAt": null,
      "assets": [
        {
          "assetId": 9001,
          "bucket": "bishe-assets",
          "objectKey": "certification/mentor/20260317/20260317061000-register-proof.pdf",
          "originalFilename": "mentor-proof.pdf",
          "contentType": "application/pdf",
          "sizeBytes": 102400,
          "lifecycleStatus": "ACTIVE",
          "deleteReason": null,
          "uploadedAt": "2026-03-17T06:10:00Z",
          "deletedAt": null
        }
      ]
    }
  }
}
```

### 2.2 登录
- `POST /auth/login`
- 鉴权：否

请求：
```json
{
  "email": "alice@example.com",
  "password": "Passw0rd!"
}
```

成功：
```json
{
  "code": "OK",
  "message": "logged in",
  "data": {
    "accessToken": "jwt_access",
    "refreshToken": "jwt_refresh",
    "role": "STUDENT"
  },
  "traceId": "trc_auth_003",
  "timestamp": "2026-03-02T12:00:04Z"
}
```

错误：
```json
{
  "code": "AUTH-1002",
  "message": "invalid credentials",
  "data": null,
  "traceId": "trc_auth_004",
  "timestamp": "2026-03-02T12:00:05Z"
}
```

### 2.2.1 忘记密码：发送验证码
- `POST /auth/password/reset/send-code`
- 鉴权：否

请求：
```json
{
  "email": "alice@example.com",
  "captchaVerificationToken": "base64url.payload.signature"
}
```

说明：
- 登录页“忘记密码”弹层先调用本接口发送 6 位验证码。
- 当前忘记密码发码与注册页共用同一套 Geetest proof 机制：前端应先调用 `POST /auth/captcha/geetest/verify`，再把返回的 `captchaVerificationToken` 透传到本接口。
- `captchaVerificationToken` 字段本身为可选字段，但当 `GET /auth/captcha/config` 返回 `enabled=true` 时，前端应视为必传；未通过人机校验时，后端会拒绝发码。
- 当前直接复用学生资料中心的安全验证码基础设施；若邮件服务未配置，开发态仍会返回 `debugCode` 便于本地联调。

成功：
```json
{
  "code": "OK",
  "message": "password reset code sent",
  "data": {
    "sent": true,
    "stage": "PASSWORD_RESET",
    "targetEmail": "alice@example.com",
    "deliveryChannel": "EMAIL",
    "expiresAt": "2026-03-26T20:30:00Z",
    "nextSendAt": "2026-03-26T20:21:00Z",
    "debugCode": null
  },
  "traceId": "trc_auth_pwd_001",
  "timestamp": "2026-03-26T20:20:00Z"
}
```

错误：
- `AUTH-1008`：Geetest 凭证无效或已过期
- `AUTH-1009`：Geetest 服务不可用
- `AUTH-1016`：目标邮箱未注册账号
- `BIZ-1405`：发送过于频繁
- `BIZ-1406`：邮件服务暂不可用

### 2.2.2 忘记密码：校验验证码
- `POST /auth/password/reset/verify-code`
- 鉴权：否

请求：
```json
{
  "email": "alice@example.com",
  "code": "314159"
}
```

成功：
```json
{
  "code": "OK",
  "message": "password reset code verified",
  "data": {
    "verified": true,
    "verificationToken": "base64url.payload.signature",
    "expiresAt": "2026-03-26T20:40:00Z"
  },
  "traceId": "trc_auth_pwd_002",
  "timestamp": "2026-03-26T20:22:00Z"
}
```

错误：
- `BIZ-1407`：验证码错误或已过期

### 2.2.3 忘记密码：更新密码
- `POST /auth/password/reset/change`
- 鉴权：否

请求：
```json
{
  "email": "alice@example.com",
  "passwordResetToken": "base64url.payload.signature",
  "newPassword": "Passw0rd!2026"
}
```

成功：
```json
{
  "code": "OK",
  "message": "password reset changed",
  "data": {
    "updated": true
  },
  "traceId": "trc_auth_pwd_003",
  "timestamp": "2026-03-26T20:24:00Z"
}
```

错误：
- `BIZ-1409`：重置凭证已失效

### 2.3 刷新令牌
- `POST /auth/refresh`
- 鉴权：否

请求：
```json
{
  "refreshToken": "jwt_refresh"
}
```

成功：
```json
{
  "code": "OK",
  "message": "refreshed",
  "data": {
    "accessToken": "jwt_access_new",
    "refreshToken": "jwt_refresh_new",
    "role": "STUDENT"
  },
  "traceId": "trc_auth_004a",
  "timestamp": "2026-03-05T14:00:00Z"
}
```

错误：
```json
{
  "code": "AUTH-1003",
  "message": "token expired",
  "data": null,
  "traceId": "trc_auth_004b",
  "timestamp": "2026-03-05T14:00:01Z"
}
```

### 2.4 当前用户
- `GET /auth/me`
- 鉴权：是

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "userId": 1001,
    "role": "STUDENT",
    "displayName": "Alice",
    "email": "alice@example.com"
  },
  "traceId": "trc_auth_005",
  "timestamp": "2026-03-02T12:00:06Z"
}
```

错误：
```json
{
  "code": "AUTH-1003",
  "message": "token expired",
  "data": null,
  "traceId": "trc_auth_006",
  "timestamp": "2026-03-02T12:00:07Z"
}
```

### 2.5 学生画像（冷启动 + 动态 + 资料中心）
#### 2.5.1 获取我的学生画像
- `GET /profiles/students/me`
- 鉴权：`STUDENT`
- 说明：
  - 返回当前学生资料中心的完整展示数据，除基础资料、隐私矩阵与动态画像外，还会额外返回账号层级 `tier` 与头像元数据 `avatar`，供 hero 身份徽章与头像编辑器使用。
  - `schoolName` 为学生学校标签展示值；后续“同校学生”可见性与社区同校动作统一基于该字段及其后端归一化 key 演进。
  - `socialLinks` 为新的受控外部主页账号列表，平台当前限制在 `GITHUB / PORTFOLIO / GITEE / JUEJIN / CSDN / ZHIHU / BILIBILI / XIAOHONGSHU / WEIBO`；`github` 与 `portfolio` 继续保留为兼容镜像字段，便于旧前端或历史消费方平滑过渡。
  - 当前画像真相层仍以本地规则与结构化证据聚合为主，不走“LLM 直接生成画像事实”的链路。
  - 当前正式接口中的 `portrait` 仍只保证 `tags / evidence / updatedAt`；`headline / summary / nextActions` 等表达层字段属于后续 V2 规划，暂不构成当前契约。

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "userId": 1001,
    "displayName": "Alice",
    "email": "alice@example.com",
    "tier": "FREE",
    "completionRate": 92,
    "realName": "王小明",
    "jobStatus": "🟢 积极找工作",
    "schoolName": "华东理工大学",
    "major": "计算机科学与技术",
    "grade": "大三",
    "gpa": "3.8 / 4.0",
    "targetPosition": "Java 后端开发",
    "honors": "2023年 国家励志奖学金",
    "github": "github.com/alice",
    "portfolio": "alice.dev",
    "socialLinks": [
      { "platform": "GITHUB", "value": "github.com/alice" },
      { "platform": "PORTFOLIO", "value": "alice.dev" },
      { "platform": "JUEJIN", "value": "alice_frontend" }
    ],
    "phone": "13800138000",
    "wechat": "alice_job",
    "skillTags": ["Java", "Spring Boot"],
    "selfIntro": "希望寻找后端实习，正在强化项目能力。",
    "avatar": {
      "uploaded": true,
      "contentType": "image/jpeg",
      "updatedAt": "2026-03-20T12:00:00Z"
    },
    "privacy": {
      "realName": { "guest": false, "student": true, "platformStudent": true, "mentor": true, "enterprise": true },
      "jobStatus": { "guest": true, "student": true, "platformStudent": true, "mentor": true, "enterprise": true },
      "eduInfo": { "guest": true, "student": true, "platformStudent": true, "mentor": true, "enterprise": true },
      "targetPos": { "guest": true, "student": true, "platformStudent": true, "mentor": true, "enterprise": true },
      "academic": { "guest": false, "student": true, "platformStudent": true, "mentor": true, "enterprise": true },
      "skills": { "guest": true, "student": true, "platformStudent": true, "mentor": true, "enterprise": true },
      "intro": { "guest": true, "student": true, "platformStudent": true, "mentor": true, "enterprise": true },
      "social": { "guest": true, "student": true, "platformStudent": true, "mentor": true, "enterprise": true },
      "email": { "guest": false, "student": false, "platformStudent": false, "mentor": true, "enterprise": true },
      "phone": { "guest": false, "student": false, "platformStudent": false, "mentor": true, "enterprise": true },
      "wechat": { "guest": false, "student": false, "platformStudent": false, "mentor": true, "enterprise": true },
      "portrait": { "guest": false, "student": true, "platformStudent": true, "mentor": true, "enterprise": true }
    },
    "portrait": {
      "tags": [
        {"code": "SKILL_JAVA_MASTERED", "label": "Java基础稳固", "source": "SKILL_PROGRESS", "confidence": 0.91},
        {"code": "COMMUNITY_ACTIVE", "label": "社区互动积极", "source": "COMMUNITY", "confidence": 0.78}
      ],
      "evidence": {
        "masteredSkills": 3,
        "learningSkills": 2,
        "interviewMessages7d": 6,
        "posts7d": 2,
        "comments7d": 5,
        "likesReceived7d": 11
      },
      "updatedAt": "2026-03-05T08:00:00Z"
    },
    "communityScore7d": 25
  },
  "traceId": "trc_profile_001",
  "timestamp": "2026-03-05T08:00:01Z"
}
```

#### 2.5.2 更新我的学生资料（含冷启动字段）
- `PUT /profiles/students/me`
- 鉴权：`STUDENT`
- 说明：
  - `selfIntro` 当前按资料中心模板收口为最多 200 个字符。
  - `PUT` 语义按整份资料更新处理；当 `realName` 传空字符串时，后端会清空真实姓名记录。
  - 外部主页区推荐优先传 `socialLinks`；`github` / `portfolio` 当前仍可继续传入，后端会自动兼容并回填到新的受控列表结构。
  - `portraitRefreshTriggered=true` 当前表示后端已触发或已尝试触发画像刷新；后续若画像刷新切入异步任务底座，该字段语义将继续保持为“已进入刷新流程”，而不是“前台已拿到最终新画像”。

请求：
```json
{
  "realName": "王小明",
  "jobStatus": "🟢 积极找工作",
  "schoolName": "华东理工大学",
  "major": "软件工程",
  "grade": "2025届",
  "gpa": "3.8 / 4.0",
  "targetPosition": "前端开发工程师",
  "honors": "2023年 国家励志奖学金\n2022年 蓝桥杯省赛一等奖",
  "socialLinks": [
    { "platform": "GITHUB", "value": "alice" },
    { "platform": "PORTFOLIO", "value": "alice.dev" },
    { "platform": "CSDN", "value": "alice_notes" }
  ],
  "phone": "13800138000",
  "wechat": "alice_job",
  "skillTags": ["React", "TypeScript", "Node.js", "工程化"],
  "selfIntro": "希望通过真实项目训练提升工程能力。"
}
```

成功：
```json
{
  "code": "OK",
  "message": "profile updated",
  "data": {
    "updated": true,
    "portraitRefreshTriggered": true
  },
  "traceId": "trc_profile_002",
  "timestamp": "2026-03-05T08:00:10Z"
}
```

错误（角色不匹配）：
```json
{
  "code": "AUTH-1004",
  "message": "permission denied",
  "data": null,
  "traceId": "trc_profile_003",
  "timestamp": "2026-03-05T08:00:11Z"
}
```

#### 2.5.3 学生画像 V2 当前接口状态与异步刷新规划
当前 `GET /profiles/students/me` 的 `portrait` 已正式返回：

- `tags`
- `strengthTags`
- `riskTags`
- `signalLevel`
- `freshnessLevel`
- `headline`
- `summary`
- `nextActions`
- `summaryVersion`
- `evidence`
- `updatedAt`

当前实现约束：
- 画像真相层继续由本地规则生成；表达层默认采用模板化总结，后续可选低频 LLM 润色。
- 当前 `student.portrait.summary.mode=LLM` 时，也只会在高价值触发链路中低频启用，不会在页面读取、社区轻互动或凌晨批刷新时实时外发。

异步任务仍属于下一阶段规划：
- 画像刷新将优先复用 `ai_async_task_jobs + ai_async_task_events` 底座，规划态建议新增：
  - `taskType=PORTRAIT_REFRESH`
  - `taskType=PORTRAIT_SUMMARY`
  - `sceneCode=STUDENT_PORTRAIT_REFRESH`
  - `sceneCode=STUDENT_PORTRAIT_SUMMARY`
- 当前阶段不新增公开的“学生画像异步任务提交接口”；画像任务仍以内域事件触发和后台任务去重为主。

账号与资料中心配套接口：
- `POST /profiles/students/me/avatar`
  - 鉴权：`STUDENT`
  - 请求体：`multipart/form-data`，字段名固定为 `file`
  - 支持 JPG / PNG 原图上传，前端可先裁成 1:1；后端收到后会再次做中心裁切、缩放和 JPEG 压缩，再写入 MinIO。
  - 成功后返回标准化后的 `contentType`、`sizeBytes` 与 `updatedAt`，用于前端立即刷新头像状态。
- `GET /profiles/students/me/avatar`
  - 鉴权：`STUDENT`
  - 返回当前登录学生自己的头像二进制内容，当前为 `image/jpeg` 原始响应，不包裹 `ApiResponse`。
  - 该接口仅用于“查看我的头像”，不提供公开静态 URL。
- `PUT /profiles/students/me/privacy`
  - 鉴权：`STUDENT`
  - 请求体为 `privacy` 对象，结构与 `GET /profiles/students/me` 返回的 `privacy` 一致。
  - 当前 `privacy.student` 表示“同校学生”角色维度；后续资料消费页会基于双方 `schoolName` 归一化后的学校标签判断是否属于同校范围。
  - `privacy.platformStudent` 表示“平台学生”角色维度，用于站内其他学生用户的更宽泛可见范围配置。
- `POST /profiles/students/me/security/email/send-code`
  - 鉴权：`STUDENT`
  - 请求体：`{"stage":"CURRENT"}` 或 `{"stage":"NEW","newEmail":"new@example.com"}`
  - 返回 `targetEmail`、`deliveryChannel`、`expiresAt`、`nextSendAt`；当本地未配置邮件服务时，`deliveryChannel` 为 `MOCK`，并额外返回 `debugCode` 便于联调。
  - 资料中心安全验证码会在有效期内由后端持久化保存，页面刷新后仍可继续校验。
- `POST /profiles/students/me/security/email/verify-current-code`
  - 鉴权：`STUDENT`
  - 请求体：`{"code":"123456"}`
  - 返回 `verificationToken`，用于后续提交邮箱换绑。
- `POST /profiles/students/me/security/email/change`
  - 鉴权：`STUDENT`
  - 请求体：`{"currentEmailVerificationToken":"...","newEmail":"new@example.com","newEmailCode":"123456"}`
  - 成功后更新 `users.email`，`/auth/me` 与 `GET /profiles/students/me` 会返回新邮箱。
- `POST /profiles/students/me/security/password/send-code`
  - 鉴权：`STUDENT`
  - 向当前绑定邮箱发送密码重置验证码，返回结构同发送邮箱验证码；验证码同样会在有效期内由后端持久化保存。
- `POST /profiles/students/me/security/password/verify-code`
  - 鉴权：`STUDENT`
  - 请求体：`{"code":"123456"}`
  - 返回 `verificationToken`，用于后续提交新密码。
- `POST /profiles/students/me/security/password/change`
  - 鉴权：`STUDENT`
  - 请求体：`{"passwordResetToken":"...","newPassword":"Passw0rd!"}`
  - 新密码规则与正式注册保持一致：8-64 位，且必须同时包含字母和数字。

#### 2.5.3 获取我的企业认证资料
- `GET /profiles/enterprises/me`
- 鉴权：`ENTERPRISE`
- 说明：
  - 当前企业资料页真实使用字段已包含 `industry`、`companySize`、`hiringTags`、`bio`、`externalLinks`、`preferences`。
  - 当前资料接口也已补齐企业 Logo 元数据：`logoUrl`、`logoConfigured`、`logoContentType`、`logoUpdatedAt`。
  - `bio` 为资料页必要说明字段；`externalLinks` 与 `preferences` 为轻量增强字段，用于承接模板中的“联系方式与外部链接”“招募偏好与学生提示”。

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "userId": 3001,
    "displayName": "字节校招",
    "realName": "王小明",
    "companyName": "字节跳动",
    "jobTitle": "招聘负责人",
    "industry": "互联网 / 软件服务",
    "companySize": "200-500 人",
    "hiringTags": ["算法工程", "校招合作", "前端产品化"],
    "bio": "我们专注企业智能化产品与校园合作项目，希望学生能在真实任务中形成作品集。",
    "externalLinks": "官网：https://example.com\n邮箱：campus@example.com",
    "preferences": "偏好有项目经历的同学，提交时建议附上仓库链接。",
    "logoUrl": "/api/v1/profiles/enterprises/3001/logo?v=1772445600000",
    "logoConfigured": true,
    "logoContentType": "image/png",
    "logoUpdatedAt": 1772445600000,
    "approvalStatus": "APPROVED"
  },
  "traceId": "trc_profile_004",
  "timestamp": "2026-03-16T14:20:01Z"
}
```

#### 2.5.4 更新我的企业认证资料
- `PUT /profiles/enterprises/me`
- 鉴权：`ENTERPRISE`
- 说明：
  - 支持部分更新；未传字段保持原值。
  - 当前企业资料页会通过本接口保存企业简介、联系方式与外部链接、招募偏好与学生提示。
  - 企业 Logo 不通过本接口上传，需使用独立 Logo 上传接口。

请求：
```json
{
  "realName": "王小明",
  "companyName": "字节跳动",
  "jobTitle": "招聘负责人",
  "industry": "互联网 / 软件服务",
  "companySize": "200-500 人",
  "hiringTags": ["算法工程", "校招合作", "前端产品化"],
  "bio": "我们专注企业智能化产品与校园合作项目，希望学生能在真实任务中形成作品集。",
  "externalLinks": "官网：https://example.com\n邮箱：campus@example.com",
  "preferences": "偏好有项目经历的同学，提交时建议附上仓库链接。"
}
```

成功：
```json
{
  "code": "OK",
  "message": "profile updated",
  "data": {
    "userId": 3001,
    "displayName": "字节校招",
    "realName": "王小明",
    "companyName": "字节跳动",
    "jobTitle": "招聘负责人",
    "industry": "互联网 / 软件服务",
    "companySize": "200-500 人",
    "hiringTags": ["算法工程", "校招合作", "前端产品化"],
    "bio": "我们专注企业智能化产品与校园合作项目，希望学生能在真实任务中形成作品集。",
    "externalLinks": "官网：https://example.com\n邮箱：campus@example.com",
    "preferences": "偏好有项目经历的同学，提交时建议附上仓库链接。",
    "logoUrl": "/api/v1/profiles/enterprises/3001/logo?v=1772445600000",
    "logoConfigured": true,
    "logoContentType": "image/png",
    "logoUpdatedAt": 1772445600000,
    "approvalStatus": "APPROVED"
  },
  "traceId": "trc_profile_005",
  "timestamp": "2026-03-16T14:20:12Z"
}
```

#### 2.5.5 上传或替换我的企业 Logo
- `POST /profiles/enterprises/me/logo`
- 鉴权：`ENTERPRISE`
- 请求：`multipart/form-data`
  - 字段：`file`
- 说明：
  - 当前企业资料页前端会先完成裁剪与预览，再通过本接口上传压缩后的 Logo。
  - 当前只接受图片文件；接口成功后会回写最新的公开 Logo 地址与更新时间。

成功：
```json
{
  "code": "OK",
  "message": "logo uploaded",
  "data": {
    "uploaded": true,
    "logoConfigured": true,
    "contentType": "image/png",
    "sizeBytes": 182340,
    "updatedAt": 1772445600000,
    "logoUrl": "/api/v1/profiles/enterprises/3001/logo?v=1772445600000"
  },
  "traceId": "trc_profile_006",
  "timestamp": "2026-04-08T12:10:31Z"
}
```

#### 2.5.6 获取我的企业 Logo / 获取企业公开 Logo
- `GET /profiles/enterprises/me/logo`
  - 鉴权：`ENTERPRISE`
  - 返回：当前登录企业自己的 Logo 原始内容
- `GET /profiles/enterprises/{enterpriseUserId}/logo`
  - 鉴权：公开可读
  - 返回：企业公开 Logo 原始内容
- 说明：
  - 前端通常会给公开 Logo 地址追加 `?v=<logoUpdatedAt>`，用于在 Logo 替换后刷新浏览器缓存。

#### 2.5.7 企业资料页密码修改链路
- `POST /profiles/enterprises/me/security/password/send-code`
  - 鉴权：`ENTERPRISE`
  - 向当前绑定邮箱发送密码重置验证码
- `POST /profiles/enterprises/me/security/password/verify-code`
  - 鉴权：`ENTERPRISE`
  - 请求体：`{"code":"123456"}`
  - 返回 `verificationToken`
- `POST /profiles/enterprises/me/security/password/change`
  - 鉴权：`ENTERPRISE`
  - 请求体：`{"passwordResetToken":"...","newPassword":"Passw0rd!"}`
  - 当前企业资料页的“修改密码”弹窗即走这一组接口

## 3. AI 工具接口
### 3.1 简历优化
- `POST /ai/resume/optimize`
- 鉴权：`STUDENT`

请求：
```json
{
  "targetRole": "Backend Engineer",
  "targetContext": "校招正式批",
  "jobDescription": "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与性能优化。",
  "resumeText": "负责订单中心核心接口开发与缓存优化。"
}
```

- 说明：当前简历优化结果统一输出中文，不再接收 `language` 参数。

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "recordId": 123,
    "summary": "简历整体方向较清晰，但仍建议补强量化成果与岗位贴合证据。",
    "strengths": ["项目经历与目标岗位方向较贴近"],
    "risks": ["部分成果描述仍缺少量化结果"],
    "suggestions": ["补 2-3 条体现业务结果的数据表达"],
    "scoreLabel": "B+",
    "structureItems": [
      {"label": "项目经历", "score": 4, "tip": "项目主线已较清晰，建议再补一条量化结果。"}
    ],
    "rewriteItems": [
      {
        "id": "rewrite-1",
        "title": "补强量化结果",
        "problem": "当前表述缺少业务结果与指标变化。",
        "beforeText": "负责订单中心后端接口开发与维护。",
        "afterText": "负责订单中心核心接口与缓存链路重构，通过索引优化与预热机制将接口延迟降低 30%。",
        "isHeuristic": false
      }
    ],
    "aiMeta": {
      "taskType": "RESUME",
      "provider": "provider_a",
      "model": "model_x",
      "latencyMs": 1820
    },
    "moderation": {
      "sourceType": "AI_OUTPUT",
      "riskLevel": "LOW",
      "action": "PASS",
      "reasonCode": "RULE_CLEAR"
    }
  },
  "traceId": "trc_ai_001",
  "timestamp": "2026-03-02T12:00:08Z"
}
```
- 说明：成功响应中的 `recordId` 即本次简历优化写入 `ai_call_logs` 后生成的历史记录 ID，前端可直接用于查询 `GET /ai/history/resume/{recordId}`；学生端当前默认基于历史详情数据在浏览器侧生成标准 PDF，若需要服务端文件流兼容能力，仍可调用 `GET /ai/resume/export/{recordId}`。

错误：
```json
{
  "code": "AI-2001",
  "message": "provider timeout",
  "data": {"fallback": "please retry"},
  "traceId": "trc_ai_002",
  "timestamp": "2026-03-02T12:00:09Z"
}
```

错误（输入拦截）：
```json
{
  "code": "MOD-1001",
  "message": "input blocked by moderation policy",
  "data": {
    "moderation": {
      "sourceType": "AI_INPUT",
      "riskLevel": "HIGH",
      "action": "BLOCK",
      "reasonCode": "POLICY_HIGH_RISK"
    }
  },
  "traceId": "trc_ai_002b",
  "timestamp": "2026-03-02T12:00:09Z"
}
```

### 3.1.1 PDF 简历优化
- `POST /ai/resume/optimize/pdf`
- 鉴权：`STUDENT`
- `Content-Type`：`multipart/form-data`

表单字段：
- `targetRole`：目标岗位，必填
- `targetContext`：目标语境，可选
- `jobDescription`：岗位 JD / 要求，可选
- `resumeFile`：PDF 简历文件，必填，仅支持 PDF

补充说明：
- 当前 PDF 简历优化结果统一输出中文，不再提供 `language` 表单字段。

实现约束（当前已落地）：
- 服务端不再先用本地 PDF 解析库抽取全文再调用模型。
- 当命中 Gemini Native 路由时，后端会按原生 `generateContent` 协议，把 PDF 文件内容通过 `contents[].parts[].inlineData.data(base64)` 直接上传。
- 当前仅使用 `inlineData` 直传，不使用 `fileData.fileUri` / File API。
- 成功响应结构与 `POST /ai/resume/optimize` 保持一致，除基础四段结果外，还会返回 `scoreLabel/structureItems/rewriteItems`。

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "recordId": 124,
    "summary": "简历方向较清晰，但建议补更多量化结果。",
    "strengths": ["项目经历贴近目标岗位"],
    "risks": ["部分成果表述仍偏笼统"],
    "suggestions": ["补 2-3 条可量化结果描述"],
    "scoreLabel": "B+",
    "structureItems": [
      {"label": "岗位匹配度", "score": 4, "tip": "与目标岗位较贴合。"}
    ],
    "rewriteItems": [
      {
        "id": "rewrite-1",
        "title": "补强岗位匹配",
        "problem": "与岗位 JD 的职责映射不够直接。",
        "beforeText": "已上传 PDF 简历，建议围绕目标岗位进一步补强代表性经历。",
        "afterText": "建议围绕岗位 JD 中最核心的一条职责，补一段“场景 + 动作 + 结果”的结果导向表达。",
        "isHeuristic": true
      }
    ],
    "aiMeta": {
      "taskType": "RESUME",
      "provider": "GEMINI_NATIVE",
      "model": "gemini-2.5-flash",
      "latencyMs": 1820
    },
    "moderation": {
      "sourceType": "AI_OUTPUT",
      "riskLevel": "LOW",
      "action": "PASS",
      "reasonCode": "RULE_CLEAR"
    }
  },
  "traceId": "trc_ai_pdf_001",
  "timestamp": "2026-03-08T13:00:00Z"
}
```

错误：
```json
{
  "code": "BIZ-1001",
  "message": "resume file must be pdf",
  "data": null,
  "traceId": "trc_ai_pdf_002",
  "timestamp": "2026-03-08T13:00:01Z"
}
```

### 3.1.2 简历优化异步任务
- `POST /ai/resume/tasks`
- 鉴权：`STUDENT`
- 说明：
  - 该接口用于提交离线型简历分析任务，当前支持 `application/json` 文本模式与 `multipart/form-data` PDF 模式两种提交方式。
  - 文本模式请求体与 `POST /ai/resume/optimize` 保持一致；PDF 模式表单字段与 `POST /ai/resume/optimize/pdf` 保持一致。
  - 提交阶段会先执行 AI 输入审查；命中 `MOD-1001` 时不会入队。
  - 成功提交后返回 `202 Accepted`，并生成 `taskId`；前端随后通过 `GET /ai/tasks/{taskId}` 轮询状态。
  - 当前简历异步任务底层使用数据库表 `ai_async_task_jobs + ai_async_task_events`，worker 轮询执行后仍会复用既有 `AiPracticeService -> AiQuotaService -> AiGatewayService` 链路，因此成功结果会继续写入 `ai_call_logs`，并返回可直接跳转复盘中心的 `recordId`。
  - 同一套异步任务底座后续也将优先承接学生画像刷新 / 画像总结的后台重算，但当前尚未对外暴露画像专属任务接口。

文本模式成功：
```json
{
  "code": "OK",
  "message": "accepted",
  "data": {
    "taskId": "aitk_1234567890abcdef",
    "taskType": "RESUME",
    "sceneCode": "RESUME_OPTIMIZE",
    "executionMode": "ASYNC_JOB",
    "status": "PENDING",
    "createdAt": "2026-03-23T09:00:00Z"
  },
  "traceId": "trc_ai_resume_async_001",
  "timestamp": "2026-03-23T09:00:00Z"
}
```

任务详情：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "taskId": "aitk_1234567890abcdef",
    "taskType": "RESUME",
    "sceneCode": "RESUME_OPTIMIZE",
    "routeCode": "SYSTEM_RESUME_OPTIMIZE",
    "executionMode": "ASYNC_JOB",
    "status": "SUCCEEDED",
    "terminal": true,
    "currentAttempt": 1,
    "maxAttempts": 3,
    "providerCode": "SYSTEM_RESUME_GEMINI_NATIVE",
    "providerType": "GEMINI_NATIVE",
    "modelName": "gemini-2.5-flash",
    "promptTemplateName": "RESUME_OPTIMIZE_CORE",
    "promptTemplateVersionNo": 1,
    "resultSummary": "简历方向较清晰，但建议补充更多量化结果。",
    "resultPayload": {
      "recordId": 123,
      "summary": "简历方向较清晰，但建议补充更多量化结果。",
      "strengths": ["项目经历贴近目标岗位"],
      "risks": ["部分成果表述仍偏笼统"],
      "suggestions": ["补 2-3 条可量化结果描述"]
    },
    "linkedRecordId": 123,
    "errorCode": null,
    "errorMessage": null,
    "nextRunAt": "2026-03-23T09:00:00Z",
    "queuedAt": "2026-03-23T09:00:00Z",
    "startedAt": "2026-03-23T09:00:02Z",
    "finishedAt": "2026-03-23T09:00:08Z",
    "createdAt": "2026-03-23T09:00:00Z",
    "updatedAt": "2026-03-23T09:00:08Z"
  },
  "traceId": "trc_ai_resume_async_002",
  "timestamp": "2026-03-23T09:00:08Z"
}
```

失败说明：
- 若任务在执行阶段因配额不足、路由缺失、provider 失败或输出审查阻断而失败，`GET /ai/tasks/{taskId}` 会返回 `status=FAILED`，并在 `errorCode/errorMessage` 中携带失败原因。
- 当前未单独提供“任务专属 SSE 事件流”；提交页仍默认通过轮询 `GET /ai/tasks/{taskId}` 刷新状态。
- 但 AI 简历异步任务成功 / 失败现已同步写入统一通知中心，并会按用户偏好进入 WebSocket 实时推送与浏览器桌面提醒；前端可通过 `/notifications` 或顶栏通知铃铛补偿进入 `/ai/resume/review?taskId=...`。

### 3.1.3 AI 联调（配额 / 路由探针）
- `POST /ai/ping`
- 鉴权：`STUDENT`
- 说明：
  - 用于联调 AI 网关、配额策略与模型路由是否按预期生效。
  - 本接口会按 `taskType` 执行真实配额 / 积分校验，并写入 AI 调用日志，不是纯探活接口。
  - `taskType` 为空时，默认按 `INTERVIEW_TEXT` 处理。

请求：
```json
{
  "taskType": "INTERVIEW_TEXT",
  "scene": "dashboard"
}
```

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "service": "server-java-ai-module",
    "module": "ai/gateway",
    "time": "2026-03-08T13:10:00Z",
    "taskType": "INTERVIEW_TEXT",
    "tier": "FREE",
    "freeCall": true,
    "chargedPoints": 0,
    "pointsBalance": 120,
    "usedToday": 2,
    "provider": "mock-provider",
    "model": "mock-economy-model",
    "scene": "dashboard"
  },
  "traceId": "trc_ai_ping_001",
  "timestamp": "2026-03-08T13:10:00Z"
}
```

错误（额度不足）：
```json
{
  "code": "AI-2201",
  "message": "额度不足，完成任务赚取积分",
  "data": null,
  "traceId": "trc_ai_ping_002",
  "timestamp": "2026-03-08T13:10:01Z"
}
```

### 3.1.1 查询模拟面试入口开关
- `GET /ai/interview/entry-options`
- 鉴权：`STUDENT`

说明：
- 当前用于前端在进入 `/ai/interview` 前决定是否展示“语音回答”和 `live` 实时语音测试模式入口。
- 目前后端实现中，`voiceAnswerEnabled` 与 `liveInterviewEnabled` 共用同一个运行开关：当运行管理关闭语音面试时，这两个入口会同时关闭。
- 本接口只返回入口可见性，不创建会话，也不替代后续正式的 `/ai/interview/sessions` 会话创建。

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "voiceAnswerEnabled": true,
    "liveInterviewEnabled": true
  },
  "traceId": "trc_ai_interview_entry_001",
  "timestamp": "2026-04-18T12:00:00Z"
}
```

### 3.2 创建面试会话（文本 / 语音 / Live 测试模式）
- `POST /ai/interview/sessions`
- 鉴权：`STUDENT`
- 计费说明：创建时会一次性预扣整场面试会话的固定额度（当前文本、`stt / tts` 语音与 `live` 测试模式共用 `INTERVIEW_TEXT` 会话包）；会话开始后，默认不再对每次追问/总结逐次扣分。

请求：
```json
{
  "targetRole": "Backend Engineer",
  "mode": "INTERVIEW_TEXT"
}
```

Live 测试模式创建示例：
```json
{
  "targetRole": "Backend Engineer",
  "mode": "INTERVIEW_TEXT",
  "sessionContext": {
    "interviewType": "PROJECT_DEEP_DIVE",
    "interviewerStyle": "STANDARD",
    "difficulty": "MEDIUM",
    "answerMode": "LIVE",
    "targetCompany": "字节跳动",
    "promptContext": "技能关键词：Java / Redis / MySQL"
  }
}
```

成功：
```json
{
  "code": "OK",
  "message": "session created",
  "data": {
    "sessionId": "is_10001",
    "mode": "INTERVIEW_TEXT",
    "firstQuestion": "请先介绍一个与你目标岗位最相关、且结果可量化的项目。",
    "chargedPoints": 25,
    "pointsBalanceAfterReserve": 40,
    "quotaUnitsReserved": 5,
    "replyRoundLimit": 30,
    "moderation": {
      "sourceType": "AI_OUTPUT",
      "riskLevel": "LOW",
      "action": "PASS",
      "reasonCode": "RULE_CLEAR"
    }
  },
  "traceId": "trc_ai_003",
  "timestamp": "2026-03-02T12:00:10Z"
}
```

说明：
- `mode` 当前继续固定传 `INTERVIEW_TEXT`；实际作答方式通过 `sessionContext.answerMode` 区分，当前支持 `TEXT`、`VOICE`、`LIVE`。
- 当 `sessionContext.answerMode=LIVE` 时，Java 后端只创建占位会话并完成积分/配额预留，`data.firstQuestion` 返回空字符串；前端随后需连接独立 Python WebSocket bridge，再在结束时把 transcript 导回正式会话。

### 3.2.1 查询文本面试会话详情
- `GET /ai/interview/sessions/{sessionId}`
- 鉴权：`STUDENT`

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "sessionId": "is_10001",
    "targetRole": "Backend Engineer",
    "mode": "INTERVIEW_TEXT",
    "status": "COMPLETED",
    "prepaidPoints": 25,
    "reservedQuotaWeight": 5,
    "pointsBalance": 80,
    "replyRoundLimit": 30,
    "replyRoundUsed": 2,
    "endedByAi": false,
    "finishReason": "USER_FINISHED",
    "createdAt": 1772787600000,
    "summaryGeneratedAt": 1772788200000,
    "sessionContext": {
      "interviewType": "PROJECT_DEEP_DIVE",
      "interviewerStyle": "STANDARD",
      "difficulty": "MEDIUM",
      "answerMode": "LIVE",
      "targetCompany": "字节跳动",
      "targetJobDescription": null,
      "prepMaterialKeys": ["LATEST_RESUME"],
      "answerHelperEnabled": false,
      "answerHelperCueKeys": [],
      "promptContext": "技能关键词：Java / Redis / MySQL"
    },
    "resumeContext": null,
    "messages": [
      {
        "role": "ASSISTANT",
        "text": "先请你用一分钟介绍一下你做过的订单中心项目。",
        "scoreHint": null,
        "audioObjectKey": null,
        "createdAt": "2026-03-08T13:20:00Z"
      },
      {
        "role": "USER",
        "text": "我主要负责订单中心后端服务改造，重点做了缓存治理、索引优化和压测回归。",
        "scoreHint": 82,
        "audioObjectKey": null,
        "createdAt": "2026-03-08T13:22:00Z"
      },
      {
        "role": "ASSISTANT",
        "text": "这里面的核心技术取舍是什么？为什么不是直接继续堆机器？",
        "scoreHint": null,
        "audioObjectKey": null,
        "createdAt": "2026-03-08T13:23:10Z"
      },
      {
        "role": "USER",
        "text": "因为当时瓶颈主要是热点查询和慢 SQL，先做缓存与索引优化能更快收敛问题，最终接口延迟降低了 30%。",
        "scoreHint": 84,
        "audioObjectKey": null,
        "createdAt": "2026-03-08T13:24:00Z"
      }
    ],
    "summary": {
      "overallScore": 82,
      "strengths": ["项目背景清晰"],
      "weaknesses": ["容量预估细节偏少"],
      "suggestions": ["补充压测数据与容量估算过程"],
      "aiMeta": {
        "taskType": "INTERVIEW_SUMMARY",
        "provider": "provider_a",
        "model": "model_x"
      },
      "moderation": {
        "sourceType": "AI_OUTPUT",
        "riskLevel": "LOW",
        "action": "PASS",
        "reasonCode": "RULE_CLEAR"
      }
    }
  },
  "traceId": "trc_ai_session_001",
  "timestamp": "2026-03-08T13:28:00Z"
}
```

说明：
- `mode` 在当前实现中仍保持 `INTERVIEW_TEXT`；若需要判断是否为 `live` 测试模式，应读取 `sessionContext.answerMode`。
- `sessionContext` 与 `resumeContext` 为本轮创建时固化的准备配置快照，供 `/ai/interview/session`、`/ai/interview/review` 与 `/ai/history` 恢复时直接回显。

### 3.2.2 删除文本面试历史
- `DELETE /ai/interview/sessions/{sessionId}`
- 鉴权：`STUDENT`
- 说明：逻辑删除该会话历史，使其不再出现在 AI 历史列表中。

成功：
```json
{
  "code": "OK",
  "message": "deleted",
  "data": null,
  "traceId": "trc_ai_session_002",
  "timestamp": "2026-03-08T13:29:00Z"
}
```

### 3.3 面试会话回复
- `POST /ai/interview/sessions/{sessionId}/reply`
- 鉴权：`STUDENT`

请求：
```json
{
  "answerText": "I built a task scheduling service..."
}
```

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "followUpQuestion": "How did you handle failure retry?",
    "scoreHint": 78,
    "aiMeta": {
      "taskType": "INTERVIEW_TEXT",
      "provider": "provider_a",
      "model": "model_x"
    },
    "moderation": {
      "sourceType": "AI_OUTPUT",
      "riskLevel": "LOW",
      "action": "PASS",
      "reasonCode": "RULE_CLEAR"
    }
  },
  "traceId": "trc_ai_005",
  "timestamp": "2026-03-02T12:00:12Z"
}
```

### 3.4 面试总结报告
- `POST /ai/interview/sessions/{sessionId}/summary`
- 鉴权：`STUDENT`

请求：无请求体（sessionId 在路径中）

成功：
```json
{
  "code": "OK",
  "message": "summary generated",
  "data": {
    "overallScore": 78,
    "strengths": ["项目经历描述清晰", "技术细节有深度"],
    "weaknesses": ["缺少量化指标", "系统设计思维待加强"],
    "suggestions": ["用数据体现业务影响", "练习系统设计题可用 DDIA 为参考"],
    "aiMeta": {
      "taskType": "INTERVIEW_SUMMARY",
      "provider": "provider_a",
      "model": "model_x"
    },
    "moderation": {
      "sourceType": "AI_OUTPUT",
      "riskLevel": "LOW",
      "action": "PASS",
      "reasonCode": "RULE_CLEAR"
    }
  },
  "traceId": "trc_ai_sum_001",
  "timestamp": "2026-03-02T12:00:20Z"
}
```

### 3.4.1 导入 Live 实时面试 transcript（测试模式）
- `POST /ai/interview/sessions/{sessionId}/live-transcript`
- 鉴权：`STUDENT`
- 说明：仅在创建会话时 `sessionContext.answerMode=LIVE` 的测试模式下可用；前端在结束实时语音后，把整理好的 `USER / ASSISTANT` transcript 导回 Java 正式会话，再继续调用 `/summary` 生成复盘。

请求：
```json
{
  "messages": [
    { "role": "ASSISTANT", "text": "先请你用一分钟介绍一下你做过的订单中心项目。" },
    { "role": "USER", "text": "我主要负责订单中心后端服务改造，重点做了缓存治理、索引优化和压测回归。" },
    { "role": "ASSISTANT", "text": "这里面的核心技术取舍是什么？为什么不是直接继续堆机器？" },
    { "role": "USER", "text": "因为当时瓶颈主要是热点查询和慢 SQL，先做缓存与索引优化能更快收敛问题，最终接口延迟降低了 30%。" }
  ]
}
```

成功：
```json
{
  "code": "OK",
  "message": "live transcript imported",
  "data": null,
  "traceId": "trc_ai_live_001",
  "timestamp": "2026-04-16T10:03:00Z"
}
```

约束：
- `messages` 必须按实际发生顺序传入；当前最多 240 条。
- `role` 仅支持 `USER`、`ASSISTANT`。
- 导回后消息会写入正式 `interview_messages`，`replyRoundUsed` 也会按 `USER` 回答轮次重算。

### 3.5 语音面试往返
- `POST /ai/interview/sessions/{sessionId}/voice-roundtrip`
- 鉴权：`STUDENT`
- `Content-Type`：`multipart/form-data`
- 表单字段：`audioFile`（浏览器录音文件或本地音频文件）

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "transcript": "我负责订单中心性能治理，结合缓存和索引优化把接口延迟降低了 30%。",
    "audioObjectKey": "upload://interview/is_10001/1710000000000-answer.webm",
    "transcriptMeta": {
      "taskType": "STT",
      "provider": "openai-compatible",
      "model": "gpt-4o-mini-transcribe",
      "latencyMs": 812
    },
    "followUpQuestion": "这些结果是通过什么指标体系和观测手段验证出来的？",
    "coachFeedback": "亮点是给出了量化结果，下一轮可以再补技术权衡与验证方式。",
    "scoreHint": 82,
    "shouldFinish": false,
    "finishReason": null,
    "sessionStatus": "ACTIVE",
    "summary": null,
    "aiMeta": {
      "taskType": "INTERVIEW_TEXT",
      "provider": "openai-compatible",
      "model": "gpt-4o-mini",
      "latencyMs": 1330
    },
    "moderation": {
      "sourceType": "AI_OUTPUT",
      "riskLevel": "LOW",
      "action": "PASS",
      "reasonCode": "RULE_CLEAR"
    }
  },
  "traceId": "trc_ai_007",
  "timestamp": "2026-03-07T12:00:14Z"
}
```

说明：
- 当前最小闭环为 `音频上传/录音 -> STT 转写 -> LLM 追问/点评 -> 总结结果页`。
- 这是当前正式落地的 `stt / tts` 语音面试主链路；`live` 测试模式是独立的 WebSocket 实时语音链路，不替代本接口。
- 当前版本不落对象存储音频文件；如需播报 AI 追问，可调用 `POST /ai/tts/synthesize` 获取 Gemini 返回的 base64 PCM 音频，并在前端适配为可播放 WAV。
- 用户消息会在会话历史中持久化为转写文本，并记录 `audioObjectKey` 便于后续接入真实对象存储。

错误（自动回退）：
```json
{
  "code": "AI-2102",
  "message": "stt failed, fallback to text mode",
  "data": {"fallbackMode": "INTERVIEW_TEXT"},
  "traceId": "trc_ai_008",
  "timestamp": "2026-03-07T12:00:15Z"
}
```

### 3.5.1 文本转语音
- `POST /ai/tts/synthesize`
- 鉴权：`STUDENT`
- `Content-Type`：`application/json`

请求：
```json
{
  "text": "你好，这是一个用于测试 TTS 的文本。",
  "stylePrompt": "Read aloud in a warm and friendly tone:",
  "voiceName": "Zephyr"
}
```

成功：
```json
{
  "code": "OK",
  "message": "tts synthesized",
  "data": {
    "text": "你好，这是一个用于测试 TTS 的文本。",
    "stylePrompt": "Read aloud in a warm and friendly tone:",
    "voiceName": "Zephyr",
    "mimeType": "audio/L16;codec=pcm;rate=24000",
    "sampleRate": 24000,
    "audioBase64": "xxx",
    "aiMeta": {
      "taskType": "TTS",
      "provider": "GEMINI_TTS",
      "model": "gemini-2.5-flash-preview-tts",
      "latencyMs": 912
    }
  },
  "traceId": "trc_ai_009",
  "timestamp": "2026-03-08T13:00:10Z"
}
```

说明：
- 请求体沿用 Gemini 原生 `generateContent` 音频模式：文本部分可用自然语言控制说话风格、语气、口音与语速。
- 返回 `audioBase64` 为 base64 编码的 PCM 数据，当前前端按 `audio/L16;codec=pcm;rate=24000` 适配为可播放 WAV。

错误（自动回退）：
```json
{
  "code": "AI-2103",
  "message": "tts failed, fallback to text",
  "data": {"fallbackMode": "TEXT"},
  "traceId": "trc_ai_010",
  "timestamp": "2026-03-08T13:00:11Z"
}
```

### 3.5.2 Gemini Live 测试模式 WebSocket bridge（sidecar）
- 说明：以下接口由独立 Python 应用 `apps/interview-live-python` 提供，不属于 Java `/api/v1` 正式 API；当前仅服务于 `/ai/interview` 的 `live` 测试模式。
- 开发态代理：Vite 将 `/ai/interview/live/**` 代理到 `http://127.0.0.1:8765`，并开启 WebSocket 透传。

健康检查：
- `GET /ai/interview/live/healthz`

示例响应：
```json
{
  "ok": true,
  "model": "gemini-3.1-flash-live-preview",
  "wsPath": "/ai/interview/live/ws",
  "apiKeyConfigured": true
}
```

实时连接：
- `WS /ai/interview/live/ws`

浏览器常见上行事件：
- `session.start`
- `audio.chunk`
- `audio.stream_end`
- `user.text`
- `session.finish`
- `session.close`

浏览器常见下行事件：
- `server.status`
- `server.ready`
- `turn.user.transcript`
- `turn.model.transcript`
- `turn.model.audio`
- `usage.update`
- `turn.complete`
- `session.reconnecting`
- `error`

说明：
- 该 bridge 只负责与 Gemini Live 的实时双向音频 / 字幕 / token 事件交互，不直接持久化 transcript。
- 当前 `live` 手动重连会开启新的 Gemini Live 会话；页面已保留本地 transcript，但服务端暂未做上下文续接回放。

### 3.6 社区预回答
- `POST /ai/community/pre-answer`
- 鉴权：`STUDENT`、`MENTOR`

请求：
```json
{
  "postId": 9001,
  "title": "How to prepare for Java backend interview?",
  "content": "..."
}
```

成功：
```json
{
  "code": "OK",
  "message": "generated",
  "data": {
    "draftComment": "Start with JVM basics, then practice project storytelling...",
    "tag": "AI_GENERATED",
    "moderation": {
      "sourceType": "AI_OUTPUT",
      "riskLevel": "LOW",
      "action": "PASS",
      "reasonCode": "RULE_CLEAR"
    }
  },
  "traceId": "trc_ai_009",
  "timestamp": "2026-03-02T12:00:16Z"
}
```

### 3.7 破冰私信生成
- `POST /ai/icebreak-message`
- 鉴权：`STUDENT`

请求：
```json
{
  "mentorId": 2001,
  "studentGoal": "Need guidance on intern preparation"
}
```

成功：
```json
{
  "code": "OK",
  "message": "generated",
  "data": {
    "messageDraft": "Hello, I am preparing for backend intern interviews...",
    "moderation": {
      "sourceType": "AI_OUTPUT",
      "riskLevel": "LOW",
      "action": "PASS",
      "reasonCode": "RULE_CLEAR"
    }
  },
  "traceId": "trc_ai_011",
  "timestamp": "2026-03-02T12:00:18Z"
}
```

### 3.8 AI 使用历史
- `GET /ai/history`
- 鉴权：`STUDENT`
- 参数：`page`、`size`、`taskType`（可选，当前支持 `RESUME`、`INTERVIEW_TEXT`）

说明：
- 学生工作台“继续事项”会用 `page=1&size=1` 读取最新一条 AI 结果。
- 前端复盘中心路由 `/ai/history` 会基于通知或页面回流 query 处理 `type=resume&recordId=...`、`type=resume&taskId=...`、`type=interview&sessionId=...` 三类选中态；其中 `taskId` 需要继续配合 `GET /ai/tasks/{taskId}` 做异步任务状态收口。

成功：
```json
{
  "code": "OK",
  "data": {
    "records": [
      {
        "id": 12,
        "taskType": "INTERVIEW_TEXT",
        "summary": "文本面试 - Backend Engineer（已完成）",
        "pointsConsumed": 25,
        "sessionId": "is_10001",
        "status": "COMPLETED",
        "createdAt": "2026-03-02T10:05:00Z"
      },
      {
        "id": 8,
        "taskType": "RESUME",
        "summary": "简历优化",
        "pointsConsumed": 0,
        "createdAt": "2026-03-02T10:00:00Z"
      }
    ],
    "total": 2,
    "page": 1,
    "size": 10
  }
}
```

#### 3.8.1 简历优化历史详情
- `GET /ai/history/resume/{recordId}`
- 鉴权：`STUDENT`

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "recordId": 8,
    "summary": "简历已具备后端岗位基础，建议进一步补齐量化成果。",
    "strengths": ["项目经历贴近目标岗位"],
    "risks": ["量化结果较少"],
    "suggestions": ["补充接口性能优化或业务结果指标"],
    "scoreLabel": "B+",
    "structureItems": [
      {"label": "项目经历", "score": 4, "tip": "项目主线已较清晰。"}
    ],
    "rewriteItems": [
      {
        "id": "rewrite-1",
        "title": "补强量化结果",
        "problem": "当前表述缺少业务结果与指标变化。",
        "beforeText": "负责订单中心后端接口与缓存优化。",
        "afterText": "负责订单中心核心接口与缓存优化，通过索引与预热策略将接口延迟下降 30%。",
        "isHeuristic": false
      }
    ],
    "targetRole": "Backend Engineer",
    "targetContext": "校招正式批",
    "inputMode": "text",
    "jobDescription": "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与性能优化。",
    "resumeText": "负责订单中心后端接口与缓存优化，接口延迟下降 30%。",
    "pdfFileName": null,
    "pointsConsumed": 0,
    "createdAt": "2026-03-02T10:00:00Z",
    "aiMeta": {
      "taskType": "RESUME",
      "provider": "provider_a",
      "model": "model_x",
      "latencyMs": 1620
    },
    "moderation": {
      "sourceType": "AI_OUTPUT",
      "riskLevel": "LOW",
      "action": "PASS",
      "reasonCode": "RULE_CLEAR"
    }
  },
  "traceId": "trc_ai_hist_001",
  "timestamp": "2026-03-08T13:35:00Z"
}
```

#### 3.8.2 删除简历优化历史
- `DELETE /ai/history/resume/{recordId}`
- 鉴权：`STUDENT`
- 说明：逻辑删除该条简历优化历史。

成功：
```json
{
  "code": "OK",
  "message": "deleted",
  "data": null,
  "traceId": "trc_ai_hist_002",
  "timestamp": "2026-03-08T13:36:00Z"
}
```

### 3.9 AI 配额查询
- `GET /ai/quota/remaining`
- 鉴权：`STUDENT`

成功：
```json
{
  "code": "OK",
  "data": {
    "tier": "FREE",
    "quotas": [
      {"taskType": "RESUME", "dailyFreeLimit": 3, "usedToday": 1, "remaining": 2},
      {"taskType": "INTERVIEW_TEXT", "dailyFreeLimit": 5, "usedToday": 0, "remaining": 5}
    ],
    "pointsBalance": 120
  }
}
```

### 3.10 简历优化报告 PDF 导出
- `GET /ai/resume/export/{recordId}`
- 鉴权：`STUDENT`
- 响应：`Content-Type: application/pdf`
- 响应头：`Content-Disposition: attachment; filename="resume-report-{recordId}.pdf"`
- 说明：基于已保存的简历优化历史生成 PDF 文件流。
- 前端当前主链路说明：
  - 学生端页面默认使用 `GET /ai/history/resume/{recordId}` 取回结构化详情，并在浏览器侧通过标准 PDF 渲染方案生成下载文件，以确保多页分页与横向分析卡布局稳定。
  - 本接口仍保留为服务端文件流导出能力与兼容回退路径。

### 3.11 SSE 流式输出（已落地：简历优化 + 面试真流式）
- 已落地接口：
  - `POST /ai/resume/optimize/stream`
  - `POST /ai/interview/sessions/{sessionId}/reply/stream`
  - `POST /ai/interview/sessions/{sessionId}/voice-roundtrip/stream`
- 鉴权：`STUDENT`
- 响应：`Content-Type: text/event-stream`
- 简历优化仍采用“服务端分段推送最终结果”的演示型 SSE：`start -> summary -> strengths -> risks -> suggestions -> done`。
- 简历优化的 `done.result` 与同步接口返回结构保持一致，当前也会包含 `recordId`，前端可直接精确绑定本次导出，不必再刷新历史后猜测“最新一条”。
- 面试文本/语音已升级为真流式文字返回：
  - `reply/stream` 会推送 `start -> reply_delta -> done/error`
  - `voice-roundtrip/stream` 会推送 `start -> transcript -> reply_delta -> done/error`
- Gemini Live 测试模式不走 Java SSE；浏览器通过 sidecar `WS /ai/interview/live/ws` 与 Python bridge 双向实时通信，结束后再用 `live-transcript + summary` 回到 Java 正式链路。
- provider 策略：OpenAI-compatible route 优先走 provider 原生 SSE/token 流；其他 provider 当前允许回退到服务端单次结果后再以 SSE 返回，接口契约保持一致。
- 面试真流式的增量内容仅对 `followUpQuestion` 做字段级提取并推送；`coachFeedback / scoreHint / shouldFinish / finishReason / summary` 仍在最终 `done.result` 中一次性返回。
- 流式治理策略：推流过程中先做 AI 输出 preview 审查，不写审计事件；完成后再对最终结果执行正式 AI 输出审查并落库，避免高频 delta 刷爆治理日志。

SSE 事件示例：
```
event: start
data: {"traceId": "trc_ai_stream_001", "message": "已接收回答，AI 正在流式生成追问。"}

event: reply_delta
data: {"traceId": "trc_ai_stream_001", "delta": "请继续介绍你如何做容量预估？"}

event: transcript
data: {"traceId": "trc_ai_stream_002", "transcript": "我主导了订单中心容量治理和缓存改造。", "audioObjectKey": "upload://interview/is_10001/1710000000000-answer.webm", "transcriptMeta": {"taskType": "STT", "provider": "provider_stt", "model": "model_stt", "latencyMs": 860}}

event: done
data: {"traceId": "trc_ai_stream_001", "message": "流式输出完成。", "result": {"followUpQuestion": "请继续介绍你如何做容量预估？", "coachFeedback": "建议补充容量评估依据。", "scoreHint": 82, "shouldFinish": false, "sessionStatus": "ACTIVE", "aiMeta": {"taskType": "INTERVIEW_TEXT", "provider": "provider_a", "model": "model_x", "latencyMs": 1820}, "moderation": {"sourceType": "AI_OUTPUT", "riskLevel": "LOW", "action": "PASS", "reasonCode": "RULE_CLEAR"}}}
```

## 4. 技能与积分接口
### 4.1 获取技能树
- `GET /skills/tree`
- 鉴权：`STUDENT`

说明：
- 返回单棵以 `programming_language_foundations` 为中心根节点的技能主树，适配当前技能星图的大树化布局。
- `relations` 用于补充跨树联动关系，当前关系类型为 `CO_LEARN`、`ADVANCE_TO`、`BRIDGE`。
- `nodes[].resources` 为节点详情面板使用的推荐资源，当前直接由数据库表 `skill_node_resources` 维护。
- `updatedAt` 使用 UTC epoch 毫秒；`summary` 为当前学生账号的技能进度汇总。

成功：
```json
{
  "code": "OK",
  "data": {
    "nodes": [
      {
        "nodeCode": "programming_language_foundations",
        "label": "计算机科学成长底座",
        "description": "作为整张技能星图的中央枢纽，把技术基础、工程实践、职业发展和成长韧性汇成一棵主树。",
        "parentCode": null,
        "sortOrder": 100,
        "status": "MASTERED",
        "unlocked": true,
        "updatedAt": 1775041200000,
        "resources": [
          {
            "id": "ossu-cs-roadmap",
            "type": "doc",
            "title": "OSSU Computer Science 路线图",
            "source": "OSSU",
            "time": "长期参考",
            "link": "https://github.com/ossu/computer-science",
            "sortOrder": 10
          },
          {
            "id": "cs50",
            "type": "video",
            "title": "CS50 计算机科学导论",
            "source": "Harvard",
            "time": "课程系列",
            "link": "https://cs50.harvard.edu/x/",
            "sortOrder": 20
          }
        ]
      },
      {
        "nodeCode": "java_programming",
        "label": "Java 与工程语言实践",
        "description": "掌握面向对象语法、标准库和工程开发里最常见的 Java 基础能力。",
        "parentCode": "programming_language_foundations",
        "sortOrder": 300,
        "status": "LEARNING",
        "unlocked": true,
        "updatedAt": 1775044800000,
        "resources": []
      },
      {
        "nodeCode": "object_oriented_modeling",
        "label": "对象建模与职责拆分",
        "description": "把类、接口、对象协作与职责边界设计得更稳定、更清晰。",
        "parentCode": "java_programming",
        "sortOrder": 310,
        "status": "NOT_STARTED",
        "unlocked": true,
        "updatedAt": null,
        "resources": []
      }
    ],
    "relations": [
      {
        "sourceNodeCode": "frontend_component_architecture",
        "targetNodeCode": "api_contract_design",
        "relationType": "CO_LEARN",
        "label": "前后端协作时适合同步补接口契约",
        "sortOrder": 50
      }
    ],
    "summary": {
      "total": 105,
      "mastered": 1,
      "learning": 1,
      "notStarted": 103
    }
  }
}
```

### 4.2 更新技能进度
- `POST /skills/progress`
- 鉴权：`STUDENT`

请求：
```json
{
  "nodeId": "programming_language_foundations",
  "targetStatus": "MASTERED"
}
```

成功：
```json
{"code": "OK", "message": "progress updated"}
```

### 4.3 每日签到
- `POST /growth/checkin`
- 鉴权：`STUDENT`

说明：
- 基础签到积分固定为 `10`。
- 当连续签到天数命中当前启用的奖励规则时，会额外发放连签奖励积分。
- 奖励规则当前存储于 `growth_checkin_reward_rules`，后续可由管理员后台维护。

成功：
```json
{
  "code": "OK",
  "data": {
    "streak": 7,
    "pointsEarned": 24,
    "checkinDate": "2026-03-19",
    "basePointsEarned": 10,
    "bonusPointsEarned": 14,
    "newBalance": 186,
    "triggeredReward": {
      "rewardCode": "CHECKIN_STREAK_7",
      "title": "七日连签奖励",
      "description": "连续签到 7 天可额外获得 14 积分。",
      "streakDays": 7,
      "bonusPoints": 14
    }
  }
}
```

错误（重复签到）：
```json
{"code": "BIZ-1301", "message": "already checked in today"}
```

### 4.4 签到总览
- `GET /growth/checkin/overview`
- 鉴权：`STUDENT`

说明：
- 用于学生工作台读取本月签到日历、当前有效连签天数、基础签到积分、连签奖励规则与下一档奖励提示。
- `growthJourneyDays` 按学生账号创建日累计，包含创建当日，用于工作台 Hero 区显示“成长计划第 N 天”。
- 当前有效连签天数口径：最近签到日期为今天或昨天时保留 streak；若昨天也未签到，则 streak 视为 0。

成功：
```json
{
  "code": "OK",
  "data": {
    "signedInToday": false,
    "growthJourneyDays": 42,
    "currentStreak": 5,
    "latestCheckinDate": "2026-03-18",
    "currentMonth": "2026-03",
    "daysInCurrentMonth": 31,
    "checkedInDays": [7, 8, 9, 10, 11, 12, 18],
    "basePointsPerDay": 10,
    "rewardRules": [
      {
        "rewardCode": "CHECKIN_STREAK_3",
        "title": "三日连签奖励",
        "description": "连续签到 3 天可额外获得 6 积分。",
        "streakDays": 3,
        "bonusPoints": 6
      },
      {
        "rewardCode": "CHECKIN_STREAK_7",
        "title": "七日连签奖励",
        "description": "连续签到 7 天可额外获得 14 积分。",
        "streakDays": 7,
        "bonusPoints": 14
      }
    ],
    "nextReward": {
      "rewardCode": "CHECKIN_STREAK_7",
      "title": "七日连签奖励",
      "description": "连续签到 7 天可额外获得 14 积分。",
      "streakDays": 7,
      "bonusPoints": 14,
      "remainingDays": 2
    }
  }
}
```

### 4.5 每日任务列表
- `GET /growth/tasks/daily`
- 鉴权：`STUDENT`

成功：
```json
{"code": "OK", "data": [{"taskId": 1, "title": "完成一次简历优化", "points": 10, "completed": false}]}
```

### 4.6 完成任务
- `POST /growth/tasks/{taskId}/complete`
- 鉴权：`STUDENT`

成功：
```json
{"code": "OK", "data": {"pointsEarned": 10, "newBalance": 130}}
```

### 4.7 积分账本
- `GET /growth/points/ledger`
- 鉴权：`STUDENT`
- 参数：`page`、`size`

成功：
```json
{"code": "OK", "data": {"balance": 130, "records": [{"deltaPoints": 10, "reasonCode": "CHECKIN", "balanceAfter": 130, "createdAt": "2026-03-04T08:00:00Z"}], "total": 25}}
```

## 5. 社区接口
### 5.1 发帖
- `POST /community/posts`
- 鉴权：`STUDENT`、`MENTOR`

请求：
```json
{"title": "Java 后端面试技巧", "content": "...", "tags": ["面经分享", "技术讨论"]}
```

成功：
```json
{
  "code": "OK",
  "data": {
    "postId": 9001,
    "moderation": {
      "sourceType": "COMMUNITY_POST",
      "riskLevel": "LOW",
      "action": "PASS",
      "reasonCode": "RULE_CLEAR"
    },
    "aiFirstCommentCreated": true,
    "aiFirstCommentId": 7001
  }
}
```

说明：
- 当功能开关 `community.ai-pre-answer.enabled=true` 且帖子直接通过审核时，系统会尝试自动生成 AI 一楼评论。
- 若 AI 生成或出站治理失败，不影响帖子主流程，接口仍返回发帖成功。

错误（进入待审）：
```json
{
  "code": "MOD-1003",
  "message": "post pending moderation review",
  "data": {
    "postId": 9002,
    "moderation": {
      "sourceType": "COMMUNITY_POST",
      "riskLevel": "MEDIUM",
      "action": "REVIEW",
      "reasonCode": "POLICY_REVIEW_REQUIRED"
    }
  }
}
```

### 5.2 帖子列表
- `GET /community/posts`
- 鉴权：`STUDENT`、`MENTOR`、`ADMIN`
- 参数：`page`、`size`、`keyword`（可选）、`tag`（可选）

### 5.3 帖子详情
- `GET /community/posts/{postId}`

说明：
- `comments[].ai` 用于标记 AI 评论；自动生成的一楼回答会返回 `ai=true`。
- 当前实现会把 AI 首评作者显示为 `AI 助手`，便于前端直接做徽标区分。

### 5.4 发评论
- `POST /community/posts/{postId}/comments`

成功：
```json
{
  "code": "OK",
  "data": {
    "commentId": 7001,
    "moderation": {
      "sourceType": "COMMUNITY_COMMENT",
      "riskLevel": "LOW",
      "action": "PASS",
      "reasonCode": "RULE_CLEAR"
    }
  }
}
```

错误（命中高风险拦截）：
```json
{
  "code": "MOD-1001",
  "message": "comment blocked by moderation policy",
  "data": {
    "moderation": {
      "sourceType": "COMMUNITY_COMMENT",
      "riskLevel": "HIGH",
      "action": "BLOCK",
      "reasonCode": "POLICY_HIGH_RISK"
    }
  }
}
```

### 5.5 点赞
- `POST /community/posts/{postId}/like`

### 5.6 取消点赞
- `DELETE /community/posts/{postId}/like`
- 鉴权：`STUDENT`、`MENTOR`

### 5.7 创建举报
- `POST /community/reports`
- 鉴权：所有已登录用户

请求：
```json
{
  "targetType": "POST",
  "targetId": "9001",
  "reasonCode": "ABUSE",
  "detail": "包含明显不当内容"
}
```

成功：
```json
{
  "code": "OK",
  "message": "report submitted",
  "data": {
    "reportId": 5001,
    "status": "PENDING"
  }
}
```

错误（频率受限）：
```json
{
  "code": "MOD-1004",
  "message": "report rate limited",
  "data": null
}
```

### 5.8 我的举报列表
- `GET /community/reports/mine`
- 鉴权：所有已登录用户
- 参数：`page`、`size`、`status`（可选）

成功：
```json
{
  "code": "OK",
  "data": {
    "records": [
      {
        "reportId": 5001,
        "targetType": "POST",
        "targetId": "9001",
        "status": "ACCEPTED",
        "createdAt": "2026-03-04T10:00:00Z",
        "updatedAt": "2026-03-04T10:30:00Z"
      }
    ],
    "total": 1
  }
}
```

### 5.9 社区贡献榜（7日）
- `GET /community/leaderboard`
- 鉴权：所有已登录用户
- 参数：`window`（固定 `7d`）、`page`、`size`

成功：
```json
{
  "code": "OK",
  "data": {
    "window": "7d",
    "formula": "post*5 + comment*2 + like*1",
    "records": [
      {
        "rank": 1,
        "studentUserId": 1001,
        "displayName": "Alice",
        "score": 39,
        "postCount": 3,
        "commentCount": 8,
        "likeReceivedCount": 8,
        "latestActivityAt": "2026-03-05T09:12:00Z"
      }
    ],
    "total": 20,
    "page": 1,
    "size": 10
  }
}
```

## 6. 导师浏览接口
### 6.1 导师列表
- `GET /mentors`
- 鉴权：`STUDENT`
- 参数：
  - `page`、`size`
  - `keyword`（可选，支持导师昵称 / 公司 / 职位 / 简介 / 擅长标签 / 服务场景模糊搜索）
  - `expertise`（标签筛选，可选）
  - `scene`（问题场景筛选，可选，固定集合与导师广场专项 spec 一致）
  - `minPrice`、`maxPrice`
  - `available`（是否仅看可接单）
  - `favorited`（是否仅看当前学生已收藏导师）

成功：
```json
{
  "code": "OK",
  "data": {
    "records": [
      {
        "userId": 2001,
        "displayName": "Dr. Wang",
        "companyName": "字节跳动",
        "jobTitle": "高级前端工程师",
        "avatarUrl": "https://api.dicebear.com/7.x/notionists/svg?seed=Dr.Wang",
        "expertiseTags": ["Java后端", "系统设计"],
        "serviceScenes": ["简历诊断", "项目表达"],
        "bio": "专注校招求职辅导与简历诊断。",
        "priceFen": 5000,
        "avgRating": 4.80,
        "totalOrders": 12,
        "available": true,
        "favorited": false
      }
    ],
    "total": 8,
    "page": 1,
    "size": 10
  }
}
```

说明：
- `avatarUrl` 为导师公开头像地址；若导师尚未手动配置头像，服务端会返回基于昵称生成的占位头像 URL。
- `favorited` 为当前登录学生视角下的便捷字段，便于导师广场在列表、预览与推荐区实时同步收藏状态。

### 6.2 导师推荐结果
- `GET /mentors/recommendations`
- 鉴权：`STUDENT`
- 参数：与 `GET /mentors` 基本一致，当前支持 `keyword`、`expertise`、`scene`、`minPrice`、`maxPrice`、`available`、`favorited`

成功：
```json
{
  "code": "OK",
  "data": {
    "scene": "简历诊断",
    "basisSummary": "本次推荐基于「问题场景、目标岗位、技能标签、AI 画像标签」综合生成。",
    "weakSignal": false,
    "basisTags": ["问题场景：简历诊断", "目标岗位：前端开发工程师", "技能：React"],
    "records": [
      {
        "userId": 2001,
        "displayName": "Dr. Wang",
        "companyName": "字节跳动",
        "jobTitle": "高级前端工程师",
        "avatarUrl": "https://api.dicebear.com/7.x/notionists/svg?seed=Dr.Wang",
        "expertiseTags": ["前端工程化", "React"],
        "serviceScenes": ["简历诊断", "项目表达"],
        "bio": "专注校招求职辅导与简历诊断。",
        "priceFen": 19900,
        "avgRating": 4.90,
        "totalOrders": 128,
        "available": true,
        "favorited": false,
        "score": 95,
        "reasons": ["匹配你的「简历诊断」问题场景", "与你的目标岗位高度相关", "评分与咨询量都比较稳定"],
        "risk": null,
        "explainText": "匹配你的「简历诊断」问题场景，并且他的公开服务场景里明确覆盖了「简历诊断」。"
      }
    ]
  }
}
```

说明：
- 当前推荐采用“规则加权排序 + 可解释文案”策略，不做纯黑盒随机排榜。
- `weakSignal=true` 表示学生画像信息不足，本次结果更多依赖公共导师数据和当前手动筛选条件。

### 6.3 我的收藏导师摘要
- `GET /mentors/favorites`
- 鉴权：`STUDENT`

成功：
```json
{
  "code": "OK",
  "data": {
    "total": 2,
    "mentorUserIds": [2001, 2002]
  }
}
```

### 6.4 导师详情
- `GET /mentors/{mentorUserId}`
- 鉴权：`STUDENT`
- 说明：
  - 当前学生侧 `/mentors` 右侧详情区会直接消费该接口。
  - 除基础名片字段外，当前导师详情还会返回 `suitableFor`、`notSuitableFor`、`prepMaterials`、`replyRhythm` 四个公开说明字段，供学生在下单前快速判断匹配度与准备要求。

成功：
```json
{
  "code": "OK",
  "data": {
    "userId": 2001,
    "displayName": "Dr. Wang",
    "companyName": "字节跳动",
    "jobTitle": "高级前端工程师",
    "avatarUrl": "https://api.dicebear.com/7.x/notionists/svg?seed=Dr.Wang",
    "expertiseTags": ["Java后端", "系统设计"],
    "serviceScenes": ["简历诊断", "项目表达"],
    "bio": "专注校招求职辅导与简历诊断。",
    "suitableFor": "适合已经有 1-2 段项目经历、准备冲刺后端实习的同学。",
    "notSuitableFor": "不适合希望从零开始系统学习 Java 语法的同学。",
    "prepMaterials": "请提前准备最新简历、目标岗位 JD 与最担心被问到的问题。",
    "replyRhythm": "工作日晚间统一回复，周末可补充长文本建议。",
    "priceFen": 8800,
    "avgRating": 4.80,
    "totalOrders": 12,
    "available": true,
    "favorited": false,
    "recentReviews": [
      {
        "orderNo": "ORD20260308001",
        "studentDisplayName": "Student A",
        "rating": 5,
        "comment": "导师建议很具体，可直接执行。",
        "createdAt": "2026-03-08T10:00:00Z"
      }
    ]
  }
}
```

说明：
- `recentReviews` 默认返回最近 3 条咨询评价；无评价时返回空数组。
- 若订单评价已因退款售后被移除，则不会再出现在 `recentReviews` 中。
- `favorited` 仍为当前登录学生视角下的收藏状态便捷字段。
- `suitableFor`、`notSuitableFor`、`prepMaterials`、`replyRhythm` 当前已作为学生侧详情页的正式公开说明字段，不再只是导师资料页私有编辑字段。

### 6.5 收藏 / 取消收藏导师
- `POST /mentors/{mentorUserId}/favorite`
- `DELETE /mentors/{mentorUserId}/favorite`
- 鉴权：`STUDENT`

成功：
```json
{
  "code": "OK",
  "data": {
    "mentorUserId": 2001,
    "favorited": true,
    "totalFavorites": 3
  }
}
```

### 6.6 咨询准备单智能草稿
- `POST /mentors/prep-sheet/generate`
- 鉴权：`STUDENT`

请求：
```json
{
  "mentorUserId": 2001,
  "scene": "简历诊断",
  "targetPosition": "前端开发工程师"
}
```

成功：
```json
{
  "code": "OK",
  "data": {
    "mentorUserId": 2001,
    "mentorDisplayName": "Dr. Wang",
    "mentorCompanyName": "字节跳动",
    "mentorJobTitle": "高级前端工程师",
    "scene": "简历诊断",
    "targetPosition": "前端开发工程师",
    "summaryDraft": "我目前正在准备「前端开发工程师」方向的求职，希望围绕「简历诊断」做一次更聚焦的咨询。",
    "coreQuestions": [
      "针对「前端开发工程师」岗位，我的简历里哪些内容最需要优先补强？",
      "我现有项目经历应该怎样改写，才能更突出技术深度和结果价值？",
      "导师视角下，企业在初筛时最容易卡住我的问题是什么？"
    ],
    "suggestedMaterials": ["我的最新简历", "目标岗位 JD", "项目介绍"],
    "expectedOutcomes": ["获得简历修改建议", "获得综合咨询建议"],
    "signalTags": ["问题场景：简历诊断", "目标岗位：前端开发工程师"]
  }
}
```

说明：
- 当前服务端返回的是“可编辑草稿”，前端仍需允许学生继续手动修改与本地保存。
- 当前草稿生成优先读取学生资料、AI 画像标签、当前问题场景与导师擅长方向。

### 6.7 导师工作台概览
- `GET /mentor/dashboard`
- 鉴权：`MENTOR`

### 6.8 导师本人资料
- `GET /mentor/profile`
- `PUT /mentor/profile`
- 鉴权：`MENTOR`
- 说明：
  - `GET /mentor/profile` 返回导师本人资料，当前包含 `displayName`、`realName`、`companyName`、`jobTitle`、`avatarUrl`、`avatarConfigured`、`avatarContentType`、`avatarUpdatedAt`、`expertiseTags`、`serviceScenes`、`bio`、`suitableFor`、`notSuitableFor`、`prepMaterials`、`replyRhythm`、`priceFen`、`avgRating`、`totalOrders`、`available`、`approvalStatus` 等字段。
  - `PUT /mentor/profile` 支持部分更新；当前可更新字段包括 `realName`、`companyName`、`jobTitle`、`avatarUrl`（兼容保留）、`expertiseTags`、`serviceScenes`、`bio`、`suitableFor`、`notSuitableFor`、`prepMaterials`、`replyRhythm`、`priceFen`、`available`。
  - `displayName`、`avgRating`、`totalOrders`、`approvalStatus` 当前仍按只读展示处理，不属于资料页可编辑字段。
  - `avatarUrl` 当前统一返回可直接展示的公开地址；若导师已上传头像对象，则优先映射到 `GET /mentors/{mentorUserId}/avatar`，便于学生侧页面直接使用 `img src` 渲染。

请求示例：
```json
{
  "realName": "王老师",
  "companyName": "字节跳动",
  "jobTitle": "高级前端工程师",
  "expertiseTags": ["前端工程化", "项目表达", "大厂面试辅导"],
  "serviceScenes": ["简历诊断", "项目表达"],
  "bio": "8 年前端开发与带人经验，擅长帮助学生把项目经历讲清楚。",
  "suitableFor": "适合希望冲刺前端校招、梳理项目亮点的同学。",
  "notSuitableFor": "不适合零基础从语法开始的长期系统课。",
  "prepMaterials": "请提前准备简历、目标岗位 JD 与项目介绍。",
  "replyRhythm": "工作日晚间集中回复，周末可安排额外加急答复。",
  "priceFen": 19900,
  "available": true
}
```

`GET /mentor/profile` 成功示例：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "userId": 2001,
    "displayName": "王老师",
    "realName": "王某某",
    "companyName": "字节跳动",
    "jobTitle": "高级前端工程师",
    "avatarUrl": "/api/v1/mentors/2001/avatar",
    "avatarConfigured": true,
    "avatarContentType": "image/jpeg",
    "avatarUpdatedAt": "2026-03-24T08:20:00Z",
    "expertiseTags": ["前端工程化", "项目表达", "大厂面试辅导"],
    "serviceScenes": ["简历诊断", "项目表达", "模拟面试复盘"],
    "bio": "8 年前端开发与带人经验，擅长帮助学生把项目经历讲清楚。",
    "suitableFor": "适合希望冲刺前端校招、梳理项目亮点的同学。",
    "notSuitableFor": "不适合零基础从语法开始的长期系统课。",
    "prepMaterials": "请提前准备简历、目标岗位 JD 与项目介绍。",
    "replyRhythm": "工作日晚间集中回复，周末可安排额外加急答复。",
    "priceFen": 19900,
    "avgRating": 4.8,
    "totalOrders": 17,
    "available": true,
    "approvalStatus": "APPROVED"
  }
}
```

### 6.8.1 导师头像上传与公开读取
- `POST /mentor/profile/avatar`
- `GET /mentors/{mentorUserId}/avatar`
- 鉴权：
  - `POST /mentor/profile/avatar`：`MENTOR`
  - `GET /mentors/{mentorUserId}/avatar`：公开可读
- 内容类型：
  - `POST /mentor/profile/avatar`：`multipart/form-data`
- 说明：
  - `POST /mentor/profile/avatar` 用于导师上传或替换本人头像，字段名固定为 `file`。
  - 上传链路会对原图做正方形裁切、尺寸压缩与 JPEG 标准化，再把对象写入 MinIO；测试环境可回退进程内存存储。
  - 上传成功后会返回 `avatarUrl`，该地址可直接被导师资料页、导师广场与学生侧详情页复用。
  - `GET /mentors/{mentorUserId}/avatar` 直接返回图片二进制内容，供浏览器无鉴权渲染；当前会附带公开缓存头。

上传成功示例：
```json
{
  "code": "OK",
  "message": "avatar uploaded",
  "data": {
    "uploaded": true,
    "contentType": "image/jpeg",
    "sizeBytes": 182344,
    "updatedAt": "2026-03-24T08:20:00Z",
    "avatarUrl": "/api/v1/mentors/2001/avatar"
  }
}
```

### 6.8.2 导师 / 企业正式认证资料
- `GET /certification/me`
- `POST /certification/me`
- `GET /certification/assets/{assetId}/content`
- 鉴权：
  - `GET /certification/me`、`POST /certification/me`：`MENTOR`、`ENTERPRISE`
  - `GET /certification/assets/{assetId}/content`：`ADMIN`、`MENTOR`、`ENTERPRISE`

说明：
- `GET /certification/me` 返回当前账号的认证状态、当前有效提交与历史提交版本。
- `POST /certification/me` 使用 `multipart/form-data`，用于导师 / 企业登录后重新提交认证资料。
- 若用户重新提交新文件，后端会：
  - 创建新的 `certification_submissions` 版本；
  - 将旧 current submission 标记为 `REPLACED`；
  - 将旧附件标记为 `REPLACED / REPLACED_BY_NEW_SUBMISSION`，以便继续保留历史提交的材料回看能力。
- `GET /certification/assets/{assetId}/content` 用于当前用户本人或管理员读取当前或历史认证附件内容。
- 当前允许读取的附件生命周期为 `ACTIVE` 与 `REPLACED`；企业 / 导师资料页的“当前有效提交摘要”“历史提交记录区”都会复用该接口。

`GET /certification/me` 成功示例：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "userId": 2001,
    "role": "MENTOR",
    "approvalStatus": "REJECTED",
    "currentSubmission": {
      "submissionId": 7002,
      "userId": 2001,
      "role": "MENTOR",
      "realName": "王老师",
      "companyName": "字节跳动",
      "jobTitle": "高级前端工程师",
      "status": "REJECTED",
      "current": true,
      "reviewNote": "请补充更清晰的在职证明",
      "previousSubmissionId": 7001,
      "submittedAt": "2026-03-18T03:00:00Z",
      "reviewedAt": "2026-03-18T05:00:00Z",
      "assets": []
    },
    "submissions": []
  }
}
```

### 6.8.3 导师财务中心模拟提现
- `GET /mentor/finance/withdrawals`
- `POST /mentor/finance/withdrawals`
- `POST /mentor/finance/withdrawals/{withdrawalId}/status`
- 鉴权：`MENTOR`
- 说明：
  - 当前提现链路继续保持毕业设计范围内的模拟流程，但申请与状态变更已经接入服务端持久化，不再依赖浏览器本地缓存。
  - `GET /mentor/finance/withdrawals` 返回当前导师的模拟提现记录列表。
  - `POST /mentor/finance/withdrawals` 用于创建新的模拟提现申请；`amountFen` 必须大于 0，且不能超过当前可提现额度。
  - `POST /mentor/finance/withdrawals/{withdrawalId}/status` 当前用于演示 `PENDING -> PROCESSING -> COMPLETED / REJECTED` 与 `PENDING -> CANCELED` 的状态流转。

创建提现请求示例：
```json
{
  "amountFen": 6000,
  "note": "第一笔模拟提现申请"
}
```

查询成功示例：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "amountFen": 6000,
        "status": "PENDING",
        "createdAt": "2026-03-26T12:00:00Z",
        "updatedAt": "2026-03-26T12:00:00Z",
        "note": "第一笔模拟提现申请"
      }
    ]
  }
}
```

### 6.5 查询导师公开可预约时段
- `GET /mentor/schedule/slots`
- 鉴权：`STUDENT`、`MENTOR`
- 参数：`mentorUserId`（必填）、`dateFrom`（可选，ISO-8601 UTC）、`dateTo`（可选，ISO-8601 UTC）
- 说明：仅返回 `AVAILABLE` 时段，供学生下单选择。

### 6.6 查询导师本人排期
- `GET /mentor/schedule/slots/me`
- 鉴权：`MENTOR`
- 参数：`dateFrom`、`dateTo`（均可选）
- 说明：返回导师本人全部时段，包括 `AVAILABLE` 与 `BOOKED`。

### 6.7 创建可预约时段
- `POST /mentor/schedule/slots`
- 鉴权：`MENTOR`

请求：
```json
{"startAt": "2026-03-10T09:00:00Z", "endAt": "2026-03-10T10:00:00Z"}
```

### 6.8 删除可预约时段
- `DELETE /mentor/schedule/slots/{slotId}`
- 鉴权：`MENTOR`
- 说明：仅允许删除仍处于 `AVAILABLE` 的时段。

## 7. 咨询与支付接口
### 7.1 创建咨询订单
- `POST /consult/orders`
- 鉴权：`STUDENT`

请求：
```json
{
  "mentorUserId": 2001,
  "sceneCode": "RESUME_DIAGNOSIS",
  "sourcePage": "MENTOR_MARKETPLACE",
  "questionText": "我想重点优化前端实习简历和项目表达，请导师帮我判断目前最应该改哪几处。",
  "questionPayload": {
    "primaryConcern": "我投前端实习时回复率很低，不确定问题出在简历还是项目表达。",
    "background": "目标岗位是前端开发工程师，最近主要投递校招与日常实习。",
    "attemptedActions": "已经自己改过一版简历，也参考了 AI 简历优化结果做了初步调整。",
    "expectedHelp": "希望导师指出优先级最高的问题，并给出下一步修改建议。",
    "additionalNotes": "如果需要，我也可以根据建议继续补充项目材料。"
  },
  "problemSummary": "希望重点优化前端简历与项目表达。",
  "coreQuestions": [
    "简历中最影响回复率的问题是什么？",
    "项目经历应该怎么写得更像真实实习可用能力？",
    "如果只能优先改 3 处，最该改哪些？"
  ],
  "expectedOutcomes": [
    "获得简历修改建议",
    "获得项目表达优化建议",
    "获得下一步行动清单"
  ],
  "selectedMaterialTypes": ["RESUME", "JOB_DESCRIPTION", "PROJECT_MATERIAL"],
  "prepSheetSnapshot": {
    "scene": "简历诊断",
    "summaryDraft": "希望重点优化简历与项目表达。",
    "suggestedMaterials": ["我的最新简历", "目标岗位 JD", "项目介绍"]
  },
  "scheduleSlotId": 31001
}
```

说明：
- `questionText` 仍作为订单主问题文本保留，用于兼容当前最小链路与后续消息线程引用。
- 当前学生端正式订单创建页已按真实链路传入 `sceneCode`、`sourcePage`、`questionPayload`、`problemSummary`、`coreQuestions`、`expectedOutcomes`、`selectedMaterialTypes` 与 `prepSheetSnapshot`，由后端持久化为下单快照。
- `questionPayload` 用于保存结构化问题表单输入，便于订单详情、导师履约区与售后争议回看。
- `prepSheetSnapshot` 用于保存导师广场准备单导入时的摘要、核心问题与建议材料快照。
- `sourcePage` 当前前端已显式透传：`MENTOR_MARKETPLACE`、`MENTOR_MARKETPLACE_RECOMMENDATION`、`MENTOR_MARKETPLACE_FAVORITES`。
- `scheduleSlotId` 可选；若传入，则会尝试预占该导师的一个 `AVAILABLE` 时段。
- 预约成功后，订单会把预约开始/结束时间快照保存到订单本身，后续即使导师排期继续调整也不影响订单展示。
- 兼容策略：当前后端最小联调阶段仍允许仅传 `mentorUserId + questionText + scheduleSlotId`。

成功：
```json
{
  "code": "OK",
  "data": {
    "orderNo": "ORD20260304001",
    "amountFen": 5000,
    "status": "CREATED",
    "sceneCode": "RESUME_DIAGNOSIS",
    "appointmentStartAt": "2026-03-10T09:00:00Z",
    "appointmentEndAt": "2026-03-10T10:00:00Z",
    "currentAttachmentCount": 0
  }
}
```

### 7.1.1 查询订单当前材料包
- `GET /consult/orders/{orderNo}/attachments`
- 鉴权：订单参与方（学生或导师）
- 参数：
  - `currentOnly`（可选，默认 `true`）
  - `includeSuperseded`（可选，默认 `false`）
- 说明：
  - 默认返回当前有效材料，不暴露旧版本链。
  - 学生端订单创建页、订单详情页与导师履约区都以“当前有效材料列表”为主要展示口径。
  - 当前有效材料需至少返回：`attachmentId`、`attachmentType`、`slotCode`、`originalFilename`、`description`、`sourceStage`、`uploadedAt`、`lifecycleStatus`。

### 7.1.2 上传订单材料包
- `POST /consult/orders/{orderNo}/attachments/batch`
- 鉴权：`STUDENT`
- 内容类型：`multipart/form-data`

说明：
- 用于学生在正式订单创建页上传初始材料包，或在后续咨询过程中继续补充 / 替换材料。
- 推荐采用 `manifestJson + files[]` 的批量上传结构，其中 `manifestJson` 描述每个文件的 `attachmentType`、`slotCode`、`description`、`replaceCurrent` 与 `sourceStage`。
- `attachmentType` 当前建议至少支持：`RESUME`、`JOB_DESCRIPTION`、`PROJECT_MATERIAL`、`OFFER_MATERIAL`、`SUPPLEMENTARY`。
- `sourceStage` 当前建议值：`ORDER_CREATE`、`CHAT_APPEND`、`CHAT_REPLACE`。
- 当 `replaceCurrent=true` 时，系统会将同一单槽位材料的旧文件标记为 `SUPERSEDED`，导师侧默认只展示新文件这一份当前有效副本。
- 当前正式范围要求支持多文件上传，但不要求前台文件预览或显式版本管理页面。

### 7.1.3 读取订单材料内容
- `GET /consult/orders/{orderNo}/attachments/{attachmentId}/content`
- 鉴权：订单参与方（学生或导师）
- 说明：
  - 当前导师履约工作区已通过该接口支持“预览 / 下载”当前有效材料。
  - 接口直接返回二进制文件内容，并附带原始文件名与内容类型，前端可按需以内联预览或下载方式处理。
  - 若材料已被删除，则返回 `404`。

### 7.1.4 删除订单材料
- `DELETE /consult/orders/{orderNo}/attachments/{attachmentId}`
- 鉴权：`STUDENT`
- 说明：
  - 允许学生在创建订单页或后续订单详情中删除自己上传的当前有效材料。
  - 删除后材料记录建议保留 `DELETED` 生命周期状态，便于审计与售后追溯。

### 7.2 我的订单列表
- `GET /consult/orders`
- 鉴权：`STUDENT`、`MENTOR`
- 参数：`page`、`size`、`status`（可选）
- 说明：列表项除金额、状态、支付模式外，还会返回 `appointmentStartAt`、`appointmentEndAt` 预约快照字段，以及未支付订单的 `autoCancelAt` 自动取消时间（可为空）。
- 说明：若列表中仍存在 `CREATED/PAYING` 且未支付的订单，前端可基于 `autoCancelAt` 做倒计时展示，并定时刷新列表状态。
- 说明：列表查询前会尝试回收超过 `payment.unpaid-timeout-minutes` 的未支付订单，并把状态刷新为 `CANCELED`。
- 说明：列表查询前也会尝试处理超过 `consult.mentor-reply-timeout-hours` 且仍处于 `PAID` 的订单；若导师超时未正式答复，订单会被系统自动转为售后退款结果。
- 说明：正式订单创建页落地后，列表项建议额外返回 `sceneCode`、`latestAttachmentCount` 或 `materialTags` 等轻量摘要字段，便于学生与导师快速识别订单类型。

### 7.3 订单详情
- `GET /consult/orders/{orderNo}`
- 鉴权：订单参与方（学生或导师）
- 订单状态当前采用：`CREATED -> PAYING -> PAID -> ANSWERED -> CLOSED`（失败/取消/退款状态保留扩展位）
- 说明：详情会返回预约快照字段 `appointmentStartAt`、`appointmentEndAt`（若下单时未选排期则为空）、未支付订单的 `autoCancelAt` 自动取消时间（可为空）、`mentorReplyDeadlineAt` 导师回复 SLA 截止时间（仅 `PAID` 阶段可返回），以及当前订单的 `afterSalesRequests` 售后申请历史。
- 说明：`afterSalesRequests` 中的记录包含 `autoTriggered` 标识；当系统因导师超时未答自动发起售后时，该字段为 `true`。
- 说明：订单详情当前已同步返回 `sceneCode`、`sourcePage`、`questionPayload`、`problemSummary`、`coreQuestions`、`expectedOutcomes`、`selectedMaterialTypes`、`prepSheetSnapshot` 与 `attachmentsSummary`，用于学生端创单成功后的真实承接、订单详情展示与导师履约区聚合。
- 说明：`attachmentsSummary` 当前至少包含 `currentAttachmentCount`、`currentMaterialTypes` 与 `records` 三部分，其中 `records` 为当前有效材料列表摘要。
- 说明：订单详情当前还会返回 `studentProfile` 摘要对象，聚合学生的求职状态、学校 / 专业、年级 / GPA、目标岗位、技能标签、自我介绍与画像标签，供导师在订单中心与履约区快速理解背景。
- 说明：`studentProfile` 会尽量遵守学生隐私矩阵中的导师可见范围；未开放或未填写的字段返回为空。
- 说明：订单详情页可在 `CREATED/PAYING` 阶段基于 `autoCancelAt` 展示剩余时间，并定时刷新支付状态。
- 说明：若订单已超过未支付超时阈值，读取详情时会懒触发状态回收，将订单更新为 `CANCELED` 并释放已占用时段。
- 说明：若订单已超过导师回复时限且仍为 `PAID`，读取详情时会懒触发系统自动售后退款；系统会自动创建或复用售后申请并写回审批结果。

### 7.4 发起支付
- `POST /pay/orders/{orderNo}/create`
- 鉴权：`STUDENT`
- 说明：
  - `MOCK` 模式下返回模拟支付提示；
  - `SANDBOX` 模式下返回已签名的支付宝沙箱网页支付地址 `paymentUrl`；
  - 当订单已处于 `PAYING` 且支付模式为 `SANDBOX` 时，重复调用本接口可重新打开同一订单的沙箱支付页，不重复创建订单；
  - 学生侧订单详情页在 `PAYING` 阶段会定时轮询订单详情，以便在沙箱回调落库后自动刷新为 `PAID`。

### 7.4.1 学生查询支付状态
- `POST /consult/orders/{orderNo}/payment/query`
- 鉴权：`STUDENT`
- 说明：
  - 仅适用于当前支付模式为 `SANDBOX` 的本人订单；
  - 会调用 `alipay.trade.query` 查询外部交易状态；
  - 若外部状态已为 `TRADE_SUCCESS`，且本地仍停留在 `PAYING`，会自动补记为 `PAID` 并写入支付记录；
  - 当前用于学生侧订单详情页“查询支付状态”动作。

### 7.4.2 学生关闭支付单
- `POST /consult/orders/{orderNo}/payment/close`
- 鉴权：`STUDENT`
- 说明：
  - 仅允许对 `CREATED/PAYING` 且未支付的 `SANDBOX` 本人订单调用；
  - 会调用 `alipay.trade.close` 关闭外部沙箱交易；
  - 成功后本地订单同步置为 `CANCELED`，并释放已预占排期；
  - 当前用于学生侧订单详情页“关闭沙箱支付单”动作。

### 7.5 支付回调
- `POST /pay/alipay/callback`
- 说明：
  - 兼容支付宝沙箱真实字段：`out_trade_no`、`trade_no`、`total_amount`、`trade_status`、`sign`、`sign_type`
  - 当 `payment.sandbox.verify-enabled=true` 时执行 RSA/RSA2 验签

### 7.6 模拟支付成功
- `POST /pay/mock/orders/{orderNo}/success`

### 7.7 发送咨询消息
- `POST /consult/orders/{orderNo}/messages`
- 说明：
  - 正常文本沟通继续走该接口。
  - 当学生在聊天过程中补充或替换材料时，建议由附件上传接口完成文件写入，再由系统自动插入一条“材料已更新”的系统消息事件；导师侧默认查看当前有效材料，而不是手动辨认版本。

### 7.7.1 导师履约 AI 回复草稿
- `POST /consult/orders/{orderNo}/ai-reply-draft`
- 鉴权：`MENTOR`
- 说明：
  - 基于真实订单上下文、学生资料摘要、材料摘要与最近消息线程生成或润色导师回复草稿。
  - 当前仅返回草稿，不会自动发送消息；导师仍需在履约工作区内确认后手动点击发送。
  - 当前复用轻量 AI 配额与治理链路，`ai_call_logs.task_type` 仍按 `COMMUNITY_REPLY` 留痕，对外 `aiMeta.taskType` 返回 `MENTOR_REPLY_DRAFT`。
  - 该接口与导师财务中心的模拟提现流程无关；提现依旧维持毕设范围内的模拟流程。

请求：
```json
{
  "currentDraft": "我先看了你的问题，建议我们先聚焦项目表达。",
  "instruction": "语气更温和一些"
}
```

成功：
```json
{
  "code": "OK",
  "message": "generated",
  "data": {
    "draftReply": "我已经结合你当前的问题整理了一版更适合直接发送的回复草稿，你可以继续按自己的语气删改。",
    "generationMode": "POLISH_EXISTING",
    "appliedInstruction": "语气更温和一些",
    "aiMeta": {
      "taskType": "MENTOR_REPLY_DRAFT",
      "provider": "mock-provider",
      "model": "mock-economy-model",
      "latencyMs": 128
    },
    "moderation": {
      "sourceType": "AI_OUTPUT",
      "riskLevel": "LOW",
      "action": "PASS",
      "reasonCode": "RULE_CLEAR"
    }
  },
  "traceId": "trc_consult_ai_001",
  "timestamp": "2026-03-26T21:40:00Z"
}
```

### 7.8 查询咨询消息
- `GET /consult/orders/{orderNo}/messages`
- 说明：
  - 消息流除普通文本消息外，可包含系统事件消息，例如“学生已更新简历”或“学生已补充项目材料”。
  - 前端不要求在消息流中展示完整版本历史；历史追溯以附件元数据与审计记录为准。

### 7.9 取消未支付咨询订单
- `POST /consult/orders/{orderNo}/cancel`
- 鉴权：`STUDENT`
- 说明：仅允许取消 `CREATED` 或 `PAYING` 且仍未支付的订单；若该订单已预占导师时段，则会同步释放该时段。

### 7.10 关闭咨询订单
- `POST /consult/orders/{orderNo}/close`
- 鉴权：`STUDENT`（订单状态必须为 `ANSWERED`）
- 说明：导师首次正式回复后订单进入 `ANSWERED`，学生确认问题已解决后主动关闭为 `CLOSED`

### 7.11 提交咨询评价
- `POST /consult/orders/{orderNo}/review`
- 鉴权：`STUDENT`（订单状态必须为 `CLOSED`）

请求：
```json
{"rating": 5, "comment": "导师回复非常详细，受益匪浅"}
```

成功：
```json
{"code": "OK", "message": "review submitted"}
```

### 7.12 学生发起咨询售后申请
- `POST /consult/orders/{orderNo}/after-sales/requests`
- 鉴权：`STUDENT`
- 说明：
  - 仅允许对 `PAID`、`ANSWERED`、`CLOSED` 订单发起；
  - 同一订单同一时刻仅允许存在一条 `PENDING` 售后申请；
  - 当前 `requestType` 固定为 `REFUND`，管理员审核通过后会复用统一退款逻辑。

请求：
```json
{"reason": "学生认为本次服务不匹配，申请退款"}
```

成功：
```json
{"code": "OK", "data": {"requestId": 8001, "orderNo": "ORD202603080001", "requestType": "REFUND", "status": "PENDING"}}
```

## 8. 企业悬赏接口
### 8.1 发布任务
- `POST /bounty/tasks`
- 鉴权：`ENTERPRISE`

请求：
```json
{
  "title": "前端项目实战",
  "description": "完成一个企业活动页并提交说明。",
  "rewardDescription": "实习内推机会",
  "deadlineAt": "2026-03-20T12:00:00Z"
}
```

说明：
- 企业端当前正式发布页为 `/enterprise/tasks/create`。
- 前端 richer 表单字段当前仍复用 `title/description/rewardDescription/deadlineAt` 四个现有字段提交；任务背景、交付要求、适合人群、参考链接等结构化信息由前端先序列化进 `description`，后续若扩专用字段再升级契约。

### 8.2 更新任务
- `PUT /bounty/tasks/{taskId}`
- 鉴权：`ENTERPRISE`（仅任务发布方）

请求：
```json
{
  "title": "前端项目实战（第二版）",
  "description": "补充任务背景、交付要求与参考资料。",
  "rewardDescription": "实习内推机会 + 项目复盘交流",
  "deadlineAt": "2026-03-28T15:59:59Z"
}
```

成功：
```json
{
  "code": "OK",
  "data": {
    "taskId": 101,
    "status": "OPEN",
    "updatedAt": "2026-03-26T07:21:00Z"
  }
}
```

说明：
- 当前企业端不单独新建编辑路由，正式复用 `/enterprise/tasks/create?editTaskId=...` 进入编辑模式。
- 已存在中选结果的任务不允许继续编辑，后端会返回业务错误 `accepted task cannot be edited`。

### 8.3 任务列表
- `GET /bounty/tasks`
- 鉴权：`STUDENT|ENTERPRISE|ADMIN`
- 参数：`page`、`size`、`keyword`（可选）、`status`（可选：`OPEN|CLOSED`）、`mineOnly`（可选，企业侧工作台与任务中心使用）

成功：
```json
{
  "code": "OK",
  "data": {
    "records": [
      {
        "taskId": 101,
        "enterpriseUserId": 3001,
        "enterpriseName": "某科技公司",
        "title": "前端项目实战",
        "descriptionSummary": "完成一个企业活动页并提交说明。",
        "rewardDescription": "实习内推机会",
        "status": "OPEN",
        "submissionCount": 3,
        "acceptedSubmissionId": null,
        "submittedByMe": true,
        "deadlineAt": "2026-03-20T12:00:00Z",
        "createdAt": "2026-03-05T10:00:00Z",
        "updatedAt": "2026-03-05T10:00:00Z"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 10
  }
}
```

### 8.3.1 企业任务中心聚合
- `GET /bounty/enterprise/task-center`
- 鉴权：`ENTERPRISE`

说明：
- 当前供 `/enterprise/tasks` 企业任务中心直接使用。
- 该接口会在后端统一返回“企业自己的全部任务 + 每个任务的状态计数 + 最近提交快照”，不再由前端逐任务 N 次拉取提交列表后本地聚合。
- `recentSubmissions` 当前默认只返回每个任务最近 6 条提交快照，用于任务中心右侧轻量快照区；完整提交阅读与筛选仍走审核工作区的 `GET /bounty/tasks/{taskId}/submissions`。
- `recentSubmissions[].studentAvatar` 为企业侧学生身份展示所需的最小头像元数据；前端会结合 `/profiles/students/{studentUserId}/avatar?v=...` 读取真实头像内容。

成功：
```json
{
  "code": "OK",
  "data": {
    "tasks": [
      {
        "taskId": 101,
        "enterpriseUserId": 3001,
        "enterpriseName": "某科技公司",
        "enterpriseLogoUrl": "/api/v1/profiles/enterprises/3001/logo",
        "title": "前端项目实战",
        "descriptionSummary": "完成一个企业活动页并提交说明。",
        "rewardDescription": "实习内推机会",
        "status": "OPEN",
        "submissionCount": 8,
        "acceptedSubmissionId": null,
        "deadlineAt": 1774008000000,
        "createdAt": 1772704800000,
        "updatedAt": 1774518000000,
        "pendingCount": 3,
        "contactedCount": 2,
        "reviewedCount": 5,
        "recentSubmissions": [
          {
            "submissionId": 3001,
            "studentUserId": 1001,
            "studentName": "Alice",
            "studentAvatar": {
              "uploaded": true,
              "contentType": "image/jpeg",
              "updatedAt": 1774512000000
            },
            "status": "SUBMITTED",
            "contentSummary": "项目链接 + 设计说明",
            "communityScore7d": 39,
            "portraitTags": ["Java基础稳固", "社区互动积极"],
            "reviewComment": null,
            "reviewedAt": null,
            "createdAt": 1772704800000
          }
        ]
      }
    ],
    "total": 1
  }
}
```

### 8.4 任务详情
- `GET /bounty/tasks/{taskId}`
- 鉴权：`STUDENT|ENTERPRISE|ADMIN`
- 说明：
  - 学生侧会返回 `mySubmission`。
  - 企业发布方会复用同一路由承接审核工作区和发布页编辑模式的数据源。

### 8.5 管理任务状态
- `POST /bounty/tasks/{taskId}/manage`
- 鉴权：`ENTERPRISE`（仅任务发布方）
- 请求：`{"action": "CLOSE"}` 或 `{"action": "REOPEN"}`
- 说明：已采纳学生成果后任务会自动 `CLOSED`；存在中选提交的任务不允许再次 `REOPEN`。

### 8.6 提交成果
- `POST /bounty/tasks/{taskId}/submissions`
- 鉴权：`STUDENT`

请求：
```json
{
  "contentText": "已完成 Figma 原型与 React 页面实现。",
  "attachmentLinks": ["https://github.com/demo/project", "https://demo.example.com"]
}
```

### 8.7 审核提交
- `POST /bounty/submissions/{submissionId}/review`
- 鉴权：`ENTERPRISE`（仅任务发布方）

请求：
```json
{
  "decision": "ACCEPT",
  "contactIntent": "进一步交流",
  "rejectTemplate": null,
  "actionNote": "方案完整，建议下周做一次线上讲解。",
  "comment": "继续接触意向：进一步交流\n补充说明：方案完整，建议下周做一次线上讲解。",
  "syncEmailReminder": true
}
```

成功：
```json
{
  "code": "OK",
  "data": {
    "submissionId": 3001,
    "taskId": 101,
    "status": "ACCEPTED",
    "taskStatus": "CLOSED",
    "reviewedAt": 1774519560000,
    "comment": "继续接触意向：进一步交流\n补充说明：方案完整，建议下周做一次线上讲解。",
    "contactIntent": "进一步交流",
    "rejectTemplate": null,
    "reviewNote": "方案完整，建议下周做一次线上讲解。",
    "updatedAt": 1774519560000,
    "history": [
      {
        "eventId": 9901,
        "eventType": "SUBMITTED",
        "occurredAt": 1772704800000,
        "comment": null,
        "contactIntent": null,
        "rejectTemplate": null,
        "note": "项目链接 + 设计说明"
      },
      {
        "eventId": 9902,
        "eventType": "CONTACT_SENT",
        "occurredAt": 1774519560000,
        "comment": "继续接触意向：进一步交流\n补充说明：方案完整，建议下周做一次线上讲解。",
        "contactIntent": "进一步交流",
        "rejectTemplate": null,
        "note": "方案完整，建议下周做一次线上讲解。"
      }
    ]
  }
}
```

说明：
- `comment` 仍为学生可见结果说明，用于通知与学生端结果回看。
- `contactIntent` / `rejectTemplate` / `actionNote` 为企业审核工作区当前正式使用的结构化决策字段，便于后续统计、筛选与留痕读取；其中：
  - `ACCEPT` 时必须提供 `contactIntent`
  - `REJECT` 时必须提供 `rejectTemplate`
  - `actionNote` 为可选补充说明
- `history` 为当前提交的真实处理留痕，当前至少覆盖提交创建、继续接触、未入选、任务因中选而关闭等事件。
- `syncEmailReminder` 为可选布尔值：
  - `true`：除站内通知外，本次审核结果还会显式请求进入邮件队列；
  - `false` 或省略：本次审核结果只保留站内通知，不额外进入邮件队列。
- 即使 `syncEmailReminder=true`，系统仍会尊重学生自己的邮件通知开关；若学生关闭了该渠道，或平台当前未配置邮件服务，则会自动回退为仅站内通知。
- `ACCEPT`：目标提交改为 `ACCEPTED`，任务自动置为 `CLOSED`，其他待审核提交自动批量置为 `REJECTED`。
- `REJECT`：目标提交改为 `REJECTED`，任务保持当前状态。
- 已处理提交或已关闭任务不允许再次 review。
- 审核完成后会向学生发送 `BOUNTY_REVIEWED` 通知；当 `syncEmailReminder=true` 时，本次审核结果还会显式尝试进入邮件队列。

### 8.8 任务提交列表（企业侧筛选）
- `GET /bounty/tasks/{taskId}/submissions`
- 鉴权：`ENTERPRISE`（仅任务发布方）
- 参数：`page`、`size`、`status`（可选）、`portraitTag`（可选，多值逗号分隔）、`minCommunityScore7d`（可选）
- 说明：
  - 接口层仍支持 `portraitTag`，但当前企业审核工作区前台已不再暴露画像标签筛选栏，主要使用状态、关键词与贡献分筛选。

成功：
```json
{
  "code": "OK",
  "data": {
    "records": [
      {
        "submissionId": 3001,
        "studentUserId": 1001,
        "studentName": "Alice",
        "studentAvatar": {
          "uploaded": true,
          "contentType": "image/jpeg",
          "updatedAt": 1774512000000
        },
        "status": "SUBMITTED",
        "contentSummary": "项目链接 + 设计说明",
        "contentText": "我完成了活动页高保真稿和实现说明。",
        "attachmentLinks": ["https://demo.example.com/a"],
        "communityScore7d": 39,
        "portraitTags": ["Java基础稳固", "社区互动积极"],
        "portraitUpdatedAt": 1772697600000,
        "reviewComment": null,
        "contactIntent": null,
        "rejectTemplate": null,
        "reviewNote": null,
        "reviewedAt": null,
        "createdAt": 1772704800000,
        "updatedAt": 1774519320000,
        "contact": {
          "hint": "当前仅展示学生向企业开放的联系方式与公开主页。",
          "emailVisible": true,
          "email": "alice@example.com",
          "phoneVisible": true,
          "phone": "13800000000",
          "wechatVisible": true,
          "wechat": "alice_wechat",
          "socialVisible": true,
          "socialLinks": [
            { "platform": "GITHUB", "value": "alice-dev" },
            { "platform": "PORTFOLIO", "value": "https://portfolio.example.com/alice" }
          ]
        },
        "history": [
          {
            "eventId": 9901,
            "eventType": "SUBMITTED",
            "occurredAt": 1772704800000,
            "comment": null,
            "contactIntent": null,
            "rejectTemplate": null,
            "note": "项目链接 + 设计说明"
          }
        ]
      }
    ],
    "total": 12,
    "page": 1,
    "size": 10
  }
}
```

说明：
- `studentAvatar` 为企业侧学生头像展示所需的最小元数据，前端会结合公开头像读取接口拼接真实头像 URL。
- `contact` 当前直接复用学生资料中心现有联系方式与隐私矩阵，只返回学生向企业开放的字段。
- 联系方式当前主要供 `/enterprise/tasks/:taskId` 审核工作区展示，任务中心不直接展开联系方式。
- `history` 当前由后端真实事件表驱动，不再是前端基于 `createdAt / reviewedAt / closedAt` 的临时推导结果。
- `contactIntent` / `rejectTemplate` / `reviewNote` 为结构化审核语义；旧数据若仅存在 `reviewComment`，后端会做兼容解析后返回。

## 9. 通知接口
说明：
- 基础路径仍为 `/api/v1/notifications`。
- 站内收件箱是事实真相层；WebSocket 与浏览器桌面提醒属于额外触达层。
- 当前通知分类固定为 `AI_TASK|CONSULT|BOUNTY|CERTIFICATION|SYSTEM|COMMUNITY`。

### 9.1 获取通知列表
- `GET /notifications`
- 鉴权：所有已登录用户
- 参数：`page`、`size`、`unreadOnly`（可选）

成功：
```json
{
  "code": "OK",
  "data": {
    "unreadCount": 3,
    "records": [
      {
        "id": 1,
        "type": "AI_RESUME_TASK_SUCCEEDED",
        "category": "AI_TASK",
        "title": "AI 简历任务已完成",
        "content": "你刚提交的 AI 简历诊断已经完成，可以进入复盘中心查看结构化结果。",
        "read": false,
        "refType": "AI_ASYNC_TASK",
        "refId": "aitk_1234567890abcdef",
        "actionCode": "VIEW_AI_REVIEW_CENTER",
        "priority": "NORMAL",
        "payload": {
          "taskId": "aitk_1234567890abcdef",
          "linkedRecordId": 123,
          "browserPopupAllowed": true
        },
        "createdAt": "2026-03-04T10:00:00Z",
        "readAt": null
      }
    ],
    "total": 12,
    "page": 1,
    "size": 10
  }
}
```

补充：
- AI 面试复盘完成后会发送 `AI_INTERVIEW_SUMMARY_READY`，其 `actionCode` 同样为 `VIEW_AI_REVIEW_CENTER`，并通过 `payload.sessionId` deep-link 到 `/ai/history?type=interview&sessionId=...`。

### 9.2 获取未读数量
- `GET /notifications/unread-count`
- 鉴权：所有已登录用户

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "unreadCount": 3
  },
  "traceId": "trc_notify_001",
  "timestamp": "2026-03-08T13:40:00Z"
}
```

### 9.3 标记已读
- `POST /notifications/{notificationId}/read`
- 鉴权：通知所属用户

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": 1,
    "read": true,
    "readAt": "2026-03-08T13:41:00Z"
  },
  "traceId": "trc_notify_002",
  "timestamp": "2026-03-08T13:41:00Z"
}
```

### 9.4 全部标记已读
- `POST /notifications/read-all`
- 鉴权：所有已登录用户

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "updatedCount": 3,
    "unreadCount": 0
  },
  "traceId": "trc_notify_003",
  "timestamp": "2026-03-08T13:42:00Z"
}
```

### 9.5 获取通知偏好
- `GET /notifications/preferences`
- 鉴权：所有已登录用户

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "records": [
      {
        "category": "AI_TASK",
        "inboxEnabled": true,
        "websocketEnabled": true,
        "browserPopupEnabled": true,
        "emailEnabled": true,
        "emailUrgencyThreshold": "HIGH",
        "quietHoursJson": null,
        "customized": false
      }
    ]
  },
  "traceId": "trc_notify_004",
  "timestamp": "2026-03-24T11:00:00Z"
}
```

说明：
- 当前首版按分类维护偏好，`inboxEnabled` 目前固定为 `true`，用于确保事务通知先稳定入箱。
- `customized=false` 表示当前记录仍使用系统默认规则；一旦用户保存过该分类偏好，后续会返回 `customized=true`。

### 9.6 更新通知偏好
- `PUT /notifications/preferences`
- 鉴权：所有已登录用户

请求：
```json
{
  "category": "CONSULT",
  "websocketEnabled": true,
  "browserPopupEnabled": false,
  "emailEnabled": true,
  "emailUrgencyThreshold": "HIGH",
  "quietHoursJson": "{\"start\":\"23:00\",\"end\":\"08:00\"}"
}
```

成功：
```json
{
  "code": "OK",
  "message": "notification preference updated",
  "data": {
    "category": "CONSULT",
    "inboxEnabled": true,
    "websocketEnabled": true,
    "browserPopupEnabled": false,
    "emailEnabled": true,
    "emailUrgencyThreshold": "HIGH",
    "quietHoursJson": "{\"start\":\"23:00\",\"end\":\"08:00\"}",
    "customized": true
  },
  "traceId": "trc_notify_005",
  "timestamp": "2026-03-24T11:05:00Z"
}
```

### 9.7 获取通知 WebSocket 短票据
- `POST /notifications/ws-ticket`
- 鉴权：所有已登录用户

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "ticket": "eyJhbGciOiJIUzI1NiJ9...",
    "wsPath": "/ws/notifications",
    "expiresAt": "2026-03-24T11:03:00Z"
  },
  "traceId": "trc_notify_006",
  "timestamp": "2026-03-24T11:00:00Z"
}
```

说明：
- 前端先调用本接口换取短时票据，再以 `GET /ws/notifications?ticket=<ticket>` 建立浏览器 WebSocket 连接。
- 票据当前使用 JWT 短签发方式，不复用普通访问令牌做握手鉴权。

### 9.8 通知 WebSocket 通道
- 握手地址：`GET /ws/notifications?ticket=<ticket>`
- 鉴权：短票据握手，握手失败返回 `401`
- 当前服务端消息：
  - 连接建立成功后先推送：
```json
{
  "type": "CONNECTED",
  "serverTime": "2026-03-24T11:00:01Z"
}
```
  - 生成新通知后推送：
```json
{
  "type": "NOTIFICATION_CREATED",
  "jobId": "njob_1234567890",
  "browserPopupAllowed": true,
  "notification": {
    "id": 101,
    "type": "CONSULT_REPLIED",
    "category": "CONSULT",
    "title": "导师已回复你的咨询",
    "content": "导师已经给出新的回复，建议尽快查看。",
    "read": false,
    "refType": "CONSULT_ORDER",
    "refId": "ORD20260324001",
    "actionCode": "VIEW_CONSULT_ORDER",
    "priority": "HIGH",
    "payload": {
      "sourceType": "CONSULT_ORDER",
      "sourceId": "ORD20260324001"
    },
    "createdAt": "2026-03-24T11:00:02Z",
    "readAt": null
  }
}
```
- 当前客户端 ACK 回执：
```json
{
  "type": "ACK",
  "jobId": "njob_1234567890"
}
```

## 10. 管理后台接口
### 10.1 AI 网关后台管理
- `GET /admin/ai/meta`
- `GET /admin/ai/providers`
- `POST /admin/ai/providers`
- `PUT /admin/ai/providers/{providerId}`
- `GET /admin/ai/prompt-templates`
- `POST /admin/ai/prompt-templates`
- `PUT /admin/ai/prompt-templates/{templateId}`
- `POST /admin/ai/prompt-templates/{templateId}/publish`
- `POST /admin/ai/prompt-templates/{templateId}/rollback`
- `POST /admin/ai/prompt-templates/render-preview`
- `GET /admin/ai/routes`
- `POST /admin/ai/routes`
- `PUT /admin/ai/routes/{routeId}`
- `POST /admin/ai/routes/resolve-preview`
- `GET /admin/ai/runtime-settings`
- `PUT /admin/ai/runtime-settings`
- 鉴权：`ADMIN`

说明：
- provider 支持维护 `providerType`、`baseUrl`、`apiKey`、`timeoutMs`、`maxRetries`、单价与 `extraConfigJson`。
- `prompt-templates` 支持维护 `taskType + templateName + versionNo + status + content + description + variablesJson`；`status` 当前取值为 `DRAFT|ACTIVE|INACTIVE`。
- `variablesJson` 现在可承载变量声明（如 `required/defaultValue/sampleValue/description`），运行时会按 `{{variableName}}` 占位符进行渲染。
- `/admin/ai/prompt-templates/{templateId}/publish` 用于显式发布某个模板版本：目标版本会被置为 `ACTIVE`，同一 `(taskType, templateName)` 下其他 `ACTIVE` 版本会自动转为 `INACTIVE`。
- `/admin/ai/prompt-templates/{templateId}/rollback` 用于把当前模板族回滚到指定历史版本；请求体需提供 `targetTemplateId`，且目标必须与当前版本属于同一 `(taskType, templateName)`。
- `/admin/ai/prompt-templates/render-preview` 用于在不真实调用模型的前提下，基于当前模板正文、变量声明和示例变量 JSON 预览最终渲染结果，并返回缺失变量列表。
- route 支持维护 `taskType + sceneCode + providerConfigId + modelName + systemPrompt + promptTemplateName + temperature + extraConfigJson`。
- 当 route 绑定 `promptTemplateName` 时，运行时优先读取同一 `taskType + templateName` 下的 `ACTIVE` 模板内容作为 system prompt；若 route 未绑定模板，则继续使用 route 自身的 `systemPrompt`。
- 若 route 已绑定模板名但当前不存在 `ACTIVE` 版本，或模板中的占位变量在运行时缺失，则返回 `AI-2003`，不再静默回退到 route `systemPrompt`。
- `/admin/ai/routes/resolve-preview` 用于在不真实调用模型的前提下，预览当前 `taskType + sceneCode + modelPreference` 将命中的 provider / route / model，并返回 `promptTemplateName` 与当前命中的模板版本号。
- `/admin/ai/runtime-settings` 用于维护 AI 网关运行时开关：`debugModeEnabled` 控制路由分发/命中/成功等诊断日志，`aiRequestLogEnabled` 控制请求开始、重试与错误请求体/响应体摘要日志。
- 当前后台不单独提供 provider 探活接口；运行态排障统一依赖 `maxRetries` 重试机制与 AI 请求日志。

### 10.2 功能开关
- `GET /admin/feature-flags`
- `POST /admin/feature-flags`
- 鉴权：`ADMIN`
- 当前最小实现支持 3 个开关：
  - `payment.mode`：`MOCK|SANDBOX`
  - `voice.enabled`：`true|false`
  - `community.ai-pre-answer.enabled`：`true|false`

查询成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "records": [
      {
        "key": "payment.mode",
        "displayName": "支付模式",
        "description": "控制咨询订单创建支付时默认走 MOCK 还是 SANDBOX。",
        "valueType": "ENUM",
        "allowedValues": ["MOCK", "SANDBOX"],
        "currentValue": "MOCK",
        "defaultValue": "MOCK",
        "overridden": false,
        "updatedBy": null,
        "updatedAt": null
      }
    ]
  }
}
```

更新请求：
```json
{
  "key": "voice.enabled",
  "value": "false"
}
```

更新成功：
```json
{
  "code": "OK",
  "message": "feature flag updated",
  "data": {
    "key": "voice.enabled",
    "currentValue": "false",
    "overridden": true,
    "updatedBy": 1,
    "updatedAt": "2026-03-08T14:42:00Z"
  }
}
```

行为说明：
- `payment.mode` 会覆盖后端静态配置，直接影响咨询订单支付创建时使用的模式。
- `voice.enabled=false` 时，`INTERVIEW_VOICE` 建会、语音往返与 `POST /ai/tts/synthesize` 会被拒绝。
- `community.ai-pre-answer.enabled=false` 时，`POST /ai/community/pre-answer` 会被拒绝。

### 10.3 AI 日志
- `GET /admin/ai/logs`
- 鉴权：`ADMIN`
- 参数：`page`、`size`、`taskType`（可选）、`provider`（可选）、`status`（可选）、`userId`（可选）

成功：
```json
{
  "code": "OK",
  "data": {
    "records": [
      {
        "id": 1,
        "traceId": "trc_admin_ai_001",
        "userId": 1001,
        "userEmail": "admin@test.local",
        "userDisplayName": "管理员",
        "taskType": "INTERVIEW_TEXT",
        "provider": "OPENAI_DEMO_MAIN",
        "model": "gpt-4o-mini",
        "status": "SUCCESS",
        "errorCode": "",
        "latencyMs": 1800,
        "requestTokens": 120,
        "responseTokens": 60,
        "totalTokens": 180,
        "estimatedCost": "0.120000",
        "chargedPoints": 5,
        "quotaWeight": 1,
        "resultSummary": "文本面试追问成功",
        "userTier": "FREE",
        "createdAt": "2026-03-07T12:09:40Z"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 10
  }
}
```

### 10.4 AI 配额策略管理
- `GET /admin/ai/quota-policies`
- `PUT /admin/ai/quota-policies/{policyId}`
- 鉴权：`ADMIN`
- `tier` 与 `taskType` 作为只读维度展示；允许在线更新 `dailyFreeLimit`、`pointsPerCall`、`dailyMaxLimit`、`modelPreference`、`maxInputTokens`。
- 保存后立即影响后台展示与后续 AI 调用时的模型偏好/输入上限口径。

### 10.5 成本看板
- `GET /admin/ai/cost-dashboard`
- `GET /admin/ai/cost-dashboard/export`
- 鉴权：`ADMIN`
- 参数：`period`（`today`/`week`/`month`）
- 导出：`export` 返回 `text/csv` 文件流，按 `OVERVIEW / MODEL / PROVIDER / TASK_TYPE / USER_TIER / TOP_USER` 分段输出聚合结果。

成功：
```json
{
  "code": "OK",
  "data": {
    "period": "today",
    "totalCalls": 1200,
    "totalCost": "15.600000",
    "byModel": [{"name": "gpt-4o-mini", "calls": 800, "cost": "8.200000"}],
    "byProvider": [{"name": "OPENAI_DEMO_MAIN", "calls": 900, "cost": "11.300000"}],
    "byTaskType": [{"name": "INTERVIEW_TEXT", "calls": 600, "cost": "7.500000"}],
    "byTier": [{"name": "FREE", "calls": 1000, "cost": "12.000000"}],
    "topUsers": [{"userId": 1001, "email": "alice@example.com", "displayName": "Alice", "calls": 20, "cost": "1.320000"}]
  }
}
```

### 10.5.1 管理端运营数据看板
- `GET /admin/dashboard/operations`
- 鉴权：`ADMIN`
- 参数：`period`（`today`/`week`/`month`，默认 `week`）
- 说明：返回当前最小运营看板，聚合激活率、7 日留存、咨询转化率、AI 成功率、社区 AI 覆盖率、内容审查拦截率、举报处置时效中位数、画像完整率与榜单覆盖率。

成功：
```json
{
  "code": "OK",
  "data": {
    "period": "week",
    "generatedAt": "2026-03-08T13:40:00Z",
    "windowStartAt": "2026-03-02T00:00:00Z",
    "windowEndAt": "2026-03-08T23:59:59Z",
    "overview": {
      "newStudents": 12,
      "activatedStudents": 7,
      "activeStudents": 35,
      "paidConsultStudents": 6,
      "aiCalls": 108,
      "aiSuccessCalls": 94,
      "newPosts": 16,
      "aiCoveredPosts": 9,
      "moderationEvents": 40,
      "blockedModerationEvents": 5,
      "closedReports": 7,
      "totalStudents": 82,
      "portraitCompletedStudents": 61,
      "activeStudents7d": 35,
      "leaderboardCoveredStudents7d": 14
    },
    "activationRate": {"label": "激活率", "value": "58.3%", "numerator": 7, "denominator": 12, "note": "24 小时内至少完成一次 AI 成功调用"},
    "retention7dRate": {"label": "7日留存", "value": "50%", "numerator": 4, "denominator": 8, "note": "基于已满 7 天的激活学生 cohort"},
    "consultConversionRate": {"label": "咨询转化率", "value": "17.1%", "numerator": 6, "denominator": 35, "note": "活跃学生中产生已支付咨询订单的学生占比"},
    "aiSuccessRate": {"label": "AI 调用成功率", "value": "87%", "numerator": 94, "denominator": 108, "note": "当前窗口 ai_call_logs 成功占比"},
    "communityAiCoverageRate": {"label": "社区 AI 覆盖率", "value": "56.3%", "numerator": 9, "denominator": 16, "note": "新帖中已出现 AI 自动首答评论的比例"},
    "moderationBlockRate": {"label": "内容审查拦截率", "value": "12.5%", "numerator": 5, "denominator": 40, "note": "当前窗口 moderation action=BLOCK 占比"},
    "reportHandleMedianHours": {"label": "举报处置时效中位数", "value": "18h", "sampleSize": 7, "note": "已关闭举报从创建到关闭的中位耗时"},
    "portraitCompletenessRate": {"label": "画像完整率", "value": "74.4%", "numerator": 61, "denominator": 82, "note": "至少形成 1 个画像标签"},
    "leaderboardCoverageRate": {"label": "社区榜单覆盖率", "value": "40%", "numerator": 14, "denominator": 35, "note": "近 7 天有贡献分学生占活跃学生比例"}
  }
}
```

### 10.6 用户密码重置
- `POST /admin/users/{userId}/reset-password`
- 鉴权：`ADMIN`

请求：
```json
{"newPassword": "NewPass1234!"}
```

成功：
```json
{"code": "OK", "message": "password reset"}
```

### 10.7 用户列表查询
- `GET /admin/users`
- 鉴权：`ADMIN`
- 参数：`page`、`size`、`keyword`（可选，支持 `userId/邮箱/昵称`）、`role`（可选）、`status`（可选，仅 `ACTIVE|SUSPENDED` 作为当前管理口径）、`approvalStatus`（可选，仅对 `MENTOR|ENTERPRISE` 生效，取值 `PENDING|APPROVED|REJECTED`）

成功：
```json
{
  "code": "OK",
  "data": {
    "records": [
      {
        "userId": 1001,
        "email": "alice@example.com",
        "displayName": "Alice",
        "role": "STUDENT",
        "tier": "FREE",
        "status": "ACTIVE",
        "approvalStatus": null,
        "createdAt": "2026-03-05T08:00:00Z",
        "lastLoginAt": "2026-03-07T01:20:00Z"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 10
  }
}
```

### 10.8 用户详情查询
- `GET /admin/users/{userId}`
- 鉴权：`ADMIN`

成功：
```json
{
  "code": "OK",
  "data": {
    "userId": 1001,
    "email": "alice@example.com",
    "displayName": "Alice",
    "role": "STUDENT",
    "tier": "FREE",
    "status": "ACTIVE",
    "approvalStatus": null,
    "createdAt": "2026-03-05T08:00:00Z",
    "studentProfile": {
      "major": "软件工程",
      "grade": "大三",
      "targetPosition": "前端开发",
      "skillTags": ["React", "TypeScript"],
      "selfIntro": "用于管理员 USER 举报详情联调"
    },
    "communityScore7d": 5
  }
}
```

### 10.9 用户状态更新
- `POST /admin/users/{userId}/status`
- 鉴权：`ADMIN`

请求：
```json
{"status": "SUSPENDED"}
```

说明：
- 当前最小联调仅支持 `ACTIVE` 与 `SUSPENDED`
- 为避免锁死当前会话，后端会拒绝对当前登录管理员自身执行状态切换

成功：
```json
{"code": "OK", "message": "user status updated"}
```

### 10.9.1 用户认证状态更新
- `POST /admin/users/{userId}/approval-status`
- 鉴权：`ADMIN`

请求：
```json
{"approvalStatus": "PENDING"}
```

说明：
- 当前仅 `MENTOR` 与 `ENTERPRISE` 角色支持认证状态管理；
- 认证状态取值：`PENDING|APPROVED|REJECTED`；
- 学生与管理员账号调用本接口会返回业务错误。
- 当前该接口仅保留为兼容能力；前端正式主链路已切换到独立认证审核工作区，优先使用 `10.9.2 ~ 10.9.4` 的提交级审核接口。

成功：
```json
{"code": "OK", "message": "user approval status updated"}
```

### 10.9.2 当前认证审核列表
- `GET /admin/users/certification-reviews`
- 鉴权：`ADMIN`
- 参数：`page`、`size`、`keyword`（可选，支持 `邮箱/昵称/真实姓名/企业`）、`role`（可选，仅 `MENTOR|ENTERPRISE`）、`status`（可选，`PENDING|APPROVED|REJECTED`）

说明：
- 只返回当前有效 submission 作为审核对象。
- 用于管理员后台 `/admin/users/reviews` 工作区列表。

### 10.9.3 当前认证审核详情
- `GET /admin/users/{userId}/certification-review`
- 鉴权：`ADMIN`

说明：
- 返回指定用户当前有效 submission、历史版本链路与所有附件元数据。

### 10.9.4 提交认证审核结论
- `POST /admin/users/{userId}/certification-review`
- 鉴权：`ADMIN`

请求：
```json
{
  "approvalStatus": "APPROVED",
  "reviewNote": "身份材料清晰，审核通过"
}
```

说明：
- 审核动作直接作用在当前 submission 上，并同步更新导师 / 企业 profile 的 `approvalStatus`。
- `reviewNote` 用于给用户后续查看当前版本或历史版本时回显审核依据。

### 10.10 咨询订单售后列表
- `GET /admin/consult/orders`
- 鉴权：`ADMIN`
- 参数：`page`、`size`、`keyword`（可选，支持 `orderNo/学生昵称/导师昵称`）、`status`（可选）
- 说明：列表查询前会同步执行未支付超时回收与导师回复超时检查；满足条件的 `PAID` 订单会被刷新为系统自动售后退款结果。

成功：
```json
{
  "code": "OK",
  "data": {
    "records": [
      {
        "orderNo": "ORD202603070001",
        "studentUserId": 1001,
        "studentDisplayName": "Alice",
        "mentorUserId": 2001,
        "mentorDisplayName": "导师王老师",
        "amountFen": 9900,
        "status": "CLOSED",
        "questionText": "如果我想退款，平台会如何处理？",
        "paymentMode": "MOCK",
        "appointmentStartAt": "2026-03-10T09:00:00Z",
        "appointmentEndAt": "2026-03-10T10:00:00Z",
        "createdAt": "2026-03-07T08:00:00Z",
        "paidAt": "2026-03-07T08:05:00Z",
        "closedAt": "2026-03-07T08:20:00Z"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 10
  }
}
```

### 10.11 咨询订单售后详情
- `GET /admin/consult/orders/{orderNo}`
- 鉴权：`ADMIN`
- 说明：除订单主信息外，还返回最新支付记录摘要 `payment`、评价摘要、最近一次退款/售后审计摘要，以及 `afterSalesRequests` 售后申请历史（如存在）。
- 说明：详情额外返回 `mentorReplyDeadlineAt`，用于展示 `PAID` 订单的导师回复 SLA 截止时间；若已超时，读取详情时会懒触发系统自动售后退款。

### 10.12 管理员手工退款/售后处理
- `POST /admin/consult/orders/{orderNo}/refund`
- 鉴权：`ADMIN`
- 说明：
  - 仅允许处理 `PAID`、`ANSWERED`、`CLOSED` 订单；
  - 若预约时段尚未结束，则会自动释放该时段；
  - 若订单已 `CLOSED` 且已有评价，则会移除评价并刷新导师评分/完成单数；
  - 当订单最近一次支付模式为 `SANDBOX` 且存在成功支付记录时，会先调用 `alipay.trade.refund` 发起全额退款，成功后再同步落本地 `REFUNDED`；
  - `MOCK` 或无有效沙箱支付成功记录的订单，仍按平台内逻辑退款收口。

请求：
```json
{"reason": "学生申请退款，管理员核验后同意"}
```

成功：
```json
{
  "code": "OK",
  "data": {
    "orderNo": "ORD202603070001",
    "status": "REFUNDED",
    "processedAt": "2026-03-07T08:30:00Z",
    "slotReleased": true,
    "reviewRemoved": true,
    "reason": "学生申请退款，管理员核验后同意",
    "externalRefundTriggered": true,
    "externalProviderTradeNo": "202603072200149999000001",
    "externalRefundRequestNo": "REFUND-ORD202603070001",
    "externalRefundStatus": "REFUND_SUCCESS"
  }
}
```

### 10.12.1 管理员查询支付宝沙箱交易
- `POST /admin/consult/orders/{orderNo}/payment/query`
- 鉴权：`ADMIN`
- 说明：
  - 仅适用于最近一次支付模式为 `SANDBOX` 的订单；
  - 会调用 `alipay.trade.query` 查询真实交易状态；
  - 若外部状态已为 `TRADE_SUCCESS`，且本地仍停留在 `PAYING`，会自动补记为 `PAID` 并写入支付记录。

### 10.12.2 管理员关闭支付宝沙箱交易
- `POST /admin/consult/orders/{orderNo}/payment/close`
- 鉴权：`ADMIN`
- 说明：
  - 仅允许对 `CREATED/PAYING` 且未支付的 `SANDBOX` 订单调用；
  - 会调用 `alipay.trade.close`；
  - 成功后本地订单同步置为 `CANCELED`，并释放已预占排期。

### 10.12.3 管理员查询支付宝沙箱退款状态
- `GET /admin/consult/orders/{orderNo}/payment/refund-query`
- 鉴权：`ADMIN`
- 说明：
  - 仅适用于已产生 `SANDBOX` 成功支付记录的订单；
  - 会固定使用 `REFUND-{orderNo}` 作为 `out_request_no` 调用 `alipay.trade.fastpay.refund.query`。

### 10.12.4 咨询售后申请列表
- `GET /admin/consult/after-sales/requests`
- 鉴权：`ADMIN`
- 参数：`page`、`size`、`keyword`（可选，支持 `orderNo/学生昵称/导师昵称`）、`status`（可选，`PENDING|APPROVED|REJECTED`）

### 10.12.5 审核咨询售后申请
- `POST /admin/consult/after-sales/requests/{requestId}/review`
- 鉴权：`ADMIN`
- 说明：
  - `approved=true` 时会复用统一退款逻辑；
  - `approved=false` 时仅更新售后单状态并回写审核备注。

请求：
```json
{"approved": true, "reviewNote": "核验通过，按平台规则退款"}
```

成功：
```json
{"code": "OK", "data": {"requestId": 8001, "orderNo": "ORD202603080001", "status": "APPROVED"}}
```

### 10.13 举报列表查询
- `GET /admin/content/reports`
- 鉴权：`ADMIN`
- 参数：`page`、`size`、`status`（可选）、`targetType`（可选）

成功：
```json
{
  "code": "OK",
  "data": {
    "records": [
      {
        "reportId": 5001,
        "targetType": "POST",
        "targetId": "9001",
        "contentPostId": "9001",
        "contentTitle": "治理样例帖子",
        "contentBody": "这里是帖子正文原文，管理员可先查看内容，再决定是否直达上下文。",
        "reasonCode": "ABUSE",
        "status": "PENDING",
        "latestAction": "NONE",
        "reportCount": 3
      }
    ],
    "total": 12
  }
}
```

### 10.14 临时补充测试积分
- `POST /admin/growth/points/grant`
- 鉴权：`ADMIN`

请求：
```json
{
  "userId": 1001,
  "points": 30,
  "reasonCode": "TEST_TOPUP"
}
```

成功：
```json
{
  "code": "OK",
  "message": "points granted",
  "data": {
    "userId": 1001,
    "deltaPoints": 30,
    "newBalance": 30,
    "reasonCode": "TEST_TOPUP"
  },
  "traceId": "trc_growth_admin_001",
  "timestamp": "2026-03-06T09:30:00Z"
}
```

### 10.15 举报处理历史查询
- `GET /admin/content/reports/{reportId}/actions`
- 鉴权：`ADMIN`

成功：
```json
{
  "code": "OK",
  "data": {
    "records": [
      {
        "actionId": 7001,
        "operatorUserId": 1,
        "operatorDisplayName": "系统管理员",
        "decision": "ACCEPTED",
        "action": "RESTORE",
        "comment": "经核查恢复展示",
        "createdAt": "2026-03-07T00:30:00Z"
      }
    ]
  }
}
```

### 10.15A 内容治理审计日志查询
- `GET /admin/content/audit-logs`
- 鉴权：`ADMIN`
- 参数：`page`、`size`、`targetType`（可选，`POST|COMMENT|USER`）、`targetId`（可选）、`actionType`（可选，如 `AUTO_HIDE|REPORT_DECISION|CONTENT_REVIEW_DECISION`）、`traceId`（可选）

成功：
```json
{
  "code": "OK",
  "data": {
    "records": [
      {
        "id": 9001,
        "traceId": "trc_20260308_000001",
        "operatorUserId": 0,
        "operatorDisplayName": "系统",
        "actionType": "AUTO_HIDE",
        "targetType": "POST",
        "targetId": "9001",
        "detailJson": "{"reasonCode":"REPORT_THRESHOLD_AUTO_HIDE","reportCount":3,"threshold":3}",
        "createdAt": "2026-03-08T15:00:00Z"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 10
  }
}
```

### 10.16 举报处置
- `POST /admin/content/reports/{reportId}/decision`
- 鉴权：`ADMIN`

请求：
```json
{
  "decision": "ACCEPTED",
  "action": "TAKE_DOWN",
  "comment": "命中违规规则，执行下架"
}
```

成功：
```json
{"code": "OK", "message": "report decided"}
```

### 10.17 待审队列查询
- `GET /admin/content/review-queue`
- 鉴权：`ADMIN`
- 参数：`page`、`size`、`sourceType`（可选）

### 10.18 待审项详情
- `GET /admin/content/review-queue/{itemId}`
- 鉴权：`ADMIN`

成功：
```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "itemId": 7001,
    "sourceType": "COMMUNITY_COMMENT",
    "targetType": "COMMENT",
    "targetId": "9002",
    "riskLevel": "MEDIUM",
    "reasonCode": "SENSITIVE_TERM",
    "preview": "这里是命中待审规则的评论预览",
    "contentTitle": null,
    "contentBody": "这里是评论全文",
    "postId": "8001",
    "postTitle": "父帖标题",
    "postBody": "父帖正文内容",
    "authorUserId": 1001,
    "authorDisplayName": "ReviewStudent",
    "authorRole": "STUDENT",
    "createdAt": "2026-03-07T01:00:00Z"
  }
}
```

说明：
- `POST` 场景返回帖子标题与正文，`postId` 为帖子自身 ID。
- `COMMENT` 场景返回评论全文，同时补充父帖 `postId/postTitle/postBody` 便于管理员复核上下文。

### 10.19 待审内容处置
- `POST /admin/content/review-queue/{itemId}/decision`
- 鉴权：`ADMIN`

请求：
```json
{
  "decision": "APPROVE",
  "comment": "人工审核通过"
}
```

### 10.20 审查策略查询/更新
- `GET /admin/content/moderation/policies`
- `PUT /admin/content/moderation/policies`
- 鉴权：`ADMIN`

请求（PUT）：
```json
{
  "aiInputEnabled": true,
  "aiOutputEnabled": true,
  "communityStrictReviewEnabled": true,
  "autoHideReportThreshold": 3
}
```

### 10.21 敏感词管理
- `GET /admin/content/sensitive-terms`
- `POST /admin/content/sensitive-terms`
- `PUT /admin/content/sensitive-terms/{termId}`
- `DELETE /admin/content/sensitive-terms/{termId}`
- `POST /admin/content/sensitive-terms/batch-status`
- `POST /admin/content/sensitive-terms/batch-delete`
- `POST /admin/content/sensitive-terms/import`（`multipart/form-data`，字段 `file`）
- `GET /admin/content/sensitive-terms/export`（返回 CSV）
- 鉴权：`ADMIN`
- 字段补充：
  - `termType`：敏感词类型，当前支持 `POLITICS|PORNOGRAPHY|TERROR|VIOLENCE|FRAUD|ABUSE|ADVERTISEMENT|ILLEGAL|OTHER`
  - 导入导出 CSV 列顺序：`term,termType,riskLevel,action,sourceScope,whitelist,enabled`

### 10.22 支付对账列表
- `GET /admin/payments/reconciliation`
- 鉴权：`ADMIN`
- 参数：`page`、`size`、`keyword`（可选，支持 `orderNo/学生昵称/导师昵称`）、`orderStatus`（可选）、`reconciliationStatus`（可选）
- 说明：
  - 当前为平台内对账视图：基于 `consult_orders + payment_records + audit_logs` 推导，不直接拉取支付宝官方账单；
  - `reconciliationStatus` 可能取值：`MATCHED`、`PENDING`、`REVIEW_REQUIRED`、`REVIEWED_PENDING`、`MANUALLY_RESOLVED`；
  - `issueTags` 用于标识异常原因，如 `UNPAID_STUCK`、`SUCCESS_NOT_APPLIED`、`REFUND_EXTERNAL_PENDING`。

成功：
```json
{
  "code": "OK",
  "data": {
    "records": [
      {
        "orderNo": "ORD202603070101",
        "studentUserId": 1001,
        "studentDisplayName": "Alice",
        "mentorUserId": 2001,
        "mentorDisplayName": "导师王老师",
        "amountFen": 9900,
        "orderStatus": "PAYING",
        "paymentMode": "SANDBOX",
        "paymentChannel": "ALIPAY",
        "latestPaymentStatus": "INIT",
        "providerTradeNo": null,
        "reconciliationStatus": "REVIEW_REQUIRED",
        "issueTags": ["UNPAID_STUCK"],
        "latestManualAction": null,
        "latestManualNote": null,
        "latestManualHandledAt": null,
        "createdAt": "2026-03-07T09:00:00Z",
        "paidAt": null,
        "closedAt": null,
        "latestPaymentCreatedAt": "2026-03-07T09:02:00Z"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 10
  }
}
```

### 10.23 支付对账详情
- `GET /admin/payments/reconciliation/{orderNo}`
- 鉴权：`ADMIN`
- 说明：返回订单主信息、推导出的对账结果、推荐人工处理动作、最新人工处理摘要以及支付记录时间线。

### 10.24 支付异常单人工处理
- `POST /admin/payments/reconciliation/{orderNo}/handle`
- 鉴权：`ADMIN`
- 说明：当前支持以下动作：
  - `MARK_PAID`：对“支付成功但未落单”的异常单执行人工补记；
  - `CANCEL_UNPAID`：对长时间未支付仍挂起的异常单执行人工取消；
  - `CONFIRM_EXTERNAL_REFUND`：对 `REFUNDED + SANDBOX` 场景登记“外部退款已线下确认完成”；
  - `MARK_REVIEWED`：仅登记人工核对备注，不改订单状态。

请求：
```json
{
  "action": "MARK_PAID",
  "note": "管理员根据支付记录人工补记成功"
}
```

成功：
```json
{
  "code": "OK",
  "data": {
    "orderNo": "ORD202603070101",
    "action": "MARK_PAID",
    "orderStatus": "PAID",
    "reconciliationStatus": "MANUALLY_RESOLVED",
    "handledAt": "2026-03-07T09:05:00Z",
    "note": "管理员根据支付记录人工补记成功"
  }
}
```

## 11. 统一枚举（契约锚点）

> 枚举定义的唯一真相源见 [00_README_AGENT_START.md §统一枚举定义](./00_README_AGENT_START.md#统一枚举定义ssot)。
> 本文引用不再独立维护，以 SSOT 为准。

- **`UserRole`**：`STUDENT | MENTOR | ENTERPRISE | ADMIN`
- **`UserTier`**：`FREE | PREMIUM`
- **`OrderStatus`**：`CREATED | PAYING | PAID | ANSWERED | CLOSED | FAILED | CANCELED | REFUNDED`
- **`SkillNodeStatus`**：`LOCKED | LEARNING | MASTERED`
- **`AiTaskType`**：`RESUME | INTERVIEW_TEXT | INTERVIEW_SUMMARY | COMMUNITY_REPLY | ICEBREAK | STT | TTS`
- **`BountySubmissionStatus`**：`SUBMITTED | REVIEWING | ACCEPTED | REJECTED`
- **`UserAccountStatus`**：`ACTIVE | PENDING | SUSPENDED`
- **`PaymentMode`**：`SANDBOX | MOCK`
- **`ModerationAction`**：`PASS | MASK | BLOCK | REVIEW`
- **`ModerationRiskLevel`**：`LOW | MEDIUM | HIGH | CRITICAL`
- **`ReportTargetType`**：`POST | COMMENT | USER`
- **`ReportStatus`**：`PENDING | ACCEPTED | REJECTED | CLOSED`
- **`ModerationSourceType`**：`AI_INPUT | AI_OUTPUT | COMMUNITY_POST | COMMUNITY_COMMENT | CONSULT_MESSAGE | BOUNTY_TEXT`

## 12. 版本策略
- 当前版本：`v1`
- 向后兼容仅允许新增可选字段。
- 破坏性变更必须升级到 `/api/v2`。

## 13. 本文档范围
覆盖 v1 的鉴权、学生画像（冷启动+动态标签）、AI（含 SSE/配额/历史/导出+内容审查）、成长、社区（含举报+7日贡献榜）、导师浏览、咨询支付（含评价）、企业悬赏（含任务提交画像筛选）、通知、管理后台（含配额/成本/密码重置/内容治理）接口。

## 14. 决策摘要
- 响应体统一。
- 枚举统一不可漂移，以 SSOT 为准。
- 支付模式通过后台开关控制。
- 治理接口统一走 `MOD-xxxx` 错误码家族。
- 社区贡献榜评分公式在 v1 固定，不做动态权重配置。

## 15. 验收标准
1. 关键接口有请求/成功/错误示例。
2. 角色权限拦截准确。
