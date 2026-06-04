package com.bishe.server.support;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * PostgreSQL Testcontainers 统一测试基类。
 */
@Testcontainers
@ActiveProfiles("test-postgres")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractPostgresIntegrationTest {

    static {
        configureDockerApiVersion();
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:16.13");

    private static void configureDockerApiVersion() {
        if (System.getProperty("api.version") != null) {
            return;
        }
        String apiVersion = resolveDockerApiVersion();
        if (apiVersion != null) {
            System.setProperty("api.version", apiVersion);
        }
    }

    private static String resolveDockerApiVersion() {
        Process process = null;
        try {
            process = new ProcessBuilder("docker", "version", "--format", "{{.Server.APIVersion}}")
                    .redirectErrorStream(true)
                    .start();
            byte[] output = process.getInputStream().readAllBytes();
            if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return null;
            }
            String value = new String(output, StandardCharsets.UTF_8).trim();
            return value.matches("\\d+\\.\\d+") ? value : null;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
