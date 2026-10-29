package com.mapmory.backend.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mapmory.backend.group.api.AlbumUpdated;
import com.mapmory.backend.member.api.MemberQuery;
import com.mapmory.backend.notification.application.port.PushMessage;
import com.mapmory.backend.notification.application.port.PushSender;
import com.mapmory.backend.notification.domain.PushPlatform;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AlbumPushListenerTest {

    private static final AlbumUpdated EVENT = new AlbumUpdated(3L, "제주 가족", 7L, "도쿄 3월", 1L, List.of(2L, 4L));

    private final PushTokenRepository pushTokenRepository = mock(PushTokenRepository.class);
    private final PushTokenService pushTokenService = mock(PushTokenService.class);
    private final MemberQuery memberQuery = mock(MemberQuery.class);
    private final PushSender pushSender = mock(PushSender.class);
    private final AlbumPushListener listener =
            new AlbumPushListener(pushTokenRepository, pushTokenService, memberQuery, pushSender);

    @Test
    void 받을_멤버의_기기마다_올린_사람과_앨범을_담아_보낸다() {
        given(pushTokenRepository.findAllByMemberIdIn(List.of(2L, 4L))).willReturn(List.of(
                new PushToken(2L, "phone-a", PushPlatform.ANDROID),
                new PushToken(2L, "tablet-a", PushPlatform.ANDROID),
                new PushToken(4L, "phone-b", PushPlatform.IOS)));
        given(memberQuery.nicknamesOf(any())).willReturn(Map.of(1L, "민호"));
        given(pushSender.send(anyList())).willReturn(List.of());

        listener.onAlbumUpdated(EVENT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushMessage>> sent = ArgumentCaptor.forClass(List.class);
        verify(pushSender).send(sent.capture());
        assertThat(sent.getValue()).extracting(PushMessage::token).containsExactly("phone-a", "tablet-a", "phone-b");
        assertThat(sent.getValue().getFirst().title()).isEqualTo("제주 가족");
        assertThat(sent.getValue().getFirst().body()).isEqualTo("민호님이 '도쿄 3월'에 사진을 올렸어요");
        assertThat(sent.getValue().getFirst().data())
                .containsExactlyInAnyOrderEntriesOf(Map.of("type", "ALBUM_UPDATED", "groupId", "3", "albumId", "7"));
    }

    @Test
    void 유효하지_않다고_답한_토큰은_지운다() {
        given(pushTokenRepository.findAllByMemberIdIn(any())).willReturn(
                List.of(new PushToken(2L, "gone", PushPlatform.ANDROID)));
        given(memberQuery.nicknamesOf(any())).willReturn(Map.of());
        given(pushSender.send(anyList())).willReturn(List.of("gone"));

        listener.onAlbumUpdated(EVENT);

        verify(pushTokenService).removeInvalid(List.of("gone"));
    }

    @Test
    void 받을_멤버나_기기가_없으면_보내지_않는다() {
        listener.onAlbumUpdated(new AlbumUpdated(3L, "제주 가족", 7L, "도쿄 3월", 1L, List.of()));
        given(pushTokenRepository.findAllByMemberIdIn(any())).willReturn(List.of());
        listener.onAlbumUpdated(EVENT);

        verify(pushSender, never()).send(anyList());
    }

    @Test
    void 보내다가_실패해도_예외를_올리지_않는다() {
        given(pushTokenRepository.findAllByMemberIdIn(any())).willThrow(new IllegalStateException("db down"));

        assertThatCode(() -> listener.onAlbumUpdated(EVENT)).doesNotThrowAnyException();
    }
}
