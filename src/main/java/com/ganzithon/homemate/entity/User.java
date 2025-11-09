package com.ganzithon.homemate.entity;

import com.ganzithon.homemate.dto.LoginResponse;
import com.ganzithon.homemate.jwt.JwtTokenProvider;
import com.ganzithon.homemate.security.UserPrincipal;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private UserAccount account;

    protected User() {
    }

    private User(UserAccount account) {
        this.account = account;
    }

    public static User register(UserAccount account) {
        return new User(account);
    }

    public boolean hasId(Long targetId) {
        return id != null && id.equals(targetId);
    }

    public void verifyPassword(PasswordEncoder encoder, String rawPassword) {
        if (account.matchesPassword(encoder, rawPassword)) {
            return;
        }
        throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    public LoginResponse issueAccessToken(JwtTokenProvider tokenProvider) {
        String accessToken = createAccessToken(tokenProvider);
        return new LoginResponse(accessToken);
    }

    public String createAccessToken(JwtTokenProvider tokenProvider) {
        requirePersisted();
        return tokenProvider.createAccessToken(
                String.valueOf(id),
                Map.of("loginId", account.loginIdValue())
        );
    }

    public UserPrincipal toPrincipal() {
        requirePersisted();
        return UserPrincipal.create(id, account.loginIdValue(), account.encodedPassword());
    }

    private void requirePersisted() {
        if (id != null) {
            return;
        }
        throw new IllegalStateException("저장되지 않은 사용자입니다.");
    }
}
