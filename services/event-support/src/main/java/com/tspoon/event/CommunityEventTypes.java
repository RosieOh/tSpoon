package com.tspoon.event;

/**
 * {@link TspoonTopics#COMMUNITY_EVENTS} 의 이벤트 이름. 형식은 event-schemas/{이름}.schema.json
 */
public final class CommunityEventTypes {

    public static final String COMMENT_CREATED = "COMMENT_CREATED";

    private CommunityEventTypes() {
    }
}
