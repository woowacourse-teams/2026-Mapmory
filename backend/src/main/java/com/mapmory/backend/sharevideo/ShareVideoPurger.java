package com.mapmory.backend.sharevideo;

import com.mapmory.backend.member.api.MemberDataPurger;
import com.mapmory.backend.upload.service.port.ObjectDeleter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 회원의 공유 영상을 S3 객체부터 지운다. 작업 행은 객체를 모두 지운 뒤에 지운다.
 */
@Component
@Order(1)
class ShareVideoPurger implements MemberDataPurger {

    private final ShareVideoJobRepository shareVideoJobRepository;
    private final ObjectDeleter objectDeleter;

    ShareVideoPurger(ShareVideoJobRepository shareVideoJobRepository, ObjectDeleter objectDeleter) {
        this.shareVideoJobRepository = shareVideoJobRepository;
        this.objectDeleter = objectDeleter;
    }

    @Override
    public void purge(Long memberId) {
        List<String> keys = new ArrayList<>(shareVideoJobRepository.findResultKeysByMemberId(memberId));
        keys.addAll(shareVideoJobRepository.findAttemptKeysByMemberId(memberId));
        List<String> failed = objectDeleter.delete(keys);
        if (!failed.isEmpty()) {
            throw new IllegalStateException("공유 영상 객체를 지우지 못했다. memberId=" + memberId + ", keys=" + failed.size());
        }
        shareVideoJobRepository.deleteAllByMemberId(memberId);
    }
}
