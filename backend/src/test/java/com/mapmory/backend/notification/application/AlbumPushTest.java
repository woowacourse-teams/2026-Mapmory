package com.mapmory.backend.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.notification.application.port.PushMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AlbumPushTest {

    private static final AlbumPushPayload EVENT = new AlbumPushPayload(3L, "제주 가족", 7L, "도쿄 3월", 1L, List.of(2L));

    @Test
    void 제목은_그룹_이름이고_본문은_올린_사람과_앨범이다() {
        AlbumPush push = AlbumPush.of(EVENT, "민호");

        assertThat(push.title()).isEqualTo("제주 가족");
        assertThat(push.body()).isEqualTo("민호님이 '도쿄 3월'에 사진을 올렸어요");
        assertThat(push.data())
                .containsExactlyInAnyOrderEntriesOf(Map.of("type", "ALBUM_UPDATED", "groupId", "3", "albumId", "7"));
    }

    @Test
    void 올린_사람_닉네임을_못_찾으면_멤버라고_부른다() {
        assertThat(AlbumPush.of(EVENT, null).body()).isEqualTo("멤버님이 '도쿄 3월'에 사진을 올렸어요");
    }

    @Test
    void 기기마다_같은_내용으로_나눈다() {
        PushMessage message = AlbumPush.of(EVENT, "민호").to("phone-a");

        assertThat(message.token()).isEqualTo("phone-a");
        assertThat(message.body()).isEqualTo("민호님이 '도쿄 3월'에 사진을 올렸어요");
    }
}
