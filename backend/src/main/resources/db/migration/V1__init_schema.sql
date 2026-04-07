CREATE TABLE IF NOT EXISTS user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    openid VARCHAR(128) NOT NULL,
    nick_name VARCHAR(255) NOT NULL DEFAULT '',
    avatar_url VARCHAR(1024) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_openid (openid)
);

CREATE TABLE IF NOT EXISTS user_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    session_token VARCHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_session_token (session_token),
    KEY idx_user_session_user_id (user_id),
    KEY idx_user_session_expires_at (expires_at)
);

CREATE TABLE IF NOT EXISTS image (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    url VARCHAR(1024) NOT NULL DEFAULT '',
    local_url VARCHAR(1024) NOT NULL DEFAULT '',
    md5 VARCHAR(32) NOT NULL,
    width INT NULL,
    height INT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_image_user_id (user_id),
    KEY idx_image_md5 (md5),
    KEY idx_image_create_time (create_time)
);

CREATE TABLE IF NOT EXISTS tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_name (name)
);

CREATE TABLE IF NOT EXISTS image_tag (
    image_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (image_id, tag_id),
    KEY idx_image_tag_tag_id (tag_id)
);
