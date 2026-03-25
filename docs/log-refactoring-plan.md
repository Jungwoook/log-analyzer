# 로그 분석 구조 진단 및 리팩토링 계획

## 현재 구조 요약

현재 요청 흐름은 `LogAnalysisController -> LogAnalysisService -> LogRepository` 형태의 단순 계층 구조다.

- `LogAnalysisController`
  - 업로드된 `MultipartFile`을 그대로 서비스에 전달한다.
- `LogAnalysisService`
  - `repository.streamLogs(file)`로 파싱된 로그 스트림을 받아 직접 순회한다.
  - API Key, Service ID, Browser 통계를 모두 직접 누적하고 정렬 규칙까지 결정한다.
  - 최종 응답 DTO 조합까지 담당한다.
- `LogRepository`
  - 파일 입력 스트림 생성
  - 라인 단위 스트리밍
  - 로그 포맷 판별
  - 한 줄 파싱
  - URL에서 `serviceId`, `apiKey` 추출
  - 특정 서비스 ID 정규화
  - 파싱 실패 예외 래핑

### 중점 진단 결과

1. 로그 한 줄 파싱 책임
   - 실제 파싱 책임은 [`src/main/java/com/jw/log_analyzer/repository/LogRepository.java`](../src/main/java/com/jw/log_analyzer/repository/LogRepository.java)의 `parseLine`, `parseBracketLine`, `parseJsonLine`에 집중되어 있다.
   - 다만 이 클래스는 단순 파서가 아니라 파일 읽기와 예외 정책까지 함께 갖고 있어서 책임이 과도하게 넓다.

2. 포맷 결합도
   - `parseLine`은 `trimmed.startsWith("{")`로 JSON 여부를 판별하고, 나머지는 곧바로 bracket 포맷으로 간주한다.
   - bracket 포맷은 정규식 `LINE_PATTERN`, JSON 포맷은 필드명 `status_code`, `@timestamp`, `service_id`, `api_key`에 강하게 결합되어 있다.
   - URL 기반 `serviceId` 추출 규칙도 `"/search/"`, `"/v1/"`에 하드코딩되어 있어 공급자별 규칙이 코드에 직접 박혀 있다.

3. 서비스 계층의 책임 혼합
   - [`src/main/java/com/jw/log_analyzer/service/LogAnalysisService.java`](../src/main/java/com/jw/log_analyzer/service/LogAnalysisService.java)는 파싱 결과 소비, 집계, 정렬 우선순위, 비율 계산, 응답 DTO 생성까지 모두 수행한다.
   - 서비스가 오케스트레이션 계층이라기보다 분석 알고리즘 구현체 역할까지 수행한다.

4. 새 로그 패턴 추가 시 수정 범위
   - 신규 포맷 지원 시 우선 `LogRepository.parseLine` 분기 추가가 필요하다.
   - 이어서 신규 파싱 메서드, 필드 매핑, 날짜 변환, URL 파생 규칙, 테스트까지 같은 클래스 중심으로 변경된다.
   - 포맷마다 결측 필드나 파생 규칙이 달라지면 서비스의 집계 로직과 응답 조합까지 영향이 확산될 가능성이 높다.

5. 테스트 어려움의 구조적 원인
   - 파서 핵심 메서드가 모두 `private`이라서 라인 단위 테스트를 직접 하기 어렵다.
   - 파싱 진입점이 `MultipartFile` 기반이라 웹 계층 타입이 하위 계층까지 스며들어 있다.
   - `ObjectMapper`가 내부에서 직접 생성되어 대체/주입이 어렵다.
   - 집계 규칙이 서비스 메서드 내부 루프에 박혀 있어 분석 규칙별 독립 테스트가 어렵다.

## 문제점

### 1. `Repository`가 저장소보다 수집기 + 파서 + 정규화기 역할을 한다

`LogRepository`는 일반적인 persistence 추상화가 아니라 다음 책임을 동시에 갖는다.

- 파일 열기
- 라인 스트림 생성
- 포맷 감지
- 포맷별 파싱
- URL 기반 값 추출
- 서비스 ID 정규화
- 파싱 실패 정책 결정

이 구조는 "어디에 무엇을 추가해야 하는가"를 흐리게 만들고, 변경이 한 클래스에 과밀하게 쌓이게 만든다.

### 2. 파싱 로직이 특정 로그 포맷과 URL 구조에 강하게 결합되어 있다

현재 파싱은 다음 전제에 의존한다.

- bracket 로그는 `[status][url][browser][timestamp]` 순서를 가진다.
- JSON 로그는 `@timestamp`, `status_code`, `url` 등의 고정 필드명을 사용한다.
- URL 패턴은 `"/search/"` 또는 `"/v1/"`를 가진다.
- 특정 검색 서비스는 `blog`, `book`, `image`, `knowledge`, `news`, `vclip` 중 하나다.

즉, 포맷 파싱과 도메인 해석이 분리되지 않았다. 새 포맷이 들어오면 "문자열을 읽는 규칙"과 "도메인 값을 해석하는 규칙"이 같이 흔들린다.

### 3. 서비스 계층이 분석 조합과 통계 계산을 과도하게 담당한다

`LogAnalysisService`는 현재 다음을 모두 수행한다.

- 파싱 스트림 수명 관리
- 로그 엔트리 반복
- 카운트 집계
- tie-break 정렬 규칙
- 브라우저 비율 계산
- 최종 응답 DTO 생성

이렇게 되면 분석 항목이 늘어날수록 서비스 클래스가 계속 비대해진다. 예를 들어 시간대별 트래픽, 상태 코드 분포, 실패율 같은 요구가 추가되면 같은 클래스에 더 많은 맵과 정렬 규칙이 누적된다.

### 4. 예외 정책이 파서 내부에 고정되어 확장 옵션이 적다

`safeParseLine`은 파싱 실패를 `InvalidLogFormatException`으로 감싸 다시 던진다. 따라서 다음 전략을 나중에 도입하기 어렵다.

- 잘못된 줄만 skip 하고 계속 분석
- 허용 가능한 경고 수를 넘을 때만 실패
- 포맷별 오류 통계 수집
- strict / lenient 모드 선택

예외 정책은 파서 자체보다 상위 오케스트레이션 또는 정책 컴포넌트가 결정하는 편이 확장에 유리하다.

### 5. 테스트 단위가 지나치게 크다

현재 테스트는 크게 두 종류다.

- `Repository` 전체를 통해 샘플 파일을 읽는 통합성 테스트
- `Service`에서 목 스트림을 주입하는 집계 테스트

하지만 다음 단위 테스트가 분리되어 있지 않다.

- bracket 한 줄 파서 테스트
- JSON 한 줄 파서 테스트
- URL 파생 규칙 테스트
- 서비스 ID 정규화 테스트
- 통계 계산기 단위 테스트
- 오류 처리 정책 테스트

결과적으로 실패 원인을 좁히기 어렵고, 작은 규칙 변경도 큰 범위의 테스트로만 검증하게 된다.

## 개선 목표

- 파일 읽기, 라인 파싱, 도메인 정규화, 통계 계산 책임을 분리한다.
- 새 로그 포맷 추가 시 기존 코드 수정 대신 새 파서 추가로 수용할 수 있게 만든다.
- 서비스 계층은 "분석 흐름 조립"에 집중시키고 계산 규칙은 별도 컴포넌트로 이동한다.
- 웹 계층 타입(`MultipartFile`)이 파싱 코어에 직접 침투하지 않도록 경계를 정리한다.
- 작은 단위의 테스트가 가능하도록 인터페이스와 순수 함수성 컴포넌트를 늘린다.

## 제안 구조

아래는 현재 코드를 크게 흔들지 않으면서 확장성을 높이기 위한 제안 구조다.

```text
controller/
  LogAnalysisController

application/
  LogAnalysisService              # 요청 흐름 조립

domain/
  model/
    LogRecord                     # 분석용 표준 도메인 모델
    AnalysisResult
  analysis/
    LogStatisticsAggregator       # 카운트 누적
    AnalysisResultFactory         # 응답 모델 변환

ingestion/
  source/
    LogLineSource                 # InputStream/Reader -> Stream<String>
    MultipartFileLogSource
  parser/
    LogParser                     # supports + parse
    BracketLogParser
    JsonLineLogParser
    CompositeLogParser
    ParseResult                   # success/failure 표현 가능
  extractor/
    ServiceIdExtractor
    ApiKeyExtractor
  policy/
    ParseErrorPolicy              # strict/lenient 결정
```

### 핵심 설계 원칙

1. 표준 도메인 모델 도입
   - 각 로그 포맷은 먼저 `LogRecord`로 변환한다.
   - 이후 분석기는 포맷별 차이를 몰라도 된다.

2. 포맷별 파서 분리
   - `BracketLogParser`, `JsonLineLogParser`가 각자 자기 포맷만 책임진다.
   - `CompositeLogParser`가 지원 가능한 파서를 순회하며 선택한다.
   - 신규 포맷은 새 파서 클래스를 추가하고 조합에 등록하는 방식으로 확장한다.

3. 파싱과 도메인 추출 분리
   - URL에서 `serviceId`, `apiKey`를 파생하는 규칙은 별도 extractor로 분리한다.
   - 공급자별 해석 규칙이 바뀌어도 파서 클래스 전체를 흔들지 않게 한다.

4. 분석기 분리
   - 서비스 내부 루프를 `LogStatisticsAggregator` 같은 별도 컴포넌트로 이동한다.
   - tie-break, 정렬, 비율 계산 규칙도 이 계층에 모은다.

5. 입력 소스 추상화
   - 파서 코어는 `MultipartFile` 대신 `InputStream`, `Reader`, `Stream<String>` 수준에서 동작하게 한다.
   - 테스트에서는 문자열 리스트나 메모리 스트림으로 쉽게 검증할 수 있다.

## 단계별 리팩토링 계획

### 1단계. 분석 도메인 모델과 용어 정리

- `LogEntryDto`와 별도로 내부 분석용 `LogRecord`를 도입한다.
- `LogRepository`의 역할을 문서상 `repository`가 아니라 `ingestion` 또는 `parser` 성격으로 재정의한다.
- 현재 응답용 `AnalysisResultDto`는 외부 API 계약으로 유지하고, 내부 계산 결과는 별도 모델로 분리한다.

### 2단계. 라인 파서 인터페이스 도입

- `LogParser` 인터페이스를 추가한다.
  - 예: `boolean supports(String line)`
  - 예: `LogRecord parse(String line)`
- 기존 `parseBracketLine`, `parseJsonLine` 로직을 각각 별도 클래스로 이동한다.
- `CompositeLogParser`가 현재의 `parseLine` 분기 역할을 대체하게 한다.

### 3단계. URL 파생 규칙 분리

- `extractServiceIdFromUrl`, `extractApiKeyFromUrl`, `normalizeServiceId`를 별도 extractor 또는 resolver 클래스로 이동한다.
- bracket 로그, JSON 로그가 같은 규칙을 공유하는지 여부를 명시적으로 드러낸다.
- 필요하면 공급자별 resolver를 둔다.
  - 예: `KokoaServiceIdResolver`
  - 예: `MaverServiceIdResolver`

### 4단계. 입력 소스와 파싱 오케스트레이션 분리

- `MultipartFile`에서 `BufferedReader`를 여는 책임을 `LogLineSource` 계층으로 이동한다.
- 서비스는 "소스 열기 -> 파싱 -> 집계"만 조립하고, 파일 읽기 세부 구현은 별도 컴포넌트가 담당하게 한다.
- 이 단계에서 `LogRepository`는 제거하거나 이름과 역할을 명확히 축소한다.

### 5단계. 통계 계산기 분리

- 서비스 내부의 `apiKeyCounts`, `serviceCounts`, `browserCounts` 누적 로직을 `LogStatisticsAggregator`로 이동한다.
- 아래 계산 규칙을 메서드 단위로 분리한다.
  - 최다 호출 API Key 선택
  - 상위 3개 서비스 선택
  - 브라우저 비율 계산
- 서비스는 결과 객체를 받아 응답 DTO로 변환만 하게 만든다.

### 6단계. 오류 처리 정책 분리

- 파서가 예외를 바로 최종 정책으로 확정하지 않도록 `ParseErrorPolicy` 또는 `ParseResult`를 도입한다.
- 먼저 현재와 동일한 strict 정책을 유지한다.
- 이후 필요 시 lenient 정책을 추가해도 기존 파서 구현은 건드리지 않게 한다.

### 7단계. 테스트 구조 재편

- 포맷별 파서 테스트
  - bracket 정상/비정상 한 줄 테스트
  - JSON 정상/비정상 한 줄 테스트
- 추출기 테스트
  - URL별 `serviceId`, `apiKey` 추출 규칙 테스트
- 집계기 테스트
  - tie-break, top3, browser ratio 계산 테스트
- 서비스 테스트
  - 컴포넌트 조립과 예외 전파만 검증

이렇게 하면 변경 원인과 실패 원인을 더 작은 범위에서 식별할 수 있다.

## 기대 효과

- 새 로그 포맷 추가 시 기존 파서 수정 범위를 줄이고 새 파서 추가 중심으로 확장할 수 있다.
- 서비스 계층이 비대해지지 않고 분석 흐름 조립 책임에 집중할 수 있다.
- URL 해석 규칙, 포맷 파싱 규칙, 통계 계산 규칙을 독립적으로 테스트할 수 있다.
- strict/lenient 같은 오류 정책 변경이 쉬워진다.
- 특정 공급자 규칙이 늘어나도 한 클래스에 하드코딩이 누적되지 않는다.

## 우선순위 제안

현재 코드 기준에서 가장 먼저 착수할 가치가 큰 순서는 다음과 같다.

1. `LogParser` 인터페이스와 포맷별 파서 분리
2. URL 추출/정규화 로직 분리
3. 통계 계산기 분리
4. 입력 소스 추상화
5. 오류 정책 분리

이 순서가 좋은 이유는 현재 동작을 크게 바꾸지 않으면서도 확장성 병목을 가장 먼저 완화할 수 있기 때문이다.
