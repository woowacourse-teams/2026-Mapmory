package com.mapmory.backend.notification.infrastructure;

import com.mapmory.backend.notification.application.port.PushMessage;
import com.mapmory.backend.notification.application.port.PushSender;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FCM 어댑터가 없을 때 쓰는 발송기. 보내지 않고 남기기만 한다.
 *
 * <p>Firebase 서비스 계정 키는 인프라 작업으로 따로 둔다(ADR 0022 결정 7). FCM 어댑터를 빈으로 올리면 이 발송기는 쓰이지 않는다.
 */
class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public List<String> send(List<PushMessage> messages) {
        log.atInfo()
                .addKeyValue("event", "PUSH_NOT_SENT")
                .addKeyValue("count", messages.size())
                .log("푸시 발송기가 없어 보내지 않았다");
        return List.of();
    }
}
