-- Pulse Observability - MySQL Database Initialization
-- This script creates the necessary database schema for Pulse Server
-- Includes multi-tenant support with tenant_id columns

-- Create database if not exists
CREATE DATABASE IF NOT EXISTS pulse_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE pulse_db;

-- Create tenants table first (referenced by other tables)
CREATE TABLE IF NOT EXISTS tenants (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    gcp_tenant_id VARCHAR(32) NULL,
    domain_name VARCHAR(32) NULL,
    INDEX idx_tenant_active (is_active),
    INDEX idx_gcp_tenant_id (gcp_tenant_id)
);

-- Insert a default tenant for existing data
INSERT INTO tenants (tenant_id, name, description, is_active, gcp_tenant_id, domain_name)
VALUES ('default', 'Default Tenant', 'Default tenant for existing data', TRUE, 'dummy-f3w8r', 'localhost')
ON DUPLICATE KEY UPDATE name = name;

-- ============================================================
-- Users and Projects tables (must be created BEFORE tables that reference them)
-- ============================================================

-- Users table for authentication and user management
CREATE TABLE IF NOT EXISTS users (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id           VARCHAR(255) NOT NULL UNIQUE COMMENT 'Unique user identifier (user-{uuid})',
    email             VARCHAR(255) NOT NULL UNIQUE COMMENT 'User email from Google OAuth',
    name              VARCHAR(255) NOT NULL COMMENT 'User display name',
    status            ENUM('pending', 'active', 'suspended') NOT NULL DEFAULT 'pending' COMMENT 'pending=added by admin, active=logged in',
    firebase_uid      VARCHAR(255) NULL UNIQUE COMMENT 'Firebase user ID for authentication',
    last_login_at     TIMESTAMP NULL COMMENT 'Track user activity',
    is_active         BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'User account status',
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_user_email (email),
    INDEX idx_user_id (user_id),
    INDEX idx_user_active (is_active),
    INDEX idx_user_status (status),
    INDEX idx_user_firebase_uid (firebase_uid)
);

-- Projects table (hierarchy: tenant -> projects)
CREATE TABLE IF NOT EXISTS projects (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id VARCHAR(64) NOT NULL UNIQUE COMMENT 'Project identifier (proj-{uuid})',
    tenant_id VARCHAR(64) NOT NULL COMMENT 'Parent tenant ID',
    name VARCHAR(255) NOT NULL COMMENT 'Project name',
    description TEXT COMMENT 'Project description',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Project status',
    created_by VARCHAR(255) NOT NULL COMMENT 'User who created the project',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_project_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    INDEX idx_project_tenant (tenant_id, is_active)
);

-- ============================================================
-- Tables that reference projects
-- ============================================================

CREATE TABLE interaction (
    interaction_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(25) NOT NULL,
    details JSON,
    is_archived TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    last_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(255),
    INDEX idx_interaction_project (project_id),
    INDEX idx_interaction_project_archived (project_id, is_archived),
    CONSTRAINT fk_interaction_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE
);

-- Symbol files table with project_id in composite primary key
CREATE TABLE symbol_files (
    project_id VARCHAR(64) NOT NULL,
    app_version VARCHAR(64) NOT NULL,
    app_version_code INT NOT NULL,
    platform ENUM('ios','android') NOT NULL,
    framework ENUM('java','js') NOT NULL,
    file_content LONGBLOB NOT NULL,
    bundleid VARCHAR(255),
    PRIMARY KEY (project_id, app_version, app_version_code, platform, framework),
    CONSTRAINT fk_symbol_files_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE
);

CREATE TABLE pulse_sdk_configs (
    version INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    description TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    config_json JSON NOT NULL,
    INDEX idx_sdk_configs_project (project_id),
    INDEX idx_sdk_configs_project_active (project_id, is_active),
    CONSTRAINT fk_sdk_configs_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE
);

-- ============================================================
-- DEFAULT SDK CONFIGURATION TEMPLATE (for reference)
-- This configuration is automatically created for each new project
-- via ProjectService.createProject() in Java code
-- ============================================================
-- {
--   "sampling": {
--     "default": {
--       "sessionSampleRate": 1
--     },
--     "rules": [],
--     "criticalEventPolicies": {
--       "alwaysSend": []
--     },
--     "criticalSessionPolicies": {
--       "alwaysSend": []
--     }
--   },
--   "signals": {
--     "filters": {
--       "mode": "blacklist",
--       "values": []
--     },
--     "scheduleDurationMs": 5000,
--     "logsCollectorUrl": "http://10.0.2.2:4318/v1/logs",
--     "metricCollectorUrl": "http://10.0.2.2:4318/v1/metrics",
--     "spanCollectorUrl": "http://10.0.2.2:4318/v1/traces",
--     "attributesToDrop": [],
--     "attributesToAdd": []
--   },
--   "interaction": {
--     "collectorUrl": "http://10.0.2.2:4318/v1/traces/v1/interactions",
--     "configUrl": "http://10.0.2.2:8080/v1/interaction-configs/",
--     "beforeInitQueueSize": 100
--   },
--   "features": [
--     {
--       "featureName": "interaction",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "java_crash",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "js_crash",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "java_anr",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "network_change",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "network_instrumentation",
--       "sessionSampleRate": 0,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "screen_session",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "custom_events",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "rn_navigation",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "rn_screen_load",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "rn_screen_interactive",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     }
--   ]
-- }

CREATE TABLE severity
(
    severity_id INT PRIMARY KEY AUTO_INCREMENT,
    name        INT NOT NULL,
    description TEXT
);

CREATE TABLE notification_channels
(
    notification_channel_id INT PRIMARY KEY AUTO_INCREMENT,
    project_id VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    type ENUM('slack', 'email') NOT NULL,
    config VARCHAR(500) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    INDEX idx_notification_channels_project (project_id),
    CONSTRAINT fk_notification_channels_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE
);


CREATE TABLE alerts (
    id INT PRIMARY KEY AUTO_INCREMENT,
    project_id VARCHAR(64) NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    scope VARCHAR(100) NOT NULL,
    dimension_filter TEXT,
    condition_expression VARCHAR(255) NOT NULL,
    severity_id INT NOT NULL,
    notification_channel_id INT NOT NULL,
    evaluation_period INT NOT NULL,
    evaluation_interval INT NOT NULL,
    last_snoozed_at TIMESTAMP NULL DEFAULT NULL,
    snoozed_from TIMESTAMP NULL DEFAULT NULL,
    snoozed_until TIMESTAMP NULL DEFAULT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    INDEX idx_alerts_project (project_id),
    INDEX idx_alerts_project_active (project_id, is_active),

    CONSTRAINT fk_alerts_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_severity FOREIGN KEY (severity_id) REFERENCES severity(severity_id),
    CONSTRAINT fk_alert_notification_channel FOREIGN KEY (notification_channel_id) REFERENCES notification_channels(notification_channel_id)
);

CREATE TABLE alert_scope (
    id INT PRIMARY KEY AUTO_INCREMENT,
    alert_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    conditions JSON NULL,
    state VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_subject_alert FOREIGN KEY (alert_id) REFERENCES alerts (id)
);

CREATE TABLE alert_evaluation_history (
    evaluation_id INT PRIMARY KEY AUTO_INCREMENT,
    scope_id INT NOT NULL,
    evaluation_result JSON NOT NULL,
    state VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
    evaluated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_eval_subject FOREIGN KEY (scope_id) REFERENCES alert_scope (id)
);

CREATE TABLE scope_types (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL UNIQUE,
    label VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE alert_metrics (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    label VARCHAR(500) NOT NULL,
    scope VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_metric_scope (name, scope)
);

INSERT INTO severity (name, description)
VALUES
    (1, 'Critical: Production outage or severe degradation with significant user impact. Requires immediate action and incident management.'),
    (2, 'Warning: Degraded performance, elevated errors, or risk of user impact. Should be investigated soon but is not a full outage.'),
    (3, 'Info: Informational or low-risk condition. No immediate action required; useful for visibility, trend analysis, or validation of changes.');

-- Insert Scope Types
INSERT INTO scope_types (name, label) VALUES
    ('interaction', 'Interactions'),
    ('network_api', 'Network APIs'),
    ('screen', 'Screen'),
    ('app_vitals', 'App Vitals');

-- Insert interaction scope metrics
INSERT INTO alert_metrics (name, label, scope) VALUES
    ('APDEX', 'Apdex Score (0 to 1)', 'interaction'),
    ('CRASH', 'Crash Count', 'interaction'),
    ('ANR', 'ANR Count', 'interaction'),
    ('FROZEN_FRAME', 'Frozen Frame Count', 'interaction'),
    ('ANALYSED_FRAME', 'Analysed Frame Count', 'interaction'),
    ('UNANALYSED_FRAME', 'Unanalysed Frame Count', 'interaction'),
    ('DURATION_P99', 'Duration P99 (ms)', 'interaction'),
    ('DURATION_P95', 'Duration P95 (ms)', 'interaction'),
    ('DURATION_P50', 'Duration P50 (ms)', 'interaction'),
    ('ERROR_RATE', 'Error Rate (%)', 'interaction'),
    ('INTERACTION_SUCCESS_COUNT', 'Success Count', 'interaction'),
    ('INTERACTION_ERROR_COUNT', 'Error Count', 'interaction'),
    ('INTERACTION_ERROR_DISTINCT_USERS', 'Distinct Users with Errors', 'interaction'),
    ('USER_CATEGORY_EXCELLENT', 'Excellent Users Count', 'interaction'),
    ('USER_CATEGORY_GOOD', 'Good Users Count', 'interaction'),
    ('USER_CATEGORY_AVERAGE', 'Average Users Count', 'interaction'),
    ('USER_CATEGORY_POOR', 'Poor Users Count', 'interaction'),
    ('CRASH_RATE', 'Crash Rate (%)', 'interaction'),
    ('ANR_RATE', 'ANR Rate (%)', 'interaction'),
    ('FROZEN_FRAME_RATE', 'Frozen Frame Rate (%)', 'interaction'),
    ('POOR_USER_RATE', 'Poor Users (%)', 'interaction'),
    ('AVERAGE_USER_RATE', 'Average Users (%)', 'interaction'),
    ('GOOD_USER_RATE', 'Good Users Rate (%)', 'interaction'),
    ('EXCELLENT_USER_RATE', 'Excellent Users (%)', 'interaction');

-- Insert APP_VITALS scope metrics
INSERT INTO alert_metrics (name, label, scope) VALUES
    ('CRASH_FREE_USERS_PERCENTAGE', 'Crash-Free Users %', 'app_vitals'),
    ('CRASH_FREE_SESSIONS_PERCENTAGE', 'Crash-Free Sessions %', 'app_vitals'),
    ('CRASH_USERS', 'Crash Users Count', 'app_vitals'),
    ('CRASH_SESSIONS', 'Crash Sessions Count', 'app_vitals'),
    ('ALL_USERS', 'Total Users Count', 'app_vitals'),
    ('ALL_SESSIONS', 'Total Sessions Count', 'app_vitals'),
    ('ANR_FREE_USERS_PERCENTAGE', 'ANR-Free Users %', 'app_vitals'),
    ('ANR_FREE_SESSIONS_PERCENTAGE', 'ANR-Free Sessions %', 'app_vitals'),
    ('ANR_USERS', 'ANR Users Count', 'app_vitals'),
    ('ANR_SESSIONS', 'ANR Sessions Count', 'app_vitals'),
    ('NON_FATAL_FREE_USERS_PERCENTAGE', 'Non-Fatal Free Users %', 'app_vitals'),
    ('NON_FATAL_FREE_SESSIONS_PERCENTAGE', 'Non-Fatal Free Sessions %', 'app_vitals'),
    ('NON_FATAL_USERS', 'Non-Fatal Users Count', 'app_vitals'),
    ('NON_FATAL_SESSIONS', 'Non-Fatal Sessions Count', 'app_vitals');

-- Insert Screen scope metrics
INSERT INTO alert_metrics (name, label, scope) VALUES
    ('SCREEN_DAILY_USERS', 'Daily Users Count', 'screen'),
    ('ERROR_RATE', 'Error Rate (%)', 'screen'),
    ('SCREEN_TIME', 'Screen Time (s)', 'screen'),
    ('LOAD_TIME', 'Load Time (ms)', 'screen'),
    ('CRASH_FREE_USERS_PERCENTAGE', 'Crash Free Users Percentage (%)', 'screen'),
    ('CRASH_FREE_SESSIONS_PERCENTAGE', 'Crash Free Sessions Percentage (%)', 'screen'),
    ('ANR_FREE_USERS_PERCENTAGE', 'ANR Free Users Percentage (%)', 'screen'),
    ('ANR_FREE_SESSIONS_PERCENTAGE', 'ANR Free Sessions Percentage (%)', 'screen'),
    ('NON_FATAL_FREE_USERS_PERCENTAGE', 'Non-Fatal Free Users Percentage (%)', 'screen'),
    ('NON_FATAL_FREE_SESSIONS_PERCENTAGE', 'Non-Fatal Free Sessions Percentage (%)', 'screen');

-- Insert network_api scope metrics
INSERT INTO alert_metrics (name, label, scope) VALUES
    ('NET_0', 'Connection Error Count', 'network_api'),
    ('NET_2XX', '2XX Success Count', 'network_api'),
    ('NET_3XX', '3XX Redirect Count', 'network_api'),
    ('NET_4XX', '4XX Client Error Count', 'network_api'),
    ('NET_5XX', '5XX Server Error Count', 'network_api'),
    ('NET_4XX_RATE', '4XX Error Rate (%)', 'network_api'),
    ('NET_5XX_RATE', '5XX Error Rate (%)', 'network_api'),
    ('DURATION_P99', 'Duration P99 (ms)', 'network_api'),
    ('DURATION_P95', 'Duration P95 (ms)', 'network_api'),
    ('DURATION_P50', 'Duration P50 (ms)', 'network_api'),
    ('ERROR_RATE', 'Error Rate (%)', 'network_api'),
    ('NET_COUNT', 'Total Network Requests', 'network_api');

-- Grant privileges (adjust as needed for your environment)
-- GRANT ALL PRIVILEGES ON pulse_db.* TO 'pulse_user'@'%' IDENTIFIED BY 'pulse_password';
-- FLUSH PRIVILEGES;

-- ============================================================
-- NEW TABLES FOR MULTI-TENANCY & RBAC (February 2026)
-- ============================================================

-- Project API Keys table for rotatable, encrypted API keys
CREATE TABLE IF NOT EXISTS project_api_keys (
    id                          BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id                  VARCHAR(64) NOT NULL COMMENT 'Project identifier (proj-{uuid})',
    api_key_encrypted           TEXT NOT NULL COMMENT 'AES-256 encrypted API key',
    encryption_salt             VARCHAR(100) NOT NULL COMMENT 'Salt used for encryption',
    api_key_digest              VARCHAR(100) NOT NULL COMMENT 'SHA-256 digest for fast lookup',
    is_active                   BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Key active status',
    grace_period_ends_at        TIMESTAMP NULL COMMENT 'For smooth key rotation',
    created_by                  VARCHAR(255) NOT NULL COMMENT 'User who created the key',
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deactivated_at              TIMESTAMP NULL COMMENT 'When key was deactivated',
    deactivated_by              VARCHAR(255) NULL COMMENT 'User who deactivated the key',
    deactivation_reason         VARCHAR(255) NULL COMMENT 'Reason for deactivation',
    
    CONSTRAINT fk_pak_project FOREIGN KEY (project_id) 
        REFERENCES projects(project_id) ON DELETE CASCADE,
    INDEX idx_project_active (project_id, is_active),
    INDEX idx_api_key_digest (api_key_digest),
    INDEX idx_grace_period (grace_period_ends_at)
) COMMENT='Rotatable encrypted API keys for SDK authentication';

-- ClickHouse Project Credentials (per-project ClickHouse users)
CREATE TABLE IF NOT EXISTS clickhouse_project_credentials (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id VARCHAR(100) NOT NULL UNIQUE COMMENT 'Project ID (proj-{uuid})',
    clickhouse_username VARCHAR(100) NOT NULL COMMENT 'ClickHouse username for this project',
    clickhouse_password_encrypted TEXT NOT NULL COMMENT 'AES encrypted password',
    encryption_salt VARCHAR(100) NOT NULL COMMENT 'Salt used for encryption',
    password_digest VARCHAR(100) NOT NULL COMMENT 'SHA-256 digest for verification',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Credential active status',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_ch_project_cred FOREIGN KEY (project_id) 
        REFERENCES projects(project_id) ON DELETE CASCADE,
    
    INDEX idx_project_active (project_id, is_active),
    INDEX idx_username (clickhouse_username)
);

-- Athena job tracking table (depends on projects table)
CREATE TABLE IF NOT EXISTS athena_job (
    job_id VARCHAR(255) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT 'Parent tenant for organizational hierarchy',
    project_id VARCHAR(64) COMMENT 'Project where query was executed (data isolation)',
    query_string TEXT NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    query_execution_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'RUNNING',
    result_location VARCHAR(500),
    error_message TEXT,
    data_scanned_in_bytes BIGINT NULL,
    execution_time_millis BIGINT NULL,
    engine_execution_time_millis BIGINT NULL,
    query_queue_time_millis BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    INDEX idx_status (status),
    INDEX idx_query_execution_id (query_execution_id),
    INDEX idx_created_at (created_at),
    INDEX idx_user_email (user_email),
    INDEX idx_user_email_created_at (user_email, created_at),
    INDEX idx_athena_job_tenant (tenant_id),
    INDEX idx_athena_job_project (project_id),
    INDEX idx_athena_job_tenant_project (tenant_id, project_id),
    CONSTRAINT fk_athena_job_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id),
    CONSTRAINT fk_athena_job_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE
);

-- DEPRECATED: Use clickhouse_project_credentials instead (per-project isolation)
-- This table is kept for backward compatibility only
CREATE TABLE IF NOT EXISTS clickhouse_tenant_credentials (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id VARCHAR(100) NOT NULL UNIQUE,
    clickhouse_username VARCHAR(100) NOT NULL,
    clickhouse_password_encrypted TEXT NOT NULL,
    encryption_salt VARCHAR(100) NOT NULL,
    password_digest VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ch_cred_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    INDEX idx_tenant_active (tenant_id, is_active)
);

Insert into clickhouse_tenant_credentials(tenant_id, clickhouse_username, clickhouse_password_encrypted, encryption_salt, password_digest,is_active)
VALUES('default', 'pulse_user', '4DmcqzM5CvhbfldwSodVxV2RKujGtFuk0/ON9qNjp/ZyL8uxdKar31iU', 'ESp44TCMn7nTiBIm6Kfd/g==', 'eGCdIGWj2FVzvDfTmTsxOl2cbpbYPRI5ts9Fk9D3jVA=', 1);

CREATE TABLE IF NOT EXISTS clickhouse_credential_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    performed_by VARCHAR(255) NOT NULL,
    details JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_credential_audit_project (project_id),
    CONSTRAINT fk_credential_audit_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE
);

-- Display summary
SELECT 'Database initialization completed successfully (with new RBAC tables)!' AS status;
SELECT COUNT(*) AS total_tables FROM information_schema.tables WHERE table_schema = 'pulse_db';
