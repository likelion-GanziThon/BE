package com.ganzithon.homemate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "housing_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HousingInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hsmp_sn", unique = true, nullable = false)
    private String hsmpSn; // 단지 식별자

    @Column(name = "brtc_nm")
    private String brtcNm; // 광역시도명

    @Column(name = "signgu_nm")
    private String signguNm; // 시군구명 (signguCode 대신 사용)

    @Column(name = "hsmp_nm")
    private String hsmpNm; // 단지명

    @Column(name = "hshld_co")
    private Integer hshldCo; // 세대수

    @Column(name = "bass_rent_gtn")
    private Long bassRentGtn; // 기본 임대보증금

    @Column(name = "bass_mt_rntchrg")
    private Long bassMtRntchrg; // 기본 월임대료

    private HousingInfo(String hsmpSn, String brtcNm, String signguNm, String hsmpNm,
                       Integer hshldCo, Long bassRentGtn, Long bassMtRntchrg) {
        this.hsmpSn = hsmpSn;
        this.brtcNm = brtcNm;
        this.signguNm = signguNm;
        this.hsmpNm = hsmpNm;
        this.hshldCo = hshldCo;
        this.bassRentGtn = bassRentGtn;
        this.bassMtRntchrg = bassMtRntchrg;
    }

    public static HousingInfo of(String hsmpSn, String brtcNm, String signguNm, String hsmpNm,
                                 Integer hshldCo, Long bassRentGtn, Long bassMtRntchrg) {
        return new HousingInfo(hsmpSn, brtcNm, signguNm, hsmpNm, hshldCo, bassRentGtn, bassMtRntchrg);
    }
}
