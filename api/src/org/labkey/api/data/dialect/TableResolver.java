/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.data.dialect;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SchemaTableInfoFactory;

import java.sql.SQLException;
import java.util.Map;

/**
 * Created by adam on 8/14/2015.
 */
public interface TableResolver
{
    void addTableInfoFactories(Map<String, SchemaTableInfoFactory> map, DbScope scope, String schemaName) throws SQLException;
    JdbcMetaDataLocator getJdbcMetaDataLocator(DbScope scope) throws SQLException;
    ForeignKeyResolver getForeignKeyResolver(DbScope scope, @Nullable String schemaName, @Nullable String tableName);
}
