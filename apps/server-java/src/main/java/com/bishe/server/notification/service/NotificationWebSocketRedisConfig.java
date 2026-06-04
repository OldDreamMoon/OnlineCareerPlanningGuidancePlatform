package com.bishe.server.notification.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 通知 WS Redis 监听配置。
 */
@Configuration
public class NotificationWebSocketRedisConfig {

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    RedisMessageListenerContainer notificationWebSocketRedisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            NotificationWebSocketRemoteDispatchService remoteDispatchService
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(remoteDispatchService, remoteDispatchService.topic());
        return container;
    }
}
