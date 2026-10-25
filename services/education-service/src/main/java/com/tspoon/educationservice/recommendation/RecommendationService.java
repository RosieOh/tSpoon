package com.tspoon.educationservice.recommendation;

import com.tspoon.auth.AuthUser;
import com.tspoon.educationservice.dto.EducationContentSummary;
import com.tspoon.educationservice.entity.EducationContent;
import com.tspoon.educationservice.entity.EducationContent.ContentCategory;
import com.tspoon.educationservice.repository.EducationContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 자녀 학년에 맞는 교육 콘텐츠 추천.
 * <ol>
 *   <li>자녀 학년에 해당하는 교육 단계(유아·초등·중등·고등) 카테고리</li>
 *   <li>육아</li>
 *   <li>그 밖의 콘텐츠</li>
 * </ol>
 * 같은 순위 안에서는 북마크·조회가 많은 순, 최신순이다. 이미 북마크한 콘텐츠는 뺀다.
 * 비로그인이거나 자녀 정보가 없으면 인기순으로 보여 준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private final EducationContentRepository contentRepository;
    private final ChildGradeProjection childGrades;

    /** @param viewer 비로그인이면 null */
    public RecommendationResponse recommend(AuthUser viewer, int size) {
        Long userId = viewer == null ? null : viewer.userId();
        Set<ContentCategory> stages = userId == null ? EnumSet.noneOf(ContentCategory.class) : stagesOf(childGrades.gradesOf(userId));
        PageRequest limit = PageRequest.of(0, size);

        List<EducationContent> contents = stages.isEmpty()
                ? contentRepository.findPopular(userId, limit)
                : contentRepository.findRecommended(stages, userId, limit);
        return new RecommendationResponse(List.copyOf(stages), contents.stream().map(EducationContentSummary::from).toList());
    }

    static Set<ContentCategory> stagesOf(List<String> grades) {
        Set<ContentCategory> stages = EnumSet.noneOf(ContentCategory.class);
        for (String grade : grades) {
            if (grade.equals("KINDERGARTEN")) {
                stages.add(ContentCategory.EARLY_CHILDHOOD);
            } else if (grade.startsWith("ELEMENTARY_")) {
                stages.add(ContentCategory.ELEMENTARY);
            } else if (grade.startsWith("MIDDLE_")) {
                stages.add(ContentCategory.MIDDLE_SCHOOL);
            } else if (grade.startsWith("HIGH_")) {
                stages.add(ContentCategory.HIGH_SCHOOL);
            }
        }
        return stages;
    }

    /**
     * @param basedOn 추천 기준이 된 교육 단계. 비어 있으면 인기순
     */
    public record RecommendationResponse(List<ContentCategory> basedOn, List<EducationContentSummary> contents) {
    }
}
