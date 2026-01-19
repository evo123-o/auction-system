package org.example.auction.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 本地文件系统实现（默认回退实现）
 */
@Service // 正确的注解
public class LocalStorageService implements StorageService { // 实现接口

    private static final Logger logger = LoggerFactory.getLogger(LocalStorageService.class);

    @Value("${app.upload.dir:uploads}") // 默认值是 uploads
    private String uploadDirStr; // 使用 Str 后缀区分字符串和 Path

    @Value("${app.upload.base-url:/uploads}") // 默认基础 URL
    private String uploadBaseUrl;

    private Path getUploadRootPath() {
        return Paths.get(uploadDirStr).normalize().toAbsolutePath();
    }

    @Override
    public String store(MultipartFile file, String folder) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file.");
        }

        // --- 安全性增强 ---
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.contains("..") || originalFilename.contains("/\\.")) {
            throw new IllegalArgumentException("Invalid file name: " + originalFilename);
        }

        // 规范化并验证 folder 路径
        String normalizedFolder = null;
        if (folder != null && !folder.isBlank()) {
            // 防止路径遍历
            Path folderPath = Paths.get(folder).normalize();
            normalizedFolder = folderPath.toString();
            if (normalizedFolder.contains("..")) {
                throw new IllegalArgumentException("Invalid folder path: " + folder);
            }
        }
        // --- 安全性增强结束 ---

        Path rootPath = getUploadRootPath();
        // 确保根目录存在
        if (!Files.exists(rootPath)) {
            Files.createDirectories(rootPath);
            logger.info("Created upload directory: {}", rootPath);
        }

        // 构建目标文件夹路径
        Path targetFolderPath = rootPath;
        if (normalizedFolder != null) {
            targetFolderPath = rootPath.resolve(normalizedFolder);
            // 确保目标文件夹存在
            if (!Files.exists(targetFolderPath)) {
                Files.createDirectories(targetFolderPath);
                logger.info("Created subdirectory: {}", targetFolderPath);
            }
        }

        // 确保目标路径仍在允许的根目录下
        Path normalizedTargetPath = targetFolderPath.normalize().toAbsolutePath();
        if (!normalizedTargetPath.startsWith(rootPath)) {
            throw new SecurityException("Attempted to access outside allowed upload directory.");
        }

        // 生成唯一文件名
        String ext = "";
        if (StringUtils.hasText(originalFilename)) { // 使用 Spring 的工具类检查字符串
            int lastDotIndex = originalFilename.lastIndexOf('.');
            if (lastDotIndex >= 0) {
                ext = originalFilename.substring(lastDotIndex);
            }
        }
        String uniqueFilename = UUID.randomUUID().toString() + ext;
        Path targetFilePath = normalizedTargetPath.resolve(uniqueFilename).normalize();

        // 确保最终文件路径也在允许的根目录下
        if (!targetFilePath.toAbsolutePath().startsWith(rootPath)) {
            throw new SecurityException("Attempted to write outside allowed upload directory.");
        }

        // 执行文件传输
        file.transferTo(targetFilePath.toFile());

        // 构建并返回可访问的 URL
        StringBuilder urlBuilder = new StringBuilder(uploadBaseUrl);
        if (normalizedFolder != null) {
            urlBuilder.append('/').append(normalizedFolder);
        }
        urlBuilder.append('/').append(uniqueFilename);

        String storedUrl = urlBuilder.toString();
        logger.info("Stored file at: {}, accessible via URL: {}", targetFilePath, storedUrl);
        return storedUrl;
    }
}