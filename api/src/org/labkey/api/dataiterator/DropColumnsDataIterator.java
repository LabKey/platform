/*
 * Copyright (c) 2023-2026 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;

import java.util.Set;
import java.util.function.Supplier;

/*
 * See SimpleTranslator.selectAll(@NotNull Set<String> skipColumns) for similar functionality, but SampleTranslator
 * copies data, this is straight pass through.
 */
public class DropColumnsDataIterator extends WrapperDataIterator
{
    int[] indexMap;
    int columnCount = 0;

    public DropColumnsDataIterator(DataIterator di, Set<String> drop)
    {
        super(di);
        int inputColumnCount = di.getColumnCount();
        indexMap = new int[inputColumnCount+1];
        indexMap[0] = 0;    // just pass through virtual _rowNumber column
        for (int inIndex = 1; inIndex <= inputColumnCount; inIndex++)
        {
            String name = di.getColumnInfo(inIndex).getName();
            if (!drop.contains(name))
            {
                indexMap[++columnCount] = inIndex;
            }
        }
    }

    @Override
    public int getColumnCount()
    {
        return columnCount;
    }

    @Override
    public Object get(int i)
    {
        return super.get(indexMap[i]);
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        return super.getColumnInfo(indexMap[i]);
    }

    @Override
    public Object getConstantValue(int i)
    {
        return super.getConstantValue(indexMap[i]);
    }

    @Override
    public Supplier<Object> getSupplier(int i)
    {
        return _delegate.getSupplier(indexMap[i]);
    }
}