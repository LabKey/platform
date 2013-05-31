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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;

import java.util.Map;

/**
 * Data source for LabKey SQL-based requests
 *
 * User: jeckels
 * Date: 5/15/13
 */
public class LabKeySQLReportDataSource extends AbstractQueryReportDataSource
{
    @NotNull private final String _sql;

    public LabKeySQLReportDataSource(@NotNull User user, @NotNull Container container, @NotNull SchemaKey schemaKey, @Nullable ContainerFilter containerFilter, @NotNull Map<String, String> parameters, @NotNull String sql)
    {
        super(user, container, schemaKey, containerFilter, parameters);
        _sql = sql;
    }

    @Override
    protected QueryDefinition createBaseQueryDef()
    {
        QueryDefinition queryDef = QueryService.get().createQueryDef(getSchema().getUser(), getSchema().getContainer(), getSchema(), GUID.makeGUID().replace("-", ""));
        queryDef.setSql(_sql);
        return queryDef;
    }

    @Override
    public String getLabKeySQL()
    {
        return _sql;
    }
}
