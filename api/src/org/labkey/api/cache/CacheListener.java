package org.labkey.api.cache;

public interface CacheListener
{
    /**
     * Notifiction that an admin has clicked the 'clear caches' link on the admin console's memory page.
     */
    void clearCaches();
}
