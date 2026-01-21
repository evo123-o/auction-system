package org.example.auction.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.Objects;
import java.util.UUID;

/**
 * 本地文件系统实现（默认回退实现）
 * - 支持基于大小的图片压缩（当 contentType 为 image/* 且超过阈值时）
 * - 支持 delete(path)：当 path 以 uploadBaseUrl 开头时会删除对应本地文件
 */
@Service
public class LocalStorageService implements StorageService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.base-url:/uploads}")
    private String uploadBaseUrl;

    /**
     * 图片文件超过该阈值（字节）会尝试压缩（默认 2MB）
     */
    @Value("${image.max-size-bytes:2097152}")
    private long maxSizeBytes;

    /**
     * 压缩后的最大宽度/高度（像素），若图片超出则等比缩放到此尺寸以内
     */
    @Value("${image.max-width:1024}")
    private int maxWidth;

    @Value("${image.max-height:1024}")
    private int maxHeight;

    @Override
    public String store(MultipartFile file, String folder) throws IOException {
        File base = new File(uploadDir);
        if (!base.exists()) {
            Files.createDirectories(base.toPath());
        }
        File dir = folder == null || folder.isBlank() ? base : new File(base, folder);
        if (!dir.exists()) Files.createDirectories(dir.toPath());

        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) ext = original.substring(original.lastIndexOf('.')).toLowerCase();

        String filename = UUID.randomUUID() + ext;
        File dest = new File(dir, filename);

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();

        // 如果是图片且大小超过阈值，尝试压缩
        final String s = folder == null || folder.isBlank() ? "" : "/" + folder;
        if (contentType.startsWith("image/") && file.getSize() > maxSizeBytes) {
            try (InputStream in = file.getInputStream()) {
                BufferedImage srcImg = ImageIO.read(in);
                if (srcImg != null) {
                    int srcW = srcImg.getWidth();
                    int srcH = srcImg.getHeight();
                    double ratio = Math.min((double) maxWidth / srcW, (double) maxHeight / srcH);
                    if (ratio < 1.0) {
                        int newW = (int) Math.round(srcW * ratio);
                        int newH = (int) Math.round(srcH * ratio);
                        Image scaled = srcImg.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
                        BufferedImage outImg = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g2d = outImg.createGraphics();
                        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g2d.drawImage(scaled, 0, 0, null);
                        g2d.dispose();

                        // 推断输出格式（去掉点）
                        String format = "jpg";
                        if (ext.length() > 1) {
                            String maybe = ext.substring(1);
                            if (maybe.equalsIgnoreCase("png") || maybe.equalsIgnoreCase("gif") || maybe.equalsIgnoreCase("bmp") || maybe.equalsIgnoreCase("jpg") || maybe.equalsIgnoreCase("jpeg")) {
                                format = maybe.equalsIgnoreCase("jpeg") ? "jpg" : maybe;
                            }
                        }

                        try (OutputStream os = new FileOutputStream(dest)) {
                            ImageIO.write(outImg, format, os);
                        }
                        // 返回 URL
                    } else {
                        // 不需要缩放，直接保存
                        file.transferTo(dest);
                    }
                } else {
                    // 不是可解析的图片，直接保存
                    file.transferTo(dest);
                }
                return uploadBaseUrl + s + "/" + filename;
            } catch (IOException e) {
                // 压缩失败回退为原始保存
                file.transferTo(dest);
                return uploadBaseUrl + s + "/" + filename;
            }
        } else {
            // 非图片或未超阈值：直接保存
            file.transferTo(dest);
            return uploadBaseUrl + s + "/" + filename;
        }
    }

    @Override
    public void delete(String path) throws IOException {
        if (path == null || path.isBlank()) return;
        // 仅支持删除本地 uploadBaseUrl 对应的文件路径，例如 /uploads/items/1/uuid.jpg
        if (!path.startsWith(uploadBaseUrl)) {
            throw new UnsupportedOperationException("LocalStorageService only supports deleting local uploadBaseUrl paths");
        }
        String rel = path.substring(uploadBaseUrl.length());
        if (rel.startsWith("/")) rel = rel.substring(1);
        File file = new File(uploadDir, rel);
        if (file.exists()) {
            Files.deleteIfExists(file.toPath());
            // try deleting empty parent directories (optional)
            File parent = file.getParentFile();
            while (parent != null && !parent.equals(new File(uploadDir)) && Objects.requireNonNull(parent.list()).length == 0) {
                Files.deleteIfExists(parent.toPath());
                parent = parent.getParentFile();
            }
        }
    }
}