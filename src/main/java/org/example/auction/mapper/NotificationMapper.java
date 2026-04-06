package org.example.auction.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.auction.entity.Notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}