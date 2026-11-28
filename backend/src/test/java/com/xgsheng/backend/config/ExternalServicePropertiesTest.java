package com.xgsheng.backend.config;

import com.xgsheng.backend.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalServicePropertiesTest {

    @Test
    void blankValuesAreUnconfigured() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        assertUnconfigured(properties);

        properties.setTencentLbsKey("  ");
        properties.setQweatherJwt("");
        properties.setTencentSpeechSecretId(null);
        properties.setTencentSpeechSecretKey(" ");
        assertUnconfigured(properties);
    }

    @Test
    void speechRequiresBothSecrets() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.setTencentSpeechSecretId("secret-id");
        assertFalse(properties.isSpeechConfigured());
        properties.setTencentSpeechSecretKey("secret-key");
        assertTrue(properties.isSpeechConfigured());
        assertFalse(properties.status().toString().contains("secret-id"));
        assertFalse(properties.status().toString().contains("secret-key"));
        assertFalse(properties.toString().contains("secret-id"));
        assertFalse(properties.toString().contains("secret-key"));
    }

    @Test
    void unconfiguredCallFailsWithoutInventingData() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.setTencentLbsKey("lbs-secret");
        properties.setQweatherJwt("weather-secret");
        properties.setTencentSpeechSecretId("speech-id");
        properties.setTencentSpeechSecretKey("speech-key");

        ExternalServiceProperties empty = new ExternalServiceProperties();
        BusinessException lbs = assertThrows(BusinessException.class, empty::requireLbsConfigured);
        BusinessException weather = assertThrows(BusinessException.class, empty::requireWeatherConfigured);
        BusinessException speech = assertThrows(BusinessException.class, empty::requireSpeechConfigured);
        assertEquals(503, lbs.getStatus());
        assertEquals("tencent lbs is not configured", lbs.getMessage());
        assertEquals("qweather is not configured", weather.getMessage());
        assertEquals("tencent speech is not configured", speech.getMessage());
        assertFalse(lbs.getMessage().contains("lbs-secret"));
        assertFalse(weather.getMessage().contains("weather-secret"));
        assertFalse(speech.getMessage().contains("speech-key"));

        properties.requireLbsConfigured();
        properties.requireWeatherConfigured();
        properties.requireSpeechConfigured();
        assertFalse(properties.toString().contains("lbs-secret"));
        assertFalse(properties.toString().contains("weather-secret"));
        assertFalse(properties.toString().contains("speech-id"));
        assertFalse(properties.toString().contains("speech-key"));
    }

    @Test
    void applicationYamlBindsTheFourEnvironmentVariablesAsEmptyDefaults() throws Exception {
        String yaml;
        try (InputStream in = ExternalServiceProperties.class.getClassLoader().getResourceAsStream("application.yaml")) {
            yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(yaml.contains("tencent-lbs-key: ${TENCENT_LBS_KEY:}"));
        assertTrue(yaml.contains("qweather-jwt: ${QWEATHER_JWT:}"));
        assertTrue(yaml.contains("qweather-api-host: ${QWEATHER_API_HOST:devapi.qweather.com}"));
        assertTrue(yaml.contains("qweather-private-key: ${QWEATHER_PRIVATE_KEY:}"));
        assertTrue(yaml.contains("qweather-private-key-path: ${QWEATHER_PRIVATE_KEY_PATH:}"));
        assertTrue(yaml.contains("qweather-kid: ${QWEATHER_KID:}"));
        assertTrue(yaml.contains("qweather-dev-id: ${QWEATHER_DEV_ID:}"));
        assertTrue(yaml.contains("qweather-project-id: ${QWEATHER_PROJECT_ID:}"));
        assertTrue(yaml.contains("tencent-speech-secret-id: ${TENCENT_SPEECH_SECRET_ID:}"));
        assertTrue(yaml.contains("tencent-speech-secret-key: ${TENCENT_SPEECH_SECRET_KEY:}"));
    }

    @Test
    void weatherConfiguredByPrivateKeySigning() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        assertFalse(properties.isWeatherConfigured());
        properties.setQweatherPrivateKeyPath("C:/secrets/ed25519.pem");
        properties.setQweatherKid("kid");
        properties.setQweatherDevId("Q123456789");
        properties.setQweatherProjectId("P123456789");
        assertTrue(properties.isQweatherJwtSigningConfigured());
        assertTrue(properties.isWeatherConfigured());
    }

    private static void assertUnconfigured(ExternalServiceProperties properties) {
        ExternalServiceStatus status = properties.status();
        assertFalse(status.isLbsConfigured());
        assertFalse(status.isWeatherConfigured());
        assertFalse(status.isSpeechConfigured());
    }
}
