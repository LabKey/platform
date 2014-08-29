/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
package org.labkey.api.data;

import org.labkey.api.query.FieldKey;

/**
 * Implementation that returns an appropriately typed NULL value
 *
 * User: matthewb
 * Date: Sep 30, 2010
 */

public class NullColumnInfo extends ColumnInfo
{
    public NullColumnInfo(TableInfo parent, FieldKey name, String sqlType)
    {
        super(name, parent);
        setSqlTypeName(sqlType);
        setReadOnly(true);
    }

    public NullColumnInfo(TableInfo parent, FieldKey name, JdbcType jdbcType)
    {
        super(name, parent);
        setJdbcType(jdbcType);
        setReadOnly(true);
    }

    public NullColumnInfo(TableInfo parent, String name, String sqlType)
    {
        super(name, parent);
        setSqlTypeName(sqlType);
        setReadOnly(true);
    }

    public NullColumnInfo(TableInfo parent, String name, JdbcType jdbcType)
    {
        super(name, parent);
        setJdbcType(jdbcType);
        setReadOnly(true);
    }

    @Override
    public SQLFragment getValueSql(String tableAliasName)
    {
        return new SQLFragment(nullValue(getSqlTypeName()));
    }

    public static String nullValue(String typeName)
    {
        if (null == typeName)
            return "NULL";
        else
            return "CAST(NULL AS " + typeName + ")";
    }
}


