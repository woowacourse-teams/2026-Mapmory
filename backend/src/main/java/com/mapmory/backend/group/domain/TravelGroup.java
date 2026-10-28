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
 *
 * <p>인원 수(memberCount)를 그룹 행에 따로 둔다. 동시에 수락할 때 그룹 행을 잠그고 읽으면 이 값은 항상 최신이지만,
 * 멤버 목록은 따로 읽어서 옛 스냅샷일 수 있다. 그래서 30명 제한은 멤버 목록이 아니라 이 값으로 판단한다.
 * 멤버 목록을 바꾸는 메서드가 이 값도 함께 바꾼다.
 */
@Entity
@Table(name = "travel_group")
public class TravelGroup extends BaseEntity {

    // travel_group.name 컬럼 길이(VARCHAR(30))와 같다.
    private static final int MAX_NAME_LENGTH = 30;
    private static final int MAX_MEMBERS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "owner_member_id", nullable = false)
    private Long ownerMemberId;

    @Column(name = "member_count", nullable = false)
    private int memberCount;

    // 멤버는 이 그룹과 생명주기가 같은 애그리거트 내부 엔티티다. 새 멤버는 함께 저장하고(PERSIST), 빠지면 지운다(orphanRemoval).
    // 그룹 삭제는 아직 없다. 생기면 group_member FK에 ON DELETE CASCADE를 더하고 @OnDelete로 맡긴다(ADR 0017 cascade 규칙).
    @OneToMany(mappedBy = "group", cascade = CascadeType.PERSIST, orphanRemoval = true)
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
        group.memberCount = 1;
        return group;
    }

    /**
     * 멤버 누구나 이름을 바꾼다. 멤버인지는 부르는 쪽이 먼저 확인한다.
     */
    public void rename(String name) {
        this.name = validateName(name);
    }

    /**
     * 초대를 수락한 회원을 멤버로 넣는다. 이미 멤버면 아무것도 바꾸지 않는다.
     */
    public void join(Long memberId, LocalDateTime joinedAt) {
        if (hasMember(memberId)) {
            return;
        }
        if (isFull()) {
            throw new BusinessException(GroupErrorCode.GROUP_FULL);
        }
        members.add(new GroupMember(this, memberId, joinedAt));
        memberCount++;
    }

    public boolean isFull() {
        return memberCount >= MAX_MEMBERS;
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

    public int getMemberCount() {
        return memberCount;
    }

    public List<GroupMember> getMembers() {
        return List.copyOf(members);
    }
}
