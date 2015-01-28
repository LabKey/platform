/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

package org.labkey.api.collections;

import org.junit.Assert;
import org.junit.Test;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class CaseInsensitiveMapWrapper<V> extends MapWrapper<String, V> implements Serializable
{
    private final Map<String, String> _correctCaseMap;

    public CaseInsensitiveMapWrapper(Map<String, V> map)
    {
        super(map);
        _correctCaseMap = new HashMap<>();
        for (Map.Entry<? extends String, ? extends V> entry : map.entrySet())
        {
            _correctCaseMap.put(entry.getKey().toLowerCase(), entry.getKey());
        }
    }

    public CaseInsensitiveMapWrapper(Map<String, V> map, CaseInsensitiveMapWrapper<V> caseMapping)
    {
        super(map);
        _correctCaseMap = caseMapping._correctCaseMap;
        for (Map.Entry<? extends String, ? extends V> entry : map.entrySet())
        {
            _correctCaseMap.put(entry.getKey().toLowerCase(), entry.getKey());
        }
    }

    public V get(Object key)
    {
        String correctKey = normalizeKey(key);
        return _map.get(correctKey);
    }

    protected String normalizeKey(Object key)
    {
        if (!(key instanceof String))
            return null;
        String s = (String)key;
        String result = _correctCaseMap.get(s.toLowerCase());
        if (result == null)
        {
            // We don't already have a canonical casing, so just use the original string
            result = s;
        }
        return result;
    }

    public V put(String key, V value)
    {
        String correctKey = normalizeKey(key);
        V ret = null;
        if (correctKey != null)
        {
            ret = remove(correctKey);
        }
        _map.put(key, value);
//        assert !_correctCaseMap.containsKey(key.toLowerCase());
        _correctCaseMap.put(null==key?null:key.toLowerCase(), key);
        return ret;
    }

    public V remove(Object key)
    {
        return _map.remove(normalizeKey(key));
    }

    public boolean containsKey(Object key)
    {
        return _map.containsKey(normalizeKey(key));
    }

    public void clear()
    {
        _map.clear();
        _correctCaseMap.clear();
    }

    public void putAll(Map<? extends String, ? extends V> t)
    {
        for (Map.Entry<? extends String, ? extends V> entry : t.entrySet())
        {
            put(entry.getKey(), entry.getValue());
        }
    }

    public static class TestCase
    {
        @Test
        public void testKeys()
        {
            Map<String, String> m = new CaseInsensitiveHashMap<>();
            Assert.assertFalse("Map should not contain key", m.containsKey("noKey"));
            Assert.assertFalse("Map should not contain null key", m.containsKey(null));
            Assert.assertNull("Map should not contain key", m.get("noKey"));
            Assert.assertNull("Map should not contain null key", m.get(null));
            m.put(null, "nullValue");
            Assert.assertFalse("Map should not contain key", m.containsKey("noKey"));
            Assert.assertTrue("Map should contain null key", m.containsKey(null));
            Assert.assertNull("Map should not contain key", m.get("noKey"));
            Assert.assertEquals("Map should contain null key", "nullValue", m.get(null));
            m.put("realKey", "realValue");
            Assert.assertTrue("Map should contain key", m.containsKey("realKey"));
            Assert.assertTrue("Map should contain key", m.containsKey("REALKEY"));
            Assert.assertTrue("Map should contain key", m.containsKey("realkey"));
            Assert.assertTrue("Map should contain null key", m.containsKey(null));
            Assert.assertEquals("Map should contain key", "realValue", m.get("realKey"));
            Assert.assertEquals("Map should contain key", "realValue", m.get("REALKEY"));
            Assert.assertEquals("Map should contain key", "realValue", m.get("realkey"));
            Assert.assertEquals("Map should contain null key", "nullValue", m.get(null));
        }
    }
}
