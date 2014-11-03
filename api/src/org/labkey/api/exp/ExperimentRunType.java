/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

package org.labkey.api.exp;

import org.labkey.api.data.*;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Set;

/**
 * User: jeckels
 * Date: Sep 25, 2006
 */
public abstract class ExperimentRunType implements Comparable<ExperimentRunType>, Handler<ExpProtocol>
{
    private final String _description;
    private final String _schemaName;
    private final String _tableName;
    public static final ExperimentRunType ALL_RUNS_TYPE = new ExperimentRunType("All Runs", ExpSchema.SCHEMA_NAME, ExpSchema.TableType.Runs.toString())
    {
        public Priority getPriority(ExpProtocol object)
        {
            return Priority.LOW;
        }
    };

    public ExperimentRunType(String description, String schemaName, String tableName)
    {
        _description = description;
        _schemaName = schemaName;
        _tableName = tableName;
    }

    public String getDescription()
    {
        return _description;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public String getTableName()
    {
        return _tableName;
    }

    public long getRunCount(User user, Container c)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, c, _schemaName);
        if (schema == null)
        {
            return 0;
        }
        TableInfo table = schema.getTable(_tableName);
        if (table == null)
        {
            return 0;
        }
        return new TableSelector(table).getRowCount();
    }

    public int compareTo(ExperimentRunType o)
    {
        return _description.compareTo(o.getDescription());
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ExperimentRunType that = (ExperimentRunType) o;

        return !(_description != null ? !_description.equals(that._description) : that._description != null);
    }

    public int hashCode()
    {
        return (_description != null ? _description.hashCode() : 0);
    }

    public static ExperimentRunType getSelectedFilter(Set<ExperimentRunType> types, String filterName)
    {
        if (filterName == null)
        {
            return null;
        }

        for (ExperimentRunType type : types)
        {
            if (type.getDescription().equals(filterName))
            {
                return type;
            }
        }
        return null;
    }

    public void populateButtonBar(ViewContext context, ButtonBar bar, DataView view, ContainerFilter containerFilter)
    {
    }

    /** Allows subclasses to render a header to the response before the QueryView with the run list gets rendered */
    public void renderHeader(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
    }
}
