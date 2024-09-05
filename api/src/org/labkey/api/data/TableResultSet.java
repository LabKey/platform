/*
 * Copyright (c) 2013-2017 LabKey Corporation
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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;

public interface TableResultSet extends ResultSet, Iterable<Map<String, Object>>
{
    boolean isComplete();

    Map<String, Object> getRowMap() throws SQLException;

    @Override
    @NotNull Iterator<Map<String, Object>> iterator();

    String getTruncationMessage(int maxRows);

    /** @return the number of rows in the result set. -1 if unknown */
    int getSize();

    default int countAll() throws SQLException
    {
        return getSize();
    }

    /** @return the DB connection associated with this ResultSet. May be null if not backed by an active connection.
     * Implementations should prefer to return ConnectionWrapper instead of the underlying connection as it has
     * more context. */
    @Nullable
    Connection getConnection() throws SQLException;

    default <T> T getWrapped(Class<T> clz)
    {
        if (clz.isAssignableFrom(this.getClass()))
            return (T)this;
        return null;
    };
}
