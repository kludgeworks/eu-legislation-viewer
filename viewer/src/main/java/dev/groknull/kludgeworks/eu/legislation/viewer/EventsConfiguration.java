// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The modules' events, as Modulith runs them: a module's listener after the publishing transaction
 * commits, on its own thread (@Async); one that failed handed its event again, every minute, on
 * every instance (Modulith resubmits each failed publication once, whichever asks first). A
 * publication is failed by its listener failing (a job deferred: JobRunner), or by the staleness
 * check (spring.modulith.events.staleness), which fails one its instance stopped before finishing.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
class EventsConfiguration {
  private static final Logger LOG = LoggerFactory.getLogger(EventsConfiguration.class);

  /** How long a failed publication waits before it is handed out again. */
  private static final Duration RESUBMITTED_AFTER = Duration.ofMinutes(1);

  private final FailedEventPublications failed;

  EventsConfiguration(FailedEventPublications failed) {
    this.failed = failed;
  }

  @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
  void resubmit() {
    LOG.debug("Handing out the failed event publications again");
    failed.resubmit(ResubmissionOptions.defaults().withMinAge(RESUBMITTED_AFTER));
  }
}
