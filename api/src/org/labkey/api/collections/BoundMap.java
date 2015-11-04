/*
 * Copyright (c) 2005-2015 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.io.Serializable;
import java.io.IOException;


public class BoundMap extends AbstractMap<String, Object> implements Serializable
{
    private static final HashMap<Class, HashMap<String, BoundProperty>> _savedPropertyMaps = new HashMap<>();

    protected Object _bean;
    transient protected HashMap<String, Object> _map = new HashMap<>();
    transient protected HashMap<String, BoundProperty> _properties;
    transient private Object _keyDebug = null;

    static class BoundProperty
    {
        BoundProperty(Method get, Method set, Class type)
        {
            _getter = get;
            _setter = set;
            _type = type;
        }

        Method _getter;
        Method _setter;
        Class _type;
    }


    public BoundMap(Object bean)
    {
        _bean = bean;
        initialize(_bean.getClass());
    }


    public BoundMap()
    {
    }


    public void setBean(Object bean)
    {
        _bean = bean;
        initialize(_bean.getClass());
    }


    public Object getBean()
    {
        return _bean;
    }

    /** Magic method for deserialization */
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException
    {
        in.defaultReadObject();
        initialize(_bean.getClass());
    }

    @SuppressWarnings({"CloneDoesntCallSuperClone"})
    @Override
    public Object clone() throws CloneNotSupportedException
    {
        throw new CloneNotSupportedException();
    }


    @Override
    public Object get(Object key)
    {
        String sKey = (String)key;
        try
        {
            BoundProperty bound = getBoundProperty(sKey);
            if (null != bound)
            {
                if (null == bound._getter)
                    throw new IllegalArgumentException("Can not get property " + sKey);
                return bound._getter.invoke(_bean);
            }
        }
        catch (IllegalAccessException x)
        {
            throw new RuntimeException(x);
        }
        catch (InvocationTargetException x)
        {
            throw new RuntimeException(x);
        }

        return _map.get(sKey);
    }


    @Override
    public Object put(String key, Object value)
    {
        try
        {
            BoundProperty bound = getBoundProperty(key);
            if (null != bound)
            {
                Object previous = null;
                if (null == bound._setter)
                    throw new IllegalArgumentException("Can not set property " + key);
                if (null != value && !bound._type.isAssignableFrom(value.getClass()))
                    value = ConvertUtils.convert(value.toString(), bound._type);
                if (null != bound._getter)
                    previous = bound._getter.invoke(_bean);
                if (value != previous)
                {
                    assert key != _keyDebug : "infinite recursion???";
                    //noinspection ConstantConditions
                    assert null != (_keyDebug = key);
                    bound._setter.invoke(_bean, value);
                }
                return previous;
            }
        }
        catch (IllegalAccessException x)
        {
            throw new RuntimeException(x);
        }
        catch (InvocationTargetException x)
        {
            throw new RuntimeException(x);
        }
        finally
        {
            //noinspection ConstantConditions
            assert null == (_keyDebug = null);
        }

        return _map.put(key, value);
    }


    @Override
    public Set<Map.Entry<String,Object>> entrySet()
    {
        Set<String> keys = keySet();
        Set<Map.Entry<String,Object>> entries = new HashSet<>();

        for (String key : keys)
        {
            entries.add(new Entry(key));
        }

        return Collections.unmodifiableSet(entries);
    }


    private class Entry implements Map.Entry<String,Object>
    {
        String key;

        Entry(String key)
        {
            this.key = key;
        }

        public String getKey()
        {
            return key;
        }

        public Object getValue()
        {
            return get(key);
        }

        public Object setValue(Object v)
        {
            return put(key, v);
        }
    }

    private String convertToPropertyName(String name)
    {
        if (1 == name.length())
            return name.toLowerCase();

        if (Character.isUpperCase(name.charAt(0)) && !Character.isUpperCase(name.charAt(1)))
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        else
            return name;
    }

    private BoundProperty getBoundProperty(String key)
    {
        if (null == key)
            return null;
        BoundProperty bound = _properties.get(key);
        if (null == bound && Character.isUpperCase(key.charAt(0)))
            bound = _properties.get(convertToPropertyName(key));

        return bound;
    }

    @Override
    public Set<String> keySet()
    {
        Set<String> keys = new HashSet<>();
        Set<String> mapKeys = _map.keySet();
        keys.addAll(mapKeys);

        Set<String> propKeys = _properties.keySet();
        keys.addAll(propKeys);

        return Collections.unmodifiableSet(keys);
    }


    @Override
    public int size()
    {
        return _map.size() + _properties.size();
    }


    @Override
    public boolean containsKey(Object key)
    {
        String sKey = (String)key;
        if (null != getBoundProperty(sKey))
            return true;
        return _map.containsKey(sKey);
    }


    @Override
    public Object remove(Object key)
    {
        String sKey = (String)key;
        if (null != getBoundProperty(sKey))
            throw new UnsupportedOperationException("can't remove property " + key);
        return _map.remove(sKey);
    }


    public Map getExtendedProperties()
    {
        return _map;
    }


    private void initialize(Class beanClass)
    {
        synchronized(_savedPropertyMaps)
        {
            HashMap<String, BoundProperty> props = _savedPropertyMaps.get(beanClass);
            if (props == null)
            {
                try
                {
                    props = new HashMap<>();
                    BeanInfo beanInfo = Introspector.getBeanInfo(beanClass);
                    PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
                    if (propertyDescriptors != null)
                    {
                        for (PropertyDescriptor propertyDescriptor : propertyDescriptors)
                        {
                            if (propertyDescriptor != null)
                            {
                                String name = propertyDescriptor.getName();
                                if ("class".equals(name))
                                    continue;
                                Method readMethod = propertyDescriptor.getReadMethod();
                                Method writeMethod = propertyDescriptor.getWriteMethod();
                                Class aType = propertyDescriptor.getPropertyType();
                                props.put(name, new BoundProperty(readMethod, writeMethod, aType));
                            }
                        }
                    }
                }
                catch (IntrospectionException e)
                {
                    Logger.getLogger(this.getClass()).error("error creating BoundMap", e);
                    throw new RuntimeException(e);
                }

                _savedPropertyMaps.put(beanClass, props);
            }

            _properties = props;
        }
    }


    public static class TestBean
    {
        private int i;
        private Integer j;
        private String s;

        public int getI()
        {
            return i;
        }

        public void setI(int i)
        {
            this.i = i;
        }

        public Integer getJ()
        {
            return j;
        }

        public void setJ(Integer j)
        {
            this.j = j;
        }

        public String getS()
        {
            return s;
        }

        public void setS(String s)
        {
            this.s = s;
        }
    }


    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            TestBean bean = new TestBean();
            Map<String,Object> m = new BoundMap(bean);

            assertEquals(0,m.get("i"));
            assertNull(m.get("j"));
            assertNull(m.get("s"));

            bean.i = 1;
            bean.j = 2;
            bean.s = "fred";

            assertEquals(1,m.get("i"));
            assertEquals(2, m.get("j"));
            assertEquals("fred",m.get("s"));

            m.put("i", "3");
            m.put("j", 4);
            m.put("s", "velma");
            m.put("t", "shaggy");

            assertEquals(3,m.get("i"));
            assertEquals(4, m.get("j"));
            assertEquals("velma", m.get("s"));
            assertEquals("shaggy", m.get("t"));
        }
    }
}
