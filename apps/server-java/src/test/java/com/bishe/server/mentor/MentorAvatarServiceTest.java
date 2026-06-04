package com.bishe.server.mentor;

import com.bishe.server.mentor.repository.MentorRepository;
import com.bishe.server.mentor.service.MentorAvatarService;
import com.bishe.server.mentor.service.MentorPublicDetailCacheService;
import com.bishe.server.mentor.service.MentorPublicListCacheService;
import com.bishe.server.storage.MinioStorageProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MentorAvatarServiceTest {

    private final MentorAvatarService mentorAvatarService = new MentorAvatarService(
            mock(MentorRepository.class),
            disabledMinioProperties(),
            mock(MentorPublicDetailCacheService.class),
            mock(MentorPublicListCacheService.class)
    );

    @Test
    void resolveAvatarUrl_shouldUsePublicAvatarEndpointWhenUploadedAvatarExists() {
        String avatarUrl = mentorAvatarService.resolveAvatarUrl(
                42L,
                null,
                "导师林乔",
                "avatars/mentor/20260402/u42/avatar-1.jpg",
                Instant.parse("2026-04-02T10:20:30Z")
        );

        assertThat(avatarUrl)
                .startsWith("/api/v1/mentors/42/avatar?v=")
                .contains("2026-04-02T10%3A20%3A30Z");
    }

    @Test
    void resolveAvatarUrl_shouldProxyAllowlistedRemoteAvatar() {
        String avatarUrl = mentorAvatarService.resolveAvatarUrl(
                77L,
                "https://api.dicebear.com/7.x/notionists/svg?seed=LinQiao",
                "导师宋越",
                null,
                null
        );

        assertThat(avatarUrl)
                .startsWith("/api/v1/mentors/77/avatar?v=")
                .doesNotContain("api.dicebear.com");
    }

    @Test
    void resolveAvatarUrl_shouldKeepNonAllowlistedRemoteAvatarUntouched() {
        String avatarUrl = mentorAvatarService.resolveAvatarUrl(
                88L,
                "https://example.com/custom-mentor.png",
                "导师程砚北",
                null,
                null
        );

        assertThat(avatarUrl).isEqualTo("https://example.com/custom-mentor.png");
    }

    @Test
    void resolveAvatarUrl_shouldFallbackToStablePublicAvatarEndpointWhenNoAvatarConfigured() {
        String avatarUrl = mentorAvatarService.resolveAvatarUrl(
                99L,
                null,
                "导师何槿",
                null,
                null
        );

        assertThat(avatarUrl)
                .startsWith("/api/v1/mentors/99/avatar?v=");
    }

    @Test
    void normalizeEndpoint_shouldAddHttpSchemeWhenMissing() throws Exception {
        Method method = MentorAvatarService.class.getDeclaredMethod("normalizeEndpoint", String.class);
        method.setAccessible(true);

        String normalized = (String) method.invoke(mentorAvatarService, "127.0.0.1:9000");

        assertThat(normalized).isEqualTo("http://127.0.0.1:9000");
    }

    private static MinioStorageProperties disabledMinioProperties() {
        MinioStorageProperties properties = new MinioStorageProperties();
        properties.setEnabled(false);
        return properties;
    }
}
