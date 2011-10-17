/*
 * Copyright (c) 2011 LabKey Corporation
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

import java.util.Collection;
import java.util.Set;

/*
* User: adam
* Date: Sep 3, 2011
* Time: 3:07:21 PM
*/

// Standard TableSelector that throws checked SQLExceptions instead of RuntimeExceptions.
public class LegacyTableSelector extends LegacySelector
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
        super(new TableSelector(table, Table.ALL_COLUMNS, filter, sort));
    }

    /* TODO: Fix up caching and connection closing, then expose this
    public Results getResults() throws SQLException
    {
        return new ResultsImpl(_selector.getResultSet(), ((TableSelector)_selector).getColumns());
    }
    */
}
