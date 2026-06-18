package com.travel.intellitravel_pioneer.controller;

import com.travel.intellitravel_pioneer.service.AiChatService;
import com.travel.intellitravel_pioneer.vo.ai.AiChatRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    @Autowired
    private AiChatService aiChatService;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestHeader("token") String token,
                                  @RequestBody AiChatRequest request) {
        return aiChatService.chatStream(token, request.getMessage(), request.getConversationId());
    }
}
