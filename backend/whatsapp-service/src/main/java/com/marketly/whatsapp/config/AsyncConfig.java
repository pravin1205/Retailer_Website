package com.marketly.whatsapp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configures the async thread pool used by {@code @Async} methods in this service.
 *
 * The primary consumer is {@link com.marketly.whatsapp.handler.MessageRouter#route},
 * which is annotated {@code @Async} so that the webhook controller can return HTTP 200
 * to Meta immediately while message processing (NLP, STT, API calls) happens in the
 * background.
 *
 * Thread pool sizing rationale:
 *  - Core pool: 4   — handles steady-state concurrent conversations.
 *  - Max pool:  20  — handles burst traffic (flash sale, mass broadcast).
 *  - Queue:     100 — buffers messages during a traffic spike before scaling up.
 *
 * Each message processing task involves:
 *  - 1–3 blocking network calls (Meta API, OpenAI/Groq, order-service)
 *  - Typical duration: 500ms–3s per message
 *
 * With 20 threads and 2s average processing time:
 *  - Throughput: ~10 messages/second sustained
 *  - Burst capacity: 100 messages queued + 20 processing simultaneously
 *
 * This is more than sufficient for the expected early-stage load.
 * Increase max pool size when P99 queue depth consistently exceeds 50.
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    /**
     * Named executor used by @Async methods in this service.
     * Naming it "whatsappTaskExecutor" allows future use of
     * {@code @Async("whatsappTaskExecutor")} for explicit binding if needed.
     */
    @Bean(name = "whatsappTaskExecutor")
    public Executor whatsappTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("wa-msg-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // Log a warning if the queue fills up — signals need for more threads
        executor.setRejectedExecutionHandler((runnable, pool) ->
            log.warn("AsyncConfig: whatsapp task queue full — task rejected. " +
                     "Active={} Queue={} Pool={}",
                     pool.getActiveCount(), pool.getQueue().size(), pool.getPoolSize()));

        executor.initialize();
        log.info("AsyncConfig: whatsappTaskExecutor initialised — core={} max={} queue={}",
                 executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        return executor;
    }
}
