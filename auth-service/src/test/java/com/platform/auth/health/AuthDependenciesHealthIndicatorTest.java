package com.platform.auth.health;

import com.platform.web.support.health.TcpConnectivityChecker;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthDependenciesHealthIndicatorTest {

    @Test
    void healthShouldBeUpWhenMysqlAndRedisAreReachable() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://mysql.example.com:3306/content_platform?useSSL=false")
                .withProperty("spring.data.redis.host", "redis.example.com")
                .withProperty("spring.data.redis.port", "6379");
        TcpConnectivityChecker checker = mock(TcpConnectivityChecker.class);
        when(checker.canConnect("mysql.example.com", 3306, 2000)).thenReturn(true);
        when(checker.canConnect("redis.example.com", 6379, 2000)).thenReturn(true);

        AuthDependenciesHealthIndicator indicator = new AuthDependenciesHealthIndicator(environment, checker);

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
    }

    @Test
    void healthShouldBeDownWhenRedisIsUnreachable() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://mysql.example.com:3306/content_platform?useSSL=false")
                .withProperty("spring.data.redis.host", "redis.example.com")
                .withProperty("spring.data.redis.port", "6379");
        TcpConnectivityChecker checker = mock(TcpConnectivityChecker.class);
        when(checker.canConnect("mysql.example.com", 3306, 2000)).thenReturn(true);
        when(checker.canConnect("redis.example.com", 6379, 2000)).thenReturn(false);

        AuthDependenciesHealthIndicator indicator = new AuthDependenciesHealthIndicator(environment, checker);

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
        assertThat(indicator.health().getDetails()).containsEntry("unreachable", java.util.List.of("redis"));
    }
}
