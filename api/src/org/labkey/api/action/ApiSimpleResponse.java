/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.beanutils.BeanUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.ObjectFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Use this for simple responses from API actions
 */
public class ApiSimpleResponse implements ApiResponse
{
    private final JSONObject _json;

    public ApiSimpleResponse()
    {
        _json = new JSONObject();
    }

    public ApiSimpleResponse(JSONObject json)
    {
        _json = json;
    }

    public ApiSimpleResponse(Map<String, ?> values)
    {
        _json = new JSONObject(values);
    }

    public ApiSimpleResponse(String key, Object value)
    {
        _json = new JSONObject();
        _json.put(key, value);
    }

    @JsonValue // Tell Jackson that this is the serialization method
    public JSONObject getJson()
    {
        return _json;
    }

    /**
     * Puts a bean at the root of the response, with no containing key.
     */
    public <T> void putBean(T bean, String... props) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException
    {
        Map<String, Object> map = getBeanMap(bean, props);
        putAll(map);
    }

    public <T> void putBeanList(String key, List<T> beans, String... props) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException
    {
        List<Map<String, Object>> beanMaps = new ArrayList<>();
        for (Object bean : beans)
            beanMaps.add(getBeanMap(bean, props));
        _json.put(key, beanMaps);
    }

    protected <T> Map<String, Object> getBeanMap(T bean, String... props) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException
    {
        if (null == props || props.length == 0)
        {
            //noinspection unchecked
            ObjectFactory<T> f = ObjectFactory.Registry.getFactory((Class<T>)bean.getClass());
            if (null == f)
                throw new IllegalArgumentException("Could not find a matching object factory.");
            return f.toMap(bean, null);
        }
        else
        {
            Map<String, Object> map = new HashMap<>(props.length);
            for(String prop : props)
                map.put(prop, BeanUtils.getProperty(bean, prop));
            return map;
        }
    }

    public boolean equals(Object o)
    {
        throw new UnsupportedOperationException("ApiSimpleResponse.equals() is not supported");
    }

    public int hashCode()
    {
        throw new UnsupportedOperationException("ApiSimpleResponse.hasCode() is not supported");
    }

    public Object get(String key)
    {
        return _json.opt(key);
    }

    public Object put(String key, Object value)
    {
        return _json.put(key,value);
    }

    public void putAll(@NotNull Map<String, Object> map)
    {
        map.forEach(_json::put);
    }

    @Override
    public void render(ApiResponseWriter writer) throws IOException
    {
        writer.writeObject(getJson());
    }
}
