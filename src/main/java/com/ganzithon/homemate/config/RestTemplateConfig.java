package com.ganzithon.homemate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() throws Exception {
        // SSL 인증서 검증을 우회하는 TrustManager 생성
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
        };

        // SSL 컨텍스트 생성 및 설정
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

        // 모든 호스트명을 신뢰하는 HostnameVerifier
        HostnameVerifier allHostsValid = (hostname, session) -> true;

        // HttpsURLConnection에 SSL 설정 적용
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

        // RestTemplate 설정
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                if (connection instanceof HttpsURLConnection) {
                    HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
                    httpsConnection.setSSLSocketFactory(sslContext.getSocketFactory());
                    httpsConnection.setHostnameVerifier(allHostsValid);
                }
            }
        };
        
        // 타임아웃 설정: 2분 (120초 = 120,000ms)
        factory.setConnectTimeout(120000); // 연결 타임아웃 2분
        factory.setReadTimeout(120000); // 읽기 타임아웃 2분

        return new RestTemplate(factory);
    }
}
