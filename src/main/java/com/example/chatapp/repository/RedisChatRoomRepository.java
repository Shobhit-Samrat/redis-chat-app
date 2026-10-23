package com.example.chatapp.repository;

import com.example.chatapp.codec.MessageCodec;
import com.example.chatapp.config.ChatProperties;
import com.example.chatapp.constants.RedisKeys;
import com.example.chatapp.model.ChatMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Redis-backed implementation.
 * <ul>
 *   <li>SET  {@code chat:rooms}                   - room index, SADD gives an atomic "create if absent"</li>
 *   <li>HASH {@code chat:room:{id}}               - room metadata</li>
 *   <li>SET  {@code chat:room:{id}:participants}  - members (O(1) add / membership test)</li>
 *   <li>LIST {@code chat:room:{id}:messages}      - history; RPUSH + LRANGE, trimmed to a max size</li>
 * </ul>
 */
@Repository
public class RedisChatRoomRepository implements ChatRoomRepository {

    private final StringRedisTemplate redis;
    private final MessageCodec codec;
    private final ChatProperties properties;

    public RedisChatRoomRepository(StringRedisTemplate redis, MessageCodec codec, ChatProperties properties) {
        this.redis = redis;
        this.codec = codec;
        this.properties = properties;
    }

    @Override
    public boolean createRoom(String roomId, Instant createdAt) {
        Long added = redis.opsForSet().add(RedisKeys.ROOM_INDEX, roomId);
        if (added == null || added == 0L) {
            return false; // somebody else already owns this name
        }
        redis.opsForHash().putAll(RedisKeys.roomMeta(roomId),
                Map.of("roomId", roomId, "createdAt", createdAt.toString()));
        return true;
    }

    @Override
    public boolean exists(String roomId) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(RedisKeys.ROOM_INDEX, roomId));
    }

    @Override
    public void addParticipant(String roomId, String participant) {
        redis.opsForSet().add(RedisKeys.participants(roomId), participant);
    }

    @Override
    public boolean isParticipant(String roomId, String participant) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(RedisKeys.participants(roomId), participant));
    }

    @Override
    public void appendMessage(String roomId, ChatMessage message) {
        String key = RedisKeys.messages(roomId);
        Long size = redis.opsForList().rightPush(key, codec.toJson(message));
        if (size != null && size > properties.maxMessagesPerRoom()) {
            // keep only the newest N entries so a busy room cannot grow without bound
            redis.opsForList().trim(key, -properties.maxMessagesPerRoom(), -1);
        }
    }

    @Override
    public List<ChatMessage> findLastMessages(String roomId, int limit) {
        List<String> raw = redis.opsForList().range(RedisKeys.messages(roomId), -limit, -1);
        if (raw == null) {
            return List.of();
        }
        return raw.stream().map(codec::fromJson).toList();
    }

    @Override
    public boolean deleteRoom(String roomId) {
        Long removed = redis.opsForSet().remove(RedisKeys.ROOM_INDEX, roomId);
        if (removed == null || removed == 0L) {
            return false;
        }
        // single DEL command removes metadata, participants and history together
        redis.delete(List.of(
                RedisKeys.roomMeta(roomId),
                RedisKeys.participants(roomId),
                RedisKeys.messages(roomId)));
        return true;
    }
}
