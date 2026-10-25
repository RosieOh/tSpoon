package com.tspoon.qnaservice.moderation;

import com.tspoon.moderation.ModerationTarget;
import com.tspoon.qnaservice.entity.Question;
import com.tspoon.qnaservice.entity.Question.QuestionStatus;
import com.tspoon.qnaservice.event.QuestionPublications;
import com.tspoon.qnaservice.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * 질문 신고·숨김. 질문은 답변 대기·완료·종료 상태가 있으므로 숨길 때 그 상태를 기억했다가 다시 공개할 때 되돌린다.
 */
@Component
@RequiredArgsConstructor
public class QuestionModerationTarget implements ModerationTarget {

    private static final Set<QuestionStatus> VISIBLE = EnumSet.of(QuestionStatus.OPEN, QuestionStatus.ANSWERED, QuestionStatus.CLOSED);

    private final QuestionRepository questionRepository;
    private final QuestionPublications questionPublications;

    @Override
    public String collection() {
        return "questions";
    }

    @Override
    public Optional<Snapshot> lockVisible(long id) {
        return questionRepository.findLockedById(id)
                .filter(question -> VISIBLE.contains(question.getStatus()))
                .map(QuestionModerationTarget::snapshot);
    }

    @Override
    public Optional<Snapshot> find(long id) {
        return questionRepository.findById(id).map(QuestionModerationTarget::snapshot);
    }

    @Override
    public void hide(long id) {
        questionRepository.findById(id)
                .filter(question -> VISIBLE.contains(question.getStatus()))
                .ifPresent(question -> {
                    question.setStatusBeforeHidden(question.getStatus());
                    question.setStatus(QuestionStatus.HIDDEN);
                    questionPublications.removed(id);
                });
    }

    @Override
    public void restore(long id) {
        questionRepository.findById(id)
                .filter(question -> question.getStatus() == QuestionStatus.HIDDEN)
                .ifPresent(question -> {
                    QuestionStatus previous = question.getStatusBeforeHidden();
                    question.setStatus(previous == null ? QuestionStatus.OPEN : previous);
                    question.setStatusBeforeHidden(null);
                    questionPublications.published(question);
                });
    }

    private static Snapshot snapshot(Question question) {
        return new Snapshot(question.getId(), question.getAuthorId(), question.getStatus().name(), question.getTitle());
    }
}
