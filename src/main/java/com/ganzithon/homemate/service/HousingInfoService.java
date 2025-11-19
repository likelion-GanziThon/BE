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

        return HousingInfo.of(
                hsmpSnStr,
                item.getBrtcNm(),
                item.getSignguNm(),
                item.getHsmpNm(),
                parseInteger(item.getHshldCo()),
                parseLong(item.getBassRentGtn()),
                parseLong(item.getBassMtRntchrg())
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

    // AI를 활용한 주거정보 추천 (TOP5)
    @Transactional(readOnly = true)
    public RecommendationResponse getRecommendations(String userPrompt, String region) {
        try {
            // 프롬프트 검증
            if (userPrompt == null || userPrompt.trim().isEmpty()) {
                throw new IllegalArgumentException("추천 받고 싶은 내용을 입력해주세요.");
            }
            
            // region 검증
            if (region == null || region.trim().isEmpty()) {
                throw new IllegalArgumentException("지역을 선택해주세요.");
            }
            
            log.info("AI 추천 요청 수신: prompt={}, region={}", userPrompt, region);

            List<String> regionCodes = getRegionCodes(region);

            if (regionCodes == null || regionCodes.isEmpty()) {
                log.warn("권역 정보가 없거나 매핑되지 않음: {}", region);
                throw new IllegalArgumentException("선택하신 지역 정보가 올바르지 않습니다. 다른 지역을 선택해주세요.");
            }

            List<HousingInfo> allHousingInfo;
            long startTime = System.currentTimeMillis();

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

            long queryTime = System.currentTimeMillis() - startTime;
            log.info("권역 '{}'에 해당하는 주거정보: {}건 조회 완료 (쿼리 시간: {}ms)", region, allHousingInfo.size(), queryTime);

            if (allHousingInfo.isEmpty()) {
                log.warn("필터링된 주거정보가 없습니다. region={}", region);
                throw new IllegalArgumentException("선택하신 지역에 주거정보 데이터가 없습니다. 다른 지역을 선택해주세요.");
            }

            // AI 전송 전 데이터 제한: 최대 10개로 제한 (30초 내외 응답 목표)
            List<HousingInfo> limitedHousingInfo = allHousingInfo.size() > 10
                ? allHousingInfo.subList(0, 10)
                : allHousingInfo;

            // AI API 전송용 데이터 준비: 엔티티를 Map으로 변환 (필요한 필드만 추출)
            long convertStartTime = System.currentTimeMillis();
            List<Map<String, Object>> housingDataList = limitedHousingInfo.parallelStream()
                    .map(this::convertToMap)
                    .collect(Collectors.toList());
            long convertTime = System.currentTimeMillis() - convertStartTime;
            log.info("AI 전송용 데이터 준비 완료: {}건/{}건 (준비 시간: {}ms)", housingDataList.size(), allHousingInfo.size(), convertTime);

            // hsmpSn -> HousingInfo 매핑 생성 (추천 결과 검증 및 조회용)
            Map<String, HousingInfo> housingInfoMap = allHousingInfo.stream()
                    .collect(Collectors.toMap(HousingInfo::getHsmpSn, info -> info));

            long aiStartTime = System.currentTimeMillis();
            List<UpstageAiService.RecommendationResult> recommendationResults =
                    upstageAiService.getRecommendations(userPrompt, housingDataList);
            long aiTime = System.currentTimeMillis() - aiStartTime;
            log.info("AI 추천 결과: {}건 추천됨 (AI 처리 시간: {}ms)", recommendationResults.size(), aiTime);

            List<RecommendationResponse.HousingRecommendation> recommendations = new ArrayList<>();
            for (int i = 0; i < recommendationResults.size() && i < 5; i++) {
                UpstageAiService.RecommendationResult result = recommendationResults.get(i);
                
                // hsmpSn이 유효한지 확인하고 HousingInfo 조회
                HousingInfo housingInfo = housingInfoMap.get(result.getHsmpSn());
                if (housingInfo != null) {
                    // HousingInfo를 HousingInfoDto로 변환
                    RecommendationResponse.HousingInfoDto housingInfoDto = 
                            new RecommendationResponse.HousingInfoDto(
                                    housingInfo.getId(),
                                    housingInfo.getHsmpSn(),
                                    housingInfo.getBrtcNm(),
                                    housingInfo.getSignguNm(),
                                    housingInfo.getHsmpNm(),
                                    housingInfo.getHshldCo(),
                                    housingInfo.getBassRentGtn(),
                                    housingInfo.getBassMtRntchrg()
                            );
                    
                    recommendations.add(new RecommendationResponse.HousingRecommendation(
                            i + 1,
                            housingInfoDto,
                            result.getReason()
                    ));
                }
            }

            log.info("최종 추천 결과: {}건 반환 (총 처리 시간: {}ms)", recommendations.size(), System.currentTimeMillis() - startTime);
            return new RecommendationResponse(recommendations);

        } catch (IllegalArgumentException e) {
            // 검증 오류는 그대로 전달
            log.warn("AI 추천 요청 검증 실패: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("AI 추천 처리 중 오류 발생", e);
            throw new RuntimeException("AI 추천 서비스 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    // AI를 활용한 주거정보 추천 (TOP5) - SSE 스트리밍 버전
    public SseEmitter getRecommendationsStream(String userPrompt, String region) {
        log.info("스트리밍 추천 요청: prompt={}, region={}", userPrompt, region);
        
        SseEmitter emitter = new SseEmitter(180000L); // 3분 타임아웃
        
        try {
            RecommendationResponse response = getRecommendations(userPrompt, region);
            
            if (response.recommendations().isEmpty()) {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"error\":\"권역에 해당하는 주거정보가 없습니다.\"}"));
                emitter.complete();
                return emitter;
            }
            
            // 추천 결과를 스트리밍으로 전송
            for (RecommendationResponse.HousingRecommendation recommendation : response.recommendations()) {
                com.fasterxml.jackson.databind.ObjectMapper objectMapper = 
                    new com.fasterxml.jackson.databind.ObjectMapper();
                String json = objectMapper.writeValueAsString(recommendation);
                
                emitter.send(SseEmitter.event()
                    .name("recommendation")
                    .data(json));
                
                // 약간의 지연을 두어 스트리밍 효과 제공
                Thread.sleep(200);
            }
            
            emitter.send(SseEmitter.event()
                .name("complete")
                .data("{\"status\":\"완료\"}"));
            emitter.complete();
            
        } catch (Exception e) {
            log.error("스트리밍 추천 중 오류 발생", e);
            emitter.completeWithError(e);
        }
        
        return emitter;
    }

    // ========== 헬퍼 메서드: Region 코드 처리 ==========
    
    /**
     * region 이름에서 brtcNm 추출
     * 예: "서울_강남권" -> "서울특별시", "충북_청주시" -> "충청북도"
     */
    private String extractBrtcNmFromRegion(String region) {
        if (region == null || region.isEmpty()) {
            return null;
        }
        
        // 정식 이름만 체크 (예: "경기도_성남시", "서울특별시_강남구")
        if (region.startsWith("서울특별시")) {
            return "서울특별시";
        } else if (region.startsWith("부산광역시")) {
            return "부산광역시";
        } else if (region.startsWith("대구광역시")) {
            return "대구광역시";
        } else if (region.startsWith("인천광역시")) {
            return "인천광역시";
        } else if (region.startsWith("광주광역시")) {
            return "광주광역시";
        } else if (region.startsWith("대전광역시")) {
            return "대전광역시";
        } else if (region.startsWith("울산광역시")) {
            return "울산광역시";
        } else if (region.startsWith("세종특별자치시")) {
            return "세종특별자치시";
        } else if (region.startsWith("경기도")) {
            return "경기도";
        } else if (region.startsWith("강원특별자치도")) {
            return "강원특별자치도";
        } else if (region.startsWith("충청북도")) {
            return "충청북도";
        } else if (region.startsWith("충청남도")) {
            return "충청남도";
        } else if (region.startsWith("전북특별자치도")) {
            return "전북특별자치도";
        } else if (region.startsWith("전라남도")) {
            return "전라남도";
        } else if (region.startsWith("경상북도")) {
            return "경상북도";
        } else if (region.startsWith("경상남도")) {
            return "경상남도";
        } else if (region.startsWith("제주특별자치도")) {
            return "제주특별자치도";
        }
        
        log.warn("알 수 없는 region: {}", region);
        return null;
    }
    
    /**
     * region과 code로 signguNm 추출
     * 예: region="서울_강남구,서초구", code="680" -> "강남구"
     */
    private String getSignguNmFromCodeByRegion(String region, String code) {
        if (region == null || code == null) {
            return null;
        }
        
        if (region.startsWith("서울")) {
            return getSignguNmFromCodeForSeoul(code);
        } else if (region.startsWith("부산")) {
            return getSignguNmFromCodeForBusan(code);
        } else if (region.startsWith("대구")) {
            return getSignguNmFromCodeForDaegu(code);
        } else if (region.startsWith("인천")) {
            return getSignguNmFromCodeForIncheon(code);
        } else if (region.startsWith("경기")) {
            return getSignguNmFromCodeForGyeonggi(code);
        } else if (region.startsWith("충북")) {
            return getSignguNmFromCodeForChungbuk(code);
        } else if (region.startsWith("충남")) {
            return getSignguNmFromCodeForChungnam(code);
        } else if (region.startsWith("전북")) {
            return getSignguNmFromCodeForJeonbuk(code);
        } else if (region.startsWith("전남")) {
            return getSignguNmFromCodeForJeonnam(code);
        } else if (region.startsWith("경북")) {
            return getSignguNmFromCodeForGyeongbuk(code);
        } else if (region.startsWith("경남")) {
            return getSignguNmFromCodeForGyeongnam(code);
        }
        
        log.warn("알 수 없는 region 또는 code: region={}, code={}", region, code);
        return null;
    }
    
    /**
     * region 이름에서 signguNm 목록 추출
     * 예: "서울_강남구,서초구" -> ["강남구", "서초구"]
     * 예: "경기_수원시,성남시" -> ["수원시", "성남시"]
     */
    private List<String> extractSignguNmsFromRegion(String region) {
        if (region == null || region.isEmpty()) {
            return List.of();
        }
        
        // region 형식: "서울_강남구,서초구" 또는 "경기_수원시,성남시"
        int underscoreIndex = region.indexOf('_');
        if (underscoreIndex == -1) {
            return List.of();
        }
        
        String signguPart = region.substring(underscoreIndex + 1);
        if (signguPart.isEmpty()) {
            return List.of();
        }
        
        // 쉼표로 구분된 구/시 이름들 추출
        return java.util.Arrays.stream(signguPart.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
    
    /**
     * region별 코드 목록 반환
     * region 이름에서 직접 signguNm을 추출하고, 이를 코드로 변환
     * 예: "서울_강남구,서초구" -> ["680", "650"]
     */
    private List<String> getRegionCodes(String region) {
        if (region == null || region.isEmpty()) {
            return List.of();
        }
        
        // 전체 광역시/도 단위 (2자리 코드) - 정식 이름만 지원
        Map<String, String> codeMap = new HashMap<>();
        codeMap.put("서울특별시", "11");
        codeMap.put("부산광역시", "26");
        codeMap.put("대구광역시", "27");
        codeMap.put("인천광역시", "28");
        codeMap.put("광주광역시", "29");
        codeMap.put("대전광역시", "30");
        codeMap.put("울산광역시", "31");
        codeMap.put("세종특별자치시", "36");
        codeMap.put("경기도", "41");
        codeMap.put("강원특별자치도", "42");
        codeMap.put("충청북도", "43");
        codeMap.put("충청남도", "44");
        codeMap.put("전북특별자치도", "45");
        codeMap.put("전라남도", "46");
        codeMap.put("경상북도", "47");
        codeMap.put("경상남도", "48");
        codeMap.put("제주특별자치도", "50");
        
        String regionCode = codeMap.get(region);
        if (regionCode != null) {
            return List.of(regionCode);
        }
        
        // region 이름에서 signguNm 목록 추출 (예: "서울_강남구,서초구")
        List<String> signguNms = extractSignguNmsFromRegion(region);
        if (signguNms.isEmpty()) {
            log.warn("region에서 signguNm을 추출할 수 없음: {}", region);
            return List.of();
        }
        
        // 각 signguNm을 코드로 변환
        List<String> codes = new ArrayList<>();
        for (String signguNm : signguNms) {
            String code = getCodeFromSignguNmByRegion(region, signguNm);
            if (code != null) {
                codes.add(code);
            }
        }
        
        return codes;
    }
    
    /**
     * region과 signguNm으로 코드 추출 (역방향 매핑)
     * 예: region="서울_강남구,서초구", signguNm="강남구" -> "680"
     */
    private String getCodeFromSignguNmByRegion(String region, String signguNm) {
        if (region == null || signguNm == null) {
            return null;
        }
        
        // 정식 이름만 지원
        if (region.startsWith("서울특별시")) {
            return getCodeFromSignguNmForSeoul(signguNm);
        } else if (region.startsWith("부산광역시")) {
            return getCodeFromSignguNmForBusan(signguNm);
        } else if (region.startsWith("대구광역시")) {
            return getCodeFromSignguNmForDaegu(signguNm);
        } else if (region.startsWith("인천광역시")) {
            return getCodeFromSignguNmForIncheon(signguNm);
        } else if (region.startsWith("경기도")) {
            return getCodeFromSignguNmForGyeonggi(signguNm);
        } else if (region.startsWith("충청북도")) {
            return getCodeFromSignguNmForChungbuk(signguNm);
        } else if (region.startsWith("충청남도")) {
            return getCodeFromSignguNmForChungnam(signguNm);
        } else if (region.startsWith("전북특별자치도")) {
            return getCodeFromSignguNmForJeonbuk(signguNm);
        } else if (region.startsWith("전라남도")) {
            return getCodeFromSignguNmForJeonnam(signguNm);
        } else if (region.startsWith("경상북도")) {
            return getCodeFromSignguNmForGyeongbuk(signguNm);
        } else if (region.startsWith("경상남도")) {
            return getCodeFromSignguNmForGyeongnam(signguNm);
        }
        
        log.warn("알 수 없는 region 또는 signguNm: region={}, signguNm={}", region, signguNm);
        return null;
    }
    
    // ========== 역방향 매핑: signguNm -> code ==========
    
    private String getCodeFromSignguNmForSeoul(String signguNm) {
        Map<String, String> nameMap = new HashMap<>();
        nameMap.put("종로구", "110");
        nameMap.put("중구", "140");
        nameMap.put("용산구", "170");
        nameMap.put("성동구", "200");
        nameMap.put("광진구", "215");
        nameMap.put("동대문구", "230");
        nameMap.put("중랑구", "260");
        nameMap.put("성북구", "290");
        nameMap.put("강북구", "305");
        nameMap.put("도봉구", "320");
        nameMap.put("노원구", "350");
        nameMap.put("은평구", "380");
        nameMap.put("서대문구", "410");
        nameMap.put("마포구", "440");
        nameMap.put("양천구", "470");
        nameMap.put("강서구", "500");
        nameMap.put("구로구", "530");
        nameMap.put("금천구", "545");
        nameMap.put("영등포구", "560");
        nameMap.put("동작구", "590");
        nameMap.put("관악구", "620");
        nameMap.put("서초구", "650");
        nameMap.put("강남구", "680");
        nameMap.put("송파구", "710");
        nameMap.put("강동구", "740");
        return nameMap.get(signguNm);
    }
    
    private String getCodeFromSignguNmForBusan(String signguNm) {
        Map<String, String> nameMap = new HashMap<>();
        nameMap.put("중구", "110");
        nameMap.put("서구", "140");
        nameMap.put("동구", "170");
        nameMap.put("영도구", "200");
        nameMap.put("부산진구", "230");
        nameMap.put("동래구", "260");
        nameMap.put("남구", "290");
        nameMap.put("북구", "320");
        nameMap.put("해운대구", "350");
        nameMap.put("사하구", "380");
        nameMap.put("금정구", "410");
        nameMap.put("강서구", "440");
        nameMap.put("연제구", "470");
        nameMap.put("수영구", "500");
        nameMap.put("사상구", "530");
        nameMap.put("기장군", "710");
        return nameMap.get(signguNm);
    }
    
    private String getCodeFromSignguNmForDaegu(String signguNm) {
        Map<String, String> nameMap = new HashMap<>();
        nameMap.put("중구", "110");
        nameMap.put("동구", "140");
        nameMap.put("서구", "170");
        nameMap.put("남구", "200");
        nameMap.put("북구", "230");
        nameMap.put("수성구", "260");
        nameMap.put("달서구", "290");
        nameMap.put("달성군", "710");
        return nameMap.get(signguNm);
    }
    
    private String getCodeFromSignguNmForIncheon(String signguNm) {
        Map<String, String> nameMap = new HashMap<>();
        nameMap.put("중구", "110");
        nameMap.put("동구", "140");
        nameMap.put("미추홀구", "177");
        nameMap.put("연수구", "185");
        nameMap.put("남동구", "200");
        nameMap.put("부평구", "237");
        nameMap.put("계양구", "245");
        nameMap.put("서구", "260");
        nameMap.put("강화군", "710");
        nameMap.put("옹진군", "720");
        return nameMap.get(signguNm);
    }
    
    private String getCodeFromSignguNmForGyeonggi(String signguNm) {
        Map<String, String> nameMap = new HashMap<>();
        // 구 단위로 세분화된 경우
        nameMap.put("수원시 권선구", "111");
        nameMap.put("수원시 장안구", "111");
        nameMap.put("수원시 영통구", "111");
        nameMap.put("수원시 팔달구", "111");
        nameMap.put("성남시 중원구", "113");
        nameMap.put("성남시 수정구", "113");
        nameMap.put("성남시 분당구", "113");
        // 시 단위
        nameMap.put("수원시", "111");
        nameMap.put("성남시", "113");
        nameMap.put("의정부시", "115");
        nameMap.put("안양시", "117");
        nameMap.put("부천시", "119");
        nameMap.put("광명시", "121");
        nameMap.put("평택시", "123");
        nameMap.put("동두천시", "125");
        nameMap.put("안산시", "127");
        nameMap.put("고양시", "129");
        nameMap.put("과천시", "131");
        nameMap.put("구리시", "133");
        nameMap.put("남양주시", "135");
        nameMap.put("오산시", "137");
        nameMap.put("시흥시", "139");
        nameMap.put("군포시", "141");
        nameMap.put("의왕시", "143");
        nameMap.put("하남시", "145");
        nameMap.put("용인시", "147");
        nameMap.put("파주시", "149");
        nameMap.put("이천시", "151");
        nameMap.put("안성시", "153");
        nameMap.put("김포시", "155");
        nameMap.put("화성시", "157");
        nameMap.put("광주시", "159");
        nameMap.put("양주시", "161");
        nameMap.put("포천시", "163");
        nameMap.put("여주시", "165");
        return nameMap.get(signguNm);
    }
    
    private String getCodeFromSignguNmForChungbuk(String signguNm) {
        Map<String, String> nameMap = new HashMap<>();
        nameMap.put("청주시", "110");
        nameMap.put("충주시", "130");
        nameMap.put("제천시", "150");
        nameMap.put("보은군", "720");
        nameMap.put("옥천군", "730");
        nameMap.put("영동군", "740");
        nameMap.put("증평군", "745");
        nameMap.put("진천군", "750");
        nameMap.put("괴산군", "760");
        nameMap.put("음성군", "770");
        nameMap.put("단양군", "800");
        return nameMap.get(signguNm);
    }
    
    private String getCodeFromSignguNmForChungnam(String signguNm) {
        Map<String, String> nameMap = new HashMap<>();
        // 구 단위로 세분화된 경우
        nameMap.put("천안시 서북구", "130");
        // 시 단위
        nameMap.put("천안시", "130");
        nameMap.put("공주시", "133");
        nameMap.put("보령시", "150");
        nameMap.put("아산시", "180");
        nameMap.put("서산시", "200");
        nameMap.put("논산시", "210");
        nameMap.put("계룡시", "230");
        nameMap.put("당진시", "250");
        return nameMap.get(signguNm);
    }
    
    private String getCodeFromSignguNmForJeonbuk(String signguNm) {
        Map<String, String> nameMap = new HashMap<>();
        nameMap.put("전주시", "110");
        nameMap.put("군산시", "130");
        nameMap.put("익산시", "140");
        nameMap.put("정읍시", "180");
        nameMap.put("남원시", "190");
        nameMap.put("김제시", "210");
        return nameMap.get(signguNm);
    }
    
    private String getCodeFromSignguNmForJeonnam(String signguNm) {
        Map<String, String> nameMap = new HashMap<>();
        nameMap.put("목포시", "110");
        nameMap.put("여수시", "130");
        nameMap.put("순천시", "150");
        nameMap.put("나주시", "170");
        nameMap.put("광양시", "230");
        return nameMap.get(signguNm);
    }
    
    private String getCodeFromSignguNmForGyeongbuk(String signguNm) {
        Map<String, String> nameMap = new HashMap<>();
        nameMap.put("포항시", "110");
        nameMap.put("경주시", "130");
        nameMap.put("김천시", "150");
        nameMap.put("안동시", "170");
        nameMap.put("구미시", "190");
        nameMap.put("영주시", "210");
        nameMap.put("영천시", "230");
        nameMap.put("상주시", "250");
        nameMap.put("문경시", "280");
        nameMap.put("경산시", "290");
        return nameMap.get(signguNm);
    }
    
    private String getCodeFromSignguNmForGyeongnam(String signguNm) {
        Map<String, String> nameMap = new HashMap<>();
        nameMap.put("창원시", "120");
        nameMap.put("진주시", "170");
        nameMap.put("통영시", "220");
        nameMap.put("사천시", "240");
        nameMap.put("김해시", "250");
        nameMap.put("밀양시", "270");
        nameMap.put("거제시", "310");
        nameMap.put("양산시", "330");
        return nameMap.get(signguNm);
    }
    
    /**
     * region과 regionCodes로 주거정보 조회
     */
    private List<HousingInfo> getHousingInfoByRegionCodes(String region, List<String> regionCodes) {
        String brtcNm = extractBrtcNmFromRegion(region);
        if (brtcNm == null) {
            log.warn("brtcNm을 추출할 수 없음: region={}", region);
            return List.of();
        }
        
        // region 이름에서 직접 signguNm 목록 추출 (더 직접적이고 정확함)
        List<String> signguNms = extractSignguNmsFromRegion(region);
        
        if (signguNms.isEmpty()) {
            // fallback: regionCodes에서 signguNm 목록 추출
            signguNms = regionCodes.stream()
                    .map(code -> getSignguNmFromCodeByRegion(region, code))
                    .filter(signguNm -> signguNm != null)
                    .distinct()
                    .collect(Collectors.toList());
        }
        
        if (signguNms.isEmpty()) {
            log.warn("signguNm을 추출할 수 없음: region={}, regionCodes={}", region, regionCodes);
            return List.of();
        }
        
        return housingInfoRepository.findByBrtcNmAndSignguNmIn(brtcNm, signguNms);
    }
    
    /**
     * 코드로 brtcNm 반환
     */
    private String getBrtcNmFromCode(String code) {
        Map<String, String> codeMap = new HashMap<>();
        codeMap.put("11", "서울특별시");
        codeMap.put("26", "부산광역시");
        codeMap.put("27", "대구광역시");
        codeMap.put("28", "인천광역시");
        codeMap.put("29", "광주광역시");
        codeMap.put("30", "대전광역시");
        codeMap.put("31", "울산광역시");
        codeMap.put("36", "세종특별자치시");
        codeMap.put("41", "경기도");
        codeMap.put("42", "강원특별자치도");
        codeMap.put("43", "충청북도");
        codeMap.put("44", "충청남도");
        codeMap.put("45", "전북특별자치도");
        codeMap.put("46", "전라남도");
        codeMap.put("47", "경상북도");
        codeMap.put("48", "경상남도");
        codeMap.put("50", "제주특별자치도");
        return codeMap.get(code);
    }
    
    /**
     * HousingInfo를 Map으로 변환 (AI 전송용, 최소 필드만)
     */
    private Map<String, Object> convertToMap(HousingInfo info) {
        Map<String, Object> map = new LinkedHashMap<>(4);
        map.put("hsmpSn", info.getHsmpSn());
        map.put("nm", info.getHsmpNm() != null && info.getHsmpNm().length() > 20 
                ? info.getHsmpNm().substring(0, 20) 
                : info.getHsmpNm());
        map.put("area", info.getHshldCo());
        map.put("rent", info.getBassMtRntchrg());
        return map;
    }
    
    // ========== 시군구 코드 매핑 메서드들 ==========
    
    private String getSignguNmFromCodeForSeoul(String code) {
        Map<String, String> codeMap = new HashMap<>();
        codeMap.put("110", "종로구");
        codeMap.put("140", "중구");
        codeMap.put("170", "용산구");
        codeMap.put("200", "성동구");
        codeMap.put("215", "광진구");
        codeMap.put("230", "동대문구");
        codeMap.put("260", "중랑구");
        codeMap.put("290", "성북구");
        codeMap.put("305", "강북구");
        codeMap.put("320", "도봉구");
        codeMap.put("350", "노원구");
        codeMap.put("380", "은평구");
        codeMap.put("410", "서대문구");
        codeMap.put("440", "마포구");
        codeMap.put("470", "양천구");
        codeMap.put("500", "강서구");
        codeMap.put("530", "구로구");
        codeMap.put("545", "금천구");
        codeMap.put("560", "영등포구");
        codeMap.put("590", "동작구");
        codeMap.put("620", "관악구");
        codeMap.put("650", "서초구");
        codeMap.put("680", "강남구");
        codeMap.put("710", "송파구");
        codeMap.put("740", "강동구");
        return codeMap.get(code);
    }
    
    private String getSignguNmFromCodeForBusan(String code) {
        Map<String, String> codeMap = new HashMap<>();
        codeMap.put("110", "중구");
        codeMap.put("140", "서구");
        codeMap.put("170", "동구");
        codeMap.put("200", "영도구");
        codeMap.put("230", "부산진구");
        codeMap.put("260", "동래구");
        codeMap.put("290", "남구");
        codeMap.put("320", "북구");
        codeMap.put("350", "해운대구");
        codeMap.put("380", "사하구");
        codeMap.put("410", "금정구");
        codeMap.put("440", "강서구");
        codeMap.put("470", "연제구");
        codeMap.put("500", "수영구");
        codeMap.put("530", "사상구");
        codeMap.put("710", "기장군");
        return codeMap.get(code);
    }
    
    private String getSignguNmFromCodeForDaegu(String code) {
        Map<String, String> codeMap = new HashMap<>();
        codeMap.put("110", "중구");
        codeMap.put("140", "동구");
        codeMap.put("170", "서구");
        codeMap.put("200", "남구");
        codeMap.put("230", "북구");
        codeMap.put("260", "수성구");
        codeMap.put("290", "달서구");
        codeMap.put("710", "달성군");
        return codeMap.get(code);
    }
    
    private String getSignguNmFromCodeForIncheon(String code) {
        Map<String, String> codeMap = new HashMap<>();
        codeMap.put("110", "중구");
        codeMap.put("140", "동구");
        codeMap.put("177", "미추홀구");
        codeMap.put("185", "연수구");
        codeMap.put("200", "남동구");
        codeMap.put("237", "부평구");
        codeMap.put("245", "계양구");
        codeMap.put("260", "서구");
        codeMap.put("710", "강화군");
        codeMap.put("720", "옹진군");
        return codeMap.get(code);
    }
    
    private String getSignguNmFromCodeForGyeonggi(String code) {
        Map<String, String> codeMap = new HashMap<>();
        codeMap.put("111", "수원시");
        codeMap.put("113", "성남시");
        codeMap.put("115", "의정부시");
        codeMap.put("117", "안양시");
        codeMap.put("119", "부천시");
        codeMap.put("121", "광명시");
        codeMap.put("123", "평택시");
        codeMap.put("125", "동두천시");
        codeMap.put("127", "안산시");
        codeMap.put("129", "고양시");
        codeMap.put("131", "과천시");
        codeMap.put("133", "구리시");
        codeMap.put("135", "남양주시");
        codeMap.put("137", "오산시");
        codeMap.put("139", "시흥시");
        codeMap.put("141", "군포시");
        codeMap.put("143", "의왕시");
        codeMap.put("145", "하남시");
        codeMap.put("147", "용인시");
        codeMap.put("149", "파주시");
        codeMap.put("151", "이천시");
        codeMap.put("153", "안성시");
        codeMap.put("155", "김포시");
        codeMap.put("157", "화성시");
        codeMap.put("159", "광주시");
        codeMap.put("161", "양주시");
        codeMap.put("163", "포천시");
        codeMap.put("165", "여주시");
        return codeMap.get(code);
    }
    
    private String getSignguNmFromCodeForChungbuk(String code) {
        Map<String, String> codeMap = new HashMap<>();
        codeMap.put("110", "청주시");
        codeMap.put("130", "충주시");
        codeMap.put("150", "제천시");
        codeMap.put("720", "보은군");
        codeMap.put("730", "옥천군");
        codeMap.put("740", "영동군");
        codeMap.put("745", "증평군");
        codeMap.put("750", "진천군");
        codeMap.put("760", "괴산군");
        codeMap.put("770", "음성군");
        codeMap.put("800", "단양군");
        return codeMap.get(code);
    }
    
    private String getSignguNmFromCodeForChungnam(String code) {
        Map<String, String> codeMap = new HashMap<>();
        codeMap.put("130", "천안시");
        codeMap.put("133", "공주시");
        codeMap.put("150", "보령시");
        codeMap.put("180", "아산시");
        codeMap.put("200", "서산시");
        codeMap.put("210", "논산시");
        codeMap.put("230", "계룡시");
        codeMap.put("250", "당진시");
        return codeMap.get(code);
    }
    
    private String getSignguNmFromCodeForJeonbuk(String code) {
        Map<String, String> codeMap = new HashMap<>();
        codeMap.put("110", "전주시");
        codeMap.put("130", "군산시");
        codeMap.put("140", "익산시");
        codeMap.put("180", "정읍시");
        codeMap.put("190", "남원시");
        codeMap.put("210", "김제시");
        return codeMap.get(code);
    }
    
    private String getSignguNmFromCodeForJeonnam(String code) {
        Map<String, String> codeMap = new HashMap<>();
        codeMap.put("110", "목포시");
        codeMap.put("130", "여수시");
        codeMap.put("150", "순천시");
        codeMap.put("170", "나주시");
        codeMap.put("230", "광양시");
        return codeMap.get(code);
    }
    
    private String getSignguNmFromCodeForGyeongbuk(String code) {
        Map<String, String> codeMap = new HashMap<>();
        codeMap.put("110", "포항시");
        codeMap.put("130", "경주시");
        codeMap.put("150", "김천시");
        codeMap.put("170", "안동시");
        codeMap.put("190", "구미시");
        codeMap.put("210", "영주시");
        codeMap.put("230", "영천시");
        codeMap.put("250", "상주시");
        codeMap.put("280", "문경시");
        codeMap.put("290", "경산시");
        return codeMap.get(code);
    }
    
    private String getSignguNmFromCodeForGyeongnam(String code) {
        Map<String, String> codeMap = new HashMap<>();
        codeMap.put("120", "창원시");
        codeMap.put("170", "진주시");
        codeMap.put("220", "통영시");
        codeMap.put("240", "사천시");
        codeMap.put("250", "김해시");
        codeMap.put("270", "밀양시");
        codeMap.put("310", "거제시");
        codeMap.put("330", "양산시");
        return codeMap.get(code);
    }
    
    /**
     * 사용 가능한 모든 region 목록 반환 (드롭다운용)
     * 형식: [{"label": "서울 / 중구", "value": "서울_중구"}, ...]
     */
    public List<Map<String, String>> getAvailableRegions() {
        List<Map<String, String>> regions = new ArrayList<>();
        
        // 서울특별시
        regions.add(createRegionMap("서울 / 중구", "서울_중구"));
        regions.add(createRegionMap("서울 / 종로구", "서울_종로구"));
        regions.add(createRegionMap("서울 / 중구,용산구", "서울_중구,용산구"));
        regions.add(createRegionMap("서울 / 종로구,용산구", "서울_종로구,용산구"));
        regions.add(createRegionMap("서울 / 용산구,성동구", "서울_용산구,성동구"));
        regions.add(createRegionMap("서울 / 영등포구", "서울_영등포구"));
        regions.add(createRegionMap("서울 / 강동구", "서울_강동구"));
        regions.add(createRegionMap("서울 / 강서구", "서울_강서구"));
        regions.add(createRegionMap("서울 / 도봉구", "서울_도봉구"));
        regions.add(createRegionMap("서울 / 은평구", "서울_은평구"));
        regions.add(createRegionMap("서울 / 송파구", "서울_송파구"));
        regions.add(createRegionMap("서울 / 성북구", "서울_성북구"));
        regions.add(createRegionMap("서울 / 강북구", "서울_강북구"));
        regions.add(createRegionMap("서울 / 양천구", "서울_양천구"));
        regions.add(createRegionMap("서울 / 관악구", "서울_관악구"));
        regions.add(createRegionMap("서울 / 서대문구", "서울_서대문구"));
        regions.add(createRegionMap("서울 / 동대문구", "서울_동대문구"));
        regions.add(createRegionMap("서울 / 구로구", "서울_구로구"));
        regions.add(createRegionMap("서울 / 중랑구", "서울_중랑구"));
        regions.add(createRegionMap("서울 / 노원구", "서울_노원구"));
        regions.add(createRegionMap("서울 / 광진구", "서울_광진구"));
        regions.add(createRegionMap("서울 / 서초구", "서울_서초구"));
        regions.add(createRegionMap("서울 / 강남구", "서울_강남구"));
        regions.add(createRegionMap("서울 / 마포구", "서울_마포구"));
        regions.add(createRegionMap("서울 / 동작구", "서울_동작구"));
        regions.add(createRegionMap("서울 / 금천구", "서울_금천구"));
        
        // 경기도
        regions.add(createRegionMap("경기 / 수원시 권선구", "경기_수원시 권선구"));
        regions.add(createRegionMap("경기 / 수원시 장안구", "경기_수원시 장안구"));
        regions.add(createRegionMap("경기 / 수원시 영통구", "경기_수원시 영통구"));
        regions.add(createRegionMap("경기 / 수원시 팔달구", "경기_수원시 팔달구"));
        regions.add(createRegionMap("경기 / 성남시 중원구", "경기_성남시 중원구"));
        regions.add(createRegionMap("경기 / 성남시 수정구", "경기_성남시 수정구"));
        regions.add(createRegionMap("경기 / 성남시 분당구", "경기_성남시 분당구"));
        
        // 부산광역시
        regions.add(createRegionMap("부산 / 영도구", "부산_영도구"));
        regions.add(createRegionMap("부산 / 기장군", "부산_기장군"));
        regions.add(createRegionMap("부산 / 서구,강서구", "부산_서구,강서구"));
        regions.add(createRegionMap("부산 / 동구", "부산_동구"));
        regions.add(createRegionMap("부산 / 북구", "부산_북구"));
        regions.add(createRegionMap("부산 / 금정구", "부산_금정구"));
        regions.add(createRegionMap("부산 / 동래구", "부산_동래구"));
        regions.add(createRegionMap("부산 / 사하구", "부산_사하구"));
        regions.add(createRegionMap("부산 / 수영구", "부산_수영구"));
        regions.add(createRegionMap("부산 / 부산진구", "부산_부산진구"));
        regions.add(createRegionMap("부산 / 연제구", "부산_연제구"));
        regions.add(createRegionMap("부산 / 남구", "부산_남구"));
        regions.add(createRegionMap("부산 / 사상구", "부산_사상구"));
        regions.add(createRegionMap("부산 / 해운대구", "부산_해운대구"));
        regions.add(createRegionMap("부산 / 중구", "부산_중구"));
        regions.add(createRegionMap("부산 / 서구", "부산_서구"));
        regions.add(createRegionMap("부산 / 강서구", "부산_강서구"));
        
        // 대구광역시
        regions.add(createRegionMap("대구 / 중구", "대구_중구"));
        regions.add(createRegionMap("대구 / 북구", "대구_북구"));
        regions.add(createRegionMap("대구 / 남구", "대구_남구"));
        regions.add(createRegionMap("대구 / 달서구", "대구_달서구"));
        regions.add(createRegionMap("대구 / 동구", "대구_동구"));
        regions.add(createRegionMap("대구 / 달성군", "대구_달성군"));
        regions.add(createRegionMap("대구 / 수성구", "대구_수성구"));
        regions.add(createRegionMap("대구 / 서구", "대구_서구"));
        
        // 인천광역시
        regions.add(createRegionMap("인천 / 중구", "인천_중구"));
        regions.add(createRegionMap("인천 / 동구,강화군", "인천_동구,강화군"));
        regions.add(createRegionMap("인천 / 계양구", "인천_계양구"));
        regions.add(createRegionMap("인천 / 미추홀구", "인천_미추홀구"));
        regions.add(createRegionMap("인천 / 남동구", "인천_남동구"));
        regions.add(createRegionMap("인천 / 서구", "인천_서구"));
        regions.add(createRegionMap("인천 / 부평구", "인천_부평구"));
        regions.add(createRegionMap("인천 / 연수구", "인천_연수구"));
        regions.add(createRegionMap("인천 / 동구", "인천_동구"));
        regions.add(createRegionMap("인천 / 강화군", "인천_강화군"));
        regions.add(createRegionMap("인천 / 옹진군", "인천_옹진군"));
        
        // 광주광역시
        regions.add(createRegionMap("광주 / 북구", "광주_북구"));
        regions.add(createRegionMap("광주 / 광산구", "광주_광산구"));
        regions.add(createRegionMap("광주 / 남구", "광주_남구"));
        regions.add(createRegionMap("광주 / 서구", "광주_서구"));
        regions.add(createRegionMap("광주 / 동구", "광주_동구"));
        
        // 대전광역시
        regions.add(createRegionMap("대전 / 서구", "대전_서구"));
        regions.add(createRegionMap("대전 / 유성구", "대전_유성구"));
        regions.add(createRegionMap("대전 / 중구", "대전_중구"));
        regions.add(createRegionMap("대전 / 동구", "대전_동구"));
        regions.add(createRegionMap("대전 / 대덕구", "대전_대덕구"));
        
        // 울산광역시
        regions.add(createRegionMap("울산 / 울주군", "울산_울주군"));
        regions.add(createRegionMap("울산 / 북구", "울산_북구"));
        regions.add(createRegionMap("울산 / 동구", "울산_동구"));
        regions.add(createRegionMap("울산 / 남구", "울산_남구"));
        regions.add(createRegionMap("울산 / 중구", "울산_중구"));
        
        // 세종특별자치시
        regions.add(createRegionMap("세종 / 세종특별자치시", "세종_세종특별자치시"));
        
        // 전라남도
        regions.add(createRegionMap("전남 / 순천시", "전남_순천시"));
        regions.add(createRegionMap("전남 / 목포시", "전남_목포시"));
        regions.add(createRegionMap("전남 / 나주시", "전남_나주시"));
        regions.add(createRegionMap("전남 / 여수시,광양시", "전남_여수시,광양시"));
        
        // 충청남도
        regions.add(createRegionMap("충남 / 공주시", "충남_공주시"));
        regions.add(createRegionMap("충남 / 서산시,보령시", "충남_서산시,보령시"));
        regions.add(createRegionMap("충남 / 아산시", "충남_아산시"));
        regions.add(createRegionMap("충남 / 천안시 서북구", "충남_천안시 서북구"));
        regions.add(createRegionMap("충남 / 서산시", "충남_서산시"));
        regions.add(createRegionMap("충남 / 보령시", "충남_보령시"));
        regions.add(createRegionMap("충남 / 논산시", "충남_논산시"));
        regions.add(createRegionMap("충남 / 계룡시", "충남_계룡시"));
        
        // 충청북도
        regions.add(createRegionMap("충북 / 제천시", "충북_제천시"));
        regions.add(createRegionMap("충북 / 충주시", "충북_충주시"));
        regions.add(createRegionMap("충북 / 음성군,진천군,증평군", "충북_음성군,진천군,증평군"));
        regions.add(createRegionMap("충북 / 보은군,옥천군,영동군,괴산군", "충북_보은군,옥천군,영동군,괴산군"));
        regions.add(createRegionMap("충북 / 음성군", "충북_음성군"));
        regions.add(createRegionMap("충북 / 진천군", "충북_진천군"));
        regions.add(createRegionMap("충북 / 증평군", "충북_증평군"));
        regions.add(createRegionMap("충북 / 영동군", "충북_영동군"));
        regions.add(createRegionMap("충북 / 옥천군", "충북_옥천군"));
        regions.add(createRegionMap("충북 / 괴산군", "충북_괴산군"));
        regions.add(createRegionMap("충북 / 보은군", "충북_보은군"));
        
        return regions;
    }
    
    private Map<String, String> createRegionMap(String label, String value) {
        Map<String, String> map = new HashMap<>();
        map.put("label", label);
        map.put("value", value);
        return map;
    }
    
    // ========== 새로운 드롭다운 방식 API 메서드 ==========
    
    /**
     * 광역시/도 목록 반환 (상위 드롭다운용)
     * 형식: [{"value": "서울", "label": "서울특별시"}, ...]
     */
    public List<Map<String, String>> getSidoList() {
        List<Map<String, String>> sidoList = new ArrayList<>();
        sidoList.add(createSidoMap("서울", "서울특별시"));
        sidoList.add(createSidoMap("경기", "경기도"));
        sidoList.add(createSidoMap("부산", "부산광역시"));
        sidoList.add(createSidoMap("대구", "대구광역시"));
        sidoList.add(createSidoMap("인천", "인천광역시"));
        sidoList.add(createSidoMap("광주", "광주광역시"));
        sidoList.add(createSidoMap("대전", "대전광역시"));
        sidoList.add(createSidoMap("울산", "울산광역시"));
        sidoList.add(createSidoMap("세종", "세종특별자치시"));
        sidoList.add(createSidoMap("전남", "전라남도"));
        sidoList.add(createSidoMap("충남", "충청남도"));
        sidoList.add(createSidoMap("충북", "충청북도"));
        return sidoList;
    }
    
    private Map<String, String> createSidoMap(String value, String label) {
        Map<String, String> map = new HashMap<>();
        map.put("value", value);
        map.put("label", label);
        return map;
    }
    
    /**
     * 특정 광역시/도의 시/군/구 목록 반환 (하위 드롭다운용)
     * 형식: [{"value": "중구", "label": "중구"}, {"value": "종로구", "label": "종로구"}, ...]
     * @throws IllegalArgumentException sido가 유효하지 않은 경우
     */
    public List<Map<String, String>> getDistrictsBySido(String sido) {
        List<Map<String, String>> districts = new ArrayList<>();
        
        if (sido == null || sido.trim().isEmpty()) {
            throw new IllegalArgumentException("상위 드롭다운에서 광역시/도를 먼저 선택해주세요.");
        }
        
        // sido를 brtcNm으로 변환
        String brtcNm = getBrtcNmFromSido(sido);
        if (brtcNm == null) {
            log.warn("알 수 없는 sido: {}", sido);
            throw new IllegalArgumentException("잘못된 광역시/도입니다. 올바른 지역을 선택해주세요.");
        }
        
        // DB에서 해당 광역시/도의 모든 시/군/구 조회
        List<String> signguNms = housingInfoRepository.findDistinctSignguNmByBrtcNm(brtcNm);
        
        // 중복 제거 및 정렬
        signguNms = signguNms.stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        
        // 데이터가 없는 경우
        if (signguNms.isEmpty()) {
            log.warn("해당 광역시/도에 시/군/구 데이터가 없습니다: sido={}, brtcNm={}", sido, brtcNm);
            throw new IllegalArgumentException("선택하신 광역시/도에 해당하는 시/군/구 데이터가 없습니다. 다른 지역을 선택해주세요.");
        }
        
        // Map 형식으로 변환
        for (String signguNm : signguNms) {
            Map<String, String> districtMap = new HashMap<>();
            districtMap.put("value", signguNm);
            districtMap.put("label", signguNm);
            districts.add(districtMap);
        }
        
        return districts;
    }
    
    /**
     * sido를 brtcNm으로 변환
     */
    private String getBrtcNmFromSido(String sido) {
        Map<String, String> sidoMap = new HashMap<>();
        // 정식 이름만 지원
        sidoMap.put("서울특별시", "서울특별시");
        sidoMap.put("부산광역시", "부산광역시");
        sidoMap.put("대구광역시", "대구광역시");
        sidoMap.put("인천광역시", "인천광역시");
        sidoMap.put("광주광역시", "광주광역시");
        sidoMap.put("대전광역시", "대전광역시");
        sidoMap.put("울산광역시", "울산광역시");
        sidoMap.put("세종특별자치시", "세종특별자치시");
        sidoMap.put("경기도", "경기도");
        sidoMap.put("강원특별자치도", "강원특별자치도");
        sidoMap.put("충청북도", "충청북도");
        sidoMap.put("충청남도", "충청남도");
        sidoMap.put("전북특별자치도", "전북특별자치도");
        sidoMap.put("전라남도", "전라남도");
        sidoMap.put("경상북도", "경상북도");
        sidoMap.put("경상남도", "경상남도");
        sidoMap.put("제주특별자치도", "제주특별자치도");
        return sidoMap.get(sido);
    }
    
    /**
     * 새로운 추천 API (sido + districts 방식)
     * sido와 districts를 조합해서 region 형식으로 변환 후 기존 getRecommendations 호출
     * @throws IllegalArgumentException 검증 실패 시
     */
    public RecommendationResponse getRecommendationsV2(String sido, List<String> districts, String prompt) {
        // sido 검증
        if (sido == null || sido.trim().isEmpty()) {
            throw new IllegalArgumentException("상위 드롭다운에서 광역시/도를 먼저 선택해주세요.");
        }
        
        // sido가 유효한지 확인
        String brtcNm = getBrtcNmFromSido(sido);
        if (brtcNm == null) {
            throw new IllegalArgumentException("잘못된 광역시/도입니다. 올바른 지역을 선택해주세요.");
        }
        
        // districts 검증
        if (districts == null || districts.isEmpty()) {
            throw new IllegalArgumentException("하위 드롭다운에서 시/군/구를 최소 1개 이상 선택해주세요.");
        }
        
        // districts에 빈 문자열이 포함되어 있는지 확인
        List<String> validDistricts = districts.stream()
                .filter(district -> district != null && !district.trim().isEmpty())
                .collect(Collectors.toList());
        
        if (validDistricts.isEmpty()) {
            throw new IllegalArgumentException("시/군/구 선택에 유효한 값이 없습니다. 올바른 시/군/구를 선택해주세요.");
        }
        
        // prompt 검증
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("추천 받고 싶은 내용을 입력해주세요.");
        }
        
        // sido와 districts를 조합해서 region 형식으로 변환
        // 예: sido="부산", districts=["서구", "강서구"] -> region="부산_서구,강서구"
        String region = sido + "_" + String.join(",", validDistricts);
        
        log.info("새로운 추천 API 호출: sido={}, districts={}, region={}", sido, validDistricts, region);
        
        // 기존 getRecommendations 메서드 호출
        return getRecommendations(prompt, region);
    }
}

