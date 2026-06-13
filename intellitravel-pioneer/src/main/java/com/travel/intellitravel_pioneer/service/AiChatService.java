package com.travel.intellitravel_pioneer.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiChatService {
    SseEmitter chatStream(String token, String message, String conversationId);
}
