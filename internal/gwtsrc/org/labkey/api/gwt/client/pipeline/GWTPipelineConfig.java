package org.labkey.api.gwt.client.pipeline;

import java.io.Serializable;
import java.util.List;

/**
 * User: jeckels
 * Date: Jan 20, 2012
 */
public class GWTPipelineConfig implements Serializable
{
    private List<GWTPipelineTask> _tasks;
    private List<GWTPipelineLocation> _locations;

    public GWTPipelineConfig()
    {
    }

    public GWTPipelineConfig(List<GWTPipelineTask> tasks, List<GWTPipelineLocation> locations)
    {
        _tasks = tasks;
        _locations = locations;
    }

    public List<GWTPipelineTask> getTasks()
    {
        return _tasks;
    }

    public List<GWTPipelineLocation> getLocations()
    {
        return _locations;
    }
}
