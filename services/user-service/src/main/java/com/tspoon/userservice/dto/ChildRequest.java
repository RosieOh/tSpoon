package com.tspoon.userservice.dto;

import com.tspoon.userservice.entity.Child;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ChildRequest(
        @NotBlank(message = "이름은 필수입니다")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다")
        String name,

        @Past(message = "생년월일은 오늘 이전이어야 합니다")
        LocalDate birthDate,

        @NotNull(message = "학년은 필수입니다")
        Child.Grade grade,

        @Size(max = 100, message = "학교 이름은 100자 이하여야 합니다")
        String school,

        @Size(max = 10, message = "관심사는 10개까지 등록할 수 있습니다")
        List<@NotBlank(message = "관심사는 비워 둘 수 없습니다")
             @Size(max = 30, message = "관심사는 30자 이하여야 합니다") String> interests,

        @Size(max = 1000, message = "메모는 1000자 이하여야 합니다")
        String notes
) {

    // 앞뒤 공백을 지우고 중복을 빼되 입력 순서는 유지한다
    public List<String> normalizedInterests() {
        return interests == null ? List.of() : interests.stream().map(String::strip).distinct().toList();
    }
}
