export type WorkspaceSnapshotEnvelope<T> = {
  data: T;
  updatedAt: string;
};

const WORKSPACE_SNAPSHOT_STORAGE_PREFIX = "bishe.workspace.snapshot.v1";

function getWorkspaceSnapshotStorage() {
  if (typeof window === "undefined") {
    return null;
  }

  // 工作区快照只做当前标签页的弱恢复，不作为接口数据真相层。
  return window.sessionStorage;
}

export function buildWorkspaceSnapshotStorageKey(...segments: Array<string | number | null | undefined>) {
  // key 按业务域和筛选维度拼接，调用方只传语义片段，不直接写 sessionStorage key。
  const normalizedSegments = segments
    .map((segment) => {
      if (segment === null || segment === undefined) {
        return "";
      }
      return String(segment).trim();
    })
    .filter(Boolean);

  return [WORKSPACE_SNAPSHOT_STORAGE_PREFIX, ...normalizedSegments].join(".");
}

export function readWorkspaceSnapshot<T>(storageKey: string) {
  const storage = getWorkspaceSnapshotStorage();
  if (!storage) {
    return null;
  }

  try {
    // 快照只服务“先回显”，解析失败直接丢弃，不影响后续接口刷新。
    const raw = storage.getItem(storageKey);
    return raw ? JSON.parse(raw) as WorkspaceSnapshotEnvelope<T> : null;
  } catch {
    return null;
  }
}

export function writeWorkspaceSnapshot<T>(
  storageKey: string,
  data: T,
  updatedAt = new Date().toISOString(),
) {
  const storage = getWorkspaceSnapshotStorage();
  if (!storage) {
    return null;
  }

  // updatedAt 方便页面判断这份快照有多旧，展示时可以决定是否提示正在刷新。
  const snapshot: WorkspaceSnapshotEnvelope<T> = {
    data,
    updatedAt,
  };

  storage.setItem(storageKey, JSON.stringify(snapshot));
  return snapshot;
}

export function clearWorkspaceSnapshot(storageKey: string) {
  const storage = getWorkspaceSnapshotStorage();
  if (!storage) {
    return;
  }

  // 只清当前工作区 key，不批量清整个 sessionStorage，避免影响其他页面回访体验。
  storage.removeItem(storageKey);
}
