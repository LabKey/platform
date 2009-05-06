package org.labkey.api.reader;

import org.labkey.api.util.CloseableIterator;
import org.labkey.api.util.Filter;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

/**
 * User: adam
 * Date: May 5, 2009
 * Time: 7:27:47 PM
 */
public class BeanTabLoader<T> extends AbstractTabLoader<T>
{
    private Filter<T> _beanFilter = null;
    private final Class<T> _clazz;

    // Infer whether there are headers
    public BeanTabLoader(Class<T> clazz, File inputFile) throws IOException
    {
        _clazz = clazz;
        setSource(inputFile);
    }

    public BeanTabLoader(Class<T> clazz, File inputFile, boolean hasColumnHeaders) throws IOException
    {
        _clazz = clazz;
        setSource(inputFile);
        setHasColumnHeaders(hasColumnHeaders);
    }

    public BeanTabLoader(Class<T> clazz, Reader reader, boolean hasColumnHeaders)
    {
        _clazz = clazz;
        setSource(reader);
        setHasColumnHeaders(hasColumnHeaders);
    }

    public BeanTabLoader(Class<T> clazz, String src, boolean hasColumnHeaders) throws IOException
    {
        _clazz = clazz;
        setSource(src);
        setHasColumnHeaders(hasColumnHeaders);
    }

    public void setBeanFilter(Filter<T> beanFilter)
    {
        _beanFilter = beanFilter;
    }

    public CloseableIterator<T> iterator()
    {
        if (null == _beanFilter)
            return new BeanIterator<T>(mapIterator(), _clazz);
        else
            return new CloseableFilterIterator<T>(new BeanIterator<T>(mapIterator(), _clazz), _beanFilter);
    }
}
