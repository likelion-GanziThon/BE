package com.ganzithon.homemate.dto;

import java.time.LocalDate;

public record ProfileResponse(
        Long id,
        String nickname,
        String desiredArea,
        LocalDate desiredMoveInDate,
        String introduction,
        String profileImageUrl
) {
    public static ProfileResponse of(Long id, String nickname, String desiredArea, 
                                    LocalDate desiredMoveInDate, String introduction, String profileImageUrl) {
        return new ProfileResponse(id, nickname, desiredArea, desiredMoveInDate, introduction, profileImageUrl);
    }
}

