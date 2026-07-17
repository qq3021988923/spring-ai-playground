# /love/chat/sse 从零开发全流程

> 一个生产级 RAG 流式对话接口的完整代码拆解
> 边写代码边注释，从 Controller 一路追到数据库

---

## 一、整体架构

```
浏览器（EventSource）
  ↓ GET /ai/love/chat/sse?message=xxx&userId=user001
AiController2.loveChatStream()           ← 入口：接收请求，拼 chatId
  ↓
LoveAdvisorService.chatStream()          ← 核心：流式对话+RAG+记忆
  ├─ rewriteIfNeeded()                   ← Query 改写策略
  ├─ chatClient.prompt()...stream()      ← 调 LLM（流式）
  ├─ Advisor 链（自动执行）               ← AOP 拦截器链
  └─ saveToVectorStore()                 ← 对话结束后存 PgVector
```

---

## 二、涉及文件清单（按调用顺序）

| 序号 | 文件 | 角色 |
|------|------|------|
| 1 | `admin/agent-hub/src/api/index.js` | 前端 SSE 请求封装 |
| 2 | `admin/agent-hub/src/components/ChatRoom.vue` | 前端聊天界面 |
| 3 | `AiController2.java` | Controller 入口 |
| 4 | `LoveAdvisorService.java` | 核心业务 Service |
| 5 | `FileBasedChatMemory.java` | 对话记忆（硬盘文件） |
| 6 | `QueryRewriter.java` | Query 改写 |
| 7 | `LoveDocumentLoader.java` | 知识库加载到 PgVector |
| 8 | `ReReadingAdvisor.java` | Re2 推理增强 |
| 9 | `MyLoggerAdvisor.java` | 日志拦截 |
| 10 | `love-knowledge-单身篇.md` 等 3 篇 | 知识库原始文档 |
| 11 | `application-dev.yml` | 数据库/API Key 配置 |

---

## 三、逐文件拆解（从 0 开始写代码）

---

### 第 1 步：前端 — 浏览器发起 SSE 长连接

**文件：`admin/agent-hub/src/api/index.js`**

```javascript
// 后端地址
const BASE_URL = 'http://localhost:8090/ai'

// 【恋爱顾问流式接口】
// 作用：创建一条到后端的 SSE 长连接，AI 说一个字浏览器就收一个字
// 为什么用 EventSource：
//   - 浏览器原生支持，不需要额外库
//   - 自动重连（断开后自动重新建立连接）
//   - 单向推送（服务端 → 客户端），聊天场景正好匹配
export const streamLove = (message, userId = 'user001') => {
  // ① 拼 URL：userId 确保不同用户的对话互不干扰
  const url = `${BASE_URL}/love/chat/sse?userId=${userId}&message=${encodeURIComponent(message)}`
  // ② 建立连接：浏览器发起 HTTP GET，response Content-Type=text/event-stream
  return new EventSource(url)
}
```

**文件：`admin/agent-hub/src/components/ChatRoom.vue`**（关键片段）

```javascript
// ③ 绑定事件：每收到一个 SSE 数据块，就追到聊天气泡上
currentEventSource.onmessage = (event) => {
  // 通过 messages.value[index] 修改（走 Vue 响应式代理），页面实时更新
  messages.value[aiMsgIndex].content += event.data
  scrollToBottom()  // 自动滚到最新消息
}

// ④ 连接关闭时（AI 说完了），停止 loading 动画
currentEventSource.onerror = () => {
  currentEventSource.close()
  loading.value = false
}
```

---

### 第 2 步：Controller — 接收请求，组装 chatId

**文件：`com/yang/controller/AiController2.java`**

```java
@GetMapping(value = "/love/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> loveChatStream(String message, String userId) {
    // ===== 作用 =====
    // ① @GetMapping：接收前端 GET 请求
    // ② produces = MediaType.TEXT_EVENT_STREAM_VALUE：
    //    告诉浏览器"我会一段一段给你数据，保持连接别断"
    // ③ 返回类型 Flux<String>：
    //    Reactor 的响应式类型，相当于一个"流"，数据会一个一个推给前端

    // ===== chatId 组装 =====
    // "love_" + "user001" = "love_user001"
    // 作用：同一个 userId → 同一个 chatId → 同一个 .kryo 文件
    //       不同用户 → 不同文件 → 互不干扰
    return loveAdvisorService.chatStream(message, "love_" + userId, null);
    //                                                         ↑
    //                                              status=null → 不过滤，搜全部文档
}
```

---

### 第 3 步：Service 初始化（Spring 启动时执行一次）

**文件：`com/yang/service/LoveAdvisorService.java`**

```java
@Slf4j
@Service
public class LoveAdvisorService {

    // ===== 字段 =====
    private final VectorStore vectorStore;      // PgVector 向量数据库
    private final LoveDocumentLoader documentLoader; // 知识库加载器
    private final QueryRewriter queryRewriter;  // Query 改写
    private final ToolCallback[] allTools;      // 8 个工具（搜索/爬虫/文件...）
    private final ChatClient chatClient;        // Spring AI 的核心"传话筒"

    // ===== System Prompt =====
    // 作用：给 AI 定人设。"你是恋爱专家小红娘"，每次请求自动拼到 prompt 最前面
    // 为什么用 private static final：
    //   - 写死在代码里，不会变
    //   - 面试说"Prompt 工程"时这就是你的模板
    private static final String SYSTEM_PROMPT = """
            你是一位专业的恋爱顾问，名叫"小红娘"。
            你的特点：
            1. 温柔体贴，善解人意
            2. 善于倾听，给出实用建议
            ...
            """;

    // ===== 构造函数（Spring 启动时调用一次）=====
    // 参数全部由 Spring 自动注入
    public LoveAdvisorService(ChatClient.Builder chatClientBuilder,
                              LoveDocumentLoader documentLoader,
                              VectorStore vectorStore,
                              QueryRewriter queryRewriter,
                              ToolCallback[] allTools) {
        this.documentLoader = documentLoader;
        this.vectorStore = vectorStore;
        this.queryRewriter = queryRewriter;
        this.allTools = allTools;

        // ----- 创建对话记忆（文件版）-----
        // user.dir = 当前项目根目录
        // 最终路径：springai-lianxi/tmp/chat-memory/love_user001.kryo
        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        // FileBasedChatMemory 实现了 Spring AI 的 ChatMemory 接口
        // 内部用 Kryo 序列化把对话存成 .kryo 文件
        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);

        // ----- 构建 ChatClient（一次构建，全生命周期复用）-----
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)  // 人设：恋爱专家小红娘
                .defaultAdvisors(              // 绑定 AOP 拦截器链
                        // ① 对话记忆：每次请求自动读/写 .kryo 文件
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // ② Re2 推理增强：把用户问题重复一遍，提升 LLM 理解
                        new ReReadingAdvisor(),
                        // ③ 日志：打印请求/响应到控制台
                        new MyLoggerAdvisor()
                )
                .build();
        // RAG Advisor 不在构造函数里注册，因为需要按请求动态传入 status 过滤
    }
```

---

### 第 4 步：流式对话核心方法

**文件：`com/yang/service/LoveAdvisorService.java` — `chatStream()` 方法**

```java
/**
 * 流式对话（SSE）：Query 改写 → RAG 检索 → LLM 流式回答 → 存 PgVector
 *
 * @param userQuestion  用户原始问题，如 "怎么追女生"
 * @param chatId        会话ID，如 "love_user001"
 * @param status        状态过滤，null=全部 / "单身" / "恋爱" / "已婚"
 * @return Flux<String> 逐字推送给前端的流
 */
public Flux<String> chatStream(String userQuestion, String chatId, String status) {

    // ===== 步骤 1：Query 改写 =====
    // 短问题（<20字）→ AI 改写 → 搜得更准
    // 长问题（≥20字）→ 直接用 → 省 token
    String rewrittenQuery = rewriteIfNeeded(userQuestion);

    // ===== 步骤 2：构建请求 + 流式调用 =====
    StringBuilder fullAnswer = new StringBuilder();  // 收集完整回答，后面存库用

    return chatClient                          // 构造函数造好的 ChatClient
            .prompt()                          // 开始构建一次 LLM 请求
            .user(rewrittenQuery)              // 用户问题（可能是改写后的）
            // ----- 传 chatId 给 MessageChatMemoryAdvisor -----
            // Advisor 内部根据这个 key 去读/写对应的 .kryo 文件
            .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
            // ----- RAG Advisor 按需加载 -----
            // buildRagAdvisor(status) 内部：
            //   1. 去 PgVector 搜相似文档
            //   2. 把结果拼进 prompt
            //   3. 如果 status 不为 null，加 Filter 过滤
            .advisors(buildRagAdvisor(status))
            // ----- 流式调用 -----
            .stream()      // ← 关键！和 .call() 的区别：
            // .call()  = 等 AI 全说完 → 一把返回完整 String
            // .stream() = AI 说一个字推一个字 → 返回 Flux<String>
            .content()     // 提取纯文本流
            // ----- 流处理 -----
            .doOnNext(fullAnswer::append)   // 每收到一个字，拼到 fullAnswer
            .doOnComplete(() -> {            // AI 全部说完了
                // 把完整问答存入 PgVector（带质量过滤）
                saveToVectorStore(userQuestion, fullAnswer.toString(), chatId);
            });
}
```

---

### 第 5 步：Advisor 链执行顺序

当 `.stream()` 被调用时，Spring AI 内部按注册顺序执行 Advisor：

```
用户消息："追求心仪对象的技巧和方法"
  ↓ ᅐ
┌─────────────────────────────────────────────────────────┐
│ ① MessageChatMemoryAdvisor（构造函数注册，第 1 个）      │
│   BEFORE：读 love_user001.kryo → 拿到历史对话            │
│          拼进 prompt："历史对话：[我叫张三, AI:你好张三]" │
│   AFTER： 把本轮对话写回 love_user001.kryo               │
│   类比：微信聊天记录自动存取                              │
├─────────────────────────────────────────────────────────┤
│ ② ReReadingAdvisor（构造函数注册，第 2 个）              │
│   BEFORE：在用户问题末尾追加                              │
│          "Read the question again: 追求心仪对象的技巧..."│
│   作用：LLM 看两遍问题，减少遗漏关键条件                   │
├─────────────────────────────────────────────────────────┤
│ ③ RetrievalAugmentationAdvisor（每次请求动态加载）       │
│   BEFORE：去 PgVector 搜：                                │
│     SELECT * FROM vector_store                           │
│     ORDER BY embedding <=> vectorize("追求心仪对象的技巧")│
│     WHERE similarity > 0.5   ← 低于 50% 的不要            │
│     LIMIT 3                  ← 最多 3 条                  │
│     搜到后拼进 prompt："参考以下资料：追求技巧/自信提升..."│
│   没搜到 → allowEmptyContext(false) → AI 可能拒绝回答     │
├─────────────────────────────────────────────────────────┤
│ ④ MyLoggerAdvisor（构造函数注册，第 4 个）               │
│   BEFORE：打印完整 prompt 到控制台（调试用）               │
│   AFTER： 等流结束，拼完整回复，打印到控制台               │
└─────────────────────────────────────────────────────────┘
  ↓
实际调用 LLM（阿里云 DashScope，qwen-plus）
  ↓
Flux<String> 一个字一个字往外推 → 前端 EventSource 逐字接收
```

---

### 第 6 步：对话记忆如何存取

**文件：`com/yang/chatmemory/FileBasedChatMemory.java`**

```java
public class FileBasedChatMemory implements ChatMemory {

    private final String BASE_DIR;  // e:/项目/tmp/chat-memory/

    // Kryo：高性能 Java 序列化库，比 Java 原生序列化快 10 倍
    private static final Kryo kryo = new Kryo();

    // 读：每次对话前，MessageChatMemoryAdvisor 调这个方法
    @Override
    public List<Message> get(String conversationId) {
        // conversationId = "love_user001"
        // 找文件：tmp/chat-memory/love_user001.kryo
        File file = new File(BASE_DIR, conversationId + ".kryo");
        if (file.exists()) {
            // Kryo 反序列化：二进制 → Java 对象
            return kryo.readObject(input, ArrayList.class);
        }
        return new ArrayList<>();  // 第一次对话，返回空列表
    }

    // 写：每次对话后，MessageChatMemoryAdvisor 调这个方法
    @Override
    public void add(String conversationId, List<Message> messages) {
        // ① 先读旧内容
        List<Message> old = get(conversationId);
        // ② 追加新消息
        old.addAll(messages);
        // ③ 写回文件（先写 .tmp，再 rename 为 .kryo，防损坏）
        saveConversation(conversationId, old);
    }
}
```

**为什么不直接用文本存？**
- Kryo 二进制格式：读写快、体积小、支持复杂对象（Message 是接口，有多种实现类）
- 文本（JSON）：每次序列化/反序列化要解析，慢

---

### 第 7 步：RAG 检索 — 知识库如何加载和搜索

**启动时加载（`@PostConstruct`）：**

```java
@PostConstruct
public void init() {
    documentLoader.initKnowledgeBase();
}
```

**文件：`com/yang/rag/LoveDocumentLoader.java`**

```java
// 三篇文档 → 三条状态标签
private static final String[][] KNOWLEDGE_FILES = {
    {"document/恋爱常见问题和回答-单身篇.md", "单身"},
    {"document/恋爱常见问题和回答-恋爱篇.md", "恋爱"},
    {"document/恋爱常见问题和回答-已婚篇.md", "已婚"}
};

public void initKnowledgeBase() {
    TokenTextSplitter splitter = new TokenTextSplitter();  // 文本切割器：太长切成小块
    for (String[] file : KNOWLEDGE_FILES) {
        // ① 读 md 文件
        TextReader reader = new TextReader(new ClassPathResource(file[0]));
        List<Document> documents = reader.get();
        // ② 切成小块（每块约 200 个 token）
        List<Document> chunks = splitter.apply(documents);
        // ③ 打标签：status=单身/恋爱/已婚
        chunks.forEach(doc -> doc.getMetadata().put("status", file[1]));
        // ④ 向量化 + 存入 PgVector（AI 自动把文本转成向量）
        vectorStore.add(chunks);
    }
}
```

**检索（对话时自动触发）：**

```java
// buildRagAdvisor() 内部调了这个检索器：
VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
    .vectorStore(vectorStore)       // 去哪个库搜
    .similarityThreshold(0.5)       // 低于 50% 相似度的垃圾不要
    .topK(3)                        // 最多返回 3 条，多了 AI "消化不良"
    .build();
```

**底层 SQL（PgVector 自动生成）：**

```sql
SELECT content, metadata,
    1 - (embedding <=> ai_embedding('如何追求心仪对象')) AS similarity
FROM vector_store
ORDER BY embedding <=> ai_embedding('如何追求心仪对象')
LIMIT 3;
```

`<=>` 是 pgvector 插件的余弦距离运算符。`1 - 余弦距离 = 余弦相似度`。

过滤条件 `WHERE similarity > 0.5`：相似度低于 50% 的直接丢掉，不给 AI。

---

### 第 8 步：对话存入 PgVector（知识库自增长）

```java
private void saveToVectorStore(String question, String answer, String chatId) {
    // ===== 两道质量过滤 =====
    // ① "嗯""哦"太短，没检索价值 → 跳过
    if (answer.length() < 20) return;
    // ② 兜底回复（翻不到资料时的拒绝话术）→ 跳过
    if (answer.contains("抱歉，我只能回答恋爱相关的问题")) return;

    // ===== 拼文本 =====
    String newKnowledge = """
    用户问题：%s
    恋爱顾问回答：%s
    """.formatted(question, answer);

    // ===== 打标签 =====
    Document doc = new Document(newKnowledge);
    doc.getMetadata().put("userId", chatId);              // 谁产生的
    doc.getMetadata().put("type", "conversation");         // 标记为对话类型
    doc.getMetadata().put("timestamp", System.currentTimeMillis());

    // ===== 存入 =====
    vectorStore.add(List.of(doc));  // 向量化 + INSERT INTO vector_store
}
```

**为什么存入？** 知识库会越用越丰富。以后别人问类似问题，能从历史对话里搜到参考资料。

---

### 第 9 步：Query 改写策略

**文件：`com/yang/rag/QueryRewriter.java`**

```java
// 调用 AI 把口语转成检索关键词
public String doQueryRewrite(String prompt) {
    Query query = new Query(prompt);                       // ① 包成 Query 对象
    Query transformedQuery = queryTransformer.transform(query); // ② 调 AI 改写
    return transformedQuery.text();                         // ③ 取出改写结果
}
```

**改写策略（省 token）：**

```java
private String rewriteIfNeeded(String userQuestion) {
    if (userQuestion.length() < 20) {
        // 短问题 → 口语化概率高 → 改写："怎么追女生"→"追求心仪对象的方法技巧"
        return queryRewriter.doQueryRewrite(userQuestion);
    }
    // 长问题 → 已经很具体了 → 直接用，省一次 LLM 调用
    return userQuestion;
}
```

---

### 第 10 步：ReReadingAdvisor — 推理增强

**文件：`com/yang/advisor/ReReadingAdvisor.java`**

```java
// 请求发出前，把用户问题末尾追加一句 "Read the question again: XXX"
private ChatClientRequest before(ChatClientRequest request) {
    String userText = request.prompt().getUserMessage().getText();  // "怎么追女生"
    String newUserText = """
            %s
            Read the question again: %s
            """.formatted(userText, userText);  // 拼成两遍
    Prompt newPrompt = request.prompt().augmentUserMessage(newUserText);
    return new ChatClientRequest(newPrompt, request.context());
}
```

**效果：** "怎么追女生\nRead the question again: 怎么追女生"。LLM 看两遍问题，复杂条件不容易漏。

---

### 第 11 步：配置文件

**文件：`application-dev.yml`**

```yaml
server:
  port: 8090

spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/yu_ai_db   # PgVector 数据库
  ai:
    dashscope:
      api-key: sk-xxxx                                 # 阿里云通义千问
      chat:
        options:
          model: qwen-plus                             # 用的模型
```

**Docker：** `docker-compose.yml` 启动 PostgreSQL 16 + pgvector 插件，端口 5433。

---

## 四、完整请求链路图

```
┌─────────────────────────────────────────────────────────────┐
│                     前端 (Vue 3 + Vite)                      │
│                                                             │
│  ChatRoom.vue                                               │
│    → sendLoveStream("怎么追女生")                            │
│    → api/index.js: streamLove("怎么追女生", "user001")       │
│    → new EventSource("/ai/love/chat/sse?message=怎么追女生   │
│                       &userId=user001")                      │
│    → eventSource.onmessage → 每收到一个字 → 更新聊天气泡     │
└─────────────────────────────────────────────────────────────┘
                           ↓ HTTP GET
┌─────────────────────────────────────────────────────────────┐
│                     后端 (Spring Boot :8090)                 │
│                                                             │
│  AiController2.loveChatStream("怎么追女生", "user001")       │
│    → chatId = "love_user001"                                │
│    → loveAdvisorService.chatStream("怎么追女生",              │
│                                     "love_user001", null)    │
│                                                             │
│  LoveAdvisorService.chatStream():                            │
│    ① rewriteIfNeeded("怎么追女生")                            │
│       → 6字 < 20 → 改写 → "追求心仪对象的方法技巧"           │
│    ② chatClient.prompt()                                    │
│         .user("追求心仪对象的方法技巧")                        │
│         .advisors(ChatMemory.CONVERSATION_ID, "love_user001")│
│         .advisors(buildRagAdvisor(null))                     │
│         .stream()                                            │
│         .content()                                           │
│    ③ Spring AI 内部执行 Advisor 链：                          │
│       ├─ MessageChatMemoryAdvisor                             │
│       │   读 love_user001.kryo → 历史对话拼进 prompt          │
│       ├─ ReReadingAdvisor                                    │
│       │   追加 Read the question again:...                   │
│       ├─ RetrievalAugmentationAdvisor                        │
│       │   搜 PgVector → 找到 3 条相关文档 → 拼进 prompt      │
│       └─ MyLoggerAdvisor                                     │
│           打印完整 prompt                                    │
│    ④ 发给 LLM（阿里云 qwen-plus）                            │
│    ⑤ Flux<String> 逐字返回 → 前端 EventSource 接收           │
│    ⑥ 流结束 → saveToVectorStore() → 存 PgVector              │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                   PostgreSQL :5433 (Docker)                  │
│                                                             │
│  vector_store 表：                                           │
│    ├─ 知识库文档（启动时从 .md 加载）                         │
│    └─ 历史对话（每次聊天后追加）                               │
│                                                             │
│  tmp/chat-memory/：                                         │
│    └─ love_user001.kryo（本次对话写入）                       │
└─────────────────────────────────────────────────────────────┘
```

---

## 五、关键技术点总结

| 技术点 | 代码位置 | 面试一句话 |
|------|------|------|
| SSE 流式 | `.stream().content()` | Spring AI 流式推送，前端 EventSource 实时显示 |
| 对话记忆 | `FileBasedChatMemory` + `MessageChatMemoryAdvisor` | 基于 Kryo 序列化的文件持久化记忆，重启不丢 |
| RAG 检索 | `RetrievalAugmentationAdvisor` + `VectorStoreDocumentRetriever` | PgVector 向量检索，阈值 0.5 + TopK 3 |
| Query 改写 | `QueryRewriter.doQueryRewrite()` | LLM 改写口语化问题，提升检索命中率 |
| 改写策略 | `rewriteIfNeeded()` | 短问题改写，长问题直接用，省 token |
| Re2 推理 | `ReReadingAdvisor` | 问题复述增强，减少 LLM 遗漏关键条件 |
| 知识库存对话 | `saveToVectorStore()` | 高质量问答回流向量库，知识库自我进化 |
| 质量过滤 | `answer.length() < 20` → 跳过 | 过滤低质量数据，防止向量库污染 |
| 状态过滤 | `Filter.Expression.eq("status", status)` | 按单身/恋爱/已婚分类检索 |