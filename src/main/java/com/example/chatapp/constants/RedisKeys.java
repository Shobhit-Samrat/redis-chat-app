package com.example.chatapp.constants;

/**
 * Single place that defines the Redis key / channel naming scheme.
 *
 * <pre>
 * chat:rooms                    SET   index of all room ids (atomic uniqueness check via SADD)
 * chat:room:{id}                HASH  room metadata (roomId, createdAt)
 * chat:room:{id}:participants   SET   participants who joined the room
 * chat:room:{id}:messages       LIST  chat history, JSON strings, oldest -> newest
 * chat:channel:{id}             PUB/SUB channel for real-time delivery
 * </pre>
 */
public final class RedisKeys {

    public static final String ROOM_INDEX = "chat:rooms";
    public static final String CHANNEL_PREFIX = "chat:channel:";
    public static final String CHANNEL_PATTERN = CHANNEL_PREFIX + "*";

    private static final String ROOM_PREFIX = "chat:room:";

    private RedisKeys() {
    }

    public static String roomMeta(String roomId) {
        return ROOM_PREFIX + roomId;
    }

    public static String participants(String roomId) {
        return ROOM_PREFIX + roomId + ":participants";
    }

    public static String messages(String roomId) {
        return ROOM_PREFIX + roomId + ":messages";
    }

    public static String channel(String roomId) {
        return CHANNEL_PREFIX + roomId;
    }

    public static String roomIdFromChannel(String channel) {
        if (channel == null || !channel.startsWith(CHANNEL_PREFIX)) {
            throw new IllegalArgumentException("Not a chat channel: " + channel);
        }
        return channel.substring(CHANNEL_PREFIX.length());
    }
}
