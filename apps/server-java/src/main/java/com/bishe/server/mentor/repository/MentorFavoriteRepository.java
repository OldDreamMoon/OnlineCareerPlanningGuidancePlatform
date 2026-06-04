package com.bishe.server.mentor.repository;

import com.bishe.server.mentor.repository.jpa.MentorFavoriteJpaRepository;
import com.bishe.server.mentor.repository.jpa.entity.MentorFavoriteEntity;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 导师收藏关系仓储。
 */
@Repository
public class MentorFavoriteRepository {

    private final MentorFavoriteJpaRepository mentorFavoriteJpaRepository;

    public MentorFavoriteRepository(MentorFavoriteJpaRepository mentorFavoriteJpaRepository) {
        this.mentorFavoriteJpaRepository = mentorFavoriteJpaRepository;
    }

    public void addFavorite(long studentUserId, long mentorUserId) {
        if (studentUserId <= 0 || mentorUserId <= 0) {
            return;
        }
        if (mentorFavoriteJpaRepository.existsByStudentUserIdAndMentorUserId(studentUserId, mentorUserId)) {
            return;
        }
        mentorFavoriteJpaRepository.save(MentorFavoriteEntity.create(studentUserId, mentorUserId));
    }

    public boolean removeFavorite(long studentUserId, long mentorUserId) {
        if (studentUserId <= 0 || mentorUserId <= 0) {
            return false;
        }
        return mentorFavoriteJpaRepository.deleteByStudentUserIdAndMentorUserId(studentUserId, mentorUserId) > 0;
    }

    public boolean isFavorited(long studentUserId, long mentorUserId) {
        if (studentUserId <= 0 || mentorUserId <= 0) {
            return false;
        }
        return mentorFavoriteJpaRepository.existsByStudentUserIdAndMentorUserId(studentUserId, mentorUserId);
    }

    public long countFavoritesByStudent(long studentUserId) {
        if (studentUserId <= 0) {
            return 0L;
        }
        return mentorFavoriteJpaRepository.countByStudentUserId(studentUserId);
    }

    public List<Long> findFavoriteMentorUserIds(long studentUserId) {
        if (studentUserId <= 0) {
            return List.of();
        }
        return mentorFavoriteJpaRepository.findMentorUserIdsByStudentUserId(studentUserId);
    }

    public Set<Long> findFavoritedMentorUserIds(long studentUserId, List<Long> mentorUserIds) {
        if (studentUserId <= 0 || mentorUserIds == null || mentorUserIds.isEmpty()) {
            return Set.of();
        }
        List<Long> distinctMentorUserIds = mentorUserIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctMentorUserIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(
                mentorFavoriteJpaRepository.findMentorUserIdsByStudentUserIdAndMentorUserIdIn(studentUserId, distinctMentorUserIds)
        );
    }
}
