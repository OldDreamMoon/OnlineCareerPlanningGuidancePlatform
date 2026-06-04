import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Alert,
  Button,
  Form,
  Input,
  InputNumber,
  Select,
  Typography,
  message,
} from "antd";
import {
  BookOpen,
  ChevronDown,
  ChevronRight,
  ExternalLink,
  FileText,
  FolderTree,
  GitBranch,
  Link2,
  PencilLine,
  Plus,
  RefreshCcw,
  Trash2,
  Video,
  Wrench,
} from "lucide-react";
import { useLatestRequest } from "../hooks/useLatestRequest";
import { useAdminStaleCache } from "../hooks/useAdminStaleCache";
import { ApiClientError, apiRequest, isAbortError } from "../lib/apiClient";
import { formatCount } from "../lib/formatters";
import {
  AdminDetailPlaceholder,
  AdminMiniStat,
  AdminPageFrame,
  AdminPageHeader,
  AdminSurfaceCard,
  joinAdminClassNames,
} from "../components/admin/AdminOpsPrimitives";

const { Search, TextArea } = Input;
const { Paragraph } = Typography;
const AdminSkillsOperationsOverlays = lazy(() => import("../components/admin/overlays/AdminSkillsOperationsOverlays"));

const RESOURCE_TYPE_OPTIONS = [
  { label: "文档", value: "doc" },
  { label: "文章", value: "article" },
  { label: "视频", value: "video" },
  { label: "工具", value: "tool" },
];

const RELATION_TYPE_OPTIONS = [
  { label: "进阶跳转", value: "ADVANCE_TO" },
  { label: "分支桥接", value: "BRIDGE" },
  { label: "并行关联", value: "CO_LEARN" },
] as const;

const NODE_FILTER_ITEMS = [
  { value: "all", label: "全部节点" },
  { value: "missing-resource", label: "缺资源" },
  { value: "with-relations", label: "有关联" },
  { value: "root", label: "根节点" },
  { value: "leaf", label: "叶子节点" },
] as const;

type TimeValue = number | string | null;
type NodeFilterValue = (typeof NODE_FILTER_ITEMS)[number]["value"] | "current-related";

type SkillOpsOverviewPayload = {
  totalNodeCount: number;
  rootNodeCount: number;
  leafNodeCount: number;
  relationCount: number;
  totalResourceCount: number;
  nodesWithoutResourceCount: number;
};

type SkillNodeRecord = {
  nodeCode: string;
  label: string;
  description: string | null;
  parentCode: string | null;
  parentLabel: string | null;
  sortOrder: number;
  childCount: number;
  resourceCount: number;
  outboundRelationCount: number;
  inboundRelationCount: number;
  updatedAt: TimeValue;
};

type SkillResourceRecord = {
  resourceCode: string;
  nodeCode: string;
  nodeLabel: string;
  resourceType: string;
  title: string;
  sourceLabel: string;
  durationLabel: string;
  linkUrl: string;
  sortOrder: number;
  updatedAt: TimeValue;
};

type SkillRelationRecord = {
  sourceNodeCode: string;
  targetNodeCode: string;
  relationType: string;
  label: string | null;
  sortOrder: number;
};

type SkillNodeListPayload = {
  records: SkillNodeRecord[];
};

type SkillResourceListPayload = {
  records: SkillResourceRecord[];
};

type SkillPreviewPayload = {
  relations: SkillRelationRecord[];
};

type SkillOpsCachePayload = {
  overview: SkillOpsOverviewPayload | null;
  nodes: SkillNodeRecord[];
  resources: SkillResourceRecord[];
  relations: SkillRelationRecord[];
  selectedNodeCode: string | null;
};

type SkillNodeFormValues = {
  nodeCode: string;
  label: string;
  description?: string;
  sortOrder?: number | null;
};

type SkillResourceFormValues = {
  resourceCode: string;
  resourceType: string;
  title: string;
  sourceLabel: string;
  durationLabel: string;
  linkUrl: string;
  sortOrder?: number | null;
};

type SkillRelationFormValues = {
  relatedNodeCode: string;
  relationType: string;
  label: string;
  sortOrder?: number | null;
};

type NodeModalState =
  | { open: false; mode: "create-root" | "create-child" | "edit"; target: null; parentContext: null }
  | { open: true; mode: "create-root" | "create-child" | "edit"; target: SkillNodeRecord | null; parentContext: SkillNodeRecord | null };

type ResourceModalState =
  | { open: false; mode: "create" | "edit"; target: null }
  | { open: true; mode: "create" | "edit"; target: SkillResourceRecord | null };

type RelationEditorState =
  | { open: false; mode: "create" | "edit"; direction: "outbound" | "inbound"; relation: null }
  | { open: true; mode: "create" | "edit"; direction: "outbound" | "inbound"; relation: SkillRelationRecord | null };

type RelatedNodeView = {
  relation: SkillRelationRecord;
  relatedNodeCode: string;
  relatedNode: SkillNodeRecord | null;
  direction: "outbound" | "inbound";
};

function getResourceTypeLabel(resourceType: string) {
  return RESOURCE_TYPE_OPTIONS.find((option) => option.value === resourceType)?.label ?? resourceType;
}

function getRelationTypeLabel(relationType: string) {
  return RELATION_TYPE_OPTIONS.find((option) => option.value === relationType)?.label ?? relationType;
}

function getResourceTypePillClass(resourceType: string) {
  if (resourceType === "video") {
    return "border-fuchsia-200 bg-fuchsia-50 text-fuchsia-700";
  }
  if (resourceType === "article") {
    return "border-amber-200 bg-amber-50 text-amber-700";
  }
  if (resourceType === "tool") {
    return "border-emerald-200 bg-emerald-50 text-emerald-700";
  }
  return "border-sky-200 bg-sky-50 text-sky-700";
}

function ResourceTypePill({
  resourceType,
  count,
}: {
  resourceType: string;
  count?: number;
}) {
  return (
    <span
      className={joinAdminClassNames(
        "inline-flex items-center rounded-full border px-2.5 py-1 text-[11px] font-bold",
        getResourceTypePillClass(resourceType),
      )}
    >
      {getResourceTypeLabel(resourceType)}
      {typeof count === "number" ? <span className="ml-1.5 text-[10px]">{formatCount(count)}</span> : null}
    </span>
  );
}

function renderResourceIcon(resourceType: string, size = 20) {
  if (resourceType === "video") {
    return <Video size={size} />;
  }
  if (resourceType === "tool") {
    return <Wrench size={size} />;
  }
  return <FileText size={size} />;
}

function normalizeNodeFormValues(values: SkillNodeFormValues) {
  return {
    nodeCode: values.nodeCode.trim(),
    label: values.label.trim(),
    description: values.description?.trim() || undefined,
    sortOrder: values.sortOrder == null ? undefined : Number(values.sortOrder),
  };
}

function normalizeResourceFormValues(values: SkillResourceFormValues) {
  return {
    resourceCode: values.resourceCode.trim(),
    resourceType: values.resourceType,
    title: values.title.trim(),
    sourceLabel: values.sourceLabel.trim(),
    durationLabel: values.durationLabel.trim(),
    linkUrl: values.linkUrl.trim(),
    sortOrder: values.sortOrder == null ? undefined : Number(values.sortOrder),
  };
}

function normalizeRelationFormValues(values: SkillRelationFormValues) {
  return {
    relatedNodeCode: values.relatedNodeCode.trim(),
    relationType: values.relationType,
    label: values.label.trim(),
    sortOrder: values.sortOrder == null ? undefined : Number(values.sortOrder),
  };
}

function buildSkillsPreviewHref(nodeCode: string) {
  const searchParams = new URLSearchParams({
    nodeCode: nodeCode,
    focusNodeCode: nodeCode,
    previewSource: "admin-skills",
  });
  return `/admin/skills/preview?${searchParams.toString()}`;
}

function buildNodePath(nodeMap: Map<string, SkillNodeRecord>, nodeCode: string | null) {
  if (!nodeCode) {
    return [] as SkillNodeRecord[];
  }

  const path: SkillNodeRecord[] = [];
  const visited = new Set<string>();
  let currentCode: string | null = nodeCode;

  while (currentCode) {
    if (visited.has(currentCode)) {
      break;
    }
    visited.add(currentCode);
    const current = nodeMap.get(currentCode);
    if (!current) {
      break;
    }
    path.unshift(current);
    currentCode = current.parentCode;
  }

  return path;
}

function collectAncestorExpansion(nodeMap: Map<string, SkillNodeRecord>, nodeCode: string, bucket: Set<string>) {
  let currentCode = nodeMap.get(nodeCode)?.parentCode ?? null;
  while (currentCode) {
    bucket.add(currentCode);
    currentCode = nodeMap.get(currentCode)?.parentCode ?? null;
  }
}

function collectDescendantNodeCodes(
  childNodeMap: Map<string | null, SkillNodeRecord[]>,
  nodeCode: string,
  bucket: Set<string>,
) {
  const stack = [...(childNodeMap.get(nodeCode) ?? [])];
  while (stack.length > 0) {
    const current = stack.pop();
    if (!current || bucket.has(current.nodeCode)) {
      continue;
    }
    bucket.add(current.nodeCode);
    const children = childNodeMap.get(current.nodeCode) ?? [];
    children.forEach((child) => stack.push(child));
  }
}

function collapseManualBranch(
  childNodeMap: Map<string | null, SkillNodeRecord[]>,
  bucket: Set<string>,
  nodeCode: string,
) {
  bucket.delete(nodeCode);
  const descendants = new Set<string>();
  collectDescendantNodeCodes(childNodeMap, nodeCode, descendants);
  descendants.forEach((descendantCode) => bucket.delete(descendantCode));
}

function expandManualBranch(
  childNodeMap: Map<string | null, SkillNodeRecord[]>,
  nodeMap: Map<string, SkillNodeRecord>,
  bucket: Set<string>,
  nodeCode: string,
) {
  const parentCode = nodeMap.get(nodeCode)?.parentCode ?? null;
  (childNodeMap.get(parentCode) ?? []).forEach((sibling) => {
    if (sibling.nodeCode !== nodeCode) {
      collapseManualBranch(childNodeMap, bucket, sibling.nodeCode);
    }
  });
  bucket.add(nodeCode);
}

function buildVisibleTreeSequence(
  childNodeMap: Map<string | null, SkillNodeRecord[]>,
  expandedNodeCodeSet: Set<string>,
  visibleTreeNodeCodes: Set<string> | null,
) {
  const sequence: Array<SkillNodeRecord & { depth: number; hasChildren: boolean }> = [];

  const visit = (parentCode: string | null, depth: number) => {
    (childNodeMap.get(parentCode) ?? []).forEach((record) => {
      if (visibleTreeNodeCodes && !visibleTreeNodeCodes.has(record.nodeCode)) {
        return;
      }
      const hasChildren = (childNodeMap.get(record.nodeCode)?.length ?? 0) > 0;
      sequence.push({ ...record, depth, hasChildren });
      if (hasChildren && expandedNodeCodeSet.has(record.nodeCode)) {
        visit(record.nodeCode, depth + 1);
      }
    });
  };

  visit(null, 0);
  return sequence;
}

function buildRelationMutationPath(relation: SkillRelationRecord) {
  return `/admin/skills/relations/${encodeURIComponent(relation.sourceNodeCode)}/${encodeURIComponent(relation.targetNodeCode)}/${encodeURIComponent(relation.relationType)}`;
}

function buildRelationKey(relation: SkillRelationRecord) {
  return `${relation.sourceNodeCode}__${relation.targetNodeCode}__${relation.relationType}`;
}

function getRelationTone(direction: "outbound" | "inbound") {
  return direction === "outbound" ? "text-sky-600 bg-sky-50 border-sky-100" : "text-emerald-600 bg-emerald-50 border-emerald-100";
}

function getRelationTypePillClass(relationType: string) {
  if (relationType === "ADVANCE_TO") {
    return "border-sky-200 bg-sky-50 text-sky-700";
  }
  if (relationType === "CO_LEARN") {
    return "border-emerald-200 bg-emerald-50 text-emerald-700";
  }
  return "border-violet-200 bg-violet-50 text-violet-700";
}

function RelationTypePill({
  relationType,
}: {
  relationType: string;
}) {
  return (
    <span
      className={joinAdminClassNames(
        "inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-bold",
        getRelationTypePillClass(relationType),
      )}
    >
      {getRelationTypeLabel(relationType)}
    </span>
  );
}

const BUTTON_CLASS_NAMES = {
  neutral: "!h-10 !rounded-[14px] !border-slate-200 !bg-white !px-4 !font-semibold !text-slate-600 hover:!border-slate-300 hover:!bg-slate-50 hover:!text-slate-900",
  sky: "!h-10 !rounded-[14px] !border-sky-200 !bg-sky-50 !px-4 !font-semibold !text-sky-700 hover:!border-sky-300 hover:!bg-sky-100 hover:!text-sky-800",
  emerald: "!h-10 !rounded-[14px] !border-emerald-200 !bg-emerald-50 !px-4 !font-semibold !text-emerald-700 hover:!border-emerald-300 hover:!bg-emerald-100 hover:!text-emerald-800",
  amber: "!h-10 !rounded-[14px] !border-amber-200 !bg-amber-50 !px-4 !font-semibold !text-amber-700 hover:!border-amber-300 hover:!bg-amber-100 hover:!text-amber-800",
  rose: "!h-10 !rounded-[14px] !border-rose-200 !bg-rose-50 !px-4 !font-semibold !text-rose-700 hover:!border-rose-300 hover:!bg-rose-100 hover:!text-rose-800",
  preview: "!h-10 !rounded-[14px] !border-none !bg-gradient-to-r !from-sky-500 !to-cyan-500 !px-4 !font-semibold !text-white hover:!from-sky-600 hover:!to-cyan-600",
  smallNeutral: "!h-8 !rounded-xl !border-slate-200 !bg-white !px-3 !text-slate-600 hover:!border-slate-300 hover:!bg-slate-50 hover:!text-slate-900",
  smallSky: "!h-8 !rounded-xl !border-sky-200 !bg-sky-50 !px-3 !text-sky-700 hover:!border-sky-300 hover:!bg-sky-100 hover:!text-sky-800",
  iconSky: "!h-9 !w-9 !rounded-[12px] !border-sky-200 !bg-sky-50 !p-0 !text-sky-700 hover:!border-sky-300 hover:!bg-sky-100 hover:!text-sky-800",
  textIcon: "!h-9 !w-9 !rounded-xl !text-slate-500 hover:!bg-slate-200/70 hover:!text-slate-900",
} as const;

export default function AdminSkillsOperationsPage() {
  const createLoadRequest = useLatestRequest();
  const [nodeForm] = Form.useForm<SkillNodeFormValues>();
  const [resourceForm] = Form.useForm<SkillResourceFormValues>();
  const [relationForm] = Form.useForm<SkillRelationFormValues>();
  // 技能治理页数据较多，缓存 overview/nodes/resources/relations 四块核心快照。
  const cache = useAdminStaleCache<SkillOpsCachePayload>("admin-skills-operations:core");
  const [overview, setOverview] = useState<SkillOpsOverviewPayload | null>(() => cache.cached?.overview ?? null);
  const [nodes, setNodes] = useState<SkillNodeRecord[]>(() => cache.cached?.nodes ?? []);
  const [resources, setResources] = useState<SkillResourceRecord[]>(() => cache.cached?.resources ?? []);
  const [relations, setRelations] = useState<SkillRelationRecord[]>(() => cache.cached?.relations ?? []);
  const [loading, setLoading] = useState(() => !cache.hasCache);
  const [error, setError] = useState<string | null>(null);
  const [nodeKeyword, setNodeKeyword] = useState("");
  const [nodeFilter, setNodeFilter] = useState<NodeFilterValue>("all");
  const [resourceKeyword, setResourceKeyword] = useState("");
  const [resourceTypeFilter, setResourceTypeFilter] = useState("");
  const [selectedNodeCode, setSelectedNodeCode] = useState<string | null>(() => cache.cached?.selectedNodeCode ?? null);
  const [selectedResourceCode, setSelectedResourceCode] = useState<string | null>(null);
  const [manualExpandedNodeCodes, setManualExpandedNodeCodes] = useState<string[]>([]);
  const [relationFocusNodeCode, setRelationFocusNodeCode] = useState<string | null>(null);
  const [treeCollapsedToLevelOne, setTreeCollapsedToLevelOne] = useState(false);
  const [nodeModalState, setNodeModalState] = useState<NodeModalState>({ open: false, mode: "create-root", target: null, parentContext: null });
  const [resourceModalState, setResourceModalState] = useState<ResourceModalState>({ open: false, mode: "create", target: null });
  const [relationEditorState, setRelationEditorState] = useState<RelationEditorState>({ open: false, mode: "create", direction: "outbound", relation: null });
  const [submittingNode, setSubmittingNode] = useState(false);
  const [submittingResource, setSubmittingResource] = useState(false);
  const [submittingRelation, setSubmittingRelation] = useState(false);
  const [nodeDeleteConfirmOpen, setNodeDeleteConfirmOpen] = useState(false);
  const [deletingNode, setDeletingNode] = useState(false);
  const [relationDeleteTarget, setRelationDeleteTarget] = useState<RelatedNodeView | null>(null);
  const [deletingRelation, setDeletingRelation] = useState(false);
  const treeNodeElementMapRef = useRef<Map<string, HTMLButtonElement>>(new Map());

  const loadData = useCallback(async (showLoading = true) => {
    const request = createLoadRequest();
    if (showLoading) {
    setLoading(true);
    }
    setError(null);
    try {
      // preview-tree 返回关系边，节点/资源仍走各自管理接口，便于分区维护。
      const [overviewPayload, nodePayload, resourcePayload, previewPayload] = await Promise.all([
        apiRequest<SkillOpsOverviewPayload>("/admin/skills/overview", { signal: request.signal }),
        apiRequest<SkillNodeListPayload>("/admin/skills/nodes", { signal: request.signal }),
        apiRequest<SkillResourceListPayload>("/admin/skills/resources", { signal: request.signal }),
        apiRequest<SkillPreviewPayload>("/admin/skills/preview-tree", { signal: request.signal }),
      ]);
      if (!request.isCurrent()) {
        return;
      }
      setOverview(overviewPayload);
      setNodes(nodePayload.records);
      setResources(resourcePayload.records);
      setRelations(previewPayload.relations ?? []);
      // 刷新后尽量保留当前选中节点，若已删除则落到第一条。
      const nextSelectedNodeCode = nodePayload.records.length === 0
        ? null
        : nodePayload.records.some((item) => item.nodeCode === selectedNodeCode)
          ? selectedNodeCode
          : nodePayload.records[0].nodeCode;
      setSelectedNodeCode(nextSelectedNodeCode);
      cache.write({
        overview: overviewPayload,
        nodes: nodePayload.records,
        resources: resourcePayload.records,
        relations: previewPayload.relations ?? [],
        selectedNodeCode: nextSelectedNodeCode,
      });
    } catch (requestError) {
      if (isAbortError(requestError) || !request.isCurrent()) {
        return;
      }
      const apiError = requestError as ApiClientError;
      setError(apiError.message || "加载技能治理页失败");
    } finally {
      if (request.isCurrent()) {
        setLoading(false);
      }
    }
  }, [cache, createLoadRequest, selectedNodeCode]);

  useEffect(() => {
    if (!cache.cached) {
      return;
    }
    // stale cache 先恢复树和右侧详情，再由 loadData 统一校准。
    setOverview(cache.cached.overview);
    setNodes(cache.cached.nodes);
    setResources(cache.cached.resources);
    setRelations(cache.cached.relations);
    setSelectedNodeCode(cache.cached.selectedNodeCode);
  }, [cache.cached]);

  useEffect(() => {
    void loadData(!cache.hasCache);
  }, [cache.hasCache, loadData]);

  const sortedNodes = useMemo(
    () => [...nodes].sort((left, right) => left.sortOrder - right.sortOrder || left.label.localeCompare(right.label, "zh-CN")),
    [nodes],
  );

  const nodeMap = useMemo(
    () => new Map(nodes.map((item) => [item.nodeCode, item])),
    [nodes],
  );

  const childNodeMap = useMemo(() => {
    // parentCode 分组后供树渲染、展开收起和路径计算共用。
    const grouped = new Map<string | null, SkillNodeRecord[]>();
    sortedNodes.forEach((item) => {
      const key = item.parentCode ?? null;
      const records = grouped.get(key) ?? [];
      records.push(item);
      grouped.set(key, records);
    });
    return grouped;
  }, [sortedNodes]);

  const resourceByNodeMap = useMemo(() => {
    // 资源按节点聚合，选中节点后右侧只展示当前节点资源。
    const grouped = new Map<string, SkillResourceRecord[]>();
    [...resources]
      .sort((left, right) => left.sortOrder - right.sortOrder || left.title.localeCompare(right.title, "zh-CN"))
      .forEach((item) => {
        const records = grouped.get(item.nodeCode) ?? [];
        records.push(item);
        grouped.set(item.nodeCode, records);
      });
    return grouped;
  }, [resources]);

  const relationByNodeMap = useMemo(() => {
    // 关系同时写入 source 和 target 两侧，方便展示“指向它”和“它指向”。
    const grouped = new Map<string, RelatedNodeView[]>();
    relations.forEach((relation) => {
      const outboundList = grouped.get(relation.sourceNodeCode) ?? [];
      outboundList.push({
        relation,
        relatedNodeCode: relation.targetNodeCode,
        relatedNode: nodeMap.get(relation.targetNodeCode) ?? null,
        direction: "outbound",
      });
      grouped.set(relation.sourceNodeCode, outboundList);

      const inboundList = grouped.get(relation.targetNodeCode) ?? [];
      inboundList.push({
        relation,
        relatedNodeCode: relation.sourceNodeCode,
        relatedNode: nodeMap.get(relation.sourceNodeCode) ?? null,
        direction: "inbound",
      });
      grouped.set(relation.targetNodeCode, inboundList);
    });

    grouped.forEach((items) => {
      items.sort((left, right) => left.relation.sortOrder - right.relation.sortOrder || left.relatedNodeCode.localeCompare(right.relatedNodeCode, "zh-CN"));
    });

    return grouped;
  }, [nodeMap, relations]);

  const selectedNode = useMemo(
    () => (selectedNodeCode ? nodeMap.get(selectedNodeCode) ?? null : null),
    [nodeMap, selectedNodeCode],
  );

  const selectedNodePath = useMemo(
    () => buildNodePath(nodeMap, selectedNode?.nodeCode ?? null),
    [nodeMap, selectedNode],
  );

  const selectedNodePathCodeSet = useMemo(
    () => new Set(selectedNodePath.map((item) => item.nodeCode)),
    [selectedNodePath],
  );

  const selectedNodePreviewHref = useMemo(
    () => (selectedNode ? buildSkillsPreviewHref(selectedNode.nodeCode) : null),
    [selectedNode],
  );

  const selectedRelatedNodes = useMemo(
    () => (selectedNode ? relationByNodeMap.get(selectedNode.nodeCode) ?? [] : []),
    [relationByNodeMap, selectedNode],
  );

  const selectedRelatedCodeSet = useMemo(
    () => new Set(selectedRelatedNodes.map((item) => item.relatedNodeCode)),
    [selectedRelatedNodes],
  );

  const relationFocusPath = useMemo(
    () => buildNodePath(nodeMap, relationFocusNodeCode),
    [nodeMap, relationFocusNodeCode],
  );

  const relationFocusPathLabel = useMemo(
    () => relationFocusPath.map((item) => item.label).join(" / "),
    [relationFocusPath],
  );

  const groupedRelatedNodes = useMemo(
    () => ({
      outbound: selectedRelatedNodes
        .filter((item) => item.direction === "outbound")
        .map((item) => ({
          ...item,
          relatedPath: buildNodePath(nodeMap, item.relatedNodeCode),
        })),
      inbound: selectedRelatedNodes
        .filter((item) => item.direction === "inbound")
        .map((item) => ({
          ...item,
          relatedPath: buildNodePath(nodeMap, item.relatedNodeCode),
        })),
    }),
    [nodeMap, selectedRelatedNodes],
  );

  const relationNodeOptions = useMemo(
    () => sortedNodes
      .filter((item) => item.nodeCode !== selectedNode?.nodeCode)
      .map((item) => ({
        label: `${item.label} · ${item.nodeCode}`,
        value: item.nodeCode,
      })),
    [selectedNode, sortedNodes],
  );

  const nextRelationSortOrder = useMemo(
    () => selectedRelatedNodes.reduce((max, item) => Math.max(max, item.relation.sortOrder), 0) + 10,
    [selectedRelatedNodes],
  );

  const nodeSearchBlobMap = useMemo(() => {
    // 搜索串合并节点、资源和关联文案，避免只搜节点标题找不到资源内容。
    const blobMap = new Map<string, string>();
    nodes.forEach((node) => {
      const nodeResources = resourceByNodeMap.get(node.nodeCode) ?? [];
      const nodeRelations = relationByNodeMap.get(node.nodeCode) ?? [];
      blobMap.set(
        node.nodeCode,
        [
          node.nodeCode,
          node.label,
          node.description ?? "",
          node.parentCode ?? "",
          node.parentLabel ?? "",
          ...nodeResources.flatMap((resource) => [resource.title, resource.sourceLabel, resource.linkUrl]),
          ...nodeRelations.flatMap((relation) => [relation.relatedNode?.label ?? "", relation.relation.label ?? "", relation.relation.relationType]),
        ].join(" ").toLowerCase(),
      );
    });
    return blobMap;
  }, [nodeMap, nodes, relationByNodeMap, resourceByNodeMap]);

  const normalizedNodeKeyword = nodeKeyword.trim().toLowerCase();

  const filteredNodes = useMemo(() => {
    // 快速筛选只影响树和跳转列表，不直接改变后端数据。
    return sortedNodes.filter((record) => {
      if (nodeFilter === "missing-resource" && record.resourceCount > 0) {
        return false;
      }
      if (nodeFilter === "with-relations" && record.inboundRelationCount + record.outboundRelationCount === 0) {
        return false;
      }
      if (nodeFilter === "root" && record.parentCode) {
        return false;
      }
      if (nodeFilter === "leaf" && record.childCount > 0) {
        return false;
      }
      if (nodeFilter === "current-related" && !selectedRelatedCodeSet.has(record.nodeCode) && record.nodeCode !== selectedNode?.nodeCode) {
        return false;
      }
      if (!normalizedNodeKeyword) {
        return true;
      }
      return (nodeSearchBlobMap.get(record.nodeCode) ?? "").includes(normalizedNodeKeyword);
    });
  }, [nodeFilter, nodeSearchBlobMap, normalizedNodeKeyword, selectedNode, selectedRelatedCodeSet, sortedNodes]);

  const filteredNodeCodeSet = useMemo(
    () => new Set(filteredNodes.map((item) => item.nodeCode)),
    [filteredNodes],
  );

  const quickJumpNodes = useMemo(
    () => filteredNodes.slice(0, 8),
    [filteredNodes],
  );

  const nodeFilterActive = Boolean(normalizedNodeKeyword) || nodeFilter !== "all";

  useEffect(() => {
    setSelectedNodeCode((current) => {
      if (filteredNodes.length === 0) {
        return null;
      }
      return filteredNodes.some((item) => item.nodeCode === current) ? current : filteredNodes[0].nodeCode;
    });
  }, [filteredNodes]);

  const selectedNodeResources = useMemo(() => {
    const scopedResources = selectedNode ? resourceByNodeMap.get(selectedNode.nodeCode) ?? [] : [];
    const normalizedResourceKeyword = resourceKeyword.trim().toLowerCase();
    return scopedResources.filter((resource) => {
      if (resourceTypeFilter && resource.resourceType !== resourceTypeFilter) {
        return false;
      }
      if (!normalizedResourceKeyword) {
        return true;
      }
      return [
        resource.resourceCode,
        resource.title,
        resource.sourceLabel,
        resource.durationLabel,
        resource.linkUrl,
      ].join(" ").toLowerCase().includes(normalizedResourceKeyword);
    });
  }, [resourceByNodeMap, resourceKeyword, resourceTypeFilter, selectedNode]);

  useEffect(() => {
    setSelectedResourceCode((current) => {
      if (selectedNodeResources.length === 0) {
        return null;
      }
      return selectedNodeResources.some((item) => item.resourceCode === current) ? current : selectedNodeResources[0].resourceCode;
    });
  }, [selectedNodeResources]);

  const selectedResource = useMemo(
    () => (selectedResourceCode ? selectedNodeResources.find((item) => item.resourceCode === selectedResourceCode) ?? null : null),
    [selectedNodeResources, selectedResourceCode],
  );

  const selectedNodeChildren = useMemo(
    () => (selectedNode ? childNodeMap.get(selectedNode.nodeCode) ?? [] : []),
    [childNodeMap, selectedNode],
  );

  const selectedNodeResourceTypeSummary = useMemo(() => {
    const summary = new Map<string, number>();
    selectedNodeResources.forEach((item) => {
      summary.set(item.resourceType, (summary.get(item.resourceType) ?? 0) + 1);
    });
    return Array.from(summary.entries()).sort(([leftType], [rightType]) => leftType.localeCompare(rightType, "zh-CN"));
  }, [selectedNodeResources]);

  const selectedNodeDeleteBlockedReason = useMemo(() => {
    if (!selectedNode) {
      return "请先选择一个节点";
    }
    // 删除节点前必须先清理子节点、资源和关系，和后端约束保持一致。
    const blockers: string[] = [];
    if (selectedNode.childCount > 0) {
      blockers.push("子节点");
    }
    if (selectedNode.resourceCount > 0) {
      blockers.push("资源");
    }
    if (selectedNode.inboundRelationCount + selectedNode.outboundRelationCount > 0) {
      blockers.push("关联");
    }
    if (blockers.length === 0) {
      return null;
    }
    return `请先清理该节点的${blockers.join("、")}后再删除`;
  }, [selectedNode]);

  const visibleTreeNodeCodes = useMemo(() => {
    if (!nodeFilterActive) {
      return null;
    }

    const visible = new Set<string>();
    const includePath = (nodeCode: string) => {
      // 搜索命中的节点需要连同祖先一起显示，否则树结构会断层。
      let current = nodeMap.get(nodeCode) ?? null;
      while (current) {
        if (visible.has(current.nodeCode)) {
          break;
        }
        visible.add(current.nodeCode);
        current = current.parentCode ? nodeMap.get(current.parentCode) ?? null : null;
      }
    };

    filteredNodes.forEach((node) => includePath(node.nodeCode));
    if (selectedNode) {
      includePath(selectedNode.nodeCode);
    }
    if (relationFocusNodeCode) {
      includePath(relationFocusNodeCode);
    }

    return visible;
  }, [filteredNodes, nodeFilterActive, nodeMap, relationFocusNodeCode, selectedNode]);

  const systemExpandedNodeCodes = useMemo(() => {
    const expanded = new Set<string>();
    // 一级节点默认展开；搜索、选中和关系定位会额外展开祖先路径。
    sortedNodes.filter((item) => item.parentCode == null).forEach((item) => expanded.add(item.nodeCode));
    if (nodeFilterActive) {
      filteredNodes.forEach((item) => collectAncestorExpansion(nodeMap, item.nodeCode, expanded));
    }
    if (selectedNode && !treeCollapsedToLevelOne) {
      collectAncestorExpansion(nodeMap, selectedNode.nodeCode, expanded);
    }
    if (relationFocusNodeCode) {
      collectAncestorExpansion(nodeMap, relationFocusNodeCode, expanded);
    }
    return Array.from(expanded);
  }, [filteredNodes, nodeFilterActive, nodeMap, relationFocusNodeCode, selectedNode, sortedNodes, treeCollapsedToLevelOne]);

  const expandedNodeCodeSet = useMemo(
    () => new Set([...manualExpandedNodeCodes, ...systemExpandedNodeCodes]),
    [manualExpandedNodeCodes, systemExpandedNodeCodes],
  );

  const visibleTreeItems = useMemo(
    () => buildVisibleTreeSequence(childNodeMap, expandedNodeCodeSet, visibleTreeNodeCodes),
    [childNodeMap, expandedNodeCodeSet, visibleTreeNodeCodes],
  );

  const toggleNodeExpansion = useCallback((nodeCode: string, nextExpanded?: boolean) => {
    setManualExpandedNodeCodes((current) => {
      const next = new Set(current);
      const shouldExpand = nextExpanded ?? !expandedNodeCodeSet.has(nodeCode);
      if (shouldExpand) {
        // 手动展开父节点时连带记录祖先，避免随后系统展开状态丢失。
        expandManualBranch(childNodeMap, nodeMap, next, nodeCode);
      } else {
        collapseManualBranch(childNodeMap, next, nodeCode);
      }
      return Array.from(next);
    });
  }, [childNodeMap, expandedNodeCodeSet, nodeMap]);

  const scrollTreeNodeIntoView = useCallback((nodeCode: string, behavior: ScrollBehavior = "smooth") => {
    window.requestAnimationFrame(() => {
      treeNodeElementMapRef.current.get(nodeCode)?.scrollIntoView({
        behavior,
        block: "nearest",
      });
    });
  }, []);

  const handleSelectNode = useCallback((nodeCode: string) => {
    setTreeCollapsedToLevelOne(false);
    setRelationFocusNodeCode(null);
    setSelectedNodeCode(nodeCode);
  }, []);

  const handleTreeNodeActivate = useCallback((record: SkillNodeRecord, hasChildren: boolean) => {
    setTreeCollapsedToLevelOne(false);
    setRelationFocusNodeCode(null);
    if (selectedNodeCode !== record.nodeCode) {
      setSelectedNodeCode(record.nodeCode);
      return;
    }
    if (hasChildren) {
      // 已选中的分支再次激活时切换展开状态，符合树控件常见交互。
      toggleNodeExpansion(record.nodeCode);
    }
  }, [selectedNodeCode, toggleNodeExpansion]);

  const handleLocateRelatedNode = useCallback((nodeCode: string) => {
    setTreeCollapsedToLevelOne(false);
    setRelationFocusNodeCode((current) => current === nodeCode ? null : nodeCode);
  }, []);

  const handleClearRelationFocus = useCallback(() => {
    setRelationFocusNodeCode(null);
  }, []);

  const handleCollapseTreeToLevelOne = useCallback(() => {
    setManualExpandedNodeCodes([]);
    setTreeCollapsedToLevelOne(true);
  }, []);

  useEffect(() => {
    if (!relationFocusNodeCode) {
      return;
    }

    // 关系定位需要等待树展开计算完成，再把目标节点滚到中间。
    const timer = window.setTimeout(() => {
      treeNodeElementMapRef.current.get(relationFocusNodeCode)?.scrollIntoView({
        behavior: "smooth",
        block: "center",
      });
    }, 80);

    return () => window.clearTimeout(timer);
  }, [expandedNodeCodeSet, relationFocusNodeCode]);

  useEffect(() => {
    if (!selectedNodeCode || relationFocusNodeCode) {
      return;
    }

    const timer = window.setTimeout(() => {
      scrollTreeNodeIntoView(selectedNodeCode);
    }, 60);

    return () => window.clearTimeout(timer);
  }, [relationFocusNodeCode, scrollTreeNodeIntoView, selectedNodeCode]);

  const handleRefresh = useCallback(() => {
    void loadData();
  }, [loadData]);

  const openCreateRootNodeModal = useCallback(() => {
    nodeForm.resetFields();
    nodeForm.setFieldsValue({
      nodeCode: "",
      label: "",
      description: "",
      sortOrder: 0,
    });
    setNodeModalState({ open: true, mode: "create-root", target: null, parentContext: null });
  }, [nodeForm]);

  const openCreateChildNodeModal = useCallback((parent: SkillNodeRecord) => {
    nodeForm.resetFields();
    nodeForm.setFieldsValue({
      nodeCode: "",
      label: "",
      description: "",
      sortOrder: Math.max(0, parent.sortOrder + 1),
    });
    setNodeModalState({ open: true, mode: "create-child", target: null, parentContext: parent });
  }, [nodeForm]);

  const openEditNodeModal = useCallback((record: SkillNodeRecord) => {
    nodeForm.setFieldsValue({
      nodeCode: record.nodeCode,
      label: record.label,
      description: record.description ?? "",
      sortOrder: record.sortOrder,
    });
    setNodeModalState({ open: true, mode: "edit", target: record, parentContext: null });
  }, [nodeForm]);

  const openCreateResourceModal = useCallback(() => {
    if (!selectedNode) {
      message.warning("请先在左侧选择一个节点");
      return;
    }

    resourceForm.resetFields();
    // 资源默认挂到当前节点，保存时再用 boundNodeCode 明确绑定。
    resourceForm.setFieldsValue({
      resourceCode: "",
      resourceType: "doc",
      title: "",
      sourceLabel: "",
      durationLabel: "",
      linkUrl: "",
      sortOrder: 0,
    });
    setResourceModalState({ open: true, mode: "create", target: null });
  }, [resourceForm, selectedNode]);

  const openEditResourceModal = useCallback((record: SkillResourceRecord) => {
    resourceForm.setFieldsValue({
      resourceCode: record.resourceCode,
      resourceType: record.resourceType,
      title: record.title,
      sourceLabel: record.sourceLabel,
      durationLabel: record.durationLabel,
      linkUrl: record.linkUrl,
      sortOrder: record.sortOrder,
    });
    setResourceModalState({ open: true, mode: "edit", target: record });
  }, [resourceForm]);

  const closeRelationEditor = useCallback(() => {
    relationForm.resetFields();
    setRelationEditorState({ open: false, mode: "create", direction: "outbound", relation: null });
  }, [relationForm]);

  const openCreateRelationEditor = useCallback(() => {
    if (!selectedNode) {
      message.warning("请先在左侧选择一个节点");
      return;
    }

    relationForm.resetFields();
    // 新建关系默认从当前节点指向其他节点，反向关系在编辑时保留方向。
    relationForm.setFieldsValue({
      relatedNodeCode: "",
      relationType: "BRIDGE",
      label: "",
      sortOrder: nextRelationSortOrder,
    });
    setRelationEditorState({ open: true, mode: "create", direction: "outbound", relation: null });
  }, [nextRelationSortOrder, relationForm, selectedNode]);

  const openEditRelationEditor = useCallback((item: RelatedNodeView) => {
    relationForm.resetFields();
    relationForm.setFieldsValue({
      relatedNodeCode: item.relatedNodeCode,
      relationType: item.relation.relationType,
      label: item.relation.label ?? "",
      sortOrder: item.relation.sortOrder,
    });
    setRelationEditorState({ open: true, mode: "edit", direction: item.direction, relation: item.relation });
  }, [relationForm]);

  const handleSubmitNode = useCallback(async () => {
    try {
      const values = await nodeForm.validateFields();
      const payload = normalizeNodeFormValues(values);
      setSubmittingNode(true);
      // 编辑节点不允许改 nodeCode/parentCode，避免破坏已有进度和关系引用。
      const response = nodeModalState.mode === "edit"
        ? await apiRequest<SkillNodeRecord>(`/admin/skills/nodes/${nodeModalState.target?.nodeCode}`, {
          method: "PUT",
          body: JSON.stringify({
            label: payload.label,
            description: payload.description,
            sortOrder: payload.sortOrder,
          }),
        })
        : await apiRequest<SkillNodeRecord>("/admin/skills/nodes", {
          method: "POST",
          body: JSON.stringify({
            nodeCode: payload.nodeCode,
            label: payload.label,
            description: payload.description,
            parentCode: nodeModalState.parentContext?.nodeCode,
            sortOrder: payload.sortOrder,
          }),
        });

      message.success(nodeModalState.mode === "edit" ? "节点已更新" : "节点已创建");
      setNodeModalState({ open: false, mode: nodeModalState.mode, target: null, parentContext: null });
      setRelationFocusNodeCode(null);
      setSelectedNodeCode(response.nodeCode);
      await loadData();
    } catch (requestError) {
      if (!(requestError instanceof ApiClientError)) {
        return;
      }
      message.error(requestError.message || "保存节点失败");
    } finally {
      setSubmittingNode(false);
    }
  }, [loadData, nodeForm, nodeModalState]);

  const handleSubmitResource = useCallback(async () => {
    const boundNodeCode = selectedNode?.nodeCode ?? resourceModalState.target?.nodeCode ?? null;
    if (!boundNodeCode) {
      message.warning("当前资源未绑定到可编辑节点");
      return;
    }

    try {
      const values = await resourceForm.validateFields();
      const payload = normalizeResourceFormValues(values);
      setSubmittingResource(true);
      // resourceCode 创建后作为稳定标识，编辑时只更新展示和链接信息。
      const response = resourceModalState.mode === "create"
        ? await apiRequest<SkillResourceRecord>("/admin/skills/resources", {
          method: "POST",
          body: JSON.stringify({
            resourceCode: payload.resourceCode,
            nodeCode: boundNodeCode,
            resourceType: payload.resourceType,
            title: payload.title,
            sourceLabel: payload.sourceLabel,
            durationLabel: payload.durationLabel,
            linkUrl: payload.linkUrl,
            sortOrder: payload.sortOrder,
          }),
        })
        : await apiRequest<SkillResourceRecord>(`/admin/skills/resources/${resourceModalState.target?.resourceCode}`, {
          method: "PUT",
          body: JSON.stringify({
            nodeCode: boundNodeCode,
            resourceType: payload.resourceType,
            title: payload.title,
            sourceLabel: payload.sourceLabel,
            durationLabel: payload.durationLabel,
            linkUrl: payload.linkUrl,
            sortOrder: payload.sortOrder,
          }),
        });

      message.success(resourceModalState.mode === "create" ? "资源已添加" : "资源已更新");
      setResourceModalState({ open: false, mode: resourceModalState.mode, target: null });
      setRelationFocusNodeCode(null);
      setSelectedNodeCode(response.nodeCode);
      setSelectedResourceCode(response.resourceCode);
      await loadData();
    } catch (requestError) {
      if (!(requestError instanceof ApiClientError)) {
        return;
      }
      message.error(requestError.message || "保存资源失败");
    } finally {
      setSubmittingResource(false);
    }
  }, [loadData, resourceForm, resourceModalState, selectedNode]);

  const handleDeleteNode = useCallback(async () => {
    if (!selectedNode) {
      message.warning("请先选择一个节点");
      return;
    }
    if (selectedNodeDeleteBlockedReason) {
      message.warning(selectedNodeDeleteBlockedReason);
      return;
    }

    try {
      setDeletingNode(true);
      // 后端会再次校验删除约束，前端提示只是减少误操作。
      await apiRequest<void>(`/admin/skills/nodes/${selectedNode.nodeCode}`, { method: "DELETE" });
      message.success("节点已删除");
      setNodeDeleteConfirmOpen(false);
      setRelationFocusNodeCode(null);
      setSelectedResourceCode(null);
      setSelectedNodeCode(selectedNode.parentCode ?? null);
      await loadData();
    } catch (requestError) {
      if (!(requestError instanceof ApiClientError)) {
        return;
      }
      message.error(requestError.message || "删除节点失败");
    } finally {
      setDeletingNode(false);
    }
  }, [loadData, selectedNode, selectedNodeDeleteBlockedReason]);

  const confirmDeleteNode = useCallback(() => {
    if (!selectedNode) {
      message.warning("请先选择一个节点");
      return;
    }
    if (selectedNodeDeleteBlockedReason) {
      message.warning(selectedNodeDeleteBlockedReason);
      return;
    }

    setNodeDeleteConfirmOpen(true);
  }, [selectedNode, selectedNodeDeleteBlockedReason]);

  const handleSubmitRelation = useCallback(async () => {
    if (!selectedNode || !relationEditorState.open) {
      return;
    }

    try {
      const values = await relationForm.validateFields();
      const payload = normalizeRelationFormValues(values);
      // inbound 编辑时实际是“对方指向当前节点”，提交前需要还原 source/target。
      const relationPayload = relationEditorState.direction === "outbound"
        ? {
          sourceNodeCode: selectedNode.nodeCode,
          targetNodeCode: payload.relatedNodeCode,
          relationType: payload.relationType,
          label: payload.label,
          sortOrder: payload.sortOrder,
        }
        : {
          sourceNodeCode: payload.relatedNodeCode,
          targetNodeCode: selectedNode.nodeCode,
          relationType: payload.relationType,
          label: payload.label,
          sortOrder: payload.sortOrder,
        };

      setSubmittingRelation(true);
      if (relationEditorState.mode === "create") {
        await apiRequest<SkillRelationRecord>("/admin/skills/relations", {
          method: "POST",
          body: JSON.stringify(relationPayload),
        });
      } else if (relationEditorState.relation) {
        await apiRequest<SkillRelationRecord>(buildRelationMutationPath(relationEditorState.relation), {
          method: "PUT",
          body: JSON.stringify(relationPayload),
        });
      }

      message.success(relationEditorState.mode === "create" ? "关联已创建" : "关联已更新");
      closeRelationEditor();
      // 保存后聚焦关联节点，方便继续检查星图连线是否符合预期。
      setRelationFocusNodeCode(payload.relatedNodeCode);
      await loadData();
    } catch (requestError) {
      if (!(requestError instanceof ApiClientError)) {
        return;
      }
      message.error(requestError.message || "保存关联失败");
    } finally {
      setSubmittingRelation(false);
    }
  }, [closeRelationEditor, loadData, relationEditorState, relationForm, selectedNode]);

  const handleDeleteRelation = useCallback(async (item: RelatedNodeView) => {
    try {
      setDeletingRelation(true);
      await apiRequest<void>(buildRelationMutationPath(item.relation), { method: "DELETE" });
      message.success("关联已删除");
      setRelationDeleteTarget(null);
      if (relationFocusNodeCode === item.relatedNodeCode) {
        setRelationFocusNodeCode(null);
      }
      if (relationEditorState.open && relationEditorState.relation && buildRelationKey(relationEditorState.relation) === buildRelationKey(item.relation)) {
        closeRelationEditor();
      }
      await loadData();
    } catch (requestError) {
      if (!(requestError instanceof ApiClientError)) {
        return;
      }
      message.error(requestError.message || "删除关联失败");
    } finally {
      setDeletingRelation(false);
    }
  }, [closeRelationEditor, loadData, relationEditorState, relationFocusNodeCode]);

  const confirmDeleteRelation = useCallback((item: RelatedNodeView) => {
    setRelationDeleteTarget(item);
  }, []);

  const quickFilterItems = useMemo(
    () => [
      ...NODE_FILTER_ITEMS,
      ...(selectedNode ? [{ value: "current-related" as const, label: "当前关联" }] : []),
    ],
    [selectedNode],
  );

  const handleTreeKeyDown = useCallback((event: React.KeyboardEvent<HTMLDivElement>) => {
    if (visibleTreeItems.length === 0) {
      return;
    }

    // 树支持键盘上下左右和 Home/End，便于后台大量节点快速巡检。
    const currentIndex = Math.max(0, visibleTreeItems.findIndex((item) => item.nodeCode === selectedNodeCode));
    const current = visibleTreeItems[currentIndex] ?? visibleTreeItems[0];
    if (!current) {
      return;
    }

    if (event.key === "ArrowDown") {
      event.preventDefault();
      const nextItem = visibleTreeItems[Math.min(visibleTreeItems.length - 1, currentIndex + 1)];
      if (nextItem) {
        handleSelectNode(nextItem.nodeCode);
      }
      return;
    }

    if (event.key === "ArrowUp") {
      event.preventDefault();
      const previousItem = visibleTreeItems[Math.max(0, currentIndex - 1)];
      if (previousItem) {
        handleSelectNode(previousItem.nodeCode);
      }
      return;
    }

    if (event.key === "ArrowRight") {
      event.preventDefault();
      if (current.hasChildren && !expandedNodeCodeSet.has(current.nodeCode)) {
        toggleNodeExpansion(current.nodeCode, true);
        return;
      }
      const nextChild = (childNodeMap.get(current.nodeCode) ?? []).find((item) => !visibleTreeNodeCodes || visibleTreeNodeCodes.has(item.nodeCode));
      if (nextChild) {
        handleSelectNode(nextChild.nodeCode);
      }
      return;
    }

    if (event.key === "ArrowLeft") {
      event.preventDefault();
      if (current.hasChildren && expandedNodeCodeSet.has(current.nodeCode)) {
        toggleNodeExpansion(current.nodeCode, false);
        return;
      }
      if (current.parentCode) {
        handleSelectNode(current.parentCode);
      }
      return;
    }

    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      handleTreeNodeActivate(current, current.hasChildren);
      return;
    }

    if (event.key === "Home") {
      event.preventDefault();
      handleSelectNode(visibleTreeItems[0].nodeCode);
      return;
    }

    if (event.key === "End") {
      event.preventDefault();
      handleSelectNode(visibleTreeItems[visibleTreeItems.length - 1].nodeCode);
    }
  }, [childNodeMap, expandedNodeCodeSet, handleSelectNode, handleTreeNodeActivate, selectedNodeCode, toggleNodeExpansion, visibleTreeItems, visibleTreeNodeCodes]);

  const renderTreeBranch = (parentCode: string | null, depth = 0): Array<JSX.Element> => {
    return (childNodeMap.get(parentCode) ?? []).flatMap((record) => {
      if (visibleTreeNodeCodes && !visibleTreeNodeCodes.has(record.nodeCode)) {
        return [];
      }

      const hasChildren = (childNodeMap.get(record.nodeCode)?.length ?? 0) > 0;
      const expanded = expandedNodeCodeSet.has(record.nodeCode);
      const isSelected = record.nodeCode === selectedNodeCode;
      const isRelationFocused = record.nodeCode === relationFocusNodeCode;
      const isPathNode = selectedNodePathCodeSet.has(record.nodeCode);
      const isRelated = selectedRelatedCodeSet.has(record.nodeCode);
      const isMatched = filteredNodeCodeSet.has(record.nodeCode);

      return [
        (
          <div key={record.nodeCode} className="space-y-1">
            <div className="flex items-stretch gap-2" style={{ paddingLeft: `${depth * 18}px` }}>
              <button
                type="button"
                aria-label={hasChildren ? (expanded ? `收起 ${record.label}` : `展开 ${record.label}`) : `${record.label} 无子节点`}
                disabled={!hasChildren}
                className={joinAdminClassNames(
                  "flex h-[38px] w-[38px] shrink-0 items-center justify-center rounded-[11px] border text-slate-500 transition-[background-color,border-color,color,box-shadow] duration-200 ease-out",
                  hasChildren
                    ? isSelected
                      ? "border-sky-200 bg-sky-50 text-sky-700 shadow-[0_10px_20px_rgba(14,165,233,0.10)] hover:border-sky-300 hover:bg-sky-100"
                      : isRelationFocused
                        ? "border-cyan-200 bg-cyan-50 text-cyan-700 shadow-[0_10px_20px_rgba(6,182,212,0.10)] hover:border-cyan-300 hover:bg-cyan-100"
                        : "border-slate-200 bg-white text-slate-500 hover:border-slate-300 hover:bg-slate-50 hover:text-slate-700"
                    : "border-slate-100 bg-slate-50 text-slate-300",
                )}
                onClick={() => {
                  if (!hasChildren) {
                    return;
                  }
                  setRelationFocusNodeCode(null);
                  toggleNodeExpansion(record.nodeCode);
                }}
              >
                {hasChildren ? (expanded ? <ChevronDown size={15} /> : <ChevronRight size={15} />) : <GitBranch size={14} />}
              </button>

              <button
                type="button"
                ref={(element) => {
                  if (element) {
                    treeNodeElementMapRef.current.set(record.nodeCode, element);
                  } else {
                    treeNodeElementMapRef.current.delete(record.nodeCode);
                  }
                }}
                className={joinAdminClassNames(
                  "group relative flex min-w-0 flex-1 items-center gap-3 rounded-[11px] border px-3 py-2 text-left transition-[background-color,border-color,color,box-shadow] duration-200 ease-out",
                  isSelected
                    ? "border-sky-300 bg-[linear-gradient(90deg,rgba(224,242,254,0.98)_0%,rgba(239,246,255,0.96)_58%,rgba(255,255,255,1)_100%)] shadow-[0_14px_26px_rgba(14,165,233,0.14)]"
                    : isRelationFocused
                      ? "border-cyan-300 bg-[linear-gradient(90deg,rgba(236,254,255,0.98)_0%,rgba(240,249,255,0.98)_100%)] shadow-[0_14px_26px_rgba(6,182,212,0.14)]"
                      : isRelated
                        ? "border-emerald-200 bg-emerald-50/72"
                        : isPathNode
                          ? "border-indigo-100 bg-indigo-50/65"
                          : isMatched && nodeFilterActive
                            ? "border-amber-200 bg-amber-50/72"
                            : "border-transparent bg-transparent hover:border-slate-200 hover:bg-white/92",
                )}
                onClick={() => handleTreeNodeActivate(record, hasChildren)}
                onDoubleClick={() => {
                  if (hasChildren) {
                    setRelationFocusNodeCode(null);
                    toggleNodeExpansion(record.nodeCode);
                  }
                }}
              >
                {isSelected || isRelationFocused ? (
                  <span
                    className={joinAdminClassNames(
                      "absolute bottom-1.5 left-1.5 top-1.5 w-1 rounded-full transition-colors duration-200 ease-out",
                      isSelected ? "bg-sky-500" : "bg-cyan-500",
                    )}
                  />
                ) : null}

                <span
                  className={joinAdminClassNames(
                    "flex h-6 w-6 shrink-0 items-center justify-center rounded-md transition-colors duration-200 ease-out",
                    isSelected
                      ? "bg-sky-500 text-white shadow-[0_8px_18px_rgba(14,165,233,0.22)]"
                      : isRelationFocused
                        ? "bg-cyan-500 text-white shadow-[0_8px_18px_rgba(6,182,212,0.22)]"
                        : hasChildren
                          ? "bg-slate-100 text-slate-600"
                          : "bg-slate-50 text-slate-500",
                  )}
                >
                  {hasChildren ? <FolderTree size={14} /> : <FileText size={14} />}
                </span>

                <span className="min-w-0 flex-1">
                  <span
                    className={joinAdminClassNames(
                      "block truncate text-[13px] font-semibold transition-colors duration-200 ease-out",
                      isSelected ? "text-sky-950" : isRelationFocused ? "text-cyan-900" : "text-slate-800",
                    )}
                  >
                    {record.label}
                  </span>
                </span>

                {isSelected ? (
                  <span className="inline-flex shrink-0 items-center rounded-full border border-sky-200 bg-white/92 px-2 py-0.5 text-[10px] font-bold text-sky-700">
                    当前
                  </span>
                ) : isRelationFocused ? (
                  <span className="inline-flex shrink-0 items-center rounded-full border border-cyan-200 bg-white/92 px-2 py-0.5 text-[10px] font-bold text-cyan-700">
                    关联
                  </span>
                ) : isRelated ? (
                  <span className="h-2 w-2 shrink-0 rounded-full bg-emerald-400" />
                ) : isMatched && nodeFilterActive ? (
                  <span className="h-2 w-2 shrink-0 rounded-full bg-amber-400" />
                ) : null}
              </button>
            </div>

            {hasChildren ? (
              <div
                className={joinAdminClassNames(
                  "grid overflow-hidden transform-gpu transition-[grid-template-rows,opacity,transform,padding] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] motion-reduce:transition-none",
                  expanded ? "grid-rows-[1fr] pt-1 opacity-100 translate-y-0" : "pointer-events-none grid-rows-[0fr] pt-0 opacity-0 -translate-y-1",
                )}
              >
                <div className="min-h-0 space-y-1">{renderTreeBranch(record.nodeCode, depth + 1)}</div>
              </div>
            ) : null}
          </div>
        ),
      ];
    });
  };

  const rootTreeNodes = renderTreeBranch(null);

  return (
    <AdminPageFrame className="!max-w-none">
      <AdminPageHeader
        sectionLabel="技能治理"
        title="技能资源治理"
        description="结构、关联与节点资源一体化配置。"
        tone="sky"
        actions={(
          <>
            <Button className={BUTTON_CLASS_NAMES.sky} onClick={openCreateRootNodeModal}>
              <Plus size={16} className="mr-2" />
              新建根节点
            </Button>
            <Button className="!h-11 !rounded-2xl !border-slate-200 !px-5 !shadow-none" onClick={handleRefresh} disabled={loading}>
              <RefreshCcw size={16} className={loading ? "mr-2 animate-spin" : "mr-2"} />
              刷新数据
            </Button>
          </>
        )}
      />

      {error ? <Alert type="error" showIcon className="rounded-[28px]" message={error} /> : null}

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-[404px_minmax(0,1fr)] 2xl:grid-cols-[436px_minmax(0,1fr)]">
        <section className="xl:sticky xl:top-6 xl:self-start">
          <div className="overflow-hidden rounded-[20px] border border-slate-200/80 bg-[linear-gradient(180deg,#f8fbff_0%,#eef4ff_38%,#ffffff_100%)] shadow-[0_16px_32px_rgba(15,23,42,0.05)]">
            <div className="border-b border-slate-200/70 px-5 py-[18px]">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <div className="text-[11px] font-bold text-slate-400">Skill Tree</div>
                  <div className="mt-2 font-['Manrope'] text-[1.36rem] font-black tracking-[-0.04em] text-slate-950">节点导航</div>
                  <div className="mt-1 text-xs leading-6 text-slate-500">{formatCount(filteredNodes.length)} 个节点命中</div>
                </div>
                <Button
                  className={BUTTON_CLASS_NAMES.iconSky}
                  onClick={openCreateRootNodeModal}
                >
                  <Plus size={16} />
                </Button>
              </div>

              <div className="mt-5 space-y-4">
                <Search
                  allowClear
                  className="w-full"
                  placeholder="检索节点 / 资源 / 关联"
                  value={nodeKeyword}
                  onChange={(event) => setNodeKeyword(event.target.value)}
                />

                <div className="flex flex-wrap gap-2">
                  {quickFilterItems.map((item) => {
                    const active = nodeFilter === item.value;
                    return (
                      <Button
                        key={item.value}
                        className={joinAdminClassNames(
                          "!h-[34px] !rounded-[12px] !px-3.5 !text-xs !font-semibold",
                          active
                            ? "!border-sky-200 !bg-sky-50 !text-sky-700"
                            : "!border-slate-200 !bg-white/95 !text-slate-600 hover:!border-slate-300 hover:!bg-white hover:!text-slate-900",
                        )}
                        onClick={() => setNodeFilter(item.value)}
                      >
                        {item.label}
                      </Button>
                    );
                  })}
                </div>

                {(nodeFilterActive || normalizedNodeKeyword) ? (
                  <div className="rounded-[14px] border border-slate-200/80 bg-white/90 p-3.5">
                    <div className="flex items-center justify-between gap-3">
                      <div className="text-[11px] font-bold text-slate-400">检索命中</div>
                      <div className="text-xs font-semibold text-slate-500">{formatCount(filteredNodes.length)} 个节点</div>
                    </div>
                    {quickJumpNodes.length > 0 ? (
                      <div className="mt-3 flex flex-wrap gap-2">
                        {quickJumpNodes.map((item) => (
                          <button
                            key={item.nodeCode}
                            type="button"
                            className="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs font-semibold text-slate-700 transition-colors hover:border-sky-200 hover:bg-sky-50 hover:text-sky-700"
                            onClick={() => handleSelectNode(item.nodeCode)}
                          >
                            {item.label}
                          </button>
                        ))}
                      </div>
                    ) : (
                      <div className="mt-3 text-sm text-slate-500">没有匹配节点</div>
                    )}
                  </div>
                ) : null}

                <div className="flex items-center justify-between gap-3 rounded-[14px] border border-slate-200 bg-[linear-gradient(135deg,#ffffff_0%,#f8fafc_48%,#f0f9ff_100%)] px-4 py-3 text-slate-900">
                  <div>
                    <div className="text-[11px] font-bold text-slate-400">
                      {relationFocusNodeCode ? "关联查看" : "树视图"}
                    </div>
                    <div className="mt-1 text-sm font-semibold text-slate-900">
                      {relationFocusNodeCode ? relationFocusPathLabel || relationFocusNodeCode : "一级展开 · 同级收束"}
                    </div>
                  </div>
                  <Button
                    className={relationFocusNodeCode ? BUTTON_CLASS_NAMES.smallSky : BUTTON_CLASS_NAMES.smallNeutral}
                    onClick={relationFocusNodeCode ? handleClearRelationFocus : handleCollapseTreeToLevelOne}
                  >
                    {relationFocusNodeCode ? "结束查看" : "收起分支"}
                  </Button>
                </div>
              </div>
            </div>

            {rootTreeNodes.length > 0 ? (
              <div
                tabIndex={0}
                className="max-h-[calc(100vh-228px)] overflow-auto px-3 py-3 outline-none focus-visible:ring-2 focus-visible:ring-sky-200 focus-visible:ring-offset-0"
                onKeyDown={handleTreeKeyDown}
              >
                <div className="space-y-1">{rootTreeNodes}</div>
              </div>
            ) : (
              <div className="p-6">
                <AdminDetailPlaceholder description="当前筛选下没有节点" />
              </div>
            )}
          </div>
        </section>

        <div className="space-y-6">
          {!selectedNode ? (
            <AdminSurfaceCard title="节点详情" description="请选择左侧节点" className="!rounded-[24px]" bodyClassName="!p-5">
              <AdminDetailPlaceholder description="请选择左侧节点查看配置" />
            </AdminSurfaceCard>
          ) : (
            <>
              <section className="overflow-hidden rounded-[26px] border border-slate-200 bg-white shadow-[0_16px_32px_rgba(15,23,42,0.05)]">
                <div className="grid gap-5 px-5 py-5 xl:grid-cols-[minmax(0,1fr)_320px] 2xl:grid-cols-[minmax(0,1fr)_352px]">
                  <div className="space-y-[18px]">
                    <div className="flex flex-wrap items-center gap-2 text-[11px] font-bold text-slate-400">
                      {selectedNodePath.map((item, index) => (
                        <span key={item.nodeCode} className="inline-flex items-center gap-2">
                          <span>{item.label}</span>
                          {index < selectedNodePath.length - 1 ? <ChevronRight size={12} /> : null}
                        </span>
                      ))}
                    </div>

                    <div className="min-w-0">
                      <div className="font-['Manrope'] text-[2.35rem] font-black tracking-[-0.05em] text-slate-950">
                        {selectedNode.label}
                      </div>
                      <div className="mt-2 font-mono text-[11px] text-slate-400">
                        {selectedNode.nodeCode}
                      </div>
                      <div className="mt-4 flex flex-wrap gap-2">
                        <span className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-600">
                          排序 {formatCount(selectedNode.sortOrder)}
                        </span>
                        <span className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-600">
                          子节点 {formatCount(selectedNode.childCount)}
                        </span>
                        <span className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-600">
                          资源 {formatCount(selectedNode.resourceCount)}
                        </span>
                        <span className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-600">
                          {selectedNode.childCount === 0 ? "叶子节点" : "分支节点"}
                        </span>
                        <span className="inline-flex max-w-full items-center truncate rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-600">
                          {selectedNode.parentCode ? `上级 ${selectedNode.parentLabel || selectedNode.parentCode}` : "根节点"}
                        </span>
                      </div>
                    </div>

                    <div className="rounded-[18px] bg-slate-50 p-3.5">
                      <div className="text-[11px] font-bold text-slate-400">节点描述</div>
                      <Paragraph className="!mb-0 !mt-2.5 !text-sm !leading-7 !text-slate-600">
                        {selectedNode.description?.trim() || "暂无描述"}
                      </Paragraph>
                    </div>

                    <div className="flex flex-wrap gap-3">
                      {selectedNodePreviewHref ? (
                        <Button
                          href={selectedNodePreviewHref}
                          target="_blank"
                          rel="noreferrer"
                          className={BUTTON_CLASS_NAMES.preview}
                        >
                          <ExternalLink size={16} className="mr-2" />
                          星图预览
                        </Button>
                      ) : null}
                      <Button className={BUTTON_CLASS_NAMES.sky} onClick={() => openCreateChildNodeModal(selectedNode)}>
                        <Plus size={16} className="mr-2" />
                        新建子节点
                      </Button>
                      <Button className={BUTTON_CLASS_NAMES.amber} onClick={() => openEditNodeModal(selectedNode)}>
                        <PencilLine size={16} className="mr-2" />
                        编辑节点
                      </Button>
                      <Button className={BUTTON_CLASS_NAMES.rose} onClick={confirmDeleteNode}>
                        <Trash2 size={16} className="mr-2" />
                        删除节点
                      </Button>
                      <Button className={BUTTON_CLASS_NAMES.emerald} onClick={openCreateResourceModal}>
                        <Plus size={16} className="mr-2" />
                        添加资源
                      </Button>
                    </div>
                  </div>

                  <div className="rounded-[22px] bg-[linear-gradient(135deg,#f8fbff_0%,#eef2ff_45%,#ecfeff_100%)] p-[18px]">
                    <div className="mb-4 flex items-center justify-between gap-3">
                      <div className="text-[11px] font-bold text-slate-400">全局概览</div>
                      <div className="text-xs font-semibold text-slate-400">当前治理台</div>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      {[
                        {
                          icon: FolderTree,
                          label: "节点",
                          value: formatCount(overview?.totalNodeCount ?? 0),
                          note: `根 ${formatCount(overview?.rootNodeCount ?? 0)}`,
                          tone: "text-indigo-600 bg-indigo-50 border-indigo-100",
                        },
                        {
                          icon: GitBranch,
                          label: "叶子",
                          value: formatCount(overview?.leafNodeCount ?? 0),
                          note: `缺资源 ${formatCount(overview?.nodesWithoutResourceCount ?? 0)}`,
                          tone: "text-emerald-600 bg-emerald-50 border-emerald-100",
                        },
                        {
                          icon: Link2,
                          label: "关联",
                          value: formatCount(overview?.relationCount ?? 0),
                          note: "跨分支",
                          tone: "text-sky-600 bg-sky-50 border-sky-100",
                        },
                        {
                          icon: BookOpen,
                          label: "资源",
                          value: formatCount(overview?.totalResourceCount ?? 0),
                          note: "节点内维护",
                          tone: "text-amber-600 bg-amber-50 border-amber-100",
                        },
                      ].map((item) => {
                        const Icon = item.icon;
                        return (
                          <div key={item.label} className="rounded-[18px] border border-white/70 bg-white/95 px-3.5 py-3.5 shadow-[0_10px_22px_rgba(15,23,42,0.04)]">
                            <div className="flex items-center justify-between gap-3">
                              <div className="text-[11px] font-bold text-slate-400">{item.label}</div>
                              <div className={joinAdminClassNames("flex h-7 w-7 items-center justify-center rounded-[10px] border", item.tone)}>
                                <Icon size={14} />
                              </div>
                            </div>
                            <div className="mt-2 font-['Manrope'] text-[1.35rem] font-black leading-none tracking-[-0.04em] text-slate-950">
                              {item.value}
                            </div>
                            <div className="mt-2 text-[11px] font-semibold text-slate-400">{item.note}</div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                </div>
              </section>

              <div className="grid gap-6 2xl:grid-cols-[minmax(0,0.84fr)_minmax(0,1.16fr)]">
                <section className="space-y-6">
                  <AdminSurfaceCard
                    title="关联跳转"
                    description={`${formatCount(selectedRelatedNodes.length)} 条关系`}
                    className="!rounded-[24px]"
                    bodyClassName="!p-5"
                  >
                    <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-[18px] border border-slate-200 bg-slate-50/80 px-4 py-3">
                      <div>
                        <div className="text-[11px] font-bold text-slate-400">当前节点关系</div>
                        <div className="mt-1 text-sm font-semibold text-slate-900">{selectedNode.label}</div>
                      </div>
                      <Button className={BUTTON_CLASS_NAMES.sky} onClick={openCreateRelationEditor}>
                        <Plus size={16} className="mr-2" />
                        新增关联
                      </Button>
                    </div>

                    {relationEditorState.open ? (
                      <div className="mb-5 rounded-[20px] border border-slate-200 bg-white p-4 shadow-[0_10px_24px_rgba(15,23,42,0.05)]">
                        <div className="flex flex-wrap items-center justify-between gap-3">
                          <div>
                            <div className="text-[11px] font-bold text-slate-400">
                              {relationEditorState.mode === "create" ? "新建关联" : "编辑关联"}
                            </div>
                            <div className="mt-1 text-sm font-semibold text-slate-900">
                              {relationEditorState.direction === "outbound" ? "从当前节点前往其他节点" : "调整回到当前节点的入口"}
                            </div>
                          </div>
                          <Button className={BUTTON_CLASS_NAMES.smallNeutral} onClick={closeRelationEditor}>
                            收起
                          </Button>
                        </div>

                        <div className="mt-4 rounded-[16px] border border-slate-200 bg-slate-50 px-4 py-3">
                          <div className="text-[11px] font-bold text-slate-400">
                            {relationEditorState.direction === "outbound" ? "起点节点" : "终点节点"}
                          </div>
                          <div className="mt-1 text-sm font-semibold text-slate-900">
                            {selectedNode.label} · {selectedNode.nodeCode}
                          </div>
                        </div>

                        <Form layout="vertical" form={relationForm} className="mt-4">
                          <Form.Item
                            label={relationEditorState.direction === "outbound" ? "目标节点" : "来源节点"}
                            name="relatedNodeCode"
                            rules={[{ required: true, message: relationEditorState.direction === "outbound" ? "请选择目标节点" : "请选择来源节点" }]}
                          >
                            <Select
                              showSearch
                              optionFilterProp="label"
                              options={relationNodeOptions}
                              placeholder={relationEditorState.direction === "outbound" ? "选择前往的节点" : "选择回到当前的节点"}
                            />
                          </Form.Item>

                          <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_164px]">
                            <Form.Item label="关系类型" name="relationType" rules={[{ required: true, message: "请选择关系类型" }]}>
                              <Select options={RELATION_TYPE_OPTIONS.slice()} />
                            </Form.Item>
                            <Form.Item label="排序值" name="sortOrder">
                              <InputNumber className="!w-full" />
                            </Form.Item>
                          </div>

                          <Form.Item label="关系说明" name="label" rules={[{ required: true, message: "请输入关系说明" }]}>
                            <Input />
                          </Form.Item>
                        </Form>

                        <div className="flex flex-wrap gap-3">
                          <Button className={BUTTON_CLASS_NAMES.sky} loading={submittingRelation} onClick={() => void handleSubmitRelation()}>
                            {relationEditorState.mode === "create" ? "创建关联" : "保存关联"}
                          </Button>
                          {relationEditorState.mode === "edit" && relationEditorState.relation ? (
                            <Button
                              className={BUTTON_CLASS_NAMES.rose}
                              onClick={() => {
                                const target = selectedRelatedNodes.find((item) => buildRelationKey(item.relation) === buildRelationKey(relationEditorState.relation!));
                                if (target) {
                                  confirmDeleteRelation(target);
                                }
                              }}
                            >
                              <Trash2 size={16} className="mr-2" />
                              删除关联
                            </Button>
                          ) : null}
                        </div>
                      </div>
                    ) : null}

                    {selectedRelatedNodes.length > 0 ? (
                      <div className="space-y-5">
                        {[
                          {
                            key: "outbound",
                            title: "从当前前往",
                            empty: "当前节点还没有指向其他节点",
                            items: groupedRelatedNodes.outbound,
                          },
                          {
                            key: "inbound",
                            title: "哪些节点会来到这里",
                            empty: "当前节点还没有被其他节点引用",
                            items: groupedRelatedNodes.inbound,
                          },
                        ].map((group) => (
                          <section key={group.key} className="space-y-3">
                            <div className="flex items-center justify-between gap-3">
                              <div className="text-[11px] font-bold text-slate-400">{group.title}</div>
                              <div className="text-xs font-semibold text-slate-400">{formatCount(group.items.length)}</div>
                            </div>
                            {group.items.length > 0 ? (
                              <div className="space-y-3">
                                {group.items.map((item) => {
                                  const isLocated = relationFocusNodeCode === item.relatedNodeCode;
                                  const relatedPathLabel = item.relatedPath.map((node) => node.label).join(" / ");

                                  return (
                                    <div
                                      key={buildRelationKey(item.relation)}
                                      className={joinAdminClassNames(
                                        "rounded-[18px] border px-3.5 py-3.5 transition-colors",
                                        isLocated ? "border-sky-200 bg-sky-50/80" : "border-slate-200 bg-slate-50",
                                      )}
                                    >
                                      <div className="flex items-start justify-between gap-4">
                                        <div className="min-w-0">
                                          <div className="truncate text-sm font-semibold text-slate-900">
                                            {item.relatedNode?.label ?? item.relatedNodeCode}
                                          </div>
                                          <div className="mt-2 flex flex-wrap items-center gap-2 text-[11px] text-slate-400">
                                            <span>{item.relatedNodeCode}</span>
                                            <span className={joinAdminClassNames("rounded-full border px-2 py-0.5", getRelationTone(item.direction))}>
                                              {item.direction === "outbound" ? "从当前前往" : "回到当前"}
                                            </span>
                                            <RelationTypePill relationType={item.relation.relationType} />
                                          </div>
                                          <div className="mt-2 text-sm leading-6 text-slate-600">{item.relation.label || getRelationTypeLabel(item.relation.relationType)}</div>
                                          <div className="mt-2 text-xs leading-6 text-slate-500">{relatedPathLabel || item.relatedNodeCode}</div>
                                        </div>
                                        <div className="flex shrink-0 flex-wrap justify-end gap-2">
                                          <Button
                                            className={joinAdminClassNames(
                                              "!h-[34px] !rounded-[12px] !px-3 !font-semibold",
                                              isLocated
                                                ? "!border-sky-200 !bg-sky-100 !text-sky-700"
                                                : "!border-sky-200 !bg-sky-50 !text-sky-700 hover:!border-sky-300 hover:!bg-sky-100 hover:!text-sky-800",
                                            )}
                                            onClick={() => handleLocateRelatedNode(item.relatedNodeCode)}
                                          >
                                            {isLocated ? "查看中" : "在树中查看"}
                                          </Button>
                                          <Button className={BUTTON_CLASS_NAMES.smallNeutral} onClick={() => openEditRelationEditor(item)}>
                                            <PencilLine size={14} className="mr-1.5" />
                                            编辑
                                          </Button>
                                          <Button className={BUTTON_CLASS_NAMES.smallNeutral} onClick={() => confirmDeleteRelation(item)}>
                                            <Trash2 size={14} className="mr-1.5" />
                                            删除
                                          </Button>
                                        </div>
                                      </div>
                                    </div>
                                  );
                                })}
                              </div>
                            ) : (
                              <AdminDetailPlaceholder description={group.empty} />
                            )}
                          </section>
                        ))}
                      </div>
                    ) : (
                      <AdminDetailPlaceholder description="当前节点暂无跨分支关联" />
                    )}
                  </AdminSurfaceCard>

                  <AdminSurfaceCard
                    title="子节点"
                    description={`${formatCount(selectedNodeChildren.length)} 个子节点`}
                    className="!rounded-[24px]"
                  bodyClassName="!p-5"
                >
                  {selectedNodeChildren.length > 0 ? (
                      <div className="overflow-hidden rounded-[18px] border border-slate-200 bg-white">
                        <div className="grid grid-cols-[minmax(0,1.2fr)_minmax(180px,0.92fr)_132px_28px] items-center gap-3 border-b border-slate-200 bg-slate-50 px-3.5 py-2 text-[10px] font-bold text-slate-400">
                          <span>节点</span>
                          <span>编码</span>
                          <span>概况</span>
                          <span className="sr-only">查看</span>
                        </div>
                        {selectedNodeChildren.map((item) => (
                          <button
                            key={item.nodeCode}
                            type="button"
                            className="group grid w-full grid-cols-[minmax(0,1.2fr)_minmax(180px,0.92fr)_132px_28px] items-center gap-3 border-b border-slate-100 px-3.5 py-2.5 text-left transition-colors last:border-b-0 hover:bg-sky-50/70"
                            onClick={() => handleSelectNode(item.nodeCode)}
                          >
                            <div className="flex min-w-0 items-center gap-2.5">
                              <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[10px] border border-slate-200 bg-slate-50 text-slate-500 transition-colors group-hover:border-sky-200 group-hover:bg-sky-100 group-hover:text-sky-700">
                                <GitBranch size={14} />
                              </div>
                              <div className="truncate text-[13px] font-semibold text-slate-900">{item.label}</div>
                            </div>
                            <div className="truncate font-mono text-[10px] text-slate-400">{item.nodeCode}</div>
                            <div className="flex items-center gap-2 text-[11px] font-semibold text-slate-500">
                              <span className="inline-flex items-center rounded-full bg-slate-100 px-2 py-1">
                                子 {formatCount(item.childCount)}
                              </span>
                              <span className="inline-flex items-center rounded-full bg-slate-100 px-2 py-1">
                                资 {formatCount(item.resourceCount)}
                              </span>
                            </div>
                            <ChevronRight size={15} className="shrink-0 text-slate-400 transition-colors group-hover:text-sky-600" />
                          </button>
                        ))}
                      </div>
                    ) : (
                      <AdminDetailPlaceholder description="当前节点暂无子节点" />
                    )}
                  </AdminSurfaceCard>
                </section>

                <AdminSurfaceCard
                  title="节点资源"
                  description={`${formatCount(selectedNodeResources.length)} 条资源 · 绑定 ${selectedNode.label}`}
                  className="!rounded-[24px]"
                  bodyClassName="!p-5"
                >
                  <div className="mb-5 flex flex-wrap items-center gap-3">
                    <div className="flex flex-1 flex-wrap items-center gap-3">
                      <Search
                        allowClear
                        className="min-w-[220px] flex-1"
                        placeholder="检索当前节点资源"
                        value={resourceKeyword}
                        onChange={(event) => setResourceKeyword(event.target.value)}
                      />
                      <Select
                        className="min-w-[150px]"
                        value={resourceTypeFilter}
                        options={[{ label: "全部类型", value: "" }, ...RESOURCE_TYPE_OPTIONS]}
                        onChange={setResourceTypeFilter}
                      />
                    </div>
                  </div>

                  {selectedNodeResourceTypeSummary.length > 0 ? (
                    <div className="mb-4 text-xs font-semibold text-slate-400">
                      资源分布：
                      {selectedNodeResourceTypeSummary.map(([resourceType, count], index) => (
                        <span key={resourceType}>
                          {index > 0 ? " · " : " "}
                          {getResourceTypeLabel(resourceType)} {formatCount(count)}
                        </span>
                      ))}
                    </div>
                  ) : null}

                  <div className="grid gap-5 xl:grid-cols-[minmax(0,1.12fr)_minmax(340px,0.88fr)] 2xl:grid-cols-[minmax(0,1.1fr)_minmax(380px,0.9fr)]">
                    <div className="space-y-4">
                      {selectedNodeResources.length > 0 ? (
                        <div className="overflow-hidden rounded-[18px] border border-slate-200 bg-white">
                          <div className="grid grid-cols-[minmax(0,1.3fr)_104px_120px_116px] items-center gap-3 border-b border-slate-200 bg-slate-50 px-3.5 py-2 text-[10px] font-bold text-slate-400">
                            <span>资源标题</span>
                            <span>类型</span>
                            <span>来源</span>
                            <span>标签</span>
                          </div>
                          {selectedNodeResources.map((item) => {
                            const isSelected = item.resourceCode === selectedResourceCode;
                            return (
                              <button
                                key={item.resourceCode}
                                type="button"
                                className={joinAdminClassNames(
                                  "grid w-full grid-cols-[minmax(0,1.3fr)_104px_120px_116px] items-center gap-3 border-b border-slate-100 px-3.5 py-2.5 text-left transition-colors last:border-b-0",
                                  isSelected
                                    ? "bg-sky-50"
                                    : "bg-white hover:bg-sky-50/60",
                                )}
                                onClick={() => setSelectedResourceCode(item.resourceCode)}
                              >
                                <div className="flex min-w-0 items-center gap-3">
                                  <div className={joinAdminClassNames(
                                    "flex h-8 w-8 shrink-0 items-center justify-center rounded-[12px] border",
                                    isSelected
                                      ? "border-sky-200 bg-white text-sky-700"
                                      : "border-slate-200 bg-slate-50 text-indigo-600",
                                  )}>
                                    {renderResourceIcon(item.resourceType, 15)}
                                  </div>
                                  <div className="min-w-0">
                                    <div className="truncate text-[13px] font-semibold text-slate-900">{item.title}</div>
                                    <div className="mt-0.5 truncate font-mono text-[10px] text-slate-400">{item.resourceCode}</div>
                                  </div>
                                </div>
                                <div>
                                  <ResourceTypePill resourceType={item.resourceType} />
                                </div>
                                <div className="truncate text-xs font-semibold text-slate-500">{item.sourceLabel}</div>
                                <div className="truncate text-xs font-semibold text-slate-500">{item.durationLabel}</div>
                              </button>
                            );
                          })}
                        </div>
                      ) : (
                        <AdminDetailPlaceholder description="当前节点暂无资源" />
                      )}
                    </div>

                    <div className="space-y-4 xl:sticky xl:top-6 xl:self-start">
                      {!selectedResource ? (
                        <div className="rounded-[20px] border border-dashed border-slate-200 bg-slate-50 px-5 py-10">
                          <AdminDetailPlaceholder description="请选择一条资源查看明细" />
                        </div>
                      ) : (
                        <>
                          <div className="rounded-[18px] border border-slate-200 bg-white p-4 shadow-[0_10px_24px_rgba(15,23,42,0.04)]">
                            <div className="flex items-start gap-3">
                              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px] border border-slate-200 bg-slate-50 text-sky-700">
                                {renderResourceIcon(selectedResource.resourceType, 18)}
                              </div>
                              <div className="min-w-0">
                                <div className="break-words text-[1.02rem] font-bold leading-6 tracking-tight text-slate-950">
                                  {selectedResource.title}
                                </div>
                                <div className="mt-1 break-all font-mono text-[10px] text-slate-400">{selectedResource.resourceCode}</div>
                              </div>
                            </div>
                          </div>

                          <div className="grid gap-2 sm:grid-cols-2">
                            {[
                              { label: "资源类型", value: getResourceTypeLabel(selectedResource.resourceType) },
                              { label: "来源", value: selectedResource.sourceLabel },
                              { label: "时长 / 标签", value: selectedResource.durationLabel },
                              { label: "排序", value: formatCount(selectedResource.sortOrder) },
                            ].map((item) => (
                              <div key={item.label} className="rounded-[16px] border border-slate-200 bg-slate-50 px-3.5 py-3">
                                <div className="text-[10px] font-bold text-slate-400">{item.label}</div>
                                <div className="mt-1.5 text-sm font-semibold text-slate-900">{item.value}</div>
                              </div>
                            ))}
                          </div>

                          <div className="rounded-[16px] border border-slate-200 bg-slate-50 px-4 py-3.5">
                            <div className="text-[11px] font-bold text-slate-400">链接</div>
                            <div className="mt-2 break-words text-sm leading-7 text-slate-600">{selectedResource.linkUrl}</div>
                          </div>

                          <div className="flex flex-wrap gap-3 pt-1">
                            <Button className={BUTTON_CLASS_NAMES.amber} onClick={() => openEditResourceModal(selectedResource)}>
                              <PencilLine size={16} className="mr-2" />
                              编辑资源
                            </Button>
                            <Button
                              href={selectedResource.linkUrl}
                              target="_blank"
                              rel="noreferrer"
                              className={BUTTON_CLASS_NAMES.neutral}
                            >
                              <ExternalLink size={16} className="mr-2" />
                              打开链接
                            </Button>
                          </div>
                        </>
                      )}
                    </div>
                  </div>
                </AdminSurfaceCard>
              </div>
            </>
          )}
        </div>
      </div>

      {nodeModalState.open || resourceModalState.open || nodeDeleteConfirmOpen || Boolean(relationDeleteTarget) ? (
        <Suspense fallback={null}>
          <AdminSkillsOperationsOverlays
            context={{
              nodeModalState,
              setNodeModalState,
              handleSubmitNode,
              submittingNode,
              nodeForm,
              resourceModalState,
              setResourceModalState,
              handleSubmitResource,
              submittingResource,
              selectedNode,
              resourceForm,
              resourceTypeOptions: RESOURCE_TYPE_OPTIONS,
              nodeDeleteConfirmOpen,
              setNodeDeleteConfirmOpen,
              deletingNode,
              handleDeleteNode,
              relationDeleteTarget,
              setRelationDeleteTarget,
              deletingRelation,
              handleDeleteRelation,
              getRelationTypeLabel,
            }}
          />
        </Suspense>
      ) : null}
    </AdminPageFrame>
  );
}
