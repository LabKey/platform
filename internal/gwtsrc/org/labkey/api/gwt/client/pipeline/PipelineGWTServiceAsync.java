package org.labkey.api.gwt.client.pipeline;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.RemoteService;

public interface PipelineGWTServiceAsync
{
    void getLocationOptions(String pipelineId, AsyncCallback<GWTPipelineConfig> async);
}
