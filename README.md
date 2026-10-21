<div align="center">

# 📈 JUBY

### 주린이를 위한 비서, JUBY

주식 초보(주린이)를 위한 AI 투자 비서 백엔드입니다.
투자 성향 분석, 시세, 기술적 백테스트, 뉴스, AI 챗봇까지 — 투자를 시작하는 데 필요한 정보를 한 곳에서 제공합니다.

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Gradle](https://img.shields.io/badge/Gradle-8.x-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)](https://docs.docker.com/compose/)
![Status](https://img.shields.io/badge/status-in%20development-lightgrey)

</div>

---

## 📌 목차

- [소개](#-소개)
- [주요 기능](#-주요-기능)
- [기술 스택](#-기술-스택)
- [아키텍처](#-아키텍처)
- [API 개요](#-api-개요)
- [시작하기](#-시작하기)
- [문서](#-문서)
- [깃 플로우](#-깃-플로우)

---

## 🧭 소개

**JUBY**는 처음 투자를 시작하는 사람들이 겪는 "뭘 봐야 할지 모르겠다"는 문제를 해결하기 위한 서비스입니다.
사용자의 투자 성향을 진단하고, 관심 종목의 시세·뉴스·기술적 백테스트 결과를 제공하며, AI 챗봇을 통해 투자 관련 질문에 답합니다.

## ✨ 주요 기능

| 기능 | 설명 |
| --- | --- |
| 🔐 **소셜 로그인** | Naver / Google / Kakao OAuth2 로그인 + JWT 기반 무상태(stateless) 인증. AT 30분 / RT 14일, RT는 쿠키로 전달 |
| 🧠 **투자 성향 테스트** | 설문 기반 투자 성향 진단 및 결과 저장, 성향 조회/변경 |
| 📊 **백테스트** | [ta4j](https://ta4j.github.io/ta4j-wiki/) 기반 SMA / EMA / RSI / MACD / Bollinger Band / Breakout 전략 백테스트 및 스코어링. 프리셋 결과는 스케줄러가 미리 계산해 둠 |
| 💹 **시세 조회** | 한국투자증권(KIS) Open API 연동 — 현재가, 일별 시세, 거래량 순위, 휴장일 조회 |
| 📇 **종목** | 종목 목록·검색·상세, 주도주 조회, 관심 종목 등록/해제 |
| 📰 **뉴스** | 네이버 뉴스 검색 API 연동 종목 뉴스 제공 |
| 🤖 **AI 챗봇** | Spring AI + OpenAI(`gpt-4o-mini`) 기반 투자 Q&A, 대화 세션/이력 관리 |
| 🧬 **벡터 검색** | Pinecone 기반 뉴스 임베딩 저장/검색 (RAG). 스케줄러가 매일 03시 전 종목 뉴스를 적재 |

## 🛠 기술 스택

**Language / Framework**
`Java 21` · `Spring Boot 3.4.1` · `Spring Security` · `Spring Data JPA` · `Spring AI (OpenAI)` · `Thymeleaf`

**Database / Infra**
`MySQL 8.0` · `Docker / Docker Compose` · `AWS EC2` · `nginx`

**External API**
한국투자증권(KIS) Open API · 네이버 뉴스 검색 API · Naver/Google/Kakao OAuth2 · OpenAI API · Pinecone

**Library**
`ta4j` (기술적 분석/백테스트) · `jjwt` (JWT) · `springdoc-openapi` (Swagger) · `okhttp` (Pinecone 호출 타임아웃)

**로깅 / 모니터링**
MDC 기반 요청 추적(`traceId`) · 구조화 로그(ECS JSON, `dev` 프로필) · CloudWatch Logs 적재 · ERROR 로그 슬랙 알림 Lambda

**CI/CD**
GitHub Actions → Docker Hub → EC2 배포 (`dev` 브랜치로 PR이 머지될 때 동작)

## 🏗 아키텍처

도메인 단위 계층형 구조입니다. 각 도메인은 동일한 패키지 규칙(`controller / service / dto / converter / entity / repository / enums / exception`)을 따릅니다.

```
juby.invest
├── domain
│   ├── auth                 # 토큰 재발급 / 로그아웃
│   ├── member               # 회원, 관심 종목
│   ├── personality_test     # 투자 성향 테스트
│   ├── backtest             # ta4j 기반 백테스트 전략/지표
│   ├── kis
│   │   ├── token            # KIS Open API 토큰 발급/캐싱
│   │   └── market           # 시세/거래량/휴장일 조회
│   ├── stock                # 종목/일별 시세 데이터
│   ├── news                 # 네이버 뉴스 연동
│   ├── openai               # OpenAI 질의
│   ├── chat                 # 챗봇 대화 세션/이력
│   └── pinecone             # 벡터 임베딩 저장/검색
├── global
│   ├── apiPayload           # 공통 API 응답/에러 처리
│   ├── security             # JWT, OAuth2 인증/인가
│   ├── logging              # MDC 요청 로깅, 스케줄러 로깅 애스펙트
│   ├── scheduler            # 일봉 수집, 뉴스 적재, 토큰 정리 등 배치
│   └── config               # Security, Swagger 등 설정
└── initiate                 # 초기 종목/시세 데이터 시딩
```

- 모든 API는 `ApiResponse<T>` 형식(`isSuccess`, `code`, `message`, `result`)의 공통 응답 포맷을 사용합니다.
- 예외는 도메인별 `<Domain>Exception` + `<Domain>ErrorCode`로 던지고, `GeneralExceptionAdvice`가 HTTP 상태/본문으로 변환합니다.
- 모든 요청 로그에는 `traceId`가 붙어 한 요청의 로그를 한 번에 조회할 수 있습니다. 자세한 규약은 [CLAUDE.md](CLAUDE.md)의 Logging 절을 참고하세요.

## 📖 API 개요

| Method | Endpoint | 설명 |
| --- | --- | --- |
| GET | `/oauth2/authorization/{naver\|google\|kakao}` | 소셜 로그인 시작 (Spring Security 기본 경로) |
| POST | `/api/auth/reissue`, `/api/auth/logout` | 쿠키의 RT로 토큰 재발급, 로그아웃 |
| GET/PATCH/DELETE | `/api/members/me`, `/api/members/me/personality` | 내 정보 조회/수정/탈퇴, 투자 성향 조회/변경 |
| GET/POST/DELETE | `/api/members/me/like-stocks` | 관심 종목 조회/등록/해제 |
| GET/POST | `/api/personality-tests` | 투자 성향 테스트 문항 조회, 응답 제출 |
| GET | `/api/stocks`, `/api/stocks/search`, `/api/stocks/{stockCode}` | 종목 목록/검색/상세 |
| GET | `/api/stocks/leading-stocks`, `/api/stocks/{stockCode}/news` | 주도주 조회, 종목 뉴스 |
| GET | `/api/backtest/preset`, `/api/backtest/preset/options` | 백테스트 프리셋 결과·선택 가능 옵션 조회 |
| GET | `/api/market/{stockCode}/`[`price`\|`daily-stock`\|`daily_itemchartprice`] | 현재가 / 일별 시세 / 기간별 시세 조회 |
| GET | `/api/market/volume-rank`, `/api/market/holiday` | 거래량 순위, 휴장일 조회 |
| GET | `/api/token/mock`, `/api/token/real` | KIS Open API 접근 토큰 발급 |
| GET | `/api/news` | 종목 관련 뉴스 검색 |
| POST | `/api/open-ai/ask` | AI 챗봇 질의 |
| GET/POST/PATCH/DELETE | `/api/chat-sessions`, `/api/chat-sessions/{id}` | 대화 세션 생성/목록/상세/제목 수정/삭제 |
| GET/POST | `/api/vectordb`, `/api/vectordb/search`, `/api/vectordb/all` | 뉴스 단건/전체 종목 적재, 관련 뉴스 3건 검색 |

> `POST /api/initiate`는 상위 100종목과 과거 일봉을 대량으로 적재하는 시딩 전용 엔드포인트입니다.
> KIS API를 반복 호출하므로 의도적으로만 호출하세요.

전체 스펙은 서버 실행 후 Swagger UI(`http://localhost:8080/swagger-ui/index.html`)에서 확인할 수 있습니다.

## 🚀 시작하기

### 1. 요구 사항

- JDK 21
- Docker / Docker Compose

### 2. 환경 변수 (`.env`) 작성

저장소 루트에 `.env`를 만듭니다. `.gitignore` 대상이므로 커밋되지 않습니다.

```dotenv
# DB — DB_URL의 호스트는 로컬에서 127.0.0.1, 배포 환경에서 mysql(컨테이너명)
DB_URL=jdbc:mysql://127.0.0.1:3306/juby?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=
DB_PASSWORD=
MYSQL_ROOT_PASSWORD=
MYSQL_DATABASE=juby

# 보안
JWT_SECRET=          # JWT 서명 키
AES_128=             # KIS 토큰 암호화 키

# 소셜 로그인
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
KAKAO_CLIENT_ID=
KAKAO_CLIENT_SECRET=
NAVER_CLIENT_ID=     # 소셜 로그인과 네이버 뉴스 검색 API가 같은 값을 사용합니다
NAVER_CLIENT_SECRET=

# 외부 API
KIS_MOCK_APP_KEY=
KIS_MOCK_APP_SECRET=
KIS_REAL_APP_KEY=
KIS_REAL_APP_SECRET=
OPEN_API_KEY=        # OpenAI API 키
PINECONE_APP_KEY=
```

### 3. 로컬 인프라 실행 (MySQL)

```bash
docker compose up -d mysql
```

> 서비스 이름 없이 `docker compose up -d`를 실행하면 배포 전용인 `app` 서비스까지 올리려다
> 실패합니다. `app`은 CI가 푸시한 이미지와 EC2의 `.env`를 전제로 하므로 로컬 대상이 아닙니다.

### 4. 애플리케이션 실행

```bash
./gradlew bootRun
```

기본 활성 프로필은 `local`이며 `http://localhost:8080` 에서 실행됩니다. 로그는 사람이 읽는 평문입니다.

> **주의**: `docker compose`는 `.env`를 자동으로 읽지만 `bootRun`은 읽지 않습니다.
> 위 변수들이 셸(또는 IDE 실행 구성)의 환경 변수로 들어가 있어야 기동됩니다.
> 예: `export $(grep -v '^#' .env | xargs) && ./gradlew bootRun`

프런트엔드는 `http://localhost:5173`을 전제로 CORS와 OAuth2 리다이렉트가 설정되어 있습니다.

### 5. 테스트

```bash
./gradlew test

# 단일 클래스 / 단일 메서드
./gradlew test --tests "juby.invest.global.logging.MdcLoggingFilterTest"
```

## 📚 문서

| 문서 | 내용 |
| --- | --- |
| [docs/code-convention.md](docs/code-convention.md) | 패키지 구조, API 응답·예외 코드, 컨트롤러/서비스/DTO 컨벤션 |
| [docs/deployment.md](docs/deployment.md) | EC2 단일 인스턴스 배포, MySQL 운영, 백업, 보안 그룹, 로그 수집 |
| [CLAUDE.md](CLAUDE.md) | 아키텍처 요약과 로깅 규약 (MDC 키, 로그 레벨 정책) |
| [lambda/](lambda/) | ERROR 로그를 슬랙으로 통보하는 Lambda (앱과 분리된 독립 Gradle 빌드) |

---

## 🔀 깃 플로우

작업 브랜치는 `dev`에서 따고, 작업이 끝나면 `dev`로 PR을 올립니다.
`dev`에 PR이 머지되면 GitHub Actions가 이미지를 빌드해 EC2에 배포합니다.

### 브랜치 이름

`<type>/<작업명>/#<이슈번호>` 형식을 사용합니다. `type`은 `feature` / `bugfix` / `refactor` /
`deploy` / `chore` 중 하나입니다.

```
feature/like-stock/#87      기능 추가
bugfix/kakao-login/#1       버그 수정
deploy/slack-pipeline/#106  배포/인프라
refactor/99                 작업명 없이 이슈번호만 붙인 과거 형태도 남아 있습니다
```

커밋 메시지는 `<type>: <한글 설명>` 형식입니다. 자세한 규칙은 [코드 컨벤션](docs/code-convention.md#7-커밋--브랜치-컨벤션)을 참고하세요.

### 작업 시작

```bash
git clone https://github.com/JUBYInvest/JUBY-BE.git
cd JUBY-BE

git checkout dev
git pull origin dev
git checkout -b feature/작업명/#이슈번호
```

### 작업 후 푸시 → PR

```bash
git push origin feature/작업명/#이슈번호
```

푸시한 뒤 GitHub에서 `dev`를 base로 PR을 생성합니다.

### 다른 작업이 `dev`에 머지됐을 때

```bash
git checkout dev
git pull origin dev

git checkout feature/작업명/#이슈번호
git merge dev
```

커밋하지 않은 작업이 있으면 `git stash`로 치워 두고 위 과정을 거친 뒤 `git stash pop`으로 되돌립니다.
