package com.aykhedma.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtFilter;

        // 🔐 Password Encoder
        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        // 🔐 Security Rules
        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http)
                        throws Exception {

                http
                                .csrf(csrf -> csrf.disable())

                                // We use JWT → no session
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                // Authorization rules
                                .authorizeHttpRequests(auth -> auth

                                                .requestMatchers("/auth/login",
                                                                "/auth/register",
                                                                "/auth/send-otp",
                                                                "/auth/verify-otp",
                                                                "/auth/refresh")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/services/categories",
                                                                "/api/services/types")
                                                .permitAll()
                                                .requestMatchers(
                                                                RegexRequestMatcher.regexMatcher(HttpMethod.GET,
                                                                                "^/api/services/categories/[0-9]+$"),
                                                                RegexRequestMatcher.regexMatcher(HttpMethod.GET,
                                                                                "^/api/services/types/[0-9]+$"))
                                                .permitAll()
                                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                                .requestMatchers("/provider/**").hasRole("PROVIDER")
                                                .requestMatchers("/consumer/**").hasRole("CONSUMER")
                                                .anyRequest().authenticated())

                                // Add JWT filter before default authentication filter
                                .addFilterBefore(jwtFilter,
                                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}