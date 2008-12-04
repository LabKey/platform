/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
import org.labkey.api.exp.api.ExpSchema;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;

import java.sql.SQLException;

/**
 * User: jeckels
 * Date: Sep 25, 2006
 */
public abstract class ExperimentRunFilter implements Comparable<ExperimentRunFilter>, Handler<ExpProtocol>
{
    private final String _description;
    private final String _schemaName;
    private final String _tableName;
    public static final ExperimentRunFilter ALL_RUNS_FILTER = new ExperimentRunFilter("All Runs", ExpSchema.SCHEMA_NAME, ExpSchema.TableType.Runs.toString())
    {
        public Priority getPriority(ExpProtocol object)
        {
            return Priority.LOW;
        }
    };

    public ExperimentRunFilter(String description, String schemaName, String tableName)
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

    public int getRunCount(User user, Container c)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, c, _schemaName);
        TableInfo table = schema.getTable(_tableName, null);
        SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM (");
        sql.append(Table.getSelectSQL(table, table.getColumns(), null, null));
        sql.append(") x");
        try
        {
            return Table.executeSingleton(schema.getDbSchema(), sql.getSQL(), sql.getParamsArray(), Integer.class, true);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public int compareTo(ExperimentRunFilter o)
    {
        return _description.compareTo(o.getDescription());
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ExperimentRunFilter that = (ExperimentRunFilter) o;

        return !(_description != null ? !_description.equals(that._description) : that._description != null);
    }

    public int hashCode()
    {
        return (_description != null ? _description.hashCode() : 0);
    }

    public static ExperimentRunFilter getSelectedFilter(String filterName)
    {
        if (filterName == null)
        {
            return null;
        }

        for (ExperimentRunFilter filter : ExperimentService.get().getExperimentRunFilters())
        {
            if (filter.getDescription().equals(filterName))
            {
                return filter;
            }
        }
        return null;
    }

    public void populateButtonBar(ViewContext context, ButtonBar bar, DataView view, ContainerFilter containerFilter)
    {
    }
}
