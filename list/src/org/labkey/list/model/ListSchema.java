/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.list.view.ListController;
import org.labkey.list.view.ListQueryView;
import org.springframework.validation.BindException;

import java.util.Map;
import java.util.Set;

public class ListSchema extends UserSchema
{
    static public final String NAME = "lists";
    public static final String DESCR = "Contains a data table for each defined list";

    static public void register()
    {
        DefaultSchema.registerProvider(NAME, new DefaultSchema.SchemaProvider() {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new ListSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public ListSchema(User user, Container container)
    {
        super(NAME, DESCR, user, container, ExperimentService.get().getSchema());
    }

    public Set<String> getTableNames()
    {
        return ListService.get().getLists(getContainer()).keySet();
    }

    public TableInfo createTable(String name)
    {
        ListDefinition def = ListService.get().getLists(getContainer()).get(name);
        if (def != null)
        {
            // don't call def.getTable(),
            // UserSchema will call afterConstruct()
            return new ListTable(this, def);
        }
        return null;
    }

    @Override
    public QueryView createView(ViewContext context, QuerySettings settings, BindException errors)
    {
        ListDefinition def = ListService.get().getLists(getContainer()).get(settings.getQueryName());
        if (def != null)
        {
            return new ListQueryView(def, this, settings, errors);
        }

        return super.createView(context, settings, errors);
    }

    public String getDomainURI(String queryName)
    {
        Container container = getContainer();
        Map<String, ListDefinition> listDefs = ListService.get().getLists(container);
        if(null == listDefs)
            throw new NotFoundException("No lists found in the container '" + container.getPath() + "'.");

        ListDefinition listDef = listDefs.get(queryName);
        if(null == listDef)
            throw new NotFoundException("List '" + queryName + "' was not found in the container '" + container.getPath() + "'.");

        return listDef.getDomain().getTypeURI();
    }

    @Override
    public NavTree getSchemaBrowserLinks(User user)
    {
        NavTree root = super.getSchemaBrowserLinks(user);
        if (getContainer().hasPermission(user, ReadPermission.class))
            root.addChild("Manage lists", ListController.getBeginURL(getContainer()));
        return root;
    }
}
