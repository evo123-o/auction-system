package org.example.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.auction.entity.PasswordResetToken;

@Mapper
public interface PasswordResetMapper extends BaseMapper<PasswordResetToken> {}
