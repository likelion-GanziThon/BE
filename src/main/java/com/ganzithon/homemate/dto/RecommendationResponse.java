package com.ganzithon.homemate.dto;

import java.util.List;

public record RecommendationResponse(
    List<HousingRecommendation> recommendations
) {
    public record HousingRecommendation(
        Integer rank,
        HousingInfoDto housingInfo,
        String reason
    ) {
    }
    
    public record HousingInfoDto(
        Long id,
        String hsmpSn,
        String brtcNm,
        String signguNm,
        String hsmpNm,
        Integer hshldCo,
        Long bassRentGtn,
        Long bassMtRntchrg
    ) {
    }
}

