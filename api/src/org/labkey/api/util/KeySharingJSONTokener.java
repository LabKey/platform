/*
 * Copyright (c) 2026 LabKey Corporation
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Assert;
import org.junit.Test;

import java.io.Reader;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * JSONTokener that returns one shared String instance per distinct object key, so a bulk payload's rows don't each
 * hold their own copy of every column name.
 */
public class KeySharingJSONTokener extends JSONTokener
{
    // Bounds the map when a request sends many unique keys; keys past the cap are not shared
    static final int MAX_SHARED_KEYS = 1000;

    private final Map<String, String> _keys = new HashMap<>();

    public KeySharingJSONTokener(Reader reader)
    {
        super(reader);
    }

    public KeySharingJSONTokener(String source)
    {
        super(source);
    }

    // org.json reads keys via nextString() but values via nextValue(), so read quoted values here to keep them out of the key map
    @Override
    public Object nextValue() throws JSONException
    {
        char c = nextClean();
        if (c == '"' || (c == '\'' && !getJsonParserConfiguration().isStrictMode()))
            return super.nextString(c);
        if (c != 0)
            back();
        return super.nextValue();
    }

    @Override
    public String nextString(char quote) throws JSONException
    {
        String key = super.nextString(quote);
        String shared = _keys.get(key);
        if (shared != null)
            return shared;
        if (_keys.size() < MAX_SHARED_KEYS)
            _keys.put(key, key);
        return key;
    }

    public static class TestCase extends Assert
    {
        private static final String ROWS = """
            {
                "schemaName": "lists",
                "rows": [
                    {"name": "a", "count": 1, "big": 12345678901, "ratio": 1.5, "flag": true, "missing": null, "nested": {"name": "inner", "list": [1, "name", null]}},
                    {"name": "name", "count": 2, "big": 12345678902, "ratio": 2.5, "flag": false, "missing": null, "nested": {"name": "inner2", "list": []}},
                    {'name': 'single', "count": -3}
                ]
            }
            """;

        @Test
        public void testKeysShared()
        {
            JSONArray rows = new JSONObject(new KeySharingJSONTokener(ROWS)).getJSONArray("rows");
            String nameKey = key(rows.getJSONObject(0), "name");
            for (int i = 1; i < rows.length(); i++)
                assertSame(nameKey, key(rows.getJSONObject(i), "name"));
            assertSame(nameKey, key(rows.getJSONObject(0).getJSONObject("nested"), "name"));
            assertSame(key(rows.getJSONObject(0), "count"), key(rows.getJSONObject(1), "count"));
            assertSame(key(rows.getJSONObject(0).getJSONObject("nested"), "list"), key(rows.getJSONObject(1).getJSONObject("nested"), "list"));

            // String values equal to a key must not come from the key map
            assertNotSame(nameKey, rows.getJSONObject(1).get("name"));
            assertNotSame(nameKey, rows.getJSONObject(0).getJSONObject("nested").getJSONArray("list").get(1));
        }

        @Test
        public void testParseUnchanged()
        {
            JSONObject expected = new JSONObject(new JSONTokener(ROWS));
            JSONObject actual = new JSONObject(new KeySharingJSONTokener(ROWS));
            assertTrue(expected.similar(actual));
            assertEquals(expected.toString(), actual.toString());

            JSONObject row = actual.getJSONArray("rows").getJSONObject(0);
            assertEquals(Integer.class, row.get("count").getClass());
            assertEquals(Long.class, row.get("big").getClass());
            assertEquals(BigDecimal.class, row.get("ratio").getClass());
            assertEquals(Boolean.TRUE, row.get("flag"));
            assertTrue(row.has("missing"));
            assertSame(JSONObject.NULL, row.get("missing"));
            assertSame(JSONObject.NULL, row.getJSONObject("nested").getJSONArray("list").get(2));
            assertEquals("single", actual.getJSONArray("rows").getJSONObject(2).get("name"));
        }

        @Test
        public void testErrorsUnchanged()
        {
            for (String bad : new String[]{"{\"a\":", "{\"a\": }", "{\"a\": \"unterminated", "{\"a\": 1,, }", "{\"a\": [1, }"})
                assertEquals(bad, parseError(new JSONTokener(bad)), parseError(new KeySharingJSONTokener(bad)));
        }

        @Test
        public void testCap()
        {
            StringBuilder sb = new StringBuilder("[");
            for (int row = 0; row < 2; row++)
            {
                sb.append(row == 0 ? "{" : ",{");
                for (int i = 0; i < MAX_SHARED_KEYS + 10; i++)
                    sb.append(i == 0 ? "" : ",").append("\"k").append(i).append("\":").append(i);
                sb.append("}");
            }
            JSONArray rows = new JSONArray(new KeySharingJSONTokener(sb.append("]").toString()));
            assertSame(key(rows.getJSONObject(0), "k0"), key(rows.getJSONObject(1), "k0"));
            String lastKey = "k" + (MAX_SHARED_KEYS + 9);
            assertNotSame(key(rows.getJSONObject(0), lastKey), key(rows.getJSONObject(1), lastKey));
            assertEquals(MAX_SHARED_KEYS + 9, rows.getJSONObject(1).getInt(lastKey));
        }

        private static String key(JSONObject obj, String name)
        {
            return obj.keySet().stream().filter(name::equals).findFirst().orElseThrow();
        }

        private static String parseError(JSONTokener tokener)
        {
            try
            {
                new JSONObject(tokener);
                return null;
            }
            catch (JSONException e)
            {
                return e.getMessage();
            }
        }
    }
}
