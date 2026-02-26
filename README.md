# Log Analyzer

Spring Boot 기반의 Open API 웹 로그 분석 프로그램

## 1. 프로젝트 개요

Open API 호출 로그를 분석하여 다음 정보를 산출

- 최다 호출 APIKEY

- 호출 횟수 기준 상위 3개 API Service ID

- 웹 브라우저별 사용 비율

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

Service에서 로그를 DTO로 변환
```java
public LogEntryDto parseLog(String line) {
    String[] parts = line.replace("[", "")
                         .split("]");

    String statusCode = parts[0];
    String url = parts[1];
    String browser = parts[2];
    String time = parts[3];

    String serviceId = extractServiceId(url);
    String apiKey = extractApiKey(url);

    return new LogEntryDto(statusCode, serviceId, apiKey, browser, time);
}
```

- 문자열 상태로 처리하지 않고 객체로 변환하여 가독성 확보

- 이후 통계 계산 로직을 단순화

- DTO를 통해 계층 간 결합도 감소

## 4. 통계 처리 방식

APIKEY 및 Service ID 통계는 Map 기반으로 처리

```java
Map<String, Integer> apiKeyCount = new HashMap<>();

apiKeyCount.put(apiKey,
    apiKeyCount.getOrDefault(apiKey, 0) + 1);
```
Map 선택 이유

- 평균 O(1) 시간 복잡도

- 대량 로그 처리에 적합

- 코드 단순성과 성능 균형 확보

## 5. 상위 3개 Service ID 추출
```java
List<Map.Entry<String, Integer>> topServices =
    serviceCount.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .toList();
```

- Stream API를 사용하여 가독성 확보

- 상위 3개를 limit으로 쉽게 제어 가능

- 향후 상위 N개로 확장 가능

## 6. 브라우저 비율 계산
```java
double ratio = (double) browserCount / totalCount * 100;
```

- double 연산을 통해 정확한 비율 계산

- 표현 단계에서 소수점 포맷팅 가능

## 7. Repository 분리 이유

현재는 파일 기반 로그를 읽습니다.

```java
public List<String> readLogs(String filePath) throws IOException {
    return Files.readAllLines(Path.of(filePath));
}
```

- 파일 접근 로직을 Service에서 분리

- DB 또는 외부 저장소로 변경 시 Repository만 수정

- 확장성 확보

## 8. Service 계층 중심 설계

Service는 전체 분석 흐름을 제어

```java
public AnalysisResultDto analyze(List<String> lines) {
    for (String line : lines) {
        LogEntryDto entry = parseLog(line);
        updateApiKeyCount(entry);
        updateServiceCount(entry);
        updateBrowserCount(entry);
    }

    return createResult();
}
```

- 비즈니스 로직 집중화

- Controller는 단순 요청/응답 처리

- 테스트 시 Service 단독 검증 가능

## 9. 기술 스택

- Language: Java
- Framework: Spring Boot
- Build Tool: Gradle
- Architecture: Layered Architecture

## 10. 프로젝트 구조
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
```

## 11. 확장 가능성

- DB 연동 시 Repository 교체로 확장 가능

- REST API 제공 가능

- 대용량 로그 처리 시 Stream 기반 처리 확장 가능

- 출력 형식 JSON 또는 CSV로 확장 가능
