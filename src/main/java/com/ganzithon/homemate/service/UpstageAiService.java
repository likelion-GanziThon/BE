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
            // 이미 HousingInfoService에서 10개로 제한되었으므로 그대로 사용
            // 주거정보 데이터를 JSON 문자열로 변환 (최소화된 필드만 포함)
            String housingDataJson = objectMapper.writeValueAsString(housingDataList);

            // 시스템 프롬프트 생성 (최소화)
            String systemPrompt = "TOP5 추천. JSON: {\"recommendations\": [{\"rank\": 1, \"hsmpSn\": \"...\", \"reason\": \"...\"}]}";

            // 사용자 메시지 생성 (최소화)
            String userMessage = String.format("%s\n%s", userPrompt, housingDataJson);

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
            // reasoning_effort 제거 (없으면 더 빠름)
            requestBody.put("temperature", 0.0); // 0.0으로 최소화하여 가장 빠른 응답
            requestBody.put("max_tokens", 200); // 최대 토큰 수 더 감소 (30초 내외 목표)

            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // HTTP 요청 생성
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // 로그 최소화

            // API 호출
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response;
            try {
                response = restTemplate.postForEntity(apiUrl, request, (Class<Map<String, Object>>) (Class<?>) Map.class);
            } catch (org.springframework.web.client.ResourceAccessException e) {
                if (e.getCause() instanceof java.net.SocketTimeoutException) {
                    // 데이터를 더 줄여서 재시도 (5개로)
                    if (housingDataList.size() > 5) {
                        List<Map<String, Object>> retryDataList = housingDataList.subList(0, 5);
                        String retryDataJson = objectMapper.writeValueAsString(retryDataList);
                        String retryUserMessage = String.format("%s\n%s", userPrompt, retryDataJson);
                        requestBody.put("messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", retryUserMessage)
                        ));
                        response = restTemplate.postForEntity(apiUrl, request, (Class<Map<String, Object>>) (Class<?>) Map.class);
                    } else {
                        throw new RuntimeException("AI API 타임아웃", e);
                    }
                } else {
                    throw e;
                }
            }

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

                    // JSON 파싱
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsedResponse = objectMapper.readValue(content, Map.class);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> recommendations = (List<Map<String, Object>>) parsedResponse.get("recommendations");

                    if (recommendations != null && !recommendations.isEmpty()) {
                        // TOP5 추천 결과 추출 (순위와 이유만)
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

