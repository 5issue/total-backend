package com.kurly.auth.infrastructure.config;

import com.kurly.auth.infrastructure.security.RefreshTokenCookieProperties;
import com.kurly.auth.infrastructure.security.jwt.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, RefreshTokenCookieProperties.class})
public class JwtConfig {
}
