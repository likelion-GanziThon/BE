package com.ganzithon.homemate.controller;

import com.ganzithon.homemate.dto.MessageResponse;
import com.ganzithon.homemate.entity.HousingInfo;
import com.ganzithon.homemate.service.HousingInfoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/housing")
@RequiredArgsConstructor
public class HousingInfoController {

    private final HousingInfoService housingInfoService;


    // 특정 지역의 데이터 가져오기 (파라미터로 지역 코드 지정)
    @PostMapping("/fetch")
    public ResponseEntity<MessageResponse> fetchHousingData(
            @RequestParam(value = "brtcCode", required = false) String brtcCode,
            @RequestParam(value = "signguCode", required = false) String signguCode,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "numOfRows", defaultValue = "100") int numOfRows) {
        try {
            int savedCount;
            if (brtcCode != null && signguCode != null) {
                // 파라미터로 지역 코드 지정
                savedCount = housingInfoService.fetchAndSaveHousingData(brtcCode, signguCode, pageNo, numOfRows);
            } else {
                // 기본 설정값 사용
                savedCount = housingInfoService.fetchAndSaveHousingData(pageNo, numOfRows);
            }
            return ResponseEntity.ok(new MessageResponse(
                    String.format("데이터 저장 완료: %d건이 저장되었습니다.", savedCount)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("데이터 저장 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    // 특정 지역의 모든 페이지 데이터 가져오기
    @PostMapping("/fetch-region")
    public ResponseEntity<MessageResponse> fetchRegionHousingData(
            @RequestParam(value = "brtcCode") String brtcCode,
            @RequestParam(value = "signguCode") String signguCode) {
        try {
            int totalSaved = housingInfoService.fetchAllHousingDataForRegion(brtcCode, signguCode);
            return ResponseEntity.ok(new MessageResponse(
                    String.format("지역 데이터 저장 완료 (brtcCode: %s, signguCode: %s): 총 %d건이 저장되었습니다.", 
                            brtcCode, signguCode, totalSaved)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("데이터 저장 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

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

    // 전국 모든 지역의 데이터 가져오기
    @PostMapping("/fetch-all-regions")
    public ResponseEntity<MessageResponse> fetchAllRegionsHousingData() {
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
}

