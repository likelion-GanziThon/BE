package com.ganzithon.homemate.service;

import com.ganzithon.homemate.dto.HousingApiResponse;
import com.ganzithon.homemate.dto.RecommendationResponse;
import com.ganzithon.homemate.entity.HousingInfo;
import com.ganzithon.homemate.repository.HousingInfoRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class HousingInfoService {

    private final HousingInfoRepository housingInfoRepository;
    private final RestTemplate restTemplate;
    private final UpstageAiService upstageAiService;

    @Value("${housing.api.key:}")
    private String apiKey;

    @Value("${housing.api.url:}")
    private String apiUrl;

    @Value("${housing.api.brtcCode:}")
    private String defaultBrtcCode;

    @Value("${housing.api.signguCode:}")
    private String defaultSignguCode;

    // 특정 지역의 데이터 가져오기 (파라미터로 지역 코드 지정)
    @Transactional
    public int fetchAndSaveHousingData(String brtcCode, String signguCode, int pageNo, int numOfRows) {
        try {
            // 필수 파라미터 검증
            if (brtcCode == null || brtcCode.trim().isEmpty()) {
                throw new RuntimeException("필수 파라미터 brtcCode(광역시도 코드)가 설정되지 않았습니다.");
            }
            if (signguCode == null || signguCode.trim().isEmpty()) {
                throw new RuntimeException("필수 파라미터 signguCode(시군구 코드)가 설정되지 않았습니다.");
            }
            
            return fetchAndSaveHousingDataInternal(brtcCode, signguCode, pageNo, numOfRows);
        } catch (Exception e) {
            log.error("API 호출 또는 데이터 저장 중 오류 발생 (brtcCode: {}, signguCode: {})", brtcCode, signguCode, e);
            throw new RuntimeException("주거정보 데이터를 가져오는 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    // 기본 설정값 사용 (기존 메서드 호환성 유지)
    @Transactional
    public int fetchAndSaveHousingData(int pageNo, int numOfRows) {
        String brtcCode = defaultBrtcCode;
        String signguCode = defaultSignguCode;
        
        if (brtcCode == null || brtcCode.trim().isEmpty()) {
            throw new RuntimeException("필수 파라미터 brtcCode(광역시도 코드)가 설정되지 않았습니다. application.properties에 설정하거나 파라미터로 전달하세요.");
        }
        if (signguCode == null || signguCode.trim().isEmpty()) {
            throw new RuntimeException("필수 파라미터 signguCode(시군구 코드)가 설정되지 않았습니다. application.properties에 설정하거나 파라미터로 전달하세요.");
        }
        
        return fetchAndSaveHousingData(brtcCode, signguCode, pageNo, numOfRows);
    }

    // 내부 구현 메서드
    private int fetchAndSaveHousingDataInternal(String brtcCode, String signguCode, int pageNo, int numOfRows) {
            // 이미 인코딩된 키 사용 (properties에서 %2B, %3D%3D 포함된 값)
            // 재인코딩 방지를 위해 쿼리 문자열을 직접 구성
            String separator = apiUrl.contains("?") ? "&" : "?";
            
            // 쿼리 문자열 직접 구성 (재인코딩 방지)
            // 패턴: ServiceKey={이미 인코딩된 키}&brtcCode={값}&signguCode={값}&...
            String queryString = String.format(
                    "%sServiceKey=%s&brtcCode=%s&signguCode=%s&pageNo=%d&numOfRows=%d&resultType=json",
                    separator, apiKey, brtcCode, signguCode, pageNo, numOfRows
            );
            
            // 완성된 URL 문자열을 URI로 변환 (재인코딩 방지)
            String fullUrl = apiUrl + queryString;
            java.net.URI uri = java.net.URI.create(fullUrl);

            log.info("API 호출 URL: {}", fullUrl.replace(apiKey, "***"));

            // API 호출 - 먼저 String으로 받아서 응답 확인
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // URI 객체를 사용하여 재인코딩 방지
            ResponseEntity<byte[]> byteResponse = restTemplate.exchange(
                    uri, HttpMethod.GET, entity, byte[].class);

            String contentType = byteResponse.getHeaders().getContentType() != null
                    ? byteResponse.getHeaders().getContentType().toString()
                    : "unknown";
            int statusCode = byteResponse.getStatusCode().value();
            byte[] bodyBytes = byteResponse.getBody();
            
            log.info("API 응답 Content-Type: {}", contentType);
            log.info("API 응답 상태 코드: {}", statusCode);
            
            if (bodyBytes == null) {
                throw new RuntimeException("API 응답 본문이 null입니다. (상태코드: " + statusCode + ")");
            }
            
            String responseBody = new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
            
            // HTML 응답 체크 (실제 HTML 태그가 있는지 확인)
            String trimmedBody = responseBody.trim();
            if (contentType.contains("text/html") && (trimmedBody.startsWith("<html") || trimmedBody.startsWith("<!DOCTYPE"))) {
                String errorPreview = responseBody.substring(0, Math.min(500, responseBody.length()));
                log.error("API가 HTML을 반환했습니다. 응답 내용 (처음 500자): {}", errorPreview);
                throw new RuntimeException(String.format(
                        "API가 JSON 대신 HTML을 반환했습니다. (상태코드: %d)\n" +
                        "API URL, 키, 파라미터를 확인하세요.\n" +
                        "응답 내용: %s", statusCode, errorPreview));
            }
            
            // JSON 응답인지 확인 (첫 문자가 { 또는 [인지)
            if (!trimmedBody.startsWith("{") && !trimmedBody.startsWith("[")) {
                String errorPreview = responseBody.substring(0, Math.min(500, responseBody.length()));
                log.error("JSON 형식이 아닌 응답입니다. 응답 내용 (처음 500자): {}", errorPreview);
                throw new RuntimeException(String.format(
                        "JSON 형식이 아닌 응답입니다. (상태코드: %d)\n" +
                        "응답 내용: %s", statusCode, errorPreview));
            }
            
            // JSON 파싱
            HousingApiResponse apiResponse;
            try {
                com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                apiResponse = objectMapper.readValue(responseBody, HousingApiResponse.class);
            } catch (Exception e) {
                String errorPreview = responseBody.substring(0, Math.min(1000, responseBody.length()));
                log.error("JSON 파싱 실패. 원본 응답 (처음 1000자): {}", errorPreview);
                throw new RuntimeException(String.format(
                        "API 응답을 JSON으로 파싱할 수 없습니다.\n" +
                        "Content-Type: %s\n" +
                        "상태 코드: %d\n" +
                        "응답 내용: %s\n" +
                        "오류: %s",
                        contentType, statusCode, errorPreview, e.getMessage()), e);
            }
            
            // 응답 구조 검증
            if (apiResponse == null || apiResponse.getHsmpList() == null) {
                log.warn("[PROBE] API 응답 구조가 올바르지 않습니다. (brtcCode: {}, signguCode: {}, pageNo: {})", 
                        brtcCode, signguCode, pageNo);
                return 0;
            }
            
            String resultCode = apiResponse.getCode();
            List<HousingApiResponse.HousingItem> items = apiResponse.getHsmpList();
            
            // API 응답 코드 확인
            if (resultCode != null && !resultCode.equals("00") && !resultCode.equals("000")) {
                log.warn("[PROBE] API 응답 코드 오류: code={} (brtcCode: {}, signguCode: {}, pageNo: {})", 
                        resultCode, brtcCode, signguCode, pageNo);
                return 0;
            }
            
            if (items == null || items.isEmpty()) {
                log.info("[EMPTY] hsmpList 배열이 비어있습니다. (brtcCode: {}, signguCode: {}, pageNo: {})", 
                        brtcCode, signguCode, pageNo);
                return 0;
            }
            
            // 첫 번째 항목에서 totalCount 가져오기 (모든 항목이 같은 값을 가짐)
            Integer totalCount = null;
            Integer currentPageNo = null;
            String responseNumOfRows = null;
            if (!items.isEmpty()) {
                HousingApiResponse.HousingItem firstItem = items.get(0);
                totalCount = firstItem.getTotalCount();
                currentPageNo = firstItem.getPageNo();
                responseNumOfRows = firstItem.getNumOfRows();
            }
            
            // totalCount 확인
            if (totalCount == null || totalCount == 0) {
                log.info("[EMPTY] totalCount=0 (brtcCode: {}, signguCode: {}, pageNo: {})", 
                        brtcCode, signguCode, pageNo);
                return 0;
            }
            
            // 상세 로깅 (첫 페이지만)
            if (pageNo == 1) {
                log.info("[PROBE] code={}, totalCount={}, pageNo={}, numOfRows={}, items={} (brtcCode: {}, signguCode: {})", 
                        resultCode, totalCount, currentPageNo, responseNumOfRows, items.size(), brtcCode, signguCode);
            }

            // 데이터 변환 및 저장
            List<HousingInfo> housingInfoList = new ArrayList<>();
            java.util.Set<String> processedHsmpSn = new java.util.HashSet<>(); // 배치 내 중복 체크용
            int savedCount = 0;
            int skippedCount = 0;

            for (HousingApiResponse.HousingItem item : items) {
                // hsmpSn 추출
                String hsmpSnStr = item.getHsmpSn() != null ? item.getHsmpSn().toString().trim() : null;
                if (hsmpSnStr == null || hsmpSnStr.isEmpty()) {
                    continue;
                }
                
                // 배치 내 중복 체크
                if (processedHsmpSn.contains(hsmpSnStr)) {
                    skippedCount++;
                    continue;
                }
                
                // DB에 이미 존재하는지 체크
                if (housingInfoRepository.existsByHsmpSn(hsmpSnStr)) {
                    skippedCount++;
                    processedHsmpSn.add(hsmpSnStr); // 처리된 것으로 표시
                    continue;
                }

                try {
                    HousingInfo housingInfo = convertToEntity(item);
                    if (housingInfo != null) {
                        housingInfoList.add(housingInfo);
                        processedHsmpSn.add(hsmpSnStr); // 처리된 것으로 표시
                    }
                } catch (Exception e) {
                    log.error("데이터 변환 중 오류 발생: {}", e.getMessage());
                }
            }

            // 일괄 저장 (중복 예외 처리)
            if (!housingInfoList.isEmpty()) {
                try {
                    housingInfoRepository.saveAll(housingInfoList);
                    savedCount = housingInfoList.size();
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    // 중복 키 예외 발생 시 개별 저장으로 전환
                    log.warn("일괄 저장 중 중복 키 오류 발생, 개별 저장으로 전환: {}", e.getMessage());
                    for (HousingInfo housingInfo : housingInfoList) {
                        try {
                            if (!housingInfoRepository.existsByHsmpSn(housingInfo.getHsmpSn())) {
                                housingInfoRepository.save(housingInfo);
                                savedCount++;
                            } else {
                                skippedCount++;
                            }
                        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                            // 중복이면 스킵
                            skippedCount++;
                            log.debug("중복 데이터 스킵: hsmpSn={}", housingInfo.getHsmpSn());
                        } catch (Exception ex) {
                            log.error("개별 저장 중 오류 발생 (hsmpSn: {}): {}", housingInfo.getHsmpSn(), ex.getMessage());
                        }
                    }
                }
            }

            log.info("데이터 저장 완료 (brtcCode: {}, signguCode: {}): {}건 저장, {}건 스킵", 
                    brtcCode, signguCode, savedCount, skippedCount);
            return savedCount;
    }


    // 특정 지역의 모든 페이지 데이터 가져오기
    @Transactional
    public int fetchAllHousingDataForRegion(String brtcCode, String signguCode) {
        int totalSaved = 0;
        int pageNo = 1;
        int numOfRows = 100;
        int savedCount;

        // 첫 페이지로 totalCount 확인
        try {
            savedCount = fetchAndSaveHousingData(brtcCode, signguCode, pageNo, numOfRows);
            totalSaved += savedCount;
            
            // 첫 페이지 응답에서 totalCount 가져오기 (내부 메서드 수정 필요)
            // 일단 첫 페이지 저장 후 계속 진행
            if (savedCount == 0) {
                log.info("[EMPTY] 첫 페이지에 데이터가 없습니다. (brtcCode: {}, signguCode: {})", brtcCode, signguCode);
                return 0;
            }
            
            pageNo++;
            
            // 나머지 페이지 처리
            do {
                // 너무 많은 요청을 방지하기 위해 잠시 대기
                try {
                    Thread.sleep(100); // 0.1초 대기
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                savedCount = fetchAndSaveHousingData(brtcCode, signguCode, pageNo, numOfRows);
                totalSaved += savedCount;
                
                if (savedCount > 0) {
                    log.debug("[PAGE] {}-{} p={} got={}건 (누적: {}건)", 
                            brtcCode, signguCode, pageNo, savedCount, totalSaved);
                }
                
                pageNo++;
                
            } while (savedCount > 0 && pageNo <= 1000); // 최대 1000페이지까지
            
        } catch (Exception e) {
            log.error("지역 데이터 수집 중 오류 발생 (brtcCode: {}, signguCode: {}): {}", 
                    brtcCode, signguCode, e.getMessage());
            throw e;
        }

        log.info("지역 데이터 저장 완료 (brtcCode: {}, signguCode: {}): 총 {}건", brtcCode, signguCode, totalSaved);
        return totalSaved;
    }

    // 기본 설정값 사용 (기존 메서드 호환성 유지)
    @Transactional
    public int fetchAllHousingData() {
        String brtcCode = defaultBrtcCode;
        String signguCode = defaultSignguCode;
        
        if (brtcCode == null || brtcCode.trim().isEmpty() || 
            signguCode == null || signguCode.trim().isEmpty()) {
            throw new RuntimeException("기본 지역 코드가 설정되지 않았습니다. application.properties에 설정하거나 모든 지역 데이터를 가져오려면 fetchAllRegionsHousingData()를 사용하세요.");
        }
        
        return fetchAllHousingDataForRegion(brtcCode, signguCode);
    }

    // 모든 지역의 데이터 가져오기
    @Transactional
    public int fetchAllRegionsHousingData() {
        log.info("전국 모든 지역의 주거정보 데이터 수집을 시작합니다...");
        
        // 기존 데이터 모두 삭제 (새로운 조건에 맞게 다시 다운로드하기 위해)
        long existingCount = housingInfoRepository.count();
        if (existingCount > 0) {
            log.info("기존 데이터 {}건을 삭제합니다...", existingCount);
            housingInfoRepository.deleteAll();
            log.info("기존 데이터 삭제 완료");
        }
        
        // 한국의 모든 광역시도 코드와 주요 시군구 코드
        // 실제로는 더 많은 시군구가 있지만, 주요 지역만 포함
        List<RegionCode> regions = getAllRegionCodes();
        
        int totalSaved = 0;
        int totalRegions = regions.size();
        int currentRegion = 0;
        
        for (RegionCode region : regions) {
            currentRegion++;
            log.info("[{}/{}] 지역 데이터 수집 중: brtcCode={}, signguCode={}", 
                    currentRegion, totalRegions, region.brtcCode, region.signguCode);
            
            try {
                int saved = fetchAllHousingDataForRegion(region.brtcCode, region.signguCode);
                totalSaved += saved;
                log.info("지역 데이터 수집 완료: {}건 저장 (누적: {}건)", saved, totalSaved);
            } catch (Exception e) {
                log.error("지역 데이터 수집 실패 (brtcCode: {}, signguCode: {}): {}", 
                        region.brtcCode, region.signguCode, e.getMessage());
                // 오류가 발생해도 다음 지역 계속 진행
            }
            
            // API 부하 방지를 위한 대기
            try {
                Thread.sleep(200); // 0.2초 대기
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        log.info("전국 모든 지역 데이터 수집 완료: 총 {}건 저장", totalSaved);
        return totalSaved;
    }

    // 지역 코드 정보 클래스
    private static class RegionCode {
        String brtcCode;
        String signguCode;
        
        RegionCode(String brtcCode, String signguCode) {
            this.brtcCode = brtcCode;
            this.signguCode = signguCode;
        }
    }

    // 모든 지역 코드 목록 반환
    private List<RegionCode> getAllRegionCodes() {
        List<RegionCode> regions = new ArrayList<>();
        
        // 서울특별시 (11) - 주요 구
        regions.add(new RegionCode("11", "110")); // 종로구
        regions.add(new RegionCode("11", "140")); // 중구
        regions.add(new RegionCode("11", "170")); // 용산구
        regions.add(new RegionCode("11", "200")); // 성동구
        regions.add(new RegionCode("11", "215")); // 광진구
        regions.add(new RegionCode("11", "230")); // 동대문구
        regions.add(new RegionCode("11", "260")); // 중랑구
        regions.add(new RegionCode("11", "290")); // 성북구
        regions.add(new RegionCode("11", "305")); // 강북구
        regions.add(new RegionCode("11", "320")); // 도봉구
        regions.add(new RegionCode("11", "350")); // 노원구
        regions.add(new RegionCode("11", "380")); // 은평구
        regions.add(new RegionCode("11", "410")); // 서대문구
        regions.add(new RegionCode("11", "440")); // 마포구
        regions.add(new RegionCode("11", "470")); // 양천구
        regions.add(new RegionCode("11", "500")); // 강서구
        regions.add(new RegionCode("11", "530")); // 구로구
        regions.add(new RegionCode("11", "545")); // 금천구
        regions.add(new RegionCode("11", "560")); // 영등포구
        regions.add(new RegionCode("11", "590")); // 동작구
        regions.add(new RegionCode("11", "620")); // 관악구
        regions.add(new RegionCode("11", "650")); // 서초구
        regions.add(new RegionCode("11", "680")); // 강남구
        regions.add(new RegionCode("11", "710")); // 송파구
        regions.add(new RegionCode("11", "740")); // 강동구
        
        // 부산광역시 (26) - 주요 구
        regions.add(new RegionCode("26", "110")); // 중구
        regions.add(new RegionCode("26", "140")); // 서구
        regions.add(new RegionCode("26", "170")); // 동구
        regions.add(new RegionCode("26", "200")); // 영도구
        regions.add(new RegionCode("26", "230")); // 부산진구
        regions.add(new RegionCode("26", "260")); // 동래구
        regions.add(new RegionCode("26", "290")); // 남구
        regions.add(new RegionCode("26", "320")); // 북구
        regions.add(new RegionCode("26", "350")); // 해운대구
        regions.add(new RegionCode("26", "380")); // 사하구
        regions.add(new RegionCode("26", "410")); // 금정구
        regions.add(new RegionCode("26", "440")); // 강서구
        regions.add(new RegionCode("26", "470")); // 연제구
        regions.add(new RegionCode("26", "500")); // 수영구
        regions.add(new RegionCode("26", "530")); // 사상구
        regions.add(new RegionCode("26", "710")); // 기장군
        
        // 대구광역시 (27)
        regions.add(new RegionCode("27", "110")); // 중구
        regions.add(new RegionCode("27", "140")); // 동구
        regions.add(new RegionCode("27", "170")); // 서구
        regions.add(new RegionCode("27", "200")); // 남구
        regions.add(new RegionCode("27", "230")); // 북구
        regions.add(new RegionCode("27", "260")); // 수성구
        regions.add(new RegionCode("27", "290")); // 달서구
        regions.add(new RegionCode("27", "710")); // 달성군
        
        // 인천광역시 (28)
        regions.add(new RegionCode("28", "110")); // 중구
        regions.add(new RegionCode("28", "140")); // 동구
        regions.add(new RegionCode("28", "177")); // 미추홀구
        regions.add(new RegionCode("28", "185")); // 연수구
        regions.add(new RegionCode("28", "200")); // 남동구
        regions.add(new RegionCode("28", "237")); // 부평구
        regions.add(new RegionCode("28", "245")); // 계양구
        regions.add(new RegionCode("28", "260")); // 서구
        regions.add(new RegionCode("28", "710")); // 강화군
        regions.add(new RegionCode("28", "720")); // 옹진군
        
        // 광주광역시 (29)
        regions.add(new RegionCode("29", "110")); // 동구
        regions.add(new RegionCode("29", "140")); // 서구
        regions.add(new RegionCode("29", "155")); // 남구
        regions.add(new RegionCode("29", "170")); // 북구
        regions.add(new RegionCode("29", "200")); // 광산구
        
        // 대전광역시 (30)
        regions.add(new RegionCode("30", "110")); // 동구
        regions.add(new RegionCode("30", "140")); // 중구
        regions.add(new RegionCode("30", "170")); // 서구
        regions.add(new RegionCode("30", "200")); // 유성구
        regions.add(new RegionCode("30", "230")); // 대덕구
        
        // 울산광역시 (31)
        regions.add(new RegionCode("31", "110")); // 중구
        regions.add(new RegionCode("31", "140")); // 남구
        regions.add(new RegionCode("31", "170")); // 동구
        regions.add(new RegionCode("31", "200")); // 북구
        regions.add(new RegionCode("31", "710")); // 울주군
        
        // 세종특별자치시 (36)
        regions.add(new RegionCode("36", "110")); // 세종시
        
        // 경기도 (41) - 주요 시군
        regions.add(new RegionCode("41", "111")); // 수원시
        regions.add(new RegionCode("41", "113")); // 성남시
        regions.add(new RegionCode("41", "115")); // 의정부시
        regions.add(new RegionCode("41", "117")); // 안양시
        regions.add(new RegionCode("41", "119")); // 부천시
        regions.add(new RegionCode("41", "121")); // 광명시
        regions.add(new RegionCode("41", "123")); // 평택시
        regions.add(new RegionCode("41", "125")); // 동두천시
        regions.add(new RegionCode("41", "127")); // 안산시
        regions.add(new RegionCode("41", "129")); // 고양시
        regions.add(new RegionCode("41", "131")); // 과천시
        regions.add(new RegionCode("41", "133")); // 구리시
        regions.add(new RegionCode("41", "135")); // 남양주시
        regions.add(new RegionCode("41", "137")); // 오산시
        regions.add(new RegionCode("41", "139")); // 시흥시
        regions.add(new RegionCode("41", "141")); // 군포시
        regions.add(new RegionCode("41", "143")); // 의왕시
        regions.add(new RegionCode("41", "145")); // 하남시
        regions.add(new RegionCode("41", "147")); // 용인시
        regions.add(new RegionCode("41", "149")); // 파주시
        regions.add(new RegionCode("41", "151")); // 이천시
        regions.add(new RegionCode("41", "153")); // 안성시
        regions.add(new RegionCode("41", "155")); // 김포시
        regions.add(new RegionCode("41", "157")); // 화성시
        regions.add(new RegionCode("41", "159")); // 광주시
        regions.add(new RegionCode("41", "161")); // 양주시
        regions.add(new RegionCode("41", "163")); // 포천시
        regions.add(new RegionCode("41", "165")); // 여주시
        
        // 강원도 (42) - 주요 시군
        regions.add(new RegionCode("42", "110")); // 춘천시
        regions.add(new RegionCode("42", "130")); // 원주시
        regions.add(new RegionCode("42", "150")); // 강릉시
        regions.add(new RegionCode("42", "170")); // 동해시
        regions.add(new RegionCode("42", "190")); // 태백시
        regions.add(new RegionCode("42", "210")); // 속초시
        regions.add(new RegionCode("42", "230")); // 삼척시
        
        // 충청북도 (43) - 주요 시군
        regions.add(new RegionCode("43", "110")); // 청주시
        regions.add(new RegionCode("43", "130")); // 충주시
        regions.add(new RegionCode("43", "150")); // 제천시
        regions.add(new RegionCode("43", "720")); // 보은군
        regions.add(new RegionCode("43", "730")); // 옥천군
        regions.add(new RegionCode("43", "740")); // 영동군
        regions.add(new RegionCode("43", "745")); // 증평군
        regions.add(new RegionCode("43", "750")); // 진천군
        regions.add(new RegionCode("43", "760")); // 괴산군
        regions.add(new RegionCode("43", "770")); // 음성군
        regions.add(new RegionCode("43", "800")); // 단양군
        
        // 충청남도 (44) - 주요 시군
        regions.add(new RegionCode("44", "130")); // 천안시
        regions.add(new RegionCode("44", "133")); // 공주시
        regions.add(new RegionCode("44", "150")); // 보령시
        regions.add(new RegionCode("44", "180")); // 아산시
        regions.add(new RegionCode("44", "200")); // 서산시
        regions.add(new RegionCode("44", "210")); // 논산시
        regions.add(new RegionCode("44", "230")); // 계룡시
        regions.add(new RegionCode("44", "250")); // 당진시
        
        // 전라북도 (45) - 주요 시군
        regions.add(new RegionCode("45", "110")); // 전주시
        regions.add(new RegionCode("45", "130")); // 군산시
        regions.add(new RegionCode("45", "140")); // 익산시
        regions.add(new RegionCode("45", "180")); // 정읍시
        regions.add(new RegionCode("45", "190")); // 남원시
        regions.add(new RegionCode("45", "210")); // 김제시
        
        // 전라남도 (46) - 주요 시군
        regions.add(new RegionCode("46", "110")); // 목포시
        regions.add(new RegionCode("46", "130")); // 여수시
        regions.add(new RegionCode("46", "150")); // 순천시
        regions.add(new RegionCode("46", "170")); // 나주시
        regions.add(new RegionCode("46", "230")); // 광양시
        
        // 경상북도 (47) - 주요 시군
        regions.add(new RegionCode("47", "110")); // 포항시
        regions.add(new RegionCode("47", "130")); // 경주시
        regions.add(new RegionCode("47", "150")); // 김천시
        regions.add(new RegionCode("47", "170")); // 안동시
        regions.add(new RegionCode("47", "190")); // 구미시
        regions.add(new RegionCode("47", "210")); // 영주시
        regions.add(new RegionCode("47", "230")); // 영천시
        regions.add(new RegionCode("47", "250")); // 상주시
        regions.add(new RegionCode("47", "280")); // 문경시
        regions.add(new RegionCode("47", "290")); // 경산시
        
        // 경상남도 (48) - 주요 시군
        regions.add(new RegionCode("48", "120")); // 창원시
        regions.add(new RegionCode("48", "170")); // 진주시
        regions.add(new RegionCode("48", "220")); // 통영시
        regions.add(new RegionCode("48", "240")); // 사천시
        regions.add(new RegionCode("48", "250")); // 김해시
        regions.add(new RegionCode("48", "270")); // 밀양시
        regions.add(new RegionCode("48", "310")); // 거제시
        regions.add(new RegionCode("48", "330")); // 양산시
        
        // 제주특별자치도 (50)
        regions.add(new RegionCode("50", "110")); // 제주시
        regions.add(new RegionCode("50", "130")); // 서귀포시
        
        return regions;
    }


    private HousingInfo convertToEntity(HousingApiResponse.HousingItem item) {
        // hsmpSn이 null이거나 빈 값이면 스킵
        String hsmpSnStr = item.getHsmpSn() != null ? item.getHsmpSn().toString().trim() : null;
        if (hsmpSnStr == null || hsmpSnStr.isEmpty()) {
            return null;
        }
        
        // 필요한 필드만 저장: brtcNm, hsmpNm, signguNm(signguCode 대신), hshldCo, bassRentGtn, bassMtRntchrg
        return HousingInfo.of(
                hsmpSnStr,
                item.getBrtcNm(), // brtcNm
                item.getSignguNm(), // signguNm (signguCode 대신 사용)
                item.getHsmpNm(), // hsmpNm
                parseInteger(item.getHshldCo()), // hshldCo
                parseLong(item.getBassRentGtn()), // bassRentGtn
                parseLong(item.getBassMtRntchrg()) // bassMtRntchrg
        );
    }

    // Object를 String으로 변환 (빈 객체, null 처리)
    private String convertToString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String str = (String) value;
            return str.isEmpty() ? null : str;
        }
        // 빈 객체 {}인 경우 null 반환
        if (value.toString().equals("{}") || value.toString().trim().isEmpty()) {
            return null;
        }
        return value.toString();
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        String str = value.toString().trim().replace(",", "");
        if (str.isEmpty()) {
            return null;
        }
        try {
            // 숫자 타입이면 직접 변환
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            log.warn("정수 변환 실패: {}", value);
            return null;
        }
    }

    private Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        String str = value.toString().trim().replace(",", "");
        if (str.isEmpty()) {
            return null;
        }
        try {
            // 숫자 타입이면 직접 변환
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            log.warn("실수 변환 실패: {}", value);
            return null;
        }
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        String str = value.toString().trim().replace(",", "");
        if (str.isEmpty()) {
            return null;
        }
        try {
            // 숫자 타입이면 직접 변환
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            log.warn("Long 변환 실패: {}", value);
            return null;
        }
    }


     // 저장된 모든 주거정보 조회

    public List<HousingInfo> getAllHousingInfo() {
        return housingInfoRepository.findAll();
    }


     // 단지 식별자로 주거정보 조회

    public HousingInfo getHousingInfoByHsmpSn(String hsmpSn) {
        return housingInfoRepository.findByHsmpSn(hsmpSn)
                .orElseThrow(() -> new RuntimeException("주거정보를 찾을 수 없습니다: " + hsmpSn));
    }

    //AI를 활용하여 사용자 프롬프트에 맞는 주거정보 TOP5를 추천
    //@param userPrompt 사용자가 입력한 프롬프트 (예: "강남구에 있는 저렴한 아파트 추천해줘")
    //@param region 권역 선택 (예: "서울_강남권", "부산_동부해안권", "경기_남부핵심권" 등)
    //@return 추천된 주거정보 목록 (TOP5)
    
    @Transactional(readOnly = true)
    public RecommendationResponse getRecommendations(String userPrompt, String region) {
        try {
            log.info("AI 추천 요청 수신: prompt={}, region={}", userPrompt, region);

            // 권역별 구/시 코드 목록 가져오기
            List<String> regionCodes = getRegionCodes(region);
            
            // 권역이 필수이므로 없으면 에러 반환
            if (regionCodes == null || regionCodes.isEmpty()) {
                log.warn("권역 정보가 없거나 매핑되지 않음: {}", region);
                return new RecommendationResponse(List.of());
            }
            
            // 1. DB 쿼리 최적화: DB 레벨에서 필터링
            List<HousingInfo> allHousingInfo;
            long startTime = System.currentTimeMillis();
            
            // 광역시/도 전체인 경우 (코드가 2자리)
            if (regionCodes.size() == 1 && regionCodes.get(0).length() == 2) {
                String brtcCode = regionCodes.get(0);
                String brtcNm = getBrtcNmFromCode(brtcCode);
                if (brtcNm != null) {
                    // DB 쿼리 최적화: DB 레벨에서 필터링
                    allHousingInfo = housingInfoRepository.findByBrtcNmLike(brtcNm);
                } else {
                    allHousingInfo = List.of();
                }
            } else {
                // 구/시 단위 필터링: region 이름에서 brtcNm 추출
                allHousingInfo = getHousingInfoByRegionCodes(region, regionCodes);
            }
            
            long queryTime = System.currentTimeMillis() - startTime;
            log.info("권역 '{}'에 해당하는 주거정보: {}건 조회 완료 (쿼리 시간: {}ms)", region, allHousingInfo.size(), queryTime);

            if (allHousingInfo.isEmpty()) {
                log.warn("필터링된 주거정보가 없습니다.");
                return new RecommendationResponse(List.of());
            }

            // 2. AI 전송 전 데이터 제한: 최대 10개로 제한 (30초 내외 응답 목표)
            List<HousingInfo> limitedHousingInfo = allHousingInfo.size() > 10 
                ? allHousingInfo.subList(0, 10) 
                : allHousingInfo;
            
            // 3. AI API 전송용 데이터 준비: 엔티티를 Map으로 변환 (필요한 필드만 추출)
            long convertStartTime = System.currentTimeMillis();
            List<Map<String, Object>> housingDataList = limitedHousingInfo.parallelStream()
                    .map(this::convertToMap)
                    .collect(Collectors.toList());
            long convertTime = System.currentTimeMillis() - convertStartTime;
            log.info("AI 전송용 데이터 준비 완료: {}건/{}건 (준비 시간: {}ms)", housingDataList.size(), allHousingInfo.size(), convertTime);

            // 4. 하이브리드 접근: Map 생성 (전체 데이터로 생성 - 추천 결과 매칭용)
            Map<String, HousingInfo> housingInfoMap = allHousingInfo.parallelStream()
                    .collect(Collectors.toConcurrentMap(
                            HousingInfo::getHsmpSn,
                            info -> info,
                            (existing, replacement) -> existing
                    ));

            // AI API 호출하여 추천 받기
            long aiStartTime = System.currentTimeMillis();
            List<UpstageAiService.RecommendationResult> recommendationResults = 
                    upstageAiService.getRecommendations(userPrompt, housingDataList);
            long aiTime = System.currentTimeMillis() - aiStartTime;
            log.info("AI 추천 결과: {}건 추천됨 (AI 처리 시간: {}ms)", recommendationResults.size(), aiTime);

            // 추천된 hsmpSn으로 HousingInfo 조회 및 순서 유지
            List<RecommendationResponse.HousingRecommendation> recommendations = new ArrayList<>();
            for (int i = 0; i < recommendationResults.size() && i < 5; i++) {
                UpstageAiService.RecommendationResult result = recommendationResults.get(i);
                HousingInfo housingInfo = housingInfoMap.get(result.getHsmpSn());
                
                if (housingInfo != null) {
                    recommendations.add(new RecommendationResponse.HousingRecommendation(
                            i + 1,
                            housingInfo,
                            result.getReason()
                    ));
                }
            }

            log.info("최종 추천 결과: {}건 반환 (총 처리 시간: {}ms)", recommendations.size(), System.currentTimeMillis() - startTime);
            return new RecommendationResponse(recommendations);

        } catch (Exception e) {
            log.error("AI 추천 처리 중 오류 발생", e);
            throw new RuntimeException("AI 추천 서비스 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 권역 코드로 광역시/도명 반환
     */
    private String getBrtcNmFromCode(String brtcCode) {
        Map<String, String> codeToNameMap = new HashMap<>();
        codeToNameMap.put("28", "인천광역시");
        codeToNameMap.put("27", "대구광역시");
        codeToNameMap.put("29", "광주광역시");
        codeToNameMap.put("30", "대전광역시");
        codeToNameMap.put("31", "울산광역시");
        codeToNameMap.put("36", "세종특별자치도");
        codeToNameMap.put("42", "강원도");
        codeToNameMap.put("43", "충청북도");
        codeToNameMap.put("44", "충청남도");
        codeToNameMap.put("45", "전라북도");
        codeToNameMap.put("46", "전라남도");
        codeToNameMap.put("47", "경상북도");
        codeToNameMap.put("48", "경상남도");
        codeToNameMap.put("50", "제주특별자치도");
        return codeToNameMap.get(brtcCode);
    }
    
    /**
     * 권역 코드 목록으로 주거정보 조회 (region 이름에서 brtcNm 추출)
     */
    private List<HousingInfo> getHousingInfoByRegionCodes(String region, List<String> regionCodes) {
        // region 이름에서 brtcNm 추출
        String brtcNm = extractBrtcNmFromRegion(region);
        if (brtcNm == null) {
            log.warn("region에서 brtcNm을 추출할 수 없음: {}", region);
            return List.of();
        }
        
        // region 코드에서 signguNm 목록 추출
        List<String> signguNms = new ArrayList<>();
        for (String code : regionCodes) {
            String signguNm = getSignguNmFromCodeByRegion(region, code);
            if (signguNm != null) {
                signguNms.add(signguNm);
            }
        }
        
        if (signguNms.isEmpty()) {
            return List.of();
        }
        
        // DB 쿼리로 조회
        return housingInfoRepository.findByBrtcNmAndSignguNmIn(brtcNm, signguNms);
    }
    
    
    //region 이름에서 brtcNm 추출
    private String extractBrtcNmFromRegion(String region) {
        if (region == null) return null;
        
        if (region.startsWith("서울_")) return "서울특별시";
        if (region.startsWith("경기_")) return "경기도";
        if (region.startsWith("부산_")) return "부산광역시";
        if (region.startsWith("인천_")) return "인천광역시";
        if (region.startsWith("대구_")) return "대구광역시";
        if (region.startsWith("대전_")) return "대전광역시";
        if (region.startsWith("울산_")) return "울산광역시";
        if (region.startsWith("광주_")) return "광주광역시";
        if (region.startsWith("세종")) return "세종특별자치시";
        if (region.startsWith("충북_")) return "충청북도";
        if (region.startsWith("충남_")) return "충청남도";
        if (region.startsWith("전북_")) return "전라북도";
        if (region.startsWith("전남_")) return "전라남도";
        if (region.startsWith("경북_")) return "경상북도";
        if (region.startsWith("경남_")) return "경상남도";
        if (region.startsWith("강원_")) return "강원도";
        if (region.startsWith("제주_")) return "제주특별자치도";
        
        // 전체 지역명 매핑
        Map<String, String> fullNameMap = new HashMap<>();
        fullNameMap.put("인천광역시", "인천광역시");
        fullNameMap.put("대구광역시", "대구광역시");
        fullNameMap.put("광주광역시", "광주광역시");
        fullNameMap.put("대전광역시", "대전광역시");
        fullNameMap.put("울산광역시", "울산광역시");
        fullNameMap.put("세종특별자치도", "세종특별자치시");
        fullNameMap.put("강원도", "강원도");
        fullNameMap.put("충청북도", "충청북도");
        fullNameMap.put("충청남도", "충청남도");
        fullNameMap.put("전라북도", "전라북도");
        fullNameMap.put("전라남도", "전라남도");
        fullNameMap.put("경상북도", "경상북도");
        fullNameMap.put("경상남도", "경상남도");
        fullNameMap.put("제주특별자치도", "제주특별자치도");
        
        return fullNameMap.getOrDefault(region, null);
    }
    
    //region과 code로 signguNm 추출
    private String getSignguNmFromCodeByRegion(String region, String code) {
        if (region == null || code == null) return null;
        
        if (region.startsWith("서울_")) return getSignguNmFromCode(code, "서울");
        if (region.startsWith("경기_")) return getSignguNmFromCode(code, "경기");
        if (region.startsWith("부산_")) return getSignguNmFromCode(code, "부산");
        if (region.startsWith("인천_")) return getSignguNmFromCodeForIncheon(code);
        if (region.startsWith("대구_")) return getSignguNmFromCodeForDaegu(code);
        if (region.startsWith("대전_")) return getSignguNmFromCodeForDaejeon(code);
        if (region.startsWith("울산_")) return getSignguNmFromCodeForUlsan(code);
        if (region.startsWith("광주_")) return getSignguNmFromCodeForGwangju(code);
        if (region.startsWith("세종")) return "세종시";
        if (region.startsWith("충북_")) return getSignguNmFromCodeForChungbuk(code);
        if (region.startsWith("충남_")) return getSignguNmFromCodeForChungnam(code);
        if (region.startsWith("전북_")) return getSignguNmFromCodeForJeonbuk(code);
        if (region.startsWith("전남_")) return getSignguNmFromCodeForJeonnam(code);
        if (region.startsWith("경북_")) return getSignguNmFromCodeForGyeongbuk(code);
        if (region.startsWith("경남_")) return getSignguNmFromCodeForGyeongnam(code);
        if (region.startsWith("강원_")) return getSignguNmFromCodeForGangwon(code);
        if (region.startsWith("제주_")) return getSignguNmFromCodeForJeju(code);
        
        return null;
    }
    
    // 각 지역별 시군구명 반환 메서드들
    private String getSignguNmFromCodeForIncheon(String code) {
        Map<String, String> map = new HashMap<>();
        map.put("110", "중구");
        map.put("140", "동구");
        map.put("177", "미추홀구");
        map.put("185", "연수구");
        map.put("200", "남동구");
        map.put("237", "부평구");
        map.put("245", "계양구");
        map.put("260", "서구");
        map.put("710", "강화군");
        map.put("720", "옹진군");
        return map.get(code);
    }
    
    private String getSignguNmFromCodeForDaegu(String code) {
        Map<String, String> map = new HashMap<>();
        map.put("110", "중구");
        map.put("140", "동구");
        map.put("170", "서구");
        map.put("200", "남구");
        map.put("230", "북구");
        map.put("260", "수성구");
        map.put("290", "달서구");
        map.put("710", "달성군");
        return map.get(code);
    }
    
    private String getSignguNmFromCodeForDaejeon(String code) {
        Map<String, String> map = new HashMap<>();
        map.put("110", "동구");
        map.put("140", "중구");
        map.put("170", "서구");
        map.put("200", "유성구");
        map.put("230", "대덕구");
        return map.get(code);
    }
    
    private String getSignguNmFromCodeForUlsan(String code) {
        Map<String, String> map = new HashMap<>();
        map.put("110", "중구");
        map.put("140", "남구");
        map.put("170", "동구");
        map.put("200", "북구");
        map.put("710", "울주군");
        return map.get(code);
    }
    
    private String getSignguNmFromCodeForGwangju(String code) {
        Map<String, String> map = new HashMap<>();
        map.put("110", "동구");
        map.put("140", "서구");
        map.put("155", "남구");
        map.put("170", "북구");
        map.put("200", "광산구");
        return map.get(code);
    }
    
    private String getSignguNmFromCodeForChungbuk(String code) {
        Map<String, String> map = new HashMap<>();
        map.put("110", "청주시");
        map.put("130", "충주시");
        map.put("150", "제천시");
        map.put("720", "보은군");
        map.put("730", "옥천군");
        map.put("740", "영동군");
        map.put("745", "증평군");
        map.put("750", "진천군");
        map.put("760", "괴산군");
        map.put("770", "음성군");
        map.put("800", "단양군");
        return map.get(code);
    }
    
    private String getSignguNmFromCodeForChungnam(String code) {
        Map<String, String> map = new HashMap<>();
        map.put("130", "천안시");
        map.put("133", "공주시");
        map.put("150", "보령시");
        map.put("180", "아산시");
        map.put("200", "서산시");
        map.put("210", "논산시");
        map.put("230", "계룡시");
        map.put("250", "당진시");
        return map.get(code);
    }
    
    private String getSignguNmFromCodeForJeonbuk(String code) {
        Map<String, String> map = new HashMap<>();
        map.put("110", "전주시");
        map.put("130", "군산시");
        map.put("140", "익산시");
        map.put("180", "정읍시");
        map.put("190", "남원시");
        map.put("210", "김제시");
        return map.get(code);
    }
    
    private String getSignguNmFromCodeForJeonnam(String code) {
        Map<String, String> map = new HashMap<>();
        map.put("110", "목포시");
        map.put("130", "여수시");
        map.put("150", "순천시");
        map.put("170", "나주시");
        map.put("230", "광양시");
        return map.get(code);
    }
    
    private String getSignguNmFromCodeForGyeongbuk(String code) {
        Map<String, String> map = new HashMap<>();
        map.put("110", "포항시");
        map.put("130", "경주시");
        map.put("150", "김천시");
        map.put("170", "안동시");
        map.put("190", "구미시");
        map.put("210", "영주시");
        map.put("230", "영천시");
        map.put("250", "상주시");
        map.put("280", "문경시");
        map.put("290", "경산시");
        return map.get(code);
    }
    
    private String getSignguNmFromCodeForGyeongnam(String code) {
        Map<String, String> map = new HashMap<>();
        map.put("120", "창원시");
        map.put("170", "진주시");
        map.put("220", "통영시");
        map.put("240", "사천시");
        map.put("250", "김해시");
        map.put("270", "밀양시");
        map.put("310", "거제시");
        map.put("330", "양산시");
        return map.get(code);
    }
    
    private String getSignguNmFromCodeForGangwon(String code) {
        Map<String, String> map = new HashMap<>();
        map.put("110", "춘천시");
        map.put("130", "원주시");
        map.put("150", "강릉시");
        map.put("170", "동해시");
        map.put("190", "태백시");
        map.put("210", "속초시");
        map.put("230", "삼척시");
        return map.get(code);
    }
    
    private String getSignguNmFromCodeForJeju(String code) {
        Map<String, String> map = new HashMap<>();
        map.put("110", "제주시");
        map.put("130", "서귀포시");
        return map.get(code);
    }
    
    /**
     * 코드로 시군구명 반환
     */
    private String getSignguNmFromCode(String code, String region) {
        if ("서울".equals(region)) {
            Map<String, String> seoulMap = new HashMap<>();
            seoulMap.put("680", "강남구");
            seoulMap.put("650", "서초구");
            seoulMap.put("710", "송파구");
            seoulMap.put("740", "강동구");
            seoulMap.put("110", "종로구");
            seoulMap.put("140", "중구");
            seoulMap.put("170", "용산구");
            seoulMap.put("200", "성동구");
            seoulMap.put("215", "광진구");
            seoulMap.put("230", "동대문구");
            seoulMap.put("260", "중랑구");
            seoulMap.put("290", "성북구");
            seoulMap.put("305", "강북구");
            seoulMap.put("320", "도봉구");
            seoulMap.put("350", "노원구");
            seoulMap.put("380", "은평구");
            seoulMap.put("410", "서대문구");
            seoulMap.put("440", "마포구");
            seoulMap.put("470", "양천구");
            seoulMap.put("500", "강서구");
            seoulMap.put("530", "구로구");
            seoulMap.put("545", "금천구");
            seoulMap.put("560", "영등포구");
            seoulMap.put("590", "동작구");
            seoulMap.put("620", "관악구");
            return seoulMap.get(code);
        } else if ("부산".equals(region)) {
            Map<String, String> busanMap = new HashMap<>();
            busanMap.put("350", "해운대구");
            busanMap.put("500", "수영구");
            busanMap.put("290", "남구");
            busanMap.put("380", "사하구");
            busanMap.put("140", "서구");
            busanMap.put("200", "영도구");
            busanMap.put("110", "중구");
            busanMap.put("320", "북구");
            busanMap.put("440", "강서구");
            busanMap.put("410", "금정구");
            busanMap.put("230", "부산진구");
            busanMap.put("260", "동래구");
            busanMap.put("470", "연제구");
            busanMap.put("530", "사상구");
            return busanMap.get(code);
        } else if ("경기".equals(region)) {
            Map<String, String> gyeonggiMap = new HashMap<>();
            gyeonggiMap.put("111", "수원시");
            gyeonggiMap.put("147", "용인시");
            gyeonggiMap.put("157", "화성시");
            gyeonggiMap.put("137", "오산시");
            gyeonggiMap.put("123", "평택시");
            gyeonggiMap.put("129", "고양시");
            gyeonggiMap.put("149", "파주시");
            gyeonggiMap.put("115", "의정부시");
            gyeonggiMap.put("161", "양주시");
            gyeonggiMap.put("125", "동두천시");
            gyeonggiMap.put("113", "성남시");
            gyeonggiMap.put("159", "광주시");
            gyeonggiMap.put("145", "하남시");
            gyeonggiMap.put("135", "남양주시");
            gyeonggiMap.put("153", "안성시");
            gyeonggiMap.put("165", "여주시");
            gyeonggiMap.put("119", "부천시");
            gyeonggiMap.put("155", "김포시");
            gyeonggiMap.put("139", "시흥시");
            gyeonggiMap.put("127", "안산시");
            gyeonggiMap.put("117", "안양시");
            gyeonggiMap.put("141", "군포시");
            gyeonggiMap.put("131", "과천시");
            gyeonggiMap.put("143", "의왕시");
            gyeonggiMap.put("133", "구리시");
            gyeonggiMap.put("163", "포천시");
            return gyeonggiMap.get(code);
        }
        return null;
    }

    
    /**
     * 권역 선택에 따라 해당하는 구/시 코드 목록 반환
     * 20개 내외 데이터가 나오도록 최적화된 조합
     */
    private List<String> getRegionCodes(String region) {
        if (region == null || region.trim().isEmpty()) {
            return List.of();
        }
        
        Map<String, List<String>> regionMap = new HashMap<>();
        
        // 서울 권역 (20개 내외로 재조합)
        regionMap.put("서울_강남권", List.of("680", "650")); // 강남구, 서초구 (2개)
        regionMap.put("서울_강동권", List.of("710", "740")); // 송파구, 강동구 (2개)
        regionMap.put("서울_도심권", List.of("110", "140", "170")); // 종로구, 중구, 용산구 (3개)
        regionMap.put("서울_동북권1", List.of("200", "215", "230", "260")); // 성동구, 광진구, 동대문구, 중랑구 (4개)
        regionMap.put("서울_동북권2", List.of("290", "305", "320", "350")); // 성북구, 강북구, 도봉구, 노원구 (4개)
        regionMap.put("서울_서북권", List.of("380", "410", "440")); // 은평구, 서대문구, 마포구 (3개)
        regionMap.put("서울_서남권1", List.of("470", "500", "530", "545")); // 양천구, 강서구, 구로구, 금천구 (4개)
        regionMap.put("서울_서남권2", List.of("560", "590", "620")); // 영등포구, 동작구, 관악구 (3개)
        
        // 경기도 권역 (20개 내외로 재조합)
        regionMap.put("경기_수원권", List.of("111")); // 수원시 (1개)
        regionMap.put("경기_용인권", List.of("147")); // 용인시 (1개)
        regionMap.put("경기_화성권", List.of("157")); // 화성시 (1개)
        regionMap.put("경기_남부권", List.of("137", "123")); // 오산, 평택 (2개)
        regionMap.put("경기_고양권", List.of("129")); // 고양시 (1개)
        regionMap.put("경기_파주권", List.of("149")); // 파주시 (1개)
        regionMap.put("경기_의정부권", List.of("115", "161", "125")); // 의정부, 양주, 동두천 (3개)
        regionMap.put("경기_성남권", List.of("113")); // 성남시 (1개)
        regionMap.put("경기_광주하남권", List.of("159", "145")); // 광주(경기), 하남 (2개)
        regionMap.put("경기_남양주권", List.of("135")); // 남양주시 (1개)
        regionMap.put("경기_안성여주권", List.of("153", "165")); // 안성, 여주 (2개)
        regionMap.put("경기_부천권", List.of("119")); // 부천시 (1개)
        regionMap.put("경기_김포권", List.of("155")); // 김포시 (1개)
        regionMap.put("경기_시흥안산권", List.of("139", "127")); // 시흥, 안산 (2개)
        regionMap.put("경기_안양군포권", List.of("117", "141")); // 안양, 군포 (2개)
        regionMap.put("경기_과천의왕권", List.of("131", "143")); // 과천, 의왕 (2개)
        regionMap.put("경기_구리포천권", List.of("133", "163")); // 구리, 포천 (2개)
        
        // 부산 권역 (20개 내외로 재조합)
        regionMap.put("부산_해운대권", List.of("350", "500")); // 해운대구, 수영구 (2개)
        regionMap.put("부산_남구", List.of("290")); // 남구 (1개)
        regionMap.put("부산_서부권", List.of("380", "140", "200", "110")); // 사하구, 서구, 영도구, 중구 (4개)
        regionMap.put("부산_북부권", List.of("320", "440", "410")); // 북구, 강서구, 금정구 (3개)
        regionMap.put("부산_중부권", List.of("230", "260")); // 부산진구, 동래구 (2개)
        regionMap.put("부산_연제사상권", List.of("470", "530")); // 연제구, 사상구 (2개)
        
        // 나머지 광역시/도는 시/군 단위로 세분화 (20개 내외 목표)
        // 인천광역시 - 구 단위로 분리
        regionMap.put("인천_중구", List.of("110")); // 인천 중구
        List<String> incheon1 = new ArrayList<>();
        incheon1.add("140"); incheon1.add("177");
        regionMap.put("인천_동구미추홀", incheon1); // 인천 동구, 미추홀구
        List<String> incheon2 = new ArrayList<>();
        incheon2.add("185"); incheon2.add("200");
        regionMap.put("인천_연수남동", incheon2); // 인천 연수구, 남동구
        List<String> incheon3 = new ArrayList<>();
        incheon3.add("237"); incheon3.add("245");
        regionMap.put("인천_부평계양", incheon3); // 인천 부평구, 계양구
        List<String> incheon4 = new ArrayList<>();
        incheon4.add("260"); incheon4.add("710");
        regionMap.put("인천_서구강화", incheon4); // 인천 서구, 강화군
        
        // 대구광역시 - 구 단위로 분리
        List<String> daegu1 = new ArrayList<>();
        daegu1.add("110"); daegu1.add("140");
        regionMap.put("대구_중구동구", daegu1); // 대구 중구, 동구
        List<String> daegu2 = new ArrayList<>();
        daegu2.add("170"); daegu2.add("200");
        regionMap.put("대구_서구남구", daegu2); // 대구 서구, 남구
        List<String> daegu3 = new ArrayList<>();
        daegu3.add("230"); daegu3.add("260");
        regionMap.put("대구_북구수성", daegu3); // 대구 북구, 수성구
        List<String> daegu4 = new ArrayList<>();
        daegu4.add("290"); daegu4.add("710");
        regionMap.put("대구_달서달성", daegu4); // 대구 달서구, 달성군
        
        // 대전광역시 - 구 단위로 분리
        List<String> daejeon1 = new ArrayList<>();
        daejeon1.add("110"); daejeon1.add("140");
        regionMap.put("대전_동구중구", daejeon1); // 대전 동구, 중구
        List<String> daejeon2 = new ArrayList<>();
        daejeon2.add("170"); daejeon2.add("200");
        regionMap.put("대전_서구유성", daejeon2); // 대전 서구, 유성구
        regionMap.put("대전_대덕구", List.of("230")); // 대전 대덕구
        
        // 울산광역시 - 구 단위로 분리
        List<String> ulsan1 = new ArrayList<>();
        ulsan1.add("110"); ulsan1.add("140");
        regionMap.put("울산_중구남구", ulsan1); // 울산 중구, 남구
        List<String> ulsan2 = new ArrayList<>();
        ulsan2.add("170"); ulsan2.add("200");
        regionMap.put("울산_동구북구", ulsan2); // 울산 동구, 북구
        regionMap.put("울산_울주군", List.of("710")); // 울산 울주군
        
        // 충청북도 - 시/군 단위로 세분화 (198건이므로 세분화 필요)
        regionMap.put("충북_청주시", List.of("110")); // 청주시
        List<String> chungbuk1 = new ArrayList<>();
        chungbuk1.add("130"); chungbuk1.add("150");
        regionMap.put("충북_충주제천", chungbuk1); // 충주시, 제천시
        List<String> chungbuk2 = new ArrayList<>();
        chungbuk2.add("720"); chungbuk2.add("730"); chungbuk2.add("740");
        regionMap.put("충북_보은옥천영동", chungbuk2); // 보은군, 옥천군, 영동군
        List<String> chungbuk3 = new ArrayList<>();
        chungbuk3.add("745"); chungbuk3.add("750"); chungbuk3.add("760");
        regionMap.put("충북_증평진천괴산", chungbuk3); // 증평군, 진천군, 괴산군
        List<String> chungbuk4 = new ArrayList<>();
        chungbuk4.add("770"); chungbuk4.add("800");
        regionMap.put("충북_음성단양", chungbuk4); // 음성군, 단양군
        
        // 충청남도 - 시 단위로 세분화
        regionMap.put("충남_천안시", List.of("130")); // 천안시
        List<String> chungnam1 = new ArrayList<>();
        chungnam1.add("133"); chungnam1.add("150");
        regionMap.put("충남_공주보령", chungnam1); // 공주시, 보령시
        List<String> chungnam2 = new ArrayList<>();
        chungnam2.add("180"); chungnam2.add("200");
        regionMap.put("충남_아산서산", chungnam2); // 아산시, 서산시
        List<String> chungnam3 = new ArrayList<>();
        chungnam3.add("210"); chungnam3.add("230"); chungnam3.add("250");
        regionMap.put("충남_논산계룡당진", chungnam3); // 논산시, 계룡시, 당진시
        
        // 전라북도 - 시 단위로 세분화
        regionMap.put("전북_전주시", List.of("110")); // 전주시
        List<String> jeonbuk1 = new ArrayList<>();
        jeonbuk1.add("130"); jeonbuk1.add("140");
        regionMap.put("전북_군산익산", jeonbuk1); // 군산시, 익산시
        List<String> jeonbuk2 = new ArrayList<>();
        jeonbuk2.add("180"); jeonbuk2.add("190"); jeonbuk2.add("210");
        regionMap.put("전북_정읍남원김제", jeonbuk2); // 정읍시, 남원시, 김제시
        
        // 전라남도 - 시 단위로 세분화
        List<String> jeonnam1 = new ArrayList<>();
        jeonnam1.add("110"); jeonnam1.add("130");
        regionMap.put("전남_목포여수", jeonnam1); // 목포시, 여수시
        List<String> jeonnam2 = new ArrayList<>();
        jeonnam2.add("150"); jeonnam2.add("170"); jeonnam2.add("230");
        regionMap.put("전남_순천나주광양", jeonnam2); // 순천시, 나주시, 광양시
        
        // 경상북도 - 시 단위로 세분화
        List<String> gyeongbuk1 = new ArrayList<>();
        gyeongbuk1.add("110"); gyeongbuk1.add("130");
        regionMap.put("경북_포항경주", gyeongbuk1); // 포항시, 경주시
        List<String> gyeongbuk2 = new ArrayList<>();
        gyeongbuk2.add("150"); gyeongbuk2.add("170"); gyeongbuk2.add("190");
        regionMap.put("경북_김천안동구미", gyeongbuk2); // 김천시, 안동시, 구미시
        List<String> gyeongbuk3 = new ArrayList<>();
        gyeongbuk3.add("210"); gyeongbuk3.add("230"); gyeongbuk3.add("250");
        regionMap.put("경북_영주영천상주", gyeongbuk3); // 영주시, 영천시, 상주시
        List<String> gyeongbuk4 = new ArrayList<>();
        gyeongbuk4.add("280"); gyeongbuk4.add("290");
        regionMap.put("경북_문경경산", gyeongbuk4); // 문경시, 경산시
        
        // 경상남도 - 시 단위로 세분화
        regionMap.put("경남_창원시", List.of("120")); // 창원시
        List<String> gyeongnam1 = new ArrayList<>();
        gyeongnam1.add("170"); gyeongnam1.add("220");
        regionMap.put("경남_진주통영", gyeongnam1); // 진주시, 통영시
        List<String> gyeongnam2 = new ArrayList<>();
        gyeongnam2.add("240"); gyeongnam2.add("250"); gyeongnam2.add("270");
        regionMap.put("경남_사천김해밀양", gyeongnam2); // 사천시, 김해시, 밀양시
        List<String> gyeongnam3 = new ArrayList<>();
        gyeongnam3.add("310"); gyeongnam3.add("330");
        regionMap.put("경남_거제양산", gyeongnam3); // 거제시, 양산시
        
        // 강원도 - 시 단위로 세분화
        List<String> gangwon1 = new ArrayList<>();
        gangwon1.add("110"); gangwon1.add("130");
        regionMap.put("강원_춘천원주", gangwon1); // 춘천시, 원주시
        List<String> gangwon2 = new ArrayList<>();
        gangwon2.add("150"); gangwon2.add("170"); gangwon2.add("190");
        regionMap.put("강원_강릉동해태백", gangwon2); // 강릉시, 동해시, 태백시
        List<String> gangwon3 = new ArrayList<>();
        gangwon3.add("210"); gangwon3.add("230");
        regionMap.put("강원_속초삼척", gangwon3); // 속초시, 삼척시
        
        // 세종특별자치도
        regionMap.put("세종특별자치도", List.of("110")); // 세종시
        
        // 광주광역시 - 구 단위로 분리
        List<String> gwangju1 = new ArrayList<>();
        gwangju1.add("110"); gwangju1.add("140");
        regionMap.put("광주_동구서구", gwangju1); // 광주 동구, 서구
        List<String> gwangju2 = new ArrayList<>();
        gwangju2.add("155"); gwangju2.add("170");
        regionMap.put("광주_남구북구", gwangju2); // 광주 남구, 북구
        regionMap.put("광주_광산구", List.of("200")); // 광주 광산구
        
        // 제주특별자치도
        regionMap.put("제주_제주시", List.of("110")); // 제주시
        regionMap.put("제주_서귀포시", List.of("130")); // 서귀포시
        
        return regionMap.getOrDefault(region, List.of());
    }
    
    // HousingInfo 엔티티를 Map으로 변환 (요청된 필드만)
    private Map<String, Object> convertToMap(HousingInfo housingInfo) {
        Map<String, Object> map = new LinkedHashMap<>(6);
        map.put("hsmpSn", housingInfo.getHsmpSn()); // 매칭용 식별자
        map.put("brtcNm", housingInfo.getBrtcNm());
        map.put("hsmpNm", housingInfo.getHsmpNm());
        map.put("signguCode", housingInfo.getSignguNm()); // signguNm을 signguCode로 전송
        map.put("hshldCo", housingInfo.getHshldCo());
        map.put("bassRentGtn", housingInfo.getBassRentGtn());
        map.put("bassMtRntchrg", housingInfo.getBassMtRntchrg());
        return map;
    }
    
    /**
     * SSE를 통한 스트리밍 추천 응답
     */
    public SseEmitter getRecommendationsStream(String userPrompt, String region) {
        SseEmitter emitter = new SseEmitter(300000L); // 5분 타임아웃
        
        // 비동기로 처리
        CompletableFuture.runAsync(() -> {
            try {
                // 1단계: DB 조회 시작 알림
                emitter.send(SseEmitter.event()
                    .name("status")
                    .data("{\"status\":\"loading\",\"message\":\"주거정보 조회 중...\"}"));
                
                // 권역별 구/시 코드 목록 가져오기
                List<String> regionCodes = getRegionCodes(region);
                
                if (regionCodes == null || regionCodes.isEmpty()) {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"error\":\"권역 정보가 없거나 매핑되지 않음\"}"));
                    emitter.complete();
                    return;
                }
                
                // DB 쿼리 최적화: DB 레벨에서 필터링
                List<HousingInfo> allHousingInfo;
                if (regionCodes.size() == 1 && regionCodes.get(0).length() == 2) {
                    String brtcCode = regionCodes.get(0);
                    String brtcNm = getBrtcNmFromCode(brtcCode);
                    if (brtcNm != null) {
                        allHousingInfo = housingInfoRepository.findByBrtcNmLike(brtcNm);
                    } else {
                        allHousingInfo = List.of();
                    }
                } else {
                    allHousingInfo = getHousingInfoByRegionCodes(region, regionCodes);
                }
                
                // 2단계: 데이터 조회 완료 알림
                emitter.send(SseEmitter.event()
                    .name("status")
                    .data(String.format("{\"status\":\"processing\",\"message\":\"%d건의 주거정보를 AI에 전송 중...\"}", allHousingInfo.size())));
                
                if (allHousingInfo.isEmpty()) {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"error\":\"필터링된 주거정보가 없습니다\"}"));
                    emitter.complete();
                    return;
                }
                
                // 병렬 처리: 데이터 변환
                List<Map<String, Object>> housingDataList = allHousingInfo.parallelStream()
                        .map(this::convertToMap)
                        .collect(Collectors.toList());
                
                Map<String, HousingInfo> housingInfoMap = allHousingInfo.parallelStream()
                        .collect(Collectors.toConcurrentMap(
                                HousingInfo::getHsmpSn,
                                info -> info,
                                (existing, replacement) -> existing
                        ));
                
                // 3단계: AI 처리 시작 알림
                emitter.send(SseEmitter.event()
                    .name("status")
                    .data("{\"status\":\"ai_processing\",\"message\":\"AI 추천 분석 중...\"}"));
                
                // AI API 호출
                List<UpstageAiService.RecommendationResult> recommendationResults = 
                        upstageAiService.getRecommendations(userPrompt, housingDataList);
                
                // 4단계: 결과 스트리밍
                List<RecommendationResponse.HousingRecommendation> recommendations = new ArrayList<>();
                for (int i = 0; i < recommendationResults.size() && i < 5; i++) {
                    UpstageAiService.RecommendationResult result = recommendationResults.get(i);
                    HousingInfo housingInfo = housingInfoMap.get(result.getHsmpSn());
                    
                    if (housingInfo != null) {
                        RecommendationResponse.HousingRecommendation recommendation = 
                                new RecommendationResponse.HousingRecommendation(
                                    i + 1,
                                    housingInfo,
                                    result.getReason()
                                );
                        recommendations.add(recommendation);
                        
                        // 각 추천 결과를 개별적으로 스트리밍
                        emitter.send(SseEmitter.event()
                            .name("recommendation")
                            .data(recommendation));
                    }
                }
                
                // 5단계: 완료 알림
                emitter.send(SseEmitter.event()
                    .name("complete")
                    .data(String.format("{\"status\":\"complete\",\"count\":%d}", recommendations.size())));
                
                emitter.complete();
                
            } catch (Exception e) {
                log.error("스트리밍 추천 처리 중 오류 발생", e);
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"error\":\"" + e.getMessage() + "\"}"));
                } catch (Exception ex) {
                    log.error("에러 전송 실패", ex);
                }
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }
}