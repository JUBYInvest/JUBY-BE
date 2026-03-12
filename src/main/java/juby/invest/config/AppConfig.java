package juby.invest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Value("${kis.mock.base-url}") private String mockInvestUrl;
    @Value("${kis.real.base-url}") private String realInvestUrl;

    // 모의 Domain
    @Bean
    public RestClient investRestClient(){
        return RestClient.builder()
                .baseUrl(mockInvestUrl)
                .build();
    }

    // 실전 Domain
    @Bean
    public RestClient realInvestRestClient(){
        return RestClient.builder()
                .baseUrl(realInvestUrl)
                .build();
    }
}
