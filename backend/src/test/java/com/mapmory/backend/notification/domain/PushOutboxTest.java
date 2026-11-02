package com.mapmory.backend.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PushOutboxTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 9, 0);

    @Test
    void 적으면_바로_보낼_차례다() {
        PushOutbox outbox = PushOutbox.pending(PushOutboxType.ALBUM_UPDATED, "{}", NOW);

        assertThat(outbox.getStatus()).isEqualTo(PushOutboxStatus.PENDING);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(outbox.getAttempts()).isZero();
    }

    @Test
    void 집어_가면_5분_동안_다시_집히지_않는다() {
        PushOutbox outbox = PushOutbox.pending(PushOutboxType.ALBUM_UPDATED, "{}", NOW);

        outbox.lease(NOW);

        assertThat(outbox.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(5));
        assertThat(outbox.getStatus()).isEqualTo(PushOutboxStatus.PENDING);
    }

    @Test
    void 실패하면_30초_2분_10분_30분_뒤에_다시_보내고_다섯_번째에_포기한다() {
        PushOutbox outbox = PushOutbox.pending(PushOutboxType.ALBUM_UPDATED, "{}", NOW);

        outbox.failed(NOW, "timeout");
        assertThat(outbox.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        outbox.failed(NOW, "timeout");
        assertThat(outbox.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(2));
        outbox.failed(NOW, "timeout");
        assertThat(outbox.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(10));
        outbox.failed(NOW, "timeout");
        assertThat(outbox.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(30));
        assertThat(outbox.isGivenUp()).isFalse();

        outbox.failed(NOW, "timeout");

        assertThat(outbox.isGivenUp()).isTrue();
        assertThat(outbox.getStatus()).isEqualTo(PushOutboxStatus.FAILED);
        assertThat(outbox.getAttempts()).isEqualTo(PushOutbox.MAX_ATTEMPTS);
    }

    @Test
    void 실패_이유는_500자까지만_남긴다() {
        PushOutbox outbox = PushOutbox.pending(PushOutboxType.ALBUM_UPDATED, "{}", NOW);

        outbox.failed(NOW, "x".repeat(600));

        assertThat(outbox.getLastError()).hasSize(500);
    }

    @Test
    void 보내면_끝난다() {
        PushOutbox outbox = PushOutbox.pending(PushOutboxType.ALBUM_UPDATED, "{}", NOW);

        outbox.sent();

        assertThat(outbox.getStatus()).isEqualTo(PushOutboxStatus.SENT);
    }
}
