package com.mapmory.backend.admin.member;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.exception.MemberErrorCode;
import com.mapmory.backend.travelrecord.AdminTravelRecordQueryRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminMemberService {

    private final MemberRepository memberRepository;
    private final AdminTravelRecordQueryRepository adminTravelRecordQueryRepository;

    public AdminMemberService(
            MemberRepository memberRepository,
            AdminTravelRecordQueryRepository adminTravelRecordQueryRepository
    ) {
        this.memberRepository = memberRepository;
        this.adminTravelRecordQueryRepository = adminTravelRecordQueryRepository;
    }

    @Transactional(readOnly = true)
    public AdminMemberListResponse getMembers(String keyword, AuthProvider provider, int page, int size) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Long memberId = parseMemberId(normalizedKeyword);

        Page<Member> result = memberRepository.searchForAdmin(
                provider,
                normalizedKeyword,
                memberId,
                PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                                .and(Sort.by(Sort.Direction.DESC, "id"))
                )
        );

        List<Long> memberIds = result.getContent().stream()
                .map(Member::getId)
                .toList();
        Map<Long, Long> recordCounts = memberIds.isEmpty()
                ? Map.of()
                : adminTravelRecordQueryRepository.countByMemberIds(memberIds).stream()
                        .collect(Collectors.toMap(
                                AdminTravelRecordQueryRepository.MemberRecordCount::getMemberId,
                                AdminTravelRecordQueryRepository.MemberRecordCount::getRecordCount
                        ));

        List<AdminMemberListResponse.MemberItem> members = result.getContent().stream()
                .map(member -> new AdminMemberListResponse.MemberItem(
                        member.getId(),
                        displayName(member),
                        member.getProvider(),
                        member.getCreatedAt(),
                        recordCounts.getOrDefault(member.getId(), 0L)
                ))
                .toList();

        return new AdminMemberListResponse(
                members,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public AdminMemberDetailResponse getMemberDetail(Long memberId, int page, int size) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

        Page<AdminTravelRecordQueryRepository.MemberRecordDetail> recordPage =
                adminTravelRecordQueryRepository.findAdminRecordDetailsByMemberId(
                        memberId,
                        PageRequest.of(page, size)
                );

        List<AdminMemberDetailResponse.TravelRecordItem> records = recordPage.getContent().stream()
                .map(record -> new AdminMemberDetailResponse.TravelRecordItem(
                        record.getRecordId(),
                        record.getCreatedAt(),
                        record.getPhotoCount()
                ))
                .toList();

        return new AdminMemberDetailResponse(
                new AdminMemberDetailResponse.MemberSummary(
                        member.getId(),
                        displayName(member),
                        member.getProvider(),
                        member.getCreatedAt()
                ),
                recordPage.getTotalElements(),
                adminTravelRecordQueryRepository.countMediaByMemberId(memberId),
                records,
                recordPage.getNumber(),
                recordPage.getSize(),
                recordPage.getTotalElements(),
                recordPage.getTotalPages()
        );
    }

    private Long parseMemberId(String keyword) {
        if (keyword == null) {
            return null;
        }
        try {
            return Long.valueOf(keyword);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String displayName(Member member) {
        String name = member.getName();
        // 닉네임을 제공하지 않은 소셜 회원은 현재 "회원12345" 형식으로 저장된다.
        if (name == null || name.isBlank() || name.matches("^회원\\d{5}$")) {
            return String.valueOf(member.getId());
        }
        return name;
    }
}
