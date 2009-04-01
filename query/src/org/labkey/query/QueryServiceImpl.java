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

package org.labkey.query;

import org.apache.commons.lang.StringUtils;
//import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.security.User;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.Cache;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.WebPartView;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.AppProps;
import org.labkey.common.util.Pair;
import org.labkey.data.xml.TablesDocument;
import org.labkey.query.persist.*;
import org.labkey.query.view.DbUserSchema;
import org.labkey.query.sql.Query;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.io.File;
import java.io.FilenameFilter;

public class QueryServiceImpl extends QueryService
{
//    static private final Logger _log = Logger.getLogger(QueryServiceImpl.class);

    protected static final FilenameFilter moduleQueryFileFilter = new FilenameFilter(){
        public boolean accept(File dir, String name)
        {
            return name.toLowerCase().endsWith(ModuleQueryDef.FILE_EXTENSION);
        }
    };

    private static Cache _moduleResourcesCache = new Cache(1024, Cache.DAY);
    private static final String QUERYDEF_SET_CACHE_ENTRY = "QUERYDEFS:";

    public static Cache getModuleResourcesCache()
    {
        return _moduleResourcesCache;
    }

    public UserSchema getUserSchema(User user, Container container, String schemaName)
    {
        QuerySchema ret = DefaultSchema.get(user, container).getSchema(schemaName);
        if (ret instanceof UserSchema)
        {
            return (UserSchema) ret;
        }
        return null;
    }

    public QueryDefinition createQueryDef(Container container, String schema, String name)
    {
        return new CustomQueryDefinitionImpl(container, schema, name);
    }

    public ActionURL urlQueryDesigner(Container container, String schema)
    {
        return urlFor(container, QueryAction.begin, schema, null);
    }

    public ActionURL urlFor(Container container, QueryAction action, String schema, String query)
    {
        ActionURL ret = new ActionURL("query", action.toString(), container);
        if (schema != null)
        {
            ret.addParameter(QueryParam.schemaName.toString(), schema);
        }
        if (query != null)
        {
            ret.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName, query);
        }
        return ret;
    }

    public QueryDefinition createQueryDefForTable(UserSchema schema, String tableName)
    {
        return new TableQueryDefinition(schema, tableName);
    }

    public Map<String, QueryDefinition> getQueryDefs(Container container, String schemaName)
    {
        Map<String, QueryDefinition> ret = new LinkedHashMap<String,QueryDefinition>();
        for (QueryDefinition queryDef : getAllQueryDefs(container, schemaName, true, false).values())
        {
            ret.put(queryDef.getName(), queryDef);
        }
        return ret;
    }

    public List<QueryDefinition> getQueryDefs(Container container)
    {
        return new ArrayList<QueryDefinition>(getAllQueryDefs(container, null, true, false).values());
    }

    private File getModuleQueriesDir(Module module)
    {
        return new File(module.getExplodedPath(), "queries");
    }

    private Map<Map.Entry<String, String>, QueryDefinition> getAllQueryDefs(Container container, String schemaName, boolean inheritable, boolean includeSnapshots)
    {
        return getAllQueryDefs(container, schemaName, inheritable, includeSnapshots, false);
    }

    private Map<Map.Entry<String, String>, QueryDefinition> getAllQueryDefs(Container container, String schemaName,
                                                                            boolean inheritable, boolean includeSnapshots, boolean allModules)
    {
        Map<Map.Entry<String, String>, QueryDefinition> ret = new LinkedHashMap<Map.Entry<String, String>, QueryDefinition>();

        //look in all the active modules in this container to see if they contain any query definitions
        if(null != schemaName)
        {
            Collection<Module> modules = allModules ? ModuleLoader.getInstance().getModules() : container.getActiveModules();
            for(Module module : modules)
            {
                File schemaDir = new File(getModuleQueriesDir(module), schemaName);
                File[] fileSet = null;

                //always scan the file system in dev mode
                if(AppProps.getInstance().isDevMode())
                    fileSet = schemaDir.listFiles(moduleQueryFileFilter);
                else
                {
                    //in production, cache the set of query defs for each module on first request
                    String fileSetCacheKey = QUERYDEF_SET_CACHE_ENTRY + module.toString();
                    fileSet = (File[])_moduleResourcesCache.get(fileSetCacheKey);
                    if(null == fileSet && schemaDir.exists())
                    {
                        fileSet = schemaDir.listFiles(moduleQueryFileFilter);
                        _moduleResourcesCache.put(fileSetCacheKey, fileSet);
                    }
                }

                if(null != fileSet)
                {
                    for(File sqlFile : fileSet)
                    {
                        ModuleQueryDef moduleQueryDef = (ModuleQueryDef)_moduleResourcesCache.get(sqlFile.getAbsolutePath());
                        if(null == moduleQueryDef || moduleQueryDef.isStale())
                        {
                            moduleQueryDef = new ModuleQueryDef(sqlFile, schemaName);
                            _moduleResourcesCache.put(sqlFile.getAbsolutePath(), moduleQueryDef);
                        }

                        ret.put(new Pair<String,String>(schemaName, moduleQueryDef.getName()),
                                new ModuleCustomQueryDefinition(moduleQueryDef, container));
                    }
                }
            }
        }

        for (QueryDef queryDef : QueryManager.get().getQueryDefs(container, schemaName, false, includeSnapshots, true))
        {
            Map.Entry<String, String> key = new Pair<String,String>(queryDef.getSchema(), queryDef.getName());
            ret.put(key, new CustomQueryDefinitionImpl(queryDef));
        }
        if (!inheritable)
            return ret;
        Container containerCur = container;
        while (!containerCur.isRoot())
        {
            containerCur = containerCur.getParent();
            for (QueryDef queryDef : QueryManager.get().getQueryDefs(containerCur, schemaName, true, includeSnapshots, true))
            {
                Map.Entry<String, String> key = new Pair<String,String>(queryDef.getSchema(), queryDef.getName());
                if (!ret.containsKey(key))
                {
                    ret.put(key, new CustomQueryDefinitionImpl(queryDef));
                }
            }
        }

        // look in the Shared project
        for (QueryDef queryDef : QueryManager.get().getQueryDefs(ContainerManager.getSharedContainer(), schemaName, true, includeSnapshots, true))
        {
            Map.Entry<String, String> key = new Pair<String,String>(queryDef.getSchema(), queryDef.getName());
            if (!ret.containsKey(key))
            {
                ret.put(key, new CustomQueryDefinitionImpl(queryDef));
            }
        }

        return ret;
    }

    public QueryDefinition getQueryDef(Container container, String schema, String name)
    {
        Map<String, QueryDefinition> ret = new LinkedHashMap<String,QueryDefinition>();
        for (QueryDefinition queryDef : getAllQueryDefs(container, schema, true, true, true).values())
        {
            ret.put(queryDef.getName(), queryDef);
        }
        return ret.get(name);
    }

    public List<CustomView> getCustomViews(User user, Container container, String schema, String query)
    {
        try {
            Map<String, CustomView> views = new HashMap<String, CustomView>();
            Map<Map.Entry<String, String>, QueryDefinition> queryDefs = getAllQueryDefs(container, schema, false, true);

            for (CstmView collist : QueryManager.get().getAllColumnLists(container, schema, query, user, true))
                addCustomView(container, user, views, collist, queryDefs);

            return new ArrayList<CustomView>(views.values());
        }
        catch (SQLException e)
        {
            return Collections.emptyList();
        }
    }

    private void addCustomView(Container container, User user, Map<String, CustomView> views, CstmView collist, Map<Map.Entry<String, String>, QueryDefinition> queryDefs)
    {
        QueryDefinition qd = queryDefs.get(new Pair(collist.getSchema(), collist.getQueryName()));
        if (qd == null)
            qd = QueryService.get().getUserSchema(user, container, collist.getSchema()).getQueryDefForTable(collist.getQueryName());

        if (qd instanceof QueryDefinitionImpl && !views.containsKey(collist.getName()))
            views.put(collist.getName(), new CustomViewImpl((QueryDefinitionImpl)qd, collist));
    }

    private Map<String, QuerySnapshotDefinition> getAllQuerySnapshotDefs(Container container, String schemaName)
    {
        Map<String, QuerySnapshotDefinition> ret = new LinkedHashMap<String,QuerySnapshotDefinition>();
        for (QuerySnapshotDef queryDef : QueryManager.get().getQuerySnapshots(container, schemaName))
        {
            ret.put(queryDef.getName(), new QuerySnapshotDefImpl(queryDef));
        }
        return ret;
    }

    public QuerySnapshotDefinition getSnapshotDef(Container container, String schema, String name)
    {
        return getAllQuerySnapshotDefs(container, schema).get(name);
    }

    public boolean isQuerySnapshot(Container container, String schema, String name)
    {
        return QueryService.get().getSnapshotDef(container, schema, name) != null;
    }

    public List<QuerySnapshotDefinition> getQuerySnapshotDefs(Container container, String schema)
    {
        List<QuerySnapshotDefinition> ret = new ArrayList<QuerySnapshotDefinition>();
        for (QuerySnapshotDef queryDef : QueryManager.get().getQuerySnapshots(container, schema))
        {
            ret.add(new QuerySnapshotDefImpl(queryDef));
        }
        return ret;
    }

    public QuerySnapshotDefinition createQuerySnapshotDef(QueryDefinition queryDef, String name)
    {
        // if this is a table based query view, we just need to save the table name, else create a copy of the query
        // definition for the snapshot to refer back to on updates.
        if (queryDef.isTableQueryDefinition())
        {
            return new QuerySnapshotDefImpl(queryDef.getContainer().getId(), queryDef.getSchemaName(), queryDef.getName(), name);
        }
        else
        {
            QueryDefinitionImpl qd = new CustomQueryDefinitionImpl(queryDef.getContainer(), queryDef.getSchemaName(), queryDef.getName() + "_" + name);

            qd.setMetadataXml(queryDef.getMetadataXml());
            qd.setSql(queryDef.getSql());
            qd.setDescription(queryDef.getDescription());
            qd.setIsHidden(true);
            qd.setIsSnapshot(true);

            return new QuerySnapshotDefImpl(qd.getQueryDef(), name);
        }
    }

    private ColumnInfo getColumn(AliasManager manager, TableInfo table, Map<FieldKey, ColumnInfo> columnMap, FieldKey key)
    {
        if (key.getTable() == null)
        {
            String name = key.getName();
            ColumnInfo ret = table.getColumn(name);

            if (ret != null && key.getName().equals(table.getTitleColumn()) && ret.getURL() == null)
            {
                List<ColumnInfo> pkColumns = table.getPkColumns();
                Map<String, ColumnInfo> pkColumnMap = new HashMap<String, ColumnInfo>();
                for (ColumnInfo column : pkColumns)
                {
                    pkColumnMap.put(column.getName(), column);
                }
                StringExpressionFactory.StringExpression url = table.getDetailsURL(pkColumnMap);
                if (url != null)
                {
                    ret.setURL(url);
                }
            }
            if (ret != null && !AliasManager.isLegalName(ret.getName()))
                ret = new AliasedColumn(ret.getName(), manager.decideAlias(key.toString()), ret);
            return ret;
        }

        if (columnMap.containsKey(key))
            return columnMap.get(key);

        ColumnInfo parent = getColumn(manager, table, columnMap, key.getParent());
        if (parent == null)
            return null;

        ColumnInfo lookup = table.getLookupColumn(parent, StringUtils.trimToNull(key.getName()));
        if (lookup == null)
            return null;

        // slight hack here, if lookup name doesn't match, then AliasedColumn will set caption=null
        String name = key.toString();
        lookup.setName(name);
        AliasedColumn ret = new AliasedColumn(key.toString(), manager.decideAlias(key.toString()), lookup);
        columnMap.put(key, ret);
        return ret;
    }


    @NotNull
    public Map<FieldKey, ColumnInfo> getColumns(TableInfo table, Collection<FieldKey> fields)
    {
        AliasManager manager = new AliasManager(table, null);
        Map<FieldKey, ColumnInfo> ret = new LinkedHashMap<FieldKey,ColumnInfo>();
        Map<FieldKey, ColumnInfo> columnMap = new HashMap<FieldKey,ColumnInfo>();
        for (FieldKey field : fields)
        {
            ColumnInfo column = getColumn(manager, table, columnMap, field);
            if (column != null)
            {
                ret.put(field, column);
            }
        }
        return ret;
    }


    public List<DisplayColumn> getDisplayColumns(TableInfo table, Collection<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> fields)
    {
        List<DisplayColumn> ret = new ArrayList<DisplayColumn>();
        Set<FieldKey> fieldKeys = new HashSet<FieldKey>();
        for (Map.Entry<FieldKey, ?> entry : fields)
        {
            fieldKeys.add(entry.getKey());
        }
        Map<FieldKey, ColumnInfo> columns = getColumns(table, fieldKeys);
        for (Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>> entry : fields)
        {
            ColumnInfo column = columns.get(entry.getKey());
            if (column == null)
                continue;
            DisplayColumn displayColumn = column.getRenderer();
            String caption = entry.getValue().get(CustomView.ColumnProperty.columnTitle);
            if (caption != null)
            {
                displayColumn.setCaption(caption);
            }
            ret.add(displayColumn);
        }
        return ret;
    }


    public void ensureRequiredColumns(TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort, Set<String> unresolvedColumns)
    {
        AliasManager manager = new AliasManager(table, columns);
        Set<FieldKey> selectedColumns = new HashSet<FieldKey>();
        Map<FieldKey, ColumnInfo> columnMap = new HashMap<FieldKey,ColumnInfo>();
        for (ColumnInfo column : columns)
        {
            FieldKey key = FieldKey.fromString(column.getName());
            selectedColumns.add(key);
            columnMap.put(key, column);
        }
        Set<String> names = new HashSet<String>();
        if (filter != null)
        {
            names.addAll(filter.getWhereParamNames());
        }
        if (sort != null)
        {
            for (Sort.SortField field : sort.getSortList())
            {
                names.add(field.getColumnName());
            }
        }
        for (String name : names)
        {
            if (StringUtils.isEmpty(name))
                continue;
            FieldKey field = FieldKey.fromString(name);
            if (selectedColumns.contains(field))
                continue;
            ColumnInfo column = getColumn(manager, table, columnMap, field);

            if (column != null)
            {
                assert field.getTable() == null || columnMap.containsKey(field);
                columns.add(column);
            }
            else
            {
                if (unresolvedColumns != null)
                {
                    unresolvedColumns.add(name);
                }
            }
        }
        if (unresolvedColumns != null)
        {
            for (String columnName : unresolvedColumns)
            {
                if (filter instanceof SimpleFilter)
                {
                    SimpleFilter simpleFilter = (SimpleFilter) filter;
                    simpleFilter.deleteConditions(columnName);
                }
                if (sort != null)
                {
                    sort.deleteSortColumn(columnName);
                }
            }
        }
    }


    public String[] getAvailableWebPartNames(UserSchema schema)
    {
        return new String[0];
    }

    public WebPartView[] getWebParts(UserSchema schema, String location)
    {
        return new WebPartView[0];
    }

    public Map<String, UserSchema> getDbUserSchemas(DefaultSchema folderSchema)
    {
        Map<String, UserSchema> ret = new HashMap<String, UserSchema>();
        DbUserSchemaDef[] defs = QueryManager.get().getDbUserSchemaDefs(folderSchema.getContainer());
        for (DbUserSchemaDef def : defs)
        {
            ret.put(def.getUserSchemaName(), new DbUserSchema(folderSchema.getUser(), folderSchema.getContainer(), def));
        }
        return ret;
    }

    public List<FieldKey> getDefaultVisibleColumns(List<ColumnInfo> columns)
    {
        List<FieldKey> ret = new ArrayList<FieldKey>();
        for (ColumnInfo column : columns)
        {
            if (column.isHidden())
                continue;
            if (CustomViewImpl.isUnselectable(column))
                continue;
            ret.add(FieldKey.fromParts(column.getName()));
        }
        return ret;
    }


    public TableInfo overlayMetadata(TableInfo tableInfo, String tableName, UserSchema schema)
    {
        if (tableInfo instanceof AbstractTableInfo && tableInfo.isMetadataOverrideable())
        {
            QueryDef queryDef = QueryManager.get().getQueryDef(schema.getContainer(), schema.getSchemaName(), tableName, false);
            if (queryDef != null && queryDef.getMetaData() != null)
            {
                try
                {
                    TablesDocument doc = TablesDocument.Factory.parse(queryDef.getMetaData());

                    List<QueryException> errors = new ArrayList<QueryException>();
                    ((AbstractTableInfo)tableInfo).loadFromXML(schema, doc.getTables().getTableArray(0), errors);
                }
                catch (org.apache.xmlbeans.XmlException e)
                {
                    // throw new RuntimeException(e);
                }
            }
        }
        return tableInfo;
    }


	public ResultSet select(QuerySchema schema, String sql) throws SQLException
	{
		Query q = new Query(schema);
		q.parse(sql);
		if (q.getParseErrors().size() > 0)
			throw q.getParseErrors().get(0);
		TableInfo table = q.getTableInfo();
		if (q.getParseErrors().size() > 0)
			throw q.getParseErrors().get(0);
        SQLFragment sqlf = getSelectSQL(table, null, null, null, 0, 0);
		return Table.executeQuery(table.getSchema(), sqlf);
	}


    public ResultSet select(TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort) throws SQLException
    {
        SQLFragment sql = getSelectSQL(table, columns, filter, sort, 0, 0);
		return Table.executeQuery(table.getSchema(), sql);
    }


	public SQLFragment getSelectSQL(TableInfo table, Collection<ColumnInfo> selectColumns, Filter filter, Sort sort, int rowCount, long offset)
	{
        SqlDialect dialect = table.getSqlDialect();
        Map<String, SQLFragment> joins = new LinkedHashMap<String, SQLFragment>();

		if (null == selectColumns)
			selectColumns = table.getColumns();

        ArrayList<ColumnInfo> allColumns = new ArrayList<ColumnInfo>(selectColumns);
        ensureRequiredColumns(table, allColumns, filter, sort, null);
        Map<String, ColumnInfo> columnMap = Table.createColumnMap(table, allColumns);
        boolean requiresExtraColumns = allColumns.size() > selectColumns.size();

        SQLFragment outerSelect = new SQLFragment("SELECT *");
        SQLFragment selectFrag = new SQLFragment("SELECT");
        String strComma = "\n";

        String tableAlias = AliasManager.makeLegalName(table.getName(), table.getSchema().getSqlDialect());
		for (ColumnInfo column : allColumns)
		{
			assert column.getParentTable() == table : "Column " + column + " is from the wrong table: " + column.getParentTable() + " instead of " + table;
			column.declareJoins(tableAlias, joins);
            selectFrag.append(strComma);
            selectFrag.append(column.getValueSql(tableAlias));
            selectFrag.append(" AS " );
            selectFrag.append(dialect.getColumnSelectName(column.getAlias()));
            strComma = ",\n";
        }
        if (requiresExtraColumns)
        {
            outerSelect = new SQLFragment("SELECT ");
            strComma = "";
            for (ColumnInfo column : selectColumns)
            {
                outerSelect.append(strComma);
                outerSelect.append(dialect.getColumnSelectName(column.getAlias()));
                strComma = ",";
            }
        }

		SQLFragment fromFrag = new SQLFragment("FROM ");
        String selectName = table.getSelectName();
        if (null != selectName)
        {
            fromFrag.append(selectName);
        }
        else
        {
            fromFrag.append("(");
            fromFrag.append(table.getFromSQL());
            fromFrag.append(")");
        }
        fromFrag.append(" ").append(tableAlias).append(" ");

		for (Map.Entry<String, SQLFragment> entry : joins.entrySet())
		{
			fromFrag.append("\n").append(entry.getValue());
		}

		SQLFragment filterFrag = null;
		if (filter != null)
		{
			filterFrag = filter.getSQLFragment(dialect, columnMap);
		}

		String orderBy = null;
		if ((sort == null || sort.getSortList().size() == 0) && (rowCount > 0 || offset > 0))
		{
			sort = createDefaultSort(selectColumns);
		}
		if (sort != null)
		{
			orderBy = sort.getOrderByClause(dialect, columnMap);
		}

		if (filterFrag == null && sort == null && rowCount == 0 && offset == 0)
		{
			selectFrag.append("\n").append(fromFrag);
			return selectFrag;
		}

		SQLFragment nestedFrom = new SQLFragment();
		nestedFrom.append("FROM (\n").append(selectFrag).append("\n").append(fromFrag).append(") x\n");

		SQLFragment ret = dialect.limitRows(outerSelect, nestedFrom, filterFrag, orderBy, rowCount, offset);
        if (AppProps.getInstance().isDevMode())
        {
            SQLFragment t = new SQLFragment();
            t.appendComment("<QueryServiceImpl.getSelectSQL()>");
            t.append(ret);
            t.appendComment("</QueryServiceImpl.getSelectSQL()>");
            String s = _prettyPrint(t.getSQL());
            ret = new SQLFragment(s, ret.getParams());
        }
	    return ret;
    }

	private static Sort createDefaultSort(Collection<ColumnInfo> columns)
	{
		Sort sort = new Sort();
		addSortableColumns(sort, columns, true);

		if (sort.getSortList().size() == 0)
		{
			addSortableColumns(sort, columns, false);
		}

		return sort;
	}

	private static void addSortableColumns(Sort sort, Collection<ColumnInfo> columns, boolean usePrimaryKey)
	{
		for (ColumnInfo column : columns)
		{
			if (usePrimaryKey && !column.isKeyField())
				continue;
			ColumnInfo sortField = column.getSortField();
			if (sortField != null)
			{
				sort.getSortList().add(sort.new SortField(sortField.getName(), column.getSortDirection()));
				return;
			}
		}
	}

    public void addQueryListener(QueryListener listener)
    {
        QueryManager.get().addQueryListener(listener);
    }


    private String _prettyPrint(String s)
    {
        StringBuilder sb = new StringBuilder(s.length() + 200);

        String[] lines = StringUtils.split(s, '\n');
        int indent = 0;
        for (String line : lines)
        {
            String t = line.trim();
            if (t.length() == 0)
                continue;
            if (t.startsWith("-- </"))
                indent = Math.max(0,indent-1);

            for (int i=0 ; i<indent ; i++)
                sb.append('\t');
            sb.append(line);
            sb.append('\n');

            if (t.startsWith("-- <") && !t.startsWith("-- </"))
                indent++;
        }
        return sb.toString();
    }
}
