-- Pulse Observability - Multi-Tenancy Migration
-- This script adds tenant_id to all necessary tables for multi-tenant support

USE pulse_db;

-- Create tenants table to store tenant information
CREATE TABLE IF NOT EXISTS tenants (
    tenant_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    gcp_tenant_id VARCHAR(32) NOT NULL,
    domain_name VARCHAR(32) NOT NULL,
    INDEX idx_tenant_active (is_active)
);

-- Insert a default tenant for existing data
INSERT INTO tenants (tenant_id, name, description, is_active, gcp_tenant_id, domain_name)
VALUES ('default', 'Default Tenant', 'Default tenant for existing data', TRUE, "default", "default")
ON DUPLICATE KEY UPDATE name = name;

-- Add tenant_id to interaction table
ALTER TABLE interaction 
ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT 'default' AFTER interaction_id,
ADD INDEX idx_interaction_tenant (tenant_id),
ADD CONSTRAINT fk_interaction_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Add tenant_id to symbol_files table
ALTER TABLE symbol_files 
ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
DROP PRIMARY KEY,
ADD PRIMARY KEY (tenant_id, app_version, app_version_code, platform, framework),
ADD CONSTRAINT fk_symbol_files_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Add tenant_id to pulse_sdk_configs table
ALTER TABLE pulse_sdk_configs 
ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT 'default' AFTER version,
ADD INDEX idx_sdk_configs_tenant (tenant_id),
ADD CONSTRAINT fk_sdk_configs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Add tenant_id to notification_channels table
ALTER TABLE notification_channels 
ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT 'default' AFTER notification_channel_id,
ADD INDEX idx_notification_channels_tenant (tenant_id),
ADD CONSTRAINT fk_notification_channels_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Add tenant_id to alerts table
ALTER TABLE alerts 
ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT 'default' AFTER id,
ADD INDEX idx_alerts_tenant (tenant_id),
ADD CONSTRAINT fk_alerts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Add tenant_id to athena_job table
ALTER TABLE athena_job 
ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT 'default' AFTER job_id,
ADD INDEX idx_athena_job_tenant (tenant_id),
ADD CONSTRAINT fk_athena_job_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Note: The following tables are reference/lookup tables and do NOT need tenant_id:
-- - severity (system-wide severity levels)
-- - scope_types (system-wide scope types)
-- - alert_metrics (system-wide metric definitions)
-- 
-- The following tables inherit tenant context through their parent tables via foreign keys:
-- - alert_scope (linked to alerts which has tenant_id)
-- - alert_evaluation_history (linked to alert_scope which is linked to alerts)

-- Update indexes for common query patterns
CREATE INDEX idx_alerts_tenant_active ON alerts(tenant_id, is_active);
CREATE INDEX idx_interaction_tenant_archived ON interaction(tenant_id, is_archived);
CREATE INDEX idx_sdk_configs_tenant_active ON pulse_sdk_configs(tenant_id, is_active);

-- Display migration summary
SELECT 'Multi-tenancy migration completed successfully!' AS status;
SELECT 
    TABLE_NAME,
    COLUMN_NAME,
    DATA_TYPE
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = 'pulse_db' 
AND COLUMN_NAME = 'tenant_id'
ORDER BY TABLE_NAME;

