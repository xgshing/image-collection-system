-- Batch 2 project tables. Do not execute until the migration report is approved.
-- This file is not run by the current application. Flyway Spring Boot is not on the classpath.
-- Do not re-run V1. Do not DROP existing user, user_session, image, tag, or image_tag tables.
-- If any target table already exists, the procedure stops and does not delete it.
-- DDL commits each CREATE TABLE. A failure after the first table leaves the earlier tables in place.
-- Recover an empty partial run only with db/manual/V2_rollback_if_empty.sql.

DROP PROCEDURE IF EXISTS batch2_create_project_tables;

DELIMITER $$

CREATE PROCEDURE batch2_create_project_tables()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'project',
              'project_member',
              'project_photo',
              'project_invite',
              'project_invite_acceptance',
              'user_message'
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'batch2 target table already exists; migration aborted without DROP';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name IN ('user', 'image')
          AND engine <> 'InnoDB'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'user or image is not InnoDB; migration aborted without altering it';
    END IF;

    CREATE TABLE project (
        id BIGINT NOT NULL AUTO_INCREMENT,
        name VARCHAR(100) NOT NULL,
        owner_user_id BIGINT NOT NULL,
        watermark_enabled TINYINT NOT NULL DEFAULT 1,
        default_watermark_position VARCHAR(16) NOT NULL DEFAULT 'bottom_left',
        status VARCHAR(16) NOT NULL DEFAULT 'active',
        created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        PRIMARY KEY (id),
        KEY idx_project_owner (owner_user_id),
        KEY idx_project_status (status),
        CONSTRAINT fk_project_owner FOREIGN KEY (owner_user_id) REFERENCES user (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
        CONSTRAINT chk_project_status CHECK (status IN ('active', 'archived')),
        CONSTRAINT chk_project_watermark_position CHECK (
            default_watermark_position IN ('top_left', 'top_right', 'bottom_left', 'bottom_right')
        )
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    CREATE TABLE project_member (
        id BIGINT NOT NULL AUTO_INCREMENT,
        project_id BIGINT NOT NULL,
        user_id BIGINT NOT NULL,
        role VARCHAR(16) NOT NULL,
        status VARCHAR(16) NOT NULL DEFAULT 'active',
        status_changed_at DATETIME(3) NULL,
        removed_by_user_id BIGINT NULL,
        joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        owner_slot TINYINT
            GENERATED ALWAYS AS (
                CASE WHEN role = 'owner' AND status = 'active' THEN 1 ELSE NULL END
            ) STORED,
        PRIMARY KEY (id),
        UNIQUE KEY uk_project_member (project_id, user_id),
        UNIQUE KEY uk_project_one_owner (project_id, owner_slot),
        KEY idx_project_member_user (user_id),
        CONSTRAINT fk_project_member_project FOREIGN KEY (project_id) REFERENCES project (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
        CONSTRAINT fk_project_member_user FOREIGN KEY (user_id) REFERENCES user (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
        CONSTRAINT fk_project_member_removed_by FOREIGN KEY (removed_by_user_id) REFERENCES user (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
        CONSTRAINT chk_project_member_role CHECK (role IN ('owner', 'member')),
        CONSTRAINT chk_project_member_status CHECK (status IN ('active', 'left', 'removed'))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    CREATE TABLE project_photo (
        id BIGINT NOT NULL AUTO_INCREMENT,
        project_id BIGINT NOT NULL,
        image_id BIGINT NOT NULL,
        photographer_user_id BIGINT NOT NULL,
        taken_at DATETIME(3) NOT NULL,
        timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
        note VARCHAR(500) NOT NULL DEFAULT '',
        location_text VARCHAR(255) NULL,
        latitude DECIMAL(9,6) NULL,
        longitude DECIMAL(9,6) NULL,
        coordinate_system VARCHAR(16) NULL,
        location_accuracy_m DECIMAL(8,2) NULL,
        location_captured_at DATETIME(3) NULL,
        gps_status VARCHAR(32) NOT NULL DEFAULT 'empty',
        geocode_status VARCHAR(32) NOT NULL DEFAULT 'skipped',
        weather_text VARCHAR(255) NULL,
        temperature_c DECIMAL(5,2) NULL,
        humidity_percent DECIMAL(5,2) NULL,
        weather_code VARCHAR(16) NULL,
        weather_provider VARCHAR(32) NULL,
        weather_observed_at DATETIME(3) NULL,
        weather_status VARCHAR(32) NOT NULL DEFAULT 'skipped',
        watermark_position VARCHAR(16) NOT NULL,
        watermark_visible TINYINT NOT NULL DEFAULT 1,
        project_name_snapshot VARCHAR(100) NOT NULL,
        width_px INT NULL,
        height_px INT NULL,
        client_request_id VARCHAR(64) NOT NULL,
        created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        PRIMARY KEY (id),
        UNIQUE KEY uk_project_photo_image (image_id),
        UNIQUE KEY uk_project_photo_request (photographer_user_id, client_request_id),
        KEY idx_project_photo_project_taken (project_id, taken_at),
        CONSTRAINT fk_project_photo_project FOREIGN KEY (project_id) REFERENCES project (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
        CONSTRAINT fk_project_photo_image FOREIGN KEY (image_id) REFERENCES image (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
        CONSTRAINT fk_project_photo_photographer FOREIGN KEY (photographer_user_id) REFERENCES user (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
        CONSTRAINT chk_project_photo_position CHECK (
            watermark_position IN ('top_left', 'top_right', 'bottom_left', 'bottom_right')
        )
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    CREATE TABLE project_invite (
        id BIGINT NOT NULL AUTO_INCREMENT,
        project_id BIGINT NOT NULL,
        inviter_user_id BIGINT NOT NULL,
        token_hash CHAR(64) NOT NULL,
        status VARCHAR(16) NOT NULL DEFAULT 'active',
        expires_at DATETIME(3) NOT NULL,
        revoked_at DATETIME(3) NULL,
        created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        PRIMARY KEY (id),
        UNIQUE KEY uk_project_invite_hash (token_hash),
        KEY idx_project_invite_project (project_id, status),
        CONSTRAINT fk_project_invite_project FOREIGN KEY (project_id) REFERENCES project (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
        CONSTRAINT fk_project_invite_inviter FOREIGN KEY (inviter_user_id) REFERENCES user (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
        CONSTRAINT chk_project_invite_status CHECK (status IN ('active', 'revoked', 'expired'))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    CREATE TABLE project_invite_acceptance (
        id BIGINT NOT NULL AUTO_INCREMENT,
        invite_id BIGINT NOT NULL,
        invitee_user_id BIGINT NOT NULL,
        decision VARCHAR(16) NOT NULL,
        decided_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        PRIMARY KEY (id),
        UNIQUE KEY uk_invite_acceptance (invite_id, invitee_user_id),
        KEY idx_invite_acceptance_user (invitee_user_id),
        CONSTRAINT fk_acceptance_invite FOREIGN KEY (invite_id) REFERENCES project_invite (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
        CONSTRAINT fk_acceptance_user FOREIGN KEY (invitee_user_id) REFERENCES user (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
        CONSTRAINT chk_acceptance_decision CHECK (decision IN ('accepted', 'rejected'))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    CREATE TABLE user_message (
        id BIGINT NOT NULL AUTO_INCREMENT,
        user_id BIGINT NOT NULL,
        type VARCHAR(32) NOT NULL,
        title VARCHAR(255) NOT NULL,
        body VARCHAR(500) NOT NULL DEFAULT '',
        project_id BIGINT NULL,
        invite_id BIGINT NULL,
        read_at DATETIME(3) NULL,
        created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        PRIMARY KEY (id),
        UNIQUE KEY uk_user_message_invite (user_id, invite_id),
        KEY idx_user_message_unread (user_id, read_at),
        KEY idx_user_message_project (user_id, project_id),
        CONSTRAINT fk_user_message_user FOREIGN KEY (user_id) REFERENCES user (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
        CONSTRAINT fk_user_message_project FOREIGN KEY (project_id) REFERENCES project (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
        CONSTRAINT fk_user_message_invite FOREIGN KEY (invite_id) REFERENCES project_invite (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,
        CONSTRAINT chk_user_message_type CHECK (type IN ('project_invite', 'project_member_removed'))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
END$$

DELIMITER ;

CALL batch2_create_project_tables();

DROP PROCEDURE IF EXISTS batch2_create_project_tables;
