package com.tspoon.event;

/**
 * 회원 이벤트를 받아 자기 데이터에 반영하는 서비스가 구현한다 (작성자 이름 갱신, 탈퇴 회원 정리 등).
 * 이 빈이 있으면 {@link UserEventListener} 가 서비스 이름을 소비자 그룹으로 tspoon.user.events 를 구독한다.
 *
 * 최소 1회 전달이라 같은 이벤트가 다시 올 수 있으므로 여러 번 처리해도 결과가 같게 구현한다.
 * 예외를 던지면 재시도한 뒤 DLT 로 간다.
 */
public interface UserEventHandler {

    /** 회원 이름이 바뀌었다 — 복사해 둔 이름을 바꾼다 */
    default void onProfileUpdated(long userId, String name) {
    }
}
