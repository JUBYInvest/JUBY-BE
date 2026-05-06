package juby.invest.domain.news.controller;

import juby.invest.domain.news.dto.NewsResDto;
import juby.invest.domain.news.exception.code.NewsSuccessCode;
import juby.invest.domain.news.service.NewsService;
import juby.invest.global.apiPayload.ApiResponse;
import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/v1/news")
public class NewsController {

    private final NewsService newsService;

    @GetMapping
    public ApiResponse<NewsResDto.NewsResponse> searchNews(
            @RequestParam("query") String query
            ){
        BaseSuccessCode successCode = NewsSuccessCode.OK;
        return ApiResponse.onSuccess(successCode, newsService.callNewsApi(query));
    }
}
