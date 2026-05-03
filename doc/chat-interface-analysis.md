# POST /ai/chat 接口全链路解析

> 当前端调用 `POST http://localhost:8090/ai/chat` 时，整个系统到底做了什么？

---

## 一、接口入口

```
POST /ai/chat
Content-Type: application/json
```

**请求体：**

```json
{
  "message": "查询北京天气",
  "mode": "agent",
  "userId": "user001"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| message | String | 用户输入的消息 |
| mode | String | 模式选择：`agent` / `love` / `ollama` |
| userId | String | 用户ID（可选） |

**控制器代码**（AiController2.java）：

```java
@PostMapping("/chat")
public String chat(@RequestBody ChatRequest request) {
    if ("agent".equals(request.getMode())) {
        return reActAgent.execute(request.getMessage());
    } else if ("love".equals(request.getMode())) {
        return loveAdvisorService.chat(request.getMessage());
    } else if ("ollama".equals(request.getMode())) {
        return ollamaService.fullAgentChat(request.getMessage());
    }
    return "";
}
```

**做了什么：** 根据 `mode` 字段路由到三个不同的服务，每个模式走完全不同的链路。

---

## 二、三种模式的完整链路

### 模式1：agent — MCP 远程工具调用

> 适用场景：需要查询天气、翻译、新闻等外部数据

```
前端请求 → AiController2.chat() → ReActAgent.execute() → ChatClient.prompt()
    → .toolCallbacks(mcpToolProvider)  ← 关键：把 MCP 工具注册给 AI
    → AI 决定是否调用工具
    → 如果调用：MCP Client → stdio 管道 → yang-mcp-server 子进程 → 执行工具方法 → 返回结果
    → AI 整合结果生成最终回答
```

#### 涉及的代码文件和作用

| 文件 | 作用 | 为什么需要 |
|------|------|-----------|
| `ChatRequest.java` | 接收前端参数（message, mode, userId） | 统一请求格式，方便扩展 |
| `AiController2.java` | 路由分发，根据 mode 调用不同服务 | 一个接口支持多种模式，前端只需调一个 URL |
| `ReActAgent.java` | Agent 核心：组装 system prompt + MCP 工具 + 记忆，调用 AI | 把"大脑"和"工具箱"组合在一起 |
| `BaseAgent.java` | Agent 基类，定义 execute 抽象方法和日志工具 | 统一 Agent 的接口规范 |
| `AgentConfig.java` | Spring 配置类，创建 ChatClient、ReActAgent 等 Bean | 依赖注入，把各组件组装好 |
| `ToolConfig.java` | 注册本地工具（当前为空数组） | 预留扩展点，以后可以加本地工具 |
| `AiService.java` | 提供 chatWithMcp() 方法，直接用 MCP 工具对话 | 简化版 MCP 调用，不走 Agent |

#### MCP 专属依赖链

当 AI 需要调用 MCP 工具时，涉及以下依赖和代码：

| 依赖/文件 | 作用 | 为什么需要 |
|-----------|------|-----------|
| `spring-ai-starter-mcp-client`（pom.xml） | MCP 客户端 starter | 自动配置 MCP Client，连接 MCP Server |
| `application-dev.yml` 中的 `spring.ai.mcp.client.stdio` | 告诉 MCP Client 用 stdio 方式连接 | stdio 是最简单的进程间通信方式，不需要额外端口 |
| `mcp-servers.json` | 定义 MCP Server 的启动命令 | MCP Client 根据这个配置启动子进程 |
| `ToolCallbackProvider`（Spring 自动注入） | MCP 工具提供者，包含所有远程工具 | Spring AI 自动把 MCP 工具封装成 ToolCallback |
| `yang-mcp-server/` 整个子模块 | MCP Server 端，提供天气/翻译/新闻工具 | 独立进程，通过 stdio 与主项目通信 |
| `application-stdio.yml`（yang-mcp-server） | MCP Server 的 stdio 模式配置 | 告诉 MCP Server 用 stdio 通信，关闭 Web 服务器 |
| `WeatherTool.java` | 天气查询工具（@Tool + @ToolParam） | MCP Server 自动发现并注册为远程工具 |
| `TranslateTool.java` | 翻译工具 | 同上 |
| `NewsTool.java` | 新闻搜索工具 | 同上 |

#### 完整调用流程图（以"查询北京天气"为例）

```
1. 前端发送 POST /ai/chat  { message: "查询北京天气", mode: "agent" }

2. AiController2.chat()
   └─ mode="agent" → reActAgent.execute("查询北京天气")

3. ReActAgent.execute()
   ├─ mcpToolProvider.getToolCallbacks()  → 获取 MCP 工具列表
   │   └─ [getWeather, translate, searchNews]  ← 来自 yang-mcp-server
   ├─ 构建 system prompt："你是智能助手小智，可以使用 MCP 远程工具..."
   └─ chatClient.prompt()
        .system(systemPrompt)
        .user("查询北京天气")
        .toolCallbacks(mcpToolProvider)   ← 注册 MCP 工具
        .advisors(MessageChatMemoryAdvisor)  ← 短期记忆
        .call()

4. AI 模型（qwen-plus）收到请求，分析：
   "用户问天气 → 我有 getWeather 工具 → 调用它"
   → 返回 tool_call: { name: "getWeather", arguments: { city: "北京" } }

5. Spring AI 框架自动执行 tool_call：
   ├─ MCP Client 将调用请求序列化为 JSON
   ├─ 通过 stdin 管道发送给 yang-mcp-server 子进程
   ├─ yang-mcp-server 收到请求，找到 WeatherTool.getWeather("北京")
   ├─ WeatherTool 调用 https://wttr.in/北京?format=...&lang=zh
   ├─ 获取天气数据："北京 当前天气：晴 +25°C ..."
   ├─ yang-mcp-server 将结果序列化为 JSON，写回 stdout
   └─ MCP Client 从 stdin 读到结果

6. Spring AI 将工具结果发回 AI 模型：
   "工具 getWeather 返回：北京 当前天气：晴 +25°C ..."
   → AI 整合信息，生成最终回答

7. 返回给前端：
   "北京当前天气：晴，温度 25°C，湿度 45%，风速 10km/h"
```

---

### 模式2：love — 恋爱顾问（RAG 知识库）

> 适用场景：恋爱咨询，基于知识库回答

```
前端请求 → AiController2.chat() → LoveAdvisorService.chat()
    → LoveDocumentLoader.search()  ← 从 PGVector 检索相关知识
    → 构建 system prompt + 知识库上下文
    → ChatClient.prompt().call()   ← AI 基于知识库回答
    → vectorStore.add()            ← 把对话存入知识库（长期记忆）
```

#### 涉及的代码文件

| 文件 | 作用 |
|------|------|
| `LoveAdvisorService.java` | 恋爱顾问核心：检索知识库 + AI 生成回答 + 存储对话 |
| `LoveDocumentLoader.java` | 文档加载器：加载恋爱知识到向量库 + 关键词搜索 |
| `VectorStore`（PGVector） | 向量数据库：存储和检索知识 |
| `ChatClient.Builder` | AI 对话客户端构建器 |

#### 不涉及 MCP

此模式只使用本地知识库（PGVector），不调用 MCP 远程工具。

---

### 模式3：ollama — 本地大模型 + MCP

> 适用场景：使用本地 Ollama 模型，同时可以调用 MCP 工具

```
前端请求 → AiController2.chat() → OllamaService.fullAgentChat()
    → LoveDocumentLoader.search()  ← RAG 检索
    → ChatClient.builder(ollamaChatModel).build()  ← 用本地模型
    → .toolCallbacks(toolCallbackProvider)  ← 也能调用 MCP 工具
    → .advisors(MessageChatMemoryAdvisor)   ← 短期记忆
    → vectorStore.add()            ← 存入长期记忆
```

#### 涉及的代码文件

| 文件 | 作用 |
|------|------|
| `OllamaService.java` | 本地模型服务：Ollama + RAG + MCP + 记忆 |
| `LoveDocumentLoader.java` | 文档加载器 |
| `VectorStore`（PGVector） | 向量数据库 |
| `ToolCallbackProvider` | MCP 工具提供者（同 agent 模式） |

#### 与 agent 模式的区别

| 对比项 | agent 模式 | ollama 模式 |
|--------|-----------|-------------|
| AI 模型 | 阿里云 DashScope（qwen-plus） | 本地 Ollama（qwen2.5:0.5b） |
| MCP 工具 | ✅ 支持 | ✅ 支持 |
| RAG 知识库 | ❌ 不使用 | ✅ 使用 |
| 短期记忆 | ✅ ChatMemory | ✅ ChatMemory |
| 长期记忆 | ❌ 不存储 | ✅ 存入 VectorStore |
| 网络依赖 | 需要网络 | 不需要网络（本地运行） |

---

## 三、MCP 核心依赖详解

### 3.1 Maven 依赖

```xml
<!-- 主项目 pom.xml -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```

**作用：** 引入 Spring AI MCP Client 自动配置。启动时自动：
1. 读取 `mcp-servers.json` 配置
2. 启动 MCP Server 子进程
3. 通过 stdio 管道建立通信
4. 获取 MCP Server 提供的工具列表
5. 将工具封装为 `ToolCallbackProvider` Bean

### 3.2 MCP 客户端配置

```yaml
# application-dev.yml
spring:
  ai:
    mcp:
      client:
        stdio:
          servers-configuration: classpath:mcp-servers.json
```

**为什么用 stdio？**
- 不需要额外端口，避免端口冲突
- 进程间通信最简单的方式
- MCP Server 作为主进程的子进程，生命周期由主进程管理

### 3.3 MCP Server 配置

```json
// mcp-servers.json
{
  "mcpServers": {
    "yang-tools": {
      "command": "java",
      "args": [
        "-Dspring.ai.mcp.server.stdio=true",
        "-Dspring.main.web-application-type=none",
        "-Dlogging.pattern.console=",
        "-jar",
        "yang-mcp-server/target/yang-mcp-server-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}
```

**每个参数的作用：**

| 参数 | 作用 |
|------|------|
| `-Dspring.ai.mcp.server.stdio=true` | 告诉 MCP Server 用 stdio 通信 |
| `-Dspring.main.web-application-type=none` | 不启动 Tomcat（不需要 HTTP） |
| `-Dlogging.pattern.console=` | 清空日志格式，避免干扰 stdio 通信 |
| `-jar yang-mcp-server/target/...jar` | 启动 MCP Server 的 jar 包 |

### 3.4 MCP Server 端配置

```yaml
# yang-mcp-server/application-stdio.yml
spring:
  ai:
    mcp:
      server:
        name: yang-mcp-server    # MCP Server 名称
        version: 0.0.1           # 版本号
        type: SYNC               # 同步模式
        stdio: true              # 使用 stdio 通信
  main:
    web-application-type: none   # 不启动 Web 服务器
    banner-mode: off             # 关闭启动 Banner
```

**为什么需要这些配置？**
- `name` 和 `version`：MCP 协议要求 Server 声明自己的身份
- `type: SYNC`：同步执行工具调用，简单可靠
- `stdio: true`：与客户端的 stdio 配置对应
- `web-application-type: none`：stdio 模式不需要 HTTP 服务器

### 3.5 MCP 工具注册方式

```java
// yang-mcp-server/WeatherTool.java
@Service   // ← Spring 自动扫描
public class WeatherTool {

    @Tool(description = "查询指定城市的实时天气情况")  // ← MCP 自动发现
    public String getWeather(
        @ToolParam(description = "城市名称，如 北京、上海") String city
    ) {
        // 调用 wttr.in API 获取天气
    }
}
```

**关键点：**
- `@Service`：让 Spring 管理这个 Bean
- `@Tool`：Spring AI MCP Server 自动扫描所有 `@Tool` 注解的方法，注册为 MCP 工具
- `@ToolParam`：描述参数，帮助 AI 理解如何传参
- **不需要手动注册**：新版 Spring AI MCP Server 自动发现 `@Tool` 方法

### 3.6 主项目如何使用 MCP 工具

```java
// ReActAgent.java — 关键代码
chatClient.prompt()
    .system(systemPrompt)
    .user(userInput)
    .toolCallbacks(mcpToolProvider)   // ← 传入 MCP 工具提供者
    .call()
    .content();
```

**为什么用 `.toolCallbacks(mcpToolProvider)` 而不是手动合并？**

| 方式 | 说明 | 问题 |
|------|------|------|
| ❌ 手动合并 ToolCallback[] | 先 getToolCallbacks() 再合并 | 启动时 MCP 还没就绪，返回空数组 |
| ❌ .tools(mcpToolProvider) | 传入 ToolCallbackProvider | 内部尝试找 @Tool 方法，找不到报错 |
| ✅ .toolCallbacks(mcpToolProvider) | 传入 ToolCallbackProvider | Spring AI 自动从 Provider 获取工具，延迟到调用时才解析 |

---

## 四、话题举例

### 举例1：查询天气（触发 MCP 工具调用）

```
用户输入：查询北京天气
模式：agent

链路：
1. AiController2 → ReActAgent.execute("查询北京天气")
2. ReActAgent 注册 MCP 工具（getWeather, translate, searchNews）
3. AI 分析：需要调用 getWeather 工具
4. MCP Client → yang-mcp-server → WeatherTool.getWeather("北京")
5. WeatherTool 调用 https://wttr.in/北京?format=...&lang=zh
6. 返回："北京 当前天气：晴 +25°C"
7. AI 整合回答："北京当前天气晴朗，温度25°C..."
```

### 举例2：翻译文本（触发 MCP 工具调用）

```
用户输入：把"我爱你"翻译成日语
模式：agent

链路：
1. AiController2 → ReActAgent.execute("把'我爱你'翻译成日语")
2. AI 分析：需要调用 translate 工具
3. MCP Client → yang-mcp-server → TranslateTool.translate("我爱你", "Japanese")
4. TranslateTool 调用 MyMemory API
5. 返回："[Japanese] 愛しています"
6. AI 整合回答：""我爱你"的日语翻译是"愛しています"..."
```

### 举例3：恋爱咨询（不触发 MCP，走 RAG）

```
用户输入：不敢表白怎么办
模式：love

链路：
1. AiController2 → LoveAdvisorService.chat("不敢表白怎么办")
2. LoveDocumentLoader.search() → 从 PGVector 检索相关知识
3. 找到相关文档："表白技巧：选择合适的时机..."
4. 构建 prompt：系统角色 + 知识库内容 + 用户问题
5. AI 基于知识库回答（不调用任何 MCP 工具）
6. 将对话存入 VectorStore（长期记忆）
```

### 举例4：本地模型 + MCP（ollama 模式）

```
用户输入：今天新闻有什么
模式：ollama

链路：
1. AiController2 → OllamaService.fullAgentChat("今天新闻有什么")
2. LoveDocumentLoader.search() → RAG 检索（可能没有相关内容）
3. 本地 Ollama 模型 + MCP 工具 → AI 决定调用 searchNews
4. MCP Client → yang-mcp-server → NewsTool.searchNews("科技", 5)
5. 返回新闻列表
6. AI 整合回答
7. 存入 VectorStore
```

### 举例5：普通聊天（不触发任何工具）

```
用户输入：你好
模式：agent

链路：
1. AiController2 → ReActAgent.execute("你好")
2. AI 分析：不需要调用任何工具，直接回答
3. 返回："你好！我是智能助手小智，有什么可以帮你的？"
```

---

## 五、架构总览图

```
┌──────────────────────────────────────────────────────────────┐
│                      前端 (Vue 3)                             │
│  POST /ai/chat  { message: "...", mode: "agent/love/ollama" }│
└──────────────────────────┬───────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                   AiController2.java                          │
│                                                               │
│   mode="agent" ──→ ReActAgent.execute()                      │
│   mode="love"  ──→ LoveAdvisorService.chat()                 │
│   mode="ollama"──→ OllamaService.fullAgentChat()             │
└──────────┬───────────────┬────────────────┬──────────────────┘
           │               │                │
           ▼               ▼                ▼
    ┌──────────┐   ┌──────────────┐  ┌──────────────┐
    │ ReActAgent│   │LoveAdvisor   │  │OllamaService │
    │           │   │Service       │  │              │
    │ ChatClient│   │ ChatClient   │  │ ChatClient   │
    │ (DashScope)│  │ (DashScope)  │  │ (Ollama)     │
    │           │   │              │  │              │
    │ MCP工具 ✅│   │ RAG知识库 ✅ │  │ MCP工具 ✅   │
    │ 记忆 ✅   │   │ 长期记忆 ✅  │  │ RAG ✅       │
    │           │   │              │  │ 记忆 ✅      │
    └─────┬─────┘   └──────┬───────┘  └──────┬───────┘
          │                │                  │
          │    ┌───────────┘                  │
          ▼    ▼                              ▼
    ┌──────────────────────────────────────────────┐
    │              Spring AI 框架层                  │
    │                                               │
    │  ChatClient ─── ToolCallbackProvider(MCP)     │
    │  ChatMemory ─── VectorStore(PGVector)         │
    │  DashScope  ─── Ollama                        │
    └──────────────────────┬───────────────────────┘
                           │ MCP stdio 管道
                           ▼
    ┌──────────────────────────────────────────────┐
    │          yang-mcp-server (子进程)              │
    │                                               │
    │  WeatherTool  ── getWeather(city)             │
    │  TranslateTool── translate(text, lang)        │
    │  NewsTool     ── searchNews(keyword, limit)   │
    │                                               │
    │  @Service + @Tool → MCP 自动注册              │
    └──────────────────────────────────────────────┘
```

---

## 六、关键设计决策解释

### Q1：为什么 MCP 工具用 `.toolCallbacks(mcpToolProvider)` 而不是 `.toolCallbacks(ToolCallback[])`？

**答：** `ToolCallbackProvider` 是一个"延迟获取"的接口。Spring AI 在调用 AI 模型时，才会从 Provider 中获取实际的 ToolCallback。这解决了 MCP Client 异步初始化的时序问题——如果用 `ToolCallback[]` 在 Bean 创建时就获取，MCP Server 可能还没启动完，拿到的是空数组。

### Q2：为什么 MCP Server 要用 stdio 而不是 HTTP？

**答：** stdio 模式下，MCP Server 是主进程的子进程，生命周期由主进程管理（主进程启动→子进程启动，主进程关闭→子进程关闭）。不需要额外端口，不会端口冲突，部署更简单。HTTP/SSE 模式适合 MCP Server 独立部署的场景。

### Q3：为什么 yang-mcp-server 需要 `application-stdio.yml`？

**答：** MCP 协议要求 Server 声明 `name`、`version`、`type` 等元信息。如果没有这些配置，MCP Client 无法正确识别 Server 的能力。鱼皮项目的 `yu-image-search-mcp-server` 也有同样的配置文件。

### Q4：为什么 `logging.pattern.console=` 要清空？

**答：** stdio 模式下，MCP Server 的所有 stdout 输出都会被 MCP Client 当作协议消息解析。如果日志有格式前缀（如 `2026-05-03 18:10:43 INFO ...`），MCP Client 会解析失败。清空后，日志不会输出到 stdout，不会干扰通信。

### Q5：三种模式为什么要分开？

**答：** 不同场景需求不同：
- **agent**：需要 MCP 远程工具，用云端大模型，响应快
- **love**：需要 RAG 知识库，用云端大模型，回答更专业
- **ollama**：需要本地运行（隐私/离线），同时也能用 MCP 工具和 RAG

---

## 七、文件清单

| 文件路径 | 类型 | 作用 |
|---------|------|------|
| `controller/AiController2.java` | 控制器 | 接口入口，路由分发 |
| `model/dto/ChatRequest.java` | DTO | 请求参数封装 |
| `agent/BaseAgent.java` | 基类 | Agent 抽象定义 |
| `agent/ReActAgent.java` | Agent | MCP + 记忆 + AI |
| `config/AgentConfig.java` | 配置 | Bean 组装 |
| `config/ToolConfig.java` | 配置 | 本地工具注册（预留） |
| `service/AiService.java` | 服务 | MCP 对话、恋爱顾问、记忆等 |
| `service/LoveAdvisorService.java` | 服务 | RAG 恋爱顾问 |
| `service/OllamaService.java` | 服务 | 本地模型 + MCP + RAG |
| `resources/application-dev.yml` | 配置 | MCP Client + 数据源 + 模型 |
| `resources/mcp-servers.json` | 配置 | MCP Server 启动参数 |
| `yang-mcp-server/.../application-stdio.yml` | 配置 | MCP Server stdio 模式 |
| `yang-mcp-server/.../WeatherTool.java` | 工具 | 天气查询 |
| `yang-mcp-server/.../TranslateTool.java` | 工具 | 翻译 |
| `yang-mcp-server/.../NewsTool.java` | 工具 | 新闻搜索 |
| `yang-mcp-server/.../YangMcpServerApplication.java` | 启动类 | MCP Server 入口 |
