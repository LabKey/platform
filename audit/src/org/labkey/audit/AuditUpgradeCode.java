package org.labkey.audit;

import org.labkey.api.data.UpgradeCode;

/**
 * User: kevink
 * Date: 8/9/13
 */
public class AuditUpgradeCode implements UpgradeCode
{
    /**
     * This upgrade code isn't called directly by an upgrade script, but
     * is called immediately after all modules have started up during the 13.2 to 13.3 ugprade.
     *
     * When this migration code is removed in release 16.2 (per our two-year upgrade policy)
     * we can remove all the deprecated AuditLogEvent, AuditViewFactory, and related classes.
     */
    public static void migrateProviders(AuditLogImpl audit)
    {
        audit.migrateProviders();
    }
}
