package org.labkey.freezerpro.export;

import com.fasterxml.jackson.core.JsonParser;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.JsonUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 10/28/2014.
 */
public class GetFreezersResponse extends FreezerProCommandResonse
{
    public static final String DATA_NODE_NAME = "Freezers";

    public GetFreezersResponse(FreezerProExport export, String text, int statusCode, PipelineJob job)
    {
        super(export, text, statusCode, DATA_NODE_NAME, job);
    }
}
