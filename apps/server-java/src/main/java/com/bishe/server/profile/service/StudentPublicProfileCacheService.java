package com.bishe.server.profile.service;

import com.bishe.server.profile.dto.StudentProfileResponse;
import com.bishe.server.profile.dto.StudentProfileSocialLinkItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 学生公开资料主体缓存：只缓存公开资料切片原始数据，不缓存 viewer 视角裁切结果。
 */
@Service
public class StudentPublicProfileCacheService {

    private static final Logger log = LoggerFactory.getLogger(StudentPublicProfileCacheService.class);
    private static final String KEY_PREFIX = "student:profile:public:";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final TypeReference<StudentPublicProfileSlice> SLICE_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public StudentPublicProfileCacheService(
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public StudentPublicProfileSlice getPublicProfile(
            long studentUserId,
            Supplier<StudentPublicProfileSlice> databaseLoader
    ) {
        if (studentUserId <= 0 || databaseLoader == null) {
            return null;
        }
        if (stringRedisTemplate == null) {
            return sanitize(databaseLoader.get());
        }

        String key = buildKey(studentUserId);
        StudentPublicProfileSlice cached = readFromRedis(key);
        if (cached != null) {
            return cached;
        }

        StudentPublicProfileSlice loaded = sanitize(databaseLoader.get());
        if (loaded == null) {
            return null;
        }
        writeToRedis(key, loaded);
        StudentPublicProfileSlice refreshed = readFromRedis(key);
        return refreshed == null ? loaded : refreshed;
    }

    public void evictNow(long studentUserId) {
        evict(studentUserId);
    }

    public void evictAfterCommit(long studentUserId) {
        runAfterCommit(() -> evict(studentUserId));
    }

    private StudentPublicProfileSlice readFromRedis(String key) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return sanitize(objectMapper.readValue(payload, SLICE_TYPE));
        } catch (Exception ex) {
            log.debug("read student public profile cache from redis failed: {}", ex.getMessage());
            return null;
        }
    }

    private void writeToRedis(String key, StudentPublicProfileSlice slice) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(slice), TTL);
        } catch (Exception ex) {
            log.debug("write student public profile cache to redis failed: {}", ex.getMessage());
        }
    }

    private void evict(long studentUserId) {
        if (stringRedisTemplate == null || studentUserId <= 0) {
            return;
        }
        try {
            stringRedisTemplate.delete(buildKey(studentUserId));
        } catch (Exception ex) {
            log.debug("evict student public profile cache from redis failed: {}", ex.getMessage());
        }
    }

    private void runAfterCommit(Runnable action) {
        if (action == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private String buildKey(long studentUserId) {
        return KEY_PREFIX + studentUserId;
    }

    private StudentPublicProfileSlice sanitize(StudentPublicProfileSlice slice) {
        if (slice == null
                || slice.userId() <= 0
                || slice.avatar() == null
                || slice.privacy() == null
                || slice.portrait() == null) {
            return null;
        }
        return new StudentPublicProfileSlice(
                slice.userId(),
                slice.displayName(),
                slice.tier(),
                slice.realName(),
                slice.jobStatus(),
                slice.schoolName(),
                slice.schoolNameKey(),
                slice.major(),
                slice.grade(),
                slice.gpa(),
                slice.targetPosition(),
                slice.honors(),
                sanitizeStringList(slice.skillTags()),
                slice.selfIntro(),
                new StudentProfileResponse.AvatarPayload(
                        slice.avatar().uploaded(),
                        slice.avatar().contentType(),
                        slice.avatar().updatedAt()
                ),
                sanitizeSocialLinks(slice.socialLinks()),
                slice.privacy(),
                sanitizePortrait(slice.portrait()),
                slice.communityScore7d()
        );
    }

    private List<String> sanitizeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private List<StudentProfileSocialLinkItem> sanitizeSocialLinks(List<StudentProfileSocialLinkItem> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private StudentProfileResponse.PortraitPayload sanitizePortrait(StudentProfileResponse.PortraitPayload portrait) {
        return new StudentProfileResponse.PortraitPayload(
                portrait.tags() == null ? List.of() : portrait.tags().stream().filter(Objects::nonNull).toList(),
                portrait.strengthTags() == null ? List.of() : portrait.strengthTags().stream().filter(Objects::nonNull).toList(),
                portrait.riskTags() == null ? List.of() : portrait.riskTags().stream().filter(Objects::nonNull).toList(),
                portrait.signalLevel(),
                portrait.freshnessLevel(),
                portrait.headline(),
                portrait.summary(),
                portrait.nextActions() == null ? List.of() : portrait.nextActions().stream().filter(Objects::nonNull).toList(),
                portrait.summaryVersion(),
                portrait.evidence(),
                portrait.updatedAt()
        );
    }

    public record StudentPublicProfileSlice(
            long userId,
            String displayName,
            String tier,
            String realName,
            String jobStatus,
            String schoolName,
            String schoolNameKey,
            String major,
            String grade,
            String gpa,
            String targetPosition,
            String honors,
            List<String> skillTags,
            String selfIntro,
            StudentProfileResponse.AvatarPayload avatar,
            List<StudentProfileSocialLinkItem> socialLinks,
            StudentProfileResponse.PrivacySettingsPayload privacy,
            StudentProfileResponse.PortraitPayload portrait,
            long communityScore7d
    ) {
    }
}
