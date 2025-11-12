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
    private String signguNm; // 시군구명

    @Column(name = "hsmp_nm")
    private String hsmpNm; // 단지명

    @Column(name = "rn_adres", length = 500)
    private String rnAdres; // 도로명 주소

    @Column(name = "hshld_co")
    private Integer hshldCo; // 세대수

    @Column(name = "suply_ty_nm")
    private String suplyTyNm; // 공급 유형 명

    @Column(name = "suply_cmnuse_ar")
    private Double suplyCmnuseAr; // 공급 공용 면적

    @Column(name = "house_ty_nm")
    private String houseTyNm; // 주택 유형 명

    @Column(name = "bass_rent_gtn")
    private Long bassRentGtn; // 기본 임대보증금

    @Column(name = "bass_mt_rntchrg")
    private Long bassMtRntchrg; // 기본 월임대료

    @Column(name = "bass_cnvrs_gtn_lmt")
    private Long bassCnvrsGtnLmt; // 기본 전환보증금

    private HousingInfo(String hsmpSn, String brtcNm, String signguNm, String hsmpNm,
                       String rnAdres, Integer hshldCo, String suplyTyNm, Double suplyCmnuseAr,
                       String houseTyNm, Long bassRentGtn, Long bassMtRntchrg, Long bassCnvrsGtnLmt) {
        this.hsmpSn = hsmpSn;
        this.brtcNm = brtcNm;
        this.signguNm = signguNm;
        this.hsmpNm = hsmpNm;
        this.rnAdres = rnAdres;
        this.hshldCo = hshldCo;
        this.suplyTyNm = suplyTyNm;
        this.suplyCmnuseAr = suplyCmnuseAr;
        this.houseTyNm = houseTyNm;
        this.bassRentGtn = bassRentGtn;
        this.bassMtRntchrg = bassMtRntchrg;
        this.bassCnvrsGtnLmt = bassCnvrsGtnLmt;
    }

    public static HousingInfo of(String hsmpSn, String brtcNm, String signguNm, String hsmpNm,
                                 String rnAdres, Integer hshldCo, String suplyTyNm, Double suplyCmnuseAr,
                                 String houseTyNm, Long bassRentGtn, Long bassMtRntchrg, Long bassCnvrsGtnLmt) {
        return new HousingInfo(hsmpSn, brtcNm, signguNm, hsmpNm, rnAdres, hshldCo,
                suplyTyNm, suplyCmnuseAr, houseTyNm, bassRentGtn, bassMtRntchrg, bassCnvrsGtnLmt);
    }
}

