package com.platform.web.support.health;

public record TcpDependency(String name, String host, int port) {

    public TcpDependency {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Dependency name must not be blank");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Dependency host must not be blank");
        }
        if (port <= 0) {
            throw new IllegalArgumentException("Dependency port must be positive");
        }
    }
}
