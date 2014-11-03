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
public class GetFreezerSamplesResponse extends FreezerProCommandResonse
{
    public static final String DATA_NODE_NAME = "Samples";

    public GetFreezerSamplesResponse(FreezerProExport export, String text, int statusCode, PipelineJob job)
    {
        super(export, text, statusCode, DATA_NODE_NAME, job);
    }

    @Override
    public String translateFieldName(String fieldName)
    {
        // special case for this API using obj_id and id as the sample id
        if ("obj_id".equalsIgnoreCase(fieldName))
            return FreezerProExport.SAMPLE_ID_FIELD_NAME;
        else
            return super.translateFieldName(fieldName);
    }
}
