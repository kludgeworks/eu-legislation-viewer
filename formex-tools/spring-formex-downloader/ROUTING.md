# Apache Camel Routing Architecture

## Overview

This application uses Apache Camel for processing large datasets (600k+ records) from SPARQL endpoints. The routing architecture is optimized for memory efficiency and parallel processing.

## Route Design

### One-Time Startup Execution

```kotlin
from("timer://runOnce?repeatCount=1&delay=1000")
```

- **Pattern**: Timer with `repeatCount=1` (idiomatic Camel)
- **Why**: More idiomatic than `ApplicationRunner` + `direct:start`
- **Delay**: 1000ms ensures CamelContext is fully initialized

### Streaming Split with Parallel Processing

```kotlin
.split(body()).streaming().parallelProcessing()
    .threads(10, 20)
    .to("direct:processItem")
```

- **`streaming()`**: Processes items one-at-a-time without loading all 600k into memory
- **`parallelProcessing()`**: Enables concurrent processing
- **`threads(10, 20)`**: Thread pool with 10-20 threads for parallel work
- **`direct:`**: No intermediate queue overhead

## Why `direct` + Streaming Instead of `SEDA`?

### Original Issue: SEDA Queue Full

When using `SEDA` with large datasets:
```
java.lang.IllegalStateException: Queue full
```

### Attempted Fix: SEDA with Backpressure

```kotlin
.to("seda:cellarData?size=5000&blockWhenFull=true&timeout=60000")
```

**Problems with this approach for 600k items:**
- Even with `size=5000`, constant blocking
- Setting `size=600000` consumes excessive memory
- `blockWhenFull=true` defeats async processing purpose
- Queue management overhead

###  Solution: `direct` with Streaming Split

According to [Apache Camel Split EIP](https://camel.apache.org/components/4.14.x/eips/split-eip.html) and [performance tuning best practices](https://dzone.com/articles/performance-tuning-ideas-for-apache-camel):

**Benefits:**
1. **No Queue Overhead**: `direct` has no intermediate queue ([comparison](https://camel.apache.org/manual/faq/how-do-the-direct-event-seda-and-vm-endpoints-compare.html))
2. **Memory Efficient**: `.streaming()` processes items individually
3. **Parallel Processing**: `parallelProcessing()` + `.threads()` provides concurrency
4. **Simpler**: No queue configuration complexity
5. **Scalable**: Handles 600k+ items efficiently

## Concurrency Model

```
SPARQL Fetch (1 thread)
   ↓
Streaming Split
   ↓
Thread Pool (10-20 threads) → direct:processItem
   ↓
UriFilter → XmlDownload → XmlPostProcess → DatabaseWrite
```

- **Producer**: Single thread fetches SPARQL results
- **Consumers**: 10-20 threads process items in parallel
- **Backpressure**: Natural flow control via thread pool saturation

## Configuration

### Thread Pool Sizing

```kotlin
.threads(10, 20)
```

- **Core**: 10 threads (always active)
- **Max**: 20 threads (scales under load)
- **Rationale**: Balances throughput with HTTP connection limits

### For Testing

Tests use `camel.springboot.main-run-controller=false` to prevent timer routes from auto-executing.

## References

- [Apache Camel Split EIP](https://camel.apache.org/components/4.14.x/eips/split-eip.html)
- [Direct vs SEDA Comparison](https://camel.apache.org/manual/faq/how-do-the-direct-event-seda-and-vm-endpoints-compare.html)
- [SEDA Component](https://camel.apache.org/components/4.8.x/seda-component.html)
- [Performance Tuning](https://dzone.com/articles/performance-tuning-ideas-for-apache-camel)
- [Best Strategy for Large Datasets](https://users.camel.apache.narkive.com/pa0YbAXy/best-strategy-to-process-a-large-number-of-rows-in-file)
