package com.ganzithon.homemate.controller;

import com.ganzithon.homemate.dto.MessageResponse;
import com.ganzithon.homemate.dto.RecommendationRequest;
import com.ganzithon.homemate.dto.RecommendationRequestV2;
import com.ganzithon.homemate.dto.RecommendationResponse;
import com.ganzithon.homemate.entity.HousingInfo;
import com.ganzithon.homemate.service.HousingInfoService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/housing")
@RequiredArgsConstructor
public class HousingInfoController {

    private final HousingInfoService housingInfoService;


    // 테스트용: 서울 중구(11-140) 한 쌍만 테스트
    @PostMapping("/fetch-test")
    public ResponseEntity<MessageResponse> fetchTestHousingData() {
        try {
            int totalSaved = housingInfoService.fetchAllHousingDataForRegion("11", "140");
            return ResponseEntity.ok(new MessageResponse(
                    String.format("테스트 데이터 저장 완료 (서울 중구): 총 %d건이 저장되었습니다.", totalSaved)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("데이터 저장 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    // 전국 모든 지역의 데이터 자동 수집
    @PostMapping("/fetch-all")
    public ResponseEntity<MessageResponse> fetchAllHousingData() {
        try {
            int totalSaved = housingInfoService.fetchAllRegionsHousingData();
            return ResponseEntity.ok(new MessageResponse(
                    String.format("전국 모든 지역 데이터 저장 완료: 총 %d건이 저장되었습니다.", totalSaved)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("데이터 저장 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    //저장된 모든 주거정보 조회

    @GetMapping
    public ResponseEntity<List<HousingInfo>> getAllHousingInfo() {
        List<HousingInfo> housingInfoList = housingInfoService.getAllHousingInfo();
        return ResponseEntity.ok(housingInfoList);
    }


     //단지 식별자로 주거정보 조회

    @GetMapping("/{hsmpSn}")
    public ResponseEntity<HousingInfo> getHousingInfoByHsmpSn(@PathVariable("hsmpSn") String hsmpSn) {
        try {
            HousingInfo housingInfo = housingInfoService.getHousingInfoByHsmpSn(hsmpSn);
            return ResponseEntity.ok(housingInfo);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    //AI를 활용한 주거정보 추천 (TOP5) 
    //@param request 사용자 프롬프트가 포함된 요청
    //@param region URL 파라미터로 권역 전달 (선택사항)
    //@return 추천된 주거정보 목록 (TOP5)
     
    @PostMapping("/recommend")
    public ResponseEntity<?> getRecommendations(
            @RequestBody RecommendationRequest request,
            @RequestParam(value = "region", required = true) String region) {
        try {
            if (request.prompt() == null || request.prompt().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("프롬프트를 입력해주세요."));
            }
            
            if (region == null || region.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("권역 정보를 입력해주세요."));
            }

            RecommendationResponse response = housingInfoService.getRecommendations(
                    request.prompt(), 
                    region // URL 파라미터로 전달된 권역 정보 (필수)
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            // 명시적인 검증 오류
            return ResponseEntity.badRequest()
                    .body(new MessageResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("추천 서비스 처리 중 오류 발생", e);
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("추천 서비스 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    //AI를 활용한 주거정보 추천 (TOP5) - SSE 스트리밍 버전
    //@param request 사용자 프롬프트가 포함된 요청
    //@param region URL 파라미터로 권역 전달 (필수)
    //@return SSE 스트림으로 추천 결과를 실시간 전송
     
    @PostMapping(value = "/recommend/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getRecommendationsStream(
            @RequestBody RecommendationRequest request,
            @RequestParam(value = "region", required = true) String region) {
        if (request.prompt() == null || request.prompt().trim().isEmpty()) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"error\":\"프롬프트를 입력해주세요.\"}"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }
        
        if (region == null || region.trim().isEmpty()) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"error\":\"권역 정보를 입력해주세요.\"}"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }
        
        return housingInfoService.getRecommendationsStream(request.prompt(), region);
    }
    
    // 사용 가능한 모든 region 목록 조회 (드롭다운용) - 기존 방식 (하위 호환)
    @GetMapping("/regions")
    public ResponseEntity<List<Map<String, String>>> getAvailableRegions() {
        List<Map<String, String>> regions = housingInfoService.getAvailableRegions();
        return ResponseEntity.ok(regions);
    }
    
    // ========== 새로운 드롭다운 방식 API ==========
    
    // 광역시/도 목록 조회 (상위 드롭다운용)
    @GetMapping("/sido")
    public ResponseEntity<List<Map<String, String>>> getSidoList() {
        List<Map<String, String>> sidoList = housingInfoService.getSidoList();
        return ResponseEntity.ok(sidoList);
    }
    
    // 특정 광역시/도의 시/군/구 목록 조회 (하위 드롭다운용)
    @GetMapping("/districts")
    public ResponseEntity<?> getDistrictsBySido(
            @RequestParam(value = "sid", required = false) String sido) {
        // sido 파라미터가 없는 경우
        if (sido == null || sido.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("상위 드롭다운에서 광역시/도를 먼저 선택해주세요."));
        }
        
        // sido가 유효한지 확인
        List<Map<String, String>> districts = housingInfoService.getDistrictsBySido(sido);
        
        // sido가 유효하지 않거나 해당 지역에 데이터가 없는 경우
        if (districts == null || districts.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("선택하신 광역시/도에 해당하는 시/군/구 데이터가 없거나 잘못된 지역입니다. 다른 지역을 선택해주세요."));
        }
        
        return ResponseEntity.ok(districts);
    }
    
    // 새로운 추천 API (sido + districts 방식)
    @PostMapping("/recommend/v2")
    public ResponseEntity<?> getRecommendationsV2(
            @RequestBody RecommendationRequestV2 request) {
        try {
            // 프롬프트 검증
            if (request == null) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("요청 데이터가 없습니다."));
            }
            
            if (request.prompt() == null || request.prompt().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("추천 받고 싶은 내용을 입력해주세요."));
            }
            
            // 광역시/도 검증
            if (request.sido() == null || request.sido().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("상위 드롭다운에서 광역시/도를 먼저 선택해주세요."));
            }
            
            // 시/군/구 검증
            if (request.districts() == null || request.districts().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("하위 드롭다운에서 시/군/구를 최소 1개 이상 선택해주세요."));
            }
            
            // districts에 빈 문자열이 포함되어 있는지 확인
            boolean hasEmptyDistrict = request.districts().stream()
                    .anyMatch(district -> district == null || district.trim().isEmpty());
            if (hasEmptyDistrict) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("시/군/구 선택에 빈 값이 포함되어 있습니다. 올바른 시/군/구를 선택해주세요."));
            }

            RecommendationResponse response = housingInfoService.getRecommendationsV2(
                    request.sido(),
                    request.districts(),
                    request.prompt()
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            // 명시적인 검증 오류
            return ResponseEntity.badRequest()
                    .body(new MessageResponse(e.getMessage()));
        } catch (Exception e) {
            // 기타 오류
            log.error("추천 서비스 처리 중 오류 발생", e);
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("추천 서비스 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
}

