package com.yang.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;

//    网络资源下载工具
//   核心作用：支持从网络URL下载图片、文档、压缩包、安装包等各类资源到服务器本地
@Component
public class ResourceDownloadTool {
  //  帮我下载这个图片：https://xxx.com/1.jpg
    //帮我下载这个文件：https://xxx.com/file.zip
    //帮我保存这个文档：https://xxx.com/doc.pdf
    @Tool(description = "从指定URL下载网络资源（图片、文件、文档、安装包等）到本地服务器")
    public String downloadResource(
            @ToolParam(description = "网络资源URL，必须以 http 或 https 开头") String url,
            @ToolParam(description = "保存的文件名（例如：test.jpg、document.pdf）") String fileName) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/download";
        String filePath = fileDir + "/" + fileName;
        try {
            FileUtil.mkdir(fileDir);
            HttpUtil.downloadFile(url, new File(filePath));
            return "资源下载成功：" + filePath;
        } catch (Exception e) {
            return "下载资源失败：" + e.getMessage();
        }
    }
}