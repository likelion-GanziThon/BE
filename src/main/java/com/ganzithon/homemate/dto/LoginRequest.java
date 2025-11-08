package com.ganzithon.homemate.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "아이디는 필수 값입니다.") String id,
        @NotBlank(message = "비밀번호는 필수 값입니다.") String password
) {}

