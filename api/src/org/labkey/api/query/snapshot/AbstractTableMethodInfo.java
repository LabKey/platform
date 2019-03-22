/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
package org.labkey.api.query.snapshot;

import org.labkey.api.data.JdbcType;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AbstractMethodInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.DbSchema;

/**
 * User: Matthew
 * Date: Mar 23, 2009
 * Time: 9:29:03 AM
 */
public abstract class AbstractTableMethodInfo extends AbstractMethodInfo
{
    protected AbstractTableMethodInfo(JdbcType jdbcType)
    {
        super(jdbcType);
    }
    
    public final SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
    {
        throw new IllegalStateException("Table name required for this method");
    }
}
