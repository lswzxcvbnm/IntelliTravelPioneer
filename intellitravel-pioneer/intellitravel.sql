-- IntelliTravel Pioneer 完整数据库结构
-- 基于PO层文件生成
-- 生成时间: 2026-06-01
-- 更新时间: 2026-06-04 (合并对话相关表)

-- 禁用外键检查，以便可以按任意顺序删除/创建表
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 用户表
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `grade` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `experience` INT DEFAULT 0,
  `avatar` VARCHAR(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `telephone` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 用户健康表
DROP TABLE IF EXISTS `user_health`;
CREATE TABLE `user_health` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `username` VARCHAR(255) NOT NULL,
  `sex` VARCHAR(255) DEFAULT NULL,
  `height` DOUBLE DEFAULT NULL,
  `weight` DOUBLE DEFAULT NULL,
  `age` INT DEFAULT NULL,
  `exercise_days_per_week` INT DEFAULT NULL,
  `combat_power` INT DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3. 行程表
DROP TABLE IF EXISTS `itinerary`;
CREATE TABLE `itinerary` (
  `id` VARCHAR(36) NOT NULL,
  `user_id` VARCHAR(36) NOT NULL,
  `conversation_id` VARCHAR(36) DEFAULT NULL COMMENT '关联的对话ID',
  `title` VARCHAR(255) DEFAULT NULL,
  `plan` TEXT,
  `stats` TEXT,
  `create_time` DATETIME(6) DEFAULT NULL,
  `update_time` DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 4. 胶囊表
DROP TABLE IF EXISTS `capsule`;
CREATE TABLE `capsule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT DEFAULT NULL,
  `title` VARCHAR(255) DEFAULT NULL,
  `content` TEXT,
  `decoration` VARCHAR(255) DEFAULT NULL,
  `color` VARCHAR(255) DEFAULT NULL,
  `is_public` BIT(1) DEFAULT NULL,
  `capsule_date` VARCHAR(255) DEFAULT NULL,
  `location_name` VARCHAR(255) DEFAULT NULL,
  `address` VARCHAR(255) DEFAULT NULL,
  `latitude` DOUBLE DEFAULT NULL,
  `longitude` DOUBLE DEFAULT NULL,
  `city` VARCHAR(255) DEFAULT NULL,
  `adcode` VARCHAR(255) DEFAULT NULL,
  `country` VARCHAR(255) DEFAULT NULL,
  `likes_count` INT DEFAULT NULL,
  `comments_count` INT DEFAULT NULL,
  `view_count` INT DEFAULT NULL,
  `created_at` DATETIME(6) DEFAULT NULL,
  `updated_at` DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 5. 胶囊媒体表
DROP TABLE IF EXISTS `capsule_media`;
CREATE TABLE `capsule_media` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `capsule_id` BIGINT DEFAULT NULL,
  `url` VARCHAR(255) DEFAULT NULL,
  `file_type` VARCHAR(255) DEFAULT NULL,
  `thumbnail` VARCHAR(255) DEFAULT NULL,
  `width` INT DEFAULT NULL,
  `height` INT DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 6. 社区帖子表
DROP TABLE IF EXISTS `community_post`;
CREATE TABLE `community_post` (
  `id` VARCHAR(255) NOT NULL,
  `summary_id` VARCHAR(255) DEFAULT NULL,
  `title` VARCHAR(255) DEFAULT NULL,
  `content` TEXT,
  `images` TEXT,
  `user_id` INT DEFAULT NULL,
  `is_public` BIT(1) DEFAULT NULL,
  `tags` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME(6) DEFAULT NULL,
  `like_count` INT DEFAULT NULL,
  `comment_count` INT DEFAULT NULL,
  `collect_count` INT DEFAULT NULL,
  `itinerary_id` VARCHAR(36) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 7. 社区评论表
DROP TABLE IF EXISTS `community_comment`;
CREATE TABLE `community_comment` (
  `id` VARCHAR(255) NOT NULL,
  `post_id` VARCHAR(255) DEFAULT NULL,
  `user_id` INT DEFAULT NULL,
  `content` TEXT,
  `parent_comment_id` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME(6) DEFAULT NULL,
  `like_count` INT DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 8. 社区收藏表
DROP TABLE IF EXISTS `community_collect`;
CREATE TABLE `community_collect` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `post_id` VARCHAR(255) DEFAULT NULL,
  `user_id` INT DEFAULT NULL,
  `created_at` DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 9. 社区点赞表
DROP TABLE IF EXISTS `community_like`;
CREATE TABLE `community_like` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `target_id` VARCHAR(255) DEFAULT NULL,
  `user_id` INT DEFAULT NULL,
  `type` ENUM('POST','COMMENT') DEFAULT NULL,
  `created_at` DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 10. 社区事件表
DROP TABLE IF EXISTS `community_event`;
CREATE TABLE `community_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `actor_user_id` INT NOT NULL,
  `receiver_user_id` INT NOT NULL,
  `post_id` VARCHAR(255) DEFAULT NULL,
  `challenge_id` BIGINT DEFAULT NULL,
  `event_type` VARCHAR(64) NOT NULL,
  `event_detail` VARCHAR(512) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_receiver_created` (`receiver_user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 11. 用户关注表
DROP TABLE IF EXISTS `user_follow`;
CREATE TABLE `user_follow` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `follower_id` INT NOT NULL COMMENT '关注者ID',
  `following_id` INT NOT NULL COMMENT '被关注者ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow` (`follower_id`, `following_id`),
  KEY `idx_follower` (`follower_id`),
  KEY `idx_following` (`following_id`),
  CONSTRAINT `fk_follower` FOREIGN KEY (`follower_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_following` FOREIGN KEY (`following_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注关系表';

-- 12. 用户好友表
DROP TABLE IF EXISTS `user_friend`;
CREATE TABLE `user_friend` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `user1_id` INT NOT NULL COMMENT '用户1 ID（较小的ID）',
  `user2_id` INT NOT NULL COMMENT '用户2 ID（较大的ID）',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '成为好友时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_friend` (`user1_id`, `user2_id`),
  KEY `idx_user1` (`user1_id`),
  KEY `idx_user2` (`user2_id`),
  CONSTRAINT `fk_friend_user1` FOREIGN KEY (`user1_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_friend_user2` FOREIGN KEY (`user2_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户好友关系表';

-- 13. 私信表
DROP TABLE IF EXISTS `private_message`;
CREATE TABLE `private_message` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `sender_id` INT NOT NULL COMMENT '发送者ID',
  `receiver_id` INT NOT NULL COMMENT '接收者ID',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `is_read` TINYINT(1) DEFAULT 0 COMMENT '是否已读 0-未读 1-已读',
  `share_type` VARCHAR(20) DEFAULT NULL COMMENT '分享类型 post-帖子 challenge-挑战',
  `share_id` VARCHAR(100) DEFAULT NULL COMMENT '分享内容的ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_sender` (`sender_id`),
  KEY `idx_receiver` (`receiver_id`),
  KEY `idx_created` (`created_at`),
  CONSTRAINT `fk_message_sender` FOREIGN KEY (`sender_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_message_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户私信表';

-- 14. 挑战记录表
DROP TABLE IF EXISTS `challenge_record`;
CREATE TABLE `challenge_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` INT DEFAULT NULL,
  `post_id` VARCHAR(255) DEFAULT NULL,
  `itinerary_id` VARCHAR(36) DEFAULT NULL,
  `proof_images` TEXT,
  `fitness_data` TEXT,
  `duration_minutes` INT DEFAULT NULL,
  `status` INT DEFAULT NULL,
  `created_at` DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 15. 战斗力表
DROP TABLE IF EXISTS `combat_power`;
CREATE TABLE `combat_power` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `current_power` INT DEFAULT NULL,
  `fitness_level` ENUM('HIGH','LOW','MEDIUM') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `active_plan` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `last_location` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `last_status` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `preferences` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` DATETIME(6) DEFAULT NULL,
  `updated_at` DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5bh6ybkcqf3y40ogvut12bfjb` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 16. 实时行程表
DROP TABLE IF EXISTS `real_time_trip`;
CREATE TABLE `real_time_trip` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `trip_id` VARCHAR(32) NOT NULL,
  `user_id` VARCHAR(36) NOT NULL,
  `itinerary_id` VARCHAR(36) DEFAULT NULL,
  `is_completed` BIT(1) NOT NULL DEFAULT 0,
  `last_status` TEXT,
  `version` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME(6) DEFAULT NULL,
  `updated_at` DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_trip_id` (`trip_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 17. 行程报告表
DROP TABLE IF EXISTS `trip_report`;
CREATE TABLE `trip_report` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` INT DEFAULT NULL,
  `trip_id` VARCHAR(255) DEFAULT NULL,
  `report_url` VARCHAR(255) DEFAULT NULL,
  `template_id` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 18. 行程摘要表
DROP TABLE IF EXISTS `trip_summary`;
CREATE TABLE `trip_summary` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `summary_id` VARCHAR(64) NOT NULL,
  `user_id` INT NOT NULL,
  `trip_id` VARCHAR(64) NOT NULL,
  `itinerary_id` VARCHAR(64) DEFAULT NULL,
  `title` VARCHAR(255) NOT NULL,
  `summary_text` TEXT NOT NULL,
  `duration_minutes` INT DEFAULT NULL,
  `total_distance_meters` BIGINT DEFAULT NULL,
  `trajectory_point_count` INT DEFAULT NULL,
  `final_steps` INT DEFAULT NULL,
  `final_current_power` INT DEFAULT NULL,
  `final_total_power` INT DEFAULT NULL,
  `created_at` DATETIME(6) DEFAULT NULL,
  `updated_at` DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trip_summary_summary_id` (`summary_id`),
  UNIQUE KEY `uk_trip_summary_trip_id` (`trip_id`),
  KEY `idx_trip_summary_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 19. 行程轨迹地图表
DROP TABLE IF EXISTS `trip_trajectory_map`;
CREATE TABLE `trip_trajectory_map` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `trip_id` VARCHAR(64) NOT NULL,
  `user_id` VARCHAR(64) NOT NULL,
  `itinerary_id` VARCHAR(64) DEFAULT NULL,
  `trajectory_map_url` TEXT,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKh4q2n8idbakp2mxoc9kthfmug` (`trip_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 20. 行程轨迹点表
DROP TABLE IF EXISTS `trip_trajectory_point`;
CREATE TABLE `trip_trajectory_point` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `trip_id` VARCHAR(64) NOT NULL,
  `user_id` VARCHAR(64) NOT NULL,
  `seq` INT NOT NULL,
  `lng` DOUBLE NOT NULL,
  `lat` DOUBLE NOT NULL,
  `sampled_at` DATETIME(6) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_trip_trajectory_trip_seq` (`trip_id`,`seq`),
  KEY `idx_trip_trajectory_trip_time` (`trip_id`,`sampled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 21. 工具调用历史表
DROP TABLE IF EXISTS `tool_call_history`;
CREATE TABLE `tool_call_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` VARCHAR(36) NOT NULL,
  `tool_name` VARCHAR(100) NOT NULL,
  `display_name` VARCHAR(100) DEFAULT NULL,
  `itinerary_id` VARCHAR(36) DEFAULT NULL,
  `parameters` TEXT,
  `result_summary` VARCHAR(500) DEFAULT NULL,
  `success` BIT(1) NOT NULL DEFAULT 1,
  `call_time` DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 22. 工具定义表
DROP TABLE IF EXISTS `tool_definition`;
CREATE TABLE `tool_definition` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tool_name` VARCHAR(100) NOT NULL,
  `display_name` VARCHAR(100) NOT NULL,
  `description` VARCHAR(500) DEFAULT NULL,
  `category` VARCHAR(50) DEFAULT NULL,
  `parameters_json` TEXT,
  `usage_example` TEXT,
  `enabled` BIT(1) NOT NULL DEFAULT 1,
  `sort_order` INT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_tool_name` (`tool_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 23. 用户工具偏好表
DROP TABLE IF EXISTS `user_tool_preference`;
CREATE TABLE `user_tool_preference` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` VARCHAR(36) NOT NULL,
  `tool_name` VARCHAR(100) NOT NULL,
  `enabled` BIT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_tool` (`user_id`, `tool_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 24. AI Agent 对话表
DROP TABLE IF EXISTS `agent_conversation`;
CREATE TABLE `agent_conversation` (
  `id` VARCHAR(36) NOT NULL COMMENT '对话ID (UUID)',
  `user_id` VARCHAR(36) NOT NULL COMMENT '用户ID',
  `title` VARCHAR(255) DEFAULT '新对话' COMMENT '对话标题',
  `tags` VARCHAR(500) DEFAULT NULL COMMENT '标签 (逗号分隔)',
  `description` TEXT DEFAULT NULL COMMENT '对话描述',
  `itinerary_id` VARCHAR(36) DEFAULT NULL COMMENT '关联行程ID',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent 对话表';

-- 25. AI Agent 对话消息表
DROP TABLE IF EXISTS `agent_message`;
CREATE TABLE `agent_message` (
  `id` VARCHAR(36) NOT NULL COMMENT '消息ID (UUID)',
  `conversation_id` VARCHAR(36) NOT NULL COMMENT '对话ID',
  `role` VARCHAR(20) NOT NULL COMMENT '角色: user / assistant / system / tool',
  `content` MEDIUMTEXT DEFAULT NULL COMMENT '消息内容',
  `tool_calls` TEXT DEFAULT NULL COMMENT '工具调用记录 (JSON)',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_conversation_id` (`conversation_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent 对话消息表';

-- 重新启用外键检查
SET FOREIGN_KEY_CHECKS = 1;