---
name: mysql-migration
description: Workflow for MySQL schema changes — adding tables, columns, or modifying the pulse_db metadata schema. Use when making changes to MySQL tables.
disable-model-invocation: true
---

# MySQL Migration

## Workflow

```
- [ ] Step 1: Create migration SQL
- [ ] Step 2: Update init script for fresh installs
- [ ] Step 3: Update backend model/DAO
- [ ] Step 4: Apply and verify
```

## Step 1: Create Migration SQL

Create `deploy/db/migration-<description>.sql`:

```sql
-- Adding a column
ALTER TABLE interaction ADD COLUMN IF NOT EXISTS new_field VARCHAR(255) DEFAULT NULL;

-- Adding a table
CREATE TABLE IF NOT EXISTS my_new_table (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

## Step 2: Update Init Script

Edit `deploy/db/mysql-init.sql` to include the change for fresh database initialization. This file runs on first MySQL container start.

## Step 3: Update Backend

1. **Model class**: add/update fields in `model/` package
2. **DAO**: update SQL in `Queries` class and DAO methods
3. **DTO**: update request/response DTOs if the field is API-facing
4. **Mapper**: update MapStruct mapper if model ↔ DTO mapping changes
5. **Service**: update business logic if needed

## Step 4: Apply and Verify

Read MySQL credentials from `deploy/.env` (variables: `MYSQL_USER` / `MYSQL_PASSWORD`, defaults in docker-compose: `pulse_user` / `pulse_password`):

```bash
# Apply to running MySQL
docker exec -i pulse-mysql mysql -u$MYSQL_USER -p$MYSQL_PASSWORD pulse_db < deploy/db/migration-<desc>.sql

# Verify
docker exec pulse-mysql mysql -u$MYSQL_USER -p$MYSQL_PASSWORD pulse_db -e "DESCRIBE my_table;"
```
