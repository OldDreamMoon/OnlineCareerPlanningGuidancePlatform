package com.bishe.server.mentor.repository;

import com.bishe.server.mentor.repository.jpa.MentorRecommendationEventJpaRepository;
import com.bishe.server.mentor.repository.jpa.MentorRecommendationRunJpaRepository;
import com.bishe.server.mentor.repository.jpa.MentorRecommendationSnapshotJpaRepository;
import com.bishe.server.mentor.repository.jpa.RecommendationEmbeddingVectorJpaRepository;
import com.bishe.server.mentor.repository.jpa.StudentRecommendationSnapshotJpaRepository;
import com.bishe.server.mentor.repository.jpa.entity.MentorRecommendationEventEntity;
import com.bishe.server.mentor.repository.jpa.entity.MentorRecommendationRunEntity;
import com.bishe.server.mentor.repository.jpa.entity.MentorRecommendationSnapshotEntity;
import com.bishe.server.mentor.repository.jpa.entity.RecommendationEmbeddingVectorEntity;
import com.bishe.server.mentor.repository.jpa.entity.StudentRecommendationSnapshotEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 导师推荐仓储：持久化推荐快照、向量缓存与推荐运行日志。
 */
@Repository
public class MentorRecommendationRepository {

    private final StudentRecommendationSnapshotJpaRepository studentRecommendationSnapshotJpaRepository;
    private final MentorRecommendationSnapshotJpaRepository mentorRecommendationSnapshotJpaRepository;
    private final RecommendationEmbeddingVectorJpaRepository recommendationEmbeddingVectorJpaRepository;
    private final MentorRecommendationRunJpaRepository mentorRecommendationRunJpaRepository;
    private final MentorRecommendationEventJpaRepository mentorRecommendationEventJpaRepository;

    public MentorRecommendationRepository(
            StudentRecommendationSnapshotJpaRepository studentRecommendationSnapshotJpaRepository,
            MentorRecommendationSnapshotJpaRepository mentorRecommendationSnapshotJpaRepository,
            RecommendationEmbeddingVectorJpaRepository recommendationEmbeddingVectorJpaRepository,
            MentorRecommendationRunJpaRepository mentorRecommendationRunJpaRepository,
            MentorRecommendationEventJpaRepository mentorRecommendationEventJpaRepository
    ) {
        this.studentRecommendationSnapshotJpaRepository = studentRecommendationSnapshotJpaRepository;
        this.mentorRecommendationSnapshotJpaRepository = mentorRecommendationSnapshotJpaRepository;
        this.recommendationEmbeddingVectorJpaRepository = recommendationEmbeddingVectorJpaRepository;
        this.mentorRecommendationRunJpaRepository = mentorRecommendationRunJpaRepository;
        this.mentorRecommendationEventJpaRepository = mentorRecommendationEventJpaRepository;
    }

    public void saveStudentSnapshot(StudentRecommendationSnapshotCommand command) {
        StudentRecommendationSnapshotEntity entity = studentRecommendationSnapshotJpaRepository.findByStudentUserId(command.studentUserId())
                .map(existing -> {
                    existing.apply(command);
                    return existing;
                })
                .orElseGet(() -> StudentRecommendationSnapshotEntity.create(command));
        studentRecommendationSnapshotJpaRepository.saveAndFlush(entity);
    }

    public void saveMentorSnapshot(MentorRecommendationSnapshotCommand command) {
        MentorRecommendationSnapshotEntity entity = mentorRecommendationSnapshotJpaRepository.findByMentorUserId(command.mentorUserId())
                .map(existing -> {
                    existing.apply(command);
                    return existing;
                })
                .orElseGet(() -> MentorRecommendationSnapshotEntity.create(command));
        mentorRecommendationSnapshotJpaRepository.saveAndFlush(entity);
    }

    public Optional<EmbeddingVectorRow> findEmbeddingVector(String entityType, long entityId, String modelCode) {
        return recommendationEmbeddingVectorJpaRepository.findByEntityTypeAndEntityIdAndModelCode(entityType, entityId, modelCode)
                .map(entity -> new EmbeddingVectorRow(
                        entity.getEntityType(),
                        entity.getEntityId(),
                        entity.getModelCode(),
                        entity.getVectorDim(),
                        entity.getVectorJson(),
                        entity.getContentHash()
                ));
    }

    public void saveEmbeddingVector(EmbeddingVectorCommand command) {
        RecommendationEmbeddingVectorEntity entity = recommendationEmbeddingVectorJpaRepository.findByEntityTypeAndEntityIdAndModelCode(
                        command.entityType(),
                        command.entityId(),
                        command.modelCode()
                )
                .map(existing -> {
                    existing.apply(command);
                    return existing;
                })
                .orElseGet(() -> RecommendationEmbeddingVectorEntity.create(command));
        recommendationEmbeddingVectorJpaRepository.saveAndFlush(entity);
    }

    public long createRecommendationRun(RecommendationRunCommand command) {
        MentorRecommendationRunEntity entity = mentorRecommendationRunJpaRepository.saveAndFlush(
                MentorRecommendationRunEntity.create(command)
        );
        return entity.getId() == null ? 0L : entity.getId();
    }

    public void appendRecommendationEvent(RecommendationEventCommand command) {
        mentorRecommendationEventJpaRepository.saveAndFlush(MentorRecommendationEventEntity.create(command));
    }

    public List<Long> findRunIdsBefore(Instant beforeTime, int limit) {
        return mentorRecommendationRunJpaRepository.findRunIdsBefore(
                beforeTime == null ? Instant.now() : beforeTime,
                PageRequest.of(0, Math.max(limit, 1))
        );
    }

    public long deleteEventsByRunIds(List<Long> runIds) {
        return executeChunkedDelete(runIds, true);
    }

    public long deleteRunsByIds(List<Long> runIds) {
        return executeChunkedDelete(runIds, false);
    }

    private long executeChunkedDelete(List<Long> ids, boolean deleteEvents) {
        if (ids == null || ids.isEmpty()) {
            return 0L;
        }
        long deletedCount = 0L;
        int chunkSize = 200;
        for (int start = 0; start < ids.size(); start += chunkSize) {
            List<Long> chunk = ids.subList(start, Math.min(start + chunkSize, ids.size()));
            deletedCount += deleteEvents
                    ? mentorRecommendationEventJpaRepository.deleteByRunIdIn(chunk)
                    : mentorRecommendationRunJpaRepository.deleteByIdIn(chunk);
        }
        return deletedCount;
    }

    public record StudentRecommendationSnapshotCommand(
            long studentUserId,
            String contentText,
            String targetPosition,
            String skillTagsJson,
            String portraitTagsJson,
            Long latestResumeRecordId,
            String latestResumeTargetRole,
            String latestResumeSummary,
            String latestResumeSuggestionsJson,
            String latestInterviewSessionId,
            String latestInterviewTargetRole,
            String latestInterviewWeaknessesJson,
            String latestInterviewSuggestionsJson,
            String signalFlagsJson,
            String contentHash
    ) {
    }

    public record MentorRecommendationSnapshotCommand(
            long mentorUserId,
            String contentText,
            String expertiseTagsJson,
            String serviceScenesJson,
            int qualityScore,
            int priceFen,
            boolean available,
            String contentHash
    ) {
    }

    public record EmbeddingVectorCommand(
            String entityType,
            long entityId,
            String modelCode,
            int vectorDim,
            String vectorJson,
            String contentHash
    ) {
    }

    public record RecommendationRunCommand(
            long studentUserId,
            String scene,
            String keyword,
            String expertise,
            String filterPayloadJson,
            String recallModelCode,
            String rerankVersion,
            boolean weakSignal,
            int candidateCount,
            int recalledCount,
            String topMentorUserIdsJson,
            String basisSummary
    ) {
    }

    public record RecommendationEventCommand(
            long runId,
            long mentorUserId,
            String eventType,
            Integer stageRank,
            Double score,
            String detailJson
    ) {
    }

    public record EmbeddingVectorRow(
            String entityType,
            long entityId,
            String modelCode,
            int vectorDim,
            String vectorJson,
            String contentHash
    ) {
    }
}
