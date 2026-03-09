package com.video.analysis.auth;

import com.video.analysis.dto.auth.AuthResponse;
import com.video.analysis.dto.auth.LoginRequest;
import com.video.analysis.dto.auth.RegisterRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthServiceImpl implements AuthService {

    private static final int SESSION_DAYS = 30;
    private static final int MAX_FAILED_LOGIN = 6;
    private static final int LOGIN_WINDOW_MINUTES = 10;
    private static final int LOCK_MINUTES = 15;
    private static final int TOKEN_BYTES = 48;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, LoginAttemptState> loginAttemptMap = new ConcurrentHashMap<>();

    public AuthServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.getUsername());
        String password = request.getPassword() == null ? "" : request.getPassword().trim();
        String confirm = request.getConfirmPassword() == null ? "" : request.getConfirmPassword().trim();

        if (!password.equals(confirm)) {
            throw new ResponseStatusException(BAD_REQUEST, "两次输入的密码不一致。");
        }
        validatePasswordStrength(password);

        if (findUserByUsername(username) != null) {
            throw new ResponseStatusException(BAD_REQUEST, "用户名已存在，请更换用户名。");
        }

        String salt = PasswordUtil.randomSaltHex();
        String hash = PasswordUtil.hashPassword(password, salt);
        LocalDateTime now = LocalDateTime.now();

        try {
            jdbcTemplate.update(
                    "INSERT INTO app_user (username, password_hash, password_salt, created_at, updated_at) VALUES (?,?,?,?,?)",
                    username,
                    hash,
                    salt,
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now)
            );
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "用户名已存在，请更换用户名。");
        }

        AppUserRow user = findUserByUsername(username);
        if (user == null) {
            throw new ResponseStatusException(BAD_REQUEST, "注册失败，请重试。");
        }

        SessionInfo session = createSession(user.id());
        return AuthResponse.success(
                "注册成功。",
                session.token(),
                session.expiresAt(),
                new AuthResponse.UserInfo(user.id(), user.username(), user.createdAt())
        );
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, String clientIp) {
        String username = normalizeUsername(request.getUsername());
        String password = request.getPassword() == null ? "" : request.getPassword().trim();
        String normalizedIp = normalizeClientIp(clientIp);
        String userKey = "u:" + username;
        String ipKey = "i:" + normalizedIp;

        ensureNotLocked(userKey);
        ensureNotLocked(ipKey);

        AppUserRow user = findUserByUsername(username);
        if (user == null || !PasswordUtil.verify(password, user.passwordSalt(), user.passwordHash())) {
            recordFailedAttempt(userKey);
            recordFailedAttempt(ipKey);
            throw new ResponseStatusException(UNAUTHORIZED, "用户名或密码错误。");
        }

        clearLoginAttempt(userKey);
        clearLoginAttempt(ipKey);
        upgradePasswordHashIfNeeded(user.id(), password, user.passwordHash());

        SessionInfo session = createSession(user.id());
        return AuthResponse.success(
                "登录成功。",
                session.token(),
                session.expiresAt(),
                new AuthResponse.UserInfo(user.id(), user.username(), user.createdAt())
        );
    }

    @Override
    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE app_session SET is_revoked=1, revoked_at=? WHERE token=?",
                Timestamp.valueOf(LocalDateTime.now()),
                token.trim()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse current(String token) {
        AuthPrincipal principal = resolveToken(token);
        if (principal == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已过期，请重新登录。");
        }
        AppUserRow user = findUserById(principal.userId());
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "用户不存在，请重新登录。");
        }
        return AuthResponse.success(
                "状态有效。",
                principal.token(),
                readSessionExpiresAt(principal.token()),
                new AuthResponse.UserInfo(user.id(), user.username(), user.createdAt())
        );
    }

    @Override
    @Transactional
    public AuthPrincipal resolveToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String pureToken = token.trim();
        if (pureToken.length() > 200) {
            return null;
        }
        List<AuthPrincipal> rows = jdbcTemplate.query(
                "SELECT s.user_id, u.username, s.token " +
                        "FROM app_session s " +
                        "JOIN app_user u ON s.user_id = u.id " +
                        "WHERE s.token=? AND s.is_revoked=0 AND s.expires_at > NOW() " +
                        "LIMIT 1",
                (rs, rowNum) -> new AuthPrincipal(
                        rs.getLong("user_id"),
                        rs.getString("username"),
                        rs.getString("token")
                ),
                pureToken
        );
        if (rows.isEmpty()) {
            return null;
        }
        jdbcTemplate.update(
                "UPDATE app_session SET last_active_at=? WHERE token=?",
                Timestamp.valueOf(LocalDateTime.now()),
                pureToken
        );
        return rows.get(0);
    }

    private String normalizeUsername(String username) {
        String value = username == null ? "" : username.trim();
        if (value.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "用户名不能为空。");
        }
        return value;
    }

    private String normalizeClientIp(String clientIp) {
        String value = clientIp == null ? "" : clientIp.trim();
        if (value.isEmpty()) {
            return "unknown";
        }
        if (value.length() > 128) {
            value = value.substring(0, 128);
        }
        return value;
    }

    private void validatePasswordStrength(String password) {
        String value = password == null ? "" : password.trim();
        if (value.length() < 8) {
            throw new ResponseStatusException(BAD_REQUEST, "密码长度至少 8 位。");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetter(ch)) {
                hasLetter = true;
            } else if (Character.isDigit(ch)) {
                hasDigit = true;
            }
            if (hasLetter && hasDigit) {
                return;
            }
        }
        throw new ResponseStatusException(BAD_REQUEST, "密码需包含字母和数字。");
    }

    private void ensureNotLocked(String key) {
        LoginAttemptState state = loginAttemptMap.get(key);
        if (state == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (state.lockedUntil() != null && state.lockedUntil().isAfter(now)) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试。");
        }
        if (state.windowStartedAt().plusMinutes(LOGIN_WINDOW_MINUTES).isBefore(now)) {
            loginAttemptMap.remove(key, state);
        }
    }

    private void recordFailedAttempt(String key) {
        LocalDateTime now = LocalDateTime.now();
        loginAttemptMap.compute(key, (unused, current) -> {
            if (current == null || current.windowStartedAt().plusMinutes(LOGIN_WINDOW_MINUTES).isBefore(now)) {
                return new LoginAttemptState(1, now, null);
            }
            if (current.lockedUntil() != null && current.lockedUntil().isAfter(now)) {
                return current;
            }
            int failed = current.failedCount() + 1;
            if (failed >= MAX_FAILED_LOGIN) {
                return new LoginAttemptState(0, now, now.plusMinutes(LOCK_MINUTES));
            }
            return new LoginAttemptState(failed, current.windowStartedAt(), null);
        });
    }

    private void clearLoginAttempt(String key) {
        loginAttemptMap.remove(key);
    }

    private void upgradePasswordHashIfNeeded(Long userId, String rawPassword, String currentHash) {
        if (!PasswordUtil.needsRehash(currentHash)) {
            return;
        }
        String salt = PasswordUtil.randomSaltHex();
        String hash = PasswordUtil.hashPassword(rawPassword, salt);
        jdbcTemplate.update(
                "UPDATE app_user SET password_hash=?, password_salt=?, updated_at=? WHERE id=?",
                hash,
                salt,
                Timestamp.valueOf(LocalDateTime.now()),
                userId
        );
    }

    private SessionInfo createSession(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(SESSION_DAYS);
        String token = generateSessionToken();
        jdbcTemplate.update(
                "INSERT INTO app_session (token, user_id, created_at, last_active_at, expires_at, is_revoked) " +
                        "VALUES (?,?,?,?,?,0)",
                token,
                userId,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(expiresAt)
        );
        return new SessionInfo(token, expiresAt);
    }

    private String generateSessionToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    private LocalDateTime readSessionExpiresAt(String token) {
        return jdbcTemplate.query(
                "SELECT expires_at FROM app_session WHERE token=? LIMIT 1",
                rs -> rs.next() ? rs.getTimestamp("expires_at").toLocalDateTime() : null,
                token
        );
    }

    private AppUserRow findUserByUsername(String username) {
        List<AppUserRow> rows = jdbcTemplate.query(
                "SELECT id, username, password_hash, password_salt, created_at FROM app_user WHERE username=? LIMIT 1",
                (rs, rowNum) -> new AppUserRow(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("password_salt"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ),
                username
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private AppUserRow findUserById(Long id) {
        if (id == null || id <= 0) {
            return null;
        }
        List<AppUserRow> rows = jdbcTemplate.query(
                "SELECT id, username, password_hash, password_salt, created_at FROM app_user WHERE id=? LIMIT 1",
                (rs, rowNum) -> new AppUserRow(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("password_salt"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ),
                id
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private record AppUserRow(Long id, String username, String passwordHash, String passwordSalt, LocalDateTime createdAt) {
    }

    private record SessionInfo(String token, LocalDateTime expiresAt) {
    }

    private record LoginAttemptState(int failedCount, LocalDateTime windowStartedAt, LocalDateTime lockedUntil) {
    }
}
