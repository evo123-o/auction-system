package org.example.auction.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 存储抽象：将 MultipartFile 存储并返回可访问的 URL（或相对路径）
 */
public interface StorageService {
    /**
     * 存储文件并返回可访问 URL（或路径）
     */
    String store(MultipartFile file, String folder) throws IOException;

    /**
     * 删除给定的存储路径（如果实现支持），路径是 store 返回的 URL 或相对路径
     */
    void delete(String path) throws IOException;
}
