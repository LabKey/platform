/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
import org.labkey.api.action.LabKeyError;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.ValidEmail;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

/**
 * User: gktaylor
 * Date: 10/29/13
 */
public class RemoteConnections
{
    public static String REMOTE_QUERY_CONNECTIONS_CATEGORY = "remote-connections";
    public static String REMOTE_FILE_CONNECTIONS_CATEGORY = "remote-file-connections";
    public static String FIELD_URL = "URL";
    public static String FIELD_USER = "user";
    public static String FIELD_PASSWORD = "password";
    public static String FIELD_CONTAINER = "container";

    public static String CONNECTION_KIND_QUERY = "query";
    public static String CONNECTION_KIND_FILE = "file";

    public static Map<String, String> getRemoteConnection(String connectionCategory, String name, Container container)
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
        if (!StringUtils.isNotBlank(newName))
        {
            errors.addError(new LabKeyError("Connection name may not be blank."));
            return false;
        }

        String url = remoteConnectionForm.getUrl();
        String user = remoteConnectionForm.getUser();
        String password = remoteConnectionForm.getPassword();
        String folderPath = remoteConnectionForm.getContainer();
        String connectionKind = remoteConnectionForm.getConnectionKind();

        if (url == null || user == null || password == null || (CONNECTION_KIND_QUERY.equals(connectionKind) && folderPath == null))
        {
            errors.addError(new LabKeyError("All fields must be filled in."));
            return false;
        }

        // validate the url string and connection
        try
        {
            URL urlObj = new URL(url);
            URLConnection conn = urlObj.openConnection();
            conn.connect();
        }
        catch (MalformedURLException e)
        {
            errors.addError(new LabKeyError("The entered URL is not valid."));
            return false;
        }
        catch (IOException e)
        {
            errors.addError(new LabKeyError("A connection to the entered URL could not be established."));
            return false;
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
        PropertyManager.PropertyMap connectionMap = PropertyManager.getEncryptedStore().getWritableProperties(container, connectionsCategory, true);
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
        PropertyManager.PropertyMap singleConnectionMap = PropertyManager.getEncryptedStore().getWritableProperties(container, newNameKey, true);
        singleConnectionMap.put(RemoteConnections.FIELD_URL, url);
        singleConnectionMap.put(RemoteConnections.FIELD_USER, user);
        singleConnectionMap.put(RemoteConnections.FIELD_PASSWORD, password);
        if (CONNECTION_KIND_QUERY.equals(connectionKind))
            singleConnectionMap.put(RemoteConnections.FIELD_CONTAINER, folderPath);
        singleConnectionMap.save();
        return true;
    }

    public static boolean deleteRemoteConnection(RemoteConnectionForm remoteConnectionForm, Container container)
    {
        String name = remoteConnectionForm.getConnectionName();

        // delete the index
        String connectionsCategory = CONNECTION_KIND_QUERY.equals(remoteConnectionForm.getConnectionKind()) ? REMOTE_QUERY_CONNECTIONS_CATEGORY : REMOTE_FILE_CONNECTIONS_CATEGORY;
        PropertyManager.PropertyMap connectionMap = PropertyManager.getEncryptedStore().getWritableProperties(container, connectionsCategory, false);
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
        private String _container;
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

        public String getUser()
        {
            return _user;
        }

        public void setUser(String user)
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

        public String getContainer()
        {
            return _container;
        }

        public void setContainer(String container)
        {
            _container = container;
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
}
