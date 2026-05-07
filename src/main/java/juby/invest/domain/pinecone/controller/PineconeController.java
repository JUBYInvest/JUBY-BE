package juby.invest.domain.pinecone.controller;

import juby.invest.domain.pinecone.dto.PineconeResDto;
import juby.invest.domain.pinecone.exception.code.PineconeSuccessCode;
import juby.invest.domain.pinecone.service.PineconeService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.RequiredArgsConstructor;
import org.openapitools.db_data.client.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/vectordb")
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
    public ApiResponse<PineconeResDto.PineconeSuccess> upsertData(
            @RequestParam("query") String query) throws ApiException {
        BaseSuccessCode successCode = PineconeSuccessCode.OK;
        return ApiResponse.onSuccess(successCode, pineconeService.upsertData(query));
    }
}
