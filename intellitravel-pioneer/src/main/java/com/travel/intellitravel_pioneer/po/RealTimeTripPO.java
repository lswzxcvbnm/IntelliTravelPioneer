package com.travel.intellitravel_pioneer.po;

import com.travel.intellitravel_pioneer.po.converter.UserRealTimeStatusJsonConverter;
import com.travel.intellitravel_pioneer.vo.trip.UserRealTimeStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import java.time.LocalDateTime;

/**
 * 实时行程数据持久化对象 (Real Time Trip Persistent Object)
 * 存储用户当前的行程状态、配置及最近一次规划结果
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "real_time_trip", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_trip_id", columnList = "trip_id", unique = true)
})
@DynamicUpdate
public class RealTimeTripPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 业务行程ID (全局唯一)
     */
    @Column(name = "trip_id", length = 32, nullable = false, unique = true)
    private String tripId;

    /**
     * 关联用户ID (对应 UserPO 或外部系统ID)
     */
    @Column(name = "user_id", nullable = false)
    private String userId;

    /**
     * 当前执行的行程计划id
     */
    @Column(name = "itinerary_id")
    private String itineraryId;

    /**
     * 行程是否已结束
     */
    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false;

    /**
     * 最近一次上报的实时状态 (详细数据JSON存储)
     */
    @Convert(converter = UserRealTimeStatusJsonConverter.class)
    @Column(name = "last_status", columnDefinition = "TEXT")
    private UserRealTimeStatus lastStatus;

    /**
     * 乐观锁版本号
     */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * 记录创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 记录更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isCompleted == null) {
            isCompleted = false;
        }
        if (version == null) {
            version = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (version == null) {
            version = 0;
        }
    }
}
