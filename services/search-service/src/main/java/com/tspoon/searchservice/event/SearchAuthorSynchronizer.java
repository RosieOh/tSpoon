package com.tspoon.searchservice.event;

import com.tspoon.event.UserEventHandler;
import com.tspoon.searchservice.index.SearchIndex;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 회원 이름이 바뀌거나 탈퇴하면 색인에 복사해 둔 작성자 이름도 바꾼다 (#49·#50 과 같은 규칙).
 */
@Component
@RequiredArgsConstructor
public class SearchAuthorSynchronizer implements UserEventHandler {

    private final SearchIndex index;

    @Override
    public void onProfileUpdated(long userId, String name) {
        index.renameAuthor(userId, name);
    }

    @Override
    public void onWithdrawn(long userId) {
        index.renameAuthor(userId, WITHDRAWN_USER_NAME);
    }
}
