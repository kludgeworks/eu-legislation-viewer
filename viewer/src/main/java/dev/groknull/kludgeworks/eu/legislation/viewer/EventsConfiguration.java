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
 * Configures how Spring Modulith runs the events of the modules.
 *
 * <ul>
 *   <li>The listener of a module runs after the publishing transaction commits, on its own thread,
 *       by {@code @Async}.
 *   <li>A listener that failed gets its event again every minute, on every instance. Modulith
 *       resubmits each failed publication once, for whichever instance asks first.
 *   <li>A publication fails when its listener fails, for example when {@code JobRunner} defers a
 *       job. It also fails by the staleness check, {@code spring.modulith.events.staleness}. This
 *       check fails a publication whose instance stopped before it finished.
 * </ul>
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
