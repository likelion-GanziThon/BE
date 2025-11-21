package com.ganzithon.homemate.controller;

import com.ganzithon.homemate.dto.Login.LoginRequest;
import com.ganzithon.homemate.dto.Login.LoginResponse;
import com.ganzithon.homemate.dto.Login.RegisterRequest;
import com.ganzithon.homemate.dto.MessageResponse;
import com.ganzithon.homemate.dto.TokenResponse;
import com.ganzithon.homemate.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponse("회원가입이 완료되었습니다."));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse token = authService.login(request);
        return ResponseEntity.ok(new TokenResponse("로그인이 완료되었습니다.", token.accessToken()));
    }
}

