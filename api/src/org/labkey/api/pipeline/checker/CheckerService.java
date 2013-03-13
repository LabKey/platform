package org.labkey.api.pipeline.checker;

import org.labkey.api.services.ServiceRegistry;

/**
 * User: jeckels
 * Date: 3/13/13
 */
public class CheckerService
{
    public static Interface get()
    {
        return ServiceRegistry.get(CheckerService.Interface.class);
    }

    public static interface Interface
    {
        public void schedule(Checker checker, int frequency);

        public void cancel(String id);
    }
}
