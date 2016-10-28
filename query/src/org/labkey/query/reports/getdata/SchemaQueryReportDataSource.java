/*
 * Copyright (c) 2013-2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;
import org.labkey.query.LinkedSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Data source for schema/query name combination GetData requests
 * User: jeckels
 * Date: 5/15/13
 */
public class SchemaQueryReportDataSource extends AbstractQueryReportDataSource
{
    private final String _queryName;

    public SchemaQueryReportDataSource(@NotNull User user, @NotNull Container container, @NotNull SchemaKey schemaKey, @NotNull String queryName, @Nullable ContainerFilter containerFilter, @NotNull Map<String, String> parameters)
    {
        super(user, container, schemaKey, containerFilter, parameters);
        _queryName = queryName;
    }

    @Override
    protected QueryDefinition createBaseQueryDef()
    {
        if (getSchema().getTable(_queryName) != null) // query exists
        {
            QueryDefinition result = getSchema().getQueryDefForTable(_queryName);
            if (result == null) // This is likely a redundant check, as TableQueryDefinition.getQueryDef() doesn't ever return null
            {
                throw new NotFoundException("No such query '" + _queryName + "' in schema '" + getSchema().getName());
            }
            return result;
        }
        else
        {
            throw new NotFoundException("No such query '" + _queryName + "' in schema '" + getSchema().getName());
        }
    }

    @Override
    public String getLabKeySQL()
    {
        return LinkedSchema.generateLabKeySQL(getQueryDefinition().getTable(getSchema(), new ArrayList<QueryException>(), true), new LinkedSchema.SQLWhereClauseSource()
        {
            @Override
            public List<String> getWhereClauses(TableInfo sourceTable)
            {
                return Collections.emptyList();
            }
        }, Collections.emptySet());
    }
}
