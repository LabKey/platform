/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.wiki.query;

import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.EnumTableInfo;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: jeckels
 * Date: Feb 9, 2012
 */
public class WikiSchema extends UserSchema
{
    private static final Set<String> TABLE_NAMES;

    private static final String CURRENT_WIKI_VERSIONS = "CurrentWikiVersions";
    private static final String ALL_WIKI_VERSIONS = "AllWikiVersions";

    static
    {
        Set<String> names = new TreeSet<>();
        names.add(WikiService.RENDERER_TYPE_TABLE_NAME);
        names.add(CURRENT_WIKI_VERSIONS);
        names.add(ALL_WIKI_VERSIONS);
        TABLE_NAMES = Collections.unmodifiableSet(names);
    }

    public static void register(Module module)
    {
        DefaultSchema.registerProvider(WikiService.SCHEMA_NAME, new DefaultSchema.SchemaProvider(module)
        {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                return true;
            }

            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new WikiSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public WikiSchema(User user, Container container)
    {
        super(WikiService.SCHEMA_NAME, "Contains information about wiki pages", user, container, CommSchema.getInstance().getSchema());
    }

    @Override
    public TableInfo createTable(String name)
    {
        if (WikiService.RENDERER_TYPE_TABLE_NAME.equalsIgnoreCase(name))
        {
            EnumTableInfo<WikiRendererType> result = new EnumTableInfo<>(WikiRendererType.class, this, "Contains the type of renderers available to format content", false);
            result.setPublicSchemaName(getName());
            result.setName(WikiService.RENDERER_TYPE_TABLE_NAME);
            return result;
        }
        else if (CURRENT_WIKI_VERSIONS.equalsIgnoreCase(name) || ALL_WIKI_VERSIONS.equalsIgnoreCase(name))
        {
            TableInfo dbTable = CommSchema.getInstance().getSchema().getTable(name);
            SimpleUserSchema.SimpleTable<WikiSchema> table = new SimpleUserSchema.SimpleTable<>(this, dbTable);
            table.setDeleteURL(AbstractTableInfo.LINK_DISABLER);
            table.init();

            // Change default sort to newest->oldest
            ColumnInfo pk = table.getColumn("RowId");
            pk.setSortDirection(Sort.SortDirection.DESC);

            return table;
        }
        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        return TABLE_NAMES;
    }
}
