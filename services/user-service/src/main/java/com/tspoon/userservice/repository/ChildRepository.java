package com.tspoon.userservice.repository;

import com.tspoon.userservice.entity.Child;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChildRepository extends JpaRepository<Child, Long> {

    @EntityGraph(attributePaths = "interests")
    List<Child> findAllByParentIdOrderByIdAsc(Long parentId);

    // 다른 회원의 자녀는 없는 것처럼 다룬다 (존재 여부를 드러내지 않음)
    Optional<Child> findByIdAndParentId(Long id, Long parentId);

    long countByParentId(Long parentId);
}
