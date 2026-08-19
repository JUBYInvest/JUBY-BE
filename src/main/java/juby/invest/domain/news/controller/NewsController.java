package juby.invest.domain.news.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import juby.invest.domain.news.dto.NewsDto;
import juby.invest.domain.news.exception.code.NewsSuccessCode;
import juby.invest.domain.news.service.NewsService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseSuccessCode;
import juby.invest.global.security.entity.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.db_data.client.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/news")
@Tag(name = "네이버 뉴스 호출 API (인증 API)", description = "네이버 뉴스 검색")
public class NewsController {

    private final NewsService newsService;

    @GetMapping
    @Operation(summary = "검색어 기반 네이버 뉴스 검색 API", description = "검색어를 입력하면 20개의 뉴스 데이터가 반환된다.")
    public ApiResponse<NewsDto.NaverNewsRes> searchNews(
            @NotBlank @RequestParam("query") String query,
            @AuthenticationPrincipal CustomOAuth2User customOAuth2User
            ) throws ApiException {
        BaseSuccessCode successCode = NewsSuccessCode.NAVER_NEWS_SEARCH_OK;
        return ApiResponse.onSuccess(successCode, newsService.callNewsApi(query));
    }
}
