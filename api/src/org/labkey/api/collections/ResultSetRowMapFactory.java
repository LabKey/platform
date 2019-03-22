/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

import org.labkey.api.data.CachedResultSet;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.util.ResultSetUtil;

import java.beans.Introspector;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
* User: adam
* Date: May 4, 2009
* Time: 1:11:17 PM
*/
public class ResultSetRowMapFactory extends RowMapFactory<Object> implements Serializable
{
    private boolean _convertBigDecimalToDouble = false;


    public static ResultSetRowMapFactory create(ResultSet rs) throws SQLException
    {
        ResultSetMetaData md = rs.getMetaData();

        if (rs instanceof CachedResultSet)
        {
            return new ResultSetRowMapFactory(md)
            {
                @Override
                public RowMap<Object> getRowMap(ResultSet rs) throws SQLException
                {
                    return (RowMap<Object>)((CachedResultSet)rs).getRowMap();
                }
            };
        }

        return new ResultSetRowMapFactory(md);
    }


    public static ResultSetRowMapFactory create(ResultSetMetaData md) throws SQLException
    {
        return new ResultSetRowMapFactory(md);
    }


    private ResultSetRowMapFactory(ResultSetMetaData md) throws SQLException
    {
        super(md.getColumnCount() + 1);

        int count = md.getColumnCount();
        Map<String, Integer> findMap = getFindMap();
        findMap.put("_row", 0);  // We're going to stuff the current row index at index 0

        for (int i = 1; i <= count; i++)
        {
            String propName = md.getColumnLabel(i);

            if (propName.length() > 0 && Character.isUpperCase(propName.charAt(0)))
                propName = Introspector.decapitalize(propName);

            findMap.put(propName, i);
        }
    }


    public void setConvertBigDecimalToDouble(boolean b)
    {
        _convertBigDecimalToDouble = b;
    }


    public RowMap<Object> getRowMap(ResultSet rs) throws SQLException
    {
        RowMap<Object> map = super.getRowMap();

        int len = rs.getMetaData().getColumnCount();

        // Stuff current row into rowMap
        int currentRow = rs.getRow();
        List<Object> _list = map.getRow();

        if (0 == _list.size())
            _list.add(currentRow);
        else
            _list.set(0, currentRow);

        for (int i = 1; i <= len; i++)
        {
            Object o = rs.getObject(i);

            if (o instanceof Clob)
            {
                o = ConvertHelper.convertClobToString((Clob)o);
            }
            // BigDecimal objects are rare, and almost always are converted immediately
            // to doubles for ease of use in Java code; we can take care of this centrally here.
            else if (o instanceof BigDecimal && _convertBigDecimalToDouble)
            {
                BigDecimal dec = (BigDecimal) o;
                o = dec.doubleValue();
            }
            else if (o instanceof Double)
            { 
                double value = ((Number) o).doubleValue();
                o = ResultSetUtil.mapDatabaseDoubleToJavaDouble(value);
            }

            if (i == _list.size())
                _list.add(o);
            else
                _list.set(i, o);
        }

        return map;
    }
}
