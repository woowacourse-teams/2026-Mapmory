package com.mapmory.backend.notification.application.port;

import java.util.List;

/**
 * 푸시를 보내는 포트. 제공자(FCM)는 infrastructure의 어댑터가 감싼다(ADR 0022 결정 7, ADR 0017 2026-10-07 보완).
 *
 * <p>발송 실패는 예외로 올리지 않고 결과로 돌려준다. 한 기기의 실패가 다른 기기의 발송을 막지 않게 한다.
 */
public interface PushSender {

    /**
     * @return 제공자가 더 이상 유효하지 않다고 답한 토큰. 부르는 쪽이 지운다
     */
    List<String> send(List<PushMessage> messages);
}
