/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

import org.apache.commons.beanutils.BeanUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.ObjectFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Use this for simple responses from Api actions.
 *
 * User: Dave
 * Date: Feb 13, 2008
 * Time: 4:44:39 PM
 */
public class ApiSimpleResponse implements ApiResponse, Map<String,Object>
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

    public ApiSimpleResponse(String key, int value)
    {
        _json = new JSONObject();
        _json.put(key, Integer.valueOf(value));
    }

    public ApiSimpleResponse(String key, boolean value)
    {
        _json = new JSONObject();
        _json.put(key, Boolean.valueOf(value));
    }

    public Map<String, ?> getProperties()
    {
        return _json;
    }

    /**
     * Puts a bean at the root of the response, with no containing key.
     */
    public <T> void putBean(T bean, String... props) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException
    {
        Map<String,Object> map = getBeanMap(bean, props);
        _json.putAll(map);
    }

    public <T> void putBean(String key, T bean, String... props) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException
    {
        _json.put(key, getBeanMap(bean, props));
    }

    public <T> void putBeanList(String key, List<T> beans, String... props) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException
    {
        List<Map> beanMaps = new ArrayList<>();
        for(Object bean : beans)
            beanMaps.add(getBeanMap(bean, props));
        _json.put(key, beanMaps);
    }

    protected <T> Map<String,Object> getBeanMap(T bean, String... props) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException
    {
        if(null == props || props.length == 0)
        {
            //noinspection unchecked
            ObjectFactory<T> f = ObjectFactory.Registry.getFactory((Class<T>)bean.getClass());
            if (null == f)
                throw new IllegalArgumentException("Cound not find a matching object factory.");
            return f.toMap(bean, null);
        }
        else
        {
            Map<String,Object> map = new HashMap<>(props.length);
            for(String prop : props)
                map.put(prop, BeanUtils.getProperty(bean, prop));
            return map;
        }
    }

    public boolean equals(Object o)
    {
        return _json.equals(o);
    }

    public int hashCode()
    {
        return _json.hashCode();
    }

    public int size()
    {
        return _json.size();
    }

    public boolean isEmpty()
    {
        return _json.isEmpty();
    }

    public Object get(Object o)
    {
        return _json.get(o);
    }

    public boolean containsKey(Object o)
    {
        return _json.containsKey(o);
    }

    public Object remove(Object o)
    {
        return _json.remove(o);
    }

    public void clear()
    {
        _json.clear();
    }

    public boolean containsValue(Object o)
    {
        return _json.containsValue(o);
    }

    @NotNull
    public Set<String> keySet()
    {
        return _json.keySet();
    }

    @NotNull
    public Collection<Object> values()
    {
        return _json.values();
    }

    @NotNull
    public Set<Map.Entry<String, Object>> entrySet()
    {
        return _json.entrySet();
    }

    @Override
    public Object put(String key, Object value)
    {
        return _json.put(key,value);
    }

    @Override
    public void putAll(@NotNull Map map)
    {
        _json.putAll(map);
    }

    @Override
    public void render(ApiResponseWriter writer) throws IOException
    {
        writer.writeObject(getProperties());
    }
}
