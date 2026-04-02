package com.platform.gateway.health;

import com.platform.web.support.health.AbstractTcpDependenciesHealthIndicator;
import com.platform.web.support.health.TcpDependency;
import com.platform.web.support.health.TcpConnectivityChecker;
import com.platform.web.support.health.TcpDependencyParser;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("nacosTcp")
public class NacosTcpHealthIndicator extends AbstractTcpDependenciesHealthIndicator {

    private static final int DEFAULT_NACOS_PORT = 8848;

    private final Environment environment;

    public NacosTcpHealthIndicator(Environment environment, TcpConnectivityChecker tcpConnectivityChecker) {
        super(tcpConnectivityChecker);
        this.environment = environment;
    }

    @Override
    protected List<TcpDependency> getDependencies() {
        String serverAddr = environment.getProperty("spring.cloud.nacos.config.server-addr");
        if (serverAddr == null || serverAddr.isBlank()) {
            serverAddr = environment.getProperty("spring.cloud.nacos.discovery.server-addr");
        }
        return TcpDependencyParser.parseAddressList("nacos", serverAddr, DEFAULT_NACOS_PORT);
    }
}
