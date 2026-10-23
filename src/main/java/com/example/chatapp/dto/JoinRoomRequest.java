package com.example.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinRoomRequest(
        @NotBlank(message = "must not be blank")
        @Size(max = 50, message = "must be at most 50 characters")
        String participant) {
}
