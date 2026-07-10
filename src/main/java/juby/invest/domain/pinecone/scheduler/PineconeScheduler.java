package juby.invest.domain.pinecone.scheduler;

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
public class PineconeScheduler {

    private final PineconeService pineconeService;

    @Scheduled(cron = "0 0 0/3 * * *", zone = "Asia/Seoul")
    public void upsertAllStockNews() {

        log.info("[스케줄러-2] 뉴스 벡터DB 적재 스케줄러 동작 시작.");

        PineconeResDto.BulkUpsertSuccess result = pineconeService.upsertAllStockNews();

        log.info("[스케줄러-2] 뉴스 벡터DB 적재 스케줄러 동작 완료. 전체 {}건 중 성공 {}건, 실패 종목: {}",
                result.totalCount(), result.successCount(), result.failedStocks());
    }
}