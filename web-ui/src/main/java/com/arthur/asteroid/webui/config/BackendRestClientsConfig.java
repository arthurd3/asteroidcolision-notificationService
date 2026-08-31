package com.arthur.asteroid.webui.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * One {@link RestClient} per backend, each with its own timeout budget.
 *
 * <p>Deliberately the same technique as asteroid-service's
 * {@code NasaRestClientsConfig}: override only the timeouts on Boot's
 * {@link HttpClientSettings} and let it keep everything else. Seeing it twice is the
 * point - per-client timeouts are not a NASA-specific trick, they are what you do
 * whenever one process calls several others that do not behave alike.
 */
@Configuration(proxyBeanMethods = false)
public class BackendRestClientsConfig {

    @Bean
    RestClient asteroidServiceRestClient(RestClient.Builder builder,
                                         ObjectProvider<ClientHttpRequestFactoryBuilder<?>> factories,
                                         ObjectProvider<HttpClientSettings> settings,
                                         BackendProperties properties) {
        return build(builder, factories, settings, properties.asteroidService());
    }

    @Bean
    RestClient notificationServiceRestClient(RestClient.Builder builder,
                                             ObjectProvider<ClientHttpRequestFactoryBuilder<?>> factories,
                                             ObjectProvider<HttpClientSettings> settings,
                                             BackendProperties properties) {
        return build(builder, factories, settings, properties.notificationService());
    }

    private static RestClient build(final RestClient.Builder builder,
                                    final ObjectProvider<ClientHttpRequestFactoryBuilder<?>> factories,
                                    final ObjectProvider<HttpClientSettings> settings,
                                    final BackendProperties.Backend backend) {

        final HttpClientSettings perBackend = settings.getIfAvailable(HttpClientSettings::defaults)
                .withTimeouts(backend.connectTimeout(), backend.readTimeout());

        return builder.clone()
                .baseUrl(backend.baseUrl())
                .requestFactory(factories.getIfAvailable(ClientHttpRequestFactoryBuilder::detect)
                        .build(perBackend))
                .build();
    }
}
