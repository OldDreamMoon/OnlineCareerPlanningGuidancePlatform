import type { ReactNode } from "react";

type MarkdownBlock =
  | { type: "heading"; level: number; text: string }
  | { type: "paragraph"; lines: string[] }
  | { type: "unordered-list"; items: string[] }
  | { type: "ordered-list"; items: string[] }
  | { type: "blockquote"; lines: string[] }
  | { type: "code"; language: string; lines: string[] };

type CommunityMarkdownRendererProps = {
  content: string | null | undefined;
  className?: string;
  emptyText?: string;
};

function normalizeHref(rawHref: string) {
  const trimmed = rawHref.trim();

  if (/^(https?:\/\/|mailto:)/i.test(trimmed)) {
    return trimmed;
  }

  if (/^www\./i.test(trimmed)) {
    return `https://${trimmed}`;
  }

  return null;
}

function renderInlineNodes(text: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const pattern = /(\[([^\]]+)\]\(([^)]+)\))|(`([^`]+)`)|(\*\*([^*]+)\*\*)|(\*([^*\n]+)\*)|(_([^_\n]+)_)/g;

  let lastIndex = 0;
  let match: RegExpExecArray | null = pattern.exec(text);
  while (match) {
    if (match.index > lastIndex) {
      nodes.push(text.slice(lastIndex, match.index));
    }

    if (match[1]) {
      const href = normalizeHref(match[3] ?? "");
      const label = match[2] ?? match[3] ?? "";
      if (href) {
        nodes.push(
          <a
            key={`${keyPrefix}-link-${match.index}`}
            href={href}
            target="_blank"
            rel="noreferrer"
            className="font-semibold text-indigo-600 underline decoration-indigo-200 underline-offset-4 hover:text-indigo-700"
          >
            {renderInlineNodes(label, `${keyPrefix}-link-label-${match.index}`)}
          </a>,
        );
      } else {
        nodes.push(label);
      }
    } else if (match[4]) {
      nodes.push(
        <code
          key={`${keyPrefix}-code-${match.index}`}
          className="rounded-md bg-slate-100 px-1.5 py-0.5 text-[0.95em] text-slate-800"
        >
          {match[5]}
        </code>,
      );
    } else if (match[6]) {
      nodes.push(
        <strong key={`${keyPrefix}-bold-${match.index}`} className="font-semibold text-slate-900">
          {renderInlineNodes(match[7] ?? "", `${keyPrefix}-bold-inner-${match.index}`)}
        </strong>,
      );
    } else if (match[8]) {
      nodes.push(
        <em key={`${keyPrefix}-italic-${match.index}`} className="italic text-slate-700">
          {renderInlineNodes(match[9] ?? "", `${keyPrefix}-italic-inner-${match.index}`)}
        </em>,
      );
    } else if (match[10]) {
      nodes.push(
        <em key={`${keyPrefix}-italic-${match.index}`} className="italic text-slate-700">
          {renderInlineNodes(match[11] ?? "", `${keyPrefix}-italic-inner-${match.index}`)}
        </em>,
      );
    }

    lastIndex = pattern.lastIndex;
    match = pattern.exec(text);
  }

  if (lastIndex < text.length) {
    nodes.push(text.slice(lastIndex));
  }

  return nodes;
}

function parseMarkdownBlocks(content: string) {
  const lines = content.replace(/\r\n?/g, "\n").split("\n");
  const blocks: MarkdownBlock[] = [];

  let index = 0;
  while (index < lines.length) {
    const line = lines[index] ?? "";

    if (!line.trim()) {
      index += 1;
      continue;
    }

    if (line.startsWith("```")) {
      const language = line.slice(3).trim();
      const buffer: string[] = [];
      index += 1;

      while (index < lines.length && !lines[index].startsWith("```")) {
        buffer.push(lines[index]);
        index += 1;
      }

      if (index < lines.length && lines[index].startsWith("```")) {
        index += 1;
      }

      blocks.push({ type: "code", language, lines: buffer });
      continue;
    }

    const headingMatch = line.match(/^(#{1,6})\s+(.*)$/);
    if (headingMatch) {
      blocks.push({
        type: "heading",
        level: headingMatch[1].length,
        text: headingMatch[2],
      });
      index += 1;
      continue;
    }

    if (/^>\s?/.test(line)) {
      const buffer: string[] = [];
      while (index < lines.length && /^>\s?/.test(lines[index])) {
        buffer.push(lines[index].replace(/^>\s?/, ""));
        index += 1;
      }
      blocks.push({ type: "blockquote", lines: buffer });
      continue;
    }

    if (/^[-*+]\s+/.test(line)) {
      const buffer: string[] = [];
      while (index < lines.length && /^[-*+]\s+/.test(lines[index])) {
        buffer.push(lines[index].replace(/^[-*+]\s+/, ""));
        index += 1;
      }
      blocks.push({ type: "unordered-list", items: buffer });
      continue;
    }

    if (/^\d+\.\s+/.test(line)) {
      const buffer: string[] = [];
      while (index < lines.length && /^\d+\.\s+/.test(lines[index])) {
        buffer.push(lines[index].replace(/^\d+\.\s+/, ""));
        index += 1;
      }
      blocks.push({ type: "ordered-list", items: buffer });
      continue;
    }

    const buffer: string[] = [];
    while (
      index < lines.length
      && lines[index].trim()
      && !lines[index].startsWith("```")
      && !/^(#{1,6})\s+/.test(lines[index])
      && !/^>\s?/.test(lines[index])
      && !/^[-*+]\s+/.test(lines[index])
      && !/^\d+\.\s+/.test(lines[index])
    ) {
      buffer.push(lines[index]);
      index += 1;
    }
    blocks.push({ type: "paragraph", lines: buffer });
  }

  return blocks;
}

export default function CommunityMarkdownRenderer({
  content,
  className,
  emptyText = "暂无正文内容。",
}: CommunityMarkdownRendererProps) {
  const normalizedContent = content?.trim() ?? "";

  if (!normalizedContent) {
    return <div className="text-base italic text-slate-400">{emptyText}</div>;
  }

  const blocks = parseMarkdownBlocks(normalizedContent);

  return (
    <div className={className ? `text-[15px] leading-8 text-slate-700 ${className}` : "text-[15px] leading-8 text-slate-700"}>
      {blocks.map((block, index) => {
        if (block.type === "heading") {
          const classMap: Record<number, string> = {
            1: "text-3xl font-black tracking-tight text-slate-900",
            2: "text-2xl font-bold tracking-tight text-slate-900",
            3: "text-xl font-bold text-slate-900",
            4: "text-lg font-bold text-slate-900",
            5: "text-base font-semibold text-slate-900",
            6: "text-sm font-semibold text-slate-500",
          };
          const headingContent = renderInlineNodes(block.text, `heading-${index}`);
          const headingClassName = classMap[block.level] ?? classMap[6];

          switch (Math.min(Math.max(block.level, 1), 6)) {
            case 1:
              return <h1 key={`block-${index}`} className={headingClassName}>{headingContent}</h1>;
            case 2:
              return <h2 key={`block-${index}`} className={headingClassName}>{headingContent}</h2>;
            case 3:
              return <h3 key={`block-${index}`} className={headingClassName}>{headingContent}</h3>;
            case 4:
              return <h4 key={`block-${index}`} className={headingClassName}>{headingContent}</h4>;
            case 5:
              return <h5 key={`block-${index}`} className={headingClassName}>{headingContent}</h5>;
            case 6:
            default:
              return <h6 key={`block-${index}`} className={headingClassName}>{headingContent}</h6>;
          }
        }

        if (block.type === "paragraph") {
          return (
            <p key={`block-${index}`} className="text-[15px] leading-8 text-slate-700">
              {renderInlineNodes(block.lines.join(" "), `paragraph-${index}`)}
            </p>
          );
        }

        if (block.type === "unordered-list") {
          return (
            <ul key={`block-${index}`} className="list-disc space-y-2 pl-6 text-[15px] leading-8 text-slate-700 marker:text-indigo-400">
              {block.items.map((item, itemIndex) => (
                <li key={`unordered-${index}-${itemIndex}`}>
                  {renderInlineNodes(item, `unordered-${index}-${itemIndex}`)}
                </li>
              ))}
            </ul>
          );
        }

        if (block.type === "ordered-list") {
          return (
            <ol key={`block-${index}`} className="list-decimal space-y-2 pl-6 text-[15px] leading-8 text-slate-700 marker:text-indigo-500">
              {block.items.map((item, itemIndex) => (
                <li key={`ordered-${index}-${itemIndex}`}>
                  {renderInlineNodes(item, `ordered-${index}-${itemIndex}`)}
                </li>
              ))}
            </ol>
          );
        }

        if (block.type === "blockquote") {
          return (
            <blockquote
              key={`block-${index}`}
              className="rounded-r-[1.4rem] border-l-4 border-indigo-200 bg-indigo-50/70 px-5 py-4 text-[15px] leading-8 text-slate-700"
            >
              {block.lines.map((line, lineIndex) => (
                <p key={`blockquote-${index}-${lineIndex}`}>
                  {renderInlineNodes(line, `blockquote-${index}-${lineIndex}`)}
                </p>
              ))}
            </blockquote>
          );
        }

        return (
          <pre
            key={`block-${index}`}
            className="overflow-x-auto rounded-[1.4rem] border border-slate-200 bg-slate-950 px-5 py-4 text-sm leading-7 text-slate-100"
          >
            <code className={block.language ? `language-${block.language}` : undefined}>
              {block.lines.join("\n")}
            </code>
          </pre>
        );
      })}
    </div>
  );
}
