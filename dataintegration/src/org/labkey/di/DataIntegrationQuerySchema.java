/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.di;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.di.view.DataIntegrationController;
import org.labkey.di.view.EtlDefQueryView;
import org.springframework.validation.BindException;

import java.util.Collections;
import java.util.Set;

/**
 * User: jeckels
 * Date: 3/13/13
 */
public class DataIntegrationQuerySchema extends SimpleUserSchema
{
    public static final String SCHEMA_NAME = "dataintegration";
    public static final String SCHEMA_DESCRIPTION = "Contains data for ETL Transformations";
    // list of each transform type with last run information
    public static final String TRANSFORMSUMMARY_TABLE_NAME = "TransformSummary";
    // history of a specific transform type ordered by newest run first
    public static final String TRANSFORMHISTORY_TABLE_NAME = "TransformHistory";
    // all transform runs
    public static final String TRANSFORMRUN_TABLE_NAME = "TransformRun";
    public static final String ETL_DEF_TABLE_NAME = "etlDef";

    public enum Columns
    {
        TransformRunId("diTransformRunId"),
        TransformModified("diModified"),
        TransformModifiedBy("diModifiedBy"),
        TransformRowVersion("diRowVersion"),
        TransformCreated("diCreated"),
        TransformCreatedBy("diCreatedBy");

        final String _name;

        Columns(String name)
        {
            _name = name;
        }

        public String getColumnName()
        {
            return _name;
        }
    }

    public DataIntegrationQuerySchema(User user, Container container)
    {
        super(SCHEMA_NAME, SCHEMA_DESCRIPTION, user, container, getSchema());
    }

    @Override
    public synchronized Set<String> getVisibleTableNames()
    {
        Set<String> tableNames = new CaseInsensitiveTreeSet();
        tableNames.addAll(super.getVisibleTableNames());
        addVirtualTables(tableNames);
        return Collections.unmodifiableSet(tableNames);
    }

    @Override
    public Set<String> getTableNames()
    {
        Set<String> tableNames = new CaseInsensitiveTreeSet(getDbSchema().getTableNames());
        addVirtualTables(tableNames);
        return Collections.unmodifiableSet(tableNames);
    }

    private void addVirtualTables(Set<String> tableNames)
    {
        tableNames.add(TRANSFORMHISTORY_TABLE_NAME);
        tableNames.add(TRANSFORMSUMMARY_TABLE_NAME);
    }

    @Override
    public TableInfo createTable(String name)
    {
        if (TRANSFORMHISTORY_TABLE_NAME.equalsIgnoreCase(name))
            return new TransformHistoryTable(this);

        if (TRANSFORMSUMMARY_TABLE_NAME.equalsIgnoreCase(name))
            return new TransformSummaryTable(this);

        if (ETL_DEF_TABLE_NAME.equalsIgnoreCase(name))
            return new EtlDefTableInfo(this).init();

        return getSchemaTable(name);
    }

    @Override
    public QueryView createView(ViewContext context, @NotNull QuerySettings settings, BindException errors)
    {
        if (TRANSFORMHISTORY_TABLE_NAME.equalsIgnoreCase(settings.getQueryName()))
            return new TransformHistoryView(this, settings, errors);
        else if (ETL_DEF_TABLE_NAME.equalsIgnoreCase(settings.getQueryName()))
            return new EtlDefQueryView(this, settings, errors);
        return super.createView(context, settings, errors);
    }


    private TableInfo getSchemaTable(String name)
    {
        SchemaTableInfo tinfo = getDbSchema().getTable(name);
        if (null == tinfo)
            return null;

        FilteredTable ftable = new FilteredTable(tinfo, this);
        ftable.wrapAllColumns(true);
        return ftable;
    }

    public static void register(final DataIntegrationModule module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module)
        {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                return true;
            }

            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new DataIntegrationQuerySchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public static DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME, DbSchemaType.Module);
    }


    public static TableInfo getTransformRunTableInfo()
    {
        return getSchema().getTable("transformrun");
    }


    public static TableInfo getTransformConfigurationTableInfo()
    {
        return getSchema().getTable("transformconfiguration");
    }

    public static TableInfo getEtlDefTableInfo()
    {
        return getSchema().getTable(ETL_DEF_TABLE_NAME);
    }

    public static String getTransformRunTableName()
    {
        return SCHEMA_NAME + "." + TRANSFORMRUN_TABLE_NAME;
    }

    private class EtlDefTableInfo extends SimpleTable<DataIntegrationQuerySchema>
    {
        public EtlDefTableInfo(DataIntegrationQuerySchema schema)
        {
            super(schema, DataIntegrationQuerySchema.getEtlDefTableInfo());
        }

        @Override
        protected void addTableURLs()
        {
            ActionURL insertUrl = new ActionURL(DataIntegrationController.CreateDefinitionAction.class, getContainer());
            setInsertURL(new DetailsURL(insertUrl));
            ActionURL updateUrl = new ActionURL(DataIntegrationController.EditDefinitionAction.class, getContainer());
            setUpdateURL(new DetailsURL(updateUrl, Collections.singletonMap("etlDefId", FieldKey.fromString("etlDefId"))));
            ActionURL deleteUrl = new ActionURL(DataIntegrationController.DeleteDefinitionsAction.class, getContainer());
            setDeleteURL(new DetailsURL(deleteUrl));
        }

        @Override
        public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
        {
            if (perm == ReadPermission.class)
                return super.hasPermission(user, perm);
            else
                return getContainer().hasPermission(user, AdminPermission.class);
        }
    }
}
