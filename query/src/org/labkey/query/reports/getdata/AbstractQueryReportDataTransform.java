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
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;

import java.util.Collection;

/**
 * User: jeckels
 * Date: 5/17/13
 */
public abstract class AbstractQueryReportDataTransform extends AbstractBaseQueryReportDataSource implements ReportDataTransform
{
    private final QueryReportDataSource _source;

    public AbstractQueryReportDataTransform(QueryReportDataSource source)
    {
        _source = source;
    }

    @Override
    public QueryReportDataSource getSource()
    {
        return _source;
    }

    @NotNull
    @Override
    public UserSchema getSchema()
    {
        return getSource().getSchema();
    }

    @Override
    public QueryDefinition getQueryDefinition()
    {
        QueryDefinition queryDef = QueryService.get().createQueryDef(getSource().getSchema().getUser(), getSource().getSchema().getContainer(), getSchema(), "InternalTransform");
        queryDef.setSql(getLabKeySQL());
        return queryDef;
    }

    protected abstract Collection<FieldKey> getRequiredInputs();
}
