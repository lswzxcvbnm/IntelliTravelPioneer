-- MySQL dump 10.13  Distrib 8.0.41, for Win64 (x86_64)
--
-- Host: localhost    Database: intellitravel
-- ------------------------------------------------------
-- Server version	8.0.41

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `capsule`
--

DROP TABLE IF EXISTS `capsule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `capsule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address` varchar(255) DEFAULT NULL,
  `capsule_date` varchar(255) DEFAULT NULL,
  `city` varchar(255) DEFAULT NULL,
  `comments_count` int DEFAULT NULL,
  `content` text,
  `country` varchar(255) DEFAULT NULL,
  `adcode` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `is_public` bit(1) DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `likes_count` int DEFAULT NULL,
  `location_name` varchar(255) DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `decoration` varchar(255) DEFAULT NULL,
  `color` varchar(255) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `view_count` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `capsule`
--

LOCK TABLES `capsule` WRITE;
/*!40000 ALTER TABLE `capsule` DISABLE KEYS */;
INSERT INTO `capsule` VALUES (1,'北京市东城区景山前街4号','2026-03-03','北京市',0,'今天天气真好，红墙黄瓦配上蓝天，绝美！这是一个值得铭记的时刻。','中国','2026-03-06 03:08:41.965962',_binary '',39.916345,0,'故宫博物院',116.397155,'开心','春季','故宫的春日记忆','2026-03-06 03:08:41.965962',1,0,'晴'),(2,'北京市东城区景山前街4号','2026-03-03','北京市',0,'今天天气真好，红墙黄瓦配上蓝天，绝美！这是一个值得铭记的时刻。','中国','2026-03-06 03:08:53.981318',_binary '',39.916345,0,'故宫博物院',116.397155,'开心','春季','故宫的春日记忆','2026-03-06 03:08:53.981318',1,0,'晴');
/*!40000 ALTER TABLE `capsule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `capsule_media`
--

DROP TABLE IF EXISTS `capsule_media`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `capsule_media` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `capsule_id` bigint DEFAULT NULL,
  `file_type` varchar(255) DEFAULT NULL,
  `height` int DEFAULT NULL,
  `thumbnail` varchar(255) DEFAULT NULL,
  `url` varchar(255) DEFAULT NULL,
  `width` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `capsule_media`
--

LOCK TABLES `capsule_media` WRITE;
/*!40000 ALTER TABLE `capsule_media` DISABLE KEYS */;
INSERT INTO `capsule_media` VALUES (1,1,'image',1080,'https://your-oss-bucket.oss-cn-beijing.aliyuncs.com/test-capsule/gugong_main.jpg?x-oss-process=image/resize,m_fill,w_300,h_300','https://your-oss-bucket.oss-cn-beijing.aliyuncs.com/test-capsule/gugong_main.jpg',1920),(2,1,'video',NULL,NULL,'https://your-oss-bucket.oss-cn-beijing.aliyuncs.com/test-capsule/gugong_corner.mp4',NULL),(3,2,'image',1080,'https://your-oss-bucket.oss-cn-beijing.aliyuncs.com/test-capsule/gugong_main.jpg?x-oss-process=image/resize,m_fill,w_300,h_300','https://your-oss-bucket.oss-cn-beijing.aliyuncs.com/test-capsule/gugong_main.jpg',1920),(4,2,'video',NULL,NULL,'https://your-oss-bucket.oss-cn-beijing.aliyuncs.com/test-capsule/gugong_corner.mp4',NULL);
/*!40000 ALTER TABLE `capsule_media` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `challenge_record`
--

DROP TABLE IF EXISTS `challenge_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `challenge_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `fitness_data` text,
  `duration_minutes` int DEFAULT NULL,
  `itinerary_id` varchar(36) DEFAULT NULL,
  `post_id` varchar(255) DEFAULT NULL,
  `proof_images` text,
  `status` int DEFAULT NULL,
  `user_id` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `challenge_record`
--

LOCK TABLES `challenge_record` WRITE;
/*!40000 ALTER TABLE `challenge_record` DISABLE KEYS */;
INSERT INTO `challenge_record` VALUES (1,'2026-03-05 03:18:24.718029','100',NULL,'f0254d8e-f526-4f15-9bec-a5c36777acd8','[\"http:/1\"]',1,1);
/*!40000 ALTER TABLE `challenge_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `combat_power`
--

DROP TABLE IF EXISTS `combat_power`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `combat_power` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active_plan` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `current_power` int DEFAULT NULL,
  `fitness_level` enum('HIGH','LOW','MEDIUM') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_location` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `last_status` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `preferences` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5bh6ybkcqf3y40ogvut12bfjb` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `combat_power`
--

LOCK TABLES `combat_power` WRITE;
/*!40000 ALTER TABLE `combat_power` DISABLE KEYS */;
/*!40000 ALTER TABLE `combat_power` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `community_collect`
--

DROP TABLE IF EXISTS `community_collect`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `community_collect` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `post_id` varchar(255) DEFAULT NULL,
  `user_id` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `community_collect`
--

LOCK TABLES `community_collect` WRITE;
/*!40000 ALTER TABLE `community_collect` DISABLE KEYS */;
INSERT INTO `community_collect` VALUES (27,'2026-03-08 08:14:58.273715','de7be8b9-7dd5-44b4-acf9-4fa8fe609703',2),(28,'2026-03-09 07:32:57.213414','de7be8b9-7dd5-44b4-acf9-4fa8fe609703',1);
/*!40000 ALTER TABLE `community_collect` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `community_comment`
--

DROP TABLE IF EXISTS `community_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `community_comment` (
  `id` varchar(255) NOT NULL,
  `content` text,
  `created_at` datetime(6) DEFAULT NULL,
  `like_count` int DEFAULT NULL,
  `parent_comment_id` varchar(255) DEFAULT NULL,
  `post_id` varchar(255) DEFAULT NULL,
  `user_id` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `community_comment`
--

LOCK TABLES `community_comment` WRITE;
/*!40000 ALTER TABLE `community_comment` DISABLE KEYS */;
INSERT INTO `community_comment` VALUES ('12ad7761-bfc5-4504-a37b-e5d52872cd7f','我也喜欢苏州','2026-03-08 07:10:54.287012',1,NULL,'de7be8b9-7dd5-44b4-acf9-4fa8fe609703',2),('e2156ffc-3d77-480a-8c87-13c4e6849b4a','我喜欢你','2026-03-08 07:41:21.373341',1,'cdb3e677-182a-4267-b58e-6d3df9f79ea8','de7be8b9-7dd5-44b4-acf9-4fa8fe609703',2);
/*!40000 ALTER TABLE `community_comment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `community_event`
--

DROP TABLE IF EXISTS `community_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `community_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor_user_id` int NOT NULL,
  `receiver_user_id` int NOT NULL,
  `post_id` varchar(255) DEFAULT NULL,
  `challenge_id` bigint DEFAULT NULL,
  `event_type` varchar(64) NOT NULL,
  `event_detail` varchar(512) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_receiver_created` (`receiver_user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `community_event`
--

LOCK TABLES `community_event` WRITE;
/*!40000 ALTER TABLE `community_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `community_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `community_like`
--

DROP TABLE IF EXISTS `community_like`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `community_like` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `target_id` varchar(255) DEFAULT NULL,
  `type` enum('COMMENT','POST') DEFAULT NULL,
  `user_id` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `community_like`
--

LOCK TABLES `community_like` WRITE;
/*!40000 ALTER TABLE `community_like` DISABLE KEYS */;
INSERT INTO `community_like` VALUES (23,'2026-03-08 08:05:48.739002','e2156ffc-3d77-480a-8c87-13c4e6849b4a','COMMENT',2),(24,'2026-03-08 08:09:35.127894','de7be8b9-7dd5-44b4-acf9-4fa8fe609703','POST',2),(25,'2026-03-09 08:19:25.340086','12ad7761-bfc5-4504-a37b-e5d52872cd7f','COMMENT',2);
/*!40000 ALTER TABLE `community_like` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `community_post`
--

DROP TABLE IF EXISTS `community_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `community_post` (
  `id` varchar(255) NOT NULL,
  `collect_count` int DEFAULT NULL,
  `comment_count` int DEFAULT NULL,
  `content` text,
  `created_at` datetime(6) DEFAULT NULL,
  `images` text,
  `is_public` bit(1) DEFAULT NULL,
  `like_count` int DEFAULT NULL,
  `summary_id` varchar(255) DEFAULT NULL,
  `tags` varchar(255) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `user_id` int DEFAULT NULL,
  `itinerary_id` varchar(36) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `community_post`
--

LOCK TABLES `community_post` WRITE;
/*!40000 ALTER TABLE `community_post` DISABLE KEYS */;
INSERT INTO `community_post` VALUES ('de7be8b9-7dd5-44b4-acf9-4fa8fe609703',2,2,'苏州是个美丽的城市','2026-03-08 07:10:44.468883','[\"http://bluewhale-lsw.oss-cn-shanghai.aliyuncs.com/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202024-07-23%20234510.png\"]',_binary '',1,'','苏州','苏州真美丽',2,NULL);
/*!40000 ALTER TABLE `community_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `itinerary`
--

DROP TABLE IF EXISTS `itinerary`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `itinerary` (
  `id` varchar(36) NOT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `plan` text,
  `title` varchar(255) DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL,
  `user_id` varchar(36) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `itinerary`
--

LOCK TABLES `itinerary` WRITE;
/*!40000 ALTER TABLE `itinerary` DISABLE KEYS */;
INSERT INTO `itinerary` VALUES ('e07a3f54-0a87-4afd-9804-053f8b965158','2026-03-09 14:11:37.315136','{\"title\":\"苏州三日城市漫步之旅\",\"estimatedCost\":1200.0,\"totalDays\":3,\"dailyPlans\":[{\"dayNumber\":1,\"items\":[{\"type\":\"景点\",\"name\":\"平江路\",\"description\":\"平江路是苏州保存最完好的古街之一，沿河而建，充满江南水乡的韵味。漫步其中，可以欣赏到白墙黛瓦的民居、古桥流水，感受苏州的历史文化氛围。\"},{\"type\":\"景点\",\"name\":\"苏帮菜馆\",\"description\":\"推荐品尝地道的苏帮菜，如松鼠桂鱼、碧螺虾仁等。人均消费约80元，预计耗时1小时。\"}]},{\"dayNumber\":2,\"items\":[{\"type\":\"景点\",\"name\":\"山塘街\",\"description\":\"山塘街是苏州另一条著名的古街，以古桥、古巷和古戏台闻名。这里不仅有传统建筑，还有许多特色小店和茶馆，适合悠闲地漫步。\"},{\"type\":\"景点\",\"name\":\"山塘小吃街\",\"description\":\"可以尝试各种苏州特色小吃，如生煎包、糖粥、桂花糕等。人均消费约60元，预计耗时1.5小时。\"}]},{\"dayNumber\":3,\"items\":[{\"type\":\"景点\",\"name\":\"留园\",\"description\":\"留园是中国四大名园之一，以古典园林建筑和精致的园林景观著称。适合深入感受苏州园林的美学与文化。\"},{\"type\":\"景点\",\"name\":\"观前街美食\",\"description\":\"在观前街可以品尝到各种苏州传统小吃和特色美食，如蟹粉小笼包、鸡头米等。人均消费约70元，预计耗时1小时。\"},{\"type\":\"购物\",\"name\":\"观前街购物\",\"description\":\"观前街是苏州著名的商业街，有许多传统手工艺品店、丝绸店和特色小吃店，适合购买纪念品和特产。预计耗时2小时。\"}]}]}','苏州三日城市漫步之旅','2026-03-09 14:11:47.878614','4');
/*!40000 ALTER TABLE `itinerary` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `real_time_trip`
--

DROP TABLE IF EXISTS `real_time_trip`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `real_time_trip` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `trip_id` varchar(32) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `is_completed` bit(1) NOT NULL,
  `last_status` text,
  `updated_at` datetime(6) DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `user_id` int NOT NULL,
  `itinerary_id` varchar(36) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_trip_id` (`trip_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `real_time_trip`
--

LOCK TABLES `real_time_trip` WRITE;
/*!40000 ALTER TABLE `real_time_trip` DISABLE KEYS */;
INSERT INTO `real_time_trip` VALUES (1,'f696015b54474d949f13f87821d1a2a7','2026-03-09 14:11:40.639034',b'1','{\"timestamp\":1773065500.270000000,\"steps\":0,\"location\":{\"lat\":32.054554,\"lng\":118.776886,\"name\":\"当前位置\"},\"current_power\":1000,\"extInfo\":\"trip initialized\",\"trip_id\":\"f696015b54474d949f13f87821d1a2a7\",\"heart_rate\":90,\"power_cost\":0,\"total_power\":1000,\"power_detail\":null}','2026-03-09 14:11:50.789865',0,'4',NULL);
/*!40000 ALTER TABLE `real_time_trip` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trip_report`
--

DROP TABLE IF EXISTS `trip_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trip_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `report_image_url` varchar(255) DEFAULT NULL,
  `template_id` varchar(255) DEFAULT NULL,
  `trip_id` varchar(255) DEFAULT NULL,
  `user_id` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trip_report`
--

LOCK TABLES `trip_report` WRITE;
/*!40000 ALTER TABLE `trip_report` DISABLE KEYS */;
/*!40000 ALTER TABLE `trip_report` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trip_summary`
--

DROP TABLE IF EXISTS `trip_summary`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trip_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `summary_id` varchar(64) NOT NULL,
  `user_id` int NOT NULL,
  `trip_id` varchar(64) NOT NULL,
  `itinerary_id` varchar(64) DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `summary_text` text NOT NULL,
  `duration_minutes` int DEFAULT NULL,
  `total_distance_meters` bigint DEFAULT NULL,
  `trajectory_point_count` int DEFAULT NULL,
  `final_steps` int DEFAULT NULL,
  `final_current_power` int DEFAULT NULL,
  `final_total_power` int DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trip_summary_summary_id` (`summary_id`),
  UNIQUE KEY `uk_trip_summary_trip_id` (`trip_id`),
  KEY `idx_trip_summary_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trip_summary`
--

LOCK TABLES `trip_summary` WRITE;
/*!40000 ALTER TABLE `trip_summary` DISABLE KEYS */;
/*!40000 ALTER TABLE `trip_summary` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trip_trajectory_map`
--

DROP TABLE IF EXISTS `trip_trajectory_map`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trip_trajectory_map` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `itinerary_id` varchar(64) DEFAULT NULL,
  `trajectory_map_url` text,
  `trip_id` varchar(64) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKh4q2n8idbakp2mxoc9kthfmug` (`trip_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trip_trajectory_map`
--

LOCK TABLES `trip_trajectory_map` WRITE;
/*!40000 ALTER TABLE `trip_trajectory_map` DISABLE KEYS */;
INSERT INTO `trip_trajectory_map` VALUES (1,'2026-03-09 14:11:40.625078',NULL,NULL,'f696015b54474d949f13f87821d1a2a7','2026-03-09 14:11:40.625078','4');
/*!40000 ALTER TABLE `trip_trajectory_map` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trip_trajectory_point`
--

DROP TABLE IF EXISTS `trip_trajectory_point`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trip_trajectory_point` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `lat` double NOT NULL,
  `lng` double NOT NULL,
  `sampled_at` datetime(6) NOT NULL,
  `seq` int NOT NULL,
  `trip_id` varchar(64) NOT NULL,
  `user_id` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_trip_trajectory_trip_seq` (`trip_id`,`seq`),
  KEY `idx_trip_trajectory_trip_time` (`trip_id`,`sampled_at`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trip_trajectory_point`
--

LOCK TABLES `trip_trajectory_point` WRITE;
/*!40000 ALTER TABLE `trip_trajectory_point` DISABLE KEYS */;
INSERT INTO `trip_trajectory_point` VALUES (1,'2026-03-09 14:11:40.605089',32.054554,118.776886,'2026-03-09 14:11:40.270000',0,'f696015b54474d949f13f87821d1a2a7','4');
/*!40000 ALTER TABLE `trip_trajectory_point` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` int NOT NULL AUTO_INCREMENT,
  `avatar` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `grade` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `experience` int DEFAULT 0,
  `location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `telephone` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` VALUES (1,NULL,NULL,NULL,0,NULL,NULL,'123456',NULL,'yorker'),(2,'http://bluewhale-lsw.oss-cn-shanghai.aliyuncs.com/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-02-19%20225623.png','2969728293@qq.com',NULL,0,'苏州',NULL,'ycz123456','15051229949','ycz'),(3,NULL,NULL,NULL,0,NULL,NULL,'123456asd',NULL,'hhh'),(4,NULL,NULL,NULL,0,NULL,NULL,'123456',NULL,'baobiwang');
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_health`
--

DROP TABLE IF EXISTS `user_health`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_health` (
  `id` int NOT NULL AUTO_INCREMENT,
  `age` int DEFAULT NULL,
  `combat_power` int DEFAULT NULL,
  `exercise_days_per_week` int DEFAULT NULL,
  `height` double DEFAULT NULL,
  `sex` varchar(255) DEFAULT NULL,
  `user_id` int NOT NULL,
  `username` varchar(255) NOT NULL,
  `weight` double DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_health`
--

LOCK TABLES `user_health` WRITE;
/*!40000 ALTER TABLE `user_health` DISABLE KEYS */;
INSERT INTO `user_health` VALUES (1,NULL,1000,NULL,NULL,NULL,4,'baobiwang',NULL);
/*!40000 ALTER TABLE `user_health` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'intellitravel'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-03-10 11:28:54
