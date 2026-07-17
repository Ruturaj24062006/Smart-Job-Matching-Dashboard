package com.careermatch.backend.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueueConfig {

    public static final String EXCHANGE = "careermatch.exchange";
    
    public static final String RESUME_UPLOADED_QUEUE = "resume.uploaded.queue";
    public static final String RESUME_UPLOADED_ROUTING_KEY = "resume.uploaded";

    public static final String JOB_POSTED_QUEUE = "job.posted.queue";
    public static final String JOB_POSTED_ROUTING_KEY = "job.posted";

    @Bean
    public TopicExchange careermatchExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue resumeUploadedQueue() {
        return QueueBuilder.durable(RESUME_UPLOADED_QUEUE).build();
    }

    @Bean
    public Binding bindingResumeUploaded(Queue resumeUploadedQueue, TopicExchange careermatchExchange) {
        return BindingBuilder.bind(resumeUploadedQueue).to(careermatchExchange).with(RESUME_UPLOADED_ROUTING_KEY);
    }

    @Bean
    public Queue jobPostedQueue() {
        return QueueBuilder.durable(JOB_POSTED_QUEUE).build();
    }

    @Bean
    public Binding bindingJobPosted(Queue jobPostedQueue, TopicExchange careermatchExchange) {
        return BindingBuilder.bind(jobPostedQueue).to(careermatchExchange).with(JOB_POSTED_ROUTING_KEY);
    }

    @Bean
    public org.springframework.amqp.support.converter.MessageConverter jsonMessageConverter() {
        return new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter();
    }
}

