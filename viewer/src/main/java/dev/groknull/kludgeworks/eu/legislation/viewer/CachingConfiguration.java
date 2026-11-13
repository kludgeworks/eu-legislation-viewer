// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Spring's caches for all modules.
 *
 * <ul>
 *   <li>Each module configures the cache manager of its own caches, in its infrastructure, and
 *       names its caches in {@code @Cacheable}. A cache is found by its name, in the managers of
 *       all modules.
 *   <li>A cache that fails, mostly because Valkey is unreachable, is logged, and the method runs as
 *       if nothing were cached. The default would fail the request.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
class CachingConfiguration implements CachingConfigurer {
  private final ObjectProvider<CacheManager> managers;

  CachingConfiguration(ObjectProvider<CacheManager> managers) {
    this.managers = managers;
  }

  @Override
  public CacheManager cacheManager() {
    var all = new CompositeCacheManager();
    all.setCacheManagers(managers.orderedStream().toList());
    all.afterPropertiesSet();
    return all;
  }

  @Override
  public CacheErrorHandler errorHandler() {
    return new LoggingCacheErrorHandler(CachingConfiguration.class.getName(), false);
  }
}
