package com.example.chatapp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Uniform response envelope: {@code {"message": "...", "roomId": "...", "status": "success|error"}}.
 * {@code roomId} is omitted when null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse(String message, String roomId, String status) {

    public static final String SUCCESS = "success";
    public static final String ERROR = "error";

    public static ApiResponse success(String message) {
        return new ApiResponse(message, null, SUCCESS);
    }

    public static ApiResponse success(String message, String roomId) {
        return new ApiResponse(message, roomId, SUCCESS);
    }

    public static ApiResponse error(String message) {
        return new ApiResponse(message, null, ERROR);
    }
}
