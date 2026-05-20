package com.yang.tools;

import cn.hutool.core.io.FileUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 文件操作工具（整合：读写文件 + AI生成下载链接）
 */
@Component
public class FileOperationTool {

    // 读取项目端口，自动生成下载链接
    @Value("${server.port:8090}")
    private String serverPort;

    // 默认文件目录：tmp/file
    private final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";

    // ===================== 原有方法（完全兼容） =====================
    @Tool(description = "读取默认目录（tmp/file）下的指定文件内容")
    public String readFile(
            @ToolParam(description = "要读取的文件名（如test.txt）") String fileName) {
        String filePath = FILE_DIR + "/" + fileName;
        try {
            return FileUtil.readUtf8String(filePath);
        } catch (Exception e) {
            return "读取文件失败：" + e.getMessage();
        }
    }

    @Tool(description = "将内容写入默认目录（tmp/file）下的指定文件")
    public String writeFile(
            @ToolParam(description = "要写入的文件名（如test.txt）") String fileName,
            @ToolParam(description = "要写入的文件内容") String content) {
        String filePath = FILE_DIR + "/" + fileName;
        try {
            FileUtil.mkdir(FILE_DIR);
            FileUtil.writeUtf8String(content, filePath);
            return "文件写入成功：" + filePath;
        } catch (Exception e) {
            return "写入文件失败：" + e.getMessage();
        }
    }

    // ===================== 新增：AI专用 → 保存并生成下载链接 =====================
    @Tool(description = "将资料保存为文件，并生成浏览器下载链接（对话直接下载）")
    public String saveAndGetDownloadUrl(
            @ToolParam(description = "保存的文件名（例：技术趋势.txt、资料.md）") String fileName,
            @ToolParam(description = "要保存的文本内容") String content) {
        try {
            // 1. 写入文件
            FileUtil.mkdir(FILE_DIR);
            String fullPath = FILE_DIR + "/" + fileName;
            FileUtil.writeUtf8String(content, fullPath);

            // 2. 生成浏览器下载链接（对接你现有接口）
            String downloadUrl = String.format(
                    "http://localhost:%s/api/ai/download?fileName=%s",
                    serverPort,
                    fileName
            );

            return "✅ 资料已保存为文件！\n🖱️ 点击链接直接浏览器下载：\n" + downloadUrl;
        } catch (Exception e) {
            return "保存失败：" + e.getMessage();
        }
    }
}