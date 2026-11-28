package com.xgsheng.backend.service;

import com.xgsheng.backend.access.ProjectAccessLookup;
import com.xgsheng.backend.access.ProjectAccessRecord;
import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.entity.Image;
import com.xgsheng.backend.entity.Project;
import com.xgsheng.backend.entity.ProjectMemberRow;
import com.xgsheng.backend.entity.ProjectPhoto;
import com.xgsheng.backend.entity.User;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.mapper.ImageMapper;
import com.xgsheng.backend.mapper.ProjectMapper;
import com.xgsheng.backend.mapper.ProjectMemberMapper;
import com.xgsheng.backend.mapper.ProjectPhotoMapper;
import com.xgsheng.backend.photo.GeocodeClient;
import com.xgsheng.backend.photo.GeocodeResult;
import com.xgsheng.backend.photo.WeatherClient;
import com.xgsheng.backend.photo.WeatherResult;
import com.xgsheng.backend.project.CreateProjectPhotoRequest;
import com.xgsheng.backend.project.ProjectPhotoRules;
import com.xgsheng.backend.project.UpdateProjectPhotoRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectPhotoCommandServiceTest {

    private final MemoryLookup lookup = new MemoryLookup();
    private final MemoryProjects projects = new MemoryProjects();
    private final MemoryMembers members = new MemoryMembers();
    private final MemoryPhotos photos = new MemoryPhotos();
    private final MemoryImages images = new MemoryImages();
    private final CountingGeocode geocode = new CountingGeocode();
    private final CountingWeather weather = new CountingWeather();
    private final ProjectPhotoWriter writer = new ProjectPhotoWriter(projects, members, photos, images);
    private final ProjectPhotoCommandService service = new ProjectPhotoCommandService(
            new ProjectAccessService(lookup),
            projects,
            photos,
            images,
            writer,
            geocode,
            weather,
            Clock.fixed(Instant.parse("2026-11-27T05:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void createStoresServerSnapshotAndDoesNotKeepSignedUrl() {
        login(7L);
        activeMember(12L, 7L);
        images.add(501L, 7L, "cos://original");

        Map<String, Object> body = service.create(12L, request(501L, "req-1"));

        assertEquals(Boolean.TRUE, body.get("created"));
        assertEquals(501L, body.get("imageId"));
        assertEquals("ok", body.get("geocodeStatus"));
        assertEquals("南山区", body.get("locationText"));
        assertEquals("ok", body.get("weatherStatus"));
        assertEquals("qweather", body.get("weatherProvider"));
        assertEquals("2026-11-27T05:10:00Z", body.get("weatherObservedAt"));
        assertEquals("装修工程", body.get("projectNameSnapshot"));
        assertFalse(body.containsKey("previewUrl"));
        assertFalse(body.containsKey("localUrl"));
        assertEquals(1, geocode.calls);
        assertEquals(1, weather.calls);
    }

    @Test
    void deniedGpsSkipsExternalCallsAndKeepsNullPlace() {
        login(7L);
        activeMember(12L, 7L);
        images.add(501L, 7L, "cos://original");
        CreateProjectPhotoRequest request = request(501L, "req-denied");
        request.setGpsStatus("denied");
        request.setLatitude(null);
        request.setLongitude(null);
        request.setCoordinateSystem(null);

        Map<String, Object> body = service.create(12L, request);

        assertEquals("skipped", body.get("geocodeStatus"));
        assertEquals("skipped", body.get("weatherStatus"));
        assertNull(body.get("locationText"));
        assertNull(body.get("latitude"));
        assertEquals(0, geocode.calls);
        assertEquals(0, weather.calls);
    }

    @Test
    void geocodeFailureKeepsCoordinates() {
        login(7L);
        activeMember(12L, 7L);
        images.add(501L, 7L, "cos://original");
        geocode.result = new GeocodeResult(null);

        Map<String, Object> body = service.create(12L, request(501L, "req-geo"));

        assertEquals("failed", body.get("geocodeStatus"));
        assertNull(body.get("locationText"));
        assertEquals(new BigDecimal("22.543096"), body.get("latitude"));
        assertEquals("ok", body.get("weatherStatus"));
    }

    @Test
    void sameRequestDoesNotOverwriteOrCallExternalAgain() {
        login(7L);
        activeMember(12L, 7L);
        images.add(501L, 7L, "cos://original");
        service.create(12L, request(501L, "req-1"));
        CreateProjectPhotoRequest again = request(501L, "req-1");
        again.setNote("第二次内容");
        again.setImageId(501L);

        Map<String, Object> body = service.create(12L, again);

        assertEquals(Boolean.FALSE, body.get("created"));
        assertEquals("", body.get("note"));
        assertEquals(1, photos.rows.size());
        assertEquals(1, geocode.calls);
    }

    @Test
    void sameImageInAnotherProjectConflicts() {
        login(7L);
        activeMember(12L, 7L);
        activeMember(13L, 7L);
        images.add(501L, 7L, "cos://original");
        service.create(12L, request(501L, "req-1"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(13L, request(501L, "req-2")));
        assertEquals(409, ex.getStatus());
        assertEquals(ProjectPhotoRules.IMAGE_USED, ex.getMessage());
        assertEquals(1, photos.rows.size());
    }

    @Test
    void imageOwnedBySomeoneElseIsRejectedBeforeInsert() {
        login(7L);
        activeMember(12L, 7L);
        images.add(501L, 8L, "cos://other");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(12L, request(501L, "req-1")));
        assertEquals(400, ex.getStatus());
        assertEquals(ProjectPhotoRules.IMAGE_NOT_FOUND, ex.getMessage());
        assertTrue(photos.rows.isEmpty());
        assertEquals(0, geocode.calls);
    }

    @Test
    void leftMemberCannotCreate() {
        login(7L);
        lookup.projectExists = true;
        lookup.membership = membership(12L, 7L, "member", "left");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(12L, request(501L, "req-1")));
        assertEquals(403, ex.getStatus());
        assertTrue(photos.rows.isEmpty());
        assertEquals(0, geocode.calls);
    }

    @Test
    void duplicateImageKeyInAnotherProjectRollsBackToConflict() {
        login(7L);
        activeMember(12L, 7L);
        images.add(501L, 7L, "cos://original");
        photos.failImageKey = true;
        ProjectPhoto other = new ProjectPhoto();
        other.setId(3L);
        other.setProjectId(99L);
        other.setImageId(501L);
        photos.hiddenByImage = other;

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(12L, request(501L, "req-1")));
        assertEquals(409, ex.getStatus());
        assertTrue(photos.rows.isEmpty());
    }

    @Test
    void insertFailureDoesNotLeaveARow() {
        login(7L);
        activeMember(12L, 7L);
        images.add(501L, 7L, "cos://original");
        photos.failInsert = true;

        assertThrows(RuntimeException.class, () -> service.create(12L, request(501L, "req-1")));
        assertTrue(photos.rows.isEmpty());
    }

    @Test
    void onlyPhotographerCanEdit() {
        login(8L);
        activeMember(12L, 8L);
        ProjectPhoto photo = stored(12L, 7L);
        photos.rows.add(photo);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(12L, 80L, noteUpdate("改了")));
        assertEquals(403, ex.getStatus());
        assertEquals(ProjectPhotoRules.ONLY_PHOTOGRAPHER, ex.getMessage());
        assertEquals("", photo.getNote());
    }

    @Test
    void photographerUpdatesOnlyEditableFields() {
        login(7L);
        activeMember(12L, 7L);
        photos.rows.add(stored(12L, 7L));

        Map<String, Object> body = service.update(12L, 80L, noteUpdate("基坑验收"));

        assertEquals("基坑验收", body.get("note"));
        assertEquals("bottom_left", body.get("watermarkPosition"));
        assertEquals("南山区", body.get("locationText"));
        assertEquals(0, geocode.calls);
    }

    @Test
    void archivedProjectRejectsCreate() {
        login(7L);
        activeMember(12L, 7L);
        projects.project.setStatus("archived");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(12L, request(501L, "req-1")));
        assertEquals(400, ex.getStatus());
        assertEquals(ProjectPhotoRules.ARCHIVED_READ_ONLY, ex.getMessage());
        assertTrue(photos.rows.isEmpty());
    }

    @Test
    void archivedProjectRejectsMetadataUpdate() {
        login(7L);
        activeMember(12L, 7L);
        projects.project.setStatus("archived");
        photos.rows.add(stored(12L, 7L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(12L, 80L, noteUpdate("不能改")));
        assertEquals(400, ex.getStatus());
        assertEquals(ProjectPhotoRules.ARCHIVED_READ_ONLY, ex.getMessage());
        assertEquals("", photos.rows.get(0).getNote());
    }

    private static CreateProjectPhotoRequest request(long imageId, String requestId) {
        CreateProjectPhotoRequest request = new CreateProjectPhotoRequest();
        request.setImageId(imageId);
        request.setClientRequestId(requestId);
        request.setTakenAt(LocalDateTime.of(2026, 11, 27, 5, 12));
        request.setTimezone("Asia/Shanghai");
        request.setNote("");
        request.setLatitude(new BigDecimal("22.543096"));
        request.setLongitude(new BigDecimal("114.057865"));
        request.setCoordinateSystem("gcj02");
        request.setGpsStatus("ok");
        request.setWatermarkPosition("bottom_left");
        request.setWatermarkVisible(true);
        return request;
    }

    private static UpdateProjectPhotoRequest noteUpdate(String note) {
        UpdateProjectPhotoRequest request = new UpdateProjectPhotoRequest();
        request.setNotePresent(true);
        request.setNote(note);
        return request;
    }

    private static ProjectPhoto stored(long projectId, long photographer) {
        ProjectPhoto photo = new ProjectPhoto();
        photo.setId(80L);
        photo.setProjectId(projectId);
        photo.setImageId(501L);
        photo.setPhotographerUserId(photographer);
        photo.setNote("");
        photo.setLocationText("南山区");
        photo.setGpsStatus("ok");
        photo.setGeocodeStatus("ok");
        photo.setWeatherStatus("ok");
        photo.setWatermarkPosition("bottom_left");
        photo.setWatermarkVisible(1);
        photo.setProjectNameSnapshot("装修工程");
        return photo;
    }

    private void activeMember(long projectId, long userId) {
        lookup.projectExists = true;
        lookup.membership = membership(projectId, userId, "member", "active");
        members.row = new ProjectMemberRow();
        members.row.setUserId(userId);
        members.row.setStatus("active");
        members.row.setRole("member");
    }

    private static ProjectAccessRecord membership(long projectId, long userId, String role, String status) {
        ProjectAccessRecord record = new ProjectAccessRecord();
        record.setOwnerUserId(1L);
        record.setUserId(userId);
        record.setRole(role);
        record.setStatus(status);
        return record;
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
        private final Project project = project();

        @Override
        public Project findById(long id) {
            return project;
        }

        @Override
        public Project lockById(long id) {
            return project;
        }

        @Override
        public int insert(Project project) {
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

        private static Project project() {
            Project project = new Project();
            project.setId(12L);
            project.setName("装修工程");
            project.setStatus("active");
            project.setOwnerUserId(7L);
            return project;
        }
    }

    private static final class MemoryMembers implements ProjectMemberMapper {
        private ProjectMemberRow row;

        @Override
        public ProjectMemberRow lockByUserId(long projectId, long userId) {
            return row;
        }

        @Override
        public int countProject(long projectId) {
            return 1;
        }

        @Override
        public ProjectAccessRecord findMembership(long projectId, long userId) {
            return null;
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

    private static final class MemoryPhotos implements ProjectPhotoMapper {
        private final List<ProjectPhoto> rows = new ArrayList<>();
        private boolean failInsert;
        private boolean failImageKey;
        private boolean conflictVisible;
        private ProjectPhoto hiddenByImage;

        @Override
        public int countByProjectId(long projectId) {
            return rows.size();
        }

        @Override
        public int countByImageId(long imageId) {
            return (int) rows.stream().filter(row -> imageId == row.getImageId()).count();
        }

        @Override
        public ProjectPhoto findByIdInProject(long projectId, long photoId) {
            return rows.stream()
                    .filter(row -> row.getId() == photoId && row.getProjectId() == projectId)
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public ProjectPhoto findByImageId(long imageId) {
            ProjectPhoto visible = rows.stream().filter(row -> imageId == row.getImageId()).findFirst().orElse(null);
            if (visible != null) {
                return visible;
            }
            return conflictVisible ? hiddenByImage : null;
        }

        @Override
        public ProjectPhoto findByPhotographerAndRequest(long photographerUserId, String clientRequestId) {
            return rows.stream()
                    .filter(row -> photographerUserId == row.getPhotographerUserId()
                            && clientRequestId.equals(row.getClientRequestId()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<ProjectPhoto> listByProject(long projectId, int limit, long offset) {
            return List.of();
        }

        @Override
        public int insert(ProjectPhoto photo) {
            if (failInsert) {
                throw new RuntimeException("insert failed");
            }
            if (failImageKey) {
                conflictVisible = true;
                throw new DuplicateKeyException("uk_project_photo_image");
            }
            photo.setId(80L);
            rows.add(photo);
            return 1;
        }

        @Override
        public int updateEditable(long id, long projectId, long photographerUserId, String note,
                                  String watermarkPosition, int watermarkVisible, LocalDateTime updatedAt) {
            ProjectPhoto photo = findByIdInProject(projectId, id);
            if (photo == null || photo.getPhotographerUserId() != photographerUserId) {
                return 0;
            }
            photo.setNote(note);
            photo.setWatermarkPosition(watermarkPosition);
            photo.setWatermarkVisible(watermarkVisible);
            return 1;
        }

        @Override
        public int deleteByIdInProject(long projectId, long photoId) {
            return rows.removeIf(row -> row.getId() == photoId && row.getProjectId() == projectId) ? 1 : 0;
        }
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
        private final List<Image> rows = new ArrayList<>();

        private void add(long id, long userId, String stored) {
            Image image = new Image();
            image.setId(id);
            image.setUserId(userId);
            image.setLocalUrl(stored);
            rows.add(image);
        }

        @Override
        public Image lockByOwner(Long userId, Long id) {
            return rows.stream()
                    .filter(row -> row.getId().equals(id) && row.getUserId().equals(userId))
                    .findFirst()
                    .orElse(null);
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
            return lockByOwner(userId, id);
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
        public void deleteImageTagByImageId(Long imageId) {
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
        public void deleteById(Long userId, Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateCreateTime(Long userId, Long id, java.util.Date createTime) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CountingGeocode implements GeocodeClient {
        private int calls;
        private GeocodeResult result = new GeocodeResult("南山区");

        @Override
        public GeocodeResult reverse(BigDecimal latitude, BigDecimal longitude) {
            calls++;
            return result;
        }
    }

    private static final class CountingWeather implements WeatherClient {
        private int calls;

        @Override
        public WeatherResult current(BigDecimal latitude, BigDecimal longitude) {
            calls++;
            return new WeatherResult("ok", "多云", new BigDecimal("26.5"), new BigDecimal("72"),
                    "101", LocalDateTime.of(2026, 11, 27, 5, 10));
        }
    }
}
