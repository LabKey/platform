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

import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.beanutils.PropertyUtils;
import org.labkey.api.collections.CaseInsensitiveHashMap;

import java.beans.PropertyDescriptor;
import java.util.Map;

/**
 * Wraps a bean class with a DynaClass that reports all properties
 * as strings. Uses BaseWrapperDynaClass which currently only
 * supports simple types.
 */
public class StringBeanDynaClass extends StringWrapperDynaClass
{
    private Class _beanClass;
    //private Map _dynaClasses = Collections.synchronizedMap(new HashMap());

    protected StringBeanDynaClass(Class beanClass)
    {
        this(beanClass, null);
    }

    protected StringBeanDynaClass(Class beanClass, Map<String, Class> extras)
    {
        _beanClass = beanClass;
        PropertyDescriptor propDescriptors[] = PropertyUtils.getPropertyDescriptors(beanClass);
        if (propDescriptors == null)
            propDescriptors = new PropertyDescriptor[0];
        Map<String, Class> propTypes = new CaseInsensitiveHashMap<>();
        for (PropertyDescriptor propDescriptor : propDescriptors)
            propTypes.put(propDescriptor.getName(), propDescriptor.getPropertyType());
        if (null != extras)
        {
            for (Map.Entry<String, Class> entry : extras.entrySet())
            {
                String prop = entry.getKey();
                if (propTypes.containsKey(prop))
                    throw new IllegalArgumentException("bean already contains property " + prop);
                Class type = entry.getValue();
                if (type == null)
                    type = String.class;
                propTypes.put(prop, type);
            }
        }

        init(beanClass.getName(), propTypes);
    }

    /**
     * Create (if necessary) and return a new <code>StringBeanDynaClass</code>
     * instance for the specified bean class.
     *
     * @param beanClass Bean class for which a WrapDynaClass is requested
     */
    public static StringBeanDynaClass createDynaClass(Class beanClass)
    {

        /*

                WrapStringDynaClass dynaClass =
                        (WrapStringDynaClass) _dynaClasses.get(beanClass);
                if (dynaClass == null)
                {
                    dynaClass = new WrapStringDynaClass(beanClass);
                    _dynaClasses.put(beanClass, dynaClass);
                }
                return (dynaClass);
        */
        return new StringBeanDynaClass(beanClass);
    }


    public static StringBeanDynaClass createDynaClass(Class beanClass, Map<String, Class> extraProps)
    {

        /*

                WrapStringDynaClass dynaClass =
                        (WrapStringDynaClass) _dynaClasses.get(beanClass);
                if (dynaClass == null)
                {
                    dynaClass = new WrapStringDynaClass(beanClass);
                    _dynaClasses.put(beanClass, dynaClass);
                }
                return (dynaClass);
        */
        return new StringBeanDynaClass(beanClass, extraProps);
    }


    /**
     * Return the bean class underlying this object
     */
    public Class getBeanClass()
    {
        return _beanClass;
    }

    public DynaBean newInstance() throws IllegalAccessException, InstantiationException
    {
        return new BeanViewForm(_beanClass);
    }
} 
