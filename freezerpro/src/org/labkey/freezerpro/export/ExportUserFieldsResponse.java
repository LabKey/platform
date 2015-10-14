/*
 * Copyright (c) 2014-2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.freezerpro.export;

import com.fasterxml.jackson.core.JsonParser;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.JsonUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 5/23/2014.
 */
public class ExportUserFieldsResponse extends FreezerProCommandResponse
{
    public ExportUserFieldsResponse(FreezerProExport export, String text, int statusCode, PipelineJob job)
    {
        super(export, text, statusCode, null, job);
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
        if (node != null && !node.isEmpty())
        {
            Map<String, Object> row = new HashMap<>();
            for (Object key : node.keySet())
            {
                row.put(String.valueOf(key), node.get(key));
            }
            data.add(row);
        }
    }
}
