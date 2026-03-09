package com.video.analysis.auth;

import com.video.analysis.dto.auth.AuthResponse;
import com.video.analysis.dto.auth.LoginRequest;
import com.video.analysis.dto.auth.RegisterRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthServiceImpl implements AuthService {

    private static final int SESSION_DAYS = 30;

    private final JdbcTemplate jdbcTemplate;

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
    public AuthResponse login(LoginRequest request) {
        String username = normalizeUsername(request.getUsername());
        String password = request.getPassword() == null ? "" : request.getPassword().trim();

        AppUserRow user = findUserByUsername(username);
        if (user == null || !PasswordUtil.verify(password, user.passwordSalt(), user.passwordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "用户名或密码错误。");
        }

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

    private SessionInfo createSession(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(SESSION_DAYS);
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
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
}
