package com.mapmory.backend.notification.domain;

/**
 * 기기 종류. iOS도 FCM 하나로 보낸다(Firebase에 APNs 키를 올림, ADR 0022 결정 7).
 */
public enum PushPlatform {
    ANDROID,
    IOS
}
