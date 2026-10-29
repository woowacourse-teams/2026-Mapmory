package com.mapmory.backend.notification.application.port;

import java.util.Map;

/**
 * 기기 하나에 보낼 푸시. data는 앱이 눌렀을 때 이동할 곳을 정하는 값이다.
 */
public record PushMessage(String token, String title, String body, Map<String, String> data) {
    public PushMessage {
        data = Map.copyOf(data);
    }
}
