package com.minichat.api.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String USAGE_EVENT_QUEUE = "usage_event";
    public static final String AUDIT_EVENT_QUEUE = "audit_event";

    @Bean
    public Queue usageEventQueue() {
        return QueueBuilder.durable(USAGE_EVENT_QUEUE).build();
    }

    @Bean
    public Queue auditEventQueue() {
        return QueueBuilder.durable(AUDIT_EVENT_QUEUE).build();
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
