# JUBY 코드 컨벤션

이 문서는 JUBY 백엔드 코드베이스에 실제로 적용되어 있는 컨벤션을 정리한 것입니다. 새 도메인/기능을 추가할 때 아래 규칙을 따라주세요.
(CodeRabbit이 PR 리뷰 시 참고하는 가이드라인이기도 합니다 — `.coderabbit.yaml`의 `knowledge_base.code_guidelines`)

## 1. 패키지 구조

도메인은 `src/main/java/juby/invest/domain/<domain>` 아래에 만들고, 다음 레이어 구조를 그대로 따릅니다.

```
domain/<domain>/
├── controller/     REST 엔드포인트. ApiResponse<T> 반환
├── service/        비즈니스 로직
├── dto/            요청/응답 DTO
├── converter/      엔티티 <-> DTO, 외부 모델 매핑
├── entity/         JPA 엔티티 (영속 대상이 있는 도메인만)
├── repository/     Spring Data JPA 리포지토리
├── enums/
└── exception/
    ├── <Domain>Exception.java         ProjectException 상속
    └── code/
        ├── <Domain>ErrorCode.java     BaseErrorCode 구현 enum
        └── <Domain>SuccessCode.java   BaseSuccessCode 구현 enum
```

새 도메인을 추가할 때 이 구조를 임의로 바꾸지 마세요. 공통으로 쓰는 코드는 `global/` 아래에 둡니다 (`global/apiPayload`, `global/security`, `global/config`).

## 2. API 응답 규칙

모든 컨트롤러는 `ApiResponse<T>` (`{isSuccess, code, message, result}`)를 반환합니다. 성공/실패 응답을 직접 조립하지 말고 아래 정적 팩토리 메서드를 사용하세요.

```java
return ApiResponse.onSuccess(MemberSuccessCode.OK, memberService.getMemberInfo(id));
```

- 원시 문자열, `ResponseEntity<String>`, 예외 메시지를 그대로 반환하지 않습니다.
- 실패는 예외를 던지는 방식으로 처리하고, `GeneralExceptionAdvice`(`@RestControllerAdvice`)가 일괄 변환합니다. 컨트롤러/서비스에서 직접 `try-catch` 후 에러 응답을 만들지 않습니다.

## 3. 예외 / 에러 코드 컨벤션

- 도메인 예외는 `ProjectException`을 상속한 전용 클래스를 만듭니다. (`StockException`, `BacktestException`, `MemberException`, `TokenException` 등)

```java
public class StockException extends ProjectException {
    public StockException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
```

- 에러/성공 코드는 `BaseErrorCode` / `BaseSuccessCode`를 구현한 enum으로 정의합니다. 필드는 `HttpStatus status`, `String code`, `String message` 세 가지만 가집니다.
- `code` 네이밍 규칙: **`<도메인 대문자><HTTP 상태코드>_<일련번호>`**

  | 예시 | 의미 |
  | --- | --- |
  | `COMMON400_1`, `COMMON404_1` | 도메인에 상관없이 공통으로 쓰는 에러 (`GeneralErrorCode`) |
  | `BACKTEST404_1`, `BACKTEST404_2` | backtest 도메인의 404 에러, 순서대로 1번, 2번 |
  | `BACKTEST200_1` | backtest 도메인의 200 성공 코드 |
  | `MEMBER404_1`, `MEMBER404_2` | member 도메인의 404 에러 |

- 같은 상황을 표현할 수 있는 `GeneralErrorCode`(`BAD_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `INTERNAL_SERVER_ERROR`)가 있다면 도메인 코드로 중복 정의하지 않습니다. 도메인 고유의 실패 사유일 때만 `<Domain>ErrorCode`를 추가하세요.
- `message`는 한국어 존댓말(`~습니다`)로 작성하고, 가능하면 사용자가 무엇을 해야 하는지까지 안내합니다.
  예: `"해당 종목이 존재하지 않습니다. 정확한 종목코드를 입력해주세요."`

## 4. 컨트롤러 컨벤션

```java
@RestController
@RequestMapping("/api/backtest")
@Tag(name = "백테스트 API", description = "백테스트 전략을 시행하고, 결과 지표를 반환한다.")
@RequiredArgsConstructor
@Slf4j
public class BacktestController {

    private final BacktestService backtestService;

    @Operation(summary = "백테스트 실행", description = "...")
    @GetMapping("/run")
    public ApiResponse<BacktestResDto.QuantScoringResponse> convert(
            @Valid @ModelAttribute BacktestReqDto.ReqInfo dto) {
        return ApiResponse.onSuccess(BacktestSuccessCode.OK, backtestService.runStrategy(dto));
    }
}
```

- 클래스에는 `@Tag(name, description)`, 각 엔드포인트에는 `@Operation(summary, description)`을 붙여 Swagger 문서가 항상 최신 상태를 유지하도록 합니다.
- 필드 주입 대신 생성자 주입(`@RequiredArgsConstructor` + `private final`)만 사용합니다.
- 요청 DTO에는 `@Valid`를 반드시 붙입니다. 쿼리 파라미터가 여러 개면 `@ModelAttribute`, JSON 바디면 `@RequestBody`를 사용합니다.
- 로그인 사용자 정보는 `@AuthenticationPrincipal CustomOAuth2User`로 받습니다.
- 인증이 필요 없는 엔드포인트만 `SecurityConfig`의 `allowUris`에 추가합니다.

## 5. 서비스 컨벤션

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestService {

    private final StockRepository stockRepository;
    ...

    @Transactional
    public BacktestResDto.QuantScoringResponse runStrategy(BacktestReqDto.ReqInfo dto) {
        Stock stock = stockRepository.findById(dto.stockCode())
                .orElseThrow(() -> new BacktestException(BacktestErrorCode.STOCKCODE_NOT_FOUND));
        ...
    }
}
```

- 조회 실패는 `Optional.orElseThrow(() -> new <Domain>Exception(<Domain>ErrorCode.XXX))` 형태로 처리합니다.
- 트랜잭션이 필요한 메서드에는 `@Transactional`을 붙입니다. 조회 전용 메서드는 `readOnly = true`를 명시하는 것을 권장합니다.
- 외부 API(KIS, OpenAI, Naver 뉴스, Pinecone) 호출 로직은 컨트롤러가 아닌 서비스(또는 전용 client 클래스)에 위치시키고, 실패/타임아웃 처리를 반드시 고려합니다.

## 6. DTO 컨벤션

요청/응답 DTO는 **동사/명사구 이름의 감싸는 클래스 + 내부 `record`** 형태로 작성합니다. `Req`/`Res` 접미사로 요청/응답을 구분합니다.

```java
public class BacktestReqDto {
    public record ReqInfo(
            @NotBlank(message = "종목 코드는 필수입니다.")
            String stockCode,

            @NotNull(message = "1 ~ 5에 맞는 성향을 입력해주세요.")
            int investType,

            LocalDate startDate,
            LocalDate endDate
    ) {}
}

public class ChangeMemberInfo {
    public record ChangeInfoReq(
            @Size(min = 2, max = 4, message = "이름은 2~4자여야 합니다.")
            String name
    ) {}

    @Builder
    public record ChangeInfoRes(
            LocalDateTime modifiedDate
    ) {}
}
```

- Bean Validation 어노테이션(`@NotBlank`, `@NotNull`, `@Size`, `@PastOrPresent` 등)에는 항상 한국어 `message`를 지정합니다.
- 응답 DTO(`record`)에 `@Builder`를 붙이면 서비스 계층에서 필드명을 명시하며 생성할 수 있어 가독성이 좋습니다.
- 요청 클래스명은 `<Domain>ReqDto`/`<Domain>ResDto` 또는 기능을 그대로 드러내는 이름(`ChangeMemberInfo`, `ChangeInvestType`)을 사용합니다. 어떤 스타일이든 컨트롤러/서비스 시그니처만 보고 무슨 요청/응답인지 알 수 있어야 합니다.

## 7. 커밋 / 브랜치 컨벤션

- 브랜치: `feat/기능명` (자세한 Git Flow는 [README.md](../README.md#깃-플로우) 참고)
- 커밋 메시지: `<type>: <한글 설명>` 형태를 사용합니다. (`feat:`, `refactor:`, `chore:`, `fix:` 등)
  예: `feat: 나의 투자성향 테스트 API 구현`, `refactor: 생일연도 정보를 추가로 받아 DB에 저장`

## 8. 하지 말아야 할 것

- 컨트롤러/서비스에서 `RuntimeException`을 직접 던지지 않습니다. 반드시 도메인 `<Domain>Exception` + `<Domain>ErrorCode`(또는 `GeneralErrorCode`)를 사용합니다.
- `application*.yaml`에 API 키/시크릿을 하드코딩하지 않습니다. 항상 `${ENV_VAR}` 형태로 참조합니다.
- KIS/OpenAI/Pinecone 등 외부 API 키를 로그로 출력하지 않습니다.
