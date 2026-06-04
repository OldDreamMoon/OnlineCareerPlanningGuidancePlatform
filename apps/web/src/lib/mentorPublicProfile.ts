import { buildQuery } from "./apiClient";

export function buildMentorPublicDetailHref(mentorUserId: number, source?: string | null) {
  return `/mentors/${mentorUserId}${buildQuery({
    from: source?.trim() || undefined,
  })}`;
}
