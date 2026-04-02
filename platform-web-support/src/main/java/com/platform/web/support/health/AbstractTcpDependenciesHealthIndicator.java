package com.platform.web.support.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractTcpDependenciesHealthIndicator implements HealthIndicator {

    private final TcpConnectivityChecker tcpConnectivityChecker;

    protected AbstractTcpDependenciesHealthIndicator(TcpConnectivityChecker tcpConnectivityChecker) {
        this.tcpConnectivityChecker = tcpConnectivityChecker;
    }

    @Override
    public Health health() {
        List<TcpDependency> dependencies;
        try {
            dependencies = getDependencies();
        } catch (RuntimeException ex) {
            return Health.down(ex)
                    .withDetail("message", ex.getMessage())
                    .build();
        }
        if (dependencies.isEmpty()) {
            return Health.down().withDetail("reason", "No readiness dependencies configured").build();
        }

        Map<String, String> dependencyMap = new LinkedHashMap<>();
        List<String> unreachable = new ArrayList<>();
        for (TcpDependency dependency : dependencies) {
            dependencyMap.put(dependency.name(), dependency.host() + ":" + dependency.port());
            boolean reachable = tcpConnectivityChecker.canConnect(
                    dependency.host(),
                    dependency.port(),
                    timeoutMillis()
            );
            if (!reachable) {
                unreachable.add(dependency.name());
            }
        }

        if (!unreachable.isEmpty()) {
            return Health.down()
                    .withDetail("dependencies", dependencyMap)
                    .withDetail("unreachable", unreachable)
                    .build();
        }

        return Health.up()
                .withDetail("dependencies", dependencyMap)
                .build();
    }

    protected int timeoutMillis() {
        return 2000;
    }

    protected abstract List<TcpDependency> getDependencies();
}
