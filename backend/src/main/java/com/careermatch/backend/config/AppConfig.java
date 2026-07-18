package com.careermatch.backend.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableCaching
public class AppConfig {

    /**
     * Shared RestTemplate with:
     * - Connection pool (max 20 total / 10 per route) for HTTP keep-alive reuse.
     * - 3s connect timeout so a bad DNS/route fails fast.
     * - 30s read timeout matching Groq's worst-case response time.
     *
     * Groq and the embedding HTTP calls must inject this bean, NOT create their own instances.
     */
    @Bean
    public RestTemplate restTemplate() {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(20);
        cm.setDefaultMaxPerRoute(10);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(3))
                .setResponseTimeout(Timeout.ofSeconds(30))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        return new RestTemplate(factory);
    }

    /**
     * Dedicated thread pool for async resume/AI processing tasks.
     * Uses a separate pool so resume processing never starves the common ForkJoinPool
     * or the Tomcat request-handling threads.
     */
    @Bean(name = "resumeProcessingExecutor")
    public Executor resumeProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("resume-ai-");
        executor.initialize();
        return executor;
    }
}
