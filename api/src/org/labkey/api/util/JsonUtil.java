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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonpCharacterEscapes;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Helper methods for working with Jackson, JSONObject, and JSONArray
 */
public class JsonUtil
{
    public static final Logger LOG = LogHelper.getLogger(JsonUtil.class, "JSON helper functions");

    // Default ObjectMapper configured for the common case.
    // The ObjectMapper is thread-safe and can be shared across requests
    // but shouldn't be mutated. If you need to reconfigure the ObjectMapper,
    // create a new instance by calling <code>ObjectMapper.copy()</code>.
    public static final ObjectMapper DEFAULT_MAPPER = createDefaultMapper();

    public static ObjectMapper createDefaultMapper()
    {
        ObjectMapper result = new LabKeyObjectMapper();
        // Allow org.json classes to be serialized by Jackson
        result.registerModule(new JsonOrgModule());
        // We must register JavaTimeModule in order to serialize LocalDate, etc.
        result.registerModule(new JavaTimeModule());
        result.setDateFormat(new SimpleDateFormat(DateUtil.getJsonDateTimeFormatString()));
        return result;
    }


    private static final int[] labkeyAsciiEscapes;
    static
    {
        labkeyAsciiEscapes = CharacterEscapes.standardAsciiEscapesForJSON().clone();
        labkeyAsciiEscapes['/'] = '/';
    }


    static class LabKeyCharacterEscapes extends JsonpCharacterEscapes
    {
        private static final LabKeyCharacterEscapes sInstance = new LabKeyCharacterEscapes();
        @Override
        public int[] getEscapeCodesForAscii()
        {
            return labkeyAsciiEscapes;
        }
    }

    static class LabKeyJsonFactory extends MappingJsonFactory
    {
        LabKeyJsonFactory()
        {
            setCharacterEscapes(LabKeyCharacterEscapes.sInstance);
        }

        LabKeyJsonFactory(LabKeyJsonFactory src, ObjectMapper mapper)
        {
            super(src, mapper);
        }

        @Override
        public JsonFactory copy()
        {
            _checkInvalidCopy(LabKeyJsonFactory.class);
            return new LabKeyJsonFactory(this, null);
        }
    }

    static class LabKeyObjectMapper extends ObjectMapper
    {
        LabKeyObjectMapper()
        {
            super(new LabKeyJsonFactory());
        }

        LabKeyObjectMapper(LabKeyObjectMapper src)
        {
            this(src, null);
        }

        LabKeyObjectMapper(LabKeyObjectMapper src, JsonFactory factory)
        {
            super(src, factory);
        }

        @Override
        public ObjectMapper copy()
        {
            _checkInvalidCopy(ObjectMapper.class);
            return new LabKeyObjectMapper(this);
        }

        @Override
        public ObjectMapper copyWith(JsonFactory factory)
        {
            _checkInvalidCopy(ObjectMapper.class);
            return new LabKeyObjectMapper(this, factory);
        }
    }


    public static JsonLocation expectObjectStart(JsonParser p) throws IOException
    {
        if (p.getCurrentToken() != JsonToken.START_OBJECT)
            throw new JsonParseException(p, "Expected object start '{', got '" + p.getCurrentToken() + "'", p.getTokenLocation());

        JsonLocation loc = p.getTokenLocation();
        p.nextToken();
        return loc;
    }

    public static void expectObjectEnd(JsonParser p) throws IOException
    {
        if (p.getCurrentToken() != JsonToken.END_OBJECT)
            throw new JsonParseException(p, "Expected object end '}', got '" + p.getCurrentToken() + "'", p.getTokenLocation());

        p.nextToken();
    }

    public static JsonLocation expectArrayStart(JsonParser p) throws IOException
    {
        if (p.getCurrentToken() != JsonToken.START_ARRAY)
            throw new JsonParseException(p, "Expected array start '[', got '" + p.getCurrentToken() + "'", p.getTokenLocation());

        JsonLocation loc = p.getTokenLocation();
        p.nextToken();
        return loc;
    }

    public static void expectArrayEnd(JsonParser p) throws IOException
    {
        if (!isArrayEnd(p))
            throw new JsonParseException(p, "Expected array end ']', got '" + p.getCurrentToken() + "'", p.getTokenLocation());

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
            throw new JsonParseException("Expected value node");

        if (n.isNull())
            return null;

        if (n.isNumber())
            return n.numberValue();

        if (n.isBoolean())
            return n.booleanValue();

        if (n.isTextual())
            return n.textValue();

        throw new JsonParseException("Unexpected value type: " + n.getNodeType());
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

    // The new JSONObject.toMap() translates all JSONObjects into Maps and JSONArrays into Lists. In many cases, this is
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

    // The JSON standard and JSONObject do not allow comments. However, many JSON documents include them. This method
    // strips comments from JSON-formatted strings, making them compatible with JSONObject() and other strict parsers.
    // See https://stackoverflow.com/questions/52394945/fastest-means-of-removing-comments-from-json-in-java
    // See https://fasterxml.github.io/jackson-core/javadoc/2.8/com/fasterxml/jackson/core/JsonParser.Feature.html#ALLOW_COMMENTS
    // See Issue 47618
    public static String stripComments(String jsonWithComments) throws JsonProcessingException
    {
        ObjectMapper mapper = DEFAULT_MAPPER.copy();
        mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
        mapper.enable(JsonParser.Feature.ALLOW_TRAILING_COMMA); // NLP *.ctc.json files have trailing commas, so allow them
        return mapper.writeValueAsString(mapper.readTree(jsonWithComments));
    }

    // +Infinity, -Infinity, and NaN values are not allowed in JSONObject, but these sometimes arise in scientific data.
    // This method translates Double and Float infinity & NaN values to values that are allowed and then puts the
    // translated values into the JSONObject.
    public static void safePut(JSONObject json, String key, Number value)
    {
        json.put(key, translateNumber(value));
    }

    public static Object translateNumber(Number value)
    {
        if (value instanceof Double d)
        {
            if (d.isNaN())
                return JSONObject.NULL;
            else if (d == Double.POSITIVE_INFINITY)
                return Double.MAX_VALUE;
            else if (d == Double.NEGATIVE_INFINITY)
                return -Double.MAX_VALUE;
        }
        else if (value instanceof Float f)
        {
            if (f.isNaN())
                return JSONObject.NULL;
            else if (f == Float.POSITIVE_INFINITY)
                return Float.MAX_VALUE;
            else if (f == Float.NEGATIVE_INFINITY)
                return -Float.MAX_VALUE;
        }

        return value;
    }

    /**
     * Sanitizes a mutable map by replacing +Infinity, -Infinity, and NaN values with JSON-legal values. Recursively
     * sanitizes all embedded maps and lists as well; these must also be mutable.
     * @param mutableMap mutable Map to sanitize
     */
    public static void sanitizeMap(Map<String, Object> mutableMap)
    {
        mutableMap.keySet().forEach(key -> sanitize(mutableMap.get(key), num -> mutableMap.put(key, translateNumber(num))));
    }

    /**
     * Sanitizes a mutable list by replacing +Infinity, -Infinity, and NaN values with JSON-legal values. Recursively
     * sanitizes all embedded maps and lists as well; these must also be mutable.
     * @param mutableList mutable List to sanitize
     */
    public static void sanitizeList(List<Object> mutableList)
    {
        ListIterator<Object> iter = mutableList.listIterator();
        while (iter.hasNext())
            sanitize(iter.next(), num -> iter.set(translateNumber(num)));
    }

    private static void sanitize(Object obj, Consumer<Number> numberAction)
    {
        if (obj instanceof Map m)
            sanitizeMap(m);
        else if (obj instanceof List list)
            sanitizeList(list);
        else if (obj instanceof Number num)
            numberAction.accept(num);
    }

    public static class TestCase extends Assert
    {
        private static final String JSON_WITH_COMMENTS = """
            {
                /*
                   Comments are explicitly disallowed in JSON, but some documents still include them and some parsers
                   allow them. In our case, the old JSONObject implementation tolerated comments but the newer one does
                   not. This document is used to test that comments normally cause the new JSONObject parser to fail and
                   stripComments() successfully strips Java-style block and single-line comments.
                */
                "widget": {  // widget is the top-level object
                    "debug": "on",
                    "window": {
                        "title": "Sample Konfabulator Widget",
                        "name": "main_window",
                        "width": 500,
                        "height": 500
                    },
                    "image": {
                        "src": "Images/Sun.png", // image must exist
                        "name": "sun1",
                        "hOffset": 250,  // horizontal offset
                        "vOffset": 250,  // vertical offset
                        "alignment": "center"
                    },
                    "text": ["also", "need", "to", "test", "trailing", "commas",],
                }
            }
            """;

        private static final String JSON_ARRAY_WITH_COMMENTS = """
            /* Here's a block comment */
            // Here's a single-line comment
            ["Ford", "BMW", "Fiat",], // Here are trailing commas, which also need to be allowed
            """;

        private static final String[] COMMENT_WORDS = new String[]{"//", "/*", "*/", "block", "single-line", ",]", "],"};

        @Test
        public void testStripComments() throws JsonProcessingException
        {
            // Test JSONObject
            assertThrows(JSONException.class, () -> new JSONObject(JSON_WITH_COMMENTS));

            Assert.assertTrue("Expected all comment words before stripping",
                Arrays.stream(COMMENT_WORDS).allMatch(JSON_WITH_COMMENTS::contains));
            String strippedObjectJson = stripComments(JSON_WITH_COMMENTS);
            Assert.assertFalse("Expected no comment words after stripping",
                StringUtils.containsAny(strippedObjectJson, COMMENT_WORDS));
            JSONObject json = new JSONObject(strippedObjectJson);
            JSONObject widget = json.getJSONObject("widget");
            Assert.assertEquals(4, widget.length());
            JSONObject window = widget.getJSONObject("window");
            Assert.assertEquals(4, window.length());
            Assert.assertEquals("Sample Konfabulator Widget", window.get("title"));
            Assert.assertEquals(500, window.get("height"));

            // Test JSONArray
            assertThrows(JSONException.class, () -> new JSONArray(JSON_ARRAY_WITH_COMMENTS));

            Assert.assertTrue("Expected all comment words before stripping",
                Arrays.stream(COMMENT_WORDS).allMatch(JSON_ARRAY_WITH_COMMENTS::contains));
            String strippedArrayJson = stripComments(JSON_ARRAY_WITH_COMMENTS);
            Assert.assertFalse("Expected no comment words after stripping",
                StringUtils.containsAny(strippedArrayJson, COMMENT_WORDS));
            Assert.assertEquals(3, new JSONArray(strippedArrayJson).length());
        }

        @Test
        public void testInfinityDouble()
        {
            assertThrows(JSONException.class, () -> new JSONObject().put("divide", 1.0/0.0));
            assertThrows(JSONException.class, () -> new JSONObject().put("negDivide", -1.0/0.0));
            assertThrows(JSONException.class, () -> new JSONObject().put("posInfinity", Double.POSITIVE_INFINITY));
            assertThrows(JSONException.class, () -> new JSONObject().put("negInfinity", Double.NEGATIVE_INFINITY));
            assertThrows(JSONException.class, () -> new JSONObject().put("NaN", Double.NaN));

            JSONObject json = new JSONObject();
            json.put("double", 1.0);
            json.put("max", Double.MAX_VALUE);
            json.put("min", Double.MIN_VALUE);

            safePut(json, "NaN", Double.NaN);
            safePut(json, "divide", 1.0/0.0);
            safePut(json, "negDivide", -1.0/0.0);
            safePut(json, "posInfinity", Double.POSITIVE_INFINITY);
            safePut(json, "negInfinity", Double.NEGATIVE_INFINITY);

            assertEquals(1.0, json.getDouble("double"), 0.0);
            assertEquals(Double.MAX_VALUE, json.getDouble("max"), 0.0);
            assertEquals(Double.MIN_VALUE, json.getDouble("min"), 0.0);

            assertTrue(json.isNull("NaN") && json.has("NaN"));
            assertEquals(Double.MAX_VALUE, json.getDouble("divide"), 0.0);
            assertEquals(-Double.MAX_VALUE, json.getDouble("negDivide"), 0.0);
            assertEquals(Double.MAX_VALUE, json.getDouble("posInfinity"), 0.0);
            assertEquals(-Double.MAX_VALUE, json.getDouble("negInfinity"), 0.0);

            assertEquals("{\"negDivide\":-1.7976931348623157E308,\"min\":4.9E-324,\"max\":1.7976931348623157E308,\"double\":1,\"NaN\":null,\"divide\":1.7976931348623157E308,\"negInfinity\":-1.7976931348623157E308,\"posInfinity\":1.7976931348623157E308}", json.toString());
        }

        @Test
        public void testInfinityFloat()
        {
            assertThrows(JSONException.class, () -> new JSONObject().put("divide", 1.0f/0.0f));
            assertThrows(JSONException.class, () -> new JSONObject().put("negDivide", -1.0f/0.0f));
            assertThrows(JSONException.class, () -> new JSONObject().put("posInfinity", Float.POSITIVE_INFINITY));
            assertThrows(JSONException.class, () -> new JSONObject().put("negInfinity", Float.NEGATIVE_INFINITY));
            assertThrows(JSONException.class, () -> new JSONObject().put("NaN", Float.NaN));

            JSONObject json = new JSONObject();
            json.put("float", 1.0f);
            json.put("max", Float.MAX_VALUE);
            json.put("min", Float.MIN_VALUE);

            safePut(json, "NaN", Float.NaN);
            safePut(json, "divide", 1.0f/0.0f);
            safePut(json, "negDivide", -1.0f/0.0f);
            safePut(json, "posInfinity", Float.POSITIVE_INFINITY);
            safePut(json, "negInfinity", Float.NEGATIVE_INFINITY);

            assertEquals(1.0f, json.getFloat("float"), 0.0f);
            assertEquals(Float.MAX_VALUE, json.getFloat("max"), 0.0f);
            assertEquals(Float.MIN_VALUE, json.getFloat("min"), 0.0f);

            assertTrue(json.isNull("NaN") && json.has("NaN"));
            assertEquals(Float.MAX_VALUE, json.getFloat("divide"), 0.0f);
            assertEquals(-Float.MAX_VALUE, json.getFloat("negDivide"), 0.0f);
            assertEquals(Float.MAX_VALUE, json.getFloat("posInfinity"), 0.0f);
            assertEquals(-Float.MAX_VALUE, json.getFloat("negInfinity"), 0.0f);

            assertEquals("{\"negDivide\":-3.4028235E38,\"min\":1.4E-45,\"max\":3.4028235E38,\"NaN\":null,\"divide\":3.4028235E38,\"negInfinity\":-3.4028235E38,\"float\":1,\"posInfinity\":3.4028235E38}", json.toString());
        }

        @Test
        // Ensure that classes implementing JSONString are generating valid, expected JSON
        public void testJsonString()
        {
            JSONObject json = new JSONObject();
            Container c = JunitUtil.getTestContainer();
            json.put("container", c);
            GUID guid = c.getEntityId();
            json.put("guid", guid);
            ActionURL url = AppProps.getInstance().getHomePageActionURL();
            json.put("actionUrl", url);
            HtmlString html = HtmlString.unsafe("<html><table><td>Hello</td></table>");
            json.put("htmlString", html);
            User u = TestContext.get().getUser();
            json.put("user", u);

            // Round trip to ensure all these JSONString implementations produce valid JSON
            JSONObject roundTripJson = new JSONObject(json.toString());

            // Test expected value for each JSONString implementation
            assertEquals(c.getId(), roundTripJson.get("container"));
            assertEquals(guid.toString(), roundTripJson.get("guid"));
            assertEquals(url.toString(), roundTripJson.get("actionUrl"));
            assertEquals(html.toString(), roundTripJson.get("htmlString"));
            assertEquals(u.getUserId(), roundTripJson.get("user"));
        }

        @Test
        public void testSanitize()
        {
            // Rough approximation of the map GetNabRunsAction generates
            Map<String, Object> response = new HashMap<>();
            response.put("assayName", "foobar");
            response.put("assayDescription", "this is my assay");
            response.put("assayId", 1234);
            response.put("neg", Double.NEGATIVE_INFINITY);
            response.put("pos", Double.POSITIVE_INFINITY);
            response.put("nan", Double.NaN);

            List<Map<String, Object>> runList = new ArrayList<>();
            response.put("runs", runList);
            runList.add(getDoubleRun());
            runList.add(getFloatRun());

            assertThrows(JSONException.class, () -> new JSONObject(response));
            JsonUtil.sanitizeMap(response);
            JSONObject json = new JSONObject(response);
        }

        @Test
        public void testJavascript() throws Exception
        {
            // just verifying current behavior: JSONObject escapes /, ObjectMapper does not escape /
            assertEquals("{\"key<\\/\":\"<\\/script>\"}", new JSONObject(Map.of("key</","</script>")).toString());
            assertEquals("{\"key</\":\"</script>\"}", new ObjectMapper().writeValueAsString(Map.of("key</","</script>")));

            // our ObjectMapper should act like JSONObject.toString()
            assertEquals("{\"key<\\/\":\"<\\/script>\"}", DEFAULT_MAPPER.writeValueAsString(Map.of("key</","</script>")));
            // test copy() while we're here
            assertEquals("{\"key<\\/\":\"<\\/script>\"}", DEFAULT_MAPPER.copy().writeValueAsString(Map.of("key</","</script>")));
        }

        private Map<String, Object> getFloatRun()
        {
            Map<String, Object> run = new HashMap<>();
            run.put("cutoff", Float.NaN);
            run.put("maxDilution", Float.NEGATIVE_INFINITY);
            run.put("minDilution", Float.POSITIVE_INFINITY);

            return run;
        }

        private Map<String, Object> getDoubleRun()
        {
            Map<String, Object> run = new HashMap<>();
            run.put("cutoff", Double.NaN);
            run.put("maxDilution", Double.NEGATIVE_INFINITY);
            run.put("minDilution", Double.POSITIVE_INFINITY);

            return run;
        }
    }
}
