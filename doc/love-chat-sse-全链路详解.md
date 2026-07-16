# /love/chat/sse 恋爱流式接口 — 全链路详解

> 从启动服务到前端逐字显示，每一步干了什么、为什么这么做、不做会怎样。

---

## 一、启动阶段（服务一启动就干的事）

### 1.1 Spring 容器初始化

**文件：** `Application.java`

```
Spring Boot 启动 → 扫描所有 @Component、@Service、@Configuration → 创建 Bean
```

### 1.2 加载知识库到 PgVector

**文件：** `LoveDocumentLoader.java`

```java
@PostConstruct
public void init() {
    documentLoader.initKnowledgeBase();
}
```

**干了什么：** 读取 `love-knowledge.md` → 切成小片段 → 向量化 → 存入 PostgreSQL（vector_store 表）

**大白话：** 把恋爱知识库从 md 文件读到数据库里，以后检索用。

**不用会怎样：** RAG 搜不到任何知识，AI 凭记忆回答，容易胡编。

---

### 1.3 创建 ChatClient（绑定所有 Advisor）

**文件：** `LoveAdvisorService.java` 构造函数

```java
this.chatClient = chatClientBuilder
    .defaultSystem(SYSTEM_PROMPT)    // 给 AI 定人设：你是恋爱专家小红娘
    .defaultAdvisors(
        MessageChatMemoryAdvisor,     // ① 对话记忆
        new ReReadingAdvisor(),       // ② 推理增强
        buildRagAdvisor(),            // ③ RAG 检索 + 兜底
        new MyLoggerAdvisor()         // ④ 日志打印
    )
    .build();
```

**大白话：** 造一个"传话筒"，以后每次跟 AI 聊天，自动经过这 4 道工序处理。

| Advisor | 干嘛的 | 不用会怎样 |
|---------|--------|-----------|
| ① MessageChatMemoryAdvisor | 记住聊天历史，下次对话带上 | AI 每次都是新朋友，不记得你 |
| ② ReReadingAdvisor | 把你问题重复一遍，AI 看两遍 | 复杂问题 AI 容易漏条件 |
| ③ buildRagAdvisor | 去 PgVector 搜相关知识，搜不到就兜底 | AI 凭记忆回答，可能胡编 |
| ④ MyLoggerAdvisor | 控制台打印请求和回复 | 出问题时不知道发了啥、回了啥 |

---

### 1.4 创建对话记忆文件夹

**文件：** `FileBasedChatMemory.java`

```java
String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
```

**大白话：** 在项目目录下建 `tmp/chat-memory/` 文件夹，每个用户的对话存一个 `.kryo` 文件。

**不用会怎样：** 重启服务后对话记忆全丢，AI 不认识你。

---

## 二、请求阶段（前端发一条消息）

### 2.1 前端发送请求

**文件：** `admin/agent-hub/src/api/index.js`

```javascript
export const streamLove = (message, userId = 'user001') => {
  const url = `http://localhost:8090/ai/love/chat/sse?userId=${userId}&message=${encodeURIComponent(message)}`
  return new EventSource(url)  // ← 建立 SSE 长连接
}
```

**文件：** `admin/agent-hub/src/components/ChatRoom.vue`

```javascript
currentEventSource.onmessage = (event) => {
  messages.value[aiMsgIndex].content += event.data  // ← 收到一个字就追到页面上
  scrollToBottom()
}
```

**大白话：** 浏览器打开一条长连接，AI 说一个字就收一个字，实时显示。不是等 AI 全说完了再一把显示。

**不用 SSE（用同步）会怎样：** 用户看着白屏等好几秒，然后突然冒出一大段回复，体验差。

---

### 2.2 Controller 接收请求

**文件：** `AiController2.java`

```java
@GetMapping(value = "/love/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> loveChatStream(String message, String userId) {
    return loveAdvisorService.chatStream(message, "love_" + userId);
}
```

**大白话：** 收到请求，拼好 chatId（`love_user001`），交给 Service 处理。

**为什么 chatId = "love_" + userId：** 确保同一个用户的所有对话存在同一个文件里，不同用户的对话互不干扰。

---

### 2.3 Service 核心逻辑

**文件：** `LoveAdvisorService.java` — `chatStream()` 方法

#### 步骤 1：Query 改写

```java
String rewrittenQuery = queryRewriter.doQueryRewrite(userQuestion);
```

**文件：** `QueryRewriter.java`

```
用户输入："怎么追女生"（口语）
    ↓ AI 改写
改写后："追求心仪对象的方法和技巧"（检索关键词）
```

**大白话：** 把用户口语化的表达改成更适合搜索的关键词，提高命中率。

**不用会怎样：** 用户说大白话，向量库搜不到，检索效果打折扣。

---

#### 步骤 2：构建 Prompt + 走 Advisor 链

```java
return chatClient
    .prompt()
    .user(rewrittenQuery)                                            // 用户问题
    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)) // 传 chatId
    .stream()                                                        // 流式调用
    .content();
```

此时 Spring AI 内部依次执行 Advisor 链：

```
用户问题："追求心仪对象的方法和技巧"
    ↓
┌──────────────────────────────────────────┐
│ ① MessageChatMemoryAdvisor               │
│   读 love_user001.kryo → 找到历史对话     │
│   拼进 prompt："历史对话：我叫张三..."     │
│   ↓
│ ② ReReadingAdvisor                       │
│   追加："Read the question again: 追求..."│
│   ↓
│ ③ RetrievalAugmentationAdvisor            │
│   去 PgVector 搜 "追求心仪对象"           │
│   找到 3 条相关文档，拼进 prompt           │
│   如果一条都找不到 → 兜底："抱歉我只能..." │
│   ↓
│ ④ MyLoggerAdvisor                        │
│   打印完整 prompt 到控制台                │
└──────────────────────────────────────────┘
    ↓
  发给 LLM（阿里云 qwen-plus）
    ↓
  AI 一个字一个字回复（Flux<String>）
    ↓
  MyLoggerAdvisor 等流结束 → 拼完整 → 打印日志
```

**大白话：** 把历史对话 + 知识库资料 + 用户问题拼在一起发给 AI，让 AI 在充分的上文下回答。

---

#### 步骤 3：流式输出 + 存入向量库

```java
.doOnNext(fullAnswer::append)       // 每收到一个字，拼到 fullAnswer 里
.doOnComplete(() -> {               // AI 说完了
    saveToVectorStore(question, fullAnswer, chatId);  // 存入 PgVector
});
```

**大白话：** 一边把字推给前端，一边后台收集完整回复。AI 说完了就把这轮问答存到 PgVector，以后能搜到。

---

### 2.4 存入知识库（带质量过滤）

**文件：** `LoveAdvisorService.java` — `saveToVectorStore()` 方法

```java
// 太短不存（"嗯""哦"没价值）
if (answer.length() < 20) { return; }

// 兜底回复不存（"抱歉我只能回答恋爱问题"）
if (answer.contains("抱歉，我只能回答恋爱相关的问题")) { return; }

// 有价值的存入，带标签
Document doc = new Document(newKnowledge);
doc.getMetadata().put("userId", chatId);
doc.getMetadata().put("type", "conversation");
vectorStore.add(List.of(doc));
```

**大白话：** 不是所有对话都存。太短的、兜底的都不要，只存有价值的恋爱问答。打了标签以后能按用户隔离。

**不用会怎样：** 垃圾数据污染向量库，检索质量下降。

---

## 三、涉及的所有文件

| 文件 | 角色 |
|------|------|
| `Application.java` | 启动入口 |
| `AiController2.java` | 接收 HTTP 请求，路由到 Service |
| `LoveAdvisorService.java` | 核心业务：RAG + 记忆 + 流式 + 存库 |
| `QueryRewriter.java` | 改写用户问题，提升检索命中率 |
| `LoveDocumentLoader.java` | 启动时加载 md 知识库到 PgVector |
| `FileBasedChatMemory.java` | 对话记忆存硬盘（.kryo 文件） |
| `ReReadingAdvisor.java` | 复述问题，增强推理 |
| `MyLoggerAdvisor.java` | 拦截请求/响应，打印日志 |
| `ToolConfig.java` | 注册 8 个工具（WebSearch、File 等） |
| `application-dev.yml` | 数据库、API Key、PgVector 配置 |
| `love-knowledge.md` | 恋爱知识库原始文档 |
| `api/index.js` | 前端 SSE 请求封装 |
| `ChatRoom.vue` | 前端聊天界面，逐字显示 |

---

## 四、为什么要这样设计

```
用户一句话："失恋了怎么办"
    ↓
① 对话记忆  → AI 知道你叫张三、25岁、女朋友叫李四
② 问题复述  → AI 不会遗漏关键信息
③ RAG 检索  → 从知识库找到"失恋恢复"相关技巧
④ 质量过滤  → 只存有价值的问答，知识库越来越丰富
⑤ 流式输出  → 用户看到打字效果，体验好
⑥ 兜底保护  → 非恋爱问题不瞎编
```

| 少了一个 | 后果 |
|----------|------|
| 没有记忆 | AI 每次都不认识你，多轮对话废了 |
| 没有 RAG | AI 凭记忆回答，可能过时、不准确 |
| 没有 Query 改写 | 口语化问题搜不到，检索命中率低 |
| 没有流式 | 等 5 秒突然弹出一大段，体验差 |
| 没有兜底 | 问天气、问代码 AI 也瞎答，不专业 |
| 没有质量过滤 | 向量库塞满垃圾，越用越蠢 |

---

## 五、生产级改造清单（已完成）

| 功能 | 状态 |
|------|------|
| 对话记忆持久化（FileBasedChatMemory） | ✅ |
| RAG 检索增强（PgVector） | ✅ |
| Query 改写 | ✅ |
| 相似度阈值 + TopK | ✅ |
| 兜底防胡答 | ✅ |
| 流式 SSE 输出 | ✅ |
| 对话存入向量库 | ✅ |
| 质量过滤 | ✅ |
| 用户标签隔离 | ✅ |
| Re2 推理增强（ReReadingAdvisor） | ✅ |
| 工具调用（chatWithTools） | ✅ |
| Kryo 防损坏写入 | ✅ |
| 结构化输出（LoveReport） | 待加 |
| 状态过滤（单身/恋爱/已婚） | 待加 |