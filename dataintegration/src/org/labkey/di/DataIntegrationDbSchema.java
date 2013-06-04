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
package org.labkey.di;

import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.HashSet;
import java.util.Set;

/**
 * User: jeckels
 * Date: 3/13/13
 */
public class DataIntegrationDbSchema extends UserSchema
{
    public static final String SCHEMA_NAME = "dataintegration";
    public static final String SCHEMA_DESCRIPTION = "Contains data for ETL Transformations";

    public enum Columns
    {
        TransformRunId("diTransformRunId"),
        TransformModified("diModified");

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

    public DataIntegrationDbSchema(User user, Container container)
    {
        super(SCHEMA_NAME, SCHEMA_DESCRIPTION, user, container, getSchema());
    }

    @Override
    public Set<String> getTableNames()
    {
        return new HashSet<String>(getDbSchema().getTableNames());
    }

    @Override
    protected TableInfo createTable(String name)
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
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new DataIntegrationDbSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public static DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }


    public static TableInfo getTransformRunTableInfo()
    {
        return getSchema().getTable("transformrun");
    }


    public static TableInfo getTranformConfigurationTableInfo()
    {
        return getSchema().getTable("transformconfiguration");
    }
}
