package com.bishe.server.mentor.repository;

import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.mentor.repository.jpa.MentorServicePackageJpaRepository;
import com.bishe.server.mentor.repository.jpa.entity.MentorServicePackageEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 导师服务套餐仓储。
 */
@Repository
public class MentorServicePackageRepository {

    private final MentorServicePackageJpaRepository mentorServicePackageJpaRepository;

    public MentorServicePackageRepository(MentorServicePackageJpaRepository mentorServicePackageJpaRepository) {
        this.mentorServicePackageJpaRepository = mentorServicePackageJpaRepository;
    }

    public List<MentorServicePackageRow> findPackagesByMentorUserId(long mentorUserId, boolean onlyEnabled) {
        List<MentorServicePackageEntity> entities = onlyEnabled
                ? mentorServicePackageJpaRepository.findByMentorUserIdAndEnabledTrueOrderBySortNoAscIdAsc(mentorUserId)
                : mentorServicePackageJpaRepository.findByMentorUserIdOrderBySortNoAscIdAsc(mentorUserId);
        return entities.stream()
                .map(this::toRow)
                .toList();
    }

    public Optional<MentorServicePackageRow> findEnabledPackageByIdForMentor(long mentorUserId, long packageId) {
        return mentorServicePackageJpaRepository.findByMentorUserIdAndIdAndEnabledTrue(mentorUserId, packageId)
                .map(this::toRow);
    }

    @Transactional
    public void replacePackagesForMentor(long mentorUserId, List<CreatePackageCommand> commands) {
        mentorServicePackageJpaRepository.deleteAllByMentorUserId(mentorUserId);
        if (commands == null || commands.isEmpty()) {
            return;
        }
        List<MentorServicePackageEntity> entities = new ArrayList<>();
        for (CreatePackageCommand command : commands) {
            entities.add(toEntity(mentorUserId, command));
        }
        mentorServicePackageJpaRepository.saveAllAndFlush(entities);
    }

    private MentorServicePackageRow toRow(MentorServicePackageEntity entity) {
        return new MentorServicePackageRow(
                entity.getId(),
                entity.getMentorUserId(),
                TextListCodec.normalizeText(entity.getPackageName()),
                TextListCodec.normalizeText(entity.getSceneCode()),
                TextListCodec.normalizeText(entity.getSceneLabel()),
                TextListCodec.normalizeText(entity.getDeliveryMode()),
                entity.getDurationMinutes(),
                entity.getPriceFen(),
                TextListCodec.normalizeText(entity.getDescription()),
                entity.isEnabled(),
                entity.getSortNo(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private MentorServicePackageEntity toEntity(long mentorUserId, CreatePackageCommand command) {
        MentorServicePackageEntity entity = MentorServicePackageEntity.create(mentorUserId);
        entity.setPackageName(command.packageName());
        entity.setSceneCode(command.sceneCode());
        entity.setSceneLabel(command.sceneLabel());
        entity.setDeliveryMode(command.deliveryMode());
        entity.setDurationMinutes(command.durationMinutes());
        entity.setPriceFen(command.priceFen());
        entity.setDescription(command.description());
        entity.setEnabled(command.enabled());
        entity.setSortNo(command.sortNo());
        return entity;
    }

    public record MentorServicePackageRow(
            long id,
            long mentorUserId,
            String packageName,
            String sceneCode,
            String sceneLabel,
            String deliveryMode,
            Integer durationMinutes,
            int priceFen,
            String description,
            boolean enabled,
            int sortNo,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreatePackageCommand(
            String packageName,
            String sceneCode,
            String sceneLabel,
            String deliveryMode,
            Integer durationMinutes,
            int priceFen,
            String description,
            boolean enabled,
            int sortNo
    ) {
    }
}
