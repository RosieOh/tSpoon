package com.tspoon.userservice.dto;

import com.tspoon.userservice.entity.Child;

import java.time.LocalDate;

public record ChildResponse(
        Long id,
        String name,
        LocalDate birthDate,
        Child.Grade grade,
        String school
) {

    public static ChildResponse from(Child child) {
        return new ChildResponse(child.getId(), child.getName(), child.getBirthDate(), child.getGrade(), child.getSchool());
    }
}
