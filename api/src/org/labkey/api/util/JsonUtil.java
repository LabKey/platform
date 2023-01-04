/*
 * Copyright (c) 2013-2018 LabKey Corporation
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Helper methods for parsing JSON objects using Jackson.
 */
public class JsonUtil
{
    // Default ObjectMapper configured for the common case.
    // The ObjectMapper is thread-safe and can be shared across requests
    // but shouldn't be mutated. If you need to reconfigure the ObjectMapper,
    // create a new instance by calling <code>ObjectMapper.copy()</code>.
    public static final ObjectMapper DEFAULT_MAPPER = createDefaultMapper();

    public static ObjectMapper createDefaultMapper()
    {
        ObjectMapper result = new ObjectMapper();
        // Allow org.json classes to be serialized by Jackson
        // result.registerModule(new JsonOrgModule()); // TODO: Uncomment this once we remove JsonOrgOldModule
        // Allow org.json.old classes to be serialized by Jackson (TODO: Remove this after migrating from org.json.old.* -> org.json.*)
        result.registerModule(new JsonOrgOldModule());
        // We must register JavaTimeModule in order to serialize LocalDate, etc.
        result.registerModule(new JavaTimeModule());
        result.setDateFormat(new SimpleDateFormat(DateUtil.getJsonDateTimeFormatString()));
        return result;
    }

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

    public static boolean isArrayEnd(JsonParser p)
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
        JSONArray jsonValues = json.optJSONArray(propName);
        if (null == jsonValues)
            return null;

        String[] strValues = new String[jsonValues.length()];
        for (int i = 0; i < jsonValues.length(); i++)
            strValues[i] = jsonValues.getString(i);
        return strValues;
    }

    public static List<Map<String, Object>> toMapList(JSONArray array)
    {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object o : array)
        {
            if (o instanceof Map<?, ?> map)
            {
                result.add((Map<String, Object>)map);
            }
            else if (o instanceof JSONObject json)
            {
                result.add(json.toMap());
            }
            else
            {
                throw new IllegalStateException("Can't convert array object to a Map: " + o.getClass());
            }
        }
        return result;
    }

    public static List<JSONObject> toJSONObjectList(JSONArray array)
    {
        List<JSONObject> result = new ArrayList<>();

        for (Object o : array)
        {
            if (o instanceof JSONObject jo)
            {
                result.add(jo);
            }
            else
            {
                throw new IllegalStateException("Array contains something other than a JSONObject, a " + o.getClass());
            }
        }

        return result;
    }

    // The new JSONObject.toMap() translates all JSONObjects into Maps and JSONArrays in Lists. In many cases, this is
    // fine, but some existing code paths want to maintain the contained JSONObjects and JSONArrays. This method does
    // that, acting more like the old JSONObject.toMap().
    public static void fillMapShallow(JSONObject json, Map<String, Object> map)
    {
        json.keySet().forEach(key -> {
            Object value = json.get(key);
            map.put(key, JSONObject.NULL == value ? null : value);
        });
    }

    // New JSONObject discards all properties with null values. This returns a JSONObject containing all Map values,
    // preserving null values using the JSONObject.NULL placeholder. This is a shallow copy; standard JSONObject
    // handling will be performed on each top-level put.
    public static JSONObject toJsonPreserveNulls(Map<String, Object> map)
    {
        JSONObject json = new JSONObject();
        map.forEach((k, v) -> json.put(k, null == v ? JSONObject.NULL : v));
        return json;
    }
}
