package com.travel.intellitravel_pioneer.vo.social;

import lombok.Data;

@Data
public class ConversationVO {
    private Integer friendId;
    private String friendName;
    private String friendAvatar;
    private String lastMessage;
    private String lastMessageTime;
    private Integer unreadCount;
}
