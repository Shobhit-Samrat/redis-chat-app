package com.example.chatapp.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.chatapp.codec.MessageCodec;
import com.example.chatapp.constants.RedisKeys;
import com.example.chatapp.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;

class RedisMessageSubscriberTest {

    private final MessageBroadcaster broadcaster = mock(MessageBroadcaster.class);
    private final MessageCodec codec = new MessageCodec(new ObjectMapper().registerModule(new JavaTimeModule()));
    private final RedisMessageSubscriber subscriber = new RedisMessageSubscriber(codec, broadcaster);

    private static DefaultMessage message(String channel, String body) {
        return new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void forwardsPublishedMessageToBroadcaster() {
        String json = "{\"participant\":\"guest_user\",\"message\":\"Hello, everyone!\",\"timestamp\":\"2024-01-01T10:00:00Z\"}";

        subscriber.onMessage(message(RedisKeys.channel("general"), json), null);

        verify(broadcaster).broadcast("general",
                new ChatMessage("guest_user", "Hello, everyone!", Instant.parse("2024-01-01T10:00:00Z")));
    }

    @Test
    void malformedPayloadIsDroppedWithoutThrowing() {
        assertDoesNotThrow(() -> subscriber.onMessage(message(RedisKeys.channel("general"), "not-json"), null));

        verifyNoInteractions(broadcaster);
    }

    @Test
    void foreignChannelIsDroppedWithoutThrowing() {
        assertDoesNotThrow(() -> subscriber.onMessage(message("other:general", "{}"), null));

        verifyNoInteractions(broadcaster);
    }
}
