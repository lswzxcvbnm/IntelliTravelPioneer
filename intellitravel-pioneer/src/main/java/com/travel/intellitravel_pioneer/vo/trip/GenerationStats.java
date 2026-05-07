package com.travel.intellitravel_pioneer.vo.trip;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerationStats {

    private int totalPromptTokens;
    private int totalCompletionTokens;
    private int totalTokens;

    private List<ToolCallRecord> toolCalls = new ArrayList<>();

    private int planCalls;
    private int executeCalls;
    private int verifyCalls;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallRecord {
        private String name;
        private String displayName;
        private String status;
    }
}
