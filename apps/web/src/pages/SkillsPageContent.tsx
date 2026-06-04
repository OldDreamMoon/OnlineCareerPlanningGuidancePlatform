import { AnimatePresence, animate, motion, useMotionValue, useSpring } from "framer-motion";
import {
  Application as PixiApplication,
  Container as PixiContainer,
  Graphics as PixiGraphics,
  Sprite as PixiSprite,
  Text as PixiText,
  TextStyle as PixiTextStyle,
  Texture as PixiTexture,
} from "pixi.js";
import {
  AlertCircle,
  BookOpen,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Code,
  Cpu,
  Database,
  ExternalLink,
  FileText,
  Globe,
  GraduationCap,
  Lock,
  Maximize,
  MessageSquare,
  Navigation,
  Network,
  PlayCircle,
  RefreshCw,
  Server,
  Shield,
  Sparkles,
  Target,
  TerminalSquare,
  Trophy,
  ZoomIn,
  ZoomOut,
  Wrench,
  type LucideIcon,
} from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import StudentIdentityAvatar from "../components/avatar/StudentIdentityAvatar";
import { useSkillsRouteTransition } from "../components/skills/SkillsRouteTransitionProvider";
import { ApiClientError, apiRequest } from "../lib/apiClient";
import { formatCount, formatDateTime } from "../lib/formatters";
import { buildStudentNickname } from "../lib/studentNames";
import { type StudentAvatarMeta } from "../lib/studentAvatar";
import {
  buildSkillLayoutGeometry,
  type SkillLayoutGeometryResult,
  type SkillLayoutNodeInput,
} from "./skills/skillLayoutCore";
import type { SkillLayoutWorkerResponse } from "./skills/skillLayout.worker";

// 画布预留足够大的世界空间，拖拽探索时不会很快碰到边界。
const MIN_CANVAS_WIDTH = 5200;
const MIN_CANVAS_HEIGHT = 5200;
const CANVAS_PADDING = 560;
// 这些是极坐标布局的基础参数，决定第一圈半径、层间距和子树扇区留白。
const FIRST_RING_RADIUS = 560;
const DEPTH_RADIUS_GAP = 360;
const RADIAL_SPAN_PADDING = Math.PI / 48;
const SUBTREE_NODE_WEIGHT = 0.32;
const SUBTREE_DEPTH_WEIGHT = 0.78;
// 视口缩放和拖拽边界集中在这里控制，防止相机把星图拖出可见范围。
const VIEWPORT_MARGIN = 560;
const MIN_SCALE = 0.3;
const MAX_SCALE = 2.5;
const DETAIL_SWITCH_DELAY_MS = 96;
// 节点和标签的估算尺寸要和 Pixi 实际绘制接近，碰撞检测才不会偏差太大。
const NODE_VISUAL_HALF_SIZE = 62;
const NODE_LABEL_MIN_WIDTH = 176;
const NODE_LABEL_MAX_WIDTH = 360;
const NODE_LABEL_HEIGHT = 52;
const NODE_LABEL_SIDE_GAP = 20;
const NODE_LABEL_STACK_GAP = 36;
const NODE_LAYOUT_PADDING = 18;
const NODE_COLLISION_BODY_PADDING = 12;
const NODE_COLLISION_TITLE_SIDE_PADDING = 28;
const NODE_COLLISION_TITLE_STACK_PADDING = 34;
// 碰撞参数只调布局松紧，不参与业务数据；迭代次数越高越稳定但越耗时。
const NODE_COLLISION_GAP = 28;
const NODE_COLLISION_ITERATIONS = 26;
const NODE_COLLISION_RADIUS_FLEX = 360;
const NODE_DEPTH_MIN_RADIUS_GAP = 240;
const NODE_LAYER_DENSITY_PADDING = 64;
const NODE_SECTOR_FLEX = Math.PI / 120;
const NODE_LAYER_INNER_BAND_MIN = 72;
const NODE_LAYER_OUTER_BAND_MIN = 96;
const NODE_LAYER_INNER_BAND_RATIO = 0.24;
const NODE_LAYER_OUTER_BAND_RATIO = 0.28;
// 笛卡尔碰撞负责最后微调，避免极坐标角度调整后仍然出现标签压叠。
const NODE_CARTESIAN_COLLISION_ITERATIONS = 24;
const NODE_CARTESIAN_RELAXATION = 0.02;
const NODE_CARTESIAN_PUSH_FACTOR = 0.68;
const NODE_CARTESIAN_SHARED_RADIAL_WEIGHT = 0.14;
const NODE_CARTESIAN_SAME_DEPTH_RADIAL_WEIGHT = 0.22;
const NODE_CARTESIAN_CROSS_DEPTH_RADIAL_WEIGHT = 0.28;
const NODE_PICK_PADDING = 18;
const NODE_CLICK_CANCEL_DISTANCE_PX = 10;
// 拖拽惯性参数只影响手感，速度过小时立即停下来避免微抖。
const DRAG_INERTIA_MIN_SPEED = 0.08;
const DRAG_INERTIA_STOP_SPEED = 0.018;
const DRAG_INERTIA_MAX_SPEED = 1.8;
const DRAG_INERTIA_DECAY_MS = 180;
// Pixi 绘制尺寸和动效参数统一放前面，方便后续调视觉时不碰业务状态。
const PIXI_NODE_SIZE = 138;
const PIXI_NODE_HALF = PIXI_NODE_SIZE / 2;
const PIXI_HOVER_SCALE = 1.12;
const PIXI_HOVER_EASING = 0.24;
const PIXI_LABEL_FONT_SIZE = 18;
const PIXI_SYMBOL_STROKE_WIDTH = 3.2;
const PIXI_LEARNING_RING_RADIUS = PIXI_NODE_SIZE * 0.61;
const PIXI_FOCUS_RING_OUTER_RADIUS = PIXI_NODE_SIZE * 0.85;
const PIXI_FOCUS_RING_INNER_RADIUS = PIXI_NODE_SIZE * 0.61;
const PIXI_SELECTION_OUTER_GLOW_RADIUS = PIXI_NODE_SIZE * 0.83;
const PIXI_SELECTION_DASHED_RADIUS = PIXI_NODE_SIZE * 0.65;
const PIXI_SELECTION_INNER_RADIUS = PIXI_NODE_SIZE * 0.48;
const PIXI_SELECTION_CROSSHAIR_RADIUS = PIXI_NODE_SIZE * 0.5;
// 初始构建分帧执行，避免一次性创建大量 Pixi 节点造成页面卡顿。
const PIXI_CAMERA_EASING = [0.22, 1, 0.36, 1] as const;
const PIXI_INITIAL_BUILD_NODE_FRAME_BUDGET_MS = 3.4;
const PIXI_RENDERER_DESTROY_OPTIONS = { removeView: true } as const;
const PIXI_SCENE_DESTROY_OPTIONS = { children: true, texture: true, textureSource: true, context: true } as const;
// 聚焦节点存在本地，刷新页面后仍回到上次学习位置。
const FOCUS_STORAGE_KEY = "student.skills.focusNode";
const ROOT_KEY = "__root__";
const CURSOR_HIDDEN_POSITION = -160;
// 布局前等待几帧和浏览器空闲，减少首屏 mount 时的测量抖动。
const SKILLS_LAYOUT_PREPARE_FRAME_COUNT = 2;
const SKILLS_LAYOUT_IDLE_TIMEOUT_MS = 180;
type TimeValue = number | string;
// 预留给人工固定关键节点坐标，目前默认让算法自动布局。
const SKILL_LAYOUT_PRESETS: Record<string, { x: number; y: number }> = {};

type SkillStatus = "NOT_STARTED" | "LEARNING" | "MASTERED";
type SkillUiStatus = SkillStatus | "LOCKED" | "TARGET";
type PageState = "loading" | "ready" | "error";
type ResourceType = "video" | "article" | "doc" | "tool";
type SkillRelationType = "CO_LEARN" | "ADVANCE_TO" | "BRIDGE";
type ManagedPixiApplication = PixiApplication & {
  __skillsDestroyed?: boolean;
  _cancelResize?: (() => void) | null;
};

type SkillResourceItem = {
  id: string;
  type: ResourceType;
  title: string;
  source: string;
  time: string;
  link: string;
  sortOrder: number;
};

type SkillTreeResponse = {
  nodes: SkillNodeResponse[];
  relations: SkillRelationResponse[];
  summary: {
    total: number;
    mastered: number;
    learning: number;
    notStarted: number;
  };
};

type SkillNodeResponse = {
  nodeCode: string;
  label: string;
  description: string | null;
  parentCode: string | null;
  sortOrder: number;
  status: SkillStatus;
  unlocked: boolean;
  updatedAt: TimeValue | null;
  resources: SkillResourceItem[];
};

type SkillRelationResponse = {
  sourceNodeCode: string;
  targetNodeCode: string;
  relationType: SkillRelationType;
  label: string;
  sortOrder: number;
};

type StudentProfileSummary = {
  userId: number;
  displayName: string;
  realName: string | null;
  avatar?: StudentAvatarMeta | null;
  tier?: string | null;
  targetPosition: string | null;
  communityScore7d: number;
  portrait: {
    evidence: {
      masteredSkills: number;
      learningSkills: number;
      interviewMessages7d: number;
      posts7d: number;
      comments7d: number;
      likesReceived7d: number;
    } | null;
    updatedAt: TimeValue | null;
  } | null;
};

type PositionedSkillNode = SkillNodeResponse & {
  depth: number;
  x: number;
  y: number;
  angle: number;
  radius: number;
  icon: LucideIcon;
};

type PositionedSkillRelation = SkillRelationResponse & {
  sourceNode: PositionedSkillNode;
  targetNode: PositionedSkillNode;
};

type SkillLayoutResult = {
  nodes: PositionedSkillNode[];
  width: number;
  height: number;
  centerX: number;
  centerY: number;
};

type NoticeState = {
  tone: "success" | "error" | "info";
  message: string;
};

type ViewportState = {
  x: number;
  y: number;
  scale: number;
};

type WorldBounds = {
  left: number;
  top: number;
  right: number;
  bottom: number;
};

type DragState = {
  active: boolean;
  pointerId: number | null;
  startX: number;
  startY: number;
  originX: number;
  originY: number;
  lastX: number;
  lastY: number;
  lastTime: number;
  velocityX: number;
  velocityY: number;
};

type ActionButtonConfig = {
  label: string;
  targetStatus: SkillStatus;
  tone: "primary" | "secondary" | "danger";
  icon: LucideIcon;
};

type SkillsPageMode = "student" | "admin-preview";

type CursorMode = "idle" | "interactive" | "drag";
type NodeLabelDirection = "left" | "right" | "top" | "bottom";
type PixiLabelDetailLevel = "near" | "mid" | "far";

type LayoutCollisionNode = {
  nodeCode: string;
  label: string;
  parentCode: string | null;
  depth: number;
  angle: number;
  originalAngle: number;
  radius: number;
  baseRadius: number;
  minAngle: number;
  maxAngle: number;
  minRadius: number;
  maxRadius: number;
  anchorX: number;
  anchorY: number;
  x: number;
  y: number;
  fixed: boolean;
};

type NodePointerCandidate = {
  pointerId: number;
  nodeCode: string;
  startX: number;
  startY: number;
};

type PixiAnimatedTarget =
  | {
    kind: "rotate";
    displayObject: PixiContainer;
    speed: number;
  }
  | {
    kind: "pulse";
    displayObject: PixiContainer;
    baseScale: number;
    amplitude: number;
    speed: number;
    alphaMin: number;
    alphaMax: number;
  }
  | {
    kind: "ping";
    displayObject: PixiContainer;
    startScale: number;
    endScale: number;
    alphaStart: number;
    alphaEnd: number;
    durationMs: number;
    delayMs?: number;
    profile?: "default" | "radar";
  }
  | {
    kind: "settle";
    displayObject: PixiContainer;
    startScale: number;
    endScale: number;
    startAlpha: number;
    endAlpha: number;
    durationMs: number;
    startTimeMs: number;
  };

type PixiNodeVisualState = {
  container: PixiContainer;
  bodyContainer: PixiContainer;
  labelContainer: PixiContainer;
  hoverScaleCurrent: number;
  hoverScaleTarget: number;
  baseZIndex: number;
};

type PixiSceneState = {
  app: PixiApplication;
  world: PixiContainer;
  treeBaseLayer: PixiGraphics;
  treeProgressLayer: PixiGraphics;
  relationBaseLayer: PixiGraphics;
  relationHighlightLayer: PixiGraphics;
  nodeLayer: PixiContainer;
  nodeVisuals: Map<string, PixiNodeVisualState>;
  animatedTargets: PixiAnimatedTarget[];
  tickerHandler: (() => void) | null;
  lastSelectedNodeCode: string | null;
};

type CancelableTask = {
  cancel: () => void;
};

type PixiRenderSnapshot = {
  positionedNodes: PositionedSkillNode[];
  positionedRelations: PositionedSkillRelation[];
  nodeMap: Map<string, PositionedSkillNode>;
  selectedNodeCode: string | null;
  focusNodeCode: string | null;
  worldCenterX: number;
  worldCenterY: number;
  labelDetailLevel: PixiLabelDetailLevel;
};

function joinClasses(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(" ");
}

function getCursorModeFromTarget(target: EventTarget | null, dragging: boolean): CursorMode {
  if (dragging) {
    return "drag";
  }

  // 光标只看当前命中的交互元素，避免拖动画布时和按钮 hover 状态打架。
  if (target instanceof Element && target.closest("button, a, [role='button'], [data-node-trigger='true'], [data-cursor='interactive']")) {
    return "interactive";
  }

  return "idle";
}

function readStoredFocusSkillCode() {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    // localStorage 读取失败时直接忽略，星图仍可按默认中心节点打开。
    return window.localStorage.getItem(FOCUS_STORAGE_KEY);
  } catch {
    return null;
  }
}

function createEmptySkillLayoutResult(): SkillLayoutResult {
  return {
    nodes: [],
    width: MIN_CANVAS_WIDTH,
    height: MIN_CANVAS_HEIGHT,
    centerX: MIN_CANVAS_WIDTH / 2,
    centerY: MIN_CANVAS_HEIGHT / 2,
  };
}

function buildSkillLayoutResultFromGeometry(
  treeNodes: SkillNodeResponse[],
  geometry: SkillLayoutGeometryResult,
): SkillLayoutResult {
  // Worker/主线程产出的几何结果只负责坐标，页面节点状态仍以接口数据为准。
  const geometryNodeMap = new Map(geometry.nodes.map((node) => [node.nodeCode, node]));

  return {
    nodes: treeNodes
      .slice()
      .sort((left, right) => left.sortOrder - right.sortOrder || left.nodeCode.localeCompare(right.nodeCode))
      .map((node) => {
        const geometryNode = geometryNodeMap.get(node.nodeCode);
        return {
          ...node,
          depth: geometryNode?.depth ?? 0,
          x: geometryNode?.x ?? geometry.centerX,
          y: geometryNode?.y ?? geometry.centerY,
          angle: geometryNode?.angle ?? (-Math.PI / 2),
          radius: geometryNode?.radius ?? 0,
          icon: getSkillIcon(node),
        };
      }),
    width: geometry.width,
    height: geometry.height,
    centerX: geometry.centerX,
    centerY: geometry.centerY,
  };
}

function waitForAnimationFrames(frameCount: number) {
  if (typeof window === "undefined" || frameCount <= 0) {
    return Promise.resolve();
  }

  // 等待固定帧数后再绘制，可以让容器尺寸和字体加载先稳定下来。
  return new Promise<void>((resolve) => {
    let remaining = frameCount;

    const step = () => {
      remaining -= 1;
      if (remaining <= 0) {
        resolve();
        return;
      }

      window.requestAnimationFrame(step);
    };

    window.requestAnimationFrame(step);
  });
}

function waitForBrowserIdle(timeoutMs = SKILLS_LAYOUT_IDLE_TIMEOUT_MS) {
  if (typeof window === "undefined") {
    return Promise.resolve();
  }

  // requestIdleCallback 可用时优先用空闲期，不可用就用短 timeout 兜底。
  return new Promise<void>((resolve) => {
    const browserWindow = window as Window & typeof globalThis;

    if (typeof browserWindow.requestIdleCallback === "function") {
      browserWindow.requestIdleCallback(
        () => resolve(),
        { timeout: timeoutMs },
      );
      return;
    }

    globalThis.setTimeout(resolve, 32);
  });
}

function isSamePixiRenderSnapshot(
  left: PixiRenderSnapshot | null,
  right: PixiRenderSnapshot,
) {
  if (!left) {
    return false;
  }

  // 快照引用没变就跳过重绘，减少 Pixi 场景频繁销毁和重建。
  return left.positionedNodes === right.positionedNodes
    && left.positionedRelations === right.positionedRelations
    && left.nodeMap === right.nodeMap
    && left.selectedNodeCode === right.selectedNodeCode
    && left.focusNodeCode === right.focusNodeCode
    && left.worldCenterX === right.worldCenterX
    && left.worldCenterY === right.worldCenterY
    && left.labelDetailLevel === right.labelDetailLevel;
}

function getSkillIcon(node: SkillNodeResponse): LucideIcon {
  const code = node.nodeCode.toLowerCase();
  const label = node.label.toLowerCase();

  // 图标按 code 优先、中文 label 兜底匹配，后台改文案后也尽量保持合理图标。
  if (
    code.includes("storytelling")
    || code.includes("reflection")
    || code.includes("career")
    || code.includes("resume")
    || code.includes("job_market")
    || code.includes("interview")
    || label.includes("就业")
    || label.includes("职业")
    || label.includes("简历")
    || label.includes("面试")
    || label.includes("成果表达")
    || label.includes("复盘")
  ) {
    return Target;
  }

  if (
    code.includes("internship")
    || code.includes("workplace")
    || code.includes("teamwork")
    || code.includes("communication")
    || code.includes("professionalism")
    || code.includes("task_execution")
    || label.includes("实习")
    || label.includes("职场")
    || label.includes("沟通")
    || label.includes("协作")
    || label.includes("专业度")
  ) {
    return MessageSquare;
  }

  if (
    code.includes("wellbeing")
    || code.includes("stress")
    || code.includes("emotion")
    || code.includes("mindset")
    || code.includes("habit")
    || code.includes("burnout")
    || label.includes("心理")
    || label.includes("压力")
    || label.includes("情绪")
    || label.includes("心态")
    || label.includes("精力")
    || label.includes("倦怠")
  ) {
    return Sparkles;
  }

  if (
    code.includes("algorithm")
    || code.includes("data_struct")
    || code.includes("computational")
    || code.includes("problem_solving")
    || code.includes("graph_problem")
    || label.includes("算法")
    || label.includes("数据结构")
    || label.includes("问题拆解")
    || label.includes("计算思维")
  ) {
    return Target;
  }

  if (
    code.includes("ai_")
    || code.includes("machine")
    || code.includes("deep")
    || code.includes("llm")
    || code.includes("prompt")
    || code.includes("agent")
    || code.includes("vibe")
    || code.includes("grounding")
    || code.includes("guardrail")
    || code.includes("eval")
    || code.includes("recommendation")
    || code.includes("probability")
    || code.includes("feature_engineering")
    || label.includes("机器学习")
    || label.includes("深度学习")
    || label.includes("大模型")
    || label.includes("ai 编程")
    || label.includes("ai 原生")
    || label.includes("提示词")
    || label.includes("上下文工程")
    || label.includes("Vibe")
    || label.includes("Agent")
    || label.includes("结对编程")
    || label.includes("护栏")
    || label.includes("评测")
    || label.includes("模型路由")
    || label.includes("成本控制")
    || label.includes("推荐系统")
    || label.includes("概率")
    || label.includes("特征工程")
  ) {
    return Sparkles;
  }

  if (code.includes("test") || label.includes("测试")) {
    return CheckCircle2;
  }

  if (
    code.includes("history")
    || code.includes("ethic")
    || code.includes("theory")
    || code.includes("compiler")
    || code.includes("logic")
    || label.includes("历史")
    || label.includes("伦理")
    || label.includes("离散")
    || label.includes("编译")
    || label.includes("证明")
  ) {
    return GraduationCap;
  }

  if (
    code.includes("logic")
    || code.includes("memory_model")
    || code.includes("embedded")
    || code.includes("iot")
    || code.includes("microcomputer")
    || label.includes("硬件")
    || label.includes("数字逻辑")
    || label.includes("嵌入式")
    || label.includes("物联网")
    || label.includes("微机")
    || label.includes("指针")
  ) {
    return Cpu;
  }

  if (
    code.includes("java")
    || code.includes("c_program")
    || code.includes("front")
    || code.includes("python")
    || code.includes("object")
    || code.includes("browser")
    || code.includes("state_data")
    || label.includes("java")
    || label.includes("前端")
    || label.includes("编程")
    || label.includes("建模")
    || label.includes("python")
    || label.includes("浏览器")
    || label.includes("数据流")
  ) {
    return Code;
  }

  if (
    code.includes("database")
    || code.includes("sql")
    || code.includes("transaction")
    || code.includes("warehouse")
    || code.includes("visualization")
    || label.includes("数据库")
    || label.includes("sql")
    || label.includes("事务")
    || label.includes("数仓")
    || label.includes("可视化")
  ) {
    return Database;
  }

  if (
    code.includes("system")
    || code.includes("linux")
    || code.includes("cloud")
    || code.includes("backend")
    || code.includes("devops")
    || code.includes("architecture")
    || code.includes("observability")
    || code.includes("container")
    || label.includes("系统")
    || label.includes("linux")
    || label.includes("云原生")
    || label.includes("后端")
    || label.includes("交付")
    || label.includes("架构")
    || label.includes("故障响应")
    || label.includes("容器")
  ) {
    return Server;
  }

  if (
    code.includes("api")
    || code.includes("protocol")
    || code.includes("collaborative")
    || label.includes("接口")
    || label.includes("协议")
    || label.includes("协作")
  ) {
    return Network;
  }

  if (
    code.includes("security")
    || code.includes("crypto")
    || label.includes("安全")
    || label.includes("密码")
  ) {
    return Shield;
  }

  if (
    code.includes("network")
    || code.includes("web")
    || label.includes("网络")
    || label.includes("web")
  ) {
    return Globe;
  }

  return TerminalSquare;
}

function getResourceIcon(type: ResourceType): LucideIcon {
  if (type === "video") {
    return PlayCircle;
  }

  if (type === "article") {
    return BookOpen;
  }

  if (type === "tool") {
    return Wrench;
  }

  return FileText;
}

function getResourceTypeLabel(type: ResourceType) {
  switch (type) {
    case "video":
      return "视频";
    case "article":
      return "文章";
    case "doc":
      return "文档";
    case "tool":
      return "工具";
    default:
      return type;
  }
}

function clampScale(value: number) {
  // 缩放统一走这一层，滚轮、按钮和聚焦动画不会各自突破边界。
  return Math.min(MAX_SCALE, Math.max(MIN_SCALE, value));
}

function normalizeAngle(value: number) {
  let next = value;

  while (next <= -Math.PI) {
    next += Math.PI * 2;
  }

  while (next > Math.PI) {
    next -= Math.PI * 2;
  }

  return next;
}

function clampViewport(
  viewport: ViewportState,
  containerWidth: number,
  containerHeight: number,
  worldWidth: number,
  worldHeight: number,
): ViewportState {
  const scaledWidth = worldWidth * viewport.scale;
  const scaledHeight = worldHeight * viewport.scale;

  // 这里按缩放后的世界尺寸反推相机边界，保留一定 margin 方便拖拽回弹。
  const minXRaw = containerWidth - scaledWidth - VIEWPORT_MARGIN;
  const maxXRaw = VIEWPORT_MARGIN;
  const minYRaw = containerHeight - scaledHeight - VIEWPORT_MARGIN;
  const maxYRaw = VIEWPORT_MARGIN;

  const minX = Math.min(minXRaw, maxXRaw);
  const maxX = Math.max(minXRaw, maxXRaw);
  const minY = Math.min(minYRaw, maxYRaw);
  const maxY = Math.max(minYRaw, maxYRaw);

  return {
    ...viewport,
    x: Math.min(maxX, Math.max(minX, viewport.x)),
    y: Math.min(maxY, Math.max(minY, viewport.y)),
  };
}

function buildCenteredViewport(
  containerWidth: number,
  containerHeight: number,
  targetX: number,
  targetY: number,
  scale: number,
  worldWidth: number,
  worldHeight: number,
) {
  return clampViewport(
    {
      scale,
      x: containerWidth / 2 - targetX * scale,
      y: containerHeight / 2 - targetY * scale,
    },
    containerWidth,
    containerHeight,
    worldWidth,
    worldHeight,
  );
}

function getLabelVisualWeight(label: string) {
  // 中文字符按更宽估算，避免标签实际宽度比碰撞盒大。
  return Array.from(label).reduce((sum, char) => {
    const codePoint = char.codePointAt(0) ?? 0;
    return sum + (codePoint <= 0x7f ? 0.72 : 1);
  }, 0);
}

function getEstimatedNodeLabelWidth(label: string) {
  return Math.min(
    NODE_LABEL_MAX_WIDTH,
    Math.max(NODE_LABEL_MIN_WIDTH, 72 + getLabelVisualWeight(label) * 22),
  );
}

function getNodeLabelPlacement(
  _node: Pick<PositionedSkillNode, "x" | "y">,
  _centerX: number,
  _centerY: number,
) {
  return {
    direction: "bottom" as NodeLabelDirection,
    className: "left-1/2 top-full mt-6 -translate-x-1/2 origin-top text-center",
  };
}

function getEstimatedNodeBounds(
  node: { x: number; y: number; label: string },
  centerX: number,
  centerY: number,
): WorldBounds {
  const labelWidth = getEstimatedNodeLabelWidth(node.label);
  const nodeLeft = node.x - NODE_VISUAL_HALF_SIZE;
  const nodeTop = node.y - NODE_VISUAL_HALF_SIZE;
  const nodeRight = node.x + NODE_VISUAL_HALF_SIZE;
  const nodeBottom = node.y + NODE_VISUAL_HALF_SIZE;
  const labelLeft = node.x - labelWidth / 2;
  const labelRight = node.x + labelWidth / 2;
  const labelTop = node.y + NODE_VISUAL_HALF_SIZE + NODE_LABEL_STACK_GAP;
  const labelBottom = labelTop + NODE_LABEL_HEIGHT;

  return {
    left: Math.min(nodeLeft, labelLeft) - NODE_LAYOUT_PADDING,
    top: Math.min(nodeTop, labelTop) - NODE_LAYOUT_PADDING,
    right: Math.max(nodeRight, labelRight) + NODE_LAYOUT_PADDING,
    bottom: Math.max(nodeBottom, labelBottom) + NODE_LAYOUT_PADDING,
  };
}

function getCollisionNodeBounds(
  node: { x: number; y: number; label: string },
  centerX: number,
  centerY: number,
): WorldBounds {
  const bounds = getEstimatedNodeBounds(node, centerX, centerY);

  return {
    left: bounds.left - NODE_COLLISION_BODY_PADDING,
    top: bounds.top - NODE_COLLISION_BODY_PADDING,
    right: bounds.right + NODE_COLLISION_BODY_PADDING,
    bottom: bounds.bottom + NODE_COLLISION_BODY_PADDING + NODE_COLLISION_TITLE_STACK_PADDING,
  };
}

function pointInBounds(bounds: WorldBounds, x: number, y: number) {
  return x >= bounds.left && x <= bounds.right && y >= bounds.top && y <= bounds.bottom;
}

function getWorldPointFromViewport(
  clientX: number,
  clientY: number,
  containerRect: DOMRect,
  viewport: ViewportState,
) {
  return {
    x: (clientX - containerRect.left - viewport.x) / viewport.scale,
    y: (clientY - containerRect.top - viewport.y) / viewport.scale,
  };
}

function pickNodeAtWorldPoint(
  nodes: PositionedSkillNode[],
  x: number,
  y: number,
  centerX: number,
  centerY: number,
): PositionedSkillNode | null {
  let matchedNode: PositionedSkillNode | null = null;
  let nearestDistance = Number.POSITIVE_INFINITY;

  // 同一点可能落入多个标签盒，最终选离指针最近的节点。
  for (const node of nodes) {
    const bounds = getEstimatedNodeBounds(node, centerX, centerY);
    if (!pointInBounds({
      left: bounds.left - NODE_PICK_PADDING,
      top: bounds.top - NODE_PICK_PADDING,
      right: bounds.right + NODE_PICK_PADDING,
      bottom: bounds.bottom + NODE_PICK_PADDING,
    }, x, y)) {
      continue;
    }

    const distance = Math.hypot(node.x - x, node.y - y);
    if (distance < nearestDistance) {
      nearestDistance = distance;
      matchedNode = node;
    }
  }

  return matchedNode;
}

function getEstimatedAngularFootprint(label: string) {
  return Math.max(
    NODE_VISUAL_HALF_SIZE * 2 + NODE_COLLISION_GAP + 32,
    getEstimatedNodeLabelWidth(label) + 54,
  );
}

function updateCollisionNodePosition(node: LayoutCollisionNode, centerX: number, centerY: number) {
  node.x = centerX + Math.cos(node.angle) * node.radius;
  node.y = centerY + Math.sin(node.angle) * node.radius;
}

function clampCollisionNode(node: LayoutCollisionNode) {
  const angleRange = node.maxAngle - node.minAngle;
  if (angleRange > 0.08) {
    const inset = Math.min(0.12, Math.max(0.02, angleRange * 0.1));
    const nextMinAngle = node.minAngle + inset;
    const nextMaxAngle = node.maxAngle - inset;
    if (nextMinAngle < nextMaxAngle) {
      node.angle = Math.min(nextMaxAngle, Math.max(nextMinAngle, node.angle));
    }
  }

  node.radius = Math.min(node.maxRadius, Math.max(node.minRadius, node.radius));
}

function syncCollisionNodeFromCartesian(node: LayoutCollisionNode, centerX: number, centerY: number) {
  node.angle = Math.atan2(node.y - centerY, node.x - centerX);
  node.radius = Math.hypot(node.x - centerX, node.y - centerY);
  clampCollisionNode(node);
  updateCollisionNodePosition(node, centerX, centerY);
}

function applyCollisionTranslation(
  node: LayoutCollisionNode,
  deltaX: number,
  deltaY: number,
  centerX: number,
  centerY: number,
  radialWeight: number,
) {
  if (node.fixed) {
    return false;
  }

  const radius = Math.max(Math.hypot(node.x - centerX, node.y - centerY), 0.001);
  const radialX = radius <= 0.001 ? Math.cos(node.angle) : (node.x - centerX) / radius;
  const radialY = radius <= 0.001 ? Math.sin(node.angle) : (node.y - centerY) / radius;
  const tangentX = -radialY;
  const tangentY = radialX;
  const tangentialAmount = deltaX * tangentX + deltaY * tangentY;
  const radialAmount = deltaX * radialX + deltaY * radialY;

  node.x += tangentX * tangentialAmount + radialX * radialAmount * radialWeight;
  node.y += tangentY * tangentialAmount + radialY * radialAmount * radialWeight;
  syncCollisionNodeFromCartesian(node, centerX, centerY);
  return true;
}

function relaxCollisionNodeTowardsAnchor(node: LayoutCollisionNode, centerX: number, centerY: number) {
  if (node.fixed) {
    return false;
  }

  const nextX = node.x + (node.anchorX - node.x) * NODE_CARTESIAN_RELAXATION;
  const nextY = node.y + (node.anchorY - node.y) * NODE_CARTESIAN_RELAXATION;
  if (Math.abs(nextX - node.x) < 0.01 && Math.abs(nextY - node.y) < 0.01) {
    return false;
  }

  node.x = nextX;
  node.y = nextY;
  syncCollisionNodeFromCartesian(node, centerX, centerY);
  return true;
}

function resolveCartesianCollisionLayout(
  layoutNodes: LayoutCollisionNode[],
  centerX: number,
  centerY: number,
) {
  if (layoutNodes.length <= 1) {
    return;
  }

  for (let iteration = 0; iteration < NODE_CARTESIAN_COLLISION_ITERATIONS; iteration += 1) {
    let moved = false;
    const bounds = layoutNodes.map((node) => getCollisionNodeBounds(node, centerX, centerY));

    for (let leftIndex = 0; leftIndex < layoutNodes.length; leftIndex += 1) {
      const leftNode = layoutNodes[leftIndex];

      for (let rightIndex = leftIndex + 1; rightIndex < layoutNodes.length; rightIndex += 1) {
        const rightNode = layoutNodes[rightIndex];
        const rightBounds = bounds[rightIndex];

        if (!boundsIntersect(bounds[leftIndex], rightBounds.left, rightBounds.top, rightBounds.right, rightBounds.bottom)) {
          continue;
        }

        const overlapX = Math.min(bounds[leftIndex].right, rightBounds.right) - Math.max(bounds[leftIndex].left, rightBounds.left);
        const overlapY = Math.min(bounds[leftIndex].bottom, rightBounds.bottom) - Math.max(bounds[leftIndex].top, rightBounds.top);
        if (overlapX <= 0 || overlapY <= 0) {
          continue;
        }

        const movableNodes = Number(!leftNode.fixed) + Number(!rightNode.fixed);
        if (movableNodes === 0) {
          continue;
        }

        const sharedParent = (leftNode.parentCode ?? ROOT_KEY) === (rightNode.parentCode ?? ROOT_KEY);
        const sameDepth = leftNode.depth === rightNode.depth;
        let vectorX = rightNode.x - leftNode.x;
        let vectorY = rightNode.y - leftNode.y;

        if (Math.hypot(vectorX, vectorY) < 0.001) {
          const averageAngle = (leftNode.angle + rightNode.angle) / 2;
          const tangentialSign = normalizeAngle(rightNode.originalAngle - leftNode.originalAngle) >= 0 ? 1 : -1;
          vectorX = -Math.sin(averageAngle) * tangentialSign;
          vectorY = Math.cos(averageAngle) * tangentialSign;
        } else if (sharedParent || sameDepth) {
          const averageAngle = (leftNode.angle + rightNode.angle) / 2;
          const tangentialSign = normalizeAngle(rightNode.originalAngle - leftNode.originalAngle) >= 0 ? 1 : -1;
          vectorX += -Math.sin(averageAngle) * tangentialSign * (sharedParent ? 0.8 : 0.45);
          vectorY += Math.cos(averageAngle) * tangentialSign * (sharedParent ? 0.8 : 0.45);
        }

        const vectorLength = Math.max(Math.hypot(vectorX, vectorY), 0.001);
        const normalX = vectorX / vectorLength;
        const normalY = vectorY / vectorLength;
        const separation = Math.max(overlapX, overlapY) + NODE_COLLISION_GAP;
        const pushDistance = Math.min(
          NODE_COLLISION_RADIUS_FLEX * 0.38,
          separation * NODE_CARTESIAN_PUSH_FACTOR,
        );
        const pushPerNode = pushDistance / movableNodes;
        const radialWeight = sharedParent
          ? NODE_CARTESIAN_SHARED_RADIAL_WEIGHT
          : sameDepth
            ? NODE_CARTESIAN_SAME_DEPTH_RADIAL_WEIGHT
            : NODE_CARTESIAN_CROSS_DEPTH_RADIAL_WEIGHT;

        if (!leftNode.fixed) {
          applyCollisionTranslation(leftNode, -normalX * pushPerNode, -normalY * pushPerNode, centerX, centerY, radialWeight);
          bounds[leftIndex] = getCollisionNodeBounds(leftNode, centerX, centerY);
          moved = true;
        }

        if (!rightNode.fixed) {
          applyCollisionTranslation(rightNode, normalX * pushPerNode, normalY * pushPerNode, centerX, centerY, radialWeight);
          bounds[rightIndex] = getCollisionNodeBounds(rightNode, centerX, centerY);
          moved = true;
        }
      }
    }

    layoutNodes.forEach((node, index) => {
      if (relaxCollisionNodeTowardsAnchor(node, centerX, centerY)) {
        bounds[index] = getCollisionNodeBounds(node, centerX, centerY);
        moved = true;
      }
    });

    if (!moved) {
      break;
    }
  }
}

function sweepCollisionGroup(
  group: LayoutCollisionNode[],
  centerX: number,
  centerY: number,
) {
  if (group.length <= 1) {
    return false;
  }

  let moved = false;

  for (let index = 1; index < group.length; index += 1) {
    const previous = group[index - 1];
    const current = group[index];
    const averageRadius = Math.max((previous.radius + current.radius) / 2, FIRST_RING_RADIUS * 0.72);
    const requiredGap = Math.min(
      0.34,
      (getEstimatedAngularFootprint(previous.label) + getEstimatedAngularFootprint(current.label)) / (averageRadius * 2),
    );
    const currentGap = current.angle - previous.angle;

    if (currentGap >= requiredGap) {
      continue;
    }

    const delta = requiredGap - currentGap;

    if (!current.fixed) {
      current.angle += delta;
      clampCollisionNode(current);
      updateCollisionNodePosition(current, centerX, centerY);
      moved = true;
    } else if (!previous.fixed) {
      previous.angle -= delta;
      clampCollisionNode(previous);
      updateCollisionNodePosition(previous, centerX, centerY);
      moved = true;
    }
  }

  for (let index = group.length - 2; index >= 0; index -= 1) {
    const current = group[index];
    const next = group[index + 1];
    const averageRadius = Math.max((current.radius + next.radius) / 2, FIRST_RING_RADIUS * 0.72);
    const requiredGap = Math.min(
      0.34,
      (getEstimatedAngularFootprint(current.label) + getEstimatedAngularFootprint(next.label)) / (averageRadius * 2),
    );
    const currentGap = next.angle - current.angle;

    if (currentGap >= requiredGap) {
      continue;
    }

    const delta = requiredGap - currentGap;

    if (!current.fixed) {
      current.angle -= delta;
      clampCollisionNode(current);
      updateCollisionNodePosition(current, centerX, centerY);
      moved = true;
    } else if (!next.fixed) {
      next.angle += delta;
      clampCollisionNode(next);
      updateCollisionNodePosition(next, centerX, centerY);
      moved = true;
    }
  }

  return moved;
}

function resolveCollisionLayout(
  layoutNodes: LayoutCollisionNode[],
  centerX: number,
  centerY: number,
) {
  if (layoutNodes.length <= 1) {
    layoutNodes.forEach((node) => updateCollisionNodePosition(node, centerX, centerY));
    return;
  }

  const parentGroups = new Map<string, LayoutCollisionNode[]>();
  const depthGroups = new Map<number, LayoutCollisionNode[]>();

  layoutNodes.forEach((node) => {
    clampCollisionNode(node);
    updateCollisionNodePosition(node, centerX, centerY);

    const parentKey = node.parentCode ?? ROOT_KEY;
    const parentGroup = parentGroups.get(parentKey);
    if (parentGroup) {
      parentGroup.push(node);
    } else {
      parentGroups.set(parentKey, [node]);
    }

    const depthGroup = depthGroups.get(node.depth);
    if (depthGroup) {
      depthGroup.push(node);
    } else {
      depthGroups.set(node.depth, [node]);
    }
  });

  parentGroups.forEach((group) => group.sort(
    (left, right) => left.originalAngle - right.originalAngle || left.nodeCode.localeCompare(right.nodeCode),
  ));
  depthGroups.forEach((group) => group.sort(
    (left, right) => left.originalAngle - right.originalAngle || left.nodeCode.localeCompare(right.nodeCode),
  ));

  for (let iteration = 0; iteration < NODE_COLLISION_ITERATIONS; iteration += 1) {
    let moved = false;
    const bounds = layoutNodes.map((node) => getCollisionNodeBounds(node, centerX, centerY));

    for (let leftIndex = 0; leftIndex < layoutNodes.length; leftIndex += 1) {
      const leftNode = layoutNodes[leftIndex];

      for (let rightIndex = leftIndex + 1; rightIndex < layoutNodes.length; rightIndex += 1) {
        const rightNode = layoutNodes[rightIndex];
        const rightBounds = bounds[rightIndex];

        if (!boundsIntersect(bounds[leftIndex], rightBounds.left, rightBounds.top, rightBounds.right, rightBounds.bottom)) {
          continue;
        }

        const overlapX = Math.min(bounds[leftIndex].right, rightBounds.right) - Math.max(bounds[leftIndex].left, rightBounds.left);
        const overlapY = Math.min(bounds[leftIndex].bottom, rightBounds.bottom) - Math.max(bounds[leftIndex].top, rightBounds.top);
        if (overlapX <= 0 || overlapY <= 0) {
          continue;
        }

        const separation = Math.max(overlapX, overlapY) + NODE_COLLISION_GAP;
        const sharedParent = (leftNode.parentCode ?? ROOT_KEY) === (rightNode.parentCode ?? ROOT_KEY);
        const sameDepth = leftNode.depth === rightNode.depth;
        const signSeed = normalizeAngle(leftNode.originalAngle - rightNode.originalAngle);
        const angleSign = Math.abs(signSeed) < 0.0001 ? (leftIndex % 2 === 0 ? -1 : 1) : Math.sign(signSeed);
        const averageRadius = Math.max((leftNode.radius + rightNode.radius) / 2, FIRST_RING_RADIUS * 0.72);
        const anglePush = Math.min(
          0.24,
          (separation / averageRadius) * (sharedParent ? 0.88 : sameDepth ? 0.72 : 0.42),
        );

        if (!leftNode.fixed) {
          leftNode.angle += angleSign * anglePush;
          moved = true;
        }

        if (!rightNode.fixed) {
          rightNode.angle -= angleSign * anglePush;
          moved = true;
        }

        if (!sameDepth) {
          const deeperNode = leftNode.depth >= rightNode.depth ? leftNode : rightNode;
          const shallowerNode = deeperNode === leftNode ? rightNode : leftNode;

          if (!deeperNode.fixed) {
            deeperNode.radius += separation * 0.08;
            moved = true;
          }

          if (!shallowerNode.fixed && shallowerNode.depth > 0) {
            shallowerNode.radius -= separation * 0.03;
            moved = true;
          }
        }

        clampCollisionNode(leftNode);
        clampCollisionNode(rightNode);
        updateCollisionNodePosition(leftNode, centerX, centerY);
        updateCollisionNodePosition(rightNode, centerX, centerY);
        bounds[leftIndex] = getCollisionNodeBounds(leftNode, centerX, centerY);
        bounds[rightIndex] = getCollisionNodeBounds(rightNode, centerX, centerY);
      }
    }

    parentGroups.forEach((group) => {
      moved = sweepCollisionGroup(group, centerX, centerY) || moved;
    });
    depthGroups.forEach((group) => {
      moved = sweepCollisionGroup(group, centerX, centerY) || moved;
    });

    if (!moved) {
      break;
    }
  }

  resolveCartesianCollisionLayout(layoutNodes, centerX, centerY);
}

function buildSkillLayout(nodes: SkillNodeResponse[]): SkillLayoutResult {
  if (nodes.length === 0) {
    return {
      nodes: [],
      width: MIN_CANVAS_WIDTH,
      height: MIN_CANVAS_HEIGHT,
      centerX: MIN_CANVAS_WIDTH / 2,
      centerY: MIN_CANVAS_HEIGHT / 2,
    };
  }

  const sortedNodes = [...nodes].sort(
    (left, right) => left.sortOrder - right.sortOrder || left.nodeCode.localeCompare(right.nodeCode),
  );
  const childrenMap = new Map<string, SkillNodeResponse[]>();

  childrenMap.set(ROOT_KEY, []);

  for (const node of sortedNodes) {
    const parentKey = node.parentCode ?? ROOT_KEY;
    const group = childrenMap.get(parentKey);
    if (group) {
      group.push(node);
    } else {
      childrenMap.set(parentKey, [node]);
    }
  }

  const leafCountCache = new Map<string, number>();
  const depthCountCache = new Map<string, number>();
  const subtreeNodeCountCache = new Map<string, number>();
  const spanWeightCache = new Map<string, number>();

  const countLeaves = (nodeCode: string): number => {
    const cached = leafCountCache.get(nodeCode);
    if (cached !== undefined) {
      return cached;
    }

    const children = childrenMap.get(nodeCode) ?? [];
    const leafCount = children.length === 0
      ? 1
      : children.reduce((sum, child) => sum + countLeaves(child.nodeCode), 0);

    leafCountCache.set(nodeCode, leafCount);
    return leafCount;
  };

  const countDepth = (nodeCode: string): number => {
    const cached = depthCountCache.get(nodeCode);
    if (cached !== undefined) {
      return cached;
    }

    const children = childrenMap.get(nodeCode) ?? [];
    const depth = children.length === 0
      ? 0
      : 1 + Math.max(...children.map((child) => countDepth(child.nodeCode)));

    depthCountCache.set(nodeCode, depth);
    return depth;
  };

  const countSubtreeNodes = (nodeCode: string): number => {
    const cached = subtreeNodeCountCache.get(nodeCode);
    if (cached !== undefined) {
      return cached;
    }

    const children = childrenMap.get(nodeCode) ?? [];
    const subtreeNodeCount = 1 + children.reduce((sum, child) => sum + countSubtreeNodes(child.nodeCode), 0);

    subtreeNodeCountCache.set(nodeCode, subtreeNodeCount);
    return subtreeNodeCount;
  };

  const getSpanWeight = (nodeCode: string): number => {
    const cached = spanWeightCache.get(nodeCode);
    if (cached !== undefined) {
      return cached;
    }

    const weight = countLeaves(nodeCode)
      + Math.max(0, countSubtreeNodes(nodeCode) - 1) * SUBTREE_NODE_WEIGHT
      + countDepth(nodeCode) * SUBTREE_DEPTH_WEIGHT;

    spanWeightCache.set(nodeCode, weight);
    return weight;
  };

  const positions = new Map<string, {
    x: number;
    y: number;
    depth: number;
    angle: number;
    radius: number;
    baseRadius: number;
    startAngle: number;
    endAngle: number;
  }>();

  const roots = childrenMap.get(ROOT_KEY) ?? [];
  const singleRootMode = roots.length === 1;
  const depthGroups = new Map<number, SkillNodeResponse[]>();

  const registerNodeDepth = (node: SkillNodeResponse, depth: number) => {
    const group = depthGroups.get(depth);
    if (group) {
      group.push(node);
    } else {
      depthGroups.set(depth, [node]);
    }

    const children = childrenMap.get(node.nodeCode) ?? [];
    children.forEach((child) => registerNodeDepth(child, depth + 1));
  };

  roots.forEach((root) => registerNodeDepth(root, 0));

  const maxDepth = Array.from(depthGroups.keys()).reduce((max, depth) => Math.max(max, depth), 0);
  const resolveNominalRadius = (depth: number) => {
    if (depth === 0) {
      return singleRootMode ? 0 : FIRST_RING_RADIUS;
    }
    return singleRootMode
      ? FIRST_RING_RADIUS + (depth - 1) * DEPTH_RADIUS_GAP
      : FIRST_RING_RADIUS + depth * DEPTH_RADIUS_GAP;
  };

  const depthRadiusMap = new Map<number, number>();
  depthRadiusMap.set(0, resolveNominalRadius(0));

  for (let depth = 1; depth <= maxDepth; depth += 1) {
    const nodesAtDepth = depthGroups.get(depth) ?? [];
    const occupiedCircumference = nodesAtDepth.reduce((sum, node) => (
      sum + getEstimatedAngularFootprint(node.label) + NODE_LAYER_DENSITY_PADDING
    ), 0);
    const densityRadius = nodesAtDepth.length <= 1
      ? 0
      : (
        occupiedCircumference / (Math.PI * 2)
      ) * 1.08;
    const previousRadius = depthRadiusMap.get(depth - 1) ?? 0;
    const nextRadius = Math.max(
      resolveNominalRadius(depth),
      previousRadius + NODE_DEPTH_MIN_RADIUS_GAP,
      densityRadius,
    );
    depthRadiusMap.set(depth, nextRadius);
  }

  const maxRadius = depthRadiusMap.get(maxDepth) ?? 0;
  const computedWidth = Math.max(MIN_CANVAS_WIDTH, (maxRadius + CANVAS_PADDING) * 2);
  const computedHeight = Math.max(MIN_CANVAS_HEIGHT, (maxRadius + CANVAS_PADDING) * 2);
  const centerX = computedWidth / 2;
  const centerY = computedHeight / 2;

  const assignLayout = (node: SkillNodeResponse, startAngle: number, endAngle: number, depth: number) => {
    const angle = depth === 0 && singleRootMode ? -Math.PI / 2 : (startAngle + endAngle) / 2;
    const baseRadius = depthRadiusMap.get(depth) ?? resolveNominalRadius(depth);
    const x = centerX + Math.cos(angle) * baseRadius;
    const y = centerY + Math.sin(angle) * baseRadius;
    positions.set(node.nodeCode, {
      x,
      y,
      depth,
      angle,
      radius: baseRadius,
      baseRadius,
      startAngle,
      endAngle,
    });

    const children = childrenMap.get(node.nodeCode) ?? [];
    if (children.length === 0) {
      return;
    }

    const totalWeight = children.reduce((sum, child) => sum + getSpanWeight(child.nodeCode), 0);
    let cursor = startAngle;

    children.forEach((child) => {
      const span = ((endAngle - startAngle) * getSpanWeight(child.nodeCode)) / Math.max(totalWeight, 1);
      const inset = span > RADIAL_SPAN_PADDING * 2 ? Math.min(RADIAL_SPAN_PADDING, span * 0.16) : 0;
      assignLayout(child, cursor + inset, cursor + span - inset, depth + 1);
      cursor += span;
    });
  };

  if (roots.length === 1) {
    assignLayout(roots[0], -Math.PI + RADIAL_SPAN_PADDING, Math.PI - RADIAL_SPAN_PADDING, 0);
  } else {
    const totalRootWeight = roots.reduce((sum, node) => sum + getSpanWeight(node.nodeCode), 0);
    let cursor = -Math.PI;

    roots.forEach((root) => {
      const span = ((Math.PI * 2) * getSpanWeight(root.nodeCode)) / Math.max(totalRootWeight, 1);
      assignLayout(root, cursor + RADIAL_SPAN_PADDING, cursor + span - RADIAL_SPAN_PADDING, 0);
      cursor += span;
    });
  }

  const collisionNodes = sortedNodes.map((node) => {
    const computedPosition = positions.get(node.nodeCode) ?? {
      x: computedWidth / 2,
      y: computedHeight / 2,
      depth: 0,
      angle: -Math.PI / 2,
      radius: 0,
      baseRadius: 0,
      startAngle: -Math.PI,
      endAngle: Math.PI,
    };
    const presetPosition = SKILL_LAYOUT_PRESETS[node.nodeCode];
    const nextX = presetPosition?.x ?? computedPosition.x;
    const nextY = presetPosition?.y ?? computedPosition.y;
    const previousRadius = computedPosition.depth > 0
      ? (depthRadiusMap.get(computedPosition.depth - 1) ?? Math.max(0, computedPosition.baseRadius - DEPTH_RADIUS_GAP))
      : 0;
    const nextLayerRadius = depthRadiusMap.get(computedPosition.depth + 1) ?? (computedPosition.baseRadius + DEPTH_RADIUS_GAP);
    const inwardRoom = computedPosition.depth === 0
      ? (singleRootMode ? 0 : Math.max(NODE_LAYER_INNER_BAND_MIN, FIRST_RING_RADIUS * 0.14))
      : Math.max(NODE_LAYER_INNER_BAND_MIN, (computedPosition.baseRadius - previousRadius) * NODE_LAYER_INNER_BAND_RATIO);
    const outwardRoom = Math.max(NODE_LAYER_OUTER_BAND_MIN, (nextLayerRadius - computedPosition.baseRadius) * NODE_LAYER_OUTER_BAND_RATIO);
    const minRadius = Math.max(0, computedPosition.baseRadius - inwardRoom);
    const maxRadius = computedPosition.baseRadius + outwardRoom;

    return {
      nodeCode: node.nodeCode,
      label: node.label,
      parentCode: node.parentCode,
      depth: computedPosition.depth,
      angle: presetPosition ? Math.atan2(nextY - centerY, nextX - centerX) : computedPosition.angle,
      originalAngle: computedPosition.angle,
      radius: presetPosition ? Math.hypot(nextX - centerX, nextY - centerY) : computedPosition.radius,
      baseRadius: computedPosition.baseRadius,
      minAngle: Math.max(-Math.PI, computedPosition.startAngle - NODE_SECTOR_FLEX),
      maxAngle: Math.min(Math.PI, computedPosition.endAngle + NODE_SECTOR_FLEX),
      minRadius,
      maxRadius,
      anchorX: nextX,
      anchorY: nextY,
      x: nextX,
      y: nextY,
      fixed: Boolean(presetPosition) || (singleRootMode && computedPosition.depth === 0),
    } satisfies LayoutCollisionNode;
  });

  resolveCollisionLayout(collisionNodes, centerX, centerY);

  let resolvedCenterX = centerX;
  let resolvedCenterY = centerY;
  let bounds = collisionNodes.map((node) => getEstimatedNodeBounds(node, resolvedCenterX, resolvedCenterY));
  const minBoundLeft = bounds.reduce((min, bound) => Math.min(min, bound.left), Number.POSITIVE_INFINITY);
  const minBoundTop = bounds.reduce((min, bound) => Math.min(min, bound.top), Number.POSITIVE_INFINITY);
  const offsetX = minBoundLeft < CANVAS_PADDING ? CANVAS_PADDING - minBoundLeft : 0;
  const offsetY = minBoundTop < CANVAS_PADDING ? CANVAS_PADDING - minBoundTop : 0;

  if (offsetX !== 0 || offsetY !== 0) {
    collisionNodes.forEach((node) => {
      node.x += offsetX;
      node.y += offsetY;
    });
    resolvedCenterX += offsetX;
    resolvedCenterY += offsetY;
    bounds = collisionNodes.map((node) => getEstimatedNodeBounds(node, resolvedCenterX, resolvedCenterY));
  }

  const collisionNodeMap = new Map(collisionNodes.map((node) => [node.nodeCode, node]));
  const laidOutNodes = sortedNodes.map((node) => {
    const collisionNode = collisionNodeMap.get(node.nodeCode) ?? collisionNodes[0];
    return {
      ...node,
      depth: collisionNode.depth,
      x: collisionNode.x,
      y: collisionNode.y,
      angle: collisionNode.angle,
      radius: collisionNode.radius,
      icon: getSkillIcon(node),
    };
  });

  const maxRight = bounds.reduce((max, bound) => Math.max(max, bound.right), 0);
  const maxBottom = bounds.reduce((max, bound) => Math.max(max, bound.bottom), 0);

  return {
    nodes: laidOutNodes,
    width: Math.max(computedWidth + offsetX, Math.ceil(maxRight + CANVAS_PADDING)),
    height: Math.max(computedHeight + offsetY, Math.ceil(maxBottom + CANVAS_PADDING)),
    centerX: resolvedCenterX,
    centerY: resolvedCenterY,
  };
}

function getCurveControlPoint(source: { x: number; y: number }, target: { x: number; y: number }, bend = 0.18) {
  const midX = (source.x + target.x) / 2;
  const midY = (source.y + target.y) / 2;
  const deltaX = target.x - source.x;
  const deltaY = target.y - source.y;
  const length = Math.hypot(deltaX, deltaY) || 1;
  const normalX = -deltaY / length;
  const normalY = deltaX / length;
  const offset = Math.min(220, Math.max(70, length * bend));
  const controlX = midX + normalX * offset;
  const controlY = midY + normalY * offset;

  return {
    controlX,
    controlY,
  };
}

function getQuadraticPoint(
  t: number,
  source: { x: number; y: number },
  control: { x: number; y: number },
  target: { x: number; y: number },
) {
  const oneMinusT = 1 - t;

  return {
    x: (oneMinusT * oneMinusT * source.x) + (2 * oneMinusT * t * control.x) + (t * t * target.x),
    y: (oneMinusT * oneMinusT * source.y) + (2 * oneMinusT * t * control.y) + (t * t * target.y),
  };
}

function drawDashedQuadraticCurve(
  graphics: PixiGraphics,
  source: { x: number; y: number },
  target: { x: number; y: number },
  bend: number,
  dashLength: number,
  gapLength: number,
) {
  const { controlX, controlY } = getCurveControlPoint(source, target, bend);
  const control = { x: controlX, y: controlY };
  const length = Math.hypot(target.x - source.x, target.y - source.y);
  const segments = Math.max(18, Math.round(length / 16));
  let dashProgress = 0;
  let drawingDash = true;
  let previousPoint = source;

  graphics.moveTo(source.x, source.y);

  for (let index = 1; index <= segments; index += 1) {
    const point = getQuadraticPoint(index / segments, source, control, target);
    const stepLength = Math.hypot(point.x - previousPoint.x, point.y - previousPoint.y);
    let consumedLength = 0;

    while (consumedLength < stepLength) {
      const threshold = drawingDash ? dashLength : gapLength;
      const remainingThreshold = threshold - dashProgress;
      const portion = Math.min(remainingThreshold, stepLength - consumedLength);
      const ratio = (consumedLength + portion) / stepLength;
      const nextPoint = {
        x: previousPoint.x + (point.x - previousPoint.x) * ratio,
        y: previousPoint.y + (point.y - previousPoint.y) * ratio,
      };

      if (drawingDash) {
        graphics.lineTo(nextPoint.x, nextPoint.y);
      } else {
        graphics.moveTo(nextPoint.x, nextPoint.y);
      }

      dashProgress += portion;
      consumedLength += portion;

      if (dashProgress >= threshold - 0.001) {
        drawingDash = !drawingDash;
        dashProgress = 0;
      }
    }

    previousPoint = point;
  }
}

function boundsIntersect(bounds: WorldBounds, left: number, top: number, right: number, bottom: number) {
  return right >= bounds.left
    && left <= bounds.right
    && bottom >= bounds.top
    && top <= bounds.bottom;
}

function getRelationTypeLabel(type: SkillRelationType) {
  switch (type) {
    case "CO_LEARN":
      return "并行补强";
    case "ADVANCE_TO":
      return "继续深入";
    case "BRIDGE":
      return "跨域桥接";
    default:
      return type;
  }
}

function getRelationStroke(type: SkillRelationType) {
  switch (type) {
    case "CO_LEARN":
      return "#38bdf8";
    case "ADVANCE_TO":
      return "#f59e0b";
    case "BRIDGE":
      return "#f472b6";
    default:
      return "#475569";
  }
}

function getUnlockRequirementLabel(
  node: PositionedSkillNode | null,
  detailParent: PositionedSkillNode | null,
  nodeMap: Map<string, PositionedSkillNode>,
) {
  if (!node) {
    return "—";
  }

  if (!detailParent) {
    return "中央起点";
  }

  const grandParent = detailParent.parentCode ? nodeMap.get(detailParent.parentCode) ?? null : null;
  if (!grandParent) {
    return `与 ${detailParent.label} 并行展开`;
  }

  return `先掌握 ${detailParent.label}`;
}

function resolveCoreNodeCode(nodes: SkillNodeResponse[]) {
  if (nodes.some((node) => node.nodeCode === "programming_language_foundations")) {
    return "programming_language_foundations";
  }

  const rootNode = nodes.find((node) => !node.parentCode);
  return rootNode?.nodeCode ?? nodes[0]?.nodeCode ?? null;
}

function getNodeUiStatus(node: SkillNodeResponse, focusNodeCode: string | null): SkillUiStatus {
  if (!node.unlocked) {
    return "LOCKED";
  }

  if (focusNodeCode === node.nodeCode) {
    return "TARGET";
  }

  return node.status;
}

function resolveFocusNodeCode(nodes: SkillNodeResponse[], preferredCode: string | null) {
  const preferredNode = preferredCode ? nodes.find((node) => node.nodeCode === preferredCode && node.unlocked) : null;
  if (preferredNode) {
    return preferredNode.nodeCode;
  }

  const learningNode = nodes.find((node) => node.unlocked && node.status === "LEARNING");
  if (learningNode) {
    return learningNode.nodeCode;
  }

  const masteredNode = nodes.find((node) => node.unlocked && node.status === "MASTERED");
  if (masteredNode) {
    return masteredNode.nodeCode;
  }

  const nextNode = nodes.find((node) => node.unlocked);
  return nextNode?.nodeCode ?? nodes[0]?.nodeCode ?? null;
}

function resolveSelectedNodeCode(nodes: SkillNodeResponse[], preferredCode: string | null, fallbackCode: string | null) {
  if (preferredCode && nodes.some((node) => node.nodeCode === preferredCode)) {
    return preferredCode;
  }

  if (fallbackCode && nodes.some((node) => node.nodeCode === fallbackCode)) {
    return fallbackCode;
  }

  return nodes[0]?.nodeCode ?? null;
}

function normalizeSearchNodeCode(value: string | null) {
  const nextValue = value?.trim();
  return nextValue ? nextValue : null;
}

function buildActionButtons(node: SkillNodeResponse | null): ActionButtonConfig[] {
  if (!node || !node.unlocked) {
    return [];
  }

  if (node.status === "NOT_STARTED") {
    return [{ label: "开始学习", targetStatus: "LEARNING", tone: "primary", icon: Target }];
  }

  if (node.status === "LEARNING") {
    return [
      { label: "标记为已掌握", targetStatus: "MASTERED", tone: "primary", icon: CheckCircle2 },
      { label: "回退为未开始", targetStatus: "NOT_STARTED", tone: "secondary", icon: ChevronLeft },
    ];
  }

  return [
    { label: "回到学习中", targetStatus: "LEARNING", tone: "secondary", icon: BookOpen },
    { label: "回退为未开始", targetStatus: "NOT_STARTED", tone: "danger", icon: ChevronLeft },
  ];
}

function colorStringToNumber(value: string) {
  return Number.parseInt(value.replace("#", ""), 16);
}

function clearPixiChildren(container: PixiContainer) {
  const children = container.removeChildren();

  children.forEach((child) => {
    child.destroy({ children: true });
  });
}

function drawPixiNodeDiamond(graphics: PixiGraphics, size: number) {
  const half = size / 2;

  graphics.poly([
    0, -half,
    half, 0,
    0, half,
    -half, 0,
  ], true);
}

function createPixiText(
  text: string,
  styleOptions: ConstructorParameters<typeof PixiTextStyle>[0],
  anchorX = 0.5,
  anchorY = 0.5,
  qualityOptions?: {
    renderScale?: number;
    resolution?: number;
  },
) {
  const nextStyleOptions = styleOptions ?? {};
  const renderScale = qualityOptions?.renderScale ?? 1.4;
  const fontSize = typeof nextStyleOptions.fontSize === "number" ? nextStyleOptions.fontSize : PIXI_LABEL_FONT_SIZE;
  const letterSpacing = typeof nextStyleOptions.letterSpacing === "number" ? nextStyleOptions.letterSpacing : 0;
  const textNode = new PixiText({
    text,
    style: new PixiTextStyle({
      ...nextStyleOptions,
      fontFamily: "\"JetBrains Mono\", \"IBM Plex Mono\", \"SFMono-Regular\", Consolas, \"Liberation Mono\", Menlo, monospace",
      fontSize: fontSize * renderScale,
      letterSpacing: letterSpacing * renderScale,
      padding: 10,
    }),
    anchor: { x: anchorX, y: anchorY },
    roundPixels: true,
    resolution: qualityOptions?.resolution ?? (typeof window !== "undefined"
      ? Math.max(3, window.devicePixelRatio * 2)
      : 3),
  });
  textNode.scale.set(1 / renderScale);
  return textNode;
}

function getPixiLabelDetailLevel(scale: number): PixiLabelDetailLevel {
  if (scale >= 1) {
    return "near";
  }

  if (scale >= 0.65) {
    return "mid";
  }

  return "far";
}

function getPixiLabelTextQuality(detailLevel: PixiLabelDetailLevel) {
  if (detailLevel === "near") {
    return {
      renderScale: 1.42,
      resolution: typeof window !== "undefined" ? Math.max(3, window.devicePixelRatio * 2) : 3,
      fontSize: PIXI_LABEL_FONT_SIZE,
      letterSpacing: 1.4,
      indicatorFontSize: 13,
    };
  }

  if (detailLevel === "mid") {
    return {
      renderScale: 1.08,
      resolution: typeof window !== "undefined" ? Math.max(2, window.devicePixelRatio * 1.35) : 2,
      fontSize: 17,
      letterSpacing: 1.1,
      indicatorFontSize: 12,
    };
  }

  return {
    renderScale: 0.92,
    resolution: typeof window !== "undefined" ? Math.max(1.35, window.devicePixelRatio * 1.05) : 1.35,
    fontSize: 16,
    letterSpacing: 0.85,
    indicatorFontSize: 11,
  };
}

function getRadarPingProgress(progress: number) {
  return 1 - ((1 - progress) ** 2.35);
}

function easeOutBack(progress: number) {
  const c1 = 1.70158;
  const c3 = c1 + 1;
  return 1 + (c3 * ((progress - 1) ** 3)) + (c1 * ((progress - 1) ** 2));
}

function drawPixiLine(
  graphics: PixiGraphics,
  x1: number,
  y1: number,
  x2: number,
  y2: number,
  color: number,
  width: number,
  alpha = 1,
) {
  graphics.moveTo(x1, y1).lineTo(x2, y2).stroke({ color, width, alpha, cap: "round", join: "round" });
}

function createPixiDashedCircle(
  radius: number,
  dashCount: number,
  color: number,
  width: number,
  alpha: number,
  rotationOffset = 0,
) {
  const container = new PixiContainer();
  const angleStep = (Math.PI * 2) / dashCount;
  const dashAngle = angleStep * 0.58;

  for (let index = 0; index < dashCount; index += 1) {
    const startAngle = (-Math.PI / 2) + rotationOffset + (angleStep * index);
    const endAngle = startAngle + dashAngle;
    const dash = new PixiGraphics();
    dash.arc(0, 0, radius, startAngle, endAngle).stroke({
      color,
      width,
      alpha,
      cap: "round",
      join: "round",
    });
    container.addChild(dash);
  }

  return container;
}

function createPixiNodeSymbol(icon: LucideIcon, color: number, locked = false) {
  const container = new PixiContainer();
  const strokeWidth = locked ? 3.1 : PIXI_SYMBOL_STROKE_WIDTH;
  const strokeAlpha = locked ? 0.82 : 0.98;
  const accentWidth = locked ? Math.max(2.1, strokeWidth - 1) : Math.max(2.4, strokeWidth - 1.2);
  const accentAlpha = Math.min(1, strokeAlpha * 0.84);

  if (icon === Database) {
    const cylinder = new PixiGraphics();
    cylinder.ellipse(0, -10, 12, 5).stroke({ color, width: strokeWidth, alpha: strokeAlpha });
    drawPixiLine(cylinder, -12, -10, -12, 10, color, strokeWidth, strokeAlpha);
    drawPixiLine(cylinder, 12, -10, 12, 10, color, strokeWidth, strokeAlpha);
    cylinder.ellipse(0, 0, 12, 5).stroke({ color, width: accentWidth, alpha: accentAlpha });
    cylinder.ellipse(0, 10, 12, 5).stroke({ color, width: strokeWidth, alpha: strokeAlpha });
    container.addChild(cylinder);
    container.scale.set(1.14);
    return container;
  }

  if (icon === Server) {
    const shell = new PixiGraphics();
    shell.roundRect(-14, -15, 28, 30, 6).stroke({ color, width: strokeWidth, alpha: strokeAlpha });
    drawPixiLine(shell, -8, -7, 8, -7, color, accentWidth, accentAlpha);
    drawPixiLine(shell, -8, 0, 8, 0, color, accentWidth, accentAlpha);
    drawPixiLine(shell, -8, 7, 8, 7, color, accentWidth, accentAlpha);
    const dots = new PixiGraphics();
    dots.circle(-10, -7, 1.8).fill({ color, alpha: strokeAlpha });
    dots.circle(-10, 0, 1.8).fill({ color, alpha: strokeAlpha });
    dots.circle(-10, 7, 1.8).fill({ color, alpha: strokeAlpha });
    container.addChild(shell, dots);
    container.scale.set(1.14);
    return container;
  }

  if (icon === Code) {
    const brackets = new PixiGraphics();
    brackets.moveTo(-9, -10).lineTo(-17, 0).lineTo(-9, 10).stroke({ color, width: strokeWidth, alpha: strokeAlpha });
    brackets.moveTo(9, -10).lineTo(17, 0).lineTo(9, 10).stroke({ color, width: strokeWidth, alpha: strokeAlpha });
    brackets.moveTo(4, -12).lineTo(-4, 12).stroke({ color, width: accentWidth, alpha: accentAlpha });
    container.addChild(brackets);
    container.scale.set(1.14);
    return container;
  }

  if (icon === Cpu) {
    const chip = new PixiGraphics();
    chip.roundRect(-10, -10, 20, 20, 4).stroke({ color, width: strokeWidth, alpha: strokeAlpha });
    chip.roundRect(-4, -4, 8, 8, 2).stroke({ color, width: accentWidth, alpha: accentAlpha });
    [-6, 0, 6].forEach((offset) => {
      drawPixiLine(chip, offset, -15, offset, -10, color, accentWidth, accentAlpha);
      drawPixiLine(chip, offset, 10, offset, 15, color, accentWidth, accentAlpha);
      drawPixiLine(chip, -15, offset, -10, offset, color, accentWidth, accentAlpha);
      drawPixiLine(chip, 10, offset, 15, offset, color, accentWidth, accentAlpha);
    });
    container.addChild(chip);
    container.scale.set(1.14);
    return container;
  }

  if (icon === Globe) {
    const globe = new PixiGraphics();
    globe.circle(0, 0, 14).stroke({ color, width: strokeWidth, alpha: strokeAlpha });
    globe.ellipse(0, 0, 6, 14).stroke({ color, width: accentWidth, alpha: accentAlpha });
    globe.ellipse(0, 0, 14, 6).stroke({ color, width: accentWidth, alpha: accentAlpha });
    drawPixiLine(globe, -14, 0, 14, 0, color, accentWidth, accentAlpha);
    drawPixiLine(globe, 0, -14, 0, 14, color, accentWidth, accentAlpha);
    container.addChild(globe);
    container.scale.set(1.14);
    return container;
  }

  if (icon === Network) {
    const lines = new PixiGraphics();
    lines.moveTo(0, -12).lineTo(-12, 9).lineTo(12, 9).lineTo(0, -12).stroke({ color, width: accentWidth, alpha: accentAlpha });
    const nodes = new PixiGraphics();
    nodes.circle(0, -12, 3).fill({ color, alpha: strokeAlpha });
    nodes.circle(-12, 9, 3).fill({ color, alpha: strokeAlpha });
    nodes.circle(12, 9, 3).fill({ color, alpha: strokeAlpha });
    container.addChild(lines, nodes);
    container.scale.set(1.14);
    return container;
  }

  if (icon === Shield) {
    const shield = new PixiGraphics();
    shield.poly([0, -14, 12, -8, 10, 4, 0, 14, -10, 4, -12, -8], true).stroke({ color, width: strokeWidth, alpha: strokeAlpha });
    drawPixiLine(shield, 0, -7, 0, 8, color, accentWidth, accentAlpha);
    drawPixiLine(shield, -4, 0, 0, 4, color, accentWidth, accentAlpha);
    drawPixiLine(shield, 0, 4, 5, -2, color, accentWidth, accentAlpha);
    container.addChild(shield);
    container.scale.set(1.14);
    return container;
  }

  if (icon === Sparkles) {
    const sparkle = new PixiGraphics();
    drawPixiLine(sparkle, 0, -13, 0, 13, color, strokeWidth, strokeAlpha);
    drawPixiLine(sparkle, -13, 0, 13, 0, color, strokeWidth, strokeAlpha);
    drawPixiLine(sparkle, -7, -7, 7, 7, color, accentWidth, accentAlpha);
    drawPixiLine(sparkle, -7, 7, 7, -7, color, accentWidth, accentAlpha);
    const mini = new PixiGraphics();
    mini.circle(10, -10, 1.8).fill({ color, alpha: strokeAlpha });
    mini.circle(-11, 11, 1.8).fill({ color, alpha: strokeAlpha });
    container.addChild(sparkle, mini);
    container.scale.set(1.14);
    return container;
  }

  if (icon === MessageSquare) {
    const bubble = new PixiGraphics();
    bubble.roundRect(-14, -10, 28, 20, 5).stroke({ color, width: strokeWidth, alpha: strokeAlpha });
    bubble.moveTo(-5, 10).lineTo(-2, 15).lineTo(3, 10).stroke({ color, width: strokeWidth, alpha: strokeAlpha });
    const dots = new PixiGraphics();
    dots.circle(-6, 0, 1.8).fill({ color, alpha: strokeAlpha });
    dots.circle(0, 0, 1.8).fill({ color, alpha: strokeAlpha });
    dots.circle(6, 0, 1.8).fill({ color, alpha: strokeAlpha });
    container.addChild(bubble, dots);
    container.scale.set(1.14);
    return container;
  }

  if (icon === GraduationCap) {
    const cap = new PixiGraphics();
    cap.moveTo(-16, -3).lineTo(0, -11).lineTo(16, -3).lineTo(0, 5).lineTo(-16, -3).stroke({ color, width: strokeWidth, alpha: strokeAlpha });
    drawPixiLine(cap, -8, 3, 8, 3, color, accentWidth, accentAlpha);
    drawPixiLine(cap, 11, -4, 11, 9, color, accentWidth, accentAlpha);
    const tassel = new PixiGraphics();
    tassel.circle(11, 11, 1.8).fill({ color, alpha: strokeAlpha });
    container.addChild(cap, tassel);
    container.scale.set(1.14);
    return container;
  }

  if (icon === Target) {
    const target = new PixiGraphics();
    target.circle(0, 0, 14).stroke({ color, width: strokeWidth, alpha: strokeAlpha });
    target.circle(0, 0, 7).stroke({ color, width: accentWidth, alpha: accentAlpha });
    drawPixiLine(target, -16, 0, -9, 0, color, accentWidth, accentAlpha);
    drawPixiLine(target, 9, 0, 16, 0, color, accentWidth, accentAlpha);
    drawPixiLine(target, 0, -16, 0, -9, color, accentWidth, accentAlpha);
    drawPixiLine(target, 0, 9, 0, 16, color, accentWidth, accentAlpha);
    const center = new PixiGraphics();
    center.circle(0, 0, 2.4).fill({ color, alpha: strokeAlpha });
    container.addChild(target, center);
    container.scale.set(1.14);
    return container;
  }

  if (icon === CheckCircle2) {
    const check = new PixiGraphics();
    check.circle(0, 0, 14).stroke({ color, width: strokeWidth, alpha: strokeAlpha });
    check.moveTo(-7, 0).lineTo(-1, 6).lineTo(8, -5).stroke({ color, width: accentWidth, alpha: accentAlpha });
    container.addChild(check);
    container.scale.set(1.14);
    return container;
  }

  if (icon === Lock) {
    const shackle = new PixiGraphics();
    shackle.moveTo(-7.8, 2.2)
      .quadraticCurveTo(-7.8, -11.4, 0, -11.4)
      .quadraticCurveTo(7.8, -11.4, 7.8, 2.2)
      .stroke({ color, width: strokeWidth, alpha: strokeAlpha, cap: "round", join: "round" });
    const body = new PixiGraphics();
    body.roundRect(-10.8, 0, 21.6, 17.4, 7.2).stroke({ color, width: strokeWidth, alpha: strokeAlpha, cap: "round", join: "round" });
    const keyhole = new PixiGraphics();
    keyhole.circle(0, 6.4, 2.8).fill({ color, alpha: strokeAlpha * 0.98 });
    drawPixiLine(keyhole, 0, 9.1, 0, 13.8, color, accentWidth, accentAlpha);
    container.addChild(shackle, body, keyhole);
    container.scale.set(1.06);
    return container;
  }

  const terminal = new PixiGraphics();
  terminal.roundRect(-14, -14, 28, 28, 5).stroke({ color, width: strokeWidth, alpha: strokeAlpha });
  terminal.moveTo(-7, -4).lineTo(-1, 1).lineTo(-7, 6).stroke({ color, width: accentWidth, alpha: accentAlpha });
  drawPixiLine(terminal, 2, 6, 8, 6, color, accentWidth, accentAlpha);
  container.addChild(terminal);
  container.scale.set(1.14);
  return container;
}

function destroyPixiApplicationSafely(app: PixiApplication | null) {
  if (!app) {
    return;
  }

  const managedApp = app as ManagedPixiApplication;
  if (managedApp.__skillsDestroyed) {
    return;
  }

  managedApp.__skillsDestroyed = true;

  if (typeof managedApp._cancelResize !== "function") {
    managedApp._cancelResize = () => undefined;
  }

  try {
    app.destroy(PIXI_RENDERER_DESTROY_OPTIONS, PIXI_SCENE_DESTROY_OPTIONS);
  } catch (error) {
    console.error("[skills][pixi] destroy fallback", error);
  } finally {
    clearPixiGlowTextureCache();
  }
}

function drawPixiNodeLabel(
  node: PositionedSkillNode,
  uiStatus: SkillUiStatus,
  _labelPlacement: ReturnType<typeof getNodeLabelPlacement>,
  isFocus: boolean,
  detailLevel: PixiLabelDetailLevel,
) {
  const container = new PixiContainer();
  const labelWidth = getEstimatedNodeLabelWidth(node.label) + (isFocus ? 20 : 0);
  const textQuality = getPixiLabelTextQuality(detailLevel);
  const labelText = createPixiText(
    node.label,
    {
      fontFamily: "monospace",
      fontSize: textQuality.fontSize,
      fontWeight: "700",
      letterSpacing: textQuality.letterSpacing,
      fill: uiStatus === "TARGET"
        ? 0xf0abfc
        : uiStatus === "MASTERED"
          ? 0x86efac
          : uiStatus === "LEARNING"
            ? 0xc7d2fe
            : uiStatus === "NOT_STARTED"
            ? 0xe2e8f0
            : 0x64748b,
    },
    0.5,
    0.5,
    { renderScale: textQuality.renderScale, resolution: textQuality.resolution },
  );
  const focusIndicator = isFocus
    ? createPixiText(
      "▲",
      {
        fontFamily: "monospace",
        fontSize: textQuality.indicatorFontSize,
        fontWeight: "700",
        fill: 0xf0abfc,
      },
      0.5,
      0.5,
      { renderScale: textQuality.renderScale, resolution: textQuality.resolution },
    )
    : null;
  const background = new PixiGraphics();
  const fillColor = uiStatus === "TARGET"
    ? 0x4a044e
    : uiStatus === "MASTERED"
      ? 0x052e16
      : uiStatus === "LEARNING"
        ? 0x1e1b4b
        : uiStatus === "NOT_STARTED"
          ? 0x0f172a
          : 0x020617;
  const borderColor = uiStatus === "TARGET"
    ? 0xd946ef
    : uiStatus === "MASTERED"
      ? 0x10b981
      : uiStatus === "LEARNING"
        ? 0x6366f1
    : uiStatus === "NOT_STARTED"
      ? 0x334155
      : 0x1e293b;

  background.roundRect(-labelWidth / 2, 0, labelWidth, NODE_LABEL_HEIGHT, 14)
    .fill({ color: fillColor, alpha: 0.92 })
    .stroke({ color: borderColor, width: 1.7, alpha: 0.92 });
  labelText.position.set(0, NODE_LABEL_HEIGHT / 2);
  if (focusIndicator) {
    focusIndicator.position.set(labelText.width / 2 + 12, NODE_LABEL_HEIGHT / 2);
  }
  container.position.set(0, PIXI_NODE_HALF + NODE_LABEL_STACK_GAP);

  container.addChild(background, labelText);
  if (focusIndicator) {
    container.addChild(focusIndicator);
  }
  return container;
}

const pixiGlowTextureCache = new Map<string, PixiTexture>();

function isPixiTextureUsable(texture: PixiTexture | undefined) {
  if (!texture || texture.destroyed) {
    return false;
  }

  return !texture.source.destroyed;
}

function clearPixiGlowTextureCache() {
  pixiGlowTextureCache.forEach((texture) => {
    if (!texture.destroyed) {
      texture.destroy(true);
    }
  });
  pixiGlowTextureCache.clear();
}

function createPixiRadialGlow(
  color: number,
  layers: Array<{ radius: number; alpha: number }>,
) {
  const sortedLayers = [...layers].sort((left, right) => left.radius - right.radius);
  const outerRadius = sortedLayers[sortedLayers.length - 1]?.radius ?? 0;
  const cacheKey = `${color}:${sortedLayers.map((layer) => `${layer.radius.toFixed(2)}-${layer.alpha.toFixed(4)}`).join("|")}`;

  let texture = pixiGlowTextureCache.get(cacheKey);

  if (!isPixiTextureUsable(texture)) {
    pixiGlowTextureCache.delete(cacheKey);
    texture = undefined;
  }

  if (!texture) {
    const padding = Math.ceil(outerRadius * 0.4);
    const diameter = Math.ceil((outerRadius + padding) * 2);
    const canvas = document.createElement("canvas");
    canvas.width = diameter;
    canvas.height = diameter;
    const context = canvas.getContext("2d");

    if (context) {
      const center = diameter / 2;
      const red = (color >> 16) & 255;
      const green = (color >> 8) & 255;
      const blue = color & 255;
      const gradient = context.createRadialGradient(center, center, 0, center, center, outerRadius + padding * 0.4);
      const peakAlpha = Math.max(...sortedLayers.map((layer) => layer.alpha), 0.08);
      gradient.addColorStop(0, `rgba(${red}, ${green}, ${blue}, ${Math.min(1, peakAlpha * 1.1)})`);

      sortedLayers.forEach((layer) => {
        const stop = Math.min(0.98, Math.max(0.04, layer.radius / (outerRadius + padding * 0.4)));
        gradient.addColorStop(stop, `rgba(${red}, ${green}, ${blue}, ${layer.alpha})`);
      });

      gradient.addColorStop(1, `rgba(${red}, ${green}, ${blue}, 0)`);
      context.fillStyle = gradient;
      context.fillRect(0, 0, diameter, diameter);
    }

    texture = PixiTexture.from(canvas, true);
    pixiGlowTextureCache.set(cacheKey, texture);
  }

  const sprite = new PixiSprite(texture);
  sprite.anchor.set(0.5);
  return sprite;
}

function createPixiSoftGlow(color: number, layers: Array<{ radius: number; alpha: number }>) {
  return createPixiRadialGlow(color, layers);
}

function createPixiSelectionAura(scene: PixiSceneState, animateEntrance: boolean) {
  const selectionAura = new PixiContainer();
  const outerGlow = new PixiGraphics();
  outerGlow.circle(0, 0, PIXI_SELECTION_OUTER_GLOW_RADIUS).fill({ color: 0xb91c1c, alpha: 0.18 }).stroke({ color: 0xdc2626, width: 2.8, alpha: 0.42 });
  const dashedRing = createPixiDashedCircle(PIXI_SELECTION_DASHED_RADIUS, 14, 0xef4444, 4.8, 0.9, 0.06);
  const innerRing = new PixiGraphics();
  innerRing.circle(0, 0, PIXI_SELECTION_INNER_RADIUS).stroke({ color: 0xf87171, width: 3.1, alpha: 0.64 });
  const crosshair = new PixiContainer();
  const crosshairShape = new PixiGraphics();
  crosshairShape.circle(0, 0, PIXI_SELECTION_CROSSHAIR_RADIUS).stroke({ color: 0xef4444, width: 3.2, alpha: 0.82 });
  const crosshairOuterOffset = PIXI_SELECTION_CROSSHAIR_RADIUS + 15;
  const crosshairInnerOffset = PIXI_SELECTION_CROSSHAIR_RADIUS * 0.5;
  drawPixiLine(crosshairShape, 0, -crosshairOuterOffset, 0, -crosshairInnerOffset, 0xf87171, 5.2, 0.94);
  drawPixiLine(crosshairShape, 0, crosshairInnerOffset, 0, crosshairOuterOffset, 0xf87171, 5.2, 0.94);
  drawPixiLine(crosshairShape, -crosshairOuterOffset, 0, -crosshairInnerOffset, 0, 0xf87171, 5.2, 0.94);
  drawPixiLine(crosshairShape, crosshairInnerOffset, 0, crosshairOuterOffset, 0, 0xf87171, 5.2, 0.94);
  crosshair.addChild(crosshairShape);
  selectionAura.addChild(outerGlow, dashedRing, innerRing, crosshair);

  scene.animatedTargets.push({ kind: "pulse", displayObject: outerGlow, baseScale: 1, amplitude: 0.09, speed: 0.004, alphaMin: 0.24, alphaMax: 0.76 });
  scene.animatedTargets.push({ kind: "rotate", displayObject: dashedRing, speed: 0.011 });
  scene.animatedTargets.push({ kind: "pulse", displayObject: innerRing, baseScale: 1, amplitude: 0.075, speed: 0.0055, alphaMin: 0.26, alphaMax: 0.76 });
  scene.animatedTargets.push({ kind: "rotate", displayObject: crosshair, speed: -0.0108 });

  if (animateEntrance) {
    selectionAura.scale.set(1.26);
    selectionAura.alpha = 0;
    scene.animatedTargets.push({
      kind: "settle",
      displayObject: selectionAura,
      startScale: 1.26,
      endScale: 1,
      startAlpha: 0,
      endAlpha: 1,
      durationMs: 460,
      startTimeMs: scene.app.ticker.lastTime,
    });
  }

  return selectionAura;
}

function applyPixiHoveredNode(scene: PixiSceneState, hoveredNodeCode: string | null) {
  scene.nodeVisuals.forEach((visual, nodeCode) => {
    const hovered = nodeCode === hoveredNodeCode;
    visual.hoverScaleTarget = hovered ? PIXI_HOVER_SCALE : 1;
    visual.container.zIndex = visual.baseZIndex + (hovered ? 100 : 0);
  });
}

function resetPixiScene(scene: PixiSceneState) {
  scene.treeBaseLayer.clear();
  scene.treeProgressLayer.clear();
  scene.relationBaseLayer.clear();
  scene.relationHighlightLayer.clear();
  clearPixiChildren(scene.nodeLayer);
  scene.nodeVisuals.clear();
  scene.animatedTargets = [];
}

function drawPixiTreeConnections(
  scene: PixiSceneState,
  positionedNodes: PositionedSkillNode[],
  nodeMap: Map<string, PositionedSkillNode>,
) {
  positionedNodes.forEach((node) => {
    if (!node.parentCode) {
      return;
    }

    const parentNode = nodeMap.get(node.parentCode);
    if (!parentNode) {
      return;
    }

    const { controlX, controlY } = getCurveControlPoint(parentNode, node, 0.06);
    scene.treeBaseLayer
      .moveTo(parentNode.x, parentNode.y)
      .quadraticCurveTo(controlX, controlY, node.x, node.y)
      .stroke({ color: 0x1e293b, width: 4.4, alpha: 1 });

    const isMasteredLine = parentNode.status === "MASTERED" && node.status === "MASTERED";
    const isLearningLine = node.unlocked && node.status === "LEARNING";

    if (isMasteredLine) {
      scene.treeProgressLayer
        .moveTo(parentNode.x, parentNode.y)
        .quadraticCurveTo(controlX, controlY, node.x, node.y)
        .stroke({ color: 0x10b981, width: 4.1, alpha: 0.95 });
    } else if (isLearningLine) {
      drawDashedQuadraticCurve(scene.treeProgressLayer, parentNode, node, 0.06, 12, 12);
      scene.treeProgressLayer.stroke({ color: 0x6366f1, width: 3.2, alpha: 0.92 });
    }
  });
}

function drawPixiRelationConnections(
  scene: PixiSceneState,
  positionedRelations: PositionedSkillRelation[],
  selectedNodeCode: string | null,
  focusNodeCode: string | null,
) {
  positionedRelations.forEach((relation) => {
    drawDashedQuadraticCurve(scene.relationBaseLayer, relation.sourceNode, relation.targetNode, 0.14, 7, 12);
    scene.relationBaseLayer.stroke({
      color: 0x334155,
      width: 2.1,
      alpha: 0.2,
    });

    const highlighted = relation.sourceNodeCode === selectedNodeCode
      || relation.targetNodeCode === selectedNodeCode
      || relation.sourceNodeCode === focusNodeCode
      || relation.targetNodeCode === focusNodeCode;

    if (!highlighted) {
      return;
    }

    drawDashedQuadraticCurve(scene.relationHighlightLayer, relation.sourceNode, relation.targetNode, 0.24, 12, 10);
    scene.relationHighlightLayer.stroke({
      color: colorStringToNumber(getRelationStroke(relation.relationType)),
      width: 3.3,
      alpha: 0.9,
    });
  });
}

function drawPixiNodeVisual(
  scene: PixiSceneState,
  node: PositionedSkillNode,
  selectedNodeCode: string | null,
  focusNodeCode: string | null,
  worldCenterX: number,
  worldCenterY: number,
  labelDetailLevel: PixiLabelDetailLevel,
  selectionChanged: boolean,
) {
  try {
    const uiStatus = getNodeUiStatus(node, focusNodeCode);
    const isSelected = selectedNodeCode === node.nodeCode;
    const isFocus = focusNodeCode === node.nodeCode && node.unlocked;
    const isLocked = !node.unlocked;
    const isLearning = node.status === "LEARNING" && node.unlocked;
    const isMastered = node.status === "MASTERED" && node.unlocked;
    const labelPlacement = getNodeLabelPlacement(node, worldCenterX, worldCenterY);
    const nodeContainer = new PixiContainer();
    const symbolColor = isLocked
      ? 0x64748b
      : isFocus
        ? 0xf0abfc
        : isMastered
          ? 0x6ee7b7
          : 0xa5b4fc;

    nodeContainer.position.set(node.x, node.y);
    nodeContainer.scale.set(1);
    nodeContainer.zIndex = isFocus ? 40 : isSelected ? 30 : 10;

    if (isFocus) {
      const focusAura = new PixiContainer();
      const focusRing = new PixiGraphics();
      focusRing.circle(0, 0, PIXI_FOCUS_RING_OUTER_RADIUS).stroke({ color: 0xd946ef, width: 2.8, alpha: 0.48 });
      focusRing.circle(0, 0, PIXI_FOCUS_RING_INNER_RADIUS).stroke({ color: 0xe879f9, width: 2.2, alpha: 0.3 });
      focusRing.rect(-1.8, -(PIXI_FOCUS_RING_OUTER_RADIUS + 2), 3.6, 20).fill({ color: 0xd946ef, alpha: 0.95 });
      focusRing.rect(-1.8, PIXI_FOCUS_RING_OUTER_RADIUS - 18, 3.6, 20).fill({ color: 0xd946ef, alpha: 0.95 });
      focusRing.rect(-(PIXI_FOCUS_RING_OUTER_RADIUS + 2), -1.8, 20, 3.6).fill({ color: 0xd946ef, alpha: 0.95 });
      focusRing.rect(PIXI_FOCUS_RING_OUTER_RADIUS - 18, -1.8, 20, 3.6).fill({ color: 0xd946ef, alpha: 0.95 });
      focusAura.addChild(focusRing);
      nodeContainer.addChild(focusAura);
      scene.animatedTargets.push({ kind: "rotate", displayObject: focusAura, speed: 0.012 });
    }

    const body = new PixiContainer();
    let glow: PixiContainer | PixiGraphics = new PixiGraphics();
    if (isFocus) {
      glow = createPixiSoftGlow(0xd946ef, [
        { radius: PIXI_NODE_SIZE * 0.84, alpha: 0.022 },
        { radius: PIXI_NODE_SIZE * 0.72, alpha: 0.036 },
        { radius: PIXI_NODE_SIZE * 0.58, alpha: 0.062 },
        { radius: PIXI_NODE_SIZE * 0.44, alpha: 0.098 },
      ]);
    } else if (isMastered) {
      glow = createPixiSoftGlow(0x10b981, [
        { radius: PIXI_NODE_SIZE * 0.8, alpha: 0.02 },
        { radius: PIXI_NODE_SIZE * 0.68, alpha: 0.032 },
        { radius: PIXI_NODE_SIZE * 0.54, alpha: 0.052 },
        { radius: PIXI_NODE_SIZE * 0.41, alpha: 0.086 },
      ]);
    } else if (isLearning) {
      glow = createPixiSoftGlow(0x6366f1, [
        { radius: PIXI_NODE_SIZE * 0.84, alpha: 0.024 },
        { radius: PIXI_NODE_SIZE * 0.72, alpha: 0.038 },
        { radius: PIXI_NODE_SIZE * 0.58, alpha: 0.06 },
        { radius: PIXI_NODE_SIZE * 0.45, alpha: 0.1 },
      ]);
    } else if (isLocked) {
      glow = createPixiSoftGlow(0x818cf8, [
        { radius: PIXI_NODE_SIZE * 0.74, alpha: 0.018 },
        { radius: PIXI_NODE_SIZE * 0.62, alpha: 0.03 },
        { radius: PIXI_NODE_SIZE * 0.49, alpha: 0.046 },
        { radius: PIXI_NODE_SIZE * 0.37, alpha: 0.07 },
      ]);
    }

    const diamond = new PixiGraphics();
    drawPixiNodeDiamond(diamond, PIXI_NODE_SIZE);
    diamond.fill({
      color: isFocus
        ? 0x4a044e
        : isMastered
          ? 0x052e16
          : isLearning
            ? 0x1e1b4b
            : 0x0f172a,
      alpha: isLocked ? 0.75 : 0.96,
    }).stroke({
      color: isFocus
        ? 0xd946ef
        : isMastered
          ? 0x10b981
          : isLearning
            ? 0x818cf8
            : 0x334155,
      width: isLocked ? 3.1 : 2.8,
      alpha: isLocked ? 0.52 : 0.95,
    });

    const innerDiamond = new PixiGraphics();
    drawPixiNodeDiamond(innerDiamond, PIXI_NODE_SIZE - 18);
    innerDiamond.stroke({
      color: symbolColor,
      width: isLocked ? 3.8 : 1.55,
      alpha: isLocked ? 0.22 : isFocus ? 0.3 : 0.2,
      cap: "round",
      join: "round",
    });

    const symbol = createPixiNodeSymbol(isLocked ? Lock : node.icon, symbolColor, isLocked);
    body.addChild(glow, diamond, innerDiamond, symbol);
    nodeContainer.addChild(body);

    if (isLearning && !isFocus) {
      const learningPulse = new PixiGraphics();
      learningPulse.circle(0, 0, PIXI_LEARNING_RING_RADIUS).stroke({ color: 0x6366f1, width: 5.2, alpha: 0.74, cap: "round", join: "round" });
      nodeContainer.addChild(learningPulse);
      scene.animatedTargets.push({
        kind: "ping",
        displayObject: learningPulse,
        startScale: 0.66,
        endScale: 1.44,
        alphaStart: 0.88,
        alphaEnd: 0,
        durationMs: 1360,
        delayMs: 120,
        profile: "radar",
      });
    }

    if (isSelected && !isFocus) {
      const selectionAura = createPixiSelectionAura(scene, selectionChanged);
      nodeContainer.addChild(selectionAura);
    }

    const labelContainer = drawPixiNodeLabel(node, uiStatus, labelPlacement, isFocus, labelDetailLevel);
    nodeContainer.addChild(labelContainer);
    scene.nodeLayer.addChild(nodeContainer);
    scene.nodeVisuals.set(node.nodeCode, {
      container: nodeContainer,
      bodyContainer: body,
      labelContainer,
      hoverScaleCurrent: 1,
      hoverScaleTarget: 1,
      baseZIndex: nodeContainer.zIndex,
    });
  } catch (error) {
    const fallbackNode = new PixiContainer();
    fallbackNode.position.set(node.x, node.y);
    const fallbackDiamond = new PixiGraphics();
    drawPixiNodeDiamond(fallbackDiamond, PIXI_NODE_SIZE);
    fallbackDiamond
      .fill({ color: node.unlocked ? 0x1e1b4b : 0x0f172a, alpha: node.unlocked ? 0.94 : 0.76 })
      .stroke({ color: node.unlocked ? 0x818cf8 : 0x334155, width: 2.2, alpha: 0.92 });
    fallbackNode.addChild(fallbackDiamond);
    scene.nodeLayer.addChild(fallbackNode);
    console.error("[skills][pixi] node render fallback", node.nodeCode, error);
  }
}

function finalizePixiScene(scene: PixiSceneState, selectedNodeCode: string | null, hoveredNodeCode: string | null) {
  scene.lastSelectedNodeCode = selectedNodeCode;
  applyPixiHoveredNode(scene, hoveredNodeCode);
}

function redrawPixiScene(
  scene: PixiSceneState,
  positionedNodes: PositionedSkillNode[],
  positionedRelations: PositionedSkillRelation[],
  nodeMap: Map<string, PositionedSkillNode>,
  selectedNodeCode: string | null,
  focusNodeCode: string | null,
  worldCenterX: number,
  worldCenterY: number,
  hoveredNodeCode: string | null,
  labelDetailLevel: PixiLabelDetailLevel,
) {
  const selectionChanged = scene.lastSelectedNodeCode !== selectedNodeCode;
  resetPixiScene(scene);
  drawPixiTreeConnections(scene, positionedNodes, nodeMap);
  drawPixiRelationConnections(scene, positionedRelations, selectedNodeCode, focusNodeCode);

  positionedNodes.forEach((node) => {
    drawPixiNodeVisual(
      scene,
      node,
      selectedNodeCode,
      focusNodeCode,
      worldCenterX,
      worldCenterY,
      labelDetailLevel,
      selectionChanged,
    );
  });

  finalizePixiScene(scene, selectedNodeCode, hoveredNodeCode);
}

function scheduleInitialPixiSceneBuild(
  scene: PixiSceneState,
  positionedNodes: PositionedSkillNode[],
  positionedRelations: PositionedSkillRelation[],
  nodeMap: Map<string, PositionedSkillNode>,
  selectedNodeCode: string | null,
  focusNodeCode: string | null,
  worldCenterX: number,
  worldCenterY: number,
  hoveredNodeCode: string | null,
  labelDetailLevel: PixiLabelDetailLevel,
  onComplete: () => void,
): CancelableTask {
  if (typeof window === "undefined") {
    redrawPixiScene(
      scene,
      positionedNodes,
      positionedRelations,
      nodeMap,
      selectedNodeCode,
      focusNodeCode,
      worldCenterX,
      worldCenterY,
      hoveredNodeCode,
      labelDetailLevel,
    );
    onComplete();
    return { cancel: () => undefined };
  }

  const selectionChanged = scene.lastSelectedNodeCode !== selectedNodeCode;
  resetPixiScene(scene);

  if (positionedNodes.length === 0) {
    drawPixiTreeConnections(scene, positionedNodes, nodeMap);
    drawPixiRelationConnections(scene, positionedRelations, selectedNodeCode, focusNodeCode);
    finalizePixiScene(scene, selectedNodeCode, hoveredNodeCode);
    onComplete();
    return { cancel: () => undefined };
  }

  let cancelled = false;
  let frameId: number | null = null;
  let nodeIndex = 0;
  let phase: "tree" | "relations" | "nodes" = "tree";

  const step = () => {
    if (cancelled) {
      return;
    }

    if (phase === "tree") {
      drawPixiTreeConnections(scene, positionedNodes, nodeMap);
      phase = "relations";
      frameId = window.requestAnimationFrame(step);
      return;
    }

    if (phase === "relations") {
      drawPixiRelationConnections(scene, positionedRelations, selectedNodeCode, focusNodeCode);
      phase = "nodes";
      frameId = window.requestAnimationFrame(step);
      return;
    }

    const frameStart = window.performance.now();
    while (nodeIndex < positionedNodes.length && window.performance.now() - frameStart < PIXI_INITIAL_BUILD_NODE_FRAME_BUDGET_MS) {
      drawPixiNodeVisual(
        scene,
        positionedNodes[nodeIndex],
        selectedNodeCode,
        focusNodeCode,
        worldCenterX,
        worldCenterY,
        labelDetailLevel,
        selectionChanged,
      );
      nodeIndex += 1;
    }

    if (nodeIndex < positionedNodes.length) {
      frameId = window.requestAnimationFrame(step);
      return;
    }

    finalizePixiScene(scene, selectedNodeCode, hoveredNodeCode);
    onComplete();
  };

  frameId = window.requestAnimationFrame(step);

  return {
    cancel: () => {
      cancelled = true;
      if (frameId !== null) {
        window.cancelAnimationFrame(frameId);
      }
    },
  };
}

function getStatusLabel(uiStatus: SkillUiStatus) {
  switch (uiStatus) {
    case "LOCKED":
      return "LOCKED // 待解锁";
    case "NOT_STARTED":
      return "READY // 未开始";
    case "LEARNING":
      return "LEARNING // 学习中";
    case "MASTERED":
      return "MASTERED // 已掌握";
    case "TARGET":
      return "TRACKING // 当前主攻";
    default:
      return uiStatus;
  }
}

function buildNodePath(nodeMap: Map<string, PositionedSkillNode>, node: PositionedSkillNode | null) {
  if (!node) {
    return [];
  }

  const path: PositionedSkillNode[] = [];
  let current: PositionedSkillNode | undefined | null = node;

  while (current) {
    path.unshift(current);
    current = current.parentCode ? nodeMap.get(current.parentCode) : null;
  }

  return path;
}

function getLoadErrorMessage(error: unknown) {
  if (error instanceof ApiClientError) {
    if (error.status === 403) {
      return "当前账号没有权限查看技能星图。";
    }

    return error.message || "技能星图加载失败，请稍后重试。";
  }

  return "技能星图加载失败，请稍后重试。";
}

function getMutationErrorMessage(error: unknown) {
  if (error instanceof ApiClientError) {
    if (error.code === "BIZ-1201") {
      return "前置技能还没掌握，先把父节点点亮为“已掌握”再继续。";
    }

    if (error.code === "BIZ-1202") {
      return "这个节点下面还有学习中的子节点，先回收子节点状态，再重置当前节点。";
    }

    if (error.code === "BIZ-1002") {
      return "技能节点不存在，或当前学生账号状态失效，请刷新后重试。";
    }

    if (error.code === "BIZ-1001") {
      return "技能状态变更参数不合法，请刷新页面后重试。";
    }

    return error.message || "技能进度更新失败，请稍后重试。";
  }

  return "技能进度更新失败，请稍后重试。";
}

function getSuccessMessage(label: string, targetStatus: SkillStatus) {
  if (targetStatus === "LEARNING") {
    return `已将「${label}」推进到学习中。`;
  }

  if (targetStatus === "MASTERED") {
    return `已将「${label}」标记为已掌握。`;
  }

  return `已将「${label}」回退为未开始。`;
}

export default function SkillsPage({
  mode = "student",
}: {
  mode?: SkillsPageMode;
}) {
  // 页面同时服务学生星图和管理员预览，mode 决定接口、返回入口和可操作范围。
  const { role, userId, displayName } = useAuth();
  const [searchParams] = useSearchParams();
  const [pageState, setPageState] = useState<PageState>("loading");
  const [loadError, setLoadError] = useState<string | null>(null);
  const [tree, setTree] = useState<SkillTreeResponse | null>(null);
  const [layoutResult, setLayoutResult] = useState<SkillLayoutResult>(() => createEmptySkillLayoutResult());
  const [layoutPreparing, setLayoutPreparing] = useState(false);
  const [sceneBootstrapReady, setSceneBootstrapReady] = useState(false);
  const [profile, setProfile] = useState<StudentProfileSummary | null>(null);
  const [selectedNodeCode, setSelectedNodeCode] = useState<string | null>(null);
  const [detailNodeCode, setDetailNodeCode] = useState<string | null>(null);
  const [focusNodeCode, setFocusNodeCode] = useState<string | null>(() => readStoredFocusSkillCode());
  const [notice, setNotice] = useState<NoticeState | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [pendingNodeCode, setPendingNodeCode] = useState<string | null>(null);
  const [viewportReady, setViewportReady] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [viewportAnimating, setViewportAnimating] = useState(false);
  const [customCursorEnabled, setCustomCursorEnabled] = useState(false);
  const [customCursorVisible, setCustomCursorVisible] = useState(false);
  const [cursorMode, setCursorMode] = useState<CursorMode>("idle");
  const [hoveredNodeCode, setHoveredNodeCode] = useState<string | null>(null);
  const [pixiReadyVersion, setPixiReadyVersion] = useState(0);
  const [labelDetailLevel, setLabelDetailLevel] = useState<PixiLabelDetailLevel>(() => getPixiLabelDetailLevel(1));
  const { notifySkillsPageLoadingState } = useSkillsRouteTransition();
  // 管理员预览可通过 query 直接定位节点，学生侧仍以本地 focus 和学习进度为主。
  const previewSelectedNodeCode = normalizeSearchNodeCode(searchParams.get("previewNodeCode") ?? searchParams.get("nodeCode"));
  const previewFocusNodeCode = normalizeSearchNodeCode(searchParams.get("focusNodeCode"));
  const isAdminPreviewMode = mode === "admin-preview";
  const backHref = isAdminPreviewMode ? "/admin/skills" : "/student/dashboard";
  const backLabel = isAdminPreviewMode ? "返回技能治理" : "退出星图";
  const primaryLocateLabel = isAdminPreviewMode ? "定位预览节点" : "追踪主攻目标";

  const containerRef = useRef<HTMLDivElement | null>(null);
  const canvasHostRef = useRef<HTMLDivElement | null>(null);
  const minorGridRef = useRef<HTMLDivElement | null>(null);
  const majorGridRef = useRef<HTMLDivElement | null>(null);
  const zoomLabelRef = useRef<HTMLSpanElement | null>(null);
  const treeRef = useRef<SkillTreeResponse | null>(tree);
  const focusNodeCodeRef = useRef<string | null>(focusNodeCode);
  const selectedNodeCodeRef = useRef<string | null>(selectedNodeCode);
  const hoveredNodeCodeRef = useRef<string | null>(hoveredNodeCode);
  const labelDetailLevelRef = useRef<PixiLabelDetailLevel>(labelDetailLevel);
  const layoutBuildVersionRef = useRef(0);
  const pixiRenderSnapshotRef = useRef<PixiRenderSnapshot | null>(null);
  const viewportRef = useRef<ViewportState>({ x: 0, y: 0, scale: 1 });
  const pixiSceneRef = useRef<PixiSceneState | null>(null);
  const pixiBootstrapTaskRef = useRef<CancelableTask | null>(null);
  const viewportAnimationControlsRef = useRef<{ stop: () => void } | null>(null);
  const viewportFrameRef = useRef<number | null>(null);
  const pendingViewportRef = useRef<ViewportState | null>(null);
  const detailSwitchTimerRef = useRef<number | null>(null);
  const nodePointerCandidateRef = useRef<NodePointerCandidate | null>(null);
  const cursorModeRef = useRef<CursorMode>("idle");
  const cursorVisibleRef = useRef(false);
  const dragStateRef = useRef<DragState>({
    active: false,
    pointerId: null,
    startX: 0,
    startY: 0,
    originX: 0,
    originY: 0,
    lastX: 0,
    lastY: 0,
    lastTime: 0,
    velocityX: 0,
    velocityY: 0,
  });
  const cursorFrameRef = useRef<number | null>(null);
  const pendingCursorPointRef = useRef<{ x: number; y: number } | null>(null);
  const adjustZoomRef = useRef<(delta: number, anchorX?: number, anchorY?: number) => void>(() => undefined);
  const rawCursorX = useMotionValue(CURSOR_HIDDEN_POSITION);
  const rawCursorY = useMotionValue(CURSOR_HIDDEN_POSITION);
  const cursorAuraX = useSpring(rawCursorX, { stiffness: 1480, damping: 58, mass: 0.12, restDelta: 0.0001 });
  const cursorAuraY = useSpring(rawCursorY, { stiffness: 1480, damping: 58, mass: 0.12, restDelta: 0.0001 });

  // 高频事件回调读 ref，避免闭包拿到旧的树、选中节点或 hover 状态。
  useEffect(() => {
    treeRef.current = tree;
  }, [tree]);

  useEffect(() => {
    focusNodeCodeRef.current = focusNodeCode;
  }, [focusNodeCode]);

  useEffect(() => {
    selectedNodeCodeRef.current = selectedNodeCode;
  }, [selectedNodeCode]);

  useEffect(() => {
    hoveredNodeCodeRef.current = hoveredNodeCode;
  }, [hoveredNodeCode]);

  useEffect(() => {
    labelDetailLevelRef.current = labelDetailLevel;
  }, [labelDetailLevel]);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    try {
      if (focusNodeCode) {
        // 主攻节点只做浏览器本地偏好，不写回后端学习进度。
        window.localStorage.setItem(FOCUS_STORAGE_KEY, focusNodeCode);
      } else {
        window.localStorage.removeItem(FOCUS_STORAGE_KEY);
      }
    } catch {
      // ignore
    }
  }, [focusNodeCode]);

  const updateCursorMode = (nextMode: CursorMode) => {
    if (cursorModeRef.current === nextMode) {
      return;
    }

    cursorModeRef.current = nextMode;
    setCursorMode(nextMode);
  };

  const updateCursorVisibility = (nextVisible: boolean) => {
    if (cursorVisibleRef.current === nextVisible) {
      return;
    }

    cursorVisibleRef.current = nextVisible;
    setCustomCursorVisible(nextVisible);
  };

  const updateHoveredNodeCode = (nextNodeCode: string | null) => {
    if (hoveredNodeCodeRef.current === nextNodeCode) {
      return;
    }

    hoveredNodeCodeRef.current = nextNodeCode;
    setHoveredNodeCode(nextNodeCode);
  };

  const resolveCursorMode = (target: EventTarget | null, draggingActive: boolean) => {
    if (draggingActive) {
      return "drag" satisfies CursorMode;
    }

    if (hoveredNodeCodeRef.current) {
      return "interactive" satisfies CursorMode;
    }

    return getCursorModeFromTarget(target, false);
  };

  const clearPendingCursorFrame = () => {
    if (typeof window !== "undefined" && cursorFrameRef.current !== null) {
      window.cancelAnimationFrame(cursorFrameRef.current);
    }

    cursorFrameRef.current = null;
    pendingCursorPointRef.current = null;
  };

  const flushPendingCursorPoint = () => {
    const point = pendingCursorPointRef.current;
    if (!point) {
      return;
    }

    rawCursorX.set(point.x);
    rawCursorY.set(point.y);
    pendingCursorPointRef.current = null;
  };

  const scheduleCursorPoint = (x: number, y: number) => {
    if (typeof window === "undefined") {
      rawCursorX.set(x);
      rawCursorY.set(y);
      return;
    }

    pendingCursorPointRef.current = { x, y };

    // 光标位置按 rAF 合并更新，鼠标高频移动时不让 React 状态跟着抖。
    if (cursorFrameRef.current !== null) {
      return;
    }

    cursorFrameRef.current = window.requestAnimationFrame(() => {
      cursorFrameRef.current = null;
      flushPendingCursorPoint();
    });
  };

  useEffect(() => {
    if (!notice) {
      return undefined;
    }

    const timeoutId = window.setTimeout(() => {
      setNotice(null);
    }, 3200);

    return () => {
      window.clearTimeout(timeoutId);
    };
  }, [notice]);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    const mediaQuery = window.matchMedia("(pointer: fine)");
    const sync = () => {
      const enabled = mediaQuery.matches;
      setCustomCursorEnabled(enabled);

      if (!enabled) {
        rawCursorX.set(CURSOR_HIDDEN_POSITION);
        rawCursorY.set(CURSOR_HIDDEN_POSITION);
        updateCursorVisibility(false);
        updateCursorMode("idle");
      }
    };

    sync();

    if (typeof mediaQuery.addEventListener === "function") {
      mediaQuery.addEventListener("change", sync);
      return () => mediaQuery.removeEventListener("change", sync);
    }

    mediaQuery.addListener(sync);
    return () => mediaQuery.removeListener(sync);
  }, [rawCursorX, rawCursorY]);

  useEffect(() => {
    const buildVersion = layoutBuildVersionRef.current + 1;
    layoutBuildVersionRef.current = buildVersion;

    if (!tree) {
      setLayoutPreparing(false);
      setLayoutResult(createEmptySkillLayoutResult());
      return;
    }

    if (tree.nodes.length === 0) {
      setLayoutPreparing(false);
      setLayoutResult(createEmptySkillLayoutResult());
      return;
    }

    let cancelled = false;
    setLayoutPreparing(true);
    let activeWorkerCleanup: (() => void) | undefined;

    const prepareLayout = async () => {
      // 等容器和字体稳定后再计算几何，首屏不会出现明显跳位。
      await waitForAnimationFrames(SKILLS_LAYOUT_PREPARE_FRAME_COUNT);
      await waitForAnimationFrames(1);

      if (cancelled || layoutBuildVersionRef.current !== buildVersion) {
        return;
      }

      const workerNodes: SkillLayoutNodeInput[] = tree.nodes.map((node) => ({
        nodeCode: node.nodeCode,
        label: node.label,
        parentCode: node.parentCode,
        sortOrder: node.sortOrder,
      }));

      const computeLayoutOnMainThread = async () => {
        // Worker 不可用或失败时退回主线程空闲期计算，保证页面仍可用。
        await waitForBrowserIdle();

        if (cancelled || layoutBuildVersionRef.current !== buildVersion) {
          return;
        }

        const geometry = buildSkillLayoutGeometry(workerNodes, SKILL_LAYOUT_PRESETS);
        if (cancelled || layoutBuildVersionRef.current !== buildVersion) {
          return;
        }

        setLayoutResult(buildSkillLayoutResultFromGeometry(tree.nodes, geometry));
        setLayoutPreparing(false);
      };

      if (typeof Worker === "undefined" || typeof window === "undefined") {
        await computeLayoutOnMainThread();
        return;
      }

      // 大树布局默认交给 Worker，避免阻塞星图页面首屏交互。
      const worker = new Worker(new URL("./skills/skillLayout.worker.ts", import.meta.url), { type: "module" });

      const finishWithGeometry = (geometry: SkillLayoutGeometryResult) => {
        if (cancelled || layoutBuildVersionRef.current !== buildVersion) {
          return;
        }

        setLayoutResult(buildSkillLayoutResultFromGeometry(tree.nodes, geometry));
        setLayoutPreparing(false);
      };

      const handleMessage = (event: MessageEvent<SkillLayoutWorkerResponse>) => {
        if (event.data.requestId !== buildVersion) {
          return;
        }

        cleanupWorkerHandle();
        finishWithGeometry(event.data.layout);
      };

      const handleError = () => {
        cleanupWorkerHandle();
        void computeLayoutOnMainThread();
      };

      const cleanupWorkerHandle = () => {
        worker.removeEventListener("message", handleMessage);
        worker.removeEventListener("error", handleError);
        worker.terminate();
        if (activeWorkerCleanup === cleanupWorkerHandle) {
          activeWorkerCleanup = undefined;
        }
      };

      activeWorkerCleanup = cleanupWorkerHandle;
      worker.addEventListener("message", handleMessage);
      worker.addEventListener("error", handleError);
      worker.postMessage({
        requestId: buildVersion,
        nodes: workerNodes,
        presets: SKILL_LAYOUT_PRESETS,
      });

    };

    void prepareLayout();

    return () => {
      cancelled = true;
      activeWorkerCleanup?.();
    };
  }, [tree]);

  const positionedNodes = layoutResult.nodes;
  const worldWidth = layoutResult.width;
  const worldHeight = layoutResult.height;
  const worldCenterX = layoutResult.centerX;
  const worldCenterY = layoutResult.centerY;

  const nodeMap = useMemo(() => {
    return new Map(positionedNodes.map((node) => [node.nodeCode, node]));
  }, [positionedNodes]);

  const positionedRelations = useMemo(() => {
    return (tree?.relations ?? [])
      .map((relation) => {
        const sourceNode = nodeMap.get(relation.sourceNodeCode);
        const targetNode = nodeMap.get(relation.targetNodeCode);
        if (!sourceNode || !targetNode) {
          return null;
        }

        return {
          ...relation,
          sourceNode,
          targetNode,
        } satisfies PositionedSkillRelation;
      })
      .filter((relation): relation is PositionedSkillRelation => Boolean(relation))
      .sort((left, right) => left.sortOrder - right.sortOrder || left.sourceNodeCode.localeCompare(right.sourceNodeCode));
  }, [nodeMap, tree?.relations]);

  const coreNodeCode = useMemo(() => resolveCoreNodeCode(tree?.nodes ?? []), [tree?.nodes]);
  const detailNode = detailNodeCode ? nodeMap.get(detailNodeCode) ?? null : null;
  const focusNode = focusNodeCode ? nodeMap.get(focusNodeCode) ?? null : null;
  const coreNode = coreNodeCode ? nodeMap.get(coreNodeCode) ?? null : null;
  const detailParent = detailNode?.parentCode ? nodeMap.get(detailNode.parentCode) ?? null : null;
  const detailPath = useMemo(() => buildNodePath(nodeMap, detailNode), [nodeMap, detailNode]);
  const detailResources = detailNode?.resources ?? [];
  const detailRelations = useMemo(() => {
    if (!detailNode) {
      return [];
    }

    return positionedRelations.filter((relation) => (
      relation.sourceNodeCode === detailNode.nodeCode || relation.targetNodeCode === detailNode.nodeCode
    ));
  }, [detailNode, positionedRelations]);

  const heroName = isAdminPreviewMode ? "管理员预览视角" : buildStudentNickname(profile, displayName, "同学");
  const treeSummary = tree?.summary ?? {
    total: 0,
    mastered: 0,
    learning: 0,
    notStarted: 0,
  };
  const portraitEvidence = profile?.portrait?.evidence;
  const focusLabel = focusNode?.label ?? "等待选择";
  const hudPercent = treeSummary.total
    ? Math.round((((treeSummary.mastered ?? 0) + (treeSummary.learning ?? 0) * 0.5) / treeSummary.total) * 100)
    : 0;
  const unlockedCount = tree?.nodes.filter((node) => node.unlocked).length ?? 0;
  const detailStatus = detailNode ? getNodeUiStatus(detailNode, focusNodeCode) : null;
  const actionButtons = isAdminPreviewMode ? [] : buildActionButtons(detailNode);

  const applyViewportToDom = (nextViewport: ViewportState) => {
    viewportRef.current = nextViewport;

    const nextLabelDetailLevel = getPixiLabelDetailLevel(nextViewport.scale);
    if (labelDetailLevelRef.current !== nextLabelDetailLevel) {
      labelDetailLevelRef.current = nextLabelDetailLevel;
      setLabelDetailLevel(nextLabelDetailLevel);
    }

    const scene = pixiSceneRef.current;
    if (scene) {
      scene.world.position.set(nextViewport.x, nextViewport.y);
      scene.world.scale.set(nextViewport.scale);
    }

    const minorGridSize = Math.max(28, 100 * nextViewport.scale);
    const majorGridSize = Math.max(140, minorGridSize * 5);

    if (minorGridRef.current) {
      minorGridRef.current.style.backgroundSize = `${minorGridSize}px ${minorGridSize}px`;
      minorGridRef.current.style.backgroundPosition = `${nextViewport.x}px ${nextViewport.y}px`;
    }

    if (majorGridRef.current) {
      majorGridRef.current.style.backgroundSize = `${majorGridSize}px ${majorGridSize}px`;
      majorGridRef.current.style.backgroundPosition = `${nextViewport.x}px ${nextViewport.y}px`;
    }

    if (zoomLabelRef.current) {
      zoomLabelRef.current.textContent = `${Math.round(nextViewport.scale * 100)}%`;
    }
  };

  useEffect(() => {
    let disposed = false;
    let app: PixiApplication | null = null;

    const initPixi = async () => {
      const host = canvasHostRef.current;
      if (!host) {
        return;
      }

      // Pixi 只负责高密度节点和连线绘制，DOM 继续承载工具栏和详情面板。
      const nextApp = new PixiApplication();
      await nextApp.init({
        width: Math.max(1, host.clientWidth),
        height: Math.max(1, host.clientHeight),
        backgroundAlpha: 0,
        antialias: true,
        autoDensity: true,
        autoStart: false,
        preference: "webgl",
        powerPreference: "high-performance",
      });

      if (disposed) {
        destroyPixiApplicationSafely(nextApp);
        return;
      }

      app = nextApp;

      host.replaceChildren(app.canvas);
      app.canvas.style.width = "100%";
      app.canvas.style.height = "100%";
      app.canvas.style.pointerEvents = "none";
      app.canvas.style.display = "block";

      const world = new PixiContainer();
      const treeBaseLayer = new PixiGraphics();
      const treeProgressLayer = new PixiGraphics();
      const relationBaseLayer = new PixiGraphics();
      const relationHighlightLayer = new PixiGraphics();
      const nodeLayer = new PixiContainer();
      nodeLayer.sortableChildren = true;
      world.addChild(treeBaseLayer, relationBaseLayer, relationHighlightLayer, treeProgressLayer, nodeLayer);
      app.stage.addChild(world);

      const scene: PixiSceneState = {
        app,
        world,
        treeBaseLayer,
        treeProgressLayer,
        relationBaseLayer,
        relationHighlightLayer,
        nodeLayer,
        nodeVisuals: new Map(),
        animatedTargets: [],
        tickerHandler: null,
        lastSelectedNodeCode: null,
      };

      scene.tickerHandler = () => {
        const elapsedRatio = app ? app.ticker.elapsedMS / 16.6667 : 1;
        const time = app ? app.ticker.lastTime : 0;

        // 旋转、脉冲、雷达圈和 hover 缩放集中在 ticker，避免散落多个动画计时器。
        scene.animatedTargets.forEach((target) => {
          if (target.kind === "rotate") {
            target.displayObject.rotation += target.speed * elapsedRatio;
            return;
          }

          if (target.kind === "settle") {
            const progress = Math.min(1, Math.max(0, (time - target.startTimeMs) / target.durationMs));
            const eased = easeOutBack(progress);
            const scale = target.startScale + ((target.endScale - target.startScale) * eased);
            target.displayObject.scale.set(scale);
            target.displayObject.alpha = target.startAlpha + ((target.endAlpha - target.startAlpha) * Math.min(1, progress * 1.18));
            return;
          }

          if (target.kind === "ping") {
            const shiftedTime = time + (target.delayMs ?? 0);
            const progress = (shiftedTime % target.durationMs) / target.durationMs;
            const eased = target.profile === "radar"
              ? getRadarPingProgress(progress)
              : 1 - ((1 - progress) ** 3);
            const scale = target.startScale + ((target.endScale - target.startScale) * eased);
            target.displayObject.scale.set(scale);
            target.displayObject.alpha = target.alphaStart + ((target.alphaEnd - target.alphaStart) * eased);
            return;
          }

          const wave = (Math.sin(time * target.speed) + 1) / 2;
          const scale = target.baseScale + ((wave - 0.5) * 2 * target.amplitude);
          target.displayObject.scale.set(scale);
          target.displayObject.alpha = target.alphaMin + (wave * (target.alphaMax - target.alphaMin));
        });

        scene.nodeVisuals.forEach((visual) => {
          const delta = visual.hoverScaleTarget - visual.hoverScaleCurrent;
          if (Math.abs(delta) <= 0.0008) {
            if (visual.hoverScaleCurrent !== visual.hoverScaleTarget) {
              visual.hoverScaleCurrent = visual.hoverScaleTarget;
              visual.container.scale.set(visual.hoverScaleCurrent);
            }
            return;
          }

          visual.hoverScaleCurrent += delta * Math.min(1, PIXI_HOVER_EASING * elapsedRatio);
          visual.container.scale.set(visual.hoverScaleCurrent);
        });
      };

      app.ticker.add(scene.tickerHandler);
      app.start();
      pixiSceneRef.current = scene;
      applyViewportToDom(viewportRef.current);
      setPixiReadyVersion((current) => current + 1);
    };

    void initPixi();

    return () => {
      disposed = true;
      pixiBootstrapTaskRef.current?.cancel();
      pixiBootstrapTaskRef.current = null;
      pixiRenderSnapshotRef.current = null;

      const scene = pixiSceneRef.current;
      if (scene && app && scene.app === app) {
        if (scene.tickerHandler) {
          app.ticker.remove(scene.tickerHandler);
        }
        app.stop();
        pixiSceneRef.current = null;
      }

      if (app) {
        destroyPixiApplicationSafely(app);
        app = null;
      }
    };
  }, []);

  useEffect(() => {
    const scene = pixiSceneRef.current;
    if (!scene || !tree || layoutPreparing) {
      return;
    }

    const renderSnapshot = {
      positionedNodes,
      positionedRelations,
      nodeMap,
      selectedNodeCode,
      focusNodeCode,
      worldCenterX,
      worldCenterY,
      labelDetailLevel,
    } satisfies PixiRenderSnapshot;

    pixiBootstrapTaskRef.current?.cancel();
    pixiBootstrapTaskRef.current = null;

    if (pageState === "loading") {
      // 首次渲染分帧创建节点，loading 结束前先把 Pixi 场景准备好。
      pixiRenderSnapshotRef.current = null;
      setSceneBootstrapReady(false);
      let activeTask: CancelableTask | null = null;
      const task = scheduleInitialPixiSceneBuild(
        scene,
        positionedNodes,
        positionedRelations,
        nodeMap,
        selectedNodeCode,
        focusNodeCode,
        worldCenterX,
        worldCenterY,
        hoveredNodeCodeRef.current,
        labelDetailLevel,
        () => {
          if (pixiBootstrapTaskRef.current === activeTask) {
            pixiBootstrapTaskRef.current = null;
          }
          pixiRenderSnapshotRef.current = renderSnapshot;
          setSceneBootstrapReady(true);
        },
      );
      activeTask = task;
      pixiBootstrapTaskRef.current = task;

      return () => {
        task.cancel();
        if (pixiBootstrapTaskRef.current === task) {
          pixiBootstrapTaskRef.current = null;
        }
      };
    }

    if (sceneBootstrapReady && isSamePixiRenderSnapshot(pixiRenderSnapshotRef.current, renderSnapshot)) {
      return;
    }

    redrawPixiScene(
      scene,
      positionedNodes,
      positionedRelations,
      nodeMap,
      selectedNodeCode,
      focusNodeCode,
      worldCenterX,
      worldCenterY,
      hoveredNodeCodeRef.current,
      labelDetailLevel,
    );
    pixiRenderSnapshotRef.current = renderSnapshot;
    setSceneBootstrapReady(true);
  }, [
    focusNodeCode,
    labelDetailLevel,
    layoutPreparing,
    nodeMap,
    pageState,
    pixiReadyVersion,
    positionedNodes,
    positionedRelations,
    selectedNodeCode,
    sceneBootstrapReady,
    tree,
    worldCenterX,
    worldCenterY,
  ]);

  useEffect(() => {
    const scene = pixiSceneRef.current;
    if (!scene || pageState === "loading" || !sceneBootstrapReady) {
      return;
    }

    applyPixiHoveredNode(scene, hoveredNodeCode);
  }, [hoveredNodeCode, pageState, pixiReadyVersion, sceneBootstrapReady]);

  useEffect(() => {
    return () => {
      pixiBootstrapTaskRef.current?.cancel();
      pixiBootstrapTaskRef.current = null;
    };
  }, []);

  const clearPendingViewportFrame = () => {
    if (typeof window !== "undefined" && viewportFrameRef.current !== null) {
      window.cancelAnimationFrame(viewportFrameRef.current);
    }

    viewportFrameRef.current = null;
    pendingViewportRef.current = null;
  };

  const flushPendingViewport = () => {
    const nextViewport = pendingViewportRef.current;
    if (!nextViewport) {
      return;
    }

    applyViewportToDom(nextViewport);
    pendingViewportRef.current = null;
  };

  const scheduleViewportApply = (nextViewport: ViewportState) => {
    if (typeof window === "undefined") {
      applyViewportToDom(nextViewport);
      return;
    }

    pendingViewportRef.current = nextViewport;

    if (viewportFrameRef.current !== null) {
      return;
    }

    viewportFrameRef.current = window.requestAnimationFrame(() => {
      viewportFrameRef.current = null;
      flushPendingViewport();
    });
  };

  const clearDetailSwitchTimer = () => {
    if (typeof window === "undefined" || detailSwitchTimerRef.current === null) {
      return;
    }

    window.clearTimeout(detailSwitchTimerRef.current);
    detailSwitchTimerRef.current = null;
  };

  const stopViewportAnimation = () => {
    viewportAnimationControlsRef.current?.stop();
    viewportAnimationControlsRef.current = null;
    setViewportAnimating(false);
  };

  const startViewportInertia = (initialVelocityX: number, initialVelocityY: number) => {
    if (typeof window === "undefined") {
      return;
    }

    const container = containerRef.current;
    if (!container) {
      return;
    }

    let velocityX = Math.max(-DRAG_INERTIA_MAX_SPEED, Math.min(DRAG_INERTIA_MAX_SPEED, initialVelocityX));
    let velocityY = Math.max(-DRAG_INERTIA_MAX_SPEED, Math.min(DRAG_INERTIA_MAX_SPEED, initialVelocityY));
    if (Math.hypot(velocityX, velocityY) < DRAG_INERTIA_MIN_SPEED) {
      return;
    }

    const width = Math.max(1, container.clientWidth);
    const height = Math.max(1, container.clientHeight);
    let animationFrameId = 0;
    let stopped = false;
    let lastTimestamp = window.performance.now();

    const stop = () => {
      if (stopped) {
        return;
      }

      stopped = true;
      window.cancelAnimationFrame(animationFrameId);
      viewportAnimationControlsRef.current = null;
      clearPendingViewportFrame();
      setViewportAnimating(false);
    };

    const step = (timestamp: number) => {
      if (stopped) {
        return;
      }

      const deltaMs = Math.min(34, Math.max(8, timestamp - lastTimestamp));
      lastTimestamp = timestamp;

      const damping = Math.exp(-deltaMs / DRAG_INERTIA_DECAY_MS);
      velocityX *= damping;
      velocityY *= damping;

      const currentViewport = viewportRef.current;
      const nextViewport = clampViewport(
        {
          scale: currentViewport.scale,
          x: currentViewport.x + velocityX * deltaMs,
          y: currentViewport.y + velocityY * deltaMs,
        },
        width,
        height,
        worldWidth,
        worldHeight,
      );

      if (nextViewport.x === currentViewport.x) {
        velocityX = 0;
      }

      if (nextViewport.y === currentViewport.y) {
        velocityY = 0;
      }

      applyViewportToDom(nextViewport);

      if (Math.hypot(velocityX, velocityY) < DRAG_INERTIA_STOP_SPEED) {
        stop();
        return;
      }

      animationFrameId = window.requestAnimationFrame(step);
    };

    setViewportAnimating(true);
    setViewportReady(true);
    clearPendingViewportFrame();
    viewportAnimationControlsRef.current?.stop();
    viewportAnimationControlsRef.current = { stop };
    animationFrameId = window.requestAnimationFrame(step);
  };

  const animateViewportTo = (nextViewport: ViewportState, durationMs = 980) => {
    const startViewport = viewportRef.current;

    setViewportAnimating(true);
    setViewportReady(true);
    clearPendingViewportFrame();
    viewportAnimationControlsRef.current?.stop();
    viewportAnimationControlsRef.current = animate(0, 1, {
      duration: durationMs / 1000,
      ease: PIXI_CAMERA_EASING,
      onUpdate: (progress) => {
        const nextFrameViewport = {
          x: startViewport.x + (nextViewport.x - startViewport.x) * progress,
          y: startViewport.y + (nextViewport.y - startViewport.y) * progress,
          scale: startViewport.scale + (nextViewport.scale - startViewport.scale) * progress,
        };

        applyViewportToDom(nextFrameViewport);
      },
      onComplete: () => {
        viewportAnimationControlsRef.current = null;
        applyViewportToDom(nextViewport);
        setViewportAnimating(false);
      },
    });
  };

  const centerOnNode = (
    nodeCode: string | null,
    scale = viewportRef.current.scale,
    animated = true,
    durationMs = 980,
  ) => {
    const container = containerRef.current;
    if (!container || !nodeCode) {
      return;
    }

    const targetNode = nodeMap.get(nodeCode);
    if (!targetNode) {
      return;
    }

    const rect = container.getBoundingClientRect();
    const nextViewport = buildCenteredViewport(
      rect.width,
      rect.height,
      targetNode.x,
      targetNode.y,
      clampScale(scale),
      worldWidth,
      worldHeight,
    );

    if (animated) {
      animateViewportTo(nextViewport, durationMs);
      return;
    }

    stopViewportAnimation();
    clearPendingViewportFrame();
    applyViewportToDom(nextViewport);
    setViewportReady(true);
  };

  const adjustZoom = (delta: number, anchorX?: number, anchorY?: number) => {
    const container = containerRef.current;
    if (!container) {
      return;
    }

    stopViewportAnimation();
    clearPendingViewportFrame();

    const rect = container.getBoundingClientRect();
    const nextAnchorX = anchorX ?? rect.width / 2;
    const nextAnchorY = anchorY ?? rect.height / 2;
    const current = viewportRef.current;
    const nextScale = clampScale(current.scale + delta);
    if (nextScale === current.scale) {
      return;
    }

    const scaleRatio = nextScale / current.scale;
    const nextX = nextAnchorX - (nextAnchorX - current.x) * scaleRatio;
    const nextY = nextAnchorY - (nextAnchorY - current.y) * scaleRatio;

    applyViewportToDom(clampViewport(
      { scale: nextScale, x: nextX, y: nextY },
      rect.width,
      rect.height,
      worldWidth,
      worldHeight,
    ));

    setViewportReady(true);
  };

  useEffect(() => {
    adjustZoomRef.current = adjustZoom;
  });

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }

    const handleWheel = (event: WheelEvent) => {
      if (!treeRef.current) {
        return;
      }

      event.preventDefault();
      const rect = container.getBoundingClientRect();
      adjustZoomRef.current(-event.deltaY * 0.0013, event.clientX - rect.left, event.clientY - rect.top);
    };

    container.addEventListener("wheel", handleWheel, { passive: false });
    return () => container.removeEventListener("wheel", handleWheel);
  }, []);

  useEffect(() => {
    return () => {
      viewportAnimationControlsRef.current?.stop();
      viewportAnimationControlsRef.current = null;
      clearDetailSwitchTimer();
      clearPendingViewportFrame();
      clearPendingCursorFrame();
    };
  }, []);

  useEffect(() => {
    notifySkillsPageLoadingState(pageState === "loading");
  }, [notifySkillsPageLoadingState, pageState]);

  useEffect(() => {
    if (pageState !== "loading" || !tree) {
      return;
    }

    if (layoutPreparing || !sceneBootstrapReady) {
      return;
    }

    if (tree.nodes.length === 0 || viewportReady) {
      setPageState("ready");
    }
  }, [layoutPreparing, pageState, sceneBootstrapReady, tree, viewportReady]);

  useEffect(() => {
    if (selectedNodeCode === detailNodeCode) {
      return;
    }

    clearDetailSwitchTimer();

    if (!selectedNodeCode) {
      setDetailNodeCode(null);
      return;
    }

    if (!detailNodeCode || typeof window === "undefined") {
      setDetailNodeCode(selectedNodeCode);
      return;
    }

    detailSwitchTimerRef.current = window.setTimeout(() => {
      setDetailNodeCode(selectedNodeCode);
      detailSwitchTimerRef.current = null;
    }, DETAIL_SWITCH_DELAY_MS);

    return () => {
      clearDetailSwitchTimer();
    };
  }, [detailNodeCode, selectedNodeCode]);

  const loadPage = async (options?: { initial?: boolean; preferredSelection?: string | null }) => {
    const replacePage = options?.initial ?? pageState !== "ready";

    if (replacePage) {
      // 首次加载或错误重试要重置布局；普通刷新只显示局部 refreshing 状态。
      setPageState("loading");
      setLoadError(null);
      setSceneBootstrapReady(false);
      setLayoutResult(createEmptySkillLayoutResult());
    } else {
      setRefreshing(true);
    }

    try {
      const treeRequestPath = isAdminPreviewMode ? "/admin/skills/preview-tree" : "/skills/tree";
      // 树结构是主数据；学生画像只影响 HUD，失败时不阻断星图打开。
      const [treeResult, profileResult] = await Promise.allSettled([
        apiRequest<SkillTreeResponse>(treeRequestPath),
        isAdminPreviewMode ? Promise.resolve(null) : apiRequest<StudentProfileSummary>("/profiles/students/me"),
      ]);

      if (treeResult.status === "rejected") {
        throw treeResult.reason;
      }

      const nextTree = treeResult.value;
      const nextProfile = isAdminPreviewMode
        ? null
        : profileResult.status === "fulfilled"
          ? profileResult.value
          : null;
      const requestedFocusNodeCode = previewFocusNodeCode ?? focusNodeCodeRef.current;
      const requestedSelectedNodeCode = previewSelectedNodeCode ?? options?.preferredSelection ?? selectedNodeCodeRef.current;
      // 选中节点和主攻节点都要重新校验，防止后台删改节点后还指向旧 code。
      const nextFocusNodeCode = resolveFocusNodeCode(nextTree.nodes, requestedFocusNodeCode);
      const nextSelectedNodeCode = resolveSelectedNodeCode(
        nextTree.nodes,
        requestedSelectedNodeCode,
        nextFocusNodeCode,
      );

      if (options?.initial) {
        setViewportReady(false);
      }
      setLayoutPreparing(nextTree.nodes.length > 0);
      setTree(nextTree);
      setProfile(nextProfile);
      setFocusNodeCode(nextFocusNodeCode);
      setSelectedNodeCode(nextSelectedNodeCode);
      setLoadError(null);

      if (!replacePage) {
        setPageState("ready");
      }
    } catch (error) {
      const message = getLoadErrorMessage(error);
      if (replacePage) {
        setPageState("error");
        setLoadError(message);
      } else {
        setNotice({ tone: "error", message });
      }
    } finally {
      setRefreshing(false);
    }
  };

  useEffect(() => {
    if (isAdminPreviewMode) {
      if (role !== "ADMIN") {
        return;
      }

      void loadPage({ initial: true, preferredSelection: previewSelectedNodeCode ?? previewFocusNodeCode });
      return;
    }

    if (role !== "STUDENT") {
      return;
    }

    void loadPage({ initial: true });
  }, [isAdminPreviewMode, previewFocusNodeCode, previewSelectedNodeCode, role]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container || positionedNodes.length === 0 || viewportReady) {
      return;
    }

    const target = nodeMap.get(selectedNodeCode ?? focusNodeCode ?? coreNodeCode ?? positionedNodes[0]?.nodeCode ?? "");
    if (!target) {
      return;
    }

    const rect = container.getBoundingClientRect();
    // 初次进入时把相机对准选中/主攻/核心节点，避免用户看到空画布角落。
    const nextViewport = buildCenteredViewport(rect.width, rect.height, target.x, target.y, 1, worldWidth, worldHeight);
    applyViewportToDom(nextViewport);
    setViewportReady(true);
  }, [coreNodeCode, focusNodeCode, nodeMap, positionedNodes, selectedNodeCode, viewportReady, worldHeight, worldWidth]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container || !viewportReady) {
      return;
    }

    const rect = container.getBoundingClientRect();
    const nextViewport = clampViewport(viewportRef.current, rect.width, rect.height, worldWidth, worldHeight);
    applyViewportToDom(nextViewport);
  }, [viewportReady, worldHeight, worldWidth]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container || typeof ResizeObserver === "undefined") {
      return;
    }

    const observer = new ResizeObserver(() => {
      const rect = container.getBoundingClientRect();
      pixiSceneRef.current?.app.renderer.resize(Math.max(1, rect.width), Math.max(1, rect.height));

      if (!viewportReady && positionedNodes.length > 0) {
        const target = nodeMap.get(selectedNodeCodeRef.current ?? focusNodeCodeRef.current ?? coreNodeCode ?? positionedNodes[0]?.nodeCode ?? "");
        if (target) {
          const nextViewport = buildCenteredViewport(rect.width, rect.height, target.x, target.y, 1, worldWidth, worldHeight);
          applyViewportToDom(nextViewport);
          setViewportReady(true);
        }
        return;
      }

      const nextViewport = clampViewport(viewportRef.current, rect.width, rect.height, worldWidth, worldHeight);
      applyViewportToDom(nextViewport);
    });

    observer.observe(container);
    return () => observer.disconnect();
  }, [coreNodeCode, nodeMap, positionedNodes, viewportReady, worldHeight, worldWidth]);

  const syncHoveredNodeFromPointer = (clientX: number, clientY: number): PositionedSkillNode | null => {
    const container = containerRef.current;
    if (!container) {
      updateHoveredNodeCode(null);
      return null;
    }

    const rect = container.getBoundingClientRect();
    const worldPoint = getWorldPointFromViewport(clientX, clientY, rect, viewportRef.current);
    // 命中测试使用世界坐标，缩放和平移不会影响节点 hover 判断。
    const matchedNode: PositionedSkillNode | null = pickNodeAtWorldPoint(
      positionedNodes,
      worldPoint.x,
      worldPoint.y,
      worldCenterX,
      worldCenterY,
    );
    updateHoveredNodeCode(matchedNode?.nodeCode ?? null);
    return matchedNode;
  };

  const handleResetView = () => {
    const target = coreNode ?? focusNode ?? positionedNodes[0];
    if (!target) {
      return;
    }

    centerOnNode(target.nodeCode, 1, true, 1040);
  };

  const handleLocateFocusNode = () => {
    const targetNodeCode = focusNode?.nodeCode ?? coreNode?.nodeCode ?? null;
    if (!targetNodeCode) {
      return;
    }

    setSelectedNodeCode(targetNodeCode);
    centerOnNode(targetNodeCode, viewportRef.current.scale, true, 1080);
  };

  const handleSetFocusNode = (nodeCode: string) => {
    setFocusNodeCode(nodeCode);
    setSelectedNodeCode(nodeCode);
    setNotice({ tone: "info", message: "当前主攻节点已切换，仅保存在本浏览器中。" });
  };

  const handleProgressUpdate = async (node: PositionedSkillNode, targetStatus: SkillStatus) => {
    setPendingNodeCode(node.nodeCode);

    try {
      // 进度更新后重新拉树，让解锁状态、汇总数和节点状态都以服务端为准。
      await apiRequest("/skills/progress", {
        method: "POST",
        body: JSON.stringify({
          nodeId: node.nodeCode,
          targetStatus,
        }),
      });

      setNotice({ tone: "success", message: getSuccessMessage(node.label, targetStatus) });
      await loadPage({ preferredSelection: node.nodeCode });
    } catch (error) {
      setNotice({ tone: "error", message: getMutationErrorMessage(error) });
    } finally {
      setPendingNodeCode(null);
    }
  };

  const handlePointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    if (event.pointerType === "mouse" && event.button === 1) {
      event.preventDefault();
      return;
    }

    if (event.pointerType === "mouse" && event.button !== 0) {
      return;
    }

    const container = containerRef.current;
    if (!container) {
      return;
    }

    stopViewportAnimation();

    const matchedNode: PositionedSkillNode | null = syncHoveredNodeFromPointer(event.clientX, event.clientY);
    if (matchedNode) {
      nodePointerCandidateRef.current = {
        pointerId: event.pointerId,
        nodeCode: matchedNode.nodeCode,
        startX: event.clientX,
        startY: event.clientY,
      };
      container.setPointerCapture(event.pointerId);
      if (customCursorEnabled && event.pointerType === "mouse") {
        updateCursorMode("interactive");
      }
      return;
    }

    dragStateRef.current = {
      active: true,
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      originX: viewportRef.current.x,
      originY: viewportRef.current.y,
      lastX: event.clientX,
      lastY: event.clientY,
      lastTime: event.timeStamp,
      velocityX: 0,
      velocityY: 0,
    };

    container.setPointerCapture(event.pointerId);
    setDragging(true);
    if (customCursorEnabled && event.pointerType === "mouse") {
      updateCursorMode("drag");
    }
  };

  const handlePointerMove = (event: React.PointerEvent<HTMLDivElement>) => {
    const container = containerRef.current;
    const dragState = dragStateRef.current;

    if (!container) {
      return;
    }

    if (dragState.active) {
      const deltaTime = Math.max(1, event.timeStamp - dragState.lastTime);
      const instantVelocityX = (event.clientX - dragState.lastX) / deltaTime;
      const instantVelocityY = (event.clientY - dragState.lastY) / deltaTime;
      dragState.velocityX = dragState.velocityX * 0.72 + instantVelocityX * 0.28;
      dragState.velocityY = dragState.velocityY * 0.72 + instantVelocityY * 0.28;
      dragState.lastX = event.clientX;
      dragState.lastY = event.clientY;
      dragState.lastTime = event.timeStamp;

      const rect = container.getBoundingClientRect();
      const nextViewport = clampViewport(
        {
          scale: viewportRef.current.scale,
          x: dragState.originX + (event.clientX - dragState.startX),
          y: dragState.originY + (event.clientY - dragState.startY),
        },
        rect.width,
        rect.height,
        worldWidth,
        worldHeight,
      );

      scheduleViewportApply(nextViewport);
      return;
    }

    const candidate = nodePointerCandidateRef.current;
    if (candidate) {
      const movedDistance = Math.hypot(event.clientX - candidate.startX, event.clientY - candidate.startY);
      if (movedDistance > NODE_CLICK_CANCEL_DISTANCE_PX) {
        nodePointerCandidateRef.current = null;
      }
    }

    syncHoveredNodeFromPointer(event.clientX, event.clientY);
  };

  const stopDragging = (event?: React.PointerEvent<HTMLDivElement>) => {
    const container = containerRef.current;
    const dragState = dragStateRef.current;
    const candidate = nodePointerCandidateRef.current;

    if (container && candidate && container.hasPointerCapture(candidate.pointerId)) {
      container.releasePointerCapture(candidate.pointerId);
    }

    if (container && dragState.pointerId !== null && container.hasPointerCapture(dragState.pointerId)) {
      container.releasePointerCapture(dragState.pointerId);
    }

    if (dragState.active) {
      flushPendingViewport();
    }

    const releaseVelocityX = dragState.velocityX;
    const releaseVelocityY = dragState.velocityY;
    const shouldStartInertia = dragState.active && Boolean(event);

    dragStateRef.current = {
      active: false,
      pointerId: null,
      startX: 0,
      startY: 0,
      originX: 0,
      originY: 0,
      lastX: 0,
      lastY: 0,
      lastTime: 0,
      velocityX: 0,
      velocityY: 0,
    };
    setDragging(false);

    if (shouldStartInertia) {
      startViewportInertia(releaseVelocityX, releaseVelocityY);
    }

    if (event && candidate) {
      const movedDistance = Math.hypot(event.clientX - candidate.startX, event.clientY - candidate.startY);
      if (movedDistance <= NODE_CLICK_CANCEL_DISTANCE_PX) {
        setSelectedNodeCode(candidate.nodeCode);
        centerOnNode(candidate.nodeCode, viewportRef.current.scale, true, 920);
      }
      syncHoveredNodeFromPointer(event.clientX, event.clientY);
    } else if (!event) {
      updateHoveredNodeCode(null);
    }

    nodePointerCandidateRef.current = null;
    if (customCursorEnabled) {
      updateCursorMode(resolveCursorMode(event?.target ?? null, false));
    }
  };

  const handleCanvasMouseDown = (event: React.MouseEvent<HTMLDivElement>) => {
    if (event.button === 1) {
      event.preventDefault();
    }
  };

  const handleCanvasAuxClick = (event: React.MouseEvent<HTMLDivElement>) => {
    if (event.button === 1) {
      event.preventDefault();
    }
  };

  const handleRootPointerEnter = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!customCursorEnabled || event.pointerType !== "mouse") {
      return;
    }

    scheduleCursorPoint(event.clientX, event.clientY);
    updateCursorVisibility(true);
    updateCursorMode(resolveCursorMode(event.target, dragStateRef.current.active));
  };

  const handleRootPointerMove = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!customCursorEnabled || event.pointerType !== "mouse") {
      return;
    }

    scheduleCursorPoint(event.clientX, event.clientY);
    updateCursorVisibility(true);
    const container = containerRef.current;
    if (container && event.target instanceof Node && !container.contains(event.target)) {
      updateHoveredNodeCode(null);
    }
    updateCursorMode(resolveCursorMode(event.target, dragStateRef.current.active));
  };

  const handleRootPointerLeave = () => {
    if (!customCursorEnabled) {
      return;
    }

    clearPendingCursorFrame();
    rawCursorX.set(CURSOR_HIDDEN_POSITION);
    rawCursorY.set(CURSOR_HIDDEN_POSITION);
    updateHoveredNodeCode(null);
    updateCursorVisibility(false);
    updateCursorMode("idle");
  };

  const handleRootPointerUp = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!customCursorEnabled || event.pointerType !== "mouse") {
      return;
    }

    updateCursorMode(resolveCursorMode(event.target, false));
  };

  if (pageState === "error" || (!tree && pageState !== "loading")) {
    return (
      <div className="relative h-screen w-full overflow-hidden bg-[#020617] font-mono text-slate-100">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(99,102,241,0.18),transparent_32%),radial-gradient(circle_at_bottom_right,rgba(20,184,166,0.12),transparent_28%),linear-gradient(180deg,#020617_0%,#081120_100%)]" />
        <div className="relative flex h-full items-center justify-center px-6 py-10">
          <div className="w-full max-w-xl rounded-[2.1rem] border border-rose-400/20 bg-slate-950/82 p-8 text-center shadow-[0_30px_90px_rgba(2,6,23,0.48)] backdrop-blur-xl">
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-rose-500/12 text-rose-300">
              <AlertCircle size={30} />
            </div>
            <h1 className="mt-5 text-3xl font-semibold text-white">技能星图暂时没有加载成功</h1>
            <p className="mx-auto mt-3 max-w-lg text-base leading-7 text-slate-400">
              {loadError || "当前技能树接口暂时不可用，请稍后重试。"}
            </p>
            <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
              <button
                type="button"
                onClick={() => void loadPage({ initial: true })}
                className="inline-flex items-center justify-center rounded-full bg-white px-5 py-3 text-base font-semibold text-slate-900 transition-transform hover:-translate-y-0.5"
              >
                重新加载
              </button>
              <Link
                to={backHref}
                className="inline-flex items-center justify-center rounded-full border border-white/15 bg-white/5 px-5 py-3 text-base font-semibold text-slate-100 transition-colors hover:border-white/25 hover:bg-white/10"
              >
                <ChevronLeft size={16} className="mr-2" />
                {backLabel}
              </Link>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div
      className={joinClasses(
        "relative h-screen w-full overflow-hidden bg-[#020617] font-mono text-slate-200 selection:bg-indigo-500/30",
        customCursorEnabled && !!tree && "skills-cursor-none",
      )}
      onPointerOverCapture={handleRootPointerEnter}
      onPointerMoveCapture={handleRootPointerMove}
      onPointerUpCapture={handleRootPointerUp}
      onPointerLeave={handleRootPointerLeave}
      onContextMenu={(event) => event.preventDefault()}
    >
      <div className="pointer-events-none absolute inset-0 z-0 transform-gpu">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,transparent_0%,#020617_100%)] opacity-85" />
        <div className="absolute -left-[12vw] -top-[16vh] h-[48vw] w-[48vw] rounded-full bg-indigo-600/18 blur-[160px]" />
        <div className="absolute right-[-16vw] top-[8vh] h-[34vw] w-[34vw] rounded-full bg-fuchsia-500/12 blur-[140px]" />
        <div className="absolute bottom-[-20vh] right-[-8vw] h-[52vw] w-[52vw] rounded-full bg-teal-500/10 blur-[170px]" />
      </div>

      <div className="pointer-events-none absolute inset-0 z-[1] overflow-hidden">
        <div
          ref={minorGridRef}
          className="absolute inset-0 opacity-55"
          style={{
            backgroundImage:
              "linear-gradient(to right, rgba(148, 163, 184, 0.08) 1px, transparent 1px), linear-gradient(to bottom, rgba(148, 163, 184, 0.08) 1px, transparent 1px)",
            backgroundSize: "100px 100px",
            backgroundPosition: "0px 0px",
            willChange: viewportAnimating ? "background-position, background-size" : "auto",
          }}
        />
        <div
          ref={majorGridRef}
          className="absolute inset-0 opacity-26"
          style={{
            backgroundImage:
              "linear-gradient(to right, rgba(56, 189, 248, 0.12) 1px, transparent 1px), linear-gradient(to bottom, rgba(56, 189, 248, 0.12) 1px, transparent 1px)",
            backgroundSize: "500px 500px",
            backgroundPosition: "0px 0px",
            willChange: viewportAnimating ? "background-position, background-size" : "auto",
          }}
        />
      </div>

      <div
        ref={containerRef}
        className={joinClasses(
          "absolute inset-0 z-10 touch-none",
          tree ? (dragging ? "cursor-grabbing" : "cursor-grab") : "pointer-events-none",
        )}
        onMouseDown={handleCanvasMouseDown}
        onAuxClick={handleCanvasAuxClick}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={stopDragging}
        onPointerCancel={() => stopDragging()}
      >
        <div
          ref={canvasHostRef}
          className="absolute inset-0"
          style={{ willChange: viewportAnimating ? "transform" : "auto" }}
        />
      </div>

      <div className="absolute left-6 top-6 z-30 flex flex-col gap-3">
        <div className="flex gap-3">
          <Link
            to={backHref}
            className="group inline-flex items-center rounded-lg border border-slate-700 bg-slate-900/80 px-5 py-3 text-slate-300 shadow-lg backdrop-blur-md transition-colors hover:text-white"
          >
            <ChevronLeft size={18} className="mr-2 transition-transform group-hover:-translate-x-1" />
            <span className="text-sm font-bold uppercase tracking-[0.24em]">{backLabel}</span>
          </Link>
          <button
            type="button"
            onClick={handleLocateFocusNode}
            className={joinClasses(
              "group inline-flex items-center rounded-lg px-5 py-3 shadow-[0_0_15px_rgba(217,70,239,0.3)] backdrop-blur-md transition-colors",
              isAdminPreviewMode
                ? "border border-sky-800 bg-sky-950/50 text-sky-300 hover:text-sky-100"
                : "border border-fuchsia-800 bg-fuchsia-950/50 text-fuchsia-300 hover:text-fuchsia-100",
            )}
          >
            <Navigation size={18} className={joinClasses("mr-2 transition-transform group-hover:scale-110", isAdminPreviewMode ? "text-sky-500" : "text-fuchsia-500")} />
            <span className="text-sm font-bold uppercase tracking-[0.24em]">{primaryLocateLabel}</span>
          </button>
          <button
            type="button"
            onClick={() => void loadPage({ preferredSelection: selectedNodeCodeRef.current ?? focusNodeCodeRef.current ?? coreNodeCode })}
            disabled={refreshing}
            className="group inline-flex items-center rounded-lg border border-slate-700 bg-slate-900/80 px-5 py-3 text-slate-300 shadow-lg backdrop-blur-md transition-colors hover:text-white disabled:cursor-not-allowed disabled:opacity-60"
          >
            <RefreshCw size={18} className={joinClasses("mr-2", refreshing && "animate-spin")} />
            <span className="text-sm font-bold uppercase tracking-[0.24em]">同步星图</span>
          </button>
        </div>

        {isAdminPreviewMode ? null : (
          <div
            className="relative w-[352px] overflow-hidden border border-slate-700 bg-slate-900/80 px-5 py-4 shadow-2xl backdrop-blur-xl"
            style={{ clipPath: "polygon(10px 0, 100% 0, 100% calc(100% - 10px), calc(100% - 10px) 100%, 0 100%, 0 10px)" }}
          >
            <div className="absolute bottom-0 left-0 top-0 w-1 bg-cyan-400" />
            <div className="pointer-events-none absolute -right-10 top-0 h-24 w-24 rounded-full bg-cyan-400/10 blur-2xl" />
            <div className="relative">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <div className="text-sm font-bold uppercase tracking-[0.24em] text-slate-500">成长脉冲观测窗</div>
                </div>
                <motion.span
                  animate={{ opacity: [0.45, 1, 0.45] }}
                  transition={{ duration: 1.8, repeat: Infinity, ease: "easeInOut" }}
                  className="rounded-full border border-cyan-400/30 bg-cyan-400/10 px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.24em] text-cyan-200"
                >
                  Live
                </motion.span>
              </div>

              <div className="mt-4 grid grid-cols-[1.15fr_0.85fr] gap-3">
                <div className="row-span-2 rounded border border-white/10 bg-white/5 p-4">
                  <div className="text-sm uppercase tracking-[0.2em] text-slate-500">已解锁节点</div>
                  <div className="mt-3 text-3xl font-black text-white">{formatCount(unlockedCount)}</div>
                </div>

                <div className="rounded border border-white/10 bg-white/5 p-3.5">
                  <div className="text-sm uppercase tracking-[0.2em] text-slate-500">画像里的掌握数</div>
                  <div className="mt-2 text-2xl font-bold text-emerald-200">{formatCount(portraitEvidence?.masteredSkills ?? 0)}</div>
                </div>

                <div className="rounded border border-white/10 bg-white/5 p-3.5">
                  <div className="text-sm uppercase tracking-[0.2em] text-slate-500">画像里的学习中</div>
                  <div className="mt-2 text-2xl font-bold text-indigo-200">{formatCount(portraitEvidence?.learningSkills ?? 0)}</div>
                </div>

                <div className="relative col-span-2 overflow-hidden rounded border border-cyan-400/20 bg-cyan-400/8 p-4">
                  <motion.div
                    className="pointer-events-none absolute inset-y-0 -left-1/3 w-1/2 bg-gradient-to-r from-transparent via-cyan-300/20 to-transparent"
                    animate={{ x: ["0%", "240%"] }}
                    transition={{ duration: 3.4, repeat: Infinity, ease: "linear" }}
                  />
                  <div className="relative flex items-start justify-between gap-4">
                    <div>
                      <div className="text-sm uppercase tracking-[0.22em] text-cyan-200/80">画像刷新信号</div>
                      <div className="mt-2 flex items-center gap-2 text-base font-semibold text-white">
                        <motion.span
                          className="h-2.5 w-2.5 rounded-full bg-cyan-400 shadow-[0_0_12px_rgba(34,211,238,0.7)]"
                          animate={{ opacity: [0.35, 1, 0.35], scale: [0.92, 1.22, 0.92] }}
                          transition={{ duration: 1.6, repeat: Infinity, ease: "easeInOut" }}
                        />
                        {profile?.portrait?.updatedAt ? formatDateTime(profile.portrait.updatedAt) : "待行为积累"}
                      </div>
                    </div>
                    <div className="text-right">
                      <div className="text-[10px] uppercase tracking-[0.22em] text-cyan-200/75">Sync Status</div>
                      <div className="mt-1 text-xs font-bold uppercase tracking-[0.24em] text-cyan-100">
                        {profile?.portrait?.updatedAt ? "Signal Stable" : "Pending"}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      <div className="absolute left-1/2 top-5 z-30 hidden -translate-x-1/2 lg:block">
        <div
          className="relative w-[min(94vw,1080px)] overflow-hidden border border-slate-700/80 bg-slate-900/84 px-14 py-4 shadow-[0_26px_70px_rgba(2,6,23,0.52)] backdrop-blur-xl"
          style={{ clipPath: "polygon(1.75% 0,98.25% 0,91.5% 100%,8.5% 100%)" }}
        >
          <div className="pointer-events-none absolute inset-x-[22%] top-0 h-px bg-gradient-to-r from-transparent via-cyan-300/55 to-transparent" />
          <div className="pointer-events-none absolute left-1/2 top-2 h-12 w-44 -translate-x-1/2 rounded-full bg-cyan-400/8 blur-2xl" />
          <div className="pointer-events-none absolute bottom-0 left-1/2 h-10 w-64 -translate-x-1/2 bg-gradient-to-r from-transparent via-slate-200/5 to-transparent" />

          <div className="relative">
            <div className="grid grid-cols-6 justify-items-center gap-5 text-base font-semibold tracking-[0.08em] text-slate-200">
              <div className="flex items-center gap-2.5 whitespace-nowrap text-slate-400">
                <div className="h-3.5 w-3.5 rotate-45 rounded-sm border border-slate-600 bg-slate-800" />
                锁定
              </div>
              <div className="flex items-center gap-2.5 whitespace-nowrap text-slate-100">
                <div className="h-3.5 w-3.5 rotate-45 rounded-sm border border-slate-300/40 bg-slate-500/30" />
                可学习
              </div>
              <div className="flex items-center gap-2.5 whitespace-nowrap text-indigo-300">
                <div className="h-3.5 w-3.5 rotate-45 rounded-sm border border-indigo-400 bg-indigo-500 shadow-[0_0_10px_rgba(99,102,241,0.8)] animate-pulse" />
                学习中
              </div>
              <div className="flex items-center gap-2.5 whitespace-nowrap text-fuchsia-300">
                <div className="h-3.5 w-3.5 rotate-45 rounded-sm border border-fuchsia-400 bg-fuchsia-500 shadow-[0_0_10px_rgba(217,70,239,0.8)]" />
                主攻目标
              </div>
              <div className="flex items-center gap-2.5 whitespace-nowrap text-sky-300">
                <div className="h-px w-9 border-t-2 border-dashed border-sky-300" />
                联动路径
              </div>
              <div className="flex items-center gap-2.5 whitespace-nowrap text-emerald-300">
                <div className="h-3.5 w-3.5 rotate-45 rounded-sm border border-emerald-400 bg-emerald-500 shadow-[0_0_10px_rgba(16,185,129,0.8)]" />
                已掌握
              </div>
            </div>
          </div>
        </div>
      </div>

      {customCursorEnabled && tree ? (
        <div className="pointer-events-none fixed inset-0 z-[80]">
          <motion.div
            className="absolute left-0 top-0"
            style={{ x: cursorAuraX, y: cursorAuraY, willChange: "transform" }}
            animate={{
              opacity: customCursorVisible ? 1 : 0,
              scale: cursorMode === "drag" ? 0.98 : cursorMode === "interactive" ? 1.08 : 1,
            }}
            transition={{ duration: 0.08, ease: "easeOut" }}
          >
            <div className="relative h-16 w-16 -translate-x-1/2 -translate-y-1/2">
              <motion.div
                className={joinClasses(
                  "absolute inset-0 rounded-full border",
                  cursorMode === "drag"
                    ? "border-rose-400/85 bg-rose-500/8 shadow-[0_0_26px_rgba(244,63,94,0.34)]"
                    : cursorMode === "interactive"
                      ? "border-fuchsia-300/90 bg-fuchsia-500/10 shadow-[0_0_30px_rgba(217,70,239,0.38)]"
                      : "border-cyan-300/75 bg-cyan-400/8 shadow-[0_0_24px_rgba(34,211,238,0.28)]",
                )}
                animate={{
                  rotate: cursorMode === "drag" ? -32 : 32,
                  scale: cursorMode === "drag" ? [0.96, 1.04, 0.96] : [0.92, 1.08, 0.92],
                  opacity: cursorMode === "interactive" ? [0.72, 1, 0.72] : [0.56, 0.9, 0.56],
                }}
                transition={{
                  rotate: { duration: cursorMode === "drag" ? 1.1 : 1.8, repeat: Infinity, ease: "easeInOut" },
                  scale: { duration: cursorMode === "drag" ? 1.05 : 1.55, repeat: Infinity, ease: "easeInOut" },
                  opacity: { duration: cursorMode === "drag" ? 1.05 : 1.55, repeat: Infinity, ease: "easeInOut" },
                }}
              />

              <motion.div
                className={joinClasses(
                  "absolute left-1/2 top-1/2 h-24 w-24 -translate-x-1/2 -translate-y-1/2 rounded-full blur-xl",
                  cursorMode === "drag"
                    ? "bg-rose-500/18"
                    : cursorMode === "interactive"
                      ? "bg-fuchsia-500/16"
                      : "bg-cyan-400/14",
                )}
                animate={{
                  scale: [0.82, 1.04, 0.82],
                  opacity: cursorMode === "interactive" ? [0.24, 0.54, 0.24] : [0.2, 0.46, 0.2],
                }}
                transition={{
                  scale: { duration: cursorMode === "drag" ? 0.98 : 1.35, repeat: Infinity, ease: "easeInOut" },
                  opacity: { duration: cursorMode === "drag" ? 0.98 : 1.35, repeat: Infinity, ease: "easeInOut" },
                }}
              />
            </div>
          </motion.div>

          <motion.div
            className="absolute left-0 top-0"
            style={{ x: rawCursorX, y: rawCursorY, willChange: "transform" }}
            animate={{
              opacity: customCursorVisible ? 1 : 0,
              scale: cursorMode === "drag" ? 0.96 : cursorMode === "interactive" ? 1.05 : 1,
            }}
            transition={{ duration: 0.04, ease: "easeOut" }}
          >
            <div className="relative h-14 w-14 -translate-x-1/2 -translate-y-1/2">
              <motion.div
                className={joinClasses(
                  "absolute left-1/2 top-1/2 h-4 w-4 -translate-x-1/2 -translate-y-1/2 rotate-45 rounded-[3px] border",
                  cursorMode === "drag"
                    ? "border-rose-200/90 bg-rose-500/40"
                    : cursorMode === "interactive"
                      ? "border-fuchsia-200/95 bg-fuchsia-500/48"
                      : "border-cyan-100/85 bg-cyan-400/42",
                )}
                animate={{
                  rotate: cursorMode === "interactive" ? [45, 135, 45] : 45,
                  scale: cursorMode === "drag" ? [0.88, 1, 0.88] : cursorMode === "interactive" ? [0.92, 1.12, 0.92] : [0.86, 1, 0.86],
                }}
                transition={{
                  rotate: { duration: 2.8, repeat: Infinity, ease: "easeInOut" },
                  scale: { duration: cursorMode === "drag" ? 0.9 : 1.2, repeat: Infinity, ease: "easeInOut" },
                }}
              />

              <div className="absolute inset-0">
                <div className={joinClasses("absolute left-1/2 top-0 h-3 w-px -translate-x-1/2", cursorMode === "drag" ? "bg-rose-200/90" : cursorMode === "interactive" ? "bg-fuchsia-200/90" : "bg-cyan-100/80")} />
                <div className={joinClasses("absolute bottom-0 left-1/2 h-3 w-px -translate-x-1/2", cursorMode === "drag" ? "bg-rose-200/90" : cursorMode === "interactive" ? "bg-fuchsia-200/90" : "bg-cyan-100/80")} />
                <div className={joinClasses("absolute left-0 top-1/2 h-px w-3 -translate-y-1/2", cursorMode === "drag" ? "bg-rose-200/90" : cursorMode === "interactive" ? "bg-fuchsia-200/90" : "bg-cyan-100/80")} />
                <div className={joinClasses("absolute right-0 top-1/2 h-px w-3 -translate-y-1/2", cursorMode === "drag" ? "bg-rose-200/90" : cursorMode === "interactive" ? "bg-fuchsia-200/90" : "bg-cyan-100/80")} />
              </div>

              <motion.div
                className={joinClasses(
                  "absolute left-1/2 top-1/2 h-10 w-10 -translate-x-1/2 -translate-y-1/2 rounded-full blur-lg",
                  cursorMode === "drag"
                    ? "bg-rose-500/20"
                    : cursorMode === "interactive"
                      ? "bg-fuchsia-500/18"
                      : "bg-cyan-400/16",
                )}
                animate={{ scale: [0.86, 1.08, 0.86], opacity: [0.24, 0.52, 0.24] }}
                transition={{ duration: cursorMode === "drag" ? 0.92 : 1.18, repeat: Infinity, ease: "easeInOut" }}
              />
            </div>
          </motion.div>
        </div>
      ) : null}

      {!isAdminPreviewMode ? (
        <div className="absolute bottom-6 left-6 z-30 w-[360px]">
        <div
          className="relative border border-slate-700/50 border-l-4 border-l-emerald-500 bg-slate-900/82 p-6 shadow-2xl backdrop-blur-xl"
          style={{ clipPath: "polygon(0 0, 100% 0, 100% calc(100% - 20px), calc(100% - 20px) 100%, 0 100%)" }}
        >
          <div className="pointer-events-none absolute right-0 top-0 h-16 w-16 bg-emerald-500/10 blur-xl" />

          <div className="relative">
            <div className="mb-5 flex items-start justify-between gap-4">
              <div>
                <div className="flex items-center gap-3">
                  <StudentIdentityAvatar
                    userId={profile?.userId ?? userId}
                    role={role}
                    displayName={heroName}
                    avatar={profile?.avatar}
                    tier={profile?.tier}
                    className="h-12 w-12 rounded-[1rem] border border-emerald-400/20 bg-slate-950/65"
                    textClassName="text-base"
                  />
                  <h2 className="text-3xl font-black tracking-widest text-white">{heroName}</h2>
                  <Sparkles size={16} className="text-emerald-300" />
                </div>
                <p className="mt-1.5 flex items-center text-xs uppercase tracking-[0.26em] text-emerald-400">
                  <span className="mr-1.5 h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse" />
                  STAR MAP // ACTIVE
                </p>
              </div>
              <div className="text-right">
                <span className="bg-gradient-to-b from-white to-slate-400 bg-clip-text text-4xl font-black leading-none text-transparent">
                  {hudPercent}
                </span>
                <span className="mt-1 block text-[10px] uppercase tracking-[0.22em] text-slate-500">Mapped</span>
              </div>
            </div>

            <div className="mb-5">
              <div className="mb-2 flex justify-between text-[10px] font-bold uppercase tracking-[0.24em]">
                <span className="text-emerald-400">Progress Charge</span>
                <span className="text-slate-300">
                  {treeSummary.mastered + treeSummary.learning}
                  <span className="text-slate-600"> / {treeSummary.total}</span>
                </span>
              </div>
              <div className="h-2 w-full overflow-hidden rounded-sm border border-slate-700 bg-slate-800">
                <div
                  className="relative h-full bg-gradient-to-r from-emerald-600 to-teal-400 shadow-[0_0_10px_rgba(16,185,129,0.8)]"
                  style={{ width: `${hudPercent}%` }}
                >
                  <div className="absolute inset-y-0 left-0 w-1/2 bg-gradient-to-r from-transparent to-white/30 animate-[translateX_2s_infinite]" />
                </div>
              </div>
            </div>

            <div className="rounded border border-slate-800 bg-slate-950/50 p-4">
              <div className="text-xs uppercase tracking-[0.24em] text-slate-500">当前主攻目标</div>
              <div className="mt-2 flex items-center gap-2 text-base font-semibold text-fuchsia-200">
                <Navigation size={14} className="text-fuchsia-300" />
                {focusLabel}
              </div>
              <div className="mt-2 text-base leading-6 text-slate-400">
                {profile?.targetPosition?.trim()
                  ? `目标岗位：${profile.targetPosition}`
                  : "目标岗位：未设置"}
              </div>
            </div>

            <div className="mt-4 grid grid-cols-2 gap-3 border-t border-slate-800 pt-4">
              <div className="flex flex-col rounded border border-slate-800 bg-slate-950/50 p-2.5">
                <span className="mb-1 text-xs uppercase tracking-[0.22em] text-slate-500">已掌握</span>
                <span className="flex items-center text-lg font-bold text-white">
                  <Trophy size={14} className="mr-1.5 text-emerald-400" />
                  {formatCount(treeSummary.mastered)}
                </span>
              </div>
              <div className="flex flex-col rounded border border-slate-800 bg-slate-950/50 p-2.5">
                <span className="mb-1 text-xs uppercase tracking-[0.22em] text-slate-500">学习中</span>
                <span className="flex items-center text-lg font-bold text-white">
                  <BookOpen size={14} className="mr-1.5 text-indigo-400" />
                  {formatCount(treeSummary.learning)}
                </span>
              </div>
              <div className="flex flex-col rounded border border-slate-800 bg-slate-950/50 p-2.5">
                <span className="mb-1 text-xs uppercase tracking-[0.22em] text-slate-500">社区贡献</span>
                <span className="flex items-center text-lg font-bold text-white">
                  <MessageSquare size={14} className="mr-1.5 text-sky-400" />
                  {formatCount(profile?.communityScore7d ?? 0)}
                </span>
              </div>
              <div className="flex flex-col rounded border border-slate-800 bg-slate-950/50 p-2.5">
                <span className="mb-1 text-xs uppercase tracking-[0.22em] text-slate-500">7日面练</span>
                <span className="flex items-center text-lg font-bold text-white">
                  <GraduationCap size={14} className="mr-1.5 text-amber-400" />
                  {formatCount(portraitEvidence?.interviewMessages7d ?? 0)}
                </span>
              </div>
            </div>
          </div>
        </div>
        </div>
      ) : null}

      <div className="absolute bottom-6 left-1/2 z-30 -translate-x-1/2">
        <div className="flex items-center space-x-5 rounded-full border border-slate-700/80 bg-slate-900/80 px-5 py-2.5 shadow-2xl backdrop-blur-xl">
          <button
            type="button"
            onClick={() => adjustZoom(-0.18)}
            className="text-slate-400 transition-all hover:scale-110 hover:text-white"
          >
            <ZoomOut size={20} />
          </button>

          <button type="button" className="group flex w-16 flex-col items-center justify-center" onClick={handleResetView}>
            <span ref={zoomLabelRef} className="mb-0.5 text-sm font-bold tracking-[0.18em] text-emerald-400">100%</span>
            <span className="flex items-center text-[8px] uppercase tracking-[0.24em] text-slate-500 transition-colors group-hover:text-slate-300">
              <Maximize size={10} className="mr-1" />
              Reset
            </span>
          </button>

          <button
            type="button"
            onClick={() => adjustZoom(0.18)}
            className="text-slate-400 transition-all hover:scale-110 hover:text-white"
          >
            <ZoomIn size={20} />
          </button>
        </div>
      </div>

      <AnimatePresence>
        {detailNode ? (
          <motion.div
            initial={{ opacity: 0, y: 32 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 72 }}
            transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
            className="absolute bottom-6 right-6 z-40 flex max-h-[calc(100vh-3rem)] w-[min(92vw,430px)] flex-col"
          >
            <div
              className="relative flex h-full flex-col overflow-hidden border border-slate-700 bg-slate-900/88 p-8 shadow-2xl backdrop-blur-xl"
              style={{ clipPath: "polygon(20px 0, 100% 0, 100% 100%, 0 100%, 0 20px)" }}
            >
              <div className={joinClasses("absolute left-0 top-0 h-1.5 w-24", detailStatus === "TARGET" ? "bg-fuchsia-500 shadow-[0_0_15px_rgba(217,70,239,1)]" : "bg-rose-500 shadow-[0_0_15px_rgba(244,63,94,1)]")} />
              <div className={joinClasses("absolute bottom-0 right-0 top-0 w-1", detailStatus === "TARGET" ? "bg-gradient-to-b from-fuchsia-500/50 to-transparent" : "bg-gradient-to-b from-rose-500/50 to-transparent")} />

              <AnimatePresence mode="wait" initial={false}>
                <motion.div
                  key={detailNode.nodeCode}
                  initial={{ opacity: 0, y: 18 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -14 }}
                  transition={{ duration: 0.16, ease: "easeOut" }}
                  className="flex h-full flex-col"
                >
                  <div className="mb-6 flex items-start justify-between gap-4">
                    <div className="flex flex-1 items-start gap-5">
                      <div
                        className={joinClasses(
                          "flex h-16 w-16 shrink-0 items-center justify-center rounded-xl border shadow-lg",
                          detailStatus === "TARGET" && "border-fuchsia-500/50 bg-fuchsia-950 text-fuchsia-400",
                          detailStatus === "MASTERED" && "border-emerald-500/50 bg-emerald-950 text-emerald-400",
                          detailStatus === "LEARNING" && "border-indigo-500/50 bg-indigo-950 text-indigo-400",
                          detailStatus === "NOT_STARTED" && "border-slate-600 bg-slate-900 text-slate-200",
                          detailStatus === "LOCKED" && "border-slate-700 bg-slate-800 text-slate-500",
                        )}
                      >
                        {!detailNode.unlocked ? <Lock size={28} /> : <detailNode.icon size={32} />}
                      </div>

                      <div className="min-w-0 flex-1">
                        <div className="mb-1.5 flex items-center space-x-2">
                          <span className={joinClasses("h-1.5 w-1.5 rounded-full animate-ping", detailStatus === "TARGET" ? "bg-fuchsia-500" : "bg-rose-500")} />
                          <span className={joinClasses("text-[10px] font-bold uppercase tracking-[0.24em]", detailStatus === "TARGET" ? "text-fuchsia-400" : "text-rose-400")}>
                            ID: {detailNode.nodeCode.toUpperCase()}
                          </span>
                        </div>
                        <h2 className="truncate text-3xl font-black tracking-wide text-white">{detailNode.label}</h2>
                        <span
                          className={joinClasses(
                            "mt-2 inline-block rounded border px-2 py-0.5 text-[10px] font-bold uppercase tracking-[0.24em]",
                            detailStatus === "TARGET" && "border-fuchsia-500/50 bg-fuchsia-500/20 text-fuchsia-300",
                            detailStatus === "MASTERED" && "border-emerald-500/30 text-emerald-400",
                            detailStatus === "LEARNING" && "border-indigo-500/30 text-indigo-400",
                            detailStatus === "NOT_STARTED" && "border-slate-600 text-slate-300",
                            detailStatus === "LOCKED" && "border-slate-700 text-slate-500",
                          )}
                        >
                          {detailStatus ? getStatusLabel(detailStatus) : "—"}
                        </span>
                      </div>
                    </div>

                    <button
                      type="button"
                      onClick={() => setSelectedNodeCode(null)}
                      className="inline-flex rounded-full border border-slate-700 bg-slate-900 p-1 text-slate-500 transition-colors hover:border-slate-500 hover:text-white"
                    >
                      <ChevronRight size={20} className="rotate-90" />
                    </button>
                  </div>

                  <div className="custom-scrollbar flex-1 overflow-y-auto pr-2">
                    <div className="mb-6 flex flex-wrap gap-2">
                      {detailPath.map((entry, index) => (
                        <div key={entry.nodeCode} className="flex items-center">
                          <span className="rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-xs text-slate-300">
                            {entry.label}
                          </span>
                          {index < detailPath.length - 1 ? <ChevronRight size={14} className="mx-1 text-slate-600" /> : null}
                        </div>
                      ))}
                    </div>

                    <div className="mb-4 grid gap-3 sm:grid-cols-2">
                      <div className="rounded border border-slate-800 bg-slate-950/40 p-4">
                        <div className="text-xs uppercase tracking-[0.22em] text-slate-500">解锁条件</div>
                        <div className="mt-3 text-base font-semibold text-white">
                          {getUnlockRequirementLabel(detailNode, detailParent, nodeMap)}
                        </div>
                      </div>
                      <div className="rounded border border-slate-800 bg-slate-950/40 p-4">
                        <div className="text-xs uppercase tracking-[0.22em] text-slate-500">最近推进</div>
                        <div className="mt-3 text-base font-semibold text-white">
                          {detailNode.updatedAt ? formatDateTime(detailNode.updatedAt) : "未记录"}
                        </div>
                      </div>
                    </div>

                    {detailRelations.length > 0 ? (
                      <div className="mb-4">
                        <h3 className="mb-3 flex items-center text-xs font-bold uppercase tracking-[0.24em] text-slate-400">
                          <Network size={14} className="mr-1.5" />
                          跨树联动
                        </h3>
                        <div className="space-y-2">
                          {detailRelations.map((relation) => {
                            const counterpart = relation.sourceNodeCode === detailNode.nodeCode
                              ? relation.targetNode
                              : relation.sourceNode;
                            const accent = getRelationStroke(relation.relationType);

                            return (
                              <button
                                key={`${relation.sourceNodeCode}-${relation.targetNodeCode}-${relation.relationType}`}
                                type="button"
                                onClick={() => {
                                  setSelectedNodeCode(counterpart.nodeCode);
                                  centerOnNode(counterpart.nodeCode, viewportRef.current.scale, true, 900);
                                }}
                                className="group flex w-full items-start justify-between rounded-lg border border-slate-700/50 bg-slate-800/30 p-3 text-left transition-all hover:border-slate-500 hover:bg-slate-800/80"
                              >
                                <div className="min-w-0 flex-1">
                                  <div className="mb-1.5 flex items-center gap-2 text-[10px] font-bold uppercase tracking-[0.22em] text-slate-400">
                                    <span className="h-2 w-2 rounded-full" style={{ backgroundColor: accent }} />
                                    <span>{getRelationTypeLabel(relation.relationType)}</span>
                                  </div>
                                  <div className="text-base font-semibold text-white">{counterpart.label}</div>
                                  <div className="mt-1 text-sm leading-6 text-slate-400">{relation.label}</div>
                                </div>
                                <ExternalLink size={14} className="ml-3 mt-1 shrink-0 text-slate-600 transition-colors group-hover:text-sky-300" />
                              </button>
                            );
                          })}
                        </div>
                      </div>
                    ) : null}

                    {detailNode.unlocked && detailResources.length > 0 ? (
                      <div className="mb-4">
                        <h3 className="mb-3 flex items-center text-xs font-bold uppercase tracking-[0.24em] text-slate-400">
                          <Target size={14} className="mr-1.5" />
                          推荐资源
                        </h3>
                        <div className="space-y-2">
                          {detailResources.map((resource) => {
                            const ResourceIcon = getResourceIcon(resource.type);
                            return (
                              <a
                                key={resource.id}
                                href={resource.link}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="group flex flex-col rounded-lg border border-slate-700/50 bg-slate-800/30 p-3 text-left transition-all hover:border-indigo-500/30 hover:bg-slate-800/80"
                              >
                                <div className="mb-1.5 flex items-start justify-between">
                                  <div className="flex items-center space-x-2">
                                    <ResourceIcon size={14} className={joinClasses(resource.type === "video" ? "text-rose-400" : resource.type === "article" ? "text-amber-300" : "text-blue-400")} />
                                    <span className="rounded bg-slate-900 px-1.5 py-0.5 text-[10px] font-bold text-slate-500">
                                      {resource.source}
                                    </span>
                                    <span className="rounded bg-slate-900 px-1.5 py-0.5 text-[10px] font-bold text-slate-500">
                                      {getResourceTypeLabel(resource.type)}
                                    </span>
                                  </div>
                                  <ExternalLink size={14} className="text-slate-600 transition-colors group-hover:text-indigo-400" />
                                </div>
                                <h4 className="text-base font-medium leading-tight text-slate-200 transition-colors group-hover:text-white">
                                  {resource.title}
                                </h4>
                                <span className="mt-2 text-xs text-slate-500">预计耗时：{resource.time}</span>
                              </a>
                            );
                          })}
                        </div>
                      </div>
                    ) : null}
                  </div>

                  <div className="mt-4 border-t border-slate-800/50 pt-4">
                    <div className="flex flex-col gap-3">
                      {!isAdminPreviewMode && detailNode.unlocked && detailNode.status === "LEARNING" && focusNodeCode !== detailNode.nodeCode ? (
                        <button
                          type="button"
                          onClick={() => handleSetFocusNode(detailNode.nodeCode)}
                          className="w-full rounded border border-fuchsia-500/30 bg-slate-800 py-3.5 text-sm font-bold uppercase tracking-[0.24em] text-fuchsia-400 transition-colors hover:border-fuchsia-500 hover:bg-fuchsia-950/50"
                        >
                          <span className="flex items-center justify-center">
                            <Navigation size={16} className="mr-2" />
                            设为当前追踪目标
                          </span>
                        </button>
                      ) : null}

                      {!isAdminPreviewMode && detailNode.unlocked ? (
                        <Link
                          to={`/ai/interview?skillNodeCode=${encodeURIComponent(detailNode.nodeCode)}&skillLabel=${encodeURIComponent(detailNode.label)}`}
                          className="relative w-full overflow-hidden rounded border border-indigo-500 bg-indigo-600 py-3.5 text-center text-sm font-bold uppercase tracking-[0.24em] text-white shadow-[0_0_15px_rgba(99,102,241,0.4)] transition-colors hover:bg-indigo-500"
                        >
                          <span className="relative z-10 flex items-center justify-center">
                            <Target size={16} className="mr-2" />
                            发起专项 AI 面试验证
                          </span>
                        </Link>
                      ) : null}

                      {actionButtons.map((action) => (
                        <button
                          key={`${detailNode.nodeCode}-${action.targetStatus}`}
                          type="button"
                          disabled={pendingNodeCode === detailNode.nodeCode}
                          onClick={() => void handleProgressUpdate(detailNode, action.targetStatus)}
                          className={joinClasses(
                            "inline-flex items-center justify-center rounded border px-4 py-3 text-base font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60",
                            action.tone === "primary" && "bg-indigo-500 text-white hover:bg-indigo-400",
                            action.tone === "secondary" && "border-white/12 bg-white/5 text-slate-100 hover:border-white/20 hover:bg-white/10",
                            action.tone === "danger" && "border-rose-400/20 bg-rose-400/10 text-rose-100 hover:border-rose-300/30 hover:bg-rose-400/15",
                          )}
                        >
                          <action.icon size={16} className="mr-2" />
                          {pendingNodeCode === detailNode.nodeCode ? "提交中..." : action.label}
                        </button>
                      ))}
                    </div>
                  </div>
                </motion.div>
              </AnimatePresence>
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>

      <AnimatePresence>
        {notice ? (
          <motion.div
            initial={{ opacity: 0, y: 20, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 18, scale: 0.96 }}
            transition={{ type: "spring", stiffness: 260, damping: 22 }}
            className="fixed bottom-6 left-1/2 z-[60] w-[min(92vw,34rem)] -translate-x-1/2"
          >
            <div
              className={joinClasses(
                "rounded-2xl border px-4 py-3 text-base shadow-[0_18px_48px_rgba(2,6,23,0.42)] backdrop-blur-xl",
                notice.tone === "success" && "border-emerald-400/25 bg-emerald-400/12 text-emerald-50",
                notice.tone === "error" && "border-rose-400/25 bg-rose-400/12 text-rose-50",
                notice.tone === "info" && "border-indigo-400/25 bg-indigo-400/12 text-indigo-50",
              )}
            >
              {notice.message}
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>

      <style>{`
        .skills-cursor-none,
        .skills-cursor-none * {
          cursor: none !important;
        }

        .custom-scrollbar::-webkit-scrollbar {
          width: 4px;
        }

        .custom-scrollbar::-webkit-scrollbar-track {
          background: rgba(15, 23, 42, 0.5);
          border-radius: 4px;
        }

        .custom-scrollbar::-webkit-scrollbar-thumb {
          background: rgba(71, 85, 105, 0.8);
          border-radius: 4px;
        }

        .custom-scrollbar::-webkit-scrollbar-thumb:hover {
          background: rgba(99, 102, 241, 0.8);
        }

        @keyframes skill-dash {
          to {
            stroke-dashoffset: -24;
          }
        }

        .animate-skill-dash {
          animation: skill-dash 1s linear infinite;
        }

        @keyframes translateX {
          0% {
            transform: translateX(-100%);
          }

          100% {
            transform: translateX(200%);
          }
        }
      `}</style>
    </div>
  );
}
