package com.example.chatapp.service;

/** Domain event raised after a room has been deleted (decouples the service from live connections). */
public record RoomDeletedEvent(String roomId) {
}
