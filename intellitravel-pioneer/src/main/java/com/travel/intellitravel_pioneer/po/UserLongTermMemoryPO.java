package com.travel.intellitravel_pioneer.po;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;
@Entity
@Table(name = "user_long_term_memory")
@Data
public class UserLongTermMemoryPO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    @Column(name = "user_id")
    private String userId;
    @Column(name = "memory_type")
    private String memoryType;
    @Column(name = "memory_key")
    private String memoryKey;
    @Column(name = "memory_value")
    private String memoryValue;
    @Column(name = "confidence")
    private BigDecimal confidence;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

