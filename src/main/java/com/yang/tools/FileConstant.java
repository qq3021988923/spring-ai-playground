package com.yang.tools;

import java.io.File;


public class FileConstant {

    // 根目录：项目目录 / tmp
    public static final String FILE_SAVE_DIR = System.getProperty("user.dir") + "/tmp";

    /**
     * 安全路径校验（防止非法访问）
     */
    public static boolean isPathInAllowedDir(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }

        File allowed = new File(FILE_SAVE_DIR);
        File target = new File(filePath);

        try {
            String allowedPath = allowed.getCanonicalPath();
            String targetPath = target.getCanonicalPath();
            return targetPath.startsWith(allowedPath);
        } catch (Exception e) {
            return false;
        }
    }
}