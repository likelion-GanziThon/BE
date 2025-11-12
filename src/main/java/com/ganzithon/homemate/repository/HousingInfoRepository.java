package com.ganzithon.homemate.repository;

import com.ganzithon.homemate.entity.HousingInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HousingInfoRepository extends JpaRepository<HousingInfo, Long> {
    Optional<HousingInfo> findByHsmpSn(String hsmpSn);
    boolean existsByHsmpSn(String hsmpSn);
}

