package com.mapmory.backend.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 저장하는 시각의 기준은 UTC다. 서버 JVM, DB, {@code @CreationTimestamp}로 남는 created_at이 모두 UTC라 같은 기준을 쓴다.
 * 한국 날짜가 필요한 곳은 {@link ServiceTime}으로 바꿔 쓴다. (#502)
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
