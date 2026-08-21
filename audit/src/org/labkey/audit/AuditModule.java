/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

package org.labkey.audit;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.SiteSettingsAuditProvider;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumn;
import org.labkey.api.migration.DatabaseMigrationService;
import org.labkey.api.migration.MigrationTableHandler;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.audit.query.AuditQuerySchema;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public class AuditModule extends DefaultModule
{
    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    public String getName()
    {
        return "Audit";
    }

    @Override
    public Double getSchemaVersion()
    {
        return 26.000;
    }

    @Override
    protected void init()
    {
        AuditLogService.registerProvider(AuditLogImpl.get());
        addController("audit", AuditController.class);
    }

    @Override
    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_NEVER;
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        AuditQuerySchema.register(this);
        AuditLogService.get().registerAuditType(new SiteSettingsAuditProvider());
        AuditController.registerAdminConsoleLinks();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return getProvisionedSchemaNames();
    }

    @Override
    @NotNull
    public Set<String> getProvisionedSchemaNames()
    {
        return Collections.singleton(AuditSchema.SCHEMA_NAME);
    }

    private final Set<String> _registeredMigrationTables = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    @Override
    public void registerMigrationHandlers(@NotNull DatabaseMigrationService service)
    {
        service.registerSchemaContributor(AuditSchema.SCHEMA_NAME, schema ->
            schema.getTableNames().stream()
                .map(schema::getTable)
                .filter(table -> table != null && _registeredMigrationTables.add(table.getSelectName()))
                .forEach(table -> service.registerTableHandler(new MigrationTableHandler()
                {
                    @Override
                    public TableInfo getTableInfo()
                    {
                        return table;
                    }

                    @Override
                    public ColumnInfo handleColumn(ColumnInfo col)
                    {
                        if ("hasdetails".equalsIgnoreCase(col.getName()))
                            return new HasDetailsCastColumn(col);
                        return col;
                    }
                })));
    }

    // Some legacy source databases store the DatasetAuditDomain "HasDetails" column as varchar
    // while the target was provisioned as boolean. CAST to BIT in the source SELECT and report
    // BOOLEAN as the JdbcType so the migration's INSERT parameter binds cleanly into the
    // target's boolean column (otherwise the parameter inherits the source column's VARCHAR
    // type and PG rejects the bound value).
    private static final class HasDetailsCastColumn extends WrappedColumn
    {
        private HasDetailsCastColumn(ColumnInfo col)
        {
            super(col, col.getName());
        }

        @Override
        @NotNull
        public JdbcType getJdbcType()
        {
            return JdbcType.BOOLEAN;
        }

        @Override
        public SQLFragment getValueSql(String tableAlias)
        {
            return new SQLFragment("CAST(").append(super.getValueSql(tableAlias)).append(" AS BIT)");
        }
    }
}
