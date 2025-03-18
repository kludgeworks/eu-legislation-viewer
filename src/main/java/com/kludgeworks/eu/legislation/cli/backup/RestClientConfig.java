package com.kludgeworks.eu.legislation.cli.backup;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.util.*;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                //workaround for https://github.com/spring-projects/spring-framework/issues/34439
                .messageConverters(new RestTemplate().getMessageConverters())
                .requestInterceptor((request, body, execution) -> {
                    System.out.println("intercept Request URI: " + request.getURI());
                    System.out.println("intercept Request Method: " + request.getMethod());
                    System.out.println("intercept Request Headers: " + request.getHeaders());

                    return execution.execute(request, body);
                }).requestInterceptor((request, body, execution) -> {
                    ClientHttpResponse response = execution.execute(request, body);
                    System.out.println("Response Status: " + response.getStatusCode());
                    System.out.println("Response Headers: " + response.getHeaders());

                    return response;
                }).defaultStatusHandler(HttpStatusCode::is3xxRedirection, (request, response) -> {
                    String redirectUrl = Objects.requireNonNull(response.getHeaders().getLocation()).toString();
                    System.out.println("Following redirect to: " + redirectUrl);
                }).build();
    }

    @Bean
    public CellarService cellarService(RestClient restClient) {
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
        return factory.createClient(CellarService.class);
    }
}