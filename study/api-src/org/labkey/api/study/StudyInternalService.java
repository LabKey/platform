package org.labkey.api.study;

import org.labkey.api.data.Container;
import org.labkey.api.services.ServiceRegistry;

/**
 * Provides access to internal study methods for modules that depend on study and are aware of study internals
 * (for example, specimen)
 */
public interface StudyInternalService
{
    static StudyInternalService get()
    {
        return ServiceRegistry.get().getService(StudyInternalService.class);
    }

    static void setInstance(StudyInternalService impl)
    {
        ServiceRegistry.get().registerService(StudyInternalService.class, impl);
    }

    /**
     * Clears all the study caches in this container plus those of any associated ancillary/published studies. Does not
     * clear caches associated with datasets.
     * @param container The study container where cache clearing will take place
     */
    void clearCaches(Container container);
}
