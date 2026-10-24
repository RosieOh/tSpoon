package com.tspoon.qnaservice.service;

import com.tspoon.auth.AuthUser;
import com.tspoon.core.exception.BusinessException;
import com.tspoon.core.exception.ResourceNotFoundException;
import com.tspoon.outbox.OutboxEventPublisher;
import com.tspoon.qnaservice.dto.AnswerRequest;
import com.tspoon.qnaservice.dto.AnswerResponse;
import com.tspoon.qnaservice.dto.QuestionDetailResponse;
import com.tspoon.qnaservice.dto.QuestionRequest;
import com.tspoon.qnaservice.dto.QuestionSummary;
import com.tspoon.qnaservice.entity.Answer;
import com.tspoon.qnaservice.entity.Question;
import com.tspoon.qnaservice.entity.Question.QuestionStatus;
import com.tspoon.qnaservice.event.QnaEvents;
import com.tspoon.qnaservice.repository.AnswerRepository;
import com.tspoon.qnaservice.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 질문·답변·채택. 채택은 질문자만 질문당 한 번 할 수 있고, 채택된 답변은 삭제할 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class QnaService {

    private static final Set<QuestionStatus> VISIBLE = EnumSet.of(QuestionStatus.OPEN, QuestionStatus.ANSWERED, QuestionStatus.CLOSED);

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional(readOnly = true)
    public Page<QuestionSummary> search(Question.QuestionCategory category, QuestionStatus status, int page, int size) {
        Set<QuestionStatus> statuses = status == null || !VISIBLE.contains(status) ? VISIBLE : Set.of(status);
        return questionRepository.search(statuses, category, PageRequest.of(page, size)).map(QuestionSummary::from);
    }

    public QuestionDetailResponse getQuestion(Long id) {
        findVisibleQuestion(id);
        questionRepository.incrementViewCount(id);
        Question question = findVisibleQuestion(id);
        return QuestionDetailResponse.of(question, answerRepository
                .findByQuestionIdAndStatusOrderByIsAcceptedDescCreatedAtAsc(id, Answer.AnswerStatus.ACTIVE).stream()
                .map(AnswerResponse::from)
                .toList());
    }

    public QuestionDetailResponse ask(AuthUser author, QuestionRequest request) {
        Question question = questionRepository.save(Question.builder()
                .title(request.title())
                .content(request.content())
                .authorId(author.userId())
                .authorName(author.name())
                .category(request.category())
                .priority(request.priority() == null ? Question.Priority.NORMAL : request.priority())
                .tags(request.tags())
                .attachments(request.attachments())
                .status(QuestionStatus.OPEN)
                .build());
        return QuestionDetailResponse.of(question, List.of());
    }

    public QuestionDetailResponse update(AuthUser user, Long id, QuestionRequest request) {
        Question question = findVisibleQuestion(id);
        if (!question.getAuthorId().equals(user.userId())) {
            throw new BusinessException("질문을 수정할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        question.setTitle(request.title());
        question.setContent(request.content());
        question.setCategory(request.category());
        if (request.priority() != null) {
            question.setPriority(request.priority());
        }
        question.setTags(request.tags());
        question.setAttachments(request.attachments());
        questionRepository.saveAndFlush(question);
        return getQuestionWithoutCounting(id);
    }

    // 답변이 달린 질문은 다른 회원의 답변이 함께 사라지므로 작성자는 지울 수 없다 (관리자는 가능)
    public void deleteQuestion(AuthUser user, Long id) {
        Question question = findVisibleQuestion(id);
        if (user.isAdmin()) {
            question.setStatus(QuestionStatus.DELETED);
            return;
        }
        if (!question.getAuthorId().equals(user.userId())) {
            throw new BusinessException("질문을 삭제할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        if (question.getAnswerCount() > 0) {
            throw new BusinessException("답변이 달린 질문은 삭제할 수 없습니다.", HttpStatus.CONFLICT);
        }
        question.setStatus(QuestionStatus.DELETED);
    }

    public void close(AuthUser user, Long id) {
        Question question = findVisibleQuestion(id);
        if (!question.getAuthorId().equals(user.userId()) && !user.isAdmin()) {
            throw new BusinessException("질문을 닫을 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        question.setStatus(QuestionStatus.CLOSED);
    }

    public AnswerResponse answer(AuthUser author, Long questionId, AnswerRequest request) {
        Question question = findVisibleQuestion(questionId);
        if (question.getStatus() == QuestionStatus.CLOSED) {
            throw new BusinessException("닫힌 질문에는 답변할 수 없습니다.", HttpStatus.CONFLICT);
        }
        Answer answer = answerRepository.save(Answer.builder()
                .question(question)
                .content(request.content())
                .authorId(author.userId())
                .authorName(author.name())
                .type(answerTypeOf(author))
                .attachments(request.attachments())
                .status(Answer.AnswerStatus.ACTIVE)
                .build());
        outboxEventPublisher.publish(QnaEvents.TOPIC, QnaEvents.AGGREGATE_QUESTION, questionId,
                QnaEvents.ANSWER_CREATED, QnaEvents.AnswerCreated.of(question, answer));
        questionRepository.incrementAnswerCount(questionId);
        return AnswerResponse.from(answer);
    }

    public AnswerResponse updateAnswer(AuthUser user, Long answerId, AnswerRequest request) {
        Answer answer = findActiveAnswer(answerId);
        if (!answer.getAuthorId().equals(user.userId())) {
            throw new BusinessException("답변을 수정할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        answer.setContent(request.content());
        answer.setAttachments(request.attachments());
        return AnswerResponse.from(answerRepository.saveAndFlush(answer));
    }

    public void deleteAnswer(AuthUser user, Long answerId) {
        Answer answer = findActiveAnswer(answerId);
        if (!answer.getAuthorId().equals(user.userId()) && !user.isAdmin()) {
            throw new BusinessException("답변을 삭제할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        if (Boolean.TRUE.equals(answer.getIsAccepted())) {
            throw new BusinessException("채택된 답변은 삭제할 수 없습니다.", HttpStatus.CONFLICT);
        }
        answer.setStatus(Answer.AnswerStatus.DELETED);
        questionRepository.decrementAnswerCount(answer.getQuestion().getId());
    }

    public QuestionDetailResponse accept(AuthUser user, Long questionId, Long answerId) {
        Question question = findVisibleQuestion(questionId);
        if (!question.getAuthorId().equals(user.userId())) {
            throw new BusinessException("질문자만 답변을 채택할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
        Answer answer = findActiveAnswer(answerId);
        if (!answer.getQuestion().getId().equals(questionId)) {
            throw new BusinessException("이 질문에 달린 답변이 아닙니다.");
        }
        if (answer.getAuthorId().equals(user.userId())) {
            throw new BusinessException("자신의 답변은 채택할 수 없습니다.");
        }
        if (questionRepository.accept(questionId, answerId) == 0) {
            throw new BusinessException("이미 채택했거나 채택할 수 없는 질문입니다.", HttpStatus.CONFLICT);
        }
        Answer accepted = answerRepository.findById(answerId).orElseThrow();
        accepted.setIsAccepted(true);
        answerRepository.flush();
        outboxEventPublisher.publish(QnaEvents.TOPIC, QnaEvents.AGGREGATE_QUESTION, questionId,
                QnaEvents.ANSWER_ACCEPTED, QnaEvents.AnswerAccepted.of(question, accepted));
        return getQuestionWithoutCounting(questionId);
    }

    private QuestionDetailResponse getQuestionWithoutCounting(Long id) {
        return QuestionDetailResponse.of(findVisibleQuestion(id), answerRepository
                .findByQuestionIdAndStatusOrderByIsAcceptedDescCreatedAtAsc(id, Answer.AnswerStatus.ACTIVE).stream()
                .map(AnswerResponse::from)
                .toList());
    }

    private static Answer.AnswerType answerTypeOf(AuthUser author) {
        return switch (author.role()) {
            case "EXPERT" -> Answer.AnswerType.EXPERT;
            case "TEACHER" -> Answer.AnswerType.TEACHER;
            case "PARENT" -> Answer.AnswerType.PARENT;
            default -> Answer.AnswerType.GENERAL;
        };
    }

    private Question findVisibleQuestion(Long id) {
        return questionRepository.findById(id)
                .filter(question -> VISIBLE.contains(question.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("질문을 찾을 수 없습니다."));
    }

    private Answer findActiveAnswer(Long id) {
        return answerRepository.findByIdAndStatus(id, Answer.AnswerStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("답변을 찾을 수 없습니다."));
    }
}
