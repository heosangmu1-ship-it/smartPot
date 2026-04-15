package com.garden.smartgarden.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * React SPA 를 Spring Boot 가 서빙할 때 필요한 설정.
 *
 * 동작:
 *  - /              -> index.html
 *  - /api/**        -> REST 컨트롤러 (SensorController 등)
 *  - /assets/**     -> Vite 가 빌드한 정적 JS/CSS (자동 서빙)
 *  - /기타           -> index.html 로 forward (SPA 라우팅 대비)
 */
@Configuration
public class SpaForwardingConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
