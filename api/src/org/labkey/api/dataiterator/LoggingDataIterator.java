/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.BatchValidationException;

import java.io.IOException;
import java.util.Formatter;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Wrapper around some other DataIterator that provides Log4J-based logging of the values that are passing through.
 * User: matthewb
 * Date: 2011-05-27
 */
public class LoggingDataIterator extends AbstractDataIterator implements ScrollableDataIterator, MapDataIterator
{
    static Logger _staticLog = Logger.getLogger(LoggingDataIterator.class);
    Logger _log = _staticLog;
    Level _pri = Level.DEBUG;

    DataIterator _data;


    public static DataIterator wrap(DataIterator in)
    {
        if (in instanceof LoggingDataIterator)
            return in;
        if (_staticLog.isEnabledFor(Level.DEBUG))
            return new LoggingDataIterator(in, _staticLog);
        return in;
    }

    public LoggingDataIterator(DataIterator in, Logger log)
    {
        super(null);
        _data = in;
        _log = log;
    }

    @Override
    public int getColumnCount()
    {
        return _data.getColumnCount();
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        return _data.getColumnInfo(i);
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        boolean hasNext = _data.next();
        if (!hasNext)
            return false;

        StringBuilder sb = new StringBuilder();
        Formatter formatter = new Formatter(sb);

        String debugName = _data.getDebugName() + " : " + _data.getClass().getName();
        sb.append(debugName).append("\n");

        for (int i=0 ; i<=_data.getColumnCount() ; i++)
        {
            String name = _data.getColumnInfo(i).getName();
            if (name.length() > 50)
                name = name.substring(name.length()-50);
            Object value = _data.get(i);
            String cls = null == value ? "NULL" : value.getClass().getSimpleName();
            if (null == value)
                value = "";
            if (value instanceof Map)
                value = value.getClass() + "@" + System.identityHashCode(value);
            formatter.format("%50s %10s| %s\n", name, cls, value);
        }

        if (supportsGetMap())
        {
            Map<String,Object> map = getMap();
            JSONObject json = new JSONObject(map);
            // avoid recursion bombs
            json.remove("extraProperties");
            sb.append(json.toString());
            sb.append("\n");
        }

        _log.log(_pri, sb.toString());

        return true;
    }

    @Override
    public Object get(int i)
    {
        return _data.get(i);
    }

    @Override
    public Supplier<Object> getSupplier(int i)
    {
        return _data.getSupplier(i);
    }

    @Override
    public void close() throws IOException
    {
        _data.close();
    }

    @Override
    public boolean isScrollable()
    {
        return _data instanceof ScrollableDataIterator && ((ScrollableDataIterator)_data).isScrollable();
    }

    @Override
    public void beforeFirst()
    {
        ((ScrollableDataIterator)_data).beforeFirst();
    }

    @Override
    public boolean supportsGetMap()
    {
        return _data instanceof MapDataIterator && ((MapDataIterator)_data).supportsGetMap();
    }

    @Override
    public Map<String, Object> getMap()
    {
        return ((MapDataIterator)_data).getMap();
    }

    @Override
    public void debugLogInfo(StringBuilder sb)
    {
        sb.append(this.getClass().getName() + "\n");
        if (null != _data)
            _data.debugLogInfo(sb);
    }
}
