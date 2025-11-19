package com.ganzithon.homemate.service;

import com.ganzithon.homemate.dto.HousingApiResponse;
import com.ganzithon.homemate.entity.HousingInfo;
import com.ganzithon.homemate.repository.HousingInfoRepository;
import java.util.ArrayList;
import java.util.List;
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
}

