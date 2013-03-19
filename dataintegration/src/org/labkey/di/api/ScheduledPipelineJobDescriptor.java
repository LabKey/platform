package org.labkey.di.api;

import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.security.User;
import org.quartz.JobExecutionException;
import org.quartz.ScheduleBuilder;

import java.util.concurrent.Callable;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 2013-03-18
 * Time: 3:21 PM
 */

public interface ScheduledPipelineJobDescriptor<C extends ScheduledPipelineJobContext>
{
    String getId();     // globally unique id (perhaps a path)
    String getName();
    String getDescription();
    String getModuleName();
    int getVersion();

    public ScheduleBuilder getScheduleBuilder();
    public String getScheduleDescription();

    Class<? extends org.quartz.Job> getJobClass();
    C getJobContext(Container c, User user);
    Callable<Boolean> getChecker(C context);
    PipelineJob getPipelineJob(C context) throws JobExecutionException;
}
