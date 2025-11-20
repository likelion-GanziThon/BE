package com.ganzithon.homemate.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HousingApiResponse {
    
    @JsonProperty("code")
    private String code; // API 응답 코드 (예: "000")

    @JsonProperty("msg")
    private String msg; // API 응답 메시지 (에러 메시지 등)

    @JsonProperty("hsmpList")
    private List<HousingItem> hsmpList; // 주거정보 리스트

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HousingItem {
        @JsonProperty("numOfRows")
        private String numOfRows; // 페이지당 데이터 수

        @JsonProperty("pageNo")
        private Integer pageNo; // 현재 페이지 번호

        @JsonProperty("totalCount")
        private Integer totalCount; // 전체 데이터 개수

        @JsonProperty("hsmpSn")
        private Object hsmpSn; // 단지 식별자 (숫자 또는 문자열)

        @JsonProperty("brtcNm")
        private String brtcNm; // 광역시도명

        @JsonProperty("signguNm")
        private String signguNm; // 시군구명

        @JsonProperty("hsmpNm")
        private String hsmpNm; // 단지명

        @JsonProperty("hshldCo")
        private Object hshldCo; // 세대수 (숫자 또는 문자열)

        @JsonProperty("bassRentGtn")
        private Object bassRentGtn; // 기본 임대보증금 (숫자 또는 문자열)

        @JsonProperty("bassMtRntchrg")
        private Object bassMtRntchrg; // 기본 월임대료 (숫자 또는 문자열)

        // 사용하지 않는 필드들 (API 응답에는 포함되지만 저장하지 않음)
        // @JsonProperty("rnAdres")
        // private String rnAdres; // 도로명 주소

        // @JsonProperty("suplyTyNm")
        // private String suplyTyNm; // 공급 유형 명

        // @JsonProperty("suplyCmnuseAr")
        // private Object suplyCmnuseAr; // 공급 공용 면적 (숫자 또는 문자열)

        // @JsonProperty("houseTyNm")
        // private Object houseTyNm; // 주택 유형 명 (빈 객체일 수 있음)

        // @JsonProperty("bassCnvrsGtnLmt")
        // private Object bassCnvrsGtnLmt; // 기본 전환보증금 (숫자 또는 문자열)
    }
}

