package com.ganzithon.homemate.repository;

import com.ganzithon.homemate.entity.HousingInfo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface HousingInfoRepository extends JpaRepository<HousingInfo, Long> {
    Optional<HousingInfo> findByHsmpSn(String hsmpSn);
    boolean existsByHsmpSn(String hsmpSn);
    List<HousingInfo> findByBrtcNmContaining(String brtcNm);
    
    // 권역별 쿼리 최적화: 광역시/도 전체 조회
    @Query("SELECT h FROM HousingInfo h WHERE h.brtcNm LIKE CONCAT('%', :brtcNm, '%')")
    List<HousingInfo> findByBrtcNmLike(@Param("brtcNm") String brtcNm);
    
    // 권역별 쿼리 최적화: 시군구 코드 목록으로 조회 (서울, 부산, 경기 등)
    @Query("SELECT h FROM HousingInfo h WHERE h.brtcNm = :brtcNm AND h.signguNm IN :signguNms")
    List<HousingInfo> findByBrtcNmAndSignguNmIn(@Param("brtcNm") String brtcNm, @Param("signguNms") List<String> signguNms);
}

