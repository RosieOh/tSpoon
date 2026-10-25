package com.tspoon.userservice.dto;

import com.tspoon.userservice.entity.Child;

import java.time.LocalDate;
import java.util.List;

public record ChildResponse(
        Long id,
        String name,
        LocalDate birthDate,
        Child.Grade grade,
        String school,
        List<String> interests,
        String notes
) {

    public static ChildResponse from(Child child) {
        return new ChildResponse(child.getId(), child.getName(), child.getBirthDate(), child.getGrade(), child.getSchool(),
                List.copyOf(child.getInterests()), child.getNotes());
    }
}
