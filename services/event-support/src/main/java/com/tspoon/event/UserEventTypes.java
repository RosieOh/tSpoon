package com.tspoon.event;

/**
 * {@link TspoonTopics#USER_EVENTS} 의 이벤트 이름. 형식은 event-schemas/{이름}.schema.json
 */
public final class UserEventTypes {

    public static final String EMAIL_VERIFICATION_REQUESTED = "EMAIL_VERIFICATION_REQUESTED";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    /** payload: {"userId", "name"} */
    public static final String PROFILE_UPDATED = "USER_PROFILE_UPDATED";
    /** payload: {"userId", "withdrawnAt"} */
    public static final String WITHDRAWN = "USER_WITHDRAWN";
    /** payload: {"userId", "grades"} — 자녀를 등록·수정·삭제할 때마다 지금 자녀들의 학년 */
    public static final String CHILDREN_CHANGED = "USER_CHILDREN_CHANGED";

    private UserEventTypes() {
    }
}
