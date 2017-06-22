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
package org.labkey.di;

import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.HashSet;
import java.util.Set;

/**
 * User: jeckels
 * Date: 3/13/13
 */
public class DataIntegrationQuerySchema extends UserSchema
{
    public static final String SCHEMA_NAME = "dataintegration";
    public static final String SCHEMA_DESCRIPTION = "Contains data for ETL Transformations";
    // list of each transform type with last run information
    public static final String TRANSFORMSUMMARY_TABLE_NAME = "TransformSummary";
    // history of a specific transform type ordered by newest run first
    public static final String TRANSFORMHISTORY_TABLE_NAME = "TransformHistory";
    // all transform runs
    public static final String TRANSFORMRUN_TABLE_NAME = "TransformRun";

    public enum Columns
    {
        TransformRunId("diTransformRunId"),
        TransformModified("diModified"),
        TransformModifiedBy("diModifiedBy"),
        TransformRowVersion("diRowVersion"),
        TransformCreated("diCreated"),
        TransformCreatedBy("diCreatedBy");

        final String _name;

        Columns(String name)
        {
            _name = name;
        }

        public String getColumnName()
        {
            return _name;
        }
    }

    public DataIntegrationQuerySchema(User user, Container container)
    {
        super(SCHEMA_NAME, SCHEMA_DESCRIPTION, user, container, getSchema());
    }

    @Override
    public Set<String> getTableNames()
    {
        HashSet<String> tableNames = new HashSet<>(getDbSchema().getTableNames());
        tableNames.add(TRANSFORMHISTORY_TABLE_NAME);
        tableNames.add(TRANSFORMSUMMARY_TABLE_NAME);
        return tableNames;
    }

    @Override
    public TableInfo createTable(String name)
    {
        if (TRANSFORMHISTORY_TABLE_NAME.equalsIgnoreCase(name))
            return new TransformHistoryTable(this);

        if (TRANSFORMSUMMARY_TABLE_NAME.equalsIgnoreCase(name))
            return new TransformSummaryTable(this);

        return getSchemaTable(name);
    }

    @Override
    public QueryView createView(ViewContext context, QuerySettings settings, BindException errors)
    {
        if (TRANSFORMHISTORY_TABLE_NAME.equalsIgnoreCase(settings.getQueryName()))
            return new TransformHistoryView(this, settings);

        return super.createView(context, settings, errors);
    }


    private TableInfo getSchemaTable(String name)
    {
        SchemaTableInfo tinfo = getDbSchema().getTable(name);
        if (null == tinfo)
            return null;

        FilteredTable ftable = new FilteredTable(tinfo, this);
        ftable.wrapAllColumns(true);
        return ftable;
    }

    public static void register(final DataIntegrationModule module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module)
        {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                return true;
            }

            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new DataIntegrationQuerySchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public static DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME, DbSchemaType.Module);
    }


    public static TableInfo getTransformRunTableInfo()
    {
        return getSchema().getTable("transformrun");
    }


    public static TableInfo getTranformConfigurationTableInfo()
    {
        return getSchema().getTable("transformconfiguration");
    }

    public static String getTransformRunTableName()
    {
        return SCHEMA_NAME + "." + TRANSFORMRUN_TABLE_NAME;
    }
}
