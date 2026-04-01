# 로그 분석 스트리밍 구조 전환 문서

## 문서 목적

이 문서는 현재 로그 분석 흐름을 "스트림 입력 후 전체 리스트 적재" 방식에서 "스트림 입력 후 누적 집계" 방식으로 전환하기 위한 설계 문서다.

현재 프로젝트는 parser 계층까지는 스트림 기반으로 동작하지만, service 계층에서 전체 로그를 `List`로 모은 뒤 analysis를 수행한다. 테스트용 샘플 파일은 크지 않지만, 실제 운영에서는 크기를 예측할 수 없는 로그 파일이 들어올 수 있으므로 메모리 사용량을 더 안정적으로 통제할 수 있는 구조가 필요하다.

이 문서는 다음 내용을 정리한다.

- 현재 구조에서 스트리밍이 실제로 끊기는 지점
- 목표 구조에서 어떤 책임이 어떻게 바뀌는지
- 누적 상태 객체 방식으로 바꾸기 위한 설계 방향
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

즉 repository는 계속 스트림을 제공하고, service가 이 스트림을 한 줄씩 소비하면서 누적 상태 객체를 갱신한 뒤 마지막에 최종 결과를 생성한다.

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
- 스트림의 각 `LogRecord`를 누적 상태 객체에 전달한다
- 누적 상태 객체로부터 최종 분석 결과를 받는다
- 결과를 DTO로 변환한다

즉 service는 "스트림 orchestration"을 담당한다.

### analysis

analysis는 더 이상 `Collection<LogRecord>` 전체를 입력으로 받지 않는다. 대신 `LogRecord`를 한 건씩 받아서 내부 상태를 누적하는 객체를 중심으로 구성한다.

이 문서에서 권장하는 기본 구조는 아래와 같다.

- `AnalysisAccumulator`
  - `accept(LogRecord record)`
  - `toAnalysisResult()`

즉 analysis의 중심은 "누적 상태 객체"다. 읽는 동안에는 상태만 갱신하고, 스트림 소비가 끝나면 마지막에 한 번만 결과를 계산한다.

### AnalysisAccumulator 생성 원칙

`AnalysisAccumulator`는 요청이 들어올 때마다 새로 생성해야 한다.

- 누적 상태를 가지는 객체이므로 singleton Spring bean으로 두지 않는다
- 이전 요청의 집계 상태가 다음 요청으로 섞이면 안 된다
- service는 분석 시작 시 accumulator 인스턴스를 새로 만들고, 해당 요청 안에서만 사용한다

## analysis 설계 방향

### 1. 권장 방식: 누적 상태 객체 + 최종 결과 생성

현재 프로젝트는 분석 항목이 많지 않고, 모두 카운트 기반이다. 따라서 항목별 분석기를 여러 개 두는 것보다 하나의 누적 상태 객체에 필요한 집계 상태를 모으는 편이 더 단순하고 실용적이다.

예시:

- `AnalysisAccumulator`
  - `Map<String, Long> apiKeyCounts`
  - `Map<String, Long> serviceCounts`
  - `Map<String, Long> browserCounts`
  - `accept(LogRecord record)`
  - `toAnalysisResult()`

이 구조에서 analysis는 아래 두 단계로 나뉜다.

1. 누적 단계
   - 각 레코드를 읽을 때마다 카운트만 증가시킨다.
2. 결과 생성 단계
   - 모든 입력이 끝난 뒤 정렬, 비율 계산, top N 계산을 한 번만 수행한다.

이 방식의 장점은 다음과 같다.

- 현재 요구사항에 가장 단순하다.
- 전체 로그를 메모리에 저장하지 않는다.
- service가 연결하기 쉽다.
- 이후 필요하면 내부 구현을 항목별로 다시 분리할 수 있다.

단점은 다음과 같다.

- 분석 항목이 계속 늘어나면 accumulator가 커질 수 있다.
- 항목별 책임이 한 객체 안에 모일 수 있다.

하지만 현재 프로젝트 규모에서는 이 단점보다 단순성 이점이 더 크다.

### 2. 대안 방식: 누적형 분석기 여러 개

대안으로는 분석 항목마다 별도 누적형 분석기를 두는 방식도 있다.

예시:

- `MostCalledApiKeyAnalyzer`
- `TopServicesAnalyzer`
- `BrowserRatioAnalyzer`

각 객체가 `accept(LogRecord)`와 `result()`를 가지는 식이다.

이 방식의 장점은 다음과 같다.

- 항목별 책임이 더 분명하다.
- 분석 항목이 늘어날 때 구조적으로 유리하다.

단점은 다음과 같다.

- 현재 프로젝트 규모에서는 구조가 다소 커진다.
- service 또는 상위 조합 계층에서 여러 분석기를 직접 묶어줘야 한다.

이 문서에서는 이 방식을 기본안으로 채택하지 않는다. 현재 요구사항이 단순하기 때문에, 우선은 하나의 누적 상태 객체로 시작하는 것이 적절하다.

## 권장 구조 예시

현재 기준으로는 아래 같은 자료구조면 충분하다.

- `Map<String, Long>` apiKeyCounts
- `Map<String, Long>` serviceCounts
- `Map<String, Long>` browserCounts

필요한 보조 계산은 모두 마지막에 수행한다.

- 최다 API Key 선택
- 상위 3개 서비스 정렬
- 브라우저 비율 계산

즉 핵심은 "읽는 동안 카운트만 누적하고, 정렬과 비율 계산은 마지막에 한 번만 수행"하는 것이다.

## 동작 보존 규칙

스트리밍 전환 후에도 기존 분석 결과의 의미와 정렬 규칙은 유지해야 한다.

### 최다 호출 API Key

- 빈 문자열과 공백 문자열, `null` API Key는 집계에서 제외한다
- 호출 수가 가장 큰 API Key를 선택한다
- 호출 수가 같으면 사전순으로 더 작은 API Key를 선택한다
- 결과가 없으면 `null`을 반환한다

### 상위 3개 서비스

- 빈 문자열과 공백 문자열, `null` 서비스 ID는 집계에서 제외한다
- 호출 수 내림차순으로 정렬한다
- 호출 수가 같으면 서비스 ID 오름차순으로 정렬한다
- 최대 3개까지만 반환한다

### 브라우저 비율

- 빈 문자열과 공백 문자열, `null` 브라우저 값은 집계에서 제외한다
- 비율 계산의 분모는 "집계 대상이 된 브라우저 건수 합계"다
- 결과 맵은 브라우저 이름 오름차순 순서를 유지한다
- 집계 대상 브라우저가 없으면 빈 맵을 반환한다

즉 스트리밍 전환은 계산 방식 변경이지, 외부에 보이는 결과 규약 변경이 아니다.

## 예상 처리 흐름

목표 처리 흐름은 아래와 같다.

1. controller가 파일을 받는다.
2. service가 repository에서 `Stream<LogRecord>`를 받는다.
3. service가 try-with-resources 안에서 스트림을 소비한다.
4. 각 `LogRecord`를 `AnalysisAccumulator`에 전달한다.
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
Stream<LogRecord> -> accumulator.accept(record) -> accumulator.toAnalysisResult()
```

또한 운영 로그에 필요한 처리 건수는 `AnalysisAccumulator`가 관리하는 것이 적절하다.

- 처리 건수는 요청 단위 누적 상태이므로 accumulator 책임에 가깝다
- service는 스트림 orchestration에 집중하고, 로그 출력 시 accumulator가 제공하는 처리 건수를 사용한다
- 예를 들어 `processedRecordCount` 같은 필드를 두고 `accept()` 호출 시 함께 증가시킬 수 있다

### 2. LogAnalysisResultAssembler

현재는 `Collection<LogRecord>`를 입력으로 받는다. 이 구조는 스트리밍 분석과 맞지 않으므로 역할을 조정해야 한다.

권장 방향은 아래 둘 중 하나다.

- `LogAnalysisResultAssembler`를 제거하고 `AnalysisAccumulator`가 직접 `AnalysisResult`를 반환
- `AnalysisAccumulator`는 상태만 유지하고, `LogAnalysisResultAssembler`는 accumulator를 받아 최종 결과만 조합

현재 프로젝트에서는 첫 번째가 더 단순하다. 즉 `AnalysisAccumulator`가 `toAnalysisResult()`까지 가지는 구조가 실용적이다.

### 3. 기존 계산기들

현재 계산기들은 모두 `Collection<LogRecord>`를 입력으로 받는다. 누적 상태 객체 방식으로 전환하면 아래처럼 처리하는 것이 적절하다.

- 초기 전환 단계에서는 기존 계산기들을 제거하거나 역할을 축소한다.
- 카운트 누적 로직은 `AnalysisAccumulator` 내부로 이동한다.
- 필요해지면 이후에 accumulator 내부 구현을 다시 항목별 클래스로 분리한다.

즉 지금 단계에서는 "분석기 분리"보다 "스트리밍 누적 구조 완성"을 우선한다.

## 예외 및 리소스 처리 규칙

스트리밍 구조에서는 예외 발생 시점과 리소스 해제 시점이 batch 구조보다 더 중요하다.

- service는 반드시 `try-with-resources`로 `Stream<LogRecord>`를 열고 닫는다
- 스트림 순회 중 예외가 발생해도 stream close가 호출되어 repository가 reader를 정리할 수 있어야 한다
- repository는 스트림 생성과 종료 시점의 리소스 정리만 책임지고, 분석 상태나 복구 로직은 알지 않는다
- `AnalysisAccumulator`는 입력 레코드를 받는 동안 필요한 상태만 누적하고, 예외 복구 책임은 가지지 않는다
- 파싱 예외가 발생하면 현재처럼 즉시 실패시키되, 에러 메시지에는 가능한 한 파일명과 line number 문맥을 유지한다
- service는 실패 로그를 남길 때 fileName과 함께 가능하면 accumulator가 보유한 처리 건수도 같이 남겨, 어느 지점까지 처리됐는지 확인할 수 있어야 한다

## 테스트 전략

스트리밍 전환 후 테스트는 아래처럼 재정리하는 것이 좋다.

### service 테스트

- 전체 리스트를 모으지 않고도 동일 결과가 나오는지 검증
- 스트림 소비 후 최종 결과가 기존과 동일한지 검증
- 스트림 순회 중 예외가 발생해도 close가 보장되는지 검증
- 실패 로그 또는 예외 경로에서 처리 건수 노출이 가능한지 검증

### analysis 테스트

- `AnalysisAccumulator` 단위 테스트
- 레코드 입력 순서가 바뀌어도 동일 결과가 나오는지 검증
- tie-break 정렬 규칙 유지 검증
- 누적 중간 상태와 최종 결과 계산이 의도대로 동작하는지 검증
- 처리 건수 누적이 실제 집계 입력 수와 일치하는지 검증

### repository 테스트

- 현재처럼 파싱 스트림이 정상 생성되는지만 검증
- 분석과 관련된 기대는 repository 테스트에 두지 않는다

## 단계별 전환 계획

### 1단계. 누적 상태 객체 도입

- `AnalysisAccumulator` 도입
- `accept(LogRecord)`와 `toAnalysisResult()` 정의
- apiKey, serviceId, browser 집계 상태를 accumulator에 배치

### 2단계. service 전환

- `LogAnalysisService`에서 `toList()` 제거
- 스트림을 순회하며 accumulator에 전달

### 3단계. 기존 batch 계산기 정리

- `Collection<LogRecord>` 기반 계산기 제거 또는 역할 축소
- 필요 시 기존 계산기 이름을 accumulator 내부 private 메서드 수준으로 흡수

### 4단계. 테스트 보강

- 기존 결과 회귀 테스트 유지
- 큰 입력을 가정한 메모리 친화적 구조 테스트 추가

## 기대 효과

- 입력 파일 크기에 덜 민감한 구조
- 전체 레코드 리스트 보관 제거
- 현재 요구사항에 더 맞는 분석 방식
- 이후 대용량 로그 처리 시 메모리 위험 감소

## 주의사항

누적 상태 객체 방식으로 바꾼다고 해서 메모리 사용이 0이 되는 것은 아니다.

- 카운트 맵 자체는 고유 키 수만큼 메모리를 사용한다
- 서비스 종류, API Key 종류가 매우 많으면 맵 크기도 커질 수 있다
- 다만 현재 구조처럼 "전체 레코드 수"에 비례해서 메모리가 증가하는 것보다는 훨씬 안정적이다

즉 이 전환의 목적은 메모리 사용을 없애는 것이 아니라, 증가 기준을 "전체 로그 수"에서 "집계 대상 고유 키 수"로 바꾸는 것이다.

## 결론

현재 구조는 repository까지만 스트리밍이고, 실제 분석은 batch 처리다. 파일 크기를 예측할 수 없는 환경까지 고려하면 이 구조는 장기적으로 불안정하다.

따라서 다음 리팩토링 방향은 명확하다.

- `Stream<LogRecord>`를 유지한다
- service에서 `toList()`를 제거한다
- analysis를 `AnalysisAccumulator` 중심의 누적 상태 객체 방식으로 전환한다

즉 앞으로의 목표는 "스트림 입력"이 아니라 "누적 상태 객체 기반의 스트림 분석"을 구현하는 것이다.
