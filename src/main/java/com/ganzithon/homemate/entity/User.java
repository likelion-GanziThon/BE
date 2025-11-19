package com.ganzithon.homemate.entity;

import com.ganzithon.homemate.dto.LoginResponse;
import com.ganzithon.homemate.jwt.JwtTokenProvider;
import com.ganzithon.homemate.security.UserPrincipal;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
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

    @Column(name = "desired_area", length = 100)
    private String desiredArea;

    @Column(name = "desired_move_in_date")
    private LocalDate desiredMoveInDate;

    @Column(name = "introduction", length = 500)
    private String introduction;

    @Column(name = "profile_image_path", length = 500)
    private String profileImagePath;

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

    public void updateProfile(String desiredArea, LocalDate desiredMoveInDate, String introduction, String profileImagePath) {
        this.desiredArea = desiredArea;
        this.desiredMoveInDate = desiredMoveInDate;
        this.introduction = introduction;
        if (profileImagePath != null && !profileImagePath.isEmpty()) {
            this.profileImagePath = profileImagePath;
        }
    }

    public void updateProfileImagePath(String profileImagePath) {
        this.profileImagePath = profileImagePath;
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

    public Long getId() {
        return id;
    }

    public String getLoginId() {
        return account.loginIdValue();
    }

    public String getDesiredArea() {
        return desiredArea;
    }

    public LocalDate getDesiredMoveInDate() {
        return desiredMoveInDate;
    }

    public String getIntroduction() {
        return introduction;
    }

    public String getProfileImagePath() {
        return profileImagePath;
    }

    private void requirePersisted() {
        if (id != null) {
            return;
        }
        throw new IllegalStateException("저장되지 않은 사용자입니다.");
    }
}
