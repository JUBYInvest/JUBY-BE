package juby.invest.domain.member.service;

import juby.invest.domain.member.converter.LikeStockConverter;
import juby.invest.domain.member.dto.LikeStockDto;
import juby.invest.domain.member.dto.LikeStockListDto;
import juby.invest.domain.member.entity.LikeStock;
import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.exception.LikeStockException;
import juby.invest.domain.member.exception.MemberException;
import juby.invest.domain.member.exception.code.likeStock.LikeStockErrorCode;
import juby.invest.domain.member.exception.code.member.MemberErrorCode;
import juby.invest.domain.member.repository.LikeStockRepository;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.domain.stock.entity.DailyPrice;
import juby.invest.domain.stock.entity.Stock;
import juby.invest.domain.stock.exception.StockException;
import juby.invest.domain.stock.exception.code.StockErrorCode;
import juby.invest.domain.stock.repository.DailyPriceRepository;
import juby.invest.domain.stock.repository.StockRepository;
import juby.invest.global.security.entity.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LikeStockService {

    private final MemberRepository memberRepository;
    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final LikeStockRepository likeStockRepository;

    /***
     * 함수 기능: 회원의 관심 종목을 등록한다.
     * @param user 인증된 회원
     * @param req 등록할 종목코드
     * @return LikeStockRes (종목코드, 종목명, 관심 여부)
     */
    @Transactional
    public LikeStockDto.LikeStockRes addLikeStock(CustomOAuth2User user, LikeStockDto.LikeStockReq req) {

        // 회원 존재 여부 확인
        Member member = memberRepository.findById(user.getId())
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        // 종목 존재 여부 확인
        Stock stock = stockRepository.findById(req.stockCode())
                .orElseThrow(() -> new StockException(StockErrorCode.STOCK_NOT_FOUND));

        // 이미 등록된 관심 종목이라면 409 응답
        try {
            likeStockRepository.save(LikeStock.builder()
                    .stock(stock)
                    .member(member)
                    .build());
        } catch (DataIntegrityViolationException e){
            log.warn("[관심 종목 중복 등록] memberId={}, stockCode={}", member.getId(), stock.getStockCode());
            throw new LikeStockException(LikeStockErrorCode.ALREADY_LIKED);
        }

        return LikeStockDto.LikeStockRes.of(stock.getStockCode(), stock.getStockName(), true);
    }


    /***
     * 함수 기능: 회원의 관심 종목을 해제한다.
     * @param user 인증된 회원
     * @param stockCode 해제할 종목코드
     * @return LikeStockRes (종목코드, 종목명, 관심 여부)
     */
    @Transactional
    public LikeStockDto.LikeStockRes deleteLikeStock(CustomOAuth2User user, String stockCode) {

        // 삭제 대상을 조회한다.
        LikeStock likeStock = likeStockRepository.findByMemberIdAndStockCodeWithStock(user.getId(), stockCode)
                .orElseThrow(() -> new LikeStockException(LikeStockErrorCode.LIKE_STOCK_NOT_FOUND));

        likeStockRepository.delete(likeStock);

        return LikeStockDto.LikeStockRes.of(stockCode, likeStock.getStock().getStockName(), false);
    }

    /***
     * 함수 기능: 회원의 관심 종목 목록을 조회한다.
     *          시세는 daily_price에 적재된 최신 거래일 기준이며,
     *          등락률은 직전 거래일 종가와 비교해 계산한다.
     * @param user 인증된 회원
     * @return LikeStockListRes (기준일, 총 개수, 관심 종목 목록)
     */
    public LikeStockListDto.LikeStockListRes getLikeStockList(CustomOAuth2User user) {

        // 회원의 관심 종목과 종목 정보를 fetch를 통해 한 번에 조회한다. (등록 최신순)
        List<LikeStock> likeStocks = likeStockRepository.findAllByMemberIdWithStock(user.getId());

        // 회원의 관심 종목이 없다면 early return을 통해 DB 조회 횟수를 줄인다.
        if (likeStocks.isEmpty()){
            return LikeStockListDto.LikeStockListRes.of(null, 0, List.of());
        }

        List<String> stockCodes = likeStocks.stream()
                .map(ls -> ls.getStock().getStockCode())
                .toList();

        // 최신 거래일과 직전 거래일을 구한다.
        LocalDate baseDate = dailyPriceRepository.findMaxDate();
        if (baseDate == null){
            throw new StockException(StockErrorCode.DAILYPRICE_NOT_FOUND);
        }
        LocalDate prevDate = dailyPriceRepository.findMaxDateBefore(baseDate);

        // 최신 거래일과 직전 거래일의 DailyPrice 정보를 Map에 저장한다.
        Map<String, DailyPrice> basePrices = toPriceMap(baseDate, stockCodes);
        Map<String, DailyPrice> prevPrices = toPriceMap(prevDate, stockCodes);

        // 관심 종목 1건을 응답 항목으로 변환한다.
        List<LikeStockListDto.LikeStockList> likeStockLists = likeStocks.stream()
                .map(ls -> LikeStockConverter.convertToLikeStockList(ls, basePrices, prevPrices))
                .toList();

        return LikeStockListDto.LikeStockListRes.of(baseDate, likeStockLists.size(), likeStockLists);
    }

    // 각 종목의 해당 일자의 일봉을 종목코드 -> DailyPrice Map으로 변환한다.
    private Map<String, DailyPrice> toPriceMap(LocalDate date, List<String> stockCodes) {

        if (date == null){
            return Map.of();
        }

        return dailyPriceRepository.findAllByDateAndStockCodesWithStock(date, stockCodes).stream()
                .collect(Collectors.toMap(
                        dp -> dp.getStock().getStockCode(),
                        dp -> dp,
                        (existing, duplicate) -> existing));
    }
}
