package com.tspoon.event;

/**
 * {@link TspoonTopics#USER_EVENTS} 의 이벤트 이름.
 */
public final class UserEventTypes {

    public static final String EMAIL_VERIFICATION_REQUESTED = "EMAIL_VERIFICATION_REQUESTED";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    /** payload: {"userId", "name"} */
    public static final String PROFILE_UPDATED = "USER_PROFILE_UPDATED";

    private UserEventTypes() {
    }
}
