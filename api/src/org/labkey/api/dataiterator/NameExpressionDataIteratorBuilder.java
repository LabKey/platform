package org.labkey.api.dataiterator;

import org.labkey.api.data.TableInfo;

public class NameExpressionDataIteratorBuilder implements DataIteratorBuilder
{
    final DataIteratorBuilder _pre;
    private final String _nameExpression;
    private final TableInfo _parentTable;

    public NameExpressionDataIteratorBuilder(DataIteratorBuilder pre, String nameExpression, TableInfo parentTable)
    {
        _pre = pre;
        _nameExpression = nameExpression;
        _parentTable = parentTable;
    }

    @Override
    public DataIterator getDataIterator(DataIteratorContext context)
    {
        DataIterator pre = _pre.getDataIterator(context);
        return LoggingDataIterator.wrap(new NameExpressionDataIterator(pre, context, _nameExpression, _parentTable));
    }
}
