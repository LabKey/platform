package org.labkey.api.util;

/**
 * Interface for objects for which allocations should be tracked on a per-request basis. Instances should be registered
 * using MemTracker at time of construction.
 * User: jeckels
 * Date: 1/16/14
 */
public interface MemTrackable
{
    public String toMemTrackerString();
}
