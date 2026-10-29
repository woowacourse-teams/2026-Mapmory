package com.mapmory.backend.notification.application;

import com.mapmory.backend.group.api.AlbumUpdated;
import com.mapmory.backend.notification.domain.PushOutbox;
import com.mapmory.backend.notification.domain.PushOutboxRepository;
import com.mapmory.backend.notification.domain.PushOutboxType;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 앨범 알림을 앨범 쓰기와 같은 트랜잭션에 적어 둔다(ADR 0022 2026-10-29 보완: 아웃박스).
 *
 * <p>쓰기가 커밋되면 알림도 반드시 남고, 롤백되면 같이 사라진다. 보내기는 {@link PushOutboxRelay}가 따로 한다.
 * 앨범 쓰기 트랜잭션 밖에서 이벤트가 오면 적을 곳이 없으므로 MANDATORY로 막는다.
 */
@Component
public class AlbumPushOutboxWriter {

    private final PushOutboxRepository pushOutboxRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AlbumPushOutboxWriter(PushOutboxRepository pushOutboxRepository, JsonMapper jsonMapper, Clock clock) {
        this.pushOutboxRepository = pushOutboxRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onAlbumUpdated(AlbumUpdated event) {
        if (event.recipientMemberIds().isEmpty()) {
            return;
        }
        pushOutboxRepository.save(PushOutbox.pending(
                PushOutboxType.ALBUM_UPDATED, jsonMapper.writeValueAsString(AlbumPushPayload.from(event)), LocalDateTime.now(clock)));
    }
}
