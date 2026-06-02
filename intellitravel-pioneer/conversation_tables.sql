-- Agent 对话管理相关表
-- 运行此 SQL 前请确保已创建 intellitravel 数据库

CREATE TABLE IF NOT EXISTS `agent_conversation` (
    `id` VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '对话ID (UUID)',
    `user_id` VARCHAR(36) NOT NULL COMMENT '用户ID',
    `title` VARCHAR(255) DEFAULT '新对话' COMMENT '对话标题',
    `tags` VARCHAR(500) DEFAULT NULL COMMENT '标签 (逗号分隔)',
    `description` TEXT DEFAULT NULL COMMENT '对话描述',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent 对话表';

CREATE TABLE IF NOT EXISTS `agent_message` (
    `id` VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '消息ID (UUID)',
    `conversation_id` VARCHAR(36) NOT NULL COMMENT '对话ID',
    `role` VARCHAR(20) NOT NULL COMMENT '角色: user / assistant / system / tool',
    `content` MEDIUMTEXT DEFAULT NULL COMMENT '消息内容',
    `tool_calls` TEXT DEFAULT NULL COMMENT '工具调用记录 (JSON)',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    INDEX `idx_conversation_id` (`conversation_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent 对话消息表';
