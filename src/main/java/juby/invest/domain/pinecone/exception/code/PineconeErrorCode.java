package juby.invest.domain.pinecone.exception.code;

import juby.invest.global.apiPayload.code.BaseErrorCode;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PineconeErrorCode implements BaseErrorCode {

    BAD_REQUEST(HttpStatus.BAD_REQUEST, "PINECONE404_1", "잘못된 요청입니다."),
    PINECONE_SEARCH_FAILED(HttpStatus.BAD_GATEWAY, "PINECONE502_1" ,"Pinecone 검색에 실패하였습니다." );

    private final HttpStatus status;
    private final String code;
    private final String message;

}
