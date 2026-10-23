package com.example.chatapp.exception;

public class RoomAlreadyExistsException extends RuntimeException {
    public RoomAlreadyExistsException(String roomId) {
        super("Chat room '" + roomId + "' already exists.");
    }
}
