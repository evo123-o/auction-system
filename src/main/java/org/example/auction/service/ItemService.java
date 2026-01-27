package org.example.auction.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.auction.dto.CreateItemRequest;
import org.example.auction.entity.Item;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Item 服务接口
 */
public interface ItemService {
    Item startAuction(Long id);

    /**
     * 分页查询（支持简单的按 title/category/status ���滤）
     */
    IPage<Item> pageItems(Page<Item> page, String title, String category, String status);

    /**
     * 根据 id 获取拍品
     */
    Item getById(Long id);

    /**
     * 创建拍品
     */
    Item create(CreateItemRequest req, Long createdBy);

    /**
     * 更新拍品（部分字段）
     */
    Item update(Long id, CreateItemRequest req);

    /**
     * 上传图片并更新拍品的 imagePath 字段；返回相对/外部可访问路径
     */
    String saveImage(Long itemId, MultipartFile file) throws IOException;

    /**
     * 更新 item 的 imagePath 字段（不处理文件存储）
     */
    Item updateImagePath(Long itemId, String imageUrl);

    /**
     * 删除拍品（同时尝试删除关联的图片文件，视 StorageService 能力而定）
     */
    boolean deleteById(Long id);
}
