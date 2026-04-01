package com.tech.docflow.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    
    public static final String OCR_QUEUE = "ocr.submission.queue";
    public static final String OCR_DLQ = "ocr.submission.dlq";
    public static final String OCR_EXCHANGE = "ocr.exchange";
    public static final String OCR_ROUTING_KEY = "ocr.submit";
    
    @Bean
    public Queue ocrQueue() {
        return QueueBuilder.durable(OCR_QUEUE)
                .withArgument("x-dead-letter-exchange", "ocr.dlx")
                .withArgument("x-dead-letter-routing-key", "ocr.dlq")
                .build();
    }
    
    @Bean
    public Queue ocrDeadLetterQueue() {
        return QueueBuilder.durable(OCR_DLQ).build();
    }
    
    @Bean
    public DirectExchange ocrExchange() {
        return new DirectExchange(OCR_EXCHANGE, true, false);
    }
    
    @Bean
    public DirectExchange ocrDeadLetterExchange() {
        return new DirectExchange("ocr.dlx", true, false);
    }
    
    @Bean
    public Binding ocrBinding(Queue ocrQueue, DirectExchange ocrExchange) {
        return BindingBuilder.bind(ocrQueue)
                .to(ocrExchange)
                .with(OCR_ROUTING_KEY);
    }
    
    @Bean
    public Binding ocrDlqBinding(Queue ocrDeadLetterQueue, DirectExchange ocrDeadLetterExchange) {
        return BindingBuilder.bind(ocrDeadLetterQueue)
                .to(ocrDeadLetterExchange)
                .with("ocr.dlq");
    }
}