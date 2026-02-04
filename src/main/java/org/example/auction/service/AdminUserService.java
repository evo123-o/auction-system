package org.example.auction.service;

import lombok.NonNull;
import org.example.auction.dto.AdminUserDto;
import org.example.auction.dto.CreateUserRequest;
import org.example.auction.dto.UpdateUserRequest;
import org.springframework.data.domain.Page;

public interface AdminUserService {
    Page<@NonNull AdminUserDto> listUsers(int page, int size);
    AdminUserDto getUser(Long id);
    AdminUserDto createUser(CreateUserRequest req);
    AdminUserDto updateUser(Long id, UpdateUserRequest req);
    boolean deleteUser(Long id);
}
