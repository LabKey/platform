/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.query.reports.getdata;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.Map;

/**
 * Builder to receive JSON deserialization for a data source for the GetData API and create the real data source.
 *
 * Subclasses are serialized in JSON based on the "type" property, which chooses the appropriate subclass.
 * User: jeckels
 * Date: 5/20/13
 */
@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, include=JsonTypeInfo.As.PROPERTY, property="type")
@JsonSubTypes({
        @JsonSubTypes.Type(value=LabKeySQLReportDataSourceBuilder.class),
        @JsonSubTypes.Type(value=SchemaQueryReportDataSourceBuilder.class)})
public abstract class AbstractReportDataSourceBuilder
{
    private SchemaKey _schemaKey;
    private String _containerFilterName;
    private Map<String, String> _parameters = Collections.emptyMap();

    public SchemaKey getSchemaKey()
    {
        return _schemaKey;
    }

    public void setSchemaName(SchemaKey schemaKey)
    {
        _schemaKey = schemaKey;
    }

    public ContainerFilter getContainerFilter(User user)
    {
        if (_containerFilterName == null)
        {
            return null;
        }
        return ContainerFilter.getContainerFilterByName(_containerFilterName, user);
    }

    public void setContainerFilter(String containerFilterName)
    {
        _containerFilterName = containerFilterName;
    }

    public Map<String, String> getParameters()
    {
        return _parameters;
    }

    public void setParameters(Map<String, String> parameters)
    {
        _parameters = Collections.unmodifiableMap(parameters);
    }

    public abstract QueryReportDataSource create(User user, Container container);
}
