## Camel Migration Plan (Spring Boot)

### Goals
- Remove Spring Batch and restart bookkeeping.
- Keep Spring Boot, reuse existing processors/writer logic.
- Use `direct:start` trigger with `seda:` for in-app decoupling (no JMS).

### Dependencies (pom.xml)
- Remove: `spring-boot-starter-batch-jdbc`, `spring-batch-integration`, batch test starters, skip policy classes.
- Add Camel BOM (matching Boot 4.x), `camel-spring-boot-starter`, `camel-direct`, `camel-seda`, `camel-http`, `camel-file` (or `camel-ftp`), `camel-jackson` (if needed), `camel-micrometer` (optional).
- Keep Artemis only if you later want durability; otherwise drop JMS deps/config.

### Configuration (application.yaml)
- Keep SPARQL endpoint, storage directory, timeouts.
- Add Camel main settings if you want auto-shutdown:  
  `camel.main.duration-max-idle-seconds: 30`

### Route Shape
1) **Trigger**  
   - `ApplicationRunner` (or `CommandLineRunner`) sends a single message to `direct:start` via `ProducerTemplate`.
   - No timer needed; JVM can exit when Camel idles (see duration above).

2) **Fetch + Dispatch**  
   ```
   from("direct:start").routeId("sparql-fetch")
     .process(sparqlFetcher)        // RestClient fetch CSV
     .process(csvToCellarData)      // parse to List<CellarData>
     .split(body())                 // stream each CellarData
       .to("seda:cellarData");      // in-memory queue
   ```
   - `seda` gives async handoff/backpressure inside one CamelContext.

3) **Consume + Ingest**  
   ```
   from("seda:cellarData").routeId("xml-download")
     .threads(10, 20)
     .process(uriFilterProcessor)   // skip if already stored
     .filter(header("skip") .isNotEqualTo(true))
       .process(xmlDownloadProcessor) // stream to disk with timeout
       .process(xmlPostProcessor)     // extract metadata
       .process(xmlDatabaseWriter);   // Exposed insert
   ```
   - Replace skip flag with your preferred signal (e.g., null body → stop).

### Error Handling
- Use route-level `errorHandler(deadLetterChannel("seda:dlq").maximumRedeliveries(3).redeliveryDelay(5000))`.
- For fail-fast, `errorHandler(defaultErrorHandler().maximumRedeliveries(0))`.
- You can attach `onException<HttpDownloadException>().useOriginalMessage().handled(false)` to let SEDA redeliver.

### Lifecycle
- With `duration-max-idle-seconds`, Camel shuts down after routes go idle (good for CLI runs).
- Without it, the app stays up; you can exit manually after the runner sends the start message.

### Code Moves
- Delete Batch artifacts: `FormexXmlBatchJob`, step listeners, `PercentageBasedSkipPolicy`, `JobLauncherEntrypoint`.
- Convert processors to Camel `Processor` beans (or simple beans invoked via `.bean()`).
- Keep domain models, Exposed database config, storage helpers.

### Testing
- Camel route tests: use `camel-test-spring-junit5` with Spring Boot; stub `sparqlFetcher` and assert messages flow through `seda`.
- Unit test processors as plain beans (no Camel).

### Optional Variations
- Need durability/backpressure? Switch `seda:cellarData` to `jms:queue:cellar.data.queue` and keep Artemis.
- Need periodic runs? Add a `timer://fetch?period=24h` route that sends to `direct:start` instead of `ApplicationRunner`.

### Progress Reporting (simple CLI)
- Use a small bean (or reuse `TerminalProgressService`) to track total + completed.
  ```kotlin
  @Component
  class ProgressTracker {
      private val progress = AtomicLong()
      @Volatile private var total = 0L
      fun start(totalItems: Long) { total = totalItems; progress.set(0) }
      fun tick() {
          val done = progress.incrementAndGet()
          println("Processed $done / $total")
      }
  }
  ```
- In routes:
  ```kotlin
  from("direct:start")
    .process { e ->
        val rows = sparqlFetcher.fetchCsv()
        val list = csvToCellarData(rows)
        progressTracker.start(list.size.toLong())
        e.in.body = list
    }
    .split(body())
      .to("seda:cellarData")

  from("seda:cellarData")
    .threads(10, 20)
    .process(uriFilterProcessor)
    .filter(header("skip").isNotEqualTo(true))
      .process(xmlDownloadProcessor)
      .process(xmlPostProcessor)
      .process(xmlDatabaseWriter)
      .process { progressTracker.tick() }
  ```
- Swap `println` for `TerminalProgressService` if you want nicer terminal output; or expose counters via `camel-micrometer` for dashboards.***
