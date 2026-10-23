package com.example.chatapp.dto;

import com.example.chatapp.model.ChatMessage;
import java.util.List;

public record MessageHistoryResponse(List<ChatMessage> messages) {
}
