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
        for (int inIndex = 0; inIndex <= inputColumnCount; inIndex++)
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