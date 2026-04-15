package com.garden.smartgarden.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 공개 URL 에 걸 간단한 BASIC 인증.
 *
 * - 기본은 비활성 (개발/로컬 테스트 편하게) → SECURITY_ENABLED=true 로 켬
 * - 사용자/비밀번호는 환경변수 SECURITY_USER / SECURITY_PASSWORD
 * - /actuator/health 는 언제든 공개 (헬스체크 용)
 */
@Configuration
public class SecurityConfig {

    @Value("${security.enabled:false}")
    private boolean securityEnabled;

    @Value("${security.user:admin}")
    private String username;

    @Value("${security.password:changeme}")
    private String password;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if (!securityEnabled) {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(a -> a.anyRequest().permitAll());
            return http.build();
        }

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> {});

        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        // NoOpPasswordEncoder 대신 {noop} prefix 로 평문 저장 명시
        UserDetails user = User.withUsername(username)
                .password("{noop}" + password)
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }
}
