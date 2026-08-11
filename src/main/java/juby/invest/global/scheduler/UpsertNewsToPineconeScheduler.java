package juby.invest.global.scheduler;

import juby.invest.domain.pinecone.dto.PineconeResDto;
import juby.invest.domain.pinecone.service.PineconeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@Profile("dev") // EC2에서만 실행
public class UpsertNewsToPineconeScheduler {

    private final PineconeService pineconeService;

    /***
     * 스케줄러 동작 시각: 매 03:00
     * 수행 동작: 1. DB에 존재하는 종목을 순회하여 각 종목당 20개의 뉴스를 호출한다.
     *          2.  가져온 각 종목의 20개의 뉴스를 Pinecone DB에 UPSERT 한다.
     */
    @Scheduled(cron = "0 0 0/3 * * *", zone = "Asia/Seoul")
    public void upsertAllStockNews() {

        log.info("[스케줄러-3] 뉴스 벡터DB 적재 스케줄러 동작 시작.");

        PineconeResDto.BulkUpsertSuccess result = pineconeService.upsertAllStockNews();

        log.info("[스케줄러-3] 뉴스 벡터DB 적재 스케줄러 동작 완료. 전체 {}건 중 성공 {}건, 실패 종목: {}",
                result.totalCount(), result.successCount(), result.failedStocks());
    }
}