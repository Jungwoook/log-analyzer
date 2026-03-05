# Log Analyzer

Spring Boot 기반의 Open API 웹 로그 분석 프로그램

## 1. 프로젝트 개요

Open API 호출 로그를 분석하여 다음 정보를 산출

- 최다 호출 APIKEY (동률 시 사전순 오름차순)
- 호출 횟수 기준 상위 3개 API Service ID (동률 시 서비스명 오름차순)
- 웹 브라우저별 사용 비율 (브라우저명 사전순)

## 2. 아키텍처 구조

```
Client
→ Controller
→ Service
→ Repository
```

## 3. 로그 파싱 설계

### 지원 형식

- **브래킷 형식** (kokoa.txt): `[상태코드][URL][웹브라우저][호출시간]`
- **JSON Lines 형식** (maver.log): 각 라인이 독립적인 JSON 객체

### 파싱 전략

```java
private static final Pattern APIKEY_PATTERN = 
    Pattern.compile("[?&]apikey=([A-Za-z0-9]{4})(?:&|$)");
```

정규식으로 Query delimiter(`?`, `&`)를 검증하여 오탐 방지
- ❌ 제외: `/search/aaaaapikey=abcd` (유효하지 않은 형식)
- ✅ 포함: `/search/news?apikey=ab12` (유효한 형식)

**설계 의도**: 두 가지 로그 형식을 자동 감지하여 처리하므로, 향후 새로운 형식 추가 시 Repository 추가로 확장 가능

## 4. 통계 처리 방식

Stream API 기반 함수형 처리로 안정성 및 가독성 확보

### 4.1 APIKEY 통계 계산

```java
Map<String, Long> apiKeyCounts = logEntries.stream()
    .map(LogEntryDto::apiKey)
    .filter(Objects::nonNull)
    .collect(Collectors.groupingBy(k -> k, Collectors.counting()));
```

### 4.2 Service ID 통계 계산

```java
Map<String, Long> serviceCounts = logEntries.stream()
    .map(LogEntryDto::serviceId)
    .filter(Objects::nonNull)
    .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
```

### 4.3 Browser 통계 계산

```java
Map<String, Long> browserCounts = logEntries.stream()
    .map(LogEntryDto::browser)
    .filter(Objects::nonNull)
    .collect(Collectors.groupingBy(b -> b, Collectors.counting()));
```

### Map 선택 이유

- 평균 O(1) 시간 복잡도로 대량 로그 처리 적합
- Stream API로 Thread-safe한 처리 확보
- null 필터링으로 데이터 무결성 보장
- Long 타입으로 Integer 오버플로우 방지

## 5. 정렬 및 필터링

### 최다 호출 APIKEY (동률 처리)

```java
.max(Map.Entry.<String, Long>comparingByValue()
        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
```

호출 횟수 기준 최대값 선택 후, 동률 시 사전순 역정렬로 알파벳 순 선택

### 상위 3개 Service ID 추출

```java
.sorted(Map.Entry.<String, Long>comparingByValue().reversed()
        .thenComparing(Map.Entry.comparingByKey()))
```

호출 횟수 내림차순 정렬 후, 동률 시 서비스명 오름차순으로 안정적인 결과 보장

### 브라우저 비율 (사전순)

```java
.sorted(Map.Entry.comparingByKey())
.forEach(entry -> browserRatio.put(entry.getKey(), 
    (entry.getValue() * 100.0) / totalBrowser));
```

LinkedHashMap으로 삽입 순서 유지하여 일관된 결과 제공

## 6. 계층별 책임

| 계층 | 책임 |
|------|------|
| **Controller** | HTTP 요청/응답 처리, 파일 다운로드 |
| **Service** | 비즈니스 로직, 분석 계산, 결과 파일 생성 |
| **Repository** | 로그 파일 읽기, 파싱 (2가지 형식 자동 감지) |
| **DTO** | 데이터 전달, 타입 안전성 확보 |

## 7. API 엔드포인트

### 기본 분석 (Kokoa)
- **요청**: `GET /api/analyze`
- **입력**: `logs/kokoa.txt` (브래킷 형식)
- **응답**: 분석 결과 텍스트 파일 다운로드
- **파일명**: `kokoa-result-{timestamp}.txt`

### JSON Lines 분석 (Maver)
- **요청**: `GET /api/analyze/maver`
- **입력**: `logs/maver.log` (JSON Lines 형식)
- **응답**: 분석 결과 텍스트 파일 다운로드
- **파일명**: `maver-result-{timestamp}.txt`

## 8. 기술 스택

- **Language**: Java 17
- **Framework**: Spring Boot 3.5
- **Build Tool**: Gradle
- **JSON 처리**: Jackson ObjectMapper
- **Architecture**: Layered Architecture (Controller → Service → Repository)

## 9. 프로젝트 구조

```
src/main/java/com/jw/log_analyzer
├── controller/          # HTTP 요청 처리
├── service/             # 비즈니스 로직
├── repository/          # 데이터 접근
├── dto/                 # 데이터 객체
└── LogAnalyzerApplication.java
```

## 10. 확장 가능성

- **DB 연동**: Repository 구현체만 교체로 확장
- **새로운 로그 형식**: Repository의 파싱 로직만 추가
- **대용량 처리**: Stream 기반 처리로 메모리 효율성 확보
- **REST API 확대**: Controller에 새로운 엔드포인트 추가
