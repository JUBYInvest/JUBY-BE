package juby.invest;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

public class Ta4jInstallTest {
    @Test
    void test(){
        BaseBarSeries series = new BaseBarSeriesBuilder().withName("mySeries").build();
        Assertions.assertThat(series.getName()).isEqualTo("mySeries");
    }
}
