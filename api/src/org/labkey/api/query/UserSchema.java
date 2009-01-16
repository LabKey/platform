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

package org.labkey.api.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.common.util.BoundMap;
import org.springframework.beans.PropertyValues;

import javax.servlet.ServletException;
import java.util.*;


abstract public class UserSchema extends AbstractSchema
{
    protected String _name;

    public UserSchema(String name, User user, Container container, DbSchema dbSchema)
    {
        super(dbSchema, user, container);
        _name = name;
    }

    public final TableInfo getTable(String name, String alias, boolean includeExtraMetadata)
    {
        if (name == null)
            return null;
        TableInfo table = createTable(name, alias);
        if (table != null)
        {
            if (!includeExtraMetadata)
            {
                return table;
            }
            return QueryService.get().overlayMetadata(table, name, this);
        }

        QueryDefinition def = QueryService.get().getQueryDef(getContainer(), getSchemaName(), name);
        if (def == null)
            return null;
        if (!includeExtraMetadata)
        {
            def.setMetadataXml(null);
        }
        return def.getTable(alias, this, null, true);
    }

    public final TableInfo getTable(String name, String alias)
    {
        return getTable(name, alias, true);
    }

    protected abstract TableInfo createTable(String name, String alias);

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
        return getContainer().hasPermission(getUser(), ACL.PERM_UPDATE);
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

    public QueryView createView(ViewContext context, QuerySettings settings) throws ServletException
    {
        return new QueryView(this, settings);
    }

    public List<String> getTableAndQueryNames(boolean visibleOnly)
    {
        Set<String> set = new HashSet<String>();
        set.addAll(visibleOnly ? getVisibleTableNames() : getTableNames());
        for (Map.Entry<String, QueryDefinition> entry : QueryService.get().getQueryDefs(getContainer(), getSchemaName()).entrySet())
        {
            if (!visibleOnly || !entry.getValue().isHidden())
            {
                set.add(entry.getKey());
            }
        }
        List<String> ret = new ArrayList<String>(set);

        Collections.sort(ret, new Comparator<String>()
        {

            public int compare(String o1, String o2)
            {
                return o1.compareToIgnoreCase(o2);
            }
        });
        return ret;
    }

    /** override this method to return schema specific QuerySettings object */
    protected QuerySettings createQuerySettings(String dataRegionName)
    {
        return new QuerySettings(dataRegionName);
    }

    public final QuerySettings getSettings(Portal.WebPart webPart, ViewContext context)
    {
        QuerySettings settings = createQuerySettings("qwp" + webPart.getIndex());
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

    public final QuerySettings getSettings(PropertyValues pvs, String dataRegionName)
    {
        QuerySettings settings = createQuerySettings(dataRegionName);
        settings.init(pvs);
        return settings;
    }
}
