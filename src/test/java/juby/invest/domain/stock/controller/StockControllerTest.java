package juby.invest.domain.stock.controller;

import juby.invest.domain.stock.dto.StockListDto;
import juby.invest.domain.stock.enums.Order;
import juby.invest.domain.stock.enums.Period;
import juby.invest.domain.stock.enums.StockSortBy;
import juby.invest.domain.stock.service.StockService;
import juby.invest.global.apiPayload.handler.GeneralExceptionAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("종목 목록 조회 파라미터 바인딩")
class StockControllerTest {

    @Mock
    private StockService stockService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 전체 컨텍스트와 시큐리티 없이 컨트롤러 + 예외 어드바이스만 올린다.
        // ArgumentResolver와 GeneralExceptionAdvice는 실제 구현이 그대로 동작한다.
        mockMvc = MockMvcBuilders.standaloneSetup(new StockController(stockService))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .build();
    }

    private StockListDto.StockListRes emptyResult() {
        return StockListDto.StockListRes.of(LocalDate.of(2026, 8, 31), List.of());
    }

    @Nested
    @DisplayName("정상 요청")
    class ValidRequest {

        @DisplayName("4개 컬럼 x 2개 방향 조합을 모두 수용한다")
        @ParameterizedTest(name = "sortBy={0}, order={1}")
        @CsvSource({
                "STOCK_NAME, ASC",    "STOCK_NAME, DESC",
                "CLOSE_PRICE, ASC",   "CLOSE_PRICE, DESC",
                "FLUCTUATE, ASC",     "FLUCTUATE, DESC",
                "TRADING_VALUE, ASC", "TRADING_VALUE, DESC",
        })
        void acceptsEverySortCombination(String sortBy, String order) throws Exception {
            given(stockService.getStockList(any())).willReturn(emptyResult());

            mockMvc.perform(get("/api/stocks")
                            .param("sortBy", sortBy)
                            .param("order", order))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isSuccess").value(true))
                    .andExpect(jsonPath("$.code").value("STOCK200_3"));
        }

        @Test
        @DisplayName("파라미터를 생략해도 200을 반환한다")
        void acceptsNoParameters() throws Exception {
            given(stockService.getStockList(any())).willReturn(emptyResult());

            mockMvc.perform(get("/api/stocks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("STOCK200_3"));
        }

        @DisplayName("빈 값이나 공백은 미지정으로 보고 기본 정렬을 적용한다")
        @ParameterizedTest(name = "sortBy=[{0}]")
        @ValueSource(strings = {"", " "})
        void treatsBlankSortByAsUnspecified(String sortBy) throws Exception {
            given(stockService.getStockList(any())).willReturn(emptyResult());

            mockMvc.perform(get("/api/stocks").param("sortBy", sortBy))
                    .andExpect(status().isOk());

            // Spring은 enum 대상의 공백 문자열을 예외 없이 null로 변환한다.
            // (TypeConverterDelegate: "It's an empty enum identifier: reset the enum value to null.")
            // 따라서 StockListReq의 compact 생성자가 기본값을 채운다.
            ArgumentCaptor<StockListDto.StockListReq> captor =
                    ArgumentCaptor.forClass(StockListDto.StockListReq.class);
            then(stockService).should().getStockList(captor.capture());

            assertThat(captor.getValue().sortBy()).isEqualTo(StockSortBy.STOCK_NAME);
            assertThat(captor.getValue().order()).isEqualTo(Order.ASC);
        }
    }

    @Nested
    @DisplayName("잘못된 파라미터 (MethodArgumentNotValidException)")
    class InvalidRequest {

        @DisplayName("잘못된 sortBy는 400을 반환한다")
        @ParameterizedTest(name = "sortBy={0}")
        @ValueSource(strings = {"INVALID", "stock_name", "StockName", "거래대금", "1"})
        void rejectsInvalidSortBy(String sortBy) throws Exception {

            mockMvc.perform(get("/api/stocks").param("sortBy", sortBy))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.isSuccess").value(false))
                    .andExpect(jsonPath("$.code").value("COMMON400_1"))
                    .andExpect(jsonPath("$.result.sortBy").exists());
        }

        @DisplayName("잘못된 order는 400을 반환한다")
        @ParameterizedTest(name = "order={0}")
        @ValueSource(strings = {"INVALID", "asc", "desc", "0"})
        void rejectsInvalidOrder(String order) throws Exception {

            mockMvc.perform(get("/api/stocks").param("order", order))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.isSuccess").value(false))
                    .andExpect(jsonPath("$.code").value("COMMON400_1"))
                    .andExpect(jsonPath("$.result.order").exists());
        }

        @Test
        @DisplayName("두 파라미터가 모두 잘못되면 두 필드 모두 응답에 담긴다")
        void reportsEveryInvalidField() throws Exception {
            // WebDataBinder는 첫 실패에서 멈추지 않고 모든 실패를 BindingResult에 수집한다.
            mockMvc.perform(get("/api/stocks")
                            .param("sortBy", "INVALID")
                            .param("order", "INVALID"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.result.sortBy").exists())
                    .andExpect(jsonPath("$.result.order").exists());
        }

        @Test
        @DisplayName("바인딩에 실패하면 서비스는 호출되지 않는다")
        void doesNotReachService() throws Exception {

            mockMvc.perform(get("/api/stocks").param("sortBy", "INVALID"))
                    .andExpect(status().isBadRequest());

            then(stockService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("단일 파라미터 (MethodArgumentTypeMismatchException)")
    class SingleParameterBinding {

        @Test
        @DisplayName("잘못된 period는 400을 반환하고 result가 문자열이다")
        void rejectsInvalidPeriod() throws Exception {
            // @RequestParam은 @ModelAttribute와 달리 result가 Map이 아닌 String이다.
            mockMvc.perform(get("/api/stocks/005930").param("period", "INVALID"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.isSuccess").value(false))
                    .andExpect(jsonPath("$.code").value("COMMON400_1"))
                    .andExpect(jsonPath("$.result").isString());

            then(stockService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("잘못된 period는 MethodArgumentTypeMismatchException으로 처리된다")
        void raisesTypeMismatchExceptionForInvalidPeriod() throws Exception {

            MvcResult result = mockMvc.perform(get("/api/stocks/005930").param("period", "INVALID"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // @RequestParam은 RequestParamMethodArgumentResolver가 값을 직접 변환하므로
            // 변환 실패가 BindingResult에 수집되지 않고 그 자리에서 예외로 던져진다.
            assertThat(result.getResolvedException())
                    .isInstanceOf(MethodArgumentTypeMismatchException.class);

            MethodArgumentTypeMismatchException exception =
                    (MethodArgumentTypeMismatchException) result.getResolvedException();

            assertThat(exception.getName()).isEqualTo("period");
            assertThat(exception.getValue()).isEqualTo("INVALID");
            assertThat(exception.getRequiredType()).isEqualTo(Period.class);
        }

        @Test
        @DisplayName("같은 enum 바인딩 실패라도 @ModelAttribute면 MethodArgumentNotValidException이다")
        void raisesNotValidExceptionForModelAttribute() throws Exception {

            MvcResult result = mockMvc.perform(get("/api/stocks").param("sortBy", "INVALID"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // 실패 내용이 아니라 파라미터를 처리한 리졸버가 예외 종류를 결정한다.
            assertThat(result.getResolvedException())
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .isNotInstanceOf(MethodArgumentTypeMismatchException.class);
        }
    }
}