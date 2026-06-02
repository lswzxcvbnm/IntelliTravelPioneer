package com.travel.intellitravel_pioneer.vo.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationDetailVO {
    private ConversationVO conversation;
    private List<AgentMessageVO> messages;
}
