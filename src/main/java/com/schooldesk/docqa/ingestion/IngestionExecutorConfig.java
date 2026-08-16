package com.schooldesk.docqa.ingestion;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
class IngestionExecutorConfig {

    @Bean("ingestionExecutor")
    ThreadPoolTaskExecutor ingestionExecutor(IngestionProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.workerThreads());
        executor.setMaxPoolSize(properties.workerThreads());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("ingest-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    static final class ContextPropagatingTaskDecorator implements TaskDecorator {

        @Override
        public Runnable decorate(Runnable task) {
            Map<String, String> callerContext = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (callerContext != null) {
                    MDC.setContextMap(callerContext);
                }
                try {
                    task.run();
                }
                finally {
                    MDC.clear();
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    }
                }
            };
        }
    }
}
