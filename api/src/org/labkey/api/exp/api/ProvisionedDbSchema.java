/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.api.exp.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SchemaTableInfoFactory;
import org.labkey.api.data.StandardSchemaTableInfoFactory;
import org.labkey.api.data.dialect.JdbcMetaDataLocator;

import java.sql.SQLException;
import java.util.Collection;

/**
 * A schema in the underlying database that is populated by tables created (provisioned) dynamically
 * based on administrator or other input into what columns/fields should be tracked.
 * Created by klum on 2/23/14.
 */
public class ProvisionedDbSchema extends DbSchema
{
    public ProvisionedDbSchema(String name, DbScope scope)
    {
        super(name, DbSchemaType.Provisioned, scope, null, null);
    }

    @Override
    public Collection<String> getTableNames()
    {
        throw new IllegalStateException("Should not be requesting table names from provisioned schema \"" + getName() + "\"");
    }

    @Nullable
    @Override
    public SchemaTableInfo createTableFromDatabaseMetaData(String requestedTableName) throws SQLException
    {
        try (JdbcMetaDataLocator locator = getSqlDialect().getJdbcMetaDataLocator(getScope(), getName(), requestedTableName))
        {
            return new SingleTableMetaDataLoader(this, locator, DbSchema.getTemp() != this).load();
        }
    }


    protected static class SingleTableMetaDataLoader extends TableMetaDataLoader<SchemaTableInfo>
    {
        private final DbSchema _schema;
        private SchemaTableInfo _ti = null;

        public SingleTableMetaDataLoader(DbSchema schema, JdbcMetaDataLocator locator, boolean ignoreTemp)
        {
            super(locator, ignoreTemp);
            _schema = schema;
        }

        @Override
        protected void handleTable(String tableName, DatabaseTableType tableType, String description) throws SQLException
        {
            SchemaTableInfoFactory factory = new StandardSchemaTableInfoFactory(tableName, tableType, description);
            _ti = factory.getSchemaTableInfo(_schema);
        }

        @Override
        protected SchemaTableInfo getReturnValue()
        {
            return _ti;
        }
    }
}
