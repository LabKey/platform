/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.data.*;


public class SQLTableInfo extends AbstractTableInfo
{
    SQLFragment _fromSQL;

    public SQLTableInfo(DbSchema schema)
    {
        super(schema);
    }

    protected boolean isCaseSensitive()
    {
        return true;
    }

    public SQLFragment getFromSQL(String aliasName)
    {
        SQLFragment ret = new SQLFragment();
        ret.append("(");
        ret.append(_fromSQL);
        ret.append(") AS " + aliasName);
        return ret;
    }

    public void setFromSQL(SQLFragment sql)
    {
        _fromSQL = sql;
    }
}

