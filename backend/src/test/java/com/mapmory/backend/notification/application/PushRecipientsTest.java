package com.mapmory.backend.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mapmory.backend.member.api.MemberQuery;
import com.mapmory.backend.notification.domain.NotificationSettingRepository;
import com.mapmory.backend.notification.domain.PushPlatform;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PushRecipientsTest {

    private final PushTokenRepository pushTokenRepository = mock(PushTokenRepository.class);
    private final NotificationSettingRepository notificationSettingRepository =
            mock(NotificationSettingRepository.class);
    private final MemberQuery memberQuery = mock(MemberQuery.class);
    private final PushRecipients recipients =
            new PushRecipients(pushTokenRepository, notificationSettingRepository, memberQuery);

    @Test
    void 앨범_알림은_활동_중이고_알림을_켠_멤버의_기기만_고른다() {
        PushToken token = new PushToken(4L, "phone-b", PushPlatform.IOS);
        given(memberQuery.inactiveAmong(List.of(2L, 3L, 4L))).willReturn(Set.of(2L));
        given(notificationSettingRepository.findGroupAlbumOffAmong(List.of(3L, 4L))).willReturn(Set.of(3L));
        given(pushTokenRepository.findAllByMemberIdIn(List.of(4L))).willReturn(List.of(token));

        assertThat(recipients.albumDevicesOf(List.of(2L, 3L, 4L))).containsExactly(token);
    }

    @Test
    void 앨범_알림을_받을_멤버가_모두_활동_중이_아니면_기기를_찾지_않는다() {
        given(memberQuery.inactiveAmong(List.of(2L))).willReturn(Set.of(2L));

        assertThat(recipients.albumDevicesOf(List.of(2L))).isEmpty();

        verify(notificationSettingRepository, never()).findGroupAlbumOffAmong(any());
        verify(pushTokenRepository, never()).findAllByMemberIdIn(any());
    }

    @Test
    void 지정한_기기는_활동_중인_회원의_등록이면_고른다() {
        PushToken token = new PushToken(2L, "device-token", PushPlatform.ANDROID);
        given(memberQuery.inactiveAmong(List.of(2L))).willReturn(Set.of());
        given(pushTokenRepository.findByIdAndMemberId(3L, 2L)).willReturn(Optional.of(token));

        assertThat(recipients.registeredDevice(2L, 3L)).contains(token);
    }

    @Test
    void 지정한_기기가_없거나_다른_회원의_등록이면_고르지_않는다() {
        given(memberQuery.inactiveAmong(List.of(2L))).willReturn(Set.of());
        given(pushTokenRepository.findByIdAndMemberId(3L, 2L)).willReturn(Optional.empty());

        assertThat(recipients.registeredDevice(2L, 3L)).isEmpty();
    }

    @Test
    void 활동_중이_아닌_회원은_기기_등록을_조회하지_않는다() {
        given(memberQuery.inactiveAmong(List.of(2L))).willReturn(Set.of(2L));

        assertThat(recipients.registeredDevice(2L, 3L)).isEmpty();

        verifyNoInteractions(pushTokenRepository);
    }
}
