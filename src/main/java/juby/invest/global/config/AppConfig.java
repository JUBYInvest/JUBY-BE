package juby.invest.global.config;

import io.pinecone.clients.Index;
import io.pinecone.configs.PineconeConfig;
import io.pinecone.configs.PineconeConnection;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.concurrent.TimeUnit;

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

        // Pinecone 클라이언트는 서버가 96개의 청크를 upsert할 때까지의 시간을 설정해두어, 연결을 유지해야한다.
        // 기본 10초는 짧기에 60초로 증가
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        PineconeConfig config = new PineconeConfig(pineconeAppKey);
        config.setHost("juby-lovh45p.svc.aped-4627-b74a.pinecone.io");

        // Index 생성자가 이 시점의 config에서 OkHttpClient를 꺼내가므로 반드시 new Index(...) 전에 설정해야 한다.
        config.setCustomOkHttpClient(okHttpClient);

        PineconeConnection connection = new PineconeConnection(config);

        return new Index(config, connection, "juby");
    }
}
