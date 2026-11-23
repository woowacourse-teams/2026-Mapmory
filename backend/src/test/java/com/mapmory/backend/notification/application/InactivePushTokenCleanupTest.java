package com.mapmory.backend.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mapmory.backend.member.api.MemberQuery;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class InactivePushTokenCleanupTest {

    private final PushTokenRepository pushTokenRepository = mock(PushTokenRepository.class);
    private final PushTokenService pushTokenService = mock(PushTokenService.class);
    private final MemberQuery memberQuery = mock(MemberQuery.class);
    private final InactivePushTokenCleanup cleanup =
            new InactivePushTokenCleanup(pushTokenRepository, pushTokenService, memberQuery);

    @Test
    void 한_차례가_가득_차면_마지막_회원_다음부터_이어서_본다() {
        List<Long> fullBatch = LongStream.rangeClosed(1, InactivePushTokenCleanup.BATCH_SIZE).boxed().toList();
        long last = InactivePushTokenCleanup.BATCH_SIZE;
        given(pushTokenRepository.findMemberIdsAfter(eq(0L), any())).willReturn(fullBatch);
        given(pushTokenRepository.findMemberIdsAfter(eq(last), any())).willReturn(List.of(last + 1));
        given(memberQuery.inactiveAmong(fullBatch)).willReturn(Set.of(1L));
        given(memberQuery.inactiveAmong(List.of(last + 1))).willReturn(Set.of(last + 1));
        given(pushTokenService.removeAllOf(any())).willReturn(2);

        int removed = cleanup.run();

        assertThat(removed).isEqualTo(4);
        verify(pushTokenService).removeAllOf(Set.of(1L));
        verify(pushTokenService).removeAllOf(Set.of(last + 1));
    }

    @Test
    void 활동_중이_아닌_회원이_없으면_지우지_않는다() {
        given(pushTokenRepository.findMemberIdsAfter(anyLong(), any())).willReturn(List.of(1L));
        given(memberQuery.inactiveAmong(List.of(1L))).willReturn(Set.of());

        assertThat(cleanup.run()).isZero();

        verify(pushTokenService, never()).removeAllOf(any());
    }
}
