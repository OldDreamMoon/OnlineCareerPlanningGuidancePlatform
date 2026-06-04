import {
  Bold,
  Code,
  Edit3,
  Eye,
  Heading2,
  Italic,
  Link2,
  List,
  ListOrdered,
  MessageSquareText,
  Quote,
} from "lucide-react";
import { useRef, useState } from "react";
import CommunityMarkdownRenderer from "./CommunityMarkdownRenderer";
import { joinClasses } from "./communityUtils";

type CommunityMarkdownComposerProps = {
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
  rows?: number;
  submitText?: string;
  onSubmit?: () => void | Promise<void>;
  onCancel?: () => void;
  submitting?: boolean;
  disabled?: boolean;
  className?: string;
  note?: string;
};

type ToolbarItem = {
  label: string;
  icon: typeof Bold;
  action: () => void;
};

export default function CommunityMarkdownComposer({
  value,
  onChange,
  placeholder,
  rows = 7,
  submitText = "提交",
  onSubmit,
  onCancel,
  submitting = false,
  disabled = false,
  className,
  note = "支持标题、列表、引用、加粗、斜体、代码块与链接。",
}: CommunityMarkdownComposerProps) {
  const [mode, setMode] = useState<"edit" | "preview">("edit");
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);

  const updateTextareaSelection = (nextValue: string, selectionStart: number, selectionEnd: number) => {
    onChange(nextValue);
    requestAnimationFrame(() => {
      textareaRef.current?.focus();
      textareaRef.current?.setSelectionRange(selectionStart, selectionEnd);
    });
  };

  const surroundSelection = (prefix: string, suffix = prefix, placeholderText = "内容") => {
    const textarea = textareaRef.current;
    if (!textarea) {
      return;
    }

    const selectionStart = textarea.selectionStart;
    const selectionEnd = textarea.selectionEnd;
    const selectedText = value.slice(selectionStart, selectionEnd) || placeholderText;
    const nextValue = `${value.slice(0, selectionStart)}${prefix}${selectedText}${suffix}${value.slice(selectionEnd)}`;
    const nextSelectionStart = selectionStart + prefix.length;
    const nextSelectionEnd = nextSelectionStart + selectedText.length;
    updateTextareaSelection(nextValue, nextSelectionStart, nextSelectionEnd);
  };

  const prependLines = (prefix: string, placeholderText: string) => {
    const textarea = textareaRef.current;
    if (!textarea) {
      return;
    }

    const selectionStart = textarea.selectionStart;
    const selectionEnd = textarea.selectionEnd;
    const selectedText = value.slice(selectionStart, selectionEnd) || placeholderText;
    const prefixedText = selectedText
      .split("\n")
      .map((line) => `${prefix}${line}`)
      .join("\n");
    const nextValue = `${value.slice(0, selectionStart)}${prefixedText}${value.slice(selectionEnd)}`;
    updateTextareaSelection(nextValue, selectionStart, selectionStart + prefixedText.length);
  };

  const insertCodeBlock = () => {
    const textarea = textareaRef.current;
    if (!textarea) {
      return;
    }

    const selectionStart = textarea.selectionStart;
    const selectionEnd = textarea.selectionEnd;
    const selectedText = value.slice(selectionStart, selectionEnd) || "在这里补充代码或结构化示例";
    const nextBlock = `\n\`\`\`\n${selectedText}\n\`\`\`\n`;
    const nextValue = `${value.slice(0, selectionStart)}${nextBlock}${value.slice(selectionEnd)}`;
    updateTextareaSelection(nextValue, selectionStart + 5, selectionStart + 5 + selectedText.length);
  };

  const toolbarItems: ToolbarItem[] = [
    { label: "标题", icon: Heading2, action: () => prependLines("## ", "补一个清晰小标题") },
    { label: "加粗", icon: Bold, action: () => surroundSelection("**", "**", "重点信息") },
    { label: "斜体", icon: Italic, action: () => surroundSelection("*", "*", "强调说明") },
    { label: "引用", icon: Quote, action: () => prependLines("> ", "引用问题背景或关键结论") },
    { label: "无序列表", icon: List, action: () => prependLines("- ", "第一条\n第二条") },
    { label: "有序列表", icon: ListOrdered, action: () => prependLines("1. ", "步骤一\n步骤二") },
    { label: "代码", icon: Code, action: insertCodeBlock },
    { label: "链接", icon: Link2, action: () => surroundSelection("[", "](https://example.com)", "链接标题") },
  ];

  return (
    <div className={joinClasses("overflow-hidden rounded-[1.6rem] border border-slate-200 bg-white shadow-sm", className)}>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 bg-slate-50/80 px-3 py-3">
        <div className="inline-flex rounded-full border border-slate-200 bg-white p-1 shadow-sm">
          <button
            type="button"
            onClick={() => setMode("edit")}
            className={joinClasses(
              "inline-flex items-center gap-1.5 rounded-full px-3.5 py-2 text-sm font-semibold transition-colors",
              mode === "edit" ? "bg-slate-900 text-white" : "text-slate-500 hover:text-slate-800",
            )}
          >
            <Edit3 size={14} />
            编辑
          </button>
          <button
            type="button"
            onClick={() => setMode("preview")}
            className={joinClasses(
              "inline-flex items-center gap-1.5 rounded-full px-3.5 py-2 text-sm font-semibold transition-colors",
              mode === "preview" ? "bg-slate-900 text-white" : "text-slate-500 hover:text-slate-800",
            )}
          >
            <Eye size={14} />
            预览
          </button>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {toolbarItems.map((item) => (
            <button
              key={item.label}
              type="button"
              disabled={disabled || mode !== "edit"}
              onClick={item.action}
              title={item.label}
              className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 transition-colors hover:border-indigo-200 hover:text-indigo-600 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <item.icon size={15} />
            </button>
          ))}
        </div>
      </div>

      {mode === "edit" ? (
        <textarea
          ref={textareaRef}
          rows={rows}
          value={value}
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
          placeholder={placeholder}
          className="min-h-[180px] w-full resize-none bg-white px-5 py-4 text-base leading-8 text-slate-800 outline-none placeholder:text-slate-400"
        />
      ) : (
        <div className="min-h-[180px] bg-slate-50/40 px-5 py-5">
          {value.trim() ? (
            <CommunityMarkdownRenderer content={value} className="space-y-4" />
          ) : (
            <div className="flex h-full min-h-[140px] items-center justify-center rounded-[1.2rem] border border-dashed border-slate-200 bg-white/70 text-base text-slate-400">
              暂无内容预览，先在左侧写一点文字吧。
            </div>
          )}
        </div>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 bg-slate-50 px-4 py-3">
        <div className="inline-flex items-center gap-2 text-sm text-slate-500">
          <MessageSquareText size={13} />
          {note}
        </div>
        <div className="flex items-center gap-2">
          {onCancel ? (
            <button
              type="button"
              onClick={onCancel}
              disabled={submitting}
              className="rounded-full px-4 py-2 text-base font-semibold text-slate-500 transition-colors hover:bg-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
            >
              取消
            </button>
          ) : null}
          {onSubmit ? (
            <button
              type="button"
              onClick={() => void onSubmit()}
              disabled={disabled || submitting || !value.trim()}
              className="rounded-full bg-indigo-600 px-5 py-2.5 text-base font-semibold text-white shadow-sm transition-colors hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400"
            >
              {submitting ? "处理中..." : submitText}
            </button>
          ) : null}
        </div>
      </div>
    </div>
  );
}
