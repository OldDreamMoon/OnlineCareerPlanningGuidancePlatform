type TimeValue = number | string | null;

type StudentPublicProfileRecentActivity = {
  id: string;
  title: string;
  href: string;
  detail?: string | null;
  createdAt?: TimeValue;
};

export type StudentPublicProfileSnapshot = {
  studentUserId: number;
  displayName: string;
  communityScore7d: number | null;
  leaderboardRank: number | null;
  postCount: number | null;
  commentCount: number | null;
  likeReceivedCount: number | null;
  latestActivityAt: TimeValue;
  recentActivities: StudentPublicProfileRecentActivity[];
  updatedAt: number | string;
};

type SnapshotPatch = Partial<Omit<StudentPublicProfileSnapshot, "studentUserId" | "recentActivities" | "updatedAt">> & {
  recentActivities?: StudentPublicProfileRecentActivity[];
};

const STORAGE_KEY_PREFIX = "bishe.student.public-profile.";
const MAX_RECENT_ACTIVITIES = 4;

function getStorageKey(studentUserId: number) {
  return `${STORAGE_KEY_PREFIX}${studentUserId}`;
}

function getStorage() {
  if (typeof window === "undefined") {
    return null;
  }
  return window.sessionStorage;
}

function normalizeRecentActivities(
  nextItems: Array<StudentPublicProfileRecentActivity | null | undefined>,
  previousItems: StudentPublicProfileRecentActivity[],
) {
  const merged = [...nextItems, ...previousItems].filter(Boolean) as StudentPublicProfileRecentActivity[];
  const unique = new Map<string, StudentPublicProfileRecentActivity>();

  for (const item of merged) {
    if (!unique.has(item.id)) {
      unique.set(item.id, item);
    }
  }

  return Array.from(unique.values()).slice(0, MAX_RECENT_ACTIVITIES);
}

export function readStudentPublicProfileSnapshot(studentUserId: number) {
  const storage = getStorage();
  if (!storage) {
    return null;
  }

  try {
    const raw = storage.getItem(getStorageKey(studentUserId));
    return raw ? JSON.parse(raw) as StudentPublicProfileSnapshot : null;
  } catch {
    return null;
  }
}

export function upsertStudentPublicProfileSnapshot(studentUserId: number, patch: SnapshotPatch) {
  const storage = getStorage();
  if (!storage) {
    return null;
  }

  const previous = readStudentPublicProfileSnapshot(studentUserId);
  const nextSnapshot: StudentPublicProfileSnapshot = {
    studentUserId,
    displayName: patch.displayName?.trim() || previous?.displayName || `学生 ${studentUserId}`,
    communityScore7d: patch.communityScore7d ?? previous?.communityScore7d ?? null,
    leaderboardRank: patch.leaderboardRank ?? previous?.leaderboardRank ?? null,
    postCount: patch.postCount ?? previous?.postCount ?? null,
    commentCount: patch.commentCount ?? previous?.commentCount ?? null,
    likeReceivedCount: patch.likeReceivedCount ?? previous?.likeReceivedCount ?? null,
    latestActivityAt: patch.latestActivityAt ?? previous?.latestActivityAt ?? null,
    recentActivities: normalizeRecentActivities(patch.recentActivities ?? [], previous?.recentActivities ?? []),
    updatedAt: new Date().toISOString(),
  };

  storage.setItem(getStorageKey(studentUserId), JSON.stringify(nextSnapshot));
  return nextSnapshot;
}

export function buildStudentPublicProfileHref(studentUserId: number) {
  return `/students/${studentUserId}`;
}

export function cacheStudentProfileFromCommunityPost(params: {
  studentUserId: number;
  displayName: string;
  title: string;
  postId: number;
  scenarioLabel?: string | null;
  createdAt?: TimeValue;
}) {
  return upsertStudentPublicProfileSnapshot(params.studentUserId, {
    displayName: params.displayName,
    latestActivityAt: params.createdAt ?? null,
    recentActivities: [{
      id: `post-${params.postId}`,
      title: params.title,
      href: `/community/${params.postId}`,
      detail: params.scenarioLabel ?? null,
      createdAt: params.createdAt ?? null,
    }],
  });
}

export function cacheStudentProfileFromCommunityComment(params: {
  studentUserId: number;
  displayName: string;
  postId: number;
  postTitle: string;
  createdAt?: TimeValue;
}) {
  return upsertStudentPublicProfileSnapshot(params.studentUserId, {
    displayName: params.displayName,
    latestActivityAt: params.createdAt ?? null,
    recentActivities: [{
      id: `comment-post-${params.postId}-${params.studentUserId}`,
      title: `参与讨论：${params.postTitle}`,
      href: `/community/${params.postId}`,
      detail: "评论互动",
      createdAt: params.createdAt ?? null,
    }],
  });
}

export function cacheStudentProfileFromLeaderboard(params: {
  studentUserId: number;
  displayName: string;
  leaderboardRank: number;
  communityScore7d: number;
  postCount: number;
  commentCount: number;
  likeReceivedCount: number;
  latestActivityAt?: TimeValue;
}) {
  return upsertStudentPublicProfileSnapshot(params.studentUserId, {
    displayName: params.displayName,
    leaderboardRank: params.leaderboardRank,
    communityScore7d: params.communityScore7d,
    postCount: params.postCount,
    commentCount: params.commentCount,
    likeReceivedCount: params.likeReceivedCount,
    latestActivityAt: params.latestActivityAt ?? null,
  });
}
