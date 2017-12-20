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

        @Override //TODO remove after merge
        public UnaryOperator<SQLFragment> getSqlTransformer()
        {
            return sqlFragment -> sqlFragment;
        }

        @Override
        public UnaryOperator<SQLFragment> getSqlTransformer(boolean hasPhiColumns)
        {
            return sqlFragment -> sqlFragment;
        }
    };

    ColumnInfoFilter getColumnInfoFilter();
    ColumnInfoTransformer getColumnInfoTransformer();

    //TODO remove default keyword for getSqlTransformer(boolean hasPhiColumns) and drop getSqlTransformer() after merge git branch
    UnaryOperator<SQLFragment> getSqlTransformer();
    default UnaryOperator<SQLFragment> getSqlTransformer(boolean hasPhiColumns)
    {
        return sqlFragment -> sqlFragment;
    }
}
