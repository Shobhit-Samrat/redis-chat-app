package com.example.chatapp.exception;

public class RoomNotFoundException extends RuntimeException {
    public RoomNotFoundException(String roomId) {
        super("Chat room '" + roomId + "' does not exist.");
    }
}
