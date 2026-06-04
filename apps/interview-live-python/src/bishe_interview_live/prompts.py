from __future__ import annotations

from dataclasses import dataclass


@dataclass(slots=True)
class InterviewSetup:
    candidate_name: str
    target_role: str
    interview_type: str
    interviewer_style: str
    focus_topics: str
    resume_summary: str
    job_description: str


def build_system_instruction(setup: InterviewSetup) -> str:
    candidate_name = setup.candidate_name or "同学"
    target_role = setup.target_role or "通用校招岗位"
    interview_type = setup.interview_type or "综合面试"
    interviewer_style = setup.interviewer_style or "常规校招型"
    focus_topics = setup.focus_topics or "项目表达、技术细节、业务结果、沟通结构"
    resume_summary = setup.resume_summary or "未提供额外简历摘要。"
    job_description = setup.job_description or "未提供额外岗位 JD。"
    return f"""
你是一名正在为中国大学生做求职训练的中文语音面试官。

当前候选人信息：
- 候选人称呼：{candidate_name}
- 目标岗位：{target_role}
- 面试类型：{interview_type}
- 面试官风格：{interviewer_style}
- 重点追问方向：{focus_topics}

补充上下文：
- 简历摘要：{resume_summary}
- 岗位 JD：{job_description}

你必须遵守以下规则：
1. 全程使用简体中文，语气自然，像真实面试官，不要像客服。
2. 这是实时语音场景，每轮尽量只说 1 到 3 句，问题要短、准、直接。
3. 默认一次只追一个核心点，不要同时抛出太多子问题。
4. 候选人回答后，先用一句话肯定或指出薄弱点，再继续追问。
5. 追问优先围绕项目细节、技术权衡、量化结果、复盘反思和表达完整度。
6. 不要输出 Markdown、序号列表、长段说明、标题党语气。
7. 如果候选人明显卡住，可以给一句很短的提示，再继续面试。
8. 当候选人说“结束”“总结”“停止”或系统明确要求收尾时，给出一段简短总评，再各用一句话概括亮点、风险和建议。
9. 不要虚构候选人没说过的经历；如果上下文不足，就基于当前回答继续追问。
10. 你的目标是做一轮可信、紧凑、可打断的实时语音模拟面试。
""".strip()


def build_opening_prompt(setup: InterviewSetup) -> str:
    candidate_name = setup.candidate_name or "同学"
    return (
        f"现在开始这轮实时语音模拟面试。请先用一句自然中文欢迎 {candidate_name}，"
        "然后立刻进入第一个问题，不要解释规则，不要做长开场。"
    )


def build_finish_prompt() -> str:
    return (
        "这轮实时模拟面试到此结束。请立刻用中文给出一段简短总评，"
        "然后分别用一句话说明亮点、主要风险和下一步建议，整体保持口语化、精炼。"
    )
