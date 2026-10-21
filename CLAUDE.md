# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

JUBY-BE ("invest") is a Spring Boot 3.4 / Java 21 backend for an investment/stock-analysis service. It aggregates stock market data from the Korea Investment Securities (KIS) Open API, runs technical-analysis backtests (via ta4j), serves news (Naver News API), stores document embeddings in Pinecone, and offers an OpenAI-backed chatbot — all behind OAuth2 social login (Naver/Google/Kakao) with JWT-based stateless auth.

## Commands

Build tool is Gradle (wrapper included). Run all commands from the repo root.

```powershell
# Build (skip tests, matches CI)
./gradlew clean build -x test

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "juby.invest.domain.backtest.service.BacktestServiceTest"

# Run a single test method
./gradlew test --tests "juby.invest.domain.backtest.service.BacktestServiceTest.someMethod"

# Run the app locally (defaults to the `local` Spring profile)
./gradlew bootRun
```

Local infra (MySQL 8.0 on 127.0.0.1:3306) is provided via `docker-compose.yml`. There is no Redis
service — pass the service name explicitly, because a bare `docker compose up -d` also tries to start
the deploy-only `app` service (private image + EC2 `.env`) and fails:

```powershell
docker compose up -d mysql
```

The app requires a `.env` file at the repo root (loaded by the runtime/Docker, see `.github/workflows/dev-deploy.yml` and `Dockerfile`) providing `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `AES_128`, OAuth client id/secrets (`GOOGLE_*`, `KAKAO_*`, `NAVER_*`), `KIS_MOCK_*`/`KIS_REAL_*` app key/secret, `OPEN_API_KEY`, and `PINECONE_APP_KEY` — see `src/main/resources/application.yaml` for the full list of referenced env vars.

CI (`.github/workflows/dev-deploy.yml`) triggers on PRs merged into `dev`: builds with Gradle, builds/pushes a Docker image, and deploys to EC2 over SSH with `SPRING_PROFILES_ACTIVE=dev`.

## Architecture

### Package layout

Code lives under `juby.invest` and is split into `domain/*` (feature verticals) and `global/*` (cross-cutting concerns), plus a one-off `initiate/` package for DB seeding.

Each domain package (`backtest`, `kis/market`, `kis/token`, `member`, `news`, `openai`, `personality_test`, `pinecone`, `stock`) follows the same internal layering:

```
controller/   REST endpoints, returns ApiResponse<T>
service/      business logic
dto/          request/response DTOs
converter/    entity <-> DTO / external-model mapping
entity/       JPA entities (only in domains with persistence)
repository/   Spring Data JPA repositories
enums/
exception/           <Domain>Exception extends ProjectException
exception/code/      <Domain>ErrorCode / <Domain>SuccessCode enums implementing BaseErrorCode/BaseSuccessCode
```

New domains should replicate this structure rather than introducing a different layering style.

`global/*` is split by cross-cutting concern, not by technical type: `apiPayload/` (response envelope + error codes), `config/`, `logging/`, `scheduler/`, `security/`. A new concern gets its own `global/<concern>/` package.

### API response & error handling convention

Every controller returns `ApiResponse<T>` (`global/apiPayload/ApiResponse.java`), a fixed envelope `{isSuccess, code, message, result}` built via `ApiResponse.onSuccess(successCode, result)` / `ApiResponse.onFailure(errorCode, result)`.

- Success/error codes are enums per domain implementing `BaseSuccessCode`/`BaseErrorCode` (see `global/apiPayload/code/GeneralSuccessCode`/`GeneralErrorCode` for the shared/common ones, e.g. `COMMON400_1`).
- Domain exceptions extend `ProjectException` (e.g. `StockException`, `BacktestException`, `TokenException`) and carry a `BaseErrorCode`.
- `global/apiPayload/handler/GeneralExceptionAdvice` (`@RestControllerAdvice`) catches `ProjectException` and maps it to the correct HTTP status/body; unhandled `RuntimeException`s fall back to `COMMON500_1`, except `AccessDeniedException`, which is rethrown so Spring Security's `ExceptionTranslationFilter` handles it.
- Log level follows the error code's HTTP status: `ProjectException` with a 4xx code logs at WARN, a 5xx code at ERROR (external-API failures such as `MARKET502_*`, `PINECONE502_1`), and unhandled exceptions at ERROR. See **Logging** below — ERROR is the alerting trigger, so the 4xx/5xx boundary decides what pages someone.

When adding a new failure case, add an entry to the relevant domain's `*ErrorCode` enum (or `GeneralErrorCode` if truly generic) rather than throwing a raw exception.

### Security & auth

- Stateless JWT auth (`global/security/`): `JwtAuthenticationFilter` runs before `UsernamePasswordAuthenticationFilter`; `JwtUtil` issues/validates tokens (access token TTL 30 min, refresh 14 days — configured under `jwt.*` in `application.yaml`).
- `JwtAuthenticationFilter` is deliberately **not** a `@Component` — `SecurityConfig` instantiates it with `new JwtAuthenticationFilter(jwtUtil, authService)`. Making it a bean would make Spring Boot auto-register it as a plain servlet filter *in addition to* the security chain, so it would also run on requests that bypass the chain. Keep new security filters out of the component scan for the same reason.
- OAuth2 social login for Naver, Google, Kakao (`CustomOAuth2MemberService`, `OAuth2SuccessHandler`, and per-provider `*Response` DTOs implementing `OAuth2Response`).
- `SecurityConfig` (`global/config/SecurityConfig.java`) disables CSRF/formLogin/httpBasic, uses `SessionCreationPolicy.STATELESS`, and permits `/`, `/swagger-ui/**`, `/v3/api-docs/**`, `/error/**`, `/api/**`, `/mypage.html`; everything else requires authentication. `CustomEntryPoint`/`CustomAccessDenied` handle 401/403.
- Method-level security is enabled (`@EnableMethodSecurity`).

### Logging

`global/logging/` holds two classes; there is no `logback-spring.xml` — everything is driven from `application-*.yaml`.

- `MdcLoggingFilter` (`@Order(Ordered.HIGHEST_PRECEDENCE)`) sits **outside** the Spring Security chain so it also captures requests whose exceptions never reach `GeneralExceptionAdvice` (JWT parse failures, 401/403 rendering). It stamps MDC, emits **one completion log line per request**, and clears MDC in `finally`.
- `SchedulerLoggingAspect` tags `@Scheduled` runs with `jobName`/`runId`/`jobResult`/`jobLatencyMs`. Its pointcut is **package-based** (`execution(* juby.invest.global.scheduler..*.*(..))`), not `@annotation(Scheduled)`: `DailyPriceScheduler.getDailyPrice()` carries two `@Scheduled` annotations, which the compiler folds into a single `@Schedules` container that `@annotation(Scheduled)` does not match. New schedulers must live in `global/scheduler` to be picked up.

MDC keys — these are a contract with the log pipeline, so don't rename them casually:

| Key | Source |
|---|---|
| `traceId` | `X-Request-Id` header if present, else generated; echoed back in the response header |
| `clientIp` | `X-Real-IP` (nginx overwrites it, so it is trustworthy), else `"UNKNOWN"` |
| `httpMethod`, `uri` | request (no querystring) |
| `status`, `latencyMs` | set in `finally` |
| `userId` | `JwtAuthenticationFilter` puts it after successful authentication |
| `jobName`, `runId`, `jobResult`, `jobLatencyMs` | `SchedulerLoggingAspect` |

Log level policy (ERROR is the alerting trigger — only use it for "someone must look at this"):

- Request completion line: 5xx → WARN, everything else → INFO. **Never ERROR** — `GeneralExceptionAdvice` already logs ERROR for those, and duplicating it double-alerts.
- An exception escaping the filter chain → ERROR with the stack trace. This is the only place such exceptions get recorded.
- `MdcLoggingFilter.shouldNotFilter` skips `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/webjars/**`, `/favicon.ico`, `/*.html`. Health checks and docs are high-volume and low-value. Because the filter is skipped entirely there, it also calls `MDC.clear()` **on entry** to drop anything a skipped request left on a pooled thread.
- Never log request/response bodies, raw tokens, cookies, or the `Authorization` header.

Tests for both classes live in `src/test/java/juby/invest/global/logging/` and use the `LogCapture` appender, which snapshots the MDC at append time (MDC is cleared immediately after logging, so reading it later shows nothing).

### KIS (Korea Investment Securities) integration

`domain/kis/token` manages OAuth access tokens for the KIS Open API (`KisToken` entity, cached/persisted via `TokenRepository`, mock vs real endpoints configured under `kis.mock.*` / `kis.real.*`). `domain/kis/market` fetches daily price/holiday/trading-volume data from KIS; `DailyPriceScheduler` runs scheduled pulls.

### Backtesting

`domain/backtest` uses the [ta4j](https://ta4j.github.io/ta4j-wiki/) library. `BacktestStrategy` is the strategy interface (`Strategy strategy(BarSeries series)`), implemented by `SmaStrategy`, `EmaStrategy`, `BollingerBandStrategy`, `BreakoutStrategy`, `MacdTrendStrategy`, `RsiReversionStrategy`. `converter/BarSeriesConverter` turns `List<DailyPrice>` (stock domain entity) into a ta4j `BarSeries` (bars keyed to 15:30 KST daily close). `indicator/*Indicator` classes (`Effect`, `Growth`, `Profit`, `Stable`) and `converter/ScoreCalculator` derive backtest scoring; `converter/AnalysisCriterionConverter` maps results to response DTOs.

### AI / vector search

- `domain/openai` wraps Spring AI's OpenAI starter for chat.
- `domain/pinecone` stores/queries embeddings in Pinecone (`pinecone.app-key`); `PineconeScheduler` handles scheduled sync jobs.

### Data seeding

`initiate/root/Initiator` is an `ApplicationReadyEvent` listener (currently commented out) that seeds the DB with top-100-market-cap stock codes/names and historical daily OHLCV data via `StockLoadService` / `DailyPriceLoadService` / `ParticularDailyPriceLoadService`. Re-enable/invoke these deliberately — they insert bulk data and hit the KIS API.

### Config profiles

`application.yaml` is the base config (env-var driven); `application-local.yaml` (JPA `ddl-auto: update`, SQL logging, localhost OAuth redirect URIs) and `application-dev.yaml` layer on top via `spring.profiles.active`. Default active profile is `local`.

Logging differs per profile and that split is intentional:

- `dev` uses Spring Boot 3.4's built-in structured logging (`logging.structured.format.console: ecs`), so every line is one ECS JSON object with MDC entries promoted to top-level fields and stack traces serialized into `error.stack_trace`. No extra dependency, and the log collector needs no multiline handling.
- `local` stays human-readable plain text with `logging.pattern.level: "%5p [%X{traceId:-}]"`. Don't turn JSON on locally.
- Logger names are case-sensitive: it is `org.hibernate.SQL`, not `org.hibernate.sql`.
