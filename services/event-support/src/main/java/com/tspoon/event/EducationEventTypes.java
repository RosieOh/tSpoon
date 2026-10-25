package com.tspoon.event;

/**
 * {@link TspoonTopics#EDUCATION_EVENTS} 의 이벤트 이름. 형식은 event-schemas/{이름}.schema.json
 */
public final class EducationEventTypes {

    /** 콘텐츠가 공개됐거나 공개 중인 내용이 바뀌었다 — 지금 모습 전체 */
    public static final String CONTENT_PUBLISHED = "CONTENT_PUBLISHED";
    /** 콘텐츠가 더 이상 공개되지 않는다 (삭제·임시저장으로 되돌림) */
    public static final String CONTENT_REMOVED = "CONTENT_REMOVED";

    private EducationEventTypes() {
    }
}
