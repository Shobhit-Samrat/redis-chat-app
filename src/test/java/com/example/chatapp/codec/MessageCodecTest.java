package com.example.chatapp.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.chatapp.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MessageCodecTest {

    private final MessageCodec codec = new MessageCodec(new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));

    @Test
    void roundTripKeepsAllFields() {
        ChatMessage original = new ChatMessage("guest_user", "Hello, everyone!", Instant.parse("2024-01-01T10:00:00Z"));

        String json = codec.toJson(original);

        assertTrue(json.contains("\"timestamp\":\"2024-01-01T10:00:00Z\""));
        assertEquals(original, codec.fromJson(json));
    }

    @Test
    void invalidJsonIsRejected() {
        assertThrows(IllegalStateException.class, () -> codec.fromJson("not-json"));
    }
}
