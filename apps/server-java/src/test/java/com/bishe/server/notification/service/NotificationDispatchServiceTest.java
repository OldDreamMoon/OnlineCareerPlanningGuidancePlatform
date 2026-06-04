package com.bishe.server.notification.service;

import com.bishe.server.notification.model.NotificationChannel;
import com.bishe.server.notification.model.NotificationDispatchStatus;
import com.bishe.server.notification.repository.NotificationDispatchJobRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDispatchServiceTest {

    @Test
    void shouldPreferRedisClaimedJobsBeforeDatabaseFallback() {
        NotificationDispatchJobRepository repository = mock(NotificationDispatchJobRepository.class);
        NotificationDispatchCoordinationService coordinationService = mock(NotificationDispatchCoordinationService.class);
        when(coordinationService.claimDueJobIds(eq("notify-worker-a"), eq(2), any()))
                .thenReturn(List.of(11L, 13L));
        when(repository.claimJob(eq(11L), eq("notify-worker-a"), any(), any())).thenReturn(true);
        when(repository.claimJob(eq(13L), eq("notify-worker-a"), any(), any())).thenReturn(true);
        when(repository.findById(11L)).thenReturn(Optional.of(row(11L, "ntfjob_11", NotificationDispatchStatus.RUNNING)));
        when(repository.findById(13L)).thenReturn(Optional.of(row(13L, "ntfjob_13", NotificationDispatchStatus.RUNNING)));

        NotificationDispatchService service = new NotificationDispatchService(repository, coordinationService);

        List<NotificationDispatchService.DispatchJobSnapshot> jobs = service.claimRunnableJobs("notify-worker-a", 2, java.time.Duration.ofSeconds(30));

        assertThat(jobs).hasSize(2);
        assertThat(jobs.getFirst().id()).isEqualTo(11L);
        verify(repository, never()).findRunnableJobIds(any(), any(Integer.class));
    }

    @Test
    void shouldFallbackToDatabasePollingWhenRedisQueueEmpty() {
        NotificationDispatchJobRepository repository = mock(NotificationDispatchJobRepository.class);
        NotificationDispatchCoordinationService coordinationService = mock(NotificationDispatchCoordinationService.class);
        when(coordinationService.claimDueJobIds(eq("notify-worker-b"), eq(1), any()))
                .thenReturn(List.of());
        when(repository.findRunnableJobIds(any(), eq(1))).thenReturn(List.of(12L));
        when(repository.claimJob(eq(12L), eq("notify-worker-b"), any(), any())).thenReturn(true);
        when(repository.findById(12L)).thenReturn(Optional.of(row(12L, "ntfjob_12", NotificationDispatchStatus.RUNNING)));

        NotificationDispatchService service = new NotificationDispatchService(repository, coordinationService);

        List<NotificationDispatchService.DispatchJobSnapshot> jobs = service.claimRunnableJobs("notify-worker-b", 1, java.time.Duration.ofSeconds(30));

        assertThat(jobs).hasSize(1);
        assertThat(jobs.getFirst().id()).isEqualTo(12L);
        verify(repository).findRunnableJobIds(any(), eq(1));
    }

    @Test
    void shouldIgnoreDuplicateAckWhenAckGuardAlreadyExists() {
        NotificationDispatchJobRepository repository = mock(NotificationDispatchJobRepository.class);
        NotificationDispatchCoordinationService coordinationService = mock(NotificationDispatchCoordinationService.class);
        when(coordinationService.tryAcquireAck(8L, "ntfjob_18")).thenReturn(false);

        NotificationDispatchService service = new NotificationDispatchService(repository, coordinationService);

        service.acknowledgeWebSocketJob("ntfjob_18", 8L);

        verify(repository, never()).markAcked(any(), any(Long.class), any());
    }

    @Test
    void shouldPurgeTerminalJobsBeforeInBatches() {
        NotificationDispatchJobRepository repository = mock(NotificationDispatchJobRepository.class);
        NotificationDispatchCoordinationService coordinationService = mock(NotificationDispatchCoordinationService.class);
        Instant purgeBefore = Instant.now().minusSeconds(3600);
        when(repository.findTerminalJobIdsBefore(any(), eq(2))).thenReturn(List.of(21L, 22L));
        when(repository.deleteAttemptsByJobIds(anyList())).thenReturn(2L);
        when(repository.deleteJobsByIds(anyList())).thenReturn(2L);

        NotificationDispatchService service = new NotificationDispatchService(repository, coordinationService);

        NotificationDispatchService.DispatchRetentionPurgeResult result = service.purgeTerminalJobsBefore(purgeBefore, 2);

        assertThat(result.deletedJobCount()).isEqualTo(2L);
        assertThat(result.deletedAttemptCount()).isEqualTo(2L);
        assertThat(result.purgeBefore()).isEqualTo(purgeBefore);
        verify(repository).deleteAttemptsByJobIds(List.of(21L, 22L));
        verify(repository).deleteJobsByIds(List.of(21L, 22L));
        verify(coordinationService).clearAfterCommit(21L);
        verify(coordinationService).clearAfterCommit(22L);
    }

    private NotificationDispatchJobRepository.DispatchJobRow row(long id, String jobId, NotificationDispatchStatus status) {
        Instant now = Instant.now();
        return new NotificationDispatchJobRepository.DispatchJobRow(
                id,
                jobId,
                101L,
                "ntfevt-test",
                8L,
                NotificationChannel.WEBSOCKET,
                status,
                1,
                3,
                now,
                "notify-worker-a",
                now.plusSeconds(30),
                null,
                null,
                null,
                null,
                null,
                "{\"type\":\"NOTIFICATION_CREATED\"}",
                now,
                now
        );
    }
}
