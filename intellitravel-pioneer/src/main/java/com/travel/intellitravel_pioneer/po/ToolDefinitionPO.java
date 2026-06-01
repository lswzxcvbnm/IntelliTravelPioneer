package com.travel.intellitravel_pioneer.po;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tool_definition")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolDefinitionPO {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "tool_name", length = 100, unique = true, nullable = false)
    private String toolName;

    @Column(name = "display_name", length = 100, nullable = false)
    private String displayName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "parameters_json", columnDefinition = "TEXT")
    private String parametersJson;

    @Column(name = "usage_example", columnDefinition = "TEXT")
    private String usageExample;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;
}
