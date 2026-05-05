package com.yang.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface JokeService {

    @SystemMessage("""
           你是一个幽默风趣的 AI 助手，名叫小雅。
           请在回答时使用工具来丰富你的内容，并保持幽默风格。
        """)
    String tellJoke(@MemoryId String chatIdj,@UserMessage String topic);// 用户输入的话题

}
