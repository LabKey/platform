/*
 * Copyright (c) 2010-2016 LabKey Corporation
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

package org.labkey.api.data.dialect;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

// Handles standard reading of column meta data
public abstract class ColumnMetaDataReader
{
    protected final ResultSet _rsCols;
    protected String _nameKey, _sqlTypeKey, _sqlTypeNameKey, _scaleKey, _nullableKey, _postionKey, _generatedKey;

    public ColumnMetaDataReader(ResultSet rsCols)
    {
        _rsCols = rsCols;
    }

    public String getName() throws SQLException
    {
        return _rsCols.getString(_nameKey);
    }

    public int getSqlType() throws SQLException
    {
        int sqlType = _rsCols.getInt(_sqlTypeKey);

        if (Types.OTHER == sqlType)
            return Types.NULL;
        else
            return sqlType;
    }

    public String getSqlTypeName() throws SQLException
    {
        return _rsCols.getString(_sqlTypeNameKey);
    }

    public int getScale() throws SQLException
    {
        return _rsCols.getInt(_scaleKey);
    }

    public boolean isNullable() throws SQLException
    {
        return _rsCols.getInt(_nullableKey) == 1;
    }

    public int getPosition() throws SQLException
    {
        return _rsCols.getInt(_postionKey);
    }

    public abstract boolean isAutoIncrement() throws SQLException;

    public @Nullable
    String getLabel() throws SQLException
    {
        return null;
    }

    public @Nullable String getDescription() throws SQLException
    {
        // Default is to put REMARKS into description.  Note: SQL Server has "remarks" column, but it's always empty??
        return StringUtils.trimToNull(_rsCols.getString("REMARKS"));
    }

    public @Nullable String getDatabaseFormat() throws SQLException
    {
        return null;
    }

    // TODO: Implement for other dialects
    public @Nullable String getDefault() throws SQLException
    {
        return null;
    }

    public boolean isGeneratedColumn() throws SQLException
    {
        if (_generatedKey != null)
            return "YES".equals(_rsCols.getString(_generatedKey));

        return false;
    }

}
