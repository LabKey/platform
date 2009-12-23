/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.pipeline.api;

import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.data.*;

import java.util.*;

/**
 * User: jeckels
 * Date: Dec 18, 2009
 */
public class PipelineQuerySchema extends UserSchema
{
    public static final String SCHEMA_NAME = "pipeline";

    public static final String JOB_TABLE_NAME = "job";

    private static final Set<String> TABLE_NAMES;

    static
    {
        Set<String> names = new TreeSet<String>();
        names.add(JOB_TABLE_NAME);
        TABLE_NAMES = Collections.unmodifiableSet(names);
    }

    public static void register()
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new PipelineQuerySchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public PipelineQuerySchema(User user, Container container)
    {
        super(SCHEMA_NAME, "Contains data about pipeline jobs", user, container, PipelineSchema.getInstance().getSchema());
    }

    protected TableInfo createTable(String name)
    {
        if (JOB_TABLE_NAME.equalsIgnoreCase(name))
        {
            FilteredTable table = new FilteredTable(PipelineSchema.getInstance().getTableInfoStatusFiles(), getContainer());
            table.wrapAllColumns(true);

            if (getContainer().isRoot())
            {
                table.setContainerFilter(new ContainerFilter.AllFolders(getUser()));
            }

            table.getColumn("Status").setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    DataColumn result = new DataColumn(colInfo);
                    result.setNoWrap(true);
                    return result;
                }
            });


            List<FieldKey> defaultCols = new ArrayList<FieldKey>();
            defaultCols.add(FieldKey.fromParts("Status"));
            defaultCols.add(FieldKey.fromParts("Created"));
            if (getContainer().isRoot())
            {
                defaultCols.add(FieldKey.fromParts("FilePath"));
            }
            else
            {
                defaultCols.add(FieldKey.fromParts("Description"));
            }
            table.setDefaultVisibleColumns(defaultCols);
            return table;
        }
        
        return null;
    }

    public Set<String> getTableNames()
    {
        return TABLE_NAMES;
    }

}
