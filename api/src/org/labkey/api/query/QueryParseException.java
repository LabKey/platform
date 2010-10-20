/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.NullColumnInfo;
import org.labkey.api.data.TableInfo;

public class QueryParseException extends QueryException
{
    int _line;
    int _column;

    public QueryParseException(String message, Throwable cause, int line, int column)
    {
        super(message, cause);
        _line = line;
        _column = column;
    }

    public QueryParseException(String queryName, QueryParseException other)
    {
        super(queryName + ":" + other.getMessage(), other.getCause());
        _line = other._line;
        _column = other._column;
    }

    public String getMessage()
    {
        String ret = super.getMessage();
        if (_line != 0)
        {
            ret = "Error on line " + _line + ":" + ret;
        }
        return ret;
    }

    public int getLine()
    {
        return _line;
    }

    public int getColumn()
    {
        return _column;
    }

    public ColumnInfo makeErrorColumnInfo(TableInfo parent, FieldKey key)
    {
        NullColumnInfo col = new NullColumnInfo(parent, key, "VARCHAR");
        col.setLabel("#ERROR: " + key.getDisplayString() + " " + getMessage());
        return col;
    }
}
