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

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;

/**
 * User: jeckels
 * Date: 3/13/13
 */
public class DataIntegrationDbSchema
{
    public static final String SCHEMA_NAME = "dataintegration";
    public enum Columns
    {
        TransformRunId("_txTransformRunId");

        final String _name;

        Columns(String name)
        {
            _name = name;
        }

        String getColumnName()
        {
            return _name;
        }
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
