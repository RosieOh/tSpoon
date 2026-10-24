package com.tspoon.educationservice.service;

import com.tspoon.auth.AuthUser;
import com.tspoon.core.exception.BusinessException;
import com.tspoon.core.exception.ResourceNotFoundException;
import com.tspoon.educationservice.dto.EducationContentRequest;
import com.tspoon.educationservice.dto.EducationContentResponse;
import com.tspoon.educationservice.dto.EducationContentSummary;
import com.tspoon.educationservice.entity.ContentBookmark;
import com.tspoon.educationservice.entity.EducationContent;
import com.tspoon.educationservice.entity.EducationContent.ContentStatus;
import com.tspoon.educationservice.repository.ContentBookmarkRepository;
import com.tspoon.educationservice.repository.EducationContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 교육 콘텐츠와 북마크. 공개(ACTIVE) 콘텐츠는 누구나 보고, 임시저장(DRAFT)은 작성자와 관리자만 본다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EducationContentService {

    private static final String NOT_FOUND = "교육 콘텐츠를 찾을 수 없습니다.";

    private final EducationContentRepository contentRepository;
    private final ContentBookmarkRepository bookmarkRepository;

    @Transactional(readOnly = true)
    public Page<EducationContentSummary> search(EducationContent.ContentCategory category, EducationContent.ContentType type,
                                                int page, int size) {
        return contentRepository.search(ContentStatus.ACTIVE, category, type, PageRequest.of(page, size))
                .map(EducationContentSummary::from);
    }

    /**
     * 공개 콘텐츠는 조회수를 올린다. 임시저장 콘텐츠는 작성자·관리자에게만 보이고 조회수에 넣지 않는다.
     *
     * @param viewer 비로그인이면 null
     */
    public EducationContentResponse getContent(Long id, AuthUser viewer) {
        EducationContent content = contentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));

        if (content.getStatus() == ContentStatus.ACTIVE) {
            contentRepository.incrementViewCount(id);
            return EducationContentResponse.from(contentRepository.findById(id).orElseThrow());
        }
        if (content.getStatus() == ContentStatus.DRAFT && viewer != null && canManage(content, viewer)) {
            return EducationContentResponse.from(content);
        }
        throw new ResourceNotFoundException(NOT_FOUND);
    }

    public EducationContentResponse create(AuthUser author, EducationContentRequest request) {
        EducationContent content = EducationContent.builder()
                .authorId(author.userId())
                .authorName(author.name())
                .build();
        apply(content, request);
        return EducationContentResponse.from(contentRepository.save(content));
    }

    public EducationContentResponse update(AuthUser user, Long id, EducationContentRequest request) {
        EducationContent content = findManageable(id, user, "콘텐츠를 수정할 권한이 없습니다.");
        apply(content, request);
        return EducationContentResponse.from(contentRepository.saveAndFlush(content));
    }

    public void delete(AuthUser user, Long id) {
        EducationContent content = findManageable(id, user, "콘텐츠를 삭제할 권한이 없습니다.");
        content.setStatus(ContentStatus.DELETED);
    }

    public void bookmark(AuthUser user, Long id) {
        EducationContent content = findActive(id);
        if (bookmarkRepository.existsByContentIdAndUserId(id, user.userId())) {
            throw new BusinessException("이미 북마크한 콘텐츠입니다.", HttpStatus.CONFLICT);
        }
        // 동시 요청은 (content_id, user_id) 유니크 제약이 막는다 → GlobalExceptionHandler 에서 409
        bookmarkRepository.saveAndFlush(ContentBookmark.builder()
                .content(content)
                .userId(user.userId())
                .userName(user.name())
                .build());
        contentRepository.incrementBookmarkCount(id);
    }

    public void removeBookmark(AuthUser user, Long id) {
        if (bookmarkRepository.deleteByContentIdAndUserId(id, user.userId()) == 0) {
            throw new ResourceNotFoundException("북마크를 찾을 수 없습니다.");
        }
        contentRepository.decrementBookmarkCount(id);
    }

    @Transactional(readOnly = true)
    public Page<EducationContentSummary> getMyBookmarks(AuthUser user, int page, int size) {
        return bookmarkRepository.findMyBookmarks(user.userId(), ContentStatus.ACTIVE, PageRequest.of(page, size))
                .map(bookmark -> EducationContentSummary.from(bookmark.getContent()));
    }

    private static void apply(EducationContent content, EducationContentRequest request) {
        content.setTitle(request.title());
        content.setDescription(request.description());
        content.setContent(request.content());
        content.setType(request.type());
        content.setCategory(request.category());
        content.setTags(request.tags());
        content.setAttachments(request.attachments());
        content.setThumbnailUrl(request.thumbnailUrl());
        content.setEstimatedTime(request.estimatedTime());
        content.setTargetAge(request.targetAge());
        content.setStatus(request.draft() ? ContentStatus.DRAFT : ContentStatus.ACTIVE);
    }

    private EducationContent findActive(Long id) {
        return contentRepository.findById(id)
                .filter(content -> content.getStatus() == ContentStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    // 삭제·숨김 상태가 아닌 콘텐츠 중 작성자 본인 또는 관리자가 다룰 수 있는 것
    private EducationContent findManageable(Long id, AuthUser user, String forbiddenMessage) {
        EducationContent content = contentRepository.findById(id)
                .filter(found -> found.getStatus() == ContentStatus.ACTIVE || found.getStatus() == ContentStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (!canManage(content, user)) {
            throw new BusinessException(forbiddenMessage, HttpStatus.FORBIDDEN);
        }
        return content;
    }

    private static boolean canManage(EducationContent content, AuthUser user) {
        return content.getAuthorId().equals(user.userId()) || user.isAdmin();
    }
}
