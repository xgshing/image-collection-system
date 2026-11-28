-- Optional recovery for an empty Batch 2 schema. This is not the default rollback.
-- MySQL cannot undo a committed CREATE TABLE. Dropping a table that already has rows
-- destroys those rows and cannot restore them.
-- This procedure counts rows first. If any Batch 2 table has a row, it stops and drops nothing.
-- Take a backup before the forward migration. Do not run this file against production.
-- It does not touch user, user_session, image, tag, or image_tag.

DROP PROCEDURE IF EXISTS batch2_rollback_if_empty;

DELIMITER $$

CREATE PROCEDURE batch2_rollback_if_empty()
BEGIN
    DECLARE row_count BIGINT DEFAULT 0;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'user_message') THEN
        SELECT COUNT(*) INTO row_count FROM user_message;
        IF row_count > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'user_message has rows; rollback aborted';
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'project_invite_acceptance') THEN
        SELECT COUNT(*) INTO row_count FROM project_invite_acceptance;
        IF row_count > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'project_invite_acceptance has rows; rollback aborted';
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'project_invite') THEN
        SELECT COUNT(*) INTO row_count FROM project_invite;
        IF row_count > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'project_invite has rows; rollback aborted';
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'project_photo') THEN
        SELECT COUNT(*) INTO row_count FROM project_photo;
        IF row_count > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'project_photo has rows; rollback aborted';
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'project_member') THEN
        SELECT COUNT(*) INTO row_count FROM project_member;
        IF row_count > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'project_member has rows; rollback aborted';
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'project') THEN
        SELECT COUNT(*) INTO row_count FROM project;
        IF row_count > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'project has rows; rollback aborted';
        END IF;
    END IF;

    DROP TABLE IF EXISTS user_message;
    DROP TABLE IF EXISTS project_invite_acceptance;
    DROP TABLE IF EXISTS project_invite;
    DROP TABLE IF EXISTS project_photo;
    DROP TABLE IF EXISTS project_member;
    DROP TABLE IF EXISTS project;
END$$

DELIMITER ;

CALL batch2_rollback_if_empty();

DROP PROCEDURE IF EXISTS batch2_rollback_if_empty;
