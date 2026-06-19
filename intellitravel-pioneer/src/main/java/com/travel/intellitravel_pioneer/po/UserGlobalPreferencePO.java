package com.travel.intellitravel_pioneer.po;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 用户全局偏好实体（跨对话知识共享）
 * 用户可主动设置的个性化偏好，在所有对话中生效
 */
@Entity
@Table(name = "user_global_preference", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "category", "preference_key"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserGlobalPreferencePO {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    /**
     * 偏好类别：
     * - STYLE: 回答风格（简略/详细/专业等）
     * - DIET: 饮食偏好（素食/清真/无辣等）
     * - FITNESS: 体能水平（轻松/适中/挑战）
     * - SPECIAL: 特殊需求（带老人/带小孩/无障碍等）
     * - CUSTOM: 自定义规则
     */
    @Column(name = "category", length = 50, nullable = false)
    private String category;

    @Column(name = "preference_key", length = 100, nullable = false)
    private String preferenceKey;

    @Column(name = "preference_value", nullable = false, columnDefinition = "TEXT")
    private String preferenceValue;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}