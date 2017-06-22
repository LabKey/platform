/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.api.util;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Helper methods for parsing JSON objects using Jackson.
 */
public class JsonUtil
{
    public static JsonLocation expectObjectStart(JsonParser p) throws IOException
    {
        if (p.getCurrentToken() != JsonToken.START_OBJECT)
            throw new JsonParseException("Expected object start '{', got '" + p.getCurrentToken() + "'", p.getTokenLocation());

        JsonLocation loc = p.getTokenLocation();
        p.nextToken();
        return loc;
    }

    public static void expectObjectEnd(JsonParser p) throws IOException
    {
        if (p.getCurrentToken() != JsonToken.END_OBJECT)
            throw new JsonParseException("Expected object end '}', got '" + p.getCurrentToken() + "'", p.getTokenLocation());

        p.nextToken();
    }

    public static JsonLocation expectArrayStart(JsonParser p) throws IOException
    {
        if (p.getCurrentToken() != JsonToken.START_ARRAY)
            throw new JsonParseException("Expected array start '[', got '" + p.getCurrentToken() + "'", p.getTokenLocation());

        JsonLocation loc = p.getTokenLocation();
        p.nextToken();
        return loc;
    }

    public static void expectArrayEnd(JsonParser p) throws IOException
    {
        if (!isArrayEnd(p))
            throw new JsonParseException("Expected array end ']', got '" + p.getCurrentToken() + "'", p.getTokenLocation());

        p.nextToken();
    }

    public static boolean isArrayEnd(JsonParser p) throws IOException
    {
        return p.getCurrentToken() == JsonToken.END_ARRAY;
    }

    public static void skipValue(JsonParser p) throws IOException
    {
        p.skipChildren();
        p.nextToken();
    }

    public static Object valueOf(JsonNode n) throws JsonParseException
    {
        if (n.isArray())
        {
            List<Object> result = new ArrayList<>();
            for (Iterator<JsonNode> elements = n.elements(); elements.hasNext();)
                result.add(valueOf(elements.next()));
            return result;
        }

        if (!n.isValueNode())
            throw new JsonParseException("Expected value node", null);

        if (n.isNull())
            return null;

        if (n.isNumber())
            return n.numberValue();

        if (n.isBoolean())
            return n.booleanValue();

        if (n.isTextual())
            return n.textValue();

        throw new JsonParseException("Unexpected value type: " + n.getNodeType(), null);
    }

    public static String[] getStringArray(JSONObject json, String propName)
    {
        if (!json.has(propName) || json.get(propName) == null)
            return null;

        JSONArray jsonValues = (JSONArray)json.get(propName);
        String[] strValues = new String[jsonValues.length()];
        for (int i = 0; i < jsonValues.length(); i++)
            strValues[i] = jsonValues.getString(i);
        return strValues;
    }
}
