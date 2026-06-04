package com.bishe.server.notification.service;

import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.notification.dto.AdminNotificationAnnouncementRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationAnnouncementServiceTest {

    @Test
    void shouldDefaultAdminAnnouncementEmailRequestToFalseWhenOmitted() {
        UserRepository userRepository = mock(UserRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        when(userRepository.findActiveUserIdsByRoles(any())).thenReturn(List.of(18L));
        when(notificationService.publish(any())).thenReturn(
                new PlatformNotificationPublishService.PublishResult("ntfevt_test_1", List.of(101L))
        );
        NotificationAnnouncementService service = new NotificationAnnouncementService(userRepository, notificationService);

        service.publishAnnouncement(9L, new AdminNotificationAnnouncementRequest(
                "系统公告",
                "今晚进行维护",
                List.of("STUDENT"),
                null,
                "NORMAL",
                "NOTIFICATION_CENTER",
                null,
                "VIEW_NOTIFICATION_CENTER",
                null
        ));

        ArgumentCaptor<PlatformNotificationPublishService.NotificationPublishCommand> commandCaptor =
                ArgumentCaptor.forClass(PlatformNotificationPublishService.NotificationPublishCommand.class);
        verify(notificationService).publish(commandCaptor.capture());
        assertThat(commandCaptor.getValue().emailRequested()).isFalse();
        assertThat(commandCaptor.getValue().payload())
                .containsEntry("emailRequested", false)
                .containsEntry("deliveryChannels", List.of("IN_APP"));
    }

    @Test
    void shouldPreserveExplicitAdminAnnouncementEmailRequest() {
        UserRepository userRepository = mock(UserRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        when(userRepository.findActiveUserIdsByRoles(any())).thenReturn(List.of(18L));
        when(notificationService.publish(any())).thenReturn(
                new PlatformNotificationPublishService.PublishResult("ntfevt_test_2", List.of(102L))
        );
        NotificationAnnouncementService service = new NotificationAnnouncementService(userRepository, notificationService);

        service.publishAnnouncement(9L, new AdminNotificationAnnouncementRequest(
                "系统公告",
                "今晚进行维护",
                List.of("STUDENT"),
                null,
                "NORMAL",
                "NOTIFICATION_CENTER",
                null,
                "VIEW_NOTIFICATION_CENTER",
                true
        ));

        ArgumentCaptor<PlatformNotificationPublishService.NotificationPublishCommand> commandCaptor =
                ArgumentCaptor.forClass(PlatformNotificationPublishService.NotificationPublishCommand.class);
        verify(notificationService).publish(commandCaptor.capture());
        assertThat(commandCaptor.getValue().emailRequested()).isTrue();
        assertThat(commandCaptor.getValue().payload())
                .containsEntry("emailRequested", true)
                .containsEntry("deliveryChannels", List.of("IN_APP", "EMAIL"));
    }
}
