-- 모든 지역의 시/군/구별 단지 개수 전체 조회

-- 1. 광역시/도별, 시/군/구별 단지 개수 (전체)
SELECT 
    brtc_nm AS '광역시/도',
    signgu_nm AS '시/군/구',
    COUNT(*) AS '단지개수'
FROM housing_info
GROUP BY brtc_nm, signgu_nm
ORDER BY brtc_nm, COUNT(*) DESC;

-- 2. 광역시/도별 총합과 함께 보기
SELECT 
    brtc_nm AS '광역시/도',
    signgu_nm AS '시/군/구',
    COUNT(*) AS '단지개수',
    SUM(COUNT(*)) OVER (PARTITION BY brtc_nm) AS '도별총합'
FROM housing_info
GROUP BY brtc_nm, signgu_nm
ORDER BY brtc_nm, COUNT(*) DESC;

-- 3. 단지 개수 순으로 정렬 (전체)
SELECT 
    brtc_nm AS '광역시/도',
    signgu_nm AS '시/군/구',
    COUNT(*) AS '단지개수'
FROM housing_info
GROUP BY brtc_nm, signgu_nm
ORDER BY COUNT(*) DESC;

-- 4. 광역시/도별로 그룹화하여 보기 (가독성 향상)
SELECT 
    CONCAT(brtc_nm, ' - ', signgu_nm) AS '지역',
    COUNT(*) AS '단지개수'
FROM housing_info
GROUP BY brtc_nm, signgu_nm
ORDER BY brtc_nm, COUNT(*) DESC;

-- 5. 20개 내외 범위 표시
SELECT 
    brtc_nm AS '광역시/도',
    signgu_nm AS '시/군/구',
    COUNT(*) AS '단지개수',
    CASE 
        WHEN COUNT(*) BETWEEN 15 AND 25 THEN '✅ 적정'
        WHEN COUNT(*) < 15 THEN '⚠️ 적음'
        WHEN COUNT(*) > 25 AND COUNT(*) <= 50 THEN '⚠️ 많음'
        ELSE '❌ 매우 많음'
    END AS '평가'
FROM housing_info
GROUP BY brtc_nm, signgu_nm
ORDER BY brtc_nm, COUNT(*) DESC;

