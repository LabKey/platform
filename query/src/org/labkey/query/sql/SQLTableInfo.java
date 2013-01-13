/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.UserSchema;


public class SQLTableInfo extends AbstractTableInfo
{
    private SQLFragment _fromSQL;

    public SQLTableInfo(DbSchema schema)
    {
        super(schema);
    }

    protected boolean isCaseSensitive()
    {
        return true;
    }

    @NotNull
    public SQLFragment getFromSQL()
    {
        return _fromSQL;
    }

    public void setFromSQL(SQLFragment sql)
    {
        _fromSQL = sql;
    }

    @Override
    public UserSchema getUserSchema()
    {
        return null;
    }
}

