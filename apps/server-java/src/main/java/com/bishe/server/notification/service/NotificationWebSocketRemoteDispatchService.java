package com.bishe.server.notification.service;

import com.bishe.server.common.TimePayloads;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 通知 WS 跨实例 Redis Pub/Sub 转发桥。
 */
@Service
public class NotificationWebSocketRemoteDispatchService implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketRemoteDispatchService.class);
    private static final String CHANNEL_PREFIX = "notify:ws:dispatch:";

    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationWebSocketOnlineRegistryService onlineRegistryService;
    private final NotificationWebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public NotificationWebSocketRemoteDispatchService(
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            NotificationWebSocketOnlineRegistryService onlineRegistryService,
            NotificationWebSocketSessionRegistry sessionRegistry,
            ObjectMapper objectMapper
    ) {
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        this.onlineRegistryService = onlineRegistryService;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    public ChannelTopic topic() {
        return new ChannelTopic(buildChannel(onlineRegistryService.currentInstanceId()));
    }

    public int publishToRemoteInstances(Set<String> remoteInstanceIds, long userId, String payloadJson) {
        if (userId <= 0 || !StringUtils.hasText(payloadJson) || remoteInstanceIds == null || remoteInstanceIds.isEmpty()
                || stringRedisTemplate == null) {
            return 0;
        }
        LinkedHashSet<String> targetInstances = new LinkedHashSet<>();
        String currentInstanceId = onlineRegistryService.currentInstanceId();
        for (String remoteInstanceId : remoteInstanceIds) {
            if (!StringUtils.hasText(remoteInstanceId)) {
                continue;
            }
            String normalizedInstanceId = remoteInstanceId.trim();
            if (!normalizedInstanceId.equals(currentInstanceId)) {
                targetInstances.add(normalizedInstanceId);
            }
        }
        if (targetInstances.isEmpty()) {
            return 0;
        }
        String messageBody = writeEnvelope(userId, payloadJson);
        int deliveredSubscriberCount = 0;
        for (String targetInstance : targetInstances) {
            try {
                Long subscriberCount = stringRedisTemplate.convertAndSend(buildChannel(targetInstance), messageBody);
                if (subscriberCount != null && subscriberCount > 0) {
                    deliveredSubscriberCount += subscriberCount.intValue();
                }
            } catch (Exception ex) {
                log.debug("publish remote notification websocket dispatch failed instance={}, message={}", targetInstance, ex.getMessage());
            }
        }
        return deliveredSubscriberCount;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null || message.getBody().length == 0) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            long userId = root.path("userId").asLong(0L);
            String payloadJson = root.path("payload").asText("");
            if (userId <= 0 || !StringUtils.hasText(payloadJson)) {
                return;
            }
            sessionRegistry.sendToLocalUser(userId, payloadJson);
        } catch (Exception ex) {
            log.debug("consume remote notification websocket dispatch failed: {}", ex.getMessage());
        }
    }

    private String buildChannel(String instanceId) {
        return CHANNEL_PREFIX + instanceId;
    }

    private String writeEnvelope(long userId, String payloadJson) {
        try {
            return objectMapper.writeValueAsString(new RemoteDispatchEnvelope(
                    userId,
                    payloadJson,
                    currentForwardedAt()
            ));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize remote notification websocket dispatch", ex);
        }
    }

    protected Long currentForwardedAt() {
        return TimePayloads.toEpochMillis(Instant.now());
    }

    private record RemoteDispatchEnvelope(
            long userId,
            String payload,
            Long forwardedAt
    ) {
    }
}
