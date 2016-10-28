/*
 * Copyright (c) 2003-2016 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.CachedResultSet;
import org.labkey.api.data.CachedResultSets;
import org.labkey.api.data.ResultSetMetaDataImpl;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


public class ResultSetUtil
{
    private static Logger _log = Logger.getLogger(ResultSetUtil.class);

    private ResultSetUtil()
    {
    }

    
    public static ResultSet close(@Nullable ResultSet rs)
    {
        if (null == rs)
            return null;

        try
        {
            rs.close();
            return null;
        }
        catch (SQLException x)
        {
            _log.error("unexpected error", x);
            return rs;
        }
    }


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


    // Just for testing purposes... splats ResultSet meta data to log
    public static void logMetaData(ResultSet rs)
    {
        try
        {
            ResultSetMetaData md = rs.getMetaData();

            for (int i = 1; i <= md.getColumnCount(); i++)
            {
                _log.info("Name: " + md.getColumnName(i));
                _log.info("Label: " + md.getColumnLabel(i));
                _log.info("Type: " + md.getColumnType(i));
                _log.info("Display Size: " + md.getColumnDisplaySize(i));
                _log.info("Type Name: " + md.getColumnTypeName(i));
                _log.info("Precision: " + md.getPrecision(i));
                _log.info("Scale: " + md.getScale(i));
                _log.info("Schema: " + md.getSchemaName(i));
                _log.info("Table: " + md.getTableName(i));
                _log.info("========================");
            }
        }
        catch (SQLException e)
        {
            _log.error("logMetaData: " + e);
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
                StringBuilder sb = new StringBuilder();

                ResultSetMetaData md = rs.getMetaData();
                int columnCount = md.getColumnCount();

                sb.append('\n');

                for (int i = 1; i <= columnCount; i++)
                {
                    sb.append(md.getColumnName(i)).append(" ");
                }

                sb.append('\n');

                while (rs.next())
                {
                    for (int i = 1; i <= columnCount; i++)
                    {
                        Object value = rs.getObject(i);
                        sb.append(null == value ? "-" : value.toString()).append(" ");
                    }

                    sb.append('\n');
                }

                log.info(sb);
            }
        }
        catch (SQLException e)
        {
            log.error("logMetaData: " + e);
        }
        finally
        {
            close(rs);
        }
    }


    /* copied from AliasManager, should have shared JS XML specific versions */
    private static boolean isLegalNameChar(char ch, boolean first)
    {
        if (ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z' || ch == '_')
            return true;
        if (first)
            return false;
        if (ch >= '0' && ch <= '9')
            return true;
        return false;
    }


    public static String legalNameFromName(String str)
    {
        StringBuilder buf = null;

        for (int i = 0; i < str.length(); i++)
        {
            if (isLegalNameChar(str.charAt(i), i == 0))
                continue;
            if (buf == null)
            {
                buf = new StringBuilder(str.length());
            }
            buf.append(str.substring(buf.length(), i));
            buf.append("_");
        }

        if (buf == null)
            return str;

        buf.append(str.substring(buf.length(), str.length()));
        return buf.toString();
    }


    private static String legalJsName(String name)
    {
        // UNDONE: handle JS specific cases (keywords)
        return legalNameFromName(name);
    }


    private static String legalXMLName(String name)
    {
        // UNDONE: handle XML specific cases
        return legalNameFromName(name);
    }


    public static void exportAsJSON(Writer out, ResultSet rs) throws SQLException, IOException
    {
        ResultSetMetaData md = rs.getMetaData();
        ExportCol[] cols = new ExportCol[md.getColumnCount()+1];
        int columnCount = md.getColumnCount();

        for (int i = 1; i <= columnCount; i++)
        {
            String label = md.getColumnLabel(i);
            String legalLabel = legalJsName(label);
            cols[i] = new ExportCol(legalLabel + ":", i == columnCount ? "" : ",");
        }

        ExportResultSet export = new ExportResultSet()
        {
            SimpleDateFormat formatTZ = new SimpleDateFormat("d MMM yyyy HH:mm:ss Z");
            
            void writeNull() throws IOException
            {
                _out.write("null");
            }

            void writeString(String s) throws IOException
            {
                _out.write(PageFlowUtil.jsString(s));
            }

            void writeDate(Date d) throws IOException
            {
                _out.write("new Date(");
                _out.write(String.valueOf(d.getTime()));
                _out.write(")");
            }

//            SimpleDateFormat formatTZ = new SimpleDateFormat("d MMM yyyy HH:mm:ss Z");
//
//            void writeDate(Date d) throws IOException
//            {
//                _out.write("new Date('");
//                _out.write(formatTZ.format(d));
//                _out.write("')");
//            }

//            void writeDate(Date d) throws IOException
//            {
//                _out.write("'@");
//                _out.write(String.valueOf(d.getTime()));
//                _out.write("@'");
//            }

            void writeObject(Object o) throws IOException
            {
                _out.write(ConvertUtils.convert(o));
            }
        };

        out.write("[");
        export.write(out, rs, "{", "}", ",", cols);
        out.write("]");
    }


    /** Writer should be UTF-8 */
    public static void exportAsXML(Writer out, ResultSet rs, String collectionName, String typeName) throws SQLException, IOException
    {
        collectionName = StringUtils.defaultString(collectionName, "rowset");
        typeName = StringUtils.defaultString(typeName, "row");

        ResultSetMetaData md = rs.getMetaData();
        int columnCount = md.getColumnCount();
        ExportCol[] cols = new ExportCol[columnCount+1];

        for (int i = 1; i <= columnCount; i++)
        {
            String name = md.getColumnName(i);
            String legalName = legalXMLName(name);
            cols[i] = new ExportCol("<" + legalName + ">", "</" + legalName + ">");
        }

        String startRow = "<" + typeName + ">";
        String endRow = "</" + typeName + ">";

        out.write("<" + collectionName + ">\n");

        ExportResultSet export = new ExportResultSet()
        {
            void writeString(String s) throws IOException
            {
                _out.write(encodeXml(s));
            }

            void writeObject(Object o) throws IOException
            {
                _out.write(ConvertUtils.convert(o));
            }
        };

        export.write(out, rs, startRow, endRow, "\n", cols);
        out.write("</" + collectionName + ">\n");
    }


    public static class ExportCol
    {
        ExportCol(String pre, String post)
        {
            prefix = pre;
            postfix = post;
        }

        String prefix;
        String postfix;
    }

    
    static class ExportResultSet
    {
        Writer _out;

        void writeNull() throws IOException
        {
        }

        void writeString(String s) throws IOException
        {
            _out.write(s);
        }

        void writeDate(Date d) throws IOException
        {
            _out.write(DateUtil.toISO(d));
        }

        void writeObject(Object o) throws IOException
        {
            _out.write(String.valueOf(o));
        }

        void write(Object o) throws IOException
        {
            if (null == o)
                writeNull();
            else if (o instanceof String)
                writeString((String)o);
            else if (o instanceof Date)
                writeDate((Date)o);
            else
                writeObject(o);
        }
        
        /** CONSIDER: wrap TSV and CSV as well? */
        void write(Writer out, ResultSet rs, String startRow, String endRow, String connector, ExportCol[] cols)
                throws SQLException, IOException
        {
            _out = out;
            int columnCount = rs.getMetaData().getColumnCount();
            String and = "";

            while (rs.next())
            {
                _out.write(and);
                _out.write(startRow);

                for (int i = 1; i <= columnCount; i++)
                {
                    _out.write(cols[i].prefix);
                    Object o = rs.getObject(i);
                    write(o);
                    _out.write(cols[i].postfix);
                }

                _out.write(endRow);
                and = connector;
            }
        }
    }

    
    static String encodeXml(String s)
    {
        // is this actually xml compatible???
        return PageFlowUtil.filter(s, false, false);
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
    
    public static class TestCase extends Assert
    {
        @Test
        public void testExport() throws IOException, SQLException
        {
            ArrayList<Map<String, Object>> maps = new ArrayList<>();
            Map<String,Object> m;

            m = new HashMap<>();
            m.put("int", 1);
            m.put("s", "one");
            maps.add(m);
            m = new HashMap<>();
            m.put("int", 2);
            m.put("s", "1<2");
            maps.add(m);
            m = new HashMap<>();
            m.put("int", null);
            m.put("s", null);
            maps.add(m);

            try (ResultSet rs = CachedResultSets.create(new TestMetaData(), false, maps, true))
            {
                StringWriter swXML = new StringWriter(1000);
                rs.beforeFirst();
                exportAsXML(swXML, rs, null, null);
//            System.out.println(swXML);

                StringWriter swJS = new StringWriter(1000);
                rs.beforeFirst();
                exportAsJSON(swJS, rs);
//            System.out.println(swJS);
            }
        }
        
        private class TestMetaData extends ResultSetMetaDataImpl
        {
            TestMetaData()
            {
                ColumnMetaData colInt = new ColumnMetaData();
                colInt.columnName = colInt.columnLabel = "int";
                addColumn(colInt);
                ColumnMetaData colS = new ColumnMetaData();
                colS.columnName = colS.columnLabel = "s";
                addColumn(colS);
            }
        }
    }
}
