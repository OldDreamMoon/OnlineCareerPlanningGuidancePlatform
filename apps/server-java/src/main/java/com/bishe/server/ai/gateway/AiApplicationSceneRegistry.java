package com.bishe.server.ai.gateway;

import java.util.List;

/**
 * AI 应用运营台场景注册表：定义需要被运营侧持续观察的业务场景。
 */
final class AiApplicationSceneRegistry {

    private static final List<ApplicationSceneDefinition> SCENES = List.of(
            new ApplicationSceneDefinition(
                    "resume.optimize",
                    "简历优化",
                    "学生 AI",
                    "/ai/resume",
                    "RESUME",
                    "RESUME_OPTIMIZE",
                    "学生提交文本或 PDF 简历后触发的结构化优化与复盘链路。"
            ),
            new ApplicationSceneDefinition(
                    "community.pre-answer",
                    "社区预答",
                    "社区",
                    "/community",
                    "COMMUNITY_REPLY",
                    "COMMUNITY_PRE_ANSWER",
                    "围绕帖子草稿或讨论上下文生成预答与辅助思路。"
            ),
            new ApplicationSceneDefinition(
                    "community.mentor-prep-sheet",
                    "导师准备单",
                    "导师广场",
                    "/mentors",
                    "COMMUNITY_REPLY",
                    "MENTOR_PREP_SHEET_GENERATE",
                    "学生从导师广场进入咨询前，用于生成准备单与材料提示。"
            ),
            new ApplicationSceneDefinition(
                    "student.portrait.summary",
                    "学生画像总结",
                    "成长画像",
                    "/profiles/students/me?tab=portrait",
                    "PORTRAIT_SUMMARY",
                    "STUDENT_PORTRAIT_SUMMARY",
                    "基于学生画像真相层生成概述、风险与下一步建议的低频表达层总结。"
            ),
            new ApplicationSceneDefinition(
                    "icebreak.message",
                    "破冰消息",
                    "导师广场",
                    "/mentors",
                    "ICEBREAK",
                    "ICEBREAK_MESSAGE",
                    "帮助学生生成与导师建立联系的破冰开场语。"
            ),
            new ApplicationSceneDefinition(
                    "interview.opening",
                    "面试首问",
                    "AI 面试",
                    "/ai/interview",
                    "INTERVIEW_TEXT",
                    "INTERVIEW_OPENING",
                    "AI 模拟面试开始时生成第一轮主问题与语境。"
            ),
            new ApplicationSceneDefinition(
                    "interview.reply",
                    "面试追问",
                    "AI 面试",
                    "/ai/interview/session",
                    "INTERVIEW_TEXT",
                    "INTERVIEW_REPLY",
                    "学生作答后继续追问与轻点评的主对话链路。"
            ),
            new ApplicationSceneDefinition(
                    "interview.answer-helper",
                    "回答辅助诊断",
                    "AI 面试",
                    "/ai/interview/session",
                    "INTERVIEW_TEXT",
                    "INTERVIEW_ANSWER_HELPER",
                    "对最近一轮回答做独立结构化诊断，不侵入主会话 prompt。"
            ),
            new ApplicationSceneDefinition(
                    "interview.summary",
                    "面试总结",
                    "AI 面试",
                    "/ai/interview/review",
                    "INTERVIEW_SUMMARY",
                    "INTERVIEW_SUMMARY",
                    "面试结束后生成总结、优劣势与建议的链路。"
            ),
            new ApplicationSceneDefinition(
                    "interview.voice-transcribe",
                    "语音转写",
                    "AI 面试",
                    "/ai/interview/session",
                    "STT",
                    "INTERVIEW_VOICE_TRANSCRIBE",
                    "语音模式下把用户音频转为文本，再进入既有追问链路。"
            ),
            new ApplicationSceneDefinition(
                    "interview.tts",
                    "面试播报 TTS",
                    "AI 面试",
                    "/ai/interview/session",
                    "TTS",
                    "TEXT_TO_SPEECH",
                    "将 AI 面试官消息与报告总结转成可播放音频。"
            )
    );

    private AiApplicationSceneRegistry() {
    }

    static List<ApplicationSceneDefinition> list() {
        return SCENES;
    }

    record ApplicationSceneDefinition(
            String channelCode,
            String displayName,
            String ownerDomain,
            String frontEntry,
            String taskType,
            String sceneCode,
            String summary
    ) {
    }
}
