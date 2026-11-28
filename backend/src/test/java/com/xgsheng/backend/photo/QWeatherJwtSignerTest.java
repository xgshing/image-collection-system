package com.xgsheng.backend.photo;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QWeatherJwtSignerTest {

    @Test
    void signsThreePartJwtWithEd25519() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair pair = generator.generateKeyPair();
        String pem = toPkcs8Pem(pair.getPrivate());

        long iat = 1_700_000_000L;
        long exp = iat + 900;
        String jwt = QWeatherJwtSigner.sign("KID123", "QDEV123456", "PROJ123456", pem, iat, exp);

        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length);
        assertTrue(parts[0].length() > 10);
        assertTrue(parts[1].length() > 10);
        assertTrue(parts[2].length() > 10);
    }

    private static String toPkcs8Pem(PrivateKey privateKey) {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
    }
}
