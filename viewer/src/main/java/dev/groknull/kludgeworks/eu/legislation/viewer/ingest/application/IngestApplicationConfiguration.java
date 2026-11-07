// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configures the ingest properties and the workers that {@link JobRunner} runs the requested jobs
 * on.
 *
 * <p>There are {@code viewer.ingest.workers} virtual threads. The requests that do not get a worker
 * wait in the in-memory queue of the executor. The event registry also keeps each {@code
 * ExpressionRequested}. Thus, if this instance does not get to a request, the registry gives it out
 * again.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IngestProperties.class)
class IngestApplicationConfiguration {
  /**
   * The bean name of the executor of the workers. The {@code @Async} of {@link JobRunner} uses it.
   */
  static final String WORKERS = "ingestWorkers";

  @Bean(WORKERS)
  ThreadPoolTaskExecutor ingestWorkers(IngestProperties properties) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(properties.workers());
    executor.setMaxPoolSize(properties.workers());
    executor.setThreadFactory(Thread.ofVirtual().name("ingest-", 0).factory());
    return executor;
  }
}
