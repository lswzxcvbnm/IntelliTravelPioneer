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
public class ConversationVO {
    private String id;
    private String userId;
    private String title;
    private String tags;
    private String description;
    private Integer messageCount;
    private String itineraryId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
