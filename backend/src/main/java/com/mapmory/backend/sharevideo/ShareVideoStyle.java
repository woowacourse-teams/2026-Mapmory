package com.mapmory.backend.sharevideo;

public enum ShareVideoStyle {
    CLASSIC,
    ZOOM,
    ORBIT,
    HORIZON;

    public static ShareVideoStyle defaultStyle() {
        return CLASSIC;
    }
}
