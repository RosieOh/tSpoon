package com.tspoon.eventservice.repository;

import com.tspoon.eventservice.entity.EventRegistration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventRegistrationRepository extends JpaRepository<EventRegistration, Long> {

    Optional<EventRegistration> findByEventIdAndUserId(Long eventId, Long userId);

    @EntityGraph(attributePaths = "event")
    List<EventRegistration> findByEventIdAndStatusOrderByCreatedAtAsc(Long eventId, EventRegistration.RegistrationStatus status);

    @EntityGraph(attributePaths = "event")
    Page<EventRegistration> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, EventRegistration.RegistrationStatus status,
                                                                      Pageable pageable);

    long countByEventIdAndStatus(Long eventId, EventRegistration.RegistrationStatus status);

    @Query("SELECT r.userId FROM EventRegistration r WHERE r.event.id = :eventId AND r.status = :status ORDER BY r.createdAt")
    List<Long> findUserIds(@Param("eventId") Long eventId, @Param("status") EventRegistration.RegistrationStatus status);
}
