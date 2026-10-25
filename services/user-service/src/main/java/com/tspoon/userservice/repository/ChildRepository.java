package com.tspoon.userservice.repository;

import com.tspoon.userservice.entity.Child;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChildRepository extends JpaRepository<Child, Long> {

    @EntityGraph(attributePaths = "interests")
    List<Child> findAllByParentIdOrderByIdAsc(Long parentId);

    // 다른 회원의 자녀는 없는 것처럼 다룬다 (존재 여부를 드러내지 않음)
    Optional<Child> findByIdAndParentId(Long id, Long parentId);

    long countByParentId(Long parentId);

    // 관심사(child_interests)는 FK ON DELETE CASCADE 로 함께 지워진다
    @Modifying
    @Query("DELETE FROM Child c WHERE c.parent.id = :parentId")
    int deleteByParentId(@Param("parentId") Long parentId);
}
