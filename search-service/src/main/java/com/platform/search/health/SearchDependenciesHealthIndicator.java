package com.platform.search.health;

import com.platform.web.support.health.AbstractTcpDependenciesHealthIndicator;
import com.platform.web.support.health.TcpConnectivityChecker;
import com.platform.web.support.health.TcpDependency;
import com.platform.web.support.health.TcpDependencyParser;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("searchDependencies")
public class SearchDependenciesHealthIndicator extends AbstractTcpDependenciesHealthIndicator {

    private final Environment environment;

    public SearchDependenciesHealthIndicator(Environment environment, TcpConnectivityChecker tcpConnectivityChecker) {
        super(tcpConnectivityChecker);
        this.environment = environment;
    }

    @Override
    protected List<TcpDependency> getDependencies() {
        return List.of(
                TcpDependencyParser.parseJdbcMysql("mysql", environment.getProperty("spring.datasource.url"), 3306),
                TcpDependencyParser.parseHostAndPort(
                        "rabbitmq",
                        environment.getProperty("spring.rabbitmq.host"),
                        environment.getProperty("spring.rabbitmq.port", Integer.class),
                        5672
                )
        );
    }
}
