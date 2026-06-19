package juby.invest.domain.pinecone.controller;

import io.swagger.v3.oas.annotations.Operation;
import juby.invest.domain.pinecone.dto.PineconeResDto;
import juby.invest.domain.pinecone.exception.code.PineconeSuccessCode;
import juby.invest.domain.pinecone.service.PineconeService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseSuccessCode;
import juby.invest.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.openapitools.db_data.client.ApiException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vectordb")
@RequiredArgsConstructor
public class PineconeController {

    private final PineconeService pineconeService;

    /***
     * 함수 기능: vectorDB에 값을 삽입하는 API
     * @param query 네이버 뉴스 검색 API의 query 파라미터
     * @return 공통 응답 양식
     * @throws ApiException pinecone 예외 처리
     */
    @GetMapping
    public ApiResponse<PineconeResDto.UpsertSuccess> upsertData(
            @RequestParam("query") String query) throws ApiException {
        BaseSuccessCode successCode = PineconeSuccessCode.CREATED;
        return ApiResponse.onSuccess(successCode, pineconeService.upsertData(query));
    }

    /***
     * 함수 기능: vectorDB에 데이터를 조회하는 API
     * @param question 전체 질문 명세
     * @param stockName 종목
     * @return 공통 응답 양식
     * @throws ApiException pinecone 예외 처리
     */
    @GetMapping("/search")
    public ApiResponse<PineconeResDto.SearchSuccess> searchData(
            @RequestParam("question") String question,
            @RequestParam("stockName") String stockName) throws ApiException {
        BaseSuccessCode successCode = PineconeSuccessCode.OK;

        return ApiResponse.onSuccess(successCode, pineconeService.searchData(question, stockName));
    }
}
