package com.mapmory.backend.auth.token.jwt;

import com.mapmory.backend.common.time.TimeConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

// JwtProvider가 Clock을 쓴다. @WebMvcTest 슬라이스는 이 설정을 import해 JWT를 쓰므로 Clock도 함께 가져온다.
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@Import(TimeConfiguration.class)
public class JwtConfig {
}
