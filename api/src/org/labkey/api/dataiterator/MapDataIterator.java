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

import org.apache.log4j.Logger;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.BatchValidationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

/**
 * User: matthewb
 * Date: 2011-09-07
 * Time: 5:32 PM
 */
public interface MapDataIterator extends DataIterator
{
    boolean supportsGetMap();
    Map<String,Object> getMap();

    static class MapDataIteratorImpl implements MapDataIterator, ScrollableDataIterator
    {
        DataIterator _input;
        boolean _mutable;

        final ArrayListMap.FindMap<Object> _findMap;
        ArrayListMap<String,Object> _currentMap;

        MapDataIteratorImpl(DataIterator in, boolean mutable)
        {
            _input = in;
            _mutable = mutable;
            Map map = new CaseInsensitiveHashMap<Integer>(in.getColumnCount()*2);
            _findMap = new ArrayListMap.FindMap<>((Map<Object,Integer>)map);
            for (int i=0 ; i<=in.getColumnCount() ; i++)
            {
                String name = in.getColumnInfo(i).getName();
                if (_findMap.containsKey(name))
                {
                    Logger.getLogger(MapDataIterator.class).warn("Map already has column named '" + name + "'");
                    continue;
                }
                _findMap.put(in.getColumnInfo(i).getName(),i);
            }
        }

        @Override
        public String getDebugName()
        {
            return "MapDataIterator";
        }

        @Override
        public boolean supportsGetMap()
        {
            return true;
        }

        @Override
        public Map<String, Object> getMap()
        {
            if (null == _currentMap)
            {
                ArrayList<Object> list = new ArrayList<>(_input.getColumnCount()+1);
                for (int i=0 ; i<=_input.getColumnCount() ; i++)
                    list.add(_input.get(i));
                _currentMap = new ArrayListMap(_findMap, list);
            }
            boolean debug = false;
            assert debug = true;
            return (!_mutable && debug) ? Collections.unmodifiableMap(_currentMap) : _currentMap;
        }

        @Override
        public int getColumnCount()
        {
            return _input.getColumnCount();
        }

        @Override
        public ColumnInfo getColumnInfo(int i)
        {
            return _input.getColumnInfo(i);
        }

        @Override
        public boolean isConstant(int i)
        {
            return false;
        }

        @Override
        public Object getConstantValue(int i)
        {
            return null;
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            _currentMap = null;
            return _input.next();
        }

        @Override
        public Object get(int i)
        {
            return _input.get(i);
        }

        @Override
        public boolean isScrollable()
        {
            return (_input instanceof ScrollableDataIterator) && ((ScrollableDataIterator)_input).isScrollable();
        }

        @Override
        public void beforeFirst()
        {
            _currentMap = null;
            ((ScrollableDataIterator)_input).beforeFirst();
        }

        @Override
        public void close() throws IOException
        {
            _input.close();
        }


        @Override
        public void debugLogInfo(StringBuilder sb)
        {
            sb.append(this.getClass().getName() + "\n");
            if (null != _input)
                _input.debugLogInfo(sb);
        }
    }
}