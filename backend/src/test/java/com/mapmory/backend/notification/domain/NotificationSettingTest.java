package com.mapmory.backend.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationSettingTest {

    @Test
    void 한_번도_바꾸지_않은_회원은_그룹_앨범_알림을_받는다() {
        NotificationSetting setting = NotificationSetting.defaultsFor(1L);

        assertThat(setting.getMemberId()).isEqualTo(1L);
        assertThat(setting.isGroupAlbum()).isTrue();
    }
}
