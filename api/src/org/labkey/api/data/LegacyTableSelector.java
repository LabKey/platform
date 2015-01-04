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

import java.util.Collection;
import java.util.Set;

/**
 * Standard {@link org.labkey.api.data.TableSelector} that throws checked SQLExceptions instead of RuntimeExceptions
 * User: adam
 * Date: Sep 3, 2011
*/

class LegacyTableSelector extends LegacySelector<TableSelector, LegacyTableSelector>
{
    LegacyTableSelector(TableInfo table, Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort)
    {
        super(new TableSelector(table, columns, filter, sort));
    }

    LegacyTableSelector(TableInfo table, Set<String> columnNames, @Nullable Filter filter, @Nullable Sort sort)
    {
        super(new TableSelector(table, columnNames, filter, sort));
    }

    LegacyTableSelector(TableInfo table, @Nullable Filter filter, @Nullable Sort sort)
    {
        super(new TableSelector(table, filter, sort));
    }

    LegacyTableSelector(ColumnInfo column, @Nullable Filter filter, @Nullable Sort sort)
    {
        super(new TableSelector(column, filter, sort));
    }

    @Override
    protected LegacyTableSelector getThis()
    {
        return this;
    }

    LegacyTableSelector setForDisplay(boolean forDisplay)
    {
        _selector.setForDisplay(forDisplay);
        return getThis();
    }

    Results getResults()
    {
        return _selector.getResults();
    }
}
