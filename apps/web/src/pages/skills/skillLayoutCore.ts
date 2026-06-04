// 画布先给一个足够大的基础世界，后面再根据实际节点边界扩展，避免星图被裁切。
const MIN_CANVAS_WIDTH = 5200;
const MIN_CANVAS_HEIGHT = 5200;
const CANVAS_PADDING = 560;

// 极坐标布局的主参数：根节点外第一圈半径、层级间距和每个扇区的安全留白。
const FIRST_RING_RADIUS = 460;
const DEPTH_RADIUS_GAP = 310;
const RADIAL_SPAN_PADDING = Math.PI / 48;

// 子树权重会影响父节点给子节点分配多少角度空间，节点多或层级深的分支会拿到更宽扇区。
const SUBTREE_NODE_WEIGHT = 0.32;
const SUBTREE_DEPTH_WEIGHT = 0.78;

// 节点本体和标签的估算尺寸。布局算法不读 DOM，所以这里用固定估算值提前避让。
const NODE_VISUAL_HALF_SIZE = 62;
const NODE_LABEL_MIN_WIDTH = 176;
const NODE_LABEL_MAX_WIDTH = 360;
const NODE_LABEL_HEIGHT = 52;
const NODE_LABEL_SIDE_GAP = 20;
const NODE_LABEL_STACK_GAP = 36;
const NODE_LAYOUT_PADDING = 18;

// 碰撞盒会比视觉盒稍大，实际布局宁可松一点，也不要让标签和节点边缘贴得太近。
const NODE_COLLISION_BODY_PADDING = 12;
const NODE_COLLISION_TITLE_SIDE_PADDING = 28;
const NODE_COLLISION_TITLE_STACK_PADDING = 34;
const NODE_COLLISION_GAP = 28;
const NODE_COLLISION_ITERATIONS = 26;
const NODE_COLLISION_RADIUS_FLEX = 360;

// 不同深度的最小层距和密度补偿，主要用于防止深层链路挤在同一圈。
const NODE_DEPTH_MIN_RADIUS_GAP = 210;
const NODE_LAYER_DENSITY_PADDING = 64;
const NODE_SECTOR_FLEX = Math.PI / 120;
const NODE_LAYER_INNER_BAND_MIN = 72;
const NODE_LAYER_OUTER_BAND_MIN = 96;
const NODE_LAYER_INNER_BAND_RATIO = 0.24;
const NODE_LAYER_OUTER_BAND_RATIO = 0.28;

// 笛卡尔碰撞是极坐标后的二次微调，专门处理标签矩形重叠这种角度法不好解决的问题。
const NODE_CARTESIAN_COLLISION_ITERATIONS = 24;
const NODE_CARTESIAN_RELAXATION = 0.02;
const NODE_CARTESIAN_PUSH_FACTOR = 0.68;
const NODE_CARTESIAN_SHARED_RADIAL_WEIGHT = 0.14;
const NODE_CARTESIAN_SAME_DEPTH_RADIAL_WEIGHT = 0.22;
const NODE_CARTESIAN_CROSS_DEPTH_RADIAL_WEIGHT = 0.28;

// 链式节点容易排成一条直线，这组参数给长链一点角度和半径扰动。
const NODE_DEPTH_CROWD_OUTSET_STEP = 12;
const NODE_DEPTH_DENSITY_RADIUS_MULTIPLIER = 1.1;
const NODE_BRANCH_GAP_BASE = 0.024;
const NODE_BRANCH_GAP_MAX = 0.11;
const NODE_CHAIN_ANGLE_STEP = 0.022;
const NODE_CHAIN_ANGLE_MAX = 0.16;
const NODE_CHAIN_RADIUS_STEP = 12;
const NODE_CHAIN_RADIUS_MAX = 64;
const ROOT_KEY = "__root__";

export type SkillLayoutNodeInput = {
  nodeCode: string;
  label: string;
  parentCode: string | null;
  sortOrder: number;
};

export type SkillLayoutGeometryNode = {
  nodeCode: string;
  depth: number;
  x: number;
  y: number;
  angle: number;
  radius: number;
};

export type SkillLayoutGeometryResult = {
  nodes: SkillLayoutGeometryNode[];
  width: number;
  height: number;
  centerX: number;
  centerY: number;
};

type WorldBounds = {
  left: number;
  top: number;
  right: number;
  bottom: number;
};

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

function normalizeAngle(value: number) {
  let next = value;

  // 布局里经常比较角度差，先压到 [-PI, PI] 可以避免跨 180 度时方向判断反掉。
  while (next <= -Math.PI) {
    next += Math.PI * 2;
  }

  while (next > Math.PI) {
    next -= Math.PI * 2;
  }

  return next;
}

function getLabelVisualWeight(label: string) {
  // 中文字符通常比 ASCII 更宽，这里用简单权重估算标签视觉宽度。
  return Array.from(label).reduce((sum, char) => {
    const codePoint = char.codePointAt(0) ?? 0;
    return sum + (codePoint <= 0x7f ? 0.72 : 1);
  }, 0);
}

function getEstimatedNodeLabelWidth(label: string) {
  // 标签宽度被限制在区间内，避免短标签过窄、长标签把整圈撑得过开。
  return Math.min(
    NODE_LABEL_MAX_WIDTH,
    Math.max(NODE_LABEL_MIN_WIDTH, 72 + getLabelVisualWeight(label) * 22),
  );
}

function getNodeLabelPlacement(
  _node: Pick<LayoutCollisionNode, "x" | "y">,
  _centerX: number,
  _centerY: number,
) {
  // 当前星图统一把标签放在节点下方，先保持视觉一致，后续需要四向标签时再扩展这里。
  return { direction: "bottom" as const };
}

function getEstimatedNodeBounds(
  node: { x: number; y: number; label: string },
  centerX: number,
  centerY: number,
): WorldBounds {
  const labelWidth = getEstimatedNodeLabelWidth(node.label);
  // 用节点圆形区域和下方标签区域合并成一个保守边界，作为画布尺寸和碰撞判断的基础。
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

  // 碰撞盒比绘制盒更大一些，给标题和边缘留出肉眼可感知的空隙。
  return {
    left: bounds.left - NODE_COLLISION_BODY_PADDING,
    top: bounds.top - NODE_COLLISION_BODY_PADDING,
    right: bounds.right + NODE_COLLISION_BODY_PADDING,
    bottom: bounds.bottom + NODE_COLLISION_BODY_PADDING + NODE_COLLISION_TITLE_STACK_PADDING,
  };
}

function getEstimatedAngularFootprint(label: string) {
  // 角度占用按“节点直径 + 标签宽度”取较大值，保证同一圆环上的文字不挤到一起。
  return Math.max(
    NODE_VISUAL_HALF_SIZE * 2 + NODE_COLLISION_GAP + 32,
    getEstimatedNodeLabelWidth(label) + 54,
  );
}

function getDepthMinRadiusGap(depth: number, singleRootMode: boolean) {
  // 单根模式下内圈更容易拥挤，所以浅层半径间隔单独放宽。
  if (singleRootMode) {
    if (depth <= 1) {
      return FIRST_RING_RADIUS;
    }

    if (depth === 2) {
      return 210;
    }

    if (depth === 3) {
      return 228;
    }

    return NODE_DEPTH_MIN_RADIUS_GAP;
  }

  if (depth === 1) {
    return 220;
  }

  if (depth === 2) {
    return 228;
  }

  if (depth === 3) {
    return 240;
  }

  return NODE_DEPTH_MIN_RADIUS_GAP;
}

function getDepthCrowdOutsetMultiplier(depth: number) {
  // 越深的节点越容易形成密集分支，外推补偿随深度逐步增加。
  if (depth <= 1) {
    return 0.22;
  }

  if (depth === 2) {
    return 0.34;
  }

  if (depth === 3) {
    return 0.5;
  }

  return 1;
}

function getDepthDensityRadiusMultiplier(depth: number) {
  if (depth <= 1) {
    return 1.02;
  }

  if (depth === 2) {
    return 1.04;
  }

  if (depth === 3) {
    return 1.06;
  }

  return NODE_DEPTH_DENSITY_RADIUS_MULTIPLIER;
}

function updateCollisionNodePosition(node: LayoutCollisionNode, centerX: number, centerY: number) {
  // angle/radius 是布局真相，x/y 是给边界和渲染使用的派生坐标。
  node.x = centerX + Math.cos(node.angle) * node.radius;
  node.y = centerY + Math.sin(node.angle) * node.radius;
}

function clampCollisionNode(node: LayoutCollisionNode) {
  const angleRange = node.maxAngle - node.minAngle;
  if (angleRange > 0.08) {
    // 不让节点贴到分配扇区边缘，给兄弟分支留一点视觉呼吸空间。
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
  // 笛卡尔位移后反算极坐标，再统一走 clamp，避免节点被推到自己的扇区外。
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

  // 位移被拆成切向和径向：切向解决同层拥挤，径向只按权重少量参与，避免层级结构散掉。
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

  // 每轮碰撞后轻轻拉回锚点，防止多次推开后星图整体越跑越散。
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

function boundsIntersect(bounds: WorldBounds, left: number, top: number, right: number, bottom: number) {
  return right >= bounds.left
    && left <= bounds.right
    && bottom >= bounds.top
    && top <= bounds.bottom;
}

function resolveCartesianCollisionLayout(
  layoutNodes: LayoutCollisionNode[],
  centerX: number,
  centerY: number,
) {
  if (layoutNodes.length <= 1) {
    return;
  }

  // 这里专门处理最终矩形碰撞，适合修正中文标签宽度导致的局部重叠。
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

        // 同父或同层节点优先沿切向分开，尽量保留原本的放射层级结构。
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
          vectorX += -Math.sin(averageAngle) * tangentialSign * (sharedParent ? 1.14 : 0.82);
          vectorY += Math.cos(averageAngle) * tangentialSign * (sharedParent ? 1.14 : 0.82);
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
  gapMultiplier = 1,
  gapCap = 0.34,
) {
  if (group.length <= 1) {
    return false;
  }

  // 从左到右、再从右到左各扫一遍，能把一串相邻节点的角度间距均匀摊开。
  let moved = false;

  for (let index = 1; index < group.length; index += 1) {
    const previous = group[index - 1];
    const current = group[index];
    const averageRadius = Math.max((previous.radius + current.radius) / 2, FIRST_RING_RADIUS * 0.72);
    const requiredGap = Math.min(
      gapCap,
      ((getEstimatedAngularFootprint(previous.label) + getEstimatedAngularFootprint(current.label)) / (averageRadius * 2)) * gapMultiplier,
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
      gapCap,
      ((getEstimatedAngularFootprint(current.label) + getEstimatedAngularFootprint(next.label)) / (averageRadius * 2)) * gapMultiplier,
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

  // 同父节点和同深度节点分别建组，后面用不同约束处理“同分支”和“同圆环”的拥挤。
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

    // 先在极坐标里处理角度和半径，再用笛卡尔微调处理标签矩形碰撞。
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
          sameDepth ? 0.38 : 0.28,
          (separation / averageRadius) * (sharedParent ? 1.18 : sameDepth ? 1.04 : 0.42),
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
      if (sweepCollisionGroup(group, centerX, centerY, 1.12, 0.42)) {
        moved = true;
      }
    });

    depthGroups.forEach((group) => {
      if (sweepCollisionGroup(group, centerX, centerY, 1.34, 0.56)) {
        moved = true;
      }
    });

    resolveCartesianCollisionLayout(layoutNodes, centerX, centerY);

    if (!moved) {
      break;
    }
  }
}

export function buildSkillLayoutGeometry(
  nodes: SkillLayoutNodeInput[],
  presets: Record<string, { x: number; y: number }> = {},
): SkillLayoutGeometryResult {
  // 外部只传技能节点和可选预设坐标；这里统一产出画布尺寸、中心点和每个节点坐标。
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
  const childrenMap = new Map<string, SkillLayoutNodeInput[]>();

  childrenMap.set(ROOT_KEY, []);

  // 先把扁平节点整理成 parent -> children 结构，后续所有递归都基于这张表走。
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
  const linearChainDepthCache = new Map<string, number>();

  const countLeaves = (nodeCode: string): number => {
    // 叶子数量决定基础扇区大小，叶子越多的分支拿到的角度越宽。
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
    // 子树深度用于给长链分支加权，避免深但节点少的链路被压成窄缝。
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
    // 子树节点数是第二个权重，解决“中等宽度但节点多”的分支拥挤问题。
    const cached = subtreeNodeCountCache.get(nodeCode);
    if (cached !== undefined) {
      return cached;
    }

    const children = childrenMap.get(nodeCode) ?? [];
    const subtreeNodeCount = 1 + children.reduce((sum, child) => sum + countSubtreeNodes(child.nodeCode), 0);

    subtreeNodeCountCache.set(nodeCode, subtreeNodeCount);
    return subtreeNodeCount;
  };

  const countLinearChainDepth = (nodeCode: string): number => {
    // 单子节点长链单独识别，后面会给它一点角度/半径扰动，减少直线重叠。
    const cached = linearChainDepthCache.get(nodeCode);
    if (cached !== undefined) {
      return cached;
    }

    const children = childrenMap.get(nodeCode) ?? [];
    const linearDepth = children.length === 1
      ? 1 + countLinearChainDepth(children[0].nodeCode)
      : 0;

    linearChainDepthCache.set(nodeCode, linearDepth);
    return linearDepth;
  };

  const getSpanWeight = (nodeCode: string): number => {
    // 最终扇区权重综合叶子、节点数和深度，是星图整体疏密的主要依据。
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
  const depthGroups = new Map<number, SkillLayoutNodeInput[]>();

  const registerNodeDepth = (node: SkillLayoutNodeInput, depth: number) => {
    // 按深度分组后可以估算每一圈需要多大半径，防止某一层节点过多。
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
    // 先给每层一个名义半径，再结合密度动态外推。
    if (depth === 0) {
      return singleRootMode ? 0 : FIRST_RING_RADIUS;
    }

    if (singleRootMode) {
      if (depth === 1) {
        return FIRST_RING_RADIUS;
      }

      if (depth === 2) {
        return FIRST_RING_RADIUS + 210;
      }

      if (depth === 3) {
        return FIRST_RING_RADIUS + 210 + 228;
      }

      return FIRST_RING_RADIUS + 210 + 228 + (depth - 3) * DEPTH_RADIUS_GAP;
    }

    if (depth === 1) {
      return FIRST_RING_RADIUS + 220;
    }

    if (depth === 2) {
      return FIRST_RING_RADIUS + 220 + 228;
    }

    if (depth === 3) {
      return FIRST_RING_RADIUS + 220 + 228 + 240;
    }

    return FIRST_RING_RADIUS + 220 + 228 + 240 + (depth - 3) * DEPTH_RADIUS_GAP;
  };

  const depthRadiusMap = new Map<number, number>();
  const depthCrowdOutsetMap = new Map<number, number>();
  depthRadiusMap.set(0, resolveNominalRadius(0));

  for (let depth = 1; depth <= maxDepth; depth += 1) {
    const nodesAtDepth = depthGroups.get(depth) ?? [];
    // 这一层的文字总占用越大，半径就越要外扩，否则同一圆环会互相压住。
    const occupiedCircumference = nodesAtDepth.reduce((sum, node) => (
      sum + getEstimatedAngularFootprint(node.label) + NODE_LAYER_DENSITY_PADDING
    ), 0);
    const crowdOutset = Math.max(0, nodesAtDepth.length - 3)
      * NODE_DEPTH_CROWD_OUTSET_STEP
      * getDepthCrowdOutsetMultiplier(depth);
    const densityRadius = nodesAtDepth.length <= 1
      ? 0
      : (occupiedCircumference / (Math.PI * 2)) * getDepthDensityRadiusMultiplier(depth);
    const previousRadius = depthRadiusMap.get(depth - 1) ?? 0;
    const nextRadius = Math.max(
      resolveNominalRadius(depth) + crowdOutset * 0.04,
      previousRadius + getDepthMinRadiusGap(depth, singleRootMode) + Math.min(28, crowdOutset * 0.06),
      densityRadius + crowdOutset,
    );
    depthRadiusMap.set(depth, nextRadius);
    depthCrowdOutsetMap.set(depth, crowdOutset);
  }

  const maxRadius = depthRadiusMap.get(maxDepth) ?? 0;
  const computedWidth = Math.max(MIN_CANVAS_WIDTH, (maxRadius + CANVAS_PADDING) * 2);
  const computedHeight = Math.max(MIN_CANVAS_HEIGHT, (maxRadius + CANVAS_PADDING) * 2);
  const centerX = computedWidth / 2;
  const centerY = computedHeight / 2;

  const assignLayout = (
    node: SkillLayoutNodeInput,
    startAngle: number,
    endAngle: number,
    depth: number,
    chainIndex = 0,
    chainDirection = 0,
  ) => {
    // 递归分配扇区：父节点先落在扇区中点，子节点再按 spanWeight 切分剩余角度。
    const midAngle = (startAngle + endAngle) / 2;
    const inferredChainDirection = chainDirection !== 0
      ? chainDirection
      : (Math.cos(midAngle) >= 0 ? 1 : -1);
    const safeInset = Math.min(0.16, Math.max(0.02, (endAngle - startAngle) * 0.12));
    const minAngle = startAngle + safeInset;
    const maxAngle = endAngle - safeInset;
    const chainAngleOffset = chainIndex >= 3
      ? inferredChainDirection * Math.min(NODE_CHAIN_ANGLE_MAX, NODE_CHAIN_ANGLE_STEP * Math.min(6, chainIndex - 1))
      : 0;
    // 单根模式下根节点固定在正上方，视觉上更像“技能星图中心”。
    const angle = depth === 0 && singleRootMode
      ? -Math.PI / 2
      : (
        minAngle < maxAngle
          ? Math.min(maxAngle, Math.max(minAngle, midAngle + chainAngleOffset))
          : midAngle + chainAngleOffset
      );
    const chainRadiusBoost = chainIndex >= 3
      ? Math.min(NODE_CHAIN_RADIUS_MAX, (chainIndex - 2) * NODE_CHAIN_RADIUS_STEP)
      : 0;
    const baseRadius = (depthRadiusMap.get(depth) ?? resolveNominalRadius(depth)) + chainRadiusBoost;
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

    // 子节点之间保留兄弟间隙，且间隙有上限，避免小分支被 gap 吃掉太多空间。
    const totalWeight = children.reduce((sum, child) => sum + getSpanWeight(child.nodeCode), 0);
    const totalSpan = endAngle - startAngle;
    const siblingGap = children.length <= 1
      ? 0
      : Math.min(
        NODE_BRANCH_GAP_MAX,
        Math.max(NODE_BRANCH_GAP_BASE, totalSpan * 0.04),
        (totalSpan * 0.22) / Math.max(1, children.length - 1),
      );
    const usableSpan = Math.max(totalSpan * 0.72, totalSpan - siblingGap * Math.max(0, children.length - 1));
    let cursor = startAngle;

    children.forEach((child) => {
      const span = (usableSpan * getSpanWeight(child.nodeCode)) / Math.max(totalWeight, 1);
      const inset = span > RADIAL_SPAN_PADDING * 2 ? Math.min(RADIAL_SPAN_PADDING, span * 0.16) : 0;
      const childStart = cursor + inset;
      const childEnd = cursor + span - inset;
      const childMidAngle = (childStart + childEnd) / 2;
      const childChainIndex = children.length === 1 ? chainIndex + 1 : 0;
      const childChainDirection = children.length === 1
        ? inferredChainDirection
        : (Math.sign(childMidAngle - angle) || (Math.cos(childMidAngle) >= 0 ? 1 : -1));
      const childLinearChainDepth = countLinearChainDepth(child.nodeCode);
      const nextChainIndex = childLinearChainDepth >= 3 ? Math.max(childChainIndex, 3) : childChainIndex;
      assignLayout(child, childStart, childEnd, depth + 1, nextChainIndex, childChainDirection);
      cursor += span + siblingGap;
    });
  };

  if (roots.length === 1) {
    // 单根树使用近似整圆扇区，让所有子分支围绕中心展开。
    assignLayout(roots[0], -Math.PI + RADIAL_SPAN_PADDING, Math.PI - RADIAL_SPAN_PADDING, 0);
  } else {
    // 多根树按根分支权重瓜分整圆，适合后台预览或未来多主题技能树。
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
    const presetPosition = presets[node.nodeCode];
    // preset 用于保持外部拖拽/调试坐标，命中 preset 的节点在碰撞阶段会固定。
    const nextX = presetPosition?.x ?? computedPosition.x;
    const nextY = presetPosition?.y ?? computedPosition.y;
    const previousRadius = computedPosition.depth > 0
      ? (depthRadiusMap.get(computedPosition.depth - 1) ?? Math.max(0, computedPosition.baseRadius - DEPTH_RADIUS_GAP))
      : 0;
    const nextLayerRadius = depthRadiusMap.get(computedPosition.depth + 1) ?? (computedPosition.baseRadius + DEPTH_RADIUS_GAP);
    const crowdOutset = depthCrowdOutsetMap.get(computedPosition.depth) ?? 0;
    const inwardRoom = computedPosition.depth === 0
      ? (singleRootMode ? 0 : Math.max(NODE_LAYER_INNER_BAND_MIN, FIRST_RING_RADIUS * 0.14))
      : Math.max(NODE_LAYER_INNER_BAND_MIN, (computedPosition.baseRadius - previousRadius) * NODE_LAYER_INNER_BAND_RATIO);
    const outwardRoom = Math.max(
      NODE_LAYER_OUTER_BAND_MIN + Math.min(54, crowdOutset * 0.12),
      (nextLayerRadius - computedPosition.baseRadius) * NODE_LAYER_OUTER_BAND_RATIO,
    );
    // 每个节点只允许在自己的角度扇区和半径带内微调，保证碰撞后仍能看出父子关系。
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

  // 碰撞后再检查真实边界，如果左上角越界，就整体平移并同步中心点。
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
  // 返回顺序沿用输入排序，渲染层可以稳定复用节点 key。
  const laidOutNodes = sortedNodes.map((node) => {
    const collisionNode = collisionNodeMap.get(node.nodeCode) ?? collisionNodes[0];
    return {
      nodeCode: node.nodeCode,
      depth: collisionNode.depth,
      x: collisionNode.x,
      y: collisionNode.y,
      angle: collisionNode.angle,
      radius: collisionNode.radius,
    };
  });

  const maxRight = bounds.reduce((max, bound) => Math.max(max, bound.right), 0);
  const maxBottom = bounds.reduce((max, bound) => Math.max(max, bound.bottom), 0);

  return {
    // 最终画布尺寸按节点实际最大边界再加 padding，防止最外层标签被裁掉。
    nodes: laidOutNodes,
    width: Math.max(computedWidth + offsetX, Math.ceil(maxRight + CANVAS_PADDING)),
    height: Math.max(computedHeight + offsetY, Math.ceil(maxBottom + CANVAS_PADDING)),
    centerX: resolvedCenterX,
    centerY: resolvedCenterY,
  };
}
