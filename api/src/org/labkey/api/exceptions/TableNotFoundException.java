/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.exceptions;

/**
 * An IllegalStateException that provides a bit more context. Callers that are particularly susceptible to table and
 * container delete race conditions (e.g., background tasks) can catch this specific exception, then suppress or retry.
 *
 * Created by adam on 1/17/2017.
 */
public class TableNotFoundException extends IllegalStateException
{
    private final String _schemaName;
    private final String _tableName;

    public TableNotFoundException(String schemaName, String tableName)
    {
        super("Table not found (deleted? race condition?)");

        _schemaName = schemaName;
        _tableName = tableName;
    }

    public String getFullName()
    {
        return getSchemaName() + "." + getTableName();
    }

    @Override
    public String getMessage()
    {
        return super.getMessage() + ": " + getFullName();
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public String getTableName()
    {
        return _tableName;
    }
}
