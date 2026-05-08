package juby.invest.domain.pinecone.exception.code;

import juby.invest.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum PineconeSuccessCode implements BaseSuccessCode {

    OK(HttpStatus.OK, "PINECONE200_1", "PineconeDB에서 데이터를 성공적으로 조회하였습니다."),
    CREATED(HttpStatus.CREATED, "PINECONE201_1", "PineconeDB에 성공적으로 값이 삽입되었습니다.");


    private final HttpStatus status;
    private final String code;
    private final String message;
}
