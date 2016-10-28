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

import java.util.HashMap;
import java.util.Map;


public class BeanViewForm<K> extends TableViewForm implements DynaBean
{
    private Class<K> _wrappedClass = null;

    protected BeanViewForm(Class<K> clss)
    {
        this(clss, null, null);
    }

    public BeanViewForm(Class<K> clss, TableInfo tinfo)
    {
        this(clss, tinfo, null);
    }


    public BeanViewForm(Class<K> clss, TableInfo tinfo, Map<String, Class> extraProps)
    {
        super(StringBeanDynaClass.createDynaClass(clss, extraProps), tinfo);
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

                factory.fromMap(bean, getStrings());
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
            return factory.fromMap(this.getStrings());
        }
    }


    public void setBean(K bean)
    {
        ObjectFactory<K> factory = ObjectFactory.Registry.getFactory(_wrappedClass);
        this.setTypedValues(factory.toMap(bean, null), false);
    }

    public Map<String, String> getStrings()
    {
        //If we don't have strings and do have typed values then
        //make the strings match the typed values
        Map<String, String> strings = super.getStrings();
        if (null == strings || strings.size() == 0 && (null != _values && _values.size() > 0))
        {
            strings = new HashMap<>();
            for (Map.Entry<String, Object> entry : _values.entrySet())
            {
                strings.put(entry.getKey(), ConvertUtils.convert(entry.getValue()));
            }
            _stringValues = strings;
        }

        return strings;
    }

    public void setOldValues(Object o)
    {
        if (o == null)
            _oldValues = null;
        else if (_wrappedClass.isAssignableFrom(o.getClass()))
            _oldValues = o;
        else if (o instanceof Map)
        {
            ObjectFactory factory = ObjectFactory.Registry.getFactory(_wrappedClass);
            _oldValues = factory.fromMap((Map<String, Object>) o);
        }
        else
        {
            throw new IllegalArgumentException("Type of old values is incompatible with wrapped class");
        }
    }
}
