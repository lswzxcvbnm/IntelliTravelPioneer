package com.travel.intellitravel_pioneer.vo.trip;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class ReportSummary {
    private Long id;
    private String templateId;
    private String reportImageUrl;
    private OffsetDateTime createdAt;
}
