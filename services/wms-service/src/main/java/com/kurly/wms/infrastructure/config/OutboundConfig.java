package com.kurly.wms.infrastructure.config;

import com.kurly.wms.infrastructure.scheduler.OutboundFailureProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OutboundFailureProperties.class)
public class OutboundConfig {
}
