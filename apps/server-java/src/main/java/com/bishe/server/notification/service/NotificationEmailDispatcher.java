package com.bishe.server.notification.service;

import com.bishe.server.auth.model.AppUser;
import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.notification.NotificationProperties;
import com.bishe.server.notification.model.NotificationChannel;
import com.bishe.server.notification.model.NotificationDispatchStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 事务型通知邮件投递器。
 */
@Component
public class NotificationEmailDispatcher implements NotificationChannelDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationEmailDispatcher.class);
    private static final String BRAND_NAME = "大学生职业规划平台";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NotificationProperties notificationProperties;
    private final UserRepository userRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public NotificationEmailDispatcher(
            NotificationProperties notificationProperties,
            UserRepository userRepository,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.notificationProperties = notificationProperties;
        this.userRepository = userRepository;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public DispatchResult dispatch(NotificationDispatchService.DispatchJobSnapshot job) {
        Instant startedAt = Instant.now();
        if (!notificationProperties.getEmail().isReady()) {
            return new DispatchResult(
                    NotificationDispatchStatus.SKIPPED,
                    job.attemptCount(),
                    false,
                    null,
                    job.payloadJson(),
                    "{\"reason\":\"email provider not configured\"}",
                    "NOTIFY-EMAIL-000",
                    "email provider not configured",
                    Duration.between(startedAt, Instant.now()).toMillis()
            );
        }

        AppUser user = userRepository.findById(job.userId()).orElse(null);
        if (user == null || user.email() == null || user.email().isBlank()) {
            return new DispatchResult(
                    NotificationDispatchStatus.SKIPPED,
                    job.attemptCount(),
                    false,
                    null,
                    job.payloadJson(),
                    "{\"reason\":\"recipient email missing\"}",
                    "NOTIFY-EMAIL-001",
                    "recipient email missing",
                    Duration.between(startedAt, Instant.now()).toMillis()
            );
        }

        Map<String, Object> payload = readPayload(job.payloadJson());
        String subject = stringValue(payload.get("subject"), "平台通知");
        String title = stringValue(payload.get("title"), subject);
        String content = stringValue(payload.get("content"), "平台有新的事务进展，请登录查看。");
        String actionCode = stringValue(payload.get("actionCode"), "VIEW_NOTIFICATION_CENTER");
        String refId = stringValue(payload.get("refId"), null);
        String appBaseUrl = stringValue(payload.get("appBaseUrl"), null);
        String actionUrl = appBaseUrl == null || appBaseUrl.isBlank() ? null : appBaseUrl.replaceAll("/$", "") + "/notifications";

        ResendEmailRequest requestBody = new ResendEmailRequest(
                buildFromAddress(),
                List.of(user.email()),
                "【" + BRAND_NAME + "】" + subject,
                buildHtmlContent(title, content, actionCode, refId, actionUrl),
                buildTextContent(title, content, actionCode, refId, actionUrl)
        );

        try {
            ResendHttpResponse response = webClientBuilder.build()
                    .post()
                    .uri(notificationProperties.getEmail().getResendApiUrl().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + notificationProperties.getEmail().getResendApiKey().trim())
                    .bodyValue(requestBody)
                    .exchangeToMono(clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(body -> new ResendHttpResponse(clientResponse.statusCode(), body)))
                    .timeout(Duration.ofSeconds(10))
                    .block();

            long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
            if (response == null) {
                return retryableResult(job, latencyMs, "NOTIFY-EMAIL-002", "email provider returned null response");
            }
            if (response.statusCode().is2xxSuccessful()) {
                return new DispatchResult(
                        NotificationDispatchStatus.SENT,
                        job.attemptCount(),
                        false,
                        null,
                        job.payloadJson(),
                        response.body(),
                        null,
                        null,
                        latencyMs
                );
            }
            if (response.statusCode().is5xxServerError()) {
                return retryableResult(job, latencyMs, "NOTIFY-EMAIL-503", "email provider temporarily unavailable");
            }
            return new DispatchResult(
                    NotificationDispatchStatus.DEAD,
                    job.attemptCount(),
                    false,
                    null,
                    job.payloadJson(),
                    response.body(),
                    "NOTIFY-EMAIL-400",
                    "email provider rejected request",
                    latencyMs
            );
        } catch (Exception ex) {
            log.warn("notification email dispatch transport error jobId={}, message={}", job.jobId(), ex.getMessage());
            long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
            return retryableResult(job, latencyMs, "NOTIFY-EMAIL-500", summarize(ex));
        }
    }

    private DispatchResult retryableResult(NotificationDispatchService.DispatchJobSnapshot job, long latencyMs, String errorCode, String errorMessage) {
        return new DispatchResult(
                NotificationDispatchStatus.FAILED,
                job.attemptCount(),
                true,
                Duration.ofSeconds(15),
                job.payloadJson(),
                "{\"error\":\"" + errorMessage.replace("\"", "'") + "\"}",
                errorCode,
                errorMessage,
                latencyMs
        );
    }

    private Map<String, Object> readPayload(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to parse email payload", ex);
        }
    }

    private String stringValue(Object rawValue, String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = String.valueOf(rawValue);
        return normalized.isBlank() ? defaultValue : normalized;
    }

    private String buildFromAddress() {
        String configured = notificationProperties.getEmail().getResendFromEmail().trim();
        if (configured.contains("<") && configured.contains(">")) {
            return configured;
        }
        return BRAND_NAME + " <" + configured + ">";
    }

    private String buildHtmlContent(String title, String content, String actionCode, String refId, String actionUrl) {
        StringBuilder builder = new StringBuilder();
        builder.append("<div style=\"font-family:Arial,'PingFang SC','Microsoft YaHei',sans-serif;color:#111827;line-height:1.7;\">")
                .append("<h2 style=\"margin:0 0 12px;\">").append(escapeHtml(title)).append("</h2>")
                .append("<p style=\"margin:0 0 16px;\">").append(escapeHtml(content)).append("</p>")
                .append("<p style=\"margin:0 0 8px;color:#6b7280;font-size:13px;\">动作：").append(escapeHtml(actionCode)).append("</p>");
        if (refId != null && !refId.isBlank()) {
            builder.append("<p style=\"margin:0 0 16px;color:#6b7280;font-size:13px;\">关联编号：").append(escapeHtml(refId)).append("</p>");
        }
        if (actionUrl != null && !actionUrl.isBlank()) {
            builder.append("<p style=\"margin:16px 0 0;\"><a href=\"").append(escapeHtml(actionUrl))
                    .append("\" style=\"display:inline-block;padding:10px 16px;background:#0f766e;color:#ffffff;text-decoration:none;border-radius:999px;\">打开通知中心</a></p>");
        }
        builder.append("</div>");
        return builder.toString();
    }

    private String buildTextContent(String title, String content, String actionCode, String refId, String actionUrl) {
        StringBuilder builder = new StringBuilder();
        builder.append(title).append('\n').append(content).append('\n')
                .append("动作：").append(actionCode);
        if (refId != null && !refId.isBlank()) {
            builder.append('\n').append("关联编号：").append(refId);
        }
        if (actionUrl != null && !actionUrl.isBlank()) {
            builder.append('\n').append("通知中心：").append(actionUrl);
        }
        return builder.toString();
    }

    private String summarize(Exception ex) {
        if (ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "email dispatch failed";
        }
        String normalized = ex.getMessage().replaceAll("\\s+", " ").trim();
        return normalized.length() <= 280 ? normalized : normalized.substring(0, 280) + "...";
    }

    private String escapeHtml(String text) {
        return text == null ? "" : text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record ResendEmailRequest(
            String from,
            List<String> to,
            String subject,
            String html,
            String text
    ) {
    }

    private record ResendHttpResponse(
            HttpStatusCode statusCode,
            String body
    ) {
    }
}
