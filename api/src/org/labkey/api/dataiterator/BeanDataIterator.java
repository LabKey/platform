/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
package org.labkey.api.dataiterator;

import org.apache.commons.beanutils.PropertyUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.query.BatchValidationException;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * User: matthewb
 * Date: May 23, 2011
 * Time: 5:01:51 PM
 */
public class BeanDataIterator<K> extends AbstractDataIterator implements DataIterator
{
    final Class _class;
    ArrayList<ColumnInfo> _cols = new ArrayList<>();
    ArrayList<Method> _readMethods = new ArrayList<>();
    final List<K> _rows;
    int _currentRow = -1;


    public BeanDataIterator(DataIteratorContext context, Class cls, List<K> rows)
    {
        super(context);

        this._class = cls;
        _cols.add(new ColumnInfo("_rowNumber", JdbcType.INTEGER));
        _readMethods.add(null);
        K bean = rows.isEmpty() ? null : rows.get(0);

        for (PropertyDescriptor origDescriptor : PropertyUtils.getPropertyDescriptors(cls))
        {
            String name = origDescriptor.getName();
            if ("class".equals(name))
                continue;
            if (null == bean || PropertyUtils.isReadable(bean, name))
            {
                Method readMethod = origDescriptor.getReadMethod();
                if (readMethod != null && readMethod.getParameterTypes().length == 0)
                {
                    _readMethods.add(readMethod);
                    _cols.add(new ColumnInfo(name, JdbcType.OTHER));
                }
            }
        }

        _rows = rows;
    }


    @Override
    public int getColumnCount()
    {
        return _cols.size() - 1;
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        return _cols.get(i);
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        return ++_currentRow < _rows.size();
    }

    @Override
    public Object get(int i)
    {
        if (i == 0)
            return _currentRow+1;
        K bean = _rows.get(_currentRow);
        Method m = _readMethods.get(i);
        try
        {
            return m.invoke(bean);
        }
        catch (IllegalAccessException x)
        {
            throw new RuntimeException(x);
        }
        catch (InvocationTargetException x)
        {
            Throwable cause = null == x.getCause() ? x : x.getCause();
            if (cause instanceof RuntimeException)
                throw (RuntimeException)cause;
            else
                throw new RuntimeException(cause);
        }
    }

    @Override
    public void close() throws IOException
    {
    }

    @Override
    public void debugLogInfo(StringBuilder sb)
    {
        sb.append(this.getClass().getName() + "\n");
        sb.append("    " + _class.getName());
    }


    public static class Builder<K> implements DataIteratorBuilder
    {
        Class cls;
        List<K> rows;

        public Builder(Class cls, List<K> rows)
        {
            this.cls = cls; this.rows = rows;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            return new BeanDataIterator<>(context, cls, rows);
        }
    }
}
