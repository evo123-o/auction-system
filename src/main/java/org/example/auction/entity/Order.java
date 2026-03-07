package org.example.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单
 * 状态：PENDING_PAYMENT / PAID / CANCELLED / BREACH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("orders")
public class Order {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long itemId;

    private Long sellerId;

    private Long buyerId;

    private BigDecimal finalPrice;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime payBy; // 最晚支付时间（例如拍卖结束后24小时）

    private String receiptPath; // HTML 凭证文件路径

    @TableField(exist = false)
    private String buyerName;

    @TableField(exist = false)
    private String sellerName;

    @TableField("deposit_amount")
    private BigDecimal depositAmount;

}
