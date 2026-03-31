# 로그 분석 리팩토링 계획

## 문서 목적

이 문서는 현재 로그 분석 애플리케이션을 `Gradle 멀티 모듈 기반 modular monolith` 구조로 재설계하기 위한 리팩토링 계획 문서다.

유스케이스 정의는 별도 문서 [docs/refactoring-usecases.md](C:\Users\okjun\Desktop\project\log-analyzer\docs\refactoring-usecases.md)를 기준으로 하며, 이 문서는 그 유스케이스를 만족시키기 위한 구조, 책임 분리, 단계별 실행 계획을 다룬다.

## 핵심 리팩토링 원칙

### 코어 시스템은 계속 단순해야 한다

기본 시스템은 아래 역할만 안정적으로 수행해야 한다.

- 요청 수신
- 파일 입력 처리
- 파싱 요청
- 분석 요청
- 응답 반환

즉 `controller`, `service`, `repository`는 전체 흐름을 조합하는 역할만 맡고, 아래 규칙은 직접 소유하지 않는다.

- 로그 형식 식별 규칙
- 형식별 파싱 규칙
- 도메인별 `serviceId` 해석 규칙
- 통계 계산 규칙

### parser는 core를 몰라야 한다

core는 parser를 호출할 수 있지만, parser는 core의 구체 구현을 알면 안 된다.

parser가 몰라야 하는 대상:

- `LogAnalysisController`
- `LogAnalysisService`
- `LogRepository`
- `MultipartFile`
- API 응답 DTO

parser는 오직 parser 계약과 자기 내부 구현만 알아야 한다.

### analysis는 공통 모델만 의존해야 한다

analysis 모듈은 특정 로그 형식이나 특정 parser 구현을 알면 안 된다. analysis는 공통 로그 모델만 받아 통계를 계산해야 한다.

## 로그 판별 원칙

현재처럼 `JSON이면 Maver`, `bracket이면 Kokoa`처럼 형식만으로 도메인을 구분하는 방식은 유지하기 어렵다. 앞으로는 아래 판별 원칙을 적용한다.

### 1순위: 로그 내용 시그니처

각 도메인 parser는 로그 내용 안에서 자기 도메인을 식별할 수 있는 시그니처를 먼저 검사한다.

예:

- 특정 필드명
- 특정 URL 패턴
- 고정 prefix
- 도메인 전용 키 이름

### 2순위: 파일명

로그 내용만으로 도메인을 식별할 수 없을 때만 파일명을 보조 정보로 사용한다.

파일명은 신뢰할 수 없는 입력이므로 1순위가 될 수 없고, 시그니처 부재 시에만 제한적으로 사용한다.

### 시그니처와 파일명으로도 구분되지 않으면 실패

아래 조건이면 parser 선택을 실패로 처리한다.

- 시그니처가 없다
- 시그니처가 모호하다
- 파일명도 도메인 판별에 도움을 주지 못한다

이 경우 시스템은 추정 파싱을 시도하지 않고 `unknown` 또는 `ambiguous` 성격의 실패를 반환해야 한다.

### 형식은 도메인 판별 이후에 사용

`JSON`, `bracket`, `csv` 같은 형식 정보는 도메인 판별의 보조 요소일 수는 있지만, 도메인을 확정하는 1차 기준으로 사용하지 않는다.

권장 흐름:

1. 시그니처 검사
2. 필요 시 파일명 검사
3. 도메인 확정
4. 해당 도메인 parser 내부에서 형식 판별
5. 파싱 수행

## 도메인별 serviceId 정책

`serviceId` 규칙은 전역 규칙이 아니라 도메인별 정책으로 분리해야 한다.

### 닫힌 도메인

허용 가능한 `serviceId` 범위가 명확한 도메인이다.

예:

- `Kokoa`: `blog`, `book`, `image`, `knowledge`, `news`, `vclip`

이 경우 허용 목록 기반 검증이 적절하다.

### 열린 도메인

요구사항에 예시만 있고, 향후 다른 `serviceId`가 추가될 수 있는 도메인이다.

예:

- `Maver`: `weather`, `stock`, `news`, `map` 등

이 경우 알려진 값 목록은 참고 정보일 뿐이며, 형식적으로 유효한 `serviceId` 문자열이면 허용하는 정책이 적절하다.

### 적용 방식

도메인별 parser 또는 도메인 내부 정책 객체가 자기 `serviceId` 규칙을 소유한다.

예:

- `KokoaServiceIdPolicy`
- `MaverServiceIdPolicy`

## 목표 모듈 구성

### 1. core module

역할:

- API 요청 수신
- 유스케이스 orchestration
- 입력 파일 읽기
- parser runtime 호출
- analysis 호출
- 응답 DTO 반환

패키지:

- `controller`
- `service`
- `repository`

### 2. parser-contract module

역할:

- parser 계약 정의
- parser와 analysis가 공유하는 최소 공통 모델 정의

대상:

- `LogParser`
- `LogRecord`
- `ParseResult`

### 3. parser module

역할:

- 등록된 parser 조합
- parser 선택
- parser 선택 실패 처리

대상:

- `CompositeLogParser`
- `LogParserRegistry`
- `ParserSelectionPolicy`

### 4. parser module internal implementations

역할:

- 도메인별 시그니처 검사
- 필요 시 파일명 기반 보조 판별
- 도메인 내부 형식 판별
- 문자열 파싱
- 도메인별 필드 추출
- 도메인별 `serviceId` 정책 적용

예시 구현체:

- `KokoaLogParser`
- `MaverLogParser`
- 이후 새 도메인 parser 구현체

이 모듈은 하나로 유지하고, 그 내부에서 도메인 기준 패키지로 나눈다.

### 5. analysis module

역할:

- 공통 로그 모델 기반 통계 계산
- 결과 조합

대상:

- `MostCalledApiKeyCalculator`
- `TopServicesCalculator`
- `BrowserRatioCalculator`
- `LogAnalysisResultAssembler`

## 모듈 의존 규칙

### 허용되는 의존

- `core -> parser-contract`
- `core -> parser`
- `core -> analysis`
- `parser -> parser-contract`
- `analysis -> parser-contract`

### 금지되는 의존

- `parser -> core`
- `analysis -> controller`
- `analysis -> repository`
- `parser -> controller`
- `parser -> service`
- `parser -> repository`

## 목표 의존 구조

```text
controller -> service -> repository
                      -> parser -> parser-contract
                      -> analysis -> parser-contract

parser -> parser-contract
```

핵심은 parser 구현체가 core를 전혀 모르는 상태를 유지하는 것이다.

## 파일 구성과 경로

리팩토링 이후 파일 구성과 경로는 `옵션 2. Gradle 멀티 모듈 기반 modular monolith`를 기준으로 확정한다.

```text
log-analyzer/
├─ settings.gradle
├─ build.gradle
├─ app-core/
│  └─ src/main/java/com/jw/log_analyzer/core/
│     ├─ controller/
│     ├─ service/
│     └─ repository/
├─ parser-contract/
│  └─ src/main/java/com/jw/log_analyzer/parser_contract/
│     ├─ LogParser.java
│     ├─ LogRecord.java
│     └─ ParseResult.java
├─ parser/
│  └─ src/main/java/com/jw/log_analyzer/parser_runtime/
│     ├─ CompositeLogParser.java
│     ├─ LogParserRegistry.java
│     └─ ParserSelectionPolicy.java
│  └─ src/main/java/com/jw/log_analyzer/parser/
│     ├─ runtime/
│     │  ├─ CompositeLogParser.java
│     │  ├─ LogParserRegistry.java
│     │  └─ ParserSelectionPolicy.java
│     └─ implementations/
│        ├─ kokoa/
│        │  ├─ KokoaLogParser.java
│        │  ├─ KokoaServiceIdPolicy.java
│        │  └─ UrlFieldExtractor.java
│        └─ maver/
│           ├─ MaverLogParser.java
│           └─ MaverServiceIdPolicy.java
├─ analysis/
│  └─ src/main/java/com/jw/log_analyzer/analysis/
│     ├─ MostCalledApiKeyCalculator.java
│     ├─ TopServicesCalculator.java
│     ├─ BrowserRatioCalculator.java
│     └─ LogAnalysisResultAssembler.java
└─ app-api/
   └─ src/main/java/com/jw/log_analyzer/dto/
      └─ AnalysisResultDto.java
```

## 현재 코드 기준 권장 이동 경로

### core로 이동할 파일

- `src/main/java/com/jw/log_analyzer/controller/LogAnalysisController.java`
- `src/main/java/com/jw/log_analyzer/controller/GlobalExceptionHandler.java`
- `src/main/java/com/jw/log_analyzer/service/LogAnalysisService.java`
- `src/main/java/com/jw/log_analyzer/repository/LogRepository.java`

### parser-contract로 이동하거나 대체할 파일

- `src/main/java/com/jw/log_analyzer/parser/LogParser.java`
- `src/main/java/com/jw/log_analyzer/dto/LogEntryDto.java`

`LogEntryDto`는 API DTO보다는 공통 로그 모델 성격이 더 강하므로 `LogRecord`로 대체하는 방향이 적절하다.

### parser로 이동할 runtime 파일

- `src/main/java/com/jw/log_analyzer/parser/CompositeLogParser.java`

### parser로 이동할 implementation 파일

- `src/main/java/com/jw/log_analyzer/parser/KokoaLogParser.java`
- `src/main/java/com/jw/log_analyzer/parser/MaverLogParser.java`
- `src/main/java/com/jw/log_analyzer/parser/UrlFieldExtractor.java`

### analysis로 유지 또는 이동할 파일

- `src/main/java/com/jw/log_analyzer/analysis/MostCalledApiKeyCalculator.java`
- `src/main/java/com/jw/log_analyzer/analysis/TopServicesCalculator.java`
- `src/main/java/com/jw/log_analyzer/analysis/BrowserRatioCalculator.java`
- `src/main/java/com/jw/log_analyzer/analysis/LogAnalysisResultAssembler.java`

## 단계별 리팩토링 계획

### 1단계. 도메인 판별 원칙 정리

작업:

- 각 도메인의 시그니처 규칙 정리
- 파일명 보조 규칙 정리
- 시그니처와 파일명으로도 판별되지 않으면 실패한다는 기준 확정

완료 기준:

- parser 선택 기준이 문서와 코드 기준으로 명확해진다

### 2단계. 도메인별 serviceId 정책 정리

작업:

- 각 도메인의 `serviceId` 규칙 분류
- 닫힌 도메인과 열린 도메인 구분
- `KokoaServiceIdPolicy`, `MaverServiceIdPolicy` 같은 책임 정의

완료 기준:

- 각 도메인의 `serviceId` 검증/정규화 방식이 명확해진다

### 3단계. 논리 모듈 경계 분리

작업:

- `core`, `parser_contract`, `parser_runtime`, `parser_implementations`, `analysis` 패키지 초안 반영
- 공통 모델 후보 정리

완료 기준:

- 현재 코드가 어떤 모듈로 갈지 모두 매핑된다

### 4단계. parser-contract 도입

작업:

- `LogParser` 계약 정리
- `LogRecord` 공통 모델 도입
- `ParseResult` 정의

완료 기준:

- parser 구현체가 core 없이 계약만으로 컴파일 가능하다

### 5단계. parser 선택/runtime 구성 분리

작업:

- `CompositeLogParser`를 runtime 역할로 이동
- parser 선택 정책 추가
- service에서 구현체 직접 참조 제거

완료 기준:

- core는 parser 구현체 대신 runtime만 참조한다

### 6단계. parser 구현체 분리

작업:

- `KokoaLogParser`, `MaverLogParser`를 구현 모듈로 이동
- helper와 `ServiceIdPolicy`를 도메인 내부로 이동

완료 기준:

- parser 추가가 core 수정이 아닌 구현체 추가로 가능해진다

### 7단계. analysis 독립성 강화

작업:

- analysis 입력을 `LogRecord`로 통일
- parser 형식 관련 조건 제거

완료 기준:

- parser 변경이 analysis 변경으로 이어지지 않는다

### 8단계. Gradle 멀티 모듈 전환

작업:

- Gradle 멀티 모듈 구성으로 분리
- 모듈 의존 규칙을 빌드 설정으로 강제

완료 기준:

- 잘못된 모듈 참조가 빌드 단계에서 차단된다

## 테스트 원칙

### core 테스트

- 요청 처리
- orchestration 흐름
- 예외 변환

### parser runtime 테스트

- parser 선택
- 선택 실패 처리
- ambiguous/unknown 처리

### parser implementation 테스트

- 시그니처 판별
- 파일명 기반 보조 판별
- 정상 파싱
- 실패 케이스
- `serviceId` 정책 검증

### analysis 테스트

- 통계 계산
- 정렬 규칙
- 결과 조합

## 결론

이번 리팩토링의 목적은 단순 패키지 분리가 아니라, 코어를 작게 유지하면서 parser와 analysis를 독립 모듈로 분리하는 것이다.

특히 parser 선택은 앞으로 `형식 기반 추정`이 아니라 `시그니처 우선, 파일명 보조, 미식별 시 실패` 원칙으로 정리해야 한다. 이 기준이 먼저 고정되어야 이후 모듈 분리와 계약 설계도 흔들리지 않는다.
