package com.example.chatapp.messaging;

import com.example.chatapp.config.ChatProperties;
import com.example.chatapp.model.ChatMessage;
import com.example.chatapp.service.RoomDeletedEvent;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Keeps the live HTTP (Server-Sent Events) connections per room and pushes Pub/Sub messages to them.
 * Lets a REST client such as Postman or curl actually <em>see</em> real-time messages, without any UI.
 */
@Component
public class SseEmitterRegistry implements MessageBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    private final ConcurrentMap<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final long timeoutMillis;

    public SseEmitterRegistry(ChatProperties properties) {
        this.timeoutMillis = properties.sseTimeout().toMillis();
    }

    /** Opens a new live stream for a room. */
    public SseEmitter register(String roomId) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        emitters.computeIfAbsent(roomId, key -> new CopyOnWriteArraySet<>()).add(emitter);

        Runnable cleanup = () -> remove(roomId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("connected").data("Subscribed to room '" + roomId + "'"));
        } catch (IOException | IllegalStateException e) {
            remove(roomId, emitter);
        }
        return emitter;
    }

    @Override
    public void broadcast(String roomId, ChatMessage message) {
        Set<SseEmitter> targets = emitters.get(roomId);
        if (targets == null) {
            return;
        }
        for (SseEmitter emitter : targets) {
            try {
                emitter.send(SseEmitter.event().name("message").data(message, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                log.debug("Removing dead emitter for room '{}'", roomId);
                remove(roomId, emitter);
            }
        }
    }

    /** Closes all live streams of a deleted room. */
    @EventListener
    public void onRoomDeleted(RoomDeletedEvent event) {
        closeRoom(event.roomId());
    }

    public void closeRoom(String roomId) {
        Set<SseEmitter> removed = emitters.remove(roomId);
        if (removed != null) {
            removed.forEach(SseEmitter::complete);
        }
    }

    public int subscriberCount(String roomId) {
        Set<SseEmitter> set = emitters.get(roomId);
        return set == null ? 0 : set.size();
    }

    private void remove(String roomId, SseEmitter emitter) {
        emitters.computeIfPresent(roomId, (key, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }
}
