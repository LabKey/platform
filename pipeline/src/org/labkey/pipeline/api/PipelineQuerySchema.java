/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerDisplayColumn;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.pipeline.query.TriggerConfigurationsTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Query-exposed schema for the pipeline module.
 * User: jeckels
 * Date: Dec 18, 2009
 */
public class PipelineQuerySchema extends UserSchema
{
    public static final String SCHEMA_NAME = "pipeline";

    public static final String JOB_TABLE_NAME = "Job";
    public static final String TRIGGER_CONFIGURATIONS_TABLE_NAME = "TriggerConfigurations";

    public static void register(Module module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module)
        {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                return true;
            }

            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new PipelineQuerySchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public PipelineQuerySchema(User user, Container container)
    {
        super(SCHEMA_NAME, "Contains data about pipeline jobs", user, container, PipelineSchema.getInstance().getSchema());
    }

    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
    {
        if (JOB_TABLE_NAME.equalsIgnoreCase(name))
        {
            FilteredTable table = new FilteredTable<PipelineQuerySchema>(PipelineSchema.getInstance().getTableInfoStatusFiles(), this, cf)
            {
                @Override
                public FieldKey getContainerFieldKey()
                {
                    return FieldKey.fromParts("Folder");
                }
            };
            table.wrapAllColumns(true);
            table.removeColumn(table.getColumn("Container"));
            table.setName(JOB_TABLE_NAME);
            var folderColumn = table.wrapColumn("Folder", table.getRealTable().getColumn("Container"));
            folderColumn.setFk(new ContainerForeignKey(this));
            folderColumn.setDisplayColumnFactory(ContainerDisplayColumn.FACTORY);
            table.addColumn(folderColumn);
            String urlExp = "/pipeline-status/details.view?rowId=${rowId}";
            table.setDetailsURL(DetailsURL.fromString(urlExp));
            table.setDescription("Contains one row per pipeline job");

            if (getContainer().isRoot())
            {
                table.setContainerFilter(new ContainerFilter.AllFolders(getUser()));
            }

            table.getMutableColumn("RowId").setURL(DetailsURL.fromString(urlExp));
            table.getMutableColumn("Status").setDisplayColumnFactory(colInfo ->
            {
                DataColumn result = new DataColumn(colInfo);
                result.setNoWrap(true);
                return result;
            });

            table.getMutableColumn("Description").setDisplayColumnFactory(new DisplayColumnFactory()
            {
                @Override
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new DataColumn(colInfo)
                    {
                        @Override
                        public void addQueryFieldKeys(Set<FieldKey> keys)
                        {
                            super.addQueryFieldKeys(keys);
                            keys.add(getURLFieldKey());
                        }

                        private FieldKey getURLFieldKey()
                        {
                            return new FieldKey(getBoundColumn().getFieldKey().getParent(), "DataUrl");
                        }

                        @Override
                        public String renderURL(RenderContext ctx)
                        {
                            return ctx.get(getURLFieldKey(), String.class);
                        }
                    };
                }
            });
            UserIdQueryForeignKey.initColumn(this, table.getMutableColumn("CreatedBy"), true);
            UserIdQueryForeignKey.initColumn(this, table.getMutableColumn("ModifiedBy"), true);
            table.getMutableColumn("JobParent").setFk(new LookupForeignKey(cf,"Job", "Description")
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    return getTable(JOB_TABLE_NAME, getLookupContainerFilter());
                }
            });

            List<FieldKey> defaultCols = new ArrayList<>();
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
            defaultCols.add(FieldKey.fromParts("Info"));
            table.setDefaultVisibleColumns(defaultCols);
            table.setTitleColumn("Description");
            return table;
        }
        else if (TRIGGER_CONFIGURATIONS_TABLE_NAME.equalsIgnoreCase(name) && getContainer().hasPermission(getUser(), AdminPermission.class))
        {
            return createTriggerConfigurationsTable(cf);
        }
        
        return null;
    }

    // for pipeline internal use only; other uses should go through createTable() above for proper permissions check
    protected SimpleUserSchema.SimpleTable<PipelineQuerySchema> createTriggerConfigurationsTable(ContainerFilter cf)
    {
        return new TriggerConfigurationsTable(this, cf).init();
    }

    @Override
    public Set<String> getTableNames()
    {
        Set<String> names = new TreeSet<>();
        names.add(JOB_TABLE_NAME);

        // Issue 32063
        if (getContainer().hasPermission(getUser(), AdminPermission.class))
            names.add(TRIGGER_CONFIGURATIONS_TABLE_NAME);

        return names;
    }

}
