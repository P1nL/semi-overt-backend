package com.platform.gateway.health;

import com.platform.web.support.health.TcpConnectivityChecker;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NacosTcpHealthIndicatorTest {

    @Test
    void healthShouldBeUpWhenNacosIsReachable() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.cloud.nacos.config.server-addr", "nacos.example.com:8848");
        TcpConnectivityChecker checker = mock(TcpConnectivityChecker.class);
        when(checker.canConnect("nacos.example.com", 8848, 2000)).thenReturn(true);

        NacosTcpHealthIndicator indicator = new NacosTcpHealthIndicator(environment, checker);

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
    }

    @Test
    void healthShouldBeDownWhenNacosIsUnreachable() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.cloud.nacos.discovery.server-addr", "nacos.example.com:8848");
        TcpConnectivityChecker checker = mock(TcpConnectivityChecker.class);
        when(checker.canConnect("nacos.example.com", 8848, 2000)).thenReturn(false);

        NacosTcpHealthIndicator indicator = new NacosTcpHealthIndicator(environment, checker);

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
        assertThat(indicator.health().getDetails()).containsEntry("unreachable", java.util.List.of("nacos"));
    }
}
