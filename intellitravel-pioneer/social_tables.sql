-- 社交功能相关表

-- 关注关系表
CREATE TABLE IF NOT EXISTS `user_follow` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `follower_id` INT NOT NULL COMMENT '关注者ID',
  `following_id` INT NOT NULL COMMENT '被关注者ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  UNIQUE KEY `uk_follow` (`follower_id`, `following_id`),
  KEY `idx_follower` (`follower_id`),
  KEY `idx_following` (`following_id`),
  CONSTRAINT `fk_follower` FOREIGN KEY (`follower_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_following` FOREIGN KEY (`following_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注关系表';

-- 好友关系表
CREATE TABLE IF NOT EXISTS `user_friend` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `user1_id` INT NOT NULL COMMENT '用户1 ID（较小的ID）',
  `user2_id` INT NOT NULL COMMENT '用户2 ID（较大的ID）',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '成为好友时间',
  UNIQUE KEY `uk_friend` (`user1_id`, `user2_id`),
  KEY `idx_user1` (`user1_id`),
  KEY `idx_user2` (`user2_id`),
  CONSTRAINT `fk_friend_user1` FOREIGN KEY (`user1_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_friend_user2` FOREIGN KEY (`user2_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户好友关系表';

-- 私信表
CREATE TABLE IF NOT EXISTS `private_message` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `sender_id` INT NOT NULL COMMENT '发送者ID',
  `receiver_id` INT NOT NULL COMMENT '接收者ID',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `is_read` TINYINT(1) DEFAULT 0 COMMENT '是否已读 0-未读 1-已读',
  `share_type` VARCHAR(20) DEFAULT NULL COMMENT '分享类型 post-帖子 challenge-挑战',
  `share_id` VARCHAR(100) DEFAULT NULL COMMENT '分享内容的ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  KEY `idx_sender` (`sender_id`),
  KEY `idx_receiver` (`receiver_id`),
  KEY `idx_created` (`created_at`),
  CONSTRAINT `fk_message_sender` FOREIGN KEY (`sender_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_message_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户私信表';
