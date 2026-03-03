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

로그 형식
```
[상태코드][URL][웹브라우저][호출시간]
```

Repository에서 정규식 기반 파싱
```java
private static final Pattern LINE_PATTERN = 
    Pattern.compile("\\[(\\d+)]\\[(.*?)\\]\\[(.*?)\\]\\[(.*?)\\]");
private static final Pattern APIKEY_PATTERN = 
    Pattern.compile("[?&]apikey=([A-Za-z0-9]{4})(?:&|$)");
```

- 정규식을 통해 정확한 파싱 로직 구현
- APIKEY_PATTERN은 Query delimiter(`?` 또는 `&`)를 검증하여 오탐 방지
- 추출한 데이터를 LogEntryDto 객체로 변환하여 가독성 확보

## 4. 통계 처리 방식

Stream API 기반 함수형 처리로 안정성 및 가독성 확보

```java
// APIKEY 통계: Long 타입으로 오버플로우 방지
Map<String, Long> apiKeyCounts = logEntries.stream()
    .map(LogEntryDto::getApiKey)
    .filter(Objects::nonNull)
    .collect(Collectors.groupingBy(k -> k, Collectors.counting()));
```

Map 선택 이유

- 평균 O(1) 시간 복잡도로 대량 로그 처리 적합
- Stream API로 Thread-safe한 처리 확보
- null 필터링으로 데이터 무결성 보장
- Long 타입으로 Integer 오버플로우 방지

## 5. 상위 3개 Service ID 추출

```java
List<Map.Entry<String, Long>> top3Services = serviceCounts.entrySet()
    .stream()
    .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
            .thenComparing(Map.Entry.comparingByKey()))
    .limit(3)
    .collect(Collectors.toList());
```

- Stream API로 가독성 확보
- `comparingByValue().reversed()`로 호출 횟수 내림차순 정렬
- `thenComparing(Map.Entry.comparingByKey())`로 동률 시 서비스명 오름차순 정렬
- 예측 가능하고 안정적인 결과 보장

## 6. 브라우저 비율 계산

```java
Map<String, Double> browserRatio = new LinkedHashMap<>();
if (totalBrowser > 0) {
    browserCounts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> browserRatio.put(entry.getKey(), 
                (entry.getValue() * 100.0) / totalBrowser));
}
```

- `LinkedHashMap`으로 삽입 순서 보장
- 브라우저명 사전순 정렬로 일관된 결과 제공
- double 연산으로 정확한 비율 계산
- 표현 단계에서 소수점 포맷팅 가능

## 7. 최다 호출 APIKEY 추출 (동률 처리)

```java
String mostCalledApiKey = apiKeyCounts.entrySet().stream()
    .max(Map.Entry.<String, Long>comparingByValue()
            .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
    .map(Map.Entry::getKey)
    .orElse(null);
```

- `comparingByValue()`로 호출 횟수 기준 최대값 선택
- 동률 시 `thenComparing(reverseOrder())`로 사전순 역정렬(알파벳 순)
- 예: apiKey `alpha`와 `beta`의 호출 횟수가 같으면 `alpha` 선택

## 8. Repository 분리 이유

현재는 파일 기반 로그를 읽습니다.

```java
public List<LogEntryDto> readAllLogs() throws IOException {
    return Files.readAllLines(logFilePath)
                .stream()
                .map(this::parseLog)
                .collect(Collectors.toList());
}
```

- 파일 접근 로직을 Service에서 분리
- DB 또는 외부 저장소로 변경 시 Repository만 수정
- 확장성 확보

## 9. Service 계층 중심 설계

Service는 전체 분석 흐름을 제어

```java
public AnalysisResultDto analyzeAndWrite() {
    List<LogEntryDto> logs = logRepository.readAllLogs();
    
    // 1. APIKEY 통계 계산
    Map<String, Long> apiKeyCounts = logs.stream()
        .map(LogEntryDto::getApiKey)
        .filter(Objects::nonNull)
        .collect(Collectors.groupingBy(k -> k, Collectors.counting()));
    
    // 2. Service ID 상위 3개 계산
    List<Map.Entry<String, Long>> top3Services = logs.stream()
        .map(LogEntryDto::getServiceId)
        .collect(Collectors.groupingBy(s -> s, Collectors.counting()))
        .entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()))
        .limit(3)
        .collect(Collectors.toList());
    
    // 3. 브라우저 비율 계산
    Map<String, Double> browserRatio = calculateBrowserRatio(logs);
    
    return new AnalysisResultDto(mostCalledApiKey, top3Services, browserRatio);
}
```

- 비즈니스 로직 집중화
- Controller는 단순 요청/응답 처리
- 테스트 시 Service 단독 검증 가능 (Mock Repository 활용)

## 10. 기술 스택

- Language: Java 17
- Framework: Spring Boot 3.5
- Build Tool: Gradle
- Architecture: Layered Architecture
- Test: JUnit 5, Mockito, AssertJ

## 11. 프로젝트 구조

```
src/main/java/com/jw/log_analyzer
├── controller
│   └── LogAnalysisController.java
├── service
│   └── LogAnalysisService.java
├── repository
│   └── LogRepository.java
├── dto
│   ├── LogEntryDto.java
│   └── AnalysisResultDto.java
└── LogAnalyzerApplication.java

src/test/java/com/jw/log_analyzer
├── repository
│   └── LogRepositoryTest.java
└── service
    └── LogAnalysisServiceTest.java
```

## 12. 확장 가능성

- DB 연동 시 Repository 교체로 확장 가능
- REST API 제공 가능
- 대용량 로그 처리 시 Stream 기반 처리로 메모리 효율성 확보
- Mock 기반 단위 테스트로 검증 용이

## 13. API 엔드포인트

- `GET /api/analyze` : `src/main/resources/logs/kokoa.txt` 분석 결과 파일 다운로드
- `GET /api/analyze/maver` : `src/main/resources/logs/maver.log` 분석 결과 파일 다운로드
- `maver.log` 는 JSON Lines 형식(`@timestamp`, `status_code`, `url`, `service_id`, `api_key`, `browser`)을 지원

