/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.migration;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.WrappedColumn;
import org.labkey.api.util.GUID;

// In this column, map any value that exactly matches a GUID to lowercase
public final class GuidMapperColumn extends WrappedColumn
{
    public GuidMapperColumn(ColumnInfo col)
    {
        super(col, col.getName());
    }

    @Override
    public SQLFragment getValueSql(String tableAlias)
    {
        SQLFragment columnAlias = super.getValueSql(tableAlias);
        //noinspection StringConcatenationInsideStringBufferAppend - SQLFragment flips out about unmatched quotes, so we're forced to use string concatenation
        return new SQLFragment("CASE WHEN ")
            .append(columnAlias)
            .append(" LIKE '" + GUID.SQL_LIKE_GUID_PATTERN + "'")
            .append(" THEN LOWER(")
            .append(columnAlias)
            .append(") ELSE ")
            .append(columnAlias)
            .append(" END");
    }
}
