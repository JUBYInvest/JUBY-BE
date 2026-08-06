<div style="text-align: center;">

# 📈 JUBY

### 주린이를 위한 비서, JUBY

주식 초보(주린이)를 위한 AI 투자 비서 백엔드입니다.
투자 성향 분석, 실시간 시세, 기술적 백테스트, 뉴스, AI 챗봇까지 — 투자를 시작하는 데 필요한 정보를 한 곳에서 제공합니다.

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Gradle](https://img.shields.io/badge/Gradle-8.x-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
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
- [Git Flow](#-git-flow)

---

## 🧭 소개

**JUBY**는 처음 투자를 시작하는 사람들이 겪는 "뭘 봐야 할지 모르겠다"는 문제를 해결하기 위한 서비스입니다.
사용자의 투자 성향을 진단하고, 관심 종목의 시세·뉴스·기술적 백테스트 결과를 제공하며, AI 챗봇을 통해 투자 관련 질문에 답합니다.

## ✨ 주요 기능

| 기능 | 설명 |
| --- | --- |
| 🔐 **소셜 로그인** | Naver / Google / Kakao OAuth2 로그인 + JWT 기반 무상태(stateless) 인증 |
| 🧠 **투자 성향 테스트** | 설문 기반 투자 성향 진단 및 결과 저장 |
| 📊 **백테스트** | [ta4j](https://ta4j.github.io/ta4j-wiki/) 기반 SMA / EMA / RSI / MACD / Bollinger Band / Breakout 전략 백테스트 및 스코어링 |
| 💹 **시세 조회** | 한국투자증권(KIS) Open API 연동 — 일별 시세, 거래량 순위, 휴장일 조회 |
| 📰 **뉴스** | 네이버 뉴스 검색 API 연동 종목 뉴스 제공 |
| 🤖 **AI 챗봇** | Spring AI + OpenAI 기반 투자 Q&A |
| 🧬 **벡터 검색** | Pinecone 기반 문서 임베딩 저장/검색 (RAG) |

## 🛠 기술 스택

**Language / Framework**
`Java 21` · `Spring Boot 3.4.1` · `Spring Security` · `Spring Data JPA` · `Spring AI (OpenAI)`

**Database / Infra**
`MySQL 8.0` · `Redis 7` · `Docker`

**External API**
한국투자증권(KIS) Open API · 네이버 뉴스 검색 API · Naver/Google/Kakao OAuth2 · OpenAI API · Pinecone

**Library**
`ta4j` (기술적 분석/백테스트) · `jjwt` (JWT) · `springdoc-openapi` (Swagger)

**CI/CD**
GitHub Actions → Docker Hub → EC2 배포

## 🏗 아키텍처

도메인 단위 계층형 구조로 구성되어 있습니다. 각 도메인은 동일한 패키지 규칙(`controller / service / dto / converter / entity / repository / exception`)을 따릅니다.

```
juby.invest
├── domain
│   ├── member              # 회원, 소셜 로그인 연동 정보
│   ├── personality_test    # 투자 성향 테스트
│   ├── backtest             # ta4j 기반 백테스트 전략/지표
│   ├── kis
│   │   ├── token            # KIS Open API 토큰 발급/캐싱
│   │   └── market           # 시세/거래량/휴장일 조회
│   ├── stock                # 종목/일별 시세 데이터
│   ├── news                 # 네이버 뉴스 연동
│   ├── openai                # AI 챗봇
│   └── pinecone              # 벡터 임베딩 저장/검색
├── global
│   ├── apiPayload           # 공통 API 응답/에러 처리
│   ├── security              # JWT, OAuth2 인증/인가
│   └── config                # Security, Swagger 등 설정
└── initiate                  # 초기 종목/시세 데이터 시딩
```

모든 API는 `ApiResponse<T>` 형식(`isSuccess`, `code`, `message`, `result`)의 공통 응답 포맷을 사용합니다.

## 📖 API 개요

| Method | Endpoint | 설명 |
| --- | --- | --- |
| GET/PATCH/DELETE | `/api/members/**` | 내 정보 조회/수정/탈퇴, 투자 성향 조회/변경 |
| GET/POST | `/api/personality-tests` | 투자 성향 테스트 문항 조회, 응답 제출 |
| GET | `/api/backtest/run` | 종목 백테스트 실행 |
| GET | `/api/market/**` | 일별 시세, 거래량 순위, 휴장일 조회 |
| GET | `/api/token/mock`, `/api/token/real` | KIS Open API 접근 토큰 발급 |
| GET | `/api/news` | 종목 관련 뉴스 검색 |
| GET | `/api/open-ai/ask` | AI 챗봇 질의 |
| GET/POST | `/api/vectordb/**` | 벡터 임베딩 저장/검색 |

전체 스펙은 서버 실행 후 Swagger UI(`/swagger-ui/**`)에서 확인할 수 있습니다.

## 🚀 시작하기

### 1. 요구 사항

- JDK 21
- Docker / Docker Compose

### 2. 로컬 인프라 실행 (MySQL, Redis)

```bash
docker compose up -d
```

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

기본 활성 프로필은 `local`이며, `http://localhost:8080` 에서 실행됩니다.

### 4. 테스트

```bash
./gradlew test
```

---

## 🔀 깃 플로우

### Clone && Push Work Flow

**a) Git Repository Clone**
```bash
git clone https://github.com/JUBYInvest/JUBY-BE.git
cd JUBY-BE
```

**b) Create Branch**
```bash
git checkout -b feat/featureName
```
`ex) git checkout -b feat/pastInvest`

**c) After work done, Branch Push (What you did)**
```bash
git push origin feat/featureName
```
`ex) git push origin feat/pastInvest`

### Merge Work Flow

**a) "IF MERGED" PULL dev branch -> local dev branch**
```bash
git checkout dev
git pull origin dev
```
※ If you are working or before committed, store your work and move to dev
```bash
git stash
git checkout dev
git pull origin dev
```

**b) After dev branch pushed into local, go to working branch and merge**
```bash
git checkout feat/pastInvest
git merge dev
```
※ If you have saved code, restore it
```bash
git checkout feat/pastInvest
git merge dev
git stash pop
```
