package org.labkey.study.model;

import org.labkey.api.data.Container;

/**
 * User: brittp
 * Date: Feb 10, 2006
 * Time: 2:39:32 PM
 */
public interface StudyCachable<T>
{
    T createMutable();
    void lock();
    Container getContainer();
    Object getPrimaryKey();
}
