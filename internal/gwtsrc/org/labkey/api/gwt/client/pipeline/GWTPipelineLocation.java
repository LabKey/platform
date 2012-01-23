package org.labkey.api.gwt.client.pipeline;

import java.io.Serializable;
import java.util.List;

/**
 * User: jeckels
 * Date: Jan 20, 2012
 */
public class GWTPipelineLocation implements Serializable
{
    private String _location;
    private List<String> _queues;

    public GWTPipelineLocation()
    {
    }

    public GWTPipelineLocation(String location, List<String> queues)
    {
        _location = location;
        _queues = queues;
    }

    public String getLocation()
    {
        return _location;
    }

    public boolean isCluster()
    {
        return _queues != null;
    }

    public List<String> getQueues()
    {
        return _queues;
    }
}
