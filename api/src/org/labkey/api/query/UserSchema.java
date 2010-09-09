/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.api.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.BoundMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;

import javax.servlet.ServletException;
import java.util.*;


abstract public class UserSchema extends AbstractSchema
{
    protected String _name;
    protected String _description;

    public UserSchema(String name, String description, User user, Container container, DbSchema dbSchema)
    {
        super(dbSchema, user, container);
        _name = name;
        _description = description;
    }

    public String getName()
    {
        return _name;
    }

    @Nullable
    public String getDescription()
    {
        return _description;
    }

    protected boolean canReadSchema()
    {
        User user = getUser();
        return user == User.getSearchUser() || getContainer().hasPermission(user, ReadPermission.class);
    }


    public TableInfo getTable(String name, boolean includeExtraMetadata)
    {
        ArrayList<QueryException> errors = new ArrayList<QueryException>();
        Object o = _getTableOrQuery(name, includeExtraMetadata, errors);
        if (o instanceof TableInfo)
        {
            return (TableInfo)o;
        }
        if (o instanceof QueryDefinition)
        {
            TableInfo t = ((QueryDefinition)o).getTable(this, errors, true);
            if (!errors.isEmpty())
                throw errors.get(0);
            return t;
        }
        return null;
    }


    public Object _getTableOrQuery(String name, boolean includeExtraMetadata, Collection<QueryException> errors)
    {
        if (name == null)
            return null;

        if (!canReadSchema())
            HttpView.throwUnauthorized("Cannot read query " + getSchemaName() + "." + name + " in " + getContainer().getPath());

        TableInfo table = createTable(name);
        if (table != null)
        {
            if (includeExtraMetadata)
                table = QueryService.get().overlayMetadata(table, name, this, errors);
            afterConstruct(table);
            return table;
        }

        QueryDefinition def = QueryService.get().getQueryDef(getUser(), getContainer(), getSchemaName(), name);

        if (def == null)
            return null;

        if (!includeExtraMetadata)
            def.setMetadataXml(null);

        return def;
    }

    public final TableInfo getTable(String name)
    {
        return getTable(name, true);
    }

    protected abstract TableInfo createTable(String name);

    abstract public Set<String> getTableNames();

    public Set<String> getVisibleTableNames()
    {
        return getTableNames();
    }

    public Container getContainer()
    {
        return _container;
    }

    public String getSchemaName()
    {
        return _name;
    }

    public Set<String> getSchemaNames()
    {
        Set<String> ret = new HashSet<String>(super.getSchemaNames());
        ret.add("Folder");
        return ret;
    }

    public QuerySchema getSchema(String name)
    {
        return DefaultSchema.get(_user, _container).getSchema(name);
    }

    public boolean canCreate()
    {
        return getContainer().hasPermission(getUser(), UpdatePermission.class);
    }

    public ActionURL urlFor(QueryAction action)
    {
        ActionURL ret;
        ret = new ActionURL("query", action.toString(), getContainer());
        ret.addParameter(QueryParam.schemaName.toString(), getSchemaName());
        return ret;
    }

    public ActionURL urlFor(QueryAction action, QueryDefinition queryDef)
    {
        return queryDef.urlFor(action, getContainer());
    }

    /**
     * Gets a pre-substitution expression for use in row-specific URLs, such as details or update views 
     */
    public StringExpression urlExpr(QueryAction action, QueryDefinition queryDef)
    {
        return queryDef.urlExpr(action, getContainer());
    }

    public QueryDefinition getQueryDefForTable(String name)
    {                                                
        return QueryService.get().createQueryDefForTable(this, name);
    }

    public ActionURL urlSchemaDesigner()
    {
        ActionURL ret = new ActionURL("query", "schema", getContainer());
        ret.addParameter(QueryParam.schemaName.toString(), getSchemaName());
        return ret;
    }

    @Deprecated
    public QueryView createView(ViewContext context, QuerySettings settings) throws ServletException
    {
        return new QueryView(this, settings);
    }

    public QueryView createView(ViewContext context, QuerySettings settings, BindException errors) throws ServletException
    {
        return new QueryView(this, settings, errors);
    }

    /**
     * Returns a sorted list of names for both built-in tables and custom queries.
     * @param visibleOnly Only return the visible tables and queries.
     * @return The names of the tables and queries.
     */
    public List<String> getTableAndQueryNames(boolean visibleOnly)
    {
        return new ArrayList<String>(_getQueries(visibleOnly).keySet());        
    }

    /**
     * Returns a sorted list of QueryDefinitions for both built-in tables and custom queries.
     * @param visibleOnly Only return the visible tables and queries.
     * @return The QueryDefinitions.
     */
    public List<QueryDefinition> getTablesAndQueries(boolean visibleOnly)
    {
        return new ArrayList<QueryDefinition>(_getQueries(visibleOnly).values());
    }

    protected Map<String, QueryDefinition> _getQueries(boolean visibleOnly)
    {
        TreeMap<String, QueryDefinition> set = new TreeMap<String, QueryDefinition>(new Comparator<String>()
        {
            @Override
            public int compare(String o1, String o2)
            {
                return o1.compareToIgnoreCase(o2);
            }
        });

        for (String tableName : visibleOnly ? getVisibleTableNames() : getTableNames())
            set.put(tableName, QueryService.get().createQueryDefForTable(this, tableName));

        for (QueryDefinition query : QueryService.get().getQueryDefs(getUser(), getContainer(), getSchemaName()).values())
        {
            if (!visibleOnly || !query.isHidden())
                set.put(query.getName(), query);
        }

        return set;
    }

    /** override this method to return schema specific QuerySettings object */
    protected QuerySettings createQuerySettings(String dataRegionName)
    {
        return new QuerySettings(dataRegionName);
    }

    public final QuerySettings getSettings(Portal.WebPart webPart, ViewContext context)
    {
        String dataRegionName = webPart.getPropertyMap().get("dataRegionName");

        if (null == dataRegionName)
            dataRegionName = "qwp" + webPart.getIndex();

        QuerySettings settings = createQuerySettings(dataRegionName);
        (new BoundMap(settings)).putAll(webPart.getPropertyMap());
        settings.init(context);

        return settings;
    }

    public final QuerySettings getSettings(ViewContext context, String dataRegionName)
    {
        QuerySettings settings = createQuerySettings(dataRegionName);
        settings.init(context);
        settings.setSchemaName(getSchemaName());

        return settings;
    }

    public final QuerySettings getSettings(ViewContext context, String dataRegionName, String queryName)
    {
        QuerySettings settings = getSettings(context, dataRegionName);
        settings.setQueryName(queryName);

        return settings;
    }

    public final QuerySettings getSettings(ViewContext context, String dataRegionName, String queryName, String viewName)
    {
        QuerySettings settings = getSettings(context, dataRegionName, queryName);
        settings.setViewName(viewName);

        return settings;
    }

    public final QuerySettings getSettings(PropertyValues pvs, String dataRegionName)
    {
        QuerySettings settings = createQuerySettings(dataRegionName);
        settings.init(pvs);
        settings.setSchemaName(getSchemaName());

        return settings;
    }

    /**
     * Returns a schema suitable for use with ontology manager for the given query.
     * May return null if ontology manager is not supported for the query.
     * @param queryName The name of the query
     * @return A domain URI for ontology manager or null.
     */
    @Nullable
    public String getDomainURI(String queryName)
    {
        return null;
    }
}
