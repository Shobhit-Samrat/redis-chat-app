package com.example.chatapp.model;

import java.time.Instant;

/**
 * A chat message. Serialised as JSON both in the Redis List and on the Pub/Sub channel:
 * {@code {"participant":"guest_user","message":"Hello","timestamp":"2024-01-01T10:00:00Z"}}
 */
public record ChatMessage(String participant, String message, Instant timestamp) {
}
