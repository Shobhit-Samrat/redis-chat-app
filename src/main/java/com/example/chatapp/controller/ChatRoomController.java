package com.example.chatapp.controller;

import com.example.chatapp.dto.ApiResponse;
import com.example.chatapp.dto.CreateRoomRequest;
import com.example.chatapp.dto.JoinRoomRequest;
import com.example.chatapp.dto.MessageHistoryResponse;
import com.example.chatapp.dto.SendMessageRequest;
import com.example.chatapp.service.ChatRoomService;
import com.example.chatapp.service.RoomStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Thin HTTP layer: validates input, delegates to the services, shapes the response. */
@Tag(name = "Chat Rooms", description = "Create rooms, join, send messages, read history, live stream, delete")
@RestController
@RequestMapping("/api/chatapp/chatrooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final RoomStreamService roomStreamService;

    public ChatRoomController(ChatRoomService chatRoomService, RoomStreamService roomStreamService) {
        this.chatRoomService = chatRoomService;
        this.roomStreamService = roomStreamService;
    }

    @Operation(summary = "Create a chat room (unique name)")
    @PostMapping
    public ResponseEntity<ApiResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        String roomId = chatRoomService.createRoom(request.roomName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Chat room '" + roomId + "' created successfully.", roomId));
    }

    @Operation(summary = "Join an existing chat room")
    @PostMapping("/{roomId}/join")
    public ResponseEntity<ApiResponse> joinRoom(@PathVariable String roomId,
                                                @Valid @RequestBody JoinRoomRequest request) {
        chatRoomService.joinRoom(roomId, request.participant());
        return ResponseEntity.ok(ApiResponse.success(
                "User '" + request.participant() + "' joined chat room '" + roomId + "'."));
    }

    @Operation(summary = "Send a message (stored in Redis List and published via Pub/Sub)")
    @PostMapping("/{roomId}/messages")
    public ResponseEntity<ApiResponse> sendMessage(@PathVariable String roomId,
                                                   @Valid @RequestBody SendMessageRequest request) {
        chatRoomService.sendMessage(roomId, request.participant(), request.message());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Message sent successfully."));
    }

    @Operation(summary = "Get the last N messages (oldest to newest)")
    @GetMapping("/{roomId}/messages")
    public ResponseEntity<MessageHistoryResponse> getMessages(@PathVariable String roomId,
                                                              @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(new MessageHistoryResponse(chatRoomService.getMessages(roomId, limit)));
    }

    /** Real-time feed (Server-Sent Events) fed by Redis Pub/Sub. */
    @Operation(summary = "Real-time message stream (Server-Sent Events fed by Redis Pub/Sub)")
    @GetMapping(value = "/{roomId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessages(@PathVariable String roomId, @RequestParam String participant) {
        return roomStreamService.openStream(roomId, participant);
    }

    @Operation(summary = "Delete a chat room and all its Redis keys")
    @DeleteMapping("/{roomId}")
    public ResponseEntity<ApiResponse> deleteRoom(@PathVariable String roomId) {
        chatRoomService.deleteRoom(roomId);
        return ResponseEntity.ok(ApiResponse.success("Chat room '" + roomId + "' deleted successfully.", roomId));
    }
}
