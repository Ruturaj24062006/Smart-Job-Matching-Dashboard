package com.careermatch.backend.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueueConfig {

    public static final String EXCHANGE = "careermatch.exchange";
    public static final String DLX_EXCHANGE = "careermatch.dlx";

    // ── Resume processing ────────────────────────────────────────────────────
    public static final String RESUME_UPLOADED_QUEUE      = "resume.uploaded.queue";
    public static final String RESUME_UPLOADED_DLQ        = "resume.uploaded.dlq";
    public static final String RESUME_UPLOADED_ROUTING_KEY = "resume.uploaded";

    // ── Job matching (triggered after embedding + extraction) ────────────────
    public static final String JOB_MATCHING_QUEUE         = "job.matching.queue";
    public static final String JOB_MATCHING_DLQ           = "job.matching.dlq";
    public static final String JOB_MATCHING_ROUTING_KEY   = "job.matching";

    // ── Recruiter job posted ─────────────────────────────────────────────────
    public static final String JOB_POSTED_QUEUE           = "job.posted.queue";
    public static final String JOB_POSTED_DLQ             = "job.posted.dlq";
    public static final String JOB_POSTED_ROUTING_KEY     = "job.posted";

    // ── Exchanges ────────────────────────────────────────────────────────────
    @Bean
    public TopicExchange careermatchExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public TopicExchange careermatchDlx() {
        return new TopicExchange(DLX_EXCHANGE);
    }

    // ── Resume uploaded ──────────────────────────────────────────────────────
    @Bean
    public Queue resumeUploadedQueue() {
        return QueueBuilder.durable(RESUME_UPLOADED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RESUME_UPLOADED_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue resumeUploadedDlq() {
        return QueueBuilder.durable(RESUME_UPLOADED_DLQ).build();
    }

    @Bean
    public Binding bindingResumeUploaded(Queue resumeUploadedQueue, TopicExchange careermatchExchange) {
        return BindingBuilder.bind(resumeUploadedQueue).to(careermatchExchange).with(RESUME_UPLOADED_ROUTING_KEY);
    }

    @Bean
    public Binding bindingResumeUploadedDlq(Queue resumeUploadedDlq, TopicExchange careermatchDlx) {
        return BindingBuilder.bind(resumeUploadedDlq).to(careermatchDlx).with(RESUME_UPLOADED_ROUTING_KEY);
    }

    // ── Job matching ─────────────────────────────────────────────────────────
    @Bean
    public Queue jobMatchingQueue() {
        return QueueBuilder.durable(JOB_MATCHING_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", JOB_MATCHING_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue jobMatchingDlq() {
        return QueueBuilder.durable(JOB_MATCHING_DLQ).build();
    }

    @Bean
    public Binding bindingJobMatching(Queue jobMatchingQueue, TopicExchange careermatchExchange) {
        return BindingBuilder.bind(jobMatchingQueue).to(careermatchExchange).with(JOB_MATCHING_ROUTING_KEY);
    }

    @Bean
    public Binding bindingJobMatchingDlq(Queue jobMatchingDlq, TopicExchange careermatchDlx) {
        return BindingBuilder.bind(jobMatchingDlq).to(careermatchDlx).with(JOB_MATCHING_ROUTING_KEY);
    }

    // ── Job posted (recruiter) ───────────────────────────────────────────────
    @Bean
    public Queue jobPostedQueue() {
        return QueueBuilder.durable(JOB_POSTED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", JOB_POSTED_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue jobPostedDlq() {
        return QueueBuilder.durable(JOB_POSTED_DLQ).build();
    }

    @Bean
    public Binding bindingJobPosted(Queue jobPostedQueue, TopicExchange careermatchExchange) {
        return BindingBuilder.bind(jobPostedQueue).to(careermatchExchange).with(JOB_POSTED_ROUTING_KEY);
    }

    @Bean
    public Binding bindingJobPostedDlq(Queue jobPostedDlq, TopicExchange careermatchDlx) {
        return BindingBuilder.bind(jobPostedDlq).to(careermatchDlx).with(JOB_POSTED_ROUTING_KEY);
    }

    // ── Shared message converter ─────────────────────────────────────────────
    @Bean
    public org.springframework.amqp.support.converter.MessageConverter jsonMessageConverter() {
        return new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter();
    }

    @Bean
    public org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory() {
        String rabbitmqUrl = System.getenv("RABBITMQ_URL");
        org.springframework.amqp.rabbit.connection.CachingConnectionFactory factory = 
            new org.springframework.amqp.rabbit.connection.CachingConnectionFactory();
        if (rabbitmqUrl != null && !rabbitmqUrl.isEmpty()) {
            try {
                factory.setUri(rabbitmqUrl);
            } catch (Exception e) {
                // Fallback to local
                factory.setHost("localhost");
                factory.setPort(5672);
            }
        } else {
            factory.setHost("localhost");
            factory.setPort(5672);
        }
        return factory;
    }
}


