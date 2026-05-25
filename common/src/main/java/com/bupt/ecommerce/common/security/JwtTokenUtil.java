package com.bupt.ecommerce.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

public final class JwtTokenUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private JwtTokenUtil() {
    }

    public static String generateToken(Long userId, String username, Role role, Duration ttl, String secret) {
        Instant now = Instant.now();
        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
        );
        Map<String, Object> payload = Map.of(
                "userId", userId,
                "username", username,
                "role", role.name(),
                "iat", now.getEpochSecond(),
                "exp", now.plus(ttl).getEpochSecond()
        );

        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(payload);
        String signingInput = encodedHeader + "." + encodedPayload;
        return signingInput + "." + sign(signingInput, secret);
    }

    public static RequestUser parseToken(String token, String secret) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token is empty");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("token format is invalid");
        }

        String signingInput = parts[0] + "." + parts[1];
        String expectedSignature = sign(signingInput, secret);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("token signature is invalid");
        }

        try {
            JsonNode payload = OBJECT_MAPPER.readTree(URL_DECODER.decode(parts[1]));
            long exp = payload.path("exp").asLong(0);
            if (exp <= Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("token is expired");
            }

            Long userId = payload.path("userId").asLong();
            String username = payload.path("username").asText();
            Role role = Role.valueOf(payload.path("role").asText());
            return new RequestUser(userId, username, role);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("token payload is invalid", ex);
        }
    }

    public static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(AuthHeaders.BEARER_PREFIX)) {
            return null;
        }
        String token = authorizationHeader.substring(AuthHeaders.BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static String encodeJson(Map<String, Object> value) {
        try {
            return URL_ENCODER.encodeToString(OBJECT_MAPPER.writeValueAsBytes(value));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to encode jwt json", ex);
        }
    }

    private static String sign(String signingInput, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] signature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            return URL_ENCODER.encodeToString(signature);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to sign jwt", ex);
        }
    }
}
