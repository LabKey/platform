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
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.FileUtil;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.query.sql.Query;
import org.labkey.query.sql.QueryTableInfo;
import org.labkey.query.design.*;
import org.labkey.query.persist.CstmView;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.view.CustomViewSetKey;

import javax.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.*;
import java.io.FilenameFilter;
import java.io.File;

@SuppressWarnings({"ThrowableInstanceNeverThrown"})
public abstract class QueryDefinitionImpl implements QueryDefinition
{
    protected static final FilenameFilter customViewFileFilter = new FilenameFilter(){
        public boolean accept(File dir, String name)
        {
            return name.toLowerCase().endsWith(ModuleCustomViewDef.FILE_EXTENSION);
        }
    };

    final static private QueryManager mgr = QueryManager.get();
    final static private Logger log = Logger.getLogger(QueryDefinitionImpl.class);
    protected UserSchema _schema = null;
    protected QueryDef _queryDef;
    private boolean _dirty;

    public QueryDefinitionImpl(QueryDef queryDef)
    {
        _queryDef = queryDef;
        _dirty = queryDef.getQueryDefId() == 0;
    }

    public QueryDefinitionImpl(Container container, String schema, String name)
    {
        _queryDef = new QueryDef();
        _queryDef.setName(name);
        _queryDef.setSchema(schema);
        _queryDef.setContainer(container.getId());
        _dirty = true;
    }

    public boolean canInherit()
    {
        return (_queryDef.getFlags() & QueryManager.FLAG_INHERITABLE) != 0;
    }



    public void delete(User user) throws SQLException
    {
        if (!canEdit(user))
        {
            throw new IllegalArgumentException("Access denied");
        }
        QueryManager.get().delete(user, _queryDef);
        _queryDef = null;
    }

    protected boolean isNew()
    {
        return _queryDef.getQueryDefId() == 0;
    }

    public boolean canEdit(User user)
    {    
        return getContainer().hasPermission(user, ACL.PERM_ADMIN);
    }

    public CustomView getCustomView(User user, HttpServletRequest request, String name)
    {
        return getAllCustomViews(user, request, true, true).get(name);
    }

    public CustomView createCustomView(User user, String name)
    {
        return new CustomViewImpl(this, user, name);
    }

    public Map<String, CustomView> getCustomViews(User user, HttpServletRequest request)
    {
        return getAllCustomViews(user, request, true);
    }

    private Map<String, CustomView> getAllCustomViews(User user, HttpServletRequest request, boolean inheritable)
    {
        return getAllCustomViews(user, request, inheritable, false);
    }

    private Map<String, CustomView> getAllCustomViews(User user, HttpServletRequest request, boolean inheritable, boolean allModules)
    {
        Map<String, CustomView> ret = new HashMap<String, CustomView>();
        try
        {
            Container container = getContainer();
            if (user != null && user.isGuest())
            {
                for (CstmView view : CustomViewSetKey.getCustomViewsFromSession(request, this).values())
                {
                    ret.put(view.getName(), new CustomViewImpl(this, view));
                }
            }
            addCustomViews(ret, mgr.getAllColumnLists(container, _queryDef.getSchema(), _queryDef.getName(), user, inheritable));

            //finally, look in all the active modules for any views defined in the file system
            Collection<Module> modules = allModules ? ModuleLoader.getInstance().getModules() : container.getActiveModules();
            for(Module module : modules)
            {
                File queryDir = new File(getQueriesDir(module), getSchemaName() + "/" + FileUtil.makeLegalName(getName()));
                if(queryDir.exists())
                {
                    for(File viewFile : queryDir.listFiles(customViewFileFilter))
                    {
                        ModuleCustomViewDef viewDef = (ModuleCustomViewDef)(QueryServiceImpl.getModuleResourcesCache().get(viewFile.getAbsolutePath()));
                        if(null == viewDef || viewDef.isStale())
                        {
                            viewDef = new ModuleCustomViewDef(viewFile);
                            QueryServiceImpl.getModuleResourcesCache().put(viewFile.getAbsolutePath(), viewDef);
                        }
                        ret.put(viewDef.getName(), new ModuleCustomView(this, viewDef));
                    }
                }
            }

        }
        catch (SQLException e)
        {
            log.error("Error", e);
        }
        return ret;
    }

    protected File getQueriesDir(Module module)
    {
        return new File(module.getExplodedPath(), "queries");
    }

    private void addCustomViews(Map<String, CustomView> map, List<CstmView> views)
    {
        for (CstmView view : views)
        {
            if (!map.containsKey(view.getName()))
            {
                map.put(view.getName(), new CustomViewImpl(this, view));
            }
        }
    }

    public Container getContainer()
    {
        return ContainerManager.getForId(_queryDef.getContainerId());
    }

    public String getName()
    {
        return _queryDef.getName();
    }

    public List<QueryException> getParseErrors(QuerySchema schema)
    {
        List<QueryException> ret = new ArrayList<QueryException>(getQuery(schema).getParseErrors());
        String metadata = StringUtils.trimToNull(getMetadataXml());
        if (metadata != null)
        {
            XmlOptions options = new XmlOptions();
            List<XmlError> errors = new ArrayList<XmlError>();
            options.setErrorListener(errors);
            try
            {
                TablesDocument table = TablesDocument.Factory.parse(metadata, options);
                table.validate(options);
            }
            catch (XmlException xmle)
            {
                ret.add(new MetadataException(xmle.getMessage()));
            }
            for (XmlError xmle : errors)
            {
                ret.add(new MetadataException(xmle.getMessage()));
            }
        }
        return ret;
    }


    public Query getQuery(QuerySchema schema)
    {
        Query query = new Query(schema);
        String sql = getSql();
        if (sql != null)
        {
            query.parse(sql);
        }
        query.setName(getSchemaName() + "." + getName());
        return query;
    }


    public Query getQuery(QuerySchema schema, List<QueryException> errors)
    {
        Query query = new Query(schema);
        String sql = getSql();
        if (sql != null)
        {
            query.parse(sql);
        }
        TablesDocument doc = getTablesDocument(errors);
        query.setTablesDocument(doc);
        query.setName(getSchemaName() + "." + getName());
        return query;
    }


    public UserSchema getSchema()
    {
        if (null == _schema)
            _schema = (UserSchema) DefaultSchema.get(null, getContainer()).getSchema(getSchemaName());
        assert _schema.getSchemaName().equals(getSchemaName());
        return _schema;
    }

    public String getSchemaName()
    {
        return _queryDef.getSchema();
    }


    private TablesDocument getTablesDocument(List<QueryException> errors)
    {
        String xml = getMetadataXml();
        if (xml != null)
        {
            try
            {
                return TablesDocument.Factory.parse(xml);
            }
            catch (XmlException xmlException)
            {
                errors.add(new QueryParseException("Error in XML", xmlException, 0, 0));
            }
        }
        return null;
    }

    public TableInfo getTable(QuerySchema schema, List<QueryException> errors, boolean includeMetadata)
    {
        if (errors == null)
        {
            errors = new ArrayList<QueryException>();
        }
        Query query = getQuery(schema, errors);
        if (!includeMetadata)
            query.setTablesDocument(null);
        TableInfo ret = query.getTableInfo();
        if (null != ret && null != query.getTablesDocument())
            ((QueryTableInfo)ret).loadFromXML(schema, query.getTablesDocument().getTables().getTableArray(0), errors);
        errors.addAll(query.getParseErrors());
        return ret;
    }

    public TableInfo getMainTable()
    {
        Query query = getQuery(getSchema());
        Set<FieldKey> tables = query.getFromTables();
        if (tables.size() != 1)
            return null;
        return query.getFromTable(tables.iterator().next());
    }

    public String getMetadataXml()
    {
        return _queryDef.getMetaData();
    }

    public void setContainer(Container container)
    {
        if (container.equals(getContainer()))
            return;
        QueryDef queryDefNew = new QueryDef();
        queryDefNew.setSchema(_queryDef.getSchema());
        queryDefNew.setName(getName());
        queryDefNew.setContainer(container.getId());
        queryDefNew.setSql(_queryDef.getSql());
        queryDefNew.setMetaData(_queryDef.getMetaData());
        queryDefNew.setDescription(_queryDef.getDescription());
        _queryDef = queryDefNew;
        _dirty = true;
    }

    public void save(User user, Container container) throws SQLException
    {
        setContainer(container);
        if (!_dirty)
            return;
        if (isNew())
        {
            _queryDef = QueryManager.get().insert(user, _queryDef);
        }
        else
        {
            _queryDef = QueryManager.get().update(user, _queryDef);
        }
        _dirty = false;
    }

    public void setCanInherit(boolean f)
    {
        edit().setFlags(mgr.setCanInherit(_queryDef.getFlags(), f));
    }

    public boolean isHidden()
    {
        return mgr.isHidden(_queryDef.getFlags());
    }

    public void setIsHidden(boolean f)
    {
        edit().setFlags(mgr.setIsHidden(_queryDef.getFlags(), f));
    }

    public boolean isSnapshot()
    {
        return mgr.isSnapshot(_queryDef.getFlags());
    }

    public void setIsSnapshot(boolean f)
    {
        edit().setFlags(mgr.setIsSnapshot(_queryDef.getFlags(), f));
    }

    public void setMetadataXml(String xml)
    {
        edit().setMetaData(StringUtils.trimToNull(xml));
    }

    public ActionURL urlFor(QueryAction action)
    {
        return urlFor(action, getContainer());
    }

    public ActionURL urlFor(QueryAction action, Container container)
    {
        ActionURL url = new ActionURL("query", action.toString(), container);
        url.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName, getName());
        url.addParameter(QueryParam.schemaName.toString(), getSchemaName());
        return url;
    }

    public String getDescription()
    {
        return _queryDef.getDescription();
    }

    public void setDescription(String description)
    {
        edit().setDescription(description);
    }

    public QueryDef getQueryDef()
    {
        return _queryDef;
    }

    public List<ColumnInfo> getColumns(CustomView view, TableInfo table)
    {
        if (table == null)
            throw new NullPointerException();
        if (view != null)
        {
            Map<FieldKey,ColumnInfo> map = QueryService.get().getColumns(table, view.getColumns());
            if (!map.isEmpty())
            {
                return new ArrayList<ColumnInfo>(map.values());
            }
        }
        return new ArrayList<ColumnInfo>(QueryService.get().getColumns(table, table.getDefaultVisibleColumns()).values());
    }

    public List<DisplayColumn> getDisplayColumns(CustomView view, TableInfo table)
    {
        if (table == null)
            throw new NullPointerException();
        List<DisplayColumn> ret;
        if (view != null)
        {
            ret = QueryService.get().getDisplayColumns(table, view.getColumnProperties());
            if (!ret.isEmpty())
            {
                return ret;
            }
        }
        ret = new ArrayList<DisplayColumn>();
        for (ColumnInfo column : QueryService.get().getColumns(table, table.getDefaultVisibleColumns()).values())
        {
            ret.add(column.getRenderer());
        }
        return ret;
    }

    public QueryDocument getDesignDocument(QuerySchema schema)
    {
        Query query = getQuery(schema); 
        QueryDocument ret = query.getDesignDocument();
        if (ret == null)
            return null;
        Map<String, DgColumn> columns = new HashMap<String, DgColumn>();
        for (DgColumn dgColumn : ret.getQuery().getSelect().getColumnArray())
        {
            columns.put(dgColumn.getAlias(), dgColumn);
        }
        String strMetadata = getMetadataXml();
        if (strMetadata != null)
        {
            try
            {
                TablesDocument doc = TablesDocument.Factory.parse(getMetadataXml());
                for (ColumnType column : doc.getTables().getTableArray()[0].getColumns().getColumnArray())
                {
                    DgColumn dgColumn = columns.get(column.getColumnName());
                    if (dgColumn != null)
                    {
                        dgColumn.setMetadata(column);
                    }
                }
            }
            catch (Exception e)
            {

            }
        }
        DgQuery.From from = ret.getQuery().addNewFrom();
        for (FieldKey key : query.getFromTables())
        {
            DgTable dgTable = from.addNewTable();
            dgTable.setAlias(key.toString());
            TableXML.initTable(dgTable.addNewMetadata(), query.getFromTable(key), key);
        }

        return ret;
    }

    public boolean updateDesignDocument(QuerySchema schema, QueryDocument doc, List<QueryException> errors)
    {
        Map<String, DgColumn> columns = new LinkedHashMap<String, DgColumn>();
        DgQuery dgQuery = doc.getQuery();
        DgQuery.Select select = dgQuery.getSelect();
        for (DgColumn column : select.getColumnArray())
        {
            String alias = column.getAlias();
            if (alias == null)
            {
                DgValue value = column.getValue();
                if (value.getField() == null)
                {
                    errors.add(new QueryException("Expression column '" + value.getSql() + "' requires an alias."));
                    return false;
                }
                FieldKey key = FieldKey.fromString(value.getField().getStringValue());
                alias = key.getName();
            }
            if (columns.containsKey(alias))
            {
                errors.add(new QueryException("There is more than one column with the alias '" + alias + "'."));
                return false;
            }
            columns.put(alias, column);
        }
        Query query = getQuery(schema);
        query.update(dgQuery, errors);
        setSql(query.getQueryText());
        String xml = getMetadataXml();
        TablesDocument tablesDoc;
        TableType xbTable;
        if (xml != null)
        {
            try
            {
                tablesDoc = TablesDocument.Factory.parse(xml);
            }
            catch (XmlException xmlException)
            {
                errors.add(new QueryException("There was an error parsing the query's original Metadata XML: " + xmlException.getMessage()));
                return false;
            }
        }
        else
        {
            tablesDoc = TablesDocument.Factory.newInstance();
        }
        TablesDocument.Tables tables = tablesDoc.getTables();
        if (tables == null)
        {
            tables = tablesDoc.addNewTables();
        }
        if (tables.getTableArray().length < 1)
        {
            xbTable = tables.addNewTable();
        }
        else
        {
            xbTable = tables.getTableArray()[0];
        }
        
        xbTable.setTableName(getName());
        xbTable.setTableDbType("NOT_IN_DB");
        TableType.Columns xbColumns = xbTable.getColumns();
        if (xbColumns == null)
        {
            xbColumns = xbTable.addNewColumns();
        }
        List<ColumnType> lstColumns = new ArrayList<ColumnType>();
        for (Map.Entry<String, DgColumn> entry : columns.entrySet())
        {
            ColumnType xbColumn = entry.getValue().getMetadata();
            if (xbColumn != null)
            {
                xbColumn.setColumnName(entry.getKey());
                lstColumns.add(xbColumn);
            }
        }
        xbColumns.setColumnArray(lstColumns.toArray(new ColumnType[lstColumns.size()]));
        setMetadataXml(tablesDoc.toString());
        return errors.size() == 0;
    }

    protected QueryDef edit()
    {
        if (_dirty)
            return _queryDef;
        _queryDef = _queryDef.clone();
        _dirty = true;
        return _queryDef;
    }

    public boolean isTableQueryDefinition()
    {
        return false;
    }

    public boolean isMetadataEditable()
    {
        return true;
    }
}
