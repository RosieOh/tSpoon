package com.tspoon.event;

/**
 * {@link TspoonTopics#QNA_EVENTS} 의 이벤트 이름. 형식은 event-schemas/{이름}.schema.json
 */
public final class QnaEventTypes {

    public static final String ANSWER_CREATED = "ANSWER_CREATED";
    public static final String ANSWER_ACCEPTED = "ANSWER_ACCEPTED";
    /** 질문이 공개됐거나 공개 중인 내용이 바뀌었다 — 지금 모습 전체 */
    public static final String QUESTION_PUBLISHED = "QUESTION_PUBLISHED";
    /** 질문이 더 이상 공개되지 않는다 (삭제·숨김) */
    public static final String QUESTION_REMOVED = "QUESTION_REMOVED";

    private QnaEventTypes() {
    }
}
