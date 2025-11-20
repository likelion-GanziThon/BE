package com.ganzithon.homemate.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties props;

    private SecretKey accessKey;

    @jakarta.annotation.PostConstruct
    void init() {
        this.accessKey = createKey(props.getAccessTokenSecret());
    }

    /**
     * 어떤 문자열이 오든지:
     * 1) Base64 디코딩 시도
     * 2) Base64URL 디코딩 시도
     * 3) 둘 다 실패하면 평문을 UTF-8 바이트로 사용
     */
    private SecretKey createKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("JWT secret key 가 설정되지 않았습니다.");
        }

        String value = raw.trim();
        byte[] keyBytes = tryDecodeBase64(value);

        if (keyBytes == null) {
            keyBytes = tryDecodeBase64Url(value);
        }

        if (keyBytes == null) {
            // 사람이 읽기 좋은 평문 비밀키
            keyBytes = value.getBytes(StandardCharsets.UTF_8);
        }

        // HS256 권장: 최소 32 bytes (256bit)
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret key는 최소 32 bytes(256bit) 이상이어야 합니다. current=" + keyBytes.length
            );
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    private byte[] tryDecodeBase64(String s) {
        try {
            return Decoders.BASE64.decode(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private byte[] tryDecodeBase64Url(String s) {
        try {
            return Decoders.BASE64URL.decode(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String createAccessToken(String subject, Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(new Date(now))
                .expiration(new Date(now + props.getAccessTokenExpiration()))
                .signWith(accessKey)
                .compact();
    }

    public Claims parseAccess(String token) {
        return Jwts.parser()
                .verifyWith(accessKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
