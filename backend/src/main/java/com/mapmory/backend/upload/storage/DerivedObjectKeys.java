package com.mapmory.backend.upload.storage;

import com.mapmory.backend.upload.domain.UploadFileType;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 원본 사진에서 만든 사본(썸네일)의 키 규칙. 사본은 원본과 다른 경로에 둔다.
 *
 * <pre>
 * 원본:   {keyPrefix}/travel-records/{memberId}/{name}.{ext}
 * 썸네일: {keyPrefix}/derived/thumb/{memberId}/{name}.webp
 * </pre>
 *
 * <p>경로를 나누면 사본을 쓰는 쪽을 derived/ 아래로 묶어 원본을 덮어쓸 수 없고, 고아 객체 대조 배치(travel-records/만 읽는다)와도
 * 겹치지 않는다. 이 서버의 접두사 밖의 키(접두사를 붙이기 전에 저장된 옛 키)와 낯선 형식은 사본을 만들지 않는다.
 */
@Component
public class DerivedObjectKeys {

    private static final String THUMBNAIL_EXTENSION = ".webp";

    private final Pattern original;
    private final Pattern thumbnail;
    private final String travelRecordsDirectory;
    private final String thumbnailDirectory;
    private final String derivedDirectory;

    public DerivedObjectKeys(S3StorageProperties properties) {
        // 서버가 발급하는 확장자만 받는다(S3ObjectKeyGenerator). 정리 배치의 역조회(originalCandidatesOf)와 같은 규칙이다.
        this.original = Pattern.compile(Pattern.quote(properties.travelRecordsDirectory())
                + "(\\d+)/([^/.][^/]*)\\.(?:" + String.join("|", canonicalExtensions()) + ")");
        this.travelRecordsDirectory = properties.travelRecordsDirectory();
        this.derivedDirectory = properties.keyPrefix() + "/derived/";
        this.thumbnailDirectory = derivedDirectory + "thumb/";
        this.thumbnail = Pattern.compile(Pattern.quote(thumbnailDirectory) + "(\\d+)/([^/.][^/]*)\\.webp");
    }

    /**
     * @return 우리 원본 형식이 아니면 빈 값
     */
    public Optional<String> thumbnailOf(String originalKey) {
        if (originalKey == null) {
            return Optional.empty();
        }
        Matcher matcher = original.matcher(originalKey);
        if (!matcher.matches() || matcher.group(2).contains("..")) {
            return Optional.empty();
        }
        return Optional.of(thumbnailDirectory + matcher.group(1) + "/" + matcher.group(2) + THUMBNAIL_EXTENSION);
    }

    /**
     * 이 썸네일을 만든 원본일 수 있는 키들. 서버가 발급하는 확장자마다 하나씩이다. 썸네일 키가 아니면 비어 있다.
     */
    public List<String> originalCandidatesOf(String thumbnailKey) {
        if (thumbnailKey == null) {
            return List.of();
        }
        Matcher matcher = thumbnail.matcher(thumbnailKey);
        if (!matcher.matches() || matcher.group(2).contains("..")) {
            return List.of();
        }
        String stem = travelRecordsDirectory + matcher.group(1) + "/" + matcher.group(2) + ".";
        return canonicalExtensions().stream()
                .map(extension -> stem + extension)
                .toList();
    }

    private static List<String> canonicalExtensions() {
        return Arrays.stream(UploadFileType.values()).map(UploadFileType::canonicalExtension).toList();
    }

    public boolean isDerived(String objectKey) {
        return objectKey != null && objectKey.startsWith(derivedDirectory) && !objectKey.contains("..");
    }
}
