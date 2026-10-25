package com.tspoon.event;

import java.time.Instant;
import java.util.List;

/**
 * 회원 이벤트를 받아 자기 데이터에 반영하는 서비스가 구현한다 (작성자 이름 갱신, 탈퇴 회원 정리 등).
 * 한 서비스에 여러 개를 등록해도 된다.
 * 이 빈이 있으면 {@link UserEventListener} 가 서비스 이름을 소비자 그룹으로 tspoon.user.events 를 구독한다.
 *
 * 최소 1회 전달이라 같은 이벤트가 다시 올 수 있으므로 여러 번 처리해도 결과가 같게 구현한다.
 * 예외를 던지면 재시도한 뒤 DLT 로 간다.
 */
public interface UserEventHandler {

    /** 탈퇴한 회원의 이름 대신 보여 줄 이름 */
    String WITHDRAWN_USER_NAME = "탈퇴한 회원";

    /** 회원 이름이 바뀌었다 — 복사해 둔 이름을 바꾼다 */
    default void onProfileUpdated(long userId, String name) {
    }

    /** 회원이 탈퇴했다 — 작성한 글은 익명으로 남기고, 개인 활동(좋아요·북마크·신청 등)과 개인정보는 정리한다 */
    default void onWithdrawn(long userId) {
    }

    /**
     * 회원의 자녀 구성이 바뀌었다 — 지금 자녀들의 학년 전체.
     *
     * @param occurredAt 늦게 도착한 옛 이벤트를 걸러낼 때 쓴다
     */
    default void onChildrenChanged(long userId, List<String> grades, Instant occurredAt) {
    }
}
