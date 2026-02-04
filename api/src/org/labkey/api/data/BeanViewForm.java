/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.beanutils.DynaClass;
import org.labkey.api.action.HasBindParameters;

import java.util.HashMap;
import java.util.Map;


public class BeanViewForm<K> extends TableViewForm implements DynaBean, HasBindParameters
{
    private final StringBeanDynaClass _dynaClass;
    private final Class<K> _wrappedClass;

    protected BeanViewForm(Class<K> clss)
    {
        this(clss, null, null);
    }

    public BeanViewForm(Class<K> clss, TableInfo tinfo)
    {
        this(clss, tinfo, null);
    }


    public BeanViewForm(Class<K> clss, TableInfo tinfo, Map<String, Class<?>> extraProps)
    {
        super(tinfo);
        _dynaClass = StringBeanDynaClass.createDynaClass(clss, extraProps);
        _wrappedClass = clss;
    }


    public K getBean()
    {
        if (null != _oldValues)
        {
            try
            {
                ObjectFactory<K> factory = ObjectFactory.Registry.getFactory(_wrappedClass);

                K bean;
                if (_oldValues instanceof Map && !(_wrappedClass.isAssignableFrom(_oldValues.getClass())))
                    bean = factory.fromMap((Map<String, ?>) _oldValues);
                else
                    bean = (K) BeanUtils.cloneBean(_oldValues);

                factory.fromMap(bean, getValuesToBind());
                return bean;
            }
            catch (ReflectiveOperationException x)
            {
                throw new RuntimeException(x);
            }
        }
        else
        {
            ObjectFactory<K> factory = ObjectFactory.Registry.getFactory(_wrappedClass);
            return factory.fromMap(getValuesToBind());
        }
    }


    public void setBean(K bean)
    {
        ObjectFactory<K> factory = ObjectFactory.Registry.getFactory(_wrappedClass);
        setTypedValues(factory.toMap(bean, null), false);
    }

    @Override
    public Map<String, Object> getValuesToBind()
    {
        //If we don't have strings and do have typed values then
        //make the strings match the typed values
        Map<String, Object> strings = super.getValuesToBind();
        if (null == strings || strings.isEmpty() && (null != _values && !_values.isEmpty()))
        {
            strings = new HashMap<>();
            for (Map.Entry<String, Object> entry : _values.entrySet())
            {
                strings.put(entry.getKey(), ConvertUtils.convert(entry.getValue()));
            }
            setValuesToBind(strings);
        }

        return strings;
    }

    @Override
    public void setOldValues(Object o)
    {
        if (o == null)
            _oldValues = null;
        else if (_wrappedClass.isAssignableFrom(o.getClass()))
            _oldValues = o;
        else if (o instanceof Map)
        {
            ObjectFactory<K> factory = ObjectFactory.Registry.getFactory(_wrappedClass);
            _oldValues = factory.fromMap((Map<String, Object>) o);
        }
        else
        {
            throw new IllegalArgumentException("Type of old values is incompatible with wrapped class");
        }
    }

    @Override
    protected SimpleConvert getSimpleConvert(String propName)
    {
        var type = _dynaClass.getTruePropType(propName);
        if (null != type)
        {
            return ConvertHelper.getSimpleConvert(type);
        }
        return super.getSimpleConvert(propName);
    }

    // DynaBean
    @Override
    public DynaClass getDynaClass()
    {
        return _dynaClass;
    }

    @Override
    public Object get(String name)
    {
        return super.getValueToBind(name);
    }

    @Override
    public void set(String name, Object value)
    {
        super.setValueToBind(name,value);
    }

    @Override
    public boolean contains(String arg0, String arg1)
    {
        throw new UnsupportedOperationException("No mapped properties in a table");
    }

    @Override
    public Object get(String arg0, String arg1)
    {
        throw new UnsupportedOperationException("No mapped properties in a table");
    }

    @Override
    public Object get(String arg0, int arg1)
    {
        throw new UnsupportedOperationException("No indexed properties in a table");
    }

    @Override
    public void remove(String arg0, String arg1)
    {
        throw new UnsupportedOperationException("No indexed properties in a table");
    }

    @Override
    public void set(String arg0, String arg1, Object arg2)
    {
        throw new UnsupportedOperationException("No mapped properties in a table");
    }

    @Override
    public void set(String arg0, int arg1, Object arg2)
    {
        throw new UnsupportedOperationException("No indexed properties in a table");
    }
}
