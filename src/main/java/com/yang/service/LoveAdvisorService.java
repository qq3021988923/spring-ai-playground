package com.yang.service;

import com.yang.rag.LoveDocumentLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class LoveAdvisorService {

    private final ChatClient.Builder chatClientBuilder;
    private final LoveDocumentLoader documentLoader;
    private final VectorStore vectorStore;   // 新增


    // 改为注入 Builder，而不是直接注入 ChatClient
    public LoveAdvisorService(ChatClient.Builder chatClientBuilder,
                              LoveDocumentLoader documentLoader,
                              VectorStore vectorStore) {
        this.chatClientBuilder = chatClientBuilder;
        this.documentLoader = documentLoader;
        this.vectorStore = vectorStore;
    }

    public void init() {
        documentLoader.initKnowledgeBase();
    }

    // rag就是先搜索我数据库现有的数据
    // 再将数据库数据+我的提示词统一喂给ai，通过ai再过滤一遍现有的数据 再进行输出
    // 检索 (查询数据库数据) → 增强 (ai过滤) → 生成
    public String chat(String userQuestion) {
        log.info("收到用户问题：{}", userQuestion);

        // PostgreSQL 和 PgVector 的关系，就像 手机 和 一个 App：
        // 1. 检索相关知识库 直接从数据库拿数据 ，意思最接近的句子 等价于调用mapper层的方法 这里
        List<Document> relatedDocs = documentLoader.search(userQuestion);

        // 2. 构建上下文
        StringBuilder context = new StringBuilder();
        context.append("以下是恋爱知识库中的相关内容：\n\n");
        for (Document doc : relatedDocs) {
            context.append(doc.getText()).append("\n\n");
        }

        // 3. 构建 System Prompt
        String systemPrompt = """
        你是一位专业的恋爱顾问，名叫"小红娘"。

        你的特点：
        1. 温柔体贴，善解人意
        2. 善于倾听，给出实用建议
        3. 语言亲切，使用 emoji 让对话更生动
        4. 结合知识库中的内容来回答
        5.禁止：重复内容、空洞套话、过度修饰
        6.整合结论，给出完整可行方案
        7.最后给1个简易实操小案例

        请参考以下知识库内容回答用户：

        %s

        如果知识库中没有相关内容，就用你自己的理解回答。
        """.formatted(context);

        // 4. 调用 AI
        String answer = chatClientBuilder.build()
                .prompt()
                .system(systemPrompt)
                .user(userQuestion)
                .call()
                .content();

        log.info("恋爱顾问回复：{}", answer);

        // ===== 新增：把本次对话存入知识库 =====
        String newKnowledge = """
        用户问题：%s
        恋爱顾问回答：%s
        """.formatted(userQuestion, answer);

        Document newDoc = new Document(newKnowledge); // 格式化打包，将这条数据在数据库建立一个目录（（向量索引））
        vectorStore.add(List.of(newDoc));   // 存入向量库，下次可以检索到，通过目录

        log.info("本次对话已存入知识库");

        return answer;
    }


}