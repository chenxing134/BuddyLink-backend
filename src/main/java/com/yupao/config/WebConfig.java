package com.yupao.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                //当**Credentials为true时，**Origin不能为星号，需为具体的ip地址【如果接口不带cookie,ip无需设成具体ip】
                .allowedOrigins("http://localhost:8080", "http://121.37.250.205:8080", "http://127.0.0.1:8080", "http://121.37.250.205:80","http://localhost:5173")
                .allowedOriginPatterns("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
