package com.travel.intellitravel_pioneer.vo.trip;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.OffsetDateTime;
import com.travel.intellitravel_pioneer.enums.SuggestionType;

/**
 * 实时调整建议对象
 * 对应接口：/api/trips/{trip_id}/suggestions
 */
@Data
public class DynamicSuggestion {
    /**
     * 建议ID
     */
    @JsonProperty("suggestion_id")
    private String suggestionId;
    /**
     * 建议类型
     */
    private SuggestionType type;
    /**
     * 建议内容
     */
    private String content;
    /**
     * 建议生成时间
     */
    private OffsetDateTime timestamp;
    /**
     * 行动指令
     */
    private ActionCommand action;
}
