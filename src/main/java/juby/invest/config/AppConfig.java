package juby.invest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Value("${kis.mock.base-url}") private String investUrl;
    @Value("${kis.real.base-url}") private String realInvestUrl;

    @Bean
    public RestClient investRestClient(){
        return RestClient.builder()
                .baseUrl(investUrl)
                .build();
    }

    @Bean
    public RestClient realInvestRestClient(){
        return RestClient.builder()
                .baseUrl(realInvestUrl)
                .build();
    }
}
