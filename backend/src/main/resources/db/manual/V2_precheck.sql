-- Read-only checks before V2. This file does not create, alter, or delete anything.
-- Run against the local database only after confirming the connection target.
-- Every result row should show ok = 1. Any 0 means stop and do not run V2.
-- Do not re-run V1 to make these checks pass.

SELECT 'server' AS item,
       VERSION() AS actual,
       '8.0.16 or newer' AS expected,
       IF(VERSION() >= '8.0.16', 1, 0) AS ok;

SELECT 'database' AS item,
       DATABASE() AS actual,
       'image_collection on the approved local instance' AS expected,
       IF(DATABASE() = 'image_collection', 1, 0) AS ok;

SELECT 'existing_table' AS item,
       TABLE_NAME AS actual,
       'InnoDB utf8mb4_unicode_ci' AS expected,
       IF(ENGINE = 'InnoDB' AND TABLE_COLLATION = 'utf8mb4_unicode_ci', 1, 0) AS ok
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('user', 'user_session', 'image', 'tag', 'image_tag')
ORDER BY TABLE_NAME;

SELECT 'signed_pk' AS item,
       CONCAT(TABLE_NAME, '.', COLUMN_NAME, ' ', COLUMN_TYPE) AS actual,
       'bigint' AS expected,
       IF(COLUMN_TYPE = 'bigint', 1, 0) AS ok
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND (
      (table_name = 'user' AND column_name = 'id')
      OR (table_name = 'image' AND column_name = 'id')
  );

SELECT 'batch2_absent' AS item,
       IFNULL(GROUP_CONCAT(table_name ORDER BY table_name), '') AS actual,
       '' AS expected,
       IF(COUNT(*) = 0, 1, 0) AS ok
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'project',
      'project_member',
      'project_photo',
      'project_invite',
      'project_invite_acceptance',
      'user_message'
  );

SELECT 'flyway_history' AS item,
       IF(COUNT(*) = 0, 'absent', 'present') AS actual,
       'absent is expected; do not create it in this batch' AS expected,
       1 AS ok
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'flyway_schema_history';
