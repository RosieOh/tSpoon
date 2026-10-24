package com.tspoon.eventservice.repository;

import com.tspoon.eventservice.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface EventRepository extends JpaRepository<Event, Long> {

    // 다가오는 행사부터
    @Query("""
            SELECT e FROM Event e
            WHERE e.status IN :statuses
              AND (:type IS NULL OR e.type = :type)
            ORDER BY e.startDate ASC
            """)
    Page<Event> search(@Param("statuses") Collection<Event.EventStatus> statuses,
                       @Param("type") Event.EventType type,
                       Pageable pageable);

    /**
     * 자리가 남아 있으면 참가 인원을 1 늘리고, 정원이 차면 FULL 로 바꾼다. 확보하지 못하면 0.
     * 같은 행을 동시에 UPDATE 하면 PostgreSQL 이 앞 요청이 끝날 때까지 기다렸다가 조건을 다시 평가하므로 정원을 넘지 않는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Event e SET
                e.currentParticipants = e.currentParticipants + 1,
                e.status = CASE WHEN e.maxParticipants IS NOT NULL AND e.currentParticipants + 1 >= e.maxParticipants
                                THEN com.tspoon.eventservice.entity.Event.EventStatus.FULL ELSE e.status END
            WHERE e.id = :id AND e.status = com.tspoon.eventservice.entity.Event.EventStatus.ACTIVE
              AND (e.maxParticipants IS NULL OR e.currentParticipants < e.maxParticipants)
            """)
    int reserveSeat(@Param("id") Long id);

    // 자리를 돌려주고, 정원 마감 상태였다면 다시 모집 중으로
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Event e SET
                e.currentParticipants = e.currentParticipants - 1,
                e.status = CASE WHEN e.status = com.tspoon.eventservice.entity.Event.EventStatus.FULL
                                THEN com.tspoon.eventservice.entity.Event.EventStatus.ACTIVE ELSE e.status END
            WHERE e.id = :id AND e.currentParticipants > 0
            """)
    int releaseSeat(@Param("id") Long id);
}
