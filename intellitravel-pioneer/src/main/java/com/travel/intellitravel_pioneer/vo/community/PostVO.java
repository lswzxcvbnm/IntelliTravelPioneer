package com.travel.intellitravel_pioneer.vo.community;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostVO {

    /** 关联的 AI 总结 ID */
    private String summaryId;

    /** 帖子标题，若不传可在服务端用总结标题填充 */
    private String title;

    /** 用户自定义的补充内容 */
    private String content;

    /** 配图 URL 列表 */
    private List<String> images;

    /** 是否公开，true/false */
    private Boolean isPublic;

    /** 标签字符串，逗号分隔 */
    private String tags;
}