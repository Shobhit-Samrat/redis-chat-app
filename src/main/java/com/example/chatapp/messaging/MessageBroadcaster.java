package com.example.chatapp.messaging;

import com.example.chatapp.model.ChatMessage;

/**
 * Delivers a message received from Pub/Sub to the clients connected to <em>this</em> instance.
 * (Observer pattern: Redis is the subject, broadcasters are the observers.)
 */
public interface MessageBroadcaster {

    void broadcast(String roomId, ChatMessage message);
}
