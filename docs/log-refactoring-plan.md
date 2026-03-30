# 로그 분석 리팩토링 계획

## 문서 목적

이 문서는 현재 로그 분석 애플리케이션을 `modular monolith` 관점으로 재구성하기 위한 리팩토링 기준 문서다.

이번 계획은 단순 패키지 정리가 아니다. 하나의 애플리케이션으로 배포하되, 내부를 독립적인 모듈 단위로 나누고 모듈 간 의존 규칙을 강하게 적용하는 것을 목표로 한다.

핵심 전제는 다음과 같다.

- 기본 시스템은 가능한 한 작게 유지한다.
- 새로운 로그 형식 추가는 기존 시스템 수정이 아니라 parser 구현 확장으로 해결한다.
- parser는 기본 시스템을 몰라야 한다.
- 분석 로직도 입력 형식이 아니라 공통 모델에만 의존해야 한다.

## 왜 Modular Monolith인가

현재 시스템의 문제는 배포 단위가 하나라는 점이 아니라, 내부 경계가 약하다는 점이다.

즉, 지금 필요한 것은 마이크로서비스 분리가 아니라 아래와 같은 구조다.

- 하나의 애플리케이션으로 실행
- 내부는 여러 모듈로 분리
- 모듈별 책임이 명확함
- 허용된 의존 방향만 유지
- 새로운 기능은 기존 코어 수정이 아니라 새 구현 추가로 수용

이 요구사항은 `modular monolith`와 가장 잘 맞는다.

## 목표 유스케이스

### 유스케이스 1. 새로운 로그 형식이 추가된다

시스템은 앞으로도 새로운 로그 형식을 받을 수 있다. 따라서 새 로그 형식 추가는 예외가 아니라 기본 시나리오다.

기대 결과:

1. 새 로그 형식을 지원할 때 코어 모듈 수정이 최소화된다.
2. 새 형식은 `parser-implementations` 내부에 새로운 parser 구현체를 추가하는 방식으로 대응한다.
3. 분석 모듈은 새 로그 형식을 몰라도 동작한다.
4. 테스트도 새 parser 구현체 중심으로 추가할 수 있다.

### 유스케이스 2. 코어 시스템은 계속 단순해야 한다

기본 시스템은 아래 역할만 안정적으로 수행해야 한다.

- 요청 수신
- 파일 입력 처리
- 파싱 요청
- 분석 요청
- 응답 반환

즉, 코어는 유스케이스 orchestration만 담당하고, 형식별 파싱 규칙은 코어 밖 모듈로 밀어내야 한다.

## 목표 모듈 구성

modular monolith 관점에서 이번 시스템은 아래 모듈로 나눈다.

### 1. core module

역할:

- 웹 요청 수신
- 애플리케이션 유스케이스 orchestration
- 입력 파일 읽기
- parser runtime 호출
- analysis 호출
- 응답 DTO 반환

핵심 패키지:

- `controller`
- `service`
- `repository`

### 2. parser-contract module

역할:

- parser가 구현해야 하는 최소 계약 정의
- parser와 core가 공유하는 공통 모델 정의

핵심 타입:

- `LogParser`
- `LogRecord`
- `ParseResult`

### 3. parser-runtime module

역할:

- 등록된 parser 구현체를 조합
- 적절한 parser 선택
- fallback 처리

핵심 타입:

- `CompositeLogParser`
- `LogParserRegistry`

### 4. parser-implementations module

역할:

- 형식별 로그 판별
- 문자열 해석
- 형식별 필드 추출
- parser 내부 helper 사용

예시 구현체:

- `KokoaLogParser`
- `MaverLogParser`
- 이후 신규 parser 구현체

이 모듈은 하나로 유지하되, 내부에서 로그 출처 또는 로그 계열 기준으로 패키지를 세분화할 수 있다.

### 5. analysis module

역할:

- 공통 로그 모델 기준 분석
- 통계 계산
- 결과 조합

핵심 타입:

- `MostCalledApiKeyCalculator`
- `TopServicesCalculator`
- `BrowserRatioCalculator`
- `LogAnalysisResultAssembler`

## 모듈 의존 규칙

### 허용하는 의존

- `core -> parser-contract`
- `core -> parser-runtime`
- `core -> analysis`
- `parser-runtime -> parser-contract`
- `parser-implementations -> parser-contract`
- `analysis -> parser-contract`

### 금지하는 의존

- `parser-implementations -> core`
- `parser-runtime -> controller`
- `parser-runtime -> service`
- `parser-runtime -> repository`
- `analysis -> controller`
- `analysis -> repository`

핵심 규칙은 이것이다.

- core는 parser를 사용할 수 있다.
- parser는 core를 참조하지 않는다.
- analysis는 로그 형식이 아니라 공통 모델만 안다.

## 목표 의존 구조

```text
controller -> service -> repository
                      -> parser-runtime -> parser-contract
                      -> analysis -> parser-contract

parser-implementations -> parser-contract
```

이 구조에서 중요한 점은 parser 구현체가 `controller`, `service`, `repository`를 모른다는 것이다.

## 파일 구성과 경로 제안

## 옵션 1. 단일 프로젝트 내부 패키지 기반 모듈화

초기 단계에서는 하나의 프로젝트 안에서 모듈 경계를 패키지로 표현할 수 있다.

```text
src/main/java/com/jw/log_analyzer/
├─ core/
│  ├─ controller/
│  │  ├─ LogAnalysisController.java
│  │  └─ GlobalExceptionHandler.java
│  ├─ service/
│  │  └─ LogAnalysisService.java
│  └─ repository/
│     └─ LogRepository.java
├─ parser_contract/
│  ├─ LogParser.java
│  ├─ LogRecord.java
│  └─ ParseResult.java
├─ parser_runtime/
│  ├─ CompositeLogParser.java
│  └─ LogParserRegistry.java
├─ parser_implementations/
│  ├─ kokoa/
│  │  ├─ KokoaLogParser.java
│  │  └─ UrlFieldExtractor.java
│  └─ maver/
│     └─ MaverLogParser.java
├─ analysis/
│  ├─ MostCalledApiKeyCalculator.java
│  ├─ TopServicesCalculator.java
│  ├─ BrowserRatioCalculator.java
│  └─ LogAnalysisResultAssembler.java
└─ dto/
   └─ AnalysisResultDto.java
```

이 구조는 논리적 모듈화에는 유효하지만, 빌드 레벨 강제는 약하다.

## 옵션 2. Gradle 멀티 모듈 기반 modular monolith

중장기적으로는 아래 구성이 더 적절하다.

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
├─ parser-runtime/
│  └─ src/main/java/com/jw/log_analyzer/parser_runtime/
│     ├─ CompositeLogParser.java
│     └─ LogParserRegistry.java
├─ parser-implementations/
│  └─ src/main/java/com/jw/log_analyzer/parser_implementations/
│     ├─ kokoa/
│     │  ├─ KokoaLogParser.java
│     │  └─ UrlFieldExtractor.java
│     └─ maver/
│        └─ MaverLogParser.java
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

이 구조에서는 빌드 설정으로 모듈 의존 방향을 강제할 수 있다. modular monolith를 제대로 구현하려면 최종적으로 이 구조를 목표로 하는 편이 낫다.

## 현재 코드 기준 권장 이동 경로

현재 파일 기준으로는 아래 방향이 적절하다.

### core로 남길 파일

- `src/main/java/com/jw/log_analyzer/controller/LogAnalysisController.java`
- `src/main/java/com/jw/log_analyzer/controller/GlobalExceptionHandler.java`
- `src/main/java/com/jw/log_analyzer/service/LogAnalysisService.java`
- `src/main/java/com/jw/log_analyzer/repository/LogRepository.java`

### parser-contract로 이동할 파일 또는 대체할 파일

- `src/main/java/com/jw/log_analyzer/parser/LogParser.java`
- `src/main/java/com/jw/log_analyzer/dto/LogEntryDto.java`

단, `LogEntryDto`는 API DTO 성격보다 내부 공통 모델 성격이 강하므로, `LogRecord`로 대체하는 편이 좋다.

### parser-runtime로 이동할 파일

- `src/main/java/com/jw/log_analyzer/parser/CompositeLogParser.java`

### parser-implementations로 이동할 파일

- `src/main/java/com/jw/log_analyzer/parser/KokoaLogParser.java`
- `src/main/java/com/jw/log_analyzer/parser/MaverLogParser.java`
- `src/main/java/com/jw/log_analyzer/parser/UrlFieldExtractor.java`

### analysis 모듈로 유지 또는 이동할 파일

- `src/main/java/com/jw/log_analyzer/analysis/MostCalledApiKeyCalculator.java`
- `src/main/java/com/jw/log_analyzer/analysis/TopServicesCalculator.java`
- `src/main/java/com/jw/log_analyzer/analysis/BrowserRatioCalculator.java`
- `src/main/java/com/jw/log_analyzer/analysis/LogAnalysisResultAssembler.java`

## 모듈별 책임 정리

### core

- 파일 업로드 입력 처리
- 입력 소스 준비
- parser runtime 호출
- 분석 모듈 호출
- API 응답 반환

### parser-contract

- 공통 parser 계약 유지
- 공통 로그 모델 유지
- parser 성공/실패 표현 유지

### parser-runtime

- parser 목록 관리
- parser 선택
- fallback 정책 수행

### parser-implementations

- 형식별 지원 여부 판단
- 로그 문자열 파싱
- 형식별 필드 추출
- parser 내부 에러 처리

### analysis

- 공통 모델 기반 통계 계산
- 결과 정렬 규칙 적용
- 최종 결과 조합

## 반드시 지켜야 할 규칙

### parser 모듈이 몰라야 하는 것

- `LogAnalysisController`
- `LogAnalysisService`
- `LogRepository`
- `MultipartFile`
- `AnalysisResultDto`
- 브라우저 비율 계산기 같은 분석 컴포넌트

### analysis 모듈이 몰라야 하는 것

- 로그 형식별 parser 구현체
- 파일 업로드 타입
- controller/repository 상세 구현

### core가 가져야 하는 태도

- parser 구현 상세를 알지 않는다.
- analysis 계산 상세를 알지 않는다.
- 전체 흐름만 조율한다.

## 단계별 리팩토링 계획

### 1단계. 논리 모듈 경계 먼저 분리

작업:

- `core`, `parser_contract`, `parser_runtime`, `parser_implementations`, `analysis` 패키지 구조 초안 반영
- 현재 클래스 책임 문서화
- 공통 모델 후보 정리

완료 기준:

- 현재 코드가 어떤 모듈로 갈지 모두 매핑됨

### 2단계. parser-contract 도입

작업:

- `LogParser` 계약 정리
- `LogRecord` 공통 모델 도입
- `ParseResult` 정의

완료 기준:

- parser 구현체가 core 없이 계약만으로 컴파일 가능

### 3단계. parser-runtime 분리

작업:

- `CompositeLogParser`를 runtime 역할로 재배치
- registry 또는 등록 구조 도입
- 서비스에서 구현체 직접 참조 제거

완료 기준:

- core는 parser 구현체 대신 runtime만 참조

### 4단계. parser-implementations 모듈화

작업:

- `KokoaLogParser`, `MaverLogParser`를 구현 모듈 내부로 이동
- extractor/helper를 parser 구현 모듈 내부로 이동
- 향후 새 로그 형식은 같은 모듈 안에 새 구현체를 추가하는 방식으로 정리

완료 기준:

- 새 parser 추가가 코어 수정이 아니라 구현체 추가로 가능

### 5단계. analysis 독립성 강화

작업:

- 분석 입력을 `LogRecord`로 통일
- parser 형식 관련 조건 제거

완료 기준:

- parser 추가가 analysis 변경으로 이어지지 않음

### 6단계. 물리 모듈 분리

작업:

- 필요 시 Gradle 멀티 모듈 전환
- 모듈별 빌드 의존 설정 추가

완료 기준:

- 잘못된 모듈 참조가 빌드 단계에서 차단됨

## 테스트 전략

modular monolith에서는 테스트도 모듈 단위로 나누어야 한다.

### core 테스트

- 요청 처리
- orchestration 흐름
- 예외 변환

### parser-runtime 테스트

- parser 선택
- fallback 동작

### parser-implementations 테스트

- supports 판별
- 정상 파싱
- 실패 케이스
- helper 규칙 검증

### analysis 테스트

- 통계 계산
- 정렬 규칙
- 결과 조합

## 권장 구현 순서

1. 현재 단일 프로젝트 안에서 논리 모듈 경계를 먼저 만든다.
2. `LogRecord`와 parser 계약을 먼저 안정화한다.
3. parser 구현을 core 밖으로 밀어낸다.
4. analysis를 공통 모델 기반으로 고정한다.
5. 구조가 안정되면 Gradle 멀티 모듈로 승격한다.

## 결론

이번 리팩토링은 parser 패키지를 조금 더 정리하는 수준이 아니라, 애플리케이션 전체를 modular monolith로 재구성하는 작업으로 보는 것이 맞다.

정리하면 다음과 같다.

- `core`는 작고 단순하게 유지한다.
- `parser`는 계약, runtime, implementations로 나누어 다룬다.
- `analysis`는 공통 모델만 의존한다.
- 새 로그 형식 추가는 코어 수정이 아니라 `parser-implementations` 확장으로 수용한다.
- 최종적으로는 Gradle 멀티 모듈 구조까지 가는 것이 가장 명확한 목표다.
