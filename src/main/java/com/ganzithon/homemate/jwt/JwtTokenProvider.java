package com.ganzithon.homemate.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Date;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties props;

    private SecretKey accessKey;

    @jakarta.annotation.PostConstruct
    void init() {
        this.accessKey = createKey(props.getAccessTokenSecret());
    }

    // === 핵심: 어떤 형식이 와도 안전하게 SecretKey 생성 ===
    private SecretKey createKey(String value) {
        byte[] keyBytes;

        if (isBase64(value)) {
            keyBytes = Decoders.BASE64.decode(value);
        } else if (isBase64Url(value)) {
            keyBytes = Decoders.BASE64URL.decode(padBase64Url(value));
        } else {
            // 사람이 읽기 좋은 평문 비밀키 지원 (권장: 충분히 길게)
            keyBytes = value.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32) { // HS256 최소 256-bit 권장
            throw new IllegalArgumentException(
                    "JWT secret key는 32 bytes 이내여야 합니다. current=" + keyBytes.length);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private boolean isBase64(String s) {
        // 표준 Base64: + / =
        return s.matches("^[A-Za-z0-9+/]+={0,2}$");
    }

    private boolean isBase64Url(String s) {
        // URL-safe: - _ (= optional)
        return s.matches("^[A-Za-z0-9_-]+={0,2}$");
    }

    private String padBase64Url(String s) {
        int mod = s.length() % 4;
        if (mod == 2) return s + "==";
        if (mod == 3) return s + "=";
        if (mod == 1) throw new IllegalArgumentException("Invalid Base64URL length");
        return s;
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
