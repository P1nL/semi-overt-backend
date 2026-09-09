package com.platform.gateway.session;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Resolves a client address without allowing an untrusted request to choose its
 * own rate-limit identity. Forwarded headers are only considered when the
 * socket peer is inside the explicitly configured proxy networks.
 */
@Component
public class ClientIpResolver {

    private static final int MAX_FORWARDED_FOR_LENGTH = 4096;
    private static final int MAX_FORWARDED_HOPS = 32;

    private final List<Cidr> trustedProxies;

    @Autowired
    public ClientIpResolver(@Value("${platform.trusted-proxies:}") String trustedProxySpec) {
        this(parseTrustedProxies(trustedProxySpec));
    }

    ClientIpResolver(List<Cidr> trustedProxies) {
        this.trustedProxies = List.copyOf(trustedProxies == null ? List.of() : trustedProxies);
    }

    /**
     * Returns a canonical address, falling back to the servlet socket peer for
     * malformed, oversized, or untrusted forwarding headers. X-Real-IP is
     * intentionally ignored; deployments must explicitly configure trusted
     * proxies before any forwarding header can affect identity.
     */
    public String resolve(ServerHttpRequest request) {
        if (request == null) {
            return "unknown";
        }

        String remoteAddress = canonicalAddress(request.getRemoteAddress() == null ? null : request.getRemoteAddress().getAddress().getHostAddress());
        if (remoteAddress == null) {
            remoteAddress = "unknown";
        }

        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = singleForwardedForValue(request);
        if (forwardedFor == null || forwardedFor.isBlank()
                || forwardedFor.length() > MAX_FORWARDED_FOR_LENGTH) {
            return remoteAddress;
        }

        String[] hops = forwardedFor.split(",", -1);
        if (hops.length == 0 || hops.length > MAX_FORWARDED_HOPS) {
            return remoteAddress;
        }

        String current = remoteAddress;
        for (int index = hops.length - 1; index >= 0; index--) {
            String hop = canonicalAddress(hops[index]);
            if (hop == null) {
                return remoteAddress;
            }
            if (!isTrustedProxy(current)) {
                return current;
            }
            current = hop;
        }

        // If every advertised hop is trusted, there is no defensible client
        // boundary. Keep the socket peer rather than accepting a caller-made
        // chain of trusted-looking addresses.
        return isTrustedProxy(current) ? remoteAddress : current;
    }

    boolean isTrustedProxy(String address) {
        InetAddress parsed = parseIpLiteral(address);
        if (parsed == null) {
            return false;
        }
        return trustedProxies.stream().anyMatch(cidr -> cidr.contains(parsed));
    }

    private String singleForwardedForValue(ServerHttpRequest request) {
        java.util.List<String> values=request.getHeaders().get("X-Forwarded-For");
        return values!=null && values.size()==1?values.get(0):null;
    }
    private String canonicalAddress(String value) {
        InetAddress parsed = parseIpLiteral(value);
        return parsed == null ? null : parsed.getHostAddress();
    }

    private InetAddress parseIpLiteral(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.length() >= 2 && candidate.charAt(0) == '['
                && candidate.charAt(candidate.length() - 1) == ']') {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        if (candidate.isBlank() || candidate.indexOf('/') >= 0 || candidate.indexOf('%') >= 0
                || !looksLikeIpLiteral(candidate)) {
            return null;
        }
        try {
            return InetAddress.getByName(candidate);
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private boolean looksLikeIpLiteral(String value) {
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (!(ch == '.' || ch == ':' || (ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    private static List<Cidr> parseTrustedProxies(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<Cidr> result = new ArrayList<>();
        for (String token : value.split(",", -1)) {
            Cidr.parse(token).ifPresent(result::add);
        }
        return result;
    }

    static final class Cidr {
        private final byte[] network;
        private final int prefixBits;

        private Cidr(byte[] network, int prefixBits) {
            this.network = network;
            this.prefixBits = prefixBits;
        }

        static java.util.Optional<Cidr> parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return java.util.Optional.empty();
            }
            String value = raw.trim();
            int slash = value.indexOf('/');
            String addressPart = slash < 0 ? value : value.substring(0, slash).trim();
            String prefixPart = slash < 0 ? null : value.substring(slash + 1).trim();
            if (addressPart.isBlank() || (prefixPart != null && prefixPart.isBlank())
                    || !looksLikeLiteral(addressPart)) {
                return java.util.Optional.empty();
            }
            try {
                InetAddress address = InetAddress.getByName(addressPart);
                int maxBits = address.getAddress().length * 8;
                int prefixBits = prefixPart == null ? maxBits : Integer.parseInt(prefixPart);
                if (prefixBits < 0 || prefixBits > maxBits) {
                    return java.util.Optional.empty();
                }
                byte[] network = address.getAddress().clone();
                int fullBytes = prefixBits / 8;
                int remainingBits = prefixBits % 8;
                if (fullBytes < network.length) {
                    if (remainingBits != 0) {
                        network[fullBytes] = (byte) (network[fullBytes]
                                & (0xFF << (8 - remainingBits)));
                        fullBytes++;
                    }
                    for (int index = fullBytes; index < network.length; index++) {
                        network[index] = 0;
                    }
                }
                return java.util.Optional.of(new Cidr(network, prefixBits));
            } catch (NumberFormatException | UnknownHostException ex) {
                return java.util.Optional.empty();
            }
        }

        boolean contains(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixBits / 8;
            int remainingBits = prefixBits % 8;
            for (int index = 0; index < fullBytes; index++) {
                if (candidate[index] != network[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }

        private static boolean looksLikeLiteral(String value) {
            if (value.length() >= 2 && value.charAt(0) == '['
                    && value.charAt(value.length() - 1) == ']') {
                value = value.substring(1, value.length() - 1);
            }
            if (value.isBlank() || value.indexOf('%') >= 0) {
                return false;
            }
            for (int index = 0; index < value.length(); index++) {
                char ch = value.charAt(index);
                if (!(ch == '.' || ch == ':' || (ch >= '0' && ch <= '9')
                        || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F'))) {
                    return false;
                }
            }
            return true;
        }
    }
}
