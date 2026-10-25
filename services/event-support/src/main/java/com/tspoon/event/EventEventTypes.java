package com.tspoon.event;

/**
 * {@link TspoonTopics#EVENT_EVENTS} 의 이벤트 이름. 형식은 event-schemas/{이름}.schema.json
 */
public final class EventEventTypes {

    /** 신청한 행사가 곧 시작한다 (행사마다 한 번) */
    public static final String EVENT_REMINDER = "EVENT_REMINDER";
    /** 신청한 행사가 취소됐다 */
    public static final String EVENT_CANCELLED = "EVENT_CANCELLED";
    /** 대기하던 회원의 신청이 자리가 나서 확정됐다 */
    public static final String WAITLIST_PROMOTED = "EVENT_WAITLIST_PROMOTED";

    private EventEventTypes() {
    }
}
