package com.xgsheng.backend.service;

import com.xgsheng.backend.access.ProjectAccessLookup;
import com.xgsheng.backend.access.ProjectAccessRecord;
import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.Project;
import com.xgsheng.backend.entity.ProjectPhoto;
import com.xgsheng.backend.entity.User;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.mapper.ProjectMapper;
import com.xgsheng.backend.mapper.ProjectPhotoMapper;
import com.xgsheng.backend.project.ProjectPhotoRules;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectPhotoQueryServiceTest {

    private final MemoryLookup lookup = new MemoryLookup();
    private final MemoryProjects projects = new MemoryProjects();
    private final MemoryPhotos photos = new MemoryPhotos();
    private final RecordingStorage storage = new RecordingStorage();
    private final ProjectPhotoQueryService service = new ProjectPhotoQueryService(
            new ProjectAccessService(lookup), projects, photos, storage);

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void activeMemberSeesPhotosOrderedByTakenAt() {
        login(7L);
        lookup.projectExists = true;
        lookup.membership = member("active");
        photos.rows.add(photo(2L, "2026-11-27T01:00:00"));
        photos.rows.add(photo(1L, "2026-11-27T03:00:00"));

        Map<String, Object> body = service.list(12L, 1, 20);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        assertEquals(1L, items.get(0).get("id"));
        assertEquals("signed:cos://1", items.get(0).get("previewUrl"));
        assertEquals(2, body.get("total"));
        assertEquals(2, storage.signCalls);
    }

    @Test
    void removedMemberGetsNoSignedUrl() {
        login(7L);
        lookup.projectExists = true;
        lookup.membership = member("removed");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail(12L, 1L));
        assertEquals(403, ex.getStatus());
        assertEquals(ProjectAccessService.NOT_A_MEMBER, ex.getMessage());
        assertEquals(0, storage.signCalls);
    }

    @Test
    void photoFromAnotherProjectIsNotFound() {
        login(7L);
        lookup.projectExists = true;
        lookup.membership = member("active");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.original(12L, 99L));
        assertEquals(404, ex.getStatus());
        assertEquals(ProjectPhotoRules.PHOTO_NOT_FOUND, ex.getMessage());
        assertEquals(0, storage.signCalls);
    }

    @Test
    void originalSignsOnlyAfterMembership() {
        login(7L);
        lookup.projectExists = true;
        lookup.membership = member("active");
        photos.rows.add(photo(8L, "2026-11-27T03:00:00"));

        Map<String, Object> body = service.original(12L, 8L);

        assertEquals("signed:cos://8", body.get("url"));
        assertEquals(3600, body.get("expiresInSeconds"));
    }

    @Test
    void archivedActiveMemberCanListDetailAndDownloadOriginal() {
        login(7L);
        lookup.projectExists = true;
        lookup.membership = member("active");
        projects.status = "archived";
        photos.rows.add(photo(8L, "2026-11-27T03:00:00"));

        Map<String, Object> listed = service.list(12L, 1, 20);
        assertEquals(1, listed.get("total"));
        assertEquals("signed:cos://8", service.detail(12L, 8L).get("previewUrl"));
        assertEquals("signed:cos://8", service.original(12L, 8L).get("url"));

        photos.rows.get(0).setWatermarkVisible(0);
        byte[] downloaded = new ProjectPhotoDownloadService(service, storage).watermarked(12L, 8L);
        assertEquals(1, downloaded[0]);
        assertEquals(0, storage.storeCalls);
    }

    @Test
    void leftMemberOfArchivedProjectIsStillForbidden() {
        login(7L);
        lookup.projectExists = true;
        lookup.membership = member("left");
        projects.status = "archived";
        photos.rows.add(photo(8L, "2026-11-27T03:00:00"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.original(12L, 8L));
        assertEquals(403, ex.getStatus());
        assertEquals(ProjectAccessService.NOT_A_MEMBER, ex.getMessage());
        assertEquals(0, storage.signCalls);
    }

    private static ProjectAccessRecord member(String status) {
        ProjectAccessRecord record = new ProjectAccessRecord();
        record.setUserId(7L);
        record.setStatus(status);
        record.setRole("member");
        return record;
    }

    private static ProjectPhoto photo(long id, String takenAt) {
        ProjectPhoto photo = new ProjectPhoto();
        photo.setId(id);
        photo.setProjectId(12L);
        photo.setImageId(id);
        photo.setPhotographerUserId(7L);
        photo.setTakenAt(LocalDateTime.parse(takenAt));
        photo.setNote("");
        photo.setGpsStatus("empty");
        photo.setGeocodeStatus("skipped");
        photo.setWeatherStatus("skipped");
        photo.setWatermarkPosition("bottom_left");
        photo.setWatermarkVisible(1);
        photo.setStoredUrl("cos://" + id);
        return photo;
    }

    private static void login(long userId) {
        User user = new User();
        user.setId(userId);
        AuthContext.setUser(user);
    }

    private static final class MemoryLookup implements ProjectAccessLookup {
        private boolean projectExists;
        private ProjectAccessRecord membership;

        @Override
        public boolean projectExists(long projectId) {
            return projectExists;
        }

        @Override
        public ProjectAccessRecord findMembership(long projectId, long userId) {
            return membership;
        }
    }

    private static final class MemoryProjects implements ProjectMapper {
        private String status = "active";

        @Override
        public Project findById(long id) {
            Project project = new Project();
            project.setId(id);
            project.setStatus(status);
            return project;
        }

        @Override
        public int insert(Project project) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Project lockById(long id) {
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

    private static final class MemoryPhotos implements ProjectPhotoMapper {
        private final List<ProjectPhoto> rows = new ArrayList<>();

        @Override
        public int countByProjectId(long projectId) {
            return rows.size();
        }

        @Override
        public int countByImageId(long imageId) {
            return 0;
        }

        @Override
        public ProjectPhoto findByIdInProject(long projectId, long photoId) {
            return rows.stream().filter(row -> row.getId() == photoId && row.getProjectId() == projectId).findFirst().orElse(null);
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
            return rows.stream()
                    .sorted(Comparator.comparing(ProjectPhoto::getTakenAt).reversed()
                            .thenComparing(Comparator.comparing(ProjectPhoto::getId).reversed()))
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public int insert(ProjectPhoto photo) {
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

    private static final class RecordingStorage implements com.xgsheng.backend.service.StorageService {
        private int signCalls;
        private int storeCalls;

        @Override
        public String storeBytes(byte[] bytes, String originalFilename, String contentType, String category) {
            storeCalls++;
            return "";
        }

        @Override
        public String canonicalUrl(String url) {
            return url;
        }

        @Override
        public String readableUrl(String storedUrl) {
            signCalls++;
            return "signed:" + storedUrl;
        }

        @Override
        public int signedReadTtlSeconds() {
            return 3600;
        }

        @Override
        public byte[] readStoredBytes(String storedUrl) {
            return new byte[]{1};
        }
    }
}
