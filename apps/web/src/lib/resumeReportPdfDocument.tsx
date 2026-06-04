export type ResumeHistoryTimeValue = number | string;

export type ResumeStructureItem = {
  label: string;
  score: number;
  tip: string;
};

export type ResumeRewriteItem = {
  id: string;
  title: string;
  problem: string;
  beforeText: string;
  afterText: string;
  isHeuristic: boolean;
};

export type ResumeAiMeta = {
  taskType: string;
  latencyMs: number;
} | null;

export type ResumeModeration = {
  sourceType: string;
  riskLevel: string;
  action: string;
  reasonCode: string;
} | null;

export type ResumeHistoryDetail = {
  recordId: number;
  summary: string;
  strengths: string[];
  risks: string[];
  suggestions: string[];
  scoreLabel: string;
  structureItems: ResumeStructureItem[] | null;
  rewriteItems: ResumeRewriteItem[] | null;
  targetRole: string;
  targetContext: string;
  inputMode: "text" | "pdf";
  jobDescription: string;
  resumeText: string;
  pdfFileName: string | null;
  pointsConsumed: number;
  createdAt: ResumeHistoryTimeValue;
  aiMeta: ResumeAiMeta;
  moderation: ResumeModeration;
};
