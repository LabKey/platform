package org.labkey.di.api;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 2013-03-18
 * Time: 3:52 PM
 */
public class ScheduledPipelineJobContext
{
    ScheduledPipelineJobDescriptor _descriptor;
    String _key;
    private Container _container;
    transient private User _user;
    private int _userId = 0;

    public ScheduledPipelineJobContext(ScheduledPipelineJobDescriptor descriptor, Container container, User user)
    {
        _descriptor = descriptor;
        _container = container;
        _user = user;
        if (null != user)
            _userId = user.getUserId();
        _key = "Container" + _container.getRowId() + ":" + _descriptor.getId();
    }

    public ScheduledPipelineJobDescriptor getDescriptor()
    {
        return _descriptor;
    }

    public Container getContainer()
    {
        return _container;
    }

    public User getUser()
    {
        if (null != _user)
            return _user;
        return UserManager.getUser(_userId);
    }

    public String getKey()
    {
        return _key;
    }

    @Override
    public String toString()
    {
        return _descriptor.getId() + " " + _container.getPath() + " " + (null==_user?"-":_user.getEmail());
    }



    // JobDataMap helpers

    public static ScheduledPipelineJobContext getFromJobDetail(JobExecutionContext jobExecutionContext)
    {
        JobDataMap map = jobExecutionContext.getTrigger().getJobDataMap();
        Object result = map.get(ScheduledPipelineJobContext.class.getName());
        if (result == null)
        {
            map = jobExecutionContext.getJobDetail().getJobDataMap();
            result = map.get(ScheduledPipelineJobContext.class.getName());
            if (result == null)
                throw new IllegalArgumentException("No ScheduledPipelineJobContext found!");
        }
        return (ScheduledPipelineJobContext) result;
    }

    protected void writeJobDataMap(JobDataMap map)
    {
        map.put(ScheduledPipelineJobContext.class.getName(), this);
    }

    public JobDataMap getJobDataMap()
    {
        JobDataMap map = new JobDataMap();
        writeJobDataMap(map);
        return map;
    }

    public void setOnJobDetails(JobDetail jobDetail)
    {
        writeJobDataMap(jobDetail.getJobDataMap());
    }
}
