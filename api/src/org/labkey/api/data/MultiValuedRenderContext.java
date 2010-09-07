package org.labkey.api.data;

import org.apache.commons.collections15.iterators.ArrayIterator;
import org.labkey.api.query.FieldKey;

import java.util.Iterator;

/**
 * User: adam
 * Date: Sep 7, 2010
 * Time: 10:02:25 AM
 */
public class MultiValuedRenderContext extends RenderContextDecorator
{
    private final FieldKey _fieldKey;
    private final Iterator<String> _iter;
    private String _currentValue = null;

    public MultiValuedRenderContext(RenderContext ctx, FieldKey fieldKey)
    {
        super(ctx);
        String valueString = (String)ctx.get(fieldKey);
        String[] values = valueString.split(",");
        _fieldKey = fieldKey;
        _iter = new ArrayIterator<String>(values);
    }

    // Advance the iterator, if possible
    public boolean next()
    {
        boolean hasNext = _iter.hasNext();

        if (hasNext)
            _currentValue = _iter.next();

        return hasNext;
    }

    @Override
    public Object get(Object key)
    {
        if (key.equals((_fieldKey)))
        {
            return _currentValue;
        }
        else
        {
            return super.get(key);
        }
    }
}
