package com.example.chatapp.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Externalised, type-safe settings (prefix {@code chat.*}).
 */
@ConfigurationProperties(prefix = "chat")
public record ChatProperties(
        @DefaultValue("true") boolean requireMembership,
        @DefaultValue("1000") int maxMessagesPerRoom,
        @DefaultValue("10") int defaultHistoryLimit,
        @DefaultValue("100") int maxHistoryLimit,
        @DefaultValue("30m") Duration sseTimeout) {
}
