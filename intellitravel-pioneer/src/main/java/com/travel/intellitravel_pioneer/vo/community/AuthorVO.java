package com.travel.intellitravel_pioneer.vo.community;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthorVO {
    private Integer userId;
    private String username;
    private String name;
    private String avatar;
}
