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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.FieldKey;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Map;

/**
 * A {@link java.sql.ResultSet} with additional metadata to make it easier to use. Understands the mapping of a
 * {@link org.labkey.api.query.FieldKey} that was part of the query to its value in the result set.
 * User: matthewb
 * Date: Nov 18, 2010
 */
public interface Results extends ResultSet, TableResultSet
{
    ColumnInfo getColumn(int i);

    /** @return the mapping from the originally requested FieldKeys to the ColumnInfos included in this set of results */
    @NotNull
    Map<FieldKey, ColumnInfo> getFieldMap();

    @NotNull
    public Map<FieldKey, Integer> getFieldIndexMap();

    @NotNull
    public Map<FieldKey, Object> getFieldKeyRowMap();

    @Nullable
    ResultSet getResultSet();

    boolean hasColumn(FieldKey key);

    int findColumn(FieldKey key) throws SQLException;

    ColumnInfo findColumnInfo(FieldKey key) throws SQLException;

    String getString(FieldKey f)
            throws SQLException;

    boolean getBoolean(FieldKey f)
            throws SQLException;

    byte getByte(FieldKey f)
            throws SQLException;

    short getShort(FieldKey f)
            throws SQLException;

    int getInt(FieldKey f)
            throws SQLException;

    long getLong(FieldKey f)
            throws SQLException;

    float getFloat(FieldKey f)
            throws SQLException;

    double getDouble(FieldKey f)
            throws SQLException;

    BigDecimal getBigDecimal(FieldKey f, int i)
            throws SQLException;

    byte[] getBytes(FieldKey f)
            throws SQLException;

    Date getDate(FieldKey f)
            throws SQLException;

    Time getTime(FieldKey f)
            throws SQLException;

    Timestamp getTimestamp(FieldKey f)
            throws SQLException;

    InputStream getAsciiStream(FieldKey f)
            throws SQLException;

    InputStream getUnicodeStream(FieldKey f)
            throws SQLException;

    InputStream getBinaryStream(FieldKey f)
            throws SQLException;

    Object getObject(FieldKey f)
            throws SQLException;

    Reader getCharacterStream(FieldKey f)
            throws SQLException;

    BigDecimal getBigDecimal(FieldKey f)
            throws SQLException;
}
