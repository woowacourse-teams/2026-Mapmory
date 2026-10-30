package com.mapmory.shared.domain.model

enum class ServiceFeedbackCategory(val label: String) {
    BUG_REPORT("오류 제보"),
    FEATURE_REQUEST("기능 제안"),
    USABILITY("사용성, 불편"),
    OTHER("기타"),
}

enum class ServiceFeedbackPlatform {
    IOS,
    ANDROID,
}
