package org.labkey.study.model;

import org.labkey.api.util.MemTracker;

/**
 * User: brittp
 * Date: Feb 10, 2006
 * Time: 2:33:38 PM
 */
public abstract class AbstractStudyCachable<T> implements StudyCachable<T>, Cloneable
{
    private boolean _mutable = true;

    public AbstractStudyCachable()
    {
        MemTracker.put(this);
    }

    protected void verifyMutability()
    {
        if (!_mutable)
            throw new IllegalStateException("Cached objects are immutable; createMutable must be called first.");
    }

    public boolean isMutable()
    {
        return _mutable;
    }

    protected void unlock()
    {
        _mutable = true;
    }

    public void lock()
    {
        _mutable = false;
    }


    public T createMutable()
    {
        try
        {
            T obj = (T) clone();
            ((AbstractStudyCachable) obj).unlock();
            return obj;
        }
        catch (CloneNotSupportedException e)
        {
            throw new RuntimeException(e);
        }
    }
}
