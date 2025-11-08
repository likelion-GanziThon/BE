package com.ganzithon.homemate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "아이디는 필수 값입니다.")
        @Size(min = 4, max = 30, message = "아이디는 4자 이상 30자 이하이어야 합니다.")
        String id,
        @NotBlank(message = "비밀번호는 필수 값입니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        String password
) {}

