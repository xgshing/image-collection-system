package com.xgsheng.backend.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Spring Boot 4 / Jackson 3 下直接绑定 JsonNode 会失败，先收 Map 再转树。
 */
public final class JsonBodies {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonBodies() {
    }

    public static JsonNode of(Map<String, ?> body) {
        return MAPPER.valueToTree(body == null ? Map.of() : body);
    }
}
