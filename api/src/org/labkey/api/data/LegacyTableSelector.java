/*
 * Copyright (c) 2011-2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;

/**
 * Standard {@link org.labkey.api.data.TableSelector} that throws checked SQLExceptions instead of RuntimeExceptions
 * User: adam
 * Date: Sep 3, 2011
*/

public class LegacyTableSelector extends LegacySelector<TableSelector, LegacyTableSelector>
{
    public LegacyTableSelector(TableInfo table, Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort)
    {
        super(new TableSelector(table, columns, filter, sort));
    }

    public LegacyTableSelector(TableInfo table, Set<String> columnNames, @Nullable Filter filter, @Nullable Sort sort)
    {
        super(new TableSelector(table, columnNames, filter, sort));
    }

    public LegacyTableSelector(TableInfo table, @Nullable Filter filter, @Nullable Sort sort)
    {
        super(new TableSelector(table, filter, sort));
    }

    public LegacyTableSelector(TableInfo table)
    {
        super(new TableSelector(table, null, null));
    }

    public LegacyTableSelector(ColumnInfo column, @Nullable Filter filter, @Nullable Sort sort)
    {
        super(new TableSelector(column, filter, sort));
    }

    @Override
    protected LegacyTableSelector getThis()
    {
        return this;
    }

    public LegacyTableSelector setForDisplay(boolean forDisplay)
    {
        _selector.setForDisplay(forDisplay);
        return getThis();
    }

    public Results getResults() throws SQLException
    {
        return _selector.getResults();
    }

    public Results getResults(boolean cache, boolean scrollable) throws SQLException
    {
        return _selector.getResults(cache, scrollable);
    }

    public Results getResultsAsync(boolean cache, boolean scrollable, HttpServletResponse response) throws SQLException, IOException
    {
        return _selector.getResultsAsync(cache, scrollable, response);
    }
}
