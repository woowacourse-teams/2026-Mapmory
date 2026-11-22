package com.mapmory.backend.common.monitoring.db;

import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 요청별 SQL 실행 수·DB 시간 계측(ADR 0032).
 *
 * <p>DataSource 빈을 datasource-proxy로 감싼다. JPA·Flyway·Bucket4j 등 DataSource를 쓰는 곳이 모두 같은 빈을 받으므로
 * Hibernate 밖의 JDBC 실행도 센다. Hikari 지표와 헬스 체크는 JDBC {@code unwrap}으로 안쪽 HikariDataSource를 찾으므로 그대로다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DbMonitoringProperties.class)
public class DbMonitoringConfiguration {

    @Bean
    RequestDbUsageListener requestDbUsageListener(DbMonitoringProperties properties) {
        return new RequestDbUsageListener(properties.slowQuery(), System::nanoTime);
    }

    @Bean
    SlowRequestObservationHandler slowRequestObservationHandler(DbMonitoringProperties properties) {
        return new SlowRequestObservationHandler(properties.slowRequest(), System::nanoTime);
    }

    // BeanPostProcessor는 다른 빈보다 먼저 만들어지므로 static으로 두고, 리스너는 DataSource를 감쌀 때 꺼낸다.
    @Bean
    static BeanPostProcessor requestDbUsageDataSourceProxy(ObjectProvider<RequestDbUsageListener> listener) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof DataSource dataSource) || bean instanceof ProxyDataSource) {
                    return bean;
                }
                return ProxyDataSourceBuilder.create(dataSource)
                        .name(beanName)
                        .listener(listener.getObject())
                        .build();
            }
        };
    }
}
