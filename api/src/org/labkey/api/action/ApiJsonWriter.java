/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
package org.labkey.api.action;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.DateUtil;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 5/13/13
 */
public class ApiJsonWriter extends ApiResponseWriter
{

    //per http://www.iana.org/assignments/media-types/application/
    public static final String CONTENT_TYPE_JSON = "application/json";

    private JsonGenerator jg = new JsonFactory().createGenerator(getWriter());
    private boolean devMode = AppProps.getInstance().isDevMode();

    public ApiJsonWriter(Writer out) throws IOException
    {
        super(out);
    }

    public ApiJsonWriter(HttpServletResponse response) throws IOException
    {
        this(response, null);
    }

    public ApiJsonWriter(HttpServletResponse response, String contentTypeOverride) throws IOException
    {
        this(response, contentTypeOverride, true);
    }

    public ApiJsonWriter(HttpServletResponse response, String contentTypeOverride, boolean prettyPrint) throws IOException
    {
        super(response);
        response.setContentType(null == contentTypeOverride ? CONTENT_TYPE_JSON : contentTypeOverride);
        response.setCharacterEncoding("utf-8");
        if (prettyPrint)
        {
            jg.useDefaultPrettyPrinter();
        }
        jg.setCodec(new ObjectMapper());  // makes the generator annotation aware
    }

    @Override
    protected void writeJsonObj(JSONObject json) throws IOException
    {
        writeValue(json);
        jg.flush();
    }

    @Override
    public void startResponse() throws IOException
    {
        assert jg.getOutputContext().inRoot() : "called startResponse() after response was already started!";
        //we always return an object at the top level
        jg.writeStartObject();
    }

    @Override
    public void endResponse() throws IOException
    {
        assert(!jg.getOutputContext().inRoot()) :  "called endResponse without a corresponding startResponse()!";
        jg.writeEndObject();
        jg.flush();
    }

    @Override
    public void startMap(String name) throws IOException
    {
        assert(!jg.getOutputContext().inRoot()) : "startResponse will start the root-level map!";
        jg.writeObjectFieldStart(name);
    }

    @Override
    public void endMap() throws IOException
    {
        jg.writeEndObject();
    }

    @Override
    public void writeProperty(String name, Object value) throws IOException
    {
        jg.writeFieldName(name);
        writeValue(value);
    }

    private void writeValue (Object value) throws IOException
    {
        if (value instanceof String || value instanceof Number || value instanceof Boolean || value == null)
        {
            jg.writeObject(value);
        }
        else if (value instanceof Map) // This also covers the JSONObject case as that subclasses HashMap. TODO: replace the creation of JSONObjects with Jackson methods
        {
            jg.writeStartObject();
            for (Object o : ((Map) value).keySet())
            {
                String key = o.toString();
                jg.writeFieldName(key);
                writeValue(((Map) value).get(key));
            }
            jg.writeEndObject();
        }
        else if (value instanceof Collection<?>)
        {
            jg.writeStartArray();
            for (Object element : (Collection<?>)value)
            {
                writeValue(element);
            }
            jg.writeEndArray();
        }
        else if (value.getClass().isArray()) // Covers arrays of both objects and primitives
        {
            jg.writeStartArray();
            for (int i = 0; i < Array.getLength(value); i++)
            {
                writeValue(Array.get(value, i));
            }
            jg.writeEndArray();
        }
        else if (value instanceof JSONArray)  // JSONArray is a wrapper for ArrayList, but doesn't expose the same convenience methods. TODO: replace the upstream creation of these JSONArrays with Jackson methods
        {
            jg.writeStartArray();
            for (int i = 0; i < ((JSONArray) value).length(); i++)
            {
                writeValue(((JSONArray)value).get(i));
            }
            jg.writeEndArray();
        }
        else if(value instanceof Date)
        {
            jg.writeString(DateUtil.formatJsonDateTime((Date) value));
        }
        else if (!isSerializeViaJacksonAnnotations())
        {
            jg.writeString(value.toString());
        }
        else
        {
            jg.writeObject(value);
        }
        if (devMode)
        {
            jg.flush();
        }
    }

    @Override
    public void startList(String name) throws IOException
    {
        jg.writeArrayFieldStart(name);
    }

    @Override
    public void endList() throws IOException
    {
        jg.writeEndArray();
    }

    @Override
    public void writeListEntry(Object entry) throws IOException
    {
        writeValue(entry);
    }
}
