
# `/ai/manus/stream` 接口全链路详解

---

## 📋 目录

1. [全链路概览](#-全链路概览)
2. [前端层](#-前端层)
3. [后端接口层](#-后端接口层)
4. [智能体层](#-智能体层)
5. [配置层](#-配置层)
6. [依赖层](#-依赖层)
7. ["用" vs "不用" 对比](#-用-vs-不用-对比)

---

## 🗺️ 全链路概览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  前端（Vue）                                                                 │
│  ┌───────────────┐     ┌───────────────┐     ┌───────────────────────┐      │
│  │  App.vue      │     │  ChatRoom.vue │     │  api/index.js         │      │
│  │ （4个选项卡） │────▶│ （SSE聊天）   │────▶│ （EventSource调用）    │      │
│  └───────────────┘     └───────────────┘     └───────────────────────┘      │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  后端（Spring Boot）                                                          │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ AiController2.java → /manus/stream 接口（接收userId + message）        │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                      │                                      │
│                                      ▼                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ YangManus.java（@Component 超级智能体）                                 │  │
│  │ 继承链：YangManus（最终业务智能体，你的小羊助手）→ ToolCallAgent
	（实现工具调用→ ReActAgent（拆分思考+行动） → BaseAgent（最顶层抽象骨架）             │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                      │                                      │
│                                      ▼                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ BaseAgent.runStream() → 异步执行，SseEmitter 流式返回                  │  │
│  │ ① reset(conversationId)：从 ChatMemory 加载历史对话                    │  │
│  │ ② 循环：for (0..maxSteps) → step() → think() → act()                  │  │
│  │ ③ saveConversationToVectorStore()：保存对话到向量库（长期记忆）        │  │
│  │ ④ sseEmitter.send(finalAnswer)：只给前端返回最终干净回答              │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                      │                                      │
│                                      ▼                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ ToolCallingManager.executeToolCalls()：Spring AI 工具执行器           │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                      │                                      │
│                                      ▼                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ ToolConfig.java → ToolCallback[]：所有注册的本地工具                   │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🎨 前端层

### 1. `admin/agent-hub/src/App.vue`

| 作用 | 为什么这样做 | "不用"会怎样 |
|------|--------------|--------------|
| 4个选项卡切换：Agent / ⚡ 超级智能体 / 💕 恋爱顾问 / 🦙 Ollama | 功能分区清晰，用户体验好 | 只有单一模式，无法区分不同智能体 |

```vue
<template>
  <div class="app">
    <nav class="navbar">
      <span class="logo"> AI Agent Hub</span>
      <div class="tabs">
        <!-- 每个选项卡对应不同智能体 -->
        <button :class="['tab', { active: mode === 'agent' }]" @click="mode = 'agent'"> Agent 模式</button>
        <button :class="['tab', { active: mode === 'manus' }]" @click="mode = 'manus'">⚡ 超级智能体</button>
        <button :class="['tab', { active: mode === 'love' }]" @click="mode = 'love'">💕 恋爱顾问</button>
        <button :class="['tab', { active: mode === 'ollama' }]" @click="mode = 'ollama'">🦙 Ollama</button>
      </div>
    </nav>
    <ChatRoom :mode="mode" />
  </div>
</template>
```

---

### 2. `admin/agent-hub/src/components/ChatRoom.vue`

| 作用 | 为什么这样做 | "不用"会怎样 |
|------|--------------|--------------|
| 聊天界面渲染（消息列表 / 输入框 / 发送按钮） | 用户直接交互的核心界面 | 没有聊天界面，只能用 curl/postman |
| SSE 流式接收处理（`EventSource`） | 实时接收后端返回，不用等全部完成 | 只能一次性返回，用户体验差 |
| `userId` 会话隔离（`localStorage` 持久化） | 不同用户对话上下文完全独立，刷新不丢失 | 用户会话互相污染，刷新就丢历史 |

```javascript
// 核心：SSE 流式调用
const sendManusStream = (userMsg) => {
  const assistantMsg = { role: 'assistant', content: '' }
  messages.value.push(assistantMsg)

  currentEventSource = streamManus(
      userMsg,
      userId.value,  // 🔥 关键：会话隔离
      (data) => {
        // ✅ 只追加有效内容，过滤空/技术细节
        if (data &amp;&amp; data.trim() &amp;&amp; !data.startsWith('Step') &amp;&amp; !data.includes('工具【')) {
          assistantMsg.content += data
          scrollToBottom()
        }
      },
      () => { loading.value = false },
      (e) => { assistantMsg.content = '网络出错了，请稍后再试' }
  )
}
```

---

### 3. `admin/agent-hub/src/api/index.js`

| 作用 | 为什么这样做 | "不用"会怎样 |
|------|--------------|--------------|
| `chat()`：普通聊天接口（POST） | 统一 API 封装，复用性好 | 每个组件直接写 axios，代码重复 |
| `streamManus()`：SSE 流式接口（GET） | 用浏览器原生 `EventSource` 监听 SSE 流 | 只能用轮询，资源浪费/延迟高 |

```javascript
import axios from 'axios'
const BASE_URL = 'http://localhost:8090/ai'

// 普通聊天
export const chat = (data, userId = 'user001') => {
  return axios.post(`${BASE_URL}/chat?userId=${userId}`, data).then(res => res.data)
}

// SSE 流式调用（核心）
export const streamManus = (message, userId = 'user001', onData, onDone, onError) => {
  const url = `${BASE_URL}/manus/stream?userId=${userId}&amp;message=${encodeURIComponent(message)}`
  const eventSource = new EventSource(url)  // 浏览器原生 SSE 客户端

  eventSource.onmessage = (event) => onData(event.data)
  eventSource.onerror = (e) => {
    eventSource.close()
    if (eventSource.readyState === EventSource.CLOSED) onDone()
    else onError(e)
  }
  return eventSource
}
```

---

## 🌐 后端接口层

### 4. `AiController2.java` → `/ai/manus/stream`

| 作用 | 为什么这样做 | "不用"会怎样 |
|------|--------------|--------------|
| 接收 `userId`（会话ID）和 `message`（用户输入） | 隔离不同用户上下文 | 所有用户共用一个会话，互相串台 |
| `@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)` | 告诉浏览器这是 SSE 流式响应，保持连接 | 浏览器会当成普通 JSON 处理，无法实时接收 |
| 返回 `SseEmitter` | Spring MVC 提供的标准 SSE 返回类型 | 只能一次性返回，无法流式推送 |

```java
@RestController
@RequestMapping("/ai")
public class AiController2 {

    // 注入你的超级智能体
    @Resource
    private YangManus yangManus;

    /**
     * 🔥 核心接口：SSE 流式调用 YangManus
     */
    @GetMapping(value = "/manus/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE流式调用YangManus超级智能体 9")
    public SseEmitter doChatWithManus(
            // 🔥 key1: userId 隔离不同用户会话
            @RequestParam(defaultValue = "user001") String userId,
            @RequestParam String message
    ) {
        // 直接调用智能体的流式方法
        return yangManus.runStream(userId, message);
    }
}
```

---

## 🤖 智能体层

### 5. `BaseAgent.java`（基类）

| 作用 | 为什么这样做 | "不用"会怎样 |
|------|--------------|--------------|
| 状态机管理：`IDLE`（空闲） → `RUNNING`（运行中） → `FINISHED`/`ERROR` | 防止并发冲突，同一时间只能执行一个任务 | 多个请求同时进来，状态混乱崩溃 |
| `reset(conversationId)`：从 `ChatMemory` 加载历史对话 | 多轮对话有上下文 | 每次都是全新对话，"刚才你说的..."失效 |
| `saveConversationToVectorStore()`：保存到向量库 | 长期记忆，可检索历史对话 | 重启就丢，无法回忆很久之前的对话 |
| `runStream()`：异步执行 + `SseEmitter` 流式返回 | 前端实时看到结果，不阻塞主线程 | 前端长时间等待，体验差 |

```java
public abstract class BaseAgent {
    private String name;
    private String systemPrompt;
    private AgentState state = AgentState.IDLE;
    private int currentStep = 0;
    private int maxSteps = 5;
    private ChatClient chatClient;
    private List<Message> messageList = new ArrayList<>();
    private ChatMemory chatMemory;
    private VectorStore vectorStore;

    /**
     * 核心：流式执行入口
     */
    public SseEmitter runStream(String conversationId, String userPrompt) {
        reset(conversationId);  // 先加载历史对话
        SseEmitter sseEmitter = new SseEmitter(300000L);  // 5分钟超时

        // 🔥 异步执行，不阻塞主线程
        CompletableFuture.runAsync(() -> {
            try {
                this.state = AgentState.RUNNING;
                messageList.add(new UserMessage(userPrompt));
                String finalAnswer = "";

                // 多步循环：think() → act()
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    currentStep = i + 1;
                    String stepResult = step();
                    if (StrUtil.isNotBlank(stepResult) && state == AgentState.FINISHED) {
                        finalAnswer = stepResult;
                    }
                }

                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    finalAnswer = "任务终止：已达到最大步骤数(" + maxSteps + ")";
                }

                // ✅ 只返回最终干净回答
                sseEmitter.send(finalAnswer);

                // 保存到记忆
                saveConversationToVectorStore(userPrompt, finalAnswer);
                if (chatMemory != null) {
                    chatMemory.add(conversationId, new UserMessage(userPrompt));
                    chatMemory.add(conversationId, new AssistantMessage(finalAnswer));
                }

                sseEmitter.complete();
            } catch (Exception e) {
                // 异常处理...
            } finally {
                cleanup();
            }
        });

        return sseEmitter;
    }

    public abstract String step();
}
```

---

### 6. `ReActAgent.java`（ReAct 模式抽象）

| 作用 | 为什么这样做 | "不用"会怎样 |
|------|--------------|--------------|
| 拆分 `think()`（思考）和 `act()`（行动） | 符合 ReAct（Reasoning + Acting）论文，逻辑清晰 | 所有代码揉在一起，难以维护 |
| 实现 `step()`：统一先 `think()` 再决定是否 `act()` | 流程标准化 | 每个智能体自己写一遍逻辑，代码重复 |

```java
public abstract class ReActAgent extends BaseAgent {
    // 思考：是否需要调用工具
    public abstract boolean think();

    // 行动：执行具体操作
    public abstract String act();

    // 统一流程
    @Override
    public String step() {
        boolean shouldAct = think();
        if (!shouldAct) return "思考完成，无需执行任何操作";
        return act();
    }
}
```

---

### 7. `ToolCallAgent.java`（工具调用核心）

| 作用 | 为什么这样做 | "不用"会怎样 |
|------|--------------|--------------|
| `think()`：问大模型要不要调用工具 | AI 自主决策，不用硬编码 | 只能写死规则，不灵活 |
| `act()`：用 `ToolCallingManager` 执行工具 | Spring AI 提供的标准工具执行器 | 自己写工具调用逻辑，容易出错 |
| `DashScopeChatOptions.builder().withInternalToolExecutionEnabled(false)` | 关闭自动执行，**手动控制工具执行流程** | AI 自动执行工具，无法插入自定义逻辑 |

```java
public abstract class ToolCallAgent extends ReActAgent {
    private final ToolCallback[] availableTools;
    private ChatResponse toolCallChatResponse;
    private final ToolCallingManager toolCallingManager;
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools) {
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 🔥 关键：关闭自动执行，由我们手动控制
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 思考：AI 决定要不要调用工具
     */
    @Override
    public boolean think() {
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        ChatResponse chatResponse = getChatClient().prompt(prompt)
                .system(getSystemPrompt())
                .toolCallbacks(availableTools)  // 传入所有可用工具
                .call()
                .chatResponse();
        this.toolCallChatResponse = chatResponse;

        AssistantMessage assistantMsg = chatResponse.getResult().getOutput();
        getMessageList().add(assistantMsg);  // 必须存上下文

        List<AssistantMessage.ToolCall> toolCallList = assistantMsg.getToolCalls();
        boolean hasToolCalls = !toolCallList.isEmpty();

        if (!hasToolCalls) {
            setState(AgentState.FINISHED);  // 无工具直接结束
            return false;
        }
        return true;
    }

    /**
     * 行动：执行工具调用
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            // 返回干净的最终回答（去掉技术细节）
            AssistantMessage lastAiMsg = (AssistantMessage) getMessageList().stream()
                    .filter(m -> m instanceof AssistantMessage)
                    .reduce((first, second) -> second)
                    .orElse(null);
            return lastAiMsg != null ?
                    lastAiMsg.getText()
                            .replaceAll("\\{.*?\\}", "")
                            .replace("doTerminate", "")
                            .trim() : "暂无回答";
        }
        try {
            // 🔥 Spring AI 工具执行管理器
            ToolExecutionResult result = toolCallingManager.executeToolCalls(
                    new Prompt(getMessageList(), this.chatOptions),
                    toolCallChatResponse
            );
            setMessageList(result.conversationHistory());

            // 检查是否是终止工具
            ToolResponseMessage toolResponse = (ToolResponseMessage) getMessageList().get(getMessageList().size() - 1);
            boolean isTerminate = toolResponse.getResponses().stream()
                    .anyMatch(res -> "doTerminate".equals(res.name()));
            if (isTerminate) {
                setState(AgentState.FINISHED);
                // 返回最终回答...
            }
            return "";  // 继续下一步
        } catch (Exception e) {
            setState(AgentState.FINISHED);
            return "⚠️ 工具执行失败";
        }
    }
}
```

---

### 8. `YangManus.java`（你的超级智能体）

| 作用 | 为什么这样做 | "不用"会怎样 |
|------|--------------|--------------|
| `@Component`：Spring 单例 Bean | Spring 容器管理，可直接 `@Resource` 注入 | 每次 `new YangManus()`，依赖无法自动注入 |
| 注入 `ChatModel` / `ChatMemory` / `VectorStore` / `ToolCallback[]` | 所有依赖由 Spring 统一管理 | 手动传参，耦合严重 |
| 超长 `systemPrompt` + `nextStepPrompt` | 定义智能体人设/规则，约束 AI 行为 | AI 瞎回答/不调用工具 |

```java
@Component
public class YangManus extends ToolCallAgent {
    public YangManus(
            ToolCallback[] allTools,
            ChatModel dashscopeChatModel,
            ChatMemory chatMemory,
            VectorStore vectorStore
    ) {
        super(allTools);
        this.setName("YangManus");
        this.setMaxSteps(5);
        this.setChatMemory(chatMemory);
        this.setVectorStore(vectorStore);
        this.setChatClient(ChatClient.builder(dashscopeChatModel).build());

        // 定义你的人设和规则
        this.setSystemPrompt("""
            你是超级智能助手 小羊~，专业、严谨、守规则...
        """);
        this.setNextStepPrompt("""
            1. 用户闲聊无需调用工具，立刻调用 doTerminate 结束...
        """);
    }
}
```

---

## ⚙️ 配置层

### 9. `AgentConfig.java`

| 作用 | 为什么这样做 | "不用"会怎样 |
|------|--------------|--------------|
| `@Configuration`：Spring 配置类 | 集中管理 Bean 注册 | Bean 到处 `@Component`，难以维护 |
| `@Bean` 注册 `YangManus`，注入 4 个依赖 | Spring 自动注入，无需手动传 | 每次 `new YangManus()` 要写一堆参数 |
| `@Qualifier("dashscopeChatModel")` | 明确指定用阿里云百炼，不用 Ollama | 两个 `ChatModel`，Spring 不知道选哪个 |

```java
@Configuration
public class AgentConfig {
    @Bean
    public YangManus yangManus(
            ToolCallback[] toolCallbacks,      // 所有工具
            @Qualifier("dashscopeChatModel") ChatModel chatModel,  // 大模型
            ChatMemory chatMemory,              // 上下文记忆
            VectorStore vectorStore             // 向量库
    ) {
        return new YangManus(toolCallbacks, chatModel, chatMemory, vectorStore);
    }
}
```

---

### 10. `ToolConfig.java`（工具注册）

| 作用 | 为什么这样做 | "不用"会怎样 |
|------|--------------|--------------|
| 所有 `@Component` 工具类 → `ToolCallbacks.from()` | 自动扫描 `@Tool` 注解的方法，注册成 `ToolCallback[]` | 每个工具手动写 `FunctionToolCallback`，代码巨多 |

```java
@Configuration
public class ToolConfig {
    @Bean
    public ToolCallback[] toolCallbacks(
            MyTools myTools,
            KnowledgeTools knowledgeTools,
            FileOperationTool fileOperationTool,
            WebSearchTool webSearchTool,
            WebScrapingTool webScrapingTool,
            ResourceDownloadTool resourceDownloadTool,
            TerminalOperationTool terminalOperationTool,
            TerminateTool terminateTool,
            ImageSearchTool imageSearchTool
    ) {
        // 🔥 Spring AI 工具自动注册器
        return ToolCallbacks.from(
                myTools,
                knowledgeTools,
                fileOperationTool,
                webSearchTool,
                webScrapingTool,
                resourceDownloadTool,
                terminalOperationTool,
                terminateTool,
                imageSearchTool
        );
    }
}
```

---

## 📦 依赖层

### 11. `pom.xml` 核心依赖

| 依赖 | 作用 | "不用"会怎样 |
|------|------|--------------|
| `spring-boot-starter-parent:3.4.4` | Spring Boot 父 POM，版本管理 | 自己管理所有 Spring Boot 依赖版本，容易冲突 |
| `spring-ai-alibaba-bom:1.0.0.2` / `spring-ai-bom:1.0.0` | Spring AI + Spring AI Alibaba **统一版本管理** | 依赖版本不兼容，各种 `NoClassDefFoundError` |
| `spring-ai-alibaba-starter-dashscope` | 阿里云百炼大模型集成 | 无法调用通义千问 |
| `spring-ai-starter-model-ollama` | Ollama 本地大模型集成 | 无法调用本地模型 |
| `spring-ai-starter-vector-store-pgvector` | PgVector 向量库（RAG 用） | 无法做长期记忆/语义检索 |
| `spring-boot-starter-web` | Web 服务（`@RestController` / `SseEmitter`） | 没有 HTTP 接口 |
| `lombok` | 自动生成 getter/setter/toString | 写一堆样板代码 |
| `hutool-all` | 工具库（文件读写/字符串/IO 等） | 自己写各种工具方法，容易出错 |
| `jsoup` | 网页抓取 | 无法爬网页内容 |
| `knife4j-openapi3-jakarta-spring-boot-starter` | 接口文档（Knife4j / Swagger） | 不知道接口怎么调，只能看代码 |

```xml
<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>1.18.36</version>
        <optional>true</optional>
    </dependency>
    <!-- Spring AI Alibaba 阿里云百炼 -->
    <dependency>
        <groupId>com.alibaba.cloud.ai</groupId>
        <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
    </dependency>
    <!-- Knife4j 接口文档 -->
    <dependency>
        <groupId>com.github.xiaoymin</groupId>
        <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
        <version>4.4.0</version>
    </dependency>
    <!-- PgVector 向量库 -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
    </dependency>
    <!-- Ollama 本地大模型 -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-ollama</artifactId>
    </dependency>
    <!-- Hutool 工具库 -->
    <dependency>
        <groupId>cn.hutool</groupId>
        <artifactId>hutool-all</artifactId>
        <version>5.8.37</version>
    </dependency>
    <!-- Jsoup 网页抓取 -->
    <dependency>
        <groupId>org.jsoup</groupId>
        <artifactId>jsoup</artifactId>
        <version>1.19.1</version>
    </dependency>
</dependencies>
```

---

## ⚖️ "用" vs "不用" 对比

| 维度 | 用（当前方案） | 不用（原始版） |
|------|----------------|----------------|
| 对话记忆 | ✅ 有（`ChatMemory` + `VectorStore`） | ❌ 无，每次都是全新对话 |
| 会话隔离 | ✅ `userId` 完全隔离 | ❌ 所有用户共用一个会话 |
| 输出方式 | ✅ SSE 流式实时返回 | ❌ 同步等待，体验差 |
| 智能体状态 | ✅ 状态机（`IDLE`/`RUNNING`/`FINISHED`） | ❌ 无状态，并发混乱 |
| 工具调用 | ✅ AI 自主决策，多步循环 | ❌ 单次调用，无法复杂任务 |
| 代码结构 | ✅ 分层清晰（基类/抽象/实现） | ❌ 所有代码揉在一起 |
| 依赖管理 | ✅ Spring 容器统一注入 | ❌ 手动传参，耦合严重 |
| 最终回答 | ✅ 干净（过滤技术细节） | ❌ 充斥 JSON/工具调用过程 |

---

## 🎯 总结

这个链路从 **前端 UI → API 层 → 智能体层 → Spring AI → 大模型/工具**，每一层都有明确的职责，通过 Spring 的依赖注入、状态管理、流式响应等特性，实现了一个**企业级、可维护、多用户隔离、有记忆的 AI 智能体**。

