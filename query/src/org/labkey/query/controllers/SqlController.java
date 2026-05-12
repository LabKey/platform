/*
 * Copyright (c) 2015-2019 LabKey Corporation
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
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.ontology.Unit;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewServlet;
import org.springframework.beans.PropertyValue;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * API access to labkey SQL capability without all the display QueryView custom view stuff.
 */
public class SqlController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SqlController.class);

    public SqlController()
    {
        setActionResolver(_actionResolver);
    }


    public static class Parameters
    {
        private final Map<String,Object> map = new CaseInsensitiveHashMap<>();

        @JsonAnySetter
        public void set(String name, Object value)
        {
            map.put(name, value);
        }
    }

    public enum Format
    {
        split,  // response that can be parsed using String.split(), configure with 'sep' and 'eol'

        compact,    // same as split, but with ditto markers (cheap way to compress and save space on client)

        //  Tab separated (RFC 4180)
        //   - Wrap a field in double quotes if it contains a tab, newline, or double quote
        //  - Escape double quotes by doubling them: " becomes ""
        //  - Fields that don't contain special characters are left unquoted
        tsv

        // We could support CSV here, I can't think of a scenario for this API where this would be preferable
        // csv     // Comma separated, google sheets style quoting (see PageFlowUtil.joinValuesToStringForExport())
    }

    public static class SqlForm
    {
        private Format format = Format.split;
        private Double apiVersion = null;
        private String schema;
        private String sql;
        private String sep = null;
        private String eol = null;
        private final Parameters parameters = new Parameters();

        public Double getApiVersion()
        {
            return apiVersion;
        }

        public void setApiVersion(Double apiVersion)
        {
            this.apiVersion = apiVersion;
        }

        public String getSchema()
        {
            return schema;
        }

        public void setSchema(String schema)
        {
            this.schema = schema;
        }

        public String getSchemaName()
        {
            return schema;
        }

        public void setSchemaName(String schema)
        {
            this.schema = schema;
        }

        public String getSql()
        {
            return sql;
        }

        public void setSql(String sql)
        {
            this.sql = PageFlowUtil.wafDecode(sql);
        }

        public Parameters getParameters()
        {
            return parameters;
        }

        public Map<String,Object> getParameterMap()
        {
            return parameters.map;
        }

        public Format getFormat()
        {
            return format;
        }

        public void setFormat(Format format)
        {
            this.format = format;
        }

        public String getSep()
        {
            return null!=sep ? sep : format==Format.compact ? "" : "\t";
        }

        public void setSep(String sep)
        {
            if (null != sep)
                this.sep = sep;
        }

        public String getEol()
        {
            return null!=eol ? eol : format==Format.compact ? "" : "\n";
        }

        public void setEol(String eol)
        {
            if (null != eol)
                this.eol = eol;
        }

        public boolean isCompact()
        {
            return Format.compact == format;
        }

        public void setCompact(boolean compact)
        {
            if (compact)
                this.format = Format.compact;
        }
    }

    /// Execute a LabKey SQL query and return results as plain text. Designed for lightweight programmatic access without the overhead of QueryView/JSON API responses.
    ///
    /// Note this is still experimental as this API does not work well with some features.
    /// In particular, some columns rely on custom DisplayColumn implementations to return meaningful data,
    /// and this code path does not use DisplayColumn.  In particular group_concat result (e.g. multi-value foreign keys)
    /// may not render correctly, as well as lineage columns like MaterialInputs/*.
    @RequiresPermission(ReadPermission.class)
    @Marshal(Marshaller.Jackson)
    public class ExecuteAction extends ReadOnlyApiAction<SqlForm>
    {
        JdbcType[] types;
        Unit[] units;

        void initWriter(Results rs) throws SQLException
        {
            final int count = rs.getMetaData().getColumnCount();
            types = new JdbcType[count];
            units = new Unit[count];

            for (int column = 1; column <= count; column++)
            {
                int index = column-1;
                types[index] = JdbcType.valueOf(rs.getMetaData().getColumnType(column));
                ColumnInfo ci = rs.getColumn(column);
                if (null != ci)
                {
                    units[index] = ci.getDisplayUnit();
                }
            }
        }

        void getStringData(Results rs, ArrayList<String> out) throws SQLException
        {
            out.clear();
            for (int column = 1; column <= types.length; column++)
            {
                int index = column - 1;
                String value = null;

                if (null != units[index])
                {
                    Number storageValue = types[index] == JdbcType.DECIMAL ? rs.getBigDecimal(column) : rs.getDouble(column);
                    if (!rs.wasNull())
                        value = String.valueOf(units[index].fromStorageUnitValue(storageValue));
                }
                else
                {
                    switch (types[index])
                    {
                        case TINYINT:
                        case SMALLINT:
                        case INTEGER:
                        {
                            int i = rs.getInt(column);
                            value = rs.wasNull() ? null : String.valueOf(i);
                            break;
                        }
                        case CHAR:
                        case VARCHAR:
                        case LONGVARCHAR:
                        {
                            value = rs.getString(column);
                            break;
                        }
                        case DOUBLE:
                        case REAL:
                        {
                            double d = rs.getDouble(column);
                            value = rs.wasNull() ? null : String.valueOf(d);
                            break;
                        }
                        case BOOLEAN:
                        {
                            boolean b = rs.getBoolean(column);
                            value = rs.wasNull() ? null : b ? "1" : "0";
                            break;
                        }
                        case DECIMAL:
                        {
                            BigDecimal dec = rs.getBigDecimal(column);
                            value = null == dec ? null : dec.toPlainString();
                            break;
                        }
                        case ARRAY:
                        {
                            Array array = rs.getArray(column);
                            if (null != array)
                            {
                                String[] strs = ConvertHelper.convert(array.getArray(), String[].class);
                                if (null != strs)
                                    value = PageFlowUtil.joinValuesToStringForExport(List.of(strs));
                            }
                            break;
                        }
                        default:
                        {
                            value = rs.getString(column);
                            break;
                        }
                    }
                }
                out.add(value);
            }
        }

        void writeResults_text(PrintWriter out, Results rs, String sep, String eol) throws SQLException
        {
            initWriter(rs);
            final int count = rs.getMetaData().getColumnCount();

            // meta-meta-data
            out.write("18.2"+sep+"name"+sep+"jdbcType"+eol);

            for (int column = 1; column <= count; column++)
            {
                out.write(rs.getColumn(column).getName());
                out.write(column == count ? eol : sep);
            }

            for (int column = 1; column <= count; column++)
            {
                int index = column-1;
                out.write(types[index].name());
                out.write(column == count ? eol : sep);
            }

            ArrayList<String> values = new ArrayList<>(count);

            while (rs.next())
            {
                getStringData(rs, values);
                for (int index = 0; index < count; index++)
                {
                    String s = values.get(index);
                    if (null != s)
                        out.write(s);
                    out.write(index == count - 1 ? eol : sep);
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
        void writeResults_compact(PrintWriter out, Results rs, String sep, String eol) throws SQLException
        {
            initWriter(rs);
            final int count = rs.getMetaData().getColumnCount();

            // meta-meta-data
            out.write("18.2"+sep+"name"+sep+"jdbcType"+eol);

            for (int column = 1; column <= count; column++)
            {
                out.write(rs.getColumn(column).getName());
                out.write(column == count ? eol : sep);
            }

            for (int index = 0; index < count; index++)
            {
                out.write(types[index].name());
                out.write(index == count-1 ? eol : sep);
            }

            String DITTO = "";
            ArrayList<String> prev = new ArrayList<>(count);
            ArrayList<String> row = new ArrayList<>(count);

            while (rs.next())
            {
                getStringData(rs, row);

                for (int index = 0; index < count; index++)
                {
                    String s = row.get(index);
                    if (null != s && !s.isEmpty())
                    {
                        if (index < prev.size() && s.equals(prev.get(index)))
                            out.write(DITTO);
                        else
                            out.write(s);
                    }
                    out.write(index == count - 1 ? eol : sep);
                }
                ArrayList<String> t = prev;
                prev = row;
                row = t;
            }
            out.flush();
        }

        /// export a Result set using RFC4180 formatting
        ///  use PageFlowUtil.joinValuesWithTabs4180
        void writeResults_tsv(PrintWriter out, Results rs) throws SQLException
        {
            initWriter(rs);
            final int count = rs.getMetaData().getColumnCount();

            List<String> names = new ArrayList<>(count);
            for (int column = 1; column <= count; column++)
                names.add(rs.getColumn(column).getName());
            out.write(PageFlowUtil.joinValuesWithTabs4180(names));
            out.write('\n');

            ArrayList<String> values = new ArrayList<>(count);

            while (rs.next())
            {
                getStringData(rs, values);
                out.write(PageFlowUtil.joinValuesWithTabs4180(values));
                out.write('\n');
            }
            out.flush();
        }

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
                switch (form.getFormat())
                {
                    case tsv:
                        writeResults_tsv(getViewContext().getResponse().getWriter(), rs);
                        break;
                    case split:
                        writeResults_text(getViewContext().getResponse().getWriter(), rs, form.getSep(),form.getEol());
                        break;
                    case compact:
                        writeResults_compact(getViewContext().getResponse().getWriter(), rs, form.getSep(),form.getEol());
                        break;
                }
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


    /// Like ExecuteAction but routes value rendering through DisplayColumn.getTsvFormattedValue() for correct
    /// type dispatch (e.g. dates as ISO-8601, multi-value columns via their DisplayColumn subclass).
    @RequiresPermission(ReadPermission.class)
    @Marshal(Marshaller.Jackson)
    public class Execute2Action extends ExecuteAction
    {
        DisplayColumn[] dcs;
        RenderContext renderCtx;
        ResultSetRowMapFactory rowMapFactory;

        @Override
        void initWriter(Results rs) throws SQLException
        {
            super.initWriter(rs);
            final int count = rs.getMetaData().getColumnCount();
            dcs = new DisplayColumn[count];
            for (int column = 1; column <= count; column++)
            {
                ColumnInfo col = rs.getColumn(column);
                DisplayColumn dc = col.getDisplayColumnFactory().createRenderer(col);
                dc.setWithLookup(false);
                dc.setFormatString(null);
                dc.setTsvFormatString(null);
                dc.setRequiresHtmlFiltering(false);
                dcs[column - 1] = dc;
            }
            renderCtx = new RenderContext(getViewContext());
            renderCtx.setResults(rs);
            rowMapFactory = ResultSetRowMapFactory.create(rs);
        }

        @Override
        void getStringData(Results rs, ArrayList<String> out) throws SQLException
        {
            out.clear();
            renderCtx.setRow(rowMapFactory.getRowMap(rs));
            for (DisplayColumn dc : dcs)
                out.add(dc.getTsvFormattedValue(renderCtx));
        }
    }


    public static class TestCase extends Assert
    {
        private static final String FOLDER_NAME = "sqlControllerTest";
        private static final String LIST_NAME = "SqlTestList";

        private Container _folder;

        @Before
        public void setUp() throws Exception
        {
            tearDown();
            Assume.assumeTrue("Requires list module", ListService.get() != null);

            User user = TestContext.get().getUser();
            _folder = ContainerManager.ensureContainer(JunitUtil.getTestContainer().getPath() + "/" + FOLDER_NAME, user);

            ListDefinition list = ListService.get().createList(_folder, LIST_NAME, ListDefinition.KeyType.AutoIncrementInteger);
            list.setKeyName("Key");
            list.getDomain().addProperty(new PropertyStorageSpec("Name", JdbcType.VARCHAR));
            list.getDomain().addProperty(new PropertyStorageSpec("Age", JdbcType.INTEGER));
            list.getDomain().addProperty(new PropertyStorageSpec("Score", JdbcType.DOUBLE));

            if (CoreSchema.getInstance().getSqlDialect().isPostgreSQL())
            {
                DomainProperty tagsProp = list.getDomain().addProperty(new PropertyStorageSpec("Tags", JdbcType.VARCHAR));
                tagsProp.setRangeURI(PropertyType.MULTI_CHOICE.getTypeUri());
                IPropertyValidator tcValidator = PropertyService.get().createValidator("urn:lsid:labkey.com:PropertyValidator:textchoice");
                tcValidator.setName("Text Choice Validator");
                tcValidator.setExpressionValue("Red|Green|Blue");
                tagsProp.addValidator(tcValidator);
            }

            list.save(user);

            TableInfo table = DefaultSchema.get(user, _folder).getSchema("lists").getTable(LIST_NAME, null);
            assertNotNull("List table not found", table);

            BatchValidationException errors = new BatchValidationException();
            table.getUpdateService().insertRows(user, _folder, List.of(
                CaseInsensitiveHashMap.<Object>of("Name", "Alice", "Age", 30, "Score", 95.5, "Tags", List.of("Red", "Green")),
                CaseInsensitiveHashMap.<Object>of("Name", "Bob", "Age", 30, "Score", 87.3, "Tags", List.of("Blue")),
                CaseInsensitiveHashMap.<Object>of("Name", "Carol", "Age", 35, "Score", 91.0, "Tags", List.of("Red", "Blue", "Green"))
            ), errors, null, null);
            if (errors.hasErrors())
                fail(errors.getRowErrors().get(0).toString());
        }

        @After
        public void tearDown()
        {
            Container folder = ContainerManager.getForPath(JunitUtil.getTestContainer().getPath() + "/" + FOLDER_NAME);
            if (folder != null)
                ContainerManager.deleteAll(folder, TestContext.get().getUser());
            _folder = null;
        }

        private MockHttpServletResponse executeSql(String schemaName, String sql, Format format) throws Exception
        {
            ActionURL url = new ActionURL("sql", "execute", _folder);
            if (schemaName != null)
                url.addParameter("schemaName", schemaName);
            if (sql != null)
                url.addParameter("sql", sql);
            if (null != format)
                url.addParameter("format", format.name());
            return ViewServlet.GET(url, TestContext.get().getUser(), null);
        }

        @Test
        public void testExecute_mssql() throws Exception
        {
            MockHttpServletResponse response = executeSql("lists",
                    "SELECT Name, Age, Score FROM " + LIST_NAME + " ORDER BY Name", Format.split);
            assertEquals(HttpServletResponse.SC_OK, response.getStatus());

            String content = response.getContentAsString();
            String[] tokens = content.split("[\t\n]");

            // Header: meta-meta-data (3) + column names (3) + types (3) = 9
            // Data: 3 rows * 3 columns = 9
            assertTrue("Expected at least 18 tokens, got " + tokens.length, tokens.length >= 18);

            // Meta-meta-data
            assertEquals("18.2", tokens[0]);
            assertEquals("name", tokens[1]);
            assertEquals("jdbcType", tokens[2]);

            // Column names
            assertEquals("Name", tokens[3]);
            assertEquals("Age", tokens[4]);
            assertEquals("Score", tokens[5]);

            // JDBC types
            assertEquals("VARCHAR", tokens[6]);
            assertEquals("INTEGER", tokens[7]);
            assertEquals("DOUBLE", tokens[8]);

            // Data rows ordered by Name
            assertEquals("Alice", tokens[9]);
            assertEquals("30", tokens[10]);
            assertEquals("95.5", tokens[11]);
            assertEquals("Bob", tokens[12]);
            assertEquals("30", tokens[13]);
            assertEquals("87.3", tokens[14]);
            assertEquals("Carol", tokens[15]);
            assertEquals("35", tokens[16]);
            assertEquals("91.0", tokens[17]);
        }

        @Test
        public void testExecute() throws Exception
        {
            if (!CoreSchema.getInstance().getSqlDialect().isPostgreSQL())
            {
                testExecute_mssql();
                return;
            }

            MockHttpServletResponse response = executeSql("lists",
                "SELECT Name, Age, Score, Tags FROM " + LIST_NAME + " ORDER BY Name", Format.split);
            assertEquals(HttpServletResponse.SC_OK, response.getStatus());

            String content = response.getContentAsString();
            String[] tokens = content.split("[\t\n]");

            // Header: meta-meta-data (3) + column names (4) + types (4) = 11
            // Data: 3 rows * 4 columns = 12
            assertTrue("Expected at least 23 tokens, got " + tokens.length, tokens.length >= 23);

            // Meta-meta-data
            assertEquals("18.2", tokens[0]);
            assertEquals("name", tokens[1]);
            assertEquals("jdbcType", tokens[2]);

            // Column names
            assertEquals("Name", tokens[3]);
            assertEquals("Age", tokens[4]);
            assertEquals("Score", tokens[5]);
            assertEquals("Tags", tokens[6]);

            // JDBC types
            assertEquals("VARCHAR", tokens[7]);
            assertEquals("INTEGER", tokens[8]);
            assertEquals("DOUBLE", tokens[9]);
            assertEquals("ARRAY", tokens[10]);

            // Data rows ordered by Name (4 columns per row)
            assertEquals("Alice", tokens[11]);
            assertEquals("30", tokens[12]);
            assertEquals("95.5", tokens[13]);
            assertTrue("Alice Tags", tokens[14].contains("Red") && tokens[14].contains("Green"));
            assertEquals("Bob", tokens[15]);
            assertEquals("30", tokens[16]);
            assertEquals("87.3", tokens[17]);
            assertTrue("Bob Tags", tokens[18].contains("Blue"));
            assertEquals("Carol", tokens[19]);
            assertEquals("35", tokens[20]);
            assertEquals("91.0", tokens[21]);
            assertTrue("Carol Tags", tokens[22].contains("Red") && tokens[22].contains("Blue") && tokens[22].contains("Green"));
        }

        @Test
        public void testExecuteCompact() throws Exception
        {
            MockHttpServletResponse response = executeSql("lists",
                "SELECT Name, Age FROM " + LIST_NAME + " ORDER BY Age, Name", Format.compact);
            assertEquals(HttpServletResponse.SC_OK, response.getStatus());

            String content = response.getContentAsString();
            String sep = "";
            String eol = "";
            String ditto = "";

            String[] records = content.split(eol);
            // records: [0]=meta, [1]=column names, [2]=types, [3..5]=data rows
            assertTrue("Expected at least 6 records", records.length >= 6);

            // Column names
            String[] colNames = records[1].split(sep);
            assertEquals("Name", colNames[0]);
            assertEquals("Age", colNames[1]);

            // First data row: Alice, 30
            String[] row1 = records[3].split(sep, -1);
            assertEquals("Alice", row1[0]);
            assertEquals("30", row1[1]);

            // Second data row: Bob, 30 (ditto marker since Age repeats)
            String[] row2 = records[4].split(sep, -1);
            assertEquals("Bob", row2[0]);
            assertEquals(ditto, row2[1]);

            // Third data row: Carol, 35
            String[] row3 = records[5].split(sep, -1);
            assertEquals("Carol", row3[0]);
            assertEquals("35", row3[1]);
        }

        @Test
        public void testExecuteTsv() throws Exception
        {
            MockHttpServletResponse response = executeSql("lists",
                "SELECT Name, Age, Score FROM " + LIST_NAME + " ORDER BY Name", Format.tsv);
            assertEquals(HttpServletResponse.SC_OK, response.getStatus());

            String content = response.getContentAsString();
            String[] lines = content.split("\n");
            assertTrue("Expected at least 4 lines (header + 3 data rows), got " + lines.length, lines.length >= 4);

            // Header row: column names
            String[] headers = lines[0].split("\t");
            assertEquals("Name", headers[0]);
            assertEquals("Age", headers[1]);
            assertEquals("Score", headers[2]);

            // Data rows ordered by Name
            String[] row1 = lines[1].split("\t");
            assertEquals("Alice", row1[0]);
            assertEquals("30", row1[1]);
            assertEquals("95.5", row1[2]);

            String[] row2 = lines[2].split("\t");
            assertEquals("Bob", row2[0]);
            assertEquals("30", row2[1]);
            assertEquals("87.3", row2[2]);

            String[] row3 = lines[3].split("\t");
            assertEquals("Carol", row3[0]);
            assertEquals("35", row3[1]);
            assertEquals("91.0", row3[2]);
        }

        @Test
        public void testNoSql() throws Exception
        {
            MockHttpServletResponse response = executeSql("lists", null, Format.split);
            assertTrue("Expected error about missing SQL",
                response.getContentAsString().contains("no sql provided"));
        }

        @Test
        public void testSchemaNotFound() throws Exception
        {
            MockHttpServletResponse response = executeSql("nonexistent",
                "SELECT 1", Format.tsv);
            assertTrue("Expected schema not found error",
                response.getContentAsString().contains("schema not found"));
        }

        private MockHttpServletResponse executeSql2(String schemaName, String sql, Format format) throws Exception
        {
            ActionURL url = new ActionURL("sql", "execute2", _folder);
            if (schemaName != null)
                url.addParameter("schemaName", schemaName);
            if (sql != null)
                url.addParameter("sql", sql);
            if (null != format)
                url.addParameter("format", format.name());
            return ViewServlet.GET(url, TestContext.get().getUser(), null);
        }

        @Test
        public void testExecute2() throws Exception
        {
            // Basic split format: same data shape as testExecute_mssql
            MockHttpServletResponse response = executeSql2("lists",
                "SELECT Name, Age, Score FROM " + LIST_NAME + " ORDER BY Name", Format.split);
            assertEquals(HttpServletResponse.SC_OK, response.getStatus());

            String content = response.getContentAsString();
            String[] tokens = content.split("[\t\n]");

            assertTrue("Expected at least 18 tokens, got " + tokens.length, tokens.length >= 18);
            assertEquals("18.2", tokens[0]);
            assertEquals("name", tokens[1]);
            assertEquals("jdbcType", tokens[2]);
            assertEquals("Name", tokens[3]);
            assertEquals("Age", tokens[4]);
            assertEquals("Score", tokens[5]);
            assertEquals("VARCHAR", tokens[6]);
            assertEquals("INTEGER", tokens[7]);
            assertEquals("DOUBLE", tokens[8]);
            assertEquals("Alice", tokens[9]);
            assertEquals("30", tokens[10]);
            assertEquals("95.5", tokens[11]);
            assertEquals("Bob", tokens[12]);
            assertEquals("30", tokens[13]);
            assertEquals("87.3", tokens[14]);
            assertEquals("Carol", tokens[15]);
            assertEquals("35", tokens[16]);
            assertEquals("91.0", tokens[17]);

            // Date column should render as ISO-8601, not a raw JDBC string
            MockHttpServletResponse dateResponse = executeSql2("lists",
                "SELECT Name, Created FROM " + LIST_NAME + " ORDER BY Name", Format.split);
            assertEquals(HttpServletResponse.SC_OK, dateResponse.getStatus());
            String dateContent = dateResponse.getContentAsString();
            String[] dateTokens = dateContent.split("[\t\n]");
            // tokens: meta(3) + colNames(2) + jdbcTypes(2) + data(3 rows * 2 cols = 6) = 13 minimum
            assertTrue("Expected at least 13 date tokens, got " + dateTokens.length, dateTokens.length >= 13);
            assertEquals("Alice", dateTokens[7]);
            // we use space instead of 'T'
            assertTrue("Created date should be ISO-8601: " + dateTokens[8],
                dateTokens[8].matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.*"));
        }

        @Test
        public void testExecuteCompact2() throws Exception
        {
            MockHttpServletResponse response = executeSql2("lists",
                "SELECT Name, Age FROM " + LIST_NAME + " ORDER BY Age, Name", Format.compact);
            assertEquals(HttpServletResponse.SC_OK, response.getStatus());

            String content = response.getContentAsString();
            String sep = "";
            String eol = "";
            String ditto = "";

            String[] records = content.split(eol);
            assertTrue("Expected at least 6 records", records.length >= 6);

            String[] colNames = records[1].split(sep);
            assertEquals("Name", colNames[0]);
            assertEquals("Age", colNames[1]);

            String[] row1 = records[3].split(sep, -1);
            assertEquals("Alice", row1[0]);
            assertEquals("30", row1[1]);

            String[] row2 = records[4].split(sep, -1);
            assertEquals("Bob", row2[0]);
            assertEquals(ditto, row2[1]);

            String[] row3 = records[5].split(sep, -1);
            assertEquals("Carol", row3[0]);
            assertEquals("35", row3[1]);
        }

        @Test
        public void testExecuteTsv2() throws Exception
        {
            MockHttpServletResponse response = executeSql2("lists",
                "SELECT Name, Age, Score FROM " + LIST_NAME + " ORDER BY Name", Format.tsv);
            assertEquals(HttpServletResponse.SC_OK, response.getStatus());

            String content = response.getContentAsString();
            String[] lines = content.split("\n");
            assertTrue("Expected at least 4 lines (header + 3 data rows), got " + lines.length, lines.length >= 4);

            String[] headers = lines[0].split("\t");
            assertEquals("Name", headers[0]);
            assertEquals("Age", headers[1]);
            assertEquals("Score", headers[2]);

            String[] row1 = lines[1].split("\t");
            assertEquals("Alice", row1[0]);
            assertEquals("30", row1[1]);
            assertEquals("95.5", row1[2]);

            String[] row2 = lines[2].split("\t");
            assertEquals("Bob", row2[0]);
            assertEquals("30", row2[1]);
            assertEquals("87.3", row2[2]);

            String[] row3 = lines[3].split("\t");
            assertEquals("Carol", row3[0]);
            assertEquals("35", row3[1]);
            assertEquals("91.0", row3[2]);
        }
    }
}
