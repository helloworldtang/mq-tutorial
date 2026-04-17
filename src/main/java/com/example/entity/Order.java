package com.example.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单实体
 */
@Data
public class Order {
    /**
     * 订单ID
     */
    private Integer id;

    /**
     * 订单状态
     */
    private String status;

    /**
     * 版本号
     */
    private Long version;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
