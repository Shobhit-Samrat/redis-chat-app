package com.example.chatapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.chatapp.config.ChatProperties;
import com.example.chatapp.exception.InvalidRequestException;
import com.example.chatapp.exception.ParticipantNotInRoomException;
import com.example.chatapp.exception.RoomAlreadyExistsException;
import com.example.chatapp.exception.RoomNotFoundException;
import com.example.chatapp.messaging.MessagePublisher;
import com.example.chatapp.model.ChatMessage;
import com.example.chatapp.repository.ChatRoomRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class DefaultChatRoomServiceTest {

    private static final Instant NOW = Instant.parse("2024-01-01T10:00:00Z");

    @Mock
    private ChatRoomRepository repository;
    @Mock
    private MessagePublisher publisher;
    @Mock
    private ApplicationEventPublisher events;

    private DefaultChatRoomService service;

    @BeforeEach
    void setUp() {
        service = newService(true);
    }

    private DefaultChatRoomService newService(boolean requireMembership) {
        ChatProperties props = new ChatProperties(requireMembership, 1000, 10, 100, Duration.ofMinutes(30));
        return new DefaultChatRoomService(repository, publisher, events, props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ---- create ----

    @Test
    void createRoom_returnsRoomId() {
        when(repository.createRoom("general", NOW)).thenReturn(true);

        assertEquals("general", service.createRoom("general"));
    }

    @Test
    void createRoom_duplicateName_throwsConflict() {
        when(repository.createRoom("general", NOW)).thenReturn(false);

        assertThrows(RoomAlreadyExistsException.class, () -> service.createRoom("general"));
    }

    // ---- join ----

    @Test
    void joinRoom_addsParticipant() {
        when(repository.exists("general")).thenReturn(true);

        service.joinRoom("general", "guest_user");

        verify(repository).addParticipant("general", "guest_user");
    }

    @Test
    void joinRoom_unknownRoom_throwsNotFound() {
        when(repository.exists("ghost")).thenReturn(false);

        assertThrows(RoomNotFoundException.class, () -> service.joinRoom("ghost", "guest_user"));
        verify(repository, never()).addParticipant(any(), any());
    }

    // ---- send ----

    @Test
    void sendMessage_persistsThenPublishes() {
        when(repository.exists("general")).thenReturn(true);
        when(repository.isParticipant("general", "guest_user")).thenReturn(true);
        ChatMessage expected = new ChatMessage("guest_user", "Hello, everyone!", NOW);

        service.sendMessage("general", "guest_user", "Hello, everyone!");

        verify(repository).appendMessage("general", expected);
        verify(publisher).publish("general", expected);
    }

    @Test
    void sendMessage_unknownRoom_throwsNotFoundAndPublishesNothing() {
        when(repository.exists("ghost")).thenReturn(false);

        assertThrows(RoomNotFoundException.class, () -> service.sendMessage("ghost", "guest_user", "Hi"));
        verify(repository, never()).appendMessage(any(), any());
        verifyNoInteractions(publisher);
    }

    @Test
    void sendMessage_nonMember_throwsForbidden() {
        when(repository.exists("general")).thenReturn(true);
        when(repository.isParticipant("general", "stranger")).thenReturn(false);

        assertThrows(ParticipantNotInRoomException.class, () -> service.sendMessage("general", "stranger", "Hi"));
        verify(repository, never()).appendMessage(any(), any());
        verifyNoInteractions(publisher);
    }

    @Test
    void sendMessage_membershipNotRequired_allowsAnyone() {
        DefaultChatRoomService relaxed = newService(false);
        when(repository.exists("general")).thenReturn(true);

        relaxed.sendMessage("general", "stranger", "Hi");

        verify(repository).appendMessage("general", new ChatMessage("stranger", "Hi", NOW));
    }

    // ---- history ----

    @Test
    void getMessages_withoutLimit_usesDefault() {
        List<ChatMessage> history = List.of(new ChatMessage("a", "one", NOW), new ChatMessage("b", "two", NOW));
        when(repository.exists("general")).thenReturn(true);
        when(repository.findLastMessages("general", 10)).thenReturn(history);

        assertEquals(history, service.getMessages("general", null));
    }

    @Test
    void getMessages_usesRequestedLimit() {
        when(repository.exists("general")).thenReturn(true);
        when(repository.findLastMessages("general", 1)).thenReturn(List.of());

        assertEquals(List.of(), service.getMessages("general", 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5, 101})
    void getMessages_invalidLimit_throwsBadRequest(int limit) {
        assertThrows(InvalidRequestException.class, () -> service.getMessages("general", limit));
    }

    @Test
    void getMessages_unknownRoom_throwsNotFound() {
        when(repository.exists("ghost")).thenReturn(false);

        assertThrows(RoomNotFoundException.class, () -> service.getMessages("ghost", 5));
    }

    // ---- delete ----

    @Test
    void deleteRoom_removesDataAndRaisesEvent() {
        when(repository.deleteRoom("general")).thenReturn(true);

        service.deleteRoom("general");

        verify(events).publishEvent(new RoomDeletedEvent("general"));
    }

    @Test
    void deleteRoom_unknownRoom_throwsNotFound() {
        when(repository.deleteRoom("ghost")).thenReturn(false);

        assertThrows(RoomNotFoundException.class, () -> service.deleteRoom("ghost"));
        verifyNoInteractions(events);
    }
}
