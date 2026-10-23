package com.example.chatapp.service;

import com.example.chatapp.messaging.SseEmitterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Opens authorised live (Server-Sent Events) streams for a room. */
@Service
public class RoomStreamService {

    private final ChatRoomService chatRoomService;
    private final SseEmitterRegistry registry;

    public RoomStreamService(ChatRoomService chatRoomService, SseEmitterRegistry registry) {
        this.chatRoomService = chatRoomService;
        this.registry = registry;
    }

    public SseEmitter openStream(String roomId, String participant) {
        chatRoomService.authorizeAccess(roomId, participant);
        return registry.register(roomId);
    }
}
