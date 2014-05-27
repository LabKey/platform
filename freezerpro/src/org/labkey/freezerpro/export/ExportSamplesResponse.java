package org.labkey.freezerpro.export;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.JsonUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.labkey.api.util.JsonUtil.expectArrayStart;

/**
 * Created by klum on 5/21/2014.
 */
public class ExportSamplesResponse extends FreezerProCommandResonse
{
    public static final String DATA_NODE_NAME = "Samples";

    public ExportSamplesResponse(String text, int statusCode, PipelineJob job)
    {
        super(text, statusCode, DATA_NODE_NAME, job);
    }
}
