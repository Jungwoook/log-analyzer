# Log Analyzer

로그 파일을 업로드하면 다음 통계를 JSON으로 반환하는 Spring Boot 기반 API입니다.

- 최다 호출 API Key
- 호출 수 기준 상위 3개 서비스 ID
- 브라우저별 사용 비율

이 프로젝트는 하나의 애플리케이션으로 배포되지만, 내부적으로는 Gradle 멀티모듈 기반의 modular monolith 구조로 분리되어 있습니다.

## 1. 모듈 구조

현재 프로젝트는 다음 4개 모듈로 구성됩니다.

```text
log-analyzer
├─ app-core
├─ parser-contract
├─ parser
└─ analysis
```

모듈 의존 방향은 아래와 같습니다.

```text
app-core -> parser-contract
app-core -> parser
app-core -> analysis

parser -> parser-contract
analysis -> parser-contract
```

핵심 제약은 `parser` 와 `analysis` 가 `app-core` 에 의존하지 않는다는 점입니다. 이 제약으로 HTTP 계층, 파싱 계층, 분석 계층의 책임이 Gradle 모듈 경계로 고정됩니다.

## 2. 모듈별 책임

### `app-core`

애플리케이션 진입점과 orchestration 을 담당합니다.

- Spring Boot 실행
- HTTP 요청/응답 처리
- 업로드 파일을 로그 스트림으로 변환 요청
- 파싱 결과를 분석기에 전달
- 분석 결과를 API 응답 DTO로 변환
- 예외를 HTTP 응답으로 매핑

주요 클래스:

- `LogAnalyzerApplication`
- `controller/LogAnalysisController`
- `controller/GlobalExceptionHandler`
- `repository/LogRepository`
- `service/LogAnalysisService`
- `service/AnalysisResultDtoMapper`
- `dto/AnalysisResultDto`
- `exception/LogProcessingException`

### `parser-contract`

파서와 분석 모듈이 공유하는 최소 계약을 정의합니다.

- `LogParser`: 파서 인터페이스
- `LogRecord`: 파싱 결과 공통 모델
- `ParserContext`: 입력 라인과 파일명 컨텍스트
- `InvalidLogFormatException`: 잘못된 로그 형식 예외

### `parser`

로그 형식을 판별하고 `LogRecord` 로 변환하는 모듈입니다.

- 파서 선택 정책 수행
- 로그 형식별 파싱 구현 제공
- URL 기반 필드 추출
- 서비스 ID 정규화 정책 적용

주요 클래스:

- `runtime/CompositeLogParser`
- `runtime/ParserSelectionPolicy`
- `implementations/KokoaLogParser`
- `implementations/MaverLogParser`
- `implementations/UrlFieldExtractor`
- `implementations/KokoaServiceIdPolicy`
- `implementations/MaverServiceIdPolicy`

### `analysis`

`LogRecord` 스트림을 입력받아 통계를 계산하는 모듈입니다.

- 누적 상태 관리
- 최다 호출 API Key 계산
- 상위 3개 서비스 계산
- 브라우저 비율 계산
- 분석 결과 모델 생성

주요 클래스:

- `AnalysisAccumulator`
- `AnalysisResult`
- `TopServiceCount`

## 3. 요청 처리 흐름

현재 분석 요청은 아래 순서로 처리됩니다.

```text
Client
  -> LogAnalysisController
  -> LogAnalysisService
  -> LogRepository.streamLogs(file)
  -> CompositeLogParser.parse(line, fileName)
  -> AnalysisAccumulator.accept(record)
  -> AnalysisAccumulator.toAnalysisResult()
  -> AnalysisResultDtoMapper.toDto(...)
  -> JSON Response
```

`LogAnalysisService` 는 직접 파싱 규칙이나 통계 계산 로직을 구현하지 않고, 각 모듈을 조합하는 역할만 담당합니다.

## 4. 스트리밍 분석 방식

현재 구조는 전체 로그를 메모리에 모아서 한 번에 처리하는 방식이 아니라, 스트림을 순회하며 누적 상태를 갱신하는 방식입니다.

이전 방식의 개념:

```text
Stream<LogRecord> -> toList() -> batch analyze
```

현재 방식:

```text
Stream<LogRecord> -> accumulator.accept(record) -> accumulator.toAnalysisResult()
```

이 방식으로 불필요한 중간 컬렉션 생성을 줄이고, 분석 책임을 `AnalysisAccumulator` 로 집중시켰습니다.

## 5. 지원 로그 형식

현재 파서 구현 기준으로 다음 로그 소스를 지원합니다.

- `KokoaLogParser`
  - bracket 형식 로그
  - 예: `[200][/search/news?apikey=a1b2][Chrome][2024-01-01 12:00:00]`
- `MaverLogParser`
  - JSON line 형식 로그
  - `status_code`, `url`, `browser`, `service_id`, `api_key`, `@timestamp` 필드를 사용

파서 선택은 `ParserSelectionPolicy` 와 `CompositeLogParser` 에서 처리합니다. 라인 내용 기반 판별을 우선하고, 필요 시 파일명 힌트를 사용합니다.

## 6. 분석 결과

응답 DTO는 아래 형태를 가집니다.

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

계산 규칙:

- 최다 호출 API Key는 호출 수 내림차순, 동률이면 API Key 오름차순
- 상위 서비스는 호출 수 내림차순, 동률이면 서비스 ID 오름차순
- 브라우저 비율은 브라우저명 오름차순으로 정렬

## 7. API

### 로그 분석

- Method: `POST`
- Path: `/api/analyze`
- Content-Type: `multipart/form-data`
- Form field: `file`

예시:

```bash
curl -X POST "http://localhost:8080/api/analyze" \
  -F "file=@./sample.log"
```

## 8. 예외 처리

현재 예외는 성격에 따라 분리되어 있습니다.

- `InvalidLogFormatException`
  - 잘못된 입력 로그 형식
  - 사용자 입력 문제에 해당
- `LogProcessingException`
  - 파일 처리 또는 예상하지 못한 내부 오류
  - 서버 처리 문제에 해당

`GlobalExceptionHandler` 가 이를 HTTP 응답으로 변환합니다.

## 9. 디렉터리 개요

```text
app-core/
  src/main/java/com/jw/log_analyzer
    controller/
    dto/
    exception/
    repository/
    service/

parser-contract/
  src/main/java/com/jw/log_analyzer/parser/contract

parser/
  src/main/java/com/jw/log_analyzer/parser
    implementations/
    runtime/

analysis/
  src/main/java/com/jw/log_analyzer/analysis
```

테스트도 모듈 경계에 맞춰 분리되어 있습니다.

## 10. 확장 방법

### 새로운 로그 형식 추가

1. `parser-contract` 의 `LogParser` 를 구현합니다.
2. `supports(ParserContext)` 와 `parse(ParserContext)` 를 구현합니다.
3. 필요하면 `supportsFileName(String fileName)` 도 구현합니다.
4. URL 규칙이 있으면 `UrlFieldExtractor` 와 서비스 ID 정책을 재사용합니다.
5. `parser` 모듈에 테스트를 추가합니다.

### 새로운 분석 항목 추가

1. `AnalysisAccumulator` 에 누적 상태와 계산 로직을 추가합니다.
2. 필요하면 `AnalysisResult` 를 확장합니다.
3. `AnalysisResultDtoMapper` 와 `AnalysisResultDto` 를 함께 갱신합니다.
4. `analysis` 와 `app-core` 테스트를 추가합니다.

## 11. 기술 스택

- Java 17
- Spring Boot 3.5.10
- Gradle 멀티모듈
- Jackson
- SLF4J + Logback

## 12. 로깅

현재 애플리케이션은 아래 주요 이벤트를 로깅합니다.

- 분석 시작/종료
- 파일 파싱 시작/종료
- 처리 레코드 수
- 통계 계산 시작/종료
- 라인 번호 기반 파싱 실패 경고
- 예기치 않은 내부 오류

로그 파일 경로:

- `logs/log-analyzer.log`
