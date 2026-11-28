package com.xgsheng.backend.photo;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectPhotoMetadataConsistencyTest {

    @Test
    void photoSqlKeepsSnapshotsAndDoesNotStoreSignedUrlsOrPatchGps() throws Exception {
        String xml = new String(getClass().getResourceAsStream("/mapper/ProjectPhotoMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        String update = xml.substring(xml.indexOf("<update id=\"updateEditable\">"), xml.indexOf("</update>"));
        String set = update.substring(update.indexOf("SET"), update.indexOf("WHERE"));
        String insert = xml.substring(xml.indexOf("<insert"), xml.indexOf("</insert>"));

        assertTrue(xml.contains("project_name_snapshot"));
        assertTrue(xml.contains("photographer_user_id"));
        assertTrue(xml.contains("taken_at"));
        assertTrue(xml.contains("gps_status"));
        assertTrue(xml.contains("geocode_status"));
        assertTrue(xml.contains("location_text"));
        assertTrue(xml.contains("weather_text"));
        assertTrue(xml.contains("weather_observed_at"));
        assertTrue(xml.contains("weather_provider"));
        assertTrue(xml.contains("watermark_position"));
        assertTrue(xml.contains("watermark_visible"));
        assertFalse(insert.contains("local_url"));
        assertFalse(set.contains("latitude"));
        assertFalse(set.contains("gps_status"));
        assertFalse(set.contains("location_text"));
        assertFalse(set.contains("weather_"));
        assertFalse(set.contains("project_id"));
        assertFalse(set.contains("photographer_user_id"));
    }
}
