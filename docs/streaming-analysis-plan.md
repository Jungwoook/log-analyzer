# 로그 분석 스트리밍 구조 전환 문서

## 문서 목적

이 문서는 현재 로그 분석 흐름을 "스트림 입력 후 전체 리스트 적재" 방식에서 "스트림 입력 후 누적 집계" 방식으로 전환하기 위한 설계 문서다.

현재 프로젝트는 parser 계층까지는 스트림 기반으로 동작하지만, service 계층에서 전체 로그를 `List`로 모은 뒤 analysis를 수행한다. 테스트용 샘플 파일은 크지 않지만, 실제 운영에서는 크기를 예측할 수 없는 로그 파일이 들어올 수 있으므로 메모리 사용량을 더 안정적으로 통제할 수 있는 구조가 필요하다.

이 문서는 다음 내용을 정리한다.

- 현재 구조에서 스트리밍이 실제로 끊기는 지점
- 목표 구조에서 어떤 책임이 어떻게 바뀌는지
- 누적 집계 방식으로 바꾸기 위한 설계 방향
- 단계별 전환 계획

## 현재 구조

현재 처리 흐름은 아래와 같다.

1. controller가 업로드 파일을 받는다.
2. service가 repository에 로그 스트림 생성을 요청한다.
3. repository가 `Stream<LogRecord>`를 반환한다.
4. service가 이 스트림을 `toList()`로 전부 수집한다.
5. analysis가 `Collection<LogRecord>`를 받아 통계를 계산한다.
6. 결과를 DTO로 변환해 응답한다.

즉 현재 구조는 겉으로는 스트림 기반처럼 보이지만, 실제 처리 방식은 아래와 같다.

```text
stream -> toList -> batch analyze
```

이 구조의 문제는 입력 파일 크기가 커질수록 `List<LogRecord>`가 메모리를 직접 압박한다는 점이다.

## 현재 구조의 한계

### 1. 스트리밍 이점이 service에서 사라진다

repository는 `Stream<LogRecord>`를 반환하지만, service가 즉시 `toList()`를 호출하면 스트림 구조의 장점이 사라진다.

결과적으로 현재 구조는 "지연 읽기"는 하되 "지연 분석"은 하지 않는 상태다.

### 2. 파일 크기에 비례해 메모리 사용량이 증가한다

현재는 로그 한 줄마다 `LogRecord` 객체를 만들고, 이 객체 전체를 메모리에 유지한 뒤 분석한다. 따라서 대용량 파일이 들어오면 전체 레코드 수에 비례해서 메모리 사용량이 증가한다.

### 3. 현재 요구사항은 전체 로그 보관을 필요로 하지 않는다

현재 분석 항목은 아래 세 가지다.

- 최다 호출 API Key
- 상위 3개 서비스
- 브라우저 비율

이 항목들은 전체 로그 레코드를 모두 메모리에 저장하지 않아도 계산할 수 있다. 각 항목은 카운터 누적만으로 충분하다.

즉 현재 분석 요구사항은 본질적으로 streaming-friendly 하다.

## 목표 구조

목표는 아래 구조다.

```text
stream -> accumulate -> finalize result
```

즉 repository는 계속 스트림을 제공하고, service 또는 analysis 계층이 이 스트림을 한 줄씩 소비하면서 누적 상태를 갱신한 뒤 마지막에 결과를 조합한다.

이 구조에서는 전체 로그를 `List<LogRecord>`로 유지하지 않는다.

## 목표 책임 분리

### repository

repository는 지금처럼 아래 역할만 담당한다.

- 업로드 파일에서 로그 라인을 읽는다
- parser를 통해 `LogRecord` 스트림을 만든다
- 스트림 종료 시 리소스를 정리한다

repository는 분석 상태를 알지 않는다.

### service

service는 다음 역할을 맡는다.

- repository에서 받은 로그 스트림을 연다
- 누적 분석기 또는 집계 컨텍스트에 각 `LogRecord`를 전달한다
- 최종 결과를 받아 DTO로 변환한다

즉 service는 "스트림 orchestration"을 담당한다.

### analysis

analysis는 더 이상 `Collection<LogRecord>` 전체를 입력으로 받지 않는다. 대신 아래 형태 중 하나로 바뀐다.

1. 누적형 분석기
2. 누적 상태 객체 + 최종 결과 조합기

추천은 두 번째다.

예시:

- `AnalysisAccumulator`
  - `accept(LogRecord record)`
  - `toAnalysisResult()`

이 구조라면 service는 스트림의 각 레코드를 accumulator에 전달하고, 마지막에 결과만 꺼내면 된다.

## 권장 설계안

### 옵션 1. 단일 누적기 방식

하나의 accumulator가 모든 통계를 관리한다.

예시 책임:

- API Key 카운트 맵
- 서비스 카운트 맵
- 브라우저 카운트 맵
- 전체 브라우저 수

장점:

- 가장 단순하다
- service와 analysis 연결이 쉽다
- 현재 프로젝트 규모에 적합하다

단점:

- 분석 항목이 많아지면 accumulator가 비대해질 수 있다

### 옵션 2. 항목별 누적기 조합 방식

분석 항목별로 accumulator를 분리한다.

예시:

- `MostCalledApiKeyAccumulator`
- `TopServicesAccumulator`
- `BrowserRatioAccumulator`
- `StreamingAnalysisAssembler`

장점:

- 분석 항목별 책임이 분리된다
- 향후 항목 추가에 유리하다

단점:

- 현재 프로젝트 규모에서는 다소 구조가 커질 수 있다

### 현재 프로젝트 기준 추천

현재는 항목 수가 적고 복잡도가 크지 않으므로, 먼저 단일 누적기 방식으로 시작하는 것이 적절하다.

이후 분석 항목이 증가하면 항목별 누적기로 다시 분리하는 것이 자연스럽다.

## 자료구조 제안

현재 요구사항 기준으로 아래 자료구조면 충분하다.

- `Map<String, Long>` apiKeyCounts
- `Map<String, Long>` serviceCounts
- `Map<String, Long>` browserCounts

필요한 보조 값:

- 브라우저 전체 카운트는 `browserCounts.values()` 합산으로 계산 가능
- 정렬은 최종 결과 생성 시점에만 수행

즉 핵심은 "읽는 동안 카운트만 누적하고, 정렬은 마지막에 한 번만 수행"하는 것이다.

## 예상 처리 흐름

목표 처리 흐름은 아래와 같다.

1. controller가 파일을 받는다.
2. service가 repository에서 `Stream<LogRecord>`를 받는다.
3. service가 try-with-resources 안에서 스트림을 소비한다.
4. 각 `LogRecord`를 accumulator에 전달한다.
5. 스트림 소비가 끝나면 accumulator가 `AnalysisResult`를 생성한다.
6. service가 이를 API DTO로 변환한다.
7. controller가 응답을 반환한다.

예시 흐름:

```text
controller
 -> service
 -> repository.streamLogs(file)
 -> forEach(accumulator::accept)
 -> accumulator.toAnalysisResult()
 -> mapper.toDto(...)
 -> response
```

## 변경 대상

### 1. LogAnalysisService

현재 `logs.toList()`를 제거하고, 스트림을 직접 순회하도록 변경해야 한다.

현재:

```text
Stream<LogRecord> -> List<LogRecord> -> assemble(Collection)
```

목표:

```text
Stream<LogRecord> -> accumulator.accept(record) -> toAnalysisResult()
```

### 2. LogAnalysisResultAssembler

현재는 `Collection<LogRecord>`를 입력으로 받는다. 이 구조는 스트리밍 분석과 맞지 않으므로 다음 둘 중 하나로 바뀌어야 한다.

- accumulator 생성/조합 책임까지 가지는 구조
- 최종 결과 조합만 담당하고, 별도 accumulator가 도입되는 구조

권장 방향은 "최종 결과 조합만 남기고, 누적 상태는 별도 클래스로 분리"하는 것이다.

### 3. 계산기들

현재 계산기들은 모두 `Collection<LogRecord>`를 입력으로 받는다. 스트리밍 구조로 바꾸려면 아래 중 하나를 선택해야 한다.

- 계산기별 `accept(LogRecord)` / `result()` 인터페이스 도입
- 단일 accumulator 내부로 계산 로직 흡수

프로젝트 규모를 고려하면 초기 전환은 단일 accumulator가 더 적절하다.

## 테스트 전략

스트리밍 전환 후 테스트는 아래처럼 재정리하는 것이 좋다.

### service 테스트

- 전체 리스트를 모으지 않고도 동일 결과가 나오는지 검증
- 대량 데이터 입력을 흉내 내는 스트림 테스트 추가 가능

### analysis 테스트

- 누적기 단위 테스트
- 레코드 입력 순서가 바뀌어도 동일 결과가 나오는지 검증
- tie-break 정렬 규칙 유지 검증

### repository 테스트

- 현재처럼 파싱 스트림이 정상 생성되는지만 검증
- 분석과 관련된 기대는 repository 테스트에 두지 않는다

## 단계별 전환 계획

### 1단계. 스트리밍 누적기 도입

- `AnalysisAccumulator` 도입
- `accept(LogRecord)`와 `toAnalysisResult()` 정의

### 2단계. service 전환

- `LogAnalysisService`에서 `toList()` 제거
- 스트림을 순회하며 accumulator에 전달

### 3단계. 기존 batch 계산기 정리

- `Collection<LogRecord>` 기반 계산기 제거 또는 내부 구현 축소
- 필요 시 accumulator 내부 구현으로 통합

### 4단계. 테스트 보강

- 기존 결과 회귀 테스트 유지
- 큰 입력을 가정한 메모리 친화적 구조 테스트 추가

## 기대 효과

- 입력 파일 크기에 덜 민감한 구조
- 전체 레코드 리스트 보관 제거
- 현재 요구사항에 더 맞는 분석 방식
- 이후 대용량 로그 처리 시 메모리 위험 감소

## 주의사항

스트리밍 구조로 바꾼다고 해서 모든 문제가 사라지는 것은 아니다.

- 카운트 맵 자체는 고유 키 수만큼 메모리를 사용한다
- 서비스 종류, API Key 종류가 매우 많으면 맵 크기도 커질 수 있다
- 다만 현재 구조처럼 "전체 레코드 수"에 비례해서 메모리가 증가하는 것보다는 훨씬 안정적이다

즉 이 전환의 목적은 메모리 사용을 0으로 만드는 것이 아니라, 증가 기준을 "전체 로그 수"에서 "집계 대상 고유 키 수"로 바꾸는 것이다.

## 결론

현재 구조는 repository까지만 스트리밍이고, 실제 분석은 batch 처리다. 파일 크기를 예측할 수 없는 환경까지 고려하면 이 구조는 장기적으로 불안정하다.

따라서 다음 리팩토링 방향은 명확하다.

- `Stream<LogRecord>`를 유지한다
- service에서 `toList()`를 제거한다
- analysis를 누적 집계 방식으로 전환한다

즉, 앞으로의 목표는 "스트림 입력"이 아니라 "스트림 분석"을 구현하는 것이다.
