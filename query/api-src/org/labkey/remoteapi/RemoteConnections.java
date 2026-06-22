/*
 * Copyright (c) 2013-2026 LabKey Corporation
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
package org.labkey.remoteapi;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.WritablePropertyMap;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.validation.BindException;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * User: gktaylor
 * Date: 10/29/13
 */
public class RemoteConnections
{
    private static final Logger LOG = LogHelper.getLogger(RemoteConnections.class, "Remote server connection management for ETLs");

    public static String REMOTE_QUERY_CONNECTIONS_CATEGORY = "remote-connections";
    public static String REMOTE_FILE_CONNECTIONS_CATEGORY = "remote-file-connections";
    public static String FIELD_URL = "URL";
    public static String FIELD_USER = "user";
    public static String FIELD_PASSWORD = "password";
    public static String FIELD_CONTAINER = "container";

    public static String CONNECTION_KIND_QUERY = "query";
    public static String CONNECTION_KIND_FILE = "file";

    // Instructions/warning displayed on both manage and edit pages
    public static String MANAGEMENT_PAGE_INSTRUCTIONS =
        """
        Administrators can define remote connections to other LabKey Server instances and then use them in ETLs to move
        data between instances. This feature should be used with care since all schemas in the remote folder will be
        available to anyone writing or running ETLs.
        """;

    public static @NotNull Map<String, String> getRemoteConnection(String connectionCategory, String name, Container container)
    {
        return PropertyManager.getEncryptedStore().getProperties(container,
                RemoteConnections.makeRemoteConnectionKey(connectionCategory, name));
    }

    public static boolean createOrEditRemoteConnection(RemoteConnectionForm remoteConnectionForm, Container container, BindException errors)
    {
        String name = remoteConnectionForm.getConnectionName();
        String newName = remoteConnectionForm.getNewConnectionName();
        boolean editing = StringUtils.isNotEmpty(name);
        boolean changingName = editing && !name.equals(newName);

        String url = remoteConnectionForm.getUrl();
        String user = remoteConnectionForm.getUserEmail();
        String password = remoteConnectionForm.getPassword();
        String folderPath = remoteConnectionForm.getFolderPath();
        String connectionKind = remoteConnectionForm.getConnectionKind();

        if (StringUtils.isBlank(newName) || url == null || user == null || password == null || (CONNECTION_KIND_QUERY.equals(connectionKind) && folderPath == null))
        {
            errors.addError(new LabKeyError("All fields must be filled in."));
            return false;
        }

        // validate the url string and connection
        URL urlObj;
        try
        {
            urlObj = new URL(url);
            URLConnection conn = urlObj.openConnection();
            conn.connect();
        }
        catch (MalformedURLException e)
        {
            errors.addError(new LabKeyError("The entered URL is not valid."));
            return false;
        }
        catch (SSLException e)
        {
            LOG.warn("TLS error connecting to remote connection URL: {}", url, e);
            errors.addError(new LabKeyError("A secure (TLS) connection to the entered URL could not be established. This is often caused by an untrusted, self-signed, or expired certificate. " + getBriefMessage(e)));
            return false;
        }
        catch (IOException e)
        {
            LOG.warn("Error connecting to remote connection URL: {}", url, e);
            errors.addError(new LabKeyError("A connection to the entered URL could not be established. " + getBriefMessage(e)));
            return false;
        }

        if (isCleartextHttpUrl(urlObj))
        {
            LOG.warn("Remote connection '{}' is configured with a cleartext http:// URL ({}). Credentials will be sent unencrypted. Use https:// instead.", newName, url);
        }

        // validate the user
        try
        {
            ValidEmail validEmail = new ValidEmail(user);
        }
        catch(ValidEmail.InvalidEmailException e)
        {
            errors.addError(new LabKeyError("The entered user is not a valid email address."));
            return false;
        }

        // save the connection name in connectionMap
        String connectionsCategory = CONNECTION_KIND_QUERY.equals(connectionKind) ? REMOTE_QUERY_CONNECTIONS_CATEGORY : REMOTE_FILE_CONNECTIONS_CATEGORY;
        WritablePropertyMap connectionMap = PropertyManager.getEncryptedStore().getWritableProperties(container, connectionsCategory, true);
        if ((!editing || changingName) && connectionMap.containsKey(makeRemoteConnectionKey(connectionsCategory, newName)))
        {
            errors.addError(new LabKeyError("There is already a remote connection with the name '" + newName + "'."));
            return false;
        }

        if (changingName)
        {
            String oldNameKey = makeRemoteConnectionKey(connectionsCategory, name);
            connectionMap.remove(oldNameKey);        // Remove old name
            PropertyManager.getEncryptedStore().deletePropertySet(container, oldNameKey);
        }

        String newNameKey = makeRemoteConnectionKey(connectionsCategory, newName);
        connectionMap.put(newNameKey, newName);
        connectionMap.save();

        // save the properties for the individual connection in the encrypted property store
        WritablePropertyMap singleConnectionMap = PropertyManager.getEncryptedStore().getWritableProperties(container, newNameKey, true);
        singleConnectionMap.put(RemoteConnections.FIELD_URL, url);
        singleConnectionMap.put(RemoteConnections.FIELD_USER, user);
        singleConnectionMap.put(RemoteConnections.FIELD_PASSWORD, password);
        if (CONNECTION_KIND_QUERY.equals(connectionKind))
            singleConnectionMap.put(RemoteConnections.FIELD_CONTAINER, folderPath);
        singleConnectionMap.save();
        return true;
    }

    /** @return a brief, user-facing description of the failure, suitable for appending to an error message. Full details should be logged separately. */
    public static String getBriefMessage(Throwable t)
    {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    public static boolean isCleartextHttpUrl(@NotNull URL url)
    {
        return "http".equalsIgnoreCase(url.getProtocol());
    }

    public static boolean deleteRemoteConnection(RemoteConnectionForm remoteConnectionForm, Container container)
    {
        String name = remoteConnectionForm.getConnectionName();

        // delete the index
        String connectionsCategory = CONNECTION_KIND_QUERY.equals(remoteConnectionForm.getConnectionKind()) ? REMOTE_QUERY_CONNECTIONS_CATEGORY : REMOTE_FILE_CONNECTIONS_CATEGORY;
        WritablePropertyMap connectionMap = PropertyManager.getEncryptedStore().getWritableProperties(container, connectionsCategory, false);
        connectionMap.remove(makeRemoteConnectionKey(connectionsCategory, name));
        connectionMap.save();

        // delete the underlying property set
        PropertyManager.getEncryptedStore().deletePropertySet(container, makeRemoteConnectionKey(connectionsCategory, name));

        return true;
    }

    public static class RemoteConnectionForm
    {
        private String _connectionName;
        private String _url;
        private String _user;
        private String _password;
        private String _folderPath;
        private String _newConnectionName;
        private String _connectionKind;

        public RemoteConnectionForm()
        {
        }

        public String getConnectionName()
        {
            return _connectionName;
        }

        public void setConnectionName(String connectionName)
        {
            _connectionName = connectionName;
        }

        public String getUrl()
        {
            return _url;
        }

        public void setUrl(String url)
        {
            _url = url;
        }

        public String getUserEmail()
        {
            return _user;
        }

        public void setUserEmail(String user)
        {
            _user = user;
        }

        public String getPassword()
        {
            return _password;
        }

        public void setPassword(String password)
        {
            _password = password;
        }

        public String getFolderPath()
        {
            return _folderPath;
        }

        public void setFolderPath(String folderPath)
        {
            _folderPath = folderPath;
        }

        public String getNewConnectionName()
        {
            return _newConnectionName;
        }

        public void setNewConnectionName(String newConnectionName)
        {
            _newConnectionName = newConnectionName;
        }

        public String getConnectionKind()
        {
            return _connectionKind;
        }

        public void setConnectionKind(String connectionKind)
        {
            _connectionKind = connectionKind;
        }
    }

    private static String makeRemoteConnectionKey(String connectionCategory, String name)
    {
        return connectionCategory + ":" + name;
    }

    public static class TestCase extends Assert
    {
        /** All URL validation failures return before touching the property store, so no container is needed */
        private BindException validate(String url)
        {
            RemoteConnectionForm form = new RemoteConnectionForm();
            form.setNewConnectionName("RemoteConnectionsTestCase");
            form.setUrl(url);
            form.setUserEmail("remoteconnections_testcase@validation.test");
            form.setPassword("password");
            // A file connection doesn't require a folder path
            form.setConnectionKind(CONNECTION_KIND_FILE);

            BindException errors = new BindException(form, "form");
            assertFalse("Expected validation to fail for URL: " + url, createOrEditRemoteConnection(form, null, errors));
            assertEquals("Expected a single validation error for URL: " + url, 1, errors.getErrorCount());
            return errors;
        }

        private void assertErrorStartsWith(BindException errors, String expectedPrefix)
        {
            String message = errors.getAllErrors().get(0).getDefaultMessage();
            assertNotNull("Expected an error message", message);
            assertTrue("Expected error message to start with '" + expectedPrefix + "' but was: " + message, message.startsWith(expectedPrefix));
        }

        @Test
        public void testMalformedUrl()
        {
            assertErrorStartsWith(validate("hptt://localhost/bogus"), "The entered URL is not valid.");
        }

        @Test
        public void testConnectionRefused() throws IOException
        {
            // Bind an ephemeral port, then release it so nothing is listening when we connect
            int port;
            try (ServerSocket socket = new ServerSocket(0))
            {
                port = socket.getLocalPort();
            }
            assertErrorStartsWith(validate("http://localhost:" + port + "/"), "A connection to the entered URL could not be established.");
        }

        @Test
        public void testTlsFailure() throws Exception
        {
            // Answer the TLS handshake with plain text, which fails the https client connection with an SSLException
            try (ServerSocket socket = new ServerSocket(0))
            {
                Thread responder = new Thread(() ->
                {
                    try (Socket client = socket.accept())
                    {
                        client.getOutputStream().write("This is not a TLS handshake".getBytes(StandardCharsets.UTF_8));
                        client.getOutputStream().flush();
                        // Drain the client's handshake bytes until it disconnects
                        InputStream in = client.getInputStream();
                        byte[] buffer = new byte[1024];
                        while (in.read(buffer) != -1) { /* keep draining */ }
                    }
                    catch (IOException ignored) {}
                }, "RemoteConnections.TestCase non-TLS responder");
                responder.start();

                assertErrorStartsWith(validate("https://localhost:" + socket.getLocalPort() + "/"), "A secure (TLS) connection to the entered URL could not be established.");
                responder.join(TimeUnit.SECONDS.toMillis(10));
            }
        }

        @Test
        public void testGetBriefMessage()
        {
            assertEquals("boom", getBriefMessage(new IOException("boom")));
            assertEquals("IOException", getBriefMessage(new IOException()));
        }

        @Test
        public void testIsCleartextHttpUrl() throws MalformedURLException
        {
            assertTrue("http:// must be detected as cleartext", isCleartextHttpUrl(new URL("http://example.com/labkey")));
            assertTrue("scheme match is case-insensitive", isCleartextHttpUrl(new URL("HTTP://example.com/labkey")));
            assertFalse("https:// is encrypted", isCleartextHttpUrl(new URL("https://example.com/labkey")));
            assertFalse("HTTPS:// is encrypted", isCleartextHttpUrl(new URL("HTTPS://example.com/labkey")));
        }
    }
}
