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
import org.apache.log4j.Logger;
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
import org.labkey.common.util.Pair;
import org.labkey.data.xml.TablesDocument;
import org.labkey.query.persist.DbUserSchemaDef;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.persist.QuerySnapshotDef;
import org.labkey.query.view.DbUserSchema;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.io.File;
import java.io.FilenameFilter;

public class QueryServiceImpl extends QueryService
{
    static private final Logger _log = Logger.getLogger(QueryServiceImpl.class);

    protected static final FilenameFilter moduleQueryFileFilter = new FilenameFilter(){
        public boolean accept(File dir, String name)
        {
            return name.toLowerCase().endsWith(ModuleQueryDef.FILE_EXTENSION);
        }
    };

    private static Cache _moduleResourcesCache = new Cache(1024, Cache.DAY);

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
        return new TableQueryDefinition(schema, tableName, schema.getTable(tableName, "query"));
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
        for(Module module : container.getActiveModules())
        {
            File schemaDir = new File(getModuleQueriesDir(module), schemaName);
            if(schemaDir.exists())
            {
                for(File sqlFile : schemaDir.listFiles(moduleQueryFileFilter))
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


    public void ensureRequiredColumns(TableInfo table, List<ColumnInfo> columns, Filter filter, Sort sort, Set<String> unresolvedColumns)
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


    public ResultSet select(TableInfo table, List<ColumnInfo> columns, Filter filter, Sort sort) throws SQLException
    {
        SQLFragment sql = getSelectSQL(table, columns, filter, sort, 0, 0);
		return Table.executeQuery(table.getSchema(), sql);
    }


	public SQLFragment getSelectSQL(TableInfo table, List<ColumnInfo> columns, Filter filter, Sort sort, int rowCount, long offset)
	{
		ensureRequiredColumns(table, columns, filter, sort, null);
		return Table.getSelectSQL(table, columns, filter, sort, rowCount, offset);
	}
}
