/*
 * Copyright (c) 2026 LabKey Corporation
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

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.ServletContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.LenientStartupPropertyHandler;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.settings.StartupPropertyEntry;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Microsoft Graph API email transport provider.
 * Sends email via Graph API using OAuth2 client credentials with token caching.
 * Supports MIME format for full message fidelity including attachments.
 */
public class GraphTransportProvider implements EmailTransportProvider
{
    private static final Logger LOG = LogManager.getLogger(GraphTransportProvider.class);

    private final Properties _properties = new Properties();

    // Token cache
    private volatile String _cachedAccessToken = null;
    private volatile long _tokenExpirationTime = 0;
    private static final long TOKEN_BUFFER_SECONDS = 300; // 5-minute buffer before expiry
    private final Object _tokenLock = new Object();

    private static class GraphStartupProperty implements StartupProperty
    {
        @Override
        public String getPropertyName()
        {
            return "<Microsoft Graph mail setting>";
        }

        @Override
        public String getDescription()
        {
            return "Microsoft Graph email settings: tenantId, clientId, clientSecret, fromAddress";
        }
    }

    @Override
    public String getName()
    {
        return "Microsoft Graph";
    }

    @Override
    public void loadConfiguration()
    {
        try
        {
            // Load from startup properties group "mail_graph"
            ModuleLoader.getInstance().handleStartupProperties(
                new LenientStartupPropertyHandler<>("mail_graph", new GraphStartupProperty())
                {
                    @Override
                    public void handle(Collection<StartupPropertyEntry> entries)
                    {
                        entries.forEach(entry ->
                            _properties.put("mail.graph." + entry.getName(), entry.getValue()));
                    }
                });

            // Fallback: check ServletContext for Graph settings
            if (_properties.isEmpty())
            {
                ServletContext context = ModuleLoader.getServletContext();
                if (context != null)
                {
                    Enumeration<String> names = context.getInitParameterNames();
                    while (names.hasMoreElements())
                    {
                        String name = names.nextElement();
                        if (name.startsWith("mail.graph."))
                            _properties.put(name, context.getInitParameter(name));
                    }
                }
            }

            if (isConfigured())
            {
                LOG.info("Email configured to use Microsoft Graph transport");
            }
        }
        catch (Exception e)
        {
            LOG.error("Exception loading Microsoft Graph configuration", e);
        }
    }

    @Override
    public boolean isConfigured()
    {
        return StringUtils.isNotBlank(getTenantId())
                && StringUtils.isNotBlank(getClientId())
                && StringUtils.isNotBlank(getClientSecret())
                && StringUtils.isNotBlank(getFromAddress());
    }

    @Override
    public void send(Message message) throws MessagingException
    {
        if (!(message instanceof MimeMessage mm))
        {
            throw new MessagingException("GraphTransportProvider only supports MimeMessage instances");
        }

        // Determine From address
        String fromAddress = getFromAddress();
        if (StringUtils.isBlank(fromAddress))
        {
            Address[] froms = mm.getFrom();
            if (froms != null && froms.length > 0)
                fromAddress = froms[0].toString();
        }
        if (StringUtils.isBlank(fromAddress))
        {
            throw new MessagingException("No fromAddress configured and message has no From header");
        }

        try
        {
            String accessToken = acquireToken();
            sendViaMime(fromAddress, accessToken, mm);
        }
        catch (IOException e)
        {
            throw new MessagingException("Failed sending mail via Microsoft Graph", e);
        }
    }

    /**
     * Acquire OAuth2 access token with caching.
     * Reuses cached token if still valid (with 5-minute buffer).
     */
    private String acquireToken() throws IOException
    {
        synchronized (_tokenLock)
        {
            long now = System.currentTimeMillis();

            // Return cached token if still valid
            if (_cachedAccessToken != null && now < (_tokenExpirationTime - TOKEN_BUFFER_SECONDS * 1000))
            {
                LOG.debug("Reusing cached Graph API token");
                return _cachedAccessToken;
            }

            String tenantId = getTenantId();
            String clientId = getClientId();
            String clientSecret = getClientSecret();

            if (StringUtils.isBlank(tenantId) || StringUtils.isBlank(clientId) || StringUtils.isBlank(clientSecret))
            {
                throw new IOException("Microsoft Graph credentials not configured");
            }

            // Fetch new token
            String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
            String form = "grant_type=client_credentials" +
                    "&client_id=" + urlEncode(clientId) +
                    "&client_secret=" + urlEncode(clientSecret) +
                    "&scope=" + urlEncode("https://graph.microsoft.com/.default");

            byte[] postData = form.getBytes(StandardCharsets.UTF_8);
            HttpURLConnection conn = (HttpURLConnection) new URL(tokenUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Content-Length", Integer.toString(postData.length));
            conn.setDoOutput(true);

            try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream()))
            {
                wr.write(postData);
            }

            int code = conn.getResponseCode();
            String response = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());

            if (code < 200 || code >= 300)
            {
                LOG.error("Token request failed (HTTP {}). Response: {}", code, response);
                throw new IOException("Token request failed with HTTP " + code);
            }

            // Parse response and cache
            JSONObject json = new JSONObject(response);
            _cachedAccessToken = json.getString("access_token");
            int expiresIn = json.getInt("expires_in");
            _tokenExpirationTime = now + (expiresIn * 1000L);

            LOG.debug("Acquired new Graph API token, expires in {} seconds", expiresIn);
            return _cachedAccessToken;
        }
    }

    /**
     * Send email using MIME format via Graph API.
     */
    private void sendViaMime(String fromAddress, String accessToken, MimeMessage mm)
            throws IOException, MessagingException
    {
        // Serialize MimeMessage to raw MIME bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mm.writeTo(baos);
        String base64Mime = Base64.getEncoder().encodeToString(baos.toByteArray());

        // POST to Graph sendMail endpoint
        String url = "https://graph.microsoft.com/v1.0/users/" + urlEncode(fromAddress) + "/sendMail";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "text/plain");
        conn.setDoOutput(true);

        try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream()))
        {
            wr.write(base64Mime.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code != 202)
        {
            String response = readAll(conn.getErrorStream());
            LOG.error("Microsoft Graph sendMail failed (HTTP {}). Response: {}", code, response);
            throw new IOException("Graph sendMail failed (HTTP " + code + "): " + response);
        }

        LOG.debug("Email sent successfully via Microsoft Graph");
    }

    private String getTenantId()
    {
        return _properties.getProperty("mail.graph.tenantId");
    }

    private String getClientId()
    {
        return _properties.getProperty("mail.graph.clientId");
    }

    private String getClientSecret()
    {
        return _properties.getProperty("mail.graph.clientSecret");
    }

    private String getFromAddress()
    {
        return _properties.getProperty("mail.graph.fromAddress");
    }

    private static String readAll(InputStream is) throws IOException
    {
        if (is == null)
            return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)))
        {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null)
                sb.append(line);
            return sb.toString();
        }
    }

    private static String urlEncode(String s)
    {
        try
        {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            return s;
        }
    }
}
