package com.example.chatapp.service;

import com.example.chatapp.config.ChatProperties;
import com.example.chatapp.exception.InvalidRequestException;
import com.example.chatapp.exception.ParticipantNotInRoomException;
import com.example.chatapp.exception.RoomAlreadyExistsException;
import com.example.chatapp.exception.RoomNotFoundException;
import com.example.chatapp.messaging.MessagePublisher;
import com.example.chatapp.model.ChatMessage;
import com.example.chatapp.repository.ChatRoomRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class DefaultChatRoomService implements ChatRoomService {

    private static final Logger log = LoggerFactory.getLogger(DefaultChatRoomService.class);

    private final ChatRoomRepository repository;
    private final MessagePublisher publisher;
    private final ApplicationEventPublisher events;
    private final ChatProperties properties;
    private final Clock clock;

    public DefaultChatRoomService(ChatRoomRepository repository,
                                  MessagePublisher publisher,
                                  ApplicationEventPublisher events,
                                  ChatProperties properties,
                                  Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String createRoom(String roomName) {
        if (!repository.createRoom(roomName, clock.instant().truncatedTo(ChronoUnit.MILLIS))) {
            throw new RoomAlreadyExistsException(roomName);
        }
        log.info("Created chat room '{}'", roomName);
        return roomName;
    }

    @Override
    public void joinRoom(String roomId, String participant) {
        requireRoom(roomId);
        repository.addParticipant(roomId, participant); // SADD is idempotent: re-joining is harmless
        log.info("'{}' joined room '{}'", participant, roomId);
    }

    @Override
    public void sendMessage(String roomId, String participant, String message) {
        authorizeAccess(roomId, participant);
        ChatMessage chatMessage = new ChatMessage(participant, message, clock.instant().truncatedTo(ChronoUnit.MILLIS));
        repository.appendMessage(roomId, chatMessage);   // 1. persist (Redis List)
        publisher.publish(roomId, chatMessage);          // 2. real-time fan-out (Redis Pub/Sub)
    }

    @Override
    public List<ChatMessage> getMessages(String roomId, Integer limit) {
        int effectiveLimit = resolveLimit(limit);
        requireRoom(roomId);
        return repository.findLastMessages(roomId, effectiveLimit);
    }

    @Override
    public void deleteRoom(String roomId) {
        if (!repository.deleteRoom(roomId)) {
            throw new RoomNotFoundException(roomId);
        }
        events.publishEvent(new RoomDeletedEvent(roomId));
        log.info("Deleted chat room '{}'", roomId);
    }

    @Override
    public void authorizeAccess(String roomId, String participant) {
        requireRoom(roomId);
        if (properties.requireMembership() && !repository.isParticipant(roomId, participant)) {
            throw new ParticipantNotInRoomException(participant, roomId);
        }
    }

    private void requireRoom(String roomId) {
        if (!repository.exists(roomId)) {
            throw new RoomNotFoundException(roomId);
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return properties.defaultHistoryLimit();
        }
        if (limit < 1 || limit > properties.maxHistoryLimit()) {
            throw new InvalidRequestException(
                    "limit must be between 1 and " + properties.maxHistoryLimit() + ".");
        }
        return limit;
    }
}
