package com.example.chatapp.constants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RedisKeysTest {

    @Test
    void buildsExpectedKeys() {
        assertEquals("chat:room:general", RedisKeys.roomMeta("general"));
        assertEquals("chat:room:general:participants", RedisKeys.participants("general"));
        assertEquals("chat:room:general:messages", RedisKeys.messages("general"));
        assertEquals("chat:channel:general", RedisKeys.channel("general"));
    }

    @Test
    void extractsRoomIdFromChannel() {
        assertEquals("general", RedisKeys.roomIdFromChannel("chat:channel:general"));
    }

    @Test
    void rejectsForeignChannel() {
        assertThrows(IllegalArgumentException.class, () -> RedisKeys.roomIdFromChannel("other:general"));
    }
}
