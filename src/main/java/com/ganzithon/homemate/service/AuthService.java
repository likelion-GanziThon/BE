package com.ganzithon.homemate.service;

import com.ganzithon.homemate.dto.Login.LoginRequest;
import com.ganzithon.homemate.dto.Login.LoginResponse;
import com.ganzithon.homemate.dto.Login.RegisterRequest;
import com.ganzithon.homemate.entity.User;
import com.ganzithon.homemate.entity.UserAccount;
import com.ganzithon.homemate.jwt.JwtTokenProvider;
import com.ganzithon.homemate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByAccountLoginIdValue(request.id())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        UserAccount account = UserAccount.create(request.id(), passwordEncoder.encode(request.password()));
        userRepository.save(User.register(account));
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByAccountLoginIdValue(request.id())
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다."));
        user.verifyPassword(passwordEncoder, request.password());
        return user.issueAccessToken(jwtTokenProvider);
    }
}

