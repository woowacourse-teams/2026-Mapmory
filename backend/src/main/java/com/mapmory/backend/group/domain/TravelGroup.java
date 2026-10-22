package com.mapmory.backend.group.domain;

import com.mapmory.backend.common.entity.BaseEntity;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.exception.GroupErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 여행을 같이 기록하는 사람들의 묶음. 멤버 목록을 함께 가진 애그리거트 루트다.
 *
 * <p>멤버 수 제한(30명), 같은 사람은 한 번만, 방장 넘기기는 멤버 목록을 다 봐야 지킬 수 있어서 멤버를 이 안에 둔다.
 * 회원은 id로만 가리킨다(ADR 0017 결정 2). 초대와 앨범은 따로 둔 애그리거트이고 이 그룹을 id로 가리킨다.
 */
@Entity
@Table(name = "travel_group")
public class TravelGroup extends BaseEntity {

    // travel_group.name 컬럼 길이(VARCHAR(30))와 같다.
    private static final int MAX_NAME_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "owner_member_id", nullable = false)
    private Long ownerMemberId;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("joinedAt ASC, id ASC")
    private List<GroupMember> members = new ArrayList<>();

    protected TravelGroup() {
    }

    private TravelGroup(String name, Long ownerMemberId) {
        this.name = validateName(name);
        this.ownerMemberId = Objects.requireNonNull(ownerMemberId, "ownerMemberId must not be null");
    }

    /**
     * 그룹을 만든 사람이 첫 멤버이자 방장이다.
     */
    public static TravelGroup create(String name, Long creatorMemberId, LocalDateTime createdAt) {
        TravelGroup group = new TravelGroup(name, creatorMemberId);
        group.members.add(new GroupMember(group, creatorMemberId, createdAt));
        return group;
    }

    public boolean hasMember(Long memberId) {
        return members.stream().anyMatch(member -> member.isMember(memberId));
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(GroupErrorCode.INVALID_GROUP_NAME);
        }
        String trimmed = name.strip();
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_NAME_LENGTH) {
            throw new BusinessException(GroupErrorCode.INVALID_GROUP_NAME);
        }
        return trimmed;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getOwnerMemberId() {
        return ownerMemberId;
    }

    public List<GroupMember> getMembers() {
        return List.copyOf(members);
    }
}
