package com.video.analysis.auth;

public final class AuthContext {

    private static final ThreadLocal<AuthPrincipal> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthPrincipal principal) {
        HOLDER.set(principal);
    }

    public static AuthPrincipal get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        AuthPrincipal principal = HOLDER.get();
        return principal == null ? null : principal.userId();
    }

    public static long requireUserId() {
        Long userId = getUserId();
        if (userId == null || userId <= 0) {
            throw new IllegalStateException("No authenticated user in request context.");
        }
        return userId;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
