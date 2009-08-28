/*
 * Copyright (c) 2009 LabKey Corporation
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
import org.labkey.api.data.*;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.util.*;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.query.design.DgQuery;
import org.labkey.query.design.QueryDocument;
import org.labkey.query.QueryDefinitionImpl;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TableType;
import org.apache.commons.lang.StringUtils;

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
    String _name = null;
    private QuerySchema _schema;
	String _querySource;
	private ArrayList<QueryParseException> _parseErrors = new ArrayList<QueryParseException>();

    private TablesDocument _metadata = null;
    private QueryRelation _queryRoot;

    private int _aliasCounter = 0;


    public Query(QuerySchema schema)
    {
        _schema = schema;
        assert MemTracker.put(this);
    }


    public Query(QuerySchema schema, String sql)
    {
        _schema = schema;
        _querySource = sql;
        assert MemTracker.put(this);
    }


    /* for debugging */
    public void setName(String name)
    {
        _name = name;
        if (null != _queryRoot)
            _queryRoot.setSavedName(name);
    }

	QuerySchema getSchema()
	{
		return _schema;
	}


    public final int incrementAliasCounter()
    {
        return ++_aliasCounter;
    }


    public void setTablesDocument(TablesDocument doc)
    {
        _metadata = doc;
    }

    public TablesDocument getTablesDocument()
    {
        return _metadata;
    }


    public void parse()
    {
        if (null == _querySource)
            throw new IllegalStateException("SQL has not been specified");
        _parse(_querySource);
    }

    public void parse(String queryText)
    {
        _querySource = queryText;
        _parse(_querySource);
        if (_parseErrors.isEmpty() && null != _queryRoot)
            _queryRoot.declareFields();
    }

	private void _parse(String queryText)
    {
		try
		{
			QNode root = (new SqlParser()).parseQuery(queryText, _parseErrors);
            QueryRelation relation = null;

			if (root instanceof QQuery)
			{
				relation = new QuerySelect(this, (QQuery)root);
			}
			else if (root instanceof QUnion)
			{
				relation = new QueryUnion(this, (QUnion)root);
			}

            if (relation == null)
                return;

            _queryRoot = relation;

            if (_queryRoot._savedName == null && _name != null)
                _queryRoot.setSavedName(_name);
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
		QueryRelation relation = resolveTable(_schema, null, key, key.getName());
		if (relation == null)
		{
			builder.append("'Table not found' AS message");
		}
		else
		{
            TableInfo table = relation.getTableInfo();
            boolean foundColumn = false;
            List<FieldKey> defaultVisibleColumns = table.getDefaultVisibleColumns();
            for (FieldKey field : defaultVisibleColumns)
			{
				if (field.getParent() != null)
					continue;
                assert null != table.getColumn(field.getName());
                if (null == table.getColumn(field.getName()))
                    continue;
				List<String> parts = new ArrayList<String>();
				parts.add(key.getName());
				parts.addAll(field.getParts());
				QFieldKey qfield = QFieldKey.of(FieldKey.fromParts(parts));
				qfield.appendSource(builder);
				builder.nextPrefix(",");
                foundColumn = true;

                // Check if there's a corresponding OORIndicator that's not part of the default set, and add it
                FieldKey oorFieldKey = new FieldKey(field.getParent(), field.getName() + OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX);
                if (table.getColumn(oorFieldKey.getName()) != null && !defaultVisibleColumns.contains(oorFieldKey))
                {
                    List<String> oorParts = new ArrayList<String>();
                    oorParts.add(key.getName());
                    oorParts.addAll(oorFieldKey.getParts());
                    QFieldKey oorQField = QFieldKey.of(FieldKey.fromParts(oorParts));
                    oorQField.appendSource(builder);
                    builder.nextPrefix(",");
                }
			}
            if (!foundColumn)
            {
                List<String> pkNames = table.getPkColumnNames();
                if (pkNames.isEmpty())
                {
                    builder.append("'No columns selected' AS message");
                }
                else
                {
                    for (String pkName : pkNames)
                    {
                        List<String> parts = new ArrayList<String>();
                        parts.add(key.getName());
                        parts.add(pkName);
                        QFieldKey qfield = QFieldKey.of(FieldKey.fromParts(parts));
                        qfield.appendSource(builder);
                        builder.nextPrefix(",");
                    }
                }
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
		return _queryRoot instanceof QuerySelect ? ((QuerySelect)_queryRoot).getFromTable(key) : null;
    }


    public String getQueryText()
    {
        return _queryRoot == null ? null : _queryRoot.getQueryText();
    }


    public Set<FieldKey> getFromTables()
    {
		return isSelect() ? ((QuerySelect)_queryRoot).getFromTables() : null;
    }


    public List<QueryParseException> getParseErrors()
    {
        return _parseErrors;
    }


    public boolean isAggregate()
    {
		return isSelect() && ((QuerySelect)_queryRoot).isAggregate();
    }


    public boolean isUnion()
    {
        return _queryRoot instanceof QueryUnion;
    }


    public boolean isSelect()
    {
        return _queryRoot instanceof QuerySelect;
    }


    public boolean hasSubSelect()
    {
		return isSelect() && ((QuerySelect)_queryRoot).hasSubSelect();
    }


    public TableInfo getTableInfo()
    {
        try
        {
            if (_parseErrors.size() > 0)
                return null;
            return _queryRoot == null ? null : _queryRoot.getTableInfo();
        }
        catch (RuntimeException x)
        {
            throw Query.wrapRuntimeException(x, _querySource);
        }
    }


    public QueryDocument getDesignDocument()
    {
		return isSelect() ? ((QuerySelect)_queryRoot).getDesignDocument() : null;
    }


    public void update(DgQuery query, List<QueryException> errors)
    {
		if (isSelect())
			((QuerySelect)_queryRoot).update(query, errors);
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
	QueryRelation resolveTable(QuerySchema schema, QNode node, FieldKey key, String alias)
	{
		List<String> parts = key.getParts();
		List<String> names = new ArrayList<String>(parts.size());
		for (String part : parts)
			names.add(FieldKey.decodePart(part));

		for (int i = 0; i < parts.size() - 1; i ++)
		{
			String name = names.get(i);
			if (name.startsWith("/"))
			{
				parseError(_parseErrors, "Schema name should not start with '/'", node);
				return null;
			}
			schema = schema.getSchema(name);
			if (schema == null)
			{
				parseError(_parseErrors, "Table " + StringUtils.join(names,".") + " not found.", node);
				return null;
			}
		}

        Object t = null;
        try
        {
            if (schema instanceof UserSchema)
                t  = ((UserSchema)schema)._getTableOrQuery(key.getName(), true);
            else
                t = schema.getTable(key.getName());
        }
        catch (UnauthorizedException ex)
        {
            parseError(_parseErrors, "No permission to read table: " + key.getName(), node);
            return null;
        }

		if (t == null)
		{
			parseError(_parseErrors, "Table " + StringUtils.join(names,".") + " not found.", node);
			return null;
		}

        if (t instanceof TableInfo)
        {
            return new QueryTable(this, schema, (TableInfo)t, alias);
        }

        if (t instanceof QueryDefinition)
        {
            QueryDefinitionImpl def = (QueryDefinitionImpl)t;
            List<QueryException> tableErrors = new ArrayList<QueryException>();
            Query query = def.getQuery(schema, tableErrors);
            if (tableErrors.size() > 0)
            {
                //noinspection ThrowableInstanceNeverThrown
                _parseErrors.add(new QueryParseException("Query '" + key.getName() + "' has errors", null, node.getLine(), node.getColumn()));
                return null;
            }

            QueryRelation ret = query._queryRoot;
            ret.setQuery(this);

            if (query.getTablesDocument() != null && query.getTablesDocument().getTables().getTableArray().length > 0)
            {
                TableType tableType = query.getTablesDocument().getTables().getTableArray(0);
                ret = new QueryLookupWrapper(this, query._queryRoot, tableType);
            }

            ret.setAlias(alias);
            return ret;
        }

		return null;
	}


	//
	// TESTING
	//



    private static class TestDataLoader extends DataLoader<Map<String,Object>>
    {
        static final String[] COLUMNS = new String[] {"d", "seven", "twelve", "day", "month", "date", "duration", "guid", "createdby", "created"};
        static final String[] TYPES = new String[] {"double", "int", "int", "string", "string", "date", "string", "string", "int", "date"};
        static final String[] days = new String[] {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        static final String[] months = new String[] {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

        String[][] data;
        ArrayListMap<String,Object> templateRow = new ArrayListMap<String,Object>();


		// UNDONE: need some NULLS in here
        @SuppressWarnings({"UnusedAssignment"})
        TestDataLoader(String propertyPrefix, int len)
        {
            String now = DateUtil.toISO(new Date());
            data = new String[len+1][];
            data[0] = COLUMNS;
            for (String c : data[0])
                templateRow.put(propertyPrefix + "#" + c, c);
            for (int i=1 ; i<=len ; i++)
            {
                String[] row = data[i] = new String[COLUMNS.length];
                int c = 0;
                row[c++] = "" + Math.exp(i);
                row[c++] = "" + (i%7);
                row[c++] = "" + (i%12);
                row[c++] = days[i%7];
                row[c++] = months[i%12];
                row[c++] = DateUtil.toISO(DateUtil.parseDateTime("2000-01-01") + i*24*60*60*1000);
                row[c++] = DateUtil.formatDuration(i*1000);
                row[c++] = GUID.makeGUID();
                row[c++] = "" + TestContext.get().getUser().getUserId();
                row[c++] = now;
            }

//            for (String[] row : data) System.err.println(StringUtils.join(row,"\t")); System.err.flush();
        }

        public String[][] getFirstNLines(int n) throws IOException
        {
            return data;
        }

        int i=1;

        public CloseableIterator<Map<String, Object>> iterator()
        {
            return new _Iterator();
        }

        class _Iterator implements CloseableIterator<Map<String, Object>>
        {
            public boolean hasNext()
            {
                return i < data.length;
            }

            public Map<String, Object> next()
            {
                return new ArrayListMap<String, Object>(templateRow, Arrays.<Object>asList(data[i++]));
            }

            public void remove()
            {
                throw new UnsupportedOperationException();
            }

            public void close() throws IOException
            {
            }
        }

        public void close()
        {
        }
    }


    static class SqlTest
    {
		public String name = null;
        public String sql;
		public String metadata = null;
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


		SqlTest(String name, String sql, String metadata, int cols, int rows)
		{
			this.name = name;
			this.sql = sql;
			this.metadata = metadata;
			countColumns = cols;
			countRows = rows;
		}
    }



    static int Rcolumns = TestDataLoader.COLUMNS.length + 2; // rowid, entityid
	static int Rsize = 84;
	static int Ssize = 84;

    static SqlTest[] tests = new SqlTest[]
    {
        new SqlTest("SELECT R.d, R.seven, R.twelve, R.day, R.month, R.date, R.duration, R.guid FROM R", 8, Rsize),
        new SqlTest("SELECT R.* FROM R", Rcolumns, Rsize),
        new SqlTest("SELECT COUNT(*) AS _count FROM R", 1, 1),
        new SqlTest("SELECT R.d, R.seven, R.twelve, R.day, R.month, R.date, R.duration, R.guid, R.created, R.createdby, R.createdby.displayname FROM R", 11, Rsize),
        new SqlTest("SELECT R.duration AS elapsed FROM R WHERE R.rowid=1", 1, 1),
		new SqlTest("SELECT R.rowid, R.seven, R.day FROM R WHERE R.day LIKE '%ues%'", 3, 12),
		new SqlTest("SELECT R.rowid, R.twelve, R.month FROM R WHERE R.month BETWEEN 'L' and 'O'", 3, 3*7), // March, May, Nov
        new SqlTest("SELECT R.rowid, R.twelve, (SELECT S.month FROM Folder.qtest.lists.S S WHERE S.rowid=R.rowid) as M FROM R WHERE R.day='Monday'", 3, 12),
        new SqlTest("SELECT T.R, T.T, T.M FROM (SELECT R.rowid as R, R.twelve as T, (SELECT S.month FROM Folder.qtest.lists.S S WHERE S.rowid=R.rowid) as M FROM R WHERE R.day='Monday') T", 3, 12),
		new SqlTest("SELECT R.rowid, R.twelve FROM R WHERE R.seven in (SELECT S.seven FROM Folder.qtest.lists.S S WHERE S.seven in (1,4))", 2, Rsize*2/7),

		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S inner join R T on S.rowid=T.rowid"),
		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S left join R T on S.rowid=T.rowid"),
		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S left outer join R T on S.rowid=T.rowid"),
		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S right join R T on S.rowid=T.rowid"),
		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S right outer join R T on S.rowid=T.rowid"),
		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S full join R T on S.rowid=T.rowid"),
		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S full outer join R T on S.rowid=T.rowid"),

        new SqlTest("SELECT R.seven FROM R UNION SELECT S.seven FROM Folder.qtest.lists.S S", 1, 7),
        new SqlTest("SELECT R.seven FROM R UNION ALL SELECT S.seven FROM Folder.qtest.lists.S S", 1, Rsize*2),
        new SqlTest("SELECT 'R' as x, R.seven FROM R UNION SELECT 'S' as x, S.seven FROM Folder.qtest.lists.S S", 2, 14),
        new SqlTest("SELECT 'R' as x, R.seven FROM R UNION SELECT 'S' as x, S.seven FROM Folder.qtest.lists.S S UNION SELECT 'T' as t, R.twelve FROM R", 2, 26),
		new SqlTest("(SELECT 'R' as x, R.seven FROM R) UNION (SELECT 'S' as x, S.seven FROM Folder.qtest.lists.S S UNION SELECT 'T' as t, R.twelve FROM R)", 2, 26),
		// mixed UNION, UNION ALL
        new SqlTest("SELECT R.seven FROM R UNION SELECT R.seven FROM R UNION SELECT R.twelve FROM R", 1, 12),
        new SqlTest("(SELECT R.seven FROM R UNION SELECT R.seven FROM R) UNION ALL SELECT R.twelve FROM R", 1, 7 + Rsize),
        new SqlTest("(SELECT R.seven FROM R UNION ALL SELECT R.seven FROM R) UNION SELECT R.twelve FROM R", 1, 12),
        new SqlTest("SELECT R.seven FROM R UNION ALL SELECT R.seven FROM R UNION ALL SELECT R.twelve FROM R", 1, 3*Rsize),
        new SqlTest("SELECT u.seven FROM (SELECT R.seven FROM R UNION SELECT R.seven FROM R UNION SELECT R.twelve FROM R) u WHERE u.seven > 5", 1, 6),
		// LIMIT
		new SqlTest("SELECT R.day, R.month, R.date FROM R LIMIT 5", 3, 5),
		new SqlTest("SELECT R.day, R.month, R.date FROM R ORDER BY R.date LIMIT 5", 3, 5),

        // quoted identifiers
        new SqlTest("SELECT T.\"count\", T.\"Opened By\", T.Seven, T.MonthName FROM (SELECT R.d as \"count\", R.seven as \"Seven\", R.twelve, R.day, R.month, R.date, R.duration, R.guid, R.created, R.createdby as \"Opened By\", R.month as MonthName FROM R) T", 4, Rsize),

        // saved queries
        new SqlTest("Rquery",
                "SELECT R.rowid, R.rowid*2 as rowid2, R.d, R.seven, R.twelve, R.day, R.month, R.date, R.duration, R.guid FROM R",
                "<tables xmlns=\"http://labkey.org/data/xml\">\n" +
                "<table tableName=\"Rquery\" tableDbType=\"NOT_IN_DB\">\n" +
                "<columns>\n" +
                " <column columnName=\"rowid\">\n" +
                "  <fk>\n" +
                "  <fkTable>Squery</fkTable>\n" +
                "  <fkColumnName>rowid</fkColumnName>\n" +
                "  </fk>\n" +
                " </column>\n" +
                " <column columnName=\"rowid2\">\n" +
                "  <fk>\n" +
                "  <fkTable>Squery</fkTable>\n" +
                "  <fkColumnName>rowid</fkColumnName>\n" +
                "  </fk>\n" +
                " </column>\n" +
                "</columns>\n" +
                "</table>\n" +
                "</tables>",
                10, Rsize),
            new SqlTest("SELECT Rquery.d FROM Rquery", 1, Rsize),

            new SqlTest("Squery",
                    "SELECT S.rowid, S.d, S.seven, S.twelve, S.day, S.month, S.date, S.duration, S.guid FROM Folder.qtest.lists.S S",
                    null,
                    9, Rsize),
            new SqlTest("SELECT S.rowid, S.d FROM Squery S", 2, Rsize),

            new SqlTest("SELECT Rquery.rowid, Rquery.rowid.duration FROM Rquery", 2, Rsize),
            new SqlTest("SELECT Rquery.rowid2, Rquery.rowid2.duration FROM Rquery", 2, Rsize),
            new SqlTest("SELECT Rquery.rowid, Rquery.rowid.date, Rquery.rowid2, Rquery.rowid2.duration FROM Rquery", 4, Rsize),

            // NOTE: DISTINCT means lookups can not be pushed down
            new SqlTest("Rdistinct", "SELECT DISTINCT R.twelve FROM R",
                    "<tables xmlns=\"http://labkey.org/data/xml\">\n" +
                    "<table tableName=\"Rquery\" tableDbType=\"NOT_IN_DB\">\n" +
                    "<columns>\n" +
                    " <column columnName=\"twelve\">\n" +
                    "  <fk>\n" +
                    "  <fkTable>Squery</fkTable>\n" +
                    "  <fkColumnName>rowid</fkColumnName>\n" +
                    "  </fk>\n" +
                    " </column>\n" +
                    "</columns>\n" +
                    "</table>\n" +
                    "</tables>",
                    1, 12),
        new SqlTest("SELECT Rdistinct.twelve, Rdistinct.twelve.duration from Rdistinct", 2, 12),

        // GROUPING
        new SqlTest("SELECT R.seven, MAX(R.twelve) AS _max FROM R GROUP BY R.seven", 2, 7),
        new SqlTest("SELECT COUNT(R.rowid) as _count FROM R", 1, 1),
        new SqlTest("SELECT R.seven, MAX(R.twelve) AS _max FROM R GROUP BY R.seven HAVING SUM(R.twelve) > 5", 2, 7),

        // METHODS
        new SqlTest("SELECT ROUND(R.d) AS _d, ROUND(R.d,1) AS _rnd, ROUND(3.1415,2) AS _pi, CONVERT(R.d,SQL_VARCHAR) AS _str FROM R", 4, Rsize),
    };

	static SqlTest[] postgres = new SqlTest[]
	{
		// ORDER BY tests
		new SqlTest("SELECT R.day, R.month, R.date FROM R ORDER BY R.date"),
		new SqlTest("SELECT R.day, R.month, R.date FROM R UNION SELECT R.day, R.month, R.date FROM R ORDER BY date")
	};

	static SqlTest[] negative = new SqlTest[]
	{
		new SqlTest("SELECT S.d, S.seven FROM S"),
		new SqlTest("SELECT S.d, S.seven FROM Folder.S"),
		new SqlTest("SELECT S.d, S.seven FROM Folder.qtest.S"),
		new SqlTest("SELECT S.d, S.seven FROM Folder.qtest.list.S"),
		new SqlTest("SELECT R.day, R.month, R.date FROM R UNION SELECT R.day, R.month, R.date FROM R LIMIT 5"),
		new SqlTest("SELECT R.day, R.month, R.date FROM R UNION SELECT R.day, R.month, R.date FROM R ORDER BY date LIMIT 5"),
        new SqlTest("SELECT R.seven, MAX(R.twelve) AS _max FROM R HAVING SUM(R.twelve) > 5"),
        new SqlTest("SELECT * FROM R"),
        new SqlTest("SELECT SUM(*) FROM R")
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


		Container getSubfolder()
		{
			return ContainerManager.ensureContainer(JunitUtil.getTestContainer().getPath() + "/qtest");
		}


        private void addProperties(ListDefinition l)
        {
            Domain d = l.getDomain();
            for (int i=0 ; i<TestDataLoader.COLUMNS.length ; i++)
            {
                DomainProperty p = d.addProperty();
                p.setPropertyURI(d.getName() + hash + "#" + TestDataLoader.COLUMNS[i]);
                p.setName(TestDataLoader.COLUMNS[i]);
                p.setRangeURI(TestDataLoader.TYPES[i]);
                if ("createdby".equals(TestDataLoader.COLUMNS[i]))
                {
                    p.setLookup(new Lookup(l.getContainer(), "core", "SiteUsers"));
                }
            }
        }

        @Override
        protected void setUp() throws Exception
        {
//            _setUp();
        }


        protected void _setUp() throws Exception
        {
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
			Container qtest = getSubfolder();
            ListService.Interface s = ListService.get();

            ListDefinition R = s.createList(c, "R");
            R.setKeyType(ListDefinition.KeyType.AutoIncrementInteger);
            R.setKeyName("rowid");
            addProperties(R);
            R.save(user);
            R.insertListItems(user, new TestDataLoader(R.getName() + hash, Rsize));

            ListDefinition S = s.createList(qtest, "S");
            S.setKeyType(ListDefinition.KeyType.AutoIncrementInteger);
            S.setKeyName("rowid");
            addProperties(S);
            S.save(user);
            S.insertListItems(user, new TestDataLoader(S.getName() + hash, Ssize));
        }


		@Override
        protected void tearDown() throws Exception
        {
//            _tearDown();
        }


        protected void _tearDown() throws Exception
        {
            User user = TestContext.get().getUser();

            for (SqlTest test : tests)
            {
                if (test.name != null)
                {
                    QueryDefinition q = QueryService.get().getQueryDef(JunitUtil.getTestContainer(), "lists", test.name);
                    if (null != q)
                        q.delete(user);
                }
            }

			ListService.Interface s = ListService.get();

			Container c = JunitUtil.getTestContainer();
			{
				Map<String,ListDefinition> m = s.getLists(c);
				if (m.containsKey("R"))
					m.get("R").delete(user);
				if (m.containsKey("S"))
					m.get("S").delete(user);
			}

			Container qtest = getSubfolder();
			{
				Map<String,ListDefinition> m = s.getLists(qtest);
				if (m.containsKey("S"))
					m.get("S").delete(user);
			}
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
                if (test.countColumns >= 0)
                    assertEquals(test.sql, test.countColumns, md.getColumnCount());
                if (test.countRows >= 0)
                    assertEquals(test.sql, test.countRows, rs.getSize());

				if (test.name != null)
				{
                    QueryDefinition existing = QueryService.get().getQueryDef(JunitUtil.getTestContainer(), "lists", test.name);
                    if (null != existing)
                        existing.delete(TestContext.get().getUser());
					QueryDefinition q = QueryService.get().createQueryDef(JunitUtil.getTestContainer(), "lists", test.name);
					q.setSql(test.sql);
					if (null != test.metadata)
						q.setMetadataXml(test.metadata);
					q.save(TestContext.get().getUser(), JunitUtil.getTestContainer());
				}
            }
            catch (Exception x)
            {
                fail(x.getMessage() + "\n" + test.sql);
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }


		private void failidate(SqlTest test) throws Exception
		{
			CachedRowSetImpl rs = null;
			try
			{
				rs = (CachedRowSetImpl)QueryService.get().select(lists, test.sql);
				fail("should fail: " + test.sql);
			}
			catch (SQLException x)
			{
				// should fail with SQLException not runtime exception
			}
			catch (QueryParseException x)
			{
				// OK
			}
			catch (Exception x)
			{
				fail("unexpected exception: " + x.toString());
			}
			finally
			{
				ResultSetUtil.close(rs);
			}
		}


        public void testSQL() throws Exception
        {
            // note getSchema() will return NULL if there are no lists yet
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            ListService.Interface s = ListService.get();

            lists = DefaultSchema.get(user, c).getSchema("lists");
            if (1==1 || null == lists)
            {
                _tearDown();
                _setUp();
                lists = DefaultSchema.get(user, c).getSchema("lists");
            }

            assertNotNull(lists);
            TableInfo Rinfo = lists.getTable("R");
            assertNotNull(Rinfo);
			TableInfo Sinfo = DefaultSchema.get(user, getSubfolder()).getSchema("lists").getTable("S");
            assertNotNull(Sinfo);

            // custom tests
			String sql = "SELECT R.d, R.seven, R.twelve, R.day, R.month, R.date, R.duration, R.created, R.createdby FROM R";
            CachedRowSetImpl rs = resultset(sql);
            ResultSetMetaData md = rs.getMetaData();
            assertTrue(sql, 0 < rs.findColumn("d"));
            assertTrue(sql, 0 < rs.findColumn("seven"));
            assertTrue(sql, 0 < rs.findColumn("twelve"));
            assertTrue(sql, 0 < rs.findColumn("day"));
            assertTrue(sql, 0 < rs.findColumn("month"));
            assertTrue(sql, 0 < rs.findColumn("date"));
            assertTrue(sql, 0 < rs.findColumn("created"));
            assertTrue(sql, 0 < rs.findColumn("createdby"));
            assertEquals(sql, 9, md.getColumnCount());
            assertEquals(sql, Rsize, rs.getSize());
			rs.next();
			for (int col=1; col<=md.getColumnCount() ; col++)
				assertNotNull(sql, rs.getObject(col));
            rs.close();

            // simple tests
            for (SqlTest test : tests)
            {
                validate(test);
            }
			if (DefaultSchema.get(user, JunitUtil.getTestContainer()).getSchema("lists").getDbSchema().getSqlDialect().allowSortOnSubqueryWithoutLimit())
			{
				for (SqlTest test : postgres)
					{
						validate(test);
					}
			}
			for (SqlTest test : negative)
			{
				failidate(test);
			}

            for (SqlTest test : tests)
            {
                if (test.name != null)
                {
                    QueryDefinition q = QueryService.get().getQueryDef(JunitUtil.getTestContainer(), "lists", test.name);
                    assertNotNull(q);
//                    q.delete(user);
                }
            }
        }

        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
