package juby.invest.domain.pinecone.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import juby.invest.domain.pinecone.dto.PineconeDto;
import juby.invest.domain.pinecone.exception.code.PineconeSuccessCode;
import juby.invest.domain.pinecone.service.PineconeService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.RequiredArgsConstructor;
import org.openapitools.db_data.client.ApiException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vectordb")
@RequiredArgsConstructor
@Tag(name = "Pinecone API", description = "데이터 삽입, 데이터 조회한다.")
public class PineconeController {

    private final PineconeService pineconeService;

    /***
     * 함수 기능: vectorDB에 값을 삽입하는 API
     * @param query 네이버 뉴스 검색 API의 query 파라미터
     * @return 공통 응답 양식
     * @throws ApiException pinecone 예외 처리
     */
    @GetMapping
    @Operation(summary = "Pinecone DB 뉴스 단건 적재 API", description = "네이버 뉴스 API를 통해 받아온 뉴스 데이터를 Pinecone에 삽입한다.")
    public ApiResponse<PineconeDto.UpsertSuccess> upsertData(
            @RequestParam("query") String query) throws ApiException {
        BaseSuccessCode successCode = PineconeSuccessCode.CREATED;
        return ApiResponse.onSuccess(successCode, pineconeService.upsertData(query));
    }

    /***
     * 함수 기능: DB에 저장된 전체 종목(기본 100개)의 뉴스를 순회하며 vectorDB에 일괄 적재하는 API
     * @return 공통 응답 양식
     */
    @PostMapping("/all")
    @Operation(summary = "Pinecone DB 뉴스 일괄 적재 API.", description = "DB에 저장된 전체 종목을 네이버 뉴스 API로 각각 순회하며 적재한다.")
    public ApiResponse<PineconeDto.BulkUpsertSuccess> upsertAllStockNews(){
        BaseSuccessCode successCode = PineconeSuccessCode.CREATED;
        return ApiResponse.onSuccess(successCode, pineconeService.upsertAllStockNews());
    }

    /***
     * 함수 기능: vectorDB에 데이터를 조회하는 API
     * @param question 전체 질문 명세
     * @param stockName 종목
     * @return 공통 응답 양식
     * @throws ApiException pinecone 예외 처리
     */
    @GetMapping("/search")
    @Operation(summary = "Pinecone DB 뉴스 조회 API", description = "Pinecone에 적재된 뉴스 중 가장 관련이 된 3개를 조회한다.")
    public ApiResponse<List<PineconeDto.StockNewsHit>> searchData(
            @RequestParam("question") String question,
            @RequestParam("stockName") String stockName) throws ApiException {
        BaseSuccessCode successCode = PineconeSuccessCode.OK;

        return ApiResponse.onSuccess(successCode, pineconeService.searchData(question, stockName));
    }
}
