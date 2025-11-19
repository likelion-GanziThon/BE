# 프론트엔드 드롭다운 구현 가이드

## 구현 방식 옵션

### 옵션 1: 2단계 드롭다운 (권장)

#### 구조
1. **광역시/도 선택 드롭다운** (1단계)
2. **시/군/구 선택 드롭다운** (2단계, 1단계 선택에 따라 동적 변경)

#### 장점
- 사용자가 직관적으로 선택 가능
- 계층적 구조로 이해하기 쉬움
- 조합 region도 별도 옵션으로 표시 가능

#### 구현 예시

```javascript
// 1단계: 광역시/도 선택
const 광역시도 = [
  { value: "서울", label: "서울특별시" },
  { value: "경기", label: "경기도" },
  { value: "부산", label: "부산광역시" },
  // ...
];

// 2단계: 시/군/구 선택 (서울 선택 시)
const 서울시군구 = [
  { value: "서울_중구", label: "중구" },
  { value: "서울_종로구", label: "종로구" },
  { value: "서울_중구,용산구", label: "중구,용산구 (조합)" },
  { value: "서울_종로구,용산구", label: "종로구,용산구 (조합)" },
  { value: "서울_용산구,성동구", label: "용산구,성동구 (조합)" },
  { value: "서울_영등포구", label: "영등포구" },
  { value: "서울_강동구", label: "강동구" },
  // ...
];

// 2단계: 시/군/구 선택 (경기 선택 시)
const 경기시군구 = [
  { value: "경기_수원시 권선구", label: "수원시 권선구" },
  { value: "경기_수원시 장안구", label: "수원시 장안구" },
  { value: "경기_수원시 영통구", label: "수원시 영통구" },
  { value: "경기_수원시 팔달구", label: "수원시 팔달구" },
  { value: "경기_성남시 중원구", label: "성남시 중원구" },
  { value: "경기_성남시 수정구", label: "성남시 수정구" },
  { value: "경기_성남시 분당구", label: "성남시 분당구" },
  // ... (DB에서 확인한 모든 경기도 시/군 추가 필요)
];
```

#### 조합 Region 처리 방법

**방법 A: 조합을 별도 옵션으로 표시**
```
서울특별시 선택 시:
- 중구
- 종로구
- 중구,용산구 (조합) ← 별도 옵션
- 종로구,용산구 (조합) ← 별도 옵션
- 용산구,성동구 (조합) ← 별도 옵션
- 영등포구
- ...
```

**방법 B: 다중 선택으로 조합 생성**
```
서울특별시 선택 시:
- [ ] 중구
- [ ] 종로구
- [ ] 용산구
- [ ] 성동구
- ...

선택한 구들을 자동으로 조합: "서울_중구,용산구"
```

---

### 옵션 2: 단일 드롭다운 (간단)

#### 구조
1. **하나의 드롭다운**에 모든 region 표시

#### 장점
- 구현이 간단
- API 응답 그대로 사용 가능

#### 단점
- 옵션이 많아서 스크롤이 길어질 수 있음
- 계층 구조가 명확하지 않음

#### 구현 예시

```javascript
// API에서 받은 데이터 그대로 사용
const regions = [
  { label: "서울 / 중구", value: "서울_중구" },
  { label: "서울 / 종로구", value: "서울_종로구" },
  { label: "서울 / 중구,용산구", value: "서울_중구,용산구" },
  { label: "경기 / 수원시 권선구", value: "경기_수원시 권선구" },
  // ...
];

// 드롭다운 렌더링
<select>
  {regions.map(region => (
    <option value={region.value}>{region.label}</option>
  ))}
</select>
```

---

## 권장 구현 방식

### ✅ 옵션 1: 2단계 드롭다운 (권장)

**이유:**
1. 사용자 경험이 더 좋음
2. 계층적 구조로 이해하기 쉬움
3. 조합 region도 명확하게 표시 가능

**구현 단계:**

1. **1단계: 광역시/도 선택**
   ```javascript
   const 광역시도목록 = [
     "서울특별시", "경기도", "부산광역시", "대구광역시", 
     "인천광역시", "광주광역시", "대전광역시", "울산광역시",
     "세종특별자치시", "전라남도", "충청남도", "충청북도"
   ];
   ```

2. **2단계: 시/군/구 선택 (동적)**
   - 1단계에서 선택한 광역시/도에 따라
   - API에서 받은 region 목록을 필터링
   - 또는 미리 정의된 매핑 사용

3. **조합 Region 처리**
   - 조합 region은 별도 옵션으로 표시
   - 또는 체크박스로 다중 선택 후 자동 조합

---

## API 활용 방법

### 방법 1: API에서 전체 목록 받아서 필터링

```javascript
// 1. API 호출
const response = await fetch('http://localhost:8080/api/housing/regions');
const allRegions = await response.json();

// 2. 광역시/도별로 그룹화
const groupedRegions = {};
allRegions.forEach(region => {
  const [광역시도] = region.label.split(' / ');
  if (!groupedRegions[광역시도]) {
    groupedRegions[광역시도] = [];
  }
  groupedRegions[광역시도].push(region);
});

// 3. 1단계 드롭다운: 광역시/도 목록
const 광역시도목록 = Object.keys(groupedRegions);

// 4. 2단계 드롭다운: 선택한 광역시/도의 시/군/구 목록
const 선택한광역시도 = "서울특별시";
const 시군구목록 = groupedRegions[선택한광역시도];
```

### 방법 2: 미리 정의된 매핑 사용

```javascript
// 광역시/도별 시/군/구 매핑 (서버에서 받거나 하드코딩)
const regionMapping = {
  "서울특별시": [
    { value: "서울_중구", label: "중구" },
    { value: "서울_종로구", label: "종로구" },
    { value: "서울_중구,용산구", label: "중구,용산구 (조합)" },
    // ...
  ],
  "경기도": [
    { value: "경기_수원시 권선구", label: "수원시 권선구" },
    // ...
  ],
  // ...
};
```

---

## 최종 답변

**네, 드롭다운은 2개만 있으면 됩니다!**

1. **광역시/도 드롭다운** (1단계)
2. **시/군/구 드롭다운** (2단계, 1단계 선택에 따라 동적 변경)

**조합 region 처리:**
- 조합 region은 2단계 드롭다운에 별도 옵션으로 표시
- 예: "중구,용산구 (조합)", "서구,강서구 (조합)"

**구현 예시:**
```jsx
// React 예시
const [selected광역시도, setSelected광역시도] = useState("");
const [selectedRegion, setSelectedRegion] = useState("");

// 1단계 드롭다운
<select value={selected광역시도} onChange={(e) => setSelected광역시도(e.target.value)}>
  <option value="">광역시/도 선택</option>
  <option value="서울특별시">서울특별시</option>
  <option value="경기도">경기도</option>
  {/* ... */}
</select>

// 2단계 드롭다운 (1단계 선택 시에만 표시)
{selected광역시도 && (
  <select value={selectedRegion} onChange={(e) => setSelectedRegion(e.target.value)}>
    <option value="">시/군/구 선택</option>
    {get시군구목록(selected광역시도).map(region => (
      <option value={region.value}>{region.label}</option>
    ))}
  </select>
)}
```

