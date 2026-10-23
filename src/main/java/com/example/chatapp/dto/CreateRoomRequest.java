package com.example.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateRoomRequest(
        @NotBlank(message = "must not be blank")
        @Pattern(regexp = "^[A-Za-z0-9_-]{1,50}$",
                message = "must be 1-50 characters: letters, digits, '_' or '-'")
        String roomName) {
}
