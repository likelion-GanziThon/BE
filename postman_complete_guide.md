# Postman 완전 가이드 - 순서별 사용법 및 오류 처리

## 📋 API 사용 순서

### Step 1: 광역시/도 목록 조회 (상위 드롭다운)
### Step 2: 시/군/구 목록 조회 (하위 드롭다운)
### Step 3: 추천 요청

---

## 🔵 Step 1: 광역시/도 목록 조회

### 요청 정보
```
GET http://localhost:8080/api/housing/sido
```

### Postman 설정
1. **Method**: `GET` 선택
2. **URL**: `http://localhost:8080/api/housing/sido`
3. **Headers**: 없음 (자동 설정됨)
4. **Body**: 없음
5. **Send** 클릭

### 예상 응답 (성공)
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

### ⚠️ 오류 발생 가능성
- **404 Not Found**: 서버가 실행되지 않았거나 URL이 잘못됨
  - 해결: 서버 실행 확인, URL 확인
- **500 Internal Server Error**: 서버 내부 오류
  - 해결: 서버 로그 확인

---

## 🟢 Step 2: 시/군/구 목록 조회 (하위 드롭다운)

### ⚠️ 중요: Step 1을 먼저 실행해야 함!

### 요청 정보
```
GET http://localhost:8080/api/housing/districts?sid={sido}
```

### Postman 설정

#### 방법 1: Params 탭 사용 (권장)
1. **Method**: `GET` 선택
2. **URL**: `http://localhost:8080/api/housing/districts`
3. **Params** 탭 클릭
4. Key에 `sid` 입력
5. Value에 `서울` 입력 (Step 1에서 받은 value 사용)
6. **Send** 클릭

#### 방법 2: URL에 직접 입력
1. **Method**: `GET` 선택
2. **URL**: `http://localhost:8080/api/housing/districts?sid=서울`
3. **Send** 클릭

### 예상 응답 (성공 - 서울 선택 시)
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

### 예상 응답 (성공 - 경기 선택 시)
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

### ⚠️ 오류 발생 가능성

#### 오류 1: sido 파라미터 없음
```
GET http://localhost:8080/api/housing/districts
→ 400 Bad Request
{
  "message": "상위 드롭다운에서 광역시/도를 먼저 선택해주세요."
}
```
**원인**: `sid` 파라미터를 누락함
**해결**: `?sid=서울` 추가

#### 오류 2: sido 값이 빈 문자열
```
GET http://localhost:8080/api/housing/districts?sid=
→ 400 Bad Request
{
  "message": "상위 드롭다운에서 광역시/도를 먼저 선택해주세요."
}
```
**원인**: `sid` 값이 비어있음
**해결**: 올바른 sido 값 입력 (예: `서울`, `경기`, `부산`)

#### 오류 3: 잘못된 sido 값
```
GET http://localhost:8080/api/housing/districts?sid=잘못된값
→ 400 Bad Request
{
  "message": "선택하신 광역시/도에 해당하는 시/군/구 데이터가 없거나 잘못된 지역입니다. 다른 지역을 선택해주세요."
}
```
**원인**: 존재하지 않는 sido 값
**해결**: Step 1에서 받은 정확한 `value` 사용 (예: `서울`, `경기`, `부산`)

#### 오류 4: 해당 지역에 데이터 없음
```
GET http://localhost:8080/api/housing/districts?sid=서울
→ 400 Bad Request
{
  "message": "선택하신 광역시/도에 해당하는 시/군/구 데이터가 없습니다. 다른 지역을 선택해주세요."
}
```
**원인**: DB에 해당 지역 데이터가 없음
**해결**: 다른 지역 선택 또는 DB 데이터 확인

---

## 🟡 Step 3: 추천 요청

### ⚠️ 중요: Step 1과 Step 2를 먼저 실행해야 함!

### 요청 정보
```
POST http://localhost:8080/api/housing/recommend/v2
```

### Postman 설정

#### Step 1: Method 및 URL
1. **Method**: `POST` 선택
2. **URL**: `http://localhost:8080/api/housing/recommend/v2`

#### Step 2: Headers 설정
1. **Headers** 탭 클릭
2. Key: `Content-Type`
3. Value: `application/json`
4. 추가 버튼 클릭

#### Step 3: Body 설정
1. **Body** 탭 클릭
2. **raw** 선택
3. 드롭다운에서 **JSON** 선택
4. 아래 JSON 형식으로 입력:

```json
{
  "sido": "부산",
  "districts": ["서구", "강서구"],
  "prompt": "교통이 편리하고 주변에 편의시설이 많은 곳을 원해요"
}
```

#### Step 4: Send 클릭

### 예상 응답 (성공)
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

### ⚠️ 오류 발생 가능성

#### 오류 1: 요청 데이터 없음
```json
POST /api/housing/recommend/v2
Body: (비어있음)
→ 400 Bad Request
{
  "message": "요청 데이터가 없습니다."
}
```
**원인**: Body가 비어있음
**해결**: JSON Body 입력

#### 오류 2: 프롬프트 없음
```json
{
  "sido": "부산",
  "districts": ["서구", "강서구"]
}
→ 400 Bad Request
{
  "message": "추천 받고 싶은 내용을 입력해주세요."
}
```
**원인**: `prompt` 필드 누락 또는 빈 값
**해결**: `prompt` 필드 추가 및 내용 입력

#### 오류 3: sido 없음
```json
{
  "districts": ["서구", "강서구"],
  "prompt": "교통이 편리한 곳을 원해요"
}
→ 400 Bad Request
{
  "message": "상위 드롭다운에서 광역시/도를 먼저 선택해주세요."
}
```
**원인**: `sido` 필드 누락 또는 빈 값
**해결**: `sido` 필드 추가 (예: `"sido": "부산"`)

#### 오류 4: districts 없음
```json
{
  "sido": "부산",
  "prompt": "교통이 편리한 곳을 원해요"
}
→ 400 Bad Request
{
  "message": "하위 드롭다운에서 시/군/구를 최소 1개 이상 선택해주세요."
}
```
**원인**: `districts` 필드 누락 또는 빈 배열
**해결**: `districts` 필드 추가 (예: `"districts": ["서구"]`)

#### 오류 5: districts에 빈 값 포함
```json
{
  "sido": "부산",
  "districts": ["서구", ""],
  "prompt": "교통이 편리한 곳을 원해요"
}
→ 400 Bad Request
{
  "message": "시/군/구 선택에 빈 값이 포함되어 있습니다. 올바른 시/군/구를 선택해주세요."
}
```
**원인**: `districts` 배열에 빈 문자열 포함
**해결**: 빈 값 제거 또는 올바른 값 입력

#### 오류 6: 잘못된 sido
```json
{
  "sido": "잘못된값",
  "districts": ["서구"],
  "prompt": "교통이 편리한 곳을 원해요"
}
→ 400 Bad Request
{
  "message": "잘못된 광역시/도입니다. 올바른 지역을 선택해주세요."
}
```
**원인**: 존재하지 않는 sido 값
**해결**: Step 1에서 받은 정확한 `value` 사용

#### 오류 7: 해당 지역에 데이터 없음
```json
{
  "sido": "서울",
  "districts": ["존재하지않는구"],
  "prompt": "교통이 편리한 곳을 원해요"
}
→ 400 Bad Request
{
  "message": "선택하신 지역에 주거정보 데이터가 없습니다. 다른 지역을 선택해주세요."
}
```
**원인**: 선택한 시/군/구에 DB 데이터가 없음
**해결**: Step 2에서 받은 정확한 `value` 사용

#### 오류 8: JSON 파싱 오류
```json
{
  "sido": "부산",
  "districts": ["서구", "강서구"],
  "prompt": "교통이 편리한 곳을 원해요"
  // 쉼표 누락 또는 중괄호 누락
}
→ 400 Bad Request
{
  "message": "JSON 형식이 올바르지 않습니다."
}
```
**원인**: JSON 문법 오류
**해결**: JSON 형식 확인 (쉼표, 중괄호, 따옴표 등)

#### 오류 9: AI API 타임아웃
```json
→ 400 Bad Request
{
  "message": "추천 서비스 처리 중 오류가 발생했습니다: AI 추천 서비스 호출 중 오류가 발생했습니다: I/O error on POST request for \"https://api.upstage.ai/v1/chat/completions\": Read timed out"
}
```
**원인**: AI API 응답 시간 초과 (2분 이상)
**해결**: 
- 잠시 후 재시도
- 다른 지역 선택 (데이터가 적은 지역)
- 프롬프트 간소화

#### 오류 10: AI API JSON 파싱 오류
```json
→ 400 Bad Request
{
  "message": "추천 서비스 처리 중 오류가 발생했습니다: AI 추천 서비스 호출 중 오류가 발생했습니다: AI API 응답을 파싱하는 중 오류가 발생했습니다: Unexpected end-of-input within/between Object entries"
}
```
**원인**: AI API 응답이 불완전한 JSON
**해결**: 
- 잠시 후 재시도
- 서버 로그 확인 (실제 응답 내용 확인)

---

## 📝 전체 사용 흐름 예시

### 예시 1: 서울 중구 추천

**Step 1: 광역시/도 목록 조회**
```
GET http://localhost:8080/api/housing/sido
→ ["서울", "경기", "부산", ...] 받음
```

**Step 2: 서울의 시/군/구 목록 조회**
```
GET http://localhost:8080/api/housing/districts?sid=서울
→ ["강남구", "강동구", ..., "중구", ...] 받음
```

**Step 3: 추천 요청**
```
POST http://localhost:8080/api/housing/recommend/v2
Headers: Content-Type: application/json
Body:
{
  "sido": "서울",
  "districts": ["중구"],
  "prompt": "교통이 편리하고 주변에 편의시설이 많은 곳을 원해요"
}
→ 추천 결과 받음
```

---

### 예시 2: 부산 서구, 강서구 다중 선택

**Step 1: 광역시/도 목록 조회**
```
GET http://localhost:8080/api/housing/sido
→ ["서울", "경기", "부산", ...] 받음
```

**Step 2: 부산의 시/군/구 목록 조회**
```
GET http://localhost:8080/api/housing/districts?sid=부산
→ ["강서구", "금정구", ..., "서구", ...] 받음
```

**Step 3: 추천 요청**
```
POST http://localhost:8080/api/housing/recommend/v2
Headers: Content-Type: application/json
Body:
{
  "sido": "부산",
  "districts": ["서구", "강서구"],
  "prompt": "바다가 가까우면서도 조용한 주거 환경을 원해요"
}
→ 추천 결과 받음
```

---

### 예시 3: 경기 수원시 권선구

**Step 1: 광역시/도 목록 조회**
```
GET http://localhost:8080/api/housing/sido
→ ["서울", "경기", "부산", ...] 받음
```

**Step 2: 경기의 시/군/구 목록 조회**
```
GET http://localhost:8080/api/housing/districts?sid=경기
→ ["수원시 권선구", "수원시 장안구", ...] 받음
```

**Step 3: 추천 요청**
```
POST http://localhost:8080/api/housing/recommend/v2
Headers: Content-Type: application/json
Body:
{
  "sido": "경기",
  "districts": ["수원시 권선구"],
  "prompt": "가족이 살기 좋고 교육 환경이 좋은 곳을 찾고 있어요"
}
→ 추천 결과 받음
```

---

## ⚠️ 주의사항

### 1. 순서 준수
- 반드시 Step 1 → Step 2 → Step 3 순서로 진행
- Step 2를 먼저 호출하면 오류 발생

### 2. 값 정확성
- Step 1에서 받은 `value`를 Step 2의 `sid` 파라미터에 사용
- Step 2에서 받은 `value`를 Step 3의 `districts` 배열에 사용
- 직접 입력하지 말고 API 응답 값 사용

### 3. JSON 형식
- Body는 반드시 JSON 형식
- 모든 문자열은 따옴표로 감싸기
- 쉼표, 중괄호, 대괄호 확인

### 4. Content-Type 헤더
- Step 3에서 반드시 `Content-Type: application/json` 설정

### 5. 포트 번호
- 기본 포트: `8080`
- 변경한 경우 해당 포트 사용

---

## 🔍 디버깅 팁

### 1. 오류 메시지 확인
- 모든 오류는 `{"message": "..."}` 형식으로 반환
- 메시지를 읽고 해당 항목 수정

### 2. 서버 로그 확인
- IntelliJ 콘솔에서 서버 로그 확인
- `log.error` 또는 `log.warn` 메시지 확인

### 3. 단계별 테스트
- Step 1 → Step 2 → Step 3 순서대로 하나씩 테스트
- 각 단계가 성공한 후 다음 단계 진행

### 4. Postman Console 확인
- Postman 하단 Console 탭에서 실제 요청/응답 확인
- 네트워크 오류 여부 확인

---

## ✅ 체크리스트

### Step 1 체크리스트
- [ ] Method가 GET인가?
- [ ] URL이 정확한가? (`/api/housing/sido`)
- [ ] 서버가 실행 중인가?

### Step 2 체크리스트
- [ ] Step 1을 먼저 실행했는가?
- [ ] Method가 GET인가?
- [ ] URL에 `?sid=서울` 파라미터가 있는가?
- [ ] sido 값이 Step 1에서 받은 `value`와 일치하는가?

### Step 3 체크리스트
- [ ] Step 1과 Step 2를 먼저 실행했는가?
- [ ] Method가 POST인가?
- [ ] Headers에 `Content-Type: application/json`이 있는가?
- [ ] Body가 raw JSON 형식인가?
- [ ] `sido` 필드가 있고 값이 올바른가?
- [ ] `districts` 필드가 있고 배열에 최소 1개 이상의 값이 있는가?
- [ ] `prompt` 필드가 있고 값이 비어있지 않은가?
- [ ] JSON 문법이 올바른가? (쉼표, 중괄호, 따옴표)

