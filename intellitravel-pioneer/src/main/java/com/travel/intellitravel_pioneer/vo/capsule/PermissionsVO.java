package com.travel.intellitravel_pioneer.vo.capsule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionsVO {
    private Boolean canEdit;
    private Boolean canDelete;
    private Boolean canComment;
}