package com.yang.config;

import com.yang.rag.LoveDocumentLoader;
import com.yang.tools.*;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolConfig {


    @Bean
    public ToolCallback[] toolCallbacks(
                                       KnowledgeTools knowledgeTools,
                                        TerminalOperationTool terminalOperationTool,
                                        FileOperationTool fileOperationTool,
                                        TerminateTool terminateTool,
                                        WebScrapingTool webScrapingTool,
                                        ResourceDownloadTool resourceDownloadTool,
                                        WebSearchTool webSearchTool,
                                        ImageSearchTool imageSearchTool

    ) {

        return ToolCallbacks.from(
               knowledgeTools,         // 恋爱知识库RAG查询（注解版）
                terminateTool,          // 终止工具
                terminalOperationTool,  // 终端操作工具
                fileOperationTool,      // 文件操作工具
                webScrapingTool  ,       // 网页抓取工具（新增）
                resourceDownloadTool,    // 网络资源下载工具
                webSearchTool,            // 联网搜索工具
                imageSearchTool           // 搜索图片链接工具

        );
    }
}