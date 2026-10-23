package com.example.chatapp.service;

import com.example.chatapp.model.ChatMessage;
import java.util.List;

/** Business operations of the chat application. */
public interface ChatRoomService {

    /** @return the id of the created room. */
    String createRoom(String roomName);

    void joinRoom(String roomId, String participant);

    void sendMessage(String roomId, String participant, String message);

    /** @param limit number of newest messages; {@code null} means the configured default. */
    List<ChatMessage> getMessages(String roomId, Integer limit);

    void deleteRoom(String roomId);

    /** Verifies the room exists (and, if configured, that the participant joined it). */
    void authorizeAccess(String roomId, String participant);
}
