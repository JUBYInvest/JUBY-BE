package juby.invest.domain.auth.dto;

public class ReissueDto {

    /***
     * 서비스 계층의 반환값. 토큰 원문만 담는다.
     * RT를 쿠키로 내릴지 바디로 내릴지는 HTTP 표현의 문제라 컨트롤러가 결정한다.
     */
    public record ReissueRes(
            String accessToken,
            String refreshToken
    ){}

    /***
     * API 응답 바디. RT는 쿠키로만 오가야 하므로 AT만 노출한다.
     */
    public record AtInfo(
            String accessToken
    ){}
}