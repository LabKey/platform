/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
import org.labkey.api.data.ObjectFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Use this for simple responses from Api actions.
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Feb 13, 2008
 * Time: 4:44:39 PM
 */
public class ApiSimpleResponse extends HashMap<String,Object> implements ApiResponse
{
    public ApiSimpleResponse()
    {
    }

    public ApiSimpleResponse(Map<String, Object> values)
    {
        putAll(values);
    }

    public ApiSimpleResponse(String key, Object value)
    {
        put(key, value);
    }

    public ApiSimpleResponse(String key, int value)
    {
        put(key, Integer.valueOf(value));
    }

    public ApiSimpleResponse(String key, boolean value)
    {
        put(key, Boolean.valueOf(value));
    }

    public Map<String, Object> getProperties()
    {
        return this;
    }

    /**
     * Puts a bean at the root of the response, with no containing key.
     */
    public <T> void putBean(T bean, String... props) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException
    {
        Map<String,Object> map = getBeanMap(bean, props);
        putAll(map);
    }

    public <T> void putBean(String key, T bean, String... props) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException
    {
        put(key, getBeanMap(bean, props));
    }

    public <T> void putBeanList(String key, List<T> beans, String... props) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException
    {
        List<Map> beanMaps = new ArrayList<Map>();
        for(Object bean : beans)
            beanMaps.add(getBeanMap(bean, props));
        put(key, beanMaps);
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
            Map<String,Object> map = new HashMap<String,Object>(props.length);
            for(String prop : props)
                map.put(prop, BeanUtils.getProperty(bean, prop));
            return map;
        }
    }
}
