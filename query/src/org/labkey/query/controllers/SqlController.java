/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.query.controllers;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Results;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.DateUtil;
import org.springframework.beans.PropertyValue;
import org.springframework.validation.BindException;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Date;
import java.util.Map;

/**
 * API access to labkey SQL capability without all the display QueryView custom view stuff.
 */
public class SqlController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SqlController.class);

    public SqlController() throws Exception
    {
        setActionResolver(_actionResolver);
    }


    public static class Parameters
    {
        private Map<String,Object> map = new CaseInsensitiveHashMap<>();

        @JsonAnySetter
        public void set(String name, Object value)
        {
            map.put(name, value);
        }
    }

    public static class SqlForm
    {
        private String schema;
        private String sql;
        private String sep = null;
        private String eol = null;
        private boolean compact = false;
        private Parameters parameters = new Parameters();

        public String getSchema()
        {
            return schema;
        }

        public void setSchema(String schema)
        {
            this.schema = schema;
        }

        public String getSql()
        {
            return sql;
        }

        public void setSql(String sql)
        {
            this.sql = sql;
        }

        public Parameters getParameters()
        {
            return parameters;
        }

        public Map<String,Object> getParameterMap()
        {
            return parameters.map;
        }

        public String getSep()
        {
            return null!=sep ? sep : compact ? "\u001f" : "\t";
        }

        public void setSep(String sep)
        {
            if (null != sep)
                this.sep = sep;
        }

        public String getEol()
        {
            return null!=eol ? eol : compact ? "\u001e" : "\t";
        }

        public void setEol(String eol)
        {
            if (null != eol)
                this.eol = eol;
        }

        public boolean isCompact()
        {
            return compact;
        }

        public void setCompact(boolean compact)
        {
            this.compact = compact;
        }
    }


    @RequiresPermission(ReadPermission.class)
    @Marshal(Marshaller.Jackson)
    public class ExecuteAction extends ApiAction<SqlForm>
    {
        @Override
        public Object execute(SqlForm form, BindException errors) throws ServletException
        {
            String schemaString = form.getSchema();
            SchemaKey schemaKey = null == schemaString ? new SchemaKey(null,"core") : SchemaKey.decode(schemaString);

            { // spring binding doesn't handle this form very well (
                if (!StringUtils.contains(getViewContext().getRequest().getContentType(),"json"))
                {
                    // white space is broken for separators, so rebind just in case
                    if (StringUtils.isNotEmpty((String)getProperty("sep")))
                        form.setSep((String)getProperty("sep"));
                    if (StringUtils.isNotEmpty((String)getProperty("eol")))
                        form.setEol((String)getProperty("eol"));
                    for (PropertyValue pv : getPropertyValues().getPropertyValues())
                    {
                        if (StringUtils.startsWith(pv.getName(),"parameters."))
                        {
                            String name = pv.getName().substring("parameters.".length());
                            Object value = pv.getValue();
                            form.getParameterMap().put(name,value);
                        }
                    }
                }
            }

            QuerySchema schema = DefaultSchema.get(getUser(), getContainer());
            for (String s : schemaKey.getParts())
            {
                schema = schema.getSchema(s);
                if (null == schema)
                {
                    errors.reject(ERROR_MSG, "schema not found: " + form.getSchema());
                    return null;
                }
            }
            ((UserSchema)schema).checkCanReadSchema();

            if (StringUtils.isEmpty(form.getSql()))
            {
                errors.reject(ERROR_MSG, "no sql provided");
                return null;
            }

            try (Results rs = QueryService.get().selectResults(schema, form.getSql(), null, form.getParameterMap(), true, false))
            {
                getViewContext().getResponse().setContentType("text/plain");
                if (form.compact)
                    writeResults_compact(getViewContext().getResponse().getWriter(), rs, form.getSep(),form.getEol());
                else
                    writeResults_text(getViewContext().getResponse().getWriter(), rs, form.getSep(),form.getEol());
            }
            catch (QueryParseException x)
            {
                errors.reject(ERROR_MSG, x.getMessage());
            }
            catch (SQLException|IOException x)
            {
                throw new ServletException(x);
            }
            return null;
        }
    }


    void writeResults_text(PrintWriter out, Results rs, String sep, String eol) throws IOException, SQLException
    {
        final int count = rs.getMetaData().getColumnCount();
        final boolean serializeDateAsNumber=false;

        for (int i = 1; i <= count; i++)
        {
            out.write(rs.getColumn(i).getName());
            out.write(i == count ? eol : sep);
        }

        // pull types from ResultSetMetaData, not ColumnInfo
        JdbcType[] types = new JdbcType[count + 1];
        for (int i = 1; i <= count; i++)
        {
            JdbcType jdbc = JdbcType.valueOf(rs.getMetaData().getColumnType(i));
            types[i] = jdbc;
            out.write(jdbc.name());
            out.write(i == count ? eol : sep);
        }

        while (rs.next())
        {
            for (int column = 1; column <= count; column++)
            {
                // let's try to avoid tons of inspection if possible, and allocating tons of objects
                // handle the most common types
                printValue:
                {
                    switch (types[column])
                    {
                        case TINYINT:
                        case SMALLINT:
                        case INTEGER:
                        {
                            int i = rs.getInt(column);
                            if (!rs.wasNull())
                                out.print(i);
                            break printValue;
                        }
                        case CHAR:
                        case VARCHAR:
                        {
                            String s = rs.getString(column);
                            if (null != s)
                                out.write(s);
                            break printValue;
                        }
                        case DOUBLE:
                        case REAL:
                        {
                            double d = rs.getDouble(column);
                            if (!rs.wasNull())
                                out.print(d);
                            break printValue;
                        }
                        case TIMESTAMP:
                        {
                            Date date = rs.getTimestamp(column);
                            if (null != date)
                            {
                                if (serializeDateAsNumber)
                                    out.print(date.getTime());
                                else
                                    //out.write(DateUtil.formatJsonDateTime(date));
                                    out.write(DateUtil.formatDateTimeISO8601(date));
                            }
                            break printValue;
                        }
                        case BOOLEAN:
                        {
                            boolean b = rs.getBoolean(column);
                            if (!rs.wasNull())
                                out.write(b ? '1' : '0');
                            break printValue;
                        }
                        case DECIMAL:
                        {
                            BigDecimal dec = rs.getBigDecimal(column);
                            if (null != dec)
                                out.write(dec.toPlainString());
                            break printValue;
                        }
                        default:
                        {
                            String obj = rs.getString(column);
                            if (null != obj)
                                out.write(obj);
                            break printValue;
                        }
                    }
                }
                out.write(column == count ? eol : sep);
            }
        }
        out.flush();
    }

    /**
     * try to generate a more compact representation
     *
     *     the value 0x1A SUB means same value as previous row
     *     truncate trailing time from date-only values
     *
     *  Consider disambiguating sql NULL and empty string.  NULL is more common so using a shorter
     *  encoding for NULL and longer for empty string makes sense (e.g. "\u0000")
     *
     *  TODO: binary/blob length prefixed encoding
     *
     * Note that while writeResults_text tries to not generate extra Strings to GC, I think the actual PrintWriter
     * implementation is generating strings inside out.write().  So this is probably not much different from a GC
     * perspective.
     */
    void writeResults_compact(PrintWriter out, Results rs, String sep, String eol) throws IOException, SQLException
    {
        final int count = rs.getMetaData().getColumnCount();
        final boolean serializeDateAsNumber=false;

        for (int i = 1; i <= count; i++)
        {
            out.write(rs.getColumn(i).getName());
            out.write(i == count ? eol : sep);
        }

        // pull types from ResultSetMetaData, not ColumnInfo
        JdbcType[] types = new JdbcType[count + 1];
        for (int i = 1; i <= count; i++)
        {
            JdbcType jdbc = JdbcType.valueOf(rs.getMetaData().getColumnType(i));
            types[i] = jdbc;
            out.write(jdbc.name());
            out.write(i == count ? eol : sep);
        }

        String DITTO = "\u0008";
        String[] prev = new String[count+1];
        String[] row = new String[count+1];

        while (rs.next())
        {
            for (int column = 1; column <= count; column++)
            {
                switch (types[column])
                {
                    case TINYINT:
                    case SMALLINT:
                    case INTEGER:
                    case CHAR:
                    case VARCHAR:
                    case DOUBLE:
                    case REAL:
                    default:
                    {
                        row[column] = rs.getString(column);
                        break;
                    }
                    case TIMESTAMP:
                    {
                        Date date = rs.getTimestamp(column);
                        if (null == date)
                            row[column] = null;
                        else if (serializeDateAsNumber)
                            row[column] = Long.toString(date.getTime());
                        else
                        {
                            String d = DateUtil.formatDateTimeISO8601(date);
                            if (d.endsWith(" 00:00"))
                                d = d.substring(0,d.length()-6);
                            row[column] = d;
                        }
                        break;
                    }
                    case BOOLEAN:
                    {
                        boolean b = rs.getBoolean(column);
                        row[column] = rs.wasNull() ? null : b ? "1": "0";
                        break;
                    }
                    case DECIMAL:
                    {
                        BigDecimal dec = rs.getBigDecimal(column);
                        row[column] = null==dec ? null : dec.toPlainString();
                        break;
                    }
                }
            }
            for (int column = 1; column <= count; column++)
            {
                String s = row[column];
                if (null != s)
                {
                    if (s.equals(prev[column]))
                        out.write(DITTO);
                    else
                        out.write(s);
                }
                out.write(column == count ? eol : sep);
            }
            String[] t = prev;
            prev = row;
            row = t;
        }
        out.flush();
    }
}