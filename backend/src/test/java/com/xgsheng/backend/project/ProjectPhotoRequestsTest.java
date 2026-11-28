package com.xgsheng.backend.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xgsheng.backend.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectPhotoRequestsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createRejectsClientWeather() throws Exception {
        BusinessException ex = assertThrows(BusinessException.class, () -> ProjectPhotoRequests.parseCreate(
                mapper.readTree("{\"imageId\":1,\"weatherText\":\"晴\"}")));
        assertEquals(400, ex.getStatus());
        assertEquals(ProjectPhotoRequests.CLIENT_SNAPSHOT, ex.getMessage());
    }

    @Test
    void patchRejectsCoordinates() throws Exception {
        BusinessException ex = assertThrows(BusinessException.class, () -> ProjectPhotoRequests.parseUpdate(
                mapper.readTree("{\"note\":\"ok\",\"latitude\":1}")));
        assertEquals(400, ex.getStatus());
        assertEquals(ProjectPhotoRequests.FIELD_NOT_EDITABLE, ex.getMessage());
    }

    @Test
    void gpsWithoutCoordinatesIsInvalid() {
        CreateProjectPhotoRequest request = new CreateProjectPhotoRequest();
        request.setImageId(1L);
        request.setClientRequestId("req");
        request.setTakenAt(java.time.LocalDateTime.parse("2026-11-27T05:12:00"));
        request.setGpsStatus("ok");
        request.setWatermarkPosition("bottom_left");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ProjectPhotoRules.validateCreate(request));
        assertEquals(ProjectPhotoRules.GPS_INVALID, ex.getMessage());
    }
}
