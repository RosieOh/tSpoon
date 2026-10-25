package com.tspoon.qnaservice.moderation;

import com.tspoon.moderation.ModerationTarget;
import com.tspoon.qnaservice.entity.Answer;
import com.tspoon.qnaservice.repository.AnswerRepository;
import com.tspoon.qnaservice.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 답변 신고·숨김. 숨긴 답변은 질문 상세와 답변 수에서 빠진다.
 */
@Component
@RequiredArgsConstructor
public class AnswerModerationTarget implements ModerationTarget {

    private static final int PREVIEW_LENGTH = 100;

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;

    @Override
    public String collection() {
        return "answers";
    }

    @Override
    public Optional<Snapshot> lockVisible(long id) {
        return answerRepository.findLockedByIdAndStatus(id, Answer.AnswerStatus.ACTIVE).map(AnswerModerationTarget::snapshot);
    }

    @Override
    public Optional<Snapshot> find(long id) {
        return answerRepository.findById(id).map(AnswerModerationTarget::snapshot);
    }

    @Override
    public void hide(long id) {
        answerRepository.findByIdAndStatus(id, Answer.AnswerStatus.ACTIVE).ifPresent(answer -> {
            answer.setStatus(Answer.AnswerStatus.HIDDEN);
            questionRepository.decrementAnswerCount(answer.getQuestion().getId());
        });
    }

    @Override
    public void restore(long id) {
        answerRepository.findByIdAndStatus(id, Answer.AnswerStatus.HIDDEN).ifPresent(answer -> {
            answer.setStatus(Answer.AnswerStatus.ACTIVE);
            questionRepository.incrementAnswerCount(answer.getQuestion().getId());
        });
    }

    private static Snapshot snapshot(Answer answer) {
        String content = answer.getContent();
        return new Snapshot(answer.getId(), answer.getAuthorId(), answer.getStatus().name(),
                content.length() > PREVIEW_LENGTH ? content.substring(0, PREVIEW_LENGTH) + "…" : content);
    }
}
