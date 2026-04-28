package com.travel.intellitravel_pioneer.vo.social;

import lombok.Data;

@Data
public class MessageVO {
    private Integer messageId;
    private Integer senderId;
    private String senderName;
    private String senderAvatar;
    private Integer receiverId;
    private String receiverName;
    private String content;
    private Boolean isRead;
    private String shareType;
    private String shareId;
    private String createdAt;
}
