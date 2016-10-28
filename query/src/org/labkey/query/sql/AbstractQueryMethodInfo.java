/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.query.sql;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AbstractMethodInfo;

/**
 * User: matthew
 * Date: 9/26/13
 * Time: 3:31 PM
 */
public abstract class AbstractQueryMethodInfo extends AbstractMethodInfo
{
    AbstractQueryMethodInfo(JdbcType jdbcType)
    {
        super(jdbcType);
    }

    @Override
    final public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
    {
        return getSQL((Query)null, dialect, arguments);
    }

    @Override
    final public SQLFragment getSQL(String tableAlias, DbSchema schema, SQLFragment[] arguments)
    {
        return super.getSQL(tableAlias, schema, arguments);
    }

    abstract public SQLFragment getSQL(Query query, SqlDialect dialect, SQLFragment[] arguments);
}
