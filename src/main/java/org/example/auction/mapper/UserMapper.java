package org.example.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.auction.entity.User;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT COUNT(*) FROM users WHERE status = 'ACTIVE'")
    Long countActiveUsers();
}
