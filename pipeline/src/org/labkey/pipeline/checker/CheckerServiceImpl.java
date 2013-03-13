package org.labkey.pipeline.checker;

import org.labkey.api.pipeline.checker.Checker;
import org.labkey.api.pipeline.checker.CheckerService;
import org.labkey.api.util.UnexpectedException;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

/**
 * User: jeckels
 * Date: 3/13/13
 * Responsible for initializing and shutting down Quartz
 */
public class CheckerServiceImpl implements CheckerService.Interface
{
    public static CheckerServiceImpl get()
    {
        return (CheckerServiceImpl)CheckerService.get();
    }

    public void startupQuartz()
    {
        try
        {
            StdSchedulerFactory.getDefaultScheduler().start();
        }
        catch (SchedulerException e)
        {
            throw new UnexpectedException(e);
        }
    }

    public void shutdownQuartz()
    {
        try
        {
            StdSchedulerFactory.getDefaultScheduler().shutdown();
        }
        catch (SchedulerException e)
        {
            throw new UnexpectedException(e);
        }
    }

    @Override
    public void schedule(Checker checker, int frequency)
    {

    }

    @Override
    public void cancel(String id)
    {
        throw new UnsupportedOperationException();
    }
}
