# DevTrace --- Codex 작업 지침

## 프로젝트 개요

-   목적: 개발자가 오류 메시지를 입력하면 접수하고, 향후 AI 분석 결과와
    이력을 제공하는 웹 서비스.
-   프로젝트 경로: `D:\workspace\vscode\java\DevTrace\`
-   Frontend: React + Vite
-   Backend: Java 21 + Spring Boot 3.5.6 + Maven Wrapper
-   Database: PostgreSQL 18
-   현재 단계: 오류 메시지 접수 API와 화면 구현 완료. PostgreSQL 설치 및
    `devtrace` 데이터베이스와 `devtrace_user` 계정 생성·조회 확인 완료.
-   아직 구현하지 않은 것: Spring Boot의 PostgreSQL 연결, 접수 데이터
    저장, 분석 이력 조회, AI API 연결, 인증.

## 작업 원칙 --- 토큰과 변경 범위 절약

1.  작업 전에 필요한 파일만 읽는다. 전체 프로젝트를 반복해서 출력하거나
    불필요하게 요약하지 않는다.
2.  먼저 관련 파일과 현재 코드를 확인하고, 이미 구현된 기능을 재작성하지
    않는다.
3.  요구사항 범위 안에서 가장 작은 변경을 한다. 무관한 리팩터링, 새
    라이브러리, 새 패턴 도입은 금지한다.
4.  파일을 수정하기 전에 짧게 변경 계획을 세우고, 완료 후 실제 변경
    사항만 요약한다.
5.  불확실한 설정이나 기존 구현을 추측하지 않는다. 파일에서 확인하거나
    사용자에게 질문한다.
6.  비밀번호, API 키, 토큰을 소스 코드나 Git에 넣지 않는다. 비밀 정보는
    환경변수로 읽는다.
7.  명령 실행 결과를 실제로 확인한 경우에만 테스트 성공이라고 보고한다.
    실행하지 않은 검증은 미실행이라고 명시한다.
8.  기존 `GET /api/hello`와 프론트엔드 Vite `/api` proxy를 유지한다.
9.  프론트엔드에서 백엔드를 호출할 때 `http://localhost:8080`을
    하드코딩하지 말고 기존처럼 `/api/...` 상대 경로를 사용한다.
10. 사용자에게 코드 학습이 중요하다. 완료 보고에는 핵심 변경 이유와
    데이터 흐름을 짧게 설명한다.

## 현재 구현된 기능

-   `GET /api/hello`: 백엔드 연결 확인 API.
-   `POST /api/analyses`: `language`, `errorMessage`를 JSON으로 받아
    임시 접수 응답 반환.
-   허용 언어: `Java`, `JavaScript`, `TypeScript`, `Python`, `SQL`,
    `Other`.
-   `errorMessage`: 필수, 공백만 입력 불가, 최대 10,000자.
-   요청 DTO, 응답 DTO, HTTP 400 오류 응답 처리, 컨트롤러 테스트가
    존재한다.
-   프론트엔드에는 언어 선택, 오류 메시지 입력, 제출, 로딩/오류/응답
    표시가 있다.
-   현재 `POST /api/analyses`는 `PENDING` 응답만 반환하며 아직 DB
    저장이나 AI 분석은 하지 않는다.

## 다음 작업: PostgreSQL 연결 및 오류 접수 데이터 저장

이 작업만 수행한다. AI API, 로그인, 이력 조회 화면 등은 아직 구현하지
않는다.

### 1. 먼저 확인할 파일

필요한 파일만 확인한다. - `backend/pom.xml` -
`backend/src/main/resources/application.properties` -
`backend/src/main/java/com/devtrace/AnalysisController.java` -
`backend/src/main/java/com/devtrace/AnalysisRequest.java` -
`backend/src/main/java/com/devtrace/AnalysisResponse.java` -
`backend/src/test/java/com/devtrace/AnalysisControllerTest.java` - 기타
기존 패키지 구조에서 실제로 관련된 파일

### 2. 데이터베이스 연결

-   PostgreSQL JDBC 드라이버가 없을 때만 `backend/pom.xml`에 추가한다.
-   접속 정보는 환경변수에서 읽는다. 기본값 예시:
    -   `DB_URL=jdbc:postgresql://localhost:5432/devtrace`
    -   `DB_USERNAME=devtrace_user`
    -   `DB_PASSWORD`는 사용자가 설정한 실제 비밀번호이며 문서나 코드에
        기록하지 않는다.
-   `application.properties`에서 `${DB_URL:...}` 같은 Spring 환경변수
    문법을 사용할 수 있다.
-   실제 비밀번호를 요구하거나 출력하지 않는다. 사용자에게 환경변수 설정
    방법을 안내한다.
-   연결 확인을 위해 불필요한 별도 라이브러리나 Docker를 도입하지
    않는다.

### 3. 접수 데이터 저장

-   현재 프로젝트의 의존성과 코드 스타일을 확인한 뒤 가장 단순하고
    일관된 방식을 선택한다.
-   Spring Data JPA를 사용하기로 하면 필요한 의존성과
    Entity/Repository를 최소한으로 추가하고, 스키마 자동 생성은 개발
    환경에서만 명시적으로 설정한다.
-   이미 프로젝트에 다른 DB 접근 방식이 있다면 그것을 우선 재사용한다.
    근거 없이 JPA와 JDBC를 함께 도입하지 않는다.
-   테이블에는 최소한 다음 정보를 저장한다:
    -   생성된 ID
    -   언어
    -   오류 메시지
    -   접수 상태 (`PENDING`)
    -   생성 시각
-   DB 저장 성공 후에만 성공 응답을 반환한다.
-   저장 실패 시 성공 응답을 반환하지 않는다. 기존 오류 응답 구조를
    재사용하고, 내부 DB 오류의 상세 정보나 비밀 정보를 클라이언트에
    노출하지 않는다.
-   기존 응답 JSON 필드와 프론트엔드 동작은 가능한 한 유지한다. 필요한
    변경이 있으면 이유를 보고한다.
-   사용자가 요청하지 않은 기능(분석 이력 목록, 삭제, 수정, 로그인, AI
    API)은 추가하지 않는다.

### 4. 테스트

-   기존 테스트를 보존한다.
-   `GET /api/hello`가 계속 동작하는지 확인한다.
-   `POST /api/analyses` 정상 요청에서 DB 저장이 호출되는지 테스트한다.
-   입력 검증 테스트를 유지한다.
-   테스트 환경에서 실제 PostgreSQL이 필요한 방식으로 바뀌었다면, 실제
    DB 연결 없이도 실행 가능한 단위 테스트와 통합 테스트의 차이를
    설명한다. 테스트를 위해 사용자의 실제 DB나 데이터를 삭제하지 않는다.
-   가능한 경우 다음을 실행한다:
    -   `cd backend`
    -   `.\mvnw.cmd test`
    -   `cd ..\frontend`
    -   `npm.cmd run build`
-   실행하지 못한 테스트는 성공했다고 쓰지 않는다.

## 완료 보고 형식

간결하게 다음 항목만 보고한다. 1. 변경/추가한 파일과 각각의 역할 2. DB
설정에 필요한 환경변수 이름 (비밀번호 값은 절대 출력하지 않음) 3. 접수
API에서 DB 저장까지의 데이터 흐름 4. 실행한 테스트와 실제 결과 5.
사용자가 직접 해야 할 다음 단계 6. 주요 클래스 3\~5개에 대한 쉬운 설명

## 실행 명령

Backend:

``` powershell
cd D:\workspace\vscode\java\DevTrace\backend
.\mvnw.cmd spring-boot:run
```

Frontend:

``` powershell
cd D:\workspace\vscode\java\DevTrace\frontend
npm.cmd run dev
```

기존 설정이나 실제 파일 내용이 이 문서와 다르면, 현재 저장소의 사실을
우선하고 차이를 보고한다.
