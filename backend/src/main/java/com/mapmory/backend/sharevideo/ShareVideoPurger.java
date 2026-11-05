package com.mapmory.backend.sharevideo;

import com.mapmory.backend.member.api.MemberDataPurger;
import com.mapmory.backend.upload.service.port.ObjectDeleter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 회원의 공유 영상을 S3 객체부터 지운다. 작업 행은 객체를 모두 지운 뒤에 지운다.
 *
 * <p>끝나지 않은 작업이 있으면 지우지 않는다. 키를 읽은 뒤 렌더러가 새 시도본을 올리면 행이 지워져 찾을 수 없는 객체가 남는다.
 * 파기가 맨 먼저 부르는 곳이라 다른 데이터도 그대로 두고 다음 날 다시 처리한다. 탈퇴한 회원은 새 작업을 만들 수 없으므로
 * 끝난 뒤에는 새로 생기지 않는다.
 */
@Component
@Order(1)
class ShareVideoPurger implements MemberDataPurger {

    private static final List<ShareVideoStatus> UNFINISHED = List.of(ShareVideoStatus.PENDING, ShareVideoStatus.RENDERING);

    private final ShareVideoJobRepository shareVideoJobRepository;
    private final ObjectDeleter objectDeleter;

    ShareVideoPurger(ShareVideoJobRepository shareVideoJobRepository, ObjectDeleter objectDeleter) {
        this.shareVideoJobRepository = shareVideoJobRepository;
        this.objectDeleter = objectDeleter;
    }

    @Override
    public void purge(Long memberId) {
        if (shareVideoJobRepository.existsByMemberIdAndStatusIn(memberId, UNFINISHED)) {
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
