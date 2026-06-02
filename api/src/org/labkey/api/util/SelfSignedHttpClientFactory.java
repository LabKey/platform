/*
 * Copyright (c) 2022-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
