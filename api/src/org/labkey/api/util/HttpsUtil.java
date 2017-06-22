/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.settings.AppProps;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Utilities to help with HTTPS connections, such as allowing self-signed certificates and other
 * behaviors that are common on developer machines where the system is not using a "real" certificate authority.
 * User: jeckels
 * Date: Nov 22, 2006
 */
public class HttpsUtil
{
    private static final Logger LOG = Logger.getLogger(HttpsUtil.class);
    private static final HostnameVerifier _hostnameVerifier = (urlHostName, session) -> true;

    private static SSLSocketFactory _socketFactory;

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


    /** Attempts a connection to the testURL, returning null on success and a Pair with error message and (possibly null) response code on failure */
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

    /**
     * Create a socket factory that does not validate the server's certificate -
     * all we care about is that the connection is encrypted if it's going over SSL
     */
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


    /**
     *  Called just before the first HTTP -> HTTPS redirect to check whether the SSL redirect port is open and responding.
     *  If not, a warning is logged once, but the server continues to function. Check is currently done in dev mode only, but
     *  recent improvements (using a known test action and making failures non-fatal) may make a production mode check viable.
     *
     *  This check has been the subject of quite a few issues, see #10968, #11103, #19628, #23878.
     */
    public static void checkSslRedirectConfiguration(HttpServletRequest request, int redirectPort)
    {
        if (AppProps.getInstance().isDevMode())
        {
            try
            {
                // Attempt to invoke GuidAction via SSL on the configured port. This is a simple action that doesn't require
                // permissions and is available during upgrade, which should leave SSL connection as the sole failure case.

                // Use a URLHelper to assemble the test URL. Note: Can't use ActionURL here, since AppProps might not have been initiated yet.
                URLHelper helper = new URLHelper(request);
                helper.setScheme("https");
                helper.setPort(redirectPort);
                helper.setContextPath(request.getContextPath());
                helper.setPath("admin/guid.view");

                // Now switch to a URL, since that's what testSslUrl() requires
                URL testURL = new URL(helper.getURIString());
                Pair<String, Integer> sslResult = HttpsUtil.testSslUrl(testURL,
                    "This LabKey Server instance is configured to require secure connections on port " + redirectPort + ", but it does not appear to be responding " +
                    "to HTTPS requests at " + testURL + ". Please see https://www.labkey.org/wiki/home/Documentation/page.view?name=stagingServerTips for " +
                    "details about how to turn off the SSL redirect settings in the database.");

                // Non-null indicates that some problem occurred... throw it so we log it
                if (null != sslResult)
                {
                    throw new ConfigurationException(sslResult.first);
                }
            }
            catch (Throwable t)
            {
                LOG.warn("Could not connect to the configured SSL redirect port", t);
            }
        }
    }
}
