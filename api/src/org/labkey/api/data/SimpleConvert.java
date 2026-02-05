package org.labkey.api.data;

import org.apache.commons.beanutils.ConversionException;

/** Implementing classes must implement convert() or getConvertFn() */
public interface SimpleConvert
{
    Object convert(Object val) throws ConversionException;

    /**
     * Some implementations may be able to pass-through or precompute a SimpleConvert, and it may
     * be faster to cache and use that implementation.  In a loop or dataiterator it may be faster to
     * use getConvertFn() e.g.
     *      var fn = col.getConvertFn();
     *      while (...)
     *          var converted = fn.convert(val);
     * <p></p>
     * instead of
     *      while(...)
     *          var converted = col.convert(val);
     */
    default SimpleConvert getConvertFn()
    {
        return this;
    }
}