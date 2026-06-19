# PlanClient - 前端应用

> 基于Vue 3 + TypeScript + Element Plus的AI旅行规划前端应用，提供智能行程规划、实时对话、行程画布等功能。

## 项目概述

PlanClient是IntelliTravel AI旅行规划系统的前端应用，采用Vue 3 Composition API，支持SSE实时通信、拖拽编辑、多轮对话等现代化交互体验。

### 核心特性

- **AI行程规划**: 自然语言输入，智能生成行程
- **实时对话**: 支持多轮对话优化行程
- **行程画布**: 时间线视图，支持拖拽排序
- **SSE实时通信**: 实时显示Agent执行过程
- **全局偏好设置**: 跨对话知识共享，AI回复遵守自定义规则
- **响应式设计**: 支持多端适配
- **组件化架构**: 高度可复用的组件设计

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5.25 | 前端框架 |
| TypeScript | 5.9.3 | 编程语言 |
| Element Plus | 2.13.2 | UI组件库 |
| Vite | 7.3.1 | 构建工具 |
| Pinia | 3.0.4 | 状态管理 |
| Vue Router | 5.0.2 | 路由管理 |
| Axios | 1.13.5 | HTTP客户端 |
| Vue TSC | 3.1.5 | TypeScript编译 |

## 项目结构

```
planclient/
├── src/
│   ├── api/                  # API接口封装
│   │   ├── itinerary.ts           # 行程API
│   │   └── preference.ts          # 全局偏好API
│   ├── components/           # 通用组件
│   ├── composables/          # 组合式函数
│   │   ├── useBackHandler.ts    # 返回处理
│   │   ├── useConversation.ts   # 对话管理
│   │   ├── useDragDrop.ts      # 拖拽功能
│   │   └── useItinerary.ts     # 行程管理
│   ├── config/               # 配置文件
│   │   └── api.ts              # API基础URL配置
│   ├── constants/            # 常量定义（工具名称/图标映射）
│   ├── router/               # 路由配置
│   ├── stores/               # Pinia状态管理
│   ├── types/                # TypeScript类型定义
│   ├── utils/                # 工具函数
│   ├── views/                # 页面组件
│   │   ├── battle/             # 行程仓库
│   │   ├── conversation/       # 对话历史列表
│   │   ├── user/               # 用户登录/注册/偏好设置
│   │   │   ├── Dashboard.vue       # 首页仪表盘
│   │   │   └── UserPreferences.vue # 全局偏好设置页面
│   │   └── workspace/          # 工作区（核心页面集合）
│   │       ├── Workspace.vue         # AI行程规划工作台
│   │       ├── ItineraryCanvas.vue   # 行程画布（时间线+拖拽编辑）
│   │       ├── ItineraryCard.vue     # 行程卡片
│   │       ├── FormInput.vue         # 结构化表单输入
│   │       ├── ChatInput.vue         # 自然语言输入
│   │       └── ToolCallCard.vue      # 工具调用展示
│   ├── App.vue               # 根组件
│   ├── main.ts               # 应用入口
│   ├── style.css             # 全局样式
│   └── vite-env.d.ts         # Vite类型声明
├── public/                   # 静态资源
├── index.html                # HTML入口
├── package.json              # 项目配置
├── tsconfig.json             # TypeScript配置
├── vite.config.ts            # Vite配置
└── README.md
```

## 安装要求

- **Node.js**: 18+
- **npm**: 9+ 或 **yarn**: 1.22+ 或 **pnpm**: 8+
- **操作系统**: Windows/Linux/macOS

## 开发环境

### 安装依赖

```bash
# 使用npm
npm install

# 使用yarn
yarn install

# 使用pnpm
pnpm install
```

### 启动开发服务器

```bash
# 使用npm
npm run dev

# 使用yarn
yarn dev

# 使用pnpm
pnpm dev
```

开发服务器将在 http://localhost:5173 启动。

### 环境配置

API基础URL在 `src/config/api.ts` 中配置：

```typescript
// 开发环境
export const API_BASE_URL = 'http://localhost:8080'

// 生产环境（根据需要修改）
// export const API_BASE_URL = 'https://your-api-domain.com'
```

## 构建与部署

### 生产环境构建

```bash
# 使用npm
npm run build

# 使用yarn
yarn build

# 使用pnpm
pnpm build
```

构建产物将输出到 `dist/` 目录。

### 预览构建结果

```bash
# 使用npm
npm run preview

# 使用yarn
yarn preview

# 使用pnpm
pnpm preview
```

### 部署方式

#### 1. 静态服务器部署

将 `dist/` 目录部署到任何静态文件服务器（Nginx、Apache、Caddy等）。

**Nginx配置示例**：

```nginx
server {
    listen 80;
    server_name your-domain.com;
    root /path/to/dist;
    index index.html;

    # Vue Router history模式支持
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API代理
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

#### 2. Docker部署

```dockerfile
# 构建阶段
FROM node:18-alpine as build-stage
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# 生产阶段
FROM nginx:alpine as production-stage
COPY --from=build-stage /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

#### 3. Vercel/Netlify部署

项目支持零配置部署到Vercel或Netlify：

1. Fork项目到GitHub
2. 在Vercel/Netlify中导入项目
3. 设置构建命令：`npm run build`
4. 设置输出目录：`dist`

## 开发指南

### 代码规范

项目使用ESLint + Prettier进行代码格式化：

```bash
# 检查代码规范
npm run lint

# 自动修复
npm run lint:fix
```

### TypeScript

项目使用TypeScript严格模式，类型定义在 `src/types/` 目录。

### 组件开发

- 使用Vue 3 Composition API
- 组件命名采用PascalCase
- 组合式函数以`use`开头
- 工具函数放在`src/utils/`

### 状态管理

使用Pinia进行状态管理，Store定义在 `src/stores/` 目录。

### 路由配置

路由配置在 `src/router/` 目录，支持：

- 路由守卫（认证检查）
- 路由懒加载
- 嵌套路由

## 核心功能

### 1. AI行程规划

- **自然语言输入解析**：用户通过自然语言描述旅行需求
- **结构化表单输入**：通过表单精确设置目的地、天数、兴趣等
- **意图识别**：前端将用户输入发送到后端，后端通过双通道架构识别意图：
  - 关键词快速匹配（0延迟）
  - AI分类器兜底（处理自然表达）
  - 意图类型：GENERATE（生成行程）、CHAT（聊天）
- **SSE实时进度显示**：实时显示Agent执行过程
- **行程画布实时更新**：行程生成后实时更新画布

### 2. 实时对话

- **多轮对话支持**：持续对话优化行程
- **消息历史记录**：保存所有对话记录，支持搜索
- **智能意图识别**：
  - 前端将用户消息发送到后端 `/api/itinerary/modify/stream` 端点
  - 后端通过双通道架构识别意图：
    1. 关键词快速匹配（0延迟）
    2. AI分类器兜底（处理自然表达）
  - 意图类型：CHAT（聊天）、MODIFY（修改行程）
  - 前端根据意图类型展示不同响应
- **自然语言回复**：AI回复为结构化自然语言，隐藏原始思考过程

### 3. 行程画布

- 时间线视图
- 拖拽排序
- 点击展开详情
- 一键跳转地图

### 4. 全局偏好设置

- **偏好类别**: 回答风格(STYLE)、饮食偏好(DIET)、体能水平(FITNESS)、特殊需求(SPECIAL)、自定义规则(CUSTOM)
- **卡片式交互**: 可视化多选，点击切换选中状态
- **自定义规则**: 动态添加/删除自定义规则，AI回复严格遵守
- **AI集成**: 偏好自动注入到行程生成和修改的AI提示词中
- **安全防护**: 内置Prompt Injection检测，内容长度限制

### 5. 用户系统

- 用户注册/登录
- 个人信息管理
- 行程收藏/分享
- 全局偏好设置（跨对话知识共享）

## 浏览器支持

- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+

## 故障排除

### 常见问题

1. **依赖安装失败**
   ```bash
   # 清除缓存
   npm cache clean --force
   rm -rf node_modules package-lock.json
   npm install
   ```

2. **开发服务器启动失败**
   - 检查端口5173是否被占用
   - 检查Node.js版本是否符合要求

3. **API请求失败**
   - 检查后端服务是否运行
   - 验证`src/config/api.ts`中的API地址

4. **构建失败**
   - 检查TypeScript类型错误
   - 运行`npm run type-check`检查类型

## 性能优化

- 路由懒加载
- 组件异步加载
- 图片懒加载
- 代码分割
- Gzip压缩

## 测试

```bash
# 运行单元测试
npm run test

# 运行端到端测试
npm run test:e2e
```

## 贡献指南

1. Fork项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建Pull Request

## 许可证

本项目仅供学习交流使用

---

> 本项目是IntelliTravel AI旅行规划系统的前端应用，基于Vue 3 + TypeScript构建，提供现代化的用户交互体验。