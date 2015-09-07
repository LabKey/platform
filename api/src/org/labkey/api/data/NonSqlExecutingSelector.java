/*
 * Copyright (c) 2014-2015 LabKey Corporation
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

import org.apache.commons.lang3.mutable.MutableLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 7/11/2014
 * Time: 6:51 AM
 */
public abstract class NonSqlExecutingSelector<SELECTOR extends NonSqlExecutingSelector> extends BaseSelector<SELECTOR>
{
    protected NonSqlExecutingSelector(@NotNull DbScope scope, @Nullable Connection conn)
    {
        super(scope, conn);
    }

    @Override
    public long getRowCount()
    {
        final MutableLong count = new MutableLong();

        forEach(rs -> count.increment());

        return count.getValue().longValue();
    }

    @Override
    public boolean exists()
    {
        return handleResultSet(getStandardResultSetFactory(), (rs, conn) -> rs.next()).booleanValue();
    }

    // Different semantics... never grab a new connection
    @Override
    public Connection getConnection() throws SQLException
    {
        return null;
    }
}
