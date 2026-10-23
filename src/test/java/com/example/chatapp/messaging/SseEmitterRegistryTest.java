package com.example.chatapp.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.chatapp.config.ChatProperties;
import com.example.chatapp.model.ChatMessage;
import com.example.chatapp.service.RoomDeletedEvent;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SseEmitterRegistryTest {

    private final SseEmitterRegistry registry =
            new SseEmitterRegistry(new ChatProperties(true, 1000, 10, 100, Duration.ofMinutes(1)));

    @Test
    void registersSubscribersPerRoom() {
        registry.register("general");
        registry.register("general");
        registry.register("random");

        assertEquals(2, registry.subscriberCount("general"));
        assertEquals(1, registry.subscriberCount("random"));
    }

    @Test
    void broadcastToRoomWithoutSubscribersIsNoOp() {
        assertDoesNotThrow(() -> registry.broadcast("empty", new ChatMessage("a", "b", Instant.now())));
    }

    @Test
    void broadcastToSubscribedRoomDoesNotFail() {
        registry.register("general");

        assertDoesNotThrow(() -> registry.broadcast("general", new ChatMessage("a", "hi", Instant.now())));
    }

    @Test
    void roomDeletionClosesAllStreamsOfThatRoomOnly() {
        registry.register("general");
        registry.register("random");

        registry.onRoomDeleted(new RoomDeletedEvent("general"));

        assertEquals(0, registry.subscriberCount("general"));
        assertEquals(1, registry.subscriberCount("random"));
    }
}
