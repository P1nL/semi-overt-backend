package com.platform.auth.api.resp;

/**
 * Internal result used by the HTTP controller to write the refresh cookie while
 * keeping the JSON response flat as {@link AuthResp}.
 */
public record AuthSessionResult(
        AuthResp response,
        String refreshToken,
        long refreshCookieMaxAgeSeconds,
        boolean persistent
) {
    @Override public String toString() { return "AuthSessionResult[redacted]"; }
}
