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

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * JSON deserialization target for GetData APIs using a schema/query name combination as the root data source.
 *
 * User: jeckels
 * Date: 5/20/13
 */
@JsonTypeName("query")
public class SchemaQueryReportDataSourceBuilder extends AbstractReportDataSourceBuilder
{
    private String _queryName;

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    @Override
    public QueryReportDataSource create(User user, Container container)
    {
        return new SchemaQueryReportDataSource(user, container, getSchemaKey(), getQueryName(), getContainerFilter(user), getParameters());
    }
}
