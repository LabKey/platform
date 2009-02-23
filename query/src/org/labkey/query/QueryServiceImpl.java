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
import org.labkey.api.settings.AppProps;
import org.labkey.common.util.Pair;
import org.labkey.data.xml.TablesDocument;
import org.labkey.query.persist.DbUserSchemaDef;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.persist.QuerySnapshotDef;
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
        Map<Map.Entry<String, String>, QueryDefinition> ret = new LinkedHashMap<Map.Entry<String, String>, QueryDefinition>();

        //look in all the active modules in this container to see if they contain any query definitions
        if(null != schemaName)
        {
            for(Module module : container.getActiveModules())
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
        for (QueryDefinition queryDef : getAllQueryDefs(container, schema, true, true).values())
        {
            ret.put(queryDef.getName(), queryDef);
        }
        return ret.get(name);
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
        return new ArrayList<QuerySnapshotDefinition>(getAllQuerySnapshotDefs(container, schema).values());
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
                ret.setAlias(manager.decideAlias(key.toString()));
            return ret;
        }
        if (columnMap.containsKey(key))
        {
            return columnMap.get(key);
        }
        ColumnInfo parent = getColumn(manager, table, columnMap, key.getTable());
        if (parent == null)
        {
            return null;
        }
        ForeignKey fk = parent.getFk();
        if (fk == null)
        {
            return null;
        }
        ColumnInfo ret = fk.createLookupColumn(parent, key.getName().length() == 0 ? null : key.getName());
        if (ret == null)
        {
            return null;
        }
        ret.setName(key.toString());
        ret.setAlias(manager.decideAlias(key.toString()));
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
		TableInfo table = q.getTableInfo("QUERY");
		if (q.getParseErrors().size() > 0)
			throw q.getParseErrors().get(0);
        SQLFragment sqlf = getSelectSQL(table, null, null, null, 0, 0);
		return Table.executeQuery(table.getSchema(), sqlf);
	}


    public ResultSet select(TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort) throws SQLException
    {
        ensureRequiredColumns(table, columns, filter, sort, null);
        SQLFragment sql = getSelectSQL(table, columns, filter, sort, 0, 0);
		return Table.executeQuery(table.getSchema(), sql);
    }


	public SQLFragment getSelectSQL(TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort, int rowCount, long offset)
	{
		SqlDialect dialect = table.getSqlDialect();
		Map<String, SQLFragment> joins = new LinkedHashMap<String, SQLFragment>();

		SQLFragment selectFrag = new SQLFragment("SELECT");
		String strComma = "\n";

		if (null == columns)
			columns =  table.getColumns();

		for (ColumnInfo column : columns)
		{
			assert column.getParentTable() == table : "Column " + column + " is from the wrong table: " + column.getParentTable() + " instead of " + table;
			column.declareJoins(joins);
			selectFrag.append(strComma);
			selectFrag.append(column.getSelectSql());
			strComma = ",\n";
		}

		SQLFragment fromFrag = new SQLFragment("FROM ");
		fromFrag.append(table.getFromSQL());
		for (Map.Entry<String, SQLFragment> entry : joins.entrySet())
		{
			fromFrag.append("\n").append(entry.getValue());
		}

		SQLFragment filterFrag = null;
		Map<String, ColumnInfo> columnMap = Table.createColumnMap(table, columns);
		if (filter != null)
		{
			filterFrag = filter.getSQLFragment(dialect, columnMap);
		}

		String orderBy = null;
		if ((sort == null || sort.getSortList().size() == 0) && (rowCount > 0 || offset > 0))
		{
			sort = createDefaultSort(columns);
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
		if (AppProps.getInstance().isDevMode())
		{
			String s = StringUtils.replace(nestedFrom.getSQL(), "\n", "\n\t\t");
			nestedFrom = new SQLFragment(s, nestedFrom.getParams());
		}

		return dialect.limitRows(new SQLFragment("SELECT *"), nestedFrom, filterFrag, orderBy, rowCount, offset);
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
}
