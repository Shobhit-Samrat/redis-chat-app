package com.example.chatapp.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.chatapp.exception.InvalidRequestException;
import com.example.chatapp.exception.ParticipantNotInRoomException;
import com.example.chatapp.exception.RoomAlreadyExistsException;
import com.example.chatapp.exception.RoomNotFoundException;
import com.example.chatapp.model.ChatMessage;
import com.example.chatapp.service.ChatRoomService;
import com.example.chatapp.service.RoomStreamService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatRoomController.class)
class ChatRoomControllerTest {

    private static final String BASE = "/api/chatapp/chatrooms";

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private ChatRoomService chatRoomService;
    @MockBean
    private RoomStreamService roomStreamService;

    @Test
    void createRoom_returns201WithSpecBody() throws Exception {
        when(chatRoomService.createRoom("general")).thenReturn("general");

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"roomName\":\"general\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Chat room 'general' created successfully."))
                .andExpect(jsonPath("$.roomId").value("general"))
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void createRoom_duplicate_returns409() throws Exception {
        when(chatRoomService.createRoom("general")).thenThrow(new RoomAlreadyExistsException("general"));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"roomName\":\"general\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Chat room 'general' already exists."));
    }

    @Test
    void createRoom_invalidName_returns400() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"roomName\":\"bad name!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void createRoom_missingBody_returns400() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void joinRoom_returns200() throws Exception {
        mockMvc.perform(post(BASE + "/general/join").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participant\":\"guest_user\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User 'guest_user' joined chat room 'general'."))
                .andExpect(jsonPath("$.status").value("success"));

        verify(chatRoomService).joinRoom("general", "guest_user");
    }

    @Test
    void joinRoom_unknownRoom_returns404() throws Exception {
        doThrow(new RoomNotFoundException("ghost")).when(chatRoomService).joinRoom("ghost", "guest_user");

        mockMvc.perform(post(BASE + "/ghost/join").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participant\":\"guest_user\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sendMessage_returns201() throws Exception {
        mockMvc.perform(post(BASE + "/general/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participant\":\"guest_user\",\"message\":\"Hello, everyone!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Message sent successfully."))
                .andExpect(jsonPath("$.status").value("success"));

        verify(chatRoomService).sendMessage("general", "guest_user", "Hello, everyone!");
    }

    @Test
    void sendMessage_unknownRoom_returns404() throws Exception {
        doThrow(new RoomNotFoundException("ghost")).when(chatRoomService)
                .sendMessage("ghost", "guest_user", "Hi");

        mockMvc.perform(post(BASE + "/ghost/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participant\":\"guest_user\",\"message\":\"Hi\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Chat room 'ghost' does not exist."));
    }

    @Test
    void sendMessage_nonMember_returns403() throws Exception {
        doThrow(new ParticipantNotInRoomException("stranger", "general")).when(chatRoomService)
                .sendMessage("general", "stranger", "Hi");

        mockMvc.perform(post(BASE + "/general/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participant\":\"stranger\",\"message\":\"Hi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sendMessage_blankMessage_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/general/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participant\":\"guest_user\",\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMessages_returnsHistory() throws Exception {
        when(chatRoomService.getMessages("general", 10)).thenReturn(List.of(
                new ChatMessage("guest_user", "Hello, everyone!", Instant.parse("2024-01-01T10:00:00Z")),
                new ChatMessage("another_user", "Hi, guest_user!", Instant.parse("2024-01-01T10:01:00Z"))));

        mockMvc.perform(get(BASE + "/general/messages").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].participant").value("guest_user"))
                .andExpect(jsonPath("$.messages[0].message").value("Hello, everyone!"))
                .andExpect(jsonPath("$.messages[0].timestamp").value("2024-01-01T10:00:00Z"))
                .andExpect(jsonPath("$.messages[1].participant").value("another_user"));
    }

    @Test
    void getMessages_invalidLimit_returns400() throws Exception {
        when(chatRoomService.getMessages("general", 0)).thenThrow(new InvalidRequestException("bad limit"));

        mockMvc.perform(get(BASE + "/general/messages").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMessages_nonNumericLimit_returns400() throws Exception {
        mockMvc.perform(get(BASE + "/general/messages").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void deleteRoom_returns200() throws Exception {
        mockMvc.perform(delete(BASE + "/general"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Chat room 'general' deleted successfully."))
                .andExpect(jsonPath("$.status").value("success"));

        verify(chatRoomService).deleteRoom("general");
    }

    @Test
    void deleteRoom_unknownRoom_returns404() throws Exception {
        doThrow(new RoomNotFoundException("ghost")).when(chatRoomService).deleteRoom("ghost");

        mockMvc.perform(delete(BASE + "/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void unsupportedMethod_returns405WithErrorEnvelope() throws Exception {
        mockMvc.perform(delete(BASE))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value("error"));
    }
}
