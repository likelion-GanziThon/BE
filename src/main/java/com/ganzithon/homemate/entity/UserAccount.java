package com.ganzithon.homemate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

@Embeddable
public class UserAccount {

    @Column(name = "login_id", nullable = false, unique = true, length = 60)
    private String loginIdValue;

    @Column(name = "password", nullable = false, length = 255)
    private String encodedPassword;

    protected UserAccount() {
    }

    private UserAccount(String loginIdValue, String encodedPassword) {
        validateLoginId(loginIdValue);
        validatePassword(encodedPassword);
        this.loginIdValue = loginIdValue;
        this.encodedPassword = encodedPassword;
    }

    public static UserAccount create(String loginIdValue, String encodedPassword) {
        return new UserAccount(loginIdValue, encodedPassword);
    }

    public boolean matchesPassword(PasswordEncoder encoder, String rawPassword) {
        if (StringUtils.hasText(rawPassword) && encoder.matches(rawPassword, encodedPassword)) {
            return true;
        }
        return false;
    }

    public String loginIdValue() {
        return loginIdValue;
    }

    public String encodedPassword() {
        return encodedPassword;
    }

    private void validateLoginId(String loginId) {
        if (StringUtils.hasText(loginId)) {
            return;
        }
        throw new IllegalArgumentException("아이디는 필수 값입니다.");
    }

    private void validatePassword(String encodedPassword) {
        if (StringUtils.hasText(encodedPassword)) {
            return;
        }
        throw new IllegalArgumentException("비밀번호는 필수 값입니다.");
    }
}

