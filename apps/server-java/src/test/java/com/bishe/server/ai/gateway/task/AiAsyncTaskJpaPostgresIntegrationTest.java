package com.bishe.server.ai.gateway.task;

import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(AiAsyncTaskRepository.class)
class AiAsyncTaskJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long USER_ID = 5311L;
    private static final String TASK_ID_SUCCESS = "pg-ai-async-success";
    private static final String TASK_ID_FAILED = "pg-ai-async-failed";
    private static final String EVENT_ID = "aievt-pg-async-1";

    @Autowired
    private AiAsyncTaskRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM ai_async_task_events WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM ai_async_task_jobs WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", USER_ID);
    }

    @Test
    void repositoryShouldPersistClaimRetryAndSucceedOnPostgres() {
        insertUser(USER_ID, "pg-ai-async-success@example.com");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        long jobId = repository.insertJob(
                TASK_ID_SUCCESS,
                USER_ID,
                "RESUME",
                "RESUME_OPTIMIZE",
                "SYSTEM_RESUME_OPTIMIZE",
                "ASYNC_JOB",
                AiAsyncTaskStatus.PENDING.name(),
                "SYSTEM_RESUME_GEMINI_NATIVE",
                "GEMINI_NATIVE",
                "gemini-2.5-flash",
                "RESUME_OPTIMIZE_CORE",
                1,
                "{\"resumeText\":\"hello\"}",
                "{\"inputMode\":\"text\"}",
                "{\"system\":\"prompt\"}",
                "{\"route\":\"snapshot\"}",
                3,
                Timestamp.from(now)
        );

        assertThat(jobId).isPositive();
        AiAsyncTaskRepository.AsyncTaskJobRow created = repository.findJobById(jobId).orElseThrow();
        assertThat(created.taskId()).isEqualTo(TASK_ID_SUCCESS);
        assertThat(created.status()).isEqualTo(AiAsyncTaskStatus.PENDING.name());
        assertThat(created.currentAttempt()).isZero();
        assertThat(created.maxAttempts()).isEqualTo(3);
        assertThat(repository.findJobByTaskId(TASK_ID_SUCCESS)).isPresent();
        assertThat(repository.findJobByTaskIdAndUserId(TASK_ID_SUCCESS, USER_ID)).isPresent();
        assertThat(repository.findRunnableJobIds(Timestamp.from(now.plusSeconds(5)), 10)).contains(jobId);

        assertThat(repository.claimJob(
                jobId,
                "pg-ai-worker-a",
                Timestamp.from(now.plusSeconds(5)),
                Timestamp.from(now.plusSeconds(65))
        )).isTrue();

        AiAsyncTaskRepository.AsyncTaskJobRow running = repository.findJobById(jobId).orElseThrow();
        assertThat(running.status()).isEqualTo(AiAsyncTaskStatus.RUNNING.name());
        assertThat(running.currentAttempt()).isEqualTo(1);
        assertThat(running.leaseOwner()).isEqualTo("pg-ai-worker-a");
        assertThat(running.leaseExpiresAt()).isEqualTo(now.plusSeconds(65));
        assertThat(running.startedAt()).isEqualTo(now.plusSeconds(5));

        Instant retryAt = now.plusSeconds(120);
        assertThat(repository.markRetryWait(jobId, "AI-RETRY", "postgres retry", Timestamp.from(retryAt))).isTrue();
        AiAsyncTaskRepository.AsyncTaskJobRow retryWait = repository.findJobById(jobId).orElseThrow();
        assertThat(retryWait.status()).isEqualTo(AiAsyncTaskStatus.RETRY_WAIT.name());
        assertThat(retryWait.errorCode()).isEqualTo("AI-RETRY");
        assertThat(retryWait.errorMessage()).isEqualTo("postgres retry");
        assertThat(retryWait.nextRunAt()).isEqualTo(retryAt);
        assertThat(retryWait.leaseOwner()).isNull();

        assertThat(repository.findRunnableJobIds(Timestamp.from(now.plusSeconds(90)), 10)).doesNotContain(jobId);
        assertThat(repository.findRunnableJobIds(Timestamp.from(now.plusSeconds(180)), 10)).contains(jobId);
        assertThat(repository.claimJob(
                jobId,
                "pg-ai-worker-b",
                Timestamp.from(now.plusSeconds(180)),
                Timestamp.from(now.plusSeconds(240))
        )).isTrue();

        assertThat(repository.markSucceeded(
                jobId,
                "done",
                "{\"summary\":\"ok\"}",
                Timestamp.from(now.plusSeconds(181))
        )).isTrue();
        AiAsyncTaskRepository.AsyncTaskJobRow succeeded = repository.findJobById(jobId).orElseThrow();
        assertThat(succeeded.status()).isEqualTo(AiAsyncTaskStatus.SUCCEEDED.name());
        assertThat(succeeded.resultSummary()).isEqualTo("done");
        assertThat(succeeded.resultPayloadJson()).contains("\"summary\"").contains("ok");
        assertThat(succeeded.finishedAt()).isEqualTo(now.plusSeconds(181));
        assertThat(succeeded.leaseOwner()).isNull();
        assertThat(succeeded.currentAttempt()).isEqualTo(2);
    }

    @Test
    void repositoryShouldPersistFailureAndEventOnPostgres() {
        insertUser(USER_ID, "pg-ai-async-failed@example.com");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long jobId = repository.insertJob(
                TASK_ID_FAILED,
                USER_ID,
                "RESUME",
                "RESUME_OPTIMIZE",
                "SYSTEM_RESUME_OPTIMIZE",
                "ASYNC_JOB",
                AiAsyncTaskStatus.PENDING.name(),
                "SYSTEM_RESUME_GEMINI_NATIVE",
                "GEMINI_NATIVE",
                "gemini-2.5-flash",
                "RESUME_OPTIMIZE_CORE",
                1,
                "{\"resumeText\":\"hello\"}",
                "{\"inputMode\":\"text\"}",
                "{\"system\":\"prompt\"}",
                "{\"route\":\"snapshot\"}",
                2,
                Timestamp.from(now)
        );

        assertThat(repository.claimJob(
                jobId,
                "pg-ai-worker-c",
                Timestamp.from(now.plusSeconds(1)),
                Timestamp.from(now.plusSeconds(31))
        )).isTrue();
        assertThat(repository.markFailed(
                jobId,
                "AI-FAILED",
                "postgres failed",
                Timestamp.from(now.plusSeconds(2))
        )).isTrue();

        AiAsyncTaskRepository.AsyncTaskJobRow failed = repository.findJobById(jobId).orElseThrow();
        assertThat(failed.status()).isEqualTo(AiAsyncTaskStatus.FAILED.name());
        assertThat(failed.errorCode()).isEqualTo("AI-FAILED");
        assertThat(failed.errorMessage()).isEqualTo("postgres failed");
        assertThat(failed.finishedAt()).isEqualTo(now.plusSeconds(2));
        assertThat(failed.leaseOwner()).isNull();

        long eventDbId = repository.insertEvent(
                EVENT_ID,
                jobId,
                TASK_ID_FAILED,
                USER_ID,
                AiAsyncTaskEventType.FAILED.name(),
                "PENDING",
                "{\"status\":\"FAILED\"}"
        );
        assertThat(eventDbId).isPositive();

        Long eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_async_task_events WHERE task_id = ?",
                Long.class,
                TASK_ID_FAILED
        );
        assertThat(eventCount).isEqualTo(1L);
        List<String> eventTypes = jdbcTemplate.queryForList(
                "SELECT event_type FROM ai_async_task_events WHERE task_id = ? ORDER BY id ASC",
                String.class,
                TASK_ID_FAILED
        );
        assertThat(eventTypes).containsExactly(AiAsyncTaskEventType.FAILED.name());
    }

    @Test
    void repositoryShouldUseJsonbColumnsAndPurgeOldTerminalRows() {
        insertUser(USER_ID, "pg-ai-async-jsonb@example.com");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        long oldJobId = repository.insertJob(
                "pg-ai-async-old",
                USER_ID,
                "RESUME",
                "RESUME_OPTIMIZE",
                "SYSTEM_RESUME_OPTIMIZE",
                "ASYNC_JOB",
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
                Timestamp.from(now.minus(40, ChronoUnit.DAYS))
        );
        assertThat(repository.markFailed(
                oldJobId,
                "AI-OLD",
                "old terminal job",
                Timestamp.from(now.minus(35, ChronoUnit.DAYS))
        )).isTrue();
        repository.insertEvent(
                "aievt-pg-old",
                oldJobId,
                "pg-ai-async-old",
                USER_ID,
                AiAsyncTaskEventType.FAILED.name(),
                "PENDING",
                "{\"status\":\"FAILED\",\"terminal\":true}"
        );

        long recentJobId = repository.insertJob(
                "pg-ai-async-recent",
                USER_ID,
                "RESUME",
                "RESUME_OPTIMIZE",
                "SYSTEM_RESUME_OPTIMIZE",
                "ASYNC_JOB",
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
                recentJobId,
                "recent done",
                "{\"summary\":\"recent\"}",
                Timestamp.from(now.minus(2, ChronoUnit.DAYS))
        )).isTrue();

        long pendingJobId = repository.insertJob(
                "pg-ai-async-pending",
                USER_ID,
                "RESUME",
                "RESUME_OPTIMIZE",
                "SYSTEM_RESUME_OPTIMIZE",
                "ASYNC_JOB",
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
                Timestamp.from(now.minus(50, ChronoUnit.DAYS))
        );

        assertJsonbColumn("ai_async_task_jobs", "input_snapshot_json");
        assertJsonbColumn("ai_async_task_jobs", "context_json");
        assertJsonbColumn("ai_async_task_jobs", "prompt_snapshot_json");
        assertJsonbColumn("ai_async_task_jobs", "route_snapshot_json");
        assertJsonbColumn("ai_async_task_jobs", "result_payload_json");
        assertJsonbColumn("ai_async_task_events", "payload_json");

        List<Long> candidateIds = repository.findTerminalJobIdsBefore(
                Timestamp.from(now.minus(30, ChronoUnit.DAYS)),
                10
        );
        assertThat(candidateIds).contains(oldJobId);
        assertThat(candidateIds).doesNotContain(recentJobId, pendingJobId);

        assertThat(repository.deleteEventsByJobIds(candidateIds)).isEqualTo(1L);
        assertThat(repository.deleteJobsByIds(candidateIds)).isEqualTo(1L);
        assertThat(repository.findJobById(oldJobId)).isEmpty();
        assertThat(repository.findJobById(recentJobId)).isPresent();
        assertThat(repository.findJobById(pendingJobId)).isPresent();
    }

    private void insertUser(long userId, String email) {
        jdbcTemplate.update(
                """
                INSERT INTO users(
                    id,
                    email,
                    password_hash,
                    role,
                    tier,
                    status,
                    display_name,
                    real_name,
                    is_deleted,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, 'STUDENT', 'FREE', 'ACTIVE', 'AI 异步 PG', 'AI 异步 PG', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                email,
                "$2a$10$seed"
        );
    }

    private void assertJsonbColumn(String tableName, String columnName) {
        String dataType = jdbcTemplate.queryForObject(
                """
                SELECT data_type
                  FROM information_schema.columns
                 WHERE table_name = ?
                   AND column_name = ?
                """,
                String.class,
                tableName,
                columnName
        );
        assertThat(dataType).isEqualTo("jsonb");
    }
}
