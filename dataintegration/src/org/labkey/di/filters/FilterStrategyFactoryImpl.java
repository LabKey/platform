package org.labkey.di.filters;

import org.jetbrains.annotations.Nullable;
import org.labkey.etl.xml.DeletedRowsSourceObjectType;
import org.labkey.etl.xml.FilterType;

/**
 * User: tgaluhn
 * Date: 4/21/2015
 */
public abstract class FilterStrategyFactoryImpl implements FilterStrategy.Factory
{
    protected final DeletedRowsSource _deletedRowsSource;

    public FilterStrategyFactoryImpl(@Nullable FilterType ft)
    {
        if (null == ft || null == ft.getDeletedRowsSource())
            _deletedRowsSource = null;
        else _deletedRowsSource = initDeletedRowsSource(ft.getDeletedRowsSource());
    }

    private DeletedRowsSource initDeletedRowsSource(DeletedRowsSourceObjectType s)
    {
        return new DeletedRowsSource(s.getSchemaName(), s.getQueryName(), s.getTimestampColumnName(), s.getRunColumnName(), s.getDeletedSourceKeyColumnName(), s.getTargetKeyColumnName());
    }
}
