// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The ingest properties, and the workers JobRunner runs the requested jobs on:
 * viewer.ingest.workers virtual threads, the requests beyond them waiting in its queue, in memory
 * (in the event registry too, so a request this instance didn't get to is handed out again:
 * ExpressionRequested).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IngestProperties.class)
class IngestApplicationConfiguration {
  /** The workers' executor, by its bean name (JobRunner's @Async). */
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
