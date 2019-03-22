/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.api.study;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.Pair;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to expose providers for a Master Patient Index
 */
public interface MasterPatientIndexService
{
    Map<String, MasterPatientIndexService> _providerMap = new HashMap<>();

    static void registerProvider(MasterPatientIndexService provider)
    {
        if (!_providerMap.containsKey(provider.getName()))
            _providerMap.put(provider.getName(), provider);
        else
            throw new RuntimeException("Master Patient Index Provider: " + provider.getName() + " is already registered");
    }

    static Collection<MasterPatientIndexService> getProviders()
    {
        return _providerMap.values();
    }

    @Nullable
    static MasterPatientIndexService getProvider(String name)
    {
        return _providerMap.get(name);
    }

    String getName();

    /**
     * Save the site server settings for this provider
     */
    void setServerSettings(ServerSettings settings);
    ServerSettings getServerSettings();

    /**
     * Configure provider settings for a folder
     */
    void setFolderSettings(Container container, FolderSettings settings);
    FolderSettings getFolderSettings(Container container);

    boolean checkServerSettings(ServerSettings settings);

    /**
     * Update patient index information for the specified container
     */
    void updateIndices(PipelineJob pipelineJob);

    /**
     * Utility to purge records matching the specified attribute key value pairs
     * @param attributes
     * @return the number of records removed
     */
    default int deleteMatchingRecords(List<Pair<String, String>> attributes)
    {
        return 0;
    }

    class ServerSettings
    {
        private String _url;
        private String _username;
        private String _password;

        public String getUrl()
        {
            return _url;
        }

        public void setUrl(String url)
        {
            _url = url;
        }

        public String getUsername()
        {
            return _username;
        }

        public void setUsername(String username)
        {
            _username = username;
        }

        public String getPassword()
        {
            return _password;
        }

        public void setPassword(String password)
        {
            _password = password;
        }

        public boolean isValid()
        {
            return !StringUtils.isBlank(_url) && !StringUtils.isBlank(_username) && !StringUtils.isBlank(_password);
        }
    }

    class FolderSettings
    {
        private String _schema;             // the schema name of the query used to provide patient information
        private String _query;              // the name of the query used to provide patient information
        private String _dataset;            // the name of the dataset to hold the universal patient index ID
        private String _fieldName;          // the name of the field to store the universal patient index ID
        private boolean _enabled;
        private int _reloadUser;

        public String getSchema()
        {
            return _schema;
        }

        public void setSchema(String schema)
        {
            _schema = schema;
        }

        public String getQuery()
        {
            return _query;
        }

        public void setQuery(String query)
        {
            _query = query;
        }

        public String getDataset()
        {
            return _dataset;
        }

        public void setDataset(String dataset)
        {
            _dataset = dataset;
        }

        public String getFieldName()
        {
            return _fieldName;
        }

        public void setFieldName(String fieldName)
        {
            _fieldName = fieldName;
        }

        public boolean isEnabled()
        {
            return _enabled;
        }

        public void setEnabled(boolean enabled)
        {
            _enabled = enabled;
        }

        public int getReloadUser()
        {
            return _reloadUser;
        }

        public void setReloadUser(int reloadUser)
        {
            _reloadUser = reloadUser;
        }

        public boolean isValid()
        {
            return !StringUtils.isBlank(_schema) && !StringUtils.isBlank(_query) && !StringUtils.isBlank(_dataset) && !StringUtils.isBlank(_fieldName);
        }
    }
}
