package com.bishe.server.community.repository;

import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.jpa.UserAccountJpaRepository;
import com.bishe.server.auth.repository.jpa.entity.UserAccountEntity;
import com.bishe.server.community.repository.jpa.CommunityCommentJpaRepository;
import com.bishe.server.community.repository.jpa.CommunityMentorProfileJpaRepository;
import com.bishe.server.community.repository.jpa.CommunityPostJpaRepository;
import com.bishe.server.community.repository.jpa.CommunityPostLikeJpaRepository;
import com.bishe.server.community.repository.jpa.entity.CommunityMentorProfileEntity;
import com.bishe.server.profile.repository.jpa.entity.CommentEntity;
import com.bishe.server.profile.repository.jpa.entity.PostEntity;
import com.bishe.server.profile.repository.jpa.entity.PostLikeEntity;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 社区帖子、评论与点赞仓储。
 */
@Repository
public class CommunityRepository {

    private static final String VISIBLE_MODERATION_STATUS = "PASS";

    private final CommunityPostJpaRepository communityPostJpaRepository;
    private final CommunityCommentJpaRepository communityCommentJpaRepository;
    private final CommunityPostLikeJpaRepository communityPostLikeJpaRepository;
    private final UserAccountJpaRepository userAccountJpaRepository;
    private final CommunityMentorProfileJpaRepository communityMentorProfileJpaRepository;

    public CommunityRepository(
            CommunityPostJpaRepository communityPostJpaRepository,
            CommunityCommentJpaRepository communityCommentJpaRepository,
            CommunityPostLikeJpaRepository communityPostLikeJpaRepository,
            UserAccountJpaRepository userAccountJpaRepository,
            CommunityMentorProfileJpaRepository communityMentorProfileJpaRepository
    ) {
        this.communityPostJpaRepository = communityPostJpaRepository;
        this.communityCommentJpaRepository = communityCommentJpaRepository;
        this.communityPostLikeJpaRepository = communityPostLikeJpaRepository;
        this.userAccountJpaRepository = userAccountJpaRepository;
        this.communityMentorProfileJpaRepository = communityMentorProfileJpaRepository;
    }

    public long createPost(
            long userId,
            String title,
            String scenarioCode,
            String content,
            String tagsCsv,
            String resolvedStatus,
            String moderationStatus,
            String riskLevel,
            long lastModerationEventId
    ) {
        PostEntity entity = PostEntity.create(
                userId,
                title,
                scenarioCode,
                content,
                tagsCsv,
                resolvedStatus,
                moderationStatus,
                riskLevel,
                lastModerationEventId
        );
        communityPostJpaRepository.saveAndFlush(entity);
        if (entity.getId() == null) {
            throw new IllegalStateException("failed to create community post");
        }
        return entity.getId();
    }

    public long createComment(long postId, long userId, String content, String moderationStatus, String riskLevel, long lastModerationEventId) {
        return saveComment(postId, userId, content, false, moderationStatus, riskLevel, lastModerationEventId);
    }

    public long createAiComment(long postId, long userId, String content, String moderationStatus, String riskLevel, long lastModerationEventId) {
        return saveComment(postId, userId, content, true, moderationStatus, riskLevel, lastModerationEventId);
    }

    public long countVisiblePosts(long viewerUserId, String keyword, String tag, String scenarioCode) {
        return communityPostJpaRepository.count(buildVisiblePostSpecification(viewerUserId, keyword, tag, scenarioCode));
    }

    public long countOwnNonPublicPosts(long viewerUserId, String keyword, String tag, String scenarioCode) {
        if (viewerUserId <= 0) {
            return 0L;
        }
        return communityPostJpaRepository.count(buildOwnNonPublicPostSpecification(viewerUserId, keyword, tag, scenarioCode));
    }

    public List<PostSummaryRow> findVisiblePosts(long viewerUserId, String keyword, String tag, String scenarioCode, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        PageRequest pageRequest = PageRequest.of(
                safePage - 1,
                safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        List<PostEntity> posts = communityPostJpaRepository.findAll(
                        buildVisiblePostSpecification(viewerUserId, keyword, tag, scenarioCode),
                        pageRequest
                )
                .getContent();
        return toPostRows(posts, viewerUserId);
    }

    public Optional<PostSummaryRow> findVisiblePostDetail(long viewerUserId, long postId) {
        return communityPostJpaRepository.findOne(buildVisiblePostDetailSpecification(viewerUserId, postId))
                .flatMap(post -> toPostRows(List.of(post), viewerUserId).stream().findFirst());
    }

    public Optional<PostSummaryRow> findAdminReadablePostDetail(long postId) {
        return communityPostJpaRepository.findReadableById(postId)
                .flatMap(post -> toPostRows(List.of(post), 0L).stream().findFirst());
    }

    public Optional<PostContextRow> findPostContext(long postId) {
        return communityPostJpaRepository.findReadableById(postId)
                .map(post -> {
                    UserAccountEntity author = loadUserAccountMap(List.of(post.getUserId())).get(post.getUserId());
                    if (author == null) {
                        return null;
                    }
                    return new PostContextRow(
                            longValue(post.getId()),
                            longValue(post.getUserId()),
                            author.getDisplayName(),
                            author.getRole() == null ? null : author.getRole().name(),
                            post.getTitle(),
                            post.getScenarioCode(),
                            post.getResolvedStatus(),
                            post.getModerationStatus(),
                            post.getRiskLevel(),
                            post.getContent()
                    );
                })
                .filter(Objects::nonNull);
    }

    public Set<Long> findLikedPostIds(long viewerUserId, List<Long> postIds) {
        if (viewerUserId <= 0 || postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(communityPostLikeJpaRepository.findLikedPostIds(viewerUserId, postIds));
    }

    public Set<Long> findParticipatedPostIds(long viewerUserId, List<Long> postIds) {
        if (viewerUserId <= 0 || postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(communityCommentJpaRepository.findParticipatedPostIds(
                viewerUserId,
                postIds,
                VISIBLE_MODERATION_STATUS
        ));
    }

    public Optional<Long> findPostAuthorUserId(long postId) {
        return communityPostJpaRepository.findAuthorUserIdByIdAndDeletedFalse(postId);
    }

    public List<CommentRow> findVisibleComments(long postId) {
        return toCommentRows(communityCommentJpaRepository.findVisibleComments(postId, VISIBLE_MODERATION_STATUS));
    }

    public List<CommentRow> findAdminReadableComments(long postId) {
        return toCommentRows(communityCommentJpaRepository.findAdminReadableComments(postId));
    }

    public boolean existsVisiblePost(long postId) {
        return communityPostJpaRepository.existsByIdAndDeletedFalseAndModerationStatus(postId, VISIBLE_MODERATION_STATUS);
    }

    public List<Long> findVisiblePostIdsByAuthorUserId(long userId) {
        return communityPostJpaRepository.findIdsByUserIdAndDeletedFalseAndModerationStatus(userId, VISIBLE_MODERATION_STATUS);
    }

    public List<Long> findVisibleCommentedPostIdsByUserId(long userId) {
        return communityCommentJpaRepository.findVisibleCommentedPostIdsByUserId(
                userId,
                VISIBLE_MODERATION_STATUS,
                VISIBLE_MODERATION_STATUS
        );
    }

    public boolean hasLike(long postId, long userId) {
        return communityPostLikeJpaRepository.existsByPostIdAndUserId(postId, userId);
    }

    public void addLike(long postId, long userId) {
        communityPostLikeJpaRepository.saveAndFlush(PostLikeEntity.create(postId, userId));
    }

    public void removeLike(long postId, long userId) {
        communityPostLikeJpaRepository.deleteByPostIdAndUserId(postId, userId);
    }

    public long countLikes(long postId) {
        return communityPostLikeJpaRepository.countByPostId(postId);
    }

    public void updateResolvedStatus(long postId, String resolvedStatus) {
        PostEntity entity = communityPostJpaRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new IllegalStateException("community post not found: " + postId));
        entity.updateResolvedStatus(resolvedStatus);
        communityPostJpaRepository.saveAndFlush(entity);
    }

    public long countLeaderboardRows(Instant windowStart) {
        return buildLeaderboardRows(windowStart).size();
    }

    public List<LeaderboardRow> findLeaderboardRows(Instant windowStart, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        List<LeaderboardRow> rows = buildLeaderboardRows(windowStart);
        int fromIndex = Math.min((safePage - 1) * safeSize, rows.size());
        int toIndex = Math.min(fromIndex + safeSize, rows.size());
        return rows.subList(fromIndex, toIndex);
    }

    private long saveComment(
            long postId,
            long userId,
            String content,
            boolean ai,
            String moderationStatus,
            String riskLevel,
            long lastModerationEventId
    ) {
        CommentEntity entity = CommentEntity.create(
                postId,
                userId,
                content,
                ai,
                moderationStatus,
                riskLevel,
                lastModerationEventId
        );
        communityCommentJpaRepository.saveAndFlush(entity);
        if (entity.getId() == null) {
            throw new IllegalStateException("failed to create community comment");
        }
        return entity.getId();
    }

    private Specification<PostEntity> buildVisiblePostSpecification(long viewerUserId, String keyword, String tag, String scenarioCode) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedScenarioCode = normalizeScenarioCode(scenarioCode);
        return (root, query, criteriaBuilder) -> {
            Join<PostEntity, UserAccountEntity> author = root.join("author");
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isFalse(root.get("deleted")));
            predicates.add(criteriaBuilder.isFalse(author.get("deleted")));
            if (viewerUserId > 0) {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.equal(root.get("moderationStatus"), VISIBLE_MODERATION_STATUS),
                        criteriaBuilder.equal(root.get("userId"), viewerUserId)
                ));
            } else {
                predicates.add(criteriaBuilder.equal(root.get("moderationStatus"), VISIBLE_MODERATION_STATUS));
            }
            appendPostFilters(root, criteriaBuilder, predicates, normalizedKeyword, tag, normalizedScenarioCode);
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<PostEntity> buildVisiblePostDetailSpecification(long viewerUserId, long postId) {
        return buildVisiblePostSpecification(viewerUserId, null, null, null)
                .and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("id"), postId));
    }

    private Specification<PostEntity> buildOwnNonPublicPostSpecification(long viewerUserId, String keyword, String tag, String scenarioCode) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedScenarioCode = normalizeScenarioCode(scenarioCode);
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("userId"), viewerUserId));
            predicates.add(criteriaBuilder.isFalse(root.get("deleted")));
            predicates.add(criteriaBuilder.notEqual(root.get("moderationStatus"), VISIBLE_MODERATION_STATUS));
            appendPostFilters(root, criteriaBuilder, predicates, normalizedKeyword, tag, normalizedScenarioCode);
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void appendPostFilters(
            jakarta.persistence.criteria.Root<PostEntity> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            List<Predicate> predicates,
            String normalizedKeyword,
            String tag,
            String normalizedScenarioCode
    ) {
        if (normalizedKeyword != null) {
            String pattern = "%" + normalizedKeyword + "%";
            predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("content")), pattern)
            ));
        }
        if (StringUtils.hasText(tag)) {
            Expression<String> tags = criteriaBuilder.coalesce(root.get("tags"), "");
            Expression<String> tagsWithBoundaries = criteriaBuilder.concat(
                    criteriaBuilder.concat(",", tags),
                    ","
            );
            predicates.add(criteriaBuilder.like(tagsWithBoundaries, "%," + tag.trim() + ",%"));
        }
        if (normalizedScenarioCode != null) {
            predicates.add(criteriaBuilder.equal(root.get("scenarioCode"), normalizedScenarioCode));
        }
    }

    private List<PostSummaryRow> toPostRows(List<PostEntity> posts, long viewerUserId) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.stream()
                .map(PostEntity::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, CommentAggregation> commentAggregationMap = loadCommentAggregationMap(postIds);
        Map<Long, Long> likeCountMap = loadLikeCountMap(postIds);
        Set<Long> likedPostIds = viewerUserId > 0 ? findLikedPostIds(viewerUserId, postIds) : Set.of();
        Set<Long> participatedPostIds = viewerUserId > 0 ? findParticipatedPostIds(viewerUserId, postIds) : Set.of();
        List<Long> authorUserIds = posts.stream()
                .map(PostEntity::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, UserAccountEntity> userMap = loadUserAccountMap(authorUserIds);
        Map<Long, CommunityMentorProfileEntity> mentorProfileMap = loadMentorProfileMap(authorUserIds);
        return posts.stream()
                .map(post -> toPostRow(
                        post,
                        viewerUserId,
                        commentAggregationMap.getOrDefault(post.getId(), CommentAggregation.EMPTY),
                        likeCountMap.getOrDefault(post.getId(), 0L),
                        likedPostIds,
                        participatedPostIds,
                        userMap,
                        mentorProfileMap
                ))
                .filter(Objects::nonNull)
                .toList();
    }

    private PostSummaryRow toPostRow(
            PostEntity post,
            long viewerUserId,
            CommentAggregation commentAggregation,
            long likeCount,
            Set<Long> likedPostIds,
            Set<Long> participatedPostIds,
            Map<Long, UserAccountEntity> userMap,
            Map<Long, CommunityMentorProfileEntity> mentorProfileMap
    ) {
        UserAccountEntity author = userMap.get(post.getUserId());
        if (author == null) {
            return null;
        }
        CommunityMentorProfileEntity mentorProfile = isMentor(author) ? mentorProfileMap.get(post.getUserId()) : null;
        boolean authoredByMe = viewerUserId > 0 && post.getUserId() != null && post.getUserId() == viewerUserId;
        boolean likedByMe = viewerUserId > 0 && post.getId() != null && likedPostIds.contains(post.getId());
        boolean participatedByMe = authoredByMe || (viewerUserId > 0 && post.getId() != null && participatedPostIds.contains(post.getId()));
        return new PostSummaryRow(
                longValue(post.getId()),
                longValue(post.getUserId()),
                author.getDisplayName(),
                resolveVisibleRealName(author, mentorProfile),
                shouldShowRealName(author, mentorProfile),
                author.getRole() == null ? null : author.getRole().name(),
                mentorProfile == null ? null : mentorProfile.getAvatarUrl(),
                mentorProfile == null ? null : mentorProfile.getAvatarObjectKey(),
                mentorProfile == null ? null : mentorProfile.getAvatarUpdatedAt(),
                mentorProfile != null,
                post.getTitle(),
                post.getScenarioCode(),
                post.getResolvedStatus(),
                post.getModerationStatus(),
                post.getRiskLevel(),
                post.getContent(),
                post.getTags(),
                commentAggregation.commentCount(),
                likeCount,
                likedByMe,
                commentAggregation.hasMentorReply(),
                authoredByMe,
                participatedByMe,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    private List<CommentRow> toCommentRows(List<CommentEntity> comments) {
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = comments.stream()
                .map(CommentEntity::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, UserAccountEntity> userMap = loadUserAccountMap(userIds);
        Map<Long, CommunityMentorProfileEntity> mentorProfileMap = loadMentorProfileMap(userIds);
        return comments.stream()
                .map(comment -> {
                    UserAccountEntity author = userMap.get(comment.getUserId());
                    if (author == null) {
                        return null;
                    }
                    CommunityMentorProfileEntity mentorProfile = isMentor(author) ? mentorProfileMap.get(comment.getUserId()) : null;
                    return new CommentRow(
                            longValue(comment.getId()),
                            longValue(comment.getUserId()),
                            author.getDisplayName(),
                            resolveVisibleRealName(author, mentorProfile),
                            shouldShowRealName(author, mentorProfile),
                            author.getRole() == null ? null : author.getRole().name(),
                            mentorProfile == null ? null : mentorProfile.getAvatarUrl(),
                            mentorProfile == null ? null : mentorProfile.getAvatarObjectKey(),
                            mentorProfile == null ? null : mentorProfile.getAvatarUpdatedAt(),
                            mentorProfile != null,
                            comment.getContent(),
                            comment.isAi(),
                            comment.getCreatedAt()
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<Long, CommentAggregation> loadCommentAggregationMap(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return communityCommentJpaRepository.summarizeVisibleCommentsByPostIds(postIds, VISIBLE_MODERATION_STATUS)
                .stream()
                .filter(item -> item.getPostId() != null)
                .collect(Collectors.toMap(
                        CommunityCommentJpaRepository.PostCommentStatsView::getPostId,
                        item -> new CommentAggregation(item.getCommentCount(), item.getMentorReplyCount() > 0),
                        (left, right) -> right
                ));
    }

    private Map<Long, Long> loadLikeCountMap(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return communityPostLikeJpaRepository.countLikesByPostIds(postIds)
                .stream()
                .filter(item -> item.getPostId() != null)
                .collect(Collectors.toMap(
                        CommunityPostLikeJpaRepository.PostLikeCountView::getPostId,
                        CommunityPostLikeJpaRepository.PostLikeCountView::getLikeCount,
                        CommunityRepository::sumLongs
                ));
    }

    private static Long sumLongs(Long left, Long right) {
        return Long.valueOf((left == null ? 0L : left) + (right == null ? 0L : right));
    }

    private Map<Long, UserAccountEntity> loadUserAccountMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userAccountJpaRepository.findAllById(userIds)
                .stream()
                .filter(entity -> entity.getId() != null && !entity.isDeleted())
                .collect(Collectors.toMap(
                        UserAccountEntity::getId,
                        Function.identity(),
                        (left, right) -> right
                ));
    }

    private Map<Long, CommunityMentorProfileEntity> loadMentorProfileMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return communityMentorProfileJpaRepository.findAllByUserIdIn(userIds)
                .stream()
                .filter(entity -> entity.getUserId() != null)
                .collect(Collectors.toMap(
                        CommunityMentorProfileEntity::getUserId,
                        Function.identity(),
                        (left, right) -> right
                ));
    }

    private List<LeaderboardRow> buildLeaderboardRows(Instant windowStart) {
        List<Long> studentUserIds = userAccountJpaRepository.findIdsByRoleAndDeletedFalseOrderByIdAsc(UserRole.STUDENT);
        if (studentUserIds.isEmpty()) {
            return List.of();
        }
        Map<Long, UserAccountEntity> userMap = loadUserAccountMap(studentUserIds);
        Map<Long, LeaderboardStat> postStats = communityPostJpaRepository.summarizeVisiblePostsSince(VISIBLE_MODERATION_STATUS, windowStart)
                .stream()
                .filter(item -> item.getUserId() != null)
                .collect(Collectors.toMap(
                        CommunityPostJpaRepository.LeaderboardPostStatView::getUserId,
                        item -> new LeaderboardStat(item.getPostCount(), item.getLastPostAt()),
                        LeaderboardStat::merge
                ));
        Map<Long, LeaderboardStat> commentStats = communityCommentJpaRepository.summarizeVisibleCommentsSince(VISIBLE_MODERATION_STATUS, windowStart)
                .stream()
                .filter(item -> item.getUserId() != null)
                .collect(Collectors.toMap(
                        CommunityCommentJpaRepository.LeaderboardCommentStatView::getUserId,
                        item -> new LeaderboardStat(item.getCommentCount(), item.getLastCommentAt()),
                        LeaderboardStat::merge
                ));
        Map<Long, LeaderboardStat> likeStats = communityPostLikeJpaRepository.summarizeLikesReceivedSince(VISIBLE_MODERATION_STATUS, windowStart)
                .stream()
                .filter(item -> item.getUserId() != null)
                .collect(Collectors.toMap(
                        CommunityPostLikeJpaRepository.LeaderboardLikeStatView::getUserId,
                        item -> new LeaderboardStat(item.getLikeReceivedCount(), item.getLastLikeAt()),
                        LeaderboardStat::merge
                ));
        return studentUserIds.stream()
                .map(userId -> toLeaderboardRow(userId, userMap.get(userId), postStats, commentStats, likeStats))
                .filter(Objects::nonNull)
                .sorted((left, right) -> {
                    int scoreCompare = Long.compare(right.score(), left.score());
                    if (scoreCompare != 0) {
                        return scoreCompare;
                    }
                    int activityCompare = compareLatestActivity(right.latestActivityAt(), left.latestActivityAt());
                    if (activityCompare != 0) {
                        return activityCompare;
                    }
                    return Long.compare(left.studentUserId(), right.studentUserId());
                })
                .toList();
    }

    private LeaderboardRow toLeaderboardRow(
            long userId,
            UserAccountEntity user,
            Map<Long, LeaderboardStat> postStats,
            Map<Long, LeaderboardStat> commentStats,
            Map<Long, LeaderboardStat> likeStats
    ) {
        if (user == null) {
            return null;
        }
        LeaderboardStat postStat = postStats.getOrDefault(userId, LeaderboardStat.EMPTY);
        LeaderboardStat commentStat = commentStats.getOrDefault(userId, LeaderboardStat.EMPTY);
        LeaderboardStat likeStat = likeStats.getOrDefault(userId, LeaderboardStat.EMPTY);
        long score = postStat.count() * 5 + commentStat.count() * 2 + likeStat.count();
        if (score <= 0) {
            return null;
        }
        return new LeaderboardRow(
                userId,
                user.getDisplayName(),
                score,
                safeInt(postStat.count()),
                safeInt(commentStat.count()),
                safeInt(likeStat.count()),
                latestActivityAt(postStat.latestAt(), commentStat.latestAt(), likeStat.latestAt())
        );
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim().toLowerCase();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeScenarioCode(String scenarioCode) {
        if (!StringUtils.hasText(scenarioCode)) {
            return null;
        }
        return scenarioCode.trim().toUpperCase();
    }

    private boolean isMentor(UserAccountEntity user) {
        return user != null && user.getRole() == UserRole.MENTOR;
    }

    private boolean shouldShowRealName(UserAccountEntity user, CommunityMentorProfileEntity mentorProfile) {
        return isMentor(user) && mentorProfile != null && mentorProfile.isShowRealName();
    }

    private String resolveVisibleRealName(UserAccountEntity user, CommunityMentorProfileEntity mentorProfile) {
        return shouldShowRealName(user, mentorProfile) ? user.getRealName() : null;
    }

    private Instant latestActivityAt(Instant... instants) {
        Instant latest = null;
        if (instants == null) {
            return null;
        }
        for (Instant instant : instants) {
            if (instant == null) {
                continue;
            }
            if (latest == null || instant.isAfter(latest)) {
                latest = instant;
            }
        }
        return latest;
    }

    private int compareLatestActivity(Instant left, Instant right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private int safeInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private long longValue(Long value) {
        return value == null ? 0L : value;
    }

    private record CommentAggregation(long commentCount, boolean hasMentorReply) {
        private static final CommentAggregation EMPTY = new CommentAggregation(0L, false);
    }

    private record LeaderboardStat(long count, Instant latestAt) {
        private static final LeaderboardStat EMPTY = new LeaderboardStat(0L, null);

        private static LeaderboardStat merge(LeaderboardStat left, LeaderboardStat right) {
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return new LeaderboardStat(
                    left.count + right.count,
                    left.latestAt == null || (right.latestAt != null && right.latestAt.isAfter(left.latestAt))
                            ? right.latestAt
                            : left.latestAt
            );
        }
    }

    /**
     * 帖子摘要/详情视图。
     */
    public record PostSummaryRow(
            long postId,
            long authorUserId,
            String authorDisplayName,
            String authorRealName,
            boolean authorShowRealName,
            String authorRole,
            String authorAvatarUrl,
            String authorAvatarObjectKey,
            Instant authorAvatarUpdatedAt,
            boolean hasMentorProfile,
            String title,
            String scenarioCode,
            String resolvedStatus,
            String moderationStatus,
            String riskLevel,
            String content,
            String tags,
            long commentCount,
            long likeCount,
            boolean likedByMe,
            boolean hasMentorReply,
            boolean authoredByMe,
            boolean participatedByMe,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    /**
     * 帖子上下文视图。
     */
    public record PostContextRow(
            long postId,
            long authorUserId,
            String authorDisplayName,
            String authorRole,
            String title,
            String scenarioCode,
            String resolvedStatus,
            String moderationStatus,
            String riskLevel,
            String content
    ) {
    }

    /**
     * 评论视图。
     */
    public record CommentRow(
            long commentId,
            long userId,
            String displayName,
            String realName,
            boolean showRealName,
            String role,
            String avatarUrl,
            String avatarObjectKey,
            Instant avatarUpdatedAt,
            boolean hasMentorProfile,
            String content,
            boolean ai,
            Instant createdAt
    ) {
    }

    /**
     * 贡献榜聚合视图。
     */
    public record LeaderboardRow(
            long studentUserId,
            String displayName,
            long score,
            int postCount,
            int commentCount,
            int likeReceivedCount,
            Instant latestActivityAt
    ) {
    }
}
