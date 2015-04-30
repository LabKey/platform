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
package org.labkey.di.filters;

import java.io.Serializable;

/**
 * User: tgaluhn
 * Date: 4/21/2015
 */
public class DeletedRowsSource implements Serializable
{
    private String _schemaName;
    private String _queryName;
    private String _timestampColumnName;
    private String _runColumnName;
    private String _deletedSourceKeyColumnName;
    private String _targetKeyColumnName;

    public DeletedRowsSource(String schemaName, String queryName, String timestampColumnName, String runColumnName, String deletedSourceKeyColumnName, String targetKeyColumnName)
    {
        _schemaName = schemaName;
        _queryName = queryName;
        _timestampColumnName = timestampColumnName;
        _runColumnName = runColumnName;
        _deletedSourceKeyColumnName = deletedSourceKeyColumnName;
        _targetKeyColumnName = targetKeyColumnName;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public String getTimestampColumnName()
    {
        return _timestampColumnName;
    }

    public String getRunColumnName()
    {
        return _runColumnName;
    }

    public String getDeletedSourceKeyColumnName()
    {
        return _deletedSourceKeyColumnName;
    }

    public String getTargetKeyColumnName()
    {
        return _targetKeyColumnName;
    }
}
