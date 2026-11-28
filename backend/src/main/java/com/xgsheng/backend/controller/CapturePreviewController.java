package com.xgsheng.backend.controller;

import com.xgsheng.backend.auth.AuthContext;
import com.xgsheng.backend.photo.ExifMetadataReader;
import com.xgsheng.backend.service.LocationPreviewService;
import com.xgsheng.backend.service.SpeechRecognitionService;
import com.xgsheng.backend.service.WeatherPreviewService;
import com.xgsheng.backend.util.JsonBodies;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 拍照预览用的地点、天气和语音。三个接口都不写项目照片。
 */
@RestController
public class CapturePreviewController {

    private final LocationPreviewService locationPreviewService;
    private final WeatherPreviewService weatherPreviewService;
    private final SpeechRecognitionService speechRecognitionService;

    public CapturePreviewController(LocationPreviewService locationPreviewService,
                                    WeatherPreviewService weatherPreviewService,
                                    SpeechRecognitionService speechRecognitionService) {
        this.locationPreviewService = locationPreviewService;
        this.weatherPreviewService = weatherPreviewService;
        this.speechRecognitionService = speechRecognitionService;
    }

    @PostMapping("/api/location/reverse")
    public Map<String, Object> reverse(@RequestBody Map<String, Object> body) {
        return locationPreviewService.reverse(JsonBodies.of(body));
    }

    @PostMapping("/api/weather/current")
    public Map<String, Object> weather(@RequestBody Map<String, Object> body) {
        return weatherPreviewService.current(JsonBodies.of(body));
    }

    @PostMapping(value = "/api/speech/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> recognize(@RequestParam("file") MultipartFile file) {
        return speechRecognitionService.recognize(file);
    }

    @PostMapping(value = "/api/photo/exif", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> exif(@RequestParam("file") MultipartFile file) {
        AuthContext.requireUserId();
        String takenAt = null;
        if (file != null && !file.isEmpty()) {
            try {
                takenAt = ExifMetadataReader.readTakenAtIso(file.getBytes());
            } catch (IOException ignored) {
                takenAt = null;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("takenAt", takenAt == null ? "" : takenAt);
        result.put("hasExif", takenAt != null);
        return result;
    }
}
