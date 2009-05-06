package org.labkey.api.reader;

import org.labkey.api.util.CloseableIterator;
import org.labkey.api.data.ObjectFactory;

import java.util.Map;
import java.io.IOException;

/**
* User: adam
* Date: May 5, 2009
* Time: 7:35:51 PM
* To change this template use File | Settings | File Templates.
*/

// Iterator that transforms Map<String, Object> to bean using ObjectFactory
public class BeanIterator<T> implements CloseableIterator<T>
{
    private CloseableIterator<Map<String, Object>> _mapIter;
    private ObjectFactory<T> _factory;

    public BeanIterator(CloseableIterator<Map<String, Object>> mapIter, Class<T> clazz)
    {
        _mapIter = mapIter;
        _factory = ObjectFactory.Registry.getFactory(clazz);
    }

    public void close() throws IOException
    {
        _mapIter.close();
    }

    public boolean hasNext()
    {
        return _mapIter.hasNext();
    }

    public T next()
    {
        Map<String, Object> row = _mapIter.next();
        return _factory.fromMap(row);
    }

    public void remove()
    {
        _mapIter.remove();
    }
}
