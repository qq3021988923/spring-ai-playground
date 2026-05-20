package com.yang.tools;

import org.springframework.stereotype.Component;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;


@Component
public class TerminalOperationTool {

     //查看当前目录：执行 dir 命令
    //看 IP：执行 ipconfig
    // 白名单：只允许执行安全命令
     private static final List<String> ALLOWED_COMMANDS = List.of(
             // 原有安全命令
             "dir", "ipconfig", "ping", "echo", "cd", "type",
             // 新增 安全只读命令
             "tree",       // 查看文件夹目录树
             "chkdsk",     // 检查磁盘（只读模式，无修复）
             "systeminfo", // 查看系统详细信息
             "hostname",   // 查看电脑名称
             "whoami",     // 查看当前登录用户
             "tasklist",   // 查看正在运行的进程
             "netstat",    // 查看网络连接
             "ver",        // 查看系统版本
             "cls",        // 清屏（无害）
             "date",       // 查看日期
             "time"        // 查看时间
     );
    @Tool(description = "在终端执行安全命令，仅允许：dir, ipconfig, ping, echo, cd, type,tree,chkdsk,systeminfo" +
            "hostname,whoami,tasklist,netstat,ver,cls,date,time")
    public String executeTerminalCommand(
            @ToolParam(description = "要执行的终端命令") String command) {

        // 安全校验
        if (command == null || command.isBlank()) {
            return "命令不能为空";
        }

        String first = command.trim().split("\\s+")[0].toLowerCase();
        if (!ALLOWED_COMMANDS.contains(first)) {
            return "❌ 禁止执行高危命令！仅允许：" + ALLOWED_COMMANDS;
        }

        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command);
            Process process = builder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "GBK"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                output.append("\n命令执行失败，退出码：").append(exitCode);
            }

        } catch (IOException | InterruptedException e) {
            output.append("执行出错：").append(e.getMessage());
        }
        return output.toString();
    }
}