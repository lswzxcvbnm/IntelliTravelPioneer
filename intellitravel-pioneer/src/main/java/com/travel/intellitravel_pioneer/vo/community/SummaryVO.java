package com.travel.intellitravel_pioneer.vo.community;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SummaryVO {
    @JsonProperty("summary_id")
    private String summaryId;

    private String content;
}
