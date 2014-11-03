/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.freezerpro.export;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.labkey.api.arrays.IntegerArray;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.JsonUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.labkey.api.util.JsonUtil.expectArrayStart;

/**
 * Created by klum on 5/21/2014.
 */
public abstract class FreezerProCommandResonse
{
    protected String _text;
    protected String _dataNodeName;
    protected int _statusCode;
    protected JsonParser _parser;
    protected PipelineJob _job;
    protected FreezerProExport _export;

    public static final String TOTAL_FIELD_NAME = "Total";
    protected int _totalRecords;

    public FreezerProCommandResonse(FreezerProExport export, String text, int statusCode, String dataNodeName, PipelineJob job)
    {
        _export = export;
        _text = text;
        _statusCode = statusCode;
        _dataNodeName = dataNodeName;
        _job = job;
    }

    public List<Map<String, Object>> loadData()
    {
        List<Map<String, Object>> data = new ArrayList<>();
        try {

            JsonFactory factory = new JsonFactory();
            new ObjectMapper(factory);
            _parser = factory.createParser(_text);

            // locate the data array
            if (!ensureDataNode(_parser, _dataNodeName))
            {
                if (_job != null)
                    _job.error("Unable to locate data in the returned response: " + _text);
                throw new IOException("Unable to locate data in the returned response: " + _text);
            }

            // parse the data array
            parseDataArray(_parser, data);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        return data;
    }

    /**
     * Position the parser to the start of the data array object
     * @param parser
     * @param dataNodeName
     * @return
     * @throws IOException
     */
    protected boolean ensureDataNode(JsonParser parser, String dataNodeName) throws IOException
    {
        JsonToken token = parser.nextToken();
        //JsonUtil.expectObjectStart(parser);
        while (token != JsonToken.END_OBJECT)
        {
            token = parser.nextToken();
            if (token == JsonToken.FIELD_NAME)
            {
                String fieldName = parser.getCurrentName();
                if (dataNodeName.equals(fieldName))
                {
                    parser.nextToken();
                    return true;
                }
                else if (TOTAL_FIELD_NAME.equalsIgnoreCase(fieldName))
                {
                    JsonToken totalToken = parser.nextToken();
                    if (totalToken == JsonToken.VALUE_NUMBER_INT)
                    {
                        _totalRecords = parser.readValueAs(Integer.class);
                    }
                }
            }
        }
        return false;
    }

    /**
     * Parse the array of objects into a list of row maps
     * @param parser
     * @param data
     */
    protected void parseDataArray(JsonParser parser, List<Map<String, Object>> data) throws IOException
    {
        expectArrayStart(parser);
        JsonToken token = parser.nextToken();

        while (token != JsonToken.END_ARRAY)
        {
            if (token == JsonToken.END_OBJECT)
                break;

            Map node = _parser.readValueAs(Map.class);
            Map<String, Object> row = new HashMap<>();
            for (Object key : node.keySet())
            {
                String fieldName = String.valueOf(key);
                if (FreezerProExport.exportField(fieldName))
                    row.put(translateFieldName(fieldName), node.get(key));
            }
            data.add(row);
            token = _parser.nextToken();

            if (token == JsonToken.END_ARRAY)
                break;
        }
    }

    protected String translateFieldName(String fieldName)
    {
        return _export.translateFieldName(fieldName);
    }

    public String getText()
    {
        return _text;
    }

    public int getStatusCode()
    {
        return _statusCode;
    }

    public int getTotalRecords()
    {
        return _totalRecords;
    }
}
