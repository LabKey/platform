package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpression;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A foreign key implementation that waits until it's asked for something and then uses the Supplier to create the
 * real foreign key. This lets us postpone potentially expensive initialization of the lookup, and in many cases
 * avoid needing to do it at all if the lookup isn't being actively used in a given codepath.
 */
public class LazyForeignKey implements ForeignKey
{
    private final Supplier<ForeignKey> _supplier;
    private final boolean _suggestColumns;

    private boolean _delegateCreationAttempted = false;
    private ForeignKey _delegate;

    public LazyForeignKey(Supplier<ForeignKey> supplier, boolean suggestColumns)
    {
        _supplier = supplier;
        _suggestColumns = suggestColumns;
    }

    private ForeignKey getDelegate()
    {
        if (!_delegateCreationAttempted)
        {
            _delegate = _supplier.get();
            _delegateCreationAttempted = true;
        }
        return _delegate;
    }

    @Override
    public @Nullable ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        return getDelegate() == null ? null : getDelegate().createLookupColumn(parent, displayField);
    }

    @Override
    public @Nullable TableInfo getLookupTableInfo()
    {
        return getDelegate() == null ? null : getDelegate().getLookupTableInfo();
    }

    @Override
    public StringExpression getURL(ColumnInfo parent)
    {
        return getDelegate() == null ? null : getDelegate().getURL(parent);
    }

    @Override
    public @NotNull NamedObjectList getSelectList(RenderContext ctx)
    {
        return getDelegate() == null ? new NamedObjectList() : getDelegate().getSelectList(ctx);
    }

    @Override
    public Container getLookupContainer()
    {
        return getDelegate() == null ? null : getDelegate().getLookupContainer();
    }

    @Override
    public String getLookupTableName()
    {
        return getDelegate() == null ? null : getDelegate().getLookupTableName();
    }

    @Override
    public String getLookupSchemaName()
    {
        return getDelegate() == null ? null : getDelegate().getLookupSchemaName();
    }

    @Override
    public String getLookupColumnName()
    {
        return getDelegate() == null ? null : getDelegate().getLookupColumnName();
    }

    @Override
    public String getLookupDisplayName()
    {
        return getDelegate() == null ? null : getDelegate().getLookupDisplayName();
    }

    @Override
    public @Nullable ForeignKey remapFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> mapping)
    {
        return getDelegate() == null ? null : getDelegate().remapFieldKeys(parent, mapping);
    }

    @Override
    public @Nullable Set<FieldKey> getSuggestedColumns()
    {
        if (!_suggestColumns)
        {
            return null;
        }
        return getDelegate() == null ? null : getDelegate().getSuggestedColumns();
    }
}
