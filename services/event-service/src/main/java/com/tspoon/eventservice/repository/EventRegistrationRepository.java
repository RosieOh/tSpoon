package com.tspoon.eventservice.repository;

import com.tspoon.eventservice.entity.EventRegistration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventRegistrationRepository extends JpaRepository<EventRegistration, Long> {

    Optional<EventRegistration> findByEventIdAndUserId(Long eventId, Long userId);

    @EntityGraph(attributePaths = "event")
    List<EventRegistration> findByEventIdAndStatusOrderByCreatedAtAsc(Long eventId, EventRegistration.RegistrationStatus status);

    @EntityGraph(attributePaths = "event")
    Page<EventRegistration> findByUserIdAndStatusInOrderByCreatedAtDesc(Long userId,
                                                                        Collection<EventRegistration.RegistrationStatus> statuses,
                                                                        Pageable pageable);

    @Query("SELECT r.id FROM EventRegistration r WHERE r.userId = :userId AND r.status IN :statuses")
    List<Long> findIdsByUserIdAndStatusIn(@Param("userId") Long userId,
                                          @Param("statuses") Collection<EventRegistration.RegistrationStatus> statuses);

    // 가장 먼저 대기한 사람
    Optional<EventRegistration> findFirstByEventIdAndStatusOrderByWaitlistedAtAscIdAsc(Long eventId,
                                                                                      EventRegistration.RegistrationStatus status);

    // 대기 순번 = 나보다 먼저 대기한 사람 수 + 1
    @Query("""
            SELECT COUNT(r) + 1 FROM EventRegistration r
            WHERE r.event.id = :eventId
              AND r.status = com.tspoon.eventservice.entity.EventRegistration.RegistrationStatus.WAITLISTED
              AND (r.waitlistedAt < :waitlistedAt OR (r.waitlistedAt = :waitlistedAt AND r.id < :id))
            """)
    long waitlistPosition(@Param("eventId") Long eventId, @Param("waitlistedAt") Instant waitlistedAt, @Param("id") Long id);

    long countByEventIdAndStatus(Long eventId, EventRegistration.RegistrationStatus status);

    @Query("SELECT r.userId FROM EventRegistration r WHERE r.event.id = :eventId AND r.status IN :statuses ORDER BY r.createdAt")
    List<Long> findUserIds(@Param("eventId") Long eventId,
                           @Param("statuses") Collection<EventRegistration.RegistrationStatus> statuses);
}
