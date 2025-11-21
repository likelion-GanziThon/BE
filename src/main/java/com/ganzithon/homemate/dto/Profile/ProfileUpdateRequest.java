package com.ganzithon.homemate.dto.Profile;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ProfileUpdateRequest(
        @Size(max = 100, message = "희망 거주 지역은 100자 이하여야 합니다.")
        String desiredArea,
        
        LocalDate desiredMoveInDate,
        
        @Size(max = 500, message = "자기소개는 500자 이하여야 합니다.")
        String introduction
) {
}

