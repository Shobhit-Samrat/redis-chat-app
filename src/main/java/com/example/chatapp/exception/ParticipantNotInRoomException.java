package com.example.chatapp.exception;

public class ParticipantNotInRoomException extends RuntimeException {
    public ParticipantNotInRoomException(String participant, String roomId) {
        super("User '" + participant + "' has not joined chat room '" + roomId + "'.");
    }
}
