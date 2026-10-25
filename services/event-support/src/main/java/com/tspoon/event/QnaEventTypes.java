package com.tspoon.event;

/**
 * {@link TspoonTopics#QNA_EVENTS} 의 이벤트 이름. 형식은 event-schemas/{이름}.schema.json
 */
public final class QnaEventTypes {

    public static final String ANSWER_CREATED = "ANSWER_CREATED";
    public static final String ANSWER_ACCEPTED = "ANSWER_ACCEPTED";

    private QnaEventTypes() {
    }
}
