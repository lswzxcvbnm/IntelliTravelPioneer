# IntelliTravel Pioneer — 云端部署指南

> **适用版本**: v1.0  
> **撰写日期**: 2026-06-14  
> **适用平台**: 阿里云 / 腾讯云 / 华为云 / 任意 VPS（CentOS 7+ / Ubuntu 20.04+）

---

## 目录

1. [部署前准备](#一部署前准备)
2. [方案 A：传统手动部署（推荐新手）](#二方案-a传统手动部署)
3. [方案 B：Docker 容器化部署（推荐生产环境）](#三方案-bdocker-容器化部署)
4. [HTTPS 配置（SSL 证书）](#四https-配置ssl-证书)
5. [CI/CD 自动化部署（可选）](#五cicd-自动化部署可选)
6. [常见问题排查](#六常见问题排查)
7. [附录：各云平台操作速查](#七附录各云平台操作速查)

---

## 一、部署前准备

### 1.1 你需要准备的东西

| 资源 | 说明 | 预估费用 |
|------|------|---------|
| ☁️ 云服务器 | 2 核 4G 以上（推荐 4 核 8G），安装 CentOS 7 / Ubuntu 20.04+ | ¥50-200/月 |
| 🌐 域名（可选） | 已备案域名，配置 DNS 解析到服务器 IP | ¥30-80/年 |
| 🗄️ MySQL 数据库 | 可自建或使用云数据库（RDS） | 自建免费 / 云 RDS ¥50+/月 |
| 📦 Java 21 | 运行后端 | 免费 |
| 📦 Node.js 20+ | 构建前端 | 免费 |
| 📦 Nginx | 反向代理 + 静态文件服务 | 免费 |

### 1.2 项目关键信息速查

| 项 | 值 |
|----|-----|
| 后端端口 | `8080` |
| 前端构建产物 | `planclient/dist/` |
| 后端 JAR | `intellitravel-pioneer/target/intellitravel-pioneer-0.0.1-SNAPSHOT.jar` |
| Java 版本 | **21** |
| 数据库 | MySQL 8.0+ |
| JPA DDL | `update`（自动建表） |

### 1.3 需要配置的环境变量

```
DB_URL          = jdbc:mysql://<数据库地址>:3306/intellitravel?...   # 数据库连接
DB_USERNAME     = root                                                # 数据库用户
DB_PASSWORD     = <你的密码>                                          # 数据库密码
DEEPSEEK_API_KEY= sk-xxxxxxxx                                        # DeepSeek AI
AMAP_MCP_KEY    = xxxxxxxxxx                                         # 高德 MCP
AMAP_KEY        = xxxxxxxxxx                                         # 高德 API
OSS_ACCESS_KEY_ID     = xxxxxxxxxx                                   # 阿里云 OSS
OSS_ACCESS_KEY_SECRET = xxxxxxxxxx                                   # 阿里云 OSS
SILICONFLOW_API_KEY   = sk-xxxxxxxx                                  # AI 图片生成（可选）
```

---

## 二、方案 A：传统手动部署

> 适用场景：单台服务器，简单直接，适合小型项目或个人使用。

### 架构图

```
用户浏览器
    │
    ▼
┌──────────┐     ┌──────────────────┐     ┌──────────────┐
│  Nginx   │────▶│  planclient/dist │     │              │
│  :80/443 │     │  (静态文件)       │     │              │
│          │     └──────────────────┘     │              │
│          │     /api/* 代理              │              │
│          │─────────────────────────────▶│ Spring Boot  │
│          │                              │    :8080     │
│          │                              │              │
└──────────┘                              └──────┬───────┘
                                                 │
                                                 ▼
                                          ┌──────────────┐
                                          │    MySQL     │
                                          │   :3306      │
                                          └──────────────┘
```

### 第一步：购买并登录云服务器

以**阿里云 ECS** 为例（腾讯云/华为云操作类似）：

1. 登录阿里云控制台 → 云服务器 ECS → 创建实例
2. 配置：
   - **镜像**: Ubuntu 22.04 或 CentOS 7.9
   - **规格**: 2 vCPU / 4 GiB 起步
   - **系统盘**: 40 GB
   - **网络**: 分配公网 IP
3. 安全组规则 — 添加入方向规则：

| 端口 | 协议 | 来源 | 用途 |
|------|------|------|------|
| 22 | TCP | 0.0.0.0/0 | SSH 登录 |
| 80 | TCP | 0.0.0.0/0 | HTTP |
| 443 | TCP | 0.0.0.0/0 | HTTPS |
| 8080 | TCP | 127.0.0.1/32 | 后端（仅本地访问） |
| 3306 | TCP | 127.0.0.1/32 | MySQL（仅本地访问） |

> ⚠️ 8080 和 3306 不要对公网开放！只允许本机 `127.0.0.1` 访问。

4. SSH 登录服务器：

```bash
ssh root@<你的服务器公网IP>
```

### 第二步：安装基础环境

#### 安装 Java 21

```bash
# Ubuntu 22.04
sudo apt update
sudo apt install -y openjdk-21-jdk

# CentOS 7 / Rocky Linux
sudo yum install -y java-21-openjdk-devel

# 验证
java -version
# 应输出: openjdk version "21.0.x" ...
```

#### 安装 Node.js 20（仅构建时需要）

```bash
# 使用 NodeSource 仓库
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs

# 验证
node -v   # v20.x.x
npm -v    # 10.x.x
```

#### 安装 MySQL 8.0

```bash
# Ubuntu 22.04
sudo apt install -y mysql-server

# 启动并设置开机自启
sudo systemctl start mysql
sudo systemctl enable mysql

# 安全初始化
sudo mysql_secure_installation
# 按提示设置 root 密码，移除匿名用户，禁止远程 root 登录等
```

创建数据库和用户：

```sql
sudo mysql -u root -p

CREATE DATABASE IF NOT EXISTS intellitravel CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- 如果用户已存在则先删除（避免 ERROR 1396）
DROP USER IF EXISTS 'traveluser'@'localhost';
CREATE USER 'traveluser'@'localhost' IDENTIFIED BY '你的密码';
GRANT ALL PRIVILEGES ON intellitravel.* TO 'traveluser'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

#### 安装 Nginx

```bash
sudo apt install -y nginx

# 启动并设置开机自启
sudo systemctl start nginx
sudo systemctl enable nginx

# 验证：浏览器访问 http://<服务器IP> ，应看到 Nginx 欢迎页
```

### 第三步：修复前端 API 地址并构建

在**本地开发机**上操作：

#### 3.1 修改 API 基础地址

编辑 [planclient/src/config/api.ts](planclient/src/config/api.ts)：

```typescript
// 修改前
export const API_BASE_URL = 'http://localhost:8080'

// 修改后（使用域名）
export const API_BASE_URL = 'https://你的域名.com'
// 或使用 IP（开发测试）
export const API_BASE_URL = 'http://120.55.248.246'
```

> 💡 **更好的做法**：使用环境变量，这样不用每次改代码。详见 [附录 A](#附录-a使用环境变量管理-api-地址)。

#### 3.2 构建前端

```bash
cd planclient/
npm install
npm run build
# 产出在 planclient/dist/ 目录
```

### 第四步：上传文件到服务器

```bash
# 在本地开发机上执行

# 先在服务器上创建目标目录
ssh root@<服务器IP> "mkdir -p /var/www/intellitravel /opt/intellitravel"

# 上传前端 dist 目录
scp -r planclient/dist/ root@<服务器IP>:/var/www/intellitravel/

# 上传后端 JAR（先构建）
cd intellitravel-pioneer/
./mvnw clean package -DskipTests
scp target/intellitravel-pioneer-0.0.1-SNAPSHOT.jar root@<服务器IP>:/opt/intellitravel/
```

### 第五步：配置 Nginx

在服务器上创建 Nginx 配置：

```bash
sudo vim /etc/nginx/sites-available/intellitravel
```

写入以下内容：

```nginx
server {
    listen 80;
    server_name 你的域名.com;   # 或服务器 IP

    # 前端静态文件
    root /var/www/intellitravel/dist;
    index index.html;

    # 静态资源缓存
    location /assets/ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # API 反向代理到后端
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # SSE 流式端点超时设置（重要！）
        proxy_read_timeout 600s;
        proxy_send_timeout 600s;
        proxy_buffering off;
        proxy_cache off;
        chunked_transfer_encoding on;
    }

    # Vue Router History 模式回退
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 安全头
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
}
```

启用配置：

```bash
sudo ln -sf /etc/nginx/sites-available/intellitravel /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default   # 删除默认站点
sudo nginx -t                                  # 测试配置
sudo systemctl reload nginx                    # 重载
```

### 第六步：启动后端服务

#### 6.1 创建 systemd 服务（推荐）

```bash
sudo vim /etc/systemd/system/intellitravel.service
```

写入：

```ini
[Unit]
Description=IntelliTravel Pioneer Backend
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/intellitravel

Environment="DB_URL=jdbc:mysql://localhost:3306/intellitravel?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true"
Environment="DB_USERNAME=traveluser"
Environment="DB_PASSWORD=你的数据库密码"
Environment="DEEPSEEK_API_KEY=sk-xxxxxxxx"
Environment="AMAP_MCP_KEY=xxxxxxxxxx"
Environment="AMAP_KEY=xxxxxxxxxx"
Environment="OSS_ACCESS_KEY_ID=xxxxxxxxxx"
Environment="OSS_ACCESS_KEY_SECRET=xxxxxxxxxx"
Environment="SILICONFLOW_API_KEY=sk-xxxxxxxx"

ExecStart=/usr/bin/java -jar -Xms512m -Xmx2g /opt/intellitravel/intellitravel-pioneer-0.0.1-SNAPSHOT.jar
ExecStop=/bin/kill -15 $MAINPID
Restart=on-failure
RestartSec=10

# 日志
StandardOutput=journal
StandardError=journal
SyslogIdentifier=intellitravel

[Install]
WantedBy=multi-user.target
```

启动：

```bash
sudo systemctl daemon-reload
sudo systemctl start intellitravel
sudo systemctl enable intellitravel     # 开机自启
sudo systemctl status intellitravel     # 检查状态
```

#### 6.2 查看日志

```bash
# 实时日志
sudo journalctl -u intellitravel -f

# 最近 100 行
sudo journalctl -u intellitravel -n 100
```

### 第七步：验证部署

1. 浏览器访问 `http://你的域名.com` 或 `http://<服务器IP>`
2. 应看到前端页面
3. 测试一个功能（如注册/登录），确认 API 连通
4. 检查后端日志确认无报错

---

## 三、方案 B：Docker 容器化部署

> 适用场景：多容器编排、快速扩缩容、标准化的生产环境。

### 架构图

```
docker-compose.yml
┌──────────────────────────────────────────────────┐
│                                                  │
│  ┌─────────┐   ┌──────────┐   ┌──────────────┐  │
│  │  nginx  │   │ backend  │   │    mysql     │  │
│  │  :80    │──▶│  :8080   │──▶│   :3306      │  │
│  │  :443   │   │          │   │              │  │
│  └─────────┘   └──────────┘   └──────────────┘  │
│                                                  │
│      数据卷: mysql-data, uploads                  │
└──────────────────────────────────────────────────┘
```

### 第一步：项目根目录准备

在项目根目录 `f:\codes\26SE3\agent-project\` 下创建以下文件：

#### 1.1 后端 Dockerfile

创建 `Dockerfile.backend`：

```dockerfile
# 构建阶段：仅用于编译
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY intellitravel-pioneer/pom.xml .
COPY intellitravel-pioneer/src ./src
RUN mvn clean package -DskipTests -B -q

# 运行阶段：仅 JRE
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "-Xms512m", "-Xmx2g", "app.jar"]
```

#### 1.2 前端 Dockerfile

创建 `Dockerfile.frontend`：

```dockerfile
# 构建阶段
FROM node:20-alpine AS build
WORKDIR /app
COPY planclient/package*.json ./
RUN npm ci
COPY planclient/ .

# **** 构建前替换 API 地址（通过 ARG 传入）****
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# 运行阶段
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

#### 1.3 Nginx 配置（给 Docker 用）

创建 `nginx.conf`：

```nginx
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    location /assets/ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    location /api/ {
        # Docker 内部通过服务名访问
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 600s;
        proxy_send_timeout 600s;
        proxy_buffering off;
        proxy_cache off;
        chunked_transfer_encoding on;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

#### 1.4 前端 Vite 配置 — 支持构建时环境变量

编辑 [planclient/vite.config.ts](planclient/vite.config.ts)，确认有 `define`：

```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  // 将环境变量注入到客户端代码
  define: {
    __API_BASE_URL__: JSON.stringify(process.env.VITE_API_BASE_URL || 'http://localhost:8080')
  },
  // ... 其他配置保持不变
})
```

然后修改 [planclient/src/config/api.ts](planclient/src/config/api.ts)：

```typescript
// 使用构建时注入的变量，开发时 fallback 到 localhost
declare const __API_BASE_URL__: string
export const API_BASE_URL = __API_BASE_URL__
```

#### 1.5 docker-compose.yml

创建 `docker-compose.yml`：

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: intellitravel-mysql
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD:-RootPass123!}
      MYSQL_DATABASE: intellitravel
      MYSQL_USER: traveluser
      MYSQL_PASSWORD: ${DB_PASSWORD:-TravelPass123!}
    ports:
      - "127.0.0.1:3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build:
      context: .
      dockerfile: Dockerfile.backend
    container_name: intellitravel-backend
    restart: always
    ports:
      - "127.0.0.1:8080:8080"
    environment:
      DB_URL: jdbc:mysql://mysql:3306/intellitravel?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true
      DB_USERNAME: traveluser
      DB_PASSWORD: ${DB_PASSWORD:-TravelPass123!}
      DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY}
      AMAP_MCP_KEY: ${AMAP_MCP_KEY}
      AMAP_KEY: ${AMAP_KEY}
      OSS_ACCESS_KEY_ID: ${OSS_ACCESS_KEY_ID}
      OSS_ACCESS_KEY_SECRET: ${OSS_ACCESS_KEY_SECRET}
      SILICONFLOW_API_KEY: ${SILICONFLOW_API_KEY:-}
    depends_on:
      mysql:
        condition: service_healthy
    volumes:
      - uploads:/app/uploads

  nginx:
    build:
      context: .
      dockerfile: Dockerfile.frontend
      args:
        VITE_API_BASE_URL: ${VITE_API_BASE_URL:-http://你的域名.com}
    container_name: intellitravel-nginx
    restart: always
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      - backend
    volumes:
      - ./ssl:/etc/nginx/ssl:ro   # SSL 证书目录（可选）

volumes:
  mysql-data:
  uploads:
```

#### 1.6 环境变量文件

创建 `.env`（**不要提交到 Git！**）：

```bash
# 数据库
DB_ROOT_PASSWORD=你的Root密码
DB_PASSWORD=你的用户密码

# LLM / AI
DEEPSEEK_API_KEY=sk-xxxxxxxx

# 高德地图
AMAP_MCP_KEY=xxxxxxxxxx
AMAP_KEY=xxxxxxxxxx

# 阿里云 OSS
OSS_ACCESS_KEY_ID=xxxxxxxxxx
OSS_ACCESS_KEY_SECRET=xxxxxxxxxx

# AI 图片生成
SILICONFLOW_API_KEY=sk-xxxxxxxx

# 前端 API 地址
VITE_API_BASE_URL=https://你的域名.com
```

添加 `.env` 到 `.gitignore`：

```bash
echo ".env" >> .gitignore
```

### 第二步：在服务器上部署

```bash
# SSH 登录服务器
ssh root@<服务器IP>

# 安装 Docker
curl -fsSL https://get.docker.com | sudo bash
sudo usermod -aG docker $USER

# 安装 Docker Compose
sudo apt install -y docker-compose-plugin   # Ubuntu
# 或
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 退出重新登录使 docker 组生效
exit
ssh root@<服务器IP>
```

### 第三步：上传并启动

```bash
# 在本地开发机上，上传项目到服务器
# 方式 1：git clone（推荐）
ssh root@<服务器IP> "git clone <你的仓库地址> /opt/intellitravel"

# 方式 2：rsync（没有 git 时）
rsync -avz --exclude 'node_modules' --exclude 'target' --exclude '.git' \
  f:/codes/26SE3/agent-project/ root@<服务器IP>:/opt/intellitravel/

# 在服务器上
cd /opt/intellitravel

# 创建 .env 文件（填入你的真实密钥）
vim .env

# 构建并启动
docker compose up -d --build

# 查看状态
docker compose ps

# 查看日志
docker compose logs -f
```

### 第四步：验证

```bash
# 检查容器运行状态
docker compose ps
# 应该看到 3 个容器都是 Up 状态

# 测试 API
curl http://localhost/api/user/health

# 浏览器访问
# http://<服务器IP>
```

---

## 四、HTTPS 配置（SSL 证书）

### 4.1 使用 Let's Encrypt 免费证书

```bash
# 安装 certbot
sudo apt install -y certbot python3-certbot-nginx

# 获取证书
sudo certbot --nginx -d 你的域名.com

# 自动续期测试
sudo certbot renew --dry-run
```

### 4.2 手动添加 SSL 到 Nginx

如果使用方案 A（手动部署），更新 Nginx 配置：

```nginx
server {
    listen 80;
    server_name 你的域名.com;
    return 301 https://$host$request_uri;   # 强制跳转 HTTPS
}

server {
    listen 443 ssl http2;
    server_name 你的域名.com;

    ssl_certificate     /etc/letsencrypt/live/你的域名.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/你的域名.com/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;

    # ... 其余配置同方案 A 第五步 ...
}
```

### 4.3 Docker 方案的 SSL

将证书挂载到 nginx 容器：

```bash
# 在服务器上用 certbot 获取证书后
sudo cp /etc/letsencrypt/live/你的域名.com/fullchain.pem ./ssl/
sudo cp /etc/letsencrypt/live/你的域名.com/privkey.pem ./ssl/

# 然后在 nginx.conf 中配置 443 server 块，重启
docker compose restart nginx
```

---

## 五、CI/CD 自动化部署（可选）

### 5.1 使用 GitHub Actions / GitLab CI

项目已有 `.gitlab-ci.yml` 构建阶段，可扩展为完整部署流程：

```yaml
# .gitlab-ci.yml 扩展
stages:
  - build
  - deploy

# ... 原有的 build 阶段保持不变 ...

deploy-to-server:
  stage: deploy
  image: alpine:latest
  before_script:
    - apk add --no-cache openssh-client rsync
    - mkdir -p ~/.ssh
    - echo "$SSH_PRIVATE_KEY" > ~/.ssh/id_rsa
    - chmod 600 ~/.ssh/id_rsa
  script:
    - ssh -o StrictHostKeyChecking=no $DEPLOY_USER@$DEPLOY_HOST "
        cd /opt/intellitravel &&
        git pull &&
        docker compose up -d --build
      "
  only:
    - master
```

需要的 GitLab CI/CD 变量（Settings → CI/CD → Variables）：

```
DEPLOY_HOST       = 你的服务器IP
DEPLOY_USER       = root
SSH_PRIVATE_KEY   = (SSH 私钥内容)
```

---

## 六、常见问题排查

### Q1: 前端页面空白 / API 请求 404

**检查清单：**
1. `api.ts` 中的 `API_BASE_URL` 是否改为服务器地址
2. Nginx 的 `/api/` 代理配置是否正确
3. 后端是否在运行：`curl http://127.0.0.1:8080/api/user/health`

### Q2: SSE 流式端点超时 / 连接中断

**原因**：Nginx 或反向代理缓冲了 SSE 流。

**修复**：确保 Nginx 配置中有：
```nginx
proxy_buffering off;
proxy_cache off;
proxy_read_timeout 600s;
```

### Q3: 数据库连接失败

**检查：**
```bash
# 方案 A
sudo systemctl status mysql
mysql -u traveluser -p -e "SELECT 1"

# 方案 B
docker compose logs mysql
```

### Q4: JPA 自动建表不生效

**原因**：数据库中可能已有旧表结构冲突。

**修复：**
- 开发环境：在 `application.properties` 中设置 `spring.jpa.hibernate.ddl-auto=create`（危险，会清空数据）
- 生产环境：手动执行 SQL 迁移

### Q5: 内存不足导致 Java 进程被杀

**症状**：后端莫名停止，`journalctl` 显示 `Killed`

**修复：**
- 降低 JVM 堆内存：`-Xms256m -Xmx1g`
- 升级服务器内存
- 开启 swap：`sudo fallocate -l 2G /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile`

### Q6: Docker 容器内无法连接宿主机 MySQL

**修复**：`docker-compose.yml` 中 MySQL 连接字符串使用服务名 `mysql` 而非 `localhost`：

```yaml
DB_URL: jdbc:mysql://mysql:3306/intellitravel?...   # ✅ 服务名
# 不是
DB_URL: jdbc:mysql://localhost:3306/intellitravel?... # ❌
```

### Q7: Windows 上编译，Linux 上部署有兼容性问题吗？

**前端（Vite / Vue）— ✅ 完全没问题**

`npm run build` 产出的 `dist/` 目录是纯静态文件（HTML + CSS + JS + 图片），与操作系统无关。Windows 上构建的产物可以直接复制到 Linux Nginx 下使用。

**后端（Spring Boot / Maven）— ⚠️ 有一个注意事项**

| 组件 | 兼容性 | 说明 |
|------|--------|------|
| JAR 包 | ✅ 没问题 | Java 字节码与平台无关，`mvnw package` 在 Windows 和 Linux 产出相同 JAR |
| Maven 依赖 | ✅ 没问题 | 所有纯 Java 依赖都跨平台 |
| `mvnw` 脚本 | ⚠️ 注意 | Windows 用 `mvnw.cmd`，Linux 用 `mvnw`（无后缀）。在 Linux 上构建直接用 `./mvnw` |
| **Playwright** | 🔴 需处理 | 海报截图功能依赖 Chromium 原生库，已排除 `driver-bundle` |

**Playwright 的跨平台处理**：

`pom.xml` 中排除了 `driver-bundle`，所以 JAR 中不包含任何平台特定的原生二进制文件。部署到 Linux 后，Playwright 会**在首次运行时自动从 CDN 下载** Linux 版的 Chromium 驱动。

但前提是 Linux 服务器上已安装系统依赖库（见方案 A/B 中的"安装 Playwright 系统依赖"步骤）。如果缺少这些库，调用海报生成 API 时会抛出 `UnsatisfiedLinkError`。

**最佳实践**：

| 策略 | 做法 |
|------|------|
| ✅ 推荐 | 在 Linux 服务器上用 `./mvnw package` 重新构建一次后端 JAR，确保一致性 |
| ⚠️ 可用 | Windows 构建 JAR 上传到 Linux 运行（纯 Java 可行），但必须确保 Linux 上 Playwright 系统依赖已装 |
| ❌ 不推荐 | Windows 上 `npm run build` 后直接复制 `dist/` 到 Linux — 这个其实完全可行，前端产物无平台依赖 |

> **总结**：前端没有问题。后端最简单的做法是——源码上传到 Linux 服务器，在服务器上执行 `./mvnw package` 构建。如必须在 Windows 上构建后上传，只要 Linux 上 Playwright 依赖已安装，也能正常运行。

---

## 七、附录：各云平台操作速查

### 阿里云 ECS

```
安全组配置：ECS 控制台 → 网络与安全 → 安全组 → 添加规则
SSH 登录：  ssh root@<公网IP>
```

### 腾讯云 CVM

```
安全组配置：CVM 控制台 → 安全组 → 添加规则
SSH 登录：  ssh root@<公网IP>
```

### 华为云 ECS

```
安全组配置：ECS 控制台 → 安全组 → 配置规则
SSH 登录：  ssh root@<弹性公网IP>
```

---

## 附录 A：使用环境变量管理 API 地址

修改 [planclient/vite.config.ts](planclient/vite.config.ts)：

```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  define: {
    __API_BASE_URL__: JSON.stringify(
      process.env.VITE_API_BASE_URL || 'http://localhost:8080'
    )
  },
  // ... 其他配置保持不变
})
```

修改 [planclient/src/config/api.ts](planclient/src/config/api.ts)：

```typescript
declare const __API_BASE_URL__: string
export const API_BASE_URL = __API_BASE_URL__
```

创建 `planclient/.env.production`：

```
VITE_API_BASE_URL=https://你的域名.com
```

这样构建时自动使用正确的生产地址，开发时仍用 localhost。

---

> **文档版本**: v1.0  
> **撰写日期**: 2026-06-14  
> **维护者**: IntelliTravel Pioneer 开发团队
