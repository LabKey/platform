/*
 * Copyright (c) 2013-2016 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.module.Module;
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
import org.labkey.list.controllers.ListController;
import org.labkey.list.view.ListQueryView;
import org.springframework.validation.BindException;

import java.util.Set;
import java.util.stream.Collectors;

public class ListQuerySchema extends UserSchema
{
    public static final String NAME = "lists";
    public static final String DESCR = "Contains a data table for each defined list";
    private static final Logger LOG = Logger.getLogger(ListQuerySchema.class);

    public static void register(Module module)
    {
        DefaultSchema.registerProvider(NAME, new DefaultSchema.SchemaProvider(module)
        {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                return true;
            }

            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new ListQuerySchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public ListQuerySchema(User user, Container container)
    {
        super(NAME, DESCR, user, container, ListSchema.getInstance().getSchema());
    }

    @Override
    public DbSchema getDbSchema()
    {
        return ListSchema.getInstance().getSchema();
    }

    public Set<String> getTableNames()
    {
        return ListManager.get().getLists(getContainer())
            .stream()
            .map(ListDef::getName)
            .collect(Collectors.toSet());
    }

    @Nullable
    public TableInfo createTable(String name)
    {
        ListDefinition def = ListService.get().getList(getContainer(), name);
        if (def != null)
        {
            Domain domain = def.getDomain();
            if (domain != null)
            {
                try
                {
                    return new ListTable(this, def, def.getDomain());
                }
                catch (IllegalStateException e)
                {
                    LOG.warn("Failed to construct list table", e);
                }
            }
        }
        return null;
    }

    @Override
    public QueryView createView(ViewContext context, @NotNull QuerySettings settings, BindException errors)
    {
        ListDefinition def = ListService.get().getList(getContainer(), settings.getQueryName());
        if (def != null)
        {
            return new ListQueryView(def, this, settings, errors);
        }

        return super.createView(context, settings, errors);
    }

    public String getDomainURI(String queryName)
    {
        Container container = getContainer();
        ListDefinition listDef = ListService.get().getList(container, queryName);
        if (null == listDef)
            throw new NotFoundException("List '" + queryName + "' was not found in the container '" + container.getPath() + "'.");
        Domain domain = listDef.getDomain();
        if (null == domain)
            throw new IllegalStateException("Domain expected.");
        return domain.getTypeURI();
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
