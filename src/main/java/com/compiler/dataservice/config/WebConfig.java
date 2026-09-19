package com.compiler.dataservice.config;

import com.compiler.dataservice.security.ApiKeyAuthFilter;
import com.compiler.dataservice.security.RateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(AppProperties props) {
        FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ApiKeyAuthFilter(props.getSecurity().getApiKeys()));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(AppProperties props) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(props.getRateLimit()));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(2);
        return registration;
    }
}
