package com.tspoon.event;

/**
 * 서비스 사이 이벤트 토픽. 토픽마다 발행하는 서비스는 하나이고, 메시지 키는 집합(aggregate) ID 라서
 * 같은 대상의 이벤트는 같은 파티션에 순서대로 들어간다. 처리에 실패한 메시지는 "{토픽}.DLT" 로 간다.
 */
public final class TspoonTopics {

    /** user-service — 이메일 인증·비밀번호 메일 요청, 프로필 변경, 탈퇴. 키: 회원 ID */
    public static final String USER_EVENTS = "tspoon.user.events";
    /** community-service — 댓글. 키: 게시글 ID */
    public static final String COMMUNITY_EVENTS = "tspoon.community.events";
    /** qna-service — 답변·채택. 키: 질문 ID */
    public static final String QNA_EVENTS = "tspoon.qna.events";
    /** event-service — 행사 리마인더·취소. 키: 행사 ID */
    public static final String EVENT_EVENTS = "tspoon.event.events";

    public static final int PARTITIONS = 3;

    private TspoonTopics() {
    }
}
