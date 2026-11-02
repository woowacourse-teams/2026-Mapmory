package com.mapmory.backend.sharevideo.dto;

import com.mapmory.backend.sharevideo.ShareVideoStyle;
import jakarta.validation.constraints.Positive;

public record CreateShareVideoRequest(
        ShareVideoStyle styleId,
        // 알림을 받을 푸시 등록 ID.
        // 생략하면 알림 없이 영상만 생성한다.
        @Positive Long pushTokenId
) {
    public ShareVideoStyle resolvedStyle() {
        if (styleId == null) {
            return ShareVideoStyle.defaultStyle();
        }
        return styleId;
    }
}
