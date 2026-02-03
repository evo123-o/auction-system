package org.example.auction.storage;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

/**
 * LocalStorageService 使用 Thumbnailator 做图片缩放/压缩，并实现 delete。
 */
@Service
public class LocalStorageService implements StorageService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.base-url:/uploads}")
    private String uploadBaseUrl;

    @Value("${image.max-size-bytes:2097152}")
    private long maxSizeBytes;

    @Value("${image.max-width:1024}")
    private int maxWidth;

    @Value("${image.max-height:1024}")
    private int maxHeight;

    @Override
    public String store(MultipartFile file, String folder) throws IOException {
        Path basePath = Paths.get(uploadDir);
        if (!basePath.isAbsolute()) {
            basePath = Paths.get(System.getProperty("user.dir")).resolve(basePath).toAbsolutePath();
        }
        Path dirPath = (folder == null || folder.isBlank()) ? basePath : basePath.resolve(folder);
        Files.createDirectories(dirPath);

        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) ext = original.substring(original.lastIndexOf('.')).toLowerCase();

        String filename = UUID.randomUUID() + ext;
        Path destPath = dirPath.resolve(filename);

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();

        // 若为图片并超过阈值，使用 Thumbnailator 缩放并压缩
        if (contentType.startsWith("image/") && file.getSize() > maxSizeBytes) {
            try (InputStream in = file.getInputStream(); OutputStream os = Files.newOutputStream(destPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                // Thumbnailator 会自动选择合适压缩质量，可设置 size 与 keepAspectRatio
                Thumbnails.of(in)
                        .size(maxWidth, maxHeight)
                        .outputFormat(determineFormat(ext))
                        .toOutputStream(os);
            } catch (IOException e) {
                // 压缩失败回退为直接保存
                try (InputStream in = file.getInputStream()) {
                    Files.copy(in, destPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } else {
            // 非图片或未超阈值，直接保存
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, destPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        return uploadBaseUrl + (folder == null || folder.isBlank() ? "" : "/" + folder) + "/" + filename;
    }

    @Override
    public void delete(String path) throws IOException {
        if (path == null || path.isBlank()) return;
        if (!path.startsWith(uploadBaseUrl)) {
            throw new UnsupportedOperationException("LocalStorageService only supports deleting local uploadBaseUrl paths");
        }
        String rel = path.substring(uploadBaseUrl.length());
        if (rel.startsWith("/")) rel = rel.substring(1);
        Path file = Paths.get(uploadDir).resolve(rel).toAbsolutePath();
        Files.deleteIfExists(file);
        Path parent = file.getParent();
        Path base = Paths.get(uploadDir).toAbsolutePath();

        while (parent != null && !parent.equals(base) && Files.exists(parent)) {
            try (java.util.stream.Stream<Path> stream = Files.list(parent)) {
                if (stream.findAny().isPresent()) {
                    break;
                }
            }
            Files.deleteIfExists(parent);
            parent = parent.getParent();
        }
    }

    private String determineFormat(String ext) {
        if (ext == null) return "jpg";
        String e = ext.startsWith(".") ? ext.substring(1) : ext;
        if (e.equalsIgnoreCase("jpeg")) return "jpg";
        if (e.equalsIgnoreCase("png")) return "png";
        if (e.equalsIgnoreCase("gif")) return "gif";
        return "jpg";
    }
}