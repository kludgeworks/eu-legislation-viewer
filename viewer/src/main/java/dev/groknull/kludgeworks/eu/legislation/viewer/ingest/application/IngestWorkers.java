// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.IngestJobs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * This instance's workers (viewer.ingest.workers), each a virtual thread: it claims a job and runs
 * it (JobRunner), then the next; with none to claim, it waits to be woken (a job scheduled here) or
 * until it next looks (viewer.ingest.poll, for jobs scheduled on other instances, or given up by a
 * worker that stopped). The workers beat for the jobs they hold (viewer.ingest.heartbeat).
 *
 * <p>Started once the application is, stopped before it is: a job running then is left to be
 * claimed again when its lease runs out.
 */
@Component
class IngestWorkers implements SmartLifecycle {
  private static final Logger LOG = LoggerFactory.getLogger(IngestWorkers.class);

  /** The jobs' owner: this instance, unique among the instances sharing the queue. */
  private final String owner = "viewer-" + UUID.randomUUID();

  private final Semaphore wakeups = new Semaphore(0);
  private final List<Thread> threads = new ArrayList<>();
  private final IngestJobs jobs;
  private final JobRunner runner;
  private final IngestProperties properties;
  private volatile boolean running;

  IngestWorkers(IngestJobs jobs, JobRunner runner, IngestProperties properties) {
    this.jobs = jobs;
    this.runner = runner;
    this.properties = properties;
  }

  /** A job was queued: one idle worker looks now rather than when it next would. */
  void wake() {
    wakeups.release();
  }

  @Scheduled(
      fixedDelayString = "${viewer.ingest.heartbeat}",
      initialDelayString = "${viewer.ingest.heartbeat}")
  void heartbeat() {
    var held = jobs.heartbeat(owner);
    if (held > 0) {
      LOG.debug("Heartbeat for {} job(s)", held);
    }
  }

  @Override
  public synchronized void start() {
    running = true;
    for (var i = 0; i < properties.workers(); i++) {
      threads.add(Thread.ofVirtual().name("ingest-" + i).start(this::work));
    }
    LOG.info(
        "Started {} ingest worker(s) as {}, looking for jobs every {}",
        properties.workers(),
        owner,
        properties.poll());
  }

  @Override
  public synchronized void stop() {
    running = false;
    threads.forEach(Thread::interrupt);
    for (var thread : threads) {
      try {
        thread.join(properties.poll());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    threads.clear();
    LOG.info("Stopped the ingest workers");
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private void work() {
    while (running) {
      try {
        var job = jobs.claim(owner, properties.lease());
        if (job.isPresent()) {
          runner.run(job.get());
        } else {
          var woken = wakeups.tryAcquire(properties.poll().toMillis(), TimeUnit.MILLISECONDS);
          LOG.trace("Looking for a job ({})", woken ? "woken" : "polling");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (RuntimeException e) {
        // the queue itself failed (the store is down, the job lost): look again in a while
        LOG.warn("Ingest worker failed: {}", e.toString());
        try {
          Thread.sleep(properties.poll());
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }
  }
}
