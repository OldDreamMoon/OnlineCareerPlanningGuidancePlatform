import { buildSkillLayoutGeometry, type SkillLayoutGeometryResult, type SkillLayoutNodeInput } from "./skillLayoutCore";

type SkillLayoutWorkerRequest = {
  requestId: number;
  nodes: SkillLayoutNodeInput[];
  presets?: Record<string, { x: number; y: number }>;
};

type SkillLayoutWorkerResponse = {
  requestId: number;
  layout: SkillLayoutGeometryResult;
};

self.onmessage = (event: MessageEvent<SkillLayoutWorkerRequest>) => {
  const { requestId, nodes, presets } = event.data;
  const layout = buildSkillLayoutGeometry(nodes, presets);
  const response: SkillLayoutWorkerResponse = {
    requestId,
    layout,
  };

  self.postMessage(response);
};

export type { SkillLayoutWorkerRequest, SkillLayoutWorkerResponse };
