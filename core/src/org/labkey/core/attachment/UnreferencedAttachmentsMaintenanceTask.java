/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.core.attachment;

import org.apache.logging.log4j.Logger;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.Formats;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;

/** Deletes are pointer-only, so this is the only code that deletes from core.Attachments */
public class UnreferencedAttachmentsMaintenanceTask implements MaintenanceTask
{
    private static final int BATCH_SIZE = 100;

    @Override
    public String getDescription()
    {
        return "Delete attachments that no attachment pointer references";
    }

    @Override
    public String getName()
    {
        return "UnreferencedAttachments";
    }

    // Deletes remove only pointers, so without this task unreferenced attachment content accumulates forever
    @Override
    public boolean canDisable()
    {
        return false;
    }

    @Override
    public void run(Logger log)
    {
        int total = 0;
        int deleted;

        do
        {
            if (ContextListener.isShuttingDown())
                break;

            try
            {
                deleted = deleteBatch();
            }
            catch (RuntimeSQLException e)
            {
                // Only an FK violation is the expected race; anything else is a real failure
                if (!"23503".equals(e.getSQLException().getSQLState()))
                    throw e;
                log.info("Stopping after a batch lost a race with a new attachment pointer; the next run will retry", e);
                break;
            }
            total += deleted;
        }
        while (deleted == BATCH_SIZE);

        if (total > 0)
            log.info("Deleted {} unreferenced attachments", Formats.commaf0.format(total));
    }

    /**
     * A pointer inserted concurrently holds a key-share lock on its attachment, so the delete waits for it and then
     * fails on FK_AttachmentPointers_AttachmentId instead of orphaning it. The next run retries the rest.
     */
    static int deleteBatch()
    {
        CoreSchema core = CoreSchema.getInstance();
        SQLFragment sql = SQLFragment.unsafe("DELETE FROM ").append(core.getTableInfoAttachments())
            .unsafeAppend(" WHERE RowId IN (SELECT a.RowId FROM ").append(core.getTableInfoAttachments(), "a")
            .unsafeAppend(" WHERE NOT EXISTS (SELECT 1 FROM ").append(core.getTableInfoAttachmentPointers(), "ap")
            .unsafeAppend(" WHERE ap.AttachmentId = a.RowId) LIMIT ?)", BATCH_SIZE);

        return new SqlExecutor(core.getSchema()).execute(sql);
    }
}
