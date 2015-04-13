/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.GroupManager;

/**
 * User: kevink
 * Date: 8/9/13
 */
public class AuditUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(AuditUpgradeCode.class);

    /**
     * This upgrade code isn't called directly by an upgrade script, but
     * is called immediately after all modules have started up during the 13.2 to 13.3 upgrade.
     *
     * When this migration code is removed in release 16.2 (per our two-year upgrade policy)
     * we can remove all the deprecated AuditLogEvent, AuditViewFactory, and related classes.
     */
    public static void migrateProviders(AuditLogImpl audit)
    {
        audit.migrateProviders();
    }

    /**
     * Convert erroneous user field values of 0 to null in the group audit event table. This problem was introduced
     * when the new audit provider framework was introduced in 13.3.
     *
     * invoked from audit-13.30-13.31.sql
     */
    @DeferredUpgrade
    public void convertGroupAuditUserField(ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            if (AuditLogService.get().isMigrateComplete() || AuditLogService.get().hasEventTypeMigrated(GroupManager.GROUP_AUDIT_EVENT))
            {
                // get the new table for the group audit provider
                AuditTypeProvider provider = AuditLogService.get().getAuditProvider(GroupManager.GROUP_AUDIT_EVENT);
                if (provider != null)
                {
                    provider.initializeProvider(context.getUpgradeUser());

                    Domain domain = provider.getDomain();
                    DbSchema dbSchema = AuditSchema.getInstance().getSchema();
                    TableInfo table = StorageProvisioner.createTableInfo(domain);

                    SQLFragment sql = new SQLFragment();
                    sql.append("UPDATE ").append(table.getSelectName()).append(" SET \"user\" = NULL WHERE \"user\" = 0");

                    new SqlExecutor(dbSchema).execute(sql);
                }
                else
                    _log.error("Unable to get the audit type provider for the event: " + GroupManager.GROUP_AUDIT_EVENT);
            }
        }
    }
}
