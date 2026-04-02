package com.platform.web.support.health;

import java.util.ArrayList;
import java.util.List;

public final class TcpDependencyParser {

    private static final String MYSQL_JDBC_PREFIX = "jdbc:mysql://";

    private TcpDependencyParser() {
    }

    public static TcpDependency parseHostAndPort(String dependencyName, String host, Integer port, int defaultPort) {
        if (host == null || host.isBlank()) {
            throw new IllegalStateException(dependencyName + " host is not configured");
        }
        int resolvedPort = port == null ? defaultPort : port;
        return new TcpDependency(dependencyName, host.trim(), resolvedPort);
    }

    public static TcpDependency parseAddress(String dependencyName, String address, int defaultPort) {
        List<TcpDependency> dependencies = parseAddressList(dependencyName, address, defaultPort);
        return dependencies.get(0);
    }

    public static List<TcpDependency> parseAddressList(String dependencyName, String addresses, int defaultPort) {
        if (addresses == null || addresses.isBlank()) {
            throw new IllegalStateException(dependencyName + " address is not configured");
        }

        List<TcpDependency> dependencies = new ArrayList<>();
        String[] segments = addresses.split(",");
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i].trim();
            if (segment.isEmpty()) {
                continue;
            }

            String normalized = normalizeAddress(segment);
            String name = segments.length == 1 ? dependencyName : dependencyName + "-" + (i + 1);
            dependencies.add(parseNormalizedAddress(name, normalized, defaultPort));
        }

        if (dependencies.isEmpty()) {
            throw new IllegalStateException(dependencyName + " address is not configured");
        }
        return dependencies;
    }

    public static TcpDependency parseJdbcMysql(String dependencyName, String jdbcUrl, int defaultPort) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalStateException(dependencyName + " jdbc url is not configured");
        }
        String normalized = jdbcUrl.trim();
        if (!normalized.startsWith(MYSQL_JDBC_PREFIX)) {
            throw new IllegalStateException("Unsupported MySQL JDBC url: " + jdbcUrl);
        }

        int start = MYSQL_JDBC_PREFIX.length();
        int end = normalized.length();
        int slashIndex = normalized.indexOf('/', start);
        int queryIndex = normalized.indexOf('?', start);
        if (slashIndex >= 0) {
            end = slashIndex;
        }
        if (queryIndex >= 0 && queryIndex < end) {
            end = queryIndex;
        }

        String authority = normalized.substring(start, end);
        int credentialSeparator = authority.lastIndexOf('@');
        if (credentialSeparator >= 0) {
            authority = authority.substring(credentialSeparator + 1);
        }
        return parseAddress(dependencyName, authority, defaultPort);
    }

    private static String normalizeAddress(String value) {
        String normalized = value;
        int schemeIndex = normalized.indexOf("://");
        if (schemeIndex >= 0) {
            normalized = normalized.substring(schemeIndex + 3);
        }
        int pathIndex = normalized.indexOf('/');
        if (pathIndex >= 0) {
            normalized = normalized.substring(0, pathIndex);
        }
        return normalized.trim();
    }

    private static TcpDependency parseNormalizedAddress(String dependencyName, String normalizedAddress, int defaultPort) {
        if (normalizedAddress.startsWith("[")) {
            int closingBracket = normalizedAddress.indexOf(']');
            if (closingBracket < 0) {
                throw new IllegalStateException("Invalid IPv6 address for " + dependencyName + ": " + normalizedAddress);
            }
            String host = normalizedAddress.substring(1, closingBracket);
            int port = defaultPort;
            if (closingBracket + 1 < normalizedAddress.length()) {
                if (normalizedAddress.charAt(closingBracket + 1) != ':') {
                    throw new IllegalStateException("Invalid IPv6 address for " + dependencyName + ": " + normalizedAddress);
                }
                port = Integer.parseInt(normalizedAddress.substring(closingBracket + 2));
            }
            return new TcpDependency(dependencyName, host, port);
        }

        int separatorIndex = normalizedAddress.lastIndexOf(':');
        if (separatorIndex > 0 && separatorIndex < normalizedAddress.length() - 1 && normalizedAddress.indexOf(':') == separatorIndex) {
            String host = normalizedAddress.substring(0, separatorIndex);
            int port = Integer.parseInt(normalizedAddress.substring(separatorIndex + 1));
            return new TcpDependency(dependencyName, host, port);
        }

        return new TcpDependency(dependencyName, normalizedAddress, defaultPort);
    }
}
