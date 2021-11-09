/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.json.JSONArray;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.JsonUtil;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * Writer that knows how to generate a JSON version of the content back to the client.
 * User: tgaluhn
 * Date: 5/13/13
 */
public class ApiJsonWriter extends ApiResponseWriter
{
    //per http://www.iana.org/assignments/media-types/application/
    public static final String CONTENT_TYPE_JSON = "application/json";

    private ObjectMapper _mapper;
    private JsonGenerator jg = new JsonFactory().createGenerator(getWriter());

    public ApiJsonWriter(Writer out) throws IOException
    {
        super(out);
        // Don't flush the underlying Writer (thus committing the response) on all calls to write JSON content. See issue 19924
        jg.disable(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM);
    }

    public ApiJsonWriter(HttpServletResponse response) throws IOException
    {
        this(response, null);
    }

    public ApiJsonWriter(HttpServletResponse response, String contentTypeOverride) throws IOException
    {
        this(response, contentTypeOverride, null, true);
    }

    public ApiJsonWriter(HttpServletResponse response, String contentTypeOverride, ObjectMapper mapper, boolean prettyPrint) throws IOException
    {
        super(response);
        response.setContentType(null == contentTypeOverride ? CONTENT_TYPE_JSON : contentTypeOverride);
        response.setCharacterEncoding("utf-8");
        if (prettyPrint)
        {
            jg.useDefaultPrettyPrinter();
        }
        _mapper = mapper;
        initGenerator();
    }

    private void initGenerator()
    {
        jg.setCodec(getObjectMapper());  // makes the generator annotation aware
        // Don't flush the underlying Writer (thus committing the response) on all calls to write JSON content. See issue 19924
        jg.disable(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM);
    }

    protected final ObjectMapper getObjectMapper()
    {
        if (_mapper == null)
            _mapper = createObjectMapper();
        return _mapper;
    }

    /**
     * Clone and configure the Jackson ObjectMapper for use in serialization.
     * If you need to perform custom configuration, override this method and create
     * a copy of the <code>DEFAULT_MAPPER</code>.
     *
     * Example:
     * <pre>
     *     ObjectMapper om = JsonUtil.DEFAULT_MAPPER.copy();
     *     om.addMixin(GWTDomain.class, GWTDomainMixin.class);
     *     return om;
     * </pre>
     */
    protected ObjectMapper createObjectMapper()
    {
        // We must register JavaTimeModule in order to serialize LocalDate, etc.
        return JsonUtil.DEFAULT_MAPPER.registerModule(new JavaTimeModule());
    }

    @Override
    public void close() throws IOException
    {
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
        writeObject(value);
    }

    @Override
    protected void writeObject(Object value) throws IOException
    {
        if (value instanceof String || value instanceof Number || value instanceof Boolean || value == null)
        {
            jg.writeObject(value);
        }
        else if (value instanceof Map) // This also covers the JSONObject case as that subclasses HashMap. TODO: replace the creation of JSONObjects with Jackson methods
        {
            boolean badContext = jg.getOutputContext().getCurrentName() == null && jg.getOutputContext().inObject();
            if (badContext)
            {   // Exceptions get serialized out into the response. However, in some parts of the processing we're in the wrong output context
                // and this would create invalid JSON. Detect and prevent this to still create valid JSON, and hopefully something downstream will handle
                // the error gracefully.
                jg.writeFieldName("unhandledException");
            }
            jg.writeStartObject();
            for (var e : ((Map<Object,Object>)value).entrySet())
            {
                jg.writeFieldName(String.valueOf(e.getKey()));
                writeObject(e.getValue());
            }
            jg.writeEndObject();
            if (badContext)
            {
                jg.writeEndObject();
            }
        }
        else if (value instanceof Collection<?>)
        {
            jg.writeStartArray();
            for (Object element : (Collection<?>)value)
            {
                writeObject(element);
            }
            jg.writeEndArray();
        }
        else if (value.getClass().isArray()) // Covers arrays of both objects and primitives
        {
            jg.writeStartArray();
            for (int i = 0; i < Array.getLength(value); i++)
            {
                writeObject(Array.get(value, i));
            }
            jg.writeEndArray();
        }
        else if (value instanceof JSONArray)  // JSONArray is a wrapper for ArrayList, but doesn't expose the same convenience methods. TODO: replace the upstream creation of these JSONArrays with Jackson methods
        {
            jg.writeStartArray();
            for (int i = 0; i < ((JSONArray) value).length(); i++)
            {
                writeObject(((JSONArray) value).get(i));
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

        // 21112: Malformed JSON response in production environments
        // TODO: This is not the recommended pattern as this causes an unnecessary amount of flushing (performance)
        jg.flush();
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
        writeObject(entry);
    }

    @Override
    protected void resetOutput() throws IOException
    {
        super.resetOutput();
        // Brute force destroy the generator we have and get a new one. There's probably a less drastic way to reset
        // the generator outputContext, but I can't find one.
        jg = new JsonFactory().createGenerator(getWriter());
        initGenerator();
    }
}
