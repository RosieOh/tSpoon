package com.tspoon.communityservice.moderation;

import com.tspoon.communityservice.entity.Board;
import com.tspoon.communityservice.event.BoardPublications;
import com.tspoon.communityservice.repository.BoardRepository;
import com.tspoon.moderation.ModerationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 게시글 신고·숨김. 숨긴 게시글은 목록·상세에서 빠지고(상태 HIDDEN), 다시 공개하면 그대로 돌아온다.
 */
@Component
@RequiredArgsConstructor
public class BoardModerationTarget implements ModerationTarget {

    private final BoardRepository boardRepository;
    private final BoardPublications boardPublications;

    @Override
    public String collection() {
        return "boards";
    }

    @Override
    public Optional<Snapshot> lockVisible(long id) {
        return boardRepository.findLockedByIdAndStatus(id, Board.BoardStatus.ACTIVE).map(BoardModerationTarget::snapshot);
    }

    @Override
    public Optional<Snapshot> find(long id) {
        return boardRepository.findById(id).map(BoardModerationTarget::snapshot);
    }

    @Override
    public void hide(long id) {
        boardRepository.findByIdAndStatus(id, Board.BoardStatus.ACTIVE).ifPresent(board -> {
            board.setStatus(Board.BoardStatus.HIDDEN);
            boardPublications.removed(id);
        });
    }

    @Override
    public void restore(long id) {
        boardRepository.findByIdAndStatus(id, Board.BoardStatus.HIDDEN).ifPresent(board -> {
            board.setStatus(Board.BoardStatus.ACTIVE);
            boardPublications.published(board);
        });
    }

    private static Snapshot snapshot(Board board) {
        return new Snapshot(board.getId(), board.getAuthorId(), board.getStatus().name(), board.getTitle());
    }
}
