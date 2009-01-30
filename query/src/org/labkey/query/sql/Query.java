/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.query.sql;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.labkey.api.data.CachedRowSetImpl;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.util.*;
import org.labkey.common.tools.DataLoader;
import org.labkey.query.design.DgQuery;
import org.labkey.query.design.QueryDocument;

import java.io.IOException;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;


/**
 * Query is the "interface" to the SQL->SQL transformation code
 *
 * This class manages the state transitions from
 *
 * parse, resolve, generateSQL
 *
 */

public class Query
{
    private QuerySchema _schema;
	private String _querySource;
	private ArrayList<QueryParseException> _parseErrors = new ArrayList<QueryParseException>();

	private QuerySelect _select;
	private QUnion _qunion;

	
    public Query(QuerySchema schema)
    {
        _schema = schema;
		_select = new QuerySelect(_schema);
        assert MemTracker.put(this);
    }


	QuerySchema getSchema()
	{
		return _schema;
	}


	public void parse(String queryText)
    {
		_querySource = queryText;

		try
		{
			QNode root = (new SqlParser()).parseUnion(queryText, _parseErrors);
			if (!_parseErrors.isEmpty())
				return;
			
			if (root instanceof QQuery)
			{
				_select = new QuerySelect(this, (QQuery)root);
			}
			else if (root instanceof QUnion)
			{
				_qunion = (QUnion)root;
			}
		}
		catch (RuntimeException ex)
		{
			throw wrapRuntimeException(ex, _querySource);
		}
    }


    /**
     * When the user has chosen to create a new query based on a particular table, create a new QueryDef which
     * selects all of the non-hidden columns from that table.
     */
	public void setRootTable(FieldKey key)
	{
		SourceBuilder builder = new SourceBuilder();
		builder.append("SELECT ");
		builder.pushPrefix("");
		TableInfo table = resolveTable(_schema, _parseErrors, null, key, key.getName());
		if (table == null)
		{
			builder.append("'Table not found' AS message");
		}
		else
		{
			for (FieldKey field : table.getDefaultVisibleColumns())
			{
				if (field.getParent() != null)
					continue;
				List<String> parts = new ArrayList<String>();
				parts.add(key.getName());
				parts.addAll(field.getParts());
				QFieldKey qfield = QFieldKey.of(FieldKey.fromParts(parts));
				qfield.appendSource(builder);
				builder.nextPrefix(",");
			}
		}
		builder.popPrefix();
		builder.append("\nFROM ");
		QFieldKey.of(new FieldKey(key.getParent(), key.getName())).appendSource(builder);
		if (key.getParent() != null)
		{
			builder.append(" AS ");
			new QIdentifier(key.getName()).appendSource(builder);
		}
		parse(builder.getText());
	}



    public TableInfo getFromTable(FieldKey key)
    {
		return _select == null ? null : _select.getFromTable(key);
    }


    public String getQueryText()
    {
		return _select == null ? null :  _select.getQueryText();

    }

    public Set<FieldKey> getFromTables()
    {
		return _select == null ? null : _select.getFromTables();
    }


    public List<QueryParseException> getParseErrors()
    {
        return _parseErrors;
    }


    public boolean isAggregate()
    {
		return _select != null && _select.isAggregate();
    }


    public boolean hasSubSelect()
    {
		return _select != null && _select.hasSubSelect();
    }


    public QueryTableInfo getTableInfo(String tableAlias)
    {
		return _select == null ? null : _select.getTableInfo(tableAlias);
    }


    public QueryDocument getDesignDocument()
    {
		return _select == null ? null : _select.getDesignDocument();

    }


    public void update(DgQuery query, List<QueryException> errors)
    {
		if (null != _select)
			_select.update(query, errors);
    }



    static QueryInternalException wrapRuntimeException(RuntimeException ex, String sql)
    {
        if (ex instanceof QueryInternalException)
            return (QueryInternalException)ex;
        else
            return new QueryInternalException(ex, sql);
    }


    static class QueryInternalException extends RuntimeException
    {
        QueryInternalException(RuntimeException cause, String sql)
        {
            super("Internal error while parsing \""+ sql + "\"", cause);
        }
    }


	//
	// Helpers
	//


	static void parseError(List<QueryParseException> errors, String message, QNode node)
	{
		int line = 0;
		int column = 0;
		if (node != null)
		{
			line = node.getLine();
			column = node.getColumn();
		}
		//noinspection ThrowableInstanceNeverThrown
		errors.add(new QueryParseException(message, null, line, column));
	}
	

	/**
	 * Resolve a particular table name.  The table name may have schema names (folder.schema.table etc.) prepended to it.
	 */
	static TableInfo resolveTable(QuerySchema schema, List<QueryParseException> errors, QNode node, FieldKey key, String alias)
	{
		List<String> parts = key.getParts();
		for (int i = 0; i < parts.size() - 1; i ++)
		{
			schema = schema.getSchema(parts.get(i));
			if (schema == null)
			{
				parseError(errors, "Table " + key + " not found.", node);
				return null;
			}
		}

		TableInfo ret = schema.getTable(key.getName(), alias);
		if (ret == null)
		{
			parseError(errors, "Table " + key + " not found.", node);
			return null;
		}

		return ret;
	}
	

	//
	// TESTING
	//



    private static class TestDataLoader extends DataLoader
    {
        static final String[] COLUMNS = new String[] {"d", "seven", "twelve", "day", "month", "date", "duration", "guid"};
        static final String[] TYPES = new String[] {"double", "int", "int", "string", "string", "date", "string", "string"};
        static final String[] days = new String[] {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        static final String[] months = new String[] {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

        String[][] data;
        ArrayListMap<String,Object> templateRow = new ArrayListMap<String,Object>();


		// UNDONE: need some NULLS in here
		TestDataLoader(String propertyPrefix, int len)
        {
            data = new String[len+1][];
            data[0] = COLUMNS;
            for (String c : data[0])
                templateRow.put(propertyPrefix + "#" + c, c);
            for (int i=1 ; i<=len ; i++)
            {
                String[] row = data[i] = new String[8];
                int c = 0;
                row[c++] = "" + Math.exp(i);
                row[c++] = "" + (i%7);
                row[c++] = "" + (i%12);
                row[c++] = days[i%7];
                row[c++] = months[i%12];
                row[c++] = DateUtil.toISO(DateUtil.parseDateTime("2000-01-01") + i*24*60*60*1000);
                row[c++] = DateUtil.formatDuration(i*1000);
                row[c] = GUID.makeGUID();
            }

//            for (String[] row : data) System.err.println(StringUtils.join(row,"\t")); System.err.flush();
        }

        public String[][] getFirstNLines(int n) throws IOException
        {
            return data;
        }

        int i=1;

        protected Iterator<Map<String, Object>> iterator() throws IOException
        {
            return new _Iterator();
        }

        class _Iterator implements Iterator<Map<String, Object>>
        {
            public boolean hasNext()
            {
                return i < data.length;
            }

            public Map<String, Object> next()
            {
                return new ArrayListMap<String,Object>(templateRow, data[i++]);
            }

            public void remove()
            {
                throw new UnsupportedOperationException();
            }
        }
    }


    static class SqlTest
    {
        public String sql;
        public int countColumns = -1;
        public int countRows = -1;

        SqlTest(String sql)
        {
            this.sql = sql;
        }

        SqlTest(String sql, int cols, int rows)
        {
            this.sql = sql;
            countColumns = cols;
            countRows = rows;
        }
    }


	static int Rsize = 84;
	static int Ssize = 84;

    static SqlTest[] tests = new SqlTest[]
    {
        new SqlTest("SELECT R.d, R.seven, R.twelve, R.day, R.month, R.date, R.duration, R.guid FROM R", 8, Rsize),
        new SqlTest("SELECT R.duration AS elapsed FROM R WHERE R.rowid=1", 1, 1),
		new SqlTest("SELECT R.rowid, R.seven, R.day FROM R WHERE R.day LIKE '%ues%'", 3, 12),
		new SqlTest("SELECT R.rowid, R.twelve, R.month FROM R WHERE R.month BETWEEN 'L' and 'O'", 3, 3*7), // March, May, Nov
        new SqlTest("SELECT R.rowid, R.twelve, (SELECT S.month FROM S WHERE S.rowid=R.rowid) as M FROM R WHERE R.day='Monday'", 3, 12),
        new SqlTest("SELECT T.R, T.T, T.M FROM (SELECT R.rowid as R, R.twelve as T, (SELECT S.month FROM S WHERE S.rowid=R.rowid) as M FROM R WHERE R.day='Monday') T", 3, 12)
    };


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


        String hash = GUID.makeHash();
        QuerySchema lists;
        ListDefinition R;
        ListDefinition S;


        private void addProperties(ListDefinition l)
        {
            Domain d = l.getDomain();
            for (int i=0 ; i<TestDataLoader.COLUMNS.length ; i++)
            {
                DomainProperty p = d.addProperty();
                p.setPropertyURI(d.getName() + hash + "#" + TestDataLoader.COLUMNS[i]);
                p.setName(TestDataLoader.COLUMNS[i]);
                p.setRangeURI(TestDataLoader.TYPES[i]);
            }
        }


        @Override
        protected void setUp() throws Exception
        {
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            ListService.Interface s = ListService.get();

            Map<String,ListDefinition> m = s.getLists(c);
            if (m.containsKey("R"))
                m.get("R").delete(user);
            if (m.containsKey("S"))
                m.get("S").delete(user);

            R = s.createList(c, "R");
            R.setKeyType(ListDefinition.KeyType.AutoIncrementInteger);
            R.setKeyName("rowid");
            addProperties(R);
            R.save(user);
            R.insertListItems(user, new TestDataLoader(R.getName() + hash, Rsize));

            S = s.createList(c, "S");
            S.setKeyType(ListDefinition.KeyType.AutoIncrementInteger);
            S.setKeyName("rowid");
            addProperties(S);
            S.save(user);
            S.insertListItems(user, new TestDataLoader(S.getName() + hash, Ssize));

            // note getSchema() will return NULL if there are no lists yet
            lists = DefaultSchema.get(user, c).getSchema(s.getSchemaName());
        }


        @Override
        protected void tearDown() throws Exception
        {
            User user = TestContext.get().getUser();
            S.delete(user);
            R.delete(user);
        }


        @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
        private CachedRowSetImpl resultset(String sql) throws Exception
        {
			try
			{
				CachedRowSetImpl rs = (CachedRowSetImpl)QueryService.get().select(lists, sql);
				assertNotNull(sql, rs);
				return rs;
			}
			catch (QueryParseException x)
			{
				fail(x.getMessage() + "\n" + sql);
				return null;
			}
			catch (SQLException x)
			{
				fail(x.getMessage() + "\n" + sql);
				return null;
			}
        }


        private void validate(SqlTest test) throws Exception
        {
            CachedRowSetImpl rs = null;
            try
            {
                rs = resultset(test.sql);
                ResultSetMetaData md = rs.getMetaData();
                assertTrue(test.sql, test.countColumns == -1 || test.countColumns == md.getColumnCount());
                assertTrue(test.sql, test.countRows == -1 || test.countRows == rs.getSize());
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }


        public void test() throws Exception
        {
            assertNotNull(lists);
            TableInfo Rinfo = lists.getTable("R", "R");
            assertNotNull(Rinfo);
            TableInfo Sinfo = lists.getTable("S", "S");
            assertNotNull(Sinfo);

            // custom tests
			String sql = "SELECT R.d, R.seven, R.twelve, R.day, R.month, R.date, R.duration FROM R";
            CachedRowSetImpl rs = resultset(sql);
            ResultSetMetaData md = rs.getMetaData();
            assertTrue(sql, 0 < rs.findColumn("d"));
            assertTrue(sql, 0 < rs.findColumn("seven"));
            assertTrue(sql, 0 < rs.findColumn("twelve"));
            assertTrue(sql, 0 < rs.findColumn("day"));
            assertTrue(sql, 0 < rs.findColumn("month"));
            assertTrue(sql, 0 < rs.findColumn("date"));
            assertTrue(sql, 0 < rs.findColumn("duration"));
            assertEquals(sql, 7, md.getColumnCount());
            assertEquals(sql, Rsize, rs.getSize());
			rs.next();
			for (int c=1; c<=md.getColumnCount() ; c++)
				assertNotNull(sql, rs.getObject(c));
            rs.close();

            // simple tests
            for (SqlTest test : tests)
            {
                validate(test);
            }
        }

        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}