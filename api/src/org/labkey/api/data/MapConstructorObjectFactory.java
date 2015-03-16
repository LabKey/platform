package org.labkey.api.data;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.util.ResultSetUtil;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

public class MapConstructorObjectFactory<K> implements ObjectFactory<K>
{
    private static Logger _log = Logger.getLogger(MapConstructorObjectFactory.class);

    private final Class<? extends K> _clss;
    private final Constructor<? extends K> _con;

    // for performance pre-calculate readable/writeable properties
    protected HashSet<String> _readableProperties = null;

    public MapConstructorObjectFactory(Class <? extends K> clss)
    {
        _clss = clss;
        K bean;

        try
        {
            _con = clss.getConstructor(Map.class);
            bean = _con.newInstance(Collections.emptyMap());
        }
        catch (InstantiationException | IllegalAccessException | InvocationTargetException x)
        {
            throw new IllegalArgumentException("MapContstructorObjectFactory requires a public constructor which takes an empty map argument.");
        }
        catch (NoSuchMethodException x)
        {
            throw new IllegalArgumentException("MapContstructorObjectFactory requires a public constructor which takes a single map argument.");
        }

        PropertyDescriptor origDescriptors[] = PropertyUtils.getPropertyDescriptors(bean);
        _readableProperties = new HashSet<>(origDescriptors.length);

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
        }
    }

    @Override
    public K fromMap(Map<String, ?> m)
    {
        if (!(m instanceof CaseInsensitiveHashMap))
            m = new CaseInsensitiveHashMap<>(m);

        try
        {
            return _con.newInstance(m);
        }
        catch (IllegalAccessException | InstantiationException | InvocationTargetException e)
        {
            _log.error("unexpected error", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public K fromMap(K bean, Map<String, ?> m)
    {
        return fromMap(m);
    }

    @Override
    public Map<String, Object> toMap(K bean, @Nullable Map<String, Object> m)
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
        return m;
    }

    @Override
    public K handle(ResultSet rs) throws SQLException
    {
        return fromMap(ResultSetUtil.mapRow(rs));
    }

    @Override
    public ArrayList<K> handleArrayList(ResultSet rs) throws SQLException
    {
        ArrayList<K> list = new ArrayList<>();
        while (rs.next())
        {
            list.add(handle(rs));
        }
        return list;
    }

    @Override
    public K[] handleArray(ResultSet rs) throws SQLException
    {
        ArrayList<K> list = handleArrayList(rs);
        K[] array = (K[]) Array.newInstance(_clss, list.size());
        return list.toArray(array);
    }
}
