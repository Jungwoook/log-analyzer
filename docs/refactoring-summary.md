# 로그 분석 리팩토링 요약

## 한눈에 보는 변경 사항

이번 리팩토링은 "기능은 유지하면서, 코드 구조를 더 쉽게 확장하고 관리할 수 있게 바꾸는 작업"에 초점을 맞췄습니다.

비전문가 기준으로 보면 아래 변화가 핵심입니다.

- 로그를 읽고 해석하고 결과를 계산하는 일이 한 곳에 몰려 있던 구조를 역할별로 나눴습니다.
- 새로운 로그 형식이 들어와도 기존 코드를 크게 건드리지 않고 추가할 수 있는 방향으로 바꿨습니다.
- 분석 항목별 계산 로직을 분리해서, 어떤 값을 어떻게 계산하는지 더 명확해졌습니다.
- 테스트 범위를 넓혀서 기존 기능이 깨지지 않는지 확인하기 쉬워졌습니다.

즉, 사용자 입장에서 바로 보이는 기능 변화보다는, 앞으로 유지보수와 기능 추가가 쉬워지도록 내부 구조를 정리한 작업입니다.

## 비전문가용 요약

### 무엇이 좋아졌나

- 기존에는 로그를 읽는 일, 로그 형식을 해석하는 일, 통계를 계산하는 일이 일부 클래스에 많이 몰려 있었습니다.
- 이제는 각 역할이 분리되어 문제가 생겨도 원인을 찾기 쉽고, 수정 범위도 줄어들었습니다.
- 새 로그 형식을 지원해야 할 때 전체 흐름을 다시 손대기보다, 해당 형식용 처리기만 추가하면 되는 구조에 가까워졌습니다.
- 분석 결과를 만드는 과정도 단계별로 나뉘어, 이후 요구사항이 바뀌어도 대응이 쉬워졌습니다.

### 유지된 점

- 기존 API 응답 형식은 유지됩니다.
- 기존 기본 로그 형식 처리도 계속 지원됩니다.
- 테스트를 통해 기존 동작이 깨지지 않았는지 확인하는 방향으로 정리되었습니다.

### 이 문서에서 뒤에 나오는 내용

아래부터는 실제로 어떤 패키지와 클래스가 어떻게 분리되었는지, 책임이 어떻게 바뀌었는지, 확장 방식과 테스트 범위가 어떻게 달라졌는지 기술적으로 설명합니다.

## 기술 요약

이번 리팩토링의 핵심 방향은 다음 두 가지입니다.

- 로그 파싱 구조를 인터페이스 기반으로 분리
- 분석 로직을 계산기 단위로 분리

기존에는 `LogRepository`가 로그 형식 판별, 파싱, URL 기반 필드 추출까지 함께 담당했고, `LogAnalysisService`가 통계 계산과 응답 조합까지 모두 처리했습니다. 현재는 parser 계층과 analysis 계층을 분리하여 서비스가 전체 흐름을 조율하는 역할에 더 가깝도록 정리되었습니다.

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
│  ├─ KokoaLogParser
│  ├─ MaverLogParser
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
│  ├─ KokoaLogParserTest
│  ├─ MaverLogParserTest
│  └─ CompositeLogParserTest
├─ repository
│  └─ LogRepositoryTest
└─ service
   ├─ LogAnalysisServiceTest
   └─ MaverLogAnalysisTest
```

## 주요 설계 차이

### 1. 서비스는 orchestration 중심

`LogAnalysisService`는 현재 아래 흐름을 조율하는 역할에 집중합니다.

1. 업로드 파일에서 로그 스트림 요청
2. 파싱 결과 수집
3. 분석 조합 컴포넌트 호출
4. 결과 반환 및 로그 기록

서비스는 더 이상 구체적인 필드 추출이나 통계 계산 규칙을 직접 구현하는 중심 지점이 아니도록 정리되었습니다.

### 2. 파싱은 인터페이스 기반 확장 구조

`LogParser` 인터페이스를 기준으로 각 parser가 아래 책임을 가집니다.

- `supports(String line)`: 해당 라인을 처리할 수 있는지 판단
- `parse(String line)`: 공통 로그 모델(`LogEntryDto`)로 변환

`CompositeLogParser`는 여러 parser를 조합하고, 앞선 parser가 처리하지 못하면 다음 parser를 시도합니다. 이 구조 덕분에 새로운 로그 형식을 추가할 때 기존 parser를 크게 수정하지 않고 구현체를 추가하는 방식으로 확장할 수 있습니다.

### 3. 공통 필드 추출 규칙 분리

`UrlFieldExtractor`가 URL 기반 `serviceId`, `apiKey` 추출 책임을 가집니다. 기본 parser와 Apache parser가 같은 규칙을 공유하므로, 파서가 늘어나도 URL 해석 규칙이 중복되지 않도록 정리되었습니다.

### 4. 분석 항목별 계산기 분리

분석 항목은 아래처럼 분리되어 있습니다.

- `MostCalledApiKeyCalculator`
- `TopServicesCalculator`
- `BrowserRatioCalculator`

그리고 `LogAnalysisResultAssembler`가 각 계산 결과를 기존 응답 DTO 형식으로 조합합니다.

## 계층별 책임 정리

### controller

- `LogAnalysisController`
  - HTTP 요청 수신
  - 파일 업로드 입력 처리
  - 서비스 호출
- `GlobalExceptionHandler`
  - `InvalidLogFormatException`을 `400 Bad Request`로 변환

### service

- `LogAnalysisService`
  - 파싱과 분석 흐름 조율
  - 처리 단계별 로깅
  - 상세 계산 로직 비보유

### repository

- `LogRepository`
  - `MultipartFile`에서 스트림 생성
  - 라인 단위 읽기
  - parser 선택기(`CompositeLogParser`)에 위임
  - 라인 번호 기반 예외 래핑

현재 이름은 `Repository`지만 실제 역할은 파일 기반 ingestion adapter에 더 가깝습니다.

### parser

- `LogParser`
  - parser 구현체 공통 계약
- `KokoaLogParser`
  - 기존 bracket 형식 + JSON lines 형식 처리
- `MaverLogParser`
  - 신규 Apache/Nginx 계열 access log 예시 처리
- `CompositeLogParser`
  - parser registry / selection / fallback 담당
- `UrlFieldExtractor`
  - URL 기반 파생 필드 추출

### analysis

- `MostCalledApiKeyCalculator`
  - 최다 호출 API Key 계산
- `TopServicesCalculator`
  - 상위 3개 서비스 계산
- `BrowserRatioCalculator`
  - 브라우저 비율 계산
- `LogAnalysisResultAssembler`
  - 계산 결과를 기존 `AnalysisResultDto`로 조합

### dto

- `LogEntryDto`
  - parser와 analyzer 사이의 공통 로그 모델
- `AnalysisResultDto`
  - 외부 API 응답 모델

## 리팩토링 전후 비교

### 리팩토링 전

- `LogRepository`가 형식 감지, 파싱, URL 추출까지 직접 처리
- `LogAnalysisService`가 집계, 정렬, 비율 계산까지 직접 처리
- 새로운 로그 형식 추가 시 기존 클래스 내부 분기 수정 필요
- 테스트가 계층 단위 중심이라 세부 규칙 검증이 어려움

### 리팩토링 후

- parser 계층이 인터페이스 기반 구조로 분리됨
- 기본 parser와 신규 parser가 공존 가능
- 서비스는 orchestration 중심으로 단순화됨
- 분석 항목별 계산기가 분리되어 책임이 명확해짐
- parser 단위 테스트와 analyzer 조합 테스트가 추가됨
- 새로운 로그 형식 추가가 "기존 클래스 수정"보다 "구현체 추가" 중심으로 바뀜

## 확장 방법

### 새로운 로그 형식 추가

1. `LogParser` 구현체 생성
2. `supports()`와 `parse()` 구현
3. 필요하면 공통 extractor 재사용
4. Spring bean으로 등록하거나 기본 조합에 추가
5. parser 단위 테스트 추가

현재 Apache access log parser 추가로 이 확장 방식이 실제 동작함을 검증했습니다.

### 새로운 분석 항목 추가

1. `LogEntryDto` 컬렉션을 입력받는 계산기 추가
2. `LogAnalysisResultAssembler`에 조합 로직 반영
3. 필요 시 응답 DTO 확장
4. 계산기 단위 테스트 추가

현재 구조는 parser와 analyzer가 서로 직접 의존하지 않으므로, 로그 형식이 늘어나더라도 분석 계층은 공통 모델 기준으로 동작합니다.

## 테스트 현황

현재 테스트는 아래 범위를 포함합니다.

- 컨트롤러 응답 형식 및 예외 처리
- 서비스 레벨 분석 결과 검증
- 기본 로그 및 JSON 로그 분석 검증
- repository의 파일 기반 읽기와 파싱 위임
- 기본 parser의 `supports()` / 정상 파싱 / 실패 케이스
- Apache parser의 `supports()` / 정상 파싱 / 실패 케이스
- composite parser의 선택 / fallback / 실패 전파
- analysis 조합 결과 검증

### 확인 결과

- `./gradlew test` 통과
- 기존 API 응답 형식 유지
- 기존 기본 로그 형식 지원 유지
- 신규 Apache access log 예시 형식 추가 완료

## 추가 개선 후보

### 1. `LogRepository` 명칭과 역할 재정리

현재 클래스는 repository보다는 file ingestion adapter에 가깝습니다. 이후 `LogFileReader`, `LogIngestionAdapter` 같은 이름으로 조정하면 역할이 더 명확해질 수 있습니다.

### 2. 내부 도메인 모델과 API DTO 분리

현재 `LogEntryDto`가 내부 공통 모델 역할까지 함께 수행합니다. 이후 `LogRecord` 같은 내부 모델을 도입하면 API DTO와 내부 모델의 경계가 더 명확해질 수 있습니다.

### 3. 예외 정책 세분화

현재는 parser fallback은 가능하지만 최종적으로는 잘못된 라인에서 전체 분석이 실패합니다. 이후 아래 정책을 선택 가능하게 만들 수 있습니다.

- strict: 현재처럼 즉시 실패
- lenient: 잘못된 라인은 건너뛰고 계속 분석
- threshold-based: 오류율 기준으로 실패

### 4. analyzer 등록형 구조

현재는 `LogAnalysisResultAssembler`가 계산기들을 직접 알고 있습니다. 분석 항목이 더 많아지면 analyzer registry 형태로 바꿔 조합 확장성을 높일 수 있습니다.

### 5. 샘플 리소스 기반 테스트 강화

현재 parser 단위 테스트는 문자열 기반입니다. 이후 샘플 로그 파일을 추가하여 형식별 통합 테스트를 강화하면 회귀 검증에 더 유리합니다.

## 결론

이번 리팩토링은 외부 기능을 크게 바꾸기보다, 내부 구조를 역할별로 분리해 확장성과 유지보수성을 높이는 데 목적이 있었습니다.

정리하면 다음과 같습니다.

- 서비스 계층은 전체 흐름 조율 중심으로 단순화되었습니다.
- 파싱 구조는 인터페이스 기반으로 분리되었습니다.
- 분석 로직은 항목별 컴포넌트로 분리되었습니다.
- 새로운 로그 형식 추가 방식이 더 명확해졌습니다.
- 테스트도 세부 규칙을 검증할 수 있는 방향으로 보강되었습니다.
