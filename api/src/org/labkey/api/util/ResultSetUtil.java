/*
 * Copyright (c) 2003-2009 Fred Hutchinson Cancer Research Center
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

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.beanutils.ConvertUtils;
import static org.apache.commons.collections.IteratorUtils.filteredIterator;
import static org.apache.commons.collections.IteratorUtils.toList;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.*;
import org.labkey.api.security.ACL;
import org.labkey.api.security.PermissionsMap;
import org.labkey.api.security.User;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.*;


public class ResultSetUtil
{
    private static Logger _log = Logger.getLogger(ResultSetUtil.class);

    private ResultSetUtil()
    {
    }

    
    public static ResultSet close(ResultSet rs)
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
    

    public static ResultSet filter(ResultSet in, Predicate pred)
    {
        Iterator<Map> it;
        boolean isComplete = true;

        if (in instanceof Table.TableResultSet)
        {
            it = ((Table.TableResultSet)in).iterator();
            isComplete = ((Table.TableResultSet)in).isComplete();
        }
        else
        {
            it = new ResultSetIterator(in);
        }

        //noinspection unchecked
        List<Map<String,Object>> accepted = toList(filteredIterator(it, pred));

        try
        {
            return new CachedRowSetImpl(in.getMetaData(), accepted, isComplete);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static ResultSet filter(ResultSet in, final User u, final PermissionsMap<Map> fn, final int required)
    {
        Predicate pred = new Predicate()
        {
            public boolean evaluate(Object object)
            {
                int perm = fn.getPermissions(u, (Map)object);
                return (perm & required) == required;
            }
        };
        return filter(in, pred);
    }


    public static class OwnerPermissions implements PermissionsMap<Map>
    {
        private String _ownerColumn;
        private int _ownerPermissions;

        public OwnerPermissions(String ownerColumn, int ownerPermissions)
        {
            _ownerColumn = ownerColumn;
            _ownerPermissions = ownerPermissions;
        }

        public int getPermissions(User u, Map t)
        {
            Integer ownerid = (Integer)t.get(_ownerColumn);
            if (null != ownerid && u.getUserId() == ownerid)
                return _ownerPermissions;
            return 0;
        }
    }


    public static class MapPermissions implements PermissionsMap<Map>
    {
        private String _keyColumn;
        private Map<Object,ACL> _aclMap;
        private int _defaultPermissions;

        private HashMap<Object,Integer> _permMap;

        /** aclMap may be used across users/calls, HOWEVER, this class should not
         * be reused, as we cache per user information.
         *
         * @param keyColumn
         * @param aclMap
         * @param defaultPermissions
         */
        public MapPermissions(String keyColumn, Map<Object, ACL> aclMap, int defaultPermissions)
        {
            _keyColumn = keyColumn;
            _aclMap = aclMap;
            _defaultPermissions = defaultPermissions;
            _permMap = new HashMap<Object,Integer>(aclMap.size() * 2);
        }

        public int getPermissions(User u, Map t)
        {
            Object key = t.get(_keyColumn);
            Integer perm = _permMap.get(key);
            if (null == perm)
            {
                ACL acl = _aclMap.get(key);
                if (null == acl)
                    perm = _defaultPermissions;
                else
                    perm = acl.getPermissions(u);
                _permMap.put(key,perm);
            }
            return perm;
        }
    }


    public static class UnionPermissions implements PermissionsMap<Map>
    {
        PermissionsMap<Map>[] _mappers;

        public UnionPermissions(PermissionsMap<Map>... mappers)
        {
            _mappers = mappers;
        }

        public int getPermissions(User u, Map t)
        {
            int perm = 0;
            for (PermissionsMap<Map> mapper : _mappers)
                perm |= mapper.getPermissions(u, t);
            return perm;
        }
    }


    // Convenience method to convert the current row in a ResultSet to a map.  Do not call this in a loop -- new up a ResultSetRowMap instead
    public static Map<String, Object> mapRow(ResultSet rs) throws SQLException
    {
        if (rs instanceof CachedRowSetImpl)
            return ((CachedRowSetImpl)rs).getRowMap();
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


    // Just for testing purposes... splats ResultSet data to log
    public static void logData(ResultSet rs)
    {
        try
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

                    sb.append(null == value ? "-" : value.toString().trim()).append(" ");
                }

                sb.append('\n');
            }

            _log.info(sb);
        }
        catch (SQLException e)
        {
            _log.error("logMetaData: " + e);
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
        for (int i = 0; i < str.length(); i ++)
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
        for (int i=1 ; i<= columnCount; i++)
        {
            String name = md.getColumnName(i);
            String legalName = legalJsName(name);
            cols[i] = new ExportCol(legalName+":", i==columnCount?"":",");
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
        for (int i=1 ; i<= columnCount; i++)
        {
            String name = md.getColumnName(i);
            String legalName = legalXMLName(name);
            cols[i] = new ExportCol("<"+legalName+">", "</" + legalName + ">");
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
                for (int i=1 ; i<=columnCount ; i++)
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

    public static double mapJavaDoubleToDatabaseDouble(double javaDouble)
    {
        if (Double.NEGATIVE_INFINITY == javaDouble)
            return NEGATIVE_INFINITY_DB_VALUE;
        else if (Double.POSITIVE_INFINITY == javaDouble)
            return POSITIVE_INFINITY_DB_VALUE;
        else
            return javaDouble;
    }

    public static double mapDatabaseDoubleToJavaDouble(double databaseValue)
    {
        if (NEGATIVE_INFINITY_DB_VALUE == databaseValue)
            return Double.NEGATIVE_INFINITY;
        else if (POSITIVE_INFINITY_DB_VALUE == databaseValue)
            return Double.POSITIVE_INFINITY;
        else
            return databaseValue;
    }
    
    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }


        public TestCase(String name)
        {
            super(name);
        }


        public void testExport()
                throws IOException, SQLException
        {
            ArrayList<Map<String,Object>> maps = new ArrayList<Map<String,Object>>();
            Map<String,Object> m;

            m = new HashMap<String,Object>();
            m.put("int", 1);
            m.put("s", "one");
            maps.add(m);
            m = new HashMap<String,Object>();
            m.put("int", 2);
            m.put("s", "1<2");
            maps.add(m);
            m = new HashMap<String,Object>();
            m.put("int", null);
            m.put("s", null);
            maps.add(m);

            ResultSet rs = new CachedRowSetImpl(new TestMetaData(), maps, true);

            StringWriter swXML = new StringWriter(1000);
            rs.beforeFirst();
            exportAsXML(swXML, rs, null, null);
            System.out.println(swXML);

            StringWriter swJS = new StringWriter(1000);
            rs.beforeFirst();
            exportAsJSON(swJS, rs);
            System.out.println(swJS);

            rs.close();
        }
        

        class TestMetaData extends ResultSetMetaDataImpl
        {
            TestMetaData()
            {
                ColumnMetaData colInt = new ColumnMetaData();
                colInt.columnName = "int";
                addColumn(colInt);
                ColumnMetaData colS = new ColumnMetaData();
                colS.columnName = "s";
                addColumn(colS);
            }
        }

        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
