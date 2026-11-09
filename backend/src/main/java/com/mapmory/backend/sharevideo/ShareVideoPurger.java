package com.mapmory.backend.sharevideo;

import com.mapmory.backend.member.api.MemberDataPurger;
import com.mapmory.backend.upload.service.port.ObjectDeleter;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 회원의 공유 영상을 S3 객체부터 지운다. 작업 행은 객체를 모두 지운 뒤에 지운다.
 *
 * <p>끝나지 않은 작업이 있으면 지우지 않는다. 키를 읽은 뒤 렌더러가 새 시도본을 올리면 행이 지워져 찾을 수 없는 객체가 남는다.
 * 파기가 맨 먼저 부르는 곳이라 다른 데이터도 그대로 두고 다음 날 다시 처리한다. 탈퇴한 회원은 새 작업을 만들 수 없으므로
 * 끝난 뒤에는 새로 생기지 않는다. 워커가 렌더링 중에 죽으면 작업이 RENDERING으로 남고 되살리는 곳이 없으므로,
 * 선점이 만료되고 1시간 지난 작업은 끝난 것으로 본다. 시도본 키는 선점할 때 행에 적혀 있어 함께 지워진다.
 */
@Component
@Order(1)
class ShareVideoPurger implements MemberDataPurger {

    // 선점이 만료된 직후에는 죽은 줄 안 워커가 아직 올리는 중일 수 있다. 업로드 한도(30초)보다 넉넉히 기다린다.
    private static final Duration LEASE_GRACE = Duration.ofHours(1);

    private final ShareVideoJobRepository shareVideoJobRepository;
    private final ObjectDeleter objectDeleter;
    private final Clock clock;

    ShareVideoPurger(ShareVideoJobRepository shareVideoJobRepository, ObjectDeleter objectDeleter, Clock clock) {
        this.shareVideoJobRepository = shareVideoJobRepository;
        this.objectDeleter = objectDeleter;
        this.clock = clock;
    }

    @Override
    public void purge(Long memberId) {
        if (shareVideoJobRepository.existsUnfinishedByMemberId(memberId, LocalDateTime.now(clock).minus(LEASE_GRACE))) {
            throw new IllegalStateException("끝나지 않은 공유 영상이 있어 다음에 파기한다. memberId=" + memberId);
        }
        List<String> keys = new ArrayList<>(shareVideoJobRepository.findResultKeysByMemberId(memberId));
        keys.addAll(shareVideoJobRepository.findAttemptKeysByMemberId(memberId));
        List<String> failed = objectDeleter.delete(keys);
        if (!failed.isEmpty()) {
            throw new IllegalStateException("공유 영상 객체를 지우지 못했다. memberId=" + memberId + ", keys=" + failed.size());
        }
        shareVideoJobRepository.deleteAllByMemberId(memberId);
    }
}
