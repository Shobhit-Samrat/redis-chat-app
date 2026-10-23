package com.example.chatapp.messaging;

import com.example.chatapp.codec.MessageCodec;
import com.example.chatapp.constants.RedisKeys;
import com.example.chatapp.model.ChatMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Publishes to the room's Redis Pub/Sub channel ({@code chat:channel:{roomId}}). */
@Component
public class RedisMessagePublisher implements MessagePublisher {

    private final StringRedisTemplate redis;
    private final MessageCodec codec;

    public RedisMessagePublisher(StringRedisTemplate redis, MessageCodec codec) {
        this.redis = redis;
        this.codec = codec;
    }

    @Override
    public void publish(String roomId, ChatMessage message) {
        redis.convertAndSend(RedisKeys.channel(roomId), codec.toJson(message));
    }
}
