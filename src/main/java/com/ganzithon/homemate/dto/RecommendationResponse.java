package com.ganzithon.homemate.dto;

import com.ganzithon.homemate.entity.HousingInfo;
import java.util.List;

public record RecommendationResponse(
    List<HousingRecommendation> recommendations
) {
    public record HousingRecommendation(
        Integer rank,
        HousingInfo housingInfo,
        String reason
    ) {
    }
}

