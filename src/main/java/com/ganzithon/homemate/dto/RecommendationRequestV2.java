package com.ganzithon.homemate.dto;

import java.util.List;

public record RecommendationRequestV2(
    String sido,           // 광역시/도 (예: "서울", "경기", "부산")
    List<String> districts, // 시/군/구 목록 (예: ["서구", "강서구"])
    String prompt          // 사용자 프롬프트
) {
}

