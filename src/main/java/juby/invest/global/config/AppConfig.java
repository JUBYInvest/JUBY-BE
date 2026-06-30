package juby.invest.global.config;

import io.pinecone.clients.Index;
import io.pinecone.configs.PineconeConfig;
import io.pinecone.configs.PineconeConnection;
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
    public RestClient mockInvestRestClient(){
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

    // Pinecone 연결
    @Bean
    public Index pineconeConfig(){
        PineconeConfig config = new PineconeConfig(pineconeAppKey);
        config.setHost("juby-lovh45p.svc.aped-4627-b74a.pinecone.io");

        PineconeConnection connection = new PineconeConnection(config);

        return new Index(config, connection, "juby");
    }
}
