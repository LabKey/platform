package org.labkey.freezerpro.export;

import com.fasterxml.jackson.core.JsonParser;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.JsonUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 5/24/2014.
 */
public class ExportLocationResponse extends FreezerProCommandResonse
{
    public ExportLocationResponse(String text, int statusCode, PipelineJob job)
    {
        super(text, statusCode, null, job);
    }

    /**
     * Position the parser to the start of the data array object
     * @param parser
     * @param dataNodeName
     * @return
     * @throws java.io.IOException
     */
    protected boolean ensureDataNode(JsonParser parser, String dataNodeName) throws IOException
    {
        parser.nextToken();
        JsonUtil.expectObjectStart(parser);

        return true;
    }

    /**
     * Parse the array of objects into a list of row maps
     * @param parser
     * @param data
     */
    protected void parseDataArray(JsonParser parser, List<Map<String, Object>> data) throws IOException
    {
        Map node = _parser.readValueAs(Map.class);
        Map<String, Object> row = new HashMap<>();
        for (Object key : node.keySet())
        {
            row.put(String.valueOf(key), node.get(key));
        }
        data.add(row);
    }
}
