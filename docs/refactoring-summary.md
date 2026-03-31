# 로그 분석 리팩토링 요약

## 간략 요약

이번 리팩토링으로 프로젝트를 실제 `modular monolith` 형태로 재구성했습니다.

- Gradle 멀티 모듈 구조로 분리했습니다.
- `parser-contract`, `parser`, `analysis`, `app-core` 경계를 코드와 빌드 설정으로 고정했습니다.
- 공통 로그 모델을 `LogRecord`로 분리했고, parser 선택 정책과 도메인별 `serviceId` 정책을 각 모듈 책임에 맞게 이동했습니다.
- `app-core`는 HTTP 요청 처리와 유스케이스 orchestration만 담당하도록 정리했습니다.
- 전체 테스트는 `./gradlew test`로 통과했습니다.

## 상세 설명

### 1. 최종 모듈 구성

현재 구조는 아래처럼 분리되어 있습니다.

```text
log-analyzer/
├─ app-core
├─ parser-contract
├─ parser
└─ analysis
```

각 모듈 책임은 다음과 같습니다.

- `app-core`
  - Spring Boot 애플리케이션
  - controller, service, repository
  - API 응답 DTO
  - 분석 결과를 API DTO로 변환하는 mapper
- `parser-contract`
  - parser 공통 계약
  - 공통 로그 모델 `LogRecord`
  - parser 컨텍스트
  - 공통 예외
- `parser`
  - parser 선택 정책
  - composite parser 조합
  - `KokoaLogParser`
  - `MaverLogParser`
  - `UrlFieldExtractor`
  - 도메인별 `ServiceIdPolicy`
- `analysis`
  - 통계 계산기
  - 분석 결과 조합기
  - 내부 분석 결과 모델

### 2. 모듈 의존 구조

실제 의존 구조도 문서 방향에 맞게 정리했습니다.

```text
app-core
 ├─ parser-contract
 ├─ parser
 └─ analysis

parser
 └─ parser-contract

analysis
 └─ parser-contract
```

핵심은 다음 두 가지입니다.

- parser 구현체가 `app-core`를 참조하지 않습니다.
- analysis가 controller/repository/API DTO를 알지 않습니다.

이전에는 단일 모듈 안에서 패키지 수준 분리만 되어 있었지만, 지금은 잘못된 참조가 Gradle 의존성 단계에서 더 빨리 드러나는 구조입니다.

### 3. 공통 모델 분리

기존 `LogEntryDto`는 사실상 API DTO라기보다 parser와 analysis 사이의 공통 도메인 모델 역할을 하고 있었습니다. 이 상태로는 문서에서 말한 `parser-contract` 경계를 만들기 어렵습니다.

이번 리팩토링에서는 이 모델을 `parser-contract`의 `LogRecord`로 이동했습니다.

이 변경으로 얻은 효과는 아래와 같습니다.

- parser는 `LogRecord`만 반환합니다.
- analysis는 `LogRecord`만 받아서 계산합니다.
- API 응답 모델은 `app-core`의 `AnalysisResultDto`로 분리됩니다.

즉, 내부 처리 모델과 외부 응답 모델의 경계가 분명해졌습니다.

### 4. 파서 선택 구조

parser 선택 구조는 아래처럼 정리했습니다.

- `parser-contract`
  - `LogParser`
  - `ParserContext`
- `parser`
  - `ParserSelectionPolicy`
  - `CompositeLogParser`

선택 규칙은 문서의 유스케이스를 따라 아래 순서로 동작합니다.

1. 라인 내용으로 먼저 식별합니다.
2. 내용으로 하나만 식별되면 바로 선택합니다.
3. 내용 후보가 여러 개면 파일명으로 좁힙니다.
4. 내용으로 식별이 안 되면 파일명을 보조 힌트로 사용합니다.
5. 그래도 결정할 수 없으면 명시적으로 실패합니다.

이제 parser 선택 기준이 구현체 등록 순서가 아니라 정책 객체에 모여 있습니다.

### 5. parser 구현체와 `serviceId` 정책

`parser` 모듈에는 선택 정책과 실제 parser 구현을 함께 배치했습니다.

- `KokoaLogParser`
  - bracket 로그 처리
  - `KokoaServiceIdPolicy` 사용
- `MaverLogParser`
  - JSON 로그 처리
  - `MaverServiceIdPolicy` 사용
- `UrlFieldExtractor`
  - URL에서 원시 값 추출

`serviceId` 정책도 도메인별로 분리했습니다.

- `KokoaServiceIdPolicy`
  - 허용 목록 기반 검증
- `MaverServiceIdPolicy`
  - 비어 있지 않은 문자열 허용

이 구조로 인해 parser가 직접 정책을 하드코딩하지 않고, 구현체 내부에서 필요한 규칙만 조합하도록 바뀌었습니다.

### 6. analysis 모듈 정리

`analysis` 모듈은 이제 `parser-contract`에만 의존합니다.

포함된 구성은 다음과 같습니다.

- `MostCalledApiKeyCalculator`
- `TopServicesCalculator`
- `BrowserRatioCalculator`
- `LogAnalysisResultAssembler`
- `AnalysisResult`
- `TopServiceCount`

중요한 점은 `analysis`가 더 이상 `AnalysisResultDto`를 만들지 않는다는 점입니다. 먼저 내부 결과 모델 `AnalysisResult`를 만들고, 이 값을 `app-core`에서 API 응답 DTO로 변환합니다.

이 분리 덕분에 analysis는 HTTP 응답 형태와 독립적으로 유지됩니다.

### 7. app-core 역할 축소

`app-core`는 다음 역할만 담당하도록 정리했습니다.

- 파일 업로드 요청 수신
- 로그 스트림 읽기 orchestration
- parser runtime 호출
- analysis 호출
- 분석 결과를 API DTO로 변환
- 예외를 HTTP 응답으로 변환

즉, `app-core`는 애플리케이션 조립과 입출력 어댑터 역할을 담당하고, parser/analysis의 상세 규칙은 각 하위 모듈이 맡습니다.

### 8. 테스트 재배치

테스트도 모듈별로 재배치했습니다.

- `app-core`
  - controller, service, repository, boot 테스트
- `parser`
  - `CompositeLogParserTest`
  - `KokoaLogParserTest`
  - `MaverLogParserTest`
- `analysis`
  - `LogAnalysisResultAssemblerTest`

즉, 테스트 위치 자체도 모듈 경계를 따르도록 정리했습니다.

### 9. 검증 결과

- 실행 명령: `./gradlew test`
- 결과: 통과

멀티 모듈 컴파일과 모듈별 테스트가 모두 정상 수행되는 것을 확인했습니다.

### 10. 이번 작업의 의미

이번 리팩토링은 단순한 패키지 정리가 아니라, 문서에 적어둔 modular monolith 구조를 실제 빌드 단위로 구현한 작업입니다.

정리하면 아래와 같습니다.

- 단일 모듈 프로젝트를 멀티 모듈 프로젝트로 전환했습니다.
- 공통 계약, parser, 분석, 애플리케이션 코어를 분리했습니다.
- DTO와 내부 처리 모델의 경계를 나눴습니다.
- parser 선택 규칙과 도메인 정책을 모듈 경계에 맞게 배치했습니다.
- 테스트도 모듈별로 재정렬했습니다.

이제 이후 변경은 패키지 정리 수준이 아니라, 각 모듈의 책임 안에서 더 안전하게 확장할 수 있는 상태입니다.
