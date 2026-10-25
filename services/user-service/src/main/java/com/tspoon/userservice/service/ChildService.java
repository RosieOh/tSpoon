package com.tspoon.userservice.service;

import com.tspoon.core.exception.BusinessException;
import com.tspoon.core.exception.ResourceNotFoundException;
import com.tspoon.userservice.dto.ChildRequest;
import com.tspoon.userservice.dto.ChildResponse;
import com.tspoon.userservice.entity.Child;
import com.tspoon.userservice.entity.User;
import com.tspoon.userservice.repository.ChildRepository;
import com.tspoon.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChildService {

    static final int MAX_CHILDREN = 10;

    private final ChildRepository childRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ChildResponse> getChildren(Long userId) {
        return childRepository.findAllByParentIdOrderByIdAsc(userId).stream()
                .map(ChildResponse::from)
                .toList();
    }

    public ChildResponse create(Long userId, ChildRequest request) {
        // 같은 회원의 등록 요청이 동시에 들어와도 상한을 넘지 않도록 회원 행을 잠근 뒤 센다
        User parent = userRepository.findLockedById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        if (childRepository.countByParentId(userId) >= MAX_CHILDREN) {
            throw new BusinessException("자녀는 최대 " + MAX_CHILDREN + "명까지 등록할 수 있습니다.", HttpStatus.CONFLICT);
        }

        Child child = Child.builder().parent(parent).build();
        apply(child, request);
        return ChildResponse.from(childRepository.save(child));
    }

    public ChildResponse update(Long userId, Long childId, ChildRequest request) {
        Child child = findOwnChild(userId, childId);
        apply(child, request);
        return ChildResponse.from(child);
    }

    public void delete(Long userId, Long childId) {
        childRepository.delete(findOwnChild(userId, childId));
    }

    private Child findOwnChild(Long userId, Long childId) {
        return childRepository.findByIdAndParentId(childId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("자녀 정보를 찾을 수 없습니다."));
    }

    private static void apply(Child child, ChildRequest request) {
        child.setName(request.name().strip());
        child.setBirthDate(request.birthDate());
        child.setGrade(request.grade());
        child.setSchool(request.school());
        child.setNotes(request.notes());
        child.replaceInterests(request.normalizedInterests());
    }
}
