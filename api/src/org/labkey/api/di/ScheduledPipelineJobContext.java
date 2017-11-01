/*
 * Copyright (c) 2013-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.di;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.writer.ContainerUser;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

/**
 * Serialization object for stashing information in Quartz to be available when the timer fires and wants to
 * start doing work.
 * User: matthewb
 * Date: 2013-03-18
 */
public class ScheduledPipelineJobContext implements ContainerUser, Cloneable
{
    private ScheduledPipelineJobDescriptor _jobDescriptor;
    String _key;
    private String _containerId = null;
    private int _userId = 0;
    private boolean _verbose = false;

    // when associated with a quartz job, this object should not be edited
    protected boolean _locked = false;

    public ScheduledPipelineJobContext()
    {
    }

    public ScheduledPipelineJobContext(ScheduledPipelineJobDescriptor descriptor, Container container, User user)
    {
        _jobDescriptor = descriptor;
        _containerId = container.getId();
        if (null != user)
            _userId = user.getUserId();
        _key = container.getRowId() + "/" + descriptor.getId();
    }

    public void setLocked()
    {
        _locked = true;
    }


    @Override
    public ScheduledPipelineJobContext clone()
    {
        try
        {
            ScheduledPipelineJobContext c = (ScheduledPipelineJobContext)super.clone();
            c._locked = false;
            return c;
        }
        catch (CloneNotSupportedException x)
        {
            throw new RuntimeException(x);
        }
    }


    public ScheduledPipelineJobDescriptor getJobDescriptor()
    {
        return _jobDescriptor;
    }

    public void setJobDescriptor(ScheduledPipelineJobDescriptor jobDescriptor)
    {
        if (_locked)
            throw new IllegalStateException("Context is read-only");
        _jobDescriptor = jobDescriptor;
    }

    public Container getContainer()
    {
        return ContainerManager.getForId(_containerId);
    }

    public User getUser()
    {
        return UserManager.getUser(_userId);
    }

    public String getKey()
    {
        return _key;
    }


    public boolean isVerbose()
    {
        return _verbose;
    }


    public void setVerbose(boolean verbose)
    {
        if (_locked)
            throw new IllegalStateException("Context is read-only");
        _verbose = verbose;
    }


    @Override
    public String toString()
    {
        return _key;
    }



    // JobDataMap helpers

    public static ScheduledPipelineJobContext getFromQuartzJobDetail(JobExecutionContext jobExecutionContext)
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

    protected void writeQuartzJobDataMap(JobDataMap map)
    {
        map.put(ScheduledPipelineJobContext.class.getName(), this);
    }

    public JobDataMap getQuartzJobDataMap()
    {
        JobDataMap map = new JobDataMap();
        writeQuartzJobDataMap(map);
        return map;
    }
}
