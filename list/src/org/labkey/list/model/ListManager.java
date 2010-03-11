/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

package org.labkey.list.model;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.list.view.ListController;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ListManager implements SearchService.DocumentProvider
{
    static private ListManager instance;
    public static final String LIST_AUDIT_EVENT = "ListAuditEvent";

    synchronized static public ListManager get()
    {
        if (instance == null)
            instance = new ListManager();
        return instance;
    }

    public DbSchema getSchema()
    {
        return ExperimentService.get().getSchema();
    }

    public TableInfo getTinfoList()
    {
        return getSchema().getTable("list");
    }

    public TableInfo getTinfoIndexInteger()
    {
        return getSchema().getTable("indexInteger");
    }

    public TableInfo getTinfoIndexVarchar()
    {
        return getSchema().getTable("indexVarchar");
    }

    public ListDef[] getLists(Container container) throws SQLException
    {
        ListDef.Key key = new ListDef.Key(container);
        return key.select();
    }

    public ListDef[] getAllLists() throws SQLException
    {
        return Table.select(getTinfoList(), Table.ALL_COLUMNS, null, null, ListDef.class);
    }

    public ListDef getList(Container container, int id) throws SQLException
    {
        SimpleFilter filter = new PkFilter(getTinfoList(), id);
        filter.addCondition("Container", container);
        return Table.selectObject(getTinfoList(), filter, null, ListDef.class);
    }
    
    public ListDef insert(User user, ListDef def) throws SQLException
    {
        ListDef ret = Table.insert(user, getTinfoList(), def);
        Container c = ContainerManager.getForId(def.getContainerId());
        if (null != c)
            enumerateDocuments(null, c, null);
        return ret;
    }

    public ListDef update(User user, ListDef def) throws SQLException
    {
        ListDef ret = Table.update(user, getTinfoList(), def, def.getRowId());
        Container c = ContainerManager.getForId(def.getContainerId());
        if (null != c)
            enumerateDocuments(null, c, null);
        return ret;
    }


    // COMBINE WITH DATASET???
    public static final SearchService.SearchCategory listCategory = new SearchService.SearchCategory("list", "List");

    public void enumerateDocuments(SearchService.IndexTask t, final @NotNull Container c, Date since)
    {
        final SearchService.IndexTask task = null==t ? ServiceRegistry.get(SearchService.class).defaultTask() : t;
        
        Runnable r = new Runnable()
        {
            public void run()
            {

                Map<String, ListDefinition> lists = ListService.get().getLists(c);
                for (ListDefinition list : lists.values())
                {
                    StringBuilder body = new StringBuilder();
                    Map<String,Object> props = new HashMap<String,Object>();

                    props.put(SearchService.PROPERTY.categories.toString(), listCategory.toString());
                    props.put(SearchService.PROPERTY.displayTitle.toString(), "List " + list.getName());

                    if (!StringUtils.isEmpty(list.getDescription()))
                        body.append(list.getDescription()).append("\n");

                    ActionURL url = new ActionURL(ListController.GridAction.class, c);
                    url.setExtraPath(c.getId());
                    url.addParameter("listId",list.getListId());

                    Domain domain = list.getDomain();
                    for (DomainProperty property : domain.getProperties())
                    {
                        String n = StringUtils.trimToEmpty(property.getName());
                        String l = StringUtils.trimToEmpty(property.getLabel());
                        if (n.equals(l))
                            l = "";
                        body.append(n).append(" ").append(l).append(",\n");
                    }

                    String documentId = "list:" + ((ListDefinitionImpl)list).getEntityId();
                    SimpleDocumentResource r = new SimpleDocumentResource(
                            new Path(documentId),
                            documentId,
                            list.getContainer().getId(),
                            "text/plain",
                            body.toString().getBytes(),
                            url,
                            props);
                    task.addResource(r, SearchService.PRIORITY.item);
                }
            }
        };

        task.addRunnable(r, SearchService.PRIORITY.group);
    }

    
    public void indexDeleted() throws SQLException
    {
    }
}
