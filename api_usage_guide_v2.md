# 새로운 드롭다운 방식 API 사용 가이드

## 📋 API 엔드포인트 목록

### 1. 광역시/도 목록 조회 (상위 드롭다운)
```
GET /api/housing/sido
```

**응답 예시:**
```json
[
  {"value": "서울", "label": "서울특별시"},
  {"value": "경기", "label": "경기도"},
  {"value": "부산", "label": "부산광역시"},
  {"value": "대구", "label": "대구광역시"},
  {"value": "인천", "label": "인천광역시"},
  {"value": "광주", "label": "광주광역시"},
  {"value": "대전", "label": "대전광역시"},
  {"value": "울산", "label": "울산광역시"},
  {"value": "세종", "label": "세종특별자치시"},
  {"value": "전남", "label": "전라남도"},
  {"value": "충남", "label": "충청남도"},
  {"value": "충북", "label": "충청북도"}
]
```

---

### 2. 시/군/구 목록 조회 (하위 드롭다운)
```
GET /api/housing/districts?sid={sido}
```

**파라미터:**
- `sid`: 광역시/도 코드 (예: "서울", "경기", "부산")

**응답 예시 (서울 선택 시):**
```json
[
  {"value": "강남구", "label": "강남구"},
  {"value": "강동구", "label": "강동구"},
  {"value": "강북구", "label": "강북구"},
  {"value": "강서구", "label": "강서구"},
  {"value": "관악구", "label": "관악구"},
  {"value": "광진구", "label": "광진구"},
  {"value": "구로구", "label": "구로구"},
  {"value": "금천구", "label": "금천구"},
  {"value": "노원구", "label": "노원구"},
  {"value": "도봉구", "label": "도봉구"},
  {"value": "동대문구", "label": "동대문구"},
  {"value": "동작구", "label": "동작구"},
  {"value": "마포구", "label": "마포구"},
  {"value": "서대문구", "label": "서대문구"},
  {"value": "서초구", "label": "서초구"},
  {"value": "성동구", "label": "성동구"},
  {"value": "성북구", "label": "성북구"},
  {"value": "송파구", "label": "송파구"},
  {"value": "양천구", "label": "양천구"},
  {"value": "영등포구", "label": "영등포구"},
  {"value": "용산구", "label": "용산구"},
  {"value": "은평구", "label": "은평구"},
  {"value": "종로구", "label": "종로구"},
  {"value": "중구", "label": "중구"},
  {"value": "중랑구", "label": "중랑구"}
]
```

**응답 예시 (경기 선택 시):**
```json
[
  {"value": "수원시 권선구", "label": "수원시 권선구"},
  {"value": "수원시 장안구", "label": "수원시 장안구"},
  {"value": "수원시 영통구", "label": "수원시 영통구"},
  {"value": "수원시 팔달구", "label": "수원시 팔달구"},
  {"value": "성남시 중원구", "label": "성남시 중원구"},
  {"value": "성남시 수정구", "label": "성남시 수정구"},
  {"value": "성남시 분당구", "label": "성남시 분당구"}
]
```

**응답 예시 (부산 선택 시):**
```json
[
  {"value": "강서구", "label": "강서구"},
  {"value": "금정구", "label": "금정구"},
  {"value": "기장군", "label": "기장군"},
  {"value": "남구", "label": "남구"},
  {"value": "동구", "label": "동구"},
  {"value": "동래구", "label": "동래구"},
  {"value": "부산진구", "label": "부산진구"},
  {"value": "북구", "label": "북구"},
  {"value": "사상구", "label": "사상구"},
  {"value": "사하구", "label": "사하구"},
  {"value": "서구", "label": "서구"},
  {"value": "수영구", "label": "수영구"},
  {"value": "연제구", "label": "연제구"},
  {"value": "영도구", "label": "영도구"},
  {"value": "중구", "label": "중구"},
  {"value": "해운대구", "label": "해운대구"}
]
```

---

### 3. 추천 API (새로운 방식)
```
POST /api/housing/recommend/v2
```

**요청 Body:**
```json
{
  "sido": "부산",
  "districts": ["서구", "강서구"],
  "prompt": "교통이 편리하고 주변에 편의시설이 많은 곳을 원해요"
}
```

**요청 예시들:**

**예시 1: 서울 중구 단일 선택**
```json
{
  "sido": "서울",
  "districts": ["중구"],
  "prompt": "교통이 편리한 곳을 찾고 있어요"
}
```

**예시 2: 부산 서구, 강서구 다중 선택**
```json
{
  "sido": "부산",
  "districts": ["서구", "강서구"],
  "prompt": "바다가 가까우면서도 조용한 주거 환경을 원해요"
}
```

**예시 3: 경기 수원시 권선구 선택**
```json
{
  "sido": "경기",
  "districts": ["수원시 권선구"],
  "prompt": "가족이 살기 좋고 교육 환경이 좋은 곳을 찾고 있어요"
}
```

**응답 예시:**
```json
{
  "recommendations": [
    {
      "hsmpSn": "1234567890",
      "hsmpNm": "○○ 아파트",
      "brtcNm": "부산광역시",
      "signguNm": "서구",
      "reason": "교통이 편리하고 지하철역과 가까워 출퇴근에 유리합니다."
    },
    {
      "hsmpSn": "0987654321",
      "hsmpNm": "△△ 주택",
      "brtcNm": "부산광역시",
      "signguNm": "강서구",
      "reason": "주변에 편의시설이 많고 생활이 편리합니다."
    },
    ...
  ]
}
```

---

## 🔄 프론트엔드 구현 흐름

### 1. 초기 로딩
```javascript
// 1. 광역시/도 목록 조회
const sidoList = await fetch('/api/housing/sido').then(r => r.json());
// sidoList를 상위 드롭다운에 표시
```

### 2. 광역시/도 선택 시
```javascript
// 2. 선택한 광역시/도의 시/군/구 목록 조회
const selectedSido = "부산";
const districts = await fetch(`/api/housing/districts?sid=${selectedSido}`).then(r => r.json());
// districts를 하위 드롭다운에 표시
```

### 3. 추천 요청
```javascript
// 3. 사용자가 시/군/구 선택 후 추천 버튼 클릭
const request = {
  sido: "부산",
  districts: ["서구", "강서구"],  // 다중 선택 가능
  prompt: "교통이 편리하고 주변에 편의시설이 많은 곳을 원해요"
};

const response = await fetch('/api/housing/recommend/v2', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify(request)
}).then(r => r.json());
```

---

## 📝 Postman 사용 예시

### 1. 광역시/도 목록 조회
```
GET http://localhost:8080/api/housing/sido
```

### 2. 시/군/구 목록 조회
```
GET http://localhost:8080/api/housing/districts?sid=부산
```

### 3. 추천 요청
```
POST http://localhost:8080/api/housing/recommend/v2

Headers:
Content-Type: application/json

Body (raw JSON):
{
  "sido": "부산",
  "districts": ["서구", "강서구"],
  "prompt": "교통이 편리하고 주변에 편의시설이 많은 곳을 원해요"
}
```

---

## ⚠️ 주의사항

1. **districts 배열**: 최소 1개 이상 선택해야 합니다.
2. **경기도의 경우**: "수원시 권선구"처럼 구 단위로 세분화되어 있으므로, value를 그대로 사용합니다.
3. **기존 API 호환성**: 기존 `/api/housing/recommend?region=...` 방식도 계속 사용 가능합니다.

---

## 🔄 기존 API와의 차이점

### 기존 방식 (하위 호환 유지)
```
POST /api/housing/recommend?region=부산_서구,강서구
Body: {"prompt": "..."}
```

### 새로운 방식 (권장)
```
POST /api/housing/recommend/v2
Body: {
  "sido": "부산",
  "districts": ["서구", "강서구"],
  "prompt": "..."
}
```

**장점:**
- 프론트엔드에서 드롭다운 구현이 더 직관적
- 다중 선택이 명확함
- API 구조가 더 명확함

