package org.labkey.api.dataiterator;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;

import java.util.Set;

public class TableInsertDataIteratorBuilder implements DataIteratorBuilder
{
    final DataIteratorBuilder builder;
    final TableInfo table;
    final Container container;     // If container != null, it will be set as a constant in the insert statement
    Set<String> keyColumns = null;
    Set<String> addlSkipColumns = null;
    Set<String> dontUpdate = null;
    boolean commitRowsBeforeContinuing = false;

    public TableInsertDataIteratorBuilder(DataIteratorBuilder data, TableInfo table, Container c)
    {
        this.builder = data;
        this.table = table;
        this.container = c;
    }

    public TableInsertDataIteratorBuilder setKeyColumns(Set<String> keyColumns)
    {
        this.keyColumns = keyColumns;
        return this;
    }

    public TableInsertDataIteratorBuilder setAddlSkipColumns(Set<String> addlSkipColumns)
    {
        this.addlSkipColumns = addlSkipColumns;
        return this;
    }

    public TableInsertDataIteratorBuilder setDontUpdate(Set<String> dontUpdate)
    {
        this.dontUpdate = dontUpdate;
        return this;
    }

    public TableInsertDataIteratorBuilder setCommitRowsBeforeContinuing(boolean commitRowsBeforeContinuing)
    {
        this.commitRowsBeforeContinuing = commitRowsBeforeContinuing;
        return this;
    }

    @Override
    public DataIterator getDataIterator(DataIteratorContext context)
    {
        DataIterator di = TableInsertDataIterator.create(builder, table, container, context, keyColumns, addlSkipColumns, dontUpdate, commitRowsBeforeContinuing);
        if (null == di)
        {
            //noinspection ThrowableNotThrown
            if (!context.getErrors().hasErrors())
                throw new NullPointerException("getDataIterator() returned NULL");
            return null;
        }
        return di;
    }
}
