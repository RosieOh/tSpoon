package com.tspoon.moderation;

import java.util.Optional;

/**
 * 신고·숨김 대상(게시글·댓글·질문·답변). 대상을 가진 서비스가 종류마다 빈으로 등록한다.
 * 숨김·복구는 대상의 상태를 바꾸는 일이라 각 서비스가 자기 규칙대로(댓글 수 조정 등) 처리한다.
 */
public interface ModerationTarget {

    /** URL 에 쓰는 종류 이름 (예: boards) — {@code /api/{collection}/{id}/reports}, {@code /api/admin/reports/{collection}} */
    String collection();

    /** 신고할 수 있는(공개 중인) 대상을 행 잠금으로 읽는다. 같은 대상에 대한 동시 신고를 줄 세워 자동 숨김 기준을 정확히 센다 */
    Optional<Snapshot> lockVisible(long id);

    /** 상태와 무관하게 읽는다 (관리자 화면) */
    Optional<Snapshot> find(long id);

    /** 공개 중이면 숨긴다. 이미 숨겨졌거나 삭제됐으면 그대로 둔다 */
    void hide(long id);

    /** 숨겨져 있으면 다시 공개한다 */
    void restore(long id);

    /**
     * @param preview 관리자 목록에 보여 줄 짧은 내용 (제목 또는 본문 앞부분)
     */
    record Snapshot(long id, Long authorId, String status, String preview) {
    }
}
