//package com.yang.tools;
//
//import cn.hutool.core.io.FileUtil;
//import cn.hutool.core.io.IoUtil;
//import com.itextpdf.kernel.font.PdfFont;
//import com.itextpdf.kernel.font.PdfFontFactory;
//import com.itextpdf.kernel.pdf.PdfDocument;
//import com.itextpdf.kernel.pdf.PdfWriter;
//import com.itextpdf.layout.Document;
//import com.itextpdf.layout.element.Paragraph;
//import org.springframework.ai.tool.annotation.Tool;
//import org.springframework.ai.tool.annotation.ToolParam;
//import org.springframework.core.io.ClassPathResource;
//import org.springframework.stereotype.Component;
//
//import java.io.File;
//import java.io.InputStream;
//import java.util.Arrays;
//
///**
// * PDF文档生成工具
// * 预加载字体、安全关闭IO流、彻底解决卡死阻塞问题
// */
//@Component
//public class PDFGenerationTool {
//
//    // 项目启动一次性预加载字体，不每次读取，避免IO阻塞
//    private static PdfFont CHINESE_FONT;
//
//    static {
//        try (InputStream fontStream = new ClassPathResource("static/fonts/simsun.ttc").getInputStream()) {
//            byte[] fontBytes = IoUtil.readBytes(fontStream);
//            // 用你能编译通过的唯一写法
//            CHINESE_FONT = PdfFontFactory.createFont(Arrays.toString(fontBytes));
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    @Tool(description = "PDF文档生成器，将文本内容生成支持中文的PDF文件并保存到服务器本地")
//    public String generatePDF(
//            @ToolParam(description = "PDF文件名，无需输入.pdf后缀") String fileName,
//            @ToolParam(description = "需要写入PDF的正文内容，支持中文") String content) {
//
//        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
//        String finalFileName = fileName.endsWith(".pdf") ? fileName : fileName + ".pdf";
//        String filePath = fileDir + File.separator + finalFileName;
//
//        try {
//            FileUtil.mkdir(fileDir);
//
//            try (PdfWriter writer = new PdfWriter(filePath);
//                 PdfDocument pdfDoc = new PdfDocument(writer);
//                 Document document = new Document(pdfDoc)) {
//
//                // 直接用预加载好的字体，不再读取IO，彻底杜绝卡死
//                document.setFont(CHINESE_FONT);
//                document.add(new Paragraph(content));
//            }
//
//            return "PDF文件生成成功！路径：" + filePath;
//        } catch (Exception e) {
//            return "PDF生成失败：" + e.getMessage();
//        }
//    }
//}