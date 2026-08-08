package juby.invest.domain.kis.websocket.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TrType {
    REGISTER("1"),
    UNREGISTER("2");

    private final String code;
}
