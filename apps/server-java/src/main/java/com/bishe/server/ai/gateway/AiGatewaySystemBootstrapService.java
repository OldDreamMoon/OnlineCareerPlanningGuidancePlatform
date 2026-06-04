package com.bishe.server.ai.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 为 AI 简历 / 面试链路自动补齐系统级 provider / prompt template / route，
 * 避免本地或新环境在 routed 模式下还需要先进后台手动配置才能联调。
 */
@Service
public class AiGatewaySystemBootstrapService implements ApplicationRunner {

    static final String RESUME_PROVIDER_CODE = "SYSTEM_RESUME_GEMINI_NATIVE";
    static final String RESUME_ROUTE_CODE = "SYSTEM_RESUME_OPTIMIZE";
    static final String RESUME_TASK_TYPE = "RESUME";
    static final String RESUME_SCENE_CODE = "RESUME_OPTIMIZE";
    static final String RESUME_TEMPLATE_NAME = "RESUME_OPTIMIZE_CORE";
    static final String LEGACY_RESUME_PRIMARY_ROUTE_CODE = "RESUME_OPTIMIZE_MAIN";
    static final String LEGACY_RESUME_FALLBACK_ROUTE_CODE = "RESUME_DEFAULT";
    static final String LEGACY_RESUME_TEMPLATE_NAME = "resume_optimize_cn";
    static final int RESUME_TEMPLATE_VERSION = 1;
    static final int RESUME_ROUTE_PRIORITY = 5;
    static final int MIN_RESUME_PROVIDER_TIMEOUT_MS = 60000;

    static final String INTERVIEW_PROVIDER_CODE = "SYSTEM_INTERVIEW_GEMINI_NATIVE";
    static final String INTERVIEW_TEXT_TASK_TYPE = "INTERVIEW_TEXT";
    static final String INTERVIEW_SUMMARY_TASK_TYPE = "INTERVIEW_SUMMARY";
    static final String STT_TASK_TYPE = "STT";
    static final String TTS_TASK_TYPE = "TTS";
    static final String INTERVIEW_OPENING_SCENE_CODE = "INTERVIEW_OPENING";
    static final String INTERVIEW_REPLY_SCENE_CODE = "INTERVIEW_REPLY";
    static final String INTERVIEW_ANSWER_HELPER_SCENE_CODE = "INTERVIEW_ANSWER_HELPER";
    static final String INTERVIEW_SUMMARY_SCENE_CODE = "INTERVIEW_SUMMARY";
    static final String INTERVIEW_STT_SCENE_CODE = "INTERVIEW_VOICE_TRANSCRIBE";
    static final String INTERVIEW_TTS_SCENE_CODE = "TEXT_TO_SPEECH";
    static final String INTERVIEW_OPENING_ROUTE_CODE = "SYSTEM_INTERVIEW_OPENING";
    static final String INTERVIEW_REPLY_ROUTE_CODE = "SYSTEM_INTERVIEW_REPLY";
    static final String INTERVIEW_ANSWER_HELPER_ROUTE_CODE = "SYSTEM_INTERVIEW_ANSWER_HELPER";
    static final String INTERVIEW_SUMMARY_ROUTE_CODE = "SYSTEM_INTERVIEW_SUMMARY";
    static final String INTERVIEW_STT_ROUTE_CODE = "SYSTEM_INTERVIEW_VOICE_TRANSCRIBE";
    static final String INTERVIEW_TTS_ROUTE_CODE = "SYSTEM_INTERVIEW_TTS";
    static final String INTERVIEW_OPENING_TEMPLATE_NAME = "INTERVIEW_OPENING_CORE";
    static final String INTERVIEW_REPLY_TEMPLATE_NAME = "INTERVIEW_REPLY_CORE";
    static final String INTERVIEW_ANSWER_HELPER_TEMPLATE_NAME = "INTERVIEW_ANSWER_HELPER_CORE";
    static final String INTERVIEW_SUMMARY_TEMPLATE_NAME = "INTERVIEW_SUMMARY_CORE";
    static final int INTERVIEW_TEMPLATE_VERSION = 2;
    static final int INTERVIEW_ROUTE_PRIORITY = 5;
    static final int MIN_INTERVIEW_PROVIDER_TIMEOUT_MS = 45000;

    static final String LEGACY_INTERVIEW_TEXT_FALLBACK_ROUTE_CODE = "INTERVIEW_TEXT_DEFAULT";
    static final String LEGACY_INTERVIEW_OPENING_ROUTE_CODE = "INTERVIEW_OPENING_MAIN";
    static final String LEGACY_INTERVIEW_REPLY_ROUTE_CODE = "INTERVIEW_REPLY_MAIN";
    static final String LEGACY_INTERVIEW_SUMMARY_FALLBACK_ROUTE_CODE = "INTERVIEW_SUMMARY_DEFAULT";
    static final String LEGACY_INTERVIEW_SUMMARY_ROUTE_CODE = "INTERVIEW_SUMMARY_MAIN";
    static final String LEGACY_INTERVIEW_STT_FALLBACK_ROUTE_CODE = "STT_DEFAULT";
    static final String LEGACY_INTERVIEW_STT_GEMINI_ROUTE_CODE = "STT_INTERVIEW_GEMINI_CANARY";
    static final String LEGACY_INTERVIEW_STT_OPENAI_ROUTE_CODE = "STT_INTERVIEW_OPENAI_MAIN";
    static final String LEGACY_INTERVIEW_TTS_FALLBACK_ROUTE_CODE = "TTS_DEFAULT";
    static final String LEGACY_INTERVIEW_TTS_ROUTE_CODE = "TTS_MAIN";
    static final String LEGACY_INTERVIEW_OPENING_TEMPLATE_NAME = "interview_opening_cn";
    static final String LEGACY_INTERVIEW_REPLY_TEMPLATE_NAME = "interview_followup_cn";
    static final String LEGACY_INTERVIEW_SUMMARY_TEMPLATE_NAME = "interview_summary_cn";
    static final String DEFAULT_GEMINI_TTS_MODEL = "gemini-2.5-flash-preview-tts";
    static final String COMMUNITY_PRE_ANSWER_SCENE_CODE = "COMMUNITY_PRE_ANSWER";
    static final String COMMUNITY_PRE_ANSWER_ROUTE_CODE = "SYSTEM_COMMUNITY_PRE_ANSWER";
    static final String COMMUNITY_PRE_ANSWER_TEMPLATE_NAME = "COMMUNITY_PRE_ANSWER_CORE";
    static final int COMMUNITY_PRE_ANSWER_TEMPLATE_VERSION = 1;
    static final int COMMUNITY_PRE_ANSWER_ROUTE_PRIORITY = 5;
    static final String ICEBREAK_TASK_TYPE = "ICEBREAK";
    static final String ICEBREAK_SCENE_CODE = "ICEBREAK_MESSAGE";
    static final String ICEBREAK_ROUTE_CODE = "SYSTEM_ICEBREAK_MESSAGE";
    static final String ICEBREAK_TEMPLATE_NAME = "ICEBREAK_MESSAGE_CORE";
    static final int ICEBREAK_TEMPLATE_VERSION = 1;
    static final int ICEBREAK_ROUTE_PRIORITY = 5;
    static final String MENTOR_PREP_PROVIDER_CODE = "SYSTEM_MENTOR_PREP_GEMINI_NATIVE";
    static final String MENTOR_PREP_TASK_TYPE = "COMMUNITY_REPLY";
    static final String MENTOR_PREP_SCENE_CODE = "MENTOR_PREP_SHEET_GENERATE";
    static final String MENTOR_PREP_ROUTE_CODE = "SYSTEM_MENTOR_PREP_SHEET";
    static final String MENTOR_PREP_TEMPLATE_NAME = "MENTOR_PREP_SHEET_CORE";
    static final int MENTOR_PREP_TEMPLATE_VERSION = 1;
    static final int MENTOR_PREP_ROUTE_PRIORITY = 5;
    static final int MIN_MENTOR_PREP_PROVIDER_TIMEOUT_MS = 30000;
    static final String PORTRAIT_SUMMARY_TASK_TYPE = "PORTRAIT_SUMMARY";
    static final String PORTRAIT_SUMMARY_SCENE_CODE = "STUDENT_PORTRAIT_SUMMARY";
    static final String PORTRAIT_SUMMARY_ROUTE_CODE = "SYSTEM_STUDENT_PORTRAIT_SUMMARY";
    static final String PORTRAIT_SUMMARY_TEMPLATE_NAME = "STUDENT_PORTRAIT_SUMMARY_CORE";
    static final int PORTRAIT_SUMMARY_TEMPLATE_VERSION = 1;
    static final int PORTRAIT_SUMMARY_ROUTE_PRIORITY = 5;

    private static final Logger log = LoggerFactory.getLogger(AiGatewaySystemBootstrapService.class);

    private static final String GEMINI_PROVIDER_EXTRA_CONFIG_TEMPLATE = """
            {"bootstrapManaged":true,"transport":"gemini-native","authMode":"%s"}
            """;

    private static final String RESUME_PROVIDER_DISPLAY_NAME = "System Resume Gemini Native";
    private static final String RESUME_TEMPLATE_DESCRIPTION = "系统预置：AI 简历优化默认模板";
    private static final String RESUME_TEMPLATE_CONTENT = """
            你是简历优化助手，请围绕目标岗位和投递语境输出结构化建议。
            当前上下文：
            - targetRole={{targetRole}}
            - targetContext={{targetContext}}
            - inputMode={{inputMode}}
            - jobDescription={{jobDescription}}
            - resumeFileName={{resumeFileName}}

            输出要求：
            1. 只返回 JSON，不要 markdown，不要额外解释。
            2. JSON schema:
            {"summary":string,"strengths":string[],"risks":string[],"suggestions":string[],"scoreLabel":string,"structureItems":[{"label":string,"score":number,"tip":string}],"rewriteItems":[{"id":string,"title":string,"problem":string,"beforeText":string,"afterText":string}]}。
            3. summary 聚焦当前岗位匹配度、投递可用度和最需要补强的方向。
            4. strengths / risks / suggestions 按需输出 1-6 条，不必凑满，最多 6 条，语言要直接、可执行、适合中文产品界面展示。
            5. scoreLabel 仅允许使用 S、A+、A、A-、B+、B、B-、C+、C、D 之一。
            6. structureItems 输出 4-6 项，score 范围只能是 1-5；label 简洁明确，如岗位匹配度、项目经历、量化结果、技术细节、教育信息、版面结构。
            7. rewriteItems 输出 3-4 条，必须包含 id / title / problem / beforeText / afterText；afterText 必须是可直接放进简历里的成品句子。
            8. 当 targetContext 或 jobDescription 未提供时，也要结合 targetRole 与简历内容给出合理建议。
            9. 当 inputMode=pdf 时，可结合 resumeFileName 与 PDF 内容理解；当 inputMode=text 时，以提交的简历文本为主。
            """;
    private static final String RESUME_TEMPLATE_VARIABLES_JSON = """
            {
              "targetRole": {
                "required": true,
                "sampleValue": "Backend Engineer",
                "description": "目标岗位"
              },
              "targetContext": {
                "required": false,
                "defaultValue": "未指定",
                "sampleValue": "校招正式批",
                "description": "目标语境"
              },
              "inputMode": {
                "required": false,
                "defaultValue": "text",
                "sampleValue": "pdf",
                "description": "输入模式"
              },
              "jobDescription": {
                "required": false,
                "defaultValue": "未提供",
                "sampleValue": "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与性能优化。",
                "description": "岗位 JD"
              },
              "resumeFileName": {
                "required": false,
                "defaultValue": "未提供",
                "sampleValue": "resume.pdf",
                "description": "PDF 文件名"
              }
            }
            """;
    private static final String RESUME_ROUTE_EXTRA_CONFIG_JSON = """
            {"bootstrapManaged":true,"supportsPdfNative":true}
            """;

    private static final String INTERVIEW_PROVIDER_DISPLAY_NAME = "System Interview Gemini Native";
    private static final String INTERVIEW_OPENING_TEMPLATE_DESCRIPTION = "系统预置：AI 面试首问模板";
    private static final String INTERVIEW_OPENING_TEMPLATE_CONTENT = """
            你是中文模拟面试官，请为当前候选人生成开场第一问。
            当前上下文：
            - targetRole={{targetRole}}
            - sessionContext={{sessionContext}}
            - resumeContext={{resumeContext}}

            输出要求：
            1. 只返回 JSON，不要 markdown，不要额外解释。
            2. JSON schema: {"firstQuestion":string}。
            3. firstQuestion 必须像真人面试官开场，先用一句自然问候或过渡把候选人带进场景，再抛出首问。
            4. firstQuestion 必须使用简体中文表达；除公司名、技术名词或必要缩写外，不得输出整段英文或英文主导句子。
            5. 语气自然、克制、口语化，不要写成系统提示、评分标准或教程文案。
            6. 首问优先围绕岗位匹配度最高的一段真实经历，鼓励候选人讲清背景、个人动作、关键取舍和量化结果。
            7. 当 sessionContext 不为空时，必须遵守其中的面试类型、面试官风格、练习强度、作答方式和岗位语境。
            8. 当 resumeContext 不为空时，优先围绕其中最值得深挖的一段真实经历切入，不要替候选人补全事实。
            """;
    private static final String INTERVIEW_OPENING_TEMPLATE_VARIABLES_JSON = """
            {
              "targetRole": {
                "required": true,
                "sampleValue": "Backend Engineer",
                "description": "目标岗位"
              },
              "sessionContext": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "interviewType=PROJECT_DEEP_DIVE\\ninterviewerStyle=STANDARD\\ndifficulty=MEDIUM",
                "description": "本轮面试配置快照"
              },
              "resumeContext": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "候选人已授权带入最近一份简历\\n简历摘要=订单中心重构经验",
                "description": "候选人授权带入的简历补充上下文"
              }
            }
            """;
    private static final String INTERVIEW_REPLY_TEMPLATE_DESCRIPTION = "系统预置：AI 面试追问与点评模板";
    private static final String INTERVIEW_REPLY_TEMPLATE_CONTENT = """
            你是中文模拟面试官兼轻量陪练，请基于候选人的回答继续追问并给出即时点评。
            当前上下文：
            - targetRole={{targetRole}}
            - sessionContext={{sessionContext}}
            - resumeContext={{resumeContext}}
            - userAnswerCount={{userAnswerCount}}
            - replyRoundLimit={{replyRoundLimit}}
            - replyRoundUsed={{replyRoundUsed}}
            - remainingReplyRounds={{remainingReplyRounds}}
            - historyLines={{historyLines}}
            - currentAnswer={{currentAnswer}}

            输出要求：
            1. 只返回 JSON，不要 markdown，不要额外解释。
            2. JSON schema: {"followUpQuestion":string,"coachFeedback":string,"scoreHint":number,"shouldFinish":boolean,"finishReason":string}。
            3. followUpQuestion 必须像真人面试官顺着候选人的上一轮回答自然接话，保持口语化，不要像 checklist。
            4. 每次只推进 1 个重点，优先追问贡献边界、技术细节、结果验证、取舍和复盘，不要一次塞很多子问题。
            5. shouldFinish=true 时，followUpQuestion 要像真人收尾，例如先简短认可，再说明这一轮先到这里，接下来进入总结。
            6. followUpQuestion、coachFeedback 与 finishReason 必须使用简体中文表达；除公司名、技术名词或必要缩写外，不得输出整段英文或英文主导句子。
            7. coachFeedback 用产品界面可直接展示的中文短评，包含一处亮点和一处可改进点，语气支持但不幼态。
            8. scoreHint 输出 40-98 的整数；当 shouldFinish=false 时 finishReason 可以省略或留空。
            9. 当 sessionContext 不为空时，必须遵守其中的面试类型、面试官风格、练习强度、作答方式和岗位语境。
            10. 当 resumeContext 不为空时，请把它当作候选人的背景补充：优先围绕其中真实经历继续追问，不要捏造简历里没有的信息。
            11. 不要为了缩短轮次而过早结束。除非候选人已经连续讲清背景、动作、结果、验证方式与关键取舍，或者回答明显重复，否则 shouldFinish 保持 false。
            12. 正常情况下，至少拿到 4 轮有效用户回答后再考虑 shouldFinish=true；如果信息仍然不充分，就继续追问。
            13. 当提供了 replyRoundLimit / remainingReplyRounds 时，把它们仅作为内部规划参考：replyRoundLimit 是最大安全上限，不是必须用满的目标轮次；如果信息已经充分，应提前结束。
            14. 不要在 followUpQuestion 或 coachFeedback 中直接向候选人提到轮次数、剩余次数、安全上限等内部控制信息；当 remainingReplyRounds 较少时，请准备自然收尾。
            """;
    private static final String INTERVIEW_REPLY_TEMPLATE_VARIABLES_JSON = """
            {
              "targetRole": {
                "required": true,
                "sampleValue": "Backend Engineer",
                "description": "目标岗位"
              },
              "sessionContext": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "interviewType=PROJECT_DEEP_DIVE\\ninterviewerStyle=COACHING\\ndifficulty=MEDIUM",
                "description": "本轮面试配置快照"
              },
              "resumeContext": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "候选人已授权带入最近一份简历\\n简历摘要=订单中心重构经验",
                "description": "候选人授权带入的简历补充上下文"
              },
              "historyLines": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "ASSISTANT: 请介绍一个项目\\nUSER: 我负责订单中心重构",
                "description": "历史对话"
              },
              "currentAnswer": {
                "required": true,
                "sampleValue": "我主导了订单中心重构...",
                "description": "当前回答"
              },
              "userAnswerCount": {
                "required": false,
                "defaultValue": "1",
                "sampleValue": "2",
                "description": "当前正在评估的用户回答轮次"
              },
              "replyRoundLimit": {
                "required": false,
                "defaultValue": "0",
                "sampleValue": "30",
                "description": "本轮面试的最大安全轮次上限，仅供 AI 内部规划"
              },
              "replyRoundUsed": {
                "required": false,
                "defaultValue": "0",
                "sampleValue": "7",
                "description": "当前回答前已落库的历史作答轮次"
              },
              "remainingReplyRounds": {
                "required": false,
                "defaultValue": "0",
                "sampleValue": "22",
                "description": "按当前回答轮次计算的剩余可用轮次，仅供 AI 内部规划"
              }
            }
            """;
    private static final String INTERVIEW_SUMMARY_TEMPLATE_DESCRIPTION = "系统预置：AI 面试总结模板";
    private static final String INTERVIEW_ANSWER_HELPER_TEMPLATE_DESCRIPTION = "系统预置：AI 面试回答辅助诊断模板";
    private static final String INTERVIEW_ANSWER_HELPER_TEMPLATE_CONTENT = """
            你是中文面试回答辅助诊断助手，请只分析候选人最近一轮已发送回答，不要继续追问，也不要代写完整答案。
            当前上下文：
            - targetRole={{targetRole}}
            - sessionContext={{sessionContext}}
            - resumeContext={{resumeContext}}
            - answerHelperCueKeys={{answerHelperCueKeys}}
            - historyLines={{historyLines}}
            - currentAnswer={{currentAnswer}}

            输出要求：
            1. 只返回 JSON，不要 markdown，不要额外解释。
            2. JSON schema: {"overallLevel":string,"overallSummary":string,"items":[{"key":string,"level":string,"summary":string,"nextAction":string}],"details":string[]}。
            3. 你只评价 currentAnswer 这一轮，不对整个会话做终局判断。
            4. overallLevel 与 items[].level 只允许使用 READY、WARN、INFO。
            5. items[].key 只允许使用 STAR、METRICS、COMPLETENESS；如果 answerHelperCueKeys 为空，则默认按这三项输出。
            6. overallSummary 用 1-2 句简体中文概括这轮回答最值得先补的一点，同时指出一处已经做得不错的地方。
            7. items[].summary 要直接指出当前表现；items[].nextAction 只给一句可执行补充建议，不要替候选人虚构经历。
            8. details 输出 1-3 条简短补充提醒，可为空数组；语言适合直接展示在中文产品界面里。
            9. 所有字段都必须使用简体中文表达；除公司名、技术名词或必要缩写外，不得输出整段英文或英文主导句子。
            10. 如果提供了 sessionContext 或 resumeContext，请把它们仅作为理解语境的辅助背景，不要编造候选人没有说过的事实。
            """;
    private static final String INTERVIEW_ANSWER_HELPER_TEMPLATE_VARIABLES_JSON = """
            {
              "targetRole": {
                "required": true,
                "sampleValue": "Backend Engineer",
                "description": "目标岗位"
              },
              "sessionContext": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "interviewType=PROJECT_DEEP_DIVE\\ninterviewerStyle=STANDARD\\ndifficulty=MEDIUM",
                "description": "本轮面试配置快照"
              },
              "resumeContext": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "候选人已授权带入最近一份简历\\n简历摘要=订单中心重构经验",
                "description": "候选人授权带入的简历补充上下文"
              },
              "answerHelperCueKeys": {
                "required": false,
                "defaultValue": "STAR, METRICS, COMPLETENESS",
                "sampleValue": "METRICS, COMPLETENESS",
                "description": "本轮启用的回答辅助维度"
              },
              "historyLines": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "ASSISTANT: 请介绍一个项目\\nUSER: 我负责订单中心重构",
                "description": "最近若干轮对话，帮助理解当前回答语境"
              },
              "currentAnswer": {
                "required": true,
                "sampleValue": "我主导了订单中心重构，通过缓存预热和索引优化把延迟降低了 30%。",
                "description": "候选人最近一轮已发送回答"
              }
            }
            """;
    private static final String INTERVIEW_SUMMARY_TEMPLATE_CONTENT = """
            你是中文模拟面试总结助手，请输出结构化复盘。
            当前上下文：
            - targetRole={{targetRole}}
            - sessionContext={{sessionContext}}
            - resumeContext={{resumeContext}}
            - historyLines={{historyLines}}

            输出要求：
            1. 只返回 JSON，不要 markdown，不要额外解释。
            2. JSON schema: {"overallScore":number,"strengths":string[],"weaknesses":string[],"suggestions":string[]}。
            3. overallScore 输出 50-100 的整数。
            4. strengths / weaknesses / suggestions 按实际情况各输出 1-6 条，不必凑满，最多 6 条；语言要像真实复盘结论，直接、具体、可执行，不要空话。
            5. strengths / weaknesses / suggestions 的文本内容必须使用简体中文表达；除公司名、技术名词或必要缩写外，不得输出整段英文或英文主导句子。
            6. 总结必须基于真实对话内容，不能臆造候选人没说过的经历或结论。
            7. 当 sessionContext 不为空时，请结合这轮设定判断回答是否达到了相应风格和强度预期。
            8. 当 resumeContext 不为空时，请结合候选人授权带入的简历背景判断其表达是否真正讲清了经历、结果与岗位匹配。
            """;
    private static final String INTERVIEW_SUMMARY_TEMPLATE_VARIABLES_JSON = """
            {
              "targetRole": {
                "required": true,
                "sampleValue": "Backend Engineer",
                "description": "目标岗位"
              },
              "sessionContext": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "interviewType=PROJECT_DEEP_DIVE\\ninterviewerStyle=STANDARD\\ndifficulty=MEDIUM",
                "description": "本轮面试配置快照"
              },
              "resumeContext": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "候选人已授权带入最近一份简历\\n简历摘要=订单中心重构经验",
                "description": "候选人授权带入的简历补充上下文"
              },
              "historyLines": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "ASSISTANT: 请介绍一个项目\\nUSER: 我负责订单中心重构",
                "description": "完整对话"
              }
            }
            """;
    private static final String INTERVIEW_ROUTE_EXTRA_CONFIG_JSON = """
            {"bootstrapManaged":true}
            """;
    private static final String INTERVIEW_STT_ROUTE_EXTRA_CONFIG_JSON = """
            {"bootstrapManaged":true,"transcriptionInstruction":"请将音频完整转写为纯文本，不要添加解释或格式化。"}
            """;
    private static final String INTERVIEW_TTS_ROUTE_EXTRA_CONFIG_JSON = """
            {"bootstrapManaged":true,"defaultStylePrompt":"Read aloud in a warm and friendly tone:","defaultVoiceName":"Zephyr"}
            """;
    private static final String COMMUNITY_MENTOR_PROVIDER_DISPLAY_NAME = "System Community & Mentor Gemini Native";
    private static final String COMMUNITY_PRE_ANSWER_TEMPLATE_DESCRIPTION = "系统预置：社区预答与导师回复草稿模板";
    private static final String COMMUNITY_PRE_ANSWER_TEMPLATE_CONTENT = """
            你是求职社区与导师咨询草稿助手，请为当前固定场景生成一版可直接使用的中文草稿。
            当前上下文：
            - generationMode={{generationMode}}
            - title={{title}}
            - content={{content}}
            - context={{context}}
            - currentDraft={{currentDraft}}
            - instruction={{instruction}}

            输出要求：
            1. 只返回 JSON，不要 markdown 之外的解释。JSON schema: {"draftComment":string}。
            2. 当 generationMode=POLISH_EXISTING 或 currentDraft 不为空时，请站在导师回复学生的口吻，结合 context 与 instruction 润色或补全 currentDraft，语气专业、真诚、具体，不夸大承诺。
            3. 当 generationMode=COMMUNITY_POST 或 currentDraft 为空时，请把它视为社区帖子预答草稿：draftComment 必须使用简体中文 Markdown 组织内容，至少包含一个三级标题和一个无序列表，可以适当加入加粗，但不要输出代码块。
            4. 所有输出都必须自然、克制、产品化，适合学生或导师直接在平台内继续编辑或发送。
            5. 只能基于 title / content / context / instruction / currentDraft 中已提供的信息生成，不要编造用户没有给出的经历、公司、项目或承诺。
            6. 当 instruction 不为空时，请优先遵守其中的方向约束；当 context 不为空时，请把它当作补充背景而不是最终成稿原文。
            7. 如果信息明显不足，也要先给出一版保守、可继续追问的草稿，不要返回“信息不足”之类的拒答。
            """;
    private static final String COMMUNITY_PRE_ANSWER_TEMPLATE_VARIABLES_JSON = """
            {
              "generationMode": {
                "required": false,
                "defaultValue": "COMMUNITY_POST",
                "sampleValue": "POLISH_EXISTING",
                "description": "生成模式：社区预答或导师回复润色"
              },
              "title": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "简历项目经历应该怎么写得更像真实实习产出？",
                "description": "帖子标题或咨询主题"
              },
              "content": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "我现在是大三，最近在改简历，但总觉得项目描述太像流水账。",
                "description": "帖子正文或问题描述"
              },
              "context": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "订单背景：学生希望优先判断简历项目表达和岗位匹配度。",
                "description": "导师订单上下文或额外补充背景"
              },
              "currentDraft": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "我已经看过你的背景，先给你一个方向判断。",
                "description": "待润色的当前草稿"
              },
              "instruction": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "语气再温和一点，并把建议拆成 3 条。",
                "description": "额外生成要求"
              }
            }
            """;
    private static final String ICEBREAK_TEMPLATE_DESCRIPTION = "系统预置：导师破冰消息模板";
    private static final String ICEBREAK_TEMPLATE_CONTENT = """
            你是导师咨询破冰助手，请生成一条学生发给导师的首条私信草稿。
            当前上下文：
            - mentorDisplayName={{mentorDisplayName}}
            - expertiseTags={{expertiseTags}}
            - mentorBio={{mentorBio}}
            - studentGoal={{studentGoal}}

            输出要求：
            1. 只返回 JSON，不要 markdown，不要额外解释。JSON schema: {"messageDraft":string}。
            2. messageDraft 必须使用简体中文，语气真诚、简洁、礼貌，不要写成群发广告，也不要过度热情。
            3. 优先围绕 studentGoal 与导师擅长方向建立联系，可适度引用 mentorBio 或 expertiseTags，但不要复读简历式长介绍。
            4. 文案长度控制在 60-140 字之间，适合平台私信开场；不要直接索要微信、电话或外部联系方式。
            5. 不能编造学生背景、导师经历或合作结果，只能基于当前上下文组织表达。
            """;
    private static final String ICEBREAK_TEMPLATE_VARIABLES_JSON = """
            {
              "mentorDisplayName": {
                "required": false,
                "defaultValue": "老师",
                "sampleValue": "王老师",
                "description": "导师展示名"
              },
              "expertiseTags": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "前端工程化, React, 校招求职",
                "description": "导师擅长标签"
              },
              "mentorBio": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "多年大厂前端与校招辅导经验。",
                "description": "导师简介摘要"
              },
              "studentGoal": {
                "required": false,
                "defaultValue": "想请教求职准备建议",
                "sampleValue": "想请教前端项目表达和校招准备路径",
                "description": "学生当前咨询目标"
              }
            }
            """;
    private static final String COMMUNITY_ASSIST_ROUTE_EXTRA_CONFIG_JSON = """
            {"bootstrapManaged":true}
            """;
    private static final String MENTOR_PREP_TEMPLATE_DESCRIPTION = "系统预置：导师咨询准备单生成模板";
    private static final String MENTOR_PREP_TEMPLATE_CONTENT = """
            你是导师咨询准备单助手，请根据学生当前画像、最近 AI 结果、当前咨询场景和导师公开资料，生成一版可继续编辑的咨询准备单草稿。
            当前上下文：
            - scene={{scene}}
            - targetPosition={{targetPosition}}
            - signalTags={{signalTags}}
            - mentorContext={{mentorContext}}
            - studentContext={{studentContext}}
            - latestResumeContext={{latestResumeContext}}
            - latestInterviewSummaryContext={{latestInterviewSummaryContext}}
            - fallbackSummaryDraft={{fallbackSummaryDraft}}
            - fallbackCoreQuestions={{fallbackCoreQuestions}}
            - fallbackSuggestedMaterials={{fallbackSuggestedMaterials}}
            - fallbackExpectedOutcomes={{fallbackExpectedOutcomes}}

            输出要求：
            1. 只返回 JSON，不要 markdown，不要额外解释。
            2. JSON schema: {"summaryDraft":string,"coreQuestions":string[],"suggestedMaterials":string[],"expectedOutcomes":string[]}。
            3. summaryDraft 必须使用简体中文，适合作为学生稍后手动继续编辑的第一版草稿；聚焦当前困扰、咨询目标和希望导师帮助判断的重点，不要编造学生没有提供的经历。
            4. coreQuestions 必须输出 3 条中文问题，按优先级排序，适合真实咨询场景直接使用；每条问题都要具体、可落地，不要空泛重复。
            5. suggestedMaterials 输出 2-5 条，优先参考 fallbackSuggestedMaterials 与导师公开资料，尽量使用中文产品界面友好的短语。
            6. expectedOutcomes 输出 1-3 条，优先使用以下表达或其等价近义：获得简历修改建议、获得面试复盘建议、获得求职方向建议、获得综合咨询建议。
            7. 如果 latestResumeContext 或 latestInterviewSummaryContext 不为空，优先把其中最相关的结论融入准备单；如果为空，则忽略，不要凭空补造。
            8. fallbackSummaryDraft / fallbackCoreQuestions / fallbackSuggestedMaterials / fallbackExpectedOutcomes 只是兜底参考，你可以在信息足够时给出更具体、更贴合当前导师与学生组合的版本，但不要明显偏离当前场景。
            9. 所有输出都必须自然、克制、适合中文求职产品界面直接展示。
            """;
    private static final String MENTOR_PREP_TEMPLATE_VARIABLES_JSON = """
            {
              "scene": {
                "required": true,
                "sampleValue": "简历诊断",
                "description": "当前咨询场景"
              },
              "targetPosition": {
                "required": true,
                "sampleValue": "前端开发工程师",
                "description": "学生当前目标岗位"
              },
              "signalTags": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "问题场景：简历诊断 | 目标岗位：前端开发工程师 | 技能：React",
                "description": "当前推荐链路已有的匹配信号标签"
              },
              "mentorContext": {
                "required": true,
                "sampleValue": "导师：王老师\\n公司：字节跳动\\n职位：高级前端工程师",
                "description": "导师公开资料摘要"
              },
              "studentContext": {
                "required": true,
                "sampleValue": "目标岗位：前端开发工程师\\n技能标签：React、TypeScript",
                "description": "学生资料与画像摘要"
              },
              "latestResumeContext": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "最近简历总结：简历方向较清晰，但量化结果不足。",
                "description": "最近一次简历优化结果摘要"
              },
              "latestInterviewSummaryContext": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "最近面试短板：项目表达偏空、结果验证不足。",
                "description": "最近一次面试总结摘要"
              },
              "fallbackSummaryDraft": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "我目前正在准备前端开发工程师方向的求职...",
                "description": "启发式兜底摘要"
              },
              "fallbackCoreQuestions": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "1. 我的简历里哪些内容最需要优先补强？",
                "description": "启发式兜底核心问题"
              },
              "fallbackSuggestedMaterials": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "我的最新简历 | 目标岗位 JD | 项目介绍",
                "description": "启发式兜底材料建议"
              },
              "fallbackExpectedOutcomes": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "获得简历修改建议 | 获得综合咨询建议",
                "description": "启发式兜底结果建议"
              }
            }
            """;
    private static final String MENTOR_PREP_ROUTE_EXTRA_CONFIG_JSON = """
            {"bootstrapManaged":true}
            """;
    private static final String PORTRAIT_SUMMARY_TEMPLATE_DESCRIPTION = "系统预置：学生成长画像表达层总结模板";
    private static final String PORTRAIT_SUMMARY_TEMPLATE_CONTENT = """
            你是学生成长画像表达层助手，请只根据系统给出的结构化画像事实，生成一版适合学生资料页展示的中文总结。
            当前上下文：
            - targetPosition={{targetPosition}}
            - strengthTags={{strengthTags}}
            - riskTags={{riskTags}}
            - signalLevel={{signalLevel}}
            - freshnessLevel={{freshnessLevel}}
            - evidenceContext={{evidenceContext}}
            - fallbackHeadline={{fallbackHeadline}}
            - fallbackSummary={{fallbackSummary}}
            - fallbackNextActions={{fallbackNextActions}}

            输出要求：
            1. 只返回 JSON，不要 markdown，不要额外解释。
            2. JSON schema: {"headline":string,"summary":string,"nextActions":string[]}。
            3. 你只能重组、润色、压缩已有事实，不得新增学生未提供的经历、项目、量化结果、公司或能力判断。
            4. headline 使用简体中文，长度适中，适合作为资料页的第一眼结论；优先点明当前优势和最需要补的短板。
            5. summary 使用 2 到 4 句简体中文，语气直接、克制、可执行；要体现 signalLevel 与 freshnessLevel 对画像可信度的影响，但不要解释模型原理。
            6. nextActions 输出 2 到 3 条简体中文动作建议，每条都要像真实行动项，避免空泛鸡汤。
            7. 当输入信号偏弱或信息较旧时，应明确提醒“先补信号”或“建议重新刷新画像”，但不能夸大风险。
            8. fallbackHeadline / fallbackSummary / fallbackNextActions 是兜底版本，你可以润色它们，但不能偏离当前事实边界。
            """;
    private static final String PORTRAIT_SUMMARY_TEMPLATE_VARIABLES_JSON = """
            {
              "targetPosition": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "Java 后端开发工程师",
                "description": "学生当前目标岗位"
              },
              "strengthTags": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "技能成长清晰 | 模拟面试积极",
                "description": "规则层提取出的优势标签"
              },
              "riskTags": {
                "required": false,
                "defaultValue": "",
                "sampleValue": "面试表达结构待补强 | 简历成果表达待强化",
                "description": "规则层提取出的风险标签"
              },
              "signalLevel": {
                "required": true,
                "sampleValue": "NORMAL",
                "description": "画像信号强度"
              },
              "freshnessLevel": {
                "required": true,
                "sampleValue": "FRESH",
                "description": "画像新鲜度"
              },
              "evidenceContext": {
                "required": true,
                "sampleValue": "已掌握技能：3 项\\n学习中技能：2 项\\n近 7 天面试消息：6 条",
                "description": "规则层证据摘要"
              },
              "fallbackHeadline": {
                "required": true,
                "sampleValue": "你在后端方向已经形成初步优势，当前最需要补的是面试表达结构。",
                "description": "本地模板兜底标题"
              },
              "fallbackSummary": {
                "required": true,
                "sampleValue": "你已经具备较清晰的技能成长与目标岗位方向，但最近的面试与简历信号显示，项目表达和回答结构仍有补强空间。",
                "description": "本地模板兜底概述"
              },
              "fallbackNextActions": {
                "required": true,
                "defaultValue": "",
                "sampleValue": "围绕一个核心项目练习 2 分钟的背景-动作-结果表达 | 给最近一份简历补齐量化结果",
                "description": "本地模板兜底行动建议"
              }
            }
            """;

    private final AiGatewayProperties properties;
    private final AiModelRouteRepository aiModelRouteRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final SceneRoutePolicyRepository sceneRoutePolicyRepository;
    private final AiProviderConfigRepository aiProviderConfigRepository;
    private final AiProviderModelRepository aiProviderModelRepository;
    private final AiConfigCryptoService cryptoService;
    private final AiRouteCacheService routeCacheService;
    private final AiPricingPolicyResolver pricingPolicyResolver;

    public AiGatewaySystemBootstrapService(
            AiGatewayProperties properties,
            AiModelRouteRepository aiModelRouteRepository,
            PromptTemplateRepository promptTemplateRepository,
            SceneRoutePolicyRepository sceneRoutePolicyRepository,
            AiProviderConfigRepository aiProviderConfigRepository,
            AiProviderModelRepository aiProviderModelRepository,
            AiConfigCryptoService cryptoService,
            AiRouteCacheService routeCacheService,
            AiPricingPolicyResolver pricingPolicyResolver
    ) {
        this.properties = properties;
        this.aiModelRouteRepository = aiModelRouteRepository;
        this.promptTemplateRepository = promptTemplateRepository;
        this.sceneRoutePolicyRepository = sceneRoutePolicyRepository;
        this.aiProviderConfigRepository = aiProviderConfigRepository;
        this.aiProviderModelRepository = aiProviderModelRepository;
        this.cryptoService = cryptoService;
        this.routeCacheService = routeCacheService;
        this.pricingPolicyResolver = pricingPolicyResolver;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.getMode() != AiGatewayMode.ROUTED) {
            return;
        }
        BootstrapContext context = resolveBootstrapContext();
        if (context == null) {
            return;
        }
        ensureResumeRouteBootstrap(context);
        ensureInterviewRouteBootstrap(context);
        ensureCommunityAssistRouteBootstrap(context);
        ensureMentorPrepRouteBootstrap(context);
        ensureStudentPortraitSummaryRouteBootstrap(context);
        routeCacheService.evictAllAfterCommit();
    }

    private BootstrapContext resolveBootstrapContext() {
        AiGatewayProperties.OpenAiCompatible providerProperties = properties.getOpenaiCompatible();
        String baseUrl = safe(providerProperties.getBaseUrl()).trim();
        String apiKey = safe(providerProperties.getApiKey()).trim();
        String defaultModel = safe(providerProperties.getDefaultModel()).trim();
        if (baseUrl.isBlank() || apiKey.isBlank() || defaultModel.isBlank()) {
            log.warn(
                    "skip ai gateway bootstrap because baseUrl/apiKey/model missing, baseUrlPresent={}, apiKeyPresent={}, modelPresent={}",
                    !baseUrl.isBlank(),
                    !apiKey.isBlank(),
                    !defaultModel.isBlank()
            );
            return null;
        }
        if (!looksLikeGeminiRoute(defaultModel, baseUrl)) {
            log.warn(
                    "skip ai gateway bootstrap because current provider config is not gemini-compatible, model={}, baseUrl={}",
                    defaultModel,
                    maskBaseUrl(baseUrl)
            );
            return null;
        }
        return new BootstrapContext(providerProperties, baseUrl, apiKey, defaultModel);
    }

    private void ensureResumeRouteBootstrap(BootstrapContext context) {
        long providerId = upsertGeminiProvider(
                RESUME_PROVIDER_CODE,
                RESUME_PROVIDER_DISPLAY_NAME,
                context,
                MIN_RESUME_PROVIDER_TIMEOUT_MS
        );
        aiProviderModelRepository.replaceProviderModels(
                providerId,
                List.of(buildProviderModel(
                        context.defaultModel(),
                        "Gemini 2.5 Flash",
                        pricingPolicyResolver.resolveBootstrapTextPricing(context.defaultModel()),
                        "[\"RESUME\"]",
                        "系统默认简历文本模型"
                ))
        );
        ensureTextPromptTemplate(
                RESUME_TASK_TYPE,
                RESUME_TEMPLATE_NAME,
                RESUME_TEMPLATE_VERSION,
                RESUME_TEMPLATE_DESCRIPTION,
                RESUME_TEMPLATE_CONTENT,
                RESUME_TEMPLATE_VARIABLES_JSON
        );
        upsertRoute(
                RESUME_ROUTE_CODE,
                RESUME_TASK_TYPE,
                RESUME_SCENE_CODE,
                providerId,
                context.defaultModel(),
                RESUME_ROUTE_PRIORITY,
                AiExecutionMode.SYNC_BLOCKING.name(),
                BigDecimal.valueOf(context.providerProperties().getTemperature()),
                null,
                RESUME_TEMPLATE_NAME,
                pricingPolicyResolver.mergePricingOverride(
                        RESUME_ROUTE_EXTRA_CONFIG_JSON,
                        pricingPolicyResolver.resolveBootstrapTextPricing(context.defaultModel())
                )
        );
        deactivateLegacyRoutes(RESUME_ROUTE_CODE, LEGACY_RESUME_PRIMARY_ROUTE_CODE, LEGACY_RESUME_FALLBACK_ROUTE_CODE);
        deactivateLegacyPromptTemplates(RESUME_ROUTE_CODE, RESUME_TASK_TYPE, LEGACY_RESUME_TEMPLATE_NAME);

        log.info(
                "resume ai route bootstrapped providerCode={}, routeCode={}, templateName={}, model={}",
                RESUME_PROVIDER_CODE,
                RESUME_ROUTE_CODE,
                RESUME_TEMPLATE_NAME,
                context.defaultModel()
        );
    }

    private void ensureInterviewRouteBootstrap(BootstrapContext context) {
        long providerId = upsertGeminiProvider(
                INTERVIEW_PROVIDER_CODE,
                INTERVIEW_PROVIDER_DISPLAY_NAME,
                context,
                MIN_INTERVIEW_PROVIDER_TIMEOUT_MS
        );
        String speechToTextModel = resolveSpeechToTextModel(context);
        String textToSpeechModel = resolveTextToSpeechModel(context);
        aiProviderModelRepository.replaceProviderModels(
                providerId,
                buildInterviewProviderModels(context.defaultModel(), speechToTextModel, textToSpeechModel)
        );
        ensureTextPromptTemplate(
                INTERVIEW_TEXT_TASK_TYPE,
                INTERVIEW_OPENING_TEMPLATE_NAME,
                INTERVIEW_TEMPLATE_VERSION,
                INTERVIEW_OPENING_TEMPLATE_DESCRIPTION,
                INTERVIEW_OPENING_TEMPLATE_CONTENT,
                INTERVIEW_OPENING_TEMPLATE_VARIABLES_JSON
        );
        ensureTextPromptTemplate(
                INTERVIEW_TEXT_TASK_TYPE,
                INTERVIEW_REPLY_TEMPLATE_NAME,
                INTERVIEW_TEMPLATE_VERSION,
                INTERVIEW_REPLY_TEMPLATE_DESCRIPTION,
                INTERVIEW_REPLY_TEMPLATE_CONTENT,
                INTERVIEW_REPLY_TEMPLATE_VARIABLES_JSON
        );
        ensureTextPromptTemplate(
                INTERVIEW_TEXT_TASK_TYPE,
                INTERVIEW_ANSWER_HELPER_TEMPLATE_NAME,
                INTERVIEW_TEMPLATE_VERSION,
                INTERVIEW_ANSWER_HELPER_TEMPLATE_DESCRIPTION,
                INTERVIEW_ANSWER_HELPER_TEMPLATE_CONTENT,
                INTERVIEW_ANSWER_HELPER_TEMPLATE_VARIABLES_JSON
        );
        ensureTextPromptTemplate(
                INTERVIEW_SUMMARY_TASK_TYPE,
                INTERVIEW_SUMMARY_TEMPLATE_NAME,
                INTERVIEW_TEMPLATE_VERSION,
                INTERVIEW_SUMMARY_TEMPLATE_DESCRIPTION,
                INTERVIEW_SUMMARY_TEMPLATE_CONTENT,
                INTERVIEW_SUMMARY_TEMPLATE_VARIABLES_JSON
        );
        BigDecimal temperature = BigDecimal.valueOf(context.providerProperties().getTemperature());
        upsertRoute(
                INTERVIEW_OPENING_ROUTE_CODE,
                INTERVIEW_TEXT_TASK_TYPE,
                INTERVIEW_OPENING_SCENE_CODE,
                providerId,
                context.defaultModel(),
                INTERVIEW_ROUTE_PRIORITY,
                AiExecutionMode.SYNC_BLOCKING.name(),
                temperature,
                null,
                INTERVIEW_OPENING_TEMPLATE_NAME,
                pricingPolicyResolver.mergePricingOverride(
                        INTERVIEW_ROUTE_EXTRA_CONFIG_JSON,
                        pricingPolicyResolver.resolveBootstrapTextPricing(context.defaultModel())
                )
        );
        upsertRoute(
                INTERVIEW_REPLY_ROUTE_CODE,
                INTERVIEW_TEXT_TASK_TYPE,
                INTERVIEW_REPLY_SCENE_CODE,
                providerId,
                context.defaultModel(),
                INTERVIEW_ROUTE_PRIORITY,
                AiExecutionMode.STREAM_SSE.name(),
                temperature,
                null,
                INTERVIEW_REPLY_TEMPLATE_NAME,
                pricingPolicyResolver.mergePricingOverride(
                        INTERVIEW_ROUTE_EXTRA_CONFIG_JSON,
                        pricingPolicyResolver.resolveBootstrapTextPricing(context.defaultModel())
                )
        );
        upsertRoute(
                INTERVIEW_ANSWER_HELPER_ROUTE_CODE,
                INTERVIEW_TEXT_TASK_TYPE,
                INTERVIEW_ANSWER_HELPER_SCENE_CODE,
                providerId,
                context.defaultModel(),
                INTERVIEW_ROUTE_PRIORITY,
                AiExecutionMode.SYNC_BLOCKING.name(),
                temperature,
                null,
                INTERVIEW_ANSWER_HELPER_TEMPLATE_NAME,
                pricingPolicyResolver.mergePricingOverride(
                        INTERVIEW_ROUTE_EXTRA_CONFIG_JSON,
                        pricingPolicyResolver.resolveBootstrapTextPricing(context.defaultModel())
                )
        );
        upsertRoute(
                INTERVIEW_SUMMARY_ROUTE_CODE,
                INTERVIEW_SUMMARY_TASK_TYPE,
                INTERVIEW_SUMMARY_SCENE_CODE,
                providerId,
                context.defaultModel(),
                INTERVIEW_ROUTE_PRIORITY,
                AiExecutionMode.SYNC_BLOCKING.name(),
                temperature,
                null,
                INTERVIEW_SUMMARY_TEMPLATE_NAME,
                pricingPolicyResolver.mergePricingOverride(
                        INTERVIEW_ROUTE_EXTRA_CONFIG_JSON,
                        pricingPolicyResolver.resolveBootstrapTextPricing(context.defaultModel())
                )
        );
        upsertRoute(
                INTERVIEW_STT_ROUTE_CODE,
                STT_TASK_TYPE,
                INTERVIEW_STT_SCENE_CODE,
                providerId,
                speechToTextModel,
                INTERVIEW_ROUTE_PRIORITY,
                AiExecutionMode.SYNC_BLOCKING.name(),
                temperature,
                null,
                null,
                pricingPolicyResolver.mergePricingOverride(
                        INTERVIEW_STT_ROUTE_EXTRA_CONFIG_JSON,
                        pricingPolicyResolver.resolveBootstrapSpeechToTextPricing(speechToTextModel)
                )
        );
        upsertRoute(
                INTERVIEW_TTS_ROUTE_CODE,
                TTS_TASK_TYPE,
                INTERVIEW_TTS_SCENE_CODE,
                providerId,
                textToSpeechModel,
                INTERVIEW_ROUTE_PRIORITY,
                AiExecutionMode.SYNC_BLOCKING.name(),
                temperature,
                null,
                null,
                pricingPolicyResolver.mergePricingOverride(
                        INTERVIEW_TTS_ROUTE_EXTRA_CONFIG_JSON,
                        pricingPolicyResolver.resolveBootstrapTextToSpeechPricing(textToSpeechModel)
                )
        );
        deactivateLegacyRoutes(
                "SYSTEM_INTERVIEW_BOOTSTRAP",
                LEGACY_INTERVIEW_TEXT_FALLBACK_ROUTE_CODE,
                LEGACY_INTERVIEW_OPENING_ROUTE_CODE,
                LEGACY_INTERVIEW_REPLY_ROUTE_CODE,
                LEGACY_INTERVIEW_SUMMARY_FALLBACK_ROUTE_CODE,
                LEGACY_INTERVIEW_SUMMARY_ROUTE_CODE,
                LEGACY_INTERVIEW_STT_FALLBACK_ROUTE_CODE,
                LEGACY_INTERVIEW_STT_GEMINI_ROUTE_CODE,
                LEGACY_INTERVIEW_STT_OPENAI_ROUTE_CODE,
                LEGACY_INTERVIEW_TTS_FALLBACK_ROUTE_CODE,
                LEGACY_INTERVIEW_TTS_ROUTE_CODE
        );
        deactivateLegacyPromptTemplates(
                "SYSTEM_INTERVIEW_BOOTSTRAP",
                INTERVIEW_TEXT_TASK_TYPE,
                LEGACY_INTERVIEW_OPENING_TEMPLATE_NAME,
                LEGACY_INTERVIEW_REPLY_TEMPLATE_NAME
        );
        deactivateLegacyPromptTemplates(
                "SYSTEM_INTERVIEW_BOOTSTRAP",
                INTERVIEW_SUMMARY_TASK_TYPE,
                LEGACY_INTERVIEW_SUMMARY_TEMPLATE_NAME
        );

        log.info(
                "interview ai routes bootstrapped providerCode={}, openingRoute={}, replyRoute={}, answerHelperRoute={}, summaryRoute={}, sttRoute={}, ttsRoute={}, model={}, sttModel={}, ttsModel={}",
                INTERVIEW_PROVIDER_CODE,
                INTERVIEW_OPENING_ROUTE_CODE,
                INTERVIEW_REPLY_ROUTE_CODE,
                INTERVIEW_ANSWER_HELPER_ROUTE_CODE,
                INTERVIEW_SUMMARY_ROUTE_CODE,
                INTERVIEW_STT_ROUTE_CODE,
                INTERVIEW_TTS_ROUTE_CODE,
                context.defaultModel(),
                speechToTextModel,
                textToSpeechModel
        );
    }

    private void ensureMentorPrepRouteBootstrap(BootstrapContext context) {
        long providerId = upsertGeminiProvider(
                MENTOR_PREP_PROVIDER_CODE,
                COMMUNITY_MENTOR_PROVIDER_DISPLAY_NAME,
                context,
                MIN_MENTOR_PREP_PROVIDER_TIMEOUT_MS
        );
        aiProviderModelRepository.replaceProviderModels(
                providerId,
                List.of(buildProviderModel(
                        context.defaultModel(),
                        "Gemini 2.5 Flash",
                        pricingPolicyResolver.resolveBootstrapTextPricing(context.defaultModel()),
                        "[\"" + MENTOR_PREP_TASK_TYPE + "\",\"" + ICEBREAK_TASK_TYPE + "\",\"" + PORTRAIT_SUMMARY_TASK_TYPE + "\"]",
                        "系统默认运营文本模型"
                ))
        );
        ensureTextPromptTemplate(
                MENTOR_PREP_TASK_TYPE,
                MENTOR_PREP_TEMPLATE_NAME,
                MENTOR_PREP_TEMPLATE_VERSION,
                MENTOR_PREP_TEMPLATE_DESCRIPTION,
                MENTOR_PREP_TEMPLATE_CONTENT,
                MENTOR_PREP_TEMPLATE_VARIABLES_JSON
        );
        upsertRoute(
                MENTOR_PREP_ROUTE_CODE,
                MENTOR_PREP_TASK_TYPE,
                MENTOR_PREP_SCENE_CODE,
                providerId,
                context.defaultModel(),
                MENTOR_PREP_ROUTE_PRIORITY,
                AiExecutionMode.SYNC_BLOCKING.name(),
                BigDecimal.valueOf(context.providerProperties().getTemperature()),
                null,
                MENTOR_PREP_TEMPLATE_NAME,
                pricingPolicyResolver.mergePricingOverride(
                        MENTOR_PREP_ROUTE_EXTRA_CONFIG_JSON,
                        pricingPolicyResolver.resolveBootstrapTextPricing(context.defaultModel())
                )
        );

        log.info(
                "mentor prep ai route bootstrapped providerCode={}, routeCode={}, templateName={}, model={}",
                MENTOR_PREP_PROVIDER_CODE,
                MENTOR_PREP_ROUTE_CODE,
                MENTOR_PREP_TEMPLATE_NAME,
                context.defaultModel()
        );
    }

    private void ensureCommunityAssistRouteBootstrap(BootstrapContext context) {
        long providerId = upsertGeminiProvider(
                MENTOR_PREP_PROVIDER_CODE,
                COMMUNITY_MENTOR_PROVIDER_DISPLAY_NAME,
                context,
                MIN_MENTOR_PREP_PROVIDER_TIMEOUT_MS
        );
        aiProviderModelRepository.replaceProviderModels(
                providerId,
                List.of(buildProviderModel(
                        context.defaultModel(),
                        "Gemini 2.5 Flash",
                        pricingPolicyResolver.resolveBootstrapTextPricing(context.defaultModel()),
                        "[\"" + MENTOR_PREP_TASK_TYPE + "\",\"" + ICEBREAK_TASK_TYPE + "\",\"" + PORTRAIT_SUMMARY_TASK_TYPE + "\"]",
                        "系统默认运营文本模型"
                ))
        );
        BigDecimal temperature = BigDecimal.valueOf(context.providerProperties().getTemperature());
        ensureTextPromptTemplate(
                MENTOR_PREP_TASK_TYPE,
                COMMUNITY_PRE_ANSWER_TEMPLATE_NAME,
                COMMUNITY_PRE_ANSWER_TEMPLATE_VERSION,
                COMMUNITY_PRE_ANSWER_TEMPLATE_DESCRIPTION,
                COMMUNITY_PRE_ANSWER_TEMPLATE_CONTENT,
                COMMUNITY_PRE_ANSWER_TEMPLATE_VARIABLES_JSON
        );
        upsertRoute(
                COMMUNITY_PRE_ANSWER_ROUTE_CODE,
                MENTOR_PREP_TASK_TYPE,
                COMMUNITY_PRE_ANSWER_SCENE_CODE,
                providerId,
                context.defaultModel(),
                COMMUNITY_PRE_ANSWER_ROUTE_PRIORITY,
                AiExecutionMode.SYNC_BLOCKING.name(),
                temperature,
                null,
                COMMUNITY_PRE_ANSWER_TEMPLATE_NAME,
                pricingPolicyResolver.mergePricingOverride(
                        COMMUNITY_ASSIST_ROUTE_EXTRA_CONFIG_JSON,
                        pricingPolicyResolver.resolveBootstrapTextPricing(context.defaultModel())
                )
        );

        ensureTextPromptTemplate(
                ICEBREAK_TASK_TYPE,
                ICEBREAK_TEMPLATE_NAME,
                ICEBREAK_TEMPLATE_VERSION,
                ICEBREAK_TEMPLATE_DESCRIPTION,
                ICEBREAK_TEMPLATE_CONTENT,
                ICEBREAK_TEMPLATE_VARIABLES_JSON
        );
        upsertRoute(
                ICEBREAK_ROUTE_CODE,
                ICEBREAK_TASK_TYPE,
                ICEBREAK_SCENE_CODE,
                providerId,
                context.defaultModel(),
                ICEBREAK_ROUTE_PRIORITY,
                AiExecutionMode.SYNC_BLOCKING.name(),
                temperature,
                null,
                ICEBREAK_TEMPLATE_NAME,
                pricingPolicyResolver.mergePricingOverride(
                        COMMUNITY_ASSIST_ROUTE_EXTRA_CONFIG_JSON,
                        pricingPolicyResolver.resolveBootstrapTextPricing(context.defaultModel())
                )
        );

        log.info(
                "community assist ai routes bootstrapped providerCode={}, preAnswerRoute={}, icebreakRoute={}, model={}",
                MENTOR_PREP_PROVIDER_CODE,
                COMMUNITY_PRE_ANSWER_ROUTE_CODE,
                ICEBREAK_ROUTE_CODE,
                context.defaultModel()
        );
    }

    private void ensureStudentPortraitSummaryRouteBootstrap(BootstrapContext context) {
        long providerId = upsertGeminiProvider(
                MENTOR_PREP_PROVIDER_CODE,
                COMMUNITY_MENTOR_PROVIDER_DISPLAY_NAME,
                context,
                MIN_MENTOR_PREP_PROVIDER_TIMEOUT_MS
        );
        aiProviderModelRepository.replaceProviderModels(
                providerId,
                List.of(buildProviderModel(
                        context.defaultModel(),
                        "Gemini 2.5 Flash",
                        pricingPolicyResolver.resolveBootstrapTextPricing(context.defaultModel()),
                        "[\"" + MENTOR_PREP_TASK_TYPE + "\",\"" + ICEBREAK_TASK_TYPE + "\",\"" + PORTRAIT_SUMMARY_TASK_TYPE + "\"]",
                        "系统默认运营文本模型"
                ))
        );
        ensureTextPromptTemplate(
                PORTRAIT_SUMMARY_TASK_TYPE,
                PORTRAIT_SUMMARY_TEMPLATE_NAME,
                PORTRAIT_SUMMARY_TEMPLATE_VERSION,
                PORTRAIT_SUMMARY_TEMPLATE_DESCRIPTION,
                PORTRAIT_SUMMARY_TEMPLATE_CONTENT,
                PORTRAIT_SUMMARY_TEMPLATE_VARIABLES_JSON
        );
        upsertRoute(
                PORTRAIT_SUMMARY_ROUTE_CODE,
                PORTRAIT_SUMMARY_TASK_TYPE,
                PORTRAIT_SUMMARY_SCENE_CODE,
                providerId,
                context.defaultModel(),
                PORTRAIT_SUMMARY_ROUTE_PRIORITY,
                AiExecutionMode.SYNC_BLOCKING.name(),
                BigDecimal.valueOf(context.providerProperties().getTemperature()),
                null,
                PORTRAIT_SUMMARY_TEMPLATE_NAME,
                pricingPolicyResolver.mergePricingOverride(
                        MENTOR_PREP_ROUTE_EXTRA_CONFIG_JSON,
                        pricingPolicyResolver.resolveBootstrapTextPricing(context.defaultModel())
                )
        );

        log.info(
                "student portrait summary ai route bootstrapped providerCode={}, routeCode={}, templateName={}, model={}",
                MENTOR_PREP_PROVIDER_CODE,
                PORTRAIT_SUMMARY_ROUTE_CODE,
                PORTRAIT_SUMMARY_TEMPLATE_NAME,
                context.defaultModel()
        );
    }

    private long upsertGeminiProvider(
            String providerCode,
            String displayName,
            BootstrapContext context,
            int minTimeoutMs
    ) {
        String apiKeyCiphertext = cryptoService.encrypt(context.apiKey());
        String apiKeyMasked = cryptoService.mask(context.apiKey());
        String extraConfigJson = buildGeminiProviderExtraConfigJson(context.baseUrl());
        int timeoutMs = Math.max(context.providerProperties().getTimeoutMs(), minTimeoutMs);
        int maxRetries = Math.max(context.providerProperties().getMaxRetries(), 0);
        AiPricingPolicyResolver.PricingOverride defaultPricing = pricingPolicyResolver.resolveBootstrapProviderDefaultPricing(context.defaultModel());
        BigDecimal costPer1kInput = defaultPricing == null
                ? (context.providerProperties().getCostPer1kInput() == null ? BigDecimal.ZERO : context.providerProperties().getCostPer1kInput())
                : defaultPricing.inputPer1k();
        BigDecimal costPer1kOutput = defaultPricing == null
                ? (context.providerProperties().getCostPer1kOutput() == null ? BigDecimal.ZERO : context.providerProperties().getCostPer1kOutput())
                : defaultPricing.outputPer1k();

        return aiProviderConfigRepository.findProviderByCode(providerCode)
                .map(existing -> {
                    aiProviderConfigRepository.updateProvider(
                            existing.id(),
                            providerCode,
                            AiProviderType.GEMINI_NATIVE.name(),
                            displayName,
                            context.baseUrl(),
                            apiKeyCiphertext,
                            apiKeyMasked,
                            true,
                            timeoutMs,
                            maxRetries,
                            costPer1kInput,
                            costPer1kOutput,
                            extraConfigJson
                    );
                    return existing.id();
                })
                .orElseGet(() -> aiProviderConfigRepository.insertProvider(
                        providerCode,
                        AiProviderType.GEMINI_NATIVE.name(),
                        displayName,
                        context.baseUrl(),
                        apiKeyCiphertext,
                        apiKeyMasked,
                        true,
                        timeoutMs,
                        maxRetries,
                        costPer1kInput,
                        costPer1kOutput,
                        extraConfigJson
                ));
    }

    private List<AiProviderModelRepository.ProviderModelMutation> buildInterviewProviderModels(
            String defaultModel,
            String speechToTextModel,
            String textToSpeechModel
    ) {
        boolean sharedTextAndStt = sameModel(defaultModel, speechToTextModel);
        boolean sharedTextAndTts = sameModel(defaultModel, textToSpeechModel);
        boolean sharedSttAndTts = sameModel(speechToTextModel, textToSpeechModel);

        List<AiProviderModelRepository.ProviderModelMutation> models = new ArrayList<>();
        models.add(buildProviderModel(
                defaultModel,
                "Gemini 2.5 Flash",
                pricingPolicyResolver.resolveBootstrapTextPricing(defaultModel),
                sharedTextAndStt
                        ? (sharedTextAndTts
                        ? "[\"" + INTERVIEW_TEXT_TASK_TYPE + "\",\"" + INTERVIEW_SUMMARY_TASK_TYPE + "\",\"" + STT_TASK_TYPE + "\",\"" + TTS_TASK_TYPE + "\"]"
                        : "[\"" + INTERVIEW_TEXT_TASK_TYPE + "\",\"" + INTERVIEW_SUMMARY_TASK_TYPE + "\",\"" + STT_TASK_TYPE + "\"]")
                        : (sharedTextAndTts
                        ? "[\"" + INTERVIEW_TEXT_TASK_TYPE + "\",\"" + INTERVIEW_SUMMARY_TASK_TYPE + "\",\"" + TTS_TASK_TYPE + "\"]"
                        : "[\"" + INTERVIEW_TEXT_TASK_TYPE + "\",\"" + INTERVIEW_SUMMARY_TASK_TYPE + "\"]"),
                sharedTextAndStt || sharedTextAndTts
                        ? "系统默认文本模型；语音相关成本以场景路由覆盖为准"
                        : "系统默认文本模型"
        ));
        if (!sharedTextAndStt) {
            models.add(buildProviderModel(
                    speechToTextModel,
                    speechToTextModel,
                    pricingPolicyResolver.resolveBootstrapSpeechToTextPricing(speechToTextModel),
                    sharedSttAndTts
                            ? "[\"" + STT_TASK_TYPE + "\",\"" + TTS_TASK_TYPE + "\"]"
                            : "[\"" + STT_TASK_TYPE + "\"]",
                    sharedSttAndTts
                            ? "系统默认语音转写模型；语音播报复用同一模型"
                            : "系统默认语音转写模型"
            ));
        }
        if (!sharedTextAndTts && !sharedSttAndTts) {
            models.add(buildProviderModel(
                    textToSpeechModel,
                    "Gemini 2.5 Flash TTS",
                    pricingPolicyResolver.resolveBootstrapTextToSpeechPricing(textToSpeechModel),
                    "[\"" + TTS_TASK_TYPE + "\"]",
                    "系统默认语音播报模型"
            ));
        }
        return models;
    }

    private AiProviderModelRepository.ProviderModelMutation buildProviderModel(
            String modelCode,
            String displayName,
            AiPricingPolicyResolver.PricingOverride pricingOverride,
            String supportedTaskTypesJson,
            String notes
    ) {
        BigDecimal inputCostPer1k = pricingOverride == null ? BigDecimal.ZERO : pricingOverride.inputPer1k();
        BigDecimal outputCostPer1k = pricingOverride == null ? BigDecimal.ZERO : pricingOverride.outputPer1k();
        return new AiProviderModelRepository.ProviderModelMutation(
                modelCode,
                displayName,
                true,
                inputCostPer1k,
                outputCostPer1k,
                null,
                null,
                supportedTaskTypesJson,
                notes
        );
    }

    private boolean sameModel(String left, String right) {
        return safe(left).trim().equalsIgnoreCase(safe(right).trim());
    }

    private void ensureTextPromptTemplate(
            String taskType,
            String templateName,
            int versionNo,
            String description,
            String content,
            String variablesJson
    ) {
        promptTemplateRepository.findAllPromptTemplates().stream()
                .filter(row -> taskType.equals(row.taskType()) && templateName.equals(row.templateName()))
                .max(Comparator.comparingInt(PromptTemplateRepository.PromptTemplateRow::versionNo))
                .ifPresentOrElse(
                        existing -> promptTemplateRepository.updatePromptTemplate(
                                existing.id(),
                                taskType,
                                templateName,
                                Math.max(existing.versionNo(), versionNo),
                                "ACTIVE",
                                AiPromptTemplateFormat.TEXT.name(),
                                content,
                                description,
                                variablesJson,
                                null
                        ),
                        () -> promptTemplateRepository.insertPromptTemplate(
                                taskType,
                                templateName,
                                versionNo,
                                "ACTIVE",
                                AiPromptTemplateFormat.TEXT.name(),
                                content,
                                description,
                                variablesJson,
                                null
                        )
                );
    }

    private void upsertRoute(
            String routeCode,
            String taskType,
            String sceneCode,
            long providerId,
            String model,
            int priorityNo,
            String executionMode,
            BigDecimal temperature,
            String systemPrompt,
            String promptTemplateName,
            String extraConfigJson
    ) {
        Long sceneRoutePolicyId = ensureDefaultSceneRoutePolicy(taskType, sceneCode);
        aiModelRouteRepository.findRouteByCode(routeCode)
                .ifPresentOrElse(
                        existing -> aiModelRouteRepository.updateRoute(
                                existing.id(),
                                routeCode,
                                taskType,
                                sceneCode,
                                sceneRoutePolicyId,
                                providerId,
                                model,
                                priorityNo,
                                existing.candidateWeight() <= 0 ? 100 : existing.candidateWeight(),
                                executionMode,
                                true,
                                temperature,
                                systemPrompt,
                                promptTemplateName,
                                extraConfigJson
                        ),
                        () -> aiModelRouteRepository.insertRoute(
                                routeCode,
                                taskType,
                                sceneCode,
                                sceneRoutePolicyId,
                                providerId,
                                model,
                                priorityNo,
                                100,
                                executionMode,
                                true,
                                temperature,
                                systemPrompt,
                                promptTemplateName,
                                extraConfigJson
                        )
                );
    }

    private Long ensureDefaultSceneRoutePolicy(String taskType, String sceneCode) {
        String normalizedTaskType = safe(taskType).trim().toUpperCase(Locale.ROOT);
        String normalizedSceneCode = safe(sceneCode).trim().toUpperCase(Locale.ROOT);
        if (normalizedTaskType.isBlank() || normalizedSceneCode.isBlank()) {
            return null;
        }
        return sceneRoutePolicyRepository.findRoutePolicyBySceneAndTier(normalizedTaskType, normalizedSceneCode, "ALL")
                .map(SceneRoutePolicyRepository.SceneRoutePolicyRow::id)
                .orElseGet(() -> sceneRoutePolicyRepository.insertRoutePolicy(
                        buildDefaultRoutePolicyCode(normalizedTaskType, normalizedSceneCode, "ALL"),
                        normalizedTaskType,
                        normalizedSceneCode,
                        "ALL",
                        "SINGLE",
                        true,
                        "系统自举默认策略",
                        null
                ));
    }

    private String buildDefaultRoutePolicyCode(String taskType, String sceneCode, String userTier) {
        return (taskType + "_" + sceneCode + "_" + userTier)
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_\\-]", "_");
    }

    private void deactivateLegacyRoutes(String replacementCode, String... routeCodes) {
        if (routeCodes == null) {
            return;
        }
        for (String routeCode : routeCodes) {
            aiModelRouteRepository.findRouteByCode(routeCode)
                    .filter(AiModelRouteRepository.ModelRouteRow::enabled)
                    .ifPresent(existing -> {
                        aiModelRouteRepository.updateRoute(
                                existing.id(),
                                existing.routeCode(),
                                existing.taskType(),
                                existing.sceneCode(),
                                existing.sceneRoutePolicyId(),
                                existing.providerConfigId(),
                                existing.modelName(),
                                existing.priorityNo(),
                                existing.candidateWeight(),
                                existing.executionMode(),
                                false,
                                existing.temperature(),
                                existing.systemPrompt(),
                                existing.promptTemplateName(),
                                existing.extraConfigJson()
                        );
                        log.info("legacy ai route disabled routeCode={}, replacedBy={}", routeCode, replacementCode);
                    });
        }
    }

    private void deactivateLegacyPromptTemplates(String replacementCode, String taskType, String... templateNames) {
        if (templateNames == null) {
            return;
        }
        for (String templateName : templateNames) {
            promptTemplateRepository.findAllPromptTemplates().stream()
                    .filter(row -> taskType.equals(row.taskType()))
                    .filter(row -> templateName.equals(row.templateName()))
                    .filter(row -> "ACTIVE".equalsIgnoreCase(safe(row.status())))
                    .forEach(existing -> {
                        promptTemplateRepository.updatePromptTemplate(
                                existing.id(),
                                existing.taskType(),
                                existing.templateName(),
                                existing.versionNo(),
                                "INACTIVE",
                                safe(existing.templateFormat()).isBlank() ? AiPromptTemplateFormat.TEXT.name() : existing.templateFormat(),
                                existing.content(),
                                existing.description(),
                                existing.variablesJson(),
                                existing.bundleJson()
                        );
                        log.info(
                                "legacy ai prompt template deactivated taskType={}, templateName={}, version={}, replacedBy={}",
                                existing.taskType(),
                                existing.templateName(),
                                existing.versionNo(),
                                replacementCode
                        );
                    });
        }
    }

    private String resolveSpeechToTextModel(BootstrapContext context) {
        String configured = safe(context.providerProperties().getSpeechToTextModel()).trim();
        if (!configured.isBlank()) {
            return configured;
        }
        return context.defaultModel();
    }

    private String resolveTextToSpeechModel(BootstrapContext context) {
        String configured = safe(context.providerProperties().getTextToSpeechModel()).trim();
        if (!configured.isBlank()) {
            return configured;
        }
        if (looksLikeGeminiRoute(context.defaultModel(), context.baseUrl())) {
            return DEFAULT_GEMINI_TTS_MODEL;
        }
        return context.defaultModel();
    }

    private boolean looksLikeGeminiRoute(String model, String baseUrl) {
        String normalizedModel = safe(model).toLowerCase(Locale.ROOT);
        String normalizedBaseUrl = safe(baseUrl).toLowerCase(Locale.ROOT);
        return normalizedModel.contains("gemini")
                || normalizedBaseUrl.contains("generativelanguage.googleapis.com")
                || normalizedBaseUrl.contains("newapi");
    }

    private String maskBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        String normalized = baseUrl.trim();
        int visible = Math.min(24, normalized.length());
        return normalized.substring(0, visible) + (normalized.length() > visible ? "..." : "");
    }

    private String buildGeminiProviderExtraConfigJson(String baseUrl) {
        String normalizedBaseUrl = safe(baseUrl).toLowerCase(Locale.ROOT);
        String authMode = normalizedBaseUrl.contains("googleapis.com") || normalizedBaseUrl.contains("generativelanguage")
                ? "QUERY_API_KEY"
                : "BEARER_TOKEN";
        return GEMINI_PROVIDER_EXTRA_CONFIG_TEMPLATE.formatted(authMode);
    }

    private String safe(String rawValue) {
        return rawValue == null ? "" : rawValue;
    }

    private record BootstrapContext(
            AiGatewayProperties.OpenAiCompatible providerProperties,
            String baseUrl,
            String apiKey,
            String defaultModel
    ) {
    }
}
