/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

import org.labkey.api.data.dialect.SqlDialect;

/**
 * A method on a table.
 * Most tables do not have methods.  Methods are often used to expose lookup columns, where the lookup column
 * name is not known at query design time.
 * It's unfortunate that both the method "getSQL" and "createColumnInfo" need to exist.
 *
 */
public interface MethodInfo
{
    /**
     * Return a {@link ColumnInfo} whose {@link ColumnInfo#getValueSql } will be the result of evaluating the method.
     */
    ColumnInfo createColumnInfo(TableInfo parentTable, ColumnInfo[] arguments, String alias);

    SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments);

    // for table methods
    SQLFragment getSQL(String tableAlias, DbSchema schema, SQLFragment[] arguments);

    JdbcType getJdbcType(JdbcType[] args);
}
