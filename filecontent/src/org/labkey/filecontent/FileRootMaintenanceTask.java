/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.filecontent;

import org.apache.commons.io.file.PathUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.StopIteratingException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.files.FileContentService;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Objects;

public class FileRootMaintenanceTask implements MaintenanceTask
{
    // Spend no more than 10 minutes crawling and computing folder sizes
    private static final int MAX_MINUTES = 10;

    @Override
    public String getDescription()
    {
        return "Calculate file root sizes";
    }

    @Override
    public String getName()
    {
        return "FileRootMaintenanceTask";
    }

    @Override
    public boolean canDisable()
    {
        return false;
    }

    @Override
    public void run(Logger log)
    {
        FileContentService service = FileContentService.get();

        if (service != null)
        {
            long deadline = HeartBeat.currentTimeMillis() + DateUtils.MILLIS_PER_MINUTE * MAX_MINUTES;
            MutableInt rootCount = new MutableInt();
            MutableBoolean finished = new MutableBoolean(true);
            TableInfo containers = CoreSchema.getInstance().getTableInfoContainers();

            String updateLastCrawledSql = "UPDATE " + containers.getSelectName() + " SET FileRootLastCrawled = ? WHERE EntityId = ?";
            String updateLastCrawledAndSizeSql = "UPDATE " + containers.getSelectName() + " SET FileRootLastCrawled = ?, FileRootSize = ? WHERE EntityId = ?";
            SqlExecutor executor = new SqlExecutor(containers.getSchema());
            String orderBy = " ORDER BY FileRootLastCrawled NULLS FIRST";
            SQLFragment selectSql = new SQLFragment("SELECT RowId, EntityId, FileRootSize FROM " + containers.getSelectName() + orderBy);

            new SqlSelector(containers.getSchema(), selectSql)
                .forEach(FileRootRecord.class, record -> {
                    Container c = ContainerManager.getForId(record.entityId());
                    if (c != null)
                    {
                        Path root = service.getFileRootPath(c);
                        try
                        {
                            Long size = null != root && Files.isDirectory(root) ? PathUtils.sizeOfDirectory(root) : null;
                            long current = HeartBeat.currentTimeMillis();

                            // Update FileRootSize only if it changed. Always update LastCrawled, even for invalid file
                            // roots and non-changing sizes.
                            boolean sizeChanged = !Objects.equals(record.fileRootSize(), size);
                            SQLFragment updateSql = sizeChanged ?
                                    // Use a TypedValue to avoid a type conversion problem on Postgres when the value is null
                                    new SQLFragment(updateLastCrawledAndSizeSql, new Date(current), new Parameter.TypedValue(size, JdbcType.BIGINT), record.entityId()) :
                                    new SQLFragment(updateLastCrawledSql, new Date(current), record.entityId());

                            // core.Containers has no PK, so Table.update() is not an option
                            executor.execute(updateSql);
                            ContainerManager.uncache(c);  // Container stashes FileRootLastCrawled & FileRootSize

                            rootCount.increment();

                            if (current >= deadline)
                            {
                                finished.setFalse();
                                throw new StopIteratingException();
                            }
                        }
                        catch (IOException e)
                        {
                            log.error("Failed to get size for {} in {}", root, c.getPath(), e);
                        }
                    }
                });

            if (finished.getValue())
                log.info("Completed crawling all {} file roots", rootCount.getValue());
            else
                log.info("Crawled {} file roots before reaching the {}-minute deadline. Crawling will continue during the next system maintenance run.", rootCount.getValue(), MAX_MINUTES);
        }
    }

    private record FileRootRecord(int rowId, String entityId, Long fileRootSize) {}
}
