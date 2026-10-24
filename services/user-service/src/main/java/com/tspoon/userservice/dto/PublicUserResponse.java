package com.tspoon.userservice.dto;

import com.tspoon.userservice.entity.User;

/**
 * 다른 회원에게 보여 주는 공개 프로필. 연락처(이메일, 전화번호, 주소)와 계정 상태는 담지 않는다.
 * 본인 정보 전체는 {@link UserResponse} 로 /api/users/profile 에서만 내려준다.
 */
public record PublicUserResponse(
        Long id,
        String name,
        User.UserRole role,
        String profileImage,
        String bio
) {

    public static PublicUserResponse from(User user) {
        return new PublicUserResponse(user.getId(), user.getName(), user.getRole(), user.getProfileImage(), user.getBio());
    }
}
