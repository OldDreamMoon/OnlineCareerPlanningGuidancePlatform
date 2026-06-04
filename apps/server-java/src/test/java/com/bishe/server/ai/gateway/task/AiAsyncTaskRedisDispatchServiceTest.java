package com.bishe.server.ai.gateway.task;

import com.bishe.server.ai.gateway.AiExecutionMode;
import com.bishe.server.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAsyncTaskRedisDispatchServiceTest {

    @Test
    void shouldPreferRedisClaimedTasksBeforeDatabaseFallback() {
        AiAsyncTaskRepository repository = mock(AiAsyncTaskRepository.class);
        AiAsyncTaskCoordinationService coordinationService = mock(AiAsyncTaskCoordinationService.class);
        when(coordinationService.claimDueJobIds(eq("ai-worker-a"), eq(2), any()))
                .thenReturn(List.of(21L, 22L));
        when(repository.claimJob(eq(21L), eq("ai-worker-a"), any(), any())).thenReturn(true);
        when(repository.claimJob(eq(22L), eq("ai-worker-a"), any(), any())).thenReturn(true);
        when(repository.findJobById(21L)).thenReturn(Optional.of(row(21L, "aitk_21", "RUNNING")));
        when(repository.findJobById(22L)).thenReturn(Optional.of(row(22L, "aitk_22", "RUNNING")));

        AiAsyncTaskService service = new AiAsyncTaskService(
                repository,
                coordinationService,
                new ObjectMapper(),
                mock(NotificationService.class)
        );

        List<AiAsyncTaskService.TaskSnapshot> tasks =
                service.claimRunnableTasks("ai-worker-a", 2, Duration.ofSeconds(30));

        assertThat(tasks).hasSize(2);
        assertThat(tasks.getFirst().taskId()).isEqualTo("aitk_21");
        verify(repository, never()).findRunnableJobIds(any(), any(Integer.class));
    }

    @Test
    void shouldFallbackToDatabasePollingWhenRedisQueueEmpty() {
        AiAsyncTaskRepository repository = mock(AiAsyncTaskRepository.class);
        AiAsyncTaskCoordinationService coordinationService = mock(AiAsyncTaskCoordinationService.class);
        when(coordinationService.claimDueJobIds(eq("ai-worker-b"), eq(1), any()))
                .thenReturn(List.of());
        when(repository.findRunnableJobIds(any(), eq(1))).thenReturn(List.of(23L));
        when(repository.claimJob(eq(23L), eq("ai-worker-b"), any(), any())).thenReturn(true);
        when(repository.findJobById(23L)).thenReturn(Optional.of(row(23L, "aitk_23", "RUNNING")));

        AiAsyncTaskService service = new AiAsyncTaskService(
                repository,
                coordinationService,
                new ObjectMapper(),
                mock(NotificationService.class)
        );

        List<AiAsyncTaskService.TaskSnapshot> tasks =
                service.claimRunnableTasks("ai-worker-b", 1, Duration.ofSeconds(30));

        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().taskId()).isEqualTo("aitk_23");
        verify(repository).findRunnableJobIds(any(), eq(1));
    }

    private AiAsyncTaskRepository.AsyncTaskJobRow row(long id, String taskId, String status) {
        Instant now = Instant.now();
        return new AiAsyncTaskRepository.AsyncTaskJobRow(
                id,
                taskId,
                7L,
                "RESUME",
                "RESUME_OPTIMIZE",
                "SYSTEM_RESUME_OPTIMIZE",
                AiExecutionMode.ASYNC_JOB.name(),
                status,
                "SYSTEM_RESUME_GEMINI_NATIVE",
                "GEMINI_NATIVE",
                "gemini-2.5-flash",
                "RESUME_OPTIMIZE_CORE",
                1,
                "{\"resumeText\":\"hello\"}",
                "{\"inputMode\":\"text\"}",
                "{\"system\":\"prompt\"}",
                "{\"route\":\"snapshot\"}",
                null,
                null,
                null,
                null,
                1,
                3,
                now,
                "ai-worker-a",
                now.plusSeconds(30),
                now,
                now,
                null,
                now,
                now
        );
    }
}
