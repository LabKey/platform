package org.labkey.api.dataiterator;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.DomainProperty;

import java.util.Map;
import java.util.Set;

public class TableInsertUpdateDataIteratorBuilder implements DataIteratorBuilder
{
    final DataIteratorBuilder builder;
    final TableInfo table;
    final Container container;
    Set<String> keyColumns = null;
    Set<String> addlSkipColumns = null;
    Set<String> dontUpdate = null;
    boolean commitRowsBeforeContinuing = false;
    private Set<DomainProperty> vocabularyProperties;
    Map<String, String> remapSchemaColumns = null;

    public TableInsertUpdateDataIteratorBuilder(DataIteratorBuilder data, TableInfo table)
    {
        this(data, table, null);
    }

    public TableInsertUpdateDataIteratorBuilder(DataIteratorBuilder data, TableInfo table, @Nullable Container container)
    {
        this(data, table, container, null);
    }

    /**
     * @param container If container != null, it will be set as a constant in the insert statement.
     */
    public TableInsertUpdateDataIteratorBuilder(DataIteratorBuilder data, TableInfo table, @Nullable Container container, Set<String> dontUpdate)
    {
        this.builder = data;
        this.table = table;
        this.container = container;
        this.dontUpdate = dontUpdate;
    }

    public TableInsertUpdateDataIteratorBuilder setKeyColumns(Set<String> keyColumns)
    {
        this.keyColumns = keyColumns;
        return this;
    }

    public TableInsertUpdateDataIteratorBuilder setAddlSkipColumns(Set<String> addlSkipColumns)
    {
        this.addlSkipColumns = addlSkipColumns;
        return this;
    }

    public TableInsertUpdateDataIteratorBuilder setDontUpdate(Set<String> dontUpdate)
    {
        this.dontUpdate = dontUpdate;
        return this;
    }

    public TableInsertUpdateDataIteratorBuilder setRemapSchemaColumns(Map<String, String> remapSchemaColumns)
    {
        this.remapSchemaColumns = remapSchemaColumns;
        return this;
    }

    /**
     * This option can be used to request that this data iterator does not return rows that are not yet 'in' the database.
     * For instance, this may be important if there is a subsequent insert into a table with an enforced foreign key to this table.
     *
     * This functionality is implemented by using EmbargoDataIterator (with cooperation of StatementDataIterator)
     */
    public TableInsertUpdateDataIteratorBuilder setCommitRowsBeforeContinuing(boolean commitRowsBeforeContinuing)
    {
        this.commitRowsBeforeContinuing = commitRowsBeforeContinuing;
        return this;
    }

    public TableInsertUpdateDataIteratorBuilder setVocabularyProperties(Set<DomainProperty> vocabularyProperties)
    {
        this.vocabularyProperties = vocabularyProperties;
        return this;
    }

    @Override
    public DataIterator getDataIterator(DataIteratorContext context)
    {
        DataIterator di = TableInsertUpdateDataIterator.create(builder, table, container, context, keyColumns, addlSkipColumns,
                dontUpdate, vocabularyProperties, commitRowsBeforeContinuing, remapSchemaColumns);
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
