package com.example.chatapp.codec;

import com.example.chatapp.model.ChatMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Converts {@link ChatMessage} to/from its JSON wire format.
 * Shared by the repository (List storage), publisher and subscriber so the format lives in one place.
 */
@Component
public class MessageCodec {

    private final ObjectMapper objectMapper;

    public MessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(ChatMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialise chat message", e);
        }
    }

    public ChatMessage fromJson(String json) {
        try {
            return objectMapper.readValue(json, ChatMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to deserialise chat message: " + json, e);
        }
    }
}
