package org.labkey.api.gwt.client.pipeline;

import com.google.gwt.user.client.rpc.RemoteService;

/**
 * User: jeckels
 * Date: Jan 20, 2012
 */
public interface PipelineGWTService extends RemoteService
{
    public GWTPipelineConfig getLocationOptions(String pipelineId);
}
