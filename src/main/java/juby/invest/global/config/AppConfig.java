package juby.invest.global.config;

import io.pinecone.configs.PineconeConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Value("${kis.mock.base-url}") private String mockInvestUrl;
    @Value("${kis.real.base-url}") private String realInvestUrl;
    @Value("${naver.news.base-url}") private String naverNewsSearchUrl;
    @Value("${naver.news.app-key}") private String naverNewsAppKey;
    @Value("${naver.news.app-secret}") private String naverNewsAppSecret;
    @Value("${pinecone.app-key}") private String pineconeAppKey;

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

    // 네이버 뉴스 검색 API
    @Bean
    public RestClient newsSearchRestClient(){
        return RestClient.builder()
                .baseUrl(naverNewsSearchUrl)
                .defaultHeader("X-Naver-Client-Id", naverNewsAppKey)
                .defaultHeader("X-Naver-Client-Secret", naverNewsAppSecret)
                .build();
    }

    // Pinecone 설정
    @Bean
    public PineconeConfig pineconeConfig(){
        return new PineconeConfig(pineconeAppKey);
    }
}
