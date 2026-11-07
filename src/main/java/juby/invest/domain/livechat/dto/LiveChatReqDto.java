package juby.invest.domain.livechat.dto;

public class LiveChatReqDto {

    /***
     * STOMP SEND 본문. 웹소켓 메시지에는 @Valid가 적용되지 않으므로 길이/공백 검증은 LiveChatService에서 한다.
     */
    public record SendReq(
            String content
    ){}
}
