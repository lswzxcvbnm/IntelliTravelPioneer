package com.travel.intellitravel_pioneer.vo.social;

import lombok.Data;

@Data
public class MessageSendRequest {
    private String receiverId;
    private String content;
    private String shareType;
    private String shareId;
}
