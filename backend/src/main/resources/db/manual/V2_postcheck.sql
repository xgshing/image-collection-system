-- Read-only structure check after V2. This file does not change data or schema.
-- The first query lists problems. An empty result means the structure matches this batch.
-- The following queries are evidence and may return rows.

SELECT 'missing_table' AS problem, expected_name AS detail
FROM (
    SELECT 'project' AS expected_name
    UNION ALL SELECT 'project_member'
    UNION ALL SELECT 'project_photo'
    UNION ALL SELECT 'project_invite'
    UNION ALL SELECT 'project_invite_acceptance'
    UNION ALL SELECT 'user_message'
) expected
LEFT JOIN information_schema.tables t
  ON t.table_schema = DATABASE()
 AND t.table_name = expected.expected_name
 AND t.engine = 'InnoDB'
 AND t.table_collation = 'utf8mb4_unicode_ci'
WHERE t.table_name IS NULL

UNION ALL

SELECT 'missing_column', CONCAT(expected.table_name, '.', expected.column_name)
FROM (
    SELECT 'project' AS table_name, 'id' AS column_name, 'bigint' AS column_type
    UNION ALL SELECT 'project', 'name', 'varchar(100)'
    UNION ALL SELECT 'project', 'owner_user_id', 'bigint'
    UNION ALL SELECT 'project', 'watermark_enabled', 'tinyint'
    UNION ALL SELECT 'project', 'default_watermark_position', 'varchar(16)'
    UNION ALL SELECT 'project', 'status', 'varchar(16)'
    UNION ALL SELECT 'project', 'created_at', 'datetime(3)'
    UNION ALL SELECT 'project', 'updated_at', 'datetime(3)'
    UNION ALL SELECT 'project_member', 'id', 'bigint'
    UNION ALL SELECT 'project_member', 'project_id', 'bigint'
    UNION ALL SELECT 'project_member', 'user_id', 'bigint'
    UNION ALL SELECT 'project_member', 'role', 'varchar(16)'
    UNION ALL SELECT 'project_member', 'status', 'varchar(16)'
    UNION ALL SELECT 'project_member', 'status_changed_at', 'datetime(3)'
    UNION ALL SELECT 'project_member', 'removed_by_user_id', 'bigint'
    UNION ALL SELECT 'project_member', 'joined_at', 'datetime(3)'
    UNION ALL SELECT 'project_member', 'owner_slot', 'tinyint'
    UNION ALL SELECT 'project_photo', 'project_id', 'bigint'
    UNION ALL SELECT 'project_photo', 'image_id', 'bigint'
    UNION ALL SELECT 'project_photo', 'photographer_user_id', 'bigint'
    UNION ALL SELECT 'project_invite', 'token_hash', 'char(64)'
    UNION ALL SELECT 'project_invite', 'expires_at', 'datetime(3)'
    UNION ALL SELECT 'project_invite', 'revoked_at', 'datetime(3)'
    UNION ALL SELECT 'project_invite_acceptance', 'invite_id', 'bigint'
    UNION ALL SELECT 'project_invite_acceptance', 'invitee_user_id', 'bigint'
    UNION ALL SELECT 'project_invite_acceptance', 'decision', 'varchar(16)'
    UNION ALL SELECT 'project_invite_acceptance', 'decided_at', 'datetime(3)'
    UNION ALL SELECT 'user_message', 'user_id', 'bigint'
    UNION ALL SELECT 'user_message', 'type', 'varchar(32)'
    UNION ALL SELECT 'user_message', 'title', 'varchar(255)'
    UNION ALL SELECT 'user_message', 'body', 'varchar(500)'
    UNION ALL SELECT 'user_message', 'project_id', 'bigint'
    UNION ALL SELECT 'user_message', 'invite_id', 'bigint'
    UNION ALL SELECT 'user_message', 'read_at', 'datetime(3)'
    UNION ALL SELECT 'user_message', 'created_at', 'datetime(3)'
) expected
LEFT JOIN information_schema.columns c
  ON c.table_schema = DATABASE()
 AND c.table_name = expected.table_name
 AND c.column_name = expected.column_name
 AND c.column_type = expected.column_type
WHERE c.column_name IS NULL

UNION ALL

SELECT 'plain_invite_token_column', column_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'project_invite'
  AND column_name IN ('token', 'plain_token', 'scene')

UNION ALL

SELECT 'owner_slot_not_generated', EXTRA
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'project_member'
  AND column_name = 'owner_slot'
  AND EXTRA NOT LIKE '%GENERATED%'

UNION ALL

SELECT 'missing_unique', expected.index_name
FROM (
    SELECT 'project_member' AS table_name, 'uk_project_member' AS index_name
    UNION ALL SELECT 'project_member', 'uk_project_one_owner'
    UNION ALL SELECT 'project_photo', 'uk_project_photo_image'
    UNION ALL SELECT 'project_photo', 'uk_project_photo_request'
    UNION ALL SELECT 'project_invite', 'uk_project_invite_hash'
    UNION ALL SELECT 'project_invite_acceptance', 'uk_invite_acceptance'
    UNION ALL SELECT 'user_message', 'uk_user_message_invite'
) expected
LEFT JOIN information_schema.statistics s
  ON s.table_schema = DATABASE()
 AND s.table_name = expected.table_name
 AND s.index_name = expected.index_name
 AND s.non_unique = 0
WHERE s.index_name IS NULL

UNION ALL

SELECT 'missing_or_cascading_fk', expected.constraint_name
FROM (
    SELECT 'fk_project_owner' AS constraint_name
    UNION ALL SELECT 'fk_project_member_project'
    UNION ALL SELECT 'fk_project_member_user'
    UNION ALL SELECT 'fk_project_member_removed_by'
    UNION ALL SELECT 'fk_project_photo_project'
    UNION ALL SELECT 'fk_project_photo_image'
    UNION ALL SELECT 'fk_project_photo_photographer'
    UNION ALL SELECT 'fk_project_invite_project'
    UNION ALL SELECT 'fk_project_invite_inviter'
    UNION ALL SELECT 'fk_acceptance_invite'
    UNION ALL SELECT 'fk_acceptance_user'
    UNION ALL SELECT 'fk_user_message_user'
    UNION ALL SELECT 'fk_user_message_project'
    UNION ALL SELECT 'fk_user_message_invite'
) expected
LEFT JOIN information_schema.referential_constraints r
  ON r.constraint_schema = DATABASE()
 AND r.constraint_name = expected.constraint_name
 AND r.delete_rule = 'RESTRICT'
 AND r.update_rule = 'RESTRICT'
WHERE r.constraint_name IS NULL

UNION ALL

SELECT 'missing_check', expected.constraint_name
FROM (
    SELECT 'chk_project_status' AS constraint_name
    UNION ALL SELECT 'chk_project_watermark_position'
    UNION ALL SELECT 'chk_project_member_role'
    UNION ALL SELECT 'chk_project_member_status'
    UNION ALL SELECT 'chk_project_photo_position'
    UNION ALL SELECT 'chk_project_invite_status'
    UNION ALL SELECT 'chk_acceptance_decision'
    UNION ALL SELECT 'chk_user_message_type'
) expected
LEFT JOIN information_schema.table_constraints c
  ON c.table_schema = DATABASE()
 AND c.constraint_name = expected.constraint_name
 AND c.constraint_type = 'CHECK'
WHERE c.constraint_name IS NULL;

SELECT TABLE_NAME, CONSTRAINT_NAME, DELETE_RULE, UPDATE_RULE
FROM information_schema.referential_constraints
WHERE constraint_schema = DATABASE()
  AND table_name IN (
      'project',
      'project_member',
      'project_photo',
      'project_invite',
      'project_invite_acceptance',
      'user_message'
  )
ORDER BY TABLE_NAME, CONSTRAINT_NAME;
