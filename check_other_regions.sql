-- 부산, 대구, 인천, 광주, 대전, 울산 지역별 세부 데이터 확인

-- 부산광역시 구별 데이터 개수
SELECT 
    signgu_nm AS '구',
    COUNT(*) AS '데이터 개수'
FROM housing_info
WHERE brtc_nm = '부산광역시'
GROUP BY signgu_nm
ORDER BY COUNT(*) DESC;

-- 대구광역시 구별 데이터 개수
SELECT 
    signgu_nm AS '구',
    COUNT(*) AS '데이터 개수'
FROM housing_info
WHERE brtc_nm = '대구광역시'
GROUP BY signgu_nm
ORDER BY COUNT(*) DESC;

-- 인천광역시 구별 데이터 개수
SELECT 
    signgu_nm AS '구',
    COUNT(*) AS '데이터 개수'
FROM housing_info
WHERE brtc_nm = '인천광역시'
GROUP BY signgu_nm
ORDER BY COUNT(*) DESC;

-- 광주광역시 구별 데이터 개수
SELECT 
    signgu_nm AS '구',
    COUNT(*) AS '데이터 개수'
FROM housing_info
WHERE brtc_nm = '광주광역시'
GROUP BY signgu_nm
ORDER BY COUNT(*) DESC;

-- 대전광역시 구별 데이터 개수
SELECT 
    signgu_nm AS '구',
    COUNT(*) AS '데이터 개수'
FROM housing_info
WHERE brtc_nm = '대전광역시'
GROUP BY signgu_nm
ORDER BY COUNT(*) DESC;

-- 울산광역시 구별 데이터 개수
SELECT 
    signgu_nm AS '구',
    COUNT(*) AS '데이터 개수'
FROM housing_info
WHERE brtc_nm = '울산광역시'
GROUP BY signgu_nm
ORDER BY COUNT(*) DESC;

-- 세종특별자치시
SELECT 
    signgu_nm AS '시',
    COUNT(*) AS '데이터 개수'
FROM housing_info
WHERE brtc_nm = '세종특별자치시'
GROUP BY signgu_nm
ORDER BY COUNT(*) DESC;

-- 전라남도 시별 데이터 개수
SELECT 
    signgu_nm AS '시',
    COUNT(*) AS '데이터 개수'
FROM housing_info
WHERE brtc_nm = '전라남도'
GROUP BY signgu_nm
ORDER BY COUNT(*) DESC;

-- 20개 내외 조합 테스트 (부산 예시)
SELECT '부산_중구,서구' AS region, COUNT(*) AS count 
FROM housing_info 
WHERE brtc_nm = '부산광역시' AND signgu_nm IN ('중구', '서구')
UNION ALL
SELECT '부산_동구,영도구', COUNT(*) 
FROM housing_info 
WHERE brtc_nm = '부산광역시' AND signgu_nm IN ('동구', '영도구')
UNION ALL
SELECT '부산_부산진구,동래구', COUNT(*) 
FROM housing_info 
WHERE brtc_nm = '부산광역시' AND signgu_nm IN ('부산진구', '동래구')
UNION ALL
SELECT '부산_남구,수영구', COUNT(*) 
FROM housing_info 
WHERE brtc_nm = '부산광역시' AND signgu_nm IN ('남구', '수영구')
UNION ALL
SELECT '부산_북구', COUNT(*) 
FROM housing_info 
WHERE brtc_nm = '부산광역시' AND signgu_nm = '북구'
UNION ALL
SELECT '부산_해운대구', COUNT(*) 
FROM housing_info 
WHERE brtc_nm = '부산광역시' AND signgu_nm = '해운대구'
UNION ALL
SELECT '부산_사하구', COUNT(*) 
FROM housing_info 
WHERE brtc_nm = '부산광역시' AND signgu_nm = '사하구'
UNION ALL
SELECT '부산_금정구', COUNT(*) 
FROM housing_info 
WHERE brtc_nm = '부산광역시' AND signgu_nm = '금정구'
UNION ALL
SELECT '부산_강서구', COUNT(*) 
FROM housing_info 
WHERE brtc_nm = '부산광역시' AND signgu_nm = '강서구'
UNION ALL
SELECT '부산_연제구', COUNT(*) 
FROM housing_info 
WHERE brtc_nm = '부산광역시' AND signgu_nm = '연제구'
UNION ALL
SELECT '부산_사상구', COUNT(*) 
FROM housing_info 
WHERE brtc_nm = '부산광역시' AND signgu_nm = '사상구'
UNION ALL
SELECT '부산_기장군', COUNT(*) 
FROM housing_info 
WHERE brtc_nm = '부산광역시' AND signgu_nm = '기장군'
ORDER BY count DESC;

