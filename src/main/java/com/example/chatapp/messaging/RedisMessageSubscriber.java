package com.example.chatapp.messaging;

import com.example.chatapp.codec.MessageCodec;
import com.example.chatapp.constants.RedisKeys;
import com.example.chatapp.model.ChatMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Receives every message published on {@code chat:channel:*} and hands it to the broadcaster.
 * Never throws: one malformed payload must not break the subscription.
 */
@Component
public class RedisMessageSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisMessageSubscriber.class);

    private final MessageCodec codec;
    private final MessageBroadcaster broadcaster;

    public RedisMessageSubscriber(MessageCodec codec, MessageBroadcaster broadcaster) {
        this.codec = codec;
        this.broadcaster = broadcaster;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            String roomId = RedisKeys.roomIdFromChannel(channel);
            ChatMessage chatMessage = codec.fromJson(body);
            log.debug("Received message on room '{}' from '{}'", roomId, chatMessage.participant());
            broadcaster.broadcast(roomId, chatMessage);
        } catch (RuntimeException e) {
            log.error("Dropping invalid Pub/Sub message", e);
        }
    }
}
