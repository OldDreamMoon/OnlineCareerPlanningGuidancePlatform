import { apiRequest } from "./apiClient";
import { formatDateTime } from "./formatters";
import type { ResumeHistoryDetail } from "./resumeReportPdfDocument";

function waitForBrowserPaint() {
  if (typeof window === "undefined") {
    return Promise.resolve();
  }

  return new Promise<void>((resolve) => {
    window.requestAnimationFrame(() => resolve());
  });
}

function formatInputMode(value: ResumeHistoryDetail["inputMode"]) {
  return value === "pdf" ? "PDF 上传" : "文本输入";
}

function formatLatency(value?: number | null) {
  if (typeof value !== "number" || !Number.isFinite(value) || value <= 0) {
    return "—";
  }

  return `${(value / 1000).toFixed(2)} 秒`;
}

function formatCreatedAt(value?: string | number | null) {
  return formatDateTime(value);
}

function buildPdfFileName(detail: ResumeHistoryDetail) {
  return `resume-report-${detail.recordId}.pdf`;
}

function toList(items: string[] | null | undefined) {
  return (items ?? []).map((item) => item.trim()).filter(Boolean);
}

function buildListSection(title: string, items: string[]) {
  const validItems = toList(items);
  if (!validItems.length) {
    return `## ${title}\n\n- 暂无内容`;
  }

  return `## ${title}\n\n${validItems.map((item, index) => `${index + 1}. ${item}`).join("\n")}`;
}

function buildStructureSection(items: ResumeHistoryDetail["structureItems"]) {
  const validItems = (items ?? []).filter((item) => item && item.label);
  if (!validItems.length) {
    return "## 结构完整度\n\n- 暂无内容";
  }

  return [
    "## 结构完整度",
    "",
    ...validItems.map((item, index) => `${index + 1}. ${item.label}：${item.score}/5；${item.tip?.trim() || "建议继续补强岗位证据。"}`),
  ].join("\n");
}

function buildRewriteSection(items: ResumeHistoryDetail["rewriteItems"]) {
  const validItems = (items ?? []).filter((item) => item && (item.title || item.afterText));
  if (!validItems.length) {
    return "## 部分修改建议\n\n- 暂无内容";
  }

  return [
    "## 部分修改建议",
    "",
    ...validItems.flatMap((item, index) => [
      `### 建议 ${String(index + 1).padStart(2, "0")}：${item.title?.trim() || "表达优化建议"}`,
      `- 问题定位：${item.problem?.trim() || "暂无"}`,
      `- 原始表达：${item.beforeText?.trim() || "暂无"}`,
      `- 建议改写：${item.afterText?.trim() || "暂无"}`,
      "",
    ]),
  ]
    .join("\n")
    .trim();
}

function buildResumeReportMarkdown(detail: ResumeHistoryDetail, exporterName?: string | null) {
  return [
    "# AI 简历复盘轻量报告",
    "",
    "大学生就业规划指导平台",
    "",
    "## 基础信息",
    "",
    `- 导出用户：${exporterName?.trim() || "当前用户"}`,
    `- 记录编号：${detail.recordId}`,
    `- 目标岗位：${detail.targetRole?.trim() || "未填写"}`,
    `- 应用场景：${detail.targetContext?.trim() || "未填写"}`,
    `- 输入模式：${formatInputMode(detail.inputMode)}`,
    `- 生成时间：${formatCreatedAt(detail.createdAt)}`,
    `- 推荐度：${detail.scoreLabel?.trim() || "—"}`,
    `- 消耗积分：${detail.pointsConsumed ?? 0}`,
    `- 生成用时：${formatLatency(detail.aiMeta?.latencyMs)}`,
    "",
    "## 复盘摘要",
    "",
    detail.summary?.trim() || "暂无摘要内容",
    "",
    buildListSection("简历亮点", detail.strengths ?? []),
    "",
    buildListSection("潜在风险", detail.risks ?? []),
    "",
    buildListSection("优化建议", detail.suggestions ?? []),
    "",
    buildStructureSection(detail.structureItems),
    "",
    buildRewriteSection(detail.rewriteItems),
    "",
    "## 岗位要求",
    "",
    detail.jobDescription?.trim() || "本次未提供岗位要求。",
    "",
    `## ${detail.inputMode === "pdf" ? "简历来源" : "简历原文"}`,
    "",
    detail.inputMode === "pdf"
      ? `已上传 PDF 文件：${detail.pdfFileName?.trim() || "未返回文件名"}`
      : detail.resumeText?.trim() || "本次未返回简历原文。",
    "",
  ].join("\n");
}

type MarkdownLine =
  | { kind: "h1"; text: string }
  | { kind: "h2"; text: string }
  | { kind: "h3"; text: string }
  | { kind: "bullet"; text: string }
  | { kind: "number"; text: string }
  | { kind: "paragraph"; text: string }
  | { kind: "blank"; text: "" };

function parseMarkdownLines(markdown: string): MarkdownLine[] {
  return markdown.split(/\r?\n/).map((line) => {
    const trimmed = line.trim();
    if (!trimmed) {
      return { kind: "blank", text: "" };
    }
    if (trimmed.startsWith("# ")) {
      return { kind: "h1", text: trimmed.slice(2).trim() };
    }
    if (trimmed.startsWith("## ")) {
      return { kind: "h2", text: trimmed.slice(3).trim() };
    }
    if (trimmed.startsWith("### ")) {
      return { kind: "h3", text: trimmed.slice(4).trim() };
    }
    if (trimmed.startsWith("- ")) {
      return { kind: "bullet", text: trimmed.slice(2).trim() };
    }
    if (/^\d+\.\s+/.test(trimmed)) {
      return { kind: "number", text: trimmed.replace(/^\d+\.\s+/, "").trim() };
    }
    return { kind: "paragraph", text: trimmed };
  });
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function renderMarkdownHtml(markdown: string) {
  const headerBlocks: string[] = [];
  const sectionBlocks: string[] = [];
  let listBuffer: { ordered: boolean; items: string[] } | null = null;
  let brandAssigned = false;
  let currentSectionTitle: string | null = null;
  let currentSectionContent: string[] = [];

  const pushBlock = (html: string) => {
    if (currentSectionTitle) {
      currentSectionContent.push(html);
      return;
    }
    headerBlocks.push(html);
  };

  const flushList = () => {
    if (!listBuffer) {
      return;
    }

    const tag = listBuffer.ordered ? "ol" : "ul";
    const listClass = listBuffer.ordered ? "report-list report-list-ordered" : "report-list report-list-unordered";
    pushBlock(
      `<${tag} class="${listClass}">${listBuffer.items.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</${tag}>`,
    );
    listBuffer = null;
  };

  const flushSection = () => {
    flushList();

    if (!currentSectionTitle) {
      return;
    }

    const sectionClass =
      currentSectionTitle === "基础信息" ? "report-section report-section-meta" : "report-section";
    sectionBlocks.push(
      `<section class="${sectionClass}"><h2 class="section-title">${escapeHtml(currentSectionTitle)}</h2><div class="section-body">${currentSectionContent.join("\n")}</div></section>`,
    );
    currentSectionTitle = null;
    currentSectionContent = [];
  };

  for (const line of parseMarkdownLines(markdown)) {
    if (line.kind === "blank") {
      flushList();
      continue;
    }

    if (line.kind === "bullet" || line.kind === "number") {
      const ordered = line.kind === "number";
      if (!listBuffer || listBuffer.ordered !== ordered) {
        flushList();
        listBuffer = {
          ordered,
          items: [],
        };
      }
      listBuffer.items.push(line.text);
      continue;
    }

    flushList();

    if (line.kind === "paragraph" && !brandAssigned && line.text === "大学生就业规划指导平台") {
      headerBlocks.push(`<p class="brand">${escapeHtml(line.text)}</p>`);
      brandAssigned = true;
      continue;
    }

    if (line.kind === "h1") {
      headerBlocks.push(`<h1 class="report-title">${escapeHtml(line.text)}</h1>`);
      continue;
    }

    if (line.kind === "h2") {
      flushSection();
      currentSectionTitle = line.text;
      continue;
    }

    if (line.kind === "h3") {
      pushBlock(`<h3 class="subsection-title">${escapeHtml(line.text)}</h3>`);
      continue;
    }

    pushBlock(`<p class="body-text">${escapeHtml(line.text)}</p>`);
  }

  flushSection();

  const headerHtml = headerBlocks.length ? `<header class="report-header">${headerBlocks.join("\n")}</header>` : "";
  return `${headerHtml}${sectionBlocks.join("\n")}`;
}

function buildWatermarkText(detail: ResumeHistoryDetail, exporterName?: string | null) {
  return `大学生就业规划指导平台 · ${exporterName?.trim() || "当前用户"} · 记录 #${detail.recordId}`;
}

function buildWatermarkHtml(detail: ResumeHistoryDetail, exporterName?: string | null) {
  const watermarkText = escapeHtml(buildWatermarkText(detail, exporterName));
  const placements = [
    "top: 10%; left: 8%;",
    "top: 30%; left: 54%;",
    "top: 52%; left: 12%;",
    "top: 72%; left: 50%;",
  ];

  return placements
    .map(
      (style, index) =>
        `<span class="watermark-item watermark-item-${index + 1}" style="${style}">${watermarkText}</span>`,
    )
    .join("");
}

function buildPrintableHtml(detail: ResumeHistoryDetail, exporterName?: string | null) {
  const fileName = buildPdfFileName(detail);
  const contentHtml = renderMarkdownHtml(buildResumeReportMarkdown(detail, exporterName));
  const watermarkHtml = buildWatermarkHtml(detail, exporterName);

  return `<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>${escapeHtml(fileName)}</title>
    <style>
      :root {
        color-scheme: light;
      }

      @page {
        size: A4;
        margin: 14mm 16mm;
      }

      * {
        box-sizing: border-box;
      }

      html,
      body {
        margin: 0;
        padding: 0;
        background: #ffffff;
        color: #1f2937;
        font-family: "PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif;
      }

      body {
        position: relative;
        font-size: 12px;
        line-height: 1.7;
      }

      main {
        position: relative;
        z-index: 1;
        width: 100%;
      }

      .watermark-layer {
        position: fixed;
        inset: 0;
        z-index: 0;
        pointer-events: none;
        overflow: hidden;
      }

      .watermark-item {
        position: absolute;
        color: rgba(37, 99, 235, 0.08);
        font-size: 17px;
        font-weight: 600;
        letter-spacing: 0.08em;
        white-space: nowrap;
        transform: rotate(-24deg);
        transform-origin: center;
      }

      h1,
      h2,
      h3,
      p,
      ul,
      ol {
        margin: 0;
      }

      .report-header {
        margin-bottom: 18px;
        padding-bottom: 14px;
        border-bottom: 1px solid #dbe3ef;
      }

      .report-title {
        font-size: 24px;
        line-height: 1.3;
        color: #0f172a;
        letter-spacing: 0.02em;
      }

      .brand {
        margin-top: 8px;
        font-size: 11px;
        color: #64748b;
      }

      .report-section {
        page-break-inside: avoid;
      }

      .report-section + .report-section {
        margin-top: 18px;
        padding-top: 16px;
        border-top: 1px solid #dbe3ef;
      }

      .section-title {
        margin-bottom: 10px;
        padding-left: 10px;
        border-left: 3px solid #60a5fa;
        font-size: 16px;
        line-height: 1.4;
        color: #1d4ed8;
        page-break-after: avoid;
      }

      .section-body {
        padding-left: 13px;
      }

      .subsection-title {
        margin-top: 12px;
        margin-bottom: 6px;
        padding-left: 8px;
        border-left: 2px solid #cbd5e1;
        font-size: 13px;
        line-height: 1.5;
        color: #0f172a;
        page-break-after: avoid;
      }

      .body-text {
        margin-top: 6px;
        color: #334155;
      }

      .report-list {
        margin-top: 6px;
        padding-left: 20px;
      }

      .report-list li {
        margin-top: 0;
        padding: 5px 0;
        color: #334155;
      }

      .report-list li + li {
        border-top: 1px dashed #e2e8f0;
      }

      .report-list li::marker {
        color: #3b82f6;
      }

      .report-section-meta .report-list li::marker {
        color: #0f172a;
      }

      .body-text,
      .report-list li {
        white-space: pre-wrap;
        word-break: break-word;
        overflow-wrap: anywhere;
      }

      @media print {
        html,
        body {
          -webkit-print-color-adjust: exact;
          print-color-adjust: exact;
        }
      }
    </style>
  </head>
  <body>
    <div class="watermark-layer" aria-hidden="true">${watermarkHtml}</div>
    <main>${contentHtml}</main>
  </body>
</html>`;
}

async function openPrintPreview(html: string) {
  if (typeof document === "undefined" || typeof window === "undefined") {
    throw new Error("当前环境暂不支持导出报告。");
  }

  await waitForBrowserPaint();

  return new Promise<void>((resolve, reject) => {
    const iframe = document.createElement("iframe");
    iframe.setAttribute("aria-hidden", "true");
    iframe.style.position = "fixed";
    iframe.style.right = "0";
    iframe.style.bottom = "0";
    iframe.style.width = "0";
    iframe.style.height = "0";
    iframe.style.border = "0";
    iframe.style.opacity = "0";
    iframe.style.pointerEvents = "none";

    let settled = false;
    let cleanupTimer = 0;
    let loadTimer = 0;

    const cleanup = () => {
      if (cleanupTimer) {
        window.clearTimeout(cleanupTimer);
      }
      if (loadTimer) {
        window.clearTimeout(loadTimer);
      }
      iframe.onload = null;
      iframe.remove();
    };

    const fail = (error: unknown) => {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      reject(error instanceof Error ? error : new Error("打印预览打开失败，请重试。"));
    };

    const succeed = () => {
      if (settled) {
        return;
      }
      settled = true;
      resolve();
    };

    loadTimer = window.setTimeout(() => {
      fail(new Error("打印预览加载超时，请重试。"));
    }, 10_000);

    iframe.onload = () => {
      if (loadTimer) {
        window.clearTimeout(loadTimer);
        loadTimer = 0;
      }

      const printWindow = iframe.contentWindow;
      const printDocument = printWindow?.document;

      if (!printWindow || !printDocument) {
        fail(new Error("打印预览打开失败，请重试。"));
        return;
      }

      const triggerPrint = () => {
        try {
          printWindow.addEventListener(
            "afterprint",
            () => {
              cleanup();
            },
            { once: true },
          );
          cleanupTimer = window.setTimeout(() => {
            cleanup();
          }, 60_000);
          printWindow.focus();
          printWindow.print();
          succeed();
        } catch (error) {
          fail(error);
        }
      };

      const fontReady = printDocument.fonts?.ready;
      if (fontReady) {
        void fontReady.then(
          () => {
            window.setTimeout(triggerPrint, 80);
          },
          () => {
            window.setTimeout(triggerPrint, 80);
          },
        );
        return;
      }

      window.setTimeout(triggerPrint, 80);
    };

    iframe.srcdoc = html;
    document.body.appendChild(iframe);
  });
}

export async function exportResumeReportAsPdf(
  recordIdOrDetail: number | ResumeHistoryDetail,
  exporterName?: string | null,
) {
  const detail =
    typeof recordIdOrDetail === "number"
      ? await apiRequest<ResumeHistoryDetail>(`/ai/history/resume/${recordIdOrDetail}`)
      : recordIdOrDetail;

  const printableHtml = buildPrintableHtml(detail, exporterName);
  await openPrintPreview(printableHtml);
}
