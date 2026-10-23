package com.example.chatapp.config;

import com.example.chatapp.constants.RedisKeys;
import com.example.chatapp.messaging.RedisMessageSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Wires Redis Pub/Sub.
 * <p>
 * A single pattern subscription ({@code chat:channel:*}) covers every room - existing and future -
 * so no per-room (un)subscription bookkeeping is needed, and every application instance
 * receives every message (horizontal scalability).
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisMessageSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new PatternTopic(RedisKeys.CHANNEL_PATTERN));
        return container;
    }
}
