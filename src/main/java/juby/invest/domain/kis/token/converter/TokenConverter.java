package juby.invest.domain.kis.token.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import juby.invest.domain.kis.token.exception.TokenException;
import juby.invest.domain.kis.token.exception.code.TokenErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Converter
@Slf4j
public class TokenConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES";
    private final byte[] secretKey;

    public TokenConverter(@Value("${aes.secret}") String secretKey) {
        this.secretKey = secretKey.getBytes(StandardCharsets.UTF_8);
    }

    /***
     * DB에 데이터를 저장할 때 내용을 어떻게 암호화할 것인가에 대한 메서드
     */
    @Override
    public String convertToDatabaseColumn(String token) {

        if (token == null || token.isBlank()){
            throw new TokenException(TokenErrorCode.TOKEN_IS_NULL);
        }

        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec); // 암호화 모든 활성화

            // 평문 -> 암호화(바이너리)
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));

            // 바이너리 데이터를 DB에 넣을 수 있도록 Base64 문자열로 인코딩하여 반환
            return Base64.getEncoder().encodeToString(encrypted);

        } catch (Exception e) {
            log.info("토큰 암호화 중 에러 발생. 원인: {}", e.getMessage());
            throw new TokenException(TokenErrorCode.TOKEN_ENCRYPT_FAILED);
        }
    }

    /***
     * DB에서 데이터를 조회해올 때 내용을 어떻게 복호화할 것인가에 대한 메서드
     */
    @Override
    public String convertToEntityAttribute(String token) {

        if (token == null || token.isBlank()){
            throw new TokenException(TokenErrorCode.TOKEN_IS_NULL);
        }

        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec); // 복호화 모드 활성화

            // DB의 Base64 문자열 -> 바이너리로 디코딩
            byte[] decoded = Base64.getDecoder().decode(token);

            // 복호화 실행
            byte[] decrypted = cipher.doFinal(decoded);

            // 바이트 배열을 String으로 변환하여 리턴
            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (Exception e){
            log.info("토큰 복호화 중 에러 발생. 원인: {}", e.getMessage());
            throw new TokenException(TokenErrorCode.TOKEN_DECRYPT_FAILED);
        }
    }
}
