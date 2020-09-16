/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.query.ExpProtocolApplicationTable;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.QueryRowReference;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Objects;
import java.util.Set;

/**
 * Provides some basic stats and recognition for experiment runs of a particular type.
 *
 * User: jeckels
 * Date: Sep 25, 2006
 */
public abstract class ExperimentRunType implements Comparable<ExperimentRunType>, ExperimentProtocolHandler
{
    public static final ExperimentRunType ALL_RUNS_TYPE = new ExperimentRunType("All Runs", ExpSchema.SCHEMA_EXP, ExpSchema.TableType.Runs.toString())
    {
        @Override
        public Priority getPriority(ExpProtocol protocol)
        {
            if (protocol.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRun)
                return Priority.LOW;

            return null;
        }
    };

    private final String _description;
    private final SchemaKey _schemaName;
    private final String _tableName;

    public ExperimentRunType(String description, String schemaName, String tableName)
    {
        this(description, SchemaKey.fromString(schemaName), tableName);
    }

    public ExperimentRunType(String description, SchemaKey schemaName, String tableName)
    {
        _description = description;
        _schemaName = schemaName;
        _tableName = tableName;
    }

    public String getDescription()
    {
        return _description;
    }

    public SchemaKey getSchemaName()
    {
        return _schemaName;
    }

    public String getTableName()
    {
        return _tableName;
    }

    /**
     * Reference to the row that represents the protocol for this run type.
     */
    @Override
    @Nullable
    public QueryRowReference getQueryRowReference(ExpProtocol protocol)
    {
        return new QueryRowReference(protocol.getContainer(), ExpSchema.SCHEMA_EXP, ExpSchema.TableType.Protocols.name(), ExpProtocolApplicationTable.Column.RowId, protocol.getRowId());
    }

    /**
     * Reference to the row that represents the run of for this run type.
     */
    @Override
    @Nullable
    public QueryRowReference getQueryRowReference(ExpProtocol protocol, ExpRun run)
    {
        return new QueryRowReference(run.getContainer(), SchemaKey.fromParts(_schemaName), _tableName, ExpRunTable.Column.RowId, run.getRowId());
    }

    /**
     * Reference to the row that represents the protocol application for this run type.
     */
    @Override
    @Nullable
    public QueryRowReference getQueryRowReference(ExpProtocol protocol, ExpProtocolApplication app)
    {
        return new QueryRowReference(app.getContainer(), ExpSchema.SCHEMA_EXP, ExpSchema.TableType.ProtocolApplications.name(), ExpProtocolApplicationTable.Column.RowId, app.getRowId());
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

    @Override
    public int compareTo(ExperimentRunType o)
    {
        return _description.compareTo(o.getDescription());
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ExperimentRunType that = (ExperimentRunType) o;

        return Objects.equals(_description, that._description);
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
