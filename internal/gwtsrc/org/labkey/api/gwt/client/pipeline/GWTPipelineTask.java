package org.labkey.api.gwt.client.pipeline;

import java.io.Serializable;

/**
 * User: jeckels
 * Date: Jan 20, 2012
 */
public class GWTPipelineTask implements Serializable
{
    private String _taskId;
    private String _name;
    private String _groupName;
    private boolean _cluster;

    private GWTPipelineLocation _defaultLocation;

    public GWTPipelineTask()
    {
    }

    public GWTPipelineTask(String taskId, String name, String groupName, boolean cluster, GWTPipelineLocation defaultLocation)
    {
        _taskId = taskId;
        _name = name;
        _groupName = groupName;
        _cluster = cluster;
        _defaultLocation = defaultLocation;
    }

    public String getTaskId()
    {
        return _taskId;
    }

    public String getName()
    {
        return _name;
    }

    public String getGroupName()
    {
        return _groupName;
    }

    public boolean isCluster()
    {
        return _cluster;
    }

    public GWTPipelineLocation getDefaultLocation()
    {
        return _defaultLocation;
    }
}
