package com.tspoon.event;

/**
 * {@link TspoonTopics#COMMUNITY_EVENTS} 의 이벤트 이름. 형식은 event-schemas/{이름}.schema.json
 */
public final class CommunityEventTypes {

    public static final String COMMENT_CREATED = "COMMENT_CREATED";
    /** 게시글이 공개됐거나 공개 중인 내용이 바뀌었다 — 지금 모습 전체 */
    public static final String BOARD_PUBLISHED = "BOARD_PUBLISHED";
    /** 게시글이 더 이상 공개되지 않는다 (삭제·숨김) */
    public static final String BOARD_REMOVED = "BOARD_REMOVED";

    private CommunityEventTypes() {
    }
}
