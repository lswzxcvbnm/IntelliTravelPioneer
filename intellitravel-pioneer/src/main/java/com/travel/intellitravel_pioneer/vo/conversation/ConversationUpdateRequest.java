package com.travel.intellitravel_pioneer.vo.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationUpdateRequest {
    private String title;
    private String tags;
    private String description;
}
