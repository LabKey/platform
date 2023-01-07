package org.labkey.api.util;

import java.util.function.Supplier;

/**
 * Wraps another Supplier, invoking it lazily and caching its results for subsequent calls to get().
 */
public class CachingSupplier<T> implements Supplier<T>
{
    private final Supplier<T> _factory;
    private boolean _invoked = false;
    private T _value;

    public CachingSupplier(Supplier<T> factory)
    {
        _factory = factory;
    }

    @Override
    public T get()
    {
        if (!_invoked)
        {
            _value = _factory.get();
            _invoked = true;
        }
        return _value;
    }
}
