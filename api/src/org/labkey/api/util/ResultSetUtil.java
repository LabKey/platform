/*
 * Copyright (c) 2003-2026 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.util;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.CachedResultSet;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.util.logging.LogHelper;

import java.beans.Introspector;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;


public class ResultSetUtil
{
    private static final Logger _log = LogHelper.getLogger(ResultSetUtil.class, "ResultSet metadata and data");
    public static final boolean STRICT_CHECKING = false;  // If true, throws when ResultSets are closed more than once. Clean up ResultSet closing for #34406.

    private ResultSetUtil()
    {
    }

    
    public static ResultSet close(@Nullable ResultSet rs)
    {
        if (null == rs)
            return null;

        try
        {
            if (rs.isClosed())
            {
                if (STRICT_CHECKING)
                    throw new IllegalStateException("ResultSet has already been closed!");
            }
            else
            {
                rs.close();
            }

            return null;
        }
        catch (SQLException x)
        {
            _log.error("unexpected error", x);
            return rs;
        }
    }


    // Note: Always close the ResultSet before closing the Statement. Closing the Statement may attempt to close the
    // ResultSet, which could mean a double close.
    public static void close(Statement stmt)
    {
        if (null == stmt)
            return;

        try
        {
            stmt.close();
        }
        catch (SQLException x)
        {
            _log.error("unexpected error", x);
        }
    }
    

    // Convenience method to convert the current row in a ResultSet to a map.  Do not call this in a loop -- use a ResultSetRowMapFactory or ResultSetIterator instead
    public static Map<String, Object> mapRow(ResultSet rs) throws SQLException
    {
        if (rs instanceof CachedResultSet)
            return ((CachedResultSet)rs).getRowMap();

        ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

        return factory.getRowMap(rs);
    }

    public static Map<String, Integer> populateFindMap(ResultSetMetaData md, Map<String, Integer> findMap) throws SQLException
    {
        findMap.put("_row", 0);  // We're going to stuff the current row index at index 0

        for (int i = 1; i <= md.getColumnCount(); i++)
        {
            String propName = md.getColumnLabel(i);

            if (!propName.isEmpty() && Character.isUpperCase(propName.charAt(0)))
                propName = Introspector.decapitalize(propName);

            findMap.put(propName, i);
        }

        return findMap;
    }

    public static Map<String, Integer> getFindMap(ResultSetMetaData md) throws SQLException
    {
        return populateFindMap(md, new ArrayListMap.FindMap<>(new CaseInsensitiveHashMap<>()));
    }

    // Just for testing purposes... splats ResultSet metadata to log
    @SuppressWarnings("unused")
    public static void logMetaData(ResultSet rs)
    {
        try
        {
            ResultSetMetaData md = rs.getMetaData();

            for (int i = 1; i <= md.getColumnCount(); i++)
            {
                _log.info("Name: {}", md.getColumnName(i));
                _log.info("Label: {}", md.getColumnLabel(i));
                _log.info("Type: {}", md.getColumnType(i));
                _log.info("Display Size: {}", md.getColumnDisplaySize(i));
                _log.info("Type Name: {}", md.getColumnTypeName(i));
                _log.info("Precision: {}", md.getPrecision(i));
                _log.info("Scale: {}", md.getScale(i));
                _log.info("Schema: {}", md.getSchemaName(i));
                _log.info("Table: {}", md.getTableName(i));
                _log.info("========================");
            }
        }
        catch (SQLException e)
        {
            _log.error("logMetaData: {}", String.valueOf(e));
        }
    }


    public static void logData(ResultSet rs)
    {
        logData(rs, _log);
    }


    // Just for testing purposes... splats ResultSet data to log
    public static void logData(ResultSet rs, Logger log)
    {
        try
        {
            if (log.isInfoEnabled())
            {
                log.info(getData(rs));
            }
        }
        finally
        {
            close(rs);
        }
    }

    private static final String SEPARATOR = "\t";

    // Callers must close the ResultSet
    public static StringBuilder getData(ResultSet rs)
    {
        try
        {
            StringBuilder sb = new StringBuilder();

            ResultSetMetaData md = rs.getMetaData();
            int columnCount = md.getColumnCount();

            sb.append('\n');

            for (int i = 1; i <= columnCount; i++)
            {
                sb.append(md.getColumnName(i)).append(SEPARATOR);
            }

            sb.append('\n');

            while (rs.next())
            {
                for (int i = 1; i <= columnCount; i++)
                {
                    Object value = rs.getObject(i);
                    sb.append(null == value ? "-" : value.toString().trim()).append(SEPARATOR);
                }

                sb.append('\n');
            }

            return sb;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public static final double POSITIVE_INFINITY_DB_VALUE = 1e300;
    public static final double NEGATIVE_INFINITY_DB_VALUE = -POSITIVE_INFINITY_DB_VALUE;
    public static final double NAN_DB_VALUE = -1e306;

    public static double mapJavaDoubleToDatabaseDouble(double javaDouble)
    {
        if (Double.NEGATIVE_INFINITY == javaDouble)
            return NEGATIVE_INFINITY_DB_VALUE;
        else if (Double.POSITIVE_INFINITY == javaDouble)
            return POSITIVE_INFINITY_DB_VALUE;
        else if (Double.isNaN(javaDouble))
            return NAN_DB_VALUE;
        else
            return javaDouble;
    }

    public static double mapDatabaseDoubleToJavaDouble(double databaseValue)
    {
        if (NEGATIVE_INFINITY_DB_VALUE == databaseValue)
            return Double.NEGATIVE_INFINITY;
        else if (POSITIVE_INFINITY_DB_VALUE == databaseValue)
            return Double.POSITIVE_INFINITY;
        else if (NAN_DB_VALUE == databaseValue)
            return Double.NaN;
        else
            return databaseValue;
    }
}
