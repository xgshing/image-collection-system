package com.xgsheng.backend.service.impl;

import com.xgsheng.backend.entity.Image;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.mapper.ImageMapper;
import com.xgsheng.backend.mapper.ProjectPhotoMapper;
import com.xgsheng.backend.service.ImageService;
import com.xgsheng.backend.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 只验证 Spring 代理会把 delete 放进一个事务。这里的连接是空的，不连接 MySQL。
 */
class ImageDeleteTransactionBoundaryTest {

    @Test
    void referencedDeleteRollsBackWithoutDeletingRows() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TxConfig.class)) {
            context.getBean(ProbePhotos.class).references = 1;
            ProbeImages images = context.getBean(ProbeImages.class);
            ImageService service = context.getBean(ImageService.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertThrows(BusinessException.class, () -> service.delete(7L, 4L));
            assertTrue(images.lockInTransaction);
            assertTrue(images.countInTransaction);
            assertFalse(images.deleted);
            assertEquals(TransactionSynchronization.STATUS_ROLLED_BACK, images.completion.get());
        }
    }

    @Test
    void unreferencedDeleteCommitsLockCheckAndBothDeletesTogether() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TxConfig.class)) {
            ProbeImages images = context.getBean(ProbeImages.class);
            ImageService service = context.getBean(ImageService.class);

            assertTrue(service.delete(7L, 4L));
            assertTrue(images.lockInTransaction);
            assertTrue(images.countInTransaction);
            assertTrue(images.tagDeleteInTransaction);
            assertTrue(images.imageDeleteInTransaction);
            assertEquals(TransactionSynchronization.STATUS_COMMITTED, images.completion.get());
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class TxConfig {
        @Bean
        DataSource dataSource() throws Exception {
            Connection connection = mock(Connection.class);
            when(connection.getAutoCommit()).thenReturn(true);
            DataSource dataSource = mock(DataSource.class);
            when(dataSource.getConnection()).thenReturn(connection);
            return dataSource;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        ProbeImages images() {
            return new ProbeImages();
        }

        @Bean
        ProbePhotos photos(ProbeImages images) {
            return new ProbePhotos(images);
        }

        @Bean
        ImageService imageService(ProbeImages images, ProbePhotos photos) {
            return new ImageServiceImpl(images, new UnusedStorage(), photos);
        }
    }

    static final class ProbeImages implements ImageMapper {
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
        private boolean lockInTransaction;
        private boolean countInTransaction;
        private boolean tagDeleteInTransaction;
        private boolean imageDeleteInTransaction;
        private boolean deleted;
        private final AtomicInteger completion = new AtomicInteger(-1);

        @Override
        public Image lockByOwner(Long userId, Long id) {
            lockInTransaction = TransactionSynchronizationManager.isActualTransactionActive();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    completion.set(status);
                }
            });
            Image image = new Image();
            image.setId(id);
            image.setUserId(userId);
            return image;
        }

        @Override
        public void deleteImageTagByImageId(Long imageId) {
            tagDeleteInTransaction = TransactionSynchronizationManager.isActualTransactionActive();
            deleted = true;
        }

        @Override
        public void deleteById(Long userId, Long id) {
            imageDeleteInTransaction = TransactionSynchronizationManager.isActualTransactionActive();
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

    static final class ProbePhotos implements ProjectPhotoMapper {
        private final ProbeImages images;
        private int references;

        private ProbePhotos(ProbeImages images) {
            this.images = images;
        }

        @Override
        public int countByImageId(long imageId) {
            images.countInTransaction = TransactionSynchronizationManager.isActualTransactionActive();
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
