package com.example.mapper;

import com.example.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 订单Mapper
 */
@Mapper
public interface OrderMapper {

    /**
     * 根据订单ID查询订单
     */
    Order selectById(@Param("orderId") Integer orderId);

    /**
     * 创建订单
     */
    int insert(Order order);

    /**
     * 更新订单状态（带版本号校验）
     */
    int updateStatusWithVersion(
        @Param("orderId") Integer orderId,
        @Param("targetStatus") String targetStatus,
        @Param("oldVersion") Long oldVersion,
        @Param("newVersion") Long newVersion
    );
}
