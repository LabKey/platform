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

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.WritablePropertyMap;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.usageMetrics.UsageMetricsProvider;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

// Temporary: measures how long the post-26.11 de-duplicating attachment migration will take. Remove once that migration is in place.
public class AttachmentHashingMaintenanceTask implements MaintenanceTask
{
    private static final String NAME = "AttachmentHashing";
    private static final String STATISTICS_CATEGORY = "AttachmentHashingStatistics";
    // Matches GlobalAttachmentManager's property; labbook may not be deployed, so read it directly
    private static final String ELN_PROPERTIES_KEY = "ELN";
    private static final String ATTACHMENT_PARENT_KEY = "attachmentParentId";
    private static final long PROGRESS_INTERVAL = TimeUnit.SECONDS.toMillis(30);

    @Override
    public String getDescription()
    {
        return "Hash all attachments once to measure hashing performance";
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public void run(Logger log)
    {
        if (!PropertyManager.getProperties(STATISTICS_CATEGORY).isEmpty())
        {
            log.debug("Attachment hashing statistics already collected; skipping");
            return;
        }

        DbScope scope = CoreSchema.getInstance().getScope();
        TableInfo documents = CoreSchema.getInstance().getTableInfoDocuments();
        String globalParentId = PropertyManager.getProperties(ELN_PROPERTIES_KEY).get(ATTACHMENT_PARENT_KEY);

        // Document is NULL for attachments stored on the file system (e.g., FileSystemSnapshot)
        SQLFragment listSql = SQLFragment.unsafe("SELECT RowId FROM ").append(documents).unsafeAppend(" WHERE Document IS NOT NULL");
        if (globalParentId != null)
            listSql.unsafeAppend(" AND Parent <> ?", globalParentId);
        listSql.unsafeAppend(" ORDER BY RowId");

        long start = System.currentTimeMillis();
        List<Integer> rowIds = new SqlSelector(scope, listSql).getArrayList(Integer.class);
        long enumerationMillis = System.currentTimeMillis() - start;
        log.info("Attachment hashing: enumerated {} documents in {}", Formats.commaf0.format(rowIds.size()), DateUtil.formatDuration(enumerationMillis));

        long hashStart = System.currentTimeMillis();
        long lastProgress = hashStart;
        int hashed = 0;
        long hashedBytes = 0;

        for (int rowId : rowIds)
        {
            if (ContextListener.isShuttingDown())
            {
                log.info("Attachment hashing: stopping due to server shutdown after hashing {} of {} documents; statistics not saved", Formats.commaf0.format(hashed), Formats.commaf0.format(rowIds.size()));
                return;
            }

            // Postgres materializes bytea values regardless, so getBytes() costs no more than getBinaryStream()
            byte[] bytes = new SqlSelector(scope, SQLFragment.unsafe("SELECT Document FROM ").append(documents).unsafeAppend(" WHERE RowId = ?", rowId)).getObject(byte[].class);

            // Null if the document was deleted after enumeration
            if (bytes != null)
            {
                DigestUtils.sha256Hex(bytes);
                hashed++;
                hashedBytes += bytes.length;
            }

            long now = System.currentTimeMillis();
            if (now - lastProgress >= PROGRESS_INTERVAL)
            {
                lastProgress = now;
                log.info("Attachment hashing: {} of {} documents hashed ({} bytes), elapsed {}, {} attachments per second, {} bytes per second", Formats.commaf0.format(hashed), Formats.commaf0.format(rowIds.size()), Formats.commaf0.format(hashedBytes), DateUtil.formatDuration(now - hashStart), Formats.commaf0.format(perSecond(hashed, now - hashStart)), Formats.commaf0.format(perSecond(hashedBytes, now - hashStart)));
            }
        }

        long hashingMillis = System.currentTimeMillis() - hashStart;
        long attachmentsPerSecond = perSecond(hashed, hashingMillis);
        long bytesPerSecond = perSecond(hashedBytes, hashingMillis);
        log.info("Attachment hashing: complete. Hashed {} documents ({} bytes) in {}, {} attachments per second, {} bytes per second", Formats.commaf0.format(hashed), Formats.commaf0.format(hashedBytes), DateUtil.formatDuration(hashingMillis), Formats.commaf0.format(attachmentsPerSecond), Formats.commaf0.format(bytesPerSecond));

        WritablePropertyMap statistics = PropertyManager.getWritableProperties(STATISTICS_CATEGORY, true);
        statistics.put("documentCount", String.valueOf(rowIds.size()));
        statistics.put("hashedCount", String.valueOf(hashed));
        statistics.put("hashedBytes", String.valueOf(hashedBytes));
        statistics.put("enumerationMillis", String.valueOf(enumerationMillis));
        statistics.put("hashingMillis", String.valueOf(hashingMillis));
        statistics.put("attachmentsPerSecond", String.valueOf(attachmentsPerSecond));
        statistics.put("bytesPerSecond", String.valueOf(bytesPerSecond));
        statistics.save();
    }

    private static long perSecond(long count, long elapsedMillis)
    {
        return elapsedMillis > 0 ? Math.round(count * 1000.0 / elapsedMillis) : 0;
    }

    public static UsageMetricsProvider getMetricsProvider()
    {
        return metrics -> {
            Map<String, String> statistics = PropertyManager.getProperties(STATISTICS_CATEGORY);
            metrics.reportedIf(!statistics.isEmpty())
                .group("attachmentHashing", "One-time SHA-256 hashing of all non-global, database-stored attachments: document counts, bytes hashed, enumeration and hashing times (ms), and attachments and bytes hashed per second")
                .putAll(() -> {
                    Map<String, Long> values = new TreeMap<>();
                    statistics.forEach((key, value) -> values.put(key, Long.valueOf(value)));
                    return values;
                });
            return metrics;
        };
    }
}
