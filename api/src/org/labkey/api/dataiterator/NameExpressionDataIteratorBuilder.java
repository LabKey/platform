/*
 * Copyright (c) 2018-2026 LabKey Corporation
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
package org.labkey.api.dataiterator;

import org.labkey.api.data.TableInfo;

public class NameExpressionDataIteratorBuilder implements DataIteratorBuilder
{
    final DataIteratorBuilder _pre;
    private final TableInfo _parentTable;
    private final String _nameColumnName;
    private final String _nameExpressionColumnName;

    public NameExpressionDataIteratorBuilder(DataIteratorBuilder pre, TableInfo parentTable)
    {
        this(pre, parentTable, "name", "nameExpression");
    }

    public NameExpressionDataIteratorBuilder(DataIteratorBuilder pre, TableInfo parentTable, String nameColumn, String nameExpressionColumn)
    {
        _pre = pre;
        _parentTable = parentTable;
        _nameColumnName = nameColumn;
        _nameExpressionColumnName = nameExpressionColumn;
    }

    @Override
    public DataIterator getDataIterator(DataIteratorContext context)
    {
        DataIterator pre = _pre.getDataIterator(context);
        return LoggingDataIterator.wrap(new NameExpressionDataIterator(pre, context, _parentTable, null, null, null, null, _nameColumnName, _nameExpressionColumnName));
    }
}
