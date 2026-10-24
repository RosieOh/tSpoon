package com.tspoon.qnaservice.controller;

import com.tspoon.auth.AuthUser;
import com.tspoon.qnaservice.dto.AnswerRequest;
import com.tspoon.qnaservice.dto.AnswerResponse;
import com.tspoon.qnaservice.dto.QuestionDetailResponse;
import com.tspoon.qnaservice.dto.QuestionRequest;
import com.tspoon.qnaservice.dto.QuestionSummary;
import com.tspoon.qnaservice.entity.Question;
import com.tspoon.qnaservice.service.QnaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Q&A", description = "질문·답변·채택")
@RestController
@RequiredArgsConstructor
public class QnaController {

    private final QnaService qnaService;

    @Operation(summary = "질문 목록 (최신순, 카테고리·상태 필터)")
    @SecurityRequirements
    @GetMapping("/api/questions")
    public ResponseEntity<PagedModel<QuestionSummary>> search(
            @RequestParam(required = false) Question.QuestionCategory category,
            @RequestParam(required = false) Question.QuestionStatus status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 는 1 이상이어야 합니다")
            @Max(value = 100, message = "size 는 100 이하여야 합니다") int size) {
        return ResponseEntity.ok(new PagedModel<>(qnaService.search(category, status, page, size)));
    }

    @Operation(summary = "질문 상세 — 조회수 증가, 채택 답변이 먼저")
    @SecurityRequirements
    @GetMapping("/api/questions/{id}")
    public ResponseEntity<QuestionDetailResponse> getQuestion(@PathVariable Long id) {
        return ResponseEntity.ok(qnaService.getQuestion(id));
    }

    @Operation(summary = "질문하기")
    @PostMapping("/api/questions")
    public ResponseEntity<QuestionDetailResponse> ask(@AuthenticationPrincipal AuthUser user,
                                                      @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(qnaService.ask(user, request));
    }

    @Operation(summary = "질문 수정 (작성자만)")
    @PutMapping("/api/questions/{id}")
    public ResponseEntity<QuestionDetailResponse> update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                                         @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(qnaService.update(user, id, request));
    }

    @Operation(summary = "질문 삭제 — 작성자는 답변이 없을 때만, 관리자는 언제나")
    @DeleteMapping("/api/questions/{id}")
    public ResponseEntity<Void> deleteQuestion(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        qnaService.deleteQuestion(user, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "질문 닫기 — 더 이상 답변을 받지 않음 (작성자·관리자)")
    @PostMapping("/api/questions/{id}/close")
    public ResponseEntity<Void> close(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        qnaService.close(user, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "답변하기 — 답변 유형은 답변자의 역할(전문가·교사·학부모)로 정해진다")
    @PostMapping("/api/questions/{id}/answers")
    public ResponseEntity<AnswerResponse> answer(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                                 @Valid @RequestBody AnswerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(qnaService.answer(user, id, request));
    }

    @Operation(summary = "답변 채택 — 질문자만, 질문당 한 번")
    @PostMapping("/api/questions/{id}/answers/{answerId}/accept")
    public ResponseEntity<QuestionDetailResponse> accept(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                                         @PathVariable Long answerId) {
        return ResponseEntity.ok(qnaService.accept(user, id, answerId));
    }

    @Operation(summary = "답변 수정 (작성자만)")
    @PutMapping("/api/answers/{answerId}")
    public ResponseEntity<AnswerResponse> updateAnswer(@AuthenticationPrincipal AuthUser user, @PathVariable Long answerId,
                                                       @Valid @RequestBody AnswerRequest request) {
        return ResponseEntity.ok(qnaService.updateAnswer(user, answerId, request));
    }

    @Operation(summary = "답변 삭제 (작성자·관리자) — 채택된 답변은 삭제할 수 없음")
    @DeleteMapping("/api/answers/{answerId}")
    public ResponseEntity<Void> deleteAnswer(@AuthenticationPrincipal AuthUser user, @PathVariable Long answerId) {
        qnaService.deleteAnswer(user, answerId);
        return ResponseEntity.noContent().build();
    }
}
