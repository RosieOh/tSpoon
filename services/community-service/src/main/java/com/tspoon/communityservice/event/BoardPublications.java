package com.tspoon.communityservice.event;

import com.tspoon.communityservice.entity.Board;
import com.tspoon.outbox.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 게시글이 공개되거나(작성·수정·숨김 해제) 더 이상 공개되지 않으면(삭제·숨김) 이벤트를 남긴다 — 검색 색인이 받아 갱신한다.
 * 변경과 같은 트랜잭션에서 불러야 한다.
 */
@Component
@RequiredArgsConstructor
public class BoardPublications {

    private final OutboxEventPublisher outboxEventPublisher;

    public void published(Board board) {
        outboxEventPublisher.publish(CommunityEvents.TOPIC, CommunityEvents.AGGREGATE_BOARD, board.getId(),
                CommunityEvents.BOARD_PUBLISHED, CommunityEvents.BoardPublished.of(board));
    }

    public void removed(Long boardId) {
        outboxEventPublisher.publish(CommunityEvents.TOPIC, CommunityEvents.AGGREGATE_BOARD, boardId,
                CommunityEvents.BOARD_REMOVED, new CommunityEvents.BoardRemoved(boardId));
    }
}
