package com.bishe.server.ai.gateway.task;

import com.bishe.server.ai.gateway.AiExecutionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:bishe_ai_async_tasks;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "ai.gateway.async-task.poll-interval-ms=600000"
})
@ActiveProfiles("test")
@Transactional
class AiAsyncTaskServiceTest {

    @Autowired
    private AiAsyncTaskService taskService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiAsyncTaskRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM ai_async_task_events");
        jdbcTemplate.update("DELETE FROM ai_async_task_jobs");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void submitTask_shouldPersistPendingTaskAndSubmittedEvent() {
        long userId = insertUser("async-submit@example.com");

        AiAsyncTaskService.TaskTicket ticket = taskService.submitTask(new AiAsyncTaskService.SubmitTaskCommand(
                userId,
                "RESUME",
                "RESUME_OPTIMIZE",
                "SYSTEM_RESUME_OPTIMIZE",
                AiExecutionMode.ASYNC_JOB.name(),
                "SYSTEM_RESUME_GEMINI_NATIVE",
                "GEMINI_NATIVE",
                "gemini-2.5-flash",
                "RESUME_OPTIMIZE_CORE",
                1,
                "{\"resumeText\":\"hello\"}",
                "{\"inputMode\":\"text\"}",
                "{\"system\":\"prompt\"}",
                "{\"route\":\"snapshot\"}",
                3
        ));

        Optional<AiAsyncTaskService.TaskSnapshot> snapshotOptional = taskService.findTask(ticket.taskId());
        assertThat(snapshotOptional).isPresent();
        AiAsyncTaskService.TaskSnapshot snapshot = snapshotOptional.orElseThrow();
        assertThat(snapshot.status()).isEqualTo(AiAsyncTaskStatus.PENDING);
        assertThat(snapshot.executionMode()).isEqualTo(AiExecutionMode.ASYNC_JOB);
        assertThat(snapshot.taskType()).isEqualTo("RESUME");
        assertThat(snapshot.sceneCode()).isEqualTo("RESUME_OPTIMIZE");

        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_async_task_events WHERE task_id = ?",
                Integer.class,
                ticket.taskId()
        );
        assertThat(eventCount).isEqualTo(1);
    }

    @Test
    void claimAndComplete_shouldAdvanceTaskToSucceeded() {
        long userId = insertUser("async-complete@example.com");
        AiAsyncTaskService.TaskTicket ticket = taskService.submitTask(new AiAsyncTaskService.SubmitTaskCommand(
                userId,
                "RESUME",
                "RESUME_OPTIMIZE",
                "SYSTEM_RESUME_OPTIMIZE",
                AiExecutionMode.ASYNC_JOB.name(),
                "SYSTEM_RESUME_GEMINI_NATIVE",
                "GEMINI_NATIVE",
                "gemini-2.5-flash",
                "RESUME_OPTIMIZE_CORE",
                1,
                "{\"resumeText\":\"hello\"}",
                "{\"inputMode\":\"text\"}",
                "{\"system\":\"prompt\"}",
                "{\"route\":\"snapshot\"}",
                2
        ));

        var claimed = taskService.claimRunnableTasks("worker-test", 5, Duration.ofSeconds(30));
        assertThat(claimed).hasSize(1);
        assertThat(claimed.getFirst().taskId()).isEqualTo(ticket.taskId());
        assertThat(claimed.getFirst().status()).isEqualTo(AiAsyncTaskStatus.RUNNING);
        assertThat(claimed.getFirst().currentAttempt()).isEqualTo(1);

        taskService.markSucceeded(claimed.getFirst().id(), "done", "{\"summary\":\"ok\"}");

        AiAsyncTaskService.TaskSnapshot snapshot = taskService.findTask(ticket.taskId()).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(AiAsyncTaskStatus.SUCCEEDED);
        assertThat(snapshot.resultSummary()).isEqualTo("done");
        assertThat(snapshot.resultPayloadJson()).contains("\"summary\"").contains("ok");

        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_async_task_events WHERE task_id = ?",
                Integer.class,
                ticket.taskId()
        );
        assertThat(eventCount).isEqualTo(3);
    }

    @Test
    void purgeTerminalTasks_shouldDeleteOnlyExpiredTerminalRows() {
        long userId = insertUser("async-retention@example.com");
        Instant now = Instant.now();

        long oldFailedJobId = repository.insertJob(
                "async-retention-old-failed",
                userId,
                "RESUME",
                "RESUME_OPTIMIZE",
                "SYSTEM_RESUME_OPTIMIZE",
                AiExecutionMode.ASYNC_JOB.name(),
                AiAsyncTaskStatus.PENDING.name(),
                "SYSTEM_RESUME_GEMINI_NATIVE",
                "GEMINI_NATIVE",
                "gemini-2.5-flash",
                "RESUME_OPTIMIZE_CORE",
                1,
                "{\"resumeText\":\"old\"}",
                "{\"inputMode\":\"text\"}",
                "{\"system\":\"prompt\"}",
                "{\"route\":\"snapshot\"}",
                2,
                Timestamp.from(now.minus(Duration.ofDays(40)))
        );
        assertThat(repository.markFailed(
                oldFailedJobId,
                "AI-OLD",
                "expired terminal task",
                Timestamp.from(now.minus(Duration.ofDays(35)))
        )).isTrue();
        repository.insertEvent(
                "aievt-retention-old",
                oldFailedJobId,
                "async-retention-old-failed",
                userId,
                AiAsyncTaskEventType.FAILED.name(),
                "PENDING",
                "{\"status\":\"FAILED\"}"
        );

        long recentSucceededJobId = repository.insertJob(
                "async-retention-recent-success",
                userId,
                "RESUME",
                "RESUME_OPTIMIZE",
                "SYSTEM_RESUME_OPTIMIZE",
                AiExecutionMode.ASYNC_JOB.name(),
                AiAsyncTaskStatus.PENDING.name(),
                "SYSTEM_RESUME_GEMINI_NATIVE",
                "GEMINI_NATIVE",
                "gemini-2.5-flash",
                "RESUME_OPTIMIZE_CORE",
                1,
                "{\"resumeText\":\"recent\"}",
                "{\"inputMode\":\"text\"}",
                "{\"system\":\"prompt\"}",
                "{\"route\":\"snapshot\"}",
                2,
                Timestamp.from(now)
        );
        assertThat(repository.markSucceeded(
                recentSucceededJobId,
                "recent done",
                "{\"summary\":\"ok\"}",
                Timestamp.from(now.minus(Duration.ofDays(2)))
        )).isTrue();

        long oldPendingJobId = repository.insertJob(
                "async-retention-old-pending",
                userId,
                "RESUME",
                "RESUME_OPTIMIZE",
                "SYSTEM_RESUME_OPTIMIZE",
                AiExecutionMode.ASYNC_JOB.name(),
                AiAsyncTaskStatus.PENDING.name(),
                "SYSTEM_RESUME_GEMINI_NATIVE",
                "GEMINI_NATIVE",
                "gemini-2.5-flash",
                "RESUME_OPTIMIZE_CORE",
                1,
                "{\"resumeText\":\"pending\"}",
                "{\"inputMode\":\"text\"}",
                "{\"system\":\"prompt\"}",
                "{\"route\":\"snapshot\"}",
                2,
                Timestamp.from(now.minus(Duration.ofDays(50)))
        );

        AiAsyncTaskService.TaskRetentionPurgeResult result = taskService.purgeTerminalTasks(Duration.ofDays(30), 20);

        assertThat(result.deletedJobCount()).isEqualTo(1L);
        assertThat(result.deletedEventCount()).isEqualTo(1L);
        assertThat(taskService.findTask("async-retention-old-failed")).isEmpty();
        assertThat(taskService.findTask("async-retention-recent-success")).isPresent();
        assertThat(taskService.findTask("async-retention-old-pending")).isPresent();
        Integer oldEventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_async_task_events WHERE task_id = ?",
                Integer.class,
                "async-retention-old-failed"
        );
        assertThat(oldEventCount).isEqualTo(0);
        assertThat(repository.findJobById(oldPendingJobId)).isPresent();
    }

    private long insertUser(String email) {
        jdbcTemplate.update(
                """
                INSERT INTO users(email, password_hash, role, tier, status, display_name, is_deleted, created_at, updated_at)
                VALUES (?, 'hash', 'STUDENT', 'FREE', 'ACTIVE', '测试同学', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                email
        );
        Long id = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        return id == null ? 0L : id;
    }
}
