import { AnimatePresence, LayoutGroup, motion } from "framer-motion";
import {
  ArrowLeft,
  ArrowRight,
  Award,
  Bot,
  Briefcase,
  Building,
  Check,
  ChevronRight,
  FileText,
  GraduationCap,
  UploadCloud,
  X,
} from "lucide-react";
import { useEffect, useRef, useState, type ChangeEvent, type FormEvent, type KeyboardEvent } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { ApiClientError, apiRequest } from "../../lib/apiClient";
import {
  clearPendingRegistration,
  type PendingRegisterRole,
  type PendingRegistrationState,
} from "../../lib/pendingRegistration";
import { getSessionSnapshot } from "../../lib/sessionStore";

type RegisterRole = PendingRegisterRole;
type FeedbackTone = "info" | "error" | "success";

type FeedbackState = {
  tone: FeedbackTone;
  message: string;
};

type RegisterResponse = {
  userId: number;
};

type CertificationAsset = {
  assetId: number;
  bucket: string;
  objectKey: string;
  originalFilename: string;
  contentType: string | null;
  sizeBytes: number;
  lifecycleStatus: string;
  deleteReason: string | null;
  uploadedAt: string | null;
  deletedAt: string | null;
};

type CertificationSubmission = {
  submissionId: number;
  userId: number;
  role: "MENTOR" | "ENTERPRISE";
  realName: string;
  companyName: string | null;
  jobTitle: string | null;
  status: string;
  current: boolean;
  reviewNote: string | null;
  previousSubmissionId: number | null;
  submittedAt: string | null;
  reviewedAt: string | null;
  assets: CertificationAsset[];
};

type RegisterWithCertificationResponse = {
  userId: number;
  role: "MENTOR" | "ENTERPRISE";
  approvalStatus: string;
  currentSubmission: CertificationSubmission;
};

type StudentFormState = {
  realName: string;
  schoolName: string;
  grade: string;
  major: string;
  targetPosition: string;
  selfIntro: string;
};

type MentorFormState = {
  realName: string;
  company: string;
  title: string;
};

type EnterpriseFormState = {
  realName: string;
  company: string;
  title: string;
};

type RoleCardConfig = {
  id: RegisterRole;
  title: string;
  desc: string;
  icon: typeof GraduationCap;
  color: string;
  bg: string;
  border: string;
  ring: string;
};

type AgreementPanel = "terms" | "privacy" | null;

type AgreementItem = {
  title: string;
  description: string;
  emphasis?: string[];
};

type PendingRegistrationResult
  = | { type: "none" }
    | {
      type: "created";
      credentials: {
        email: string;
        password: string;
      };
      approvalStatus: string | null;
      certification: CertificationSubmission | null;
    };

export type RegisterDetailsPanelProps = {
  initialRole: RegisterRole | null;
  onBack: () => void;
  pendingRegistration: PendingRegistrationState;
  demoModeEnabled?: boolean;
  demoCertificationBypassEnabled?: boolean;
};

const DEFAULT_ROLE: RegisterRole = "STUDENT";
const MAX_SKILL_TAGS = 8;
const ENTERPRISE_DRAFT_STORAGE_PREFIX = "bishe.auth.enterprise-draft";
const fieldLabelClass = "mb-2 block text-sm font-bold text-slate-500";
const inputClass = "w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-base text-slate-700 transition-all focus:outline-none";
const readOnlyInputClass = "w-full rounded-xl border border-slate-200 bg-slate-100 px-4 py-3 text-base text-slate-500 focus:outline-none";
const formSectionClass = "rounded-[1.5rem] border border-white/90 bg-white/92 p-5 shadow-[0_14px_32px_rgba(15,23,42,0.06)]";

const rolesConfig: RoleCardConfig[] = [
  {
    id: "STUDENT",
    title: "在校学生",
    desc: "完善基础画像后，AI 简历、模拟面试与成长路径会更贴合你。",
    icon: GraduationCap,
    color: "text-indigo-500",
    bg: "bg-indigo-50",
    border: "border-indigo-500",
    ring: "ring-indigo-500/20",
  },
  {
    id: "MENTOR",
    title: "实战导师",
    desc: "提交身份信息与认证材料，审核通过后即可开启导师侧能力。",
    icon: Briefcase,
    color: "text-emerald-500",
    bg: "bg-emerald-50",
    border: "border-emerald-500",
    ring: "ring-emerald-500/20",
  },
  {
    id: "ENTERPRISE",
    title: "企业认证",
    desc: "提交联系人与企业材料，后续用于岗位发布与候选人协作。",
    icon: Building,
    color: "text-amber-500",
    bg: "bg-amber-50",
    border: "border-amber-500",
    ring: "ring-amber-500/20",
  },
];

const STUDENT_GRADE_OPTIONS = [
  "大一",
  "大二",
  "大三",
  "大四",
  "研一",
  "研二及以上",
  "已毕业",
] as const;

const userAgreementItems: AgreementItem[] = [
  {
    title: "服务定位与协议适用",
    description: "本平台面向学生、导师与企业用户提供职业成长、求职辅导、咨询协作、任务管理及相关 AI 辅助能力。你在注册、登录、认证、发布内容、使用 AI 服务、参与咨询与交易等环节的行为，均受本协议及平台另行公示规则约束。",
  },
  {
    title: "账号注册与资料真实性",
    description: "你应使用本人或依法授权的信息完成注册，保证邮箱、昵称、实名信息、学校/企业/导师身份资料、联系人信息及认证材料真实、完整、准确、持续有效。因资料不实、冒用、伪造、过期或无法核验导致的审核失败、功能受限或法律责任，由你自行承担。",
  },
  {
    title: "账号安全与使用责任",
    description: "你应妥善保管账号、密码、验证码、登录设备和二次验证信息，不得出借、出租、转让、售卖或共享账号，不得以爬虫、批量注册、脚本调用、绕过限制或其他异常方式使用平台。因你保管不善、主动授权或违规共享导致的风险和损失，由你自行承担。",
  },
  {
    title: "认证审核与功能开通",
    description: "导师与企业账号的部分功能以身份审核通过为前提。平台有权基于真实性、完整性、合规性、风险情况及业务需要，对认证申请进行初审、复核、补件、驳回、延后开通、暂停或终止，并保留依法留存审核记录与日志的权利。",
  },
  {
    title: "通知、送达与规则更新",
    description: "平台可通过页面公告、站内消息、注册邮箱、短信或其他合理方式向你发送与注册、认证、服务变更、风险提示、违规处理及协议更新相关的通知。相关通知一经发出并进入可接收状态，即视为已经送达。平台有权依据业务变化与监管要求对规则进行更新，并在合理范围内提示你关注最新版本。",
  },
  {
    title: "合法合规使用",
    description: "你承诺遵守网络安全、数据安全、个人信息保护、深度合成、生成式人工智能、内容标识、知识产权、反不正当竞争等适用法律法规，不得利用平台制作、复制、发布、传播违法违规、虚假误导、侵权、侮辱诽谤、仿冒他人身份或损害公共利益的信息，不得利用平台从事诈骗、引流、刷量、洗稿、骚扰、作弊等行为。",
    emphasis: ["不得利用平台制作、复制、发布、传播违法违规、虚假误导、侵权、侮辱诽谤、仿冒他人身份或损害公共利益的信息", "不得利用平台从事诈骗、引流、刷量、洗稿、骚扰、作弊等行为"],
  },
  {
    title: "AI 服务使用边界",
    description: "平台输出的岗位建议、简历分析、面试问答、咨询辅助、推荐结果和其他 AI 生成内容，仅作为信息整理、职业辅导和流程辅助参考，不构成法律、医疗、投资、心理诊疗、录用结果、收益保证或其他具有专业强制约束力的结论。你应结合自身情况、人工判断与正式业务流程独立决策。",
    emphasis: ["仅作为信息整理、职业辅导和流程辅助参考", "不构成法律、医疗、投资、心理诊疗、录用结果、收益保证或其他具有专业强制约束力的结论"],
  },
  {
    title: "知识产权与第三方权利",
    description: "你上传、发布、提交或用于生成的文本、图片、音频、视频、企业资料、作品样本及其他内容，应保证已取得合法权利或授权，不侵犯任何主体的著作权、商标权、专利权、名誉权、肖像权、隐私权、商业秘密及其他合法权益。因权利瑕疵引发的投诉、索赔或争议，由你自行处理并承担责任。",
  },
  {
    title: "AI 生成内容标识与传播",
    description: "对通过平台生成、编辑、导出或再次传播的 AI 相关内容，你应遵守平台关于声明、提示、标识、来源说明和使用场景的管理要求；不得恶意删除、篡改、伪造、隐匿平台依法设置的显式或隐式标识，不得将生成内容包装为人工原创、真人真实表达或官方结论误导他人。",
    emphasis: ["不得恶意删除、篡改、伪造、隐匿平台依法设置的显式或隐式标识", "不得将生成内容包装为人工原创、真人真实表达或官方结论误导他人"],
  },
  {
    title: "平台治理与违规处置",
    description: "为维护平台秩序、用户权益与监管合规，平台可对注册、登录、发布、咨询、交易、AI 调用、导出、投诉举报等环节进行风险识别、人工审核、限制频次、功能降级、内容下架、账号冻结、终止服务、留存证据及向主管部门报告等处置；涉嫌违法犯罪的，平台有权依法配合调查。",
    emphasis: ["平台可对注册、登录、发布、咨询、交易、AI 调用、导出、投诉举报等环节进行风险识别、人工审核、限制频次、功能降级、内容下架、账号冻结、终止服务", "涉嫌违法犯罪的，平台有权依法配合调查"],
  },
  {
    title: "投诉举报与侵权处理",
    description: "你理解并同意，平台有权建立内容投诉、侵权通知、申诉复核与争议留痕机制。当第三方主张你发布、上传、提交或传播的内容涉嫌侵权、违规或损害其合法权益时，平台可依据收到的材料先行采取删除、屏蔽、断开链接、暂停展示、限制传播等临时措施，并通知相关方补充材料或进入申诉流程。",
  },
  {
    title: "服务变更、中断与终止",
    description: "平台可根据业务运营、合规要求、系统维护、安全治理或产品迭代，对服务内容、页面结构、收费策略、功能权限、规则文本及开放范围进行调整，也可能在必要时中断、暂停或终止部分服务。平台会在合理范围内以站内公告、页面提示或通知方式进行说明。",
  },
  {
    title: "账号注销与历史留存",
    description: "你可以在符合法律法规、交易结清、争议处理及安全校验要求的前提下申请注销账号。账号注销后，平台将停止向你提供相应服务，但基于履约举证、审计留痕、争议处理、监管配合与法律法规规定必须保存的信息，平台可在必要期限内继续留存。",
  },
  {
    title: "适用法律与争议解决",
    description: "本协议的订立、履行、解释与争议处理适用中华人民共和国法律。因本协议或平台服务引起的争议，双方应先友好协商；协商不成的，任一方可向平台运营主体所在地有管辖权的人民法院提起诉讼。",
  },
];

const privacyAgreementItems: AgreementItem[] = [
  {
    title: "处理目的与最小必要原则",
    description: "我们会基于注册开户、身份识别、邮箱验证、角色管理、认证审核、咨询与任务协作、交易履约、客户支持、风险控制、投诉处理、内容安全和合规留痕等目的，在最小必要范围内处理你的个人信息；不会因你拒绝提供非必要信息而无故拒绝基本注册功能。",
  },
  {
    title: "我们收集的信息范围",
    description: "根据你选择的身份和实际使用场景，我们可能处理账号信息（邮箱、密码摘要、验证码记录）、基础资料（昵称、实名、学校、专业、公司、职位、个人简介等）、认证资料（身份证明、在职/企业证明、资质文件）、互动内容、交易与咨询记录、设备信息、访问日志、操作日志及安全风控信息。",
  },
  {
    title: "敏感个人信息与认证材料",
    description: "对于认证文件、身份证明、人脸/肖像材料、联系方式、交易信息及未满十四周岁未成年人的个人信息等可能属于敏感个人信息的内容，我们仅在具有特定目的、充分必要且采取严格保护措施的情况下处理，并尽量控制访问人员、用途范围和留存期限。",
    emphasis: ["认证文件、身份证明、人脸/肖像材料、联系方式、交易信息及未满十四周岁未成年人的个人信息等可能属于敏感个人信息", "仅在具有特定目的、充分必要且采取严格保护措施的情况下处理"],
  },
  {
    title: "第三方服务与委托处理",
    description: "为完成邮箱验证码发送、文件存储、系统运维、安全防护、消息触达等必要功能，我们可能委托第三方技术服务商提供支持。我们会要求其在授权范围内处理信息，并通过合同或规则约束其履行保密与安全义务；未经合法依据或你的明确授权，我们不会向无关第三方出售你的个人信息。",
  },
  {
    title: "公开展示与可见范围",
    description: "当你主动开启公开主页、展示职业资料、发布内容、提交作品样本、参与社区互动或申请导师/企业展示位时，相关信息可能在平台指定范围内被其他用户查看。你应谨慎决定公开内容的范围和粒度；对于你主动公开的信息，平台会按照页面展示规则进行呈现，但不对你主动公开带来的商业机会、联系结果或第三方使用后果作保证。",
  },
  {
    title: "日志留存与安全治理",
    description: "为保障账号安全、处理投诉举报、留存认证轨迹并满足审计与合规要求，我们可能记录必要的登录、操作、访问、设备标识、IP、AI 调用及内容安全处置记录，并采取权限控制、审计追踪等合理必要措施保护数据安全。",
  },
  {
    title: "内部权限控制与数据安全措施",
    description: "我们会根据业务职责对个人信息访问范围进行最小授权控制，并结合账号鉴别、传输保护、日志审计、异常监测、备份恢复、人员管理等措施持续提升安全能力。尽管我们会尽力保障信息安全，但受限于技术条件与风险环境，任何系统均无法保证绝对安全；如发生可能影响你权益的安全事件，我们将依法依规采取补救并履行告知义务。",
  },
  {
    title: "AI 服务声明与结果边界",
    description: "平台提供的 AI 功能主要用于岗位理解、职业辅导、文本整理、面试训练、咨询辅助和流程协同。AI 输出可能受到模型能力、上下文、数据时效、提示词和输入真实性影响，存在不完整、不准确、偏差或不适用于个案的情形；你不应将其直接作为录用、合规、法律、医疗、投资或其他高风险事项的唯一依据。",
    emphasis: ["AI 输出可能受到模型能力、上下文、数据时效、提示词和输入真实性影响", "你不应将其直接作为录用、合规、法律、医疗、投资或其他高风险事项的唯一依据"],
  },
  {
    title: "模型优化与训练使用边界",
    description: "平台将根据实际业务与合法依据决定是否对用户输入、输出、标注结果或服务日志进行质量分析、安全评估、模型测试或产品优化。对于认证材料、身份证明等高敏感资料，我们将坚持审慎、必要和最小范围原则处理；如未来存在超出当前页面说明的重要用途变化，平台将依法另行告知并履行相应程序。",
  },
  {
    title: "AI 内容标识、审核与投诉处置",
    description: "依据现行生成式人工智能、深度合成与内容标识规则，平台可能对 AI 生成或显著编辑内容增加显式提示、文件元数据标识、来源说明或其他技术标识，并可对输入与输出进行安全审核、风险拦截、人工复核、日志留存与投诉处理。你向公众发布相关内容时，也应履行相应声明和标识义务。",
    emphasis: ["平台可能对 AI 生成或显著编辑内容增加显式提示、文件元数据标识、来源说明或其他技术标识", "你向公众发布相关内容时，也应履行相应声明和标识义务"],
  },
  {
    title: "你的个人信息权利",
    description: "在符合法律法规及平台身份校验要求的前提下，你有权查询、复制、更正、补充、删除你的个人信息，有权撤回同意、注销账号、获取相关规则说明，并可通过平台公示渠道行使投诉、举报或申诉权利；法律法规另有规定或为保障交易安全、履约举证、争议处理所必需的除外。",
    emphasis: ["你有权查询、复制、更正、补充、删除你的个人信息，有权撤回同意、注销账号、获取相关规则说明", "法律法规另有规定或为保障交易安全、履约举证、争议处理所必需的除外"],
  },
  {
    title: "未成年人信息保护",
    description: "若你属于未成年人，尤其是不满十四周岁的未成年人，应在监护人同意和指导下使用平台并提交相关资料。对不满十四周岁未成年人的个人信息，我们将按照敏感个人信息的更高保护标准处理；如发现未成年人私密信息或相关扩散风险，平台有权及时提示、限制传播并采取必要保护措施。",
  },
  {
    title: "保存期限、删除与跨境边界",
    description: "我们会在实现处理目的所必需的期限内保存个人信息，超过必要期限后依法删除或匿名化处理。原则上相关信息存储于中国境内；如未来确需跨境提供，我们将依法履行相应程序并另行告知。",
  },
];

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function renderAgreementDescription(description: string, emphasis: string[] | undefined) {
  if (!emphasis?.length) {
    return description;
  }

  const tokens = Array.from(new Set(emphasis.filter(Boolean))).sort((left, right) => right.length - left.length);

  if (!tokens.length) {
    return description;
  }

  const matcher = new RegExp(`(${tokens.map((item) => escapeRegExp(item)).join("|")})`, "g");
  const segments = description.split(matcher);

  return segments.map((segment, index) => {
    if (!segment) {
      return null;
    }

    const isEmphasis = tokens.includes(segment);

    if (!isEmphasis) {
      return <span key={`${segment}-${index}`}>{segment}</span>;
    }

    return (
      <span
        key={`${segment}-${index}`}
        className="font-semibold text-amber-700"
      >
        {segment}
      </span>
    );
  });
}

function feedbackStyles(tone: FeedbackTone) {
  switch (tone) {
    case "error":
      return "border-rose-200 bg-rose-50 text-rose-700";
    case "success":
      return "border-emerald-200 bg-emerald-50 text-emerald-700";
    default:
      return "border-indigo-200 bg-indigo-50 text-indigo-700";
  }
}

function normalizeTag(tag: string) {
  return tag.trim().replace(/\s+/g, " ");
}

function formatFileSize(sizeBytes: number) {
  if (sizeBytes >= 1024 * 1024) {
    return `${(sizeBytes / (1024 * 1024)).toFixed(2)} MB`;
  }
  if (sizeBytes >= 1024) {
    return `${(sizeBytes / 1024).toFixed(1)} KB`;
  }
  return `${sizeBytes} B`;
}

function resolvePostAuthDestination(role: RegisterRole) {
  if (role === "MENTOR") {
    return "/mentor/dashboard";
  }

  if (role === "ENTERPRISE") {
    return "/enterprise/dashboard";
  }

  return "/student/dashboard";
}

function getEnterpriseDraftKey(identifier: string | null) {
  if (!identifier) {
    return null;
  }

  return `${ENTERPRISE_DRAFT_STORAGE_PREFIX}:${identifier}`;
}

function readEnterpriseDraft(identifier: string | null): EnterpriseFormState | null {
  if (typeof window === "undefined") {
    return null;
  }

  const key = getEnterpriseDraftKey(identifier);

  if (!key) {
    return null;
  }

  const raw = window.sessionStorage.getItem(key);

  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as Partial<EnterpriseFormState> & { contactName?: string };
    const realName = typeof parsed.realName === "string"
      ? parsed.realName
      : typeof parsed.contactName === "string"
        ? parsed.contactName
        : null;

    if (realName === null || typeof parsed.company !== "string" || typeof parsed.title !== "string") {
      return null;
    }

    return {
      realName,
      company: parsed.company,
      title: parsed.title,
    };
  } catch {
    return null;
  }
}

function writeEnterpriseDraft(identifier: string | null, draft: EnterpriseFormState) {
  if (typeof window === "undefined") {
    return;
  }

  const key = getEnterpriseDraftKey(identifier);

  if (!key) {
    return;
  }

  window.sessionStorage.setItem(key, JSON.stringify(draft));
}

function TagEditor({
  count,
  disabled,
  inputValue,
  onInputChange,
  onKeyDown,
  onRemove,
  placeholder,
  tags,
}: {
  count: number;
  disabled?: boolean;
  inputValue: string;
  onInputChange: (value: string) => void;
  onKeyDown: (event: KeyboardEvent<HTMLInputElement>) => void;
  onRemove: (tag: string) => void;
  placeholder: string;
  tags: string[];
}) {
  return (
    <div>
      <label className={joinClasses(fieldLabelClass, "flex items-center justify-between")}>
        <span>技能标签</span>
        <span className="text-xs font-normal normal-case tracking-normal text-slate-400">{count}/{MAX_SKILL_TAGS}</span>
      </label>
      <div className="flex min-h-[56px] w-full flex-wrap items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 p-2 transition-all focus-within:ring-2 focus-within:ring-indigo-500/50">
        <AnimatePresence>
          {tags.map((tag) => (
            <motion.span
              key={tag}
              initial={{ scale: 0, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0, opacity: 0 }}
              className="flex items-center rounded-md bg-indigo-100 px-3 py-1.5 text-sm text-indigo-700 shadow-sm"
            >
              {tag}
              <X size={14} className="ml-1 cursor-pointer hover:text-indigo-900" onClick={() => onRemove(tag)} />
            </motion.span>
          ))}
        </AnimatePresence>
        <input
          type="text"
          value={inputValue}
          onChange={(event) => onInputChange(event.target.value)}
          onKeyDown={onKeyDown}
          disabled={disabled}
          className="min-w-[140px] flex-1 border-none bg-transparent text-base text-slate-700 outline-none disabled:opacity-50"
          placeholder={placeholder}
        />
      </div>
    </div>
  );
}

export default function RegisterDetailsPanel({
  initialRole,
  onBack,
  pendingRegistration,
  demoModeEnabled = false,
  demoCertificationBypassEnabled = false,
}: RegisterDetailsPanelProps) {
  const { login, loading } = useAuth();
  const navigate = useNavigate();
  const [selectedRole, setSelectedRole] = useState<RegisterRole>(pendingRegistration.preferredRole ?? initialRole ?? DEFAULT_ROLE);
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback] = useState<FeedbackState | null>(null);
  const [agreementsAccepted, setAgreementsAccepted] = useState(false);
  const [expandedAgreement, setExpandedAgreement] = useState<AgreementPanel>(null);
  const [studentForm, setStudentForm] = useState<StudentFormState>({
    realName: "",
    schoolName: "",
    grade: "",
    major: "",
    targetPosition: "",
    selfIntro: "",
  });
  const [studentSkillTags, setStudentSkillTags] = useState<string[]>([]);
  const [studentTagInput, setStudentTagInput] = useState("");
  const [studentGradeDropdownOpen, setStudentGradeDropdownOpen] = useState(false);
  const [mentorForm, setMentorForm] = useState<MentorFormState>({
    realName: "",
    company: "",
    title: "",
  });
  const [mentorSelectedFile, setMentorSelectedFile] = useState<File | null>(null);
  const [enterpriseForm, setEnterpriseForm] = useState<EnterpriseFormState>(() => {
    // 企业二阶段资料按邮箱暂存，刷新页面后不会丢失联系人和企业名称。
    const draft = readEnterpriseDraft(pendingRegistration.email);

    return draft ?? {
      realName: "",
      company: "",
      title: "",
    };
  });
  const [enterpriseSelectedFile, setEnterpriseSelectedFile] = useState<File | null>(null);
  const navigateTimeoutRef = useRef<number | null>(null);
  const studentGradeDropdownRef = useRef<HTMLDivElement | null>(null);
  const mentorFileInputRef = useRef<HTMLInputElement | null>(null);
  const enterpriseFileInputRef = useRef<HTMLInputElement | null>(null);
  const busy = submitting || loading;

  useEffect(() => {
    return () => {
      if (navigateTimeoutRef.current !== null) {
        window.clearTimeout(navigateTimeoutRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (!expandedAgreement) {
      return;
    }

    const handleWindowKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === "Escape") {
        setExpandedAgreement(null);
      }
    };

    window.addEventListener("keydown", handleWindowKeyDown);

    return () => {
      window.removeEventListener("keydown", handleWindowKeyDown);
    };
  }, [expandedAgreement]);

  useEffect(() => {
    if (!expandedAgreement || typeof document === "undefined") {
      return undefined;
    }

    // 协议弹层打开时锁住 body 滚动，避免背景表单跟随滚动造成错位。
    const originalOverflow = document.body.style.overflow;
    const originalPaddingRight = document.body.style.paddingRight;
    const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;

    document.body.style.overflow = "hidden";
    if (scrollbarWidth > 0) {
      document.body.style.paddingRight = `${scrollbarWidth}px`;
    }

    return () => {
      document.body.style.overflow = originalOverflow;
      document.body.style.paddingRight = originalPaddingRight;
    };
  }, [expandedAgreement]);

  useEffect(() => {
    setSelectedRole(pendingRegistration.preferredRole ?? initialRole ?? DEFAULT_ROLE);
  }, [initialRole, pendingRegistration.preferredRole]);

  useEffect(() => {
    if (!studentGradeDropdownOpen) {
      return undefined;
    }

    const handlePointerDown = (event: PointerEvent) => {
      if (!studentGradeDropdownRef.current?.contains(event.target as Node)) {
        setStudentGradeDropdownOpen(false);
      }
    };

    const handleEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key === "Escape") {
        setStudentGradeDropdownOpen(false);
      }
    };

    window.addEventListener("pointerdown", handlePointerDown);
    window.addEventListener("keydown", handleEscape);

    return () => {
      window.removeEventListener("pointerdown", handlePointerDown);
      window.removeEventListener("keydown", handleEscape);
    };
  }, [studentGradeDropdownOpen]);

  const queueNavigation = (destination: string) => {
    if (navigateTimeoutRef.current !== null) {
      window.clearTimeout(navigateTimeoutRef.current);
    }

    navigateTimeoutRef.current = window.setTimeout(() => {
      navigate(destination, { replace: true });
    }, 900);
  };

  const handleRoleSelect = (nextRole: RegisterRole) => {
    if (busy) {
      return;
    }

    setFeedback(null);
    setSelectedRole(nextRole);
  };

  const openAgreementPanel = (panel: Exclude<AgreementPanel, null>) => {
    setExpandedAgreement(panel);
  };

  const closeAgreementPanel = () => {
    setExpandedAgreement(null);
  };

  const handleStudentTagKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key !== "Enter") {
      return;
    }

    event.preventDefault();
    const newTag = normalizeTag(studentTagInput);

    // 技能标签限制数量并去重，空标签直接丢弃。
    if (!newTag || studentSkillTags.includes(newTag) || studentSkillTags.length >= MAX_SKILL_TAGS) {
      setStudentTagInput("");
      return;
    }

    setStudentSkillTags((current) => [...current, newTag]);
    setStudentTagInput("");
  };

  const handleStudentGradeSelect = (grade: (typeof STUDENT_GRADE_OPTIONS)[number]) => {
    setStudentForm((current) => ({ ...current, grade }));
    setStudentGradeDropdownOpen(false);
  };

  const validateStudentForm = () => {
    if (!pendingRegistration.displayName.trim()) {
      return "请先返回上一步补充昵称。";
    }

    if (!studentForm.realName.trim()) {
      return "请填写真实姓名。";
    }

    if (!studentForm.schoolName.trim()) {
      return "请先填写你的学校名称，后续同校学生隐私与社区能力会基于这个标签生效。";
    }

    if (!studentForm.grade) {
      return "请先选择当前年级。";
    }

    if (!studentForm.major.trim()) {
      return "请先填写你的主修专业。";
    }

    if (!studentForm.targetPosition.trim()) {
      return "请先填写你当前希望冲刺的目标岗位。";
    }

    return null;
  };

  const validateMentorForm = () => {
    if (!mentorForm.realName.trim()) {
      return "请填写导师真实姓名。";
    }

    if (!mentorForm.company.trim()) {
      return "请填写所在公司或机构名称。";
    }

    if (!mentorForm.title.trim()) {
      return "请填写当前职级或岗位名称。";
    }

    if (!demoCertificationBypassEnabled && !mentorSelectedFile) {
      return "请先选择导师认证资料后再提交。";
    }

    return null;
  };

  const validateEnterpriseForm = () => {
    if (!enterpriseForm.realName.trim()) {
      return "请填写联系人真实姓名。";
    }

    if (!enterpriseForm.company.trim()) {
      return "请填写企业名称。";
    }

    if (!enterpriseForm.title.trim()) {
      return "请填写你的岗位或身份。";
    }

    if (!demoCertificationBypassEnabled && !enterpriseSelectedFile) {
      return "请先选择企业认证资料后再提交。";
    }

    return null;
  };

  const persistStudentProfile = async () => {
    // 学生注册先建账号并登录，再补写学生画像基础资料。
    await apiRequest("/profiles/students/me", {
      method: "PUT",
      body: JSON.stringify({
        realName: studentForm.realName.trim(),
        schoolName: studentForm.schoolName.trim(),
        major: studentForm.major.trim(),
        grade: studentForm.grade,
        targetPosition: studentForm.targetPosition.trim(),
        skillTags: studentSkillTags,
        selfIntro: studentForm.selfIntro.trim(),
      }),
    });
  };

  const buildRegistrationCertificationFormData = (
    nextRole: Extract<RegisterRole, "MENTOR" | "ENTERPRISE">,
    file: File | null,
  ) => {
    // 导师/企业注册和首次认证合并提交，后端会生成待审核 submission。
    const formData = new FormData();
    formData.append("role", nextRole);
    formData.append("email", pendingRegistration.email.trim().toLowerCase());
    formData.append("password", pendingRegistration.password);
    formData.append("displayName", pendingRegistration.displayName.trim());
    formData.append("realName", nextRole === "MENTOR" ? mentorForm.realName.trim() : enterpriseForm.realName.trim());
    formData.append("companyName", nextRole === "MENTOR" ? mentorForm.company.trim() : enterpriseForm.company.trim());
    formData.append("jobTitle", nextRole === "MENTOR" ? mentorForm.title.trim() : enterpriseForm.title.trim());
    if (pendingRegistration.emailVerificationToken) {
      formData.append("emailVerificationToken", pendingRegistration.emailVerificationToken);
    }
    if (file) {
      formData.append("file", file);
    }
    return formData;
  };

  const registerPendingAccount = async (): Promise<PendingRegistrationResult> => {
    const normalizedEmail = pendingRegistration.email.trim().toLowerCase();
    let certification: CertificationSubmission | null = null;
    let approvalStatus: string | null = null;

    if (selectedRole === "STUDENT") {
      // 学生只走公开注册接口，详细资料在登录成功后写入 profile。
      const requestBody = {
        role: selectedRole,
        email: normalizedEmail,
        password: pendingRegistration.password,
        displayName: pendingRegistration.displayName.trim(),
        realName: studentForm.realName.trim(),
        emailVerificationToken: pendingRegistration.emailVerificationToken,
      };

      await apiRequest<RegisterResponse>("/auth/register", {
        method: "POST",
        skipAuth: true,
        body: JSON.stringify(requestBody),
      });
    } else {
      const file = selectedRole === "MENTOR" ? mentorSelectedFile : enterpriseSelectedFile;

      if (!file && !demoCertificationBypassEnabled) {
        throw new ApiClientError("请先选择认证资料文件", 400, "CERT-CLIENT-001");
      }

      // 导师/企业必须带认证材料，账号创建后默认进入待审核状态。
      const response = await apiRequest<RegisterWithCertificationResponse>("/auth/register-with-certification", {
        method: "POST",
        skipAuth: true,
        body: buildRegistrationCertificationFormData(selectedRole, file),
      });
      approvalStatus = response.approvalStatus;
      certification = response.currentSubmission;
    }

    clearPendingRegistration();
    return {
      type: "created",
      credentials: {
        email: normalizedEmail,
        password: pendingRegistration.password,
      },
      approvalStatus,
      certification,
    };
  };

  const handleCertificationFileChange = (
    nextRole: Extract<RegisterRole, "MENTOR" | "ENTERPRISE">,
    event: ChangeEvent<HTMLInputElement>,
  ) => {
    const file = event.target.files?.[0];
    event.target.value = "";

    if (!file) {
      return;
    }

    setFeedback(null);
    if (nextRole === "MENTOR") {
      setMentorSelectedFile(file);
      return;
    }

    setEnterpriseSelectedFile(file);
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFeedback(null);

    if (!agreementsAccepted) {
      setFeedback({
        tone: "error",
        message: "请先阅读并勾选用户协议、隐私条款与 AI 合规免责声明后再提交注册。",
      });
      return;
    }

    const validationMessage = selectedRole === "STUDENT"
      ? validateStudentForm()
      : selectedRole === "MENTOR"
        ? validateMentorForm()
        : validateEnterpriseForm();

    if (validationMessage) {
      setFeedback({ tone: "error", message: validationMessage });
      return;
    }

    setSubmitting(true);

    try {
      if (selectedRole === "ENTERPRISE") {
        // 企业材料通常填写更久，提交前再保存一次草稿供失败重试。
        writeEnterpriseDraft(pendingRegistration.email, enterpriseForm);
      }

      const registrationResult = await registerPendingAccount();

      if (registrationResult.type !== "created") {
        setFeedback({ tone: "error", message: "注册信息读取失败，请返回上一步重试。" });
        return;
      }

      try {
        // 建号成功后尝试自动登录；失败时保留账号，让用户回登录页手动进入。
        await login(registrationResult.credentials.email, registrationResult.credentials.password);
      } catch {
        setFeedback({
          tone: "info",
          message: "账号已经创建成功，但自动登录没有完成。你可以直接回到登录页继续进入平台。",
        });
        queueNavigation("/auth?mode=login");
        return;
      }

      if (selectedRole === "STUDENT") {
        // 学生资料写入依赖已登录 token，因此放在自动登录之后。
        await persistStudentProfile();
        setFeedback({
          tone: "success",
          message: "账号已创建，学生资料也已保存，正在进入平台。",
        });
      } else if (selectedRole === "MENTOR") {
        setFeedback({
          tone: "success",
          message: registrationResult.approvalStatus === "APPROVED"
            ? `导师账号已创建，演示模式下已直接开通。${registrationResult.certification?.assets[0]?.originalFilename ? ` 已提交文件：${registrationResult.certification.assets[0].originalFilename}。` : demoCertificationBypassEnabled ? " 本次未要求上传认证文件。" : ""}`
            : `导师账号已创建，认证资料已提交，正在审核中。${registrationResult.certification?.assets[0]?.originalFilename ? ` 已提交文件：${registrationResult.certification.assets[0].originalFilename}。` : ""}`,
        });
      } else {
        setFeedback({
          tone: "success",
          message: registrationResult.approvalStatus === "APPROVED"
            ? `企业账号已创建，演示模式下已直接开通。${registrationResult.certification?.assets[0]?.originalFilename ? ` 已提交文件：${registrationResult.certification.assets[0].originalFilename}。` : demoCertificationBypassEnabled ? " 本次未要求上传认证文件。" : ""}`
            : `企业账号已创建，认证资料已提交，正在审核中。${registrationResult.certification?.assets[0]?.originalFilename ? ` 已提交文件：${registrationResult.certification.assets[0].originalFilename}。` : ""}`,
        });
      }

      queueNavigation(resolvePostAuthDestination(selectedRole));
    } catch (error) {
      const apiError = error as ApiClientError;

      if (!getSessionSnapshot().accessToken && apiError.code === "AUTH-1001") {
        setFeedback({
          tone: "error",
          message: "这个邮箱已经注册过了，可以直接回登录页继续。",
        });
        return;
      }

      const message = apiError.code === "AUTH-1005"
        ? "当前只支持学生、导师和企业自助注册。"
        : apiError.code === "AUTH-1014"
          ? "邮箱验证缺失了，请返回上一步重新获取并填写验证码。"
          : apiError.code === "AUTH-1015"
            ? "邮箱验证已失效，请返回上一步重新获取并填写验证码。"
            : apiError.message || "注册提交失败，请稍后再试。";

      setFeedback({
        tone: "error",
        message,
      });
    } finally {
      setSubmitting(false);
    }
  };

  const submitButtonClass = selectedRole === "STUDENT"
    ? "bg-indigo-600 hover:bg-indigo-700 shadow-indigo-200"
    : selectedRole === "MENTOR"
      ? "bg-emerald-600 hover:bg-emerald-700 shadow-emerald-200"
      : "bg-amber-500 hover:bg-amber-600 shadow-amber-200";

  const roleFormShellClass = selectedRole === "STUDENT"
    ? "border-indigo-100 bg-indigo-50/45"
    : selectedRole === "MENTOR"
      ? "border-emerald-100 bg-emerald-50/45"
      : "border-amber-100 bg-amber-50/50";
  const submitDisabled = busy || !agreementsAccepted;

  const submitButtonLabel = selectedRole === "STUDENT"
    ? "完成注册并进入平台"
    : selectedRole === "MENTOR"
      ? demoCertificationBypassEnabled
        ? "创建导师账号并直接开通"
        : "创建导师账号并提交认证资料"
      : demoCertificationBypassEnabled
        ? "创建企业账号并直接开通"
        : "创建企业账号并提交认证资料";

  const agreementModalTitle = expandedAgreement === "terms" ? "用户协议" : "隐私条款与 AI 服务声明";
  const agreementModalDescription = expandedAgreement === "terms"
    ? "查看账号注册、认证审核、合法使用、AI 内容责任边界与争议解决等正式条款。"
    : "查看个人信息处理规则、敏感信息保护、AI 服务边界、标识义务与未成年人保护说明。";
  const agreementModalItems = expandedAgreement === "terms" ? userAgreementItems : privacyAgreementItems;
  const AgreementModalIcon = expandedAgreement === "terms" ? FileText : Bot;
  const agreementModalLeadTitle = expandedAgreement === "terms"
    ? "请在完成注册前，完整阅读并理解以下服务使用条款。"
    : "请在提交注册资料前，完整阅读并理解以下隐私处理与 AI 服务说明。";
  const agreementModalLeadPoints = expandedAgreement === "terms"
    ? ["适用于学生、导师、企业三类账号", "覆盖注册、认证、内容发布与 AI 使用场景", "提交注册即视为同意最新版有效条款"]
    : ["覆盖账号注册、认证、内容安全与 AI 辅助场景", "重点说明敏感信息、日志留存与未成年人保护", "正式商用前仍应结合运营主体与法务意见更新"];
  const agreementModalFootnote = expandedAgreement === "terms"
    ? "本协议结合《中华人民共和国民法典》《中华人民共和国网络安全法》《中华人民共和国数据安全法》《中华人民共和国个人信息保护法》《生成式人工智能服务管理暂行办法》《互联网信息服务深度合成管理规定》等现行规范整理，适用于平台注册、身份认证、内容发布、咨询协作与 AI 辅助服务相关场景。"
    : "本说明结合《中华人民共和国个人信息保护法》《中华人民共和国网络安全法》《中华人民共和国数据安全法》《未成年人网络保护条例》《生成式人工智能服务管理暂行办法》《互联网信息服务深度合成管理规定》《人工智能生成合成内容标识办法》等现行规范整理，适用于平台注册、身份认证、内容治理及 AI 服务场景中的个人信息处理活动。";
  const agreementModalOverlay = typeof document === "undefined"
    ? null
    : createPortal(
      <AnimatePresence>
        {expandedAgreement ? (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-[120] overflow-y-auto bg-slate-950/55 px-4 py-6 backdrop-blur-sm"
            onClick={(event) => {
              if (event.target === event.currentTarget) {
                closeAgreementPanel();
              }
            }}
          >
            <div
              className="flex min-h-full items-center justify-center"
              onClick={(event) => {
                if (event.target === event.currentTarget) {
                  closeAgreementPanel();
                }
              }}
            >
              <motion.div
                initial={{ opacity: 0, y: 24, scale: 0.98 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: 16, scale: 0.98 }}
                transition={{ duration: 0.22, ease: "easeOut" }}
                className="flex h-[min(84vh,56rem)] w-full max-w-[58rem] flex-col overflow-hidden rounded-[2rem] border border-white/75 bg-white shadow-[0_28px_80px_rgba(15,23,42,0.28)]"
                onClick={(event) => {
                  event.stopPropagation();
                }}
              >
                <div className="flex items-start justify-between gap-4 border-b border-slate-200 bg-white px-7 py-6">
                  <div className="min-w-0">
                    <div className="inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100 text-slate-700 shadow-[inset_0_1px_0_rgba(255,255,255,0.7)]">
                      <AgreementModalIcon size={20} />
                    </div>
                    <div className="mt-4 flex flex-wrap items-center gap-2 text-[11px] font-semibold tracking-[0.18em] text-slate-400">
                      <span>{expandedAgreement === "terms" ? "平台用户协议" : "隐私条款与 AI 服务声明"}</span>
                      <span className="h-1 w-1 rounded-full bg-slate-300" />
                      <span>当前适用文本</span>
                    </div>
                    <h3 className="mt-3 text-[1.9rem] font-semibold tracking-tight text-slate-950">{agreementModalTitle}</h3>
                  <p className="mt-2 max-w-3xl text-[15px] leading-[1.8] text-slate-500">{agreementModalDescription}</p>
                  </div>
                  <button
                    type="button"
                    onClick={closeAgreementPanel}
                    className="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border border-slate-200 bg-slate-50 text-slate-500 transition-colors hover:border-slate-300 hover:bg-white hover:text-slate-900"
                  >
                    <X size={18} />
                  </button>
                </div>

                <div className="min-h-0 flex-1 overflow-y-auto rounded-b-[2rem] bg-slate-50/80 px-7 py-6">
                  <div className="rounded-[1.5rem] border border-slate-200 bg-white px-5 py-5 shadow-[0_10px_30px_rgba(15,23,42,0.04)]">
                    <div className="text-sm font-semibold text-slate-900">{agreementModalLeadTitle}</div>
                    <p className="mt-2 text-sm leading-[1.85] text-slate-600 [text-indent:2em]">
                      为保证注册流程、账号认证、内容治理、AI 辅助服务与个人信息处理规则具有清晰边界，以下条款将构成你与平台之间的基础约定。若你对任一条款存在疑问，应在继续注册前停止提交并进一步确认。
                    </p>
                    <div className="mt-4 flex flex-wrap gap-2">
                      {agreementModalLeadPoints.map((item) => (
                        <span
                          key={item}
                          className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs font-medium text-slate-600"
                        >
                          {item}
                        </span>
                      ))}
                    </div>
                  </div>

                  <ul className="mt-5 space-y-4">
                    {agreementModalItems.map((item, index) => (
                      <li
                        key={item.title}
                        className="rounded-[1.5rem] border border-slate-200 bg-white px-5 py-5 shadow-[0_10px_30px_rgba(15,23,42,0.04)]"
                      >
                        <div className="flex items-start gap-4">
                          <div className="inline-flex min-h-[2.75rem] min-w-[2.75rem] items-center justify-center rounded-2xl bg-slate-950 text-[13px] font-semibold text-white shadow-[0_12px_24px_rgba(15,23,42,0.14)]">
                            {(index + 1).toString().padStart(2, "0")}
                          </div>
                          <div className="min-w-0 flex-1">
                            <div className="text-[11px] font-semibold text-slate-400">
                              第 {index + 1} 条
                            </div>
                            <div className="mt-2 text-[1.1rem] font-semibold text-slate-950">{item.title}</div>
                            <p className="mt-3 text-[15px] leading-[1.9] text-slate-600 [text-indent:2em]">
                              {renderAgreementDescription(item.description, item.emphasis)}
                            </p>
                          </div>
                        </div>
                      </li>
                    ))}
                  </ul>

                  <div className="mt-5 rounded-[1.5rem] border border-slate-200 bg-white px-5 py-5 shadow-[0_10px_30px_rgba(15,23,42,0.04)]">
                    <div className="text-sm font-semibold text-slate-900">适用说明</div>
                    <p className="mt-2 text-sm leading-[1.85] text-slate-600 [text-indent:2em]">
                      {agreementModalFootnote}
                    </p>
                    <p className="mt-3 text-sm leading-[1.85] text-slate-600 [text-indent:2em]">
                      若相关法律法规、监管要求、平台服务范围或用户权益保护机制发生调整，平台将依法通过页面展示、站内通知、注册邮箱或其他合理方式向你说明最新适用规则。
                    </p>
                  </div>
                </div>
              </motion.div>
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>,
      document.body,
    );

  return (
    <>
      <div className="relative flex h-full w-full flex-1 min-h-0 flex-col bg-white">
      <div className="w-full border-b border-slate-100 bg-slate-950 px-6 py-5 text-white lg:px-10 lg:py-5">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={onBack}
              className="inline-flex h-11 w-11 items-center justify-center rounded-2xl border border-white/12 bg-white/8 text-white transition-colors hover:bg-white/12"
            >
              <ArrowLeft size={18} />
            </button>
            <div>
              <div className="text-[11px] text-slate-400">Register Details</div>
              <h2 className="mt-1 text-2xl font-semibold text-white">最后一步，完善你的身份资料</h2>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div className="h-2.5 w-2.5 rounded-full bg-indigo-500" />
            <div className="h-2.5 w-8 rounded-full bg-indigo-500 shadow-[0_0_10px_rgba(99,102,241,0.5)]" />
          </div>
        </div>

        <div className="mt-4 flex flex-wrap gap-2 text-xs text-slate-200">
          <span className="rounded-full border border-white/12 bg-white/8 px-3 py-1.5">昵称：{pendingRegistration.displayName}</span>
          <span className="rounded-full border border-white/12 bg-white/8 px-3 py-1.5">{pendingRegistration.email}</span>
        </div>

        <p className="mt-4 max-w-3xl text-sm leading-6 text-slate-300">
          {demoCertificationBypassEnabled
            ? "选择身份并补充必要资料，提交后会直接完成注册；演示模式下导师与企业可跳过上传并直接开通。"
            : "选择身份并补充必要资料，提交后会直接完成注册；导师与企业资料会进入审核流程。"}
        </p>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-1 min-h-0 flex-col">
        <div className="flex-1 min-h-0 overflow-y-auto px-6 py-5 lg:px-10 lg:py-4">
          <div className="mx-auto flex w-full max-w-[1220px] lg:min-h-full lg:items-center">
            <div className="grid w-full gap-5 lg:grid-cols-[17.5rem_minmax(0,1fr)] lg:items-center lg:gap-8">
              <aside className="min-w-0 self-start lg:self-center">
              <label className="mb-3 block text-base font-bold text-slate-700">你希望以什么身份加入平台？</label>
              <LayoutGroup>
                <div className="grid grid-cols-1 gap-3">
                  {rolesConfig.map((config) => {
                    const Icon = config.icon;
                    const isSelected = selectedRole === config.id;

                    return (
                      <motion.button
                        key={config.id}
                        type="button"
                        whileHover={{ scale: 1.02 }}
                        whileTap={{ scale: 0.98 }}
                        onClick={() => handleRoleSelect(config.id)}
                        className={joinClasses(
                          "relative rounded-2xl border-2 p-3.5 text-left transition-all duration-300",
                          isSelected
                            ? `${config.border} ${config.bg} shadow-md ring-4 ${config.ring}`
                            : "border-slate-100 bg-white hover:border-slate-300 hover:bg-slate-50",
                        )}
                      >
                        <AnimatePresence initial={false}>
                          {isSelected ? (
                            <motion.span
                              layoutId="register-role-check"
                              initial={{ scale: 0.45, opacity: 0 }}
                              animate={{ scale: 1, opacity: 1 }}
                              exit={{ scale: 0.45, opacity: 0 }}
                              transition={{ type: "spring", stiffness: 320, damping: 20 }}
                              className={joinClasses("absolute -right-2 -top-2 flex h-6 w-6 items-center justify-center rounded-full text-white shadow-sm", config.color.replace("text-", "bg-"))}
                            >
                              <Check size={14} strokeWidth={3} />
                            </motion.span>
                          ) : null}
                        </AnimatePresence>

                        <div className="flex items-start gap-3">
                          <div className={joinClasses("flex h-11 w-11 shrink-0 items-center justify-center rounded-xl", isSelected ? "bg-white shadow-sm" : "bg-slate-100", config.color)}>
                            <Icon size={22} />
                          </div>
                          <div className="min-w-0">
                            <div className={joinClasses("text-sm font-bold sm:text-base", isSelected ? "text-slate-900" : "text-slate-700")}>{config.title}</div>
                            <p className="mt-1 text-[12px] leading-5 text-slate-500">{config.desc}</p>
                          </div>
                        </div>
                      </motion.button>
                    );
                  })}
                </div>
              </LayoutGroup>
              </aside>

              <div className="min-w-0 lg:flex lg:min-h-[31rem] lg:flex-col lg:justify-center">
                <div className={joinClasses("rounded-[1.75rem] border p-4 shadow-[0_18px_44px_rgba(15,23,42,0.06)] sm:p-5 lg:p-7", roleFormShellClass)}>
                  {feedback ? (
                    <div className={joinClasses("mb-4 rounded-2xl border px-4 py-3 text-base leading-7", feedbackStyles(feedback.tone))}>
                      {feedback.message}
                    </div>
                  ) : null}
                  {demoModeEnabled ? (
                    <div className="mb-4 rounded-2xl border border-sky-200 bg-sky-50 px-4 py-3 text-sm leading-6 text-sky-800">
                      当前为本地演示模式。注册后的复杂审核链路已放宽，仅用于答辩演示。
                    </div>
                  ) : null}

                  <AnimatePresence mode="wait">
                    {selectedRole === "STUDENT" ? (
                      <motion.div
                        key="student-form"
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -20 }}
                        transition={{ duration: 0.25 }}
                        className="grid gap-4"
                      >
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
                          <div>
                            <label className={fieldLabelClass}>社区昵称</label>
                            <input
                              type="text"
                              value={pendingRegistration.displayName}
                              readOnly
                              className={readOnlyInputClass}
                            />
                          </div>
                          <div>
                            <label className={fieldLabelClass}>真实姓名</label>
                            <input
                              type="text"
                              required
                              value={studentForm.realName}
                              onChange={(event) => setStudentForm((current) => ({ ...current, realName: event.target.value }))}
                              className={joinClasses(inputClass, "focus:ring-2 focus:ring-indigo-500/50")}
                              placeholder="请填写真实姓名"
                            />
                          </div>
                          <div>
                            <label className={fieldLabelClass}>学校名称</label>
                            <input
                              type="text"
                              required
                              value={studentForm.schoolName}
                              onChange={(event) => setStudentForm((current) => ({ ...current, schoolName: event.target.value }))}
                              className={joinClasses(inputClass, "focus:ring-2 focus:ring-indigo-500/50")}
                              placeholder="例如：华东理工大学"
                            />
                          </div>
                          <div ref={studentGradeDropdownRef}>
                            <label className={fieldLabelClass}>当前年级</label>
                            <div className="relative">
                              <button
                                type="button"
                                aria-haspopup="listbox"
                                aria-expanded={studentGradeDropdownOpen}
                                onClick={() => setStudentGradeDropdownOpen((current) => !current)}
                                className={joinClasses(
                                  inputClass,
                                  "flex items-center justify-between text-left focus:ring-2 focus:ring-indigo-500/50",
                                  studentForm.grade ? "text-slate-700" : "text-slate-400",
                                )}
                              >
                                <span>{studentForm.grade || "请选择年级..."}</span>
                                <ChevronRight
                                  size={18}
                                  className={joinClasses(
                                    "shrink-0 text-slate-400 transition-transform",
                                    studentGradeDropdownOpen && "rotate-90",
                                  )}
                                />
                              </button>

                              <AnimatePresence>
                                {studentGradeDropdownOpen ? (
                                  <motion.div
                                    initial={{ opacity: 0, y: 8, scale: 0.98 }}
                                    animate={{ opacity: 1, y: 0, scale: 1 }}
                                    exit={{ opacity: 0, y: 6, scale: 0.98 }}
                                    transition={{ duration: 0.16, ease: "easeOut" }}
                                    className="absolute left-0 right-0 top-[calc(100%+0.5rem)] z-20 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-[0_20px_40px_rgba(15,23,42,0.12)]"
                                  >
                                    <div className="max-h-64 overflow-y-auto p-2" role="listbox" aria-label="当前年级">
                                      {STUDENT_GRADE_OPTIONS.map((grade) => {
                                        const isSelected = studentForm.grade === grade;

                                        return (
                                          <button
                                            key={grade}
                                            type="button"
                                            role="option"
                                            aria-selected={isSelected}
                                            onClick={() => handleStudentGradeSelect(grade)}
                                            className={joinClasses(
                                              "flex w-full items-center justify-between rounded-xl px-3 py-2.5 text-left text-sm transition-colors",
                                              isSelected
                                                ? "bg-indigo-50 font-semibold text-indigo-700"
                                                : "text-slate-600 hover:bg-slate-50 hover:text-slate-900",
                                            )}
                                          >
                                            <span>{grade}</span>
                                            {isSelected ? <Check size={16} className="text-indigo-600" /> : null}
                                          </button>
                                        );
                                      })}
                                    </div>
                                  </motion.div>
                                ) : null}
                              </AnimatePresence>
                            </div>
                          </div>
                          <div>
                            <label className={fieldLabelClass}>主修专业</label>
                            <input
                              type="text"
                              required
                              value={studentForm.major}
                              onChange={(event) => setStudentForm((current) => ({ ...current, major: event.target.value }))}
                              className={joinClasses(inputClass, "focus:ring-2 focus:ring-indigo-500/50")}
                              placeholder="例如：计算机科学与技术"
                            />
                          </div>
                          <div className="md:col-span-2 xl:col-span-2">
                            <label className={fieldLabelClass}>目标岗位</label>
                            <input
                              type="text"
                              required
                              value={studentForm.targetPosition}
                              onChange={(event) => setStudentForm((current) => ({ ...current, targetPosition: event.target.value }))}
                              className={joinClasses(inputClass, "focus:ring-2 focus:ring-indigo-500/50")}
                              placeholder="例如：前端开发 / Java 后端"
                            />
                          </div>
                        </div>

                        <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,0.92fr)_minmax(0,1.08fr)] xl:items-start">
                          <TagEditor
                            count={studentSkillTags.length}
                            disabled={studentSkillTags.length >= MAX_SKILL_TAGS}
                            inputValue={studentTagInput}
                            onInputChange={setStudentTagInput}
                            onKeyDown={handleStudentTagKeyDown}
                            onRemove={(tag) => setStudentSkillTags((current) => current.filter((item) => item !== tag))}
                            placeholder={studentSkillTags.length >= MAX_SKILL_TAGS ? "标签数量已达上限" : "输入技能后按 Enter"}
                            tags={studentSkillTags}
                          />

                          <div>
                            <label className={fieldLabelClass}>简短自我介绍</label>
                            <textarea
                              rows={4}
                              value={studentForm.selfIntro}
                              onChange={(event) => setStudentForm((current) => ({ ...current, selfIntro: event.target.value }))}
                              className={joinClasses(inputClass, "min-h-[132px] resize-none focus:ring-2 focus:ring-indigo-500/50")}
                              placeholder="简单介绍一下你的目标、项目经历或当前正在补强的方向。"
                            />
                          </div>
                        </div>
                      </motion.div>
                    ) : null}

                    {selectedRole === "MENTOR" ? (
                      <motion.div
                        key="mentor-form"
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -20 }}
                        transition={{ duration: 0.25 }}
                        className="grid gap-5 xl:grid-cols-[minmax(0,0.92fr)_minmax(0,1.08fr)] xl:items-stretch"
                      >
                        <div className={joinClasses(formSectionClass, "flex h-full flex-col gap-5")}>
                          <div>
                            <label className={fieldLabelClass}>真实姓名</label>
                            <input
                              type="text"
                              required
                              value={mentorForm.realName}
                              onChange={(event) => setMentorForm((current) => ({ ...current, realName: event.target.value }))}
                              className={joinClasses(inputClass, "focus:ring-2 focus:ring-emerald-500/50")}
                              placeholder="请填写真实姓名"
                            />
                          </div>

                          <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-1">
                            <div>
                              <label className={fieldLabelClass}>就职公司 / 机构</label>
                              <div className="relative">
                                <Building size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                                <input
                                  type="text"
                                  required
                                  value={mentorForm.company}
                                  onChange={(event) => setMentorForm((current) => ({ ...current, company: event.target.value }))}
                                  className={joinClasses(inputClass, "pl-10 focus:ring-2 focus:ring-emerald-500/50")}
                                  placeholder="例如：某某公司 / 某某高校"
                                />
                              </div>
                            </div>
                            <div>
                              <label className={fieldLabelClass}>当前职级 / Title</label>
                              <div className="relative">
                                <Award size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                                <input
                                  type="text"
                                  required
                                  value={mentorForm.title}
                                  onChange={(event) => setMentorForm((current) => ({ ...current, title: event.target.value }))}
                                  className={joinClasses(inputClass, "pl-10 focus:ring-2 focus:ring-emerald-500/50")}
                                  placeholder="例如：高级前端工程师 / 副教授"
                                />
                              </div>
                            </div>
                          </div>
                        </div>

                        <div className={joinClasses(formSectionClass, "flex h-full flex-col")}>
                          <label className={fieldLabelClass}>{demoCertificationBypassEnabled ? "认证资料（演示模式可跳过）" : "上传认证资料"}</label>
                          <div
                            className={joinClasses(
                              "group mt-1 flex min-h-[18rem] flex-1 cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed p-7 text-center transition-colors",
                              mentorSelectedFile ? "border-emerald-500 bg-emerald-50/70" : "border-emerald-200 bg-slate-50 hover:bg-slate-100",
                            )}
                            onClick={() => {
                              if (!submitting) {
                                mentorFileInputRef.current?.click();
                              }
                            }}
                          >
                            {mentorSelectedFile ? (
                              <>
                                <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
                                  <Check size={24} />
                                </div>
                                <p className="text-base font-bold text-emerald-700">认证资料已选中</p>
                                <p className="mt-1 text-sm text-emerald-600/80">{mentorSelectedFile.name}</p>
                                <p className="mt-2 text-xs text-emerald-700/70">{formatFileSize(mentorSelectedFile.size)}</p>
                              </>
                            ) : (
                              <>
                                <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-white text-emerald-500 shadow-sm transition-transform group-hover:scale-110">
                                  <UploadCloud size={24} />
                                </div>
                                <p className="mb-1 text-base font-bold text-slate-700">{demoCertificationBypassEnabled ? "可选上传认证文件" : "点击选择认证文件"}</p>
                                <p className="text-sm text-slate-500">
                                  {demoCertificationBypassEnabled
                                    ? "演示模式下可以直接跳过；如需展示材料页，也可继续上传 JPG、PNG、PDF。"
                                    : "支持 JPG、PNG、PDF，请上传清晰可辨认的证明材料。"}
                                </p>
                              </>
                            )}
                          </div>
                          <input
                            ref={mentorFileInputRef}
                            type="file"
                            accept=".jpg,.jpeg,.png,.pdf,image/jpeg,image/png,application/pdf"
                            className="hidden"
                            onChange={(event) => handleCertificationFileChange("MENTOR", event)}
                          />
                        </div>
                      </motion.div>
                    ) : null}

                    {selectedRole === "ENTERPRISE" ? (
                      <motion.div
                        key="enterprise-form"
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -20 }}
                        transition={{ duration: 0.25 }}
                        className="grid gap-5 xl:grid-cols-[minmax(0,0.92fr)_minmax(0,1.08fr)] xl:items-stretch"
                      >
                        <div className={joinClasses(formSectionClass, "flex h-full flex-col gap-5")}>
                          <div>
                            <label className={fieldLabelClass}>真实姓名</label>
                            <input
                              type="text"
                              required
                              value={enterpriseForm.realName}
                              onChange={(event) => setEnterpriseForm((current) => ({ ...current, realName: event.target.value }))}
                              className={joinClasses(inputClass, "focus:ring-2 focus:ring-amber-500/50")}
                              placeholder="请填写联系人真实姓名"
                            />
                          </div>

                          <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-1">
                            <div>
                              <label className={fieldLabelClass}>企业名称 / 所在机构</label>
                              <div className="relative">
                                <Building size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                                <input
                                  type="text"
                                  required
                                  value={enterpriseForm.company}
                                  onChange={(event) => setEnterpriseForm((current) => ({ ...current, company: event.target.value }))}
                                  className={joinClasses(inputClass, "pl-10 focus:ring-2 focus:ring-amber-500/50")}
                                  placeholder="例如：某某科技有限公司"
                                />
                              </div>
                            </div>
                            <div>
                              <label className={fieldLabelClass}>当前岗位 / Title</label>
                              <div className="relative">
                                <Award size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                                <input
                                  type="text"
                                  required
                                  value={enterpriseForm.title}
                                  onChange={(event) => setEnterpriseForm((current) => ({ ...current, title: event.target.value }))}
                                  className={joinClasses(inputClass, "pl-10 focus:ring-2 focus:ring-amber-500/50")}
                                  placeholder="例如：招聘负责人 / 校招运营"
                                />
                              </div>
                            </div>
                          </div>
                        </div>

                        <div className={joinClasses(formSectionClass, "flex h-full flex-col")}>
                          <label className={fieldLabelClass}>{demoCertificationBypassEnabled ? "认证资料（演示模式可跳过）" : "上传认证资料"}</label>
                          <div
                            className={joinClasses(
                              "group mt-1 flex min-h-[18rem] flex-1 cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed p-7 text-center transition-colors",
                              enterpriseSelectedFile ? "border-amber-500 bg-amber-50/70" : "border-amber-200 bg-slate-50 hover:bg-slate-100",
                            )}
                            onClick={() => {
                              if (!submitting) {
                                enterpriseFileInputRef.current?.click();
                              }
                            }}
                          >
                            {enterpriseSelectedFile ? (
                              <>
                                <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-amber-100 text-amber-600">
                                  <Check size={24} />
                                </div>
                                <p className="text-base font-bold text-amber-700">认证资料已选中</p>
                                <p className="mt-1 text-sm text-amber-600/80">{enterpriseSelectedFile.name}</p>
                                <p className="mt-2 text-xs text-amber-700/70">{formatFileSize(enterpriseSelectedFile.size)}</p>
                              </>
                            ) : (
                              <>
                                <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-white text-amber-500 shadow-sm transition-transform group-hover:scale-110">
                                  <UploadCloud size={24} />
                                </div>
                                <p className="mb-1 text-base font-bold text-slate-700">{demoCertificationBypassEnabled ? "可选上传认证文件" : "点击选择认证文件"}</p>
                                <p className="text-sm text-slate-500">
                                  {demoCertificationBypassEnabled
                                    ? "演示模式下可以直接跳过；如需展示材料页，也可继续上传 JPG、PNG、PDF。"
                                    : "支持 JPG、PNG、PDF，请上传清晰可辨认的证明材料。"}
                                </p>
                              </>
                            )}
                          </div>
                          <input
                            ref={enterpriseFileInputRef}
                            type="file"
                            accept=".jpg,.jpeg,.png,.pdf,image/jpeg,image/png,application/pdf"
                            className="hidden"
                            onChange={(event) => handleCertificationFileChange("ENTERPRISE", event)}
                          />
                        </div>
                      </motion.div>
                    ) : null}
                  </AnimatePresence>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="border-t border-slate-100 bg-white px-6 py-4 lg:px-10 lg:py-4">
          <div className="mx-auto flex w-full flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
            <div className="min-w-0 flex-1">
              <p className="text-sm leading-6 text-slate-500">
                {demoCertificationBypassEnabled
                  ? "确认资料无误并勾选协议后即可完成注册；导师与企业账号会直接进入可演示状态。"
                  : "确认资料无误并勾选协议后即可完成注册。"}
              </p>
              <div className={joinClasses("mt-2 flex items-start gap-3 text-sm leading-6 transition-colors", agreementsAccepted ? "text-emerald-800" : "text-slate-600")}>
                <input
                  type="checkbox"
                  checked={agreementsAccepted}
                  onChange={(event) => setAgreementsAccepted(event.target.checked)}
                  className="mt-1 h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-slate-400"
                />
                <span>
                  我已阅读并同意
                  {" "}
                  <button
                    type="button"
                    onClick={() => openAgreementPanel("terms")}
                    className="font-semibold text-slate-900 underline decoration-slate-300 underline-offset-4 transition-colors hover:text-indigo-600"
                  >
                    《用户协议》
                  </button>
                  {" "}
                  与
                  {" "}
                  <button
                    type="button"
                    onClick={() => openAgreementPanel("privacy")}
                    className="font-semibold text-slate-900 underline decoration-slate-300 underline-offset-4 transition-colors hover:text-indigo-600"
                  >
                    《隐私条款》《AI 服务声明》
                  </button>
                </span>
              </div>
            </div>
            <button
              type="submit"
              disabled={submitDisabled}
              className={joinClasses("group inline-flex w-full items-center justify-center rounded-xl px-6 py-3.5 text-base font-bold text-white shadow-lg transition-all disabled:cursor-not-allowed disabled:opacity-70 sm:w-auto", submitButtonClass)}
            >
              {busy ? "提交中..." : submitButtonLabel}
              <ArrowRight size={18} className="ml-2 transition-transform group-hover:translate-x-1" />
            </button>
          </div>
        </div>
      </form>

      </div>
      {agreementModalOverlay}
    </>
  );
}
