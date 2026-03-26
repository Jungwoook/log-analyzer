# 로그 분석 리팩토링 점검 요약

## 리팩토링 개요

이번 리팩토링은 두 축에 집중했다.

- 로그 파싱 구조를 인터페이스 기반으로 분리
- 분석 로직을 서비스 계층에서 분리

기존에는 `LogRepository`에 포맷 판별, 파싱, URL 기반 필드 추출이 함께 들어 있었고, `LogAnalysisService`는 통계 계산과 응답 조합까지 모두 담당하고 있었다. 현재는 parser 계층과 analysis 계층이 분리되어, 서비스는 전체 흐름 orchestration 중심으로 단순화되었다.

## 변경된 구조

### 메인 패키지 구성

```text
com.jw.log_analyzer
├─ analysis
│  ├─ MostCalledApiKeyCalculator
│  ├─ TopServicesCalculator
│  ├─ BrowserRatioCalculator
│  └─ LogAnalysisResultAssembler
├─ controller
│  ├─ LogAnalysisController
│  └─ GlobalExceptionHandler
├─ dto
│  ├─ LogEntryDto
│  └─ AnalysisResultDto
├─ exception
│  └─ InvalidLogFormatException
├─ parser
│  ├─ LogParser
│  ├─ CompositeLogParser
│  ├─ DefaultLogParser
│  ├─ ApacheAccessLogParser
│  └─ UrlFieldExtractor
├─ repository
│  └─ LogRepository
└─ service
   └─ LogAnalysisService
```

### 테스트 패키지 구성

```text
src/test/java/com/jw/log_analyzer
├─ analysis
│  └─ LogAnalysisResultAssemblerTest
├─ controller
│  └─ LogAnalysisControllerTest
├─ parser
│  ├─ DefaultLogParserTest
│  ├─ ApacheAccessLogParserTest
│  └─ CompositeLogParserTest
├─ repository
│  └─ LogRepositoryTest
└─ service
   ├─ LogAnalysisServiceTest
   └─ MaverLogAnalysisTest
```

## 주요 설계 원칙

### 1. 서비스는 orchestration만 담당

`LogAnalysisService`는 현재 다음 흐름만 담당한다.

1. 업로드 파일을 받아 로그 스트림 요청
2. 파싱 성공 결과를 수집
3. 분석 조합 컴포넌트 호출
4. 결과 반환 및 로그 기록

서비스는 더 이상 정규식, 필드 추출, 통계 정렬 규칙을 직접 알지 않는다.

### 2. 파싱은 인터페이스 기반 확장 구조

`LogParser` 인터페이스를 기준으로 각 parser가 다음 책임을 가진다.

- `supports(String line)`: 해당 라인을 처리 가능한지 판별
- `parse(String line)`: 공통 로그 모델(`LogEntryDto`)로 변환

`CompositeLogParser`는 여러 parser를 조합하고, 앞선 parser 실패 시 다음 parser를 시도한다. 이 구조 덕분에 새 포맷 추가 시 기존 parser를 수정하지 않고 구현체 추가 중심으로 확장할 수 있다.

### 3. 공통 필드 추출 규칙 재사용

`UrlFieldExtractor`가 URL 기반 `serviceId`, `apiKey` 추출 책임을 가진다. 기본 parser와 신규 Apache parser가 이 규칙을 공유하므로, parser가 늘어나도 URL 해석 규칙이 중복되지 않는다.

### 4. 분석 항목별 계산기 분리

현재 분석 항목은 아래와 같이 분리되어 있다.

- `MostCalledApiKeyCalculator`
- `TopServicesCalculator`
- `BrowserRatioCalculator`

그리고 `LogAnalysisResultAssembler`가 위 결과를 기존 응답 DTO 형식으로 조합한다.

## 각 계층 및 주요 클래스의 책임

### controller

- `LogAnalysisController`
  - HTTP 요청 수신
  - 파일 업로드 입력 처리
  - 서비스 호출
- `GlobalExceptionHandler`
  - `InvalidLogFormatException`을 `400 Bad Request`로 변환

### service

- `LogAnalysisService`
  - 파싱과 분석 흐름 조립
  - 처리 단계별 로깅
  - 세부 계산 로직 비보유

### repository

- `LogRepository`
  - `MultipartFile`에서 스트림 생성
  - 라인 단위 읽기
  - parser 선택기(`CompositeLogParser`)에 위임
  - 라인 번호 기반 예외 래핑

현재 이름은 `Repository`지만 역할상 파일 기반 ingestion adapter에 가깝다.

### parser

- `LogParser`
  - parser 구현 표준 계약
- `DefaultLogParser`
  - 기존 bracket 포맷 + JSON lines 포맷 처리
- `ApacheAccessLogParser`
  - 신규 Apache/Nginx 계열 access log 예시 처리
- `CompositeLogParser`
  - parser registry / selection / fallback 역할
- `UrlFieldExtractor`
  - URL 기반 파생 필드 추출

### analysis

- `MostCalledApiKeyCalculator`
  - 최다 API Key 계산
- `TopServicesCalculator`
  - 상위 3개 서비스 계산
- `BrowserRatioCalculator`
  - 브라우저 비율 계산
- `LogAnalysisResultAssembler`
  - 항목별 계산 결과를 기존 `AnalysisResultDto`로 조합

### dto

- `LogEntryDto`
  - parser와 analyzer 사이의 공통 로그 모델
- `AnalysisResultDto`
  - 외부 API 응답 모델

## 리팩토링 전후 개선된 점

### 리팩토링 전

- `LogRepository`에 포맷 감지, 파싱, URL 추출이 응집
- `LogAnalysisService`에 집계, 정렬, 비율 계산이 응집
- 신규 로그 포맷 추가 시 기존 클래스 내부 분기 수정 필요
- 테스트가 계층 단위 중심이라 parser/분석 규칙 개별 검증이 어려움

### 리팩토링 후

- parser 계층이 인터페이스 기반 구조로 분리됨
- 기본 parser와 신규 parser가 공존 가능
- 서비스는 orchestration 중심으로 단순화됨
- 분석 항목별 계산기가 분리되어 책임이 선명해짐
- parser 단위 테스트와 analyzer 조합 테스트가 추가됨
- 신규 로그 포맷 추가가 "기존 클래스 수정"보다 "새 구현체 추가" 중심으로 바뀜

## 확장 방법

### 새로운 로그 포맷 추가

1. `LogParser` 구현체 생성
2. `supports()`와 `parse()` 구현
3. 필요하면 공통 extractor 재사용
4. Spring bean으로 등록하거나 기본 조합에 추가
5. parser 단위 테스트 추가

현재 Apache access log parser 추가로 이 확장 방식이 실제 동작함을 검증했다.

### 새로운 분석 항목 추가

1. `LogEntryDto` 컬렉션을 입력받는 계산기 추가
2. `LogAnalysisResultAssembler`에 조합 로직 반영
3. 필요 시 응답 DTO 확장
4. 계산기 단위 테스트 추가

현재 구조상 parser와 analyzer는 서로 직접 의존하지 않으므로, 로그 포맷이 늘어나도 분석 계층은 공통 모델만 기준으로 동작한다.

## 테스트 현황

현재 테스트는 다음 흐름을 커버한다.

- 컨트롤러 응답 형식 및 예외 처리
- 서비스 레벨 분석 결과 유지
- 실샘플 JSON 로그 분석 유지
- repository의 파일 기반 읽기 + 파싱 흐름
- 기본 parser의 `supports()` / 정상 파싱 / 실패 케이스
- 신규 Apache parser의 `supports()` / 정상 파싱 / 실패 케이스
- composite parser의 선택 / fallback / 실패 전파
- analysis 조합 결과 유지

### 점검 결과

- `./gradlew test` 통과
- 기존 API 응답 형식 유지
- 기존 기본 로그 포맷 지원 유지
- 신규 Apache access log 예시 포맷 추가 완료

## 확장 가능성 점검 결과

### 1. 파싱 구조

확장 가능하다.

- `DefaultLogParser`가 catch-all 성격을 가지되 우선순위가 낮게 배치됨
- 신규 parser가 우선 적용될 수 있음
- `CompositeLogParser`가 fallback을 제공함

### 2. 분석 구조

확장 가능하다.

- 항목별 계산기가 분리되어 있음
- 서비스는 세부 계산 로직을 직접 알지 않음
- 공통 모델 기반이라 parser 추가와 독립적으로 분석 로직 유지 가능

### 3. 기존 동작 유지

대체로 유지된다.

- 기존 컨트롤러 API 유지
- 기존 결과 DTO 유지
- 기존 bracket / JSON 로그 결과 유지
- 기존 테스트 통과

## 향후 개선 포인트

### 1. `LogRepository` 명칭과 역할 재정의

현재 클래스는 repository보다 file ingestion adapter에 가깝다. 추후 `LogFileReader`, `LogIngestionAdapter` 같은 이름으로 재정리하면 역할이 더 명확해진다.

### 2. 내부 도메인 모델과 API DTO 분리

현재 `LogEntryDto`가 내부 공통 모델 역할까지 같이 수행한다. 추후 `LogRecord` 같은 내부 모델을 도입하면 API DTO와 내부 모델 경계가 더 선명해진다.

### 3. 예외 정책의 세분화

현재는 parser fallback은 가능하지만, 최종적으로는 잘못된 라인에서 전체 분석이 실패한다. 향후 아래 정책을 선택 가능하게 만들 수 있다.

- strict: 현재처럼 즉시 실패
- lenient: 잘못된 라인은 건너뛰고 계속 분석
- threshold-based: 오류 수 기준 실패

### 4. analyzer 등록형 구조

현재는 `LogAnalysisResultAssembler`가 계산기들을 직접 알고 있다. 분석 항목이 더 많아지면 analyzer registry 형태로 바꿔 조합 확장성을 높일 수 있다.

### 5. parser별 샘플 리소스 테스트 강화

현재 parser 단위 테스트는 문자열 기반이다. 향후 샘플 로그 파일을 추가해 포맷별 회귀 테스트를 강화하면 유지보수성이 더 좋아진다.

## 결론

현재 리팩토링은 목표한 방향으로 의미 있게 진전되었다.

- 서비스 계층은 orchestration 중심으로 단순화되었다.
- 파싱 구조는 인터페이스 기반으로 분리되었다.
- 분석 로직은 항목별 컴포넌트로 분리되었다.
- 신규 로그 패턴 추가가 실제 구현과 테스트로 검증되었다.

아직 내부 모델 분리, 예외 정책 분리, analyzer 등록형 구조 같은 추가 개선 여지는 남아 있지만, 현재 구조는 이전보다 책임이 훨씬 선명하고 확장 가능한 상태다.
