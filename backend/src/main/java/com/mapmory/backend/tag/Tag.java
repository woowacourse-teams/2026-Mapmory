package com.mapmory.backend.tag;

import com.mapmory.backend.common.entity.BaseEntity;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.text.Normalizer;
import java.util.Locale;

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

    @Column(nullable = false, length = 30)
    private String name;

    @Column(name = "name_key", nullable = false, length = 30)
    private String nameKey;

    protected Tag() {
    }

    private Tag(Member member, TagName tagName) {
        this.member = member;
        applyName(tagName);
    }

    public static Tag of(Member member, String rawName) {
        return new Tag(member, TagName.from(rawName));
    }

    public void rename(String rawName) {
        applyName(TagName.from(rawName));
    }

    static String nameKeyOf(String rawName) {
        return TagName.from(rawName).nameKey();
    }

    private void applyName(TagName tagName) {
        this.name = tagName.displayName();
        this.nameKey = tagName.nameKey();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    String getNameKey() {
        return nameKey;
    }

    private record TagName(String displayName, String nameKey) {
        private static final int MAX_LENGTH = 30;
        private static final String FORBIDDEN_CHARACTERS = "#";

        private static TagName from(String rawName) {
            validateNotNull(rawName);

            String withoutOuterWhitespace = rawName.strip();
            String withSingleSpaces = collapseConsecutiveWhitespace(withoutOuterWhitespace);
            String displayName = normalizeUnicode(withSingleSpaces);

            validateLength(displayName);
            validateForbiddenCharacters(displayName);

            String nameKey = createNameKey(displayName);
            return new TagName(displayName, nameKey);
        }

        private static void validateNotNull(String rawName) {
            if (rawName == null) {
                throwInvalidTagName();
            }
        }

        private static String collapseConsecutiveWhitespace(String name) {
            return name.replaceAll("\\p{javaWhitespace}+", " ");
        }

        private static String normalizeUnicode(String name) {
            return Normalizer.normalize(name, Normalizer.Form.NFC);
        }

        private static void validateLength(String name) {
            int length = name.codePointCount(0, name.length());
            if (length < 1 || length > MAX_LENGTH) {
                throwInvalidTagName();
            }
        }

        private static void validateForbiddenCharacters(String name) {
            boolean containsForbiddenCharacter = name.codePoints()
                    .anyMatch(codePoint -> FORBIDDEN_CHARACTERS.indexOf(codePoint) >= 0);
            boolean containsControlCharacter = name.codePoints().anyMatch(Character::isISOControl);
            if (containsForbiddenCharacter || containsControlCharacter) {
                throwInvalidTagName();
            }
        }

        private static String createNameKey(String displayName) {
            return displayName.toLowerCase(Locale.ROOT);
        }

        private static void throwInvalidTagName() {
            throw new BusinessException(TagErrorCode.INVALID_TAG_NAME);
        }
    }
}
