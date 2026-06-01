package com.travel.intellitravel_pioneer.po;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_tool_preference", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "tool_name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserToolPreferencePO {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "tool_name", length = 100, nullable = false)
    private String toolName;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;
}
