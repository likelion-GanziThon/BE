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

        // houseTyNm이 빈 객체일 수 있으므로 String으로 변환
        String houseTyNm = convertToString(item.getHouseTyNm());
        
        return HousingInfo.of(
                hsmpSnStr,
                item.getBrtcNm(),
                item.getSignguNm(),
                item.getHsmpNm(),
                item.getRnAdres(),
                parseInteger(item.getHshldCo()),
                item.getSuplyTyNm(),
                parseDouble(item.getSuplyCmnuseAr()),
                houseTyNm,
                parseLong(item.getBassRentGtn()),
                parseLong(item.getBassMtRntchrg()),
                parseLong(item.getBassCnvrsGtnLmt())
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
            
            // 권역에 해당하는 주거정보 필터링 (제한 없음 - 권역별로 나눠서 받으므로)
            List<HousingInfo> allHousingInfo;
            // 광역시/도 전체인 경우 (코드가 2자리)
            if (regionCodes.size() == 1 && regionCodes.get(0).length() == 2) {
                String brtcCode = regionCodes.get(0);
                allHousingInfo = housingInfoRepository.findAll().stream()
                        .filter(info -> {
                            // brtcNm에서 코드 추출 또는 직접 매칭
                            if (brtcCode.equals("28") && info.getBrtcNm() != null && info.getBrtcNm().contains("인천")) return true;
                            if (brtcCode.equals("27") && info.getBrtcNm() != null && info.getBrtcNm().contains("대구")) return true;
                            if (brtcCode.equals("29") && info.getBrtcNm() != null && info.getBrtcNm().contains("광주")) return true;
                            if (brtcCode.equals("30") && info.getBrtcNm() != null && info.getBrtcNm().contains("대전")) return true;
                            if (brtcCode.equals("31") && info.getBrtcNm() != null && info.getBrtcNm().contains("울산")) return true;
                            if (brtcCode.equals("36") && info.getBrtcNm() != null && info.getBrtcNm().contains("세종")) return true;
                            if (brtcCode.equals("42") && info.getBrtcNm() != null && info.getBrtcNm().contains("강원")) return true;
                            if (brtcCode.equals("43") && info.getBrtcNm() != null && info.getBrtcNm().contains("충북")) return true;
                            if (brtcCode.equals("44") && info.getBrtcNm() != null && info.getBrtcNm().contains("충남")) return true;
                            if (brtcCode.equals("45") && info.getBrtcNm() != null && info.getBrtcNm().contains("전북")) return true;
                            if (brtcCode.equals("46") && info.getBrtcNm() != null && info.getBrtcNm().contains("전남")) return true;
                            if (brtcCode.equals("47") && info.getBrtcNm() != null && info.getBrtcNm().contains("경북")) return true;
                            if (brtcCode.equals("48") && info.getBrtcNm() != null && info.getBrtcNm().contains("경남")) return true;
                            if (brtcCode.equals("50") && info.getBrtcNm() != null && info.getBrtcNm().contains("제주")) return true;
                            return false;
                        })
                        .collect(Collectors.toList());
            } else {
                // 구/시 단위 필터링
                allHousingInfo = housingInfoRepository.findAll().stream()
                        .filter(info -> {
                            String signguCode = getSignguCode(info);
                            return signguCode != null && regionCodes.contains(signguCode);
                        })
                        .collect(Collectors.toList());
            }
            log.info("권역 '{}'에 해당하는 주거정보: {}건 조회 완료", region, allHousingInfo.size());

            if (allHousingInfo.isEmpty()) {
                log.warn("필터링된 주거정보가 없습니다.");
                return new RecommendationResponse(List.of());
            }

            log.info("필터링된 주거정보: {}건", allHousingInfo.size());

            // HousingInfo 엔티티를 Map으로 변환 (AI API에 전달하기 위해)
            List<Map<String, Object>> housingDataList = allHousingInfo.stream()
                    .map(this::convertToMap)
                    .collect(Collectors.toList());

            // AI API 호출하여 추천 받기
            List<UpstageAiService.RecommendationResult> recommendationResults = 
                    upstageAiService.getRecommendations(userPrompt, housingDataList);

            log.info("AI 추천 결과: {}건 추천됨", recommendationResults.size());

            // 추천된 hsmpSn으로 HousingInfo 조회 및 순서 유지
            Map<String, HousingInfo> housingInfoMap = allHousingInfo.stream()
                    .collect(Collectors.toMap(
                            HousingInfo::getHsmpSn,
                            info -> info,
                            (existing, replacement) -> existing
                    ));

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

            log.info("최종 추천 결과: {}건 반환", recommendations.size());
            return new RecommendationResponse(recommendations);

        } catch (Exception e) {
            log.error("AI 추천 처리 중 오류 발생", e);
            throw new RuntimeException("AI 추천 서비스 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    
    /**
     * 권역 선택에 따라 해당하는 구/시 코드 목록 반환
     */
    private List<String> getRegionCodes(String region) {
        if (region == null || region.trim().isEmpty()) {
            return List.of();
        }
        
        Map<String, List<String>> regionMap = new HashMap<>();
        
        // 서울 권역
        regionMap.put("서울_강남권", List.of("680", "650", "710", "740")); // 강남구, 서초구, 송파구, 강동구
        regionMap.put("서울_도심권", List.of("110", "140", "170")); // 종로구, 중구, 용산구
        regionMap.put("서울_동북권", List.of("200", "215", "230", "260", "290", "305", "320", "350")); // 성동구, 광진구, 동대문구, 중랑구, 성북구, 강북구, 도봉구, 노원구
        regionMap.put("서울_서북권", List.of("380", "410", "440")); // 은평구, 서대문구, 마포구
        regionMap.put("서울_서남권", List.of("470", "500", "530", "545", "560", "590", "620")); // 양천구, 강서구, 구로구, 금천구, 영등포구, 동작구, 관악구
        
        // 경기도 권역
        regionMap.put("경기_남부핵심권", List.of("111", "147", "157", "137", "123")); // 수원, 용인, 화성, 오산, 평택
        regionMap.put("경기_북부", List.of("129", "149", "115", "161", "125")); // 고양, 파주, 의정부, 양주, 동두천
        regionMap.put("경기_동부", List.of("113", "159", "145", "135", "153", "165")); // 성남, 광주(경기), 하남, 남양주, 안성, 여주
        regionMap.put("경기_서부", List.of("119", "155", "139", "127")); // 부천, 김포, 시흥, 안산
        regionMap.put("경기_중부", List.of("117", "141", "131", "143")); // 안양, 군포, 과천, 의왕
        regionMap.put("경기_북동부", List.of("133", "163")); // 구리, 포천
        regionMap.put("경기_남서부", List.of("123", "153")); // 평택, 안성 + 화성 남부
        
        // 부산 권역
        regionMap.put("부산_동부해안권", List.of("350", "500", "290")); // 해운대구, 수영구, 남구
        regionMap.put("부산_서부권", List.of("380", "140", "200", "110")); // 사하구, 서구, 영도구, 중구
        regionMap.put("부산_북부권", List.of("320", "440", "410")); // 북구, 강서구, 금정구
        regionMap.put("부산_중부상업권", List.of("230", "260", "470", "530")); // 부산진구, 동래구, 연제구, 사상구
        
        // 나머지 광역시/도 (전체 포함)
        regionMap.put("인천", List.of("28")); // 인천 전체
        regionMap.put("대구", List.of("27")); // 대구 전체
        regionMap.put("광주", List.of("29")); // 광주 전체
        regionMap.put("대전", List.of("30")); // 대전 전체
        regionMap.put("울산", List.of("31")); // 울산 전체
        regionMap.put("세종", List.of("36")); // 세종 전체
        regionMap.put("강원", List.of("42")); // 강원 전체
        regionMap.put("충북", List.of("43")); // 충북 전체
        regionMap.put("충남", List.of("44")); // 충남 전체
        regionMap.put("전북", List.of("45")); // 전북 전체
        regionMap.put("전남", List.of("46")); // 전남 전체
        regionMap.put("경북", List.of("47")); // 경북 전체
        regionMap.put("경남", List.of("48")); // 경남 전체
        regionMap.put("제주", List.of("50")); // 제주 전체
        
        return regionMap.getOrDefault(region, List.of());
    }
    
    /**
     * HousingInfo에서 시군구 코드 추출
     * signguNm에서 코드를 추출하거나, brtcNm과 signguNm 조합으로 판단
     */
    private String getSignguCode(HousingInfo info) {
        // 서울의 경우
        if (info.getBrtcNm() != null && info.getBrtcNm().contains("서울")) {
            String signguNm = info.getSignguNm();
            if (signguNm == null) return null;
            
            // 서울 구별 코드 매핑
            Map<String, String> seoulCodeMap = new HashMap<>();
            seoulCodeMap.put("강남구", "680");
            seoulCodeMap.put("서초구", "650");
            seoulCodeMap.put("송파구", "710");
            seoulCodeMap.put("강동구", "740");
            seoulCodeMap.put("종로구", "110");
            seoulCodeMap.put("중구", "140");
            seoulCodeMap.put("용산구", "170");
            seoulCodeMap.put("성동구", "200");
            seoulCodeMap.put("광진구", "215");
            seoulCodeMap.put("동대문구", "230");
            seoulCodeMap.put("중랑구", "260");
            seoulCodeMap.put("성북구", "290");
            seoulCodeMap.put("강북구", "305");
            seoulCodeMap.put("도봉구", "320");
            seoulCodeMap.put("노원구", "350");
            seoulCodeMap.put("은평구", "380");
            seoulCodeMap.put("서대문구", "410");
            seoulCodeMap.put("마포구", "440");
            seoulCodeMap.put("양천구", "470");
            seoulCodeMap.put("강서구", "500");
            seoulCodeMap.put("구로구", "530");
            seoulCodeMap.put("금천구", "545");
            seoulCodeMap.put("영등포구", "560");
            seoulCodeMap.put("동작구", "590");
            seoulCodeMap.put("관악구", "620");
            return seoulCodeMap.get(signguNm);
        }
        
        // 부산의 경우
        if (info.getBrtcNm() != null && info.getBrtcNm().contains("부산")) {
            String signguNm = info.getSignguNm();
            if (signguNm == null) return null;
            
            Map<String, String> busanCodeMap = new HashMap<>();
            busanCodeMap.put("해운대구", "350");
            busanCodeMap.put("수영구", "500");
            busanCodeMap.put("남구", "290");
            busanCodeMap.put("사하구", "380");
            busanCodeMap.put("서구", "140");
            busanCodeMap.put("영도구", "200");
            busanCodeMap.put("중구", "110");
            busanCodeMap.put("북구", "320");
            busanCodeMap.put("강서구", "440");
            busanCodeMap.put("금정구", "410");
            busanCodeMap.put("부산진구", "230");
            busanCodeMap.put("동래구", "260");
            busanCodeMap.put("연제구", "470");
            busanCodeMap.put("사상구", "530");
            return busanCodeMap.get(signguNm);
        }
        
        // 경기도의 경우 (시 단위)
        if (info.getBrtcNm() != null && info.getBrtcNm().contains("경기")) {
            String signguNm = info.getSignguNm();
            if (signguNm == null) return null;
            
            Map<String, String> gyeonggiCodeMap = new HashMap<>();
            gyeonggiCodeMap.put("수원시", "111");
            gyeonggiCodeMap.put("용인시", "147");
            gyeonggiCodeMap.put("화성시", "157");
            gyeonggiCodeMap.put("오산시", "137");
            gyeonggiCodeMap.put("평택시", "123");
            gyeonggiCodeMap.put("고양시", "129");
            gyeonggiCodeMap.put("파주시", "149");
            gyeonggiCodeMap.put("의정부시", "115");
            gyeonggiCodeMap.put("양주시", "161");
            gyeonggiCodeMap.put("동두천시", "125");
            gyeonggiCodeMap.put("성남시", "113");
            gyeonggiCodeMap.put("광주시", "159");
            gyeonggiCodeMap.put("하남시", "145");
            gyeonggiCodeMap.put("남양주시", "135");
            gyeonggiCodeMap.put("안성시", "153");
            gyeonggiCodeMap.put("여주시", "165");
            gyeonggiCodeMap.put("부천시", "119");
            gyeonggiCodeMap.put("김포시", "155");
            gyeonggiCodeMap.put("시흥시", "139");
            gyeonggiCodeMap.put("안산시", "127");
            gyeonggiCodeMap.put("안양시", "117");
            gyeonggiCodeMap.put("군포시", "141");
            gyeonggiCodeMap.put("과천시", "131");
            gyeonggiCodeMap.put("의왕시", "143");
            gyeonggiCodeMap.put("구리시", "133");
            gyeonggiCodeMap.put("포천시", "163");
            return gyeonggiCodeMap.get(signguNm);
        }
        
        // 나머지는 null 반환 (광역시/도 전체는 brtcNm으로만 필터링)
        return null;
    }
    
    // HousingInfo 엔티티를 Map으로 변환 (필수 필드만 포함하여 토큰 수 절감)
    private Map<String, Object> convertToMap(HousingInfo housingInfo) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hsmpSn", housingInfo.getHsmpSn());
        map.put("brtcNm", housingInfo.getBrtcNm());
        map.put("signguNm", housingInfo.getSignguNm());
        map.put("hsmpNm", housingInfo.getHsmpNm());
        // 주소는 길 수 있으므로 간단히만 포함
        if (housingInfo.getRnAdres() != null && housingInfo.getRnAdres().length() > 50) {
            map.put("rnAdres", housingInfo.getRnAdres().substring(0, 50) + "...");
        } else {
            map.put("rnAdres", housingInfo.getRnAdres());
        }
        map.put("houseTyNm", housingInfo.getHouseTyNm());
        map.put("bassRentGtn", housingInfo.getBassRentGtn());
        map.put("bassMtRntchrg", housingInfo.getBassMtRntchrg());
        // 불필요한 필드는 제외하여 토큰 수 절감
        // map.put("hshldCo", housingInfo.getHshldCo());
        // map.put("suplyTyNm", housingInfo.getSuplyTyNm());
        // map.put("suplyCmnuseAr", housingInfo.getSuplyCmnuseAr());
        // map.put("bassCnvrsGtnLmt", housingInfo.getBassCnvrsGtnLmt());
        return map;
    }
}