package com.travel.intellitravel_pioneer.vo.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentMessageVO {
    private String id;
    private String conversationId;
    private String role;
    private String content;
    private String toolCalls;
    private LocalDateTime createTime;
}
