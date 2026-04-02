package com.platform.auth.health;

import com.platform.web.support.health.AbstractTcpDependenciesHealthIndicator;
import com.platform.web.support.health.TcpConnectivityChecker;
import com.platform.web.support.health.TcpDependency;
import com.platform.web.support.health.TcpDependencyParser;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("authDependencies")
public class AuthDependenciesHealthIndicator extends AbstractTcpDependenciesHealthIndicator {

    private final Environment environment;

    public AuthDependenciesHealthIndicator(Environment environment, TcpConnectivityChecker tcpConnectivityChecker) {
        super(tcpConnectivityChecker);
        this.environment = environment;
    }

    @Override
    protected List<TcpDependency> getDependencies() {
        return List.of(
                TcpDependencyParser.parseJdbcMysql("mysql", environment.getProperty("spring.datasource.url"), 3306),
                TcpDependencyParser.parseHostAndPort(
                        "redis",
                        environment.getProperty("spring.data.redis.host"),
                        environment.getProperty("spring.data.redis.port", Integer.class),
                        6379
                )
        );
    }
}
