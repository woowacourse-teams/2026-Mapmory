package com.mapmory.backend.sharevideo.dto;

import com.mapmory.backend.sharevideo.ShareVideoStyle;

public record CreateShareVideoRequest(
        ShareVideoStyle styleId
) {
    public ShareVideoStyle resolvedStyle() {
        if (styleId == null) {
            return ShareVideoStyle.defaultStyle();
        }
        return styleId;
    }
}
