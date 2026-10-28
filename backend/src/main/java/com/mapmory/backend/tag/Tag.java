package com.mapmory.backend.tag;

import com.mapmory.backend.common.entity.BaseEntity;
import com.mapmory.backend.member.Member;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "tag",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tag_member_name_key",
                columnNames = {"member_id", "name_key"}
        )
)
public class Tag extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Embedded
    private TagName tagName;

    protected Tag() {
    }

    private Tag(Member member, TagName tagName) {
        this.member = member;
        this.tagName = tagName;
    }

    public static Tag of(Member member, String rawName) {
        return new Tag(member, TagName.from(rawName));
    }

    public void rename(String rawName) {
        this.tagName = TagName.from(rawName);
    }

    static String nameKeyOf(String rawName) {
        return TagName.from(rawName).nameKey();
    }

    /**
     * member는 지연 로딩이지만 id만 읽으므로 회원을 조회하지 않는다.
     */
    public boolean isOwnedBy(Long memberId) {
        return memberId != null && memberId.equals(member.getId());
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return tagName.displayName();
    }

    String getNameKey() {
        return tagName.nameKey();
    }
}
