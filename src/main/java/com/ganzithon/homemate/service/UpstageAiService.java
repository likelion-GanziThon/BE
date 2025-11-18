package com.ganzithon.homemate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpstageAiService {

    private final RestTemplate restTemplate;
    
    // ObjectMapper는 Spring Boot에서 자동으로 Bean으로 등록되므로 주입받거나 직접 생성
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${upstage.api.key:}")
    private String apiKey;

    @Value("${upstage.api.url:}")
    private String apiUrl;

    @Value("${upstage.api.model:}")
    private String model;

    //주거정보 데이터를 기반으로 사용자 프롬프트에 맞는 TOP5 추천을 반환합니다.
     
     //@param userPrompt 사용자가 입력한 프롬프트
     //@param housingDataList DB에서 조회한 주거정보 목록
     //@return 추천 결과 (hsmpSn과 reason 포함)

    public List<RecommendationResult> getRecommendations(String userPrompt, List<Map<String, Object>> housingDataList) {
        try {
            // 주거정보 데이터를 JSON 문자열로 변환
            String housingDataJson = objectMapper.writeValueAsString(housingDataList);

            // 시스템 프롬프트 생성
            String systemPrompt = """
                당신은 주거정보 추천 전문가입니다. 사용자의 요구사항을 분석하여 가장 적합한 주거정보를 추천해주세요.
                
                주거정보 데이터의 필드 설명:
                - hsmpSn: 단지 식별자
                - brtcNm: 광역시도명 (예: 서울특별시, 부산광역시)
                - signguNm: 시군구명 (예: 강남구, 서초구)
                - hsmpNm: 단지명
                - rnAdres: 도로명 주소
                - hshldCo: 세대수
                - suplyTyNm: 공급 유형 명 (예: 공공임대, 전세임대)
                - suplyCmnuseAr: 공급 공용 면적 (제곱미터)
                - houseTyNm: 주택 유형 명 (예: 아파트, 오피스텔)
                - bassRentGtn: 기본 임대보증금 (원)
                - bassMtRntchrg: 기본 월임대료 (원)
                - bassCnvrsGtnLmt: 기본 전환보증금 (원)
                
                사용자의 요구사항을 분석하여 가장 적합한 주거정보 TOP5를 추천해주세요.
                추천 결과는 JSON 형식으로 반환하며, 각 항목에는 추천 순위, 단지 식별자(hsmpSn), 추천 이유를 포함해야 합니다.
                """;

            // 사용자 메시지 생성
            String userMessage = String.format("""
                사용자 요구사항: %s
                
                다음 주거정보 데이터 중에서 사용자 요구사항에 가장 적합한 TOP5를 추천해주세요:
                %s
                
                응답 형식:
                {
                  "recommendations": [
                    {
                      "rank": 1,
                      "hsmpSn": "단지 식별자",
                      "reason": "추천 이유"
                    },
                    ...
                  ]
                }
                """, userPrompt, housingDataJson);

            // JSON 스키마 정의 (실제 스키마 구조)
            Map<String, Object> schemaDefinition = new HashMap<>();
            schemaDefinition.put("type", "object");
            schemaDefinition.put("properties", Map.of(
                "recommendations", Map.of(
                    "type", "array",
                    "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "rank", Map.of("type", "integer"),
                            "hsmpSn", Map.of("type", "string"),
                            "reason", Map.of("type", "string")
                        ),
                        "required", List.of("rank", "hsmpSn", "reason")
                    )
                )
            ));
            schemaDefinition.put("required", List.of("recommendations"));

            // json_schema 객체 (name과 schema 필드 포함)
            Map<String, Object> jsonSchema = new HashMap<>();
            jsonSchema.put("name", "recommendation_response"); // 스키마 이름 (필수)
            jsonSchema.put("schema", schemaDefinition); // 실제 스키마 정의 (필수)

            // 요청 바디 생성
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
            ));
            // response_format 구조: type, json_schema(name, schema)
            Map<String, Object> responseFormat = new HashMap<>();
            responseFormat.put("type", "json_schema");
            responseFormat.put("json_schema", jsonSchema);
            requestBody.put("response_format", responseFormat);
            requestBody.put("reasoning_effort", "high");
            requestBody.put("temperature", 0.3);

            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // HTTP 요청 생성
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            log.info("Upstage AI API 호출 시작: model={}, prompt length={}", model, userPrompt.length());

            // API 호출
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(apiUrl, request, (Class<Map<String, Object>>) (Class<?>) Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                // 응답 파싱
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> firstChoice = choices.get(0);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
                    String content = (String) message.get("content");

                    log.info("Upstage AI API 응답 수신: content length={}", content.length());

                    // JSON 파싱
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsedResponse = objectMapper.readValue(content, Map.class);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> recommendations = (List<Map<String, Object>>) parsedResponse.get("recommendations");

                    if (recommendations != null && !recommendations.isEmpty()) {
                        // TOP5 추천 결과 추출
                        return recommendations.stream()
                                .limit(5)
                                .map(rec -> new RecommendationResult(
                                        (String) rec.get("hsmpSn"),
                                        (String) rec.get("reason")
                                ))
                                .toList();
                    }
                }
            }

            log.warn("Upstage AI API 응답에서 추천 결과를 찾을 수 없습니다.");
            return List.of();

        } catch (Exception e) {
            log.error("Upstage AI API 호출 중 오류 발생", e);
            throw new RuntimeException("AI 추천 서비스 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    // 추천 결과를 담는 내부 클래스

    public static class RecommendationResult {
        private final String hsmpSn;
        private final String reason;

        public RecommendationResult(String hsmpSn, String reason) {
            this.hsmpSn = hsmpSn;
            this.reason = reason != null ? reason : "사용자 요구사항에 적합한 주거정보입니다.";
        }

        public String getHsmpSn() {
            return hsmpSn;
        }

        public String getReason() {
            return reason;
        }
    }
}

