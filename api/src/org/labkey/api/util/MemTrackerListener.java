package org.labkey.api.util;

import java.util.Set;

/**
 * User: adam
 * Date: 4/13/12
 * Time: 11:12 PM
 */
public interface MemTrackerListener
{
    // Called before GC and tallying of held objects. Implementors should purge held objects and (optionally) add
    // objects to the passed in setthat should be ignored .
    public void beforeReport(Set<Object> set);
}
