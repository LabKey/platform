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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;

import java.beans.Introspector;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;


public class BuilderObjectFactory<K> implements ObjectFactory<K>
{
    private static Logger _log = Logger.getLogger(BuilderObjectFactory.class);

    private final Class<K> _class;
    private final Class<? extends Builder> _classBuilder;

    // CONSIDER using MethodAndConverter in BeanObjectFactory as well,
    // might be faster than PropertyUtils and BeanUtils
    private static class MethodAndConverter
    {
        final Method method;
        final Class type;
        final Converter converter;
        MethodAndConverter(Method m,Class type)
        {
            method = m;
            this.type = type;
            converter = ConvertUtils.lookup(type);
        }
        void set(Object bean, Object value) throws InvocationTargetException, IllegalAccessException
        {
            if (value instanceof Clob)
            {
                try
                {
                    value = ConvertHelper.convertClobToString((Clob)value);
                }
                catch (SQLException e)
                {
                    throw new InvocationTargetException(e);
                }
            }
            if (null != value && null != converter)
                value = converter.convert(type, value);
            method.invoke(bean, value);
        }
        Object get(Object bean) throws InvocationTargetException, IllegalAccessException
        {
            return method.invoke(bean);
        }
    }

    // for performance pre-calculate readable/writeable properties
    protected final CaseInsensitiveHashMap<MethodAndConverter> _writeableProperties = new CaseInsensitiveHashMap<>();
    protected final CaseInsensitiveHashMap<MethodAndConverter> _readableProperties = new CaseInsensitiveHashMap<>();


    public BuilderObjectFactory(@NotNull Class<K> classReturn, @NotNull Class<? extends Builder> classBuilder)
    {
        _class = classReturn;
        _classBuilder = classBuilder;


        for (Method m : _class.getMethods())
        {
            String name = m.getName();
            if ("getClass".equals(name))
                continue;
            if (m.getParameterTypes().length != 0 || null != m.getAnnotation(Transient.class))
                continue;
            if (!Modifier.isPublic(m.getModifiers()))
                continue;

            if (name.startsWith("get"))
                name = name.substring(3);
            else if (name.startsWith("is"))
                name = name.substring(2);
            else if (name.startsWith("has"))
                name = name.substring(3);
            else
                continue;

            name = Introspector.decapitalize(name);
            _readableProperties.put(name,new MethodAndConverter(m,m.getReturnType()));
        }

        for (Method m : _classBuilder.getMethods())
        {
            if (m.getParameterTypes().length != 1)
                continue;
            if (!Modifier.isPublic(m.getModifiers()))
                continue;
            Class retCls = m.getReturnType();
            if (retCls != Void.TYPE && retCls != _classBuilder)
                continue;
            // check for setField() or has same name as a readable property
            // e.g. setContainer() or container()
            String name = m.getName();
            if (name.startsWith("set"))
                name = name.substring(3);
            else if (_readableProperties.containsKey(name))
                /* ok */;
            else
                continue;

            name = Introspector.decapitalize(name);
            _writeableProperties.put(name,new MethodAndConverter(m,m.getParameterTypes()[0]));
        }
   }


    public Object newInstance()
    {
        try
        {
            return _classBuilder.newInstance();
        }
        catch (InstantiationException | IllegalAccessException x)
        {
            throw new RuntimeException(x);
        }
    }


    public K fromMap(Map<String, ?> m)
    {
        Builder<K> builder = (Builder<K>)newInstance();
        if (!(m instanceof CaseInsensitiveHashMap))
            m = new CaseInsensitiveHashMap<>(m);

        for (Map.Entry<String,MethodAndConverter> e : _writeableProperties.entrySet())
        {
            String name = e.getKey();
            MethodAndConverter setter = e.getValue();
            Object value = null;
            try
            {
                // If the map contains the key, assuming that we should use the map's value, even if it's null.
                // Otherwise, don't set a value on the bean.
                if (m.containsKey(name))
                {
                    value = m.get(name);
                    setter.set(builder,value);
                }
            }
            catch (IllegalAccessException | InvocationTargetException x)
            {
                assert null == "unexpected exception";
            }
            catch (IllegalArgumentException x)
            {
                _log.error("could not set property: " + name + "=" + String.valueOf(value), x);
            }
        }

        return builder.build();
    }


    @Override
    public K fromMap(K bean, Map<String, ?> m)
    {
        throw new UnsupportedOperationException("Not yet implemented");
    }


    @Override
    public @NotNull Map<String, Object> toMap(K bean, @Nullable Map<String, Object> m)
    {
        return _toMap(bean, m, false);
    }


    protected @NotNull Map<String, Object> _toMap(K bean, @Nullable Map<String, Object> m, boolean stringify)
    {
        try
        {
            if (null == m)
                m = new CaseInsensitiveHashMap<>();

            for (Map.Entry<String,MethodAndConverter> e : _readableProperties.entrySet())
            {
                String name = e.getKey();
                MethodAndConverter getter = e.getValue();

                Object value = getter.get(bean);
                if (stringify)
                    value = ConvertUtils.convert(value);
                m.put(name, value);
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
        MethodAndConverter[] setters = new MethodAndConverter[count + 1];
        for (int i = 1; i <= count; i++)
        {
            String label = md.getColumnLabel(i);
            MethodAndConverter m = _writeableProperties.get(label);
            if (null != m)
                setters[i] = m;
        }

        ArrayList<K> list = new ArrayList<>();

        try
        {
            while (rs.next())
            {
                Builder<K> builder = _classBuilder.newInstance();

                for (int i = 1; i <= count; i++)
                {
                    MethodAndConverter setter = setters[i];
                    if (null == setter)
                        continue;
                    try
                    {
                        Object value = rs.getObject(i);
                        if (value instanceof Double)
                            value = ResultSetUtil.mapDatabaseDoubleToJavaDouble((Double) value);
                        setter.set(builder,value);
                    }
                    catch (ConvertHelper.ContainerConversionException e)
                    {
                        throw new ConvertHelper.ContainerConversionException(e.getMessage());
                    }
                    catch (IllegalAccessException | InvocationTargetException e)
                    {
                        throw new IllegalStateException("Failed to copy property '" + setter + "' on class " + _classBuilder.getName(), e);
                    }
                    catch (ConversionException e)
                    {
                        // This addresses #22762. I don't like this hack at all, but we can't blow up if java upgrade code touches a bean before all the corresponding database columns are finalized.
                        if (!ModuleLoader.getInstance().isUpgradeInProgress() || !"No value specified".equals(e.getMessage()))
                            throw new IllegalStateException("Failed to copy property '" + setter + "' on class " + _classBuilder.getName(), e);
                    }
                }

                list.add(builder.build());
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


    // TESTS

    public static class Foo extends Assert
    {
        final private String _s;
        final private int _i;
        final private double _f;
        public Foo()
        {
            _s = ""; _i=0; _f=0.0;
        }
        private Foo(String s, int i, double f)
        {
            _s = s; _i = i; _f = f;
        }

        public String getS()
        {
            return _s;
        }

        public int getI()
        {
            return _i;
        }

        public double getF()
        {
            return _f;
        }
    }
    public static class FooBuilder implements Builder<Foo>
    {
        private String _s;
        private int _i;
        private double _f;

        @Override
        public Foo build()
        {
            return new Foo(_s,_i,_f);
        }

        public void setS(String s)
        {
            _s = s;
        }

        public FooBuilder setI(int i)
        {
            _i = i;
            return this;
        }

        public FooBuilder f(double f)
        {
            _f = f;
            return this;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void fromMap() throws Exception
        {
            ObjectFactory<Foo> f = new BuilderObjectFactory<>(Foo.class, FooBuilder.class);
            Map<String, Object> m = PageFlowUtil.mapInsensitive("s", "ONE", "i", 1, "f", 1.0);
            Foo foo = f.fromMap(m);
            assertEquals("ONE", foo.getS());
            assertEquals(1, foo.getI());
            assertEquals(String.valueOf(1.0), String.valueOf(foo.getF()));
        }
        @Test
        public void toMap() throws Exception
        {
            Foo foo = new Foo("ONE", 1, 1.0);
            ObjectFactory<Foo> f = new BuilderObjectFactory<>(Foo.class, FooBuilder.class);
            Map<String, Object> m = f.toMap(foo, new CaseInsensitiveHashMap<Object>());
            assertEquals("ONE", m.get("s"));
            assertEquals(1, m.get("I"));
            assertEquals(String.valueOf(1.0), String.valueOf(m.get("F")));
        }
    }
}
