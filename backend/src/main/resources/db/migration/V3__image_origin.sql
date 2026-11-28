-- 区分收藏图片和水印相机原图：collect=收藏，project=水印相机项目原图（不进入收藏列表）
ALTER TABLE image
    ADD COLUMN origin VARCHAR(16) NOT NULL DEFAULT 'collect' AFTER md5,
    ADD KEY idx_image_user_origin (user_id, origin);

-- 已经被项目照片引用、且没有任何标签的图片，视为水印相机原图。
-- 有标签的图片说明用户在收藏里整理过，保持为收藏。
UPDATE image i
SET i.origin = 'project'
WHERE EXISTS (SELECT 1 FROM project_photo pp WHERE pp.image_id = i.id)
  AND NOT EXISTS (SELECT 1 FROM image_tag it WHERE it.image_id = i.id);
