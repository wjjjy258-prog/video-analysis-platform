package com.video.analysis.config;

import com.video.analysis.auth.PasswordUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Component
public class SchemaUpgradeRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public SchemaUpgradeRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        // Keep startup migration deterministic: create -> add columns -> backfill -> indexes.
        ensureAuthTables();
        long seedUserId = ensureSeedUser();

        ensureImportJobTable(seedUserId);
        ensureImportRejectTable(seedUserId);
        ensureTenantColumns(seedUserId);
        ensureVideoColumns(seedUserId);

        backfillVideoData(seedUserId);
        backfillTenantData(seedUserId);
        remapOrphanTenants(seedUserId);
        purgeLegacySeedData(seedUserId);
        ensureVideoIndexes();
    }

    private void ensureAuthTables() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS app_user (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                        "username VARCHAR(64) NOT NULL," +
                        "password_hash VARCHAR(128) NOT NULL," +
                        "password_salt VARCHAR(64) NOT NULL," +
                        "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                        "UNIQUE KEY uk_app_user_username (username)" +
                        ")"
        );

        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS app_session (" +
                        "token VARCHAR(96) PRIMARY KEY," +
                        "user_id BIGINT NOT NULL," +
                        "created_at DATETIME NOT NULL," +
                        "last_active_at DATETIME NOT NULL," +
                        "expires_at DATETIME NOT NULL," +
                        "is_revoked TINYINT(1) NOT NULL DEFAULT 0," +
                        "revoked_at DATETIME NULL," +
                        "INDEX idx_app_session_user (user_id)," +
                        "INDEX idx_app_session_expire (expires_at)" +
                        ")"
        );
    }

    private long ensureSeedUser() {
        Long demoId = queryLong("SELECT id FROM app_user WHERE username='demo' LIMIT 1");
        if (demoId != null && demoId > 0) {
            return demoId;
        }

        LocalDateTime now = LocalDateTime.now();
        String salt = PasswordUtil.randomSaltHex();
        String hash = PasswordUtil.hashPassword("123456", salt);

        jdbcTemplate.update(
                "INSERT INTO app_user (username, password_hash, password_salt, created_at, updated_at) VALUES (?,?,?,?,?)",
                "demo",
                hash,
                salt,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );

        Long id = queryLong("SELECT id FROM app_user WHERE username='demo' LIMIT 1");
        return id == null || id <= 0 ? 1L : id;
    }

    private void ensureTenantColumns(long seedUserId) {
        // Tenant isolation is mandatory across all business tables.
        addColumnIfMissing("video", "tenant_user_id", "BIGINT NOT NULL DEFAULT " + seedUserId + " AFTER id");
        addColumnIfMissing("user", "tenant_user_id", "BIGINT NOT NULL DEFAULT " + seedUserId + " AFTER user_id");
        addColumnIfMissing("comment", "tenant_user_id", "BIGINT NOT NULL DEFAULT " + seedUserId + " AFTER comment_id");
        addColumnIfMissing("user_behavior", "tenant_user_id", "BIGINT NOT NULL DEFAULT " + seedUserId + " AFTER id");
        addColumnIfMissing("video_statistics", "tenant_user_id", "BIGINT NOT NULL DEFAULT " + seedUserId + " AFTER id");
        addColumnIfMissing("user_interest_result", "tenant_user_id", "BIGINT NOT NULL DEFAULT " + seedUserId + " AFTER id");
        addColumnIfMissing("import_job", "tenant_user_id", "BIGINT NOT NULL DEFAULT " + seedUserId + " AFTER id");
        addColumnIfMissing("import_reject_record", "tenant_user_id", "BIGINT NOT NULL DEFAULT " + seedUserId + " AFTER id");
    }

    private void ensureVideoColumns(long seedUserId) {
        // Standardized ingestion fields. All are additive for backward compatibility.
        addColumnIfMissing("video", "source_platform", "VARCHAR(32) NOT NULL DEFAULT 'unknown' AFTER author");
        addColumnIfMissing("video", "source_url", "VARCHAR(1000) NULL AFTER source_platform");
        addColumnIfMissing("video", "platform_video_id", "VARCHAR(128) NULL AFTER dedupe_key");
        addColumnIfMissing("video", "import_type", "VARCHAR(32) NOT NULL DEFAULT 'import' AFTER publish_time");
        addColumnIfMissing("video", "source_file", "VARCHAR(260) NULL AFTER import_type");
        addColumnIfMissing("video", "import_time", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER source_file");
        addColumnIfMissing("video", "data_quality_score", "DECIMAL(5,2) NOT NULL DEFAULT 0.00 AFTER import_time");
        addColumnIfMissing("video", "quality_level", "VARCHAR(16) NOT NULL DEFAULT 'GOOD' AFTER data_quality_score");
        addColumnIfMissing("video", "share_count", "BIGINT NOT NULL DEFAULT 0 AFTER comment_count");
        addColumnIfMissing("video", "favorite_count", "BIGINT NOT NULL DEFAULT 0 AFTER share_count");
        addColumnIfMissing("video", "duration_sec", "BIGINT NOT NULL DEFAULT 0 AFTER favorite_count");
        addColumnIfMissing("video", "ai_confidence", "DECIMAL(5,4) NOT NULL DEFAULT 0.0000 AFTER data_quality_score");
        addColumnIfMissing("video", "tags_json", "TEXT NULL AFTER ai_confidence");
        addColumnIfMissing("video", "extra_json", "TEXT NULL AFTER tags_json");
        addColumnIfMissing("video", "import_job_id", "BIGINT NULL AFTER data_quality_score");
        addColumnIfMissing("video", "dedupe_key", "VARCHAR(128) NULL AFTER tenant_user_id");
        addColumnIfMissing("video", "tenant_user_id", "BIGINT NOT NULL DEFAULT " + seedUserId + " AFTER id");
    }

    private void ensureImportJobTable(long seedUserId) {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS import_job (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                        "tenant_user_id BIGINT NOT NULL DEFAULT " + seedUserId + "," +
                        "import_type VARCHAR(32) NOT NULL," +
                        "source_platform VARCHAR(32) DEFAULT NULL," +
                        "source_file VARCHAR(260) DEFAULT NULL," +
                        "source_count INT NOT NULL DEFAULT 0," +
                        "success_count INT NOT NULL DEFAULT 0," +
                        "started_at DATETIME NOT NULL," +
                        "finished_at DATETIME DEFAULT NULL," +
                        "status VARCHAR(16) NOT NULL DEFAULT 'RUNNING'," +
                        "notes VARCHAR(500) DEFAULT NULL," +
                        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "INDEX idx_import_job_started_at (started_at)," +
                        "INDEX idx_import_job_tenant (tenant_user_id)" +
                        ")"
        );
    }

    private void ensureImportRejectTable(long seedUserId) {
        // Reject table stores rows filtered by quality gate with actionable feedback.
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS import_reject_record (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                        "tenant_user_id BIGINT NOT NULL DEFAULT " + seedUserId + "," +
                        "import_job_id BIGINT DEFAULT NULL," +
                        "source_platform VARCHAR(32) NOT NULL DEFAULT 'unknown'," +
                        "source_file VARCHAR(260) DEFAULT NULL," +
                        "reject_reason VARCHAR(300) NOT NULL," +
                        "suggest_fix VARCHAR(300) DEFAULT NULL," +
                        "raw_excerpt TEXT," +
                        "quality_score DECIMAL(5,2) NOT NULL DEFAULT 0.00," +
                        "ai_used TINYINT(1) NOT NULL DEFAULT 0," +
                        "ai_confidence DECIMAL(5,4) NOT NULL DEFAULT 0.0000," +
                        "import_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "INDEX idx_reject_tenant_time (tenant_user_id, import_time)," +
                        "INDEX idx_reject_tenant_job (tenant_user_id, import_job_id)" +
                        ")"
        );
        addColumnIfMissing("import_reject_record", "tenant_user_id", "BIGINT NOT NULL DEFAULT " + seedUserId + " AFTER id");
        addColumnIfMissing("import_reject_record", "import_job_id", "BIGINT NULL AFTER tenant_user_id");
        addColumnIfMissing("import_reject_record", "source_platform", "VARCHAR(32) NOT NULL DEFAULT 'unknown' AFTER import_job_id");
        addColumnIfMissing("import_reject_record", "source_file", "VARCHAR(260) NULL AFTER source_platform");
        addColumnIfMissing("import_reject_record", "reject_reason", "VARCHAR(300) NOT NULL DEFAULT '质量分过低' AFTER source_file");
        addColumnIfMissing("import_reject_record", "suggest_fix", "VARCHAR(300) NULL AFTER reject_reason");
        addColumnIfMissing("import_reject_record", "raw_excerpt", "TEXT NULL AFTER suggest_fix");
        addColumnIfMissing("import_reject_record", "quality_score", "DECIMAL(5,2) NOT NULL DEFAULT 0.00 AFTER raw_excerpt");
        addColumnIfMissing("import_reject_record", "ai_used", "TINYINT(1) NOT NULL DEFAULT 0 AFTER quality_score");
        addColumnIfMissing("import_reject_record", "ai_confidence", "DECIMAL(5,4) NOT NULL DEFAULT 0.0000 AFTER ai_used");
        addColumnIfMissing("import_reject_record", "import_time", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER ai_confidence");
    }

    private void backfillVideoData(long seedUserId) {
        // Backfill protects existing databases when upgrading from older schema versions.
        jdbcTemplate.execute(
                "UPDATE video " +
                        "SET tenant_user_id = " + seedUserId + " " +
                        "WHERE tenant_user_id IS NULL OR tenant_user_id <= 0"
        );

        jdbcTemplate.execute(
                "UPDATE video " +
                        "SET source_platform = 'unknown' " +
                        "WHERE source_platform IS NULL OR TRIM(source_platform) = ''"
        );

        jdbcTemplate.execute(
                "UPDATE video " +
                        "SET import_type = CASE " +
                        "    WHEN COALESCE(source_platform, '') = 'mock' THEN 'mock' " +
                        "    ELSE 'import' " +
                        "END " +
                        "WHERE import_type IS NULL OR TRIM(import_type) = ''"
        );

        jdbcTemplate.execute(
                "UPDATE video " +
                        "SET import_time = COALESCE(import_time, publish_time, NOW())"
        );

        jdbcTemplate.execute(
                "UPDATE video " +
                        "SET data_quality_score = ROUND(" +
                        "    (CASE WHEN title IS NOT NULL AND TRIM(title) <> '' THEN 22 ELSE 0 END) +" +
                        "    (CASE WHEN author IS NOT NULL AND TRIM(author) <> '' THEN 14 ELSE 0 END) +" +
                        "    (CASE WHEN source_platform IS NOT NULL AND TRIM(source_platform) <> '' THEN 10 ELSE 0 END) +" +
                        "    (CASE WHEN play_count > 0 THEN 20 ELSE 0 END) +" +
                        "    (CASE WHEN like_count >= 0 THEN 10 ELSE 0 END) +" +
                        "    (CASE WHEN comment_count >= 0 THEN 10 ELSE 0 END) +" +
                        "    (CASE WHEN publish_time IS NOT NULL THEN 9 ELSE 0 END) +" +
                        "    (CASE WHEN source_url IS NOT NULL AND TRIM(source_url) <> '' THEN 5 ELSE 0 END)" +
                        ", 2) " +
                        "WHERE data_quality_score IS NULL OR data_quality_score <= 0"
        );

        jdbcTemplate.execute(
                "UPDATE video " +
                        "SET quality_level = CASE " +
                        "  WHEN COALESCE(data_quality_score, 0) < 40 THEN 'REJECTED' " +
                        "  WHEN COALESCE(data_quality_score, 0) < 60 THEN 'LOW' " +
                        "  ELSE 'GOOD' END"
        );

        jdbcTemplate.execute(
                "UPDATE video " +
                        "SET share_count = COALESCE(share_count, 0), " +
                        "favorite_count = COALESCE(favorite_count, 0), " +
                        "duration_sec = COALESCE(duration_sec, 0), " +
                        "ai_confidence = COALESCE(ai_confidence, 0)"
        );

        jdbcTemplate.execute(
                "UPDATE video " +
                        "SET dedupe_key = SHA1(CONCAT_WS('|'," +
                        "    LOWER(COALESCE(source_platform, 'unknown'))," +
                        "    LOWER(TRIM(COALESCE(source_url, '')))," +
                        "    LOWER(TRIM(COALESCE(title, '')))," +
                        "    LOWER(TRIM(COALESCE(author, '')))" +
                        ")) " +
                        "WHERE dedupe_key IS NULL OR TRIM(dedupe_key) = ''"
        );

        jdbcTemplate.execute(
                "DELETE v1 FROM video v1 " +
                        "JOIN video v2 ON v1.tenant_user_id = v2.tenant_user_id " +
                        "AND v1.dedupe_key = v2.dedupe_key " +
                        "AND v1.id > v2.id " +
                        "WHERE v1.dedupe_key IS NOT NULL AND TRIM(v1.dedupe_key) <> ''"
        );
    }

    private void backfillTenantData(long seedUserId) {
        updateTenantIfMissing("user", seedUserId);
        updateTenantIfMissing("comment", seedUserId);
        updateTenantIfMissing("user_behavior", seedUserId);
        updateTenantIfMissing("video_statistics", seedUserId);
        updateTenantIfMissing("user_interest_result", seedUserId);
        updateTenantIfMissing("import_job", seedUserId);
        updateTenantIfMissing("import_reject_record", seedUserId);
    }

    private void remapOrphanTenants(long fallbackUserId) {
        remapOrphanTenant("video", fallbackUserId);
        remapOrphanTenant("user", fallbackUserId);
        remapOrphanTenant("comment", fallbackUserId);
        remapOrphanTenant("user_behavior", fallbackUserId);
        remapOrphanTenant("video_statistics", fallbackUserId);
        remapOrphanTenant("user_interest_result", fallbackUserId);
        remapOrphanTenant("import_job", fallbackUserId);
        remapOrphanTenant("import_reject_record", fallbackUserId);
    }

    private void purgeLegacySeedData(long seedUserId) {
        if (!tableExists("video")) {
            return;
        }

        String seedVideoCondition = "v.tenant_user_id = " + seedUserId + " AND COALESCE(v.source_platform, '') = 'seed' AND COALESCE(v.import_type, '') = 'seed'";
        if (tableExists("user_behavior")) {
            jdbcTemplate.execute(
                    "DELETE ub FROM user_behavior ub " +
                            "JOIN video v ON ub.video_id = v.id " +
                            "WHERE ub.tenant_user_id = " + seedUserId + " AND " + seedVideoCondition
            );
        }
        if (tableExists("comment")) {
            jdbcTemplate.execute(
                    "DELETE c FROM comment c " +
                            "JOIN video v ON c.video_id = v.id " +
                            "WHERE c.tenant_user_id = " + seedUserId + " AND " + seedVideoCondition
            );
        }
        if (tableExists("video_statistics")) {
            jdbcTemplate.execute(
                    "DELETE vs FROM video_statistics vs " +
                            "JOIN video v ON vs.video_id = v.id " +
                            "WHERE vs.tenant_user_id = " + seedUserId + " AND " + seedVideoCondition
            );
        }
        if (tableExists("import_job")) {
            jdbcTemplate.execute(
                    "DELETE FROM import_job " +
                            "WHERE tenant_user_id = " + seedUserId + " AND import_type = 'seed'"
            );
        }
        jdbcTemplate.execute(
                "DELETE FROM video " +
                        "WHERE tenant_user_id = " + seedUserId + " " +
                        "AND COALESCE(source_platform, '') = 'seed' " +
                        "AND COALESCE(import_type, '') = 'seed'"
        );
    }

    private void remapOrphanTenant(String table, long fallbackUserId) {
        if (!tableExists(table)) {
            return;
        }
        jdbcTemplate.execute(
                "UPDATE " + table + " " +
                        "SET tenant_user_id = " + fallbackUserId + " " +
                        "WHERE tenant_user_id NOT IN (SELECT id FROM app_user)"
        );
    }

    private void updateTenantIfMissing(String table, long tenantUserId) {
        if (!tableExists(table)) {
            return;
        }
        jdbcTemplate.execute(
                "UPDATE " + table + " " +
                        "SET tenant_user_id = " + tenantUserId + " " +
                        "WHERE tenant_user_id IS NULL OR tenant_user_id <= 0"
        );
    }

    private void ensureVideoIndexes() {
        // Query-path indexes for dashboard reads and import dedupe checks.
        if (indexExists("video", "uk_video_dedupe")) {
            jdbcTemplate.execute("DROP INDEX uk_video_dedupe ON video");
        }
        if (!indexExists("video", "uk_video_tenant_dedupe")) {
            jdbcTemplate.execute("CREATE UNIQUE INDEX uk_video_tenant_dedupe ON video(tenant_user_id, dedupe_key)");
        }
        if (!indexExists("video", "idx_video_import_time")) {
            jdbcTemplate.execute("CREATE INDEX idx_video_import_time ON video(import_time)");
        }
        if (!indexExists("video", "idx_video_tenant_platform")) {
            jdbcTemplate.execute("CREATE INDEX idx_video_tenant_platform ON video(tenant_user_id, source_platform)");
        }
        if (!indexExists("video", "idx_video_tenant_import_time")) {
            jdbcTemplate.execute("CREATE INDEX idx_video_tenant_import_time ON video(tenant_user_id, import_time)");
        }
        if (!indexExists("video", "idx_video_tenant_category")) {
            jdbcTemplate.execute("CREATE INDEX idx_video_tenant_category ON video(tenant_user_id, category)");
        }
        if (!indexExists("video", "idx_video_tenant_play_like")) {
            jdbcTemplate.execute("CREATE INDEX idx_video_tenant_play_like ON video(tenant_user_id, play_count, like_count)");
        }
        if (!indexExists("video", "idx_video_tenant_platform_video_id")) {
            jdbcTemplate.execute("CREATE INDEX idx_video_tenant_platform_video_id ON video(tenant_user_id, source_platform, platform_video_id)");
        }
        if (!indexExists("video", "idx_video_tenant_quality")) {
            jdbcTemplate.execute("CREATE INDEX idx_video_tenant_quality ON video(tenant_user_id, quality_level, data_quality_score)");
        }
        if (!indexExists("user_behavior", "idx_behavior_tenant_time")) {
            jdbcTemplate.execute("CREATE INDEX idx_behavior_tenant_time ON user_behavior(tenant_user_id, time)");
        }
        if (!indexExists("user_behavior", "idx_behavior_tenant_video")) {
            jdbcTemplate.execute("CREATE INDEX idx_behavior_tenant_video ON user_behavior(tenant_user_id, video_id)");
        }
        if (!indexExists("user_behavior", "idx_behavior_tenant_user_action_time")) {
            jdbcTemplate.execute("CREATE INDEX idx_behavior_tenant_user_action_time ON user_behavior(tenant_user_id, user_id, action, time)");
        }
        if (!indexExists("comment", "idx_comment_tenant")) {
            jdbcTemplate.execute("CREATE INDEX idx_comment_tenant ON comment(tenant_user_id)");
        }
        if (!indexExists("comment", "idx_comment_tenant_video")) {
            jdbcTemplate.execute("CREATE INDEX idx_comment_tenant_video ON comment(tenant_user_id, video_id)");
        }
        if (!indexExists("video_statistics", "idx_stat_tenant_date")) {
            jdbcTemplate.execute("CREATE INDEX idx_stat_tenant_date ON video_statistics(tenant_user_id, stat_date)");
        }
        if (!indexExists("import_reject_record", "idx_reject_tenant_time")) {
            jdbcTemplate.execute("CREATE INDEX idx_reject_tenant_time ON import_reject_record(tenant_user_id, import_time)");
        }
        if (!indexExists("import_reject_record", "idx_reject_tenant_job")) {
            jdbcTemplate.execute("CREATE INDEX idx_reject_tenant_job ON import_reject_record(tenant_user_id, import_job_id)");
        }
    }

    private void addColumnIfMissing(String tableName, String columnName, String columnDefinition) {
        if (!tableExists(tableName)) {
            return;
        }
        Integer columnExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
        );
        if (columnExists != null && columnExists == 0) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
        }
    }

    private boolean indexExists(String tableName, String indexName) {
        if (!tableExists(tableName)) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class,
                tableName,
                indexName
        );
        return count != null && count > 0;
    }

    private Long queryLong(String sql) {
        return jdbcTemplate.query(sql, rs -> rs.next() ? rs.getLong(1) : null);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }
}
