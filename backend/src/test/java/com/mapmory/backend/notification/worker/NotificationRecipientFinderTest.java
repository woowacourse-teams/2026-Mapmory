package com.mapmory.backend.notification.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.notification.domain.PushPlatform;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationRecipientFinderTest {

    private final MemberRepository members = mock(MemberRepository.class);
    private final PushTokenRepository tokens = mock(PushTokenRepository.class);
    private final NotificationRecipientFinder finder = new NotificationRecipientFinder(members, tokens);
    private final ClaimedNotification notification =
            new ClaimedNotification(1L, "claim", "VIDEO_COMPLETED", "{}", 2L, 3L);

    @Test
    void 활동_중인_회원의_등록_기기를_반환한다() {
        Member member = Member.of("회원", UUID.randomUUID());
        PushToken token = new PushToken(2L, "device-token", PushPlatform.ANDROID);
        when(members.findById(2L)).thenReturn(Optional.of(member));
        when(tokens.findByIdAndMemberId(3L, 2L)).thenReturn(Optional.of(token));

        assertThat(finder.find(notification)).contains(token);
    }

    @Test
    void 등록이_없거나_다른_회원의_등록이면_대상이_없다() {
        when(members.findById(2L)).thenReturn(Optional.of(Member.of("회원", UUID.randomUUID())));
        when(tokens.findByIdAndMemberId(3L, 2L)).thenReturn(Optional.empty());

        assertThat(finder.find(notification)).isEmpty();
    }

    @Test
    void 탈퇴한_회원은_기기_등록을_조회하지_않는다() {
        Member member = Member.of("회원", UUID.randomUUID());
        member.withdraw(LocalDateTime.of(2026, 10, 30, 0, 0));
        when(members.findById(2L)).thenReturn(Optional.of(member));

        assertThat(finder.find(notification)).isEmpty();
        verifyNoInteractions(tokens);
    }
}
