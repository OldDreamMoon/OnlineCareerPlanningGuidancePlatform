package com.bishe.server.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 通知 WS 在线索引：在 Redis 中维护用户在线索引、session 映射与实例索引。
 */
@Service
public class NotificationWebSocketOnlineRegistryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketOnlineRegistryService.class);
    private static final String USER_KEY_PREFIX = "notify:ws:online:user:";
    private static final String SESSION_KEY_PREFIX = "notify:ws:online:session:";
    private static final String INSTANCE_KEY_PREFIX = "notify:ws:online:instance:";
    private static final Duration SESSION_TTL = Duration.ofSeconds(90);
    private static final Duration INDEX_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate stringRedisTemplate;
    private final String instanceId;

    @Autowired
    public NotificationWebSocketOnlineRegistryService(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this(stringRedisTemplateProvider.getIfAvailable(), "notifyws-" + UUID.randomUUID().toString().substring(0, 8));
    }

    NotificationWebSocketOnlineRegistryService(StringRedisTemplate stringRedisTemplate, String instanceId) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.instanceId = StringUtils.hasText(instanceId) ? instanceId.trim() : "notifyws-local";
    }

    public String currentInstanceId() {
        return instanceId;
    }

    public void refreshSessionPresence(long userId, String sessionId) {
        if (userId <= 0 || !StringUtils.hasText(sessionId) || stringRedisTemplate == null) {
            return;
        }
        String sessionRef = buildSessionRef(sessionId);
        String sessionKey = buildSessionKey(sessionRef);
        String userKey = buildUserKey(userId);
        String instanceKey = buildInstanceKey();
        try {
            ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();
            SetOperations<String, String> setOperations = stringRedisTemplate.opsForSet();
            valueOperations.set(sessionKey, Long.toString(userId), SESSION_TTL);
            setOperations.add(userKey, sessionRef);
            stringRedisTemplate.expire(userKey, INDEX_TTL);
            setOperations.add(instanceKey, sessionRef);
            stringRedisTemplate.expire(instanceKey, INDEX_TTL);
        } catch (Exception ex) {
            log.debug("refresh notification websocket session presence failed: {}", ex.getMessage());
        }
    }

    public void removeSessionPresence(long userId, String sessionId) {
        if (userId <= 0 || !StringUtils.hasText(sessionId) || stringRedisTemplate == null) {
            return;
        }
        String sessionRef = buildSessionRef(sessionId);
        String sessionKey = buildSessionKey(sessionRef);
        try {
            SetOperations<String, String> setOperations = stringRedisTemplate.opsForSet();
            stringRedisTemplate.delete(sessionKey);
            setOperations.remove(buildUserKey(userId), sessionRef);
            setOperations.remove(buildInstanceKey(), sessionRef);
        } catch (Exception ex) {
            log.debug("remove notification websocket session presence failed: {}", ex.getMessage());
        }
    }

    public int countOnlineSessions(long userId) {
        if (userId <= 0 || stringRedisTemplate == null) {
            return 0;
        }
        String userKey = buildUserKey(userId);
        try {
            SetOperations<String, String> setOperations = stringRedisTemplate.opsForSet();
            Set<String> sessionRefs = setOperations.members(userKey);
            if (sessionRefs == null || sessionRefs.isEmpty()) {
                return 0;
            }
            int activeCount = 0;
            Set<String> staleRefs = new LinkedHashSet<>();
            for (String sessionRef : sessionRefs) {
                if (!StringUtils.hasText(sessionRef)) {
                    continue;
                }
                if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(buildSessionKey(sessionRef)))) {
                    activeCount++;
                } else {
                    staleRefs.add(sessionRef);
                }
            }
            if (!staleRefs.isEmpty()) {
                setOperations.remove(userKey, staleRefs.toArray());
            }
            return activeCount;
        } catch (Exception ex) {
            log.debug("count notification websocket online sessions failed: {}", ex.getMessage());
            return 0;
        }
    }

    public Set<String> findRemoteInstanceIds(long userId) {
        if (userId <= 0 || stringRedisTemplate == null) {
            return Set.of();
        }
        String userKey = buildUserKey(userId);
        try {
            SetOperations<String, String> setOperations = stringRedisTemplate.opsForSet();
            Set<String> sessionRefs = setOperations.members(userKey);
            if (sessionRefs == null || sessionRefs.isEmpty()) {
                return Set.of();
            }
            LinkedHashSet<String> remoteInstanceIds = new LinkedHashSet<>();
            LinkedHashSet<String> staleRefs = new LinkedHashSet<>();
            for (String sessionRef : sessionRefs) {
                if (!StringUtils.hasText(sessionRef)) {
                    continue;
                }
                if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(buildSessionKey(sessionRef)))) {
                    staleRefs.add(sessionRef);
                    continue;
                }
                String ownerInstanceId = parseInstanceId(sessionRef);
                if (StringUtils.hasText(ownerInstanceId) && !instanceId.equals(ownerInstanceId)) {
                    remoteInstanceIds.add(ownerInstanceId);
                }
            }
            if (!staleRefs.isEmpty()) {
                setOperations.remove(userKey, staleRefs.toArray());
            }
            return remoteInstanceIds.isEmpty() ? Set.of() : Set.copyOf(remoteInstanceIds);
        } catch (Exception ex) {
            log.debug("resolve notification websocket remote instance ids failed: {}", ex.getMessage());
            return Set.of();
        }
    }

    public void evictLocalInstancePresenceNow() {
        if (stringRedisTemplate == null) {
            return;
        }
        String instanceKey = buildInstanceKey();
        try {
            SetOperations<String, String> setOperations = stringRedisTemplate.opsForSet();
            ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();
            Set<String> sessionRefs = setOperations.members(instanceKey);
            if (sessionRefs != null) {
                for (String sessionRef : sessionRefs) {
                    if (!StringUtils.hasText(sessionRef)) {
                        continue;
                    }
                    String sessionKey = buildSessionKey(sessionRef);
                    String userIdValue = valueOperations.get(sessionKey);
                    if (StringUtils.hasText(userIdValue)) {
                        try {
                            long userId = Long.parseLong(userIdValue);
                            setOperations.remove(buildUserKey(userId), sessionRef);
                        } catch (NumberFormatException ignored) {
                            // ignore broken registry value and continue cleanup
                        }
                    }
                    stringRedisTemplate.delete(sessionKey);
                }
            }
            stringRedisTemplate.delete(instanceKey);
        } catch (Exception ex) {
            log.debug("evict local notification websocket presence failed: {}", ex.getMessage());
        }
    }

    private String buildSessionRef(String sessionId) {
        return instanceId + ":" + sessionId.trim();
    }

    private String parseInstanceId(String sessionRef) {
        if (!StringUtils.hasText(sessionRef)) {
            return null;
        }
        int delimiterIndex = sessionRef.indexOf(':');
        if (delimiterIndex <= 0) {
            return null;
        }
        return sessionRef.substring(0, delimiterIndex);
    }

    private String buildUserKey(long userId) {
        return USER_KEY_PREFIX + userId;
    }

    private String buildSessionKey(String sessionRef) {
        return SESSION_KEY_PREFIX + sessionRef;
    }

    private String buildInstanceKey() {
        return INSTANCE_KEY_PREFIX + instanceId;
    }
}
