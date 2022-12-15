package org.labkey.api.util;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustSelfSignedStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

/**
 * Implements standard pattern for producing CloseableHttpClients that accept self-signed certificates. HttpClient 5.x
 * made this even more complicated by requiring a ConnectionManager in the mix. Use a SelfSignedHttpClientFactory to
 * reduce boilerplate in code that makes HTTP requests to external servers.
 */
public class SelfSignedHttpClientFactory
{
    private final HttpClientConnectionManager _connectionManager;

    public SelfSignedHttpClientFactory()
    {
        try
        {
            SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
            sslContextBuilder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
            SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContextBuilder.build());
            _connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslConnectionSocketFactory)
                .build();
        }
        catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e)
        {
            throw new RuntimeException(e);
        }
    }

    public CloseableHttpClient buildClient()
    {
        return HttpClientBuilder.create()
            .setConnectionManager(_connectionManager)
            .setConnectionManagerShared(true)
            .build();
    }
}
