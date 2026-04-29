package juby.invest.domain.stock.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@Table(name = "stock")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    @Id
    @Column(name = "stock_code", length = 20)
    private String stockCode;

    @Column(name = "stock_name", nullable = false, length = 50)
    private String stockName;

    @Builder
    public Stock(String stockCode, String stockName){
        this.stockCode = stockCode;
        this.stockName = stockName;
    }
}
