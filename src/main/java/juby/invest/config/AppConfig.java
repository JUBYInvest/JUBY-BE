package juby.invest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Value("${kis.base-url}") private String investUrl;

    @Bean
    public RestClient investRestClient(){
        return RestClient.builder()
                .baseUrl(investUrl)
                .build();
    }

}
