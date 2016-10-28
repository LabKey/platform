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
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.UnexpectedException;
import org.springframework.beans.factory.InitializingBean;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;


public class BeanObjectFactory<K> implements ObjectFactory<K> // implements ResultSetHandler
{
    private static Logger _log = Logger.getLogger(BeanObjectFactory.class);

    private Class<K> _class;

    // for performance pre-calculate readable/writeable properties
    protected HashSet<String> _writeableProperties = null;
    protected HashSet<String> _readableProperties = null;


    protected BeanObjectFactory()
    {
    }


    public BeanObjectFactory(Class<K> clss)
    {
        _class = clss;
        _writeableProperties = new HashSet<>();
        _readableProperties = new HashSet<>();

        K bean;
        try
        {
            bean = _class.newInstance();
        }
        catch (InstantiationException | IllegalAccessException x)
        {
            throw new RuntimeException(x);
        }

        PropertyDescriptor origDescriptors[] = PropertyUtils.getPropertyDescriptors(bean);
        _writeableProperties = new HashSet<>(origDescriptors.length * 2);
        _readableProperties = new HashSet<>(origDescriptors.length * 2);

        for (PropertyDescriptor origDescriptor : origDescriptors)
        {
            String name = origDescriptor.getName();
            if ("class".equals(name))
                continue;
            if (PropertyUtils.isReadable(bean, name))
            {
                Method readMethod = origDescriptor.getReadMethod();
                if (null != readMethod)
                {
                    if (readMethod.getParameterTypes().length == 0 && null == readMethod.getAnnotation(Transient.class))
                        _readableProperties.add(name);
                }
            }
            if (PropertyUtils.isWriteable(bean, name))
                _writeableProperties.add(name);
        }
    }


    // Implement "official" property name rule
    public String convertToPropertyName(String name)
    {
        if (1 == name.length())
            return name.toLowerCase();

        if (Character.isUpperCase(name.charAt(0)) && !Character.isUpperCase(name.charAt(1)))
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        else
            return name;
    }

    public K fromMap(Map<String, ?> m)
    {
        try
        {
            K bean = _class.newInstance();
            fromMap(bean, m);
            return bean;
        }
        catch (IllegalAccessException | InstantiationException x)
        {
            _log.error("unexpected error", x);
            throw new RuntimeException(x);
        }
    }
    

    @Override
    public K fromMap(K bean, Map<String, ?> m)
    {
        if (!(m instanceof CaseInsensitiveHashMap))
            m = new CaseInsensitiveHashMap<>(m);

        for (String prop : _writeableProperties)
        {
            Object value = null;
            try
            {
                // If the map contains the key, assuming that we should use the map's value, even if it's null.
                // Otherwise, don't set a value on the bean.
                if (m.containsKey(prop))
                {
                    value = m.get(prop);
                    BeanUtils.copyProperty(bean, prop, value);
                }
            }
            catch (IllegalAccessException | InvocationTargetException x)
            {
                throw new UnexpectedException(x);
            }
            catch (IllegalArgumentException x)
            {
                _log.error("could not set property: " + prop + "=" + String.valueOf(value), x);
            }
        }

        this.fixupBean(bean);
        return bean;
    }


    @Override
    public @NotNull Map<String, Object> toMap(K bean, @Nullable Map<String, Object> m)
    {
        return _toMap(bean, m, false);
    }

    public @NotNull Map<String, String> toStringMap(K bean, @Nullable Map<String, String> m)
    {
        //noinspection unchecked
        return (Map)_toMap(bean, (Map)m, true);
    }

    protected @NotNull Map<String, Object> _toMap(K bean, @Nullable Map<String, Object> m, boolean stringify)
    {
        try
        {
            if (null == m)
                m = new CaseInsensitiveHashMap<>();

            for (String name : _readableProperties)
            {
                try
                {
                    Object value = PropertyUtils.getSimpleProperty(bean, name);
                    if (stringify)
                        value = ConvertUtils.convert(value);
                    m.put(name, value);
                }
                catch (NoSuchMethodException e)
                {
                    assert false : e;
                }
            }
        }
        catch (IllegalAccessException x)
        {
            assert false : x;
        }
        catch (InvocationTargetException x)
        {
            assert false : x;
            if (x.getTargetException() instanceof RuntimeException)
                throw (RuntimeException)x.getTargetException();
        }
        fixupMap(m, bean);
        return m;
    }


    @Override
    public K handle(ResultSet rs) throws SQLException
    {
        Map<String, Object> map = ResultSetUtil.mapRow(rs);
        return fromMap(map);
    }

    @Override
    public ArrayList<K> handleArrayList(ResultSet rs) throws SQLException
    {
        ResultSetMetaData md = rs.getMetaData();
        int count = md.getColumnCount();
        CaseInsensitiveHashMap<String> propMap = new CaseInsensitiveHashMap<>(count * 2);
        for (String prop : _writeableProperties)
            propMap.put(prop, prop);

        String[] properties = new String[count + 1];
        for (int i = 1; i <= count; i++)
        {
            String label = md.getColumnLabel(i);
            String prop = propMap.get(label); //Map to correct casing...
            if (null != prop)
                properties[i] = prop;
        }

        ArrayList<K> list = new ArrayList<>();

        try
        {
            while (rs.next())
            {
                K bean = _class.newInstance();

                for (int i = 1; i <= count; i++)
                {
                    String prop = properties[i];
                    if (null == prop)
                        continue;
                    try
                    {
                        Object value = rs.getObject(i);
                        if (value instanceof Double)
                            value = ResultSetUtil.mapDatabaseDoubleToJavaDouble((Double) value);
                        BeanUtils.copyProperty(bean, prop, value);
                    }
                    catch (ConvertHelper.ContainerConversionException e)
                    {
                        // Rethrow exception as-is
                        throw e;
                    }
                    catch (IllegalAccessException | InvocationTargetException e)
                    {
                        throw new IllegalStateException("Failed to copy property '" + prop + "' on class " + _class.getName(), e);
                    }
                    catch (ConversionException e)
                    {
                        // This addresses #22762. I don't like this hack at all, but we can't blow up if java upgrade code touches a bean before all the corresponding database columns are finalized.
                        if (!ModuleLoader.getInstance().isUpgradeInProgress() || !"No value specified".equals(e.getMessage()))
                            throw new IllegalStateException("Failed to copy property '" + prop + "' on class " + _class.getName(), e);
                    }
                }

                fixupBean(bean);
                list.add(bean);
            }
        }
        catch (InstantiationException | IllegalAccessException x)
        {
            assert false : "unexpected exception";
        }

        return list;
    }


    @Override
    public K[] handleArray(ResultSet rs) throws SQLException
    {
        ArrayList<K> list = handleArrayList(rs);
        K[] array = (K[]) Array.newInstance(_class, list.size());
        return list.toArray(array);
    }


    protected void fixupMap(Map<String, Object> m, K o)
    {
    }


    protected void fixupBean(K o)
    {
        if (o instanceof InitializingBean)
        {
            try
            {
                ((InitializingBean)o).afterPropertiesSet();
            }
            catch (Exception x)
            {
                if (x instanceof RuntimeException)
                    throw (RuntimeException)x;
                 throw new RuntimeException(x);
            }
        }
    }
}
