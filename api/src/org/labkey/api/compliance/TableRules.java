/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.compliance;

import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.column.ColumnInfoFilter;
import org.labkey.api.query.column.ColumnInfoTransformer;

import java.util.function.UnaryOperator;

public interface TableRules
{
    TableRules NOOP_TABLE_RULES = new TableRules()
    {
        @Override
        public ColumnInfoFilter getColumnInfoFilter()
        {
            return columnInfo -> true;
        }

        @Override
        public ColumnInfoTransformer getColumnInfoTransformer()
        {
            return columnInfo -> columnInfo;
        }

        @Override
        public UnaryOperator<SQLFragment> getSqlTransformer()
        {
            return sqlFragment -> sqlFragment;
        }
    };

    /**
     * Only add columns to the TableInfo that are allowed by the ColumnInfoFilter.
     */
    ColumnInfoFilter getColumnInfoFilter();

    /**
     * Columns that are allowed by the ColumnInfoFilter will be transformed by
     * the ColumnInfoTransformer.  The transform may return the same column instance or a new column.
     */
    ColumnInfoTransformer getColumnInfoTransformer();

    UnaryOperator<SQLFragment> getSqlTransformer();
}
