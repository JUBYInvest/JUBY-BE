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

The app requires a `.env` file at the repo root providing `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `AES_128`, OAuth client id/secrets (`GOOGLE_*`, `KAKAO_*`, `NAVER_*`), `KIS_MOCK_*`/`KIS_REAL_*` app key/secret, `OPEN_API_KEY`, and `PINECONE_APP_KEY`. Copy `.env.example` and fill it in; `src/main/resources/application.yaml` is the full list of referenced vars.

`application.yaml` imports it itself via `spring.config.import: optional:file:.env[.properties]`, so `bootRun`, the IDE, and `java -jar` all pick it up with no shell exports — but **it is parsed as a `.properties` file, not shell dotenv**. Consequences that bite: `#` only starts a comment at the start of a line (`KEY=v # note` makes the value `v # note`), quotes are kept literally, `\` is an escape and disappears, and the file is read as ISO-8859-1 so non-ASCII values are mangled. Docker Compose reads the same file with its own parser (`env_file`, plus `${...}` interpolation for the `mysql` service), so keep values to the intersection of both: no quotes, no trailing comments, ASCII only. The lookup is relative to the working directory, which is why the EC2 container (CWD `/`, no `.env`) relies on Compose's `env_file` instead — real env vars outrank imported config files. CI writes EC2's `.env` from the `ENV_FILE` secret (see `.github/workflows/dev-deploy.yml`).

CI (`.github/workflows/dev-deploy.yml`) triggers on PRs merged into `dev`: builds with Gradle, builds/pushes a Docker image, and deploys to EC2 over SSH with `SPRING_PROFILES_ACTIVE=dev`.

## Architecture

### Package layout

Code lives under `juby.invest` and is split into `domain/*` (feature verticals) and `global/*` (cross-cutting concerns), plus a one-off `initiate/` package for DB seeding.

Each domain package (`backtest`, `kis/market`, `kis/realtime`, `kis/token`, `marketindex`, `member`, `news`, `openai`, `personality_test`, `pinecone`, `stock`) follows the same internal layering:

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

`global/logging/` holds the MDC filter, the scheduler aspect, and `LogThrottle`/`ThrottledLogger` (per-interval WARN collapsing for tick-rate paths); there is no `logback-spring.xml` — everything is driven from `application-*.yaml`.

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

Tests for these classes live in `src/test/java/juby/invest/global/logging/` and use the `LogCapture` appender, which snapshots the MDC at append time (MDC is cleared immediately after logging, so reading it later shows nothing).

### KIS (Korea Investment Securities) integration

`domain/kis/token` manages OAuth access tokens for the KIS Open API (`KisToken` entity, cached/persisted via `TokenRepository`, mock vs real endpoints configured under `kis.mock.*` / `kis.real.*`). `domain/kis/market` fetches daily price/holiday/trading-volume data from KIS; `DailyPriceScheduler` runs scheduled pulls. `MarketService.isClosedDay` wraps the holiday API — reuse it instead of re-deriving "is the market open" from `getHolidayList`.

#### Realtime quotes (`domain/kis/realtime`)

Intraday prices come from the KIS **websocket**, not from polling. Two TRs share the same pipeline: `H0STCNT0` (국내주식 실시간체결가) and `H0UPCNT0` (국내지수 실시간체결). `RealtimeSessionManager` opens one session per credential in `kis.ws.credentials` and registers that session's assigned `RealtimeSubscription`s (a `(trId, trKey)` pair — one slot of the 41-per-session cap).

Frame handling is split so a second TR costs almost nothing: `RealtimeFrameSplitter` does the TR-agnostic work (`|` split, record count, deriving record length) and returns a `RealtimeFrame`; `RealtimePriceWebSocketHandler` then routes by `trId` to `RealtimeTickParser` → `RealtimePriceStore` or `RealtimeIndexParser` → `RealtimeIndexStore`. Both stores extend `RealtimeSnapshotStore<T>`, which owns the map and the freshness rule. **The two parsers cannot be merged — the field positions differ** (전일대비율 is index 5 for stocks, 9 for indices; 누적거래량 13 vs 5), and index values are decimals so they are parsed as `BigDecimal`.

`StockService.getStockList`/`getStockDetails` and `LikeStockService.getLikeStockList` prefer `RealtimePriceStore` and fall back to `DailyPrice` when it misses. `MarketIndexService` (domain `marketindex`) prefers `RealtimeIndexStore`, then a 1-minute cache of the KIS 현재지수 REST call (`MarketService.getIndexPrice`, `FHPUP02100000`) — there is no index table, so the REST call is the only fallback. It drops an index that fails rather than failing the whole response.

Constraints that drive this design — changing any of them means re-reading the whole flow:

- **41 registrations per session, one session per appkey.** 100 stocks + 2 indices = 102 slots, so 3 credentials are needed (real ×1 + mock ×2). `SubscriptionAllocator` splits stocks deterministically (sorted, even split) so the same session always owns the same stocks across restarts, and throws rather than silently dropping subscriptions past the cap.
- **Indices are pinned to the real-domain session** (`Credential.realDomain`, set in `application.yaml`). A mock session that does not support the index TR would receive nothing *without being rejected*, which is far harder to notice than a hard failure. `RealtimeConnectionManualTest.mockSessionSupportsIndexTr` is the test that would let us lift this restriction.
- **Single instance only.** Two app instances with the same appkey kill each other's sessions, so `RealtimeSessionScheduler` (08:55 connect / 15:40 disconnect) is `@Profile("dev")`. Scaling out requires session-ownership design first; there is no shared store for quotes.
- **`approval_key` is never persisted.** It is only used to establish a session and does not affect an already-open one, so `TokenService.issueApprovalKey` returns it without saving, and reconnects simply issue a new one.
- **Ticks are never written to the DB.** Only the latest tick per stock is kept, with freshness judged by *receive time* (`RealtimeSnapshotStore.STALE_AFTER`). Confirmed daily closes still come from `DailyPriceScheduler`.
- **Frame parsing must not throw.** Record length is derived by dividing field count by the frame's record count (KIS has appended fields before: 46 → 47), and a broken record is dropped alone. Each parser also checks the key format (6-digit stock code, 4-digit index code) because a divisible-but-shifted frame otherwise passes silently. Per-tick failure logs go through `ThrottledLogger` — unthrottled they would emit thousands of lines per second.

Never log an appkey, app secret, or `approval_key`; session logs are identified by the credential's `name` instead.

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
