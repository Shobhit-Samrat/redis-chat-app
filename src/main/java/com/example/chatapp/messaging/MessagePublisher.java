package com.example.chatapp.messaging;

import com.example.chatapp.model.ChatMessage;

/** Publishes a chat message to every subscriber of a room. */
public interface MessagePublisher {

    void publish(String roomId, ChatMessage message);
}
