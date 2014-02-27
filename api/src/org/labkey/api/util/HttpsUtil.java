/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.URL;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;

/**
 * User: jeckels
 * Date: Nov 22, 2006
 */
public class HttpsUtil
{

    private static SSLSocketFactory _socketFactory;
    private static HostnameVerifier _hostnameVerifier = new HostnameVerifier()
    {
        public boolean verify(String urlHostName, SSLSession session)
        {
            return true;
        }
    };

    /**
     * Disables host name validation, as well as certificate trust chain
     * validation, on the connection. This will let Java connect to self-signed
     * certs over SSL without complaint, as well as to SSL certs that
     * don't match the URL (for example, connecting to "localhost" when the
     * SSL for localhost says it's for the server "labkey.com".
     */
    public static void disableValidation(HttpsURLConnection sslConnection)
    {
        sslConnection.setHostnameVerifier(_hostnameVerifier);
        sslConnection.setSSLSocketFactory(getSocketFactory());
    }


    // Attempts a connection to the testURL, returning null on success and a Pair with error message and (possibly null) response code on failure
    public static @Nullable Pair<String, Integer> testSslUrl(URL testURL, String advice)
    {
        try
        {
            HttpsURLConnection connection = (HttpsURLConnection)testURL.openConnection();
            HttpsUtil.disableValidation(connection);

            if (connection.getResponseCode() != 200)
            {
                return new Pair<>("Bad response code, " + connection.getResponseCode() + " when connecting to the SSL port over HTTPS", connection.getResponseCode());
            }
        }
        catch (IOException e)
        {
            return new Pair<>("Error connecting over HTTPS - Attempted to connect to " + testURL + " and received the following error: " +
                    (e.getMessage() == null ? e.toString() : e.getMessage()) + ". " + advice, null);
        }

        return null;
    }

    // Create a socket factory that does not validate the server's certificate -
    // all we care about is that the connection is encrypted if it's going over SSL
    private synchronized static SSLSocketFactory getSocketFactory()
    {
        if (_socketFactory == null)
        {
            //Create a trust manager that does not validate certificate chains:
            TrustManager[] trustAllCerts = new TrustManager[]
                {
                    new X509TrustManager()
                    {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers()
                        {
                            return new java.security.cert.X509Certificate[0];
                        }

                        public void checkClientTrusted(java.security.cert.X509Certificate[] x509Certificates, String string)
                        {}

                        public void checkServerTrusted(java.security.cert.X509Certificate[] x509Certificates, String string)
                        {}
                    }
                };

            try
            {
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new SecureRandom());
                _socketFactory = sc.getSocketFactory();
            }
            catch (NoSuchAlgorithmException | KeyManagementException e)
            {
                throw new RuntimeException(e);
            }
        }
        return _socketFactory;
    }
}
