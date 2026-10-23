package com.example.chatapp.repository;

import com.example.chatapp.model.ChatMessage;
import java.time.Instant;
import java.util.List;

/**
 * Persistence port for chat data. The service layer depends only on this interface,
 * so the Redis implementation can be swapped or mocked freely.
 */
public interface ChatRoomRepository {

    /** Atomically registers a room. @return {@code false} if a room with this id already exists. */
    boolean createRoom(String roomId, Instant createdAt);

    boolean exists(String roomId);

    void addParticipant(String roomId, String participant);

    boolean isParticipant(String roomId, String participant);

    void appendMessage(String roomId, ChatMessage message);

    /** @return up to {@code limit} newest messages, ordered oldest -> newest. */
    List<ChatMessage> findLastMessages(String roomId, int limit);

    /** Removes the room and all related keys. @return {@code false} if the room did not exist. */
    boolean deleteRoom(String roomId);
}
