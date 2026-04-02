package com.platform.review.health;

import com.platform.web.support.health.TcpConnectivityChecker;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewDependenciesHealthIndicatorTest {

    @Test
    void healthShouldBeUpWhenMysqlAndRabbitMqAreReachable() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://mysql.example.com:3306/content_platform?useSSL=false")
                .withProperty("spring.rabbitmq.host", "rabbit.example.com")
                .withProperty("spring.rabbitmq.port", "5672");
        TcpConnectivityChecker checker = mock(TcpConnectivityChecker.class);
        when(checker.canConnect("mysql.example.com", 3306, 2000)).thenReturn(true);
        when(checker.canConnect("rabbit.example.com", 5672, 2000)).thenReturn(true);

        ReviewDependenciesHealthIndicator indicator = new ReviewDependenciesHealthIndicator(environment, checker);

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
    }

    @Test
    void healthShouldBeDownWhenMysqlIsUnreachable() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://mysql.example.com:3306/content_platform?useSSL=false")
                .withProperty("spring.rabbitmq.host", "rabbit.example.com")
                .withProperty("spring.rabbitmq.port", "5672");
        TcpConnectivityChecker checker = mock(TcpConnectivityChecker.class);
        when(checker.canConnect("mysql.example.com", 3306, 2000)).thenReturn(false);
        when(checker.canConnect("rabbit.example.com", 5672, 2000)).thenReturn(true);

        ReviewDependenciesHealthIndicator indicator = new ReviewDependenciesHealthIndicator(environment, checker);

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
        assertThat(indicator.health().getDetails()).containsEntry("unreachable", java.util.List.of("mysql"));
    }
}
