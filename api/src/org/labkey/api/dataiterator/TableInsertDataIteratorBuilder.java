package org.labkey.api.dataiterator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.DomainProperty;

import java.util.Map;
import java.util.Set;

public class TableInsertDataIteratorBuilder implements DataIteratorBuilder
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
    boolean failOnEmptyUpdate = false;

    public TableInsertDataIteratorBuilder(DataIteratorBuilder data, @NotNull TableInfo table)
    {
        this(data, table, null);
    }

    public TableInsertDataIteratorBuilder(DataIteratorBuilder data, @NotNull TableInfo table, @Nullable Container container)
    {
        this(data, table, container, null);
    }

    /**
     * @param container If container != null, it will be set as a constant in the insert statement.
     */
    public TableInsertDataIteratorBuilder(DataIteratorBuilder data, @NotNull TableInfo table, @Nullable Container container, Set<String> dontUpdate)
    {
        this.builder = data;
        if (table == null)
        {
            throw new IllegalArgumentException("Table cannot be null");
        }
        this.table = table;
        this.container = container;
        this.dontUpdate = dontUpdate;
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

    public TableInsertDataIteratorBuilder setRemapSchemaColumns(Map<String, String> remapSchemaColumns)
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
    public TableInsertDataIteratorBuilder setCommitRowsBeforeContinuing(boolean commitRowsBeforeContinuing)
    {
        this.commitRowsBeforeContinuing = commitRowsBeforeContinuing;
        return this;
    }

    public TableInsertDataIteratorBuilder setVocabularyProperties(Set<DomainProperty> vocabularyProperties)
    {
        this.vocabularyProperties = vocabularyProperties;
        return this;
    }

    public TableInsertDataIteratorBuilder setFailOnEmptyUpdate(boolean failOnEmptyUpdate)
    {
        this.failOnEmptyUpdate = failOnEmptyUpdate;
        return this;
    }

    @Override
    public DataIterator getDataIterator(DataIteratorContext context)
    {
        DataIterator di = TableInsertUpdateDataIterator.create(builder, table, container, context, keyColumns, addlSkipColumns,
                dontUpdate, vocabularyProperties, commitRowsBeforeContinuing, remapSchemaColumns, failOnEmptyUpdate);
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
