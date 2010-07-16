package org.labkey.api.cache;

/**
 * User: adam
 * Date: Jun 20, 2010
 * Time: 10:07:01 AM
 */

// A thread-safe Cache implementation
public interface Cache<K, V> extends BasicCache<K, V>, Tracking
{
}
