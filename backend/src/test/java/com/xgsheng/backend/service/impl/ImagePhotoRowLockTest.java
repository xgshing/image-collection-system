package com.xgsheng.backend.service.impl;

import com.xgsheng.backend.entity.Image;
import com.xgsheng.backend.entity.Project;
import com.xgsheng.backend.entity.ProjectMemberRow;
import com.xgsheng.backend.entity.ProjectPhoto;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.mapper.ImageMapper;
import com.xgsheng.backend.mapper.ProjectMapper;
import com.xgsheng.backend.mapper.ProjectMemberMapper;
import com.xgsheng.backend.mapper.ProjectPhotoMapper;
import com.xgsheng.backend.project.ProjectPhotoRules;
import com.xgsheng.backend.service.ProjectPhotoWriter;
import com.xgsheng.backend.service.StorageService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用同一把可重入锁模拟 InnoDB 的 image 行锁。它不能代替真实 MySQL 的锁等待。
 */
class ImagePhotoRowLockTest {

    @Test
    void deleteThatLocksFirstRemovesImageBeforeAssociation() throws Exception {
        Gate gate = new Gate();
        Harness harness = new Harness(gate);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch held = new CountDownLatch(1);
        gate.onLocked = held::countDown;
        try {
            Future<Boolean> deleting = pool.submit(() -> {
                try {
                    return harness.images().delete(7L, 4L);
                } finally {
                    gate.unlock();
                }
            });
            assertTrue(held.await(5, TimeUnit.SECONDS));
            Future<BusinessException> inserting = pool.submit(() -> insertOrError(harness, gate));
            assertTrue(deleting.get(5, TimeUnit.SECONDS));
            assertEquals(ProjectPhotoRules.IMAGE_NOT_FOUND, inserting.get(5, TimeUnit.SECONDS).getMessage());
            assertFalse(gate.imagePresent.get());
            assertEquals(0, gate.photos.get());
            assertFalse(gate.photos.get() > 0 && !gate.imagePresent.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void associationThatLocksFirstBlocksLaterDelete() throws Exception {
        Gate gate = new Gate();
        Harness harness = new Harness(gate);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch held = new CountDownLatch(1);
        gate.onLocked = held::countDown;
        try {
            Future<?> inserting = pool.submit(() -> {
                try {
                    return harness.writer().insert(12L, 7L, draft(), null, null, LocalDateTime.now());
                } finally {
                    gate.unlock();
                }
            });
            assertTrue(held.await(5, TimeUnit.SECONDS));
            Future<BusinessException> deleting = pool.submit(() -> {
                try {
                    harness.images().delete(7L, 4L);
                    return null;
                } catch (BusinessException ex) {
                    return ex;
                } finally {
                    gate.unlock();
                }
            });
            inserting.get(5, TimeUnit.SECONDS);
            assertEquals(ProjectPhotoRules.DELETE_BLOCKED, deleting.get(5, TimeUnit.SECONDS).getMessage());
            assertTrue(gate.imagePresent.get());
            assertEquals(1, gate.photos.get());
            assertFalse(gate.tagsDeleted.get());
            assertFalse(gate.photos.get() > 0 && !gate.imagePresent.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private static BusinessException insertOrError(Harness harness, Gate gate) {
        try {
            harness.writer().insert(12L, 7L, draft(), null, null, LocalDateTime.now());
            return null;
        } catch (BusinessException ex) {
            return ex;
        } finally {
            gate.unlock();
        }
    }

    private static ProjectPhoto draft() {
        ProjectPhoto photo = new ProjectPhoto();
        photo.setProjectId(12L);
        photo.setImageId(4L);
        photo.setPhotographerUserId(7L);
        photo.setTakenAt(LocalDateTime.of(2026, 11, 27, 5, 12));
        photo.setTimezone("Asia/Shanghai");
        photo.setNote("");
        photo.setGpsStatus("denied");
        photo.setWatermarkPosition("bottom_left");
        photo.setWatermarkVisible(1);
        photo.setClientRequestId("req-lock");
        return photo;
    }

    private static final class Harness {
        private final ImageServiceImpl imageService;
        private final ProjectPhotoWriter writer;

        private Harness(Gate gate) {
            SharedImages images = new SharedImages(gate);
            SharedPhotos photos = new SharedPhotos(gate);
            this.imageService = new ImageServiceImpl(images, new UnusedStorage(), photos);
            this.writer = new ProjectPhotoWriter(new OpenProject(), new ActiveMember(), photos, images);
        }

        private ImageServiceImpl images() {
            return imageService;
        }

        private ProjectPhotoWriter writer() {
            return writer;
        }
    }

    private static final class Gate {
        private final ReentrantLock rowLock = new ReentrantLock();
        private final AtomicBoolean imagePresent = new AtomicBoolean(true);
        private final AtomicInteger photos = new AtomicInteger();
        private final AtomicBoolean tagsDeleted = new AtomicBoolean();
        private volatile Runnable onLocked;

        private void lock() {
            rowLock.lock();
            Runnable callback = onLocked;
            if (callback != null) {
                onLocked = null;
                callback.run();
            }
        }

        private void unlock() {
            if (rowLock.isHeldByCurrentThread()) {
                rowLock.unlock();
            }
        }

        private void requireHeld() {
            if (!rowLock.isHeldByCurrentThread()) {
                throw new IllegalStateException("image row lock is not held");
            }
        }
    }

    private static final class SharedImages implements ImageMapper {
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
        private final Gate gate;

        private SharedImages(Gate gate) {
            this.gate = gate;
        }

        @Override
        public Image lockByOwner(Long userId, Long id) {
            gate.lock();
            if (!gate.imagePresent.get()) {
                gate.unlock();
                return null;
            }
            Image image = new Image();
            image.setId(id);
            image.setUserId(userId);
            return image;
        }

        @Override
        public void deleteImageTagByImageId(Long imageId) {
            gate.requireHeld();
            gate.tagsDeleted.set(true);
        }

        @Override
        public void deleteById(Long userId, Long id) {
            gate.requireHeld();
            gate.imagePresent.set(false);
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

    private static final class SharedPhotos implements ProjectPhotoMapper {
        private final Gate gate;

        private SharedPhotos(Gate gate) {
            this.gate = gate;
        }

        @Override
        public int countByImageId(long imageId) {
            gate.requireHeld();
            return gate.photos.get();
        }

        @Override
        public int insert(ProjectPhoto photo) {
            gate.requireHeld();
            photo.setId(80L);
            gate.photos.incrementAndGet();
            return 1;
        }

        @Override
        public int countByProjectId(long projectId) {
            return 0;
        }

        @Override
        public ProjectPhoto findByIdInProject(long projectId, long photoId) {
            return null;
        }

        @Override
        public ProjectPhoto findByImageId(long imageId) {
            return null;
        }

        @Override
        public ProjectPhoto findByPhotographerAndRequest(long photographerUserId, String clientRequestId) {
            return null;
        }

        @Override
        public List<ProjectPhoto> listByProject(long projectId, int limit, long offset) {
            return List.of();
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

    private static final class OpenProject implements ProjectMapper {
        @Override
        public Project lockById(long id) {
            Project project = new Project();
            project.setId(id);
            project.setName("项目");
            project.setStatus("active");
            return project;
        }

        @Override
        public int insert(Project project) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Project findById(long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updateOwner(long projectId, long previousOwnerUserId, long newOwnerUserId, LocalDateTime updatedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updateActive(Project project) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int archiveActive(long projectId, long ownerUserId, java.time.LocalDateTime updatedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteMessagesForProject(long projectId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteAcceptancesForProject(long projectId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteInvitesForProject(long projectId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deletePhotosForProject(long projectId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteMembersForProject(long projectId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteArchived(long projectId, long ownerUserId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.xgsheng.backend.entity.ProjectSummaryRow> listActiveForUser(long userId, int limit, long offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countActiveForUser(long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.xgsheng.backend.entity.ProjectSummaryRow findActiveDetail(long projectId, long userId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class ActiveMember implements ProjectMemberMapper {
        @Override
        public ProjectMemberRow lockByUserId(long projectId, long userId) {
            ProjectMemberRow row = new ProjectMemberRow();
            row.setUserId(userId);
            row.setStatus("active");
            return row;
        }

        @Override
        public int countProject(long projectId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.xgsheng.backend.access.ProjectAccessRecord findMembership(long projectId, long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int insertOwner(long projectId, long userId, LocalDateTime joinedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ProjectMemberRow> lockByUserIds(long projectId, long firstUserId, long secondUserId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int countActiveOwners(long projectId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int demoteOwner(long id, long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int promoteMember(long id, long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int markRemoved(long id, long removedByUserId, LocalDateTime changedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int markLeft(long id, LocalDateTime changedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int insertMember(long projectId, long userId, LocalDateTime joinedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int reactivateMember(long id, long userId, LocalDateTime changedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ProjectMemberRow> listActive(long projectId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updateNickName(long userId, String nickName) {
            throw new UnsupportedOperationException();
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
