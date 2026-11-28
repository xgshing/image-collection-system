package com.xgsheng.backend.service.impl;

import com.xgsheng.backend.entity.Image;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.mapper.ImageMapper;
import com.xgsheng.backend.mapper.ProjectPhotoMapper;
import com.xgsheng.backend.project.ProjectPhotoRules;
import com.xgsheng.backend.service.StorageService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageDeletionProtectionTest {

    private final MemoryImages images = new MemoryImages();
    private final MemoryPhotos photos = new MemoryPhotos();
    private final ImageServiceImpl service = new ImageServiceImpl(images, new UnusedStorage(), photos);

    @Test
    void referencedImageIsNotDeleted() {
        images.present = true;
        photos.references = 1;

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(7L, 4L));
        assertEquals(400, ex.getStatus());
        assertEquals(ProjectPhotoRules.DELETE_BLOCKED, ex.getMessage());
        assertFalse(images.deleted);
    }

    @Test
    void unreferencedImageKeepsTheOldDeleteFlow() {
        images.present = true;

        assertTrue(service.delete(7L, 4L));
        assertTrue(images.deleted);
        assertEquals(0, photos.references);
    }

    @Test
    void missingImageDoesNotLookLikeSuccess() {
        assertFalse(service.delete(7L, 4L));
        assertFalse(images.deleted);
    }

    private static final class MemoryImages implements ImageMapper {
        @Override
        public void updateOrigin(Long userId, Long id, String origin) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Image lockProjectOriginById(Long id) {
            return null;
        }

        @Override
        public void deleteProjectOriginImageById(Long id) {
            throw new UnsupportedOperationException();
        }
        private boolean present;
        private boolean deleted;

        @Override
        public Image lockByOwner(Long userId, Long id) {
            if (!present) {
                return null;
            }
            Image image = new Image();
            image.setId(id);
            image.setUserId(userId);
            return image;
        }

        @Override
        public void deleteImageTagByImageId(Long imageId) {
            deleted = true;
        }

        @Override
        public void deleteById(Long userId, Long id) {
            deleted = true;
        }

        @Override
        public void insert(Image image) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Image findByMd5(Long userId, String md5) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Image findById(Long userId, Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Image> findByTag(Long userId, String tag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Image> findByTags(Long userId, List<String> tags) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Image> findAll(Long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> findAllTagNames(Long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> findRecentTagNames(Long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> findTagNamesByImageId(Long imageId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Long findTagIdByName(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void insertTag(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void insertImageTag(Long imageId, Long tagId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteImageTagByUserIdAndTagId(Long userId, Long tagId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void insertImageTagForUserImagesByTagId(Long userId, Long sourceTagId, Long targetTagId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateCreateTime(Long userId, Long id, java.util.Date createTime) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class MemoryPhotos implements ProjectPhotoMapper {
        private int references;

        @Override
        public int countByImageId(long imageId) {
            return references;
        }

        @Override
        public int countByProjectId(long projectId) {
            return 0;
        }

        @Override
        public com.xgsheng.backend.entity.ProjectPhoto findByIdInProject(long projectId, long photoId) {
            return null;
        }

        @Override
        public com.xgsheng.backend.entity.ProjectPhoto findByImageId(long imageId) {
            return null;
        }

        @Override
        public com.xgsheng.backend.entity.ProjectPhoto findByPhotographerAndRequest(long photographerUserId, String clientRequestId) {
            return null;
        }

        @Override
        public List<com.xgsheng.backend.entity.ProjectPhoto> listByProject(long projectId, int limit, long offset) {
            return List.of();
        }

        @Override
        public int insert(com.xgsheng.backend.entity.ProjectPhoto photo) {
            return 0;
        }

        @Override
        public int updateEditable(long id, long projectId, long photographerUserId, String note,
                                  String watermarkPosition, int watermarkVisible, LocalDateTime updatedAt) {
            return 0;
        }

        @Override
        public int deleteByIdInProject(long projectId, long photoId) {
            return 0;
        }
    }

    private static final class UnusedStorage implements StorageService {
        @Override
        public String storeBytes(byte[] bytes, String originalFilename, String contentType, String category) {
            return "";
        }

        @Override
        public String canonicalUrl(String url) {
            return url;
        }

        @Override
        public String readableUrl(String storedUrl) {
            return storedUrl;
        }

        @Override
        public int signedReadTtlSeconds() {
            return 3600;
        }

        @Override
        public byte[] readStoredBytes(String storedUrl) {
            return new byte[0];
        }
    }
}
