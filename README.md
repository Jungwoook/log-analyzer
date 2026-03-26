# Log Analyzer

Spring Boot 기반 로그 분석 API입니다. 업로드한 로그 파일을 파싱해 아래 정보를 JSON으로 반환합니다.

- 최다 호출 API Key
- 호출 수 기준 상위 3개 서비스 ID
- 브라우저별 사용 비율

## 1. 프로젝트 개요

현재 구조는 "파일 읽기 -> 로그 파싱 -> 분석 계산 -> 결과 조합"을 단계별로 분리하는 방향으로 정리되어 있습니다.

- `controller`: HTTP 요청/응답 처리
- `service`: 전체 분석 흐름 orchestration
- `repository`: 업로드 파일에서 로그 라인 스트림 생성
- `parser`: 로그 포맷별 파싱 책임 분리
- `analysis`: 분석 항목별 계산 책임 분리

핵심 변경점은 다음 두 가지입니다.

1. 로그 파서가 `LogRepository`에서 분리되어 `parser` 패키지로 이동했습니다.
2. 통계 계산과 결과 조합이 `LogAnalysisService`에서 분리되어 `analysis` 패키지로 이동했습니다.

## 2. 요청 흐름

```text
Client
  -> LogAnalysisController
  -> LogAnalysisService
  -> LogRepository
  -> CompositeLogParser
  -> LogAnalysisResultAssembler
  -> AnalysisResultDto
```

`LogAnalysisService`는 직접 파싱 규칙이나 집계 규칙을 구현하지 않고, 각 컴포넌트를 조합하는 역할만 담당합니다.

## 3. 로그 파싱 구조

### 지원 포맷

- 기본 bracket 형식: `[상태코드][URL][브라우저][요청시각]`
- JSON Lines 형식: 각 라인이 독립적인 JSON 객체
- Apache/Nginx access log 형식

### 파서 구성

```text
parser/
  LogParser
  CompositeLogParser
  DefaultLogParser
  ApacheAccessLogParser
  UrlFieldExtractor
```

- `LogParser`
  - 각 파서 구현체의 공통 계약입니다.
  - `supports(String line)`와 `parse(String line)`를 제공합니다.
- `CompositeLogParser`
  - 등록된 파서 목록을 순회하며 처리 가능한 파서를 선택합니다.
  - 특정 파서가 실패하면 다음 후보로 넘기고, 모두 실패하면 예외를 발생시킵니다.
- `DefaultLogParser`
  - 기존 bracket 로그와 JSON Lines 로그를 처리합니다.
- `ApacheAccessLogParser`
  - Apache/Nginx 계열 access log를 처리합니다.
- `UrlFieldExtractor`
  - URL에서 `serviceId`, `apiKey`를 추출하는 공통 규칙을 담당합니다.

### 파서 분리의 의미

- 새로운 로그 포맷을 추가할 때 기존 repository 코드를 크게 수정하지 않아도 됩니다.
- URL 기반 필드 추출 규칙이 파서 구현마다 중복되지 않습니다.
- 포맷별 테스트를 parser 단위로 분리할 수 있습니다.

## 4. 로그 분석 구조

### 분석 컴포넌트

```text
analysis/
  MostCalledApiKeyCalculator
  TopServicesCalculator
  BrowserRatioCalculator
  LogAnalysisResultAssembler
```

- `MostCalledApiKeyCalculator`
  - 가장 많이 호출된 API Key를 계산합니다.
  - 호출 수가 같으면 사전순으로 뒤에 오는 값을 선택합니다.
- `TopServicesCalculator`
  - 호출 수 기준 상위 3개 서비스 ID를 계산합니다.
  - 호출 수가 같으면 서비스명을 오름차순으로 정렬합니다.
- `BrowserRatioCalculator`
  - 브라우저별 호출 비율을 계산합니다.
  - 결과는 브라우저명 기준 오름차순으로 정렬합니다.
- `LogAnalysisResultAssembler`
  - 개별 계산 결과를 `AnalysisResultDto`로 조합합니다.

### 분석 구조 변경의 의미

- 서비스 계층이 계산 규칙을 직접 들고 있지 않아 책임이 단순해졌습니다.
- 분석 항목 추가 시 기존 서비스 메서드에 집계 코드를 계속 누적하지 않아도 됩니다.
- tie-break, 정렬, 비율 계산 규칙을 항목별 테스트로 검증할 수 있습니다.

## 5. 계층별 책임

| 계층 | 책임 |
|------|------|
| `Controller` | HTTP 요청 수신, 파일 업로드 입력 처리, 예외 응답 변환 |
| `Service` | 분석 시작/종료 로깅, 파싱과 분석 흐름 조합 |
| `Repository` | `MultipartFile`에서 로그 스트림 생성, 라인 번호 기반 예외 래핑 |
| `Parser` | 로그 포맷 판별, 라인 파싱, URL 기반 필드 추출 |
| `Analysis` | API Key/서비스/브라우저 통계 계산, 응답 DTO 조합 |
| `DTO` | 파싱 결과와 API 응답 전달 |

## 6. API 엔드포인트

### 로그 분석

- 요청: `POST /api/analyze`
- Content-Type: `multipart/form-data`
- 입력: `file`
- 응답: 분석 결과 JSON

예시:

```bash
curl -X POST "http://localhost:8080/api/analyze" \
  -F "file=@./sample.log"
```

응답 예시:

```json
{
  "mostCalledApiKey": "a1b2",
  "top3Services": [
    { "serviceId": "news", "count": 120 },
    { "serviceId": "book", "count": 95 },
    { "serviceId": "map", "count": 72 }
  ],
  "browserRatio": {
    "Chrome": 55.5,
    "Safari": 31.2,
    "Whale": 13.3
  }
}
```

## 7. 프로젝트 구조

```text
src/main/java/com/jw/log_analyzer
  analysis/              # 분석 항목 계산 및 결과 조합
  controller/            # HTTP 요청/응답 처리
  dto/                   # 데이터 전달 객체
  exception/             # 예외 정의
  parser/                # 로그 포맷별 파서
  repository/            # 파일 기반 로그 읽기
  service/               # 분석 흐름 orchestration
  LogAnalyzerApplication.java
```

테스트도 구조 변경에 맞춰 패키지별로 분리되어 있습니다.

```text
src/test/java/com/jw/log_analyzer
  analysis/
  controller/
  parser/
  repository/
  service/
```

## 8. 확장 방법

### 새로운 로그 포맷 추가

1. `LogParser` 구현체를 추가합니다.
2. `supports()`와 `parse()`를 구현합니다.
3. 필요하면 `UrlFieldExtractor`의 공통 규칙을 재사용합니다.
4. parser 단위 테스트를 추가합니다.

현재 구조에서는 기존 repository나 service에 포맷 분기 코드를 직접 추가하는 방식보다, 새 parser 구현체를 추가하는 방식으로 확장하는 것이 기준입니다.

### 새로운 분석 항목 추가

1. `LogEntryDto` 컬렉션을 입력받는 계산 컴포넌트를 추가합니다.
2. `LogAnalysisResultAssembler`에 조합 로직을 반영합니다.
3. 필요하면 응답 DTO를 확장합니다.
4. 계산 컴포넌트 테스트를 추가합니다.

## 9. 기술 스택

- Language: Java 17
- Framework: Spring Boot 3.5
- Build Tool: Gradle
- JSON 처리: Jackson ObjectMapper
- Logging: SLF4J + Logback

## 10. 로깅

- 요청 진입 시 파일명 기준 `info` 로그 기록
- 파일 파싱 시작/완료 로그 기록
- 통계 계산 시작/완료 로그 기록
- 전체 분석 완료 시 `durationMs` 기록
- 라인별 파싱 실패는 `warn` 로그와 함께 라인 번호를 포함해 예외 처리
- 전체 분석 실패는 `error` 로그 기록
- 로그 파일 경로: `logs/log-analyzer.log`
