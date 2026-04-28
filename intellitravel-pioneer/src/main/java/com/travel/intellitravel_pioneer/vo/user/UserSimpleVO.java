package com.travel.intellitravel_pioneer.vo.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSimpleVO {
    private String id;
    private String name;
    private String avatar;
}