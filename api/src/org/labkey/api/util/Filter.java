package org.labkey.api.util;

/**
 * User: adam
 * Date: Apr 10, 2009
 * Time: 9:05:45 AM
 */
public interface Filter<T>
{
    boolean accept(T object);
}
