package eu.legislation.formex.ingester

import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder
import org.apache.hc.client5.http.ssl.TrustAllStrategy
import org.apache.hc.core5.ssl.SSLContextBuilder
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Test-specific RestClient configuration for E2E tests.
 *
 * This configuration is ONLY used in test profiles (e2e, e2e-error-logging).
 * It configures RestClient to trust all SSL certificates, which is necessary
 * for testing in corporate environments with SSL inspection proxies (e.g., Zscaler).
 *
 * WARNING: This configuration should NEVER be used in production! It disables
 * SSL certificate validation, which creates a security vulnerability.
 *
 * The @Primary annotation ensures this bean takes precedence over the production
 * RestClient bean when running E2E tests.
 */
@TestConfiguration
@Profile("e2e", "e2e-error-logging")
class TestRestClientConfig {
    /**
     * RestClient that trusts all SSL certificates for E2E testing.
     *
     * This configuration:
     * - Trusts all SSL certificates (including self-signed and proxy certificates)
     * - Disables hostname verification
     * - Same timeout settings as production (30s connect, 5min read)
     *
     * This allows E2E tests to work in corporate environments with SSL inspection
     * proxies without requiring certificate import into Java's truststore.
     *
     * @return RestClient configured to trust all certificates
     */
    @Bean
    @Primary
    fun testRestClient(): RestClient {
        // Create SSL context that trusts all certificates
        val sslContext =
            SSLContextBuilder
                .create()
                .loadTrustMaterial(TrustAllStrategy())
                .build()

        // Configure connection manager with permissive SSL
        val connectionManager =
            PoolingHttpClientConnectionManagerBuilder
                .create()
                .setSSLSocketFactory(
                    SSLConnectionSocketFactoryBuilder
                        .create()
                        .setSslContext(sslContext)
                        .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                        .build(),
                ).build()

        // Create HttpClient with custom SSL configuration
        val httpClient =
            HttpClients
                .custom()
                .setConnectionManager(connectionManager)
                .build()

        val requestFactory =
            HttpComponentsClientHttpRequestFactory(httpClient).apply {
                setConnectTimeout(Duration.ofSeconds(30))
                setReadTimeout(Duration.ofMinutes(5))
            }

        return RestClient
            .builder()
            .requestFactory(requestFactory)
            .build()
    }
}
