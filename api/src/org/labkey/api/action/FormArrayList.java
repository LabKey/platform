package org.labkey.api.action;

import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: May 29, 2007
 * Time: 5:14:01 PM
 *
 * Useful List implementation for form beans
 */
public class FormArrayList<T> extends ArrayList<T>
{
    protected int maxSize = 1024;           // defensive programming
    final Class<? extends T> _class;

    public FormArrayList(Class<? extends T> listClass)
    {
        _class = listClass;
    }

    @Override
    public T get(int index)
    {
        try
        {
            if (index >= maxSize)
                throw new IndexOutOfBoundsException();
            while (size() <= index)
                add(null);
            if (null == super.get(index))
                set(index, newInstance());
            return super.get(index);
        }
        catch (InstantiationException x)
        {
            throw new RuntimeException(x);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public T set(int i, T t)
    {
        while (size() < i+1)
            add(null);
        return super.set(i,t);
    }

    protected T newInstance() throws IllegalAccessException, InstantiationException
    {
        return _class.newInstance();
    }

    public void setMaxSize(int max)
    {
        maxSize = max;
    }
}
