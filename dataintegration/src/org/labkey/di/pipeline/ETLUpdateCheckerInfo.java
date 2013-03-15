package org.labkey.di.pipeline;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;

import java.io.Serializable;

/**
 * User: jeckels
 * Date: 3/13/13
 */
public class ETLUpdateCheckerInfo implements Serializable
{
    private ETLDescriptor _etlDescriptor;
    private Container _container;
    private User _user;

    public ETLUpdateCheckerInfo(ETLDescriptor etlDescriptor, Container container, User user)
    {
        _etlDescriptor = etlDescriptor;
        _container = container;
        _user = user;
    }

    public ETLDescriptor getETLDescriptor()
    {
        return _etlDescriptor;
    }

    public Container getContainer()
    {
        return _container;
    }

    public User getUser()
    {
        return _user;
    }

    public static ETLUpdateCheckerInfo getFromJobDetail(JobDetail jobDetail)
    {
        Object result = jobDetail.getJobDataMap().get(ETLUpdateCheckerInfo.class.getName());
        if (result == null)
        {
            throw new IllegalArgumentException("No ETLUpdateCheckerInfo found!");
        }
        return (ETLUpdateCheckerInfo) result;
    }

    protected void writeJobDataMap(JobDataMap map)
    {
        map.put(ETLUpdateCheckerInfo.class.getName(), this);
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


    public String getName()
    {
        return "Container" + _container.getRowId() + ":" + _etlDescriptor.getTransformId();
    }

    @Override
    public String toString()
    {
        return _etlDescriptor.getTransformId() + " " + _container.getPath() + " " + (null==_user?"-":_user.getEmail());
    }
}
