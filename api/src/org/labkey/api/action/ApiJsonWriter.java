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
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.Pair;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writer that knows how to generate a JSON version of the content back to the client.
 */
public class ApiJsonWriter extends ApiResponseWriter
{
    //per http://www.iana.org/assignments/media-types/application/
    public static final String CONTENT_TYPE_JSON = "application/json";

    private ObjectMapper _mapper;
    private JsonGenerator jg = new JsonFactory().createGenerator(getWriter());

    enum WriterState {Initialized, Started, Ended, Closed}
    private WriterState state = WriterState.Initialized;

    public ApiJsonWriter(Writer out) throws IOException
    {
        super(out);
        // Issue 19924: Do not flush the underlying Writer (thus committing the response) on all calls to write JSON content.
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
        // Issue 19924: Do not flush the underlying Writer (thus committing the response) on all calls to write JSON content.
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
        return JsonUtil.DEFAULT_MAPPER;
    }

    @Override
    public void close() throws IOException
    {
        if (state == WriterState.Closed)
            return;
        state = WriterState.Closed;

        jg.flush();
        if (state == WriterState.Started)
            throw new IllegalStateException("close() called without calling endResponse()");
    }

    private void ensureNotClosed()
    {
        if (state == WriterState.Closed)
            throw new IllegalStateException("writer is closed");
    }

    @Override
    public void startResponse() throws IOException
    {
        ensureNotClosed();
        if (state != WriterState.Initialized)
            throw new IllegalStateException("startResponse() has already been called");
        state = WriterState.Started;

        // Always return an object at the top level
        assert jg.getOutputContext().inRoot();
        jg.writeStartObject();
    }

    @Override
    public void endResponse() throws IOException
    {
        ensureNotClosed();
        if (state != WriterState.Started)
            throw new IllegalStateException("startResponse() has not been called");

        assert !jg.getOutputContext().inRoot() :  "called endResponse() without a corresponding startResponse()!";
        jg.writeEndObject();
        jg.flush();
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
        ensureNotClosed();
        if (value instanceof String || value instanceof Number || value instanceof Boolean || value == null)
        {
            jg.writeObject(value);
        }
        else if (value instanceof Map<?, ?> map) // This also covers the org.json.old.JSONObject case as that subclasses HashMap. TODO: replace the creation of JSONObjects with Jackson methods
        {
            writeMap(map);
        }
        else if (value instanceof JSONObject jsonObject)
        {
            writeMap(jsonObject.toMap());
        }
        else if (value instanceof Collection<?> coll)
        {
            jg.writeStartArray();
            try
            {
                for (Object element : coll)
                    writeObject(element);
            }
            finally
            {
                jg.writeEndArray();
            }
        }
        else if (value.getClass().isArray()) // Covers arrays of both objects and primitives
        {
            jg.writeStartArray();
            try
            {
                for (int i = 0; i < Array.getLength(value); i++)
                    writeObject(Array.get(value, i));
            }
            finally
            {
                jg.writeEndArray();
            }
        }
        else if (value instanceof JSONArray jsonArray) // TODO: replace the upstream creation of these JSONArrays with Jackson methods
        {
            jg.writeStartArray();
            try
            {
                for (Object o : jsonArray)
                    writeObject(o);
            }
            finally
            {
                jg.writeEndArray();
            }
        }
        else if (value instanceof Date)
        {
            jg.writeString(DateUtil.formatJsonDateTime((Date) value));
        }
        // Always use Jackson serialization for SimpleResponse, Issue 47216
        else if (isSerializeViaJacksonAnnotations() || value instanceof SimpleResponse<?>)
        {
            jg.writeObject(value);
        }
        else if (value == JSONObject.NULL)
        {
            jg.writeNull();
        }
        else
        {
            jg.writeString(value.toString());
        }
    }

    private void writeMap(Map<?, ?> map) throws IOException
    {
        boolean badContext = jg.getOutputContext().getCurrentName() == null && jg.getOutputContext().inObject();
        if (badContext)
        {
            throw new IllegalStateException("How did we get here");
            /* OLD VERSION
            // Exceptions get serialized out into the response. However, in some parts of the processing we're in the wrong output context
            // and this would create invalid JSON. Detect and prevent this to still create valid JSON, and hopefully something downstream will handle
            // the error gracefully.
            jg.writeFieldName("unhandledException");@J
            jg.writeStartObject();
            for (var e : map.entrySet())
            {
                jg.writeFieldName(String.valueOf(e.getKey()));
                writeObject(e.getValue());
            }
            jg.writeEndObject();
            jg.writeEndObject();
             */
        }

        jg.writeStartObject();
        try
        {
            writeProperties(map);
        }
        finally
        {
            jg.writeEndObject();
        }
    }


    void writeProperties(Map<?,?> map) throws IOException
    {
        assert jg.getOutputContext().inObject();
        for (var e : map.entrySet())
            writeProperty(String.valueOf(e.getKey()), e.getValue());
    }

    @Override
    public void writeProperties(JSONObject json) throws IOException
    {
        writeProperties(json.toMap());
    }

    public void startObject(String name) throws IOException
    {
        jg.writeObjectFieldStart(name);
    }

    public void endObject() throws IOException
    {
        jg.writeEndObject();
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

    /**
     * Attempt to reset the http response.  If possible the response will be completely reset.  If not possible,
     * this code will attempt to return the response to the top-level object in the json response.
     * Since the response will be in an unspecified state, the caller should use writeExceptionPropertiesToRootAndEndObject() after calling this method.

     */
    @Override
    protected void resetOutput() throws IOException
    {
        var context = jg.getOutputContext();

        // If the entire response so far is buffered in memory, we get a do over.
        if (!getResponse().isCommitted())
        {
            getResponse().reset();
            jg = new JsonFactory().createGenerator(getWriter());
            initGenerator();
            if (state == WriterState.Started)
                jg.writeStartObject();
            return;
        }
        else
        {
            // If context.inRoot() is true, then either we haven't started writing the response object, or we've finished writing it.
            // So either there's nothing to do, or nothing we can do.
            if (context.inRoot())
                return;
        }

        // Because of our fastidious use of try-with-resource and try-finally we expect to be in the root object,
        assert context.getParent().inRoot();

        // But if that's not the case, we try to get back into that state to make any reported exception readable.
        //noinspection StatementWithEmptyBody
        while (!context.getParent().inRoot() && closeContext())
        {
            // pass
        }
    }

    // return true if closeContext() can be called again
    private boolean closeContext() throws IOException
    {
        var context = jg.getOutputContext();
        if (context.inRoot())
            return false;
        else if (context.inArray())
            jg.writeEndArray();
        else if (context.inObject())
            jg.writeEndObject();
        return false;
    }


    static int beancount = 0;

    public static class Bean extends HashMap<String,Object> {}

    public static class TopBean extends Bean
    {
        TopBean()
        {
            beancount = 0;
            putAll(Map.of("name","top","inner_beans",getBeans()));
        }

        public ArrayList<Map<String,Object>> getBeans()
        {
            int count = 2;
            var ret = new ArrayList<Map<String,Object>>(count);
            for (var i=0 ; i<count-1 ; i++)
                ret.add(new GoodBean());
            ret.add(new BadBean());
            return ret;
        }
    }

    public static class GoodBean extends Bean
    {
        GoodBean()
        {
            putAll(Map.of("names", getArray(), "map", getMap()));
        }
        public List<String> getArray()
        {
            return List.of("black","black-eyed","cannellini","chick peas","kidney","lentil","pinto","fava","navy","edamame","soy");
        }
        public Map<String,Object> getMap()
        {
            return Map.of("key","value");
        }
    }

    public static class BadBean extends GoodBean
    {
        BadBean()
        {
            super();
        }

        @Override
        public Map<String,Object> getMap()
        {
            // extra nesting for good measure
            var poison = new AbstractMap<String,Object>()
            {
                @NotNull @Override public Set<Entry<String, Object>> entrySet()
                {
                    return new LinkedHashSet<>(Arrays.asList(new Pair<>("key","value"), new Pair<>("poison", "*") {
                                @Override public String getValue()
                                {
                                    throw new IllegalStateException("throwing up");
                                }
                            }));
                }
            };
            return Map.of("key","value","poison",poison);
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testExceptionNotCommitted() throws IOException
        {
            var beans = new TopBean();
            var writer = new ApiJsonWriter(new MockHttpServletResponse());
            writer.setSerializeViaJacksonAnnotations(true);
            writer.startResponse();
            try
            {
                writer.writeProperty("schemaName", "test");
                writer.writeProperty("queryName", "beans");
                writer.writeProperty("formatVersion", 99.9);
                writer.writeProperty("beans", beans);
                fail("shouldn't be here");
            }
            catch (Exception ex)
            {
                writer.handleRenderException(ex);
            }
            finally
            {
                writer.endResponse();
            }
            var responseText = ((MockHttpServletResponse)writer.getResponse()).getContentAsString();
            var json = new JSONObject(responseText);
            assertEquals("throwing up", json.getString("exception"));
            assertTrue(json.has("stackTrace"));
            assertFalse(json.has("schemaName"));
        }

        @Test
        public void testExceptionCommitted() throws IOException
        {
            var beans = new TopBean();

            var res = new MockHttpServletResponse();
            res.getWriter().write(StringUtils.repeat(' ', 10*1000));
            res.flushBuffer();
            assert res.isCommitted();

            var writer = new ApiJsonWriter(new MockHttpServletResponse());
            writer.setSerializeViaJacksonAnnotations(true);
            writer.startResponse();
            try
            {
                writer.writeProperty("schemaName", "test");
                writer.writeProperty("queryName", "beans");
                writer.writeProperty("formatVersion", 99.9);
                writer.writeProperty("beans", beans);
                fail("shouldn't be here");
            }
            catch (Exception ex)
            {
                writer.handleRenderException(ex);
            }
            finally
            {
                writer.endResponse();
            }
            var responseText = ((MockHttpServletResponse)writer.getResponse()).getContentAsString();
            var json = new JSONObject(responseText);
            assertEquals("throwing up", json.getString("exception"));
            assertTrue(json.has("stackTrace"));
            assertTrue(json.has("schemaName"));
        }
    }
}
